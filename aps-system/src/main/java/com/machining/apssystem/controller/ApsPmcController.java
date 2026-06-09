package com.machining.apssystem.controller;

import com.machining.apssystem.dto.ApsDepartmentDTO;
import com.machining.apssystem.dto.ApsMachineCapabilityDTO;
import com.machining.apssystem.dto.ApsMachineDTO;
import com.machining.apssystem.dto.ApsPmcTaskDetailDTO;
import com.machining.apssystem.dto.ApsWorkItemDTO;
import com.machining.apssystem.dto.ApsWorkItemPageDTO;
import com.machining.apssystem.dto.DepartmentAssignmentReq;
import com.machining.apssystem.dto.DepartmentCapacityAdjustReq;
import com.machining.apssystem.dto.DepartmentCapacityResetReq;
import com.machining.apssystem.dto.DepartmentSaveReq;
import com.machining.apssystem.dto.MachineCapabilitySaveReq;
import com.machining.apssystem.dto.MachineSaveReq;
import com.machining.apssystem.dto.WorkItemBulkDeleteReq;
import com.machining.apssystem.service.ApsDepartmentService;
import com.machining.apssystem.service.ApsMachineCapabilityService;
import com.machining.apssystem.service.ApsPmcService;
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
@RequestMapping("/api/aps/pmc")
public class ApsPmcController {
    @Autowired
    private ApsPmcService pmcService;

    @Autowired
    private ApsDepartmentService departmentService;

    @Autowired
    private ApsMachineCapabilityService machineCapabilityService;

    @GetMapping("/departments")
    public List<ApsDepartmentDTO> listDepartments(
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "status", required = false) String status) {
        return departmentService.listDepartments(type, status);
    }

    @PostMapping("/departments")
    public ApsDepartmentDTO saveDepartment(@Valid @RequestBody DepartmentSaveReq req) {
        return departmentService.saveDepartment(req);
    }

    @PutMapping("/departments/{id}")
    public ApsDepartmentDTO updateDepartment(@PathVariable Long id, @Valid @RequestBody DepartmentSaveReq req) {
        return departmentService.updateDepartment(id, req);
    }

    @PostMapping("/departments/{id}/adjust-capacity")
    public ApsDepartmentDTO adjustDepartmentCapacity(@PathVariable Long id, @Valid @RequestBody DepartmentCapacityAdjustReq req) {
        return departmentService.adjustDepartmentCapacity(id, req);
    }

    @PostMapping("/departments/{id}/reset-capacity")
    public ApsDepartmentDTO resetDepartmentCapacity(@PathVariable Long id, @Valid @RequestBody DepartmentCapacityResetReq req) {
        return departmentService.resetDepartmentCapacity(id, req);
    }

    @DeleteMapping("/departments/{id}")
    public Map<String, Object> deleteDepartment(@PathVariable Long id) {
        departmentService.deleteDepartment(id);
        return Map.of("success", true);
    }

    @GetMapping("/machines")
    public List<ApsMachineDTO> listMachines(@RequestParam(value = "departmentId", required = false) Long departmentId) {
        return machineCapabilityService.listMachines(departmentId);
    }

    @PostMapping("/machines")
    public ApsMachineDTO saveMachine(@Valid @RequestBody MachineSaveReq req) {
        return machineCapabilityService.saveMachine(req);
    }

    @PutMapping("/machines/{id}")
    public ApsMachineDTO updateMachine(@PathVariable Long id, @Valid @RequestBody MachineSaveReq req) {
        return machineCapabilityService.updateMachine(id, req);
    }

    @DeleteMapping("/machines/{id}")
    public Map<String, Object> deleteMachine(@PathVariable Long id) {
        machineCapabilityService.deleteMachine(id);
        return Map.of("success", true);
    }

    @GetMapping("/machine-capabilities")
    public List<ApsMachineCapabilityDTO> listMachineCapabilities(
            @RequestParam(value = "machineId", required = false) Long machineId) {
        return machineCapabilityService.listMachineCapabilities(machineId);
    }

    @PostMapping("/machine-capabilities")
    public ApsMachineCapabilityDTO saveMachineCapability(@Valid @RequestBody MachineCapabilitySaveReq req) {
        return machineCapabilityService.saveMachineCapability(req);
    }

    @DeleteMapping("/machine-capabilities/{id}")
    public Map<String, Object> deleteMachineCapability(@PathVariable Long id) {
        machineCapabilityService.deleteMachineCapability(id);
        return Map.of("success", true);
    }

    @GetMapping("/tasks")
    public List<ApsWorkItemDTO> listPmcTasks(
            @RequestParam(value = "assignmentStatus", required = false) String assignmentStatus,
            @RequestParam(value = "limit", required = false) Integer limit) {
        return pmcService.listPmcTasks(assignmentStatus, limit);
    }

    @GetMapping("/tasks/page")
    public ApsWorkItemPageDTO listPmcTaskPage(
            @RequestParam(value = "assignmentStatus", required = false) String assignmentStatus,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return pmcService.listPmcTaskPage(assignmentStatus, page, pageSize);
    }

    @GetMapping("/tasks/{id}")
    public ApsPmcTaskDetailDTO getTaskDetail(@PathVariable Long id) {
        return pmcService.getTaskDetail(id);
    }

    @DeleteMapping("/tasks")
    public Map<String, Object> deleteTasks(@Valid @RequestBody WorkItemBulkDeleteReq req) {
        return pmcService.deleteWorkItems(req);
    }

    @PostMapping("/tasks/{id}/assign-department")
    public ApsPmcTaskDetailDTO assignDepartment(@PathVariable Long id, @Valid @RequestBody DepartmentAssignmentReq req) {
        return pmcService.assignDepartment(id, req);
    }

    @PostMapping("/tasks/{id}/release-assignment")
    public ApsPmcTaskDetailDTO releaseAssignment(@PathVariable Long id) {
        return pmcService.releaseAssignment(id);
    }
}
