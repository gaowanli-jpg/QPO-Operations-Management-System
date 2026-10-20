package com.machining.quote.controller;

import com.machining.quote.config.RequirePermission;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.PartSearchItemDetailDTO;
import com.machining.quote.dto.PartSearchItemRowDTO;
import com.machining.quote.service.PartSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market/part-search")
public class PartSearchController {
    @Autowired
    private PartSearchService partSearchService;

    @GetMapping("/rfq-items")
    @RequirePermission("market:part-search:view")
    public PageResult<PartSearchItemRowDTO> listRfqItems(
            @RequestParam("partNo") String partNo,
            @RequestParam(value = "version", required = false) String version,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return partSearchService.searchRfqItems(partNo, version, page, pageSize);
    }

    @GetMapping("/rfq-items/{itemId}")
    @RequirePermission("market:part-search:view")
    public PartSearchItemDetailDTO getRfqItem(@PathVariable Integer itemId) {
        return partSearchService.getRfqItemDetail(itemId);
    }

    @GetMapping("/sales-items")
    @RequirePermission("market:part-search:view")
    public PageResult<PartSearchItemRowDTO> listSalesItems(
            @RequestParam("partNo") String partNo,
            @RequestParam(value = "version", required = false) String version,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return partSearchService.searchSalesItems(partNo, version, page, pageSize);
    }

    @GetMapping("/sales-items/{itemId}")
    @RequirePermission("market:part-search:view")
    public PartSearchItemDetailDTO getSalesItem(@PathVariable Long itemId) {
        return partSearchService.getSalesItemDetail(itemId);
    }
}
