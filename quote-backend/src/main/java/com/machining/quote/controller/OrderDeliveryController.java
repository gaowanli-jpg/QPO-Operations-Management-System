package com.machining.quote.controller;

import com.machining.quote.config.RequirePermission;
import com.machining.quote.dto.CustomerOptionDTO;
import com.machining.quote.dto.DeliveredItemDTO;
import com.machining.quote.dto.DeliveredItemQueryReq;
import com.machining.quote.dto.DeliveryTicketBatchReq;
import com.machining.quote.dto.LockedItemDTO;
import com.machining.quote.dto.LockedItemMesProductionBatchReq;
import com.machining.quote.dto.LockedItemPageDTO;
import com.machining.quote.dto.LockedItemQueryReq;
import com.machining.quote.dto.PageResult;
import com.machining.quote.entity.SystemTask;
import com.machining.quote.service.DeliveredItemService;
import com.machining.quote.service.LockedItemService;
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

@RestController
@RequestMapping("/api/order-delivery")
public class OrderDeliveryController {
    @Autowired
    private DeliveredItemService deliveredItemService;
    @Autowired
    private LockedItemService lockedItemService;
    @Autowired
    private SystemTaskService systemTaskService;

    @GetMapping("/tickets")
    @RequirePermission("market:delivered-item:view")
    public PageResult<DeliveredItemDTO> listTickets(DeliveredItemQueryReq req) {
        return deliveredItemService.listTickets(req);
    }

    @GetMapping("/tickets/customers")
    @RequirePermission("market:delivered-item:customer-select")
    public List<CustomerOptionDTO> listTicketCustomers(
            @RequestParam(value = "field", required = false) String field,
            @RequestParam(value = "keyword", required = false) String keyword) {
        return deliveredItemService.listCustomerOptions(field, keyword);
    }

    @GetMapping("/tickets/{ticketId}")
    @RequirePermission("market:delivered-item:view")
    public DeliveredItemDTO getTicket(@PathVariable Integer ticketId) {
        return deliveredItemService.getTicket(ticketId);
    }

    @PostMapping("/tickets/{ticketId}/mes-material")
    @RequirePermission("market:delivered-item:mes-sync")
    public SystemTask syncMesMaterial(@PathVariable Integer ticketId) {
        return systemTaskService.submitMesMaterialSyncTask(ticketId);
    }

    @PostMapping("/tickets/mes-material/batch")
    @RequirePermission("market:delivered-item:mes-sync")
    public SystemTask syncMesMaterialBatch(@RequestBody DeliveryTicketBatchReq req) {
        return systemTaskService.submitMesMaterialBatchSyncTask(req == null ? null : req.getTicketIds());
    }

    @PostMapping("/tickets/{ticketId}/wms-allocation")
    @RequirePermission("market:delivered-item:wms-allocate")
    public SystemTask submitWmsAllocation(@PathVariable Integer ticketId) {
        return systemTaskService.submitWmsAllocationTask(ticketId);
    }

    @PostMapping("/tickets/wms-allocation/batch")
    @RequirePermission("market:delivered-item:wms-allocate")
    public SystemTask submitWmsAllocationBatch(@RequestBody DeliveryTicketBatchReq req) {
        return systemTaskService.submitWmsAllocationBatchTask(req == null ? null : req.getTicketIds());
    }

    @GetMapping("/allocations")
    @RequirePermission("market:locked-item:view")
    public LockedItemPageDTO listAllocations(@ModelAttribute LockedItemQueryReq req) {
        return lockedItemService.listAllocations(req);
    }

    @GetMapping("/allocations/customers")
    @RequirePermission("market:locked-item:customer-select")
    public List<CustomerOptionDTO> listAllocationCustomers(
            @RequestParam(value = "field", required = false) String field,
            @RequestParam(value = "keyword", required = false) String keyword) {
        return lockedItemService.listCustomerOptions(field, keyword);
    }

    @GetMapping("/tickets/{ticketId}/allocations")
    @RequirePermission("market:locked-item:view")
    public List<LockedItemDTO> listTicketAllocations(@PathVariable Integer ticketId) {
        return lockedItemService.listTicketAllocations(ticketId);
    }

    @PostMapping("/allocations/mes-production")
    @RequirePermission("market:locked-item:mes-production-submit")
    public SystemTask submitMesProduction(@RequestBody LockedItemMesProductionBatchReq req) {
        return systemTaskService.submitMesProductionBatchTask(req == null ? null : req.getAllocationIds());
    }
}
