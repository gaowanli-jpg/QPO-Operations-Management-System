package com.machining.quote.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.config.RequirePermission;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.CustomerOptionDTO;
import com.machining.quote.dto.OrderItemPageDTO;
import com.machining.quote.dto.OrderItemPageReq;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.PricingMaterialOptionDTO;
import com.machining.quote.dto.PricingItemRemarkUpdateReq;
import com.machining.quote.dto.PricingOptionPageReq;
import com.machining.quote.dto.PricingProcessOptionDTO;
import com.machining.quote.dto.ProcessArrangementTreeNodeDTO;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.service.EngineerService;
import com.machining.quote.service.DataScopeService;
import com.machining.quote.service.EngineeringPricingOptionService;
import com.machining.quote.service.ExcelExportService;
import com.machining.quote.dto.QuoteSubmitReq;
import com.machining.quote.service.OrderItemPageService;
import com.machining.quote.service.OrderManagementService;
import com.machining.quote.service.ProcessArrangementService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/engineer") // 工程部的统一网址前缀
public class EngineerController {
    private static final int SETUP_PREVIEW_LIMIT = 10;

    // --- 统一在这里召唤需要的依赖（管家和查询器） ---
    @Autowired
    private ItemQuoteVersionMapper itemQuoteVersionMapper;

    @Autowired
    private EngineerService engineerService;

    @Autowired(required = false)
    private DataScopeService dataScopeService;

    @Autowired
    private OrderManagementService orderManagementService;

    @Autowired
    private OrderItemPageService orderItemPageService;

    @Autowired
    private OrderRfqMapper orderRfqMapper;

    @Autowired
    private ExcelExportService excelExportService;

    @Autowired
    private EngineeringPricingOptionService pricingOptionService;

    @Autowired
    private ProcessArrangementService processArrangementService;

    // ---------------------------------------------

    @GetMapping("/setup/customers")
    @RequirePermission("engineer:setup:customer-select")
    public PageResult<CustomerOptionDTO> listSetupCustomers(
            @RequestParam(value = "field", required = false) String field,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return orderManagementService.pageCustomerOptions(field, keyword, page, pageSize);
    }

    @GetMapping("/setup/orders")
    @RequirePermission("engineer:setup:order-select")
    public PageResult<OrderRfq> listSetupOrders(
            @RequestParam(value = "customerCode", required = false) String customerCode,
            @RequestParam(value = "customerName", required = false) String customerName,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return orderManagementService.listOrders(customerCode, customerName, keyword, status, page, pageSize);
    }

    @GetMapping("/setup/summary/{rfqId}")
    @RequirePermission("engineer:setup:view")
    public Map<String, Object> getSetupSummary(@PathVariable Integer rfqId) {
        return getSetupOrderSummary(rfqId);
    }

