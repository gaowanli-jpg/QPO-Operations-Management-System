package com.machining.quote.service.mes;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.machining.quote.entity.MesIntegrationBatch;
import com.machining.quote.entity.MesMaterialBatchItem;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderDeliveryTicketMesRequest;
import com.machining.quote.mapper.MesIntegrationBatchMapper;
import com.machining.quote.mapper.MesMaterialBatchItemMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMesRequestMapper;
import com.machining.quote.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MES 物料回调超时兜底。
 *
 * <p>MES 明确：回调 status 只有 SUCCESS，失败不回调。也就是说一次请求如果最终没产出料号，
 * QPO 永远收不到任何回调，提单行会一直停在「待回调」——既不会成功，也不会失败，
 * 用户点重新获取又会被「有在途请求」挡住。</p>
 *
 * <p>所以必须自己兜底：超过阈值仍是待回调的请求转「结果待核对」。这里刻意<b>不</b>转 FAILED：
 * MES 可能已经把料建出来了，只是回调丢了；一旦记成可重试失败，用户重发就会造出第二个
 * 物料准备任务，MES 会以 1030900002/1030900003 拒绝，形成失败-重发-再失败的死循环。
 * RESULT_UNKNOWN 同时仍然占住身份去重位，符合「不换新 bizId 重发」的口径。</p>
 */
@Service
public class MesMaterialCallbackTimeoutService {
    private static final Logger log = LoggerFactory.getLogger(MesMaterialCallbackTimeoutService.class);

    static final String STATUS_CALLBACK_PENDING = "CALLBACK_PENDING";
    static final String STATUS_RESULT_UNKNOWN = "RESULT_UNKNOWN";
    static final String ACTION_TIMEOUT = "DELIVERY_MES_MATERIAL_CALLBACK_TIMEOUT";

    @Autowired(required = false)
    private OrderDeliveryTicketMesRequestMapper requestMapper;
    @Autowired(required = false)
    private OrderDeliveryTicketMapper ticketMapper;
    @Autowired(required = false)
    private MesMaterialBatchItemMapper batchItemMapper;
    @Autowired(required = false)
    private MesIntegrationBatchMapper batchMapper;
    @Autowired(required = false)
    private AuditService auditService;

    /** 超过该时长仍是待回调，就认为回调已经丢了。 */
    @Value("${erp.mes.material-callback-timeout.hours:4}")
    private long timeoutHours = 4;

    /** 单次最多收口多少个请求，避免一次扫描把库拖住。 */
    @Value("${erp.mes.material-callback-timeout.batch-size:200}")
    private int batchSize = 200;

