package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class MaterialMasterStage2BaselineTest {
    private static final Path SCHEMA = Path.of("src", "main", "resources", "db", "migration", "V1__qpo_baseline_schema.sql");
    private static final Path SEED = Path.of("src", "main", "resources", "db", "migration", "V2__qpo_baseline_seed_data.sql");
    private static final List<String> SNAPSHOT_TABLES = List.of(
            "item_quote_version",
            "sales_order_item",
            "order_delivery_ticket",
            "order_delivery_ticket_allocation");

    @Test
    void baselineDefinesMaterialTypeDictionaryAndMaterialCodeIdentity() throws IOException {
        String schema = read(SCHEMA);

        assertThat(schema)
                .contains(
                        "CREATE TABLE dict_material_type (",
                        "type_code character varying(64) NOT NULL",
                        "type_name character varying(120) NOT NULL",
                        "enabled boolean DEFAULT true NOT NULL",
                        "sort_no integer DEFAULT 0 NOT NULL",
                        "ALTER TABLE ONLY dict_material_type ALTER COLUMN id SET DEFAULT nextval('dict_material_type_id_seq'::regclass)",
                        "ADD CONSTRAINT dict_material_type_pkey PRIMARY KEY (id)",
                        "CREATE UNIQUE INDEX uk_dict_material_type_code ON dict_material_type USING btree (type_code)",
                        "CREATE UNIQUE INDEX uk_dict_material_type_name ON dict_material_type USING btree (type_name)",
                        "CREATE INDEX idx_dict_material_type_enabled_sort ON dict_material_type USING btree (enabled, sort_no, id)");
        assertThat(schema)
                .contains(
                        "CREATE TABLE dict_material (",
                        "material_code character varying(80) NOT NULL",
                        "material_type_id bigint NOT NULL",
                        "material_category character varying(80)",
                        "enabled boolean DEFAULT true NOT NULL",
                        "sort_no integer DEFAULT 0 NOT NULL",
                        "created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL",
                        "updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL",
                        "CREATE UNIQUE INDEX uk_dict_material_code_upper ON dict_material USING btree (upper((material_code)::text))",
                        "CREATE INDEX idx_dict_material_material_type_enabled_sort ON dict_material USING btree (material_type_id, enabled, sort_no, id)",
                        "CREATE INDEX idx_dict_material_name ON dict_material USING btree (material_name, id)",
                        "CREATE INDEX idx_dict_material_category ON dict_material USING btree (material_category, id)",
                        "ADD CONSTRAINT fk_dict_material_type FOREIGN KEY (material_type_id) REFERENCES dict_material_type(id) ON DELETE RESTRICT")
                .doesNotContain("material_type character varying(50)", "idx_dict_material_name_remarks");
    }

    @Test
    void baselineAddsEngineeringMaterialSnapshotFieldsToAllBusinessSnapshotTables() throws IOException {
        String schema = read(SCHEMA);

        for (String table : SNAPSHOT_TABLES) {
            String createTable = createTableBlock(schema, table);
            assertThat(createTable)
                    .as(table + " should carry full engineering material identity snapshot")
                    .contains(
                            "engineering_material_code character varying(80)",
                            "engineering_material_type_code character varying(64)",
                            "engineering_material_type_name character varying(120)",
                            "engineering_material_category character varying(80)");
        }
    }

    @Test
    void seedDataCreatesRawMaterialTypeAndAssignsCodesToEveryMaterial() throws IOException {
        String seed = read(SEED);

        assertThat(seed)
                .contains("INSERT INTO dict_material_type VALUES")
                .contains("RAW_MATERIAL")
                .contains("SELECT pg_catalog.setval('dict_material_type_id_seq', 1, true)");

        List<String> materialRows = seed.lines()
                .filter(line -> line.startsWith("INSERT INTO dict_material VALUES"))
                .toList();
        assertThat(materialRows).hasSizeGreaterThan(40);
        assertThat(materialRows)
                .allSatisfy(row -> assertThat(row)
                        .contains("'MAT-")
                        .doesNotContain("material_type,"));
        assertThat(materialRows)
                .anySatisfy(row -> assertThat(row).contains("INSERT INTO dict_material VALUES (5, 'MAT-000005', '45#', 1,"))
                .anySatisfy(row -> assertThat(row).contains("INSERT INTO dict_material VALUES (38, 'MAT-000038', 'A5052', 1,"));
    }

    private String createTableBlock(String schema, String table) {
        Pattern pattern = Pattern.compile("CREATE TABLE " + Pattern.quote(table) + " \\((.*?)\\n\\);", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(schema);
        assertThat(matcher.find()).as("CREATE TABLE %s should exist", table).isTrue();
        return matcher.group(1);
    }

    private String read(Path path) throws IOException {
        assertThat(path).exists();
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
