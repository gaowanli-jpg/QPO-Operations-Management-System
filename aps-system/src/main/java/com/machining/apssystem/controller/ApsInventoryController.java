package com.machining.apssystem.controller;

import com.machining.apssystem.dto.ApsMaterialInventoryDTO;
import com.machining.apssystem.dto.ApsMaterialLockDTO;
import com.machining.apssystem.dto.ApsMaterialShortageDTO;
import com.machining.apssystem.dto.ApsPartInventoryDTO;
import com.machining.apssystem.dto.ApsPartInventoryLockDTO;
import com.machining.apssystem.dto.ApsPartInventoryPageDTO;
import com.machining.apssystem.dto.ApsPartInventoryPendingStockDTO;
import com.machining.apssystem.dto.ApsPartProductionRequirementDTO;
import com.machining.apssystem.dto.MaterialInventorySaveReq;
import com.machining.apssystem.dto.PartInventoryModifyReq;
import com.machining.apssystem.dto.PartInventorySaveReq;
import com.machining.apssystem.dto.PartInventoryStockOpReq;
import com.machining.apssystem.dto.PartInventoryTransferReq;
import com.machining.apssystem.service.ApsInventoryService;
import jakarta.validation.Valid;
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
@RequestMapping("/api/aps/inventory")
public class ApsInventoryController {
    @Autowired
    private ApsInventoryService inventoryService;

    @GetMapping("/parts")
    public List<ApsPartInventoryDTO> listPartInventory(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "limit", required = false) Integer limit) {
        return inventoryService.listPartInventory(keyword, limit);
    }

    @GetMapping("/parts/customers")
    public List<String> listPartInventoryCustomers() {
        return inventoryService.listPartInventoryCustomers();
    }

    @GetMapping("/parts/page")
    public ApsPartInventoryPageDTO listPartInventoryPage(
            @RequestParam("customerName") String customerName,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return inventoryService.listPartInventoryPage(customerName, keyword, page, pageSize);
    }

    @PostMapping("/parts")
    public ApsPartInventoryDTO savePartInventory(@Valid @RequestBody PartInventorySaveReq req) {
        return inventoryService.savePartInventory(req);
    }

    @PutMapping("/parts/{id}")
    public ApsPartInventoryDTO updatePartInventory(@PathVariable Long id, @Valid @RequestBody PartInventorySaveReq req) {
        return inventoryService.updatePartInventory(id, req);
    }

    @GetMapping("/parts/{id}/pending")
    public List<ApsPartInventoryPendingStockDTO> listPartPendingStocks(@PathVariable Long id) {
        return inventoryService.listPartPendingStocks(id);
    }

    @PostMapping("/parts/add-stock")
    public ApsPartInventoryDTO addPartStock(@Valid @RequestBody PartInventoryStockOpReq req) {
        return inventoryService.addPartStock(req);
    }

    @PostMapping("/parts/transfer-pending")
    public ApsPartInventoryDTO transferPartPendingToStock(@Valid @RequestBody PartInventoryTransferReq req) {
        return inventoryService.transferPartPendingToStock(req);
    }

    @PostMapping("/parts/modify")
    public ApsPartInventoryDTO modifyPartInventory(@Valid @RequestBody PartInventoryModifyReq req) {
        return inventoryService.modifyPartInventory(req);
    }

    @DeleteMapping("/parts/{id}")
    public Map<String, Object> deletePartInventory(@PathVariable Long id) {
        inventoryService.deletePartInventory(id);
        return Map.of("success", true);
    }

    @GetMapping("/parts/locks")
    public List<ApsPartInventoryLockDTO> listPartLocks(@RequestParam(value = "limit", required = false) Integer limit) {
        return inventoryService.listPartLocks(limit);
    }

    @GetMapping("/parts/production-requirements")
    public List<ApsPartProductionRequirementDTO> listProductionRequirements(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "limit", required = false) Integer limit) {
        return inventoryService.listProductionRequirements(status, limit);
    }

    @GetMapping("/materials")
    public List<ApsMaterialInventoryDTO> listMaterialInventory(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "limit", required = false) Integer limit) {
        return inventoryService.listMaterialInventory(keyword, limit);
    }

    @PostMapping("/materials")
    public ApsMaterialInventoryDTO saveMaterialInventory(@Valid @RequestBody MaterialInventorySaveReq req) {
        return inventoryService.saveMaterialInventory(req);
    }

    @PutMapping("/materials/{id}")
    public ApsMaterialInventoryDTO updateMaterialInventory(@PathVariable Long id, @Valid @RequestBody MaterialInventorySaveReq req) {
        return inventoryService.updateMaterialInventory(id, req);
    }

    @DeleteMapping("/materials/{id}")
    public Map<String, Object> deleteMaterialInventory(@PathVariable Long id) {
        inventoryService.deleteMaterialInventory(id);
        return Map.of("success", true);
    }

    @GetMapping("/materials/locks")
    public List<ApsMaterialLockDTO> listMaterialLocks(@RequestParam(value = "limit", required = false) Integer limit) {
        return inventoryService.listMaterialLocks(limit);
    }

    @GetMapping("/materials/shortages")
    public List<ApsMaterialShortageDTO> listMaterialShortages(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "limit", required = false) Integer limit) {
        return inventoryService.listMaterialShortages(status, limit);
    }
}