    /** @return 本次收口的请求数。 */
    public int expireOverdueCallbacks() {
        if (requestMapper == null) {
            return 0;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusHours(Math.max(1, timeoutHours));
        List<OrderDeliveryTicketMesRequest> overdue = requestMapper.selectList(
                new QueryWrapper<OrderDeliveryTicketMesRequest>()
                        .eq("request_status", STATUS_CALLBACK_PENDING)
                        // responded_at 是 MES 受理时刻；updated_at/created_at 兜底，避免空值漏扫。
                        .apply("COALESCE(responded_at, updated_at, created_at) < {0}", cutoff)
                        .orderByAsc("id")
                        .last("LIMIT " + Math.max(1, batchSize)));
        if (overdue.isEmpty()) {
            return 0;
        }
        Set<Long> touchedBatchIds = new LinkedHashSet<>();
        int expired = 0;
        for (OrderDeliveryTicketMesRequest request : overdue) {
            try {
                if (expire(request, touchedBatchIds)) {
                    expired++;
                }
            } catch (RuntimeException error) {
                log.warn("MES material callback timeout failed mesRequestId={}",
                        request.getMesRequestId(), error);
            }
        }
        for (Long batchId : touchedBatchIds) {
            refreshBatchStatus(batchId);
        }
        if (expired > 0) {
            log.info("MES material callback timeout expired {} request(s)", expired);
        }
        return expired;
    }

    private boolean expire(OrderDeliveryTicketMesRequest request, Set<Long> touchedBatchIds) {
        String mesRequestId = request == null ? null : trimToNull(request.getMesRequestId());
        if (mesRequestId == null) {
            return false;
        }
        String message = timeoutMessage();
        // 先抢请求行：只有仍停在 CALLBACK_PENDING 才由本方法收口。
        // 回调若在同一时刻到达并写成 SUCCESS，这里会命中 0 行，直接放弃，不覆盖真结果。
        OrderDeliveryTicketMesRequest update = new OrderDeliveryTicketMesRequest();
        update.setRequestStatus(STATUS_RESULT_UNKNOWN);
        update.setResponseMessage(abbreviate(message));
        update.setUpdatedAt(LocalDateTime.now());
        UpdateWrapper<OrderDeliveryTicketMesRequest> wrapper = new UpdateWrapper<>();
        wrapper.eq("mes_request_id", mesRequestId)
                .eq("request_status", STATUS_CALLBACK_PENDING);
        if (requestMapper.update(update, wrapper) <= 0) {
            return false;
        }
        int ticketCount = expireTickets(mesRequestId, message);
        int itemCount = expireBatchItems(mesRequestId, message, touchedBatchIds);
        recordAudit(request, message, ticketCount, itemCount);
        log.warn("MES material callback timed out mesRequestId={} ticketCount={} itemCount={} timeoutHours={}",
                mesRequestId, ticketCount, itemCount, timeoutHours);
        return true;
    }

    private int expireTickets(String mesRequestId, String message) {
        if (ticketMapper == null) {
            return 0;
        }
        OrderDeliveryTicket update = new OrderDeliveryTicket();
        update.setMesSyncStatus(STATUS_RESULT_UNKNOWN);
        update.setMesSyncMessage(abbreviate(message));
        UpdateWrapper<OrderDeliveryTicket> wrapper = new UpdateWrapper<>();
        wrapper.eq("mes_request_id", mesRequestId)
                .eq("mes_sync_status", STATUS_CALLBACK_PENDING);
        return ticketMapper.update(update, wrapper);
    }

    private int expireBatchItems(String mesRequestId, String message, Set<Long> touchedBatchIds) {
        if (batchItemMapper == null) {
            return 0;
        }
        List<MesMaterialBatchItem> items = batchItemMapper.selectList(
                new QueryWrapper<MesMaterialBatchItem>()
                        .eq("line_request_id", mesRequestId)
                        .in("status", STATUS_CALLBACK_PENDING, "SUBMITTING", "PROCESSING"));
        List<Long> ids = new ArrayList<>();
        for (MesMaterialBatchItem item : items) {
            MesMaterialBatchItem update = new MesMaterialBatchItem();
            update.setId(item.getId());
            update.setStatus(STATUS_RESULT_UNKNOWN);
            update.setMessage(abbreviate(message));
            update.setUpdatedAt(LocalDateTime.now());
            batchItemMapper.updateById(update);
            ids.add(item.getId());
            if (item.getBatchId() != null) {
                touchedBatchIds.add(item.getBatchId());
            }
        }
        return ids.size();
    }

    /** 批次台账的汇总状态按行重算，否则页面会一直显示「部分待回调」。 */
    private void refreshBatchStatus(Long batchId) {
        if (batchMapper == null || batchItemMapper == null || batchId == null) {
            return;
        }
        List<MesMaterialBatchItem> items = batchItemMapper.selectList(
                new QueryWrapper<MesMaterialBatchItem>().eq("batch_id", batchId));
        String status = MesMaterialSyncService.summarizeMaterialBatchStatus(
                items.stream().map(MesMaterialBatchItem::getStatus).toList());
        MesIntegrationBatch update = new MesIntegrationBatch();
        update.setId(batchId);
        update.setStatus(status);
        update.setUpdatedAt(LocalDateTime.now());
        if ("SUCCESS".equals(status) || "FAILED".equals(status)) {
            update.setCompletedAt(LocalDateTime.now());
        }
        batchMapper.updateById(update);
    }

    private void recordAudit(
            OrderDeliveryTicketMesRequest request,
            String message,
            int ticketCount,
            int itemCount) {
        if (auditService == null || request == null) {
            return;
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("ticketId", request.getTicketId());
        summary.put("mesRequestId", request.getMesRequestId());
        summary.put("taskNo", request.getMesExternalTaskId());
        summary.put("requestStatus", STATUS_RESULT_UNKNOWN);
        summary.put("timeoutHours", timeoutHours);
        summary.put("expiredTicketCount", ticketCount);
        summary.put("expiredBatchItemCount", itemCount);
        auditService.record(
                ACTION_TIMEOUT,
                "order_delivery_ticket_mes_request",
                request.getId(),
                null,
                summary,
                "DELIVERY",
                STATUS_RESULT_UNKNOWN,
                AuditService.labelForAction(ACTION_TIMEOUT)
                        + " mesRequestId=" + request.getMesRequestId()
                        + " timeoutHours=" + timeoutHours,
                null
        );
    }

    private String timeoutMessage() {
        return "MES 超过 " + timeoutHours + " 小时未回调料号（MES 只在成功时回调），结果未知；"
                + "请按原 bizId 到 MES 核对该任务，不要直接重新获取";
    }

    private String abbreviate(String message) {
        if (message == null) {
            return null;
        }
        String trimmed = message.trim();
        return trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 500);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
