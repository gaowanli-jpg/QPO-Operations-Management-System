package com.machining.quote.controller;

import com.machining.quote.config.RequireRole;
import com.machining.quote.dto.InternalTargetLeadTimeSetReq;
import com.machining.quote.dto.InternalTargetLeadTimeShiftReq;
import com.machining.quote.dto.InternalTargetLeadTimeUpdateReq;
import com.machining.quote.service.InternalTargetLeadTimeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequireRole({"MARKET", "ADMIN"})
@RequestMapping("/api/market/internal-target-lead-time")
public class InternalTargetLeadTimeController {
    @Autowired
    private InternalTargetLeadTimeService internalTargetLeadTimeService;

    @GetMapping("/{rfqId}/items")
    public List<Map<String, Object>> listItems(@PathVariable Integer rfqId) {
        return internalTargetLeadTimeService.listItemRows(rfqId);
    }

    @PostMapping("/shift")
    public Map<String, Object> shiftLeadTimes(@RequestBody InternalTargetLeadTimeShiftReq req) {
        return internalTargetLeadTimeService.shiftLeadTimes(req);
    }

    @PostMapping("/set")
    public Map<String, Object> setLeadTimes(@RequestBody InternalTargetLeadTimeSetReq req) {
        return internalTargetLeadTimeService.setLeadTimes(req);
    }

    @PostMapping("/update")
    public Map<String, Object> updateLeadTime(@RequestBody InternalTargetLeadTimeUpdateReq req) {
        return internalTargetLeadTimeService.updateLeadTime(req);
    }
}
