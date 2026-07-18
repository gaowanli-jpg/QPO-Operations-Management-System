package com.machining.quote.service;

import com.machining.quote.dto.MenuTreeDTO;
import com.machining.quote.dto.PermissionInfoDTO;
import com.machining.quote.entity.SystemPermission;
import com.machining.quote.entity.SystemRole;
import com.machining.quote.mapper.SystemPermissionMapper;
import com.machining.quote.mapper.SystemRoleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class PermissionService {
    private static final Logger log = LoggerFactory.getLogger(PermissionService.class);

    @Autowired
    private SystemRoleMapper roleMapper;

    @Autowired
    private SystemPermissionMapper permissionMapper;

    public PermissionInfoDTO getPermissionInfo(Integer legacyUserId, String legacyRole) {
        List<SystemRole> roles = loadRoles(legacyUserId);
        List<SystemPermission> permissions = loadPermissions(legacyUserId);

        if (roles.isEmpty()) {
            roles = fallbackRoles(legacyRole);
        }
        if (permissions.isEmpty()) {
            permissions = fallbackPermissions(roles);
        }

        PermissionInfoDTO info = new PermissionInfoDTO();
        info.setRoles(roleCodes(roles));
        info.setPermissions(permissionCodes(permissions));
        info.setSuperAdmin(roles.stream().anyMatch(this::isSuperAdmin));
        info.setMenus(buildMenus(permissions));
        return info;
    }

    private List<SystemRole> loadRoles(Integer legacyUserId) {
        if (legacyUserId == null || roleMapper == null) {
            return List.of();
        }
        try {
            List<SystemRole> roles = roleMapper.selectActiveRolesByLegacyUserId(legacyUserId);
            return roles == null ? List.of() : roles;
        } catch (RuntimeException e) {
            log.warn("Permission roles fallback used userId={} reason={}", legacyUserId, e.getClass().getSimpleName());
            return List.of();
        }
    }

    private List<SystemPermission> loadPermissions(Integer legacyUserId) {
        if (legacyUserId == null || permissionMapper == null) {
            return List.of();
        }
        try {
            List<SystemPermission> permissions = permissionMapper.selectEnabledPermissionsByLegacyUserId(legacyUserId);
            return permissions == null ? List.of() : permissions;
        } catch (RuntimeException e) {
            log.warn("Permission points fallback used userId={} reason={}", legacyUserId, e.getClass().getSimpleName());
            return List.of();
        }
    }

    private List<String> roleCodes(List<SystemRole> roles) {
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        for (SystemRole role : roles) {
            if (!isBlank(role.getRoleCode())) {
                codes.add(role.getRoleCode());
            }
        }
        return new ArrayList<>(codes);
    }

    private List<String> permissionCodes(List<SystemPermission> permissions) {
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        for (SystemPermission permission : permissions) {
            if (!isBlank(permission.getPermissionCode())) {
                codes.add(permission.getPermissionCode());
            }
        }
        return new ArrayList<>(codes);
    }

    private List<MenuTreeDTO> buildMenus(List<SystemPermission> permissions) {
        List<SystemPermission> visibleMenus = permissions.stream()
                .filter(permission -> "MENU".equalsIgnoreCase(permission.getPermissionType()))
                .filter(permission -> Boolean.TRUE.equals(permission.getVisible()))
                .filter(permission -> !isBlank(permission.getRoutePath()))
                .sorted(Comparator.comparing(this::sortOrder).thenComparing(permission -> permission.getId() == null ? 0L : permission.getId()))
                .toList();

        Map<Long, MenuTreeDTO> byId = new LinkedHashMap<>();
        List<MenuTreeDTO> roots = new ArrayList<>();
        for (SystemPermission permission : visibleMenus) {
            MenuTreeDTO menu = toMenu(permission);
            if (menu.getId() != null) {
                byId.put(menu.getId(), menu);
            }
        }
        for (MenuTreeDTO menu : byId.values()) {
            Long parentId = menu.getParentId();
            if (parentId != null && parentId > 0 && byId.containsKey(parentId)) {
                byId.get(parentId).getChildren().add(menu);
            } else {
                roots.add(menu);
            }
        }
        return roots;
    }

    private MenuTreeDTO toMenu(SystemPermission permission) {
        MenuTreeDTO menu = new MenuTreeDTO();
        menu.setId(permission.getId());
        menu.setParentId(permission.getParentId());
        menu.setTitle(permission.getPermissionName());
        menu.setPermission(permission.getPermissionCode());
        menu.setPath(permission.getRoutePath());
        menu.setComponentKey(permission.getComponentKey());
        menu.setIcon(permission.getIcon());
        menu.setSortOrder(permission.getSortOrder());
        return menu;
    }

    private int sortOrder(SystemPermission permission) {
        return permission.getSortOrder() == null ? 0 : permission.getSortOrder();
    }

    private boolean isSuperAdmin(SystemRole role) {
        return Boolean.TRUE.equals(role.getSuperAdmin()) || "SUPER_ADMIN".equalsIgnoreCase(role.getRoleCode());
    }

    private List<SystemRole> fallbackRoles(String legacyRole) {
        SystemRole role = new SystemRole();
        String normalized = legacyRole == null ? "" : legacyRole.trim().toUpperCase(Locale.ROOT);
        role.setRoleCode(switch (normalized) {
            case "ADMIN" -> "SUPER_ADMIN";
            case "MARKET" -> "MARKET_USER";
            case "ENGINEER" -> "ENGINEER_USER";
            default -> "READONLY";
        });
        role.setRoleName(role.getRoleCode());
        role.setSuperAdmin("SUPER_ADMIN".equals(role.getRoleCode()));
        return List.of(role);
    }

    private List<SystemPermission> fallbackPermissions(List<SystemRole> roles) {
        Set<String> roleCodes = new LinkedHashSet<>(roleCodes(roles));
        if (roleCodes.contains("SUPER_ADMIN")) {
            return FALLBACK_CATALOG;
        }
        LinkedHashSet<SystemPermission> result = new LinkedHashSet<>();
        for (SystemPermission permission : FALLBACK_CATALOG) {
            Set<String> allowed = FALLBACK_ROLE_PERMISSIONS.get(permission.getPermissionCode());
            if (allowed != null && allowed.stream().anyMatch(roleCodes::contains)) {
                result.add(permission);
            }
        }
        return new ArrayList<>(result);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static final List<SystemPermission> FALLBACK_CATALOG = List.of(
            permission(1L, "MENU", "market:rfq:view", "新单导入", "/market", "MarketDashboard", true, 110),
            permission(2L, "BUTTON", "market:rfq:import", "导入 RFQ", null, null, false, 111),
            permission(3L, "MENU", "market:order:view", "订单管理", "/orders", "OrderManage", true, 120),
            permission(4L, "MENU", "market:quote-process:view", "报价处理", "/quote-process", "QuoteProcess", true, 130),
            permission(5L, "MENU", "market:release:view", "议价提单", "/release", "MarketRelease", true, 140),
            permission(6L, "MENU", "engineer:setup:view", "零件核价", "/engineering", "GlobalSetup", true, 210),
            permission(7L, "MENU", "engineer:pricing:view", "逐件核价", "/pricing", "ItemPricing", false, 220),
            permission(8L, "MENU", "engineer:dict:view", "材料工艺", "/material-process", "MaterialProcessManage", true, 230),
            permission(9L, "MENU", "system:user:view", "用户管理", "/users", "UserManage", true, 810),
            permission(10L, "MENU", "system:audit:view", "操作日志", "/audit-logs", "AuditLogManage", true, 850),
            permission(11L, "API", "common:order:select", "选择订单", null, null, false, 910),
            permission(12L, "API", "common:customer:select", "选择客户", null, null, false, 911),
            permission(13L, "API", "common:file:preview", "预览文件", null, null, false, 912),
            permission(14L, "API", "common:task:view", "查看异步任务", null, null, false, 913)
    );

    private static final Map<String, Set<String>> FALLBACK_ROLE_PERMISSIONS = Map.ofEntries(
            Map.entry("market:rfq:view", Set.of("MARKET_USER", "MARKET_MANAGER", "READONLY")),
            Map.entry("market:rfq:import", Set.of("MARKET_USER", "MARKET_MANAGER")),
            Map.entry("market:order:view", Set.of("MARKET_USER", "MARKET_MANAGER", "READONLY")),
            Map.entry("market:quote-process:view", Set.of("MARKET_USER", "MARKET_MANAGER", "READONLY")),
            Map.entry("market:release:view", Set.of("MARKET_USER", "MARKET_MANAGER", "READONLY")),
            Map.entry("engineer:setup:view", Set.of("ENGINEER_USER", "ENGINEER_MANAGER", "READONLY")),
            Map.entry("engineer:pricing:view", Set.of("ENGINEER_USER", "ENGINEER_MANAGER", "READONLY")),
            Map.entry("engineer:dict:view", Set.of("ENGINEER_USER", "ENGINEER_MANAGER", "READONLY")),
            Map.entry("system:user:view", Set.of()),
            Map.entry("system:audit:view", Set.of("AUDITOR")),
            Map.entry("common:order:select", Set.of("MARKET_USER", "MARKET_MANAGER", "ENGINEER_USER", "ENGINEER_MANAGER", "READONLY")),
            Map.entry("common:customer:select", Set.of("MARKET_USER", "MARKET_MANAGER", "ENGINEER_USER", "ENGINEER_MANAGER", "READONLY")),
            Map.entry("common:file:preview", Set.of("MARKET_USER", "MARKET_MANAGER", "ENGINEER_USER", "ENGINEER_MANAGER", "READONLY")),
            Map.entry("common:task:view", Set.of("MARKET_USER", "MARKET_MANAGER", "ENGINEER_USER", "ENGINEER_MANAGER", "READONLY"))
    );

    private static SystemPermission permission(
            Long id,
            String type,
            String code,
            String name,
            String path,
            String componentKey,
            boolean visible,
            int sortOrder) {
        SystemPermission permission = new SystemPermission();
        permission.setId(id);
        permission.setParentId(0L);
        permission.setPermissionType(type);
        permission.setPermissionCode(code);
        permission.setPermissionName(name);
        permission.setRoutePath(path);
        permission.setComponentKey(componentKey);
        permission.setVisible(visible);
        permission.setSortOrder(sortOrder);
        return permission;
    }
}
