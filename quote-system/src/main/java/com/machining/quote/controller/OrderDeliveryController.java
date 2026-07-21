package com.machining.quote.controller;

import com.machining.quote.config.RequirePermission;
import com.machining.quote.dto.DeliveredItemDTO;
import com.machining.quote.dto.DeliveredItemQueryReq;
import com.machining.quote.dto.PageResult;
import com.machining.quote.entity.SystemTask;
import com.machining.quote.service.DeliveredItemService;
import com.machining.quote.service.SystemTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/order-delivery")
public class OrderDeliveryController {
    @Autowired
    private DeliveredItemService deliveredItemService;
    @Autowired
    private SystemTaskService systemTaskService;

    @GetMapping("/tickets")
    @RequirePermission("market:delivered-item:view")
    public PageResult<DeliveredItemDTO> listTickets(DeliveredItemQueryReq req) {
        return deliveredItemService.listTickets(req);
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

    @PostMapping("/tickets/{ticketId}/wms-allocation")
    @RequirePermission("market:delivered-item:wms-allocate")
    public SystemTask submitWmsAllocation(@PathVariable Integer ticketId) {
        return systemTaskService.submitWmsAllocationTask(ticketId);
    }
}
