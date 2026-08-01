package com.machining.quote.service.mes;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.machining.quote.dto.ReleaseSourceType;
import com.machining.quote.dto.mes.MesMaterialCallbackReq;
import com.machining.quote.dto.mes.MesMaterialCallbackResp;
import com.machining.quote.dto.mes.MesMaterialResolveReq;
import com.machining.quote.dto.mes.MesMaterialResolveResp;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderDeliveryTicketMesRequest;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMesRequestMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.BusinessFileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class MesMaterialSyncService {
    private static final Logger log = LoggerFactory.getLogger(MesMaterialSyncService.class);
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_CALLBACK_PENDING = "CALLBACK_PENDING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String MATERIAL_CONFIRMED = "已确认";
    private static final String MATERIAL_SOURCE_MES_TICKET = "MES_TICKET";

    @Autowired
    private OrderDeliveryTicketMapper ticketMapper;
    @Autowired(required = false)
    private OrderDeliveryTicketMesRequestMapper mesRequestMapper;
    @Autowired(required = false)
    private OrderRfqMapper orderRfqMapper;
    @Autowired(required = false)
    private ItemQuoteVersionMapper itemMapper;
    @Autowired(required = false)
    private SalesOrderItemMapper salesOrderItemMapper;
    @Autowired
    private MesMaterialClient mesMaterialClient;
    @Autowired
    private BusinessFileService businessFileService;
    @Autowired(required = false)
    private AuditService auditService;

    public MesMaterialCallbackResp handleMaterialCallback(MesMaterialCallbackReq req) {
        long startedAt = System.currentTimeMillis();
        String mesRequestId = trimToNull(req == null ? null : req.getMesRequestId());
        if (mesRequestId == null) {
            throw callbackError(HttpStatus.BAD_REQUEST, "MES 请求ID不能为空");
        }
        Integer ticketId = req == null ? null : req.getTicketId();
        if (ticketId == null) {
            throw callbackError(HttpStatus.BAD_REQUEST, "ticketId 不能为空");
        }
        String externalTraceId = trimToNull(req.getExternalTraceId());
        log.info("MES material callback received ticketId={} mesRequestId={} externalTraceId={}",
                ticketId, mesRequestId, externalTraceId);
        OrderDeliveryTicketMesRequest request = loadMesRequest(mesRequestId);
        if (!Objects.equals(request.getTicketId(), ticketId)) {
            recordCallbackAudit("DELIVERY_MES_MATERIAL_CALLBACK_FAILED", null, request, req, STATUS_FAILED,
                    "MES 回调 ticketId 与请求流水不匹配");
            throw callbackError(HttpStatus.CONFLICT, "MES 回调 ticketId 与请求流水不匹配");
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
            recordCallbackAudit("DELIVERY_MES_MATERIAL_CALLBACK_FAILED", ticket, request, req, STATUS_FAILED, req.getMessage());
            return callbackResponse(false, "MES 回调失败", ticketId, mesRequestId, null);
        }
        if (isBlank(materialNo)) {
            throw callbackError(HttpStatus.BAD_REQUEST, "MES 回调物料号不能为空");
        }
        if (STATUS_SUCCESS.equalsIgnoreCase(ticket.getMesSyncStatus()) && !isBlank(ticket.getMaterialNo())) {
            if (materialNo.equals(ticket.getMaterialNo().trim())) {
                return callbackResponse(true, "MES 回调已处理", ticketId, mesRequestId, materialNo);
            }
            recordCallbackAudit("DELIVERY_MES_MATERIAL_CALLBACK_CONFLICT", ticket, request, req, STATUS_FAILED,
                    "MES 回调物料号冲突");
            throw callbackError(HttpStatus.CONFLICT, "MES 回调物料号冲突，不能覆盖已确认物料号");
        }
        assertSalesOrderItemMaterialWritable(ticket, materialNo);
        markCallbackSuccess(ticket, mesRequestId, materialNo, req.getMessage(), externalTraceId);
        updateMesRequestFromCallback(mesRequestId, STATUS_SUCCESS, materialNo, externalTraceId, req.getMessage(), true);
        syncSalesOrderItemMaterialNo(ticket, materialNo, req.getMessage());
        recordCallbackAudit("DELIVERY_MES_MATERIAL_CALLBACK_SUCCESS", ticket, request, req, STATUS_SUCCESS, null);
        log.info("MES material callback completed ticketId={} mesRequestId={} materialNo={} durationMs={}",
                ticketId, mesRequestId, materialNo, System.currentTimeMillis() - startedAt);
        return callbackResponse(true, "QPO已接收物料号回调", ticketId, mesRequestId, materialNo);
    }

    public Map<String, Object> syncMaterialNo(Integer ticketId, Long taskId) {
        long startedAt = System.currentTimeMillis();
        OrderDeliveryTicket ticket = loadTicket(ticketId);
        assertCanStart(ticket);
        String mesRequestId = generateMesRequestId(ticket.getId());
        log.info("MES material sync started ticketId={} taskId={} rfqId={} partNo={}",
                ticket.getId(), taskId, ticket.getRfqId(), ticket.getPartNo());
        insertMesRequest(ticket, taskId, mesRequestId);
        markProcessing(ticket, mesRequestId);
        try {
            MesMaterialResolveReq req = buildRequest(ticket, mesRequestId);
            MesMaterialResolveResp resp = mesMaterialClient.resolveMaterial(req);
            validateResponseSuccess(resp, mesRequestId);
            String materialNo = normalizeMaterialNo(resp);
            if (!isBlank(materialNo)) {
                markSuccess(ticket, mesRequestId, materialNo, resp == null ? null : resp.getMessage(), resp == null ? null : resp.getExternalTraceId());
                updateMesRequest(mesRequestId, STATUS_SUCCESS, materialNo, resp == null ? null : resp.getExternalTraceId(), resp == null ? null : resp.getMessage(), true);
                Map<String, Object> summary = summary(ticket, taskId, mesRequestId, materialNo, STATUS_SUCCESS, resp == null ? null : resp.getExternalTraceId());
                recordAudit(
                        "DELIVERY_MES_MATERIAL_SYNC_SUCCESS",
                        ticket,
                        taskId,
                        mesRequestId,
                        materialNo,
                        STATUS_SUCCESS,
                        resp == null ? null : resp.getExternalTraceId(),
                        null
                );
                log.info("MES material sync completed ticketId={} taskId={} rfqId={} partNo={} materialNo={} durationMs={}",
                        ticket.getId(), taskId, ticket.getRfqId(), ticket.getPartNo(), materialNo,
                        System.currentTimeMillis() - startedAt);
                return summary;
            }
            if (isCallbackPendingResponse(resp)) {
                markCallbackPending(ticket, mesRequestId, resp.getExternalTraceId(), resp.getMessage());
                updateMesRequest(mesRequestId, STATUS_CALLBACK_PENDING, null, resp.getExternalTraceId(), resp.getMessage(), false);
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
            recordAudit("DELIVERY_MES_MATERIAL_SYNC_FAILED", ticket, taskId, mesRequestId, null, STATUS_FAILED, null, error.getMessage());
            log.error("MES material sync failed ticketId={} taskId={} rfqId={} partNo={} durationMs={}",
                    ticket.getId(), taskId, ticket.getRfqId(), ticket.getPartNo(),
                    System.currentTimeMillis() - startedAt, error);
            throw error;
        }
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

    private MesMaterialResolveReq buildRequest(OrderDeliveryTicket ticket, String mesRequestId) {
        MesMaterialResolveReq req = new MesMaterialResolveReq();
        req.setMesRequestId(mesRequestId);
        req.setTicketId(ticket.getId());
        req.setRfqId(ticket.getRfqId());
        req.setPartNo(required(ticket.getPartNo(), "图号不能为空"));
        req.setVersion(ticket.getVersion() == null ? "" : ticket.getVersion());
        req.setInternalProjectNo(required(ticket.getInternalProjectNo(), "内部项目号不能为空"));
        OrderRfq order = orderRfqMapper == null || ticket.getRfqId() == null ? null : orderRfqMapper.selectById(ticket.getRfqId());
        req.setRfqNo(order == null ? ticket.getRfqNo() : order.getRfqNo());
        req.setCustomerName(order == null ? ticket.getCustomerName() : order.getCustomerName());
        req.setSourceType(resolveSourceType(ticket));
        req.setSourceOrderId(resolveSourceOrderId(ticket));
        req.setSourceItemId(resolveSourceItemId(ticket));
        req.setOrderNo(resolveOrderNo(ticket, req.getRfqNo()));
        String drawingUrl = resolveDrawingUrl(ticket);
        req.setDrawingUrl(businessFileService.externalReadableUrl(required(drawingUrl, "图纸不能为空")));
        return req;
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
        return STATUS_CALLBACK_PENDING.equals(normalized) || "PENDING".equals(normalized) || "ACCEPTED".equals(normalized);
    }

    private void markProcessing(OrderDeliveryTicket ticket, String mesRequestId) {
        OrderDeliveryTicket update = new OrderDeliveryTicket();
        update.setId(ticket.getId());
        update.setMesRequestId(mesRequestId);
        update.setMesSyncStatus(STATUS_PROCESSING);
        update.setMesSyncMessage("MES material sync processing");
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

    private void markSuccess(OrderDeliveryTicket ticket, String mesRequestId, String materialNo, String message, String externalTraceId) {
        OrderDeliveryTicket update = new OrderDeliveryTicket();
        update.setId(ticket.getId());
        update.setMaterialNo(materialNo);
        update.setMesSyncStatus(STATUS_SUCCESS);
        update.setMesSyncMessage(isBlank(message) ? "MES material resolved" : message.trim());
        update.setMesSyncedAt(LocalDateTime.now());
        update.setMesRequestId(mesRequestId);
        update.setMesExternalTaskId(trimToNull(externalTraceId));
        ticketMapper.updateById(update);
        syncSalesOrderItemMaterialNo(ticket, materialNo, update.getMesSyncMessage());
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

    private void assertSalesOrderItemMaterialWritable(OrderDeliveryTicket ticket, String materialNo) {
        if (salesOrderItemMapper == null
                || ticket == null
                || ticket.getSourceItemId() == null
                || !ReleaseSourceType.SALES_ORDER.name().equals(ticket.getSourceType())) {
            return;
        }
        SalesOrderItem item = salesOrderItemMapper.selectById(ticket.getSourceItemId());
        if (item == null || isBlank(item.getMaterialNo())) {
            return;
        }
        if (materialNo.equals(item.getMaterialNo().trim())) {
            return;
        }
        if (MATERIAL_CONFIRMED.equals(item.getMaterialNoStatus())) {
            throw callbackError(HttpStatus.CONFLICT, "销售订单零件已确认不同物料号，不能覆盖");
        }
        throw callbackError(HttpStatus.CONFLICT, "销售订单零件已有不同物料号，不能覆盖");
    }

    private void syncSalesOrderItemMaterialNo(OrderDeliveryTicket ticket, String materialNo, String message) {
        if (salesOrderItemMapper == null
                || ticket == null
                || ticket.getSourceItemId() == null
                || !ReleaseSourceType.SALES_ORDER.name().equals(ticket.getSourceType())) {
            return;
        }
        SalesOrderItem update = new SalesOrderItem();
        update.setId(ticket.getSourceItemId());
        update.setMaterialNo(materialNo);
        update.setMaterialNoStatus(MATERIAL_CONFIRMED);
        update.setMaterialNoSource(MATERIAL_SOURCE_MES_TICKET);
        update.setMaterialNoSourceTicketId(ticket.getId() == null ? null : ticket.getId().longValue());
        update.setMaterialNoSyncedAt(LocalDateTime.now());
        update.setMaterialNoMessage(abbreviate(message));
        salesOrderItemMapper.updateById(update);
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
        if (auditService == null) {
            return;
        }
        auditService.record(
                action,
                "order_delivery_ticket",
                ticket.getId(),
                null,
                summary(ticket, taskId, mesRequestId, materialNo, result, externalTraceId),
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
        if (auditService == null) {
            return;
        }
        Integer ticketId = ticket == null ? (req == null ? null : req.getTicketId()) : ticket.getId();
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
            Integer ticketId,
            String mesRequestId,
            String materialNo) {
        MesMaterialCallbackResp resp = new MesMaterialCallbackResp();
        resp.setSuccess(success);
        resp.setMessage(message);
        resp.setTicketId(ticketId);
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

    private void insertMesRequest(OrderDeliveryTicket ticket, Long taskId, String mesRequestId) {
        if (mesRequestMapper == null) {
            return;
        }
        OrderDeliveryTicketMesRequest request = new OrderDeliveryTicketMesRequest();
        request.setTicketId(ticket.getId());
        request.setMesRequestId(mesRequestId);
        request.setSystemTaskId(taskId);
        request.setRequestStatus(STATUS_PROCESSING);
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

    private Long resolveSourceOrderId(OrderDeliveryTicket ticket) {
        if (ticket.getSourceOrderId() != null) {
            return ticket.getSourceOrderId();
        }
        return ticket.getRfqId() == null ? null : ticket.getRfqId().longValue();
    }

    private Long resolveSourceItemId(OrderDeliveryTicket ticket) {
        if (ticket.getSourceItemId() != null) {
            return ticket.getSourceItemId();
        }
        return ticket.getItemId() == null ? null : ticket.getItemId().longValue();
    }

    private String resolveOrderNo(OrderDeliveryTicket ticket, String rfqNo) {
        if (!isBlank(rfqNo)) {
            return rfqNo.trim();
        }
        return isBlank(ticket.getRfqNo()) ? null : ticket.getRfqNo().trim();
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
