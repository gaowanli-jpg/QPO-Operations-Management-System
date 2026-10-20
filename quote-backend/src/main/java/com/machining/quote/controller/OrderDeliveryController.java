package com.machining.quote.controller;

import com.machining.quote.config.RequirePermission;
import com.machining.quote.dto.CustomerOptionDTO;
import com.machining.quote.dto.DeliveredItemDTO;
import com.machining.quote.dto.DeliveredItemQueryReq;
import com.machining.quote.dto.DeliveryItemBatchDiscardReq;
import com.machining.quote.dto.DeliveryItemBatchRestoreReq;
import com.machining.quote.dto.DeliveryItemDiscardReq;
import com.machining.quote.dto.DeliveryItemRestoreReq;
import com.machining.quote.dto.DeliveryTicketBatchReq;
import com.machining.quote.dto.LockedItemDTO;
import com.machining.quote.dto.LockedItemMesProductionBatchReq;
import com.machining.quote.dto.LockedItemPageDTO;
import com.machining.quote.dto.LockedItemQueryReq;
import com.machining.quote.dto.LockedItemStockPickingBatchReq;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.ProductionItemQueryReq;
import com.machining.quote.entity.SystemTask;
import com.machining.quote.service.DeliveredItemService;
import com.machining.quote.service.LockedItemService;
import com.machining.quote.service.ProductionItemService;
import com.machining.quote.service.SystemTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/order-delivery")
public class OrderDeliveryController {
    @Autowired
    private DeliveredItemService deliveredItemService;
    @Autowired
    private LockedItemService lockedItemService;
    @Autowired
    private ProductionItemService productionItemService;
    @Autowired
    private SystemTaskService systemTaskService;

    @GetMapping("/tickets")
    @RequirePermission("market:delivered-item:view")
    public PageResult<DeliveredItemDTO> listTickets(DeliveredItemQueryReq req) {
        return deliveredItemService.listTickets(req);
    }

    @GetMapping("/tickets/customers")
    @RequirePermission("market:delivered-item:customer-select")
    public PageResult<CustomerOptionDTO> listTicketCustomers(
            @RequestParam(value = "field", required = false) String field,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return deliveredItemService.pageCustomerOptions(field, keyword, page, pageSize);
    }

    @GetMapping("/tickets/{ticketId}")
    @RequirePermission("market:delivered-item:view")
    public DeliveredItemDTO getTicket(@PathVariable Integer ticketId) {
        return deliveredItemService.getTicket(ticketId);
    }

    @PostMapping("/tickets/{ticketId}/discard")
    @RequirePermission("market:delivered-item:discard")
    public Map<String, Object> discardTicket(
            @PathVariable Integer ticketId,
            @RequestBody(required = false) DeliveryItemDiscardReq req) {
        if (req == null) {
            req = new DeliveryItemDiscardReq();
        }
        return deliveredItemService.discardTicket(ticketId, req);
    }

    @PostMapping("/tickets/{ticketId}/restore")
    @RequirePermission("market:delivered-item:restore")
    public Map<String, Object> restoreTicket(
            @PathVariable Integer ticketId,
            @RequestBody(required = false) DeliveryItemRestoreReq req) {
        if (req == null) {
            req = new DeliveryItemRestoreReq();
        }
        return deliveredItemService.restoreTicket(ticketId, req);
    }

    @PostMapping("/tickets/batch-discard")
    @RequirePermission("market:delivered-item:discard")
    public Map<String, Object> batchDiscardTickets(@RequestBody(required = false) DeliveryItemBatchDiscardReq req) {
        if (req == null) {
            req = new DeliveryItemBatchDiscardReq();
        }
        return deliveredItemService.batchDiscardTickets(req);
    }

    @PostMapping("/tickets/batch-restore")
    @RequirePermission("market:delivered-item:restore")
    public Map<String, Object> batchRestoreTickets(@RequestBody(required = false) DeliveryItemBatchRestoreReq req) {
        if (req == null) {
            req = new DeliveryItemBatchRestoreReq();
        }
        return deliveredItemService.batchRestoreTickets(req);
    }

    @PostMapping("/tickets/mes-material/batch")
    @RequirePermission("market:delivered-item:mes-sync")
    public SystemTask syncMesMaterialBatch(@RequestBody DeliveryTicketBatchReq req) {
        return systemTaskService.submitMesMaterialBatchSyncTask(req == null ? null : req.getTicketIds());
    }

    @PostMapping("/tickets/wms-allocation/batch")
    @RequirePermission("market:delivered-item:wms-allocate")
    public SystemTask submitWmsAllocationBatch(@RequestBody DeliveryTicketBatchReq req) {
        return systemTaskService.submitWmsAllocationBatchTask(req == null ? null : req.getTicketIds());
    }


    @GetMapping("/locked-items")
    @RequirePermission("market:locked-item:view")
    public LockedItemPageDTO listLockedItems(@ModelAttribute LockedItemQueryReq req) {
        return lockedItemService.listAllocations(req);
    }

