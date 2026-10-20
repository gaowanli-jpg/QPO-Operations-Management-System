package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SystemStorageConfigStage7Test {
    private static final Path CONTROLLER = Path.of("src/main/java/com/machining/quote/controller/SystemStorageConfigController.java");
    private static final Path SERVICE = Path.of("src/main/java/com/machining/quote/service/SystemStorageConfigManageService.java");
    private static final Path DTO = Path.of("src/main/java/com/machining/quote/dto/SystemStorageConfigDTO.java");
    private static final Path SAVE_REQ = Path.of("src/main/java/com/machining/quote/dto/SystemStorageConfigSaveReq.java");
    private static final Path AUDIT_SERVICE = Path.of("src/main/java/com/machining/quote/service/AuditService.java");

    @Test
    void exposesStorageConfigApiWithSystemConfigPermissions() throws Exception {
        assertThat(DTO).exists();
        assertThat(SAVE_REQ).exists();

        String controller = Files.readString(CONTROLLER, StandardCharsets.UTF_8);

        assertThat(controller)
                .contains("@RequestMapping(\"/api/system/storage-configs\")")
                .contains("@GetMapping")
                .contains("@GetMapping(\"/active\")")
                .contains("@PostMapping")
                .contains("@PutMapping(\"/{id}\")")
                .contains("@PostMapping(\"/{id}/test\")")
                .contains("@PutMapping(\"/{id}/activate\")")
                .contains("@PutMapping(\"/{id}/disable\")")
                .contains("@RequirePermission(\"system:config:view\")")
                .contains("@RequirePermission(\"system:config:storage:update\")")
                .contains("@RequirePermission(\"system:config:storage:test\")")
                .contains("@RequirePermission(\"system:config:storage:activate\")");
    }

    @Test
    void serviceKeepsSecretsRedactedAndRecordsStorageAudits() throws Exception {
        String service = Files.readString(SERVICE, StandardCharsets.UTF_8);
        String auditService = Files.readString(AUDIT_SERVICE, StandardCharsets.UTF_8);

        assertThat(service)
                .contains("SYSTEM_STORAGE_CONFIG_CREATE")
                .contains("SYSTEM_STORAGE_CONFIG_UPDATE")
                .contains("SYSTEM_STORAGE_CONFIG_TEST")
                .contains("SYSTEM_STORAGE_CONFIG_TEST_FAILED")
                .contains("SYSTEM_STORAGE_CONFIG_ACTIVATE")
                .contains("SYSTEM_STORAGE_CONFIG_DISABLE")
                .contains("\"******\"")
                .doesNotContain("plainSecret")
                .doesNotContain("secret-value");

        assertThat(auditService)
                .contains("\"accesssecret\"")
                .contains("\"secretkey\"")
                .contains("SYSTEM_STORAGE_CONFIG_CREATE")
                .contains("SYSTEM_STORAGE_CONFIG_ACTIVATE");
    }
}
