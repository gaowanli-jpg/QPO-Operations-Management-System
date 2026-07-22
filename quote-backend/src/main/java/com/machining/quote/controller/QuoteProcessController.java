package com.machining.quote.controller;

import com.machining.quote.config.RequirePermission;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.QuoteLeadTimeSetReq;
import com.machining.quote.dto.QuoteLeadTimeShiftReq;
import com.machining.quote.dto.QuoteLeadTimeUpdateReq;
import com.machining.quote.service.QuoteProcessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/market/quote-process")
public class QuoteProcessController {
    @Autowired
    private QuoteProcessService quoteProcessService;

    @GetMapping("/{rfqId}")
    @RequirePermission("market:quote-process:view")
    public Map<String, Object> getQuoteProcess(@PathVariable Integer rfqId) {
        return quoteProcessService.getQuoteProcess(rfqId);
    }

    @GetMapping("/{rfqId}/summary")
    @RequirePermission("market:quote-process:view")
    @SuppressWarnings("unchecked")
    public Map<String, Object> getQuoteProcessSummary(@PathVariable Integer rfqId) {
        Map<String, Object> workspace = quoteProcessService.getQuoteProcess(rfqId);
        List<Map<String, Object>> items = (List<Map<String, Object>>) workspace.getOrDefault("itemList", List.of());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderInfo", workspace.get("orderInfo"));
        result.put("itemCount", items.size());
        result.put("missingLeadTimeCount", items.stream().filter(row -> isBlank(row.get("effectiveLeadTime"))).count());
        result.put("quotedCount", items.stream().filter(row -> row.get("finalUnitPrice") != null).count());
        return result;
    }

    @GetMapping("/{rfqId}/items")
    @RequirePermission("market:quote-process:view")
    @SuppressWarnings("unchecked")
    public PageResult<Map<String, Object>> listQuoteProcessItems(
            @PathVariable Integer rfqId,
            @org.springframework.web.bind.annotation.RequestParam(value = "page", required = false) Integer page,
            @org.springframework.web.bind.annotation.RequestParam(value = "pageSize", required = false) Integer pageSize) {
        Map<String, Object> workspace = quoteProcessService.getQuoteProcess(rfqId);
        return page((List<Map<String, Object>>) workspace.getOrDefault("itemList", List.of()), page, pageSize);
    }

    @PostMapping("/lead-time/shift")
    @RequirePermission("market:quote-process:update-lead-time")
    public Map<String, Object> shiftLeadTimes(@RequestBody QuoteLeadTimeShiftReq req) {
        return quoteProcessService.shiftLeadTimes(req);
    }

    @PostMapping("/lead-time/set")
    @RequirePermission("market:quote-process:update-lead-time")
    public Map<String, Object> setLeadTimes(@RequestBody QuoteLeadTimeSetReq req) {
        return quoteProcessService.setLeadTimes(req);
    }

    @PostMapping("/lead-time/update")
    @RequirePermission("market:quote-process:update-lead-time")
    public Map<String, Object> updateLeadTime(@RequestBody QuoteLeadTimeUpdateReq req) {
        return quoteProcessService.updateLeadTime(req);
    }

    @PostMapping("/{rfqId}/save-quote")
    @RequirePermission("market:quote-process:save-quote")
    public Map<String, Object> saveQuoteSheet(@PathVariable Integer rfqId) {
        return quoteProcessService.saveQuoteSheet(rfqId);
    }

    private static boolean isBlank(Object value) {
        return value == null || value.toString().trim().isEmpty();
    }

    private static <T> PageResult<T> page(List<T> allRows, Integer page, Integer pageSize) {
        List<T> rows = allRows == null ? List.of() : allRows;
        int safePage = page == null || page < 1 ? 1 : page;
        int safePageSize = pageSize == null || pageSize < 1 ? 50 : Math.min(pageSize, 100);
        int fromIndex = (int) Math.min((long) (safePage - 1) * safePageSize, rows.size());
        int toIndex = Math.min(fromIndex + safePageSize, rows.size());
        return PageResult.of(rows.subList(fromIndex, toIndex), rows.size(), safePage, safePageSize);
    }
}
