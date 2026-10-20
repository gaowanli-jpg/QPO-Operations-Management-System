package com.machining.quote;

import com.machining.quote.dto.PermissionInfoDTO;
import com.machining.quote.service.PermissionService;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOrderChangeNoticePermissionTest {
    @Test
    void hideAndRestoreAreProtectedButtonsGrantedOnlyToMarketRoles() throws Exception {
        PermissionService service = new PermissionService();
        PermissionInfoDTO market = service.getPermissionInfo(null, "MARKET");
        PermissionInfoDTO readOnly = service.getPermissionInfo(null, "READONLY");

        assertThat(market.getPermissions()).contains("change:sales-change:hide", "change:sales-change:restore");
        assertThat(readOnly.getPermissions()).doesNotContain("change:sales-change:hide", "change:sales-change:restore");

        String controller = Files.readString(Path.of("src", "main", "java", "com", "machining", "quote", "controller", "SalesOrderChangeNoticeController.java"));
        assertThat(controller)
                .contains("@PostMapping(\"/batch-hide\")", "@RequirePermission(\"change:sales-change:hide\")")
                .contains("@PostMapping(\"/batch-restore\")", "@RequirePermission(\"change:sales-change:restore\")");
    }
}
