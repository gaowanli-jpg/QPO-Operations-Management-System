package com.machining.quote.controller;

import com.machining.quote.config.RequirePermission;
import com.machining.quote.dto.DeliveredItemDTO;
import com.machining.quote.dto.DeliveredItemQueryReq;
import com.machining.quote.dto.PageResult;
import com.machining.quote.service.DeliveredItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/order-delivery")
public class OrderDeliveryController {
    @Autowired
    private DeliveredItemService deliveredItemService;

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
}
