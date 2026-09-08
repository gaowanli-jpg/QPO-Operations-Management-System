package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.config.AuthContext;
import com.machining.quote.dto.CustomerOptionDTO;
import com.machining.quote.dto.DeliveryItemDiscardReq;
import com.machining.quote.dto.DeliveryItemRestoreReq;
import com.machining.quote.dto.LockedItemDTO;
import com.machining.quote.dto.LockedItemPageDTO;
import com.machining.quote.dto.LockedItemQueryReq;
import com.machining.quote.dto.PageResult;
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class LockedItemService {
    private static final int TICKET_ALLOCATION_LIMIT = 1000;
    private static final Set<String> LOCKED_STOCK_TYPES = Set.of("IN_STOCK_LOCK", "IN_PRODUCTION_LOCK");

    @Autowired
    private OrderDeliveryTicketAllocationMapper allocationMapper;
    @Autowired
    private OrderDeliveryTicketMapper ticketMapper;
    @Autowired
    private DataScopeService dataScopeService;
    @Autowired(required = false)
    private AuditService auditService;
    @Autowired(required = false)
    private CustomerMasterService customerMasterService;

    public LockedItemPageDTO listAllocations(LockedItemQueryReq req) {
        LockedItemQueryReq effectiveReq = req == null ? new LockedItemQueryReq() : req;
        int safePage = normalizePage(effectiveReq.getPage());
        int safePageSize = normalizePageSize(effectiveReq.getPageSize());
        QueryWrapper<LockedItemDTO> query = buildQuery(effectiveReq);

        long total = allocationMapper.countLockedItems(query);
        if (total == 0) {
            return LockedItemPageDTO.of(List.of(), 0, safePage, safePageSize);
        }
        List<LockedItemDTO> records = allocationMapper.selectLockedItems(
                query, safePageSize, (safePage - 1L) * safePageSize);
        return LockedItemPageDTO.of(records, total, safePage, safePageSize);
    }

    public List<LockedItemDTO> listTicketAllocations(Integer ticketId) {
        if (ticketId == null) {
            throw new RuntimeException("Ticket ID is required");
        }
        LockedItemQueryReq req = new LockedItemQueryReq();
        QueryWrapper<LockedItemDTO> query = buildQuery(req);
        query.eq("alloc.ticket_id", ticketId);
        return allocationMapper.selectLockedItems(query, TICKET_ALLOCATION_LIMIT, 0);
    }

    public List<String> listCustomers() {
        if (customerMasterService != null) {
            return customerMasterService.listCustomerNamesForCurrentScope();
        }
        QueryWrapper<LockedItemDTO> query = new QueryWrapper<>();
        applyLockedStockScope(query);
        applyStockPickingActiveScope(query);
        applyDiscardedScope(query, Boolean.FALSE);
        applyCustomerPresentScope(query);
        dataScopeService.applyDeliveryTicketScope(query, "ticket");
        return allocationMapper.selectLockedItemCustomers(query).stream()
                .filter(this::hasText)
                .toList();
    }

    public List<CustomerOptionDTO> listCustomerOptions(String field, String keyword) {
        if (customerMasterService != null) {
            return customerMasterService.listCustomerOptions(field, keyword);
        }
        return listCustomers().stream()
                .filter(customerName -> matchesCustomerKeyword(customerName, field, keyword))
                .map(this::toCustomerOption)
                .toList();
    }

    public PageResult<CustomerOptionDTO> pageCustomerOptions(String field, String keyword, Integer page, Integer pageSize) {
        if (customerMasterService != null) {
            return customerMasterService.pageCustomerOptions(field, keyword, page, pageSize);
        }
        List<CustomerOptionDTO> rows = listCustomers().stream()
                .filter(customerName -> matchesCustomerKeyword(customerName, field, keyword))
                .map(this::toCustomerOption)
                .toList();
        return pageRows(rows, page, pageSize);
    }

    private QueryWrapper<LockedItemDTO> buildQuery(LockedItemQueryReq req) {
        QueryWrapper<LockedItemDTO> query = new QueryWrapper<>();
        applyLockedStockScope(query);
        applyCustomerFilter(query, req.getCustomerCode(), req.getCustomerName());
        applyLike(query, "alloc.rfq_no", req.getRfqNo());
        applyLike(query, "alloc.part_no", req.getPartNo());
        applyEquals(query, "alloc.version", req.getVersion());
        applyLike(query, "alloc.material_no", req.getMaterialNo());
        applyLike(query, "alloc.internal_project_no", req.getInternalProjectNo());
        applyLike(query, "alloc.customer_order_no", req.getCustomerOrderNo());
        applyLike(query, "alloc.customer_project_no", req.getCustomerProjectNo());
        applyEquals(query, "alloc.item_type", req.getItemType());
        applyLockedAllocationType(query, req.getAllocationType());
        applyStockPickingStatusScope(query, req.getStockPickingStatus());
        if (req.getDateFrom() != null) {
            query.ge("alloc.delivery_date", req.getDateFrom());
        }
        if (req.getDateTo() != null) {
            query.le("alloc.delivery_date", req.getDateTo());
        }
        applyDiscardedScope(query, req.getDiscarded());
        dataScopeService.applyDeliveryTicketScope(query, "ticket");
        return query;
    }

    @Transactional
    public Map<String, Object> discardAllocation(Integer allocationId, DeliveryItemDiscardReq req) {
        OrderDeliveryTicketAllocation allocation = lockAllocation(allocationId);
        assertCanAccessAllocation(allocation);
        if (Boolean.TRUE.equals(allocation.getDiscarded())) {
            throw new RuntimeException("该锁库零件已废弃");
        }
        String reason = requiredReason(req == null ? null : req.getReason());
        LocalDateTime now = LocalDateTime.now();
        allocation.setDiscarded(true);
        allocation.setDiscardedAt(now);
        allocation.setDiscardedBy(currentUserId());
        allocation.setDiscardReason(reason);
        allocation.setUpdatedAt(now);
        if (allocationMapper.updateById(allocation) <= 0) {
            throw new RuntimeException("锁库零件废弃失败，请刷新后重试");
        }
        Map<String, Object> result = actionResult(allocation, "锁库零件已废弃。", "discarded", reason);
        recordAudit("LOCKED_ITEM_DISCARD", allocation, result, "废弃锁库零件");
        return result;
    }

    @Transactional
    public Map<String, Object> restoreAllocation(Integer allocationId, DeliveryItemRestoreReq req) {
        OrderDeliveryTicketAllocation allocation = lockAllocation(allocationId);
        assertCanAccessAllocation(allocation);
        if (!Boolean.TRUE.equals(allocation.getDiscarded())) {
            throw new RuntimeException("只有已废弃的锁库零件可以恢复");
        }
        String reason = optionalReason(req == null ? null : req.getReason());
        LocalDateTime now = LocalDateTime.now();
        allocation.setDiscarded(false);
        allocation.setRestoredAt(now);
        allocation.setRestoredBy(currentUserId());
        allocation.setRestoreReason(reason);
        allocation.setUpdatedAt(now);
        if (allocationMapper.updateById(allocation) <= 0) {
            throw new RuntimeException("锁库零件恢复失败，请刷新后重试");
        }
        Map<String, Object> result = actionResult(allocation, "锁库零件已恢复。", "active", reason);
        recordAudit("LOCKED_ITEM_RESTORE", allocation, result, "恢复锁库零件");
        return result;
    }

    private void applyCustomerFilter(QueryWrapper<LockedItemDTO> query, String customerCode, String customerName) {
        if (hasText(customerCode)) {
            String code = customerCode.trim();
            query.apply("(rfq.customer_code = {0} "
                    + "OR (alloc.source_type = 'SALES_ORDER' AND alloc.source_order_id IN "
                    + "(SELECT so.id FROM sales_order_header so WHERE so.customer_code = {1})) "
                    + "OR (ticket.source_type = 'SALES_ORDER' AND ticket.source_order_id IN "
                    + "(SELECT so.id FROM sales_order_header so WHERE so.customer_code = {2})))", code, code, code);
            return;
        }
        applyEquals(query, "alloc.customer_name", customerName);
    }

    private void applyLockedStockScope(QueryWrapper<LockedItemDTO> query) {
        query.in("alloc.allocation_type", LOCKED_STOCK_TYPES);
    }

    private void applyDiscardedScope(QueryWrapper<?> query, Boolean discarded) {
        query.eq("alloc.discarded", Boolean.TRUE.equals(discarded));
    }

    private void applyLockedAllocationType(QueryWrapper<LockedItemDTO> query, String allocationType) {
        String normalized = normalizeUpper(allocationType);
        if (!hasText(normalized)) {
            return;
        }
        if (LOCKED_STOCK_TYPES.contains(normalized)) {
            query.eq("alloc.allocation_type", normalized);
        } else {
            query.apply("1 = 0");
        }
    }

    private void applyCustomerPresentScope(QueryWrapper<LockedItemDTO> query) {
        query.isNotNull("COALESCE(alloc.customer_name, ticket.customer_name, rfq.customer_name)");
        query.ne("COALESCE(alloc.customer_name, ticket.customer_name, rfq.customer_name)", "");
    }

    private void applyStockPickingStatusScope(QueryWrapper<LockedItemDTO> query, String status) {
        String normalized = normalizeUpper(status);
        if (!hasText(normalized)) {
            applyStockPickingActiveScope(query);
            return;
        }
        switch (normalized) {
            case "PENDING" -> query.apply("COALESCE(NULLIF(TRIM(alloc.stock_picking_status), ''), 'PENDING') = 'PENDING'");
            case "PROCESSING", "SUCCESS", "FAILED", "RESULT_UNKNOWN" -> query.eq("alloc.stock_picking_status", normalized);
            default -> {
                applyStockPickingActiveScope(query);
                query.eq("alloc.stock_picking_status", normalized);
            }
        }
    }

    private void applyStockPickingActiveScope(QueryWrapper<LockedItemDTO> query) {
        query.apply("COALESCE(NULLIF(TRIM(alloc.stock_picking_status), ''), 'PENDING') <> 'SUCCESS'");
    }

    private OrderDeliveryTicketAllocation lockAllocation(Integer allocationId) {
        if (allocationId == null) {
            throw new RuntimeException("锁库零件 ID 不能为空");
        }
        OrderDeliveryTicketAllocation allocation = allocationMapper.selectByIdForUpdate(allocationId);
        if (allocation == null) {
            throw new RuntimeException("锁库零件不存在");
        }
        if (!LOCKED_STOCK_TYPES.contains(normalizeUpper(allocation.getAllocationType()))) {
            throw new RuntimeException("只有锁库零件可以废弃或恢复");
        }
        return allocation;
    }

    private void assertCanAccessAllocation(OrderDeliveryTicketAllocation allocation) {
        OrderDeliveryTicket ticket = allocation.getTicketId() == null ? null : ticketMapper.selectById(allocation.getTicketId());
        if (ticket == null) {
            throw new RuntimeException("锁库零件对应的提单不存在");
        }
        dataScopeService.assertCanAccessDeliveryTicket(ticket);
    }

    private String requiredReason(String value) {
        String reason = optionalReason(value);
        if (!hasText(reason)) {
            throw new RuntimeException("废弃原因不能为空");
        }
        return reason;
    }

    private String optionalReason(String value) {
        if (!hasText(value)) {
            return null;
        }
        String reason = value.trim();
        if (reason.length() > 500) {
            throw new RuntimeException("原因不能超过 500 个字符");
        }
        return reason;
    }

    private Long currentUserId() {
        AuthContext.SessionUser current = AuthContext.get();
        return current == null || current.id() == null ? null : current.id().longValue();
    }

    private Map<String, Object> actionResult(OrderDeliveryTicketAllocation allocation, String message, String state, String reason) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", message);
        result.put("allocationId", allocation.getId());
        result.put("ticketId", allocation.getTicketId());
        result.put("rfqNo", allocation.getRfqNo());
        result.put("partNo", allocation.getPartNo());
        result.put("version", allocation.getVersion());
        result.put("allocationType", allocation.getAllocationType());
        result.put("stockPickingStatus", allocation.getStockPickingStatus());
        result.put("discarded", Boolean.TRUE.equals(allocation.getDiscarded()));
        result.put("state", state);
        result.put("reason", reason);
        result.put("discardedAt", allocation.getDiscardedAt());
        result.put("discardedBy", allocation.getDiscardedBy());
        result.put("restoredAt", allocation.getRestoredAt());
        result.put("restoredBy", allocation.getRestoredBy());
        return result;
    }

    private void recordAudit(String action, OrderDeliveryTicketAllocation allocation, Map<String, Object> summary, String label) {
        if (auditService == null) {
            return;
        }
        auditService.record(
                action,
                "order_delivery_ticket_allocation",
                allocation.getId(),
                null,
                summary,
                "DELIVERY",
                "SUCCESS",
                label + " allocationId=" + allocation.getId(),
                null
        );
    }

    private void applyLike(QueryWrapper<LockedItemDTO> query, String column, String value) {
        if (hasText(value)) {
            query.like(column, value.trim());
        }
    }

    private void applyEquals(QueryWrapper<LockedItemDTO> query, String column, String value) {
        if (hasText(value)) {
            query.eq(column, value.trim());
        }
    }

    private String normalizeUpper(String value) {
        return hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : value;
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 20;
        }
        return Math.min(pageSize, 100);
    }

    private <T> PageResult<T> pageRows(List<T> allRows, Integer page, Integer pageSize) {
        List<T> rows = allRows == null ? List.of() : allRows;
        int safePage = normalizePage(page);
        int safePageSize = normalizePageSize(pageSize);
        int fromIndex = (int) Math.min((long) (safePage - 1) * safePageSize, rows.size());
        int toIndex = Math.min(fromIndex + safePageSize, rows.size());
        return PageResult.of(rows.subList(fromIndex, toIndex), rows.size(), safePage, safePageSize);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private boolean matchesCustomerKeyword(String customerName, String field, String keyword) {
        if (!hasText(keyword)) {
            return true;
        }
        String normalizedField = field == null ? "all" : field.replace("_", "").replace("-", "").trim().toLowerCase(Locale.ROOT);
        if (!"all".equals(normalizedField) && !"name".equals(normalizedField)) {
            return false;
        }
        return customerName != null && customerName.toLowerCase(Locale.ROOT).contains(keyword.trim().toLowerCase(Locale.ROOT));
    }

    private CustomerOptionDTO toCustomerOption(String customerName) {
        CustomerOptionDTO option = new CustomerOptionDTO();
        option.setCustomerCode(customerName);
        option.setCustomerShortName(customerName);
        option.setCustomerName(customerName);
        return option;
    }
}
