package com.machining.quote;

import com.machining.quote.service.AuditService;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AssemblyMarkupFeeDictionarySourceTest {
    private static final Path MAIN_JAVA = Path.of("src", "main", "java", "com", "machining", "quote");
    private static final Path MAIN_RESOURCES = Path.of("src", "main", "resources");
    private static final Path DOCS_ROOT = Path.of("..", "docs");

    @Test
    void migrationAndSchemaCreateAssemblyMarkupFeeDictionary() throws IOException {
        String migration = Files.readString(
                MAIN_RESOURCES.resolve("db/migration/V2__qpo_baseline_seed_data.sql"),
                StandardCharsets.UTF_8
        );
        String schema = Files.readString(
                MAIN_RESOURCES.resolve(Path.of("db", "migration", "V1__qpo_baseline_schema.sql")),
                StandardCharsets.UTF_8
        );
        String assemblyFeeSeed = section(
                migration,
                "-- Data for Name: dict_assembly_markup_fee",
                "-- Data for Name: dict_extra_fee_item"
        );

        assertThat(assemblyFeeSeed)
                .contains("INSERT INTO dict_assembly_markup_fee")
                .contains("'管理费'", "'利润'", "'税费'")
                .doesNotContain("'运输费'");
        assertThat(migration).contains("INSERT INTO dict_markup_fee", "'运输费'");
        assertThat(schema).contains("CREATE TABLE dict_assembly_markup_fee");
    }

    @Test
    void dictControllerExposesAssemblyMarkupFeeEndpointsWithExistingFeePermissions() throws IOException {
        String source = source("controller/DictController.java");

        assertThat(source)
                .contains("@GetMapping(\"/assembly-markup-fees\")")
                .contains("@PostMapping(\"/assembly-markup-fees\")")
                .contains("@PutMapping(\"/assembly-markup-fees/{id}\")")
                .contains("@DeleteMapping(\"/assembly-markup-fees/{id}\")")
                .contains("@RequirePermission(\"engineer:dict:view\")")
                .contains("@RequirePermission(\"engineer:dict:fee-manage\")")
                .contains("DICT_ASSEMBLY_MARKUP_FEE_CREATE")
                .contains("DICT_ASSEMBLY_MARKUP_FEE_UPDATE")
                .contains("DICT_ASSEMBLY_MARKUP_FEE_DELETE");
    }

    @Test
    void auditAndDocumentationCoverAssemblyMarkupFeeDictionary() throws IOException {
        assertThat(AuditService.labelForAction("DICT_ASSEMBLY_MARKUP_FEE_CREATE"))
                .isEqualTo("新增组装件百分比费用");
        assertThat(AuditService.labelForAction("DICT_ASSEMBLY_MARKUP_FEE_UPDATE"))
                .isEqualTo("更新组装件百分比费用");
        assertThat(AuditService.labelForAction("DICT_ASSEMBLY_MARKUP_FEE_DELETE"))
                .isEqualTo("删除组装件百分比费用");

        String permissionDoc = Files.readString(
                DOCS_ROOT.resolve("QPO权限用户体系/02-权限点清单与现有角色映射.md"),
                StandardCharsets.UTF_8
        );
        String logDoc = Files.readString(
                DOCS_ROOT.resolve("QPO日志模块/03-日志运维说明.md"),
                StandardCharsets.UTF_8
        );

        assertThat(permissionDoc)
                .contains("组装件百分比费用类")
                .contains("engineer:dict:fee-manage");
        assertThat(logDoc)
                .contains("DICT_ASSEMBLY_MARKUP_FEE_CREATE")
                .contains("组装件百分比费用类");
    }

    private String source(String relativePath) throws IOException {
        return Files.readString(MAIN_JAVA.resolve(relativePath), StandardCharsets.UTF_8);
    }

    private String section(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start + startMarker.length());
        assertThat(start).isGreaterThanOrEqualTo(0);
        assertThat(end).isGreaterThan(start);
        return source.substring(start, end);
    }
}