    @GetMapping("/locked-items/customers")
    @RequirePermission("market:locked-item:customer-select")
    public PageResult<CustomerOptionDTO> listLockedItemCustomers(
            @RequestParam(value = "field", required = false) String field,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return lockedItemService.pageCustomerOptions(field, keyword, page, pageSize);
    }

    @PostMapping("/locked-items/{allocationId}/discard")
    @RequirePermission("market:locked-item:discard")
    public Map<String, Object> discardLockedItem(
            @PathVariable Integer allocationId,
            @RequestBody(required = false) DeliveryItemDiscardReq req) {
        if (req == null) {
            req = new DeliveryItemDiscardReq();
        }
        return lockedItemService.discardAllocation(allocationId, req);
    }

    @PostMapping("/locked-items/{allocationId}/restore")
    @RequirePermission("market:locked-item:restore")
    public Map<String, Object> restoreLockedItem(
            @PathVariable Integer allocationId,
            @RequestBody(required = false) DeliveryItemRestoreReq req) {
        if (req == null) {
            req = new DeliveryItemRestoreReq();
        }
        return lockedItemService.restoreAllocation(allocationId, req);
    }

    @PostMapping("/locked-items/batch-discard")
    @RequirePermission("market:locked-item:discard")
    public Map<String, Object> batchDiscardLockedItems(@RequestBody(required = false) DeliveryItemBatchDiscardReq req) {
        if (req == null) {
            req = new DeliveryItemBatchDiscardReq();
        }
        return lockedItemService.batchDiscardAllocations(req);
    }

    @PostMapping("/locked-items/batch-restore")
    @RequirePermission("market:locked-item:restore")
    public Map<String, Object> batchRestoreLockedItems(@RequestBody(required = false) DeliveryItemBatchRestoreReq req) {
        if (req == null) {
            req = new DeliveryItemBatchRestoreReq();
        }
        return lockedItemService.batchRestoreAllocations(req);
    }

    @PostMapping("/locked-items/stock-picking-orders")
    @RequirePermission("market:locked-item:stock-picking-submit")
    public SystemTask submitStockPickingOrders(@RequestBody LockedItemStockPickingBatchReq req) {
        return systemTaskService.submitLockedStockPickingOrderBatchTask(req == null ? null : req.getAllocationIds());
    }

    @GetMapping("/production-items")
    @RequirePermission("market:production-item:view")
    public LockedItemPageDTO listProductionItems(@ModelAttribute ProductionItemQueryReq req) {
        return productionItemService.listProductionItems(req);
    }

    @PostMapping("/production-items/{allocationId}/discard")
    @RequirePermission("market:production-item:discard")
    public Map<String, Object> discardProductionItem(
            @PathVariable Integer allocationId,
            @RequestBody(required = false) DeliveryItemDiscardReq req) {
        if (req == null) {
            req = new DeliveryItemDiscardReq();
        }
        return productionItemService.discardAllocation(allocationId, req);
    }

    @PostMapping("/production-items/{allocationId}/restore")
    @RequirePermission("market:production-item:restore")
    public Map<String, Object> restoreProductionItem(
            @PathVariable Integer allocationId,
            @RequestBody(required = false) DeliveryItemRestoreReq req) {
        if (req == null) {
            req = new DeliveryItemRestoreReq();
        }
        return productionItemService.restoreAllocation(allocationId, req);
    }

    @PostMapping("/production-items/batch-discard")
    @RequirePermission("market:production-item:discard")
    public Map<String, Object> batchDiscardProductionItems(@RequestBody(required = false) DeliveryItemBatchDiscardReq req) {
        if (req == null) {
            req = new DeliveryItemBatchDiscardReq();
        }
        return productionItemService.batchDiscardAllocations(req);
    }

    @PostMapping("/production-items/batch-restore")
    @RequirePermission("market:production-item:restore")
    public Map<String, Object> batchRestoreProductionItems(@RequestBody(required = false) DeliveryItemBatchRestoreReq req) {
        if (req == null) {
            req = new DeliveryItemBatchRestoreReq();
        }
        return productionItemService.batchRestoreAllocations(req);
    }

    @GetMapping("/production-items/customers")
    @RequirePermission("market:production-item:customer-select")
    public PageResult<CustomerOptionDTO> listProductionItemCustomers(
            @RequestParam(value = "field", required = false) String field,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return productionItemService.pageCustomerOptions(field, keyword, page, pageSize);
    }

    @GetMapping("/tickets/{ticketId}/allocations")
    @RequirePermission("market:locked-item:view")
    public List<LockedItemDTO> listTicketAllocations(@PathVariable Integer ticketId) {
        return lockedItemService.listTicketAllocations(ticketId);
    }

    @PostMapping("/production-items/mes-production")
    @RequirePermission("market:production-item:mes-production-submit")
    public SystemTask submitMesProduction(@RequestBody LockedItemMesProductionBatchReq req) {
        return systemTaskService.submitMesProductionBatchTask(req == null ? null : req.getAllocationIds());
    }
}
