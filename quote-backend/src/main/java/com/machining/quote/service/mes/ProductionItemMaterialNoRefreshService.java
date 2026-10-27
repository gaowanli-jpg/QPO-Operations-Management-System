package com.machining.quote.service.mes;

import com.machining.quote.dto.ReleaseSourceType;
import com.machining.quote.dto.mes.MesMaterialResolveReq;
import com.machining.quote.dto.mes.MesMaterialResolveResp;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderDeliveryTicketAllocation;
import com.machining.quote.entity.OrderDeliveryTicketMesRequest;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.OrderDeliveryTicketAllocationMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMesRequestMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 待产零件「刷新料号」异常修复。
 *
 * <p>适用场景：MES 侧删除旧料号并重新建号（例如人工填错料号后被更正），QPO 已确认的
 * material_no 变成死号，生产下发会持续报「以下料号不存在」。</p>
 *
 * <p>与正常「批量获取物料号」的区别：</p>
 * <ul>
 *   <li>正常流程遇到已确认的不同料号会拒绝覆盖（{@code assertWritable}），本流程在通过
 *       前置校验后强制覆盖，并把来源标记为 {@code MES_REFRESH}；</li>
 *   <li>必须填写原因，全流程审计；</li>
 *   <li>已成功下发 MES、或存在在途下发请求的生产行一律不允许刷新。</li>
 * </ul>
 */
@Service
public class ProductionItemMaterialNoRefreshService {
    private static final Logger log = LoggerFactory.getLogger(ProductionItemMaterialNoRefreshService.class);

    public static final String TASK_TYPE = "MES_MATERIAL_NO_REFRESH";
    static final String STATUS_SUCCESS = "SUCCESS";
    static final String STATUS_FAILED = "FAILED";

    private static final String REQUEST_STATUS_PROCESSING = "PROCESSING";
    private static final String MES_PRODUCTION_SUCCESS = "SUCCESS";
    private static final java.util.Set<String> IN_FLIGHT_PRODUCTION_STATUSES =
            java.util.Set.of("PROCESSING", "SUBMITTING", "RESULT_UNKNOWN");
    private static final int MAX_REFRESH_BATCH_SIZE = 200;
    private static final int MAX_REASON_LENGTH = 200;
    private static final DateTimeFormatter BIZ_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final char[] CROCKFORD32 = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    private OrderDeliveryTicketAllocationMapper allocationMapper;
    @Autowired
    private OrderDeliveryTicketMapper ticketMapper;
    @Autowired(required = false)
    private SalesOrderItemMapper salesOrderItemMapper;
    @Autowired(required = false)
    private SalesOrderHeaderMapper salesOrderHeaderMapper;
    @Autowired(required = false)
    private OrderRfqMapper orderRfqMapper;
    @Autowired(required = false)
    private OrderDeliveryTicketMesRequestMapper mesRequestMapper;
    @Autowired
    private MesMaterialClient mesMaterialClient;
    @Autowired
    private ProductionItemMaterialNoRefreshWriter refreshWriter;
    @Autowired(required = false)
    private MesProductionDispatchService mesProductionDispatchService;
    @Autowired(required = false)
    private AuditService auditService;

    public List<Integer> normalizeAllocationIds(List<Integer> allocationIds) {
        if (allocationIds == null) {
            throw new RuntimeException("请至少选择一个待产零件");
        }
        LinkedHashSet<Integer> normalized = new LinkedHashSet<>();
        for (Integer allocationId : allocationIds) {
            if (allocationId != null && allocationId > 0) {
                normalized.add(allocationId);
            }
        }
        if (normalized.isEmpty()) {
            throw new RuntimeException("请至少选择一个待产零件");
        }
        if (normalized.size() > MAX_REFRESH_BATCH_SIZE) {
            throw new RuntimeException("单次刷新料号最多支持 " + MAX_REFRESH_BATCH_SIZE + " 行");
        }
        return new ArrayList<>(normalized);
    }

    public void assertCanAccessAllocations(List<Integer> allocationIds) {
        if (mesProductionDispatchService == null) {
            return;
        }
        mesProductionDispatchService.assertCanAccessAllocations(allocationIds);
    }

