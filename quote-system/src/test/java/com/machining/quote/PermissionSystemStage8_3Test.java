package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.controller.SystemRoleController;
import com.machining.quote.dto.PermissionTreeNodeDTO;
import com.machining.quote.dto.RolePermissionAssignReq;
import com.machining.quote.entity.SystemPermission;
import com.machining.quote.entity.SystemRole;
import com.machining.quote.mapper.SystemPermissionMapper;
import com.machining.quote.mapper.SystemRoleMapper;
import com.machining.quote.mapper.SystemRolePermissionMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.SystemRoleService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PermissionSystemStage8_3Test {
    @Test
    void controllerExposesPermissionTreeEndpoint() throws Exception {
        String controllerSource = Files.readString(
                Path.of("src/main/java/com/machining/quote/controller/SystemRoleController.java"),
                StandardCharsets.UTF_8
        );
        assertThat(controllerSource).contains("@GetMapping(\"/permissions/tree\")");

        SystemRoleService service = mock(SystemRoleService.class);
        PermissionTreeNodeDTO node = new PermissionTreeNodeDTO();
        node.setKey("group:market");
        when(service.listPermissionTree()).thenReturn(List.of(node));

        SystemRoleController controller = new SystemRoleController();
        ReflectionTestUtils.setField(controller, "systemRoleService", service);

        assertThat(controller.listPermissionTree()).containsExactly(node);
    }

    @Test
    void permissionTreeGroupsAssignablePermissionsAndHidesLegacyCommon() {
        SystemPermissionMapper permissionMapper = mock(SystemPermissionMapper.class);
        SystemRoleService service = new SystemRoleService();
        ReflectionTestUtils.setField(service, "permissionMapper", permissionMapper);

        when(permissionMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(
                permission(1L, 0L, "MENU", "market:rfq:view", "新单导入", true, true, 110),
                permission(2L, 1L, "BUTTON", "market:rfq:import", "导入 RFQ", false, true, 111),
                permission(15L, 0L, "MENU", "market:history-order:view", "历史订单", true, true, 125),
                permission(901L, 0L, "API", "common:task:view", "查看异步任务", false, false, 910)
        ));

        List<PermissionTreeNodeDTO> tree = service.listPermissionTree();

        PermissionTreeNodeDTO marketGroup = findNode(tree, "group:market");
        PermissionTreeNodeDTO orderGroup = findNode(tree, "group:orders");
        assertThat(marketGroup.getLabel()).isEqualTo("市场");
        assertThat(findNode(marketGroup.getChildren(), "market:rfq:view").getChildren())
                .extracting(PermissionTreeNodeDTO::getPermissionCode)
                .containsExactly("market:rfq:import");
        assertThat(findNode(orderGroup.getChildren(), "market:history-order:view").getPermissionName())
                .isEqualTo("历史订单");
        assertThat(flattenCodes(tree)).doesNotContain("common:task:view");
    }

    @Test
    void assignPermissionsAddsParentMenuFiltersGroupKeysAndAuditsDelta() {
        SystemRoleMapper roleMapper = mock(SystemRoleMapper.class);
        SystemPermissionMapper permissionMapper = mock(SystemPermissionMapper.class);
        SystemRolePermissionMapper rolePermissionMapper = mock(SystemRolePermissionMapper.class);
        AuditService auditService = mock(AuditService.class);
        SystemRoleService service = service(roleMapper, permissionMapper, rolePermissionMapper, auditService);

        when(roleMapper.selectById(20L)).thenReturn(role(20L, "MARKET_USER"));
        when(rolePermissionMapper.selectPermissionCodesByRoleId(20L))
                .thenReturn(List.of("market:rfq:view", "market:order:view", "common:task:view"));
        when(permissionMapper.selectByPermissionCodes(List.of("market:quote-process:export")))
                .thenReturn(List.of(permission(105L, 4L, "BUTTON", "market:quote-process:export", "导出报价单", false, true, 132)));
        when(permissionMapper.selectById(4L))
                .thenReturn(permission(4L, 0L, "MENU", "market:quote-process:view", "报价处理", true, true, 130));
        when(permissionMapper.selectByPermissionCodes(List.of("market:rfq:view", "market:order:view", "common:task:view")))
                .thenReturn(List.of(
                        permission(1L, 0L, "MENU", "market:rfq:view", "新单导入", true, true, 110),
                        permission(3L, 0L, "MENU", "market:order:view", "订单管理", true, true, 120),
                        permission(904L, 0L, "API", "common:task:view", "查看异步任务", false, false, 913)
                ));

        RolePermissionAssignReq req = new RolePermissionAssignReq();
        req.setPermissionCodes(List.of("group:market", "market:quote-process:export"));

        List<String> result = service.assignPermissions(20L, req);

        assertThat(result).containsExactly(
                "market:quote-process:view",
                "market:quote-process:export",
                "common:task:view"
        );
        verify(rolePermissionMapper).deleteByRoleId(20L);
        verify(rolePermissionMapper).insertIgnore(20L, 4L);
        verify(rolePermissionMapper).insertIgnore(20L, 105L);
        verify(rolePermissionMapper).insertIgnore(20L, 904L);

        ArgumentCaptor<Map<String, Object>> afterCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditService).record(eq("SYSTEM_ROLE_ASSIGN_PERMISSION"), eq("system_role"), eq(20L), any(), afterCaptor.capture());
        assertThat(afterCaptor.getValue())
                .containsEntry("addedPermissionCodes", List.of("market:quote-process:view", "market:quote-process:export"))
                .containsEntry("removedPermissionCodes", List.of("market:rfq:view", "market:order:view"))
                .containsEntry("addedCount", 2)
                .containsEntry("removedCount", 2)
                .containsEntry("moduleCount", 1)
                .containsEntry("permissionCount", 3);
    }

    @Test
    void assignPermissionsRejectsUnknownPermissionCodes() {
        SystemRoleMapper roleMapper = mock(SystemRoleMapper.class);
        SystemPermissionMapper permissionMapper = mock(SystemPermissionMapper.class);
        SystemRolePermissionMapper rolePermissionMapper = mock(SystemRolePermissionMapper.class);
        SystemRoleService service = service(roleMapper, permissionMapper, rolePermissionMapper, mock(AuditService.class));

        when(roleMapper.selectById(20L)).thenReturn(role(20L, "MARKET_USER"));
        when(rolePermissionMapper.selectPermissionCodesByRoleId(20L)).thenReturn(List.of());
        when(permissionMapper.selectByPermissionCodes(List.of("market:missing:view"))).thenReturn(List.of());

        RolePermissionAssignReq req = new RolePermissionAssignReq();
        req.setPermissionCodes(List.of("market:missing:view"));

        assertThatThrownBy(() -> service.assignPermissions(20L, req))
                .hasMessageContaining("Some permission codes do not exist");
    }

    @Test
    void assigningWithoutModuleRemovesItsChildren() {
        SystemRoleMapper roleMapper = mock(SystemRoleMapper.class);
        SystemPermissionMapper permissionMapper = mock(SystemPermissionMapper.class);
        SystemRolePermissionMapper rolePermissionMapper = mock(SystemRolePermissionMapper.class);
        AuditService auditService = mock(AuditService.class);
        SystemRoleService service = service(roleMapper, permissionMapper, rolePermissionMapper, auditService);

        when(roleMapper.selectById(20L)).thenReturn(role(20L, "MARKET_USER"));
        when(rolePermissionMapper.selectPermissionCodesByRoleId(20L))
                .thenReturn(List.of("market:release:view", "market:release:create"));
        when(permissionMapper.selectByPermissionCodes(List.of("market:release:view", "market:release:create")))
                .thenReturn(List.of(
                        permission(5L, 0L, "MENU", "market:release:view", "议价提单", true, true, 140),
                        permission(106L, 5L, "BUTTON", "market:release:create", "生成提单", false, true, 141)
                ));

        RolePermissionAssignReq req = new RolePermissionAssignReq();
        req.setPermissionCodes(List.of("group:market"));

        assertThat(service.assignPermissions(20L, req)).isEmpty();
        verify(rolePermissionMapper).deleteByRoleId(20L);
        verify(rolePermissionMapper, never()).insertIgnore(eq(20L), any());

        ArgumentCaptor<Map<String, Object>> afterCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditService).record(eq("SYSTEM_ROLE_ASSIGN_PERMISSION"), eq("system_role"), eq(20L), any(), afterCaptor.capture());
        assertThat(afterCaptor.getValue())
                .containsEntry("removedPermissionCodes", List.of("market:release:view", "market:release:create"))
                .containsEntry("removedCount", 2)
                .containsEntry("permissionCount", 0);
    }

    private SystemRoleService service(
            SystemRoleMapper roleMapper,
            SystemPermissionMapper permissionMapper,
            SystemRolePermissionMapper rolePermissionMapper,
            AuditService auditService) {
        SystemRoleService service = new SystemRoleService();
        ReflectionTestUtils.setField(service, "roleMapper", roleMapper);
        ReflectionTestUtils.setField(service, "permissionMapper", permissionMapper);
        ReflectionTestUtils.setField(service, "rolePermissionMapper", rolePermissionMapper);
        ReflectionTestUtils.setField(service, "auditService", auditService);
        return service;
    }

    private PermissionTreeNodeDTO findNode(List<PermissionTreeNodeDTO> nodes, String key) {
        return nodes.stream()
                .filter(node -> key.equals(node.getKey()))
                .findFirst()
                .orElseThrow();
    }

    private List<String> flattenCodes(List<PermissionTreeNodeDTO> nodes) {
        return nodes.stream()
                .flatMap(node -> {
                    List<String> current = node.getPermissionCode() == null ? List.of() : List.of(node.getPermissionCode());
                    List<String> children = flattenCodes(node.getChildren());
                    return java.util.stream.Stream.concat(current.stream(), children.stream());
                })
                .toList();
    }

    private SystemRole role(Long id, String code) {
        SystemRole role = new SystemRole();
        role.setId(id);
        role.setRoleCode(code);
        role.setRoleName(code);
        role.setEnabled(true);
        role.setDeleted(false);
        return role;
    }

    private SystemPermission permission(
            Long id,
            Long parentId,
            String type,
            String code,
            String name,
            boolean visible,
            boolean assignable,
            int sortOrder) {
        SystemPermission permission = new SystemPermission();
        permission.setId(id);
        permission.setParentId(parentId);
        permission.setPermissionType(type);
        permission.setPermissionCode(code);
        permission.setPermissionName(name);
        permission.setVisible(visible);
        permission.setEnabled(true);
        permission.setAssignable(assignable);
        permission.setSortOrder(sortOrder);
        return permission;
    }
}
