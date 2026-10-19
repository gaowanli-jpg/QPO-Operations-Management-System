package com.machining.quote.controller;

import com.machining.quote.config.RequirePermission;
import com.machining.quote.dto.CustomerOptionDTO;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.SalesOrderChangeDispatchModeReq;
import com.machining.quote.dto.SalesOrderChangeDispatchSettingDTO;
import com.machining.quote.dto.SalesOrderChangeNoticeDTO;
import com.machining.quote.dto.SalesOrderChangeNoticeQueryReq;
import com.machining.quote.dto.SalesOrderChangeNoticeSendReq;
import com.machining.quote.entity.SystemTask;
import com.machining.quote.service.CustomerMasterService;
import com.machining.quote.service.SalesOrderChangeNoticeService;
import com.machining.quote.service.SystemTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/change/sales-order-changes")
public class SalesOrderChangeNoticeController {
    @Autowired
    private SalesOrderChangeNoticeService noticeService;
    @Autowired
    private SystemTaskService systemTaskService;
    @Autowired(required = false)
    private CustomerMasterService customerMasterService;

    @GetMapping
    @RequirePermission("change:sales-change:view")
    public PageResult<SalesOrderChangeNoticeDTO> list(SalesOrderChangeNoticeQueryReq req) {
        return noticeService.page(req);
    }

    @GetMapping("/{noticeId}")
    @RequirePermission("change:sales-change:view")
    public SalesOrderChangeNoticeDTO detail(@PathVariable String noticeId) {
        return noticeService.detail(noticeId);
    }

    @GetMapping("/customers")
    @RequirePermission("change:sales-change:customer-select")
    public PageResult<CustomerOptionDTO> customers(
            @RequestParam(value = "field", required = false) String field,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        if (customerMasterService == null) {
            return PageResult.of(java.util.List.of(), 0, page == null ? 1 : page, pageSize == null ? 20 : pageSize);
        }
        return customerMasterService.pageCustomerOptions(field, keyword, page, pageSize);
    }

    @PostMapping("/batch-send")
    @RequirePermission("change:sales-change:send")
    public SystemTask batchSend(@RequestBody(required = false) SalesOrderChangeNoticeSendReq req) {
        return systemTaskService.submitSalesOrderChangeNoticeTask(req == null ? null : req.getNoticeIds());
    }

    @GetMapping("/settings")
    @RequirePermission("change:sales-change:view")
    public SalesOrderChangeDispatchSettingDTO setting() {
        return noticeService.setting();
    }

    @PutMapping("/settings/mode")
    @RequirePermission("change:sales-change:mode-update")
    public SalesOrderChangeDispatchSettingDTO updateMode(@RequestBody SalesOrderChangeDispatchModeReq req) {
        return noticeService.updateMode(req);
    }
}