    public Map<String, Object> refreshBatch(List<Integer> allocationIds, String reason, Long taskId) {
        List<Integer> normalizedIds = normalizeAllocationIds(allocationIds);
        String normalizedReason = normalizeReason(reason);
        assertCanAccessAllocations(normalizedIds);

        List<OrderDeliveryTicketAllocation> allocations = allocationMapper.selectBatchIds(normalizedIds);
        Map<Integer, List<Integer>> allocationIdsByTicketId = new LinkedHashMap<>();
        if (allocations != null) {
            for (OrderDeliveryTicketAllocation allocation : allocations) {
                if (allocation == null || allocation.getTicketId() == null) {
                    continue;
                }
                allocationIdsByTicketId
                        .computeIfAbsent(allocation.getTicketId(), ignored -> new ArrayList<>())
                        .add(allocation.getId());
            }
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<Integer, List<Integer>> entry : allocationIdsByTicketId.entrySet()) {
            Integer ticketId = entry.getKey();
            List<Integer> ticketAllocationIds = entry.getValue();
            try {
                rows.add(refreshOne(ticketId, ticketAllocationIds, normalizedReason, taskId));
            } catch (RuntimeException error) {
                String message = safeMessage(error);
                Map<String, Object> failed = failedRow(ticketId, ticketAllocationIds, null, null, message, null);
                recordAudit(
                        "PRODUCTION_ITEM_MATERIAL_NO_REFRESH_FAILED",
                        ticketId,
                        ticketAllocationIds,
                        buildAuditSummary(ticketId, ticketAllocationIds, null, null,
                                normalizedReason, null, taskId, null),
                        message);
                log.warn("MES material no refresh failed ticketId={} taskId={} allocationIds={} reason={} message={}",
                        ticketId, taskId, ticketAllocationIds, normalizedReason, message);
                rows.add(failed);
            }
        }
        if (rows.isEmpty()) {
            rows.add(failedRow(null, normalizedIds, null, null, "所选待产零件不存在或已删除", null));
        }
        return summary(taskId, rows);
    }

    private Map<String, Object> refreshOne(Integer ticketId,
                                           List<Integer> allocationIds,
                                           String reason,
                                           Long taskId) {
        OrderDeliveryTicket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new IllegalStateException("提单行不存在 ticketId=" + ticketId);
        }
        if (Boolean.TRUE.equals(ticket.getDiscarded())) {
            throw new IllegalStateException("提单行已废弃，不能刷新料号");
        }
        List<OrderDeliveryTicketAllocation> ticketAllocations = allocationMapper.selectByTicketId(ticketId);
        assertNoBlockingProductionRows(ticketAllocations);

        SalesOrderItem salesItem = resolveSalesOrderItem(ticket);
        String previousMaterialNo = firstNonBlank(
                salesItem == null ? null : salesItem.getMaterialNo(),
                ticket.getMaterialNo());
        if (previousMaterialNo == null) {
            throw new IllegalStateException("该零件还没有已确认料号，请使用「批量获取物料号」");
        }

        String bizId = newRefreshBizId();
        insertMesRequest(ticket, taskId, bizId, previousMaterialNo, reason);
        MesMaterialResolveResp resp;
        try {
            resp = mesMaterialClient.resolveMaterial(buildRequest(ticket, bizId));
        } catch (RuntimeException error) {
            updateMesRequest(bizId, STATUS_FAILED, null, null, safeMessage(error));
            throw error;
        }
        validateResponse(resp, bizId);
        String newMaterialNo = trimToNull(resp.getMaterialNo());
        if (newMaterialNo == null) {
            String message = "MES 未返回料号";
            updateMesRequest(bizId, STATUS_FAILED, null, resp.getExternalTraceId(), message);
            throw new IllegalStateException(message);
        }
        if (newMaterialNo.equals(previousMaterialNo)) {
            String message = "MES 返回料号与当前一致（" + previousMaterialNo + "），料号未发生变化，请先到 MES 确认";
            updateMesRequest(bizId, STATUS_FAILED, previousMaterialNo, resp.getExternalTraceId(), message);
            throw new IllegalStateException(message);
        }

