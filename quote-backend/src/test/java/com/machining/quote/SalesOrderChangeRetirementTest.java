package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOrderChangeRetirementTest {
    private static final Path BACKEND_SOURCE = Path.of("src", "main", "java", "com", "machining", "quote");
    private static final Path FRONTEND_SOURCE = Path.of("..", "quote-frontend", "src");

    @Test
    void oldSalesOrderChangeProductionArtifactsAreAbsent() {
        assertThat(BACKEND_SOURCE.resolve(Path.of("controller", "SalesOrderChangeController.java"))).doesNotExist();
        assertThat(BACKEND_SOURCE.resolve(Path.of("service", "SalesOrderChangeService.java"))).doesNotExist();
        assertThat(FRONTEND_SOURCE.resolve(Path.of("components", "SalesOrderChangeManage.vue"))).doesNotExist();
        assertThat(FRONTEND_SOURCE.resolve(Path.of("services", "salesOrderChangeApi.js"))).doesNotExist();
    }

    @Test
    void currentFrontendAndRuntimePermissionCatalogsExcludeRetiredModule() throws Exception {
        String permissionService = read(BACKEND_SOURCE.resolve(Path.of("service", "PermissionService.java")));
        String router = read(FRONTEND_SOURCE.resolve(Path.of("router", "index.js")));
        String app = read(FRONTEND_SOURCE.resolve(Path.of("App.vue")));

        assertThat(permissionService).doesNotContain("change:sales-order:");
        assertThat(router).doesNotContain("/sales-order-change", "SalesOrderChange");
        assertThat(app).doesNotContain("/sales-order-change", "SalesOrderChange");
    }

    private String read(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
