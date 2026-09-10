package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.config.AuthContext;
import com.machining.quote.dto.CustomerOptionDTO;
import com.machining.quote.dto.DeliveryItemBatchDiscardReq;
import com.machining.quote.dto.DeliveryItemBatchRestoreReq;
import com.machining.quote.dto.DeliveryItemDiscardReq;
import com.machining.quote.dto.DeliveryItemRestoreReq;
import com.machining.quote.dto.LockedItemDTO;
import com.machining.quote.dto.LockedItemPageDTO;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.ProductionItemQueryReq;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderDeliveryTicketAllocation;
import com.machining.quote.mapper.OrderDeliveryTicketAllocationMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ProductionItemService {
    private static final String PRODUCTION_REQUIRED_TYPE = "PRODUCTION_REQUIRED";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_CALLBACK_PENDING = "CALLBACK_PENDING";

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

    public LockedItemPageDTO listProductionItems(ProductionItemQueryReq req) {
        ProductionItemQueryReq effectiveReq = req == null ? new ProductionItemQueryReq() : req;
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

    public List<String> listCustomers() {
        if (customerMasterService != null) {
            return customerMasterService.listCustomerNamesForCurrentScope();
        }
        QueryWrapper<LockedItemDTO> query = new QueryWrapper<>();
        query.eq("alloc.allocation_type", PRODUCTION_REQUIRED_TYPE);
        applyProductionActiveScope(query);
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

    private QueryWrapper<LockedItemDTO> buildQuery(ProductionItemQueryReq req) {
        QueryWrapper<LockedItemDTO> query = new QueryWrapper<>();
        query.eq("alloc.allocation_type", PRODUCTION_REQUIRED_TYPE);
        applyCustomerFilter(query, req.getCustomerCode(), req.getCustomerName());
        applyLike(query, "alloc.rfq_no", req.getRfqNo());
        applyLike(query, "alloc.part_no", req.getPartNo());
        applyEquals(query, "alloc.version", req.getVersion());
        applyLike(query, "alloc.material_no", req.getMaterialNo());
        applyLike(query, "alloc.internal_project_no", req.getInternalProjectNo());
        applyLike(query, "alloc.customer_order_no", req.getCustomerOrderNo());
        applyLike(query, "alloc.customer_project_no", req.getCustomerProjectNo());
        applyEquals(query, "alloc.item_type", req.getItemType());
        applyProductionStatusScope(query, req.getMesProductionStatus());
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
            throw new RuntimeException("该待产零件已废弃");
        }
        if (STATUS_CALLBACK_PENDING.equalsIgnoreCase(safeText(allocation.getMesProductionStatus()))) {
            throw new RuntimeException("MES 待回调的待产零件不能废弃，请等待回调后再处理");
        }
        String reason = requiredReason(req == null ? null : req.getReason());
        LocalDateTime now = LocalDateTime.now();
        allocation.setDiscarded(true);
        allocation.setDiscardedAt(now);
        allocation.setDiscardedBy(currentUserId());
        allocation.setDiscardReason(reason);
        allocation.setUpdatedAt(now);
        if (allocationMapper.updateById(allocation) <= 0) {
            throw new RuntimeException("待产零件废弃失败，请刷新后重试");
        }
        Map<String, Object> result = actionResult(allocation, "待产零件已废弃。", "discarded", reason);
        recordAudit("PRODUCTION_ITEM_DISCARD", allocation, result, "废弃待产零件");
        return result;
    }

    @Transactional
    public Map<String, Object> restoreAllocation(Integer allocationId, DeliveryItemRestoreReq req) {
        OrderDeliveryTicketAllocation allocation = lockAllocation(allocationId);
        assertCanAccessAllocation(allocation);
        if (!Boolean.TRUE.equals(allocation.getDiscarded())) {
            throw new RuntimeException("只有已废弃的待产零件可以恢复");
        }
        String reason = optionalReason(req == null ? null : req.getReason());
        LocalDateTime now = LocalDateTime.now();
        allocation.setDiscarded(false);
        allocation.setRestoredAt(now);
        allocation.setRestoredBy(currentUserId());
        allocation.setRestoreReason(reason);
        allocation.setUpdatedAt(now);
        if (allocationMapper.updateById(allocation) <= 0) {
            throw new RuntimeException("待产零件恢复失败，请刷新后重试");
        }
        Map<String, Object> result = actionResult(allocation, "待产零件已恢复。", "active", reason);
        recordAudit("PRODUCTION_ITEM_RESTORE", allocation, result, "恢复待产零件");
        return result;
    }

    @Transactional
    public Map<String, Object> batchDiscardAllocations(DeliveryItemBatchDiscardReq req) {
        List<Integer> allocationIds = normalizeIds(req == null ? null : req.getAllocationIds());
        String reason = requiredReason(req == null ? null : req.getReason());
        DeliveryItemDiscardReq singleReq = new DeliveryItemDiscardReq();
        singleReq.setReason(reason);
        List<Map<String, Object>> results = allocationIds.stream()
                .map(allocationId -> discardAllocation(allocationId, singleReq))
                .toList();
        return batchResult("待产零件批量废弃完成。", "discarded", reason, "allocationIds", allocationIds, results);
    }

    @Transactional
    public Map<String, Object> batchRestoreAllocations(DeliveryItemBatchRestoreReq req) {
        List<Integer> allocationIds = normalizeIds(req == null ? null : req.getAllocationIds());
        String reason = optionalReason(req == null ? null : req.getReason());
        DeliveryItemRestoreReq singleReq = new DeliveryItemRestoreReq();
        singleReq.setReason(reason);
        List<Map<String, Object>> results = allocationIds.stream()
                .map(allocationId -> restoreAllocation(allocationId, singleReq))
                .toList();
        return batchResult("待产零件批量恢复完成。", "active", reason, "allocationIds", allocationIds, results);
    }

    private void applyDiscardedScope(QueryWrapper<?> query, Boolean discarded) {
        query.eq("alloc.discarded", Boolean.TRUE.equals(discarded));
    }

    private OrderDeliveryTicketAllocation lockAllocation(Integer allocationId) {
        if (allocationId == null) {
            throw new RuntimeException("待产零件 ID 不能为空");
        }
        OrderDeliveryTicketAllocation allocation = allocationMapper.selectByIdForUpdate(allocationId);
        if (allocation == null) {
            throw new RuntimeException("待产零件不存在");
        }
        if (!PRODUCTION_REQUIRED_TYPE.equalsIgnoreCase(safeText(allocation.getAllocationType()))) {
            throw new RuntimeException("只有待产零件可以废弃或恢复");
        }
        return allocation;
    }

    private List<Integer> normalizeIds(List<Integer> ids) {
        if (ids == null) {
            throw new RuntimeException("请选择需要处理的待产零件");
        }
        List<Integer> normalized = new LinkedHashSet<>(ids).stream()
                .filter(id -> id != null && id > 0)
                .toList();
        if (normalized.isEmpty()) {
            throw new RuntimeException("请选择需要处理的待产零件");
        }
        return normalized;
    }

    private void assertCanAccessAllocation(OrderDeliveryTicketAllocation allocation) {
        OrderDeliveryTicket ticket = allocation.getTicketId() == null ? null : ticketMapper.selectById(allocation.getTicketId());
        if (ticket == null) {
            throw new RuntimeException("待产零件对应的提单不存在");
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
        result.put("discarded", Boolean.TRUE.equals(allocation.getDiscarded()));
        result.put("state", state);
        result.put("reason", reason);
        result.put("mesProductionStatus", allocation.getMesProductionStatus());
        result.put("discardedAt", allocation.getDiscardedAt());
        result.put("discardedBy", allocation.getDiscardedBy());
        result.put("restoredAt", allocation.getRestoredAt());
        result.put("restoredBy", allocation.getRestoredBy());
        return result;
    }

    private Map<String, Object> batchResult(
            String message,
            String state,
            String reason,
            String idKey,
            List<Integer> ids,
            List<Map<String, Object>> results) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", message);
        result.put("state", state);
        result.put("reason", reason);
        result.put("count", results.size());
        result.put(idKey, ids);
        result.put("results", results);
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

    private String safeText(String value) {
        return value == null ? "" : value.trim();
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

    private void applyProductionStatusScope(QueryWrapper<LockedItemDTO> query, String status) {
        String normalized = normalizeUpper(status);
        if (!hasText(normalized)) {
            applyProductionActiveScope(query);
            return;
        }
        switch (normalized) {
            case STATUS_PENDING -> query.apply("COALESCE(NULLIF(TRIM(alloc.mes_production_status), ''), 'PENDING') = 'PENDING'");
            case STATUS_PROCESSING -> query.eq("alloc.mes_production_status", STATUS_PROCESSING);
            case STATUS_FAILED -> query.eq("alloc.mes_production_status", STATUS_FAILED);
            default -> {
                applyProductionActiveScope(query);
                query.eq("alloc.mes_production_status", normalized);
            }
        }
    }

    private void applyProductionActiveScope(QueryWrapper<LockedItemDTO> query) {
        query.apply("COALESCE(NULLIF(TRIM(alloc.mes_production_status), ''), 'PENDING') <> 'SUCCESS'");
    }

    private void applyCustomerPresentScope(QueryWrapper<LockedItemDTO> query) {
        query.isNotNull("COALESCE(alloc.customer_name, ticket.customer_name, rfq.customer_name)");
        query.ne("COALESCE(alloc.customer_name, ticket.customer_name, rfq.customer_name)", "");
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
