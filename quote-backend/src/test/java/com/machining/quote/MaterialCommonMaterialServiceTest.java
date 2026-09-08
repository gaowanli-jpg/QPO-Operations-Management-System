package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.machining.quote.config.RequirePermission;
import com.machining.quote.controller.DictController;
import com.machining.quote.dto.MaterialCommonImportPreviewResp;
import com.machining.quote.dto.MaterialCommonUpdateReq;
import com.machining.quote.dto.MaterialCommonUpdateResp;
import com.machining.quote.dto.MaterialMasterDTO;
import com.machining.quote.dto.PageResult;
import com.machining.quote.entity.DictMaterial;
import com.machining.quote.entity.DictMaterialPriceSpec;
import com.machining.quote.entity.DictMaterialType;
import com.machining.quote.mapper.DictMaterialMapper;
import com.machining.quote.mapper.DictMaterialPriceSpecMapper;
import com.machining.quote.mapper.DictMaterialTypeMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.MaterialMasterService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MaterialCommonMaterialServiceTest {

    @Test
    void migrationAddsCommonMaterialFlagWithIndexedPagingSupport() throws Exception {
        String migration = Files.readString(Path.of("src/main/resources/db/migration/V46__material_common_flag.sql"), StandardCharsets.UTF_8)
                .toLowerCase(Locale.ROOT);

        assertThat(migration)
                .contains("alter table dict_material add column if not exists common_material boolean not null default false")
                .contains("idx_dict_material_common_enabled_type_sort")
                .contains("idx_dict_material_common_enabled_u9_category_sort")
                .doesNotContain("drop table")
                .doesNotContain("v1__qpo_baseline_schema");
    }

    @Test
    @SuppressWarnings("unchecked")
    void pageMaterialsFiltersByCommonScopeMaterialTypeAndU9Category() {
        MaterialMasterService service = service();
        DictMaterialMapper materialMapper = mapper(service, "materialMapper");
        DictMaterialPriceSpecMapper specMapper = mapper(service, "materialPriceSpecMapper");
        DictMaterialTypeMapper typeMapper = mapper(service, "materialTypeMapper");

        DictMaterial material = material(10L, "MAT-001", "6061", 1L, true);
        material.setCommonMaterial(true);
        material.setCategoryName("U9原材料");
        when(materialMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);
        when(materialMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(material));
        when(specMapper.selectList(any())).thenReturn(List.of());
        when(typeMapper.selectBatchIds(any())).thenReturn(List.of(materialType(1L, "MATERIAL", "原材料")));

        PageResult<MaterialMasterDTO> page = service.pageMaterials(true, "6061", 1, 20, "COMMON", "MATERIAL", "U9原材料");

        assertThat(page.getRecords()).singleElement().satisfies(row -> {
            assertThat(row.getMaterialCode()).isEqualTo("MAT-001");
            assertThat(row.getCommonMaterial()).isTrue();
            assertThat(row.getCategoryName()).isEqualTo("U9原材料");
        });
        ArgumentCaptor<QueryWrapper<DictMaterial>> countCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(materialMapper).selectCount(countCaptor.capture());
        String sql = countCaptor.getValue().getCustomSqlSegment().toLowerCase(Locale.ROOT);
        assertThat(sql)
                .contains("enabled", "common_material", "category_name =", "material_type_id")
                .contains("upper(type_code) in ('material')")
                .contains("material_code", "material_name", "remarks");
    }

    @Test
    @SuppressWarnings("unchecked")
    void listMaterialU9CategoryNamesUsesDistinctColumnWithCommonAndTypeFilters() {
        MaterialMasterService service = service();
        DictMaterialMapper materialMapper = mapper(service, "materialMapper");
        when(materialMapper.selectObjs(any(QueryWrapper.class))).thenReturn(List.of("U9原材料", "U9成品"));

        List<String> categories = service.listMaterialU9CategoryNames("COMMON", "MATERIAL");

        assertThat(categories).containsExactly("U9原材料", "U9成品");
        ArgumentCaptor<QueryWrapper<DictMaterial>> queryCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(materialMapper).selectObjs(queryCaptor.capture());
        assertThat(queryCaptor.getValue().getSqlSelect().toLowerCase(Locale.ROOT))
                .contains("distinct category_name");
        assertThat(queryCaptor.getValue().getCustomSqlSegment().toLowerCase(Locale.ROOT))
                .contains("enabled", "common_material", "category_name is not null", "trim(category_name) <> ''")
                .contains("upper(type_code) in ('material')")
                .contains("order by category_name asc");
        verify(materialMapper, org.mockito.Mockito.never()).selectList(any(QueryWrapper.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateMaterialCommonStatusTogglesSelectedMaterialsAndAuditsSummary() {
        MaterialMasterService service = service();
        DictMaterialMapper materialMapper = mapper(service, "materialMapper");
        AuditService auditService = mapper(service, "auditService");
        when(materialMapper.selectBatchIds(List.of(10L, 20L))).thenReturn(List.of(
                material(10L, "MAT-001", "6061", 1L, false),
                material(20L, "MAT-002", "SUS304", 1L, false)
        ));
        when(materialMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(2);

        MaterialCommonUpdateReq req = new MaterialCommonUpdateReq();
        req.setMaterialIds(List.of(10L, 20L));
        req.setCommonMaterial(true);

        MaterialCommonUpdateResp resp = service.updateMaterialCommonStatus(req);

        assertThat(resp.getMatchedCount()).isEqualTo(2);
        assertThat(resp.getUpdatedCount()).isEqualTo(2);
        ArgumentCaptor<UpdateWrapper<DictMaterial>> updateCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(materialMapper).update(isNull(), updateCaptor.capture());
        assertThat(updateCaptor.getValue().getSqlSet().toLowerCase(Locale.ROOT)).contains("common_material");
        assertThat(updateCaptor.getValue().getCustomSqlSegment().toLowerCase(Locale.ROOT)).contains("id in");
        verify(auditService).record(
                org.mockito.ArgumentMatchers.eq("DICT_MATERIAL_UPDATE"),
                org.mockito.ArgumentMatchers.eq("dict_material"),
                org.mockito.ArgumentMatchers.eq("common-material-batch"),
                isNull(),
                org.mockito.ArgumentMatchers.argThat(after -> {
                    assertThat(after).isInstanceOf(Map.class);
                    Map<?, ?> summary = (Map<?, ?>) after;
                    assertThat(summary.get("commonMaterial")).isEqualTo(true);
                    assertThat(summary.get("matchedCount")).isEqualTo(2);
                    assertThat(summary.get("updatedCount")).isEqualTo(2);
                    assertThat(summary.get("materialCodeSamples")).isEqualTo(List.of("MAT-001", "MAT-002"));
                    return true;
                })
        );
    }

    @Test
    void updateMaterialCommonStatusRejectsEmptySelection() {
        MaterialMasterService service = service();

        MaterialCommonUpdateReq req = new MaterialCommonUpdateReq();
        req.setMaterialIds(List.of());
        req.setCommonMaterial(true);

        assertThatThrownBy(() -> service.updateMaterialCommonStatus(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("材料");
    }

    @Test
    @SuppressWarnings("unchecked")
    void previewCommonMaterialImportMatchesByTrimmedUpperMaterialCode() throws Exception {
        MaterialMasterService service = service();
        DictMaterialMapper materialMapper = mapper(service, "materialMapper");
        DictMaterial first = material(10L, "MAT-001", "6061", 1L, true);
        first.setCommonMaterial(false);
        DictMaterial second = material(20L, "MAT-002", "SUS304", 1L, true);
        second.setCommonMaterial(true);
        when(materialMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(first, second));

        MaterialCommonImportPreviewResp preview = service.previewCommonMaterialImport(materialCodeWorkbook(
                "物料号", " mat-001 ", "MAT-002", "mat-001", "MAT-MISSING", ""));

        assertThat(preview.getUploadedCount()).isEqualTo(4);
        assertThat(preview.getMatchedCount()).isEqualTo(2);
        assertThat(preview.getAlreadyCommonCount()).isEqualTo(1);
        assertThat(preview.getNotFoundCount()).isEqualTo(1);
        assertThat(preview.getDuplicateCount()).isEqualTo(1);
        assertThat(preview.getRows()).extracting(MaterialCommonImportPreviewResp.Row::getStatus)
                .containsExactly("MATCHED", "ALREADY_COMMON", "DUPLICATE", "NOT_FOUND");
        assertThat(preview.getMatchedMaterialIds()).containsExactly(10L, 20L);
        ArgumentCaptor<QueryWrapper<DictMaterial>> queryCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(materialMapper).selectList(queryCaptor.capture());
        assertThat(queryCaptor.getValue().getCustomSqlSegment().toLowerCase(Locale.ROOT))
                .contains("upper(trim(material_code)) in");
    }

    @Test
    void previewCommonMaterialImportRequiresMaterialNoHeader() {
        MaterialMasterService service = service();

        assertThatThrownBy(() -> service.previewCommonMaterialImport(materialCodeWorkbook("材料编码", "MAT-001")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("物料号");
    }

    @Test
    void dictControllerExposesCommonMaterialEndpointsWithMaterialManagePermission() throws Exception {
        assertRoute("updateMaterialCommonStatus", new Class<?>[]{MaterialCommonUpdateReq.class}, PutMapping.class,
                "/materials/common-status", "engineer:dict:material-manage");
        assertRoute("previewCommonMaterialImport", new Class<?>[]{org.springframework.web.multipart.MultipartFile.class}, PostMapping.class,
                "/materials/common-status/import-preview", "engineer:dict:material-manage");
        assertRoute("listMaterialU9CategoryNames", new Class<?>[0], GetMapping.class,
                "/materials/u9-category-names", "engineer:dict:view");
    }

    private MaterialMasterService service() {
        MaterialMasterService service = new MaterialMasterService();
        ReflectionTestUtils.setField(service, "materialMapper", mock(DictMaterialMapper.class));
        ReflectionTestUtils.setField(service, "materialPriceSpecMapper", mock(DictMaterialPriceSpecMapper.class));
        ReflectionTestUtils.setField(service, "materialTypeMapper", mock(DictMaterialTypeMapper.class));
        ReflectionTestUtils.setField(service, "auditService", mock(AuditService.class));
        return service;
    }

    @SuppressWarnings("unchecked")
    private <T> T mapper(MaterialMasterService service, String fieldName) {
        return (T) ReflectionTestUtils.getField(service, fieldName);
    }

    private DictMaterial material(Long id, String code, String name, Long typeId, boolean enabled) {
        DictMaterial material = new DictMaterial();
        material.setId(id);
        material.setMaterialCode(code);
        material.setMaterialName(name);
        material.setMaterialTypeId(typeId);
        material.setEnabled(enabled);
        material.setCommonMaterial(false);
        return material;
    }

    private DictMaterialType materialType(Long id, String code, String name) {
        DictMaterialType type = new DictMaterialType();
        type.setId(id);
        type.setTypeCode(code);
        type.setTypeName(name);
        type.setEnabled(true);
        return type;
    }

    private MockMultipartFile materialCodeWorkbook(String header, String... codes) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("materials");
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue(header);
            for (int i = 0; i < codes.length; i++) {
                sheet.createRow(i + 1).createCell(0).setCellValue(codes[i]);
            }
            workbook.write(out);
            return new MockMultipartFile(
                    "file",
                    "materials.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    out.toByteArray());
        }
    }

    private void assertRoute(
            String methodName,
            Class<?>[] parameterTypes,
            Class<?> mappingType,
            String path,
            String permission) throws Exception {
        Method method = DictController.class.getDeclaredMethod(methodName, parameterTypes);
        RequirePermission requirePermission = method.getAnnotation(RequirePermission.class);
        assertThat(requirePermission).as(methodName + " should have RequirePermission").isNotNull();
        assertThat(requirePermission.value()).containsExactly(permission);

        if (mappingType == GetMapping.class) {
            assertThat(method.getAnnotation(GetMapping.class).value()).containsExactly(path);
        } else if (mappingType == PostMapping.class) {
            assertThat(method.getAnnotation(PostMapping.class).value()).containsExactly(path);
        } else if (mappingType == PutMapping.class) {
            assertThat(method.getAnnotation(PutMapping.class).value()).containsExactly(path);
        } else {
            throw new IllegalArgumentException("Unsupported mapping type " + mappingType);
        }
    }
}
