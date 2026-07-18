package com.machining.quote.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.config.RequirePermission;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.service.EngineerService;
import com.machining.quote.service.DataScopeService;
import com.machining.quote.dto.QuoteSubmitReq;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/engineer") // 工程部的统一网址前缀
public class EngineerController {

    // --- 统一在这里召唤需要的依赖（管家和查询器） ---
    @Autowired
    private ItemQuoteVersionMapper itemQuoteVersionMapper;

    @Autowired
    private EngineerService engineerService;

    @Autowired(required = false)
    private DataScopeService dataScopeService;

    // ---------------------------------------------

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

    @PostMapping("/complete-order/{rfqId}")
    @RequirePermission("engineer:pricing:complete-order")
    public Map<String, Object> completeOrderQuote(@PathVariable Integer rfqId) {
        engineerService.completeOrderQuote(rfqId);
        return Map.of("success", true, "message", "订单报价已完成，状态已更新为已报价");
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
}
