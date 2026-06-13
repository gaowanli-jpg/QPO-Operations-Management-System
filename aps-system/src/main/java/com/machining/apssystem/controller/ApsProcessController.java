package com.machining.apssystem.controller;

import com.machining.apssystem.config.RequireRole;
import com.machining.apssystem.dto.ApsProcessDTO;
import com.machining.apssystem.service.ApsProcessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequireRole({"ADMIN"})
@RequestMapping("/api/aps/processes")
public class ApsProcessController {
    @Autowired
    private ApsProcessService processService;

    @GetMapping
    public List<ApsProcessDTO> listProcesses(
            @RequestParam(value = "processType", required = false) String processType) {
        return processService.listProcesses(processType);
    }

    @PostMapping("/sync-from-erp")
    public Map<String, Object> syncFromErp(
            @RequestParam(value = "processType", required = false) String processType) {
        return processService.syncFromErp(processType);
    }
}
