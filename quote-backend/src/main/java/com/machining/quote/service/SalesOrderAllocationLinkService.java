package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.ReleaseSourceType;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderDeliveryTicketAllocation;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.OrderDeliveryTicketAllocationMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class SalesOrderAllocationLinkService {
    private static final String TYPE_PRODUCTION = "PRODUCTION_REQUIRED";
    private static final String TYPE_IN_STOCK = "IN_STOCK_LOCK";
    private static final String TYPE_IN_PRODUCTION = "IN_PRODUCTION_LOCK";

    public record AllocationQuantitySyncResult(
            Integer allocationId,
            Long salesOrderItemId,
            Integer oldQty,
            Integer newQty,
            boolean ticketSynced) {
    }

    @Autowired
    private SalesOrderHeaderMapper headerMapper;
    @Autowired
    private SalesOrderItemMapper itemMapper;
    @Autowired
    private OrderDeliveryTicketMapper ticketMapper;
    @Autowired
    private OrderDeliveryTicketAllocationMapper allocationMapper;

    public SalesOrderItem lockItemForMutation(Long salesOrderId, Long itemId) {
        SalesOrderItem snapshot = itemMapper.selectById(itemId);
        assertItemBelongsToOrder(snapshot, salesOrderId, itemId);
        if (snapshot.getAllocationId() != null) {
            OrderDeliveryTicketAllocation allocation = allocationMapper.selectById(snapshot.getAllocationId().intValue());
            if (allocation != null && allocation.getTicketId() != null) {
                ticketMapper.selectByIdForUpdate(allocation.getTicketId());
            }
            allocationMapper.selectByIdForUpdate(snapshot.getAllocationId().intValue());
        }
        return itemMapper.selectByIdForUpdate(itemId);
    }

    public void lockSalesOrderForTicket(Integer ticketId) {
        OrderDeliveryTicket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            return;
        }
        Long salesOrderId = resolveSalesOrderId(ticket);
        if (salesOrderId != null) {
            headerMapper.selectByIdForUpdate(salesOrderId);
        }
    }

    public OrderDeliveryTicket lockTicketForMaterialSuccess(Integer ticketId) {
        OrderDeliveryTicket snapshot = ticketMapper.selectById(ticketId);
        if (snapshot == null) {
            return null;
        }
        SalesOrderItem sourceItem = resolveSourceItem(snapshot, false);
        if (sourceItem != null && sourceItem.getSalesOrderId() != null) {
            headerMapper.selectByIdForUpdate(sourceItem.getSalesOrderId());
        }
        OrderDeliveryTicket currentSnapshot = ticketMapper.selectById(ticketId);
        if (currentSnapshot == null) {
            return null;
        }
        if (ReleaseSourceType.SALES_ORDER.name().equalsIgnoreCase(defaultText(currentSnapshot.getSourceType(), ""))
                && currentSnapshot.getSourceItemId() != null) {
            return ticketMapper.selectSalesOrderTicketsBySourceItemIdForUpdate(currentSnapshot.getSourceItemId()).stream()
                    .filter(row -> Objects.equals(row.getId(), ticketId))
                    .findFirst()
                    .orElse(null);
        }
        return ticketMapper.selectByIdForUpdate(ticketId);
    }

    public void linkMaterialAllocation(OrderDeliveryTicket ticket, OrderDeliveryTicketAllocation allocation) {
        if (ticket == null || allocation == null || allocation.getId() == null) {
            return;
        }
        SalesOrderItem item = resolveSourceItem(ticket, false);
        if (item == null) {
            return;
        }
        if (ReleaseSourceType.SALES_ORDER.name().equalsIgnoreCase(defaultText(ticket.getSourceType(), ""))) {
            item = splitDirectSourceItemByTickets(ticket, item);
        }
        if (Objects.equals(item.getAllocationId(), allocation.getId().longValue())) {
            return;
        }
        if (item.getAllocationId() != null) {
            throw new RuntimeException("销售订单零件行已关联其他 allocation，无法重复关联：" + item.getId());
        }
        item.setAllocationId(allocation.getId().longValue());
        item.setAllocationType(allocation.getAllocationType());
        item.setQty(allocation.getQty());
        item.setReleasedQty(allocation.getQty());
        item.setRemainingQty(0);
        item.setUnshippedQty(Math.max(0, safeInt(allocation.getQty()) - safeInt(item.getShippedQty())));
        item.setUpdatedAt(LocalDateTime.now());
        itemMapper.updateById(item);
    }

    private SalesOrderItem splitDirectSourceItemByTickets(OrderDeliveryTicket currentTicket, SalesOrderItem sourceItem) {
        List<OrderDeliveryTicket> tickets = ticketMapper.selectSalesOrderTicketsBySourceItemIdForUpdate(sourceItem.getId());
        if (tickets == null || tickets.isEmpty()) {
            tickets = List.of(currentTicket);
        }
        List<OrderDeliveryTicket> ordered = tickets.stream()
                .filter(Objects::nonNull)
                .filter(row -> row.getId() != null && row.getQty() != null && row.getQty() > 0)
                .sorted(Comparator.comparing(OrderDeliveryTicket::getId))
                .toList();
        if (ordered.stream().noneMatch(row -> Objects.equals(row.getId(), currentTicket.getId()))) {
            throw new RuntimeException("销售订单提单行无法关联到其源零件行：ticket=" + currentTicket.getId());
        }
        int ticketQty = ordered.stream().mapToInt(OrderDeliveryTicket::getQty).sum();
        int originalQty = safeInt(sourceItem.getQty());
        int originalShippedQty = safeInt(sourceItem.getShippedQty());
        if (ticketQty > originalQty) {
            throw new RuntimeException("销售订单提单总量超过零件行数量，无法建立拆行关联：item=" + sourceItem.getId());
        }
        boolean alreadyMapped = ordered.stream().allMatch(row -> {
            SalesOrderItem linked = itemMapper.selectBySourceTicketIdForUpdate(row.getId());
            return linked != null;
        });
        if (alreadyMapped) {
            SalesOrderItem mapped = itemMapper.selectBySourceTicketIdForUpdate(currentTicket.getId());
            if (mapped != null) {
                currentTicket.setSourceItemId(mapped.getId());
                return mapped;
            }
        }
        int nextLineNo = nextLineNo(sourceItem.getSalesOrderId());
        OrderDeliveryTicket firstTicket = ordered.get(0);
        int firstQty = firstTicket.getQty();
        setTicketLineQuantity(sourceItem, firstQty, Math.min(safeInt(sourceItem.getShippedQty()), firstQty));
        sourceItem.setSourceTicketId(firstTicket.getId());
        sourceItem.setStatus(QuoteStatusWorkflow.RELEASED);
        sourceItem.setUpdatedAt(LocalDateTime.now());
        firstTicket.setSourceItemId(sourceItem.getId());
        ticketMapper.updateById(firstTicket);

        Map<Integer, SalesOrderItem> ticketItems = new java.util.LinkedHashMap<>();
        ticketItems.put(firstTicket.getId(), sourceItem);
        int shippedBefore = Math.min(originalShippedQty, firstQty);
        for (int index = 1; index < ordered.size(); index++) {
            OrderDeliveryTicket sibling = ordered.get(index);
            int qty = sibling.getQty();
            int shippedQty = Math.min(Math.max(0, originalShippedQty - shippedBefore), qty);
            SalesOrderItem splitItem = copyTicketItem(sourceItem, sibling, qty, shippedQty, nextLineNo++);
            itemMapper.insert(splitItem);
            sibling.setSourceItemId(splitItem.getId());
            ticketMapper.updateById(sibling);
            ticketItems.put(sibling.getId(), splitItem);
            shippedBefore += shippedQty;
        }
        int remainderQty = Math.max(0, originalQty - ticketQty);
        if (remainderQty > 0) {
            int remainderShipped = Math.max(0, originalShippedQty - shippedBefore);
            SalesOrderItem remainder = copyRemainderItem(sourceItem, remainderQty, remainderShipped, nextLineNo);
            itemMapper.insert(remainder);
        }
        QueryWrapper<SalesOrderItem> countQuery = new QueryWrapper<>();
        countQuery.eq("sales_order_id", sourceItem.getSalesOrderId());
        SalesOrderHeader header = headerMapper.selectById(sourceItem.getSalesOrderId());
        if (header != null) {
            header.setTotalItemCount(Math.toIntExact(itemMapper.selectCount(countQuery)));
            header.setUpdatedAt(LocalDateTime.now());
            headerMapper.updateById(header);
        }
        SalesOrderItem linkedCurrent = ticketItems.get(currentTicket.getId());
        currentTicket.setSourceItemId(linkedCurrent.getId());
        return linkedCurrent;
    }

    private SalesOrderItem copyTicketItem(SalesOrderItem source, OrderDeliveryTicket ticket, int qty, int shippedQty, int lineNo) {
        SalesOrderItem item = copySalesOrderItem(source, lineNo);
        item.setSourceTicketId(ticket.getId());
        setTicketLineQuantity(item, qty, shippedQty);
        item.setStatus(QuoteStatusWorkflow.RELEASED);
        return item;
    }

    private SalesOrderItem copyRemainderItem(SalesOrderItem source, int qty, int shippedQty, int lineNo) {
        SalesOrderItem item = copySalesOrderItem(source, lineNo);
        item.setSourceTicketId(null);
        item.setReleasedQty(0);
        item.setRemainingQty(qty);
        item.setQty(qty);
        item.setShippedQty(shippedQty);
        item.setUnshippedQty(Math.max(0, qty - shippedQty));
        item.setTaxIncludedTotalPrice(total(source.getTaxIncludedUnitPrice(), qty));
        item.setUntaxedTotalPrice(total(source.getUntaxedUnitPrice(), qty));
        item.setStatus(QuoteStatusWorkflow.PARTIALLY_RELEASED);
        return item;
    }

    private SalesOrderItem copySalesOrderItem(SalesOrderItem source, int lineNo) {
        SalesOrderItem item = new SalesOrderItem();
        BeanUtils.copyProperties(source, item);
        item.setId(null);
        item.setLineNo(lineNo);
        item.setAllocationId(null);
        item.setAllocationType(null);
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        return item;
    }

    private void setTicketLineQuantity(SalesOrderItem item, int qty, int shippedQty) {
        item.setQty(qty);
        item.setShippedQty(shippedQty);
        item.setReleasedQty(qty);
        item.setRemainingQty(0);
        item.setUnshippedQty(Math.max(0, qty - shippedQty));
        item.setTaxIncludedTotalPrice(total(item.getTaxIncludedUnitPrice(), qty));
        item.setUntaxedTotalPrice(total(item.getUntaxedUnitPrice(), qty));
    }

    public void syncWmsAllocations(OrderDeliveryTicket ticket, List<OrderDeliveryTicketAllocation> allocations) {
        if (ticket == null || allocations == null || allocations.isEmpty()) {
            return;
        }
        SalesOrderItem sourceItem = resolveSourceItem(ticket, true);
        if (sourceItem == null || sourceItem.getSalesOrderId() == null) {
            return;
        }
        SalesOrderHeader header = headerMapper.selectByIdForUpdate(sourceItem.getSalesOrderId());
        if (header == null) {
            return;
        }
        SalesOrderItem productionItem = sourceItem;
        int nextLineNo = nextLineNo(header.getId());
        List<OrderDeliveryTicketAllocation> ordered = allocations.stream()
                .filter(Objects::nonNull)
                .filter(row -> "ACTIVE".equalsIgnoreCase(defaultText(row.getStatus(), "ACTIVE")))
                .sorted(Comparator.comparing(OrderDeliveryTicketAllocation::getId,
                        Comparator.nullsLast(Integer::compareTo)))
                .toList();
        for (OrderDeliveryTicketAllocation allocation : ordered) {
            String type = defaultText(allocation.getAllocationType(), "");
            if (TYPE_PRODUCTION.equalsIgnoreCase(type)) {
                productionItem = ensureProductionItem(productionItem, allocation);
                continue;
            }
            if (!TYPE_IN_STOCK.equalsIgnoreCase(type) && !TYPE_IN_PRODUCTION.equalsIgnoreCase(type)) {
                continue;
            }
            SalesOrderItem linked = allocation.getId() == null
                    ? null
                    : itemMapper.selectByAllocationIdForUpdate(allocation.getId().longValue());
            if (linked == null && safeInt(allocation.getQty()) > 0) {
                linked = copySplitItem(productionItem, allocation, nextLineNo++);
                itemMapper.insert(linked);
            }
            if (linked != null && linked.getAllocationId() == null) {
                linked.setAllocationId(allocation.getId().longValue());
                linked.setAllocationType(type);
                linked.setUpdatedAt(LocalDateTime.now());
                itemMapper.updateById(linked);
            }
        }
        QueryWrapper<SalesOrderItem> countQuery = new QueryWrapper<>();
        countQuery.eq("sales_order_id", header.getId());
        header.setTotalItemCount(Math.toIntExact(itemMapper.selectCount(countQuery)));
        header.setUpdatedAt(LocalDateTime.now());
        headerMapper.updateById(header);
    }

    public AllocationQuantitySyncResult syncAllocationQtyFromItem(SalesOrderHeader header, SalesOrderItem item, Integer oldQty) {
        if (item == null || item.getAllocationId() == null || Objects.equals(oldQty, item.getQty())) {
            return null;
        }
        OrderDeliveryTicketAllocation snapshot = allocationMapper.selectById(item.getAllocationId().intValue());
        if (snapshot == null) {
            throw new RuntimeException("销售订单零件关联的 allocation 不存在：" + item.getAllocationId());
        }
        OrderDeliveryTicket ticket = snapshot.getTicketId() == null
                ? null
                : ticketMapper.selectByIdForUpdate(snapshot.getTicketId());
        OrderDeliveryTicketAllocation allocation = allocationMapper.selectByIdForUpdate(item.getAllocationId().intValue());
        if (allocation == null) {
            throw new RuntimeException("销售订单零件关联的 allocation 不存在：" + item.getAllocationId());
        }
        Integer qty = requireNonNegative(item.getQty());
        boolean syncTicket = canSyncTicketQuantity(ticket);
        updateLinkedQuantity(item, allocation, qty);
        if (syncTicket && ticket != null) {
            allocation.setTicketQty(qty);
            ticket.setQty(qty);
            ticketMapper.updateById(ticket);
        }
        return new AllocationQuantitySyncResult(
                allocation.getId(),
                item.getId(),
                oldQty,
                qty,
                syncTicket && ticket != null);
    }

    public SalesOrderItem lockItemByAllocation(Long salesOrderId, Integer allocationId) {
        OrderDeliveryTicketAllocation allocation = allocationMapper.selectById(allocationId);
        if (allocation == null) {
            throw new RuntimeException("allocation 不存在：" + allocationId);
        }
        if (allocation.getTicketId() != null) {
            ticketMapper.selectByIdForUpdate(allocation.getTicketId());
        }
        OrderDeliveryTicketAllocation locked = allocationMapper.selectByIdForUpdate(allocationId);
        SalesOrderItem item = itemMapper.selectByAllocationIdForUpdate(locked.getId().longValue());
        assertItemBelongsToOrder(item, salesOrderId, item == null ? null : item.getId());
        return item;
    }

    public void updateLinkedQuantity(SalesOrderItem item, OrderDeliveryTicketAllocation allocation, int qty) {
        item.setQty(qty);
        item.setReleasedQty(Math.min(safeInt(item.getReleasedQty()), qty));
        item.setRemainingQty(Math.max(0, qty - safeInt(item.getReleasedQty())));
        item.setUnshippedQty(Math.max(0, qty - safeInt(item.getShippedQty())));
        if (item.getUntaxedUnitPrice() != null) {
            item.setUntaxedTotalPrice(item.getUntaxedUnitPrice().multiply(BigDecimal.valueOf(qty))
                    .setScale(2, RoundingMode.HALF_UP));
        }
        if (item.getTaxIncludedUnitPrice() != null) {
            item.setTaxIncludedTotalPrice(item.getTaxIncludedUnitPrice().multiply(BigDecimal.valueOf(qty))
                    .setScale(2, RoundingMode.HALF_UP));
        }
        item.setUpdatedAt(LocalDateTime.now());
        itemMapper.updateById(item);
        allocation.setQty(qty);
        allocation.setUpdatedAt(LocalDateTime.now());
        allocationMapper.updateById(allocation);
    }

    public boolean syncTicketQtyIfWmsPending(OrderDeliveryTicketAllocation allocation, int qty) {
        if (allocation == null || allocation.getTicketId() == null) {
            return false;
        }
        OrderDeliveryTicket ticket = ticketMapper.selectByIdForUpdate(allocation.getTicketId());
        if (ticket == null || "SUCCESS".equalsIgnoreCase(defaultText(ticket.getWmsAllocateStatus(), "PENDING"))) {
            return false;
        }
        if (!canSyncTicketQuantity(ticket)) {
            return false;
        }
        ticket.setQty(qty);
        ticketMapper.updateById(ticket);
        allocation.setTicketQty(qty);
        allocationMapper.updateById(allocation);
        return true;
    }

    private boolean canSyncTicketQuantity(OrderDeliveryTicket ticket) {
        if (ticket == null) {
            return false;
        }
        String status = defaultText(ticket.getWmsAllocateStatus(), "PENDING").toUpperCase();
        return "PENDING".equals(status) || "FAILED".equals(status);
    }

    private SalesOrderItem ensureProductionItem(SalesOrderItem source, OrderDeliveryTicketAllocation allocation) {
        if (source.getAllocationId() == null || !Objects.equals(source.getAllocationId(), allocation.getId().longValue())) {
            source.setAllocationId(allocation.getId().longValue());
            source.setAllocationType(TYPE_PRODUCTION);
        }
        updateLinkedQuantity(source, allocation, safeInt(allocation.getQty()));
        return source;
    }

    private SalesOrderItem copySplitItem(SalesOrderItem source, OrderDeliveryTicketAllocation allocation, int lineNo) {
        SalesOrderItem item = new SalesOrderItem();
        item.setSalesOrderId(source.getSalesOrderId());
        item.setSourceType(source.getSourceType());
        item.setSourceOrderId(source.getSourceOrderId());
        item.setSourceItemId(source.getSourceItemId());
        item.setSourceTicketId(null);
        item.setSalesOrderDate(source.getSalesOrderDate());
        item.setLineNo(lineNo);
        item.setPartNo(source.getPartNo());
        item.setDescription(source.getDescription());
        item.setQty(safeInt(allocation.getQty()));
        item.setVersion(source.getVersion());
        item.setOrderType(source.getOrderType());
        item.setCustomerOrderNo(source.getCustomerOrderNo());
        item.setCustomerProjectNo(source.getCustomerProjectNo());
        item.setCustomerOrderTitle(source.getCustomerOrderTitle());
        item.setInternalProjectNo(source.getInternalProjectNo());
        item.setMaterialNo(source.getMaterialNo());
        item.setMaterialNoStatus(source.getMaterialNoStatus());
        item.setMaterialNoSource(source.getMaterialNoSource());
        item.setMaterialNoSourceTicketId(source.getMaterialNoSourceTicketId());
        item.setMaterialNoSyncedAt(source.getMaterialNoSyncedAt());
        item.setMaterialNoMessage(source.getMaterialNoMessage());
        item.setCustomerDeliveryDate(source.getCustomerDeliveryDate());
        item.setInternalDeliveryDate(source.getInternalDeliveryDate());
        item.setMaterialName(allocation.getMaterialName());
        item.setEngineeringMaterialCode(allocation.getEngineeringMaterialCode());
        item.setEngineeringMaterialName(allocation.getEngineeringMaterialName());
        item.setEngineeringMaterialTypeCode(allocation.getEngineeringMaterialTypeCode());
        item.setEngineeringMaterialTypeName(allocation.getEngineeringMaterialTypeName());
        item.setEngineeringMaterialCategory(allocation.getEngineeringMaterialCategory());
        item.setMaterialShape(allocation.getMaterialShape());
        item.setSurfaceTreatment(allocation.getSurfaceTreatment());
        item.setTaxIncludedUnitPrice(source.getTaxIncludedUnitPrice());
        item.setUntaxedUnitPrice(source.getUntaxedUnitPrice());
        item.setShippedQty(0);
        item.setReleasedQty(safeInt(allocation.getQty()));
        item.setRemainingQty(0);
        item.setUnshippedQty(safeInt(allocation.getQty()));
        item.setDrawingUrl(source.getDrawingUrl());
        item.setHasDrawing(source.getHasDrawing());
        item.setMaterialGrossWeight(allocation.getMaterialGrossWeight());
        item.setPartNetWeight(allocation.getPartNetWeight());
        item.setQuoteMode(source.getQuoteMode());
        item.setAssemblyFee(source.getAssemblyFee());
        item.setBaseCost(source.getBaseCost());
        item.setItemType(allocation.getItemType());
        item.setAssemblyRelationStatus(source.getAssemblyRelationStatus());
        item.setMaterialData(allocation.getMaterialData());
        item.setProcessRoute(allocation.getProcessRoute());
        item.setFeeData(source.getFeeData());
        item.setRemark(allocation.getRemark());
        item.setStatus(source.getStatus());
        item.setAllocationId(allocation.getId().longValue());
        item.setAllocationType(allocation.getAllocationType());
        item.setTaxIncludedTotalPrice(total(source.getTaxIncludedUnitPrice(), allocation.getQty()));
        item.setUntaxedTotalPrice(total(source.getUntaxedUnitPrice(), allocation.getQty()));
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        return item;
    }

    private BigDecimal total(BigDecimal unitPrice, Integer qty) {
        return unitPrice == null ? null : unitPrice.multiply(BigDecimal.valueOf(safeInt(qty))).setScale(2, RoundingMode.HALF_UP);
    }

    private int nextLineNo(Long salesOrderId) {
        List<SalesOrderItem> items = itemMapper.selectBySalesOrderIdForUpdate(salesOrderId);
        return items.stream().map(SalesOrderItem::getLineNo).filter(Objects::nonNull).max(Integer::compareTo).orElse(0) + 1;
    }

    private SalesOrderItem resolveSourceItem(OrderDeliveryTicket ticket, boolean lock) {
        if (ticket == null) {
            return null;
        }
        if (ReleaseSourceType.SALES_ORDER.name().equalsIgnoreCase(defaultText(ticket.getSourceType(), ""))
                && ticket.getSourceItemId() != null) {
            return lock ? itemMapper.selectByIdForUpdate(ticket.getSourceItemId())
                    : itemMapper.selectById(ticket.getSourceItemId());
        }
        if (ticket.getId() == null) {
            return null;
        }
        return lock ? itemMapper.selectBySourceTicketIdForUpdate(ticket.getId())
                : itemMapper.selectBySourceTicketId(ticket.getId());
    }

    private Long resolveSalesOrderId(OrderDeliveryTicket ticket) {
        SalesOrderItem item = resolveSourceItem(ticket, false);
        return item == null ? null : item.getSalesOrderId();
    }

    private void assertItemBelongsToOrder(SalesOrderItem item, Long salesOrderId, Object itemId) {
        if (item == null) {
            throw new RuntimeException("销售订单零件不存在：" + itemId);
        }
        if (salesOrderId != null && !Objects.equals(salesOrderId, item.getSalesOrderId())) {
            throw new RuntimeException("销售订单零件不属于当前销售订单：" + item.getId());
        }
    }

    private Integer requireNonNegative(Integer qty) {
        if (qty == null || qty < 0) {
            throw new RuntimeException("数量必须大于等于 0");
        }
        return qty;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
