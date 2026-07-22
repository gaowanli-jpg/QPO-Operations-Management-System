package com.machining.quote.controller;

import com.machining.quote.config.RequirePermission;
import com.machining.quote.dto.OrderItemPageReq;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
    public Map<String, Object> getQuoteProcessSummary(@PathVariable Integer rfqId) {
        return quoteProcessService.getQuoteProcessSummary(rfqId);
    }

    @GetMapping("/{rfqId}/items")
    @RequirePermission("market:quote-process:view")
    public PageResult<Map<String, Object>> listQuoteProcessItems(
            @PathVariable Integer rfqId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "partNo", required = false) String partNo,
            @RequestParam(value = "version", required = false) String version) {
        OrderItemPageReq req = new OrderItemPageReq();
        req.setRfqId(rfqId);
        req.setPage(page);
        req.setPageSize(pageSize);
        req.setKeyword(keyword);
        req.setStatus(status);
        req.setPartNo(partNo);
        req.setVersion(version);
        return quoteProcessService.listQuoteProcessItems(req);
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

}
