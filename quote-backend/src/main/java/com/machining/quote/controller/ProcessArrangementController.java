package com.machining.quote.controller;

import com.machining.quote.config.RequirePermission;
import com.machining.quote.dto.ProcessArrangementDeleteSummaryDTO;
import com.machining.quote.dto.ProcessArrangementItemBatchReq;
import com.machining.quote.dto.ProcessArrangementItemMoveReq;
import com.machining.quote.dto.ProcessArrangementNodeSaveReq;
import com.machining.quote.dto.ProcessArrangementOptionDTO;
import com.machining.quote.dto.ProcessArrangementTreeNodeDTO;
import com.machining.quote.service.ProcessArrangementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/dict/process-arrangement")
public class ProcessArrangementController {
    @Autowired
    private ProcessArrangementService processArrangementService;

    @GetMapping("/tree")
    @RequirePermission("engineer:dict:view")
    public List<ProcessArrangementTreeNodeDTO> listTree() {
        return processArrangementService.listTree();
    }

    @GetMapping("/unplaced-options")
    @RequirePermission("engineer:dict:view")
    public List<ProcessArrangementOptionDTO> listUnplacedOptions(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "sourceType", required = false) String sourceType) {
        return processArrangementService.listUnplacedOptions(keyword, sourceType);
    }

    @PostMapping("/nodes")
    @RequirePermission("engineer:dict:process-manage")
    public ProcessArrangementTreeNodeDTO createNode(@RequestBody ProcessArrangementNodeSaveReq req) {
        return processArrangementService.createNode(req);
    }

    @PutMapping("/nodes/{id}")
    @RequirePermission("engineer:dict:process-manage")
    public ProcessArrangementTreeNodeDTO updateNode(
            @PathVariable Long id,
            @RequestBody ProcessArrangementNodeSaveReq req) {
        return processArrangementService.updateNode(id, req);
    }

    @DeleteMapping("/nodes/{id}")
    @RequirePermission("engineer:dict:process-manage")
    public ProcessArrangementDeleteSummaryDTO deleteNode(@PathVariable Long id) {
        return processArrangementService.deleteNode(id);
    }

    @PostMapping("/items/batch")
    @RequirePermission("engineer:dict:process-manage")
    public List<ProcessArrangementTreeNodeDTO> placeItems(@RequestBody ProcessArrangementItemBatchReq req) {
        return processArrangementService.placeItems(req);
    }

    @PutMapping("/items/{id}/move")
    @RequirePermission("engineer:dict:process-manage")
    public ProcessArrangementTreeNodeDTO moveItem(
            @PathVariable Long id,
            @RequestBody ProcessArrangementItemMoveReq req) {
        return processArrangementService.moveItem(id, req);
    }

    @DeleteMapping("/items/{id}")
    @RequirePermission("engineer:dict:process-manage")
    public Map<String, Object> removeItem(@PathVariable Long id) {
        processArrangementService.removeItem(id);
        return Map.of("success", true, "message", "工艺编排挂载已移除");
    }
}
