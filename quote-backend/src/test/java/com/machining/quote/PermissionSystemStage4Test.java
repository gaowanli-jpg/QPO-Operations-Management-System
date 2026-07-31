package com.machining.quote;

import com.machining.quote.dto.PermissionInfoDTO;
import com.machining.quote.service.PermissionService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionSystemStage4Test {
    @Test
    void fallbackPermissionInfoExposesVisibleMenuTreeForDynamicShell() {
        PermissionService permissionService = new PermissionService();

        PermissionInfoDTO info = permissionService.getPermissionInfo(null, "ADMIN");

        assertThat(info.getMenus())
                .extracting("path")
                .containsExactly(
                        "/market",
                        "/orders",
                        "/sales-match",
                        "/history-orders",
                        "/delivered-items",
                        "/locked-items",
                        "/quote-process",
                        "/release",
                        "/u9-sync",
                        "/delivery-documents",
                        "/quote-template",
                        "/engineering",
                        "/material-process",
                        "/users",
                        "/roles",
                        "/organization",
                        "/customers",
                        "/system-config",
                        "/audit-logs"
                );
    }

    @Test
    void fallbackPermissionInfoReturnsButtonPermissionPoints() {
        PermissionService permissionService = new PermissionService();

        PermissionInfoDTO info = permissionService.getPermissionInfo(null, "ADMIN");

        assertThat(info.getPermissions()).contains(
                "market:rfq:import",
                "market:rfq:upload-drawing",
                "market:sales-match:view",
                "market:sales-match:customer-select",
                "market:sales-match:order-select",
                "market:sales-match:apply",
                "market:quote-process:update-lead-time",
                "market:quote-process:save-quote",
                "market:quote-process:export",
                "market:delivered-item:wms-allocate",
                "market:release:create",
                "market:template:upload",
                "engineer:setup:save-config",
                "engineer:pricing:save-item",
                "engineer:pricing:refresh",
                "engineer:pricing:complete-order",
                "engineer:pricing:export-sheet",
                "engineer:dict:material-manage",
                "engineer:dict:process-manage",
                "engineer:dict:fee-manage",
                "system:user:create",
                "system:user:update",
                "system:user:disable",
                "system:user:reset-password",
                "system:user:delete",
                "system:role:create",
                "system:role:update",
                "system:role:delete",
                "system:role:assign-permission",
                "system:role:assign-data-scope",
                "system:dept:manage",
                "system:post:manage",
                "system:customer:create",
                "system:customer:update",
                "system:customer:delete",
                "system:audit:export",
                "system:audit:retention"
        );
        assertThat(info.getPermissions()).doesNotContain(
                "market:rfq:order-type-select",
                "market:release:reset"
        );
    }
}
