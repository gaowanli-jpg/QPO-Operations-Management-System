package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.dto.LockedItemDTO;
import com.machining.quote.dto.LockedItemPageDTO;
import com.machining.quote.dto.LockedItemQueryReq;
import com.machining.quote.mapper.OrderDeliveryTicketAllocationMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class LockedItemService {
    private static final int TICKET_ALLOCATION_LIMIT = 1000;

    @Autowired
    private OrderDeliveryTicketAllocationMapper allocationMapper;
    @Autowired
    private DataScopeService dataScopeService;

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

    private QueryWrapper<LockedItemDTO> buildQuery(LockedItemQueryReq req) {
        QueryWrapper<LockedItemDTO> query = new QueryWrapper<>();
        applyLike(query, "COALESCE(alloc.customer_name, rfq.customer_name)", req.getCustomerName());
        applyLike(query, "rfq.rfq_no", req.getRfqNo());
        applyLike(query, "alloc.part_no", req.getPartNo());
        applyEquals(query, "alloc.version", req.getVersion());
        applyLike(query, "alloc.material_no", req.getMaterialNo());
        applyLike(query, "alloc.internal_project_no", req.getInternalProjectNo());
        applyEquals(query, "alloc.allocation_type", normalizeUpper(req.getAllocationType()));
        applyLike(query, "alloc.wms_production_order_no", req.getWmsProductionOrderNo());
        if (req.getDateFrom() != null) {
            query.ge("alloc.created_at", req.getDateFrom().atStartOfDay());
        }
        if (req.getDateTo() != null) {
            query.lt("alloc.created_at", req.getDateTo().plusDays(1).atStartOfDay());
        }
        dataScopeService.applyRfqScope(query, "alloc.rfq_id");
        return query;
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

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
