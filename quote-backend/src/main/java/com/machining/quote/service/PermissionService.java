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
            return fallbackCatalog();
        }
        LinkedHashSet<SystemPermission> result = new LinkedHashSet<>();
        for (SystemPermission permission : fallbackCatalog()) {
            Set<String> allowed = allowedRolesFor(permission.getPermissionCode());
            if (allowed != null && allowed.stream().anyMatch(roleCodes::contains)) {
                result.add(permission);
            }
        }
        return new ArrayList<>(result);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static List<SystemPermission> fallbackCatalog() {
        List<SystemPermission> permissions = new ArrayList<>(FALLBACK_CATALOG);
        permissions.addAll(FALLBACK_STAGE4_CATALOG);
        return permissions;
    }

    private static Set<String> allowedRolesFor(String permissionCode) {
        return FALLBACK_ROLE_PERMISSIONS.get(permissionCode);
    }

    private static final List<SystemPermission> FALLBACK_CATALOG = List.of(
            permission(1L, "MENU", "market:rfq:view", "新单导入", "/market", "MarketDashboard", true, 110),
            permission(2L, "BUTTON", "market:rfq:import", "导入 RFQ", null, null, false, 111, 1L),
            permission(101L, "BUTTON", "market:rfq:upload-drawing", "上传图纸", null, null, false, 114, 1L),
            permission(130L, "API", "market:rfq:customer-select", "新单导入选择客户", null, null, false, 115, 1L),
            permission(131L, "API", "market:rfq:file-preview", "新单导入预览文件", null, null, false, 116, 1L),
            permission(169L, "BUTTON", "market:rfq:import-sales-order", "导入销售订单", null, null, false, 117, 1L),
            permission(3L, "MENU", "market:order:view", "询价订单", "/orders", "OrderManage", true, 120),
            permission(102L, "BUTTON", "market:order:supplement-drawing", "补传图纸", null, null, false, 121, 3L),
            permission(209L, "BUTTON", "market:order:update-item", "保存询价订单零件信息", null, null, false, 122, 3L),
            deprecatedPermission(103L, "BUTTON", "market:order:delete", "删除订单兼容接口（已废弃）", null, null, 122, 3L),
            permission(132L, "API", "market:order:customer-select", "询价订单选择客户", null, null, false, 123, 3L),
            permission(133L, "API", "market:order:file-preview", "询价订单预览文件", null, null, false, 124, 3L),
            permission(190L, "MENU", "market:sales-order:view", "销售订单", "/sales-orders", "SalesOrderManage", true, 125),
            permission(214L, "MENU", "market:part-search:view", "零件查询", "/part-search", "PartSearchManage", true, 125),
            permission(191L, "API", "market:sales-order:customer-select", "销售订单选择客户", null, null, false, 126, 190L),
            permission(192L, "API", "market:sales-order:file-preview", "销售订单预览文件", null, null, false, 127, 190L),
            permission(193L, "BUTTON", "market:sales-order:supplement-drawing", "销售订单补传图纸", null, null, false, 128, 190L),
            permission(194L, "BUTTON", "market:sales-order:discard", "废弃销售订单", null, null, false, 129, 190L),
            permission(195L, "BUTTON", "market:sales-order:restore", "恢复销售订单", null, null, false, 130, 190L),
            permission(215L, "BUTTON", "market:sales-order:production-task-notice-export", "导出生产任务通知单", null, null, false, 131, 190L),
            permission(225L, "BUTTON", "market:sales-order:item-detail-export", "导出明细表", null, null, false, 133, 190L),
            permission(217L, "BUTTON", "market:sales-order:update", "维护销售订单信息", null, null, false, 132, 190L),
            permission(153L, "BUTTON", "market:order:rollback-quoted", "已报价退回已核价", null, null, false, 128, 3L),
            permission(154L, "BUTTON", "market:order:rollback-priced", "已核价退回未核价", null, null, false, 129, 3L),
            permission(155L, "BUTTON", "market:order:discard", "废弃订单", null, null, false, 130, 3L),
            permission(156L, "BUTTON", "market:order:restore", "恢复废弃订单", null, null, false, 131, 3L),
            deprecatedPermission(15L, "MENU", "market:history-order:view", "历史订单兼容权限（已废弃）", "/history-orders", null, 125, 0L),
            deprecatedPermission(134L, "API", "market:history-order:customer-select", "历史订单客户选择兼容接口（已废弃）", null, null, 126, 15L),
            deprecatedPermission(135L, "API", "market:history-order:file-preview", "历史订单文件预览兼容接口（已废弃）", null, null, 127, 15L),
            permission(158L, "MENU", "market:delivered-item:view", "已提零件", "/delivered-items", "DeliveredItemManage", true, 126),
            permission(159L, "API", "market:delivered-item:file-preview", "已提零件预览图纸", null, null, false, 127, 158L),
            permission(160L, "BUTTON", "market:delivered-item:mes-sync", "获取MES物料号", null, null, false, 128, 158L),
            permission(161L, "BUTTON", "market:delivered-item:wms-allocate", "提交WMS锁库", null, null, false, 129, 158L),
            permission(163L, "API", "market:delivered-item:customer-select", "已提零件选择客户", null, null, false, 130, 158L),
            permission(210L, "BUTTON", "market:delivered-item:discard", "废弃已提零件", null, null, false, 131, 158L),
            permission(211L, "BUTTON", "market:delivered-item:restore", "恢复已提零件", null, null, false, 132, 158L),
            permission(199L, "MENU", "market:production-item:view", "待产零件", "/production-items", "ProductionItemManage", true, 127),
            permission(200L, "API", "market:production-item:customer-select", "待产零件选择客户", null, null, false, 128, 199L),
            permission(201L, "BUTTON", "market:production-item:mes-production-submit", "待产零件下发MES生产", null, null, false, 129, 199L),
            permission(212L, "BUTTON", "market:production-item:discard", "废弃待产零件", null, null, false, 130, 199L),
            permission(213L, "BUTTON", "market:production-item:restore", "恢复待产零件", null, null, false, 131, 199L),
            permission(162L, "MENU", "market:locked-item:view", "锁库零件", "/locked-items", "LockedItemManage", true, 128),
            permission(164L, "API", "market:locked-item:customer-select", "锁库零件选择客户", null, null, false, 128, 162L),
            permission(222L, "BUTTON", "market:locked-item:stock-picking-submit", "锁库零件下发提库存工单", null, null, false, 129, 162L),
            permission(223L, "BUTTON", "market:locked-item:discard", "废弃锁库零件", null, null, false, 130, 162L),
            permission(224L, "BUTTON", "market:locked-item:restore", "恢复锁库零件", null, null, false, 131, 162L),
            permission(202L, "MENU", "change:sales-order:view", "销售变更", "/sales-order-change", "SalesOrderChangeManage", true, 700),
            permission(203L, "API", "change:sales-order:customer-select", "销售变更选择客户", null, null, false, 701, 202L),
            permission(204L, "API", "change:sales-order:order-select", "销售变更选择订单", null, null, false, 702, 202L),
            permission(205L, "BUTTON", "change:sales-order:update", "保存销售变更", null, null, false, 703, 202L),
            permission(226L, "MENU", "change:linked-change:view", "关联变更", "/linked-changes", "LinkedChangeManage", true, 699),
            permission(227L, "API", "change:linked-change:customer-select", "关联变更选择客户", null, null, false, 700, 226L),
            permission(228L, "BUTTON", "change:linked-change:handle", "处理关联变更通知", null, null, false, 701, 226L),
            permission(4L, "MENU", "market:quote-process:view", "报价处理", "/quote-process", "QuoteProcess", true, 130),
            permission(104L, "BUTTON", "market:quote-process:update-lead-time", "维护报价交期", null, null, false, 131, 4L),
            permission(105L, "BUTTON", "market:quote-process:export", "导出报价单", null, null, false, 132, 4L),
            permission(136L, "API", "market:quote-process:customer-select", "报价处理选择客户", null, null, false, 133, 4L),
            permission(137L, "API", "market:quote-process:order-select", "报价处理选择订单", null, null, false, 134, 4L),
            permission(138L, "API", "market:quote-process:task-view", "报价处理查看异步任务", null, null, false, 135, 4L),
            permission(146L, "BUTTON", "market:quote-process:save-quote", "保存报价单", null, null, false, 136, 4L),
            permission(5L, "MENU", "market:release:view", "议价提单", "/release", "MarketRelease", true, 140),
            permission(106L, "BUTTON", "market:release:create", "生成提单", null, null, false, 141, 5L),
            permission(139L, "API", "market:release:customer-select", "议价提单选择客户", null, null, false, 143, 5L),
            permission(140L, "API", "market:release:order-select", "议价提单选择订单", null, null, false, 144, 5L),
            permission(141L, "API", "market:release:task-view", "议价提单查看异步任务", null, null, false, 145, 5L),
            permission(218L, "MENU", "market:release-append:view", "追加提单", "/release-append", "ReleaseAppendManage", true, 145),
            permission(219L, "API", "market:release-append:customer-select", "追加提单选择客户", null, null, false, 1451, 218L),
            permission(220L, "API", "market:release-append:order-select", "追加提单选择销售订单", null, null, false, 1452, 218L),
            permission(221L, "BUTTON", "market:release-append:create", "追加销售提单", null, null, false, 1453, 218L),
            permission(186L, "MENU", "market:u9-sync:view", "U9同步", "/u9-sync", "SalesOrderU9Sync", true, 146),
            permission(187L, "API", "market:u9-sync:customer-select", "U9同步选择客户", null, null, false, 147, 186L),
            permission(188L, "API", "market:u9-sync:order-select", "U9同步选择销售订单", null, null, false, 148, 186L),
            permission(189L, "BUTTON", "market:u9-sync:submit", "同步U9", null, null, false, 149, 186L),
            permission(165L, "MENU", "market:delivery-document:view", "提单表据", "/delivery-documents", "DeliveryDocumentManage", true, 150),
            permission(166L, "API", "market:delivery-document:customer-select", "提单表据选择客户", null, null, false, 151, 165L),
            permission(167L, "API", "market:delivery-document:order-select", "提单表据选择订单", null, null, false, 152, 165L),
            permission(168L, "BUTTON", "market:delivery-document:export", "导出提单表据", null, null, false, 153, 165L),
            permission(108L, "MENU", "market:template:view", "报价模板", "/quote-template", "QuoteTemplateManage", true, 160),
            permission(109L, "BUTTON", "market:template:upload", "上传报价模板", null, null, false, 161, 108L),
            permission(6L, "MENU", "engineer:setup:view", "零件核价", "/engineering", "GlobalSetup", true, 210),
            permission(110L, "BUTTON", "engineer:setup:save-config", "保存全局设定", null, null, false, 211, 6L),
            permission(142L, "API", "engineer:setup:customer-select", "零件核价选择客户", null, null, false, 212, 6L),
            permission(143L, "API", "engineer:setup:order-select", "零件核价选择订单", null, null, false, 213, 6L),
            permission(144L, "API", "engineer:setup:file-preview", "零件核价预览图纸", null, null, false, 214, 6L),
            permission(7L, "MENU", "engineer:pricing:view", "逐件核价", "/pricing", "ItemPricing", false, 220, 6L),
            permission(111L, "BUTTON", "engineer:pricing:save-item", "保存单件核价", null, null, false, 221, 7L),
            permission(112L, "BUTTON", "engineer:pricing:refresh", "一键刷新核价", null, null, false, 222, 7L),
            permission(113L, "BUTTON", "engineer:pricing:complete-order", "完成整单报价", null, null, false, 223, 7L),
            permission(145L, "API", "engineer:pricing:task-view", "逐件核价查看异步任务", null, null, false, 224, 7L),
            permission(152L, "BUTTON", "engineer:pricing:export-sheet", "生成核价单", null, null, false, 225, 7L),
            permission(8L, "MENU", "engineer:dict:view", "材料工艺", "/material-process", "MaterialProcessManage", true, 230),
            permission(114L, "BUTTON", "engineer:dict:material-manage", "维护材料", null, null, false, 231, 8L),
            permission(115L, "BUTTON", "engineer:dict:process-manage", "维护工艺", null, null, false, 232, 8L),
            permission(116L, "BUTTON", "engineer:dict:fee-manage", "维护费用", null, null, false, 233, 8L),
            permission(9L, "MENU", "system:user:view", "用户管理", "/users", "UserManage", true, 810),
            permission(117L, "BUTTON", "system:user:create", "新增用户", null, null, false, 811, 9L),
            permission(118L, "BUTTON", "system:user:update", "编辑用户", null, null, false, 812, 9L),
            permission(119L, "BUTTON", "system:user:disable", "启停用户", null, null, false, 813, 9L),
            permission(120L, "BUTTON", "system:user:reset-password", "重置密码", null, null, false, 814, 9L),
            permission(121L, "BUTTON", "system:user:delete", "删除用户", null, null, false, 815, 9L),
            permission(122L, "MENU", "system:role:view", "角色权限", "/roles", "SystemRoleManage", true, 820),
            permission(123L, "BUTTON", "system:role:create", "新增角色", null, null, false, 821, 122L),
            permission(124L, "BUTTON", "system:role:update", "编辑角色", null, null, false, 822, 122L),
            permission(125L, "BUTTON", "system:role:delete", "删除角色", null, null, false, 823, 122L),
            permission(126L, "BUTTON", "system:role:assign-permission", "分配角色权限", null, null, false, 824, 122L),
            permission(127L, "BUTTON", "system:role:assign-data-scope", "分配数据权限", null, null, false, 825, 122L),
            permission(147L, "MENU", "system:org:view", "组织档案", "/organization", "SystemOrgManage", true, 830),
            permission(148L, "MENU", "system:dept:view", "部门管理", null, null, false, 831, 147L),
            permission(149L, "BUTTON", "system:dept:manage", "维护部门", null, null, false, 832, 148L),
            permission(150L, "MENU", "system:post:view", "岗位管理", null, null, false, 840, 147L),
            permission(151L, "BUTTON", "system:post:manage", "维护岗位", null, null, false, 841, 150L),
            permission(170L, "MENU", "system:customer:view", "客户资料", "/customers", "CustomerManage", true, 845),
            permission(171L, "BUTTON", "system:customer:sync", "同步U9客户", null, null, false, 846, 170L),
            permission(216L, "BUTTON", "system:customer:supplier-code-update", "维护客户供应商代码", null, null, false, 847, 170L),
            permission(174L, "MENU", "system:config:view", "系统配置", "/system-config", "SystemConfigManage", true, 849),
            permission(175L, "BUTTON", "system:config:storage:update", "维护文件存储配置", null, null, false, 8491, 174L),
            permission(176L, "BUTTON", "system:config:storage:test", "测试文件存储配置", null, null, false, 8492, 174L),
            permission(177L, "BUTTON", "system:config:storage:activate", "激活文件存储配置", null, null, false, 8493, 174L),
            permission(178L, "BUTTON", "system:config:integration:update", "维护出站接口/数据中心配置", null, null, false, 8494, 174L),
            permission(179L, "BUTTON", "system:config:integration:test", "测试出站接口/数据中心配置", null, null, false, 8495, 174L),
            permission(180L, "BUTTON", "system:config:integration:activate", "激活出站接口/数据中心配置", null, null, false, 8496, 174L),
            permission(197L, "BUTTON", "system:config:inbound:update", "维护入站接口配置", null, null, false, 8497, 174L),
            permission(198L, "BUTTON", "system:config:inbound:token-rotate", "轮换入站回调Token", null, null, false, 8498, 174L),
            permission(206L, "BUTTON", "system:config:sso:update", "维护数据中心登录配置", null, null, false, 8499, 174L),
            permission(207L, "BUTTON", "system:config:sso:test", "测试数据中心登录配置", null, null, false, 8500, 174L),
            permission(208L, "BUTTON", "system:config:sso:activate", "激活数据中心登录配置", null, null, false, 8501, 174L),
            permission(10L, "MENU", "system:audit:view", "操作日志", "/audit-logs", "AuditLogManage", true, 850),
            permission(128L, "BUTTON", "system:audit:export", "导出操作日志", null, null, false, 851, 10L),
            permission(129L, "BUTTON", "system:audit:retention", "配置日志保留策略", null, null, false, 852, 10L)
    );

    private static final List<SystemPermission> FALLBACK_STAGE4_CATALOG = List.of(
            compatibilityPermission(903L, "common:file:preview", "预览文件", 912),
            compatibilityPermission(904L, "common:task:view", "查看异步任务", 913)
    );

    private static final Map<String, Set<String>> FALLBACK_ROLE_PERMISSIONS = Map.ofEntries(
            Map.entry("market:rfq:view", Set.of("MARKET_USER", "MARKET_MANAGER", "READONLY")),
            Map.entry("market:rfq:import", Set.of("MARKET_USER", "MARKET_MANAGER")),
            Map.entry("market:rfq:upload-drawing", Set.of("MARKET_USER", "MARKET_MANAGER")),
            Map.entry("market:rfq:customer-select", Set.of("MARKET_USER", "MARKET_MANAGER", "READONLY")),
            Map.entry("market:rfq:file-preview", Set.of("MARKET_USER", "MARKET_MANAGER", "READONLY")),
            Map.entry("market:rfq:import-sales-order", Set.of("MARKET_USER", "MARKET_MANAGER")),
            Map.entry("market:order:view", Set.of("MARKET_USER", "MARKET_MANAGER", "READONLY")),
            Map.entry("market:order:supplement-drawing", Set.of("MARKET_USER", "MARKET_MANAGER")),
            Map.entry("market:order:delete", Set.of("MARKET_MANAGER")),
            Map.entry("market:order:customer-select", Set.of("MARKET_USER", "MARKET_MANAGER", "READONLY")),
            Map.entry("market:order:file-preview", Set.of("MARKET_USER", "MARKET_MANAGER", "READONLY")),
            Map.entry("market:sales-order:view", Set.of("MARKET_USER", "MARKET_MANAGER", "READONLY")),
            Map.entry("market:sales-order:customer-select", Set.of("MARKET_USER", "MARKET_MANAGER", "READONLY")),
            Map.entry("market:sales-order:file-preview", Set.of("MARKET_USER", "MARKET_MANAGER", "READONLY")),
            Map.entry("market:sales-order:supplement-drawing", Set.of("MARKET_USER", "MARKET_MANAGER")),
            Map.entry("market:sales-order:discard", Set.of("MARKET_MANAGER")),
            Map.entry("market:sales-order:restore", Set.of("MARKET_MANAGER")),
            Map.entry("market:sales-order:production-task-notice-export", Set.of("MARKET_USER", "MARKET_MANAGER")),
            Map.entry("market:sales-order:item-detail-export", Set.of("MARKET_USER", "MARKET_MANAGER")),
            Map.entry("market:sales-order:update", Set.of("SUPER_ADMIN", "MARKET_MANAGER")),
            Map.entry("market:part-search:view", Set.of("SUPER_ADMIN", "MARKET_USER", "MARKET_MANAGER", "READONLY")),
            Map.entry("market:order:rollback-quoted", Set.of("MARKET_MANAGER")),
            Map.entry("market:order:rollback-priced", Set.of("MARKET_MANAGER")),
            Map.entry("market:order:discard", Set.of("MARKET_MANAGER")),
            Map.entry("market:order:restore", Set.of("MARKET_MANAGER")),
            Map.entry("market:order:update-item", Set.of("MARKET_MANAGER")),
            Map.entry("market:history-order:view", Set.of("MARKET_USER", "MARKET_MANAGER", "READONLY")),
            Map.entry("market:history-order:customer-select", Set.of("MARKET_USER", "MARKET_MANAGER", "READONLY")),
            Map.entry("market:history-order:file-preview", Set.of("MARKET_USER", "MARKET_MANAGER", "READONLY")),
            Map.entry("market:delivered-item:view", Set.of("MARKET_USER", "MARKET_MANAGER", "READONLY")),
            Map.entry("market:delivered-item:file-preview", Set.of("MARKET_USER", "MARKET_MANAGER", "READONLY")),
            Map.entry("market:delivered-item:mes-sync", Set.of("MARKET_USER", "MARKET_MANAGER")),
            Map.entry("market:delivered-item:wms-allocate", Set.of("MARKET_USER", "MARKET_MANAGER")),
            Map.entry("market:delivered-item:customer-select", Set.of("MARKET_USER", "MARKET_MANAGER", "READONLY")),
            Map.entry("market:delivered-item:discard", Set.of("MARKET_USER", "MARKET_MANAGER")),
            Map.entry("market:delivered-item:restore", Set.of("MARKET_USER", "MARKET_MANAGER")),
            Map.entry("market:production-item:view", Set.of("MARKET_USER", "MARKET_MANAGER", "READONLY")),
            Map.entry("market:production-item:customer-select", Set.of("MARKET_USER", "MARKET_MANAGER", "READONLY")),
            Map.entry("market:production-item:mes-production-submit", Set.of("MARKET_USER", "MARKET_MANAGER")),
            Map.entry("market:production-item:discard", Set.of("MARKET_USER", "MARKET_MANAGER")),
            Map.entry("market:production-item:restore", Set.of("MARKET_USER", "MARKET_MANAGER")),
            Map.entry("market:locked-item:view", Set.of("MARKET_USER", "MARKET_MANAGER", "READONLY")),
            Map.entry("market:locked-item:customer-select", Set.of("MARKET_USER", "MARKET_MANAGER", "READONLY")),
            Map.entry("market:locked-item:stock-picking-submit", Set.of("MARKET_USER", "MARKET_MANAGER")),
            Map.entry("market:locked-item:discard", Set.of("MARKET_USER", "MARKET_MANAGER")),
            Map.entry("market:locked-item:restore", Set.of("MARKET_USER", "MARKET_MANAGER")),
            Map.entry("change:sales-order:view", Set.of("MARKET_USER", "MARKET_MANAGER", "READONLY")),
            Map.entry("change:sales-order:customer-select", Set.of("MARKET_USER", "MARKET_MANAGER", "READONLY")),
            Map.entry("change:sales-order:order-select", Set.of("MARKET_USER", "MARKET_MANAGER", "READONLY")),
            Map.entry("change:sales-order:update", Set.of("MARKET_USER", "MARKET_MANAGER")),
            Map.entry("change:linked-change:view", Set.of("SUPER_ADMIN", "MARKET_USER", "MARKET_MANAGER", "READONLY")),
            Map.entry("change:linked-change:customer-select", Set.of("SUPER_ADMIN", "MARKET_USER", "MARKET_MANAGER", "READONLY")),
            Map.entry("change:linked-change:handle", Set.of("SUPER_ADMIN", "MARKET_USER", "MARKET_MANAGER")),
            Map.entry("market:quote-process:view", Set.of("MARKET_USER", "MARKET_MANAGER", "READONLY")),
            Map.entry("market:quote-process:update-lead-time", Set.of("MARKET_USER", "MARKET_MANAGER")),
            Map.entry("market:quote-process:export", Set.of("MARKET_USER", "MARKET_MANAGER")),
            Map.entry("market:quote-process:save-quote", Set.of("MARKET_USER", "MARKET_MANAGER")),
            Map.entry("market:quote-process:customer-select", Set.of("MARKET_USER", "MARKET_MANAGER", "READONLY")),
            Map.entry("market:quote-process:order-select", Set.of("MARKET_USER", "MARKET_MANAGER", "READONLY")),
            Map.entry("market:quote-process:task-view", Set.of("MARKET_USER", "MARKET_MANAGER", "READONLY")),
            Map.entry("market:release:view", Set.of("MARKET_USER", "MARKET_MANAGER", "READONLY")),
            Map.entry("market:release:create", Set.of("MARKET_USER", "MARKET_MANAGER")),
            Map.entry("market:release:customer-select", Set.of("MARKET_USER", "MARKET_MANAGER", "READONLY")),
            Map.entry("market:release:order-select", Set.of("MARKET_USER", "MARKET_MANAGER", "READONLY")),
            Map.entry("market:release:task-view", Set.of("MARKET_USER", "MARKET_MANAGER", "READONLY")),
            Map.entry("market:release-append:view", Set.of("MARKET_USER", "MARKET_MANAGER", "READONLY")),
            Map.entry("market:release-append:customer-select", Set.of("MARKET_USER", "MARKET_MANAGER", "READONLY")),
            Map.entry("market:release-append:order-select", Set.of("MARKET_USER", "MARKET_MANAGER", "READONLY")),
            Map.entry("market:release-append:create", Set.of("MARKET_USER", "MARKET_MANAGER")),
            Map.entry("market:u9-sync:view", Set.of("MARKET_USER", "MARKET_MANAGER", "READONLY")),
            Map.entry("market:u9-sync:customer-select", Set.of("MARKET_USER", "MARKET_MANAGER", "READONLY")),
            Map.entry("market:u9-sync:order-select", Set.of("MARKET_USER", "MARKET_MANAGER", "READONLY")),
            Map.entry("market:u9-sync:submit", Set.of("MARKET_USER", "MARKET_MANAGER")),
            Map.entry("market:delivery-document:view", Set.of("MARKET_USER", "MARKET_MANAGER", "READONLY")),
            Map.entry("market:delivery-document:customer-select", Set.of("MARKET_USER", "MARKET_MANAGER", "READONLY")),
            Map.entry("market:delivery-document:order-select", Set.of("MARKET_USER", "MARKET_MANAGER", "READONLY")),
            Map.entry("market:delivery-document:export", Set.of("MARKET_USER", "MARKET_MANAGER")),
            Map.entry("market:template:view", Set.of("MARKET_MANAGER")),
            Map.entry("market:template:upload", Set.of("MARKET_MANAGER")),
            Map.entry("engineer:setup:view", Set.of("ENGINEER_USER", "ENGINEER_MANAGER", "READONLY")),
            Map.entry("engineer:setup:save-config", Set.of("ENGINEER_USER", "ENGINEER_MANAGER")),
            Map.entry("engineer:setup:customer-select", Set.of("ENGINEER_USER", "ENGINEER_MANAGER", "READONLY")),
            Map.entry("engineer:setup:order-select", Set.of("ENGINEER_USER", "ENGINEER_MANAGER", "READONLY")),
            Map.entry("engineer:setup:file-preview", Set.of("ENGINEER_USER", "ENGINEER_MANAGER", "READONLY")),
            Map.entry("engineer:pricing:view", Set.of("ENGINEER_USER", "ENGINEER_MANAGER", "READONLY")),
            Map.entry("engineer:pricing:save-item", Set.of("ENGINEER_USER", "ENGINEER_MANAGER")),
            Map.entry("engineer:pricing:refresh", Set.of("ENGINEER_USER", "ENGINEER_MANAGER")),
            Map.entry("engineer:pricing:complete-order", Set.of("ENGINEER_USER", "ENGINEER_MANAGER")),
            Map.entry("engineer:pricing:task-view", Set.of("ENGINEER_USER", "ENGINEER_MANAGER", "READONLY")),
            Map.entry("engineer:pricing:export-sheet", Set.of("ENGINEER_USER", "ENGINEER_MANAGER")),
            Map.entry("engineer:dict:view", Set.of("ENGINEER_USER", "ENGINEER_MANAGER", "READONLY")),
            Map.entry("engineer:dict:material-manage", Set.of("ENGINEER_MANAGER")),
            Map.entry("engineer:dict:process-manage", Set.of("ENGINEER_MANAGER")),
            Map.entry("engineer:dict:fee-manage", Set.of("ENGINEER_MANAGER")),
            Map.entry("system:user:view", Set.of()),
            Map.entry("system:user:create", Set.of()),
            Map.entry("system:user:update", Set.of()),
            Map.entry("system:user:disable", Set.of()),
            Map.entry("system:user:reset-password", Set.of()),
            Map.entry("system:user:delete", Set.of()),
            Map.entry("system:role:view", Set.of()),
            Map.entry("system:role:create", Set.of()),
            Map.entry("system:role:update", Set.of()),
            Map.entry("system:role:delete", Set.of()),
            Map.entry("system:role:assign-permission", Set.of()),
            Map.entry("system:role:assign-data-scope", Set.of()),
            Map.entry("system:org:view", Set.of()),
            Map.entry("system:dept:view", Set.of()),
            Map.entry("system:dept:manage", Set.of()),
            Map.entry("system:post:view", Set.of()),
            Map.entry("system:post:manage", Set.of()),
            Map.entry("system:customer:view", Set.of("MARKET_MANAGER")),
            Map.entry("system:customer:sync", Set.of()),
            Map.entry("system:customer:supplier-code-update", Set.of("MARKET_MANAGER")),
            Map.entry("system:config:view", Set.of()),
            Map.entry("system:config:storage:update", Set.of()),
            Map.entry("system:config:storage:test", Set.of()),
            Map.entry("system:config:storage:activate", Set.of()),
            Map.entry("system:config:integration:update", Set.of()),
            Map.entry("system:config:integration:test", Set.of()),
            Map.entry("system:config:integration:activate", Set.of()),
            Map.entry("system:config:inbound:update", Set.of()),
            Map.entry("system:config:inbound:token-rotate", Set.of()),
            Map.entry("system:config:sso:update", Set.of()),
            Map.entry("system:config:sso:test", Set.of()),
            Map.entry("system:config:sso:activate", Set.of()),
            Map.entry("system:audit:view", Set.of("AUDITOR")),
            Map.entry("system:audit:export", Set.of("AUDITOR")),
            Map.entry("system:audit:retention", Set.of()),
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
        return permission(id, type, code, name, path, componentKey, visible, sortOrder, 0L);
    }

    private static SystemPermission permission(
            Long id,
            String type,
            String code,
            String name,
            String path,
            String componentKey,
            boolean visible,
            int sortOrder,
            Long parentId) {
        SystemPermission permission = new SystemPermission();
        permission.setId(id);
        permission.setParentId(parentId);
        permission.setPermissionType(type);
        permission.setPermissionCode(code);
        permission.setPermissionName(name);
        permission.setRoutePath(path);
        permission.setComponentKey(componentKey);
        boolean assignable = true;
        permission.setVisible(visible);
        permission.setEnabled(true);
        permission.setAssignable(assignable);
        permission.setSortOrder(sortOrder);
        return permission;
    }

    private static SystemPermission compatibilityPermission(Long id, String code, String name, int sortOrder) {
        SystemPermission permission = new SystemPermission();
        permission.setId(id);
        permission.setParentId(0L);
        permission.setPermissionType("API");
        permission.setPermissionCode(code);
        permission.setPermissionName(name);
        boolean assignable = false;
        permission.setVisible(false);
        permission.setEnabled(true);
        permission.setAssignable(assignable);
        permission.setSortOrder(sortOrder);
        return permission;
    }

    private static SystemPermission deprecatedPermission(
            Long id,
            String type,
            String code,
            String name,
            String path,
            String componentKey,
            int sortOrder,
            Long parentId) {
        SystemPermission permission = permission(id, type, code, name, path, componentKey, false, sortOrder, parentId);
        permission.setAssignable(false);
        return permission;
    }
}
