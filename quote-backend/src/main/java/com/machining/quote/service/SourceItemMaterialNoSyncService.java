package com.machining.quote.service;

import com.machining.quote.dto.ReleaseSourceType;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class SourceItemMaterialNoSyncService {
    public static final String MATERIAL_CONFIRMED = "已确认";
    public static final String MATERIAL_SOURCE_MES_TICKET = "MES_TICKET";

    @Autowired
    private ItemQuoteVersionMapper itemMapper;
    @Autowired
    private SalesOrderItemMapper salesOrderItemMapper;
    @Autowired
    private OrderDeliveryTicketMapper ticketMapper;

    @Transactional
    public void assertMaterialWritable(OrderDeliveryTicket ticket, String materialNo) {
        String normalizedMaterialNo = normalizeMaterialNo(materialNo);
        LockedTargets targets = lockTargets(ticket);
        assertTargetsWritable(targets, normalizedMaterialNo);
    }

    @Transactional
    public MaterialNoSyncResult syncConfirmedMaterialNo(OrderDeliveryTicket ticket, String materialNo, String message) {
        String normalizedMaterialNo = normalizeMaterialNo(materialNo);
        LockedTargets targets = lockTargets(ticket);
        assertTargetsWritable(targets, normalizedMaterialNo);
        if (targets.isEmpty()) {
            return MaterialNoSyncResult.empty();
        }

        LocalDateTime confirmedAt = LocalDateTime.now();
        Long sourceTicketId = targets.ticket().getId() == null ? null : targets.ticket().getId().longValue();
        String confirmationMessage = abbreviate(message);
        int rfqUpdatedCount = syncRfqItem(
                targets.rfqItem(), normalizedMaterialNo, sourceTicketId, confirmedAt, confirmationMessage);
        int salesOrderUpdatedCount = syncSalesOrderItem(
                targets.salesOrderItem(), normalizedMaterialNo, sourceTicketId, confirmedAt, confirmationMessage);
        int targetCount = (targets.rfqItem() == null ? 0 : 1) + (targets.salesOrderItem() == null ? 0 : 1);
        int updatedCount = rfqUpdatedCount + salesOrderUpdatedCount;
        return new MaterialNoSyncResult(
                targets.rfqItem() == null ? 0 : 1,
                targets.salesOrderItem() == null ? 0 : 1,
                rfqUpdatedCount,
                salesOrderUpdatedCount,
                targetCount - updatedCount);
    }

    private int syncRfqItem(
            ItemQuoteVersion item,
            String materialNo,
            Long sourceTicketId,
            LocalDateTime confirmedAt,
            String message) {
        if (item == null || isConfirmedSame(item.getMaterialNo(), item.getMaterialNoStatus(), materialNo)) {
            return 0;
        }
        ItemQuoteVersion update = new ItemQuoteVersion();
        update.setId(item.getId());
        update.setMaterialNo(materialNo);
        update.setMaterialNoStatus(MATERIAL_CONFIRMED);
        update.setMaterialNoSource(MATERIAL_SOURCE_MES_TICKET);
        update.setMaterialNoSourceTicketId(sourceTicketId);
        update.setMaterialNoSyncedAt(confirmedAt);
        update.setMaterialNoMessage(message);
        itemMapper.updateById(update);
        return 1;
    }

    private int syncSalesOrderItem(
            SalesOrderItem item,
            String materialNo,
            Long sourceTicketId,
            LocalDateTime confirmedAt,
            String message) {
        if (item == null || isConfirmedSame(item.getMaterialNo(), item.getMaterialNoStatus(), materialNo)) {
            return 0;
        }
        SalesOrderItem update = new SalesOrderItem();
        update.setId(item.getId());
        update.setMaterialNo(materialNo);
        update.setMaterialNoStatus(MATERIAL_CONFIRMED);
        update.setMaterialNoSource(MATERIAL_SOURCE_MES_TICKET);
        update.setMaterialNoSourceTicketId(sourceTicketId);
        update.setMaterialNoSyncedAt(confirmedAt);
        update.setMaterialNoMessage(message);
        salesOrderItemMapper.updateById(update);
        return 1;
    }

    private LockedTargets lockTargets(OrderDeliveryTicket ticket) {
        OrderDeliveryTicket lockedTicket = lockTicket(ticket);
        SourceRef source = resolveSource(lockedTicket);
        if (source == null) {
            return new LockedTargets(lockedTicket, null, null);
        }
        if (source.isRfq()) {
            ItemQuoteVersion rfqItem = loadRfqItem(source.rfqItemId());
            SalesOrderItem linkedSalesOrderItem = loadLinkedSalesOrderItem(lockedTicket.getId());
            return new LockedTargets(lockedTicket, rfqItem, linkedSalesOrderItem);
        }
        return new LockedTargets(lockedTicket, null, loadSalesOrderItem(source.salesOrderItemId()));
    }

    private OrderDeliveryTicket lockTicket(OrderDeliveryTicket ticket) {
        if (ticket == null) {
            throw new RuntimeException("提单行不能为空");
        }
        if (ticketMapper == null || ticket.getId() == null) {
            return ticket;
        }
        OrderDeliveryTicket locked = ticketMapper.selectByIdForUpdate(ticket.getId());
        if (locked == null) {
            throw new RuntimeException("提单行不存在: " + ticket.getId());
        }
        return locked;
    }

    private void assertTargetsWritable(LockedTargets targets, String materialNo) {
        if (targets.rfqItem() != null) {
            assertWritable(
                    "RFQ 来源零件",
                    targets.rfqItem().getMaterialNo(),
                    targets.rfqItem().getMaterialNoStatus(),
                    materialNo);
        }
        if (targets.salesOrderItem() != null) {
            assertWritable(
                    targets.rfqItem() == null ? "销售订单零件" : "派生销售订单零件",
                    targets.salesOrderItem().getMaterialNo(),
                    targets.salesOrderItem().getMaterialNoStatus(),
                    materialNo);
        }
    }

    private ItemQuoteVersion loadRfqItem(Integer itemId) {
        if (itemMapper == null) {
            throw new RuntimeException("RFQ 来源零件回写服务不可用");
        }
        if (itemId == null) {
            throw new RuntimeException("RFQ 来源零件 ID 不能为空");
        }
        ItemQuoteVersion item = itemMapper.selectByIdForUpdate(itemId);
        if (item == null) {
            throw new RuntimeException("RFQ 来源零件不存在: " + itemId);
        }
        return item;
    }

    private SalesOrderItem loadSalesOrderItem(Long itemId) {
        if (salesOrderItemMapper == null) {
            throw new RuntimeException("销售订单零件回写服务不可用");
        }
        if (itemId == null) {
            throw new RuntimeException("销售订单来源零件 ID 不能为空");
        }
        SalesOrderItem item = salesOrderItemMapper.selectByIdForUpdate(itemId);
        if (item == null) {
            throw new RuntimeException("销售订单来源零件不存在: " + itemId);
        }
        return item;
    }

    private SalesOrderItem loadLinkedSalesOrderItem(Integer ticketId) {
        if (salesOrderItemMapper == null || ticketId == null) {
            return null;
        }
        return salesOrderItemMapper.selectBySourceTicketIdForUpdate(ticketId);
    }

    private void assertWritable(String sourceLabel, String existingMaterialNo, String existingStatus, String materialNo) {
        String existing = normalizeNullable(existingMaterialNo);
        if (existing == null || existing.equals(materialNo)) {
            return;
        }
        if (MATERIAL_CONFIRMED.equals(normalizeNullable(existingStatus))) {
            throw new MaterialNoConflictException(sourceLabel + "已确认不同物料号，不能覆盖");
        }
        throw new MaterialNoConflictException(sourceLabel + "已有不同物料号，不能覆盖");
    }

    private boolean isConfirmedSame(String existingMaterialNo, String existingStatus, String materialNo) {
        return materialNo.equals(normalizeNullable(existingMaterialNo))
                && MATERIAL_CONFIRMED.equals(normalizeNullable(existingStatus));
    }

    private SourceRef resolveSource(OrderDeliveryTicket ticket) {
        if (ticket == null) {
            throw new RuntimeException("提单行不能为空");
        }
        String sourceType = normalizeSourceType(ticket.getSourceType());
        if (ReleaseSourceType.SALES_ORDER.name().equals(sourceType)) {
            return SourceRef.salesOrder(ticket.getSourceItemId());
        }
        if (ReleaseSourceType.RFQ.name().equals(sourceType)) {
            Integer itemId = ticket.getSourceItemId() == null
                    ? ticket.getItemId()
                    : Math.toIntExact(ticket.getSourceItemId());
            return SourceRef.rfq(itemId);
        }
        return null;
    }

    private String normalizeSourceType(String value) {
        if (value == null || value.trim().isEmpty()) {
            return ReleaseSourceType.RFQ.name();
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeMaterialNo(String value) {
        String materialNo = normalizeNullable(value);
        if (materialNo == null) {
            throw new IllegalArgumentException("MES 物料号不能为空");
        }
        if (materialNo.length() > 120) {
            throw new IllegalArgumentException("MES 物料号长度超过 120");
        }
        return materialNo;
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }

    private String abbreviate(String value) {
        String text = value == null ? "" : value.trim();
        return text.length() <= 500 ? text : text.substring(0, 500);
    }

    public static class MaterialNoConflictException extends RuntimeException {
        public MaterialNoConflictException(String message) {
            super(message);
        }
    }

    public record MaterialNoSyncResult(
            int rfqTargetCount,
            int salesOrderTargetCount,
            int rfqUpdatedCount,
            int salesOrderUpdatedCount,
            int idempotentCount) {
        public static MaterialNoSyncResult empty() {
            return new MaterialNoSyncResult(0, 0, 0, 0, 0);
        }

        public int targetCount() {
            return rfqTargetCount + salesOrderTargetCount;
        }

        public int updatedCount() {
            return rfqUpdatedCount + salesOrderUpdatedCount;
        }
    }

    private record LockedTargets(
            OrderDeliveryTicket ticket,
            ItemQuoteVersion rfqItem,
            SalesOrderItem salesOrderItem) {
        boolean isEmpty() {
            return rfqItem == null && salesOrderItem == null;
        }
    }

    private record SourceRef(boolean rfq, Integer rfqItemId, Long salesOrderItemId) {
        static SourceRef rfq(Integer itemId) {
            return new SourceRef(true, itemId, null);
        }

        static SourceRef salesOrder(Long itemId) {
            return new SourceRef(false, null, itemId);
        }

        boolean isRfq() {
            return rfq;
        }
    }
}
