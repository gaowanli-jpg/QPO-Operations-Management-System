package com.machining.quote.service.mes;

import com.machining.quote.dto.ReleaseSourceType;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderDeliveryTicketAllocation;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketAllocationMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 待产零件「刷新料号」的落库执行器。
 *
 * <p>与 {@link com.machining.quote.service.SourceItemMaterialNoSyncService} 的差别：
 * 后者是正常流程，已确认的不同料号一律拒绝覆盖；本类专门服务异常修复，
 * 在通过前置校验后强制覆盖，并把来源标记为 {@link #MATERIAL_SOURCE_MES_REFRESH} 以便追溯。</p>
 *
 * <p>拆成独立 Bean 的原因：需要真正的 {@code @Transactional}（自调用不会走代理），
 * 且必须在事务内用 FOR UPDATE 重新校验一次，避免与并发的生产下发交叉。</p>
 */
@Service
public class ProductionItemMaterialNoRefreshWriter {
    private static final Logger log = LoggerFactory.getLogger(ProductionItemMaterialNoRefreshWriter.class);

    public static final String MATERIAL_CONFIRMED = "已确认";
    public static final String MATERIAL_SOURCE_MES_REFRESH = "MES_REFRESH";

    private static final String MES_SYNC_SUCCESS = "SUCCESS";
    private static final String MES_PRODUCTION_SUCCESS = "SUCCESS";
    private static final Set<String> IN_FLIGHT_PRODUCTION_STATUSES = Set.of("PROCESSING", "SUBMITTING", "RESULT_UNKNOWN");

    @Autowired
    private OrderDeliveryTicketMapper ticketMapper;
    @Autowired
    private OrderDeliveryTicketAllocationMapper allocationMapper;
    @Autowired(required = false)
    private SalesOrderItemMapper salesOrderItemMapper;
    @Autowired(required = false)
    private ItemQuoteVersionMapper itemQuoteVersionMapper;

    @Transactional
    public RefreshWriteResult apply(Integer ticketId,
                                    String newMaterialNo,
                                    String reason,
                                    String mesRequestId,
                                    String externalTraceId,
                                    String message) {
        String normalizedNew = requireText(newMaterialNo, "刷新料号不能为空");
        if (ticketId == null) {
            throw new IllegalArgumentException("提单行ID不能为空");
        }
        OrderDeliveryTicket ticket = ticketMapper.selectByIdForUpdate(ticketId);
        if (ticket == null) {
            throw new IllegalStateException("提单行不存在 ticketId=" + ticketId);
        }
        if (Boolean.TRUE.equals(ticket.getDiscarded())) {
            throw new IllegalStateException("提单行已废弃，不能刷新料号 ticketId=" + ticketId);
        }
        List<OrderDeliveryTicketAllocation> allocations = allocationMapper.selectByTicketIdForUpdate(ticketId);
        assertNoBlockingProductionRows(allocations);

        ItemQuoteVersion rfqItem = null;
        SalesOrderItem salesItem = null;
        if (isSalesOrderSource(ticket)) {
            salesItem = salesOrderItemMapper == null || ticket.getSourceItemId() == null
                    ? null
                    : salesOrderItemMapper.selectByIdForUpdate(ticket.getSourceItemId());
        } else {
            Integer rfqItemId = resolveRfqItemId(ticket);
            rfqItem = itemQuoteVersionMapper == null || rfqItemId == null
                    ? null
                    : itemQuoteVersionMapper.selectByIdForUpdate(rfqItemId);
            salesItem = salesOrderItemMapper == null
                    ? null
                    : salesOrderItemMapper.selectBySourceTicketIdForUpdate(ticketId);
        }

        String previousMaterialNo = firstNonBlank(
                salesItem == null ? null : salesItem.getMaterialNo(),
                rfqItem == null ? null : rfqItem.getMaterialNo(),
                ticket.getMaterialNo());

        LocalDateTime now = LocalDateTime.now();
        String syncMessage = buildSyncMessage(previousMaterialNo, normalizedNew, reason);

        OrderDeliveryTicket ticketUpdate = new OrderDeliveryTicket();
        ticketUpdate.setId(ticketId);
        ticketUpdate.setMaterialNo(normalizedNew);
        ticketUpdate.setMesSyncStatus(MES_SYNC_SUCCESS);
        ticketUpdate.setMesSyncMessage(abbreviate(syncMessage, 500));
        ticketUpdate.setMesSyncedAt(now);
        ticketUpdate.setMesRequestId(trimToNull(mesRequestId));
        ticketUpdate.setMesExternalTaskId(trimToNull(externalTraceId));
        ticketMapper.updateById(ticketUpdate);

        int salesOrderUpdated = writeSalesOrderItem(salesItem, normalizedNew, ticketId, now, syncMessage);
        int rfqUpdated = writeRfqItem(rfqItem, normalizedNew, ticketId, now, syncMessage);
        int allocationUpdated = writeAllocations(allocations, normalizedNew, now);

        log.info("MES material no refresh applied ticketId={} mesRequestId={} previousMaterialNo={} newMaterialNo={} "
                        + "allocationUpdated={} salesOrderUpdated={} rfqUpdated={} reason={}",
                ticketId, mesRequestId, previousMaterialNo, normalizedNew,
                allocationUpdated, salesOrderUpdated, rfqUpdated, reason);

        return new RefreshWriteResult(
                previousMaterialNo,
                normalizedNew,
                allocationUpdated,
                salesOrderUpdated,
                rfqUpdated);
    }

    private void assertNoBlockingProductionRows(List<OrderDeliveryTicketAllocation> allocations) {
        for (OrderDeliveryTicketAllocation allocation : allocations) {
            if (allocation == null || allocation.getId() == null) {
                continue;
            }
            String status = normalize(allocation.getMesProductionStatus());
            if (MES_PRODUCTION_SUCCESS.equals(status)) {
                throw new IllegalStateException("生产行已成功下发 MES，不能刷新料号 allocationId=" + allocation.getId());
            }
            if (IN_FLIGHT_PRODUCTION_STATUSES.contains(status)) {
                throw new IllegalStateException("生产行存在在途下发请求，请先核对结果再刷新料号 allocationId="
                        + allocation.getId() + " status=" + status);
            }
        }
    }

    private int writeSalesOrderItem(SalesOrderItem item,
                                    String materialNo,
                                    Integer ticketId,
                                    LocalDateTime now,
                                    String message) {
        if (item == null || item.getId() == null) {
            return 0;
        }
        SalesOrderItem update = new SalesOrderItem();
        update.setId(item.getId());
        update.setMaterialNo(materialNo);
        update.setMaterialNoStatus(MATERIAL_CONFIRMED);
        update.setMaterialNoSource(MATERIAL_SOURCE_MES_REFRESH);
        update.setMaterialNoSourceTicketId(ticketId == null ? null : ticketId.longValue());
        update.setMaterialNoSyncedAt(now);
        update.setMaterialNoMessage(abbreviate(message, 500));
        salesOrderItemMapper.updateById(update);
        return 1;
    }

    private int writeRfqItem(ItemQuoteVersion item,
                             String materialNo,
                             Integer ticketId,
                             LocalDateTime now,
                             String message) {
        if (item == null || item.getId() == null) {
            return 0;
        }
        ItemQuoteVersion update = new ItemQuoteVersion();
        update.setId(item.getId());
        update.setMaterialNo(materialNo);
        update.setMaterialNoStatus(MATERIAL_CONFIRMED);
        update.setMaterialNoSource(MATERIAL_SOURCE_MES_REFRESH);
        update.setMaterialNoSourceTicketId(ticketId == null ? null : ticketId.longValue());
        update.setMaterialNoSyncedAt(now);
        update.setMaterialNoMessage(abbreviate(message, 500));
        itemQuoteVersionMapper.updateById(update);
        return 1;
    }

    private int writeAllocations(List<OrderDeliveryTicketAllocation> allocations,
                                 String materialNo,
                                 LocalDateTime now) {
        int updated = 0;
        for (OrderDeliveryTicketAllocation allocation : allocations) {
            if (allocation == null || allocation.getId() == null) {
                continue;
            }
            if (materialNo.equals(trimToNull(allocation.getMaterialNo()))) {
                continue;
            }
            OrderDeliveryTicketAllocation update = new OrderDeliveryTicketAllocation();
            update.setId(allocation.getId());
            update.setMaterialNo(materialNo);
            update.setUpdatedAt(now);
            allocationMapper.updateById(update);
            updated++;
        }
        return updated;
    }

    private boolean isSalesOrderSource(OrderDeliveryTicket ticket) {
        return ReleaseSourceType.SALES_ORDER.name().equals(normalize(ticket.getSourceType()));
    }

    private Integer resolveRfqItemId(OrderDeliveryTicket ticket) {
        if (ticket.getSourceItemId() != null) {
            return Math.toIntExact(ticket.getSourceItemId());
        }
        return ticket.getItemId();
    }

    private String buildSyncMessage(String previousMaterialNo, String newMaterialNo, String reason) {
        StringBuilder builder = new StringBuilder("异常修复刷新料号");
        if (previousMaterialNo != null && !previousMaterialNo.equals(newMaterialNo)) {
            builder.append("：").append(previousMaterialNo).append(" → ").append(newMaterialNo);
        }
        if (reason != null && !reason.isBlank()) {
            builder.append("；原因：").append(reason.trim());
        }
        return builder.toString();
    }

    private String requireText(String value, String message) {
        String text = trimToNull(value);
        if (text == null) {
            throw new IllegalArgumentException(message);
        }
        return text;
    }

    private String normalize(String value) {
        String text = trimToNull(value);
        return text == null ? "" : text.toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
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

    private String abbreviate(String value, int maxLength) {
        String text = trimToNull(value);
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    public record RefreshWriteResult(
            String previousMaterialNo,
            String newMaterialNo,
            int allocationUpdated,
            int salesOrderUpdated,
            int rfqUpdated) {
    }
}
