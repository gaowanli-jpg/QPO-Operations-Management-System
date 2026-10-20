package com.machining.quote.service.mes;

import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderDeliveryTicketAllocation;
import com.machining.quote.mapper.OrderDeliveryTicketAllocationMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProductionAllocationLifecycleService {
    private static final String MATERIAL_SOURCE = "MES_MATERIAL";

    @Autowired
    private OrderDeliveryTicketMapper ticketMapper;
    @Autowired
    private OrderDeliveryTicketAllocationMapper allocationMapper;
    @Autowired
    private com.machining.quote.service.SalesOrderAllocationLinkService salesOrderAllocationLinkService;

    @Transactional
    public void recordMaterialSuccess(Integer ticketId,
                                      String materialNo,
                                      String message,
                                      String mesRequestId,
                                      String externalTraceId,
                                      boolean callbackReceived) {
        String normalizedMaterialNo = materialNo == null ? null : materialNo.trim();
        if (ticketId == null || normalizedMaterialNo == null || normalizedMaterialNo.isEmpty()) {
            throw new IllegalArgumentException("ticketId and confirmed materialNo are required");
        }
        OrderDeliveryTicket ticket = salesOrderAllocationLinkService.lockTicketForMaterialSuccess(ticketId);
        if (ticket == null) {
            throw new IllegalStateException("delivery ticket not found ticketId=" + ticketId);
        }
        if (ticket.getMaterialNo() != null && !ticket.getMaterialNo().isBlank()
                && !normalizedMaterialNo.equals(ticket.getMaterialNo().trim())) {
            throw new IllegalStateException("confirmed material number changed ticketId=" + ticketId);
        }

        LocalDateTime now = LocalDateTime.now();
        OrderDeliveryTicket ticketUpdate = new OrderDeliveryTicket();
        ticketUpdate.setId(ticketId);
        ticketUpdate.setMaterialNo(normalizedMaterialNo);
        ticketUpdate.setMesSyncStatus("SUCCESS");
        ticketUpdate.setMesSyncMessage(isBlank(message) ? "MES material resolved" : message.trim());
        ticketUpdate.setMesSyncedAt(now);
        ticketUpdate.setMesRequestId(mesRequestId);
        ticketUpdate.setMesExternalTaskId(trimToNull(externalTraceId));
        if (callbackReceived) {
            ticketUpdate.setMesCallbackReceivedAt(now);
            ticketUpdate.setMesCallbackMessage(abbreviate(message));
        }
        ticketMapper.updateById(ticketUpdate);

        List<OrderDeliveryTicketAllocation> existing = allocationMapper.selectByTicketIdForUpdate(ticketId);
        boolean initialized = existing.stream().anyMatch(row -> MATERIAL_SOURCE.equals(row.getAllocationSource()));
        if (initialized || existing.stream().anyMatch(row -> "PRODUCTION_REQUIRED".equals(row.getAllocationType())
                || "IN_STOCK_LOCK".equals(row.getAllocationType())
                || "IN_PRODUCTION_LOCK".equals(row.getAllocationType()))) {
            existing.stream()
                    .filter(row -> "PRODUCTION_REQUIRED".equalsIgnoreCase(row.getAllocationType()))
                    .findFirst()
                    .ifPresent(row -> salesOrderAllocationLinkService.linkMaterialAllocation(ticket, row));
            return;
        }
        if (Boolean.TRUE.equals(ticket.getDiscarded()) || ticket.getQty() == null || ticket.getQty() <= 0) {
            return;
        }

        OrderDeliveryTicketAllocation allocation = new OrderDeliveryTicketAllocation();
        allocation.setTicketId(ticket.getId());
        allocation.setRfqId(ticket.getRfqId());
        allocation.setItemId(ticket.getItemId());
        allocation.setSourceType(ticket.getSourceType());
        allocation.setSourceOrderId(ticket.getSourceOrderId());
        allocation.setSourceItemId(ticket.getSourceItemId());
        allocation.setAllocationType("PRODUCTION_REQUIRED");
        allocation.setAllocationSource(MATERIAL_SOURCE);
        Map<String, Object> sourcePayload = new LinkedHashMap<>();
        sourcePayload.put("source", "MES_MATERIAL_SUCCESS");
        sourcePayload.put("ticketId", ticketId);
        allocation.setSourcePayload(sourcePayload);
        allocation.setMesProductionStatus("PENDING");
        allocation.setStatus("ACTIVE");
        allocation.setCreatedAt(now);
        allocation.setUpdatedAt(now);
        allocationMapper.insert(allocation);
        salesOrderAllocationLinkService.linkMaterialAllocation(ticket, allocation);
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private String abbreviate(String value) {
        String normalized = trimToNull(value);
        return normalized == null || normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
