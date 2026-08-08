package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CanonicalPriceStage5DocumentationTest {
    private static final Path BACKEND_MAIN = Path.of("src", "main");
    private static final Path FRONTEND_SRC = Path.of("..", "quote-frontend", "src");
    private static final Path DOCS = Path.of("..", "docs");
    private static final List<Path> CURRENT_PRICE_DOCS = List.of(
            Path.of("QPO报价状态流转", "02-零件状态与分批提单不可逆优化开发文档.md"),
            Path.of("QPO报价状态流转", "04-零件核价预览与重复件复用职责拆分开发文档.md"),
            Path.of("QPO报价状态流转", "05-重复件匹配仅使用已提单历史零件开发文档.md"),
            Path.of("QPO报价状态流转", "10-销售订单直提流程方案讨论文档.md"),
            Path.of("QPO报价状态流转", "13-销售订单导入进入QPO主流程开发文档.md"),
            Path.of("QPO报价状态流转", "14-销售订单独立主表开发文档.md"),
            Path.of("QPO报价状态流转", "15-销售匹配模块开发文档.md"),
            Path.of("QPO报价状态流转", "16-销售订单U9同步模块开发文档.md"),
            Path.of("QPO报价状态流转", "26-销售匹配材料字段口径重构开发文档.md"),
            Path.of("QPO报价状态流转", "33-销售订单导入即提单与异常治理重构开发文档.md"));

    @Test
    void currentRuntimeAndSchemaDoNotReferenceLegacyPriceColumns() throws IOException {
        String legacySnakeUnit = "final_" + "unit_price";
        String legacySnakeTotal = "final_" + "total_price";
        String legacyCamelUnit = "final" + "UnitPrice";
        String legacyCamelTotal = "final" + "TotalPrice";

        assertThat(read(Path.of("src", "main", "resources", "db", "migration", "V1__qpo_baseline_schema.sql")))
                .doesNotContain(legacySnakeUnit, legacySnakeTotal);
        assertThat(filesUnder(BACKEND_MAIN.resolve("java")))
                .allSatisfy(source -> assertThat(source)
                        .doesNotContain(legacySnakeUnit, legacySnakeTotal,
                                legacyCamelUnit, legacyCamelTotal));
        assertThat(filesUnder(BACKEND_MAIN.resolve("resources"), path ->
                !path.toString().contains("db" + java.io.File.separator + "migration")))
                .allSatisfy(source -> assertThat(source)
                        .doesNotContain(legacySnakeUnit, legacySnakeTotal));
        assertThat(filesUnder(FRONTEND_SRC))
                .allSatisfy(source -> assertThat(source)
                        .doesNotContain(legacySnakeUnit, legacySnakeTotal,
                                legacyCamelUnit, legacyCamelTotal));
    }

    @Test
    void currentBusinessDocsUseCanonicalPriceVocabulary() throws IOException {
        String legacySnakeUnit = "final_" + "unit_price";
        String legacySnakeTotal = "final_" + "total_price";
        String legacyCamelUnit = "final" + "UnitPrice";
        String legacyCamelTotal = "final" + "TotalPrice";
        for (Path relative : CURRENT_PRICE_DOCS) {
            String source = read(DOCS.resolve(relative));
            assertThat(source)
                    .contains("tax_included_unit_price")
                    .doesNotContain(legacySnakeUnit, legacySnakeTotal,
                            legacyCamelUnit, legacyCamelTotal);
        }
    }

    @Test
    void permissionsAndLoggingDocsDeclareStableGovernance() throws IOException {
        String permissions = read(DOCS.resolve(Path.of(
                "QPO权限用户体系", "02-权限点清单与现有角色映射.md")));
        String adminGuide = read(DOCS.resolve(Path.of(
                "QPO权限用户体系", "03-权限体系部署与管理员使用说明.md")));
        String operations = read(DOCS.resolve(Path.of(
                "QPO日志模块", "03-日志运维说明.md")));
        String loggingConvention = read(DOCS.resolve(Path.of(
                "QPO日志模块", "04-主流程文件日志规范化开发文档.md")));

        assertThat(permissions).contains("不新增用户权限", "权限 code", "数据范围");
        assertThat(adminGuide).contains("不新增用户权限", "权限 code", "数据范围");
        assertThat(operations).contains("taxIncludedUnitPrice", "不写操作日志");
        assertThat(loggingConvention).contains("taxIncludedUnitPrice", "普通列表、详情、预览");
    }

    private static List<String> filesUnder(Path root) throws IOException {
        return filesUnder(root, path -> true);
    }

    private static List<String> filesUnder(Path root, java.util.function.Predicate<Path> filter)
            throws IOException {
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(filter)
                    .map(CanonicalPriceStage5DocumentationTest::readUnchecked)
                    .toList();
        }
    }

    private static String readUnchecked(Path path) {
        try {
            return read(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read " + path, exception);
        }
    }

    private static String read(Path path) throws IOException {
        assertThat(path).exists();
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
