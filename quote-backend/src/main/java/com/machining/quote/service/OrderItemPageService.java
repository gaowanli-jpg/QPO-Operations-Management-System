package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.OrderItemPageDTO;
import com.machining.quote.dto.OrderItemPageReq;
import com.machining.quote.dto.OrderItemSummaryDTO;
import com.machining.quote.dto.ReleaseItemBatchQueryReq;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class OrderItemPageService {
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 100;

    private final OrderRfqMapper orderRfqMapper;
    private final ItemQuoteVersionMapper itemMapper;
    private final DataScopeService dataScopeService;
    private final JdbcTemplate jdbcTemplate;

    public OrderItemPageService(
            OrderRfqMapper orderRfqMapper,
            ItemQuoteVersionMapper itemMapper,
            DataScopeService dataScopeService,
            JdbcTemplate jdbcTemplate) {
        this.orderRfqMapper = orderRfqMapper;
        this.itemMapper = itemMapper;
        this.dataScopeService = dataScopeService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public OrderItemPageDTO pageItems(OrderItemPageReq req) {
        if (req == null || req.getRfqId() == null) {
            throw new IllegalArgumentException("rfqId is required");
        }
        OrderRfq order = orderRfqMapper.selectById(req.getRfqId());
        if (order == null) {
            throw new IllegalArgumentException("Order does not exist");
        }
        dataScopeService.assertCanAccessOrder(order);

        int page = safePage(req.getPage());
        int pageSize = safePageSize(req.getPageSize());
        OrderItemSummaryDTO summary = querySummary(req.getRfqId());
        if (isSupplementalDrawingScope(req) && !QuoteStatusWorkflow.isReleasedOrderStatus(order.getStatus())) {
            return OrderItemPageDTO.of(List.of(), 0L, page, pageSize, summary);
        }
        long offset = (long) (page - 1) * pageSize;

        Long total = itemMapper.selectCount(buildFilteredQuery(req));
        QueryWrapper<ItemQuoteVersion> pageQuery = buildFilteredQuery(req)
                .orderByAsc("id")
                .last("LIMIT " + pageSize + " OFFSET " + offset);
        List<ItemQuoteVersion> records = itemMapper.selectList(pageQuery);

        return OrderItemPageDTO.of(records, total == null ? 0L : total, page, pageSize, summary);
    }

    public OrderItemPageDTO pageItemsBatch(ReleaseItemBatchQueryReq req) {
        int page = safePage(req == null ? null : req.getPage());
        int pageSize = safePageSize(req == null ? null : req.getPageSize());
        List<Integer> accessibleRfqIds = accessibleRfqIds(req == null ? List.of() : req.getRfqIds());
        if (accessibleRfqIds.isEmpty()) {
            return OrderItemPageDTO.of(List.of(), 0L, page, pageSize, null);
        }

        ReleaseItemBatchQueryReq queryReq = new ReleaseItemBatchQueryReq();
        queryReq.setRfqIds(accessibleRfqIds);
        queryReq.setKeyword(req == null ? null : req.getKeyword());
        queryReq.setStatus(req == null ? null : req.getStatus());

        long offset = (long) (page - 1) * pageSize;
        Long total = itemMapper.selectCount(buildFilteredBatchQuery(queryReq));
        QueryWrapper<ItemQuoteVersion> pageQuery = buildFilteredBatchQuery(queryReq)
                .orderByAsc("rfq_id")
                .orderByAsc("id")
                .last("LIMIT " + pageSize + " OFFSET " + offset);
        List<ItemQuoteVersion> records = itemMapper.selectList(pageQuery);

        return OrderItemPageDTO.of(records, total == null ? 0L : total, page, pageSize, null);
    }

    public OrderItemSummaryDTO summarizeItems(Integer rfqId) {
        if (rfqId == null) {
            throw new IllegalArgumentException("rfqId is required");
        }
        OrderRfq order = orderRfqMapper.selectById(rfqId);
        if (order == null) {
            throw new IllegalArgumentException("Order does not exist");
        }
        dataScopeService.assertCanAccessOrder(order);
        return querySummary(rfqId);
    }

    private QueryWrapper<ItemQuoteVersion> buildFilteredQuery(OrderItemPageReq req) {
        QueryWrapper<ItemQuoteVersion> query = new QueryWrapper<>();
        query.eq("rfq_id", req.getRfqId());
        if (req.getExcludeItemId() != null) {
            query.ne("id", req.getExcludeItemId());
        }
        if (!isBlank(req.getKeyword())) {
            String keyword = req.getKeyword().trim();
            query.and(wrapper -> wrapper
                    .like("part_no", keyword)
                    .or()
                    .like("version", keyword)
                    .or()
                    .like("description", keyword));
        }
        if (!isBlank(req.getStatus())) {
            query.eq("status", req.getStatus().trim());
        }
        if (!isBlank(req.getPartNo())) {
            query.like("part_no", req.getPartNo().trim());
        }
        if (!isBlank(req.getVersion())) {
            query.eq("version", req.getVersion().trim());
        }
        if (isSupplementalDrawingScope(req)) {
            applySupplementalDrawingPricingFilter(query);
        }
        return query;
    }

    private void applySupplementalDrawingPricingFilter(QueryWrapper<ItemQuoteVersion> query) {
        query.apply("NULLIF(BTRIM(drawing_url), '') IS NOT NULL")
                .isNull("tax_included_unit_price")
                .in("status", QuoteStatusWorkflow.UNPRICED, QuoteStatusWorkflow.NO_DRAWING);
    }

    private boolean isSupplementalDrawingScope(OrderItemPageReq req) {
        return req != null && OrderItemPageReq.SCOPE_SUPPLEMENTAL_DRAWING.equalsIgnoreCase(req.getScope());
    }

    private QueryWrapper<ItemQuoteVersion> buildFilteredBatchQuery(ReleaseItemBatchQueryReq req) {
        List<Integer> accessibleRfqIds = req.getRfqIds();
        QueryWrapper<ItemQuoteVersion> query = new QueryWrapper<>();
        query.in("rfq_id", accessibleRfqIds);
        if (!isBlank(req.getKeyword())) {
            String keyword = req.getKeyword().trim();
            query.and(wrapper -> wrapper
                    .like("part_no", keyword)
                    .or()
                    .like("version", keyword)
                    .or()
                    .like("description", keyword));
        }
        if (!isBlank(req.getStatus())) {
            query.eq("status", req.getStatus().trim());
        }
        return query;
    }

    private List<Integer> accessibleRfqIds(List<Integer> rawRfqIds) {
        List<Integer> rfqIds = new ArrayList<>(new LinkedHashSet<>(
                rawRfqIds == null ? List.of() : rawRfqIds.stream()
                        .filter(id -> id != null)
                        .toList()));
        if (rfqIds.isEmpty()) {
            return List.of();
        }

        Map<Integer, OrderRfq> orderById = new HashMap<>();
        orderRfqMapper.selectBatchIds(rfqIds).forEach(order -> orderById.put(order.getId(), order));

        List<Integer> result = new ArrayList<>();
        for (Integer rfqId : rfqIds) {
            OrderRfq order = orderById.get(rfqId);
            if (order == null) {
                continue;
            }
            if (canAccessOrder(order)) {
                result.add(rfqId);
            }
        }
        return result;
    }

    private boolean canAccessOrder(OrderRfq order) {
        if (dataScopeService == null) {
            return true;
        }
        try {
            dataScopeService.assertCanAccessOrder(order);
            return true;
        } catch (RuntimeException e) {
            if (isDataScopeDenied(e)) {
                return false;
            }
            throw e;
        }
    }

    private boolean isDataScopeDenied(RuntimeException e) {
        String message = e.getMessage();
        return message != null && message.startsWith("No data permission for order ");
    }

    private OrderItemSummaryDTO querySummary(Integer rfqId) {
        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT
                    COUNT(*) AS item_count,
                    SUM(CASE WHEN tax_included_unit_price IS NOT NULL THEN 1 ELSE 0 END) AS priced_count,
                    SUM(CASE WHEN status = ? THEN 1 ELSE 0 END) AS quoted_count,
                    SUM(CASE WHEN status IN (?, ?) THEN 1 ELSE 0 END) AS released_count,
                    SUM(CASE WHEN drawing_url IS NULL OR BTRIM(drawing_url) = '' THEN 1 ELSE 0 END) AS missing_drawing_count
                FROM item_quote_version
                WHERE rfq_id = ?
                """,
                QuoteStatusWorkflow.QUOTED,
                QuoteStatusWorkflow.ITEM_RELEASED,
                QuoteStatusWorkflow.RELEASED,
                rfqId);
        OrderItemSummaryDTO summary = new OrderItemSummaryDTO();
        summary.setItemCount(number(row.get("item_count")));
        summary.setPricedCount(number(row.get("priced_count")));
        summary.setQuotedCount(number(row.get("quoted_count")));
        summary.setReleasedCount(number(row.get("released_count")));
        summary.setMissingDrawingCount(number(row.get("missing_drawing_count")));
        return summary;
    }

    private int safePage(Integer page) {
        return page == null || page < 1 ? DEFAULT_PAGE : page;
    }

    private int safePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
