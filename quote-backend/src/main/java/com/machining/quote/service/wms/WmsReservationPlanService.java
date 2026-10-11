package com.machining.quote.service.wms;

import com.machining.quote.dto.wms.WmsSaleLadingReserveReq;
import com.machining.quote.dto.wms.WmsSaleLadingReserveResp;
import com.machining.quote.entity.OrderDeliveryTicketAllocation;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.OrderDeliveryTicketAllocationMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class WmsReservationPlanService {
    @Autowired
    private OrderDeliveryTicketAllocationMapper allocationMapper;
    @Autowired
    private SalesOrderItemMapper salesOrderItemMapper;
    @Autowired
    private SalesOrderHeaderMapper salesOrderHeaderMapper;
    @Autowired(required = false)
    private WmsReservationFingerprintService fingerprintService = new WmsReservationFingerprintService();

    public ReservationPlan prepareAllocations(List<Integer> allocationIds) {
        ReservationPlan plan = new ReservationPlan();
        if (allocationIds == null || allocationIds.isEmpty()) {
            return plan;
        }
        Map<GroupKey, List<PreparedAllocation>> byGroup = new LinkedHashMap<>();
        for (Integer allocationId : allocationIds.stream().filter(Objects::nonNull).distinct().toList()) {
            PreparedAllocation prepared = prepareAllocation(allocationId, plan.getFailures());
            if (prepared == null) {
                continue;
            }
            byGroup.computeIfAbsent(new GroupKey(
                    prepared.getSalesOrderId(),
                    prepared.getSaleNo(),
                    prepared.getCustomerProjectNo()), ignored -> new ArrayList<>())
                    .add(prepared);
        }
        byGroup.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> addGroup(plan, entry.getKey(), entry.getValue()));
        return plan;
    }

    public List<AllocationSplit> split(ReservationGroup group, WmsSaleLadingReserveResp.Data responseData) {
        if (group == null || responseData == null || responseData.getItems() == null) {
            throw new IllegalArgumentException("WMS reservation split requires group and response data");
        }
        Map<String, WmsSaleLadingReserveResp.Item> responseByProduct = responseData.getItems().stream()
                .collect(Collectors.toMap(
                        WmsSaleLadingReserveResp.Item::getProductCode,
                        Function.identity(),
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));
        List<AllocationSplit> result = new ArrayList<>();
        Map<String, List<AllocationReservation>> allocationsByProduct = group.getAllocations().stream()
                .collect(Collectors.groupingBy(
                        AllocationReservation::getProductCode,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        for (ReservationItem requestItem : group.getItems()) {
            WmsSaleLadingReserveResp.Item responseItem = responseByProduct.get(requestItem.getProductCode());
            if (responseItem == null) {
                throw new IllegalArgumentException("WMS reservation response missing productCode=" + requestItem.getProductCode());
            }
            BigDecimal stockBucket = responseItem.getFrozenCount().subtract(responseItem.getInProductionCount());
            BigDecimal inProductionBucket = responseItem.getInProductionCount();
            BigDecimal surplusBucket = responseItem.getSurplusCount();
            List<AllocationReservation> allocations = new ArrayList<>(
                    allocationsByProduct.getOrDefault(requestItem.getProductCode(), List.of()));
            allocations.sort(allocationComparator());
            for (AllocationReservation allocation : allocations) {
                BigDecimal remaining = allocation.getReserveCount();
                BigDecimal stock = min(remaining, stockBucket);
                remaining = remaining.subtract(stock);
                stockBucket = stockBucket.subtract(stock);
                BigDecimal inProduction = min(remaining, inProductionBucket);
                remaining = remaining.subtract(inProduction);
                inProductionBucket = inProductionBucket.subtract(inProduction);
                BigDecimal surplus = remaining;
                surplusBucket = surplusBucket.subtract(surplus);
                AllocationSplit split = new AllocationSplit();
                split.setAllocationId(allocation.getAllocationId());
                split.setTicketId(allocation.getTicketId());
                split.setSalesOrderItemId(allocation.getSalesOrderItemId());
                split.setSalesOrderLineNo(allocation.getSalesOrderLineNo());
                split.setProductCode(allocation.getProductCode());
                split.setReserveCount(allocation.getReserveCount());
                split.setStockFrozenCount(stock);
                split.setInProductionCount(inProduction);
                split.setSurplusCount(surplus);
                result.add(split);
            }
            if (stockBucket.signum() != 0 || inProductionBucket.signum() != 0 || surplusBucket.signum() != 0) {
                throw new IllegalArgumentException("WMS reservation split bucket not fully consumed productCode=" + requestItem.getProductCode());
            }
        }
        result.sort(Comparator
                .comparing(AllocationSplit::getSalesOrderLineNo, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(AllocationSplit::getAllocationId, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(AllocationSplit::getTicketId, Comparator.nullsLast(Integer::compareTo)));
        return result;
    }

    private PreparedAllocation prepareAllocation(Integer allocationId, List<RowFailure> failures) {
        OrderDeliveryTicketAllocation allocation = allocationMapper.selectByIdForUpdate(allocationId);
        if (allocation == null) {
            failures.add(new RowFailure(allocationId, null, "production allocation not found"));
            return null;
        }
        if (!"PRODUCTION_REQUIRED".equalsIgnoreCase(text(allocation.getAllocationType()))
                || !"ACTIVE".equalsIgnoreCase(firstText(allocation.getStatus(), "ACTIVE"))) {
            failures.add(new RowFailure(allocationId, allocation.getTicketId(), "allocation is not an active production row"));
            return null;
        }
        if (allocation.getAllocationSplitSource() != null) {
            failures.add(new RowFailure(allocationId, allocation.getTicketId(), "allocation has already completed an initial split"));
            return null;
        }
        SalesOrderItem sourceItem = salesOrderItemMapper.selectByAllocationIdForUpdate(allocationId.longValue());
        if (sourceItem == null || sourceItem.getSalesOrderId() == null) {
            failures.add(new RowFailure(allocationId, allocation.getTicketId(), "sales order item not found for allocation"));
            return null;
        }
        SalesOrderHeader header = salesOrderHeaderMapper.selectByIdForUpdate(sourceItem.getSalesOrderId());
        if (header == null) {
            failures.add(new RowFailure(allocationId, allocation.getTicketId(), "sales order header not found"));
            return null;
        }
        String customerOrderNo = firstText(sourceItem.getCustomerOrderNo(), header.getCustomerOrderNo(), allocation.getCustomerOrderNo());
        if (customerOrderNo == null) {
            failures.add(new RowFailure(allocationId, allocation.getTicketId(), "customerOrderNo is required"));
            return null;
        }
        String productCode = text(allocation.getMaterialNo());
        if (productCode == null) {
            failures.add(new RowFailure(allocationId, allocation.getTicketId(), "materialNo is required"));
            return null;
        }
        if (allocation.getQty() == null || allocation.getQty() <= 0) {
            failures.add(new RowFailure(allocationId, allocation.getTicketId(), "qty must be positive"));
            return null;
        }
        if (allocation.getTicketId() == null) {
            failures.add(new RowFailure(allocationId, null, "source delivery ticket is required for traceability"));
            return null;
        }
        String fallbackRemark = firstText(allocation.getRemark(), allocation.getMaterialName(), allocation.getDescription(), allocation.getPartNo());
        if (fallbackRemark == null) {
            failures.add(new RowFailure(allocationId, allocation.getTicketId(), "remark is required"));
            return null;
        }
        PreparedAllocation prepared = new PreparedAllocation();
        prepared.setAllocation(allocation);
        prepared.setSalesOrderId(header.getId());
        prepared.setSaleNo(customerOrderNo);
        prepared.setSalesOrderItemId(sourceItem.getId());
        prepared.setSalesOrderLineNo(sourceItem.getLineNo());
        prepared.setProductCode(productCode);
        prepared.setReserveCount(BigDecimal.valueOf(allocation.getQty()));
        prepared.setFallbackRemark(fallbackRemark);
        prepared.setCustomerOrderNo(customerOrderNo);
        prepared.setCustomerProjectNo(firstText(sourceItem.getCustomerProjectNo(), header.getCustomerProjectNo(), allocation.getCustomerProjectNo()));
        return prepared;
    }

    private void addGroup(ReservationPlan plan, GroupKey key, List<PreparedAllocation> preparedAllocations) {
        preparedAllocations.sort(preparedComparator());
        ReservationGroup group = new ReservationGroup();
        group.setSaleOrderId(key.salesOrderId());
        group.setSaleNo(key.saleNo());
        Map<String, List<PreparedAllocation>> byProduct = preparedAllocations.stream()
                .collect(Collectors.groupingBy(
                        PreparedAllocation::getProductCode,
                        LinkedHashMap::new,
                        Collectors.toList()));
        byProduct.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> addItem(group, entry.getKey(), entry.getValue(), plan.getFailures()));
        if (group.getItems().isEmpty()) {
            return;
        }
        group.getAllocations().sort(allocationComparator());
        WmsSaleLadingReserveReq request = new WmsSaleLadingReserveReq();
        request.setExternalBizId(null);
        request.setSaleNo(group.getSaleNo());
        request.setItems(group.getItems().stream().map(this::toRequestItem).toList());
        group.setRequest(request);
        group.setRequestFingerprint(fingerprintService.fingerprint(group.getSaleNo(), request.getItems()));
        plan.getGroups().add(group);
    }

    private void addItem(ReservationGroup group, String productCode, List<PreparedAllocation> allocations, List<RowFailure> failures) {
        allocations.sort(preparedComparator());
        int failuresBefore = failures.size();
        String customerProjectNo = consistentValue(productCode, allocations, PreparedAllocation::getCustomerProjectNo, "customerProjectNo", failures);
        String customerOrderNo = consistentValue(productCode, allocations, PreparedAllocation::getCustomerOrderNo, "customerOrderNo", failures);
        if (failures.size() > failuresBefore) {
            return;
        }
        BigDecimal reserveCount = allocations.stream()
                .map(PreparedAllocation::getReserveCount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        ReservationItem item = new ReservationItem();
        item.setProductCode(productCode);
        item.setReserveCount(reserveCount);
        item.setRemark(remark(allocations));
        item.setCustomerProjectNo(customerProjectNo);
        item.setCustomerOrderNo(customerOrderNo);
        group.getItems().add(item);
        for (PreparedAllocation allocation : allocations) {
            AllocationReservation reservation = new AllocationReservation();
            reservation.setAllocationId(allocation.getAllocation().getId());
            reservation.setTicketId(allocation.getAllocation().getTicketId());
            reservation.setSalesOrderItemId(allocation.getSalesOrderItemId());
            reservation.setSalesOrderLineNo(allocation.getSalesOrderLineNo());
            reservation.setProductCode(productCode);
            reservation.setReserveCount(allocation.getReserveCount());
            group.getAllocations().add(reservation);
        }
    }

    private String consistentValue(
            String productCode,
            List<PreparedAllocation> allocations,
            Function<PreparedAllocation, String> extractor,
            String fieldName,
            List<RowFailure> failures) {
        List<String> values = allocations.stream().map(extractor).map(this::text).distinct().toList();
        if (values.size() <= 1) {
            return values.isEmpty() ? null : values.get(0);
        }
        for (PreparedAllocation allocation : allocations) {
            failures.add(new RowFailure(allocation.getAllocation().getId(), allocation.getAllocation().getTicketId(),
                    fieldName + " is inconsistent for productCode=" + productCode));
        }
        return null;
    }

    private String remark(List<PreparedAllocation> allocations) {
        List<Function<PreparedAllocation, String>> extractors = List.of(
                row -> row.getAllocation().getRemark(),
                row -> row.getAllocation().getMaterialName(),
                row -> row.getAllocation().getDescription(),
                row -> row.getAllocation().getPartNo());
        for (Function<PreparedAllocation, String> extractor : extractors) {
            String value = allocations.stream()
                    .map(extractor)
                    .map(this::text)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private WmsSaleLadingReserveReq.Item toRequestItem(ReservationItem item) {
        WmsSaleLadingReserveReq.Item requestItem = new WmsSaleLadingReserveReq.Item();
        requestItem.setProductCode(item.getProductCode());
        requestItem.setReserveCount(item.getReserveCount());
        requestItem.setRemark(item.getRemark());
        requestItem.setCustomerProjectNo(item.getCustomerProjectNo());
        requestItem.setCustomerOrderNo(item.getCustomerOrderNo());
        return requestItem;
    }

    private Comparator<PreparedAllocation> preparedComparator() {
        return Comparator.comparing(PreparedAllocation::getSalesOrderLineNo, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(row -> row.getAllocation().getId(), Comparator.nullsLast(Integer::compareTo));
    }

    private Comparator<AllocationReservation> allocationComparator() {
        return Comparator.comparing(AllocationReservation::getSalesOrderLineNo, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(AllocationReservation::getAllocationId, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(AllocationReservation::getTicketId, Comparator.nullsLast(Integer::compareTo));
    }

    private BigDecimal min(BigDecimal left, BigDecimal right) {
        return left.compareTo(right) <= 0 ? left : right;
    }

    private String firstText(String... values) {
        for (String value : values) {
            String text = text(value);
            if (text != null) {
                return text;
            }
        }
        return null;
    }

    private String text(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record GroupKey(Long salesOrderId, String saleNo, String customerProjectNo) implements Comparable<GroupKey> {
        @Override
        public int compareTo(GroupKey other) {
            int idCompare = Comparator.nullsLast(Long::compareTo).compare(salesOrderId, other.salesOrderId);
            if (idCompare != 0) {
                return idCompare;
            }
            int saleNoCompare = saleNo.compareTo(other.saleNo);
            if (saleNoCompare != 0) {
                return saleNoCompare;
            }
            return Comparator.nullsLast(String::compareTo).compare(customerProjectNo, other.customerProjectNo);
        }
    }

    @Data
    public static class ReservationPlan {
        private List<ReservationGroup> groups = new ArrayList<>();
        private List<RowFailure> failures = new ArrayList<>();
    }

    @Data
    public static class ReservationGroup {
        private Long saleOrderId;
        private String saleNo;
        private List<ReservationItem> items = new ArrayList<>();
        private List<AllocationReservation> allocations = new ArrayList<>();
        private WmsSaleLadingReserveReq request;
        private String requestFingerprint;
    }

    @Data
    public static class ReservationItem {
        private String productCode;
        private BigDecimal reserveCount;
        private String remark;
        private String customerProjectNo;
        private String customerOrderNo;
    }

    @Data
    public static class AllocationReservation {
        private Integer allocationId;
        private Integer ticketId;
        private Long salesOrderItemId;
        private Integer salesOrderLineNo;
        private String productCode;
        private BigDecimal reserveCount;
    }

    @Data
    private static class PreparedAllocation {
        private OrderDeliveryTicketAllocation allocation;
        private Long salesOrderId;
        private String saleNo;
        private Long salesOrderItemId;
        private Integer salesOrderLineNo;
        private String productCode;
        private BigDecimal reserveCount;
        private String fallbackRemark;
        private String customerProjectNo;
        private String customerOrderNo;
    }

    @Data
    public static class AllocationSplit {
        private Integer allocationId;
        private Integer ticketId;
        private Long salesOrderItemId;
        private Integer salesOrderLineNo;
        private String productCode;
        private BigDecimal reserveCount;
        private BigDecimal stockFrozenCount;
        private BigDecimal inProductionCount;
        private BigDecimal surplusCount;
    }

    @Data
    public static class RowFailure {
        private final Integer allocationId;
        private final Integer ticketId;
        private final String message;
    }
}
