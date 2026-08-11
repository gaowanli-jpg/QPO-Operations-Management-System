package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.machining.quote.dto.u9.U9SalesOrderHeadDTO;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.service.u9.U9CommonEntityClient;
import com.machining.quote.service.u9.U9SalesOrderContractValidator;
import com.machining.quote.service.u9.U9SalesOrderContractValidator.ValidationResult;
import com.machining.quote.service.u9.U9SalesOrderRequestAssembler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class SalesOrderU9SyncStateService {
    public static final String STATUS_UNSYNCED = "未同步";
    public static final String STATUS_SYNCING = "同步中";
    public static final String STATUS_SYNCED = "已同步";
    public static final String STATUS_FAILED = "同步失败";
    public static final String STATUS_PENDING_CONFIRMATION = "结果待确认";

    @Autowired
    private SalesOrderHeaderMapper headerMapper;
    @Autowired
    private SalesOrderItemMapper itemMapper;
    @Autowired(required = false)
    private DataScopeService dataScopeService;
    @Autowired
    private U9SalesOrderRequestAssembler requestAssembler = new U9SalesOrderRequestAssembler();
    @Autowired
    private U9SalesOrderContractValidator contractValidator = new U9SalesOrderContractValidator();
    private Clock clock = Clock.systemDefaultZone();

    @Transactional
    public PreparedOrder reserve(Long salesOrderId) {
        SalesOrderHeader header = lockedHeader(salesOrderId);
        assertDataScope(header);
        assertCanReserve(header);
        List<SalesOrderItem> items = loadItems(header.getId());
        ValidationResult validation = contractValidator.validate(header, items);
        if (validation.hasBlockingErrors()) {
            String message = validation.firstBlockingError();
            markValidationFailed(header, message);
            throw new RuntimeException(message);
        }
        U9SalesOrderHeadDTO request = requestAssembler.assemble(header, items);
        String fingerprint = requestAssembler.fingerprint(request);
        String attemptId = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime startedAt = now();

        UpdateWrapper<SalesOrderHeader> update = new UpdateWrapper<>();
        update.eq("id", header.getId())
                .set("u9_sync_status", STATUS_SYNCING)
                .set("u9_sync_attempt_id", attemptId)
                .set("u9_sync_request_hash", fingerprint)
                .set("u9_sync_started_at", startedAt)
                .set("u9_sync_finished_at", null)
                .set("u9_sync_message", "U9 同步已预约，等待外部创建")
                .set("updated_at", startedAt);
        int updated = headerMapper.update(null, update);
        if (updated <= 0) {
            throw new RuntimeException("U9 同步预约失败，请刷新后重试");
        }
        header.setU9SyncStatus(STATUS_SYNCING);
        header.setU9SyncAttemptId(attemptId);
        header.setU9SyncRequestHash(fingerprint);
        header.setU9SyncStartedAt(startedAt);
        header.setU9SyncFinishedAt(null);
        header.setU9SyncMessage("U9 同步已预约，等待外部创建");
        header.setUpdatedAt(startedAt);
        return new PreparedOrder(header, items, request, attemptId, fingerprint, startedAt);
    }

    @Transactional
    public PreparedOrder prepareForReconcile(Long salesOrderId) {
        SalesOrderHeader header = lockedHeader(salesOrderId);
        assertDataScope(header);
        String status = trimToNull(header.getU9SyncStatus());
        if (!STATUS_PENDING_CONFIRMATION.equals(status) && !STATUS_SYNCING.equals(status)) {
            throw new RuntimeException("当前 U9 状态不需要核对");
        }
        List<SalesOrderItem> items = loadItems(header.getId());
        U9SalesOrderHeadDTO request = requestAssembler.assemble(header, items);
        String fingerprint = trimToNull(header.getU9SyncRequestHash());
        if (fingerprint == null) {
            fingerprint = requestAssembler.fingerprint(request);
        }
        return new PreparedOrder(
                header,
                items,
                request,
                trimToNull(header.getU9SyncAttemptId()),
                fingerprint,
                header.getU9SyncStartedAt()
        );
    }

    @Transactional
    public void markSuccess(PreparedOrder prepared, String u9OrderId, String u9OrderCode, String message) {
        updateFinalState(prepared, STATUS_SYNCED, u9OrderId, u9OrderCode, message);
    }

    @Transactional
    public void markFailed(PreparedOrder prepared, String message) {
        updateFinalState(prepared, STATUS_FAILED, null, null, message);
    }

    @Transactional
    public void markPending(PreparedOrder prepared, String message) {
        updateFinalState(prepared, STATUS_PENDING_CONFIRMATION, null, null, message);
    }

    private SalesOrderHeader lockedHeader(Long salesOrderId) {
        if (salesOrderId == null) {
            throw new RuntimeException("销售订单 ID 不能为空");
        }
        SalesOrderHeader header = headerMapper.selectByIdForUpdate(salesOrderId);
        if (header == null) {
            throw new RuntimeException("销售订单不存在");
        }
        return header;
    }

    private void assertCanReserve(SalesOrderHeader header) {
        String status = trimToNull(header.getU9SyncStatus());
        if (STATUS_SYNCING.equals(status)) {
            throw new RuntimeException("U9 同步处理中，请刷新后再核对");
        }
        if (STATUS_PENDING_CONFIRMATION.equals(status)) {
            throw new RuntimeException("结果待确认状态只能核对，不能直接重新创建");
        }
        if (STATUS_SYNCED.equals(status)) {
            throw new RuntimeException("销售订单已同步，不能重复创建 U9 订单");
        }
    }

    private List<SalesOrderItem> loadItems(Long salesOrderId) {
        QueryWrapper<SalesOrderItem> query = new QueryWrapper<>();
        query.eq("sales_order_id", salesOrderId).orderByAsc("id");
        List<SalesOrderItem> items = itemMapper.selectList(query);
        return items == null ? List.of() : items;
    }

    private void updateFinalState(PreparedOrder prepared, String status, String u9OrderId, String u9OrderCode, String message) {
        LocalDateTime finishedAt = now();
        UpdateWrapper<SalesOrderHeader> update = new UpdateWrapper<>();
        update.eq("id", prepared.header().getId());
        if (!isBlank(prepared.attemptId())) {
            update.eq("u9_sync_attempt_id", prepared.attemptId());
        }
        update.set("u9_sync_status", status)
                .set("u9_order_id", trimToNull(u9OrderId))
                .set("u9_order_code", trimToNull(u9OrderCode))
                .set("u9_sync_message", safeMessage(message))
                .set("u9_sync_finished_at", finishedAt)
                .set("updated_at", finishedAt);
        int updated = headerMapper.update(null, update);
        if (updated <= 0) {
            throw new RuntimeException("U9 同步状态已变化，请刷新后查看最新结果");
        }
        prepared.header().setU9SyncStatus(status);
        prepared.header().setU9OrderId(trimToNull(u9OrderId));
        prepared.header().setU9OrderCode(trimToNull(u9OrderCode));
        prepared.header().setU9SyncMessage(safeMessage(message));
        prepared.header().setU9SyncFinishedAt(finishedAt);
        prepared.header().setUpdatedAt(finishedAt);
    }

    private void markValidationFailed(SalesOrderHeader header, String message) {
        LocalDateTime finishedAt = now();
        String safeMessage = safeMessage(message);
        UpdateWrapper<SalesOrderHeader> update = new UpdateWrapper<>();
        update.eq("id", header.getId())
                .set("u9_sync_status", STATUS_FAILED)
                .set("u9_sync_message", safeMessage)
                .set("u9_sync_finished_at", finishedAt)
                .set("updated_at", finishedAt);
        headerMapper.update(null, update);
        header.setU9SyncStatus(STATUS_FAILED);
        header.setU9SyncMessage(safeMessage);
        header.setU9SyncFinishedAt(finishedAt);
        header.setUpdatedAt(finishedAt);
    }

    private void assertDataScope(SalesOrderHeader header) {
        if (dataScopeService != null) {
            dataScopeService.assertCanAccessSalesOrder(header);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private String safeMessage(String value) {
        String text = isBlank(value) ? "U9 同步状态更新" : U9CommonEntityClient.sanitize(value).replaceAll("\\s+", " ").trim();
        return text.length() <= 500 ? text : text.substring(0, 500) + "...";
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public record PreparedOrder(
            SalesOrderHeader header,
            List<SalesOrderItem> items,
            U9SalesOrderHeadDTO request,
            String attemptId,
            String requestFingerprint,
            LocalDateTime startedAt) {
    }
}
