package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MesProcessTypeSyncServiceTest {
    private static final Path DESIGN_DOC = Path.of(
            "..", "docs", "superpowers", "specs", "2026-08-13-mes-master-data-sync-design.md");
    private static final Path PROCESS_CATEGORY_ENTITY = Path.of(
            "src", "main", "java", "com", "machining", "quote", "entity", "DictProcessCategory.java");
    private static final Path PROCESS_DETAIL_ENTITY = Path.of(
            "src", "main", "java", "com", "machining", "quote", "entity", "DictProcessDetail.java");
    private static final Path MATERIAL_PROCESS_PAGE = Path.of(
            "..", "quote-frontend", "src", "components", "MaterialProcessManage.vue");

    @Test
    void processSyncSkeletonPreservesProcessCategoryAndDetailTables() throws IOException {
        assertThat(read(DESIGN_DOC))
                .contains(
                        "dict_process_category",
                        "dict_process_detail",
                        "MES 工艺类型进入 `dict_process_category`",
                        "每个 MES 工艺类型至少保证存在一个 `dict_process_detail`",
                        "默认 `detail_name = MES name`",
                        "不删除、不覆盖");

        assertThat(read(PROCESS_CATEGORY_ENTITY))
                .contains(
                        "@TableName(\"dict_process_category\")",
                        "private String processCode;",
                        "private String categoryName;",
                        "private String sourceType;",
                        "private String mesExternalId;",
                        "private LocalDateTime mesSyncedAt;");
        assertThat(read(PROCESS_DETAIL_ENTITY))
                .contains("@TableName(\"dict_process_detail\")", "private Long categoryId;", "private String detailName;");
    }

    @Test
    void processSyncSkeletonKeepsMaterialProcessProcessTabsVisible() throws IOException {
        assertThat(read(MATERIAL_PROCESS_PAGE))
                .contains(
                        "name=\"processCategories\"",
                        "name=\"processDetails\"",
                        "openProcessCategoryDialog",
                        "openProcessDetailDialog",
                        "saveProcessCategory",
                        "saveProcessDetail");
    }

    private static String read(Path path) throws IOException {
        assertThat(path).exists();
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
