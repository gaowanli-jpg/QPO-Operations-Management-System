package com.machining.quote;

import com.machining.quote.config.RequirePermission;
import com.machining.quote.controller.OrderManagementController;
import com.machining.quote.dto.OrderItemMaintenanceUpdateReq;
import com.machining.quote.service.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OrderItemMaintenanceContractTest {
    private static final String UPDATE_PERMISSION = "market:order:update-item";

    @Test
    void controllerExposesOrderScopedMaintenanceEndpointsWithUpdatePermission() throws Exception {
        assertPostEndpoint(
                OrderManagementController.class.getDeclaredMethod("saveOrderItems", Integer.class, OrderItemMaintenanceUpdateReq.class),
                "/orders/{rfqId}/items/batch",
                UPDATE_PERMISSION);
        assertMultipartEndpoint(
                OrderManagementController.class.getDeclaredMethod("replaceDrawings", Integer.class, java.util.List.class, java.util.List.class),
                "/orders/{rfqId}/drawings/replace",
                UPDATE_PERMISSION);
        assertMultipartEndpoint(
                OrderManagementController.class.getDeclaredMethod("previewItemDataReplacement", Integer.class, org.springframework.web.multipart.MultipartFile.class),
                "/orders/{rfqId}/item-data-replacement/preview",
                UPDATE_PERMISSION);
        assertMultipartEndpoint(
                OrderManagementController.class.getDeclaredMethod("confirmItemDataReplacement", Integer.class, String.class, org.springframework.web.multipart.MultipartFile.class),
                "/orders/{rfqId}/item-data-replacement/confirm",
                UPDATE_PERMISSION);
    }

    @Test
    void auditLabelsAndPermissionDocsTrackOrderItemMaintenance() throws Exception {
        assertThat(AuditService.inferModule("ORDER_ITEM_UPDATE")).isEqualTo("ORDER");
        assertThat(AuditService.labelForAction("ORDER_ITEM_UPDATE")).isEqualTo("保存询价订单零件信息");
        assertThat(AuditService.labelForAction("ORDER_ITEM_DATA_REPLACEMENT")).isEqualTo("询价订单表格数据替换");
        assertThat(AuditService.labelForAction("ORDER_DRAWING_REPLACE")).isEqualTo("询价订单替换图纸");

        String docs = Files.readString(Path.of("..", "docs", "QPO权限用户体系", "02-权限点清单与现有角色映射.md"));
        assertThat(docs).contains("market:order:update-item");
        assertThat(docs).contains("保存询价订单零件信息 / 表格数据替换 / 导入替换图纸");
    }

    @Test
    void migrationAddsOrderItemUpdatePermissionAfterBaseline() throws Exception {
        Path migration = Path.of("src", "main", "resources", "db", "migration", "V31__order_item_update_permission.sql");
        String sql = Files.readString(migration);

        assertThat(sql).contains("market:order:update-item");
        assertThat(sql).contains("MARKET_MANAGER");
        assertThat(sql).doesNotContain("DELETE FROM system_permission");
    }

    private void assertPostEndpoint(Method method, String path, String permissionCode) {
        PostMapping mapping = method.getAnnotation(PostMapping.class);
        RequirePermission permission = method.getAnnotation(RequirePermission.class);

        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly(path);
        assertThat(permission.value()).containsExactly(permissionCode);
    }

    private void assertMultipartEndpoint(Method method, String path, String permissionCode) {
        PostMapping mapping = method.getAnnotation(PostMapping.class);
        RequirePermission permission = method.getAnnotation(RequirePermission.class);

        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly(path);
        assertThat(mapping.consumes()).containsExactly(MediaType.MULTIPART_FORM_DATA_VALUE);
        assertThat(permission.value()).containsExactly(permissionCode);
    }
}
