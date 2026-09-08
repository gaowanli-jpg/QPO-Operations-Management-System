package com.machining.quote.controller;

import com.machining.quote.config.RequirePermission;
import com.machining.quote.service.BusinessFileService;
import com.machining.quote.service.DrawingFolderAccessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/business-files")
public class BusinessFileController {
    @Autowired
    private DrawingFolderAccessService drawingFolderAccessService;

    @GetMapping("/drawing-folder")
    @RequirePermission({
            "market:rfq:file-preview",
            "market:release:view",
            "market:order:file-preview",
            "market:sales-order:file-preview",
            "market:delivered-item:file-preview",
            "market:part-search:view",
            "engineer:setup:file-preview",
            "engineer:pricing:view"
    })
    public Map<String, Object> listDrawingFolderFiles(
            @RequestParam("folderUrl") String folderUrl,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize) {
        BusinessFileService.DrawingFolderFilePage filePage = drawingFolderAccessService.pageFiles(
                folderUrl,
                keyword,
                page == null ? 1 : page,
                pageSize == null ? 20 : pageSize);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("records", filePage.records());
        result.put("files", filePage.records());
        result.put("total", filePage.total());
        result.put("page", filePage.page());
        result.put("pageSize", filePage.pageSize());
        return result;
    }
}
