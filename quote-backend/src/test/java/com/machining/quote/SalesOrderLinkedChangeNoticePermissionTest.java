package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOrderLinkedChangeNoticePermissionTest {
    private static final Path ROOT = Path.of("src", "main", "java");

    @Test
    void linkedChangeControllerUsesIndependentApiPathAndPermissionGuards() throws Exception {
        String source = Files.readString(ROOT.resolve(Path.of(
                "com", "machining", "quote", "controller", "LinkedChangeController.java")),
                StandardCharsets.UTF_8);

        assertThat(source)
                .contains("@RequestMapping(\"/api/change/linked-changes\")")
                .contains("@GetMapping")
                .contains("@GetMapping(\"/customers\")")
                .contains("@GetMapping(\"/{id}\")")
                .contains("@PostMapping(\"/{id}/handle\")")
                .contains("@PostMapping(\"/{id}/reopen\")")
                .contains("@RequirePermission(\"change:linked-change:view\")")
                .contains("@RequirePermission(\"change:linked-change:customer-select\")")
                .contains("@RequirePermission(\"change:linked-change:handle\")");
        assertThat(Files.readString(ROOT.resolve(Path.of(
                "com", "machining", "quote", "controller", "SalesOrderChangeController.java")),
                StandardCharsets.UTF_8))
                .doesNotContain("linked-changes");
    }

    @Test
    void linkedChangeServiceAppliesSalesOrderScopeAndSupportsBothImpactModes() throws Exception {
        String source = Files.readString(ROOT.resolve(Path.of(
                "com", "machining", "quote", "service", "SalesOrderLinkedChangeNoticeService.java")),
                StandardCharsets.UTF_8);

        assertThat(source)
                .contains("applySalesOrderIdScope(query, \"sales_order_id\")")
                .contains("Boolean.TRUE.equals(req.getHasDownstreamImpact())")
                .contains("Boolean.FALSE.equals(req.getHasDownstreamImpact())")
                .contains("query.eq(\"downstream_impact_count\", 0)")
                .contains("handling_status")
                .contains("getHandleRemark")
                .contains("handle_remark");
    }
}
