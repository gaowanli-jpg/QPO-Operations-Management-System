package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.dto.CustomerOptionDTO;
import com.machining.quote.dto.PermissionTreeNodeDTO;
import com.machining.quote.dto.RoleDataScopeAssignReq;
import com.machining.quote.dto.RolePermissionAssignReq;
import com.machining.quote.dto.SystemPermissionDTO;
import com.machining.quote.dto.SystemRoleDTO;
import com.machining.quote.dto.SystemRoleSaveReq;
import com.machining.quote.entity.CustomerMaster;
import com.machining.quote.entity.SystemPermission;
import com.machining.quote.entity.SystemRole;
import com.machining.quote.mapper.CustomerMasterMapper;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class SystemRoleService {
    private static final Set<String> DATA_SCOPES = Set.of("ALL", "SELF", "CUSTOMER", "NONE");
    private static final List<PermissionGroup> PERMISSION_GROUPS = List.of(
            new PermissionGroup("market", "市场", 100),
            new PermissionGroup("orders", "订单", 200),
            new PermissionGroup("engineer", "工程", 300),
            new PermissionGroup("system", "系统", 800)
    );

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
    @Autowired(required = false)
    private CustomerMasterMapper customerMasterMapper;
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

    public List<PermissionTreeNodeDTO> listPermissionTree() {
        QueryWrapper<SystemPermission> query = new QueryWrapper<>();
        query.and(wrapper -> wrapper.eq("assignable", true).or().isNull("assignable"));
        query.and(wrapper -> wrapper.eq("enabled", true).or().isNull("enabled"));
        query.orderByAsc("sort_order").orderByAsc("id");

        List<SystemPermission> permissions = safeList(permissionMapper.selectList(query)).stream()
                .filter(this::isTreePermission)
                .sorted(permissionComparator())
                .toList();

        Map<Long, PermissionTreeNodeDTO> nodesById = new LinkedHashMap<>();
        for (SystemPermission permission : permissions) {
            nodesById.put(permission.getId(), toPermissionTreeNode(permission));
        }

        Map<String, PermissionTreeNodeDTO> groupNodes = new LinkedHashMap<>();
        for (PermissionGroup group : PERMISSION_GROUPS) {
            PermissionTreeNodeDTO node = new PermissionTreeNodeDTO();
            node.setKey("group:" + group.key());
            node.setLabel(group.label());
            node.setGroupKey(group.key());
            node.setGroupName(group.label());
            node.setSortOrder(group.sortOrder());
            groupNodes.put(group.key(), node);
        }

        for (SystemPermission permission : permissions) {
            PermissionTreeNodeDTO node = nodesById.get(permission.getId());
            Long parentId = permission.getParentId();
            if (parentId != null && parentId > 0 && nodesById.containsKey(parentId)) {
                nodesById.get(parentId).getChildren().add(node);
                continue;
            }
            String groupKey = resolveGroupKey(permission.getPermissionCode());
            PermissionTreeNodeDTO groupNode = groupNodes.get(groupKey);
            if (groupNode != null) {
                groupNode.getChildren().add(node);
            }
        }

        for (PermissionTreeNodeDTO node : groupNodes.values()) {
            sortTree(node.getChildren());
        }
        return groupNodes.values().stream()
                .filter(node -> !node.getChildren().isEmpty())
                .toList();
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
        List<String> requestedCodes = filterAssignableRequestCodes(normalizeCodes(req == null ? List.of() : req.getPermissionCodes()));
        List<SystemPermission> requestedPermissions = requestedCodes.isEmpty()
                ? List.of()
                : safeList(permissionMapper.selectByPermissionCodes(requestedCodes));
        if (requestedPermissions.size() != requestedCodes.size()) {
            throw new RuntimeException("Some permission codes do not exist");
        }
        Map<String, SystemPermission> nextPermissionsByCode = new LinkedHashMap<>();
        for (SystemPermission permission : requestedPermissions) {
            if (isAssignable(permission)) {
                addPermissionWithParents(nextPermissionsByCode, permission);
            }
        }

        List<SystemPermission> beforePermissions = beforeCodes.isEmpty()
                ? List.of()
                : safeList(permissionMapper.selectByPermissionCodes(beforeCodes));
        for (SystemPermission permission : beforePermissions) {
            if (!isAssignable(permission)) {
                nextPermissionsByCode.put(permission.getPermissionCode(), permission);
            }
        }

        List<SystemPermission> permissions = nextPermissionsByCode.values().stream()
                .sorted(permissionComparator())
                .toList();
        List<String> nextCodes = permissions.stream()
                .map(SystemPermission::getPermissionCode)
                .toList();

        rolePermissionMapper.deleteByRoleId(roleId);
        for (SystemPermission permission : permissions) {
            rolePermissionMapper.insertIgnore(roleId, permission.getId());
        }

        Set<String> beforeAssignableCodes = assignablePermissionCodes(beforePermissions);
        Set<String> nextAssignableCodes = assignablePermissionCodes(permissions);
        List<String> addedCodes = difference(nextAssignableCodes, beforeAssignableCodes);
        List<String> removedCodes = difference(beforeAssignableCodes, nextAssignableCodes);
        Map<String, Object> before = permissionAssignmentSnapshot(role.getRoleCode(), beforeCodes, List.of(), List.of(), beforePermissions);
        Map<String, Object> after = permissionAssignmentSnapshot(role.getRoleCode(), nextCodes, addedCodes, removedCodes, permissions);
        auditService.record("SYSTEM_ROLE_ASSIGN_PERMISSION", "system_role", roleId, before, after);
        return nextCodes;
    }

    public Map<String, Object> getDataScope(Long roleId) {
        SystemRole role = requireRole(roleId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("roleId", roleId);
        result.put("dataScope", role.getDataScope());
        result.put("deptIds", List.of());
        List<CustomerOptionDTO> customers = safeList(dataScopeCustomerMapper.selectCustomerOptionsByRoleId(roleId));
        result.put("customers", customers);
        result.put("customerCodes", customers.stream()
                .map(CustomerOptionDTO::getCustomerCode)
                .filter(value -> !isBlank(value))
                .toList());
        result.put("customerNames", customers.stream()
                .map(CustomerOptionDTO::getCustomerName)
                .filter(value -> !isBlank(value))
                .toList());
        return result;
    }

    @Transactional
    public Map<String, Object> assignDataScope(Long roleId, RoleDataScopeAssignReq req) {
        SystemRole role = requireRole(roleId);
        Map<String, Object> before = getDataScope(roleId);
        String dataScope = normalizeDataScope(req == null ? null : req.getDataScope());
        List<String> customerCodes = requestedCustomerCodes(req);
        if (!"CUSTOMER".equals(dataScope)) {
            customerCodes = List.of();
        }

        role.setDataScope(dataScope);
        role.setUpdatedAt(LocalDateTime.now());
        roleMapper.updateById(role);

        dataScopeDeptMapper.deleteByRoleId(roleId);
        dataScopeCustomerMapper.deleteByRoleId(roleId);
        for (String customerCode : customerCodes) {
            CustomerMaster customer = resolveCustomerForScope(customerCode);
            String snapshot = customer == null ? customerCode : firstNonBlank(customer.getCustomerName(), customer.getCustomerShortName(), customerCode);
            dataScopeCustomerMapper.insertIgnore(roleId, customerCode, snapshot);
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

    private PermissionTreeNodeDTO toPermissionTreeNode(SystemPermission permission) {
        PermissionTreeNodeDTO dto = new PermissionTreeNodeDTO();
        dto.setKey(permission.getPermissionCode());
        dto.setLabel(permission.getPermissionName());
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
        dto.setDisabled(Boolean.FALSE);
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

    private List<String> requestedCustomerCodes(RoleDataScopeAssignReq req) {
        if (req == null) {
            return List.of();
        }
        if (req.getCustomerCodes() != null && !req.getCustomerCodes().isEmpty()) {
            return normalizeCodes(req.getCustomerCodes());
        }
        return normalizeCodes(req.getCustomerNames());
    }

    private CustomerMaster resolveCustomerForScope(String customerCode) {
        if (isBlank(customerCode)) {
            throw new RuntimeException("Customer code is required");
        }
        if (customerMasterMapper == null) {
            return null;
        }
        CustomerMaster customer = customerMasterMapper.selectEnabledByCode(customerCode);
        if (customer == null) {
            throw new RuntimeException("Customer code does not exist or is disabled");
        }
        return customer;
    }

    private String firstNonBlank(String... values) {
        for (String value : values == null ? new String[0] : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private List<String> filterAssignableRequestCodes(List<String> values) {
        return values.stream()
                .filter(code -> !code.startsWith("group:"))
                .toList();
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

    private boolean isTreePermission(SystemPermission permission) {
        return permission != null
                && isAssignable(permission)
                && (permission.getEnabled() == null || Boolean.TRUE.equals(permission.getEnabled()))
                && permission.getPermissionCode() != null
                && !permission.getPermissionCode().startsWith("common:");
    }

    private void addPermissionWithParents(Map<String, SystemPermission> permissionsByCode, SystemPermission permission) {
        if (permission == null || permission.getPermissionCode() == null) {
            return;
        }
        Long parentId = permission.getParentId();
        if (parentId != null && parentId > 0) {
            SystemPermission parent = permissionMapper.selectById(parentId);
            if (parent == null) {
                throw new RuntimeException("Parent permission does not exist");
            }
            if (isAssignable(parent)) {
                addPermissionWithParents(permissionsByCode, parent);
            }
        }
        permissionsByCode.put(permission.getPermissionCode(), permission);
    }

    private Map<String, Object> permissionAssignmentSnapshot(
            String roleCode,
            List<String> permissionCodes,
            List<String> addedCodes,
            List<String> removedCodes,
            List<SystemPermission> permissions) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("roleCode", roleCode);
        row.put("permissionCodes", permissionCodes);
        row.put("addedPermissionCodes", addedCodes);
        row.put("removedPermissionCodes", removedCodes);
        row.put("addedCount", addedCodes.size());
        row.put("removedCount", removedCodes.size());
        row.put("moduleCount", (int) permissions.stream().filter(this::isMenuPermission).count());
        row.put("permissionCount", permissionCodes.size());
        return row;
    }

    private Set<String> assignablePermissionCodes(List<SystemPermission> permissions) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (SystemPermission permission : safeList(permissions)) {
            if (isAssignable(permission) && permission.getPermissionCode() != null) {
                result.add(permission.getPermissionCode());
            }
        }
        return result;
    }

    private List<String> difference(Set<String> left, Set<String> right) {
        List<String> result = new ArrayList<>();
        for (String code : left) {
            if (!right.contains(code)) {
                result.add(code);
            }
        }
        return result;
    }

    private boolean isMenuPermission(SystemPermission permission) {
        return isAssignable(permission) && "MENU".equals(permission.getPermissionType());
    }

    private Comparator<SystemPermission> permissionComparator() {
        return Comparator
                .comparing((SystemPermission permission) -> permission.getSortOrder() == null ? Integer.MAX_VALUE : permission.getSortOrder())
                .thenComparing(permission -> permission.getId() == null ? Long.MAX_VALUE : permission.getId());
    }

    private void sortTree(List<PermissionTreeNodeDTO> nodes) {
        nodes.sort(Comparator
                .comparing((PermissionTreeNodeDTO node) -> node.getSortOrder() == null ? Integer.MAX_VALUE : node.getSortOrder())
                .thenComparing(node -> node.getId() == null ? Long.MAX_VALUE : node.getId()));
        for (PermissionTreeNodeDTO node : nodes) {
            sortTree(node.getChildren());
        }
    }

    private String resolveGroupKey(String permissionCode) {
        if (permissionCode == null) {
            return null;
        }
        if (permissionCode.startsWith("market:order:")
                || permissionCode.startsWith("market:sales-order:")
                || permissionCode.startsWith("market:part-search:")
                || permissionCode.startsWith("market:delivered-item:")
                || permissionCode.startsWith("market:production-item:")
                || permissionCode.startsWith("market:locked-item:")) {
            return "orders";
        }
        if (permissionCode.startsWith("market:")) {
            return "market";
        }
        if (permissionCode.startsWith("engineer:")) {
            return "engineer";
        }
        if (permissionCode.startsWith("system:")) {
            return "system";
        }
        return null;
    }

    private record PermissionGroup(String key, String label, Integer sortOrder) {
    }
}
