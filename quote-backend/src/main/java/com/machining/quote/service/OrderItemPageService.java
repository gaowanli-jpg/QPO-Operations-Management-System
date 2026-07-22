package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.OrderItemPageDTO;
import com.machining.quote.dto.OrderItemPageReq;
import com.machining.quote.dto.OrderItemSummaryDTO;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderRfqMapper;
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
        long offset = (long) (page - 1) * pageSize;

        Long total = itemMapper.selectCount(buildFilteredQuery(req));
        QueryWrapper<ItemQuoteVersion> pageQuery = buildFilteredQuery(req)
                .orderByAsc("id")
                .last("LIMIT " + pageSize + " OFFSET " + offset);
        List<ItemQuoteVersion> records = itemMapper.selectList(pageQuery);
        OrderItemSummaryDTO summary = querySummary(req.getRfqId());

        return OrderItemPageDTO.of(records, total == null ? 0L : total, page, pageSize, summary);
    }

    private QueryWrapper<ItemQuoteVersion> buildFilteredQuery(OrderItemPageReq req) {
        QueryWrapper<ItemQuoteVersion> query = new QueryWrapper<>();
        query.eq("rfq_id", req.getRfqId());
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
        return query;
    }

    private OrderItemSummaryDTO querySummary(Integer rfqId) {
        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT
                    COUNT(*) AS item_count,
                    SUM(CASE WHEN final_unit_price IS NOT NULL THEN 1 ELSE 0 END) AS priced_count,
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
