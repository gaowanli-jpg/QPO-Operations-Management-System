package com.machining.quote.service.aps;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.machining.quote.dto.aps.ApsDeliveryItemBatchDTO;
import com.machining.quote.dto.aps.ApsDeliveryItemDTO;
import com.machining.quote.dto.aps.ApsProcessDTO;
import com.machining.quote.entity.DictExtraFeeItem;
import com.machining.quote.entity.DictProcessCategory;
import com.machining.quote.entity.DictProcessDetail;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.DictExtraFeeItemMapper;
import com.machining.quote.mapper.DictProcessCategoryMapper;
import com.machining.quote.mapper.DictProcessDetailMapper;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
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
    private ItemQuoteVersionMapper itemMapper;
    @Autowired
    private OrderRfqMapper orderRfqMapper;
    @Autowired
    private DictProcessCategoryMapper processCategoryMapper;
    @Autowired
    private DictProcessDetailMapper processDetailMapper;
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

        List<ApsDeliveryItemDTO> records = tickets.stream()
                .map(ticket -> buildRow(ticket, orderMap.get(ticket.getRfqId()), itemMap.get(ticket.getItemId())))
                .toList();

        ApsDeliveryItemBatchDTO result = new ApsDeliveryItemBatchDTO();
        result.setRecords(records);
        result.setCount(records.size());
        result.setHasMore(hasMore);
        result.setNextAfterTicketId(records.isEmpty() ? afterTicketId : records.get(records.size() - 1).getTicketId());
        return result;
    }

    private List<ApsProcessDTO> listWorkHourProcesses() {
        QueryWrapper<DictProcessDetail> qw = new QueryWrapper<>();
        qw.eq("enabled", true).orderByAsc("sort_no").orderByAsc("id");
        List<DictProcessDetail> details = processDetailMapper.selectList(qw);
        Set<Long> categoryIds = details.stream()
                .map(DictProcessDetail::getCategoryId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<Long, DictProcessCategory> categories = loadProcessCategories(categoryIds);
        return details.stream()
                .filter(detail -> !stringValue(detail.getDetailName()).isEmpty())
                .filter(detail -> {
                    DictProcessCategory category = categories.get(detail.getCategoryId());
                    return category != null && !Boolean.FALSE.equals(category.getEnabled());
                })
                .map(detail -> {
                    DictProcessCategory category = categories.get(detail.getCategoryId());
                    ApsProcessDTO row = new ApsProcessDTO();
                    row.setSourceId(toSafeInteger(detail.getId()));
                    row.setProcessName(detail.getDetailName().trim());
                    row.setProcessType(WORK_HOUR_PROCESS);
                    row.setCalcType(WORK_HOUR);
                    row.setSortNo(firstInt(detail.getSortNo(), category.getSortNo()));
                    return row;
                })
                .toList();
    }

    private Map<Long, DictProcessCategory> loadProcessCategories(Set<Long> categoryIds) {
        if (categoryIds.isEmpty()) {
            return Map.of();
        }
        QueryWrapper<DictProcessCategory> qw = new QueryWrapper<>();
        qw.in("id", categoryIds);
        return processCategoryMapper.selectList(qw).stream()
                .collect(Collectors.toMap(DictProcessCategory::getId, Function.identity(), (left, right) -> left));
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

    private ApsDeliveryItemDTO buildRow(OrderDeliveryTicket ticket, OrderRfq order, ItemQuoteVersion item) {
        ApsDeliveryItemDTO row = new ApsDeliveryItemDTO();
        row.setTicketId(ticket.getId());
        row.setRfqId(ticket.getRfqId());
        row.setItemId(ticket.getItemId());

        row.setRfqNo(order == null ? "" : order.getRfqNo());
        row.setCustomerName(order == null ? "" : order.getCustomerName());
        row.setOrderStatus(order == null ? "" : order.getStatus());
        row.setInternalProjectNo(ticket.getInternalProjectNo());
        row.setCustomerOrderNo(ticket.getCustomerOrderNo());
        row.setCustomerProjectNo(ticket.getCustomerProjectNo());
        row.setProjectNo(ticket.getProjectNo());
        row.setOrderType(ticket.getOrderType());
        row.setItemType(ticket.getItemType());

        row.setPartNo(ticket.getPartNo());
        row.setVersion(ticket.getVersion());
        row.setDescription(ticket.getDescription());
        row.setQty(ticket.getQty());
        row.setSourceItemQty(item == null ? null : item.getQty());
        row.setUnit(item == null ? "" : item.getUnit());

        Object materialSource = ticket.getMaterialData();
        row.setMaterialName(ticket == null ? "" : ticket.getEngineeringMaterialName());
        row.setMaterialType(resolveMaterialType(materialSource));
        row.setMaterialGrossWeight(ticket.getMaterialGrossWeight());
        row.setMaterialDemandWeight(multiply(row.getMaterialGrossWeight(), ticket.getQty()));
        row.setPartNetWeight(ticket.getPartNetWeight());
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

        row.setMaterialData(materialSource);
        Object routeSource = ticket.getProcessRoute();
        row.setProcessSnapshotSource("ORDER_DELIVERY_TICKET");
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

    private String resolveMaterialType(Object materialSource) {
        Map<String, Object> materialData = normalizeMap(materialSource);
        return stringValue(materialData.get("shape"));
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

    private Integer firstInt(Integer primary, Integer fallback) {
        return primary != null ? primary : fallback;
    }

    private Integer toSafeInteger(Long value) {
        if (value == null || value > Integer.MAX_VALUE || value < Integer.MIN_VALUE) {
            return null;
        }
        return value.intValue();
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
