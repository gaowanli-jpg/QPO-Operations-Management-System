package com.machining.quote.controller;

import com.machining.quote.config.RequireRole;
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

import java.util.Map;

@RestController
@RequireRole({"MARKET", "ADMIN"})
@RequestMapping("/api/market/quote-process")
public class QuoteProcessController {
    @Autowired
    private QuoteProcessService quoteProcessService;

    @GetMapping("/{rfqId}")
    public Map<String, Object> getQuoteProcess(@PathVariable Integer rfqId) {
        return quoteProcessService.getQuoteProcess(rfqId);
    }

    @PostMapping("/lead-time/shift")
    public Map<String, Object> shiftLeadTimes(@RequestBody QuoteLeadTimeShiftReq req) {
        return quoteProcessService.shiftLeadTimes(req);
    }

    @PostMapping("/lead-time/set")
    public Map<String, Object> setLeadTimes(@RequestBody QuoteLeadTimeSetReq req) {
        return quoteProcessService.setLeadTimes(req);
    }

    @PostMapping("/lead-time/update")
    public Map<String, Object> updateLeadTime(@RequestBody QuoteLeadTimeUpdateReq req) {
        return quoteProcessService.updateLeadTime(req);
    }
}