    @GetMapping("/pricing/summary/{rfqId}")
    @RequirePermission("engineer:pricing:view")
    public Map<String, Object> getPricingSummary(@PathVariable Integer rfqId) {
        OrderRfq order = orderRfqMapper.selectById(rfqId);
        if (dataScopeService != null) {
            dataScopeService.assertCanAccessOrder(order);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("orderInfo", order);
        result.put("summary", orderItemPageService.summarizeItems(rfqId));
        return result;
    }

    @GetMapping("/pricing/customers")
    @RequirePermission("engineer:pricing:view")
    public PageResult<CustomerOptionDTO> listPricingCustomers(
            @RequestParam(value = "field", required = false) String field,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return orderManagementService.pageCustomerOptions(field, keyword, page, pageSize);
    }

    @GetMapping("/pricing/supplemental-drawing/orders")
    @RequirePermission("engineer:pricing:view")
    public PageResult<OrderRfq> listSupplementalDrawingPricingOrders(
            @RequestParam(value = "customerName", required = false) String customerName,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return engineerService.listSupplementalPricingOrders(customerName, keyword, status, page, pageSize);
    }

    @GetMapping("/pricing/options/materials")
    @RequirePermission("engineer:pricing:view")
    public PageResult<PricingMaterialOptionDTO> pagePricingMaterialOptions(@ModelAttribute PricingOptionPageReq req) {
        return pricingOptionService.pageMaterialOptions(req);
    }

    @GetMapping("/pricing/options/material-u9-category-names")
    @RequirePermission("engineer:pricing:view")
    public List<String> listPricingMaterialU9CategoryNames(@ModelAttribute PricingOptionPageReq req) {
        return pricingOptionService.listMaterialU9CategoryNames(req);
    }

    @GetMapping("/pricing/options/processes")
    @RequirePermission("engineer:pricing:view")
    public PageResult<PricingProcessOptionDTO> pagePricingProcessOptions(@ModelAttribute PricingOptionPageReq req) {
        return pricingOptionService.pageProcessOptions(req);
    }

    @GetMapping("/pricing/options/process-arrangement/tree")
    @RequirePermission("engineer:pricing:view")
    public List<ProcessArrangementTreeNodeDTO> listPricingProcessArrangementTree() {
        return processArrangementService.listTree();
    }

    @GetMapping("/pricing/items/{rfqId}")
    @RequirePermission("engineer:pricing:view")
    public PageResult<ItemQuoteVersion> listPricingItems(
            @PathVariable Integer rfqId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "scope", required = false) String scope,
            @RequestParam(value = "partNo", required = false) String partNo,
            @RequestParam(value = "version", required = false) String version,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        OrderItemPageReq req = new OrderItemPageReq();
        req.setRfqId(rfqId);
        req.setKeyword(keyword);
        req.setStatus(status);
        req.setScope(scope);
        req.setPartNo(partNo);
        req.setVersion(version);
        req.setPage(page);
        req.setPageSize(pageSize);
        OrderItemPageDTO result = orderItemPageService.pageItems(req);
        return PageResult.of(result.getRecords(), result.getTotal(), result.getPage(), result.getPageSize());
    }

    @GetMapping("/pricing/assembly-child-candidates/{rfqId}")
    @RequirePermission("engineer:pricing:view")
    public PageResult<ItemQuoteVersion> listAssemblyChildCandidates(
            @PathVariable Integer rfqId,
            @RequestParam(value = "parentItemId", required = false) Integer parentItemId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        OrderItemPageReq req = new OrderItemPageReq();
        req.setRfqId(rfqId);
        req.setExcludeItemId(parentItemId);
        req.setKeyword(keyword);
        req.setPage(page);
        req.setPageSize(pageSize);
        OrderItemPageDTO result = orderItemPageService.pageItems(req);
        return PageResult.of(result.getRecords(), result.getTotal(), result.getPage(), result.getPageSize());
    }

    /**
     * 获取待评估的零件任务列表
     * 网址: http://localhost:8080/api/engineer/pending-items
     */
    @GetMapping("/pending-items")
    @RequirePermission("engineer:setup:view")
    public List<ItemQuoteVersion> getPendingItems() {
        // 创建一个查询条件构造器
        QueryWrapper<ItemQuoteVersion> queryWrapper = new QueryWrapper<>();
        // 按照 ID 倒序排列，让最新导入的零件排在最前面
        if (dataScopeService != null) {
            dataScopeService.applyRfqScope(queryWrapper, "rfq_id");
        }
        queryWrapper.and(wrapper -> wrapper
                .ne("status", QuoteStatusWorkflow.DISCARDED)
                .or()
                .isNull("status"));
        queryWrapper.orderByDesc("id");

        // 去数据库里把符合条件的零件全部查出来并返回
        return itemQuoteVersionMapper.selectList(queryWrapper);
    }

    /**
     * 工程师提交工艺矩阵与报价
     * 网址: http://localhost:8080/api/engineer/submit-quote
     */
    @PostMapping("/submit-quote")
    @RequirePermission("engineer:pricing:save-item")
    public Map<String, Object> submitQuote(@RequestBody QuoteSubmitReq req) {
        engineerService.calculateAndSaveQuote(req);
        return Map.of("success", true, "message", "核价成功，矩阵数据已入库。");
    }

