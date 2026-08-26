package com.machining.quote.service.wms;

import com.machining.quote.dto.wms.WmsSaleLadingReserveReq;
import com.machining.quote.dto.wms.WmsSaleLadingReserveResp;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
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
    private OrderDeliveryTicketMapper ticketMapper;
    @Autowired
    private SalesOrderItemMapper salesOrderItemMapper;
    @Autowired
    private SalesOrderHeaderMapper salesOrderHeaderMapper;
    @Autowired(required = false)
    private WmsReservationFingerprintService fingerprintService = new WmsReservationFingerprintService();

    public ReservationPlan prepare(List<Integer> ticketIds) {
        ReservationPlan plan = new ReservationPlan();
        if (ticketIds == null || ticketIds.isEmpty()) {
            return plan;
        }
        Map<GroupKey, List<PreparedTicket>> byGroup = new LinkedHashMap<>();
        for (Integer ticketId : ticketIds.stream().filter(Objects::nonNull).distinct().toList()) {
            OrderDeliveryTicket ticket = ticketMapper.selectByIdForUpdate(ticketId);
            if (ticket == null) {
                plan.getFailures().add(new RowFailure(ticketId, "delivery ticket not found"));
                continue;
            }
            PreparedTicket prepared = prepareTicket(ticket, plan.getFailures());
            if (prepared == null) {
                continue;
            }
            byGroup.computeIfAbsent(new GroupKey(prepared.getSaleOrderId(), prepared.getSaleNo()), ignored -> new ArrayList<>())
                    .add(prepared);
        }
        byGroup.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> addGroup(plan, entry.getKey(), entry.getValue()));
        return plan;
    }

    public List<TicketAllocationSplit> split(ReservationGroup group, WmsSaleLadingReserveResp.Data responseData) {
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
        List<TicketAllocationSplit> result = new ArrayList<>();
        Map<String, List<TicketReservation>> ticketsByProduct = group.getTickets().stream()
                .collect(Collectors.groupingBy(
                        TicketReservation::getProductCode,
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
            List<TicketReservation> tickets = new ArrayList<>(ticketsByProduct.getOrDefault(requestItem.getProductCode(), List.of()));
            tickets.sort(ticketComparator());
            for (TicketReservation ticket : tickets) {
                BigDecimal remaining = ticket.getReserveCount();
                BigDecimal stock = min(remaining, stockBucket);
                remaining = remaining.subtract(stock);
                stockBucket = stockBucket.subtract(stock);
                BigDecimal inProduction = min(remaining, inProductionBucket);
                remaining = remaining.subtract(inProduction);
                inProductionBucket = inProductionBucket.subtract(inProduction);
                BigDecimal surplus = remaining;
                surplusBucket = surplusBucket.subtract(surplus);
                TicketAllocationSplit split = new TicketAllocationSplit();
                split.setTicketId(ticket.getTicketId());
                split.setSalesOrderItemId(ticket.getSalesOrderItemId());
                split.setSalesOrderLineNo(ticket.getSalesOrderLineNo());
                split.setProductCode(ticket.getProductCode());
                split.setReserveCount(ticket.getReserveCount());
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
                .comparing(TicketAllocationSplit::getSalesOrderLineNo, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(TicketAllocationSplit::getTicketId));
        return result;
    }

    private PreparedTicket prepareTicket(OrderDeliveryTicket ticket, List<RowFailure> failures) {
        SalesOrderItem linkedItem = salesOrderItemMapper.selectBySourceTicketIdForUpdate(ticket.getId());
        SalesOrderItem directItem = directSalesOrderItem(ticket);
        if (linkedItem != null && directItem != null && isDifferentSource(linkedItem, directItem)) {
            failures.add(new RowFailure(ticket.getId(), "sales order source ambiguous"));
            return null;
        }
        SalesOrderItem sourceItem = linkedItem != null ? linkedItem : directItem;
        if (sourceItem == null) {
            failures.add(new RowFailure(ticket.getId(), "sales order item not found"));
            return null;
        }
        if (sourceItem.getSalesOrderId() == null) {
            failures.add(new RowFailure(ticket.getId(), "sales order header not found"));
            return null;
        }
        SalesOrderHeader header = salesOrderHeaderMapper.selectByIdForUpdate(sourceItem.getSalesOrderId());
        if (header == null) {
            failures.add(new RowFailure(ticket.getId(), "sales order header not found"));
            return null;
        }
        String customerOrderNo = firstText(sourceItem.getCustomerOrderNo(), header.getCustomerOrderNo(), ticket.getCustomerOrderNo());
        if (customerOrderNo == null) {
            failures.add(new RowFailure(ticket.getId(), "customerOrderNo is required"));
            return null;
        }
        String productCode = text(ticket.getMaterialNo());
        if (productCode == null) {
            failures.add(new RowFailure(ticket.getId(), "materialNo is required"));
            return null;
        }
        if (ticket.getQty() == null || ticket.getQty() <= 0) {
            failures.add(new RowFailure(ticket.getId(), "qty must be positive"));
            return null;
        }
        String fallbackRemark = firstText(ticket.getRemark(), ticket.getMaterialName(), ticket.getDescription(), ticket.getPartNo());
        if (fallbackRemark == null) {
            failures.add(new RowFailure(ticket.getId(), "remark is required"));
            return null;
        }
        PreparedTicket prepared = new PreparedTicket();
        prepared.setTicket(ticket);
        prepared.setSaleOrderId(header.getId());
        prepared.setSaleNo(customerOrderNo);
        prepared.setSalesOrderItemId(sourceItem.getId());
        prepared.setSalesOrderLineNo(sourceItem.getLineNo());
        prepared.setProductCode(productCode);
        prepared.setReserveCount(BigDecimal.valueOf(ticket.getQty()));
        prepared.setFallbackRemark(fallbackRemark);
        prepared.setCustomerOrderNo(customerOrderNo);
        prepared.setCustomerProjectNo(firstText(sourceItem.getCustomerProjectNo(), header.getCustomerProjectNo(), ticket.getCustomerProjectNo()));
        return prepared;
    }

    private SalesOrderItem directSalesOrderItem(OrderDeliveryTicket ticket) {
        if (!"SALES_ORDER".equalsIgnoreCase(text(ticket.getSourceType())) || ticket.getSourceItemId() == null) {
            return null;
        }
        return salesOrderItemMapper.selectByIdForUpdate(ticket.getSourceItemId());
    }

    private boolean isDifferentSource(SalesOrderItem left, SalesOrderItem right) {
        return !Objects.equals(left.getId(), right.getId())
                || !Objects.equals(left.getSalesOrderId(), right.getSalesOrderId());
    }

    private void addGroup(ReservationPlan plan, GroupKey key, List<PreparedTicket> preparedTickets) {
        preparedTickets.sort(preparedComparator());
        ReservationGroup group = new ReservationGroup();
        group.setSaleOrderId(key.saleOrderId());
        group.setSaleNo(key.saleNo());
        Map<String, List<PreparedTicket>> byProduct = preparedTickets.stream()
                .collect(Collectors.groupingBy(
                        PreparedTicket::getProductCode,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        byProduct.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> addItem(group, entry.getKey(), entry.getValue(), plan.getFailures()));
        if (group.getItems().isEmpty()) {
            return;
        }
        group.getTickets().sort(ticketComparator());
        WmsSaleLadingReserveReq request = new WmsSaleLadingReserveReq();
        request.setExternalBizId(null);
        request.setSaleNo(group.getSaleNo());
        request.setItems(group.getItems().stream().map(this::toRequestItem).toList());
        group.setRequest(request);
        group.setRequestFingerprint(fingerprintService.fingerprint(group.getSaleNo(), request.getItems()));
        plan.getGroups().add(group);
    }

    private void addItem(ReservationGroup group, String productCode, List<PreparedTicket> tickets, List<RowFailure> failures) {
        tickets.sort(preparedComparator());
        int failuresBefore = failures.size();
        String customerProjectNo = consistentValue(productCode, tickets, PreparedTicket::getCustomerProjectNo, "customerProjectNo", failures);
        String customerOrderNo = consistentValue(productCode, tickets, PreparedTicket::getCustomerOrderNo, "customerOrderNo", failures);
        if (failures.size() > failuresBefore) {
            return;
        }
        BigDecimal reserveCount = tickets.stream()
                .map(PreparedTicket::getReserveCount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        ReservationItem item = new ReservationItem();
        item.setProductCode(productCode);
        item.setReserveCount(reserveCount);
        item.setRemark(remark(tickets));
        item.setCustomerProjectNo(customerProjectNo);
        item.setCustomerOrderNo(customerOrderNo);
        group.getItems().add(item);
        for (PreparedTicket ticket : tickets) {
            TicketReservation reservation = new TicketReservation();
            reservation.setTicketId(ticket.getTicket().getId());
            reservation.setSalesOrderItemId(ticket.getSalesOrderItemId());
            reservation.setSalesOrderLineNo(ticket.getSalesOrderLineNo());
            reservation.setProductCode(productCode);
            reservation.setReserveCount(ticket.getReserveCount());
            group.getTickets().add(reservation);
        }
    }

    private String consistentValue(
            String productCode,
            List<PreparedTicket> tickets,
            Function<PreparedTicket, String> extractor,
            String fieldName,
            List<RowFailure> failures) {
        List<String> values = tickets.stream()
                .map(extractor)
                .map(this::text)
                .distinct()
                .toList();
        if (values.size() <= 1) {
            return values.isEmpty() ? null : values.get(0);
        }
        for (PreparedTicket ticket : tickets) {
            failures.add(new RowFailure(ticket.getTicket().getId(),
                    fieldName + " is inconsistent for productCode=" + productCode));
        }
        return null;
    }

    private String remark(List<PreparedTicket> tickets) {
        String ticketRemark = tickets.stream()
                .map(ticket -> text(ticket.getTicket().getRemark()))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        if (ticketRemark != null) {
            return ticketRemark;
        }
        List<Function<OrderDeliveryTicket, String>> fallbackExtractors = List.of(
                OrderDeliveryTicket::getMaterialName,
                OrderDeliveryTicket::getDescription,
                OrderDeliveryTicket::getPartNo
        );
        for (Function<OrderDeliveryTicket, String> extractor : fallbackExtractors) {
            String fallback = tickets.stream()
                    .map(ticket -> text(extractor.apply(ticket.getTicket())))
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            if (fallback != null) {
                return fallback;
            }
        }
        return tickets.get(0).getFallbackRemark();
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

    private Comparator<PreparedTicket> preparedComparator() {
        return Comparator
                .comparing(PreparedTicket::getSalesOrderLineNo, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(ticket -> ticket.getTicket().getId());
    }

    private Comparator<TicketReservation> ticketComparator() {
        return Comparator
                .comparing(TicketReservation::getSalesOrderLineNo, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(TicketReservation::getTicketId);
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

    private record GroupKey(Long saleOrderId, String saleNo) implements Comparable<GroupKey> {
        @Override
        public int compareTo(GroupKey other) {
            int idCompare = Comparator.nullsLast(Long::compareTo).compare(saleOrderId, other.saleOrderId);
            if (idCompare != 0) {
                return idCompare;
            }
            return saleNo.compareTo(other.saleNo);
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
        private List<TicketReservation> tickets = new ArrayList<>();
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
    public static class TicketReservation {
        private Integer ticketId;
        private Long salesOrderItemId;
        private Integer salesOrderLineNo;
        private String productCode;
        private BigDecimal reserveCount;
    }

    @Data
    private static class PreparedTicket {
        private OrderDeliveryTicket ticket;
        private Long saleOrderId;
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
    public static class TicketAllocationSplit {
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
        private final Integer ticketId;
        private final String message;
    }
}
