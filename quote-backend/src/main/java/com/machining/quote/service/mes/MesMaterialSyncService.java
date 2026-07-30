package com.machining.quote.service.mes;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.machining.quote.dto.mes.MesMaterialResolveReq;
import com.machining.quote.dto.mes.MesMaterialResolveResp;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.BusinessFileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class MesMaterialSyncService {
    private static final Logger log = LoggerFactory.getLogger(MesMaterialSyncService.class);
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";

    @Autowired
    private OrderDeliveryTicketMapper ticketMapper;
    @Autowired(required = false)
    private OrderRfqMapper orderRfqMapper;
    @Autowired(required = false)
    private ItemQuoteVersionMapper itemMapper;
    @Autowired
    private MesMaterialClient mesMaterialClient;
    @Autowired
    private BusinessFileService businessFileService;
    @Autowired(required = false)
    private AuditService auditService;

    public Map<String, Object> syncMaterialNo(Integer ticketId, Long taskId) {
        long startedAt = System.currentTimeMillis();
        OrderDeliveryTicket ticket = loadTicket(ticketId);
        assertCanStart(ticket);
        log.info("MES material sync started ticketId={} taskId={} rfqId={} partNo={}",
                ticket.getId(), taskId, ticket.getRfqId(), ticket.getPartNo());
        markProcessing(ticket);
        try {
            MesMaterialResolveReq req = buildRequest(ticket);
            MesMaterialResolveResp resp = mesMaterialClient.resolveMaterial(req);
            String materialNo = normalizeMaterialNo(resp);
            markSuccess(ticket, materialNo, resp == null ? null : resp.getMessage());
            Map<String, Object> summary = summary(ticket, taskId, materialNo);
            recordAudit("DELIVERY_MES_MATERIAL_SYNC_SUCCESS", ticket, taskId, materialNo, "SUCCESS", null);
            log.info("MES material sync completed ticketId={} taskId={} rfqId={} partNo={} materialNo={} durationMs={}",
                    ticket.getId(), taskId, ticket.getRfqId(), ticket.getPartNo(), materialNo,
                    System.currentTimeMillis() - startedAt);
            return summary;
        } catch (RuntimeException error) {
            markFailed(ticket, error.getMessage());
            recordAudit("DELIVERY_MES_MATERIAL_SYNC_FAILED", ticket, taskId, null, "FAILED", error.getMessage());
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
    }

    private MesMaterialResolveReq buildRequest(OrderDeliveryTicket ticket) {
        MesMaterialResolveReq req = new MesMaterialResolveReq();
        req.setTicketId(ticket.getId());
        req.setRfqId(ticket.getRfqId());
        req.setPartNo(required(ticket.getPartNo(), "图号不能为空"));
        req.setVersion(ticket.getVersion() == null ? "" : ticket.getVersion());
        req.setInternalProjectNo(required(ticket.getInternalProjectNo(), "内部项目号不能为空"));
        OrderRfq order = orderRfqMapper == null || ticket.getRfqId() == null ? null : orderRfqMapper.selectById(ticket.getRfqId());
        req.setRfqNo(order == null ? null : order.getRfqNo());
        req.setCustomerName(order == null ? null : order.getCustomerName());
        String drawingUrl = null;
        if (itemMapper != null && ticket.getItemId() != null) {
            ItemQuoteVersion item = itemMapper.selectById(ticket.getItemId());
            drawingUrl = item == null ? null : item.getDrawingUrl();
        }
        req.setDrawingUrl(businessFileService.externalReadableUrl(required(drawingUrl, "图纸不能为空")));
        return req;
    }

    private String normalizeMaterialNo(MesMaterialResolveResp resp) {
        if (resp == null || !Boolean.TRUE.equals(resp.getSuccess())) {
            throw new RuntimeException(resp == null || isBlank(resp.getMessage()) ? "MES 返回失败" : resp.getMessage());
        }
        String materialNo = required(resp.getMaterialNo(), "MES 未返回物料号").trim();
        if (materialNo.length() > 100) {
            throw new RuntimeException("MES 物料号长度超过 100");
        }
        return materialNo;
    }

    private void markProcessing(OrderDeliveryTicket ticket) {
        OrderDeliveryTicket update = new OrderDeliveryTicket();
        update.setId(ticket.getId());
        update.setMesSyncStatus(STATUS_PROCESSING);
        update.setMesSyncMessage("MES material sync processing");
        UpdateWrapper<OrderDeliveryTicket> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", ticket.getId())
                .and(status -> status
                        .isNull("mes_sync_status")
                        .or()
                        .notIn("mes_sync_status", STATUS_PROCESSING, STATUS_SUCCESS));
        int updated = ticketMapper.update(update, wrapper);
        if (updated <= 0) {
            OrderDeliveryTicket current = loadTicket(ticket.getId());
            assertCanStart(current);
            throw new RuntimeException("MES material sync status changed before processing");
        }
    }

    private void markSuccess(OrderDeliveryTicket ticket, String materialNo, String message) {
        OrderDeliveryTicket update = new OrderDeliveryTicket();
        update.setId(ticket.getId());
        update.setMaterialNo(materialNo);
        update.setMesSyncStatus(STATUS_SUCCESS);
        update.setMesSyncMessage(isBlank(message) ? "MES material resolved" : message.trim());
        update.setMesSyncedAt(LocalDateTime.now());
        ticketMapper.updateById(update);
    }

    private void markFailed(OrderDeliveryTicket ticket, String message) {
        OrderDeliveryTicket update = new OrderDeliveryTicket();
        update.setId(ticket.getId());
        update.setMesSyncStatus(STATUS_FAILED);
        update.setMesSyncMessage(abbreviate(isBlank(message) ? "MES material sync failed" : message));
        ticketMapper.updateById(update);
    }

    private void recordAudit(String action, OrderDeliveryTicket ticket, Long taskId, String materialNo, String result, String errorMessage) {
        if (auditService == null) {
            return;
        }
        auditService.record(
                action,
                "order_delivery_ticket",
                ticket.getId(),
                null,
                summary(ticket, taskId, materialNo),
                "DELIVERY",
                result,
                AuditService.labelForAction(action) + " ticketId=" + ticket.getId() + " taskId=" + taskId,
                errorMessage
        );
    }

    private Map<String, Object> summary(OrderDeliveryTicket ticket, Long taskId, String materialNo) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("ticketId", ticket.getId());
        summary.put("taskId", taskId);
        summary.put("rfqId", ticket.getRfqId());
        summary.put("partNo", ticket.getPartNo());
        summary.put("version", ticket.getVersion());
        summary.put("internalProjectNo", ticket.getInternalProjectNo());
        if (!isBlank(materialNo)) {
            summary.put("materialNo", materialNo);
        }
        return summary;
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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
