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
    private static final Path PRICING_MARKUP_DOC = Path.of(
            "..", "docs", "QPO报价状态流转", "49-百分比费用分类与乘法加权价格公式开发文档.md");

    @Test
    void migrationCreatesUnifiedPricingMarkupFeeDictionaryAndRetiresOldTables() throws IOException {
        String migration = Files.readString(
                MAIN_RESOURCES.resolve("db/migration/V23__pricing_markup_fee_category_scope.sql"),
                StandardCharsets.UTF_8
        );

        assertThat(migration)
                .contains("CREATE TABLE dict_pricing_markup_fee")
                .contains("pricing_scope")
                .contains("fee_category")
                .contains("system_required")
                .contains("FROM dict_markup_fee")
                .contains("FROM dict_assembly_markup_fee")
                .contains("DROP TABLE IF EXISTS dict_markup_fee")
                .contains("DROP TABLE IF EXISTS dict_assembly_markup_fee");
    }

    @Test
    void dictControllerExposesUnifiedPricingMarkupFeeEndpointsOnly() throws IOException {
        String source = source("controller/DictController.java");

        assertThat(source)
                .contains("@GetMapping(\"/pricing-markup-fees\")")
                .contains("@PostMapping(\"/pricing-markup-fees\")")
                .contains("@PutMapping(\"/pricing-markup-fees/{id}\")")
                .contains("@DeleteMapping(\"/pricing-markup-fees/{id}\")")
                .contains("@RequirePermission(\"engineer:dict:view\")")
                .contains("@RequirePermission(\"engineer:dict:fee-manage\")")
                .doesNotContain("/markup-fees")
                .doesNotContain("/assembly-markup-fees")
                .doesNotContain("DICT_MARKUP_FEE_CREATE")
                .doesNotContain("DICT_ASSEMBLY_MARKUP_FEE_CREATE");
    }

    @Test
    void auditAndDevelopmentDocCoverUnifiedPricingMarkupFeeDictionary() throws IOException {
        assertThat(AuditService.labelForAction("DICT_PRICING_MARKUP_FEE_CREATE"))
                .contains("pricing markup fee");
        assertThat(AuditService.labelForAction("DICT_PRICING_MARKUP_FEE_UPDATE"))
                .contains("pricing markup fee");
        assertThat(AuditService.labelForAction("DICT_PRICING_MARKUP_FEE_DELETE"))
                .contains("pricing markup fee");

        String doc = Files.readString(PRICING_MARKUP_DOC, StandardCharsets.UTF_8);
        assertThat(doc)
                .contains("dict_pricing_markup_fee")
                .contains("pricingScope = NORMAL / ASSEMBLY")
                .contains("feeCategory")
                .contains("engineer:dict:fee-manage");
    }

    private String source(String relativePath) throws IOException {
        return Files.readString(MAIN_JAVA.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
