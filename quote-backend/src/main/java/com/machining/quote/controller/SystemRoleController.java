package com.machining.quote.controller;

import com.machining.quote.config.RequirePermission;
import com.machining.quote.dto.PermissionTreeNodeDTO;
import com.machining.quote.dto.RoleDataScopeAssignReq;
import com.machining.quote.dto.RolePermissionAssignReq;
import com.machining.quote.dto.SystemPermissionDTO;
import com.machining.quote.dto.SystemRoleDTO;
import com.machining.quote.dto.SystemRoleSaveReq;
import com.machining.quote.service.SystemRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
public class SystemRoleController {
    @Autowired
    private SystemRoleService systemRoleService;

    @GetMapping("/roles")
    @RequirePermission("system:role:view")
    public List<SystemRoleDTO> listRoles() {
        return systemRoleService.listRoles();
    }

    @PostMapping("/roles")
    @RequirePermission("system:role:create")
    public Map<String, Object> createRole(@RequestBody SystemRoleSaveReq req) {
        return Map.of("success", true, "message", "Role created", "data", systemRoleService.saveRole(req));
    }

    @PutMapping("/roles/{id}")
    @RequirePermission("system:role:update")
    public Map<String, Object> updateRole(@PathVariable Long id, @RequestBody SystemRoleSaveReq req) {
        req.setId(id);
        return Map.of("success", true, "message", "Role updated", "data", systemRoleService.saveRole(req));
    }

    @DeleteMapping("/roles/{id}")
    @RequirePermission("system:role:delete")
    public Map<String, Object> deleteRole(@PathVariable Long id) {
        systemRoleService.deleteRole(id);
        return Map.of("success", true, "message", "Role deleted");
    }

    @GetMapping("/roles/{id}/permissions")
    @RequirePermission("system:role:view")
    public List<String> getRolePermissions(@PathVariable Long id) {
        return systemRoleService.getRolePermissionCodes(id);
    }

    @PostMapping("/roles/{id}/permissions")
    @RequirePermission("system:role:assign-permission")
    public Map<String, Object> assignRolePermissions(@PathVariable Long id, @RequestBody RolePermissionAssignReq req) {
        return Map.of("success", true, "message", "Role permissions updated", "data", systemRoleService.assignPermissions(id, req));
    }

    @GetMapping("/roles/{id}/data-scope")
    @RequirePermission("system:role:view")
    public Map<String, Object> getRoleDataScope(@PathVariable Long id) {
        return systemRoleService.getDataScope(id);
    }

    @PostMapping("/roles/{id}/data-scope")
    @RequirePermission("system:role:assign-data-scope")
    public Map<String, Object> assignRoleDataScope(@PathVariable Long id, @RequestBody RoleDataScopeAssignReq req) {
        return Map.of("success", true, "message", "Role data scope updated", "data", systemRoleService.assignDataScope(id, req));
    }

    @GetMapping("/permissions")
    @RequirePermission("system:role:view")
    public List<SystemPermissionDTO> listPermissions() {
        return systemRoleService.listPermissions();
    }

    @GetMapping("/permissions/tree")
    @RequirePermission("system:role:view")
    public List<PermissionTreeNodeDTO> listPermissionTree() {
        return systemRoleService.listPermissionTree();
    }
}
