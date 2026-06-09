package com.machining.quote.controller;

import com.machining.quote.config.RequireRole;
import com.machining.quote.dto.aps.ApsDeliveryItemBatchDTO;
import com.machining.quote.dto.aps.ApsProcessDTO;
import com.machining.quote.service.ApsSourceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/aps/source")
@RequireRole({"ADMIN", "MARKET", "ENGINEER"})
public class ApsSourceController {
    @Autowired
    private ApsSourceService apsSourceService;

    @GetMapping("/processes")
    public List<ApsProcessDTO> listProcesses(
            @RequestParam(value = "processType", required = false) String processType) {
        return apsSourceService.listProcesses(processType);
    }

    @GetMapping("/delivery-items")
    public ApsDeliveryItemBatchDTO listDeliveryItems(
            @RequestParam(value = "afterTicketId", required = false) Integer afterTicketId,
            @RequestParam(value = "limit", required = false) Integer limit) {
        return apsSourceService.listDeliveryItems(afterTicketId, limit);
    }
}
