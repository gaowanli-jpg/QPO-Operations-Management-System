package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MesMaterialMasterSyncServiceTest {
    private static final Path DESIGN_DOC = Path.of(
            "..", "docs", "superpowers", "specs", "2026-08-13-mes-master-data-sync-design.md");
    private static final Path MATERIAL_ENTITY = Path.of(
            "src", "main", "java", "com", "machining", "quote", "entity", "DictMaterial.java");
    private static final Path MATERIAL_TYPE_ENTITY = Path.of(
            "src", "main", "java", "com", "machining", "quote", "entity", "DictMaterialType.java");
    private static final Path MATERIAL_PROCESS_PAGE = Path.of(
            "..", "quote-frontend", "src", "components", "MaterialProcessManage.vue");
    private static final Path DISPATCH_SERVICE = Path.of(
            "src", "main", "java", "com", "machining", "quote", "service", "mes", "MesProductionDispatchService.java");

    @Test
    void materialSyncSkeletonPreservesMaterialAndMaterialTypeTables() throws IOException {
        assertThat(read(DESIGN_DOC))
                .contains(
                        "dict_material",
                        "dict_material_type",
                        "MATERIAL / PRODUCT / SEMI",
                        "不按 `materialType` 过滤",
                        "MES 同步不得覆盖已有单价");

        assertThat(read(MATERIAL_ENTITY))
                .contains("@TableName(\"dict_material\")", "private String materialCode;", "private Long materialTypeId;");
        assertThat(read(MATERIAL_TYPE_ENTITY))
                .contains("@TableName(\"dict_material_type\")", "private String typeCode;", "private String typeName;");
    }

    @Test
    void materialSyncSkeletonKeepsMaterialTabsAndQpoPriceOwnershipVisible() throws IOException {
        assertThat(read(MATERIAL_PROCESS_PAGE))
                .contains(
                        "name=\"materials\"",
                        "name=\"materialTypes\"",
                        "openMaterialDialog",
                        "openMaterialTypeDialog",
                        "saveMaterial",
                        "saveMaterialType");

        assertThat(read(DESIGN_DOC))
                .contains(
                        "platePrice",
                        "barPrice",
                        "tubePrice",
                        "材料单价缺失或为 0 时，后端禁止完成核价",
                        "阶段 6：材料单价缺失禁止完成核价");
    }

    @Test
    void materialSyncSkeletonKeepsMesBomContractOnMaterialCodeAndGrossWeight() throws IOException {
        assertThat(read(DISPATCH_SERVICE))
                .contains(
                        "allocation.getEngineeringMaterialCode()",
                        "allocation.getMaterialGrossWeight()",
                        "new MesProductionBomDTO(materialCode, BigDecimal.valueOf(grossWeightKg))");
    }

    private static String read(Path path) throws IOException {
        assertThat(path).exists();
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