    @PutMapping("/pricing/items/{itemId}/remark")
    @RequirePermission("engineer:pricing:save-item")
    public Map<String, Object> updatePricingItemRemark(
            @PathVariable Integer itemId,
            @RequestBody(required = false) PricingItemRemarkUpdateReq req) {
        ItemQuoteVersion item = engineerService.updatePricingItemRemark(itemId, req);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "备注已保存");
        result.put("remark", item == null ? null : item.getRemark());
        return result;
    }

    @PostMapping("/complete-order/{rfqId}")
    @RequirePermission("engineer:pricing:complete-order")
    public Map<String, Object> completeOrderQuote(@PathVariable Integer rfqId) {
        engineerService.completeOrderQuote(rfqId);
        return Map.of("success", true, "message", "订单核价已完成，已进入报价处理");
    }

    @PostMapping("/pricing/supplemental-drawing/complete-order/{rfqId}")
    @RequirePermission("engineer:pricing:complete-order")
    public Map<String, Object> completeSupplementalDrawingQuote(@PathVariable Integer rfqId) {
        Map<String, Object> result = new HashMap<>(engineerService.completeSupplementalDrawingQuote(rfqId));
        result.put("success", true);
        result.put("message", "补图待核价已完成，订单状态保持不变");
        return result;
    }

    @GetMapping("/pricing/export-sheet/{rfqId}")
    @RequirePermission("engineer:pricing:export-sheet")
    public void exportPricingSheet(@PathVariable Integer rfqId, HttpServletResponse response) throws IOException {
        excelExportService.exportPricingSheet(rfqId, response);
    }

    @PostMapping("/refresh-quotes/{rfqId}")
    @RequirePermission("engineer:pricing:refresh")
    public Map<String, Object> refreshOrderQuotes(@PathVariable Integer rfqId) {
        return engineerService.refreshOrderQuotes(rfqId);
    }

    @GetMapping("/repeat-analysis/{rfqId}")
    @RequirePermission("engineer:pricing:view")
    public Map<String, Object> analyzeRepeatOrder(@PathVariable Integer rfqId) {
        return engineerService.analyzeRepeatOrder(rfqId);
    }

    @GetMapping("/assembly-relations/{parentItemId}")
    @RequirePermission("engineer:pricing:view")
    public List<Map<String, Object>> listAssemblyRelations(@PathVariable Integer parentItemId) {
        return engineerService.listAssemblyRelations(parentItemId);
    }

    @PostMapping("/apply-repeat-quotes/{rfqId}")
    @RequirePermission("engineer:pricing:save-item")
    public Map<String, Object> applyRepeatQuotes(@PathVariable Integer rfqId) {
        return engineerService.applyRepeatQuotes(rfqId);
    }

    @PostMapping("/order-config/{rfqId}")
    @RequirePermission("engineer:setup:save-config")
    public Map<String, Object> saveOrderEngineerConfig(
            @PathVariable Integer rfqId,
            @RequestBody Map<String, Object> config) {
        engineerService.saveOrderEngineerConfig(rfqId, config);
        return Map.of("success", true, "message", "订单全局默认设定已保存");
    }

    private Map<String, Object> getOrderSummary(Integer rfqId) {
        OrderRfq order = orderRfqMapper.selectById(rfqId);
        if (dataScopeService != null) {
            dataScopeService.assertCanAccessOrder(order);
        }
        QueryWrapper<ItemQuoteVersion> qw = new QueryWrapper<>();
        qw.eq("rfq_id", rfqId).orderByAsc("id");
        List<ItemQuoteVersion> items = itemQuoteVersionMapper.selectList(qw);

        Map<String, Object> result = new HashMap<>();
        result.put("orderInfo", order);
        result.put("itemList", items);
        return result;
    }

    private Map<String, Object> getSetupOrderSummary(Integer rfqId) {
        OrderRfq order = orderRfqMapper.selectById(rfqId);
        if (dataScopeService != null) {
            dataScopeService.assertCanAccessOrder(order);
        }

        QueryWrapper<ItemQuoteVersion> countQw = new QueryWrapper<>();
        countQw.eq("rfq_id", rfqId);
        Long itemCount = itemQuoteVersionMapper.selectCount(countQw);

        QueryWrapper<ItemQuoteVersion> previewQw = new QueryWrapper<>();
        previewQw.eq("rfq_id", rfqId)
                .orderByAsc("id")
                .last("LIMIT " + SETUP_PREVIEW_LIMIT);
        List<ItemQuoteVersion> previewItems = itemQuoteVersionMapper.selectList(previewQw);

        Map<String, Object> result = new HashMap<>();
        result.put("orderInfo", order);
        result.put("itemList", previewItems);
        result.put("previewItemList", previewItems);
        result.put("itemCount", itemCount == null ? 0L : itemCount);
        result.put("previewLimit", SETUP_PREVIEW_LIMIT);
        return result;
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