        ProductionItemMaterialNoRefreshWriter.RefreshWriteResult writeResult;
        try {
            writeResult = refreshWriter.apply(
                    ticketId,
                    newMaterialNo,
                    reason,
                    bizId,
                    resp.getExternalTraceId(),
                    resp.getMessage());
        } catch (RuntimeException error) {
            updateMesRequest(bizId, STATUS_FAILED, null, resp.getExternalTraceId(), safeMessage(error));
            throw error;
        }

        String message = "料号已刷新：" + writeResult.previousMaterialNo() + " → " + writeResult.newMaterialNo();
        updateMesRequest(bizId, STATUS_SUCCESS, newMaterialNo, resp.getExternalTraceId(), message);
        recordAudit(
                "PRODUCTION_ITEM_MATERIAL_NO_REFRESH_SUCCESS",
                ticketId,
                allocationIds,
                buildAuditSummary(ticketId, allocationIds, writeResult.previousMaterialNo(),
                        writeResult.newMaterialNo(), reason, bizId, taskId, writeResult),
                null);
        log.info("MES material no refresh succeeded ticketId={} taskId={} partNo={} version={} "
                        + "previousMaterialNo={} newMaterialNo={} bizId={} reason={}",
                ticketId, taskId, ticket.getPartNo(), ticket.getVersion(),
                writeResult.previousMaterialNo(), writeResult.newMaterialNo(), bizId, reason);

