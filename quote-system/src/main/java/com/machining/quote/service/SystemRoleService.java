package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.dto.RoleDataScopeAssignReq;
import com.machining.quote.dto.RolePermissionAssignReq;
import com.machining.quote.dto.SystemPermissionDTO;
import com.machining.quote.dto.SystemRoleDTO;
import com.machining.quote.dto.SystemRoleSaveReq;
import com.machining.quote.entity.SystemPermission;
import com.machining.quote.entity.SystemRole;
import com.machining.quote.mapper.SystemPermissionMapper;
import com.machining.quote.mapper.SystemRoleDataScopeCustomerMapper;
import com.machining.quote.mapper.SystemRoleDataScopeDeptMapper;
import com.machining.quote.mapper.SystemRoleMapper;
import com.machining.quote.mapper.SystemRolePermissionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class SystemRoleService {
    private static final Set<String> DATA_SCOPES = Set.of("ALL", "DEPT", "DEPT_AND_CHILD", "SELF", "CUSTOMER", "CUSTOM", "NONE");

    @Autowired
    private SystemRoleMapper roleMapper;
    @Autowired
    private SystemPermissionMapper permissionMapper;
    @Autowired
    private SystemRolePermissionMapper rolePermissionMapper;
    @Autowired
    private SystemRoleDataScopeDeptMapper dataScopeDeptMapper;
    @Autowired
    private SystemRoleDataScopeCustomerMapper dataScopeCustomerMapper;
    @Autowired
    private AuditService auditService;

    public List<SystemRoleDTO> listRoles() {
        QueryWrapper<SystemRole> query = new QueryWrapper<>();
        query.eq("deleted", false).orderByAsc("sort_order").orderByAsc("id");
        return roleMapper.selectList(query).stream().map(this::toDto).toList();
    }

    public List<SystemPermissionDTO> listPermissions() {
        QueryWrapper<SystemPermission> query = new QueryWrapper<>();
        query.and(wrapper -> wrapper.eq("assignable", true).or().isNull("assignable"));
        query.orderByAsc("sort_order").orderByAsc("id");
        return permissionMapper.selectList(query).stream().map(this::toPermissionDto).toList();
    }

    @Transactional
    public SystemRoleDTO saveRole(SystemRoleSaveReq req) {
        String roleCode = normalizeCode(req.getRoleCode());
        if (roleCode.isEmpty()) {
            throw new RuntimeException("Role code is required");
        }
        if (isBlank(req.getRoleName())) {
            throw new RuntimeException("Role name is required");
        }
        String dataScope = normalizeDataScope(req.getDataScope());
        boolean creating = req.getId() == null;
        SystemRole role = creating ? new SystemRole() : requireRole(req.getId());
        Map<String, Object> before = creating ? null : roleSnapshot(role);

        role.setRoleCode(roleCode);
        role.setRoleName(req.getRoleName().trim());
        role.setDescription(trimToNull(req.getDescription()));
        role.setSuperAdmin(Boolean.TRUE.equals(req.getSuperAdmin()));
        role.setDataScope(dataScope);
        role.setEnabled(req.getEnabled() == null || req.getEnabled());
        role.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
        role.setUpdatedAt(LocalDateTime.now());
        role.setDeleted(false);

        if (creating) {
            role.setCreatedAt(LocalDateTime.now());
            roleMapper.insert(role);
            auditService.record("SYSTEM_ROLE_CREATE", "system_role", role.getId(), null, roleSnapshot(role));
        } else {
            roleMapper.updateById(role);
            auditService.record("SYSTEM_ROLE_UPDATE", "system_role", role.getId(), before, roleSnapshot(role));
        }
        return toDto(role);
    }

    @Transactional
    public void deleteRole(Long id) {
        SystemRole role = requireRole(id);
        if (Boolean.TRUE.equals(role.getSuperAdmin())) {
            throw new RuntimeException("Super admin role cannot be deleted");
        }
        Map<String, Object> before = roleSnapshot(role);
        role.setDeleted(true);
        role.setEnabled(false);
        role.setUpdatedAt(LocalDateTime.now());
        roleMapper.updateById(role);
        auditService.record("SYSTEM_ROLE_DELETE", "system_role", id, before, null);
    }

    public List<String> getRolePermissionCodes(Long roleId) {
        requireRole(roleId);
        List<String> codes = rolePermissionMapper.selectPermissionCodesByRoleId(roleId);
        return codes == null ? List.of() : codes;
    }

    @Transactional
    public List<String> assignPermissions(Long roleId, RolePermissionAssignReq req) {
        SystemRole role = requireRole(roleId);
        List<String> beforeCodes = getRolePermissionCodes(roleId);
        List<String> requestedCodes = normalizeCodes(req == null ? List.of() : req.getPermissionCodes());
        List<SystemPermission> requestedPermissions = requestedCodes.isEmpty()
                ? List.of()
                : permissionMapper.selectByPermissionCodes(requestedCodes);
        if (requestedPermissions.size() != requestedCodes.size()) {
            throw new RuntimeException("Some permission codes do not exist");
        }
        Set<String> assignableCodes = new LinkedHashSet<>();
        for (SystemPermission permission : requestedPermissions) {
            if (isAssignable(permission)) {
                assignableCodes.add(permission.getPermissionCode());
            }
        }

        List<SystemPermission> beforePermissions = beforeCodes.isEmpty()
                ? List.of()
                : permissionMapper.selectByPermissionCodes(beforeCodes);
        for (SystemPermission permission : beforePermissions) {
            if (!isAssignable(permission)) {
                assignableCodes.add(permission.getPermissionCode());
            }
        }

        List<String> nextCodes = new ArrayList<>(assignableCodes);
        List<SystemPermission> permissions = nextCodes.isEmpty()
                ? List.of()
                : permissionMapper.selectByPermissionCodes(nextCodes);

        rolePermissionMapper.deleteByRoleId(roleId);
        for (SystemPermission permission : permissions) {
            rolePermissionMapper.insertIgnore(roleId, permission.getId());
        }

        Map<String, Object> before = new LinkedHashMap<>();
        before.put("roleCode", role.getRoleCode());
        before.put("permissionCodes", beforeCodes);
        before.put("permissionCount", beforeCodes.size());
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("roleCode", role.getRoleCode());
        after.put("permissionCodes", nextCodes);
        after.put("permissionCount", nextCodes.size());
        auditService.record("SYSTEM_ROLE_ASSIGN_PERMISSION", "system_role", roleId, before, after);
        return nextCodes;
    }

    public Map<String, Object> getDataScope(Long roleId) {
        SystemRole role = requireRole(roleId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("roleId", roleId);
        result.put("dataScope", role.getDataScope());
        result.put("deptIds", safeList(dataScopeDeptMapper.selectDeptIdsByRoleId(roleId)));
        result.put("customerNames", safeList(dataScopeCustomerMapper.selectCustomerNamesByRoleId(roleId)));
        return result;
    }

    @Transactional
    public Map<String, Object> assignDataScope(Long roleId, RoleDataScopeAssignReq req) {
        SystemRole role = requireRole(roleId);
        Map<String, Object> before = getDataScope(roleId);
        String dataScope = normalizeDataScope(req == null ? null : req.getDataScope());
        List<Long> deptIds = req == null || req.getDeptIds() == null ? List.of() : distinctLongs(req.getDeptIds());
        List<String> customerNames = req == null || req.getCustomerNames() == null
                ? List.of()
                : normalizeCodes(req.getCustomerNames());

        role.setDataScope(dataScope);
        role.setUpdatedAt(LocalDateTime.now());
        roleMapper.updateById(role);

        dataScopeDeptMapper.deleteByRoleId(roleId);
        for (Long deptId : deptIds) {
            dataScopeDeptMapper.insertIgnore(roleId, deptId);
        }
        dataScopeCustomerMapper.deleteByRoleId(roleId);
        for (String customerName : customerNames) {
            dataScopeCustomerMapper.insertIgnore(roleId, customerName);
        }

        Map<String, Object> after = getDataScope(roleId);
        auditService.record("SYSTEM_ROLE_ASSIGN_DATA_SCOPE", "system_role", roleId, before, after);
        return after;
    }

    private SystemRole requireRole(Long id) {
        if (id == null) {
            throw new RuntimeException("Role id is required");
        }
        SystemRole role = roleMapper.selectById(id);
        if (role == null || Boolean.TRUE.equals(role.getDeleted())) {
            throw new RuntimeException("Role does not exist");
        }
        return role;
    }

    private SystemRoleDTO toDto(SystemRole role) {
        SystemRoleDTO dto = new SystemRoleDTO();
        dto.setId(role.getId());
        dto.setRoleCode(role.getRoleCode());
        dto.setRoleName(role.getRoleName());
        dto.setDescription(role.getDescription());
        dto.setSuperAdmin(role.getSuperAdmin());
        dto.setDataScope(role.getDataScope());
        dto.setEnabled(role.getEnabled());
        dto.setSortOrder(role.getSortOrder());
        return dto;
    }

    private SystemPermissionDTO toPermissionDto(SystemPermission permission) {
        SystemPermissionDTO dto = new SystemPermissionDTO();
        dto.setId(permission.getId());
        dto.setParentId(permission.getParentId());
        dto.setPermissionType(permission.getPermissionType());
        dto.setPermissionCode(permission.getPermissionCode());
        dto.setPermissionName(permission.getPermissionName());
        dto.setRoutePath(permission.getRoutePath());
        dto.setComponentKey(permission.getComponentKey());
        dto.setIcon(permission.getIcon());
        dto.setSortOrder(permission.getSortOrder());
        dto.setVisible(permission.getVisible());
        dto.setEnabled(permission.getEnabled());
        dto.setAssignable(permission.getAssignable());
        return dto;
    }

    private Map<String, Object> roleSnapshot(SystemRole role) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", role.getId());
        row.put("roleCode", role.getRoleCode());
        row.put("roleName", role.getRoleName());
        row.put("description", role.getDescription());
        row.put("superAdmin", role.getSuperAdmin());
        row.put("dataScope", role.getDataScope());
        row.put("enabled", role.getEnabled());
        row.put("sortOrder", role.getSortOrder());
        return row;
    }

    private String normalizeCode(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeDataScope(String value) {
        String dataScope = normalizeCode(value);
        if (dataScope.isEmpty()) {
            return "ALL";
        }
        if (!DATA_SCOPES.contains(dataScope)) {
            throw new RuntimeException("Invalid data scope");
        }
        return dataScope;
    }

    private List<String> normalizeCodes(List<String> values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : values == null ? List.<String>of() : values) {
            String normalized = value == null ? "" : value.trim();
            if (!normalized.isEmpty()) {
                result.add(normalized);
            }
        }
        return new ArrayList<>(result);
    }

    private List<Long> distinctLongs(List<Long> values) {
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        for (Long value : values) {
            if (value != null) {
                result.add(value);
            }
        }
        return new ArrayList<>(result);
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean isAssignable(SystemPermission permission) {
        return permission == null || permission.getAssignable() == null || Boolean.TRUE.equals(permission.getAssignable());
    }
}
