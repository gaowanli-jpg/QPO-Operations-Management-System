package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.dto.ReleaseSourceType;
import com.machining.quote.dto.SalesOrderU9SyncResp;
import com.machining.quote.dto.SalesOrderU9SyncResultRow;
import com.machining.quote.dto.u9.U9ApiResult;
import com.machining.quote.dto.u9.U9ResultDTO;
import com.machining.quote.dto.u9.U9SalesOrderHeadDTO;
import com.machining.quote.dto.u9.U9SalesOrderLineDTO;
import com.machining.quote.dto.u9.U9SalesOrderShipLineDTO;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.service.u9.U9SalesOrderClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SalesOrderU9SyncService {
    private static final Logger log = LoggerFactory.getLogger(SalesOrderU9SyncService.class);
    private static final String ACTION = "SALES_ORDER_U9_SYNC";
    private static final String TARGET_TYPE = "sales_order_header";
    private static final String STATUS_SYNCING = "同步中";
    private static final String STATUS_SYNCED = "已同步";
    private static final String STATUS_FAILED = "同步失败";
    private static final String RESP_SUCCESS = "SUCCESS";
    private static final String RESP_FAILED = "FAILED";
    private static final String RESP_PARTIAL = "PARTIAL_SUCCESS";

    @Autowired
    private SalesOrderHeaderMapper headerMapper;
    @Autowired
    private SalesOrderItemMapper itemMapper;
    @Autowired
    private OrderDeliveryTicketMapper ticketMapper;
    @Autowired
    private U9SalesOrderClient u9SalesOrderClient;
    @Autowired
    private AuditService auditService;
    @Autowired(required = false)
    private DataScopeService dataScopeService;

    @Transactional
    public SalesOrderU9SyncResp syncSalesOrders(List<Long> salesOrderIds) {
        long startedAt = System.currentTimeMillis();
        List<Long> ids = normalizeIds(salesOrderIds);
        SalesOrderU9SyncResp resp = new SalesOrderU9SyncResp();
        if (ids.isEmpty()) {
            resp.setSuccess(false);
            resp.setStatus(RESP_FAILED);
            resp.setMessage("请选择需要同步 U9 的销售订单");
            resp.setTotalOrderCount(0);
            resp.setSuccessOrderCount(0);
            resp.setFailedOrderCount(0);
            return resp;
        }

        log.info("Sales order U9 sync started orderCount={}", ids.size());
        List<PreparedOrder> preparedOrders = new ArrayList<>();
        for (Long id : ids) {
            SalesOrderHeader header = headerMapper.selectByIdForUpdate(id);
            if (header == null) {
                resp.getRows().add(failedRow(id, null, "销售订单不存在"));
                continue;
            }
            assertDataScope(header);
            if (alreadySynced(header)) {
                resp.getRows().add(failedRow(header, "销售订单已同步，不能重复创建 U9 订单"));
                continue;
            }
            try {
                preparedOrders.add(prepareOrder(header));
            } catch (RuntimeException error) {
                String message = safeError(error.getMessage());
                markFailed(header, message);
                resp.getRows().add(failedRow(header, message));
            }
        }

        if (!preparedOrders.isEmpty()) {
            syncPreparedOrders(preparedOrders, resp);
        }
        finalizeResponse(resp);
        audit(resp, startedAt);
        log.info("Sales order U9 sync completed orderCount={} successOrderCount={} failedOrderCount={} durationMs={}",
                resp.getTotalOrderCount(), resp.getSuccessOrderCount(), resp.getFailedOrderCount(),
                System.currentTimeMillis() - startedAt);
        return resp;
    }

    private void syncPreparedOrders(List<PreparedOrder> preparedOrders, SalesOrderU9SyncResp resp) {
        for (PreparedOrder prepared : preparedOrders) {
            markSyncing(prepared.header());
        }
        Map<String, PreparedOrder> preparedByOtherId = preparedOrders.stream()
                .collect(Collectors.toMap(order -> order.request().getOtherID(), Function.identity(), (left, right) -> left, LinkedHashMap::new));
        try {
            U9ApiResult<U9ResultDTO> u9Result = u9SalesOrderClient.create(preparedOrders.stream()
                    .map(PreparedOrder::request)
                    .toList());
            if (u9Result == null || u9Result.getResCode() == null || u9Result.getResCode() != 0) {
                String message = safeError(firstNonBlank(
                        u9Result == null ? null : u9Result.getResMsg(),
                        u9Result == null ? null : u9Result.getException(),
                        "U9 sales order sync failed"
                ));
                failPreparedOrders(preparedOrders, resp, message);
                return;
            }
            Map<String, U9ResultDTO> resultByOtherId = (u9Result.getData() == null ? List.<U9ResultDTO>of() : u9Result.getData())
                    .stream()
                    .filter(row -> !isBlank(row.getMOtherID()))
                    .collect(Collectors.toMap(U9ResultDTO::getMOtherID, Function.identity(), (left, right) -> left));
            for (Map.Entry<String, PreparedOrder> entry : preparedByOtherId.entrySet()) {
                PreparedOrder prepared = entry.getValue();
                U9ResultDTO resultRow = resultByOtherId.get(entry.getKey());
                if (resultRow != null && Boolean.TRUE.equals(resultRow.getMIsSucess())) {
                    markSuccess(prepared.header(), resultRow);
                    resp.getRows().add(successRow(prepared.header(), resultRow));
                } else {
                    String message = safeError(firstNonBlank(
                            resultRow == null ? null : resultRow.getMErrorMsg(),
                            "U9 result missing for otherId=" + entry.getKey()
                    ));
                    markFailed(prepared.header(), message);
                    resp.getRows().add(failedRow(prepared.header(), message));
                }
            }
        } catch (RuntimeException error) {
            String message = safeError(error.getMessage());
            failPreparedOrders(preparedOrders, resp, message);
            log.error("Sales order U9 sync failed orderCount={} reason={}", preparedOrders.size(), message, sanitizedException(error, message));
        }
    }

    private PreparedOrder prepareOrder(SalesOrderHeader header) {
        String salesOrderNo = required(header.getSalesOrderNo(), "销售订单号不能为空");
        String customerCode = required(header.getCustomerCode(), "客户编码不能为空，不能同步 U9");
        String documentTypeCode = required(header.getDocumentTypeCode(), "单据类型编码不能为空，不能同步 U9");
        List<SalesOrderItem> items = loadItems(header.getId());
        Map<Long, SalesOrderItem> itemById = items.stream()
                .filter(item -> item.getId() != null)
                .collect(Collectors.toMap(SalesOrderItem::getId, Function.identity(), (left, right) -> left));
        List<OrderDeliveryTicket> tickets = loadTickets(header.getId());
        if (tickets.isEmpty()) {
            throw new RuntimeException("销售订单尚未生成 QPO 提单，不能同步 U9");
        }

        U9SalesOrderHeadDTO head = new U9SalesOrderHeadDTO();
        head.setOtherID(salesOrderNo);
        head.setDocNo(header.getDocumentNo());
        head.setBusinessDate(formatDate(header.getSalesOrderDate(), LocalDate.now()));
        head.setOrderBy(customerCode);
        head.setDocumentType(documentTypeCode);
        head.setCustomerOrderNo(header.getCustomerOrderNo());
        head.setIsPriceIncludeTax(true);
        int lineNo = 1;
        for (OrderDeliveryTicket ticket : tickets) {
            Long sourceItemId = ticket.getSourceItemId();
            SalesOrderItem item = sourceItemId == null ? null : itemById.get(sourceItemId);
            if (item == null) {
                throw new RuntimeException("提单行 " + ticket.getId() + " 未匹配销售订单零件，不能同步 U9");
            }
            head.getSOLineDTOList().add(toU9Line(item, ticket, lineNo++));
        }
        return new PreparedOrder(header, head);
    }

    private U9SalesOrderLineDTO toU9Line(SalesOrderItem item, OrderDeliveryTicket ticket, int sequence) {
        String materialNo = required(ticket.getMaterialNo(), "提单行 " + ticket.getId() + " 未取得物料号，不能同步 U9");
        BigDecimal qty = requiredQty(ticket);
        BigDecimal price = requiredPrice(item);
        String unit = "PCS";

        U9SalesOrderLineDTO line = new U9SalesOrderLineDTO();
        line.setDocLineNo(item.getLineNo() == null || item.getLineNo() <= 0 ? sequence : item.getLineNo());
        line.setCustomerItemNo(item.getSalesMaterialNo());
        line.setCustomerItemName(item.getDescription());
        line.setItemCode(materialNo);
        line.setItemVersion(item.getVersion());
        line.setFreeType(-1);
        line.setFreeReason(-1);
        line.setProject(firstNonBlank(ticket.getProjectNo(), ticket.getInternalProjectNo(), item.getInternalProjectNo()));
        line.setOrderByQtyTU(qty);
        line.setOrderByQtyPU(qty);
        line.setTU(unit);
        line.setPU(unit);
        line.setTBUToPBURate(BigDecimal.ONE);
        line.setOrderPriceTC(price);
        line.setFinallyPriceTC(price);
        line.setPriceSource(1);
        line.setShipTogetherFlag(-1);

        U9SalesOrderShipLineDTO shipLine = new U9SalesOrderShipLineDTO();
        shipLine.setDocSubLineNo(1);
        shipLine.setItemCode(materialNo);
        shipLine.setRequireDate(formatDate(ticket.getDeliveryDate(), firstDate(item.getInternalDeliveryDate(), item.getCustomerDeliveryDate(), LocalDate.now())));
        shipLine.setDeliveryDate(shipLine.getRequireDate());
        shipLine.setPlanDate(shipLine.getRequireDate());
        shipLine.setShipPlanQtyTU(qty);
        shipLine.setShipPlanQtyPU(qty);
        shipLine.setTU(unit);
        shipLine.setPU(unit);
        shipLine.setTUBaseToPUBaseRate(BigDecimal.ONE);
        shipLine.setMemo(ticket.getRemark());
        line.getSOShiplineDTOList().add(shipLine);
        return line;
    }

    private List<SalesOrderItem> loadItems(Long salesOrderId) {
        QueryWrapper<SalesOrderItem> query = new QueryWrapper<>();
        query.eq("sales_order_id", salesOrderId).orderByAsc("id");
        List<SalesOrderItem> items = itemMapper.selectList(query);
        return items == null ? List.of() : items;
    }

    private List<OrderDeliveryTicket> loadTickets(Long salesOrderId) {
        QueryWrapper<OrderDeliveryTicket> query = new QueryWrapper<>();
        query.eq("source_type", ReleaseSourceType.SALES_ORDER.name())
                .eq("source_order_id", salesOrderId)
                .orderByAsc("id");
        List<OrderDeliveryTicket> tickets = ticketMapper.selectList(query);
        return tickets == null ? List.of() : tickets;
    }

    private void failPreparedOrders(List<PreparedOrder> preparedOrders, SalesOrderU9SyncResp resp, String message) {
        for (PreparedOrder prepared : preparedOrders) {
            markFailed(prepared.header(), message);
            resp.getRows().add(failedRow(prepared.header(), message));
        }
    }

    private void markSyncing(SalesOrderHeader header) {
        SalesOrderHeader update = new SalesOrderHeader();
        update.setId(header.getId());
        update.setU9SyncStatus(STATUS_SYNCING);
        update.setU9SyncMessage("U9 同步处理中");
        update.setUpdatedAt(LocalDateTime.now());
        headerMapper.updateById(update);
    }

    private void markSuccess(SalesOrderHeader header, U9ResultDTO row) {
        SalesOrderHeader update = new SalesOrderHeader();
        update.setId(header.getId());
        update.setDocumentNo(firstNonBlank(row.getMCode(), header.getDocumentNo()));
        update.setU9SyncStatus(STATUS_SYNCED);
        update.setU9OrderId(row.getMID());
        update.setU9OrderCode(row.getMCode());
        update.setU9SyncMessage("U9 同步成功");
        update.setUpdatedAt(LocalDateTime.now());
        headerMapper.updateById(update);
    }

    private void markFailed(SalesOrderHeader header, String message) {
        SalesOrderHeader update = new SalesOrderHeader();
        update.setId(header.getId());
        update.setU9SyncStatus(STATUS_FAILED);
        update.setU9SyncMessage(safeError(message));
        update.setUpdatedAt(LocalDateTime.now());
        headerMapper.updateById(update);
    }

    private void finalizeResponse(SalesOrderU9SyncResp resp) {
        int successCount = (int) resp.getRows().stream().filter(SalesOrderU9SyncResultRow::isSuccess).count();
        int failedCount = resp.getRows().size() - successCount;
        resp.setTotalOrderCount(resp.getRows().size());
        resp.setSuccessOrderCount(successCount);
        resp.setFailedOrderCount(failedCount);
        resp.setSuccess(failedCount == 0 && resp.getTotalOrderCount() > 0);
        resp.setStatus(statusFor(successCount, failedCount));
        resp.setMessage(resp.isSuccess()
                ? "U9 同步成功"
                : firstNonBlank(resp.getRows().stream()
                        .filter(row -> !row.isSuccess())
                        .map(SalesOrderU9SyncResultRow::getErrorMessage)
                        .filter(message -> !isBlank(message))
                        .findFirst()
                        .orElse(null), "U9 同步失败"));
    }

    private String statusFor(int successCount, int failedCount) {
        if (failedCount == 0) {
            return RESP_SUCCESS;
        }
        if (successCount > 0) {
            return RESP_PARTIAL;
        }
        return RESP_FAILED;
    }

    private SalesOrderU9SyncResultRow successRow(SalesOrderHeader header, U9ResultDTO u9Row) {
        SalesOrderU9SyncResultRow row = new SalesOrderU9SyncResultRow();
        row.setSuccess(true);
        row.setStatus(RESP_SUCCESS);
        row.setSalesOrderId(header.getId());
        row.setSalesOrderNo(header.getSalesOrderNo());
        row.setU9OrderId(u9Row.getMID());
        row.setU9OrderCode(u9Row.getMCode());
        return row;
    }

    private SalesOrderU9SyncResultRow failedRow(SalesOrderHeader header, String message) {
        return failedRow(header.getId(), header.getSalesOrderNo(), message);
    }

    private SalesOrderU9SyncResultRow failedRow(Long salesOrderId, String salesOrderNo, String message) {
        SalesOrderU9SyncResultRow row = new SalesOrderU9SyncResultRow();
        row.setSuccess(false);
        row.setStatus(RESP_FAILED);
        row.setSalesOrderId(salesOrderId);
        row.setSalesOrderNo(salesOrderNo);
        row.setErrorMessage(safeError(message));
        return row;
    }

    private void audit(SalesOrderU9SyncResp resp, long startedAt) {
        if (auditService == null) {
            return;
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("status", resp.getStatus());
        summary.put("totalOrderCount", resp.getTotalOrderCount());
        summary.put("successOrderCount", resp.getSuccessOrderCount());
        summary.put("failedOrderCount", resp.getFailedOrderCount());
        summary.put("salesOrderNoSample", resp.getRows().stream()
                .map(SalesOrderU9SyncResultRow::getSalesOrderNo)
                .filter(value -> !isBlank(value))
                .limit(10)
                .toList());
        summary.put("u9OrderCodeSample", resp.getRows().stream()
                .map(SalesOrderU9SyncResultRow::getU9OrderCode)
                .filter(value -> !isBlank(value))
                .limit(10)
                .toList());
        summary.put("durationMs", System.currentTimeMillis() - startedAt);
        auditService.record(
                ACTION,
                TARGET_TYPE,
                resp.getRows().size() == 1 ? resp.getRows().get(0).getSalesOrderId() : null,
                null,
                summary,
                "MARKET",
                resp.isSuccess() ? "SUCCESS" : "FAILED",
                AuditService.labelForAction(ACTION),
                resp.isSuccess() ? null : resp.getMessage()
        );
    }

    private List<Long> normalizeIds(List<Long> rawIds) {
        if (rawIds == null) {
            return List.of();
        }
        return rawIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
    }

    private boolean alreadySynced(SalesOrderHeader header) {
        return STATUS_SYNCED.equals(header.getU9SyncStatus())
                && (!isBlank(header.getU9OrderId()) || !isBlank(header.getU9OrderCode()));
    }

    private void assertDataScope(SalesOrderHeader header) {
        if (dataScopeService != null) {
            dataScopeService.assertCanAccessSalesOrder(header);
        }
    }

    private BigDecimal requiredQty(OrderDeliveryTicket ticket) {
        if (ticket.getQty() == null || ticket.getQty() <= 0) {
            throw new RuntimeException("提单行 " + ticket.getId() + " 数量不能为空，不能同步 U9");
        }
        return BigDecimal.valueOf(ticket.getQty());
    }

    private BigDecimal requiredPrice(SalesOrderItem item) {
        BigDecimal price = item.getFinalUnitPrice() == null ? item.getTaxIncludedUnitPrice() : item.getFinalUnitPrice();
        if (price == null) {
            price = item.getUntaxedUnitPrice();
        }
        if (price == null) {
            throw new RuntimeException("销售订单零件 " + item.getPartNo() + " 价格不能为空，不能同步 U9");
        }
        return price;
    }

    private String required(String value, String message) {
        if (isBlank(value)) {
            throw new RuntimeException(message);
        }
        return value.trim();
    }

    private LocalDate firstDate(LocalDate... values) {
        for (LocalDate value : values) {
            if (value != null) {
                return value;
            }
        }
        return LocalDate.now();
    }

    private String formatDate(LocalDate preferred, LocalDate fallback) {
        return (preferred == null ? fallback : preferred).toString();
    }

    private String safeError(String value) {
        if (isBlank(value)) {
            return "U9 同步失败";
        }
        String text = value.replaceAll("\\s+", " ").trim();
        text = text.replaceAll("(?i)\\b(token|clientsecret|client[-_]?secret|authorization|cookie|password|secret)\\s*[:=]\\s*[^\\s,;]+", "[REDACTED]");
        text = text.replaceAll("(?i)SO(LineDTOList|ShiplineDTOList)\\s*=\\s*\\[[^]]*]", "request=[REDACTED]");
        text = text.replaceAll("(?i)\\[(\\s*\\{.*}\\s*)]", "request=[REDACTED]");
        return text.length() <= 500 ? text : text.substring(0, 500) + "...";
    }

    private RuntimeException sanitizedException(Throwable source, String message) {
        RuntimeException sanitized = new RuntimeException(message);
        if (source != null) {
            sanitized.setStackTrace(source.getStackTrace());
        }
        return sanitized;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record PreparedOrder(SalesOrderHeader header, U9SalesOrderHeadDTO request) {
    }
}