        Map<String, Object> row = baseRow(ticketId, allocationIds, ticket);
        row.put("previousMaterialNo", writeResult.previousMaterialNo());
        row.put("newMaterialNo", writeResult.newMaterialNo());
        row.put("allocationUpdated", writeResult.allocationUpdated());
        row.put("salesOrderUpdated", writeResult.salesOrderUpdated());
        row.put("rfqUpdated", writeResult.rfqUpdated());
        row.put("mesRequestId", bizId);
        row.put("bizId", bizId);
        row.put("status", STATUS_SUCCESS);
        row.put("message", message);
        return row;
    }

    private SalesOrderItem resolveSalesOrderItem(OrderDeliveryTicket ticket) {
        if (salesOrderItemMapper == null || ticket == null || ticket.getId() == null) {
            return null;
        }
        SalesOrderItem linked = salesOrderItemMapper.selectBySourceTicketIdForUpdate(ticket.getId());
        if (linked != null) {
            return linked;
        }
        if (ReleaseSourceType.SALES_ORDER.name().equals(normalizeUpper(ticket.getSourceType()))
                && ticket.getSourceItemId() != null) {
            return salesOrderItemMapper.selectByIdForUpdate(ticket.getSourceItemId());
        }
        return null;
    }

    private void assertNoBlockingProductionRows(List<OrderDeliveryTicketAllocation> allocations) {
        if (allocations == null) {
            return;
        }
        for (OrderDeliveryTicketAllocation allocation : allocations) {
            if (allocation == null || allocation.getId() == null) {
                continue;
            }
            String status = normalizeUpper(allocation.getMesProductionStatus());
            if (MES_PRODUCTION_SUCCESS.equals(status)) {
                throw new IllegalStateException("生产行已成功下发 MES，不能刷新料号 allocationId=" + allocation.getId());
            }
            if (IN_FLIGHT_PRODUCTION_STATUSES.contains(status)) {
                throw new IllegalStateException("生产行存在在途下发请求，请先核对结果再刷新料号 allocationId="
                        + allocation.getId() + " status=" + status);
            }
        }
    }

    private MesMaterialResolveReq buildRequest(OrderDeliveryTicket ticket, String bizId) {
        MesMaterialResolveReq req = new MesMaterialResolveReq();
        req.setMesRequestId(bizId);
        req.setInternalProjectNo(firstNonBlank(
                ticket.getInternalProjectNo(),
                ticket.getProjectNo(),
                ticket.getCustomerProjectNo()));
        OrderRfq order = orderRfqMapper == null || ticket.getRfqId() == null
                ? null
                : orderRfqMapper.selectById(ticket.getRfqId());
        req.setCustomerCode(resolveCustomerCode(ticket, order));
        req.setCustomerName(firstNonBlank(
                order == null ? null : order.getCustomerName(),
                ticket.getCustomerName()));
        MesMaterialResolveReq.Item item = new MesMaterialResolveReq.Item();
        item.setDrawingNo(requireText(ticket.getPartNo(), "图号为空，无法向 MES 重新解析料号"));
        item.setDrawingVersion(ticket.getVersion() == null ? "" : ticket.getVersion());
        item.setMaterial(trimToNull(ticket.getMaterialName()));
        item.setName(trimToNull(ticket.getDescription()));
        item.setRemark(trimToNull(ticket.getRemark()));
        item.setCustomerRequirements(MesCustomerRequirementsAssembler.assemble(
                ticket.getMaterialName(),
                ticket.getSurfaceTreatment()));
        req.setItems(List.of(item));
        return req;
    }

    private String resolveCustomerCode(OrderDeliveryTicket ticket, OrderRfq order) {
        if (ReleaseSourceType.SALES_ORDER.name().equals(normalizeUpper(ticket.getSourceType()))
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

    private void validateResponse(MesMaterialResolveResp resp, String bizId) {
        if (resp == null || !Boolean.TRUE.equals(resp.getSuccess())) {
            throw new IllegalStateException(resp == null || isBlank(resp.getMessage())
                    ? "MES 返回失败"
                    : resp.getMessage().trim());
        }
        String responseBizId = trimToNull(resp.getMesRequestId());
        if (responseBizId != null && !responseBizId.equals(bizId)) {
            throw new IllegalStateException("MES 返回的 bizId 与请求不一致");
        }
    }

    private void insertMesRequest(OrderDeliveryTicket ticket,
                                  Long taskId,
                                  String bizId,
                                  String previousMaterialNo,
                                  String reason) {
        if (mesRequestMapper == null) {
            return;
        }
        OrderDeliveryTicketMesRequest request = new OrderDeliveryTicketMesRequest();
        request.setTicketId(ticket.getId());
        request.setMesRequestId(bizId);
        request.setSystemTaskId(taskId);
        request.setRequestStatus(REQUEST_STATUS_PROCESSING);
        request.setMaterialNo(previousMaterialNo);
        request.setRequestMessage(abbreviate("刷新料号（异常修复）：原料号=" + previousMaterialNo + "；原因=" + reason, 500));
        request.setRequestedAt(LocalDateTime.now());
        mesRequestMapper.insert(request);
    }

    private void updateMesRequest(String bizId,
                                  String status,
                                  String materialNo,
                                  String externalTraceId,
                                  String message) {
        if (mesRequestMapper == null || isBlank(bizId)) {
            return;
        }
        OrderDeliveryTicketMesRequest update = new OrderDeliveryTicketMesRequest();
        update.setRequestStatus(status);
        update.setMaterialNo(trimToNull(materialNo));
        update.setMesExternalTaskId(trimToNull(externalTraceId));
        update.setResponseMessage(abbreviate(message, 500));
        update.setRespondedAt(LocalDateTime.now());
        if (STATUS_SUCCESS.equals(status) || STATUS_FAILED.equals(status)) {
            update.setCompletedAt(LocalDateTime.now());
        }
        update.setUpdatedAt(LocalDateTime.now());
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<OrderDeliveryTicketMesRequest> wrapper =
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<OrderDeliveryTicketMesRequest>()
                        .eq("mes_request_id", bizId);
        mesRequestMapper.update(update, wrapper);
    }

    private Map<String, Object> summary(Long taskId, List<Map<String, Object>> rows) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("taskType", TASK_TYPE);
        summary.put("taskId", taskId);
        summary.put("totalCount", rows.size());
        summary.put("successCount", count(rows, STATUS_SUCCESS));
        summary.put("failedCount", count(rows, STATUS_FAILED));
        summary.put("rows", rows);
        return summary;
    }

    private int count(List<Map<String, Object>> rows, String status) {
        return Math.toIntExact(rows.stream().filter(row -> status.equals(row.get("status"))).count());
    }

    private Map<String, Object> baseRow(Integer ticketId,
                                        List<Integer> allocationIds,
                                        OrderDeliveryTicket ticket) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("ticketId", ticketId);
        row.put("allocationIds", allocationIds);
        row.put("partNo", ticket == null ? null : ticket.getPartNo());
        row.put("version", ticket == null ? null : ticket.getVersion());
        return row;
    }

    private Map<String, Object> failedRow(Integer ticketId,
                                          List<Integer> allocationIds,
                                          String previousMaterialNo,
                                          String newMaterialNo,
                                          String message,
                                          String bizId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("ticketId", ticketId);
        row.put("allocationIds", allocationIds == null ? List.of() : allocationIds);
        if (previousMaterialNo != null) {
            row.put("previousMaterialNo", previousMaterialNo);
        }
        if (newMaterialNo != null) {
            row.put("newMaterialNo", newMaterialNo);
        }
        if (bizId != null) {
            row.put("bizId", bizId);
        }
        row.put("status", STATUS_FAILED);
        row.put("message", abbreviate(message, 500));
        return row;
    }

    private Map<String, Object> buildAuditSummary(Integer ticketId,
                                                  List<Integer> allocationIds,
                                                  String previousMaterialNo,
                                                  String newMaterialNo,
                                                  String reason,
                                                  String bizId,
                                                  Long taskId,
                                                  ProductionItemMaterialNoRefreshWriter.RefreshWriteResult writeResult) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("ticketId", ticketId);
        summary.put("allocationIds", allocationIds);
        summary.put("previousMaterialNo", previousMaterialNo);
        summary.put("newMaterialNo", newMaterialNo);
        summary.put("reason", reason);
        summary.put("bizId", bizId);
        summary.put("taskId", taskId);
        summary.put("allocationUpdated", writeResult == null ? 0 : writeResult.allocationUpdated());
        summary.put("salesOrderUpdated", writeResult == null ? 0 : writeResult.salesOrderUpdated());
        summary.put("rfqUpdated", writeResult == null ? 0 : writeResult.rfqUpdated());
        return summary;
    }

    private void recordAudit(String action,
                             Integer ticketId,
                             List<Integer> allocationIds,
                             Map<String, Object> summary,
                             String errorMessage) {
        if (auditService == null) {
            return;
        }
        boolean failed = errorMessage != null;
        auditService.record(
                action,
                "order_delivery_ticket",
                ticketId,
                null,
                summary,
                "DELIVERY",
                failed ? STATUS_FAILED : STATUS_SUCCESS,
                AuditService.labelForAction(action) + " ticketId=" + ticketId
                        + " allocationIds=" + allocationIds,
                errorMessage);
    }

    private String normalizeReason(String reason) {
        String text = trimToNull(reason);
        if (text == null) {
            throw new RuntimeException("请填写刷新料号的原因");
        }
        if (text.length() > MAX_REASON_LENGTH) {
            throw new RuntimeException("刷新原因不能超过 " + MAX_REASON_LENGTH + " 个字符");
        }
        return text;
    }

    private String newRefreshBizId() {
        return "QPO-MATR-" + LocalDate.now().format(BIZ_DATE) + "-" + ulid();
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

    private String safeMessage(RuntimeException error) {
        if (error == null) {
            return "刷新料号失败";
        }
        String message = error.getMessage();
        return isBlank(message) ? error.getClass().getSimpleName() : message.trim();
    }

    private String requireText(String value, String message) {
        String text = trimToNull(value);
        if (text == null) {
            throw new IllegalStateException(message);
        }
        return text;
    }

    private String normalizeUpper(String value) {
        String text = trimToNull(value);
        return text == null ? "" : text.toUpperCase(Locale.ROOT);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String text = trimToNull(value);
            if (text != null) {
                return text;
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String abbreviate(String value, int maxLength) {
        String text = value == null ? null : value.trim();
        if (text == null) {
            return null;
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
