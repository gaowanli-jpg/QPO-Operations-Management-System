package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.machining.quote.config.AuthContext;
import com.machining.quote.dto.CustomerOptionDTO;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.SalesOrderLinkedChangeNoticeHandleReq;
import com.machining.quote.dto.SalesOrderLinkedChangeNoticeQueryReq;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderDeliveryTicketAllocation;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.entity.SalesOrderLinkedChangeNotice;
import com.machining.quote.mapper.SalesOrderLinkedChangeNoticeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SalesOrderLinkedChangeNoticeService {
    static final String ACTION_HANDLE = "LINKED_CHANGE_NOTICE_HANDLE";
    static final String ACTION_REOPEN = "LINKED_CHANGE_NOTICE_REOPEN";
    private static final String UNHANDLED = "UNHANDLED";
    private static final String HANDLED = "HANDLED";

    @Autowired
    private SalesOrderLinkedChangeNoticeMapper noticeMapper;
    @Autowired
    private SalesOrderLinkedChangeNoticeFactory factory;
    @Autowired(required = false)
    private DataScopeService dataScopeService;
    @Autowired(required = false)
    private CustomerMasterService customerMasterService;
    @Autowired(required = false)
    private AuditService auditService;

    public PageResult<SalesOrderLinkedChangeNotice> page(SalesOrderLinkedChangeNoticeQueryReq req) {
        SalesOrderLinkedChangeNoticeQueryReq safeReq = req == null ? new SalesOrderLinkedChangeNoticeQueryReq() : req;
        int page = normalizePage(safeReq.getPage());
        int pageSize = normalizePageSize(safeReq.getPageSize());
        QueryWrapper<SalesOrderLinkedChangeNotice> countQuery = buildQuery(safeReq);
        Long total = noticeMapper.selectCount(countQuery);
        if (total == null || total == 0) return PageResult.of(List.of(), 0, page, pageSize);
        QueryWrapper<SalesOrderLinkedChangeNotice> listQuery = buildQuery(safeReq);
        listQuery.orderByDesc("linked_discarded_at").orderByDesc("id")
                .last("LIMIT " + pageSize + " OFFSET " + ((page - 1L) * pageSize));
        return PageResult.of(noticeMapper.selectList(listQuery), total, page, pageSize);
    }

    public SalesOrderLinkedChangeNotice detail(Long id) {
        QueryWrapper<SalesOrderLinkedChangeNotice> query = new QueryWrapper<>();
        query.eq("id", id);
        applyDataScope(query);
        SalesOrderLinkedChangeNotice row = noticeMapper.selectOne(query);
        if (row == null) throw new IllegalArgumentException("关联变更记录不存在或无权查看");
        return row;
    }

    public PageResult<CustomerOptionDTO> pageCustomers(String field, String keyword, Integer page, Integer pageSize) {
        if (customerMasterService == null) return PageResult.of(List.of(), 0, normalizePage(page), normalizePageSize(pageSize));
        return customerMasterService.pageCustomerOptions(field, keyword, page, pageSize);
    }

    @Transactional
    public SalesOrderLinkedChangeNotice handle(Long id, SalesOrderLinkedChangeNoticeHandleReq req) {
        return updateHandling(id, req, true);
    }

    @Transactional
    public SalesOrderLinkedChangeNotice reopen(Long id, SalesOrderLinkedChangeNoticeHandleReq req) {
        return updateHandling(id, req, false);
    }

    public int createForItem(
            SalesOrderHeader order,
            SalesOrderItem item,
            List<OrderDeliveryTicket> tickets,
            List<OrderDeliveryTicketAllocation> allocations,
            String triggerScope,
            String reason,
            LocalDateTime at,
            Long operatorId) {
        String eventKey = UUID.randomUUID().toString();
        SalesOrderLinkedChangeNotice snapshot = factory.create(order, item, tickets, allocations,
                triggerScope, eventKey, reason, at, operatorId);
        return noticeMapper.insertBatch(List.of(snapshot));
    }

    public int createForItems(
            SalesOrderHeader order,
            List<SalesOrderItem> items,
            Map<Long, List<OrderDeliveryTicket>> ticketsByItem,
            Map<Long, List<OrderDeliveryTicketAllocation>> allocationsByItem,
            String reason,
            LocalDateTime at,
            Long operatorId) {
        if (items == null || items.isEmpty()) return 0;
        String eventKey = UUID.randomUUID().toString();
        List<SalesOrderLinkedChangeNotice> snapshots = new ArrayList<>(items.size());
        for (SalesOrderItem item : items) {
            snapshots.add(factory.create(order, item,
                    ticketsByItem.getOrDefault(item.getId(), List.of()),
                    allocationsByItem.getOrDefault(item.getId(), List.of()),
                    "ORDER", eventKey, reason, at, operatorId));
        }
        return noticeMapper.insertBatch(snapshots);
    }

    private SalesOrderLinkedChangeNotice updateHandling(Long id, SalesOrderLinkedChangeNoticeHandleReq req, boolean handled) {
        SalesOrderLinkedChangeNotice before = detail(id);
        String expected = handled ? UNHANDLED : HANDLED;
        String target = handled ? HANDLED : UNHANDLED;
        if (target.equals(before.getHandlingStatus())) return before;
        String requestedRemark = normalizeRemark(req == null ? null : req.getRemark());
        String remark = requestedRemark == null && !handled ? before.getHandleRemark() : requestedRemark;
        LocalDateTime now = LocalDateTime.now();
        Long operatorId = currentUserId();
        UpdateWrapper<SalesOrderLinkedChangeNotice> update = new UpdateWrapper<>();
        update.eq("id", id).eq("handling_status", expected)
                .set("handling_status", target).set("updated_at", now);
        if (handled) {
            update.set("handled_at", now).set("handled_by", operatorId).set("handle_remark", remark);
        } else {
            update.set("handled_at", null).set("handled_by", null).set("handle_remark", remark);
        }
        int count = noticeMapper.update(null, update);
        if (count == 0) {
            SalesOrderLinkedChangeNotice current = detail(id);
            if (target.equals(current.getHandlingStatus())) return current;
            throw new IllegalStateException("关联变更处理状态已变化，请刷新后重试");
        }
        SalesOrderLinkedChangeNotice after = detail(id);
        recordHandlingAudit(before, after, handled ? ACTION_HANDLE : ACTION_REOPEN);
        return after;
    }

    private QueryWrapper<SalesOrderLinkedChangeNotice> buildQuery(SalesOrderLinkedChangeNoticeQueryReq req) {
        QueryWrapper<SalesOrderLinkedChangeNotice> query = new QueryWrapper<>();
        if (req.getCustomerId() != null) query.eq("customer_id", req.getCustomerId());
        if (hasText(req.getCustomerCode())) query.eq("customer_code", req.getCustomerCode().trim());
        if (hasText(req.getCustomerOrderNo())) query.like("customer_order_no", req.getCustomerOrderNo().trim());
        if (hasText(req.getInternalProjectNo())) query.like("internal_project_no", req.getInternalProjectNo().trim());
        if (hasText(req.getSalesOrderNo())) query.like("sales_order_no", req.getSalesOrderNo().trim());
        if (hasText(req.getPartNo())) query.like("part_no", req.getPartNo().trim());
        if (hasText(req.getVersion())) query.eq("version", req.getVersion().trim());
        if (hasText(req.getTriggerScope())) query.eq("trigger_scope", req.getTriggerScope().trim().toUpperCase());
        if (hasText(req.getHandlingStatus())) query.eq("handling_status", req.getHandlingStatus().trim().toUpperCase());
        if (hasText(req.getWmsStatus())) query.eq("wms_status", req.getWmsStatus().trim().toUpperCase());
        if (hasText(req.getMesProductionStatus())) query.eq("mes_production_status", req.getMesProductionStatus().trim().toUpperCase());
        if (hasText(req.getStockPickingStatus())) query.eq("stock_picking_status", req.getStockPickingStatus().trim().toUpperCase());
        if (Boolean.TRUE.equals(req.getHasDownstreamImpact())) {
            query.gt("downstream_impact_count", 0);
        } else if (Boolean.FALSE.equals(req.getHasDownstreamImpact())) {
            query.eq("downstream_impact_count", 0);
        }
        if (req.getDateFrom() != null) query.ge("linked_discarded_at", req.getDateFrom().atStartOfDay());
        if (req.getDateTo() != null) query.lt("linked_discarded_at", req.getDateTo().plusDays(1).atStartOfDay());
        if (hasText(req.getKeyword())) {
            String keyword = req.getKeyword().trim();
            query.and(wrapper -> wrapper.like("sales_order_no", keyword)
                    .or().like("customer_name", keyword)
                    .or().like("part_no", keyword)
                    .or().like("version", keyword));
        }
        applyDataScope(query);
        return query;
    }

    private void applyDataScope(QueryWrapper<SalesOrderLinkedChangeNotice> query) {
        if (dataScopeService != null) dataScopeService.applySalesOrderIdScope(query, "sales_order_id");
    }

    private void recordHandlingAudit(SalesOrderLinkedChangeNotice before, SalesOrderLinkedChangeNotice after, String action) {
        if (auditService == null) return;
        Map<String, Object> beforeData = new LinkedHashMap<>();
        beforeData.put("handlingStatus", before.getHandlingStatus());
        Map<String, Object> afterData = new LinkedHashMap<>();
        afterData.put("handlingStatus", after.getHandlingStatus());
        afterData.put("salesOrderNo", after.getSalesOrderNo());
        afterData.put("lineNo", after.getLineNo());
        afterData.put("partNo", after.getPartNo());
        afterData.put("version", after.getVersion());
        afterData.put("wmsStatus", after.getWmsStatus());
        afterData.put("mesProductionStatus", after.getMesProductionStatus());
        afterData.put("stockPickingStatus", after.getStockPickingStatus());
        afterData.put("handledBy", after.getHandledBy());
        afterData.put("remarkLength", after.getHandleRemark() == null ? 0 : after.getHandleRemark().length());
        auditService.record(action, "sales_order_linked_change_notice", after.getId(), beforeData, afterData,
                "CHANGE", "SUCCESS", action.equals(ACTION_HANDLE) ? "已标记关联变更通知" : "已恢复关联变更未处理状态", null);
    }

    private String normalizeRemark(String value) {
        String normalized = value == null || value.trim().isEmpty() ? null : value.trim();
        if (normalized != null && normalized.length() > 500) throw new IllegalArgumentException("处理备注不能超过 500 个字符");
        return normalized;
    }

    private int normalizePage(Integer value) { return value == null || value < 1 ? 1 : value; }
    private int normalizePageSize(Integer value) { return value == null || value < 1 ? 20 : Math.min(value, 100); }
    private boolean hasText(String value) { return value != null && !value.trim().isEmpty(); }
    private Long currentUserId() {
        AuthContext.SessionUser user = AuthContext.get();
        return user == null || user.id() == null ? null : user.id().longValue();
    }
}
