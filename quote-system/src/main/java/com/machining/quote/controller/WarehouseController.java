package com.machining.quote.controller;

import com.machining.quote.dto.InventoryModifyReq;
import com.machining.quote.dto.InventorySaveReq;
import com.machining.quote.dto.InventoryStockOpReq;
import com.machining.quote.dto.InventoryTransferReq;
import com.machining.quote.dto.PageResult;
import com.machining.quote.config.RequireRole;
import com.machining.quote.entity.InventoryMaster;
import com.machining.quote.entity.InventoryPendingStock;
import com.machining.quote.service.InventoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequireRole({"WAREHOUSE", "ADMIN"})
@RequestMapping("/api/warehouse")
public class WarehouseController {
    @Autowired
    private InventoryService inventoryService;

    @GetMapping("/customers")
    public List<String> listCustomers() {
        return inventoryService.listCustomers();
    }

    @GetMapping("/inventory")
    public PageResult<Map<String, Object>> listInventory(
            @RequestParam("customerName") String customerName,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return inventoryService.list(customerName, keyword, page, pageSize);
    }

    @GetMapping("/inventory/{inventoryId}/pending")
    public List<InventoryPendingStock> listPending(@PathVariable Integer inventoryId) {
        return inventoryService.listPending(inventoryId);
    }

    @PostMapping("/inventory")
    public Map<String, Object> saveInventory(@RequestBody InventorySaveReq req) {
        InventoryMaster saved = inventoryService.saveOrUpdate(req);
        return success("库存零件已录入", saved);
    }

    @PostMapping("/inventory/add-stock")
    public Map<String, Object> addStock(@RequestBody InventoryStockOpReq req) {
        InventoryMaster saved = inventoryService.addStock(req);
        return success("库存已新增", saved);
    }

    @PostMapping("/inventory/transfer-pending")
    public Map<String, Object> transferPendingToStock(@RequestBody InventoryTransferReq req) {
        InventoryMaster saved = inventoryService.transferPendingToStock(req);
        return success("在产库存已转入已入库库存", saved);
    }

    @PostMapping("/inventory/modify")
    public Map<String, Object> modifyInventory(@RequestBody InventoryModifyReq req) {
        InventoryMaster saved = inventoryService.modify(req);
        return success("库存已修改", saved);
    }

    @DeleteMapping("/inventory/{inventoryId}")
    public Map<String, Object> deleteInventory(@PathVariable Integer inventoryId) {
        inventoryService.deleteInventory(inventoryId);
        return success("库存记录已彻底删除", null);
    }

    private Map<String, Object> success(String message, Object data) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", data);
        result.put("message", message);
        return result;
    }
}
