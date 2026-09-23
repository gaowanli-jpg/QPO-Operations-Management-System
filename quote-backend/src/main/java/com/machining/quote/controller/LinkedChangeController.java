package com.machining.quote.controller;

import com.machining.quote.config.RequirePermission;
import com.machining.quote.dto.CustomerOptionDTO;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.SalesOrderLinkedChangeNoticeHandleReq;
import com.machining.quote.dto.SalesOrderLinkedChangeNoticeQueryReq;
import com.machining.quote.entity.SalesOrderLinkedChangeNotice;
import com.machining.quote.service.SalesOrderLinkedChangeNoticeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/change/linked-changes")
public class LinkedChangeController {
    @Autowired
    private SalesOrderLinkedChangeNoticeService linkedChangeNoticeService;

    @GetMapping
    @RequirePermission("change:linked-change:view")
    public PageResult<SalesOrderLinkedChangeNotice> list(SalesOrderLinkedChangeNoticeQueryReq req) {
        return linkedChangeNoticeService.page(req);
    }

    @GetMapping("/customers")
    @RequirePermission("change:linked-change:customer-select")
    public PageResult<CustomerOptionDTO> customers(
            @RequestParam(value = "field", required = false) String field,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return linkedChangeNoticeService.pageCustomers(field, keyword, page, pageSize);
    }

    @GetMapping("/{id}")
    @RequirePermission("change:linked-change:view")
    public SalesOrderLinkedChangeNotice detail(@PathVariable Long id) {
        return linkedChangeNoticeService.detail(id);
    }

    @PostMapping("/{id}/handle")
    @RequirePermission("change:linked-change:handle")
    public SalesOrderLinkedChangeNotice handle(
            @PathVariable Long id,
            @RequestBody(required = false) SalesOrderLinkedChangeNoticeHandleReq req) {
        return linkedChangeNoticeService.handle(id, req);
    }

    @PostMapping("/{id}/reopen")
    @RequirePermission("change:linked-change:handle")
    public SalesOrderLinkedChangeNotice reopen(
            @PathVariable Long id,
            @RequestBody(required = false) SalesOrderLinkedChangeNoticeHandleReq req) {
        return linkedChangeNoticeService.reopen(id, req);
    }
}
