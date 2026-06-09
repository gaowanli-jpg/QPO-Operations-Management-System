package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.machining.quote.dto.aps.ApsDeliveryItemBatchDTO;
import com.machining.quote.dto.aps.ApsDeliveryItemDTO;
import com.machining.quote.dto.aps.ApsProcessDTO;
import com.machining.quote.entity.DictExtraFeeItem;
import com.machining.quote.entity.DictProcess;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderProcessSheet;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.DictExtraFeeItemMapper;
import com.machining.quote.mapper.DictProcessMapper;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderProcessSheetMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ApsSourceService {
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;
    private static final String WORK_HOUR_PROCESS = "WORK_HOUR";
    private static final String NON_WORK_HOUR_PROCESS = "NON_WORK_HOUR";
    private static final String WORK_HOUR = "\u5de5\u65f6";
    private static final String NON_WORK_HOUR_FEE = "\u975e\u5de5\u65f6\u8d39\u7528";

    @Autowired
    private OrderDeliveryTicketMapper ticketMapper;
    @Autowired
    private OrderProcessSheetMapper processSheetMapper;
    @Autowired
    private ItemQuoteVersionMapper itemMapper;
    @Autowired
    private OrderRfqMapper orderRfqMapper;
    @Autowired
    private DictProcessMapper processMapper;
    @Autowired
    private DictExtraFeeItemMapper extraFeeItemMapper;

    @Value("${erp.public-base-url:http://localhost:8088}")
    private String publicBaseUrl;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<ApsProcessDTO> listProcesses(String processType) {
        String safeType = normalizeProcessType(processType);
        List<ApsProcessDTO> result = new ArrayList<>();
        if (safeType.isEmpty() || WORK_HOUR_PROCESS.equals(safeType)) {
            result.addAll(listWorkHourProcesses());
        }
        if (safeType.isEmpty() || NON_WORK_HOUR_PROCESS.equals(safeType)) {
            result.addAll(listNonWorkHourProcesses());
        }
        return result;
    }

    public ApsDeliveryItemBatchDTO listDeliveryItems(Integer afterTicketId, Integer limit) {
        int pageSize = normalizeLimit(limit);
        QueryWrapper<OrderDeliveryTicket> qw = new QueryWrapper<>();
        if (afterTicketId != null && afterTicketId > 0) {
            qw.gt("id", afterTicketId);
        }
        qw.orderByAsc("id").last("LIMIT " + (pageSize + 1));
        List<OrderDeliveryTicket> rawTickets = ticketMapper.selectList(qw);

        boolean hasMore = rawTickets.size() > pageSize;
        List<OrderDeliveryTicket> tickets = hasMore ? rawTickets.subList(0, pageSize) : rawTickets;

        Map<Integer, OrderRfq> orderMap = loadOrders(tickets);
        Map<Integer, ItemQuoteVersion> itemMap = loadItems(tickets);
        Map<Integer, OrderProcessSheet> processSheetMap = loadProcessSheets(tickets);

        List<ApsDeliveryItemDTO> records = tickets.stream()
                .map(ticket -> buildRow(ticket, orderMap.get(ticket.getRfqId()), itemMap.get(ticket.getItemId()),
                        processSheetMap.get(ticket.getItemId())))
                .toList();

        ApsDeliveryItemBatchDTO result = new ApsDeliveryItemBatchDTO();
        result.setRecords(records);
        result.setCount(records.size());
        result.setHasMore(hasMore);
        result.setNextAfterTicketId(records.isEmpty() ? afterTicketId : records.get(records.size() - 1).getTicketId());
        return result;
    }

    private List<ApsProcessDTO> listWorkHourProcesses() {
        QueryWrapper<DictProcess> qw = new QueryWrapper<>();
        qw.orderByAsc("sort_no").orderByAsc("id");
        return processMapper.selectList(qw).stream()
                .filter(process -> !stringValue(process.getProcessName()).isEmpty())
                .map(process -> {
                    ApsProcessDTO row = new ApsProcessDTO();
                    row.setSourceId(process.getId());
                    row.setProcessName(process.getProcessName().trim());
                    row.setProcessType(WORK_HOUR_PROCESS);
                    row.setCalcType(WORK_HOUR);
                    row.setSortNo(process.getSortNo());
                    return row;
                })
                .toList();
    }

    private List<ApsProcessDTO> listNonWorkHourProcesses() {
        QueryWrapper<DictExtraFeeItem> qw = new QueryWrapper<>();
        qw.eq("enabled", true).orderByAsc("sort_no").orderByAsc("id");
        return extraFeeItemMapper.selectList(qw).stream()
                .filter(item -> !stringValue(item.getFeeName()).isEmpty())
                .map(item -> {
                    ApsProcessDTO row = new ApsProcessDTO();
                    row.setSourceId(item.getId());
                    row.setProcessName(item.getFeeName().trim());
                    row.setProcessType(NON_WORK_HOUR_PROCESS);
                    row.setCalcType(NON_WORK_HOUR_FEE);
                    row.setSortNo(item.getSortNo());
                    return row;
                })
                .toList();
    }

    private ApsDeliveryItemDTO buildRow(OrderDeliveryTicket ticket, OrderRfq order, ItemQuoteVersion item,
                                        OrderProcessSheet processSheet) {
        ApsDeliveryItemDTO row = new ApsDeliveryItemDTO();
        row.setTicketId(ticket.getId());
        row.setRfqId(ticket.getRfqId());
        row.setItemId(ticket.getItemId());
        row.setSourceProcessSheetId(processSheet == null ? null : processSheet.getId());

        row.setRfqNo(order == null ? "" : order.getRfqNo());
        row.setCustomerName(order == null ? "" : order.getCustomerName());
        row.setOrderStatus(order == null ? "" : order.getStatus());
        row.setInternalProjectNo(ticket.getInternalProjectNo());
        row.setProjectNo(ticket.getProjectNo());
        row.setOrderType(ticket.getOrderType());
        row.setItemType(ticket.getItemType());

        row.setPartNo(ticket.getPartNo());
        row.setVersion(ticket.getVersion());
        row.setDescription(item == null ? "" : item.getDescription());
        row.setQty(ticket.getQty());
        row.setSourceItemQty(item == null ? null : item.getQty());
        row.setUnit(item == null ? "" : item.getUnit());

        row.setMaterialName(resolveEngineeringMaterialName(ticket, item));
        row.setMaterialType(resolveMaterialType(item));
        row.setMaterialGrossWeight(item == null ? null : item.getMaterialGrossWeight());
        row.setMaterialDemandWeight(multiply(item == null ? null : item.getMaterialGrossWeight(), ticket.getQty()));
        row.setPartNetWeight(item == null ? null : item.getPartNetWeight());
        row.setSurfaceTreatment(firstText(ticket.getSurfaceTreatment(), item == null ? "" : item.getSurfaceTreatment()));

        row.setLeadTime(item == null ? "" : item.getLeadTime());
        row.setQuoteLeadTime(item == null ? "" : item.getQuoteLeadTime());
        row.setInternalTargetLeadTime(item == null ? "" : item.getInternalTargetLeadTime());
        row.setStartDate(ticket.getStartDate());
        row.setDeliveryDate(ticket.getDeliveryDate());
        row.setRemark(ticket.getRemark());
        row.setArrangeType(ticket.getArrangeType());

        String drawingUrl = item == null ? "" : item.getDrawingUrl();
        row.setDrawingUrl(drawingUrl);
        row.setDrawingViewUrl(toAbsoluteUrl(drawingUrl));

        row.setMaterialData(item == null ? null : item.getMaterialData());
        Object routeSource = processSheet != null && processSheet.getProcessRoute() != null
                ? processSheet.getProcessRoute()
                : item == null ? null : item.getProcessRoute();
        row.setProcessSnapshotSource(processSheet != null && processSheet.getProcessRoute() != null
                ? "ORDER_PROCESS_SHEET"
                : "ITEM_QUOTE_VERSION");
        List<ApsDeliveryItemDTO.ProcessRow> processRows = normalizeProcessRows(routeSource);
        row.setProcessRows(processRows);
        double unitWorkHours = processRows.stream()
                .map(ApsDeliveryItemDTO.ProcessRow::getWorkHours)
                .filter(value -> value != null)
                .mapToDouble(Double::doubleValue)
                .sum();
        row.setTotalWorkHours(multiply(unitWorkHours, ticket.getQty()));
        return row;
    }

    private Map<Integer, OrderRfq> loadOrders(List<OrderDeliveryTicket> tickets) {
        Set<Integer> ids = tickets.stream()
                .map(OrderDeliveryTicket::getRfqId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        QueryWrapper<OrderRfq> qw = new QueryWrapper<>();
        qw.in("id", ids);
        return orderRfqMapper.selectList(qw).stream().collect(Collectors.toMap(OrderRfq::getId, Function.identity()));
    }

    private Map<Integer, ItemQuoteVersion> loadItems(List<OrderDeliveryTicket> tickets) {
        Set<Integer> ids = tickets.stream()
                .map(OrderDeliveryTicket::getItemId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        QueryWrapper<ItemQuoteVersion> qw = new QueryWrapper<>();
        qw.in("id", ids);
        return itemMapper.selectList(qw).stream().collect(Collectors.toMap(ItemQuoteVersion::getId, Function.identity()));
    }

    private Map<Integer, OrderProcessSheet> loadProcessSheets(List<OrderDeliveryTicket> tickets) {
        Set<Integer> itemIds = tickets.stream()
                .map(OrderDeliveryTicket::getItemId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        QueryWrapper<OrderProcessSheet> qw = new QueryWrapper<>();
        qw.in("item_id", itemIds).orderByDesc("id");
        Map<Integer, OrderProcessSheet> result = new LinkedHashMap<>();
        for (OrderProcessSheet sheet : processSheetMapper.selectList(qw)) {
            result.putIfAbsent(sheet.getItemId(), sheet);
        }
        return result;
    }

    private List<ApsDeliveryItemDTO.ProcessRow> normalizeProcessRows(Object routeObject) {
        List<Map<String, Object>> rows = normalizeRoute(routeObject);
        List<ApsDeliveryItemDTO.ProcessRow> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String processName = stringValue(row.get("processName"));
            if (processName.isEmpty()) {
                continue;
            }
            ApsDeliveryItemDTO.ProcessRow processRow = new ApsDeliveryItemDTO.ProcessRow();
            processRow.setSortNo(intValue(row.get("sortNo")));
            processRow.setProcessName(processName);
            processRow.setCalcType(stringValue(row.get("calcType")));
            processRow.setSourceType(stringValue(row.get("sourceType")));
            processRow.setInputVal(doubleValue(row.get("inputVal")));
            processRow.setWorkHours(resolveWorkHours(row));
            processRow.setUnitRate(doubleValue(row.get("unitRate")));
            processRow.setLineCost(doubleValue(row.get("lineCost")));
            result.add(processRow);
        }
        return result;
    }

    private Double resolveWorkHours(Map<String, Object> row) {
        String calcType = stringValue(row.get("calcType"));
        String sourceType = stringValue(row.get("sourceType"));
        if (NON_WORK_HOUR_FEE.equals(calcType) || "extra".equalsIgnoreCase(sourceType)) {
            return null;
        }
        return doubleValue(row.get("inputVal"));
    }

    private List<Map<String, Object>> normalizeRoute(Object routeObject) {
        if (routeObject == null) {
            return List.of();
        }
        if (routeObject instanceof List<?> rawList) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : rawList) {
                if (item instanceof Map<?, ?> rawMap) {
                    Map<String, Object> normalized = new LinkedHashMap<>();
                    rawMap.forEach((key, value) -> normalized.put(String.valueOf(key), value));
                    result.add(normalized);
                }
            }
            sortRoute(result);
            return result;
        }
        if (routeObject instanceof String text && !text.trim().isEmpty()) {
            try {
                List<Map<String, Object>> result = objectMapper.readValue(text, new TypeReference<List<Map<String, Object>>>() {
                });
                sortRoute(result);
                return result;
            } catch (Exception ignored) {
                return List.of();
            }
        }
        return List.of();
    }

    private void sortRoute(List<Map<String, Object>> rows) {
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).putIfAbsent("__index", i);
        }
        rows.sort(Comparator.comparing(
                (Map<String, Object> row) -> intValue(row.get("sortNo")),
                Comparator.nullsLast(Integer::compareTo)
        ).thenComparing(row -> intValue(row.get("__index")), Comparator.nullsLast(Integer::compareTo)));
        rows.forEach(row -> row.remove("__index"));
    }

    private String resolveMaterialType(ItemQuoteVersion item) {
        if (item == null) {
            return "";
        }
        Map<String, Object> materialData = normalizeMap(item.getMaterialData());
        return stringValue(materialData.get("shape"));
    }

    private String resolveEngineeringMaterialName(OrderDeliveryTicket ticket, ItemQuoteVersion item) {
        return firstText(
                ticket == null ? "" : ticket.getEngineeringMaterialName(),
                item == null ? "" : item.getEngineeringMaterialName()
        );
    }

    private Map<String, Object> normalizeMap(Object value) {
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> result = new LinkedHashMap<>();
            rawMap.forEach((key, mapValue) -> result.put(String.valueOf(key), mapValue));
            return result;
        }
        if (value instanceof String text && !text.trim().isEmpty()) {
            try {
                return objectMapper.readValue(text, new TypeReference<Map<String, Object>>() {
                });
            } catch (Exception ignored) {
                return Map.of();
            }
        }
        return Map.of();
    }

    private String toAbsoluteUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return "";
        }
        String trimmed = url.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        String base = publicBaseUrl == null ? "" : publicBaseUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + (trimmed.startsWith("/") ? trimmed : "/" + trimmed);
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private String normalizeProcessType(String processType) {
        String safeType = stringValue(processType).toUpperCase();
        if (safeType.isEmpty()) {
            return "";
        }
        if (!WORK_HOUR_PROCESS.equals(safeType) && !NON_WORK_HOUR_PROCESS.equals(safeType)) {
            throw new IllegalArgumentException("processType must be WORK_HOUR or NON_WORK_HOUR.");
        }
        return safeType;
    }

    private String firstText(String primary, String fallback) {
        return primary != null && !primary.trim().isEmpty() ? primary : fallback;
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private Integer intValue(Object value) {
        Double number = doubleValue(value);
        return number == null ? null : number.intValue();
    }

    private Double doubleValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double multiply(Double left, Integer right) {
        if (left == null || right == null) {
            return null;
        }
        return left * right;
    }
}
