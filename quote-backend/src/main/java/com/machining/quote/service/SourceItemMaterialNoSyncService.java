package com.machining.quote.service;

import com.machining.quote.dto.ReleaseSourceType;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
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

    @Autowired(required = false)
    private ItemQuoteVersionMapper itemMapper;
    @Autowired(required = false)
    private SalesOrderItemMapper salesOrderItemMapper;

    @Transactional
    public void assertMaterialWritable(OrderDeliveryTicket ticket, String materialNo) {
        String normalizedMaterialNo = normalizeMaterialNo(materialNo);
        SourceRef source = resolveSource(ticket);
        if (source == null) {
            return;
        }
        if (source.isRfq()) {
            ItemQuoteVersion item = loadRfqItem(source.rfqItemId());
            assertWritable("RFQ 来源零件", item.getMaterialNo(), item.getMaterialNoStatus(), normalizedMaterialNo);
            return;
        }
        SalesOrderItem item = loadSalesOrderItem(source.salesOrderItemId());
        assertWritable("销售订单零件", item.getMaterialNo(), item.getMaterialNoStatus(), normalizedMaterialNo);
    }

    @Transactional
    public void syncConfirmedMaterialNo(OrderDeliveryTicket ticket, String materialNo, String message) {
        String normalizedMaterialNo = normalizeMaterialNo(materialNo);
        SourceRef source = resolveSource(ticket);
        if (source == null) {
            return;
        }
        if (source.isRfq()) {
            syncRfqItem(ticket, source.rfqItemId(), normalizedMaterialNo, message);
            return;
        }
        syncSalesOrderItem(ticket, source.salesOrderItemId(), normalizedMaterialNo, message);
    }

    private void syncRfqItem(OrderDeliveryTicket ticket, Integer itemId, String materialNo, String message) {
        ItemQuoteVersion item = loadRfqItem(itemId);
        assertWritable("RFQ 来源零件", item.getMaterialNo(), item.getMaterialNoStatus(), materialNo);
        ItemQuoteVersion update = new ItemQuoteVersion();
        update.setId(itemId);
        update.setMaterialNo(materialNo);
        update.setMaterialNoStatus(MATERIAL_CONFIRMED);
        update.setMaterialNoSource(MATERIAL_SOURCE_MES_TICKET);
        update.setMaterialNoSourceTicketId(ticket == null || ticket.getId() == null ? null : ticket.getId().longValue());
        update.setMaterialNoSyncedAt(LocalDateTime.now());
        update.setMaterialNoMessage(abbreviate(message));
        itemMapper.updateById(update);
    }

    private void syncSalesOrderItem(OrderDeliveryTicket ticket, Long itemId, String materialNo, String message) {
        SalesOrderItem item = loadSalesOrderItem(itemId);
        assertWritable("销售订单零件", item.getMaterialNo(), item.getMaterialNoStatus(), materialNo);
        SalesOrderItem update = new SalesOrderItem();
        update.setId(itemId);
        update.setMaterialNo(materialNo);
        update.setMaterialNoStatus(MATERIAL_CONFIRMED);
        update.setMaterialNoSource(MATERIAL_SOURCE_MES_TICKET);
        update.setMaterialNoSourceTicketId(ticket == null || ticket.getId() == null ? null : ticket.getId().longValue());
        update.setMaterialNoSyncedAt(LocalDateTime.now());
        update.setMaterialNoMessage(abbreviate(message));
        salesOrderItemMapper.updateById(update);
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
