package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DrawingFolderBindingMigrationTest {
    private static final Path MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration", "V49__drawing_folder_binding.sql");
    private static final Path BASELINE_SCHEMA = Path.of(
            "src", "main", "resources", "db", "migration", "V1__qpo_baseline_schema.sql");
    private static final Path ORDER_ENTITY = Path.of(
            "src", "main", "java", "com", "machining", "quote", "entity", "OrderRfq.java");
    private static final Path SALES_ORDER_ENTITY = Path.of(
            "src", "main", "java", "com", "machining", "quote", "entity", "SalesOrderHeader.java");

    @Test
    void migrationAddsNullableDrawingFolderFieldsWithoutEditingBaselineSchema() throws IOException {
        String migration = read(MIGRATION);
        String baseline = read(BASELINE_SCHEMA);

        assertThat(migration)
                .contains("ALTER TABLE order_rfq")
                .contains("ADD COLUMN IF NOT EXISTS drawing_folder_url")
                .contains("ADD COLUMN IF NOT EXISTS drawing_folder_name")
                .contains("ADD COLUMN IF NOT EXISTS drawing_folder_file_count")
                .contains("ALTER TABLE sales_order_header");
        assertThat(baseline)
                .doesNotContain("drawing_folder_url")
                .doesNotContain("drawing_folder_name")
                .doesNotContain("drawing_folder_file_count");
    }

    @Test
    void entitiesExposeDrawingFolderMetadataOnOrderHeaders() throws IOException {
        assertThat(read(ORDER_ENTITY))
                .contains("private String drawingFolderUrl;")
                .contains("private String drawingFolderName;")
                .contains("private Integer drawingFolderFileCount;");
        assertThat(read(SALES_ORDER_ENTITY))
                .contains("private String drawingFolderUrl;")
                .contains("private String drawingFolderName;")
                .contains("private Integer drawingFolderFileCount;");
    }

    private String read(Path path) throws IOException {
        assertThat(path).exists();
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
