package com.machining.quote.service.mes;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.machining.quote.dto.ReleaseSourceType;
import com.machining.quote.dto.mes.MesMaterialCallbackReq;
import com.machining.quote.dto.mes.MesMaterialCallbackResp;
import com.machining.quote.dto.mes.MesMaterialResolveReq;
import com.machining.quote.dto.mes.MesMaterialResolveResp;
import com.machining.quote.entity.MesIntegrationBatch;
import com.machining.quote.entity.MesMaterialBatchItem;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderDeliveryTicketMesRequest;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.MesIntegrationBatchMapper;
import com.machining.quote.mapper.MesMaterialBatchItemMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMesRequestMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.BusinessFileService;
import com.machining.quote.service.SourceItemMaterialNoSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class MesMaterialSyncService {
    private static final Logger log = LoggerFactory.getLogger(MesMaterialSyncService.class);
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_CALLBACK_PENDING = "CALLBACK_PENDING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_RESULT_UNKNOWN = "RESULT_UNKNOWN";
    private static final String BATCH_STATUS_PARTIAL_PENDING = "PARTIAL_PENDING";
    private static final int MATERIAL_CALLBACK_MAX_ITEMS = 100;
    private static final DateTimeFormatter BIZ_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final char[] CROCKFORD32 = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();
    private final MesMaterialRequestAssembler batchRequestAssembler = new MesMaterialRequestAssembler();
    private final MesBatchFingerprintService batchFingerprintService = new MesBatchFingerprintService();

    @Autowired
    private OrderDeliveryTicketMapper ticketMapper;
    @Autowired(required = false)
    private OrderDeliveryTicketMesRequestMapper mesRequestMapper;
    @Autowired(required = false)
    private MesBatchStateService mesBatchStateService;
    @Autowired(required = false)
    private MesIntegrationBatchMapper mesIntegrationBatchMapper;
    @Autowired(required = false)
    private MesMaterialBatchItemMapper mesMaterialBatchItemMapper;
    @Autowired(required = false)
    private OrderRfqMapper orderRfqMapper;
    @Autowired(required = false)
    private ItemQuoteVersionMapper itemMapper;
    @Autowired(required = false)
    private SalesOrderItemMapper salesOrderItemMapper;
    @Autowired(required = false)
    private SalesOrderHeaderMapper salesOrderHeaderMapper;
    @Autowired
    private MesMaterialClient mesMaterialClient;
    @Autowired
    private BusinessFileService businessFileService;
    @Autowired(required = false)
    private AuditService auditService;
    @Autowired(required = false)
    private MesMaterialDedupService dedupService;
    @Autowired
    private SourceItemMaterialNoSyncService sourceItemMaterialNoSyncService;

    public MesMaterialCallbackResp handleMaterialCallback(MesMaterialCallbackReq req) {
        long startedAt = System.currentTimeMillis();
        String mesRequestId = trimToNull(req == null ? null : req.getMesRequestId());
        if (mesRequestId == null) {
            throw callbackError(HttpStatus.BAD_REQUEST, "MES 请求ID不能为空");
        }
        String externalTraceId = trimToNull(req.getExternalTraceId());
        log.info("MES material callback received mesRequestId={} externalTraceId={}",
                mesRequestId, externalTraceId);
        OrderDeliveryTicketMesRequest request = loadMesRequest(mesRequestId);
        Integer ticketId = request.getTicketId();
        if (ticketId == null) {
            recordCallbackAudit("DELIVERY_MES_MATERIAL_CALLBACK_FAILED", null, request, req, STATUS_FAILED,
                    "MES 请求流水缺少提单行ID");
            throw callbackError(HttpStatus.INTERNAL_SERVER_ERROR, "MES 请求流水缺少提单行ID");
        }
        OrderDeliveryTicket ticket = loadTicket(ticketId);
        if (!mesRequestId.equals(trimToNull(ticket.getMesRequestId()))) {
            updateMesRequestFromCallback(mesRequestId, "STALE", null, externalTraceId, "旧 MES 回调已拒绝", false);
            recordCallbackAudit("DELIVERY_MES_MATERIAL_CALLBACK_CONFLICT", ticket, request, req, STATUS_FAILED,
                    "旧 MES 回调不能覆盖当前请求");
            throw callbackError(HttpStatus.CONFLICT, "旧 MES 回调不能覆盖当前请求");
        }
        String materialNo = normalizeMaterialNo(req.getMaterialNo());
        if (!STATUS_SUCCESS.equalsIgnoreCase(trimToNull(req.getStatus()))) {
            markFailed(ticket, mesRequestId, isBlank(req.getMessage()) ? "MES 回调失败" : req.getMessage());
            updateMesRequestFromCallback(mesRequestId, STATUS_FAILED, null, externalTraceId, req.getMessage(), true);
            if (dedupService != null) {
                dedupService.completeMasterFailed(mesRequestId, req.getMessage());
            }
            recordCallbackAudit("DELIVERY_MES_MATERIAL_CALLBACK_FAILED", ticket, request, req, STATUS_FAILED, req.getMessage());
            return callbackResponse(false, "MES 回调失败", mesRequestId, null);
        }
        if (isBlank(materialNo)) {
            throw callbackError(HttpStatus.BAD_REQUEST, "MES 回调物料号不能为空");
        }
        if (STATUS_SUCCESS.equalsIgnoreCase(ticket.getMesSyncStatus()) && !isBlank(ticket.getMaterialNo())) {
            if (materialNo.equals(ticket.getMaterialNo().trim())) {
                SourceItemMaterialNoSyncService.MaterialNoSyncResult sourceSyncResult =
                        syncSourceItemMaterialNoForCallback(
                                ticket, materialNo, req.getMessage(), request, req);
                if (dedupService != null) {
                    dedupService.completeMasterSuccess(mesRequestId, materialNo, req.getMessage(), externalTraceId);
                }
                recordCallbackAudit(
                        "DELIVERY_MES_MATERIAL_CALLBACK_SUCCESS",
                        ticket,
                        request,
                        req,
                        STATUS_SUCCESS,
                        null,
                        sourceSyncResult);
                return callbackResponse(true, "MES 回调已处理", mesRequestId, materialNo);
            }
            recordCallbackAudit("DELIVERY_MES_MATERIAL_CALLBACK_CONFLICT", ticket, request, req, STATUS_FAILED,
                    "MES 回调物料号冲突");
            throw callbackError(HttpStatus.CONFLICT, "MES 回调物料号冲突，不能覆盖已确认物料号");
        }
        SourceItemMaterialNoSyncService.MaterialNoSyncResult sourceSyncResult =
                syncSourceItemMaterialNoForCallback(
                        ticket, materialNo, req.getMessage(), request, req);
        markCallbackSuccess(ticket, mesRequestId, materialNo, req.getMessage(), externalTraceId);
        updateMesRequestFromCallback(mesRequestId, STATUS_SUCCESS, materialNo, externalTraceId, req.getMessage(), true);
        if (dedupService != null) {
            dedupService.completeMasterSuccess(mesRequestId, materialNo, req.getMessage(), externalTraceId);
        }
        recordCallbackAudit(
                "DELIVERY_MES_MATERIAL_CALLBACK_SUCCESS",
                ticket,
                request,
                req,
                STATUS_SUCCESS,
                null,
                sourceSyncResult);
        log.info("MES material callback completed ticketId={} mesRequestId={} materialNo={} durationMs={}",
                ticketId, mesRequestId, materialNo, System.currentTimeMillis() - startedAt);
        return callbackResponse(true, "QPO已接收物料号回调", mesRequestId, materialNo);
    }

    @Transactional
    public MesMaterialCallbackResp handleMaterialBatchCallback(MesMaterialCallbackReq req) {
        long startedAt = System.currentTimeMillis();
        MaterialBatchCallbackEnvelope envelope = validateMaterialBatchCallbackRequest(req);
        if (mesIntegrationBatchMapper == null || mesMaterialBatchItemMapper == null || mesRequestMapper == null) {
            throw callbackError(HttpStatus.INTERNAL_SERVER_ERROR, "MES material batch callback service is unavailable");
        }
        MesIntegrationBatch batch = loadMaterialBatch(envelope.bizId());
        List<MesMaterialBatchItem> batchItems = loadMaterialBatchItems(batch);
        Map<String, MesMaterialBatchItem> itemByKey = indexBatchItems(batchItems);
        List<MaterialBatchCallbackApplication> applications = new ArrayList<>();
        Map<Long, String> projectedStatuses = projectedStatusMap(batchItems);
        int duplicateCount = 0;

        for (MaterialBatchCallbackRow row : envelope.rows()) {
            MesMaterialBatchItem batchItem = itemByKey.get(row.itemKey());
            if (batchItem == null) {
                throw callbackError(HttpStatus.CONFLICT,
                        "MES material callback row does not match current QPO batch item");
            }
            OrderDeliveryTicketMesRequest request = loadMesRequest(batchItem.getLineRequestId());
            OrderDeliveryTicket ticket = loadTicket(batchItem.getTicketId());
            validateMaterialBatchCallbackAssociation(batch, batchItem, request, ticket, row);
            BatchCallbackDisposition disposition = disposition(batchItem, row);
            if (disposition.duplicate()) {
                duplicateCount++;
                continue;
            }
            applications.add(new MaterialBatchCallbackApplication(row, batchItem, request, ticket));
            projectedStatuses.put(batchItem.getId(), row.success() ? STATUS_SUCCESS : STATUS_FAILED);
        }

        SourceSyncAccumulator sourceSync = new SourceSyncAccumulator();
        for (MaterialBatchCallbackApplication application : applications) {
            sourceSync.add(applyMaterialBatchCallbackRow(application));
        }
        if (!applications.isEmpty()) {
            updateMaterialBatchAfterCallback(batch, projectedStatuses);
        }
        log.info(
                "MES material batch callback accepted bizId={} receivedCount={} appliedCount={} duplicateCount={} "
                        + "sourceTargetCount={} sourceUpdatedCount={} durationMs={}",
                envelope.bizId(), envelope.rows().size(), applications.size(), duplicateCount,
                sourceSync.targetCount, sourceSync.updatedCount, System.currentTimeMillis() - startedAt);
        return batchCallbackResponse(
                true,
                0,
                "accepted",
                envelope.bizId(),
                envelope.rows().size(),
                applications.size(),
                duplicateCount,
                sourceSync);
    }

    private MaterialBatchCallbackEnvelope validateMaterialBatchCallbackRequest(MesMaterialCallbackReq req) {
        String bizId = trimToNull(req == null ? null : req.getMesRequestId());
        if (bizId == null) {
            throw callbackError(HttpStatus.BAD_REQUEST, "MES material callback bizId cannot be blank");
        }
        if (bizId.length() > 80) {
            throw callbackError(HttpStatus.BAD_REQUEST, "MES material callback bizId is too long");
        }
        List<MesMaterialCallbackReq.Item> items = req.getItems();
        if (items == null || items.isEmpty()) {
            throw callbackError(HttpStatus.BAD_REQUEST, "MES material callback items cannot be empty");
        }
        if (items.size() > MATERIAL_CALLBACK_MAX_ITEMS) {
            throw callbackError(HttpStatus.BAD_REQUEST, "MES material callback items exceed 100 rows");
        }
        List<MaterialBatchCallbackRow> rows = new ArrayList<>();
        Set<String> callbackKeys = new HashSet<>();
        for (int index = 0; index < items.size(); index++) {
            MesMaterialCallbackReq.Item item = items.get(index);
            if (item == null) {
                throw callbackError(HttpStatus.BAD_REQUEST, "MES material callback item cannot be null");
            }
            String taskNo = requireCallbackText(item.getTaskNo(), 120, "items[" + index + "].taskNo");
            String drawingNo = requireCallbackText(item.getDrawingNo(), 128, "items[" + index + "].drawingNo");
            String drawingVersion = optionalCallbackText(item.getDrawingVersion(), 64, "items[" + index + "].drawingVersion");
            String status = requireCallbackStatus(item.getStatus(), index);
            String productCode = optionalCallbackText(item.getProductCode(), 100, "items[" + index + "].productCode");
            String productName = optionalCallbackText(item.getProductName(), 255, "items[" + index + "].productName");
            String message = optionalCallbackText(item.getMessage(), 500, "items[" + index + "].message");
            if (STATUS_SUCCESS.equals(status) && isBlank(productCode)) {
                throw callbackError(HttpStatus.BAD_REQUEST, "items[" + index + "].productCode cannot be blank when status is SUCCESS");
            }
            if (STATUS_FAILED.equals(status) && !isBlank(productCode)) {
                throw callbackError(HttpStatus.BAD_REQUEST, "items[" + index + "].productCode must be blank when status is FAILED");
            }
            if (STATUS_FAILED.equals(status) && isBlank(message)) {
                throw callbackError(HttpStatus.BAD_REQUEST, "items[" + index + "].message cannot be blank when status is FAILED");
            }
            String itemKey = materialItemKey(drawingNo, drawingVersion);
            if (!callbackKeys.add(itemKey)) {
                throw callbackError(HttpStatus.BAD_REQUEST, "MES material callback contains duplicate drawing/version");
            }
            rows.add(new MaterialBatchCallbackRow(
                    index, taskNo, drawingNo, drawingVersion, status, productCode, productName, message, itemKey));
        }
        return new MaterialBatchCallbackEnvelope(bizId, List.copyOf(rows));
    }

    private String requireCallbackText(String value, int maxLength, String field) {
        String text = trimToNull(value);
        if (text == null) {
            throw callbackError(HttpStatus.BAD_REQUEST, field + " cannot be blank");
        }
        if (text.length() > maxLength) {
            throw callbackError(HttpStatus.BAD_REQUEST, field + " is too long");
        }
        return text;
    }

    private String optionalCallbackText(String value, int maxLength, String field) {
        String text = trimToNull(value);
        if (text != null && text.length() > maxLength) {
            throw callbackError(HttpStatus.BAD_REQUEST, field + " is too long");
        }
        return text;
    }

    private String requireCallbackStatus(String value, int index) {
        String status = trimToNull(value);
        if (status == null) {
            throw callbackError(HttpStatus.BAD_REQUEST, "items[" + index + "].status cannot be blank");
        }
        status = status.toUpperCase(Locale.ROOT);
        if (!STATUS_SUCCESS.equals(status) && !STATUS_FAILED.equals(status)) {
            throw callbackError(HttpStatus.BAD_REQUEST, "items[" + index + "].status must be SUCCESS or FAILED");
        }
        return status;
    }

    private MesIntegrationBatch loadMaterialBatch(String bizId) {
        QueryWrapper<MesIntegrationBatch> wrapper = new QueryWrapper<>();
        wrapper.eq("biz_id", bizId)
                .eq("batch_type", MesBatchStateService.BATCH_TYPE_MATERIAL);
        MesIntegrationBatch batch = mesIntegrationBatchMapper.selectOne(wrapper);
        if (batch == null) {
            throw callbackError(HttpStatus.NOT_FOUND, "MES material batch bizId does not exist");
        }
        return batch;
    }

    private List<MesMaterialBatchItem> loadMaterialBatchItems(MesIntegrationBatch batch) {
        QueryWrapper<MesMaterialBatchItem> wrapper = new QueryWrapper<>();
        wrapper.eq("batch_id", batch.getId()).orderByAsc("id");
        List<MesMaterialBatchItem> items = mesMaterialBatchItemMapper.selectList(wrapper);
        if (items == null || items.isEmpty()) {
            throw callbackError(HttpStatus.CONFLICT, "MES material batch has no QPO rows");
        }
        return items;
    }

    private Map<String, MesMaterialBatchItem> indexBatchItems(List<MesMaterialBatchItem> items) {
        Map<String, MesMaterialBatchItem> result = new LinkedHashMap<>();
        for (MesMaterialBatchItem item : items) {
            String key = trimToNull(item.getItemKey());
            if (key == null) {
                key = materialItemKey(item.getDrawingNo(), item.getDrawingVersion());
            }
            result.put(key, item);
        }
        return result;
    }

    private Map<Long, String> projectedStatusMap(List<MesMaterialBatchItem> items) {
        Map<Long, String> result = new LinkedHashMap<>();
        for (MesMaterialBatchItem item : items) {
            result.put(item.getId(), normalizeCallbackExistingStatus(item.getStatus()));
        }
        return result;
    }

    private void validateMaterialBatchCallbackAssociation(
            MesIntegrationBatch batch,
            MesMaterialBatchItem batchItem,
            OrderDeliveryTicketMesRequest request,
            OrderDeliveryTicket ticket,
            MaterialBatchCallbackRow row) {
        if (batchItem.getBatchId() == null || !batchItem.getBatchId().equals(batch.getId())) {
            throw callbackError(HttpStatus.CONFLICT, "MES material callback row belongs to another batch");
        }
        if (request.getBatchId() != null && !request.getBatchId().equals(batch.getId())) {
            throw callbackError(HttpStatus.CONFLICT, "MES material callback request belongs to another batch");
        }
        if (request.getBatchItemId() != null && !request.getBatchItemId().equals(batchItem.getId())) {
            throw callbackError(HttpStatus.CONFLICT, "MES material callback request belongs to another batch item");
        }
        if (request.getTicketId() == null || !request.getTicketId().equals(batchItem.getTicketId())) {
            throw callbackError(HttpStatus.CONFLICT, "MES material callback request ticket mismatch");
        }
        String currentLineRequestId = trimToNull(ticket.getMesRequestId());
        if (!batchItem.getLineRequestId().equals(currentLineRequestId)) {
            throw callbackError(HttpStatus.CONFLICT, "Stale MES material callback cannot overwrite the current request");
        }
        String savedTaskNo = firstNonBlank(batchItem.getMesTaskNo(), request.getMesExternalTaskId());
        if (!isBlank(savedTaskNo) && !savedTaskNo.equals(row.taskNo())) {
            throw callbackError(HttpStatus.CONFLICT, "MES material callback taskNo mismatch");
        }
    }

    private BatchCallbackDisposition disposition(MesMaterialBatchItem batchItem, MaterialBatchCallbackRow row) {
        String currentStatus = normalizeCallbackExistingStatus(batchItem.getStatus());
        String currentProductCode = normalizeMaterialNo(batchItem.getProductCode());
        if (STATUS_SUCCESS.equals(currentStatus)) {
            if (row.success() && row.productCode().equals(currentProductCode)) {
                return BatchCallbackDisposition.duplicateResult();
            }
            throw callbackError(HttpStatus.CONFLICT, "MES material callback productCode conflicts with confirmed result");
        }
        if (STATUS_FAILED.equals(currentStatus)) {
            if (!row.success()) {
                return BatchCallbackDisposition.duplicateResult();
            }
            throw callbackError(HttpStatus.CONFLICT, "MES material callback cannot overwrite a failed terminal result");
        }
        if (row.success() && !isBlank(currentProductCode) && !row.productCode().equals(currentProductCode)) {
            throw callbackError(HttpStatus.CONFLICT, "MES material callback productCode conflicts with existing row value");
        }
        return BatchCallbackDisposition.applyResult();
    }

    private String normalizeCallbackExistingStatus(String status) {
        String normalized = trimToNull(status);
        return normalized == null ? STATUS_PROCESSING : normalized.toUpperCase(Locale.ROOT);
    }

    private SourceItemMaterialNoSyncService.MaterialNoSyncResult applyMaterialBatchCallbackRow(
            MaterialBatchCallbackApplication application) {
        MaterialBatchCallbackRow row = application.row();
        MesMaterialCallbackReq adapter = legacyCallbackAdapter(application.batchItem().getLineRequestId(), row);
        if (row.success()) {
            SourceItemMaterialNoSyncService.MaterialNoSyncResult sourceSyncResult = syncSourceItemMaterialNoForCallback(
                    application.ticket(), row.productCode(), row.message(), application.request(), adapter);
            markCallbackSuccess(
                    application.ticket(), application.batchItem().getLineRequestId(), row.productCode(), row.message(), row.taskNo());
            updateMesRequestFromCallback(
                    application.batchItem().getLineRequestId(), STATUS_SUCCESS, row.productCode(), row.taskNo(), row.message(), true);
            updateMaterialBatchItemFromCallback(application.batchItem(), row, STATUS_SUCCESS, true);
            if (dedupService != null) {
                dedupService.completeMasterSuccess(
                        application.batchItem().getLineRequestId(), row.productCode(), row.message(), row.taskNo());
            }
            recordCallbackAudit(
                    "DELIVERY_MES_MATERIAL_CALLBACK_SUCCESS",
                    application.ticket(),
                    application.request(),
                    adapter,
                    STATUS_SUCCESS,
                    null,
                    sourceSyncResult);
            return sourceSyncResult;
        }
        String failedMessage = isBlank(row.message()) ? "MES material callback failed" : row.message();
        markFailed(application.ticket(), application.batchItem().getLineRequestId(), failedMessage);
        updateMesRequestFromCallback(
                application.batchItem().getLineRequestId(), STATUS_FAILED, null, row.taskNo(), failedMessage, true);
        updateMaterialBatchItemFromCallback(application.batchItem(), row, STATUS_FAILED, true);
        if (dedupService != null) {
            dedupService.completeMasterFailed(application.batchItem().getLineRequestId(), failedMessage);
        }
        recordCallbackAudit(
                "DELIVERY_MES_MATERIAL_CALLBACK_FAILED",
                application.ticket(),
                application.request(),
                adapter,
                STATUS_FAILED,
                failedMessage);
        return SourceItemMaterialNoSyncService.MaterialNoSyncResult.empty();
    }

    private MesMaterialCallbackReq legacyCallbackAdapter(String lineRequestId, MaterialBatchCallbackRow row) {
        MesMaterialCallbackReq adapter = new MesMaterialCallbackReq();
        adapter.setMesRequestId(lineRequestId);
        adapter.setExternalTraceId(row.taskNo());
        adapter.setStatus(row.status());
        adapter.setMaterialNo(row.productCode());
        adapter.setMessage(row.message());
        return adapter;
    }

    private void updateMaterialBatchItemFromCallback(
            MesMaterialBatchItem source,
            MaterialBatchCallbackRow row,
            String status,
            boolean completed) {
        MesMaterialBatchItem update = new MesMaterialBatchItem();
        update.setId(source.getId());
        update.setStatus(status);
        update.setMesTaskNo(row.taskNo());
        update.setProductCode(row.success() ? row.productCode() : null);
        update.setProductName(row.success() ? row.productName() : null);
        update.setResponseStatus(row.status());
        update.setMessage(abbreviate(row.message()));
        update.setUpdatedAt(LocalDateTime.now());
        if (completed) {
            update.setCompletedAt(LocalDateTime.now());
        }
        mesMaterialBatchItemMapper.updateById(update);
    }

    private void updateMaterialBatchAfterCallback(MesIntegrationBatch batch, Map<Long, String> projectedStatuses) {
        String status = summarizeMaterialBatchStatus(projectedStatuses.values().stream().toList());
        MesIntegrationBatch update = new MesIntegrationBatch();
        update.setId(batch.getId());
        update.setStatus(status);
        update.setRespondedAt(LocalDateTime.now());
        update.setResponseCode("0");
        update.setResponseMessage("MES material callback accepted");
        update.setUpdatedAt(LocalDateTime.now());
        if (STATUS_SUCCESS.equals(status) || STATUS_FAILED.equals(status)) {
            update.setCompletedAt(LocalDateTime.now());
        }
        mesIntegrationBatchMapper.updateById(update);
    }

    private String summarizeMaterialBatchStatus(List<String> statuses) {
        boolean hasPending = false;
        boolean hasFailed = false;
        boolean allSuccess = !statuses.isEmpty();
        for (String status : statuses) {
            String normalized = normalizeCallbackExistingStatus(status);
            if (!STATUS_SUCCESS.equals(normalized)) {
                allSuccess = false;
            }
            if (STATUS_FAILED.equals(normalized)) {
                hasFailed = true;
            }
            if (!STATUS_SUCCESS.equals(normalized) && !STATUS_FAILED.equals(normalized)) {
                hasPending = true;
            }
        }
        if (allSuccess) {
            return STATUS_SUCCESS;
        }
        if (hasPending) {
            return BATCH_STATUS_PARTIAL_PENDING;
        }
        return hasFailed ? STATUS_FAILED : STATUS_RESULT_UNKNOWN;
    }

    private MesMaterialCallbackResp batchCallbackResponse(
            boolean success,
            int code,
            String msg,
            String bizId,
            int receivedCount,
            int appliedCount,
            int duplicateCount,
            SourceSyncAccumulator sourceSync) {
        MesMaterialCallbackResp resp = new MesMaterialCallbackResp();
        resp.setSuccess(success);
        resp.setCode(code);
        resp.setMsg(msg);
        resp.setMesRequestId(bizId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bizId", bizId);
        data.put("receivedCount", receivedCount);
        data.put("appliedCount", appliedCount);
        data.put("duplicateCount", duplicateCount);
        data.put("sourceSyncTargetCount", sourceSync == null ? 0 : sourceSync.targetCount);
        data.put("sourceSyncUpdatedCount", sourceSync == null ? 0 : sourceSync.updatedCount);
        data.put("sourceSyncIdempotentCount", sourceSync == null ? 0 : sourceSync.idempotentCount);
        resp.setData(data);
        return resp;
    }

    public Map<String, Object> syncMaterialNo(Integer ticketId, Long taskId) {
        long startedAt = System.currentTimeMillis();
        OrderDeliveryTicket ticket = loadTicket(ticketId);
        assertCanStart(ticket);
        MesMaterialDedupService.DedupResolveResult dedupResult = dedupService == null
                ? null
                : dedupService.resolveBeforeMes(ticket, taskId);
        if (dedupResult != null && dedupResult.isFollowExistingActive()) {
            return completeDedupFollow(ticket, taskId, dedupResult, startedAt);
        }
        String mesRequestId = generateMesRequestId(ticket.getId());
        log.info("MES material sync started ticketId={} taskId={} rfqId={} partNo={}",
                ticket.getId(), taskId, ticket.getRfqId(), ticket.getPartNo());
        try {
            insertMesRequest(ticket, taskId, mesRequestId, dedupResult);
        } catch (RuntimeException error) {
            if (isDedupUniqueConflict(error, dedupResult)) {
                MesMaterialDedupService.DedupResolveResult followResult =
                        dedupService.followActiveMaster(ticket, taskId, dedupResult.getDedupKey());
                return completeDedupFollow(ticket, taskId, followResult, startedAt);
            }
            throw error;
        }
        if (dedupResult != null) {
            log.info("MES material dedup master created ticketId={} mesRequestId={} dedupKey={}",
                    ticket.getId(), mesRequestId, dedupResult.getDedupKey());
        }
        markProcessing(ticket, mesRequestId, dedupResult);
        try {
            MesMaterialResolveReq req = buildRequest(ticket, mesRequestId);
            MesMaterialResolveResp resp = mesMaterialClient.resolveMaterial(req);
            validateResponseSuccess(resp, mesRequestId);
            String materialNo = normalizeMaterialNo(resp);
            if (!isBlank(materialNo)) {
                SourceItemMaterialNoSyncService.MaterialNoSyncResult sourceSyncResult = markSuccess(
                        ticket,
                        mesRequestId,
                        materialNo,
                        resp == null ? null : resp.getMessage(),
                        resp == null ? null : resp.getExternalTraceId());
                updateMesRequest(mesRequestId, STATUS_SUCCESS, materialNo, resp == null ? null : resp.getExternalTraceId(), resp == null ? null : resp.getMessage(), true);
                if (dedupService != null) {
                    dedupService.completeMasterSuccess(
                            mesRequestId,
                            materialNo,
                            resp == null ? null : resp.getMessage(),
                            resp == null ? null : resp.getExternalTraceId()
                    );
                }
                Map<String, Object> summary = summary(ticket, taskId, mesRequestId, materialNo, STATUS_SUCCESS, resp == null ? null : resp.getExternalTraceId());
                appendSourceSyncSummary(summary, sourceSyncResult);
                recordAudit(
                        "DELIVERY_MES_MATERIAL_SYNC_SUCCESS",
                        ticket,
                        taskId,
                        mesRequestId,
                        materialNo,
                        STATUS_SUCCESS,
                        resp == null ? null : resp.getExternalTraceId(),
                        null,
                        sourceSyncResult
                );
                log.info("MES material sync completed ticketId={} taskId={} rfqId={} partNo={} materialNo={} durationMs={}",
                        ticket.getId(), taskId, ticket.getRfqId(), ticket.getPartNo(), materialNo,
                        System.currentTimeMillis() - startedAt);
                return summary;
            }
            if (isCallbackPendingResponse(resp)) {
                markCallbackPending(ticket, mesRequestId, resp.getExternalTraceId(), resp.getMessage());
                updateMesRequest(mesRequestId, STATUS_CALLBACK_PENDING, null, resp.getExternalTraceId(), resp.getMessage(), false);
                if (dedupService != null) {
                    dedupService.markMasterCallbackPending(mesRequestId, resp.getExternalTraceId(), resp.getMessage());
                }
                Map<String, Object> summary = summary(ticket, taskId, mesRequestId, null, STATUS_CALLBACK_PENDING, resp.getExternalTraceId());
                recordAudit(
                        "DELIVERY_MES_MATERIAL_SYNC_ACCEPTED",
                        ticket,
                        taskId,
                        mesRequestId,
                        null,
                        STATUS_CALLBACK_PENDING,
                        resp.getExternalTraceId(),
                        null
                );
                log.info("MES material sync accepted ticketId={} taskId={} mesRequestId={} externalTraceId={} durationMs={}",
                        ticket.getId(), taskId, mesRequestId, resp.getExternalTraceId(), System.currentTimeMillis() - startedAt);
                return summary;
            }
            throw new RuntimeException(isBlank(resp == null ? null : resp.getMessage()) ? "MES 未返回物料号" : resp.getMessage());
        } catch (RuntimeException error) {
            markFailed(ticket, mesRequestId, error.getMessage());
            updateMesRequest(mesRequestId, STATUS_FAILED, null, null, error.getMessage(), true);
            if (dedupService != null) {
                dedupService.completeMasterFailed(mesRequestId, error.getMessage());
            }
            recordAudit("DELIVERY_MES_MATERIAL_SYNC_FAILED", ticket, taskId, mesRequestId, null, STATUS_FAILED, null, error.getMessage());
            log.error("MES material sync failed ticketId={} taskId={} rfqId={} partNo={} durationMs={}",
                    ticket.getId(), taskId, ticket.getRfqId(), ticket.getPartNo(),
                    System.currentTimeMillis() - startedAt, error);
            throw error;
        }
    }

    public List<Map<String, Object>> syncMaterialBatch(List<OrderDeliveryTicket> tickets, Long taskId) {
        if (tickets == null || tickets.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, List<MaterialBatchPlan>> groups = new LinkedHashMap<>();
        for (OrderDeliveryTicket ticket : tickets) {
            try {
                OrderDeliveryTicket current = ticket == null ? null : loadTicket(ticket.getId());
                assertCanStart(current);
                MaterialBatchPlan plan = buildBatchPlan(current);
                MesMaterialDedupService.DedupResolveResult dedupResult = dedupService == null
                        ? null
                        : dedupService.resolveBeforeMes(current, taskId);
                if (dedupResult != null && dedupResult.isFollowExistingActive()) {
                    rows.add(batchDedupFollowRow(current, taskId, dedupResult));
                    continue;
                }
                groups.computeIfAbsent(plan.groupKey(), ignored -> new ArrayList<>()).add(plan);
            } catch (RuntimeException error) {
                Integer ticketId = ticket == null ? null : ticket.getId();
                rows.add(batchFailureRow(ticket, taskId, null, error.getMessage(), false));
                log.warn("MES material batch row rejected ticketId={} taskId={} reason={}",
                        ticketId, taskId, error.getMessage());
            }
        }
        for (List<MaterialBatchPlan> group : groups.values()) {
            rows.addAll(syncMaterialPlanGroup(group, taskId));
        }
        return rows;
    }

    private Map<String, Object> batchDedupFollowRow(
            OrderDeliveryTicket ticket,
            Long taskId,
            MesMaterialDedupService.DedupResolveResult dedupResult) {
        Map<String, Object> result = summary(
                ticket,
                taskId,
                dedupResult.getMasterMesRequestId(),
                null,
                dedupResult.getStatus(),
                null);
        appendDedupSummary(result, dedupResult);
        result.put("submittedToMes", false);
        result.put("message", "Follow active MES material request for same part/version");
        return result;
    }

    private List<Map<String, Object>> syncMaterialPlanGroup(List<MaterialBatchPlan> group, Long taskId) {
        Map<String, MaterialBatchPlan> mastersByKey = new LinkedHashMap<>();
        Map<String, List<MaterialBatchPlan>> followersByKey = new LinkedHashMap<>();
        for (MaterialBatchPlan plan : group) {
            MaterialBatchPlan master = mastersByKey.putIfAbsent(plan.itemKey(), plan);
            if (master != null) {
                followersByKey.computeIfAbsent(plan.itemKey(), ignored -> new ArrayList<>()).add(plan);
            }
        }
        List<MaterialBatchPlan> masters = new ArrayList<>(mastersByKey.values());
        Map<Integer, Map<String, Object>> resultByTicketId = new LinkedHashMap<>();
        Map<Integer, PreparedMaterialPlan> preparedByTicketId = new LinkedHashMap<>();
        String bizId = newMaterialBizId();
        MesMaterialResolveReq req = null;
        try {
            MaterialBatchPlan first = masters.get(0);
            req = batchRequestAssembler.assemble(
                    bizId,
                    first.customerCode(),
                    first.customerName(),
                    first.internalProjectNo(),
                    masters.stream().map(MaterialBatchPlan::requestItem).toList());
            String requestFingerprint = batchFingerprintService.fingerprint(req);
            preparedByTicketId.putAll(reserveMaterialMasters(bizId, masters, taskId, requestFingerprint));
            MesMaterialResolveResp resp = mesMaterialClient.resolveMaterial(req);
            validateBatchResponseSuccess(resp, bizId);
            ResponseIndex responseIndex = indexMaterialResponse(resp, mastersByKey.keySet());
            for (MaterialBatchPlan master : masters) {
                PreparedMaterialPlan prepared = preparedByTicketId.get(master.ticket().getId());
                if (responseIndex.duplicateKeys().contains(master.itemKey())) {
                    resultByTicketId.put(master.ticket().getId(), withBizId(applyBatchFailure(
                            prepared, taskId, "MES returned duplicate rows for drawing/version", true), bizId));
                    applyFollowerFailures(master, followersByKey, resultByTicketId, taskId, bizId,
                            "MES returned duplicate rows for drawing/version");
                    continue;
                }
                MesMaterialResolveResp.Item item = responseIndex.itemsByKey().get(master.itemKey());
                if (item == null) {
                    resultByTicketId.put(master.ticket().getId(), withBizId(applyBatchFailure(
                            prepared, taskId, "MES response missing row for drawing/version", true), bizId));
                    applyFollowerFailures(master, followersByKey, resultByTicketId, taskId, bizId,
                            "MES response missing row for drawing/version");
                    continue;
                }
                Map<String, Object> masterResult = withBizId(applyBatchItem(prepared, item, taskId, true), bizId);
                resultByTicketId.put(master.ticket().getId(), masterResult);
                applyFollowerResult(prepared, followersByKey, item, resultByTicketId, taskId, bizId);
            }
        } catch (RuntimeException error) {
            String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            log.error("MES material batch group failed bizId={} taskId={} itemCount={} reason={}",
                    bizId, taskId, group.size(), message, error);
            for (MaterialBatchPlan plan : group) {
                PreparedMaterialPlan prepared = preparedByTicketId.get(plan.ticket().getId());
                resultByTicketId.put(plan.ticket().getId(), withBizId(prepared == null
                        ? batchFailureRow(plan.ticket(), taskId, null, message, true)
                        : applyBatchFailure(prepared, taskId, message, true), bizId));
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (MaterialBatchPlan plan : group) {
            rows.add(withBizId(resultByTicketId.getOrDefault(plan.ticket().getId(),
                    batchFailureRow(plan.ticket(), taskId, null, "MES batch result was not produced", true)), bizId));
        }
        return rows;
    }

    private MaterialBatchPlan buildBatchPlan(OrderDeliveryTicket ticket) {
        String internalProjectNo = required(ticket.getInternalProjectNo(), "Internal project number cannot be blank");
        OrderRfq order = orderRfqMapper == null || ticket.getRfqId() == null ? null : orderRfqMapper.selectById(ticket.getRfqId());
        String customerCode = resolveCustomerCode(ticket, order);
        String customerName = order == null ? ticket.getCustomerName() : order.getCustomerName();
        String drawingNo = required(ticket.getPartNo(), "Drawing number cannot be blank");
        String drawingVersion = ticket.getVersion() == null ? "" : ticket.getVersion().trim();
        String drawingUrl = resolveDrawingUrl(ticket);
        MesMaterialResolveReq.Item item = new MesMaterialResolveReq.Item();
        item.setDrawingNo(drawingNo.trim());
        item.setDrawingVersion(drawingVersion);
        item.setDrawingUrls(List.of(businessFileService.externalReadableUrl(required(drawingUrl, "Drawing URL cannot be blank"))));
        item.setRemark(trimToNull(ticket.getRemark()));
        item.setCustomerRequirements(MesCustomerRequirementsAssembler.assemble(
                ticket.getDescription(),
                ticket.getSurfaceTreatment()));
        String groupKey = normalizeKeyPart(customerCode) + "|" + normalizeKeyPart(customerName) + "|" + normalizeKeyPart(internalProjectNo);
        return new MaterialBatchPlan(
                ticket,
                trimToNull(customerCode),
                trimToNull(customerName),
                internalProjectNo.trim(),
                item,
                materialItemKey(drawingNo, drawingVersion),
                groupKey);
    }

    private Map<Integer, PreparedMaterialPlan> reserveMaterialMasters(
            String bizId,
            List<MaterialBatchPlan> masters,
            Long taskId,
            String requestFingerprint) {
        Map<Integer, PreparedMaterialPlan> prepared = new LinkedHashMap<>();
        if (mesBatchStateService != null) {
            MesBatchStateService.MaterialBatchReservation reservation = new MesBatchStateService.MaterialBatchReservation(
                    taskId,
                    null,
                    masters.get(0).groupKey(),
                    requestFingerprint,
                    masters.stream()
                            .map(plan -> new MesBatchStateService.MaterialItemReservation(
                                    plan.ticket().getId(),
                                    null,
                                    plan.requestItem().getDrawingNo(),
                                    plan.requestItem().getDrawingVersion(),
                                    plan.itemKey()))
                            .toList());
            MesBatchStateService.MaterialBatchReservationResult result =
                    mesBatchStateService.reserveMaterialBatch(bizId, reservation);
            Map<Integer, MesMaterialBatchItem> itemByTicketId = new HashMap<>();
            for (MesMaterialBatchItem item : result.items()) {
                itemByTicketId.put(item.getTicketId(), item);
            }
            MesIntegrationBatch batch = result.batch();
            for (MaterialBatchPlan plan : masters) {
                MesMaterialBatchItem item = itemByTicketId.get(plan.ticket().getId());
                if (item == null) {
                    throw new RuntimeException("MES batch reservation did not return item for ticket " + plan.ticket().getId());
                }
                OrderDeliveryTicketMesRequest request = insertBatchMesRequest(
                        plan.ticket(), taskId, item.getLineRequestId(), plan.itemKey(), batch.getId(), item.getId());
                linkBatchItemToRequest(item, request);
                prepared.put(plan.ticket().getId(), new PreparedMaterialPlan(plan, item.getLineRequestId(), item.getId(), batch.getId()));
            }
            return prepared;
        }

        for (MaterialBatchPlan plan : masters) {
            String lineRequestId = newMaterialLineRequestId();
            markProcessing(plan.ticket(), lineRequestId, MesMaterialDedupService.DedupResolveResult.master(plan.itemKey()));
            insertBatchMesRequest(plan.ticket(), taskId, lineRequestId, plan.itemKey(), null, null);
            prepared.put(plan.ticket().getId(), new PreparedMaterialPlan(plan, lineRequestId, null, null));
        }
        return prepared;
    }

    private Map<String, Object> applyBatchItem(
            PreparedMaterialPlan prepared,
            MesMaterialResolveResp.Item item,
            Long taskId,
            boolean submittedToMes) {
        String materialNo = normalizeMaterialNo(item == null ? null : item.getProductCode());
        String externalTraceId = responseItemTrace(item);
        String message = "MES material resolved";
        if (!isBlank(materialNo)) {
            SourceItemMaterialNoSyncService.MaterialNoSyncResult sourceSyncResult = markSuccess(
                    prepared.plan().ticket(),
                    prepared.lineRequestId(),
                    materialNo,
                    message,
                    externalTraceId);
            updateMesRequest(prepared.lineRequestId(), STATUS_SUCCESS, materialNo, externalTraceId, message, true);
            updateBatchItem(prepared, STATUS_SUCCESS, item, materialNo, message, true);
            if (dedupService != null) {
                dedupService.completeMasterSuccess(prepared.lineRequestId(), materialNo, message, externalTraceId);
            }
            Map<String, Object> result = summary(prepared.plan().ticket(), taskId, prepared.lineRequestId(), materialNo, STATUS_SUCCESS, externalTraceId);
            appendSourceSyncSummary(result, sourceSyncResult);
            result.put("submittedToMes", submittedToMes);
            result.put("message", message);
            recordAudit("DELIVERY_MES_MATERIAL_SYNC_SUCCESS", prepared.plan().ticket(), taskId,
                    prepared.lineRequestId(), materialNo, STATUS_SUCCESS, externalTraceId, null, sourceSyncResult);
            return result;
        }
        if (isPendingMaterialItem(item)) {
            String pendingMessage = "MES accepted, waiting for material callback";
            markCallbackPending(prepared.plan().ticket(), prepared.lineRequestId(), externalTraceId, pendingMessage);
            updateMesRequest(prepared.lineRequestId(), STATUS_CALLBACK_PENDING, null, externalTraceId, pendingMessage, false);
            updateBatchItem(prepared, STATUS_CALLBACK_PENDING, item, null, pendingMessage, false);
            if (dedupService != null) {
                dedupService.markMasterCallbackPending(prepared.lineRequestId(), externalTraceId, pendingMessage);
            }
            Map<String, Object> result = summary(prepared.plan().ticket(), taskId, prepared.lineRequestId(), null, STATUS_CALLBACK_PENDING, externalTraceId);
            result.put("submittedToMes", submittedToMes);
            result.put("message", pendingMessage);
            recordAudit("DELIVERY_MES_MATERIAL_SYNC_ACCEPTED", prepared.plan().ticket(), taskId,
                    prepared.lineRequestId(), null, STATUS_CALLBACK_PENDING, externalTraceId, null);
            return result;
        }
        return applyBatchFailure(prepared, taskId, "MES returned no material number", submittedToMes);
    }

    private void applyFollowerResult(
            PreparedMaterialPlan master,
            Map<String, List<MaterialBatchPlan>> followersByKey,
            MesMaterialResolveResp.Item item,
            Map<Integer, Map<String, Object>> resultByTicketId,
            Long taskId,
            String bizId) {
        List<MaterialBatchPlan> followers = followersByKey.getOrDefault(master.plan().itemKey(), List.of());
        for (MaterialBatchPlan follower : followers) {
            if (dedupService != null && isPendingMaterialItem(item)) {
                MesMaterialDedupService.DedupResolveResult dedupResult = dedupService.registerBatchFollower(
                        follower.ticket(),
                        taskId,
                        follower.itemKey(),
                        master.plan().ticket().getId(),
                        master.lineRequestId(),
                        STATUS_CALLBACK_PENDING);
                Map<String, Object> result = summary(
                        follower.ticket(),
                        taskId,
                        master.lineRequestId(),
                        null,
                        dedupResult.getStatus(),
                        responseItemTrace(item));
                appendDedupSummary(result, dedupResult);
                result.put("submittedToMes", false);
                result.put("message", "MES accepted, waiting for material callback");
                resultByTicketId.put(follower.ticket().getId(), withBizId(result, bizId));
                continue;
            }
            String followerLineId = newMaterialLineRequestId();
            PreparedMaterialPlan prepared = new PreparedMaterialPlan(follower, followerLineId, null, null);
            Map<String, Object> result = applyBatchItem(prepared, item, taskId, false);
            result.put("dedupResult", MesMaterialDedupService.RESULT_FOLLOW_EXISTING_ACTIVE);
            result.put("dedupKey", follower.itemKey());
            resultByTicketId.put(follower.ticket().getId(), withBizId(result, bizId));
        }
    }

    private void applyFollowerFailures(
            MaterialBatchPlan master,
            Map<String, List<MaterialBatchPlan>> followersByKey,
            Map<Integer, Map<String, Object>> resultByTicketId,
            Long taskId,
            String bizId,
            String message) {
        for (MaterialBatchPlan follower : followersByKey.getOrDefault(master.itemKey(), List.of())) {
            resultByTicketId.put(follower.ticket().getId(),
                    withBizId(batchFailureRow(follower.ticket(), taskId, newMaterialLineRequestId(), message, false), bizId));
        }
    }

    private Map<String, Object> withBizId(Map<String, Object> result, String bizId) {
        if (result != null && !isBlank(bizId)) {
            result.put("bizId", bizId);
        }
        return result;
    }

    private Map<String, Object> applyBatchFailure(
            PreparedMaterialPlan prepared,
            Long taskId,
            String message,
            boolean submittedToMes) {
        markFailed(prepared.plan().ticket(), prepared.lineRequestId(), message);
        updateMesRequest(prepared.lineRequestId(), STATUS_FAILED, null, null, message, true);
        updateBatchItem(prepared, STATUS_FAILED, null, null, message, true);
        if (dedupService != null) {
            dedupService.completeMasterFailed(prepared.lineRequestId(), message);
        }
        Map<String, Object> result = batchFailureRow(prepared.plan().ticket(), taskId, prepared.lineRequestId(), message, submittedToMes);
        recordAudit("DELIVERY_MES_MATERIAL_SYNC_FAILED", prepared.plan().ticket(), taskId,
                prepared.lineRequestId(), null, STATUS_FAILED, null, message);
        return result;
    }

    private Map<String, Object> batchFailureRow(
            OrderDeliveryTicket ticket,
            Long taskId,
            String lineRequestId,
            String message,
            boolean submittedToMes) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ticketId", ticket == null ? null : ticket.getId());
        result.put("taskId", taskId);
        result.put("status", STATUS_FAILED);
        result.put("mesRequestId", lineRequestId);
        result.put("message", abbreviate(message));
        result.put("submittedToMes", submittedToMes);
        if (ticket != null) {
            result.put("rfqId", ticket.getRfqId());
            result.put("sourceType", ticket.getSourceType());
            result.put("sourceOrderId", ticket.getSourceOrderId());
            result.put("sourceItemId", ticket.getSourceItemId());
            result.put("partNo", ticket.getPartNo());
            result.put("version", ticket.getVersion());
            result.put("internalProjectNo", ticket.getInternalProjectNo());
        }
        return result;
    }

    private void validateBatchResponseSuccess(MesMaterialResolveResp resp, String bizId) {
        if (resp == null || !Boolean.TRUE.equals(resp.getSuccess())) {
            throw new RuntimeException(resp == null || isBlank(resp.getMessage()) ? "MES returned failure" : resp.getMessage());
        }
        if (isBlank(resp.getMesRequestId())) {
            throw new RuntimeException("MES material bizId cannot be blank");
        }
        if (!bizId.equals(resp.getMesRequestId().trim())) {
            throw new RuntimeException("MES material bizId mismatch");
        }
    }

    private ResponseIndex indexMaterialResponse(MesMaterialResolveResp resp, Set<String> expectedKeys) {
        Map<String, MesMaterialResolveResp.Item> itemsByKey = new LinkedHashMap<>();
        Set<String> duplicateKeys = new HashSet<>();
        List<MesMaterialResolveResp.Item> items = resp.getData() == null || resp.getData().getItems() == null
                ? List.of()
                : resp.getData().getItems();
        if (items.isEmpty() && expectedKeys.size() == 1 && (isCallbackPendingResponse(resp) || !isBlank(resp.getMaterialNo()))) {
            MesMaterialResolveResp.Item synthetic = new MesMaterialResolveResp.Item();
            synthetic.setStatus(resp.getStatus());
            synthetic.setProductCode(resp.getMaterialNo());
            synthetic.setTaskNo(resp.getExternalTraceId());
            items = List.of(synthetic);
        }
        for (MesMaterialResolveResp.Item item : items) {
            String key = materialItemKey(item == null ? null : item.getDrawingNo(), item == null ? null : item.getDrawingVersion());
            if (key.equals("|") && expectedKeys.size() == 1) {
                key = expectedKeys.iterator().next();
            }
            if (!expectedKeys.contains(key)) {
                continue;
            }
            if (itemsByKey.putIfAbsent(key, item) != null) {
                duplicateKeys.add(key);
            }
        }
        return new ResponseIndex(itemsByKey, duplicateKeys);
    }

    private boolean isPendingMaterialItem(MesMaterialResolveResp.Item item) {
        if (item == null || isBlank(item.getStatus())) {
            return false;
        }
        String status = item.getStatus().trim().toUpperCase(Locale.ROOT);
        return STATUS_CALLBACK_PENDING.equals(status)
                || "PENDING_CREATE".equals(status)
                || "PENDING".equals(status)
                || "ACCEPTED".equals(status);
    }

    private String responseItemTrace(MesMaterialResolveResp.Item item) {
        if (item == null) {
            return null;
        }
        return firstNonBlank(item.getTaskNo(), item.getTaskId() == null ? null : String.valueOf(item.getTaskId()));
    }

    private OrderDeliveryTicketMesRequest insertBatchMesRequest(
            OrderDeliveryTicket ticket,
            Long taskId,
            String mesRequestId,
            String dedupKey,
            Long batchId,
            Long batchItemId) {
        if (mesRequestMapper == null) {
            return null;
        }
        OrderDeliveryTicketMesRequest request = new OrderDeliveryTicketMesRequest();
        request.setTicketId(ticket.getId());
        request.setMesRequestId(mesRequestId);
        request.setSystemTaskId(taskId);
        request.setBatchId(batchId);
        request.setBatchItemId(batchItemId);
        request.setRequestStatus(STATUS_PROCESSING);
        request.setDedupKey(trimToNull(dedupKey));
        request.setRequestRole(MesMaterialDedupService.ROLE_MASTER);
        request.setFollowerCount(0);
        request.setRequestMessage("MES material batch submitted");
        request.setRequestedAt(LocalDateTime.now());
        mesRequestMapper.insert(request);
        return request;
    }

    private void linkBatchItemToRequest(MesMaterialBatchItem item, OrderDeliveryTicketMesRequest request) {
        if (item == null || item.getId() == null || request == null || request.getId() == null || mesMaterialBatchItemMapper == null) {
            return;
        }
        MesMaterialBatchItem update = new MesMaterialBatchItem();
        update.setId(item.getId());
        update.setMesRequestRowId(request.getId());
        update.setUpdatedAt(LocalDateTime.now());
        mesMaterialBatchItemMapper.updateById(update);
    }

    private void updateBatchItem(
            PreparedMaterialPlan prepared,
            String status,
            MesMaterialResolveResp.Item responseItem,
            String materialNo,
            String message,
            boolean completed) {
        if (prepared == null || prepared.batchItemId() == null || mesMaterialBatchItemMapper == null) {
            return;
        }
        MesMaterialBatchItem update = new MesMaterialBatchItem();
        update.setId(prepared.batchItemId());
        update.setStatus(status);
        update.setProductCode(trimToNull(materialNo));
        update.setMesTaskNo(responseItem == null ? null : trimToNull(responseItem.getTaskNo()));
        update.setMesTaskId(responseItem == null || responseItem.getTaskId() == null ? null : String.valueOf(responseItem.getTaskId()));
        update.setProductId(responseItem == null || responseItem.getProductId() == null ? null : String.valueOf(responseItem.getProductId()));
        update.setProductName(responseItem == null ? null : trimToNull(responseItem.getProductName()));
        update.setResponseStatus(responseItem == null ? null : trimToNull(responseItem.getStatus()));
        update.setMessage(abbreviate(message));
        update.setUpdatedAt(LocalDateTime.now());
        if (completed) {
            update.setCompletedAt(LocalDateTime.now());
        }
        mesMaterialBatchItemMapper.updateById(update);
    }

    private String materialItemKey(String drawingNo, String drawingVersion) {
        return normalizeKeyPart(drawingNo) + "|" + normalizeKeyPart(drawingVersion);
    }

    private String normalizeKeyPart(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String newMaterialBizId() {
        return "QPO-MAT-" + LocalDate.now().format(BIZ_DATE) + "-" + ulid();
    }

    private String newMaterialLineRequestId() {
        return "QPO-MATL-" + ulid();
    }

    private String ulid() {
        byte[] bytes = new byte[16];
        long timestamp = System.currentTimeMillis();
        bytes[0] = (byte) (timestamp >>> 40);
        bytes[1] = (byte) (timestamp >>> 32);
        bytes[2] = (byte) (timestamp >>> 24);
        bytes[3] = (byte) (timestamp >>> 16);
        bytes[4] = (byte) (timestamp >>> 8);
        bytes[5] = (byte) timestamp;
        byte[] random = new byte[10];
        RANDOM.nextBytes(random);
        System.arraycopy(random, 0, bytes, 6, random.length);
        BigInteger value = new BigInteger(1, bytes);
        char[] encoded = new char[26];
        for (int i = encoded.length - 1; i >= 0; i--) {
            encoded[i] = CROCKFORD32[value.and(BigInteger.valueOf(31)).intValue()];
            value = value.shiftRight(5);
        }
        return new String(encoded);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private record MaterialBatchCallbackEnvelope(
            String bizId,
            List<MaterialBatchCallbackRow> rows) {
    }

    private record MaterialBatchCallbackRow(
            int index,
            String taskNo,
            String drawingNo,
            String drawingVersion,
            String status,
            String productCode,
            String productName,
            String message,
            String itemKey) {
        private boolean success() {
            return STATUS_SUCCESS.equals(status);
        }
    }

    private record MaterialBatchCallbackApplication(
            MaterialBatchCallbackRow row,
            MesMaterialBatchItem batchItem,
            OrderDeliveryTicketMesRequest request,
            OrderDeliveryTicket ticket) {
    }

    private record BatchCallbackDisposition(boolean duplicate) {
        private static BatchCallbackDisposition applyResult() {
            return new BatchCallbackDisposition(false);
        }

        private static BatchCallbackDisposition duplicateResult() {
            return new BatchCallbackDisposition(true);
        }
    }

    private static class SourceSyncAccumulator {
        private int targetCount;
        private int updatedCount;
        private int idempotentCount;

        private void add(SourceItemMaterialNoSyncService.MaterialNoSyncResult result) {
            if (result == null) {
                return;
            }
            targetCount += result.targetCount();
            updatedCount += result.updatedCount();
            idempotentCount += result.idempotentCount();
        }
    }

    private record MaterialBatchPlan(
            OrderDeliveryTicket ticket,
            String customerCode,
            String customerName,
            String internalProjectNo,
            MesMaterialResolveReq.Item requestItem,
            String itemKey,
            String groupKey) {
    }

    private record PreparedMaterialPlan(
            MaterialBatchPlan plan,
            String lineRequestId,
            Long batchItemId,
            Long batchId) {
    }

    private record ResponseIndex(
            Map<String, MesMaterialResolveResp.Item> itemsByKey,
            Set<String> duplicateKeys) {
    }

    private OrderDeliveryTicket loadTicket(Integer ticketId) {
        if (ticketId == null) {
            throw new RuntimeException("提单行 ID 不能为空");
        }
        OrderDeliveryTicket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new RuntimeException("提单行不存在");
        }
        return ticket;
    }

    private void assertCanStart(OrderDeliveryTicket ticket) {
        String status = ticket.getMesSyncStatus();
        if (STATUS_SUCCESS.equalsIgnoreCase(status) && !isBlank(ticket.getMaterialNo())) {
            throw new RuntimeException("MES material sync already completed");
        }
        if (STATUS_PROCESSING.equalsIgnoreCase(status)) {
            throw new RuntimeException("MES material sync is already processing");
        }
        if (STATUS_CALLBACK_PENDING.equalsIgnoreCase(status)) {
            throw new RuntimeException("MES material sync is waiting for callback");
        }
    }

    private Map<String, Object> completeDedupFollow(
            OrderDeliveryTicket ticket,
            Long taskId,
            MesMaterialDedupService.DedupResolveResult dedupResult,
            long startedAt) {
        Map<String, Object> result = summary(
                ticket,
                taskId,
                dedupResult.getMasterMesRequestId(),
                null,
                dedupResult.getStatus(),
                null
        );
        appendDedupSummary(result, dedupResult);
        log.info("MES material dedup follow ticketId={} masterTicketId={} masterMesRequestId={} dedupKey={} durationMs={}",
                ticket.getId(), dedupResult.getMasterTicketId(), dedupResult.getMasterMesRequestId(),
                dedupResult.getDedupKey(), System.currentTimeMillis() - startedAt);
        return result;
    }

    private MesMaterialResolveReq buildRequest(OrderDeliveryTicket ticket, String mesRequestId) {
        MesMaterialResolveReq req = new MesMaterialResolveReq();
        req.setMesRequestId(mesRequestId);
        req.setInternalProjectNo(required(ticket.getInternalProjectNo(), "内部项目号不能为空"));
        OrderRfq order = orderRfqMapper == null || ticket.getRfqId() == null ? null : orderRfqMapper.selectById(ticket.getRfqId());
        req.setCustomerCode(resolveCustomerCode(ticket, order));
        req.setCustomerName(order == null ? ticket.getCustomerName() : order.getCustomerName());
        String drawingUrl = resolveDrawingUrl(ticket);
        MesMaterialResolveReq.Item item = new MesMaterialResolveReq.Item();
        item.setDrawingNo(required(ticket.getPartNo(), "图号不能为空"));
        item.setDrawingVersion(ticket.getVersion() == null ? "" : ticket.getVersion());
        item.setDrawingUrls(List.of(businessFileService.externalReadableUrl(required(drawingUrl, "图纸不能为空"))));
        item.setRemark(trimToNull(ticket.getRemark()));
        item.setCustomerRequirements(MesCustomerRequirementsAssembler.assemble(
                ticket.getDescription(),
                ticket.getSurfaceTreatment()));
        req.setItems(List.of(item));
        return req;
    }

    private String resolveCustomerCode(OrderDeliveryTicket ticket, OrderRfq order) {
        if (ReleaseSourceType.SALES_ORDER.name().equals(resolveSourceType(ticket))
                && salesOrderHeaderMapper != null
                && ticket.getSourceOrderId() != null) {
            SalesOrderHeader header = salesOrderHeaderMapper.selectById(ticket.getSourceOrderId());
            if (header != null && !isBlank(header.getCustomerCode())) {
                return header.getCustomerCode().trim();
            }
        }
        if (order != null && !isBlank(order.getCustomerCode())) {
            return order.getCustomerCode().trim();
        }
        return null;
    }

    private void validateResponseSuccess(MesMaterialResolveResp resp, String mesRequestId) {
        if (resp == null || !Boolean.TRUE.equals(resp.getSuccess())) {
            throw new RuntimeException(resp == null || isBlank(resp.getMessage()) ? "MES 返回失败" : resp.getMessage());
        }
        if (isBlank(resp.getMesRequestId())) {
            throw new RuntimeException("MES 请求ID不能为空");
        }
        if (!mesRequestId.equals(resp.getMesRequestId().trim())) {
            throw new RuntimeException("MES 请求ID不匹配");
        }
    }

    private String normalizeMaterialNo(MesMaterialResolveResp resp) {
        return normalizeMaterialNo(resp == null ? null : resp.getMaterialNo());
    }

    private String normalizeMaterialNo(String value) {
        String materialNo = value == null ? "" : value.trim();
        if (materialNo.isEmpty()) {
            return "";
        }
        if (materialNo.length() > 100) {
            throw new RuntimeException("MES 物料号长度超过 100");
        }
        return materialNo;
    }

    private boolean isCallbackPendingResponse(MesMaterialResolveResp resp) {
        if (resp == null) {
            return false;
        }
        if (Boolean.TRUE.equals(resp.getCallbackRequired())) {
            return true;
        }
        String status = resp.getStatus();
        if (isBlank(status)) {
            return false;
        }
        String normalized = status.trim().toUpperCase();
        return STATUS_CALLBACK_PENDING.equals(normalized)
                || "PENDING_CREATE".equals(normalized)
                || "PENDING".equals(normalized)
                || "ACCEPTED".equals(normalized);
    }

    private void markProcessing(
            OrderDeliveryTicket ticket,
            String mesRequestId,
            MesMaterialDedupService.DedupResolveResult dedupResult) {
        OrderDeliveryTicket update = new OrderDeliveryTicket();
        update.setId(ticket.getId());
        update.setMesRequestId(mesRequestId);
        update.setMesSyncStatus(STATUS_PROCESSING);
        update.setMesSyncMessage("MES material sync processing");
        if (dedupResult != null) {
            update.setMesDedupKey(dedupResult.getDedupKey());
            update.setMesRequestRole(MesMaterialDedupService.ROLE_MASTER);
        }
        UpdateWrapper<OrderDeliveryTicket> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", ticket.getId())
                .and(status -> status
                        .isNull("mes_sync_status")
                        .or()
                        .notIn("mes_sync_status", STATUS_PROCESSING, STATUS_SUCCESS, STATUS_CALLBACK_PENDING));
        int updated = ticketMapper.update(update, wrapper);
        if (updated <= 0) {
            OrderDeliveryTicket current = loadTicket(ticket.getId());
            assertCanStart(current);
            throw new RuntimeException("MES material sync status changed before processing");
        }
    }

    private SourceItemMaterialNoSyncService.MaterialNoSyncResult markSuccess(
            OrderDeliveryTicket ticket,
            String mesRequestId,
            String materialNo,
            String message,
            String externalTraceId) {
        String syncMessage = isBlank(message) ? "MES material resolved" : message.trim();
        SourceItemMaterialNoSyncService.MaterialNoSyncResult sourceSyncResult =
                syncSourceItemMaterialNo(ticket, materialNo, syncMessage);
        OrderDeliveryTicket update = new OrderDeliveryTicket();
        update.setId(ticket.getId());
        update.setMaterialNo(materialNo);
        update.setMesSyncStatus(STATUS_SUCCESS);
        update.setMesSyncMessage(syncMessage);
        update.setMesSyncedAt(LocalDateTime.now());
        update.setMesRequestId(mesRequestId);
        update.setMesExternalTaskId(trimToNull(externalTraceId));
        ticketMapper.updateById(update);
        return sourceSyncResult;
    }

    private void markCallbackPending(OrderDeliveryTicket ticket, String mesRequestId, String externalTraceId, String message) {
        OrderDeliveryTicket update = new OrderDeliveryTicket();
        update.setId(ticket.getId());
        update.setMesSyncStatus(STATUS_CALLBACK_PENDING);
        update.setMesSyncMessage(isBlank(message) ? "MES accepted, waiting for callback" : message.trim());
        update.setMesRequestId(mesRequestId);
        update.setMesExternalTaskId(trimToNull(externalTraceId));
        ticketMapper.updateById(update);
    }

    private void markCallbackSuccess(OrderDeliveryTicket ticket, String mesRequestId, String materialNo, String message, String externalTraceId) {
        OrderDeliveryTicket update = new OrderDeliveryTicket();
        update.setId(ticket.getId());
        update.setMaterialNo(materialNo);
        update.setMesSyncStatus(STATUS_SUCCESS);
        update.setMesSyncMessage(isBlank(message) ? "MES material callback resolved" : message.trim());
        update.setMesSyncedAt(LocalDateTime.now());
        update.setMesRequestId(mesRequestId);
        update.setMesExternalTaskId(trimToNull(externalTraceId));
        update.setMesCallbackReceivedAt(LocalDateTime.now());
        update.setMesCallbackMessage(abbreviate(message));
        ticketMapper.updateById(update);
    }

    private SourceItemMaterialNoSyncService.MaterialNoSyncResult syncSourceItemMaterialNoForCallback(
            OrderDeliveryTicket ticket,
            String materialNo,
            String message,
            OrderDeliveryTicketMesRequest request,
            MesMaterialCallbackReq req) {
        try {
            return syncSourceItemMaterialNo(ticket, materialNo, message);
        } catch (SourceItemMaterialNoSyncService.MaterialNoConflictException error) {
            recordCallbackAudit(
                    "DELIVERY_MES_MATERIAL_CALLBACK_CONFLICT",
                    ticket,
                    request,
                    req,
                    STATUS_FAILED,
                    error.getMessage());
            throw callbackError(HttpStatus.CONFLICT, error.getMessage());
        }
    }

    private SourceItemMaterialNoSyncService.MaterialNoSyncResult syncSourceItemMaterialNo(
            OrderDeliveryTicket ticket,
            String materialNo,
            String message) {
        if (sourceItemMaterialNoSyncService == null) {
            return SourceItemMaterialNoSyncService.MaterialNoSyncResult.empty();
        }
        return sourceItemMaterialNoSyncService.syncConfirmedMaterialNo(ticket, materialNo, message);
    }

    private String resolveDrawingUrl(OrderDeliveryTicket ticket) {
        if (!isBlank(ticket.getDrawingUrl())) {
            return ticket.getDrawingUrl();
        }
        if (ReleaseSourceType.SALES_ORDER.name().equals(ticket.getSourceType())
                && salesOrderItemMapper != null
                && ticket.getSourceItemId() != null) {
            SalesOrderItem item = salesOrderItemMapper.selectById(ticket.getSourceItemId());
            if (item != null && !isBlank(item.getDrawingUrl())) {
                return item.getDrawingUrl();
            }
        }
        if (itemMapper != null && ticket.getItemId() != null) {
            ItemQuoteVersion item = itemMapper.selectById(ticket.getItemId());
            return item == null ? null : item.getDrawingUrl();
        }
        return null;
    }

    private void markFailed(OrderDeliveryTicket ticket, String mesRequestId, String message) {
        OrderDeliveryTicket update = new OrderDeliveryTicket();
        update.setId(ticket.getId());
        update.setMesRequestId(mesRequestId);
        update.setMesSyncStatus(STATUS_FAILED);
        update.setMesSyncMessage(abbreviate(isBlank(message) ? "MES material sync failed" : message));
        ticketMapper.updateById(update);
    }

    private OrderDeliveryTicketMesRequest loadMesRequest(String mesRequestId) {
        if (mesRequestMapper == null) {
            throw callbackError(HttpStatus.INTERNAL_SERVER_ERROR, "MES 请求流水服务不可用");
        }
        QueryWrapper<OrderDeliveryTicketMesRequest> wrapper = new QueryWrapper<>();
        wrapper.eq("mes_request_id", mesRequestId);
        OrderDeliveryTicketMesRequest request = mesRequestMapper.selectOne(wrapper);
        if (request == null) {
            throw callbackError(HttpStatus.NOT_FOUND, "MES 请求流水不存在");
        }
        return request;
    }

    private void updateMesRequestFromCallback(
            String mesRequestId,
            String status,
            String materialNo,
            String externalTraceId,
            String message,
            boolean completed) {
        if (mesRequestMapper == null || isBlank(mesRequestId)) {
            return;
        }
        OrderDeliveryTicketMesRequest update = new OrderDeliveryTicketMesRequest();
        update.setRequestStatus(status);
        update.setMaterialNo(trimToNull(materialNo));
        update.setMesExternalTaskId(trimToNull(externalTraceId));
        update.setCallbackMessage(abbreviate(message));
        update.setCallbackReceivedAt(LocalDateTime.now());
        update.setUpdatedAt(LocalDateTime.now());
        if (completed) {
            update.setCompletedAt(LocalDateTime.now());
        }
        UpdateWrapper<OrderDeliveryTicketMesRequest> wrapper = new UpdateWrapper<>();
        wrapper.eq("mes_request_id", mesRequestId);
        mesRequestMapper.update(update, wrapper);
    }

    private void recordAudit(
            String action,
            OrderDeliveryTicket ticket,
            Long taskId,
            String mesRequestId,
            String materialNo,
            String result,
            String externalTraceId,
            String errorMessage) {
        recordAudit(
                action,
                ticket,
                taskId,
                mesRequestId,
                materialNo,
                result,
                externalTraceId,
                errorMessage,
                SourceItemMaterialNoSyncService.MaterialNoSyncResult.empty());
    }

    private void recordAudit(
            String action,
            OrderDeliveryTicket ticket,
            Long taskId,
            String mesRequestId,
            String materialNo,
            String result,
            String externalTraceId,
            String errorMessage,
            SourceItemMaterialNoSyncService.MaterialNoSyncResult sourceSyncResult) {
        if (auditService == null) {
            return;
        }
        Map<String, Object> auditSummary = summary(
                ticket, taskId, mesRequestId, materialNo, result, externalTraceId);
        appendSourceSyncSummary(auditSummary, sourceSyncResult);
        auditService.record(
                action,
                "order_delivery_ticket",
                ticket.getId(),
                null,
                auditSummary,
                "DELIVERY",
                result,
                AuditService.labelForAction(action) + " ticketId=" + ticket.getId() + " taskId=" + taskId + " mesRequestId=" + mesRequestId,
                errorMessage
        );
    }

    private void recordCallbackAudit(
            String action,
            OrderDeliveryTicket ticket,
            OrderDeliveryTicketMesRequest request,
            MesMaterialCallbackReq req,
            String result,
            String errorMessage) {
        recordCallbackAudit(
                action,
                ticket,
                request,
                req,
                result,
                errorMessage,
                SourceItemMaterialNoSyncService.MaterialNoSyncResult.empty());
    }

    private void recordCallbackAudit(
            String action,
            OrderDeliveryTicket ticket,
            OrderDeliveryTicketMesRequest request,
            MesMaterialCallbackReq req,
            String result,
            String errorMessage,
            SourceItemMaterialNoSyncService.MaterialNoSyncResult sourceSyncResult) {
        if (auditService == null) {
            return;
        }
        Integer ticketId = ticket == null ? (request == null ? null : request.getTicketId()) : ticket.getId();
        String mesRequestId = req == null ? null : req.getMesRequestId();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("ticketId", ticketId);
        summary.put("mesRequestId", mesRequestId);
        summary.put("externalTraceId", req == null ? null : req.getExternalTraceId());
        summary.put("status", result);
        summary.put("materialNo", req == null ? null : req.getMaterialNo());
        if (ticket != null) {
            summary.put("sourceType", ticket.getSourceType());
            summary.put("sourceOrderId", ticket.getSourceOrderId());
            summary.put("sourceItemId", ticket.getSourceItemId());
            summary.put("partNo", ticket.getPartNo());
            summary.put("version", ticket.getVersion());
        }
        if (request != null) {
            summary.put("requestStatus", request.getRequestStatus());
        }
        appendSourceSyncSummary(summary, sourceSyncResult);
        auditService.record(
                action,
                "order_delivery_ticket",
                ticketId,
                null,
                summary,
                "DELIVERY",
                result,
                AuditService.labelForAction(action) + " ticketId=" + ticketId + " mesRequestId=" + mesRequestId,
                errorMessage
        );
    }

    private MesMaterialCallbackResp callbackResponse(
            boolean success,
            String message,
            String mesRequestId,
            String materialNo) {
        MesMaterialCallbackResp resp = new MesMaterialCallbackResp();
        resp.setSuccess(success);
        resp.setMsg(message);
        resp.setMesRequestId(mesRequestId);
        resp.setMaterialNo(trimToNull(materialNo));
        return resp;
    }

    private Map<String, Object> summary(OrderDeliveryTicket ticket, Long taskId, String mesRequestId, String materialNo, String status, String externalTraceId) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("ticketId", ticket.getId());
        summary.put("taskId", taskId);
        summary.put("status", status);
        summary.put("mesRequestId", mesRequestId);
        summary.put("rfqId", ticket.getRfqId());
        summary.put("sourceType", ticket.getSourceType());
        summary.put("sourceOrderId", ticket.getSourceOrderId());
        summary.put("sourceItemId", ticket.getSourceItemId());
        summary.put("partNo", ticket.getPartNo());
        summary.put("version", ticket.getVersion());
        summary.put("internalProjectNo", ticket.getInternalProjectNo());
        if (!isBlank(materialNo)) {
            summary.put("materialNo", materialNo);
        }
        if (!isBlank(externalTraceId)) {
            summary.put("externalTraceId", externalTraceId.trim());
        }
        return summary;
    }

    private void appendSourceSyncSummary(
            Map<String, Object> summary,
            SourceItemMaterialNoSyncService.MaterialNoSyncResult sourceSyncResult) {
        SourceItemMaterialNoSyncService.MaterialNoSyncResult result = sourceSyncResult == null
                ? SourceItemMaterialNoSyncService.MaterialNoSyncResult.empty()
                : sourceSyncResult;
        summary.put("sourceSyncTargetCount", result.targetCount());
        summary.put("sourceSyncUpdatedCount", result.updatedCount());
        summary.put("sourceSyncIdempotentCount", result.idempotentCount());
        summary.put("rfqSourceUpdatedCount", result.rfqUpdatedCount());
        summary.put("salesOrderSourceUpdatedCount", result.salesOrderUpdatedCount());
    }

    private void appendDedupSummary(
            Map<String, Object> summary,
            MesMaterialDedupService.DedupResolveResult dedupResult) {
        summary.put("dedupResult", dedupResult.getType());
        summary.put("dedupKey", dedupResult.getDedupKey());
        String role = MesMaterialDedupService.RESULT_MASTER_CREATED.equals(dedupResult.getType())
                ? MesMaterialDedupService.ROLE_MASTER
                : MesMaterialDedupService.ROLE_FOLLOWER;
        summary.put("mesRequestRole", role);
        if (dedupResult.getMasterTicketId() != null) {
            summary.put("masterTicketId", dedupResult.getMasterTicketId());
        }
        if (!isBlank(dedupResult.getMasterMesRequestId())) {
            summary.put("masterMesRequestId", dedupResult.getMasterMesRequestId());
        }
    }

    private boolean isDedupUniqueConflict(
            RuntimeException error,
            MesMaterialDedupService.DedupResolveResult dedupResult) {
        if (dedupService == null || dedupResult == null || isBlank(dedupResult.getDedupKey())) {
            return false;
        }
        Throwable current = error;
        while (current != null) {
            if (current instanceof DataIntegrityViolationException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.contains("uk_delivery_mes_request_active_dedup")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void insertMesRequest(
            OrderDeliveryTicket ticket,
            Long taskId,
            String mesRequestId,
            MesMaterialDedupService.DedupResolveResult dedupResult) {
        if (mesRequestMapper == null) {
            return;
        }
        OrderDeliveryTicketMesRequest request = new OrderDeliveryTicketMesRequest();
        request.setTicketId(ticket.getId());
        request.setMesRequestId(mesRequestId);
        request.setSystemTaskId(taskId);
        request.setRequestStatus(STATUS_PROCESSING);
        if (dedupResult != null) {
            request.setDedupKey(dedupResult.getDedupKey());
            request.setRequestRole(MesMaterialDedupService.ROLE_MASTER);
            request.setFollowerCount(0);
        }
        request.setRequestMessage("MES material sync submitted");
        request.setRequestedAt(LocalDateTime.now());
        mesRequestMapper.insert(request);
    }

    private void updateMesRequest(String mesRequestId, String status, String materialNo, String externalTraceId, String message, boolean completed) {
        if (mesRequestMapper == null || isBlank(mesRequestId)) {
            return;
        }
        OrderDeliveryTicketMesRequest update = new OrderDeliveryTicketMesRequest();
        update.setRequestStatus(status);
        update.setMaterialNo(trimToNull(materialNo));
        update.setMesExternalTaskId(trimToNull(externalTraceId));
        update.setResponseMessage(abbreviate(message));
        update.setRespondedAt(LocalDateTime.now());
        update.setUpdatedAt(LocalDateTime.now());
        if (completed) {
            update.setCompletedAt(LocalDateTime.now());
        }
        UpdateWrapper<OrderDeliveryTicketMesRequest> wrapper = new UpdateWrapper<>();
        wrapper.eq("mes_request_id", mesRequestId);
        mesRequestMapper.update(update, wrapper);
    }

    private String generateMesRequestId(Integer ticketId) {
        return "MES-" + (ticketId == null ? "0" : ticketId) + "-" + UUID.randomUUID();
    }

    private String resolveSourceType(OrderDeliveryTicket ticket) {
        return isBlank(ticket.getSourceType()) ? ReleaseSourceType.RFQ.name() : ticket.getSourceType().trim();
    }

    private MesMaterialCallbackException callbackError(HttpStatus status, String message) {
        return new MesMaterialCallbackException(status, message);
    }

    private String required(String value, String message) {
        if (isBlank(value)) {
            throw new RuntimeException(message);
        }
        return value.trim();
    }

    private String abbreviate(String value) {
        String text = value == null ? "" : value.trim();
        return text.length() <= 500 ? text : text.substring(0, 500);
    }

    private String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
