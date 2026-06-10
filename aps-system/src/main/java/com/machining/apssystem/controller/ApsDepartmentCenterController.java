package com.machining.apssystem.controller;

import com.machining.apssystem.dto.ApsPmcTaskDetailDTO;
import com.machining.apssystem.dto.ApsWorkItemPageDTO;
import com.machining.apssystem.service.ApsDepartmentCenterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/aps/department-center")
public class ApsDepartmentCenterController {
    @Autowired
    private ApsDepartmentCenterService departmentCenterService;

    @GetMapping("/tasks/page")
    public ApsWorkItemPageDTO listDepartmentTaskPage(
            @RequestParam(value = "departmentId", required = false) Long departmentId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return departmentCenterService.listDepartmentTaskPage(departmentId, status, page, pageSize);
    }

    @GetMapping("/tasks/{workItemId}")
    public ApsPmcTaskDetailDTO getDepartmentTaskDetail(@PathVariable Long workItemId) {
        return departmentCenterService.getDepartmentTaskDetail(workItemId);
    }
}
