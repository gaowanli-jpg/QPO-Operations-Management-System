package com.machining.quote.controller;

import com.machining.quote.config.RequirePermission;
import com.machining.quote.dto.ProcessCategoryDTO;
import com.machining.quote.dto.ProcessCategorySaveReq;
import com.machining.quote.dto.ProcessDetailDTO;
import com.machining.quote.dto.ProcessDetailSaveReq;
import com.machining.quote.service.ProcessMasterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dict")
public class ProcessMasterController {
    @Autowired
    private ProcessMasterService processMasterService;

    @GetMapping("/process-categories")
    @RequirePermission("engineer:dict:view")
    public List<ProcessCategoryDTO> listProcessCategories(
            @RequestParam(value = "enabledOnly", required = false) Boolean enabledOnly) {
        return processMasterService.listCategories(enabledOnly);
    }

    @PostMapping("/process-categories")
    @RequirePermission("engineer:dict:process-manage")
    public Map<String, Object> createProcessCategory(@RequestBody ProcessCategorySaveReq req) {
        ProcessCategoryDTO data = processMasterService.saveCategory(null, req);
        return Map.of("success", true, "message", "工艺大类已新增", "data", data);
    }

    @PutMapping("/process-categories/{id}")
    @RequirePermission("engineer:dict:process-manage")
    public Map<String, Object> updateProcessCategory(
            @PathVariable Long id,
            @RequestBody ProcessCategorySaveReq req) {
        ProcessCategoryDTO data = processMasterService.saveCategory(id, req);
        return Map.of("success", true, "message", "工艺大类已更新", "data", data);
    }

    @GetMapping("/process-details")
    @RequirePermission("engineer:dict:view")
    public List<ProcessDetailDTO> listProcessDetails(
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "enabledOnly", required = false) Boolean enabledOnly) {
        return processMasterService.listDetails(categoryId, enabledOnly);
    }

    @PostMapping("/process-details")
    @RequirePermission("engineer:dict:process-manage")
    public Map<String, Object> createProcessDetail(@RequestBody ProcessDetailSaveReq req) {
        ProcessDetailDTO data = processMasterService.saveDetail(null, req);
        return Map.of("success", true, "message", "具体工艺已新增", "data", data);
    }

    @PutMapping("/process-details/{id}")
    @RequirePermission("engineer:dict:process-manage")
    public Map<String, Object> updateProcessDetail(
            @PathVariable Long id,
            @RequestBody ProcessDetailSaveReq req) {
        ProcessDetailDTO data = processMasterService.saveDetail(id, req);
        return Map.of("success", true, "message", "具体工艺已更新", "data", data);
    }
}
