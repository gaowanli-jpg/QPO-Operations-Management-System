package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.config.RequirePermission;
import com.machining.quote.controller.DictController;
import com.machining.quote.dto.MaterialMasterDTO;
import com.machining.quote.dto.MaterialMasterSaveReq;
import com.machining.quote.dto.MaterialPriceSpecDTO;
import com.machining.quote.dto.MaterialTypeDTO;
import com.machining.quote.dto.MaterialTypeSaveReq;
import com.machining.quote.dto.PageResult;
import com.machining.quote.entity.DictMaterial;
import com.machining.quote.entity.DictMaterialPriceSpec;
import com.machining.quote.entity.DictMaterialType;
import com.machining.quote.mapper.DictMaterialMapper;
import com.machining.quote.mapper.DictMaterialPriceSpecMapper;
import com.machining.quote.mapper.DictMaterialTypeMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.MaterialMasterService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MaterialMasterBackendServiceTest {

    @Test
    void listMaterialsReturnsMaterialTypeFieldsWithoutAuditLog() {
        MaterialMasterService service = service();
        DictMaterialMapper materialMapper = mapper(service, "materialMapper");
        DictMaterialPriceSpecMapper specMapper = mapper(service, "materialPriceSpecMapper");
        DictMaterialTypeMapper materialTypeMapper = mapper(service, "materialTypeMapper");
        AuditService auditService = mapper(service, "auditService");

        DictMaterial material = material(10L, "MAT-000010", "SUS304", 1L, true);
        material.setMaterialCategory("不锈钢");
        material.setSortNo(10);
        LocalDateTime syncedAt = LocalDateTime.of(2026, 8, 13, 12, 30);
        material.setSourceType("MES");
        material.setMesExternalId("mes-mat-10");
        material.setMesMaterialType("MATERIAL");
        material.setMesPurchaseType("BUY");
        material.setMesProductionType("OUTSOURCE");
        material.setMesDocNum("DOC-10");
        material.setMesStandard("GB/T 1220");
        material.setMesWeight("12.34");
        material.setMesDensityRaw("7.93");
        material.setDensity(7.85);
        material.setMesSyncedAt(syncedAt);
        DictMaterialType type = materialType(1L, "RAW_MATERIAL", "原材料", true);
        when(materialMapper.selectList(any())).thenReturn(List.of(material));
        when(specMapper.selectList(any())).thenReturn(List.of(
                spec(100L, 10L, "SPEC_1", "规格一", 12.0, null, 13.0, null, 10),
                spec(101L, 10L, "SPEC_2", "规格二", null, null, null, null, 20)));
        when(materialTypeMapper.selectBatchIds(any())).thenReturn(List.of(type));

        List<MaterialMasterDTO> rows = service.listMaterials(null);

        assertThat(rows).hasSize(1);
        MaterialMasterDTO row = rows.get(0);
        assertThat(row.getMaterialCode()).isEqualTo("MAT-000010");
        assertThat(row.getMaterialName()).isEqualTo("SUS304");
        assertThat(row.getMaterialTypeCode()).isEqualTo("RAW_MATERIAL");
        assertThat(row.getMaterialTypeName()).isEqualTo("原材料");
        assertThat(row.getMaterialCategory()).isEqualTo("不锈钢");
        assertThat(row.getSourceType()).isEqualTo("MES");
        assertThat(row.getMesExternalId()).isEqualTo("mes-mat-10");
        assertThat(row.getMesMaterialType()).isEqualTo("MATERIAL");
        assertThat(row.getMesPurchaseType()).isEqualTo("BUY");
        assertThat(row.getMesProductionType()).isEqualTo("OUTSOURCE");
        assertThat(row.getMesDocNum()).isEqualTo("DOC-10");
        assertThat(row.getMesStandard()).isEqualTo("GB/T 1220");
        assertThat(row.getMesWeight()).isEqualTo("12.34");
        assertThat(row.getMesDensityRaw()).isEqualTo("7.93");
        assertThat(row.getDensity()).isEqualTo(7.85);
        assertThat(row.getPriceSpecs()).hasSize(3);
        assertThat(row.getPriceSpecs()).extracting(MaterialPriceSpecDTO::getSpecCode)
                .containsExactly("SPEC_1", "SPEC_2", "SPEC_3");
        assertThat(row.getPriceSpecs().get(0)).satisfies(priceSpec -> {
            assertThat(priceSpec.getUnifiedPrice()).isEqualTo(12.0);
            assertThat(priceSpec.getPlatePrice()).isNull();
            assertThat(priceSpec.getEffectivePlatePrice()).isEqualTo(12.0);
            assertThat(priceSpec.getBarPrice()).isEqualTo(13.0);
            assertThat(priceSpec.getEffectiveBarPrice()).isEqualTo(13.0);
            assertThat(priceSpec.getEffectiveTubePrice()).isEqualTo(12.0);
        });
        assertThat(row.getMesSyncedAt()).isEqualTo(syncedAt);
        verifyNoInteractions(auditService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void pageMaterialsSupportsServerSidePaginationAndKeywordSearch() throws Exception {
        MaterialMasterService service = service();
        DictMaterialMapper materialMapper = mapper(service, "materialMapper");
        DictMaterialPriceSpecMapper specMapper = mapper(service, "materialPriceSpecMapper");
        DictMaterialTypeMapper materialTypeMapper = mapper(service, "materialTypeMapper");
        AuditService auditService = mapper(service, "auditService");

        DictMaterial material = material(10L, "MAT-000010", "SUS304", 1L, true);
        DictMaterialType type = materialType(1L, "RAW_MATERIAL", "原材料", true);
        when(materialMapper.selectCount(any(QueryWrapper.class))).thenReturn(25L);
        when(materialMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(material));
        when(specMapper.selectList(any())).thenReturn(List.of(spec(100L, 10L, "SPEC_1", "规格一", 12.0, null, null, null, 10)));
        when(materialTypeMapper.selectBatchIds(any())).thenReturn(List.of(type));

        Method pageMaterials = MaterialMasterService.class.getMethod(
                "pageMaterials", Boolean.class, String.class, Integer.class, Integer.class);
        PageResult<MaterialMasterDTO> page = (PageResult<MaterialMasterDTO>) pageMaterials.invoke(
                service, true, "raw", 2, 20);

        assertThat(page.getTotal()).isEqualTo(25);
        assertThat(page.getPage()).isEqualTo(2);
        assertThat(page.getPageSize()).isEqualTo(20);
        assertThat(page.getRecords()).singleElement().satisfies(row -> {
            assertThat(row.getMaterialCode()).isEqualTo("MAT-000010");
            assertThat(row.getMaterialTypeCode()).isEqualTo("RAW_MATERIAL");
            assertThat(row.getMaterialTypeName()).isEqualTo("原材料");
            assertThat(row.getPriceSpecs()).hasSize(3);
        });
        verifyNoInteractions(auditService);
    }

    @Test
    void createMaterialRejectsDuplicateCodeCaseInsensitively() {
        MaterialMasterService service = service();
        DictMaterialMapper materialMapper = mapper(service, "materialMapper");
        DictMaterialTypeMapper materialTypeMapper = mapper(service, "materialTypeMapper");
        when(materialTypeMapper.selectById(1L)).thenReturn(materialType(1L, "RAW_MATERIAL", "原材料", true));
        when(materialMapper.selectCount(any())).thenReturn(1L);

        MaterialMasterSaveReq req = materialReq(" mat-001 ", "45#", 1L);

        assertThatThrownBy(() -> service.saveMaterial(null, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("材料编码已存在");
    }

    @Test
    void createMaterialRejectsDisabledMaterialType() {
        MaterialMasterService service = service();
        DictMaterialTypeMapper materialTypeMapper = mapper(service, "materialTypeMapper");
        when(materialTypeMapper.selectById(2L)).thenReturn(materialType(2L, "OUTSOURCED", "外购件", false));

        MaterialMasterSaveReq req = materialReq("MAT-OUT-001", "外购件材料", 2L);

        assertThatThrownBy(() -> service.saveMaterial(null, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("材料类型已停用");
    }

    @Test
    void createMaterialNormalizesDefaultsAndAuditsConciseSummary() {
        MaterialMasterService service = service();
        DictMaterialMapper materialMapper = mapper(service, "materialMapper");
        DictMaterialPriceSpecMapper specMapper = mapper(service, "materialPriceSpecMapper");
        DictMaterialTypeMapper materialTypeMapper = mapper(service, "materialTypeMapper");
        AuditService auditService = mapper(service, "auditService");
        when(materialTypeMapper.selectById(1L)).thenReturn(materialType(1L, "RAW_MATERIAL", "原材料", true));
        when(materialMapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> {
            DictMaterial inserted = invocation.getArgument(0);
            inserted.setId(99L);
            return 1;
        }).when(materialMapper).insert(any(DictMaterial.class));

        MaterialMasterSaveReq req = materialReq(" mat-keep-case ", " 45# ", 1L);
        req.setDensity(7.85);
        req.setPriceSpecs(List.of(specReq("SPEC_1", "规格一", 12.0, null, 13.0, null)));
        req.setEnabled(null);
        req.setSortNo(null);

        MaterialMasterDTO saved = service.saveMaterial(null, req);

        assertThat(saved.getId()).isEqualTo(99L);
        assertThat(saved.getMaterialCode()).isEqualTo("mat-keep-case");
        assertThat(saved.getMaterialName()).isEqualTo("45#");
        assertThat(saved.getMaterialTypeCode()).isEqualTo("RAW_MATERIAL");
        assertThat(saved.getEnabled()).isTrue();
        assertThat(saved.getSourceType()).isEqualTo("LOCAL");
        assertThat(saved.getMesExternalId()).isNull();
        assertThat(saved.getMesMaterialType()).isNull();
        assertThat(saved.getMesSyncedAt()).isNull();
        assertThat(saved.getPriceSpecs()).hasSize(3);
        assertThat(saved.getPriceSpecs().get(0)).satisfies(priceSpec -> {
            assertThat(priceSpec.getSpecCode()).isEqualTo("SPEC_1");
            assertThat(priceSpec.getUnifiedPrice()).isEqualTo(12.0);
            assertThat(priceSpec.getBarPrice()).isEqualTo(13.0);
            assertThat(priceSpec.getEffectivePlatePrice()).isEqualTo(12.0);
        });
        verify(materialMapper).insert(any(DictMaterial.class));
        verify(specMapper).delete(any(QueryWrapper.class));
        verify(specMapper, times(3)).insert(any(DictMaterialPriceSpec.class));
        verify(auditService).record(
                org.mockito.ArgumentMatchers.eq("DICT_MATERIAL_CREATE"),
                org.mockito.ArgumentMatchers.eq("dict_material"),
                org.mockito.ArgumentMatchers.eq(99L),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.argThat(after -> {
                    assertThat(after).isInstanceOf(Map.class);
                    Map<?, ?> summary = (Map<?, ?>) after;
                    assertThat(summary.get("materialCode")).isEqualTo("mat-keep-case");
                    assertThat(summary.get("materialTypeCode")).isEqualTo("RAW_MATERIAL");
                    assertThat(summary.get("changedSpecCodes")).isEqualTo(List.of("SPEC_1"));
                    assertThat(summary.containsKey("platePrice")).isFalse();
                    assertThat(summary.containsKey("barPrice")).isFalse();
                    assertThat(summary.containsKey("tubePrice")).isFalse();
                    return true;
                })
        );
    }

    @Test
    void mesSourcedMaterialKeepsMesIdentityButAllowsQpoPriceAndDensityMaintenance() {
        MaterialMasterService service = service();
        DictMaterialMapper materialMapper = mapper(service, "materialMapper");
        DictMaterialTypeMapper materialTypeMapper = mapper(service, "materialTypeMapper");
        when(materialTypeMapper.selectById(1L)).thenReturn(materialType(1L, "MATERIAL", "MES raw", true));
        when(materialTypeMapper.selectById(2L)).thenReturn(materialType(2L, "SEMI", "MES semi", true));

        DictMaterial before = material(10L, "MES-MAT-001", "MES 45#", 1L, true);
        before.setMaterialCategory("steel");
        before.setSourceType("MES");
        before.setMesExternalId("mes-001");
        when(materialMapper.selectById(10L)).thenReturn(before);

        MaterialMasterSaveReq changedIdentity = materialReq("LOCAL-MAT", "Local 45#", 2L);
        changedIdentity.setMaterialCategory("aluminum");
        assertThatThrownBy(() -> service.saveMaterial(10L, changedIdentity))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MES material identity");
        verify(materialMapper, never()).updateById(any(DictMaterial.class));

        MaterialMasterSaveReq localMaintenance = materialReq("MES-MAT-001", "MES 45#", 1L);
        localMaintenance.setMaterialCategory("steel");
        localMaintenance.setDensity(7.9);
        localMaintenance.setPriceSpecs(List.of(specReq("SPEC_2", "规格二", 8.8, 8.9, 8.6, 8.4)));
        localMaintenance.setRemarks("QPO local price and density");

        MaterialMasterDTO saved = service.saveMaterial(10L, localMaintenance);

        assertThat(saved.getMaterialCode()).isEqualTo("MES-MAT-001");
        assertThat(saved.getMaterialName()).isEqualTo("MES 45#");
        assertThat(saved.getMaterialTypeId()).isEqualTo(1L);
        assertThat(saved.getMaterialCategory()).isEqualTo("steel");
        assertThat(saved.getDensity()).isEqualTo(7.9);
        assertThat(saved.getPriceSpecs()).hasSize(3);
        assertThat(saved.getPriceSpecs()).filteredOn(spec -> "SPEC_2".equals(spec.getSpecCode()))
                .singleElement()
                .satisfies(spec -> {
                    assertThat(spec.getUnifiedPrice()).isEqualTo(8.8);
                    assertThat(spec.getPlatePrice()).isEqualTo(8.9);
                    assertThat(spec.getBarPrice()).isEqualTo(8.6);
                    assertThat(spec.getTubePrice()).isEqualTo(8.4);
                });
        assertThat(saved.getSourceType()).isEqualTo("MES");
        assertThat(saved.getMesExternalId()).isEqualTo("mes-001");
    }

    @Test
    void deleteMesSourcedMaterialIsRejectedBecauseMesOwnsMaterialIdentity() {
        MaterialMasterService service = service();
        DictMaterialMapper materialMapper = mapper(service, "materialMapper");
        DictMaterial before = material(10L, "MES-MAT-001", "MES 45#", 1L, true);
        before.setSourceType("MES");
        when(materialMapper.selectById(10L)).thenReturn(before);

        assertThatThrownBy(() -> service.deleteMaterial(10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MES material");
        verify(materialMapper, never()).updateById(any(DictMaterial.class));
    }

    @Test
    void deleteReferencedMaterialTypeDisablesInsteadOfHardDelete() {
        MaterialMasterService service = service();
        DictMaterialMapper materialMapper = mapper(service, "materialMapper");
        DictMaterialTypeMapper materialTypeMapper = mapper(service, "materialTypeMapper");
        AuditService auditService = mapper(service, "auditService");
        when(materialTypeMapper.selectById(1L)).thenReturn(materialType(1L, "RAW_MATERIAL", "原材料", true));
        when(materialMapper.selectCount(any())).thenReturn(2L);

        service.deleteMaterialType(1L);

        verify(materialTypeMapper).updateById(org.mockito.ArgumentMatchers.argThat(type ->
                type.getId().equals(1L) && Boolean.FALSE.equals(type.getEnabled())));
        verify(materialTypeMapper, never()).deleteById(1L);
        verify(auditService).record(
                org.mockito.ArgumentMatchers.eq("DICT_MATERIAL_TYPE_DISABLE"),
                org.mockito.ArgumentMatchers.eq("dict_material_type"),
                org.mockito.ArgumentMatchers.eq(1L),
                any(),
                any()
        );
    }

    @Test
    void dictControllerExposesMaterialTypeAndMaterialCrudWithExpectedPermissions() throws Exception {
        assertRoute("listMaterialTypes", new Class<?>[]{Boolean.class}, GetMapping.class, "/material-types", "engineer:dict:view");
        assertRoute("createMaterialType", new Class<?>[]{MaterialTypeSaveReq.class}, PostMapping.class, "/material-types", "engineer:dict:material-manage");
        assertRoute("updateMaterialType", new Class<?>[]{Long.class, MaterialTypeSaveReq.class}, PutMapping.class, "/material-types/{id}", "engineer:dict:material-manage");
        assertRoute("deleteMaterialType", new Class<?>[]{Long.class}, DeleteMapping.class, "/material-types/{id}", "engineer:dict:material-manage");
        assertRoute("listMaterials", new Class<?>[]{Boolean.class}, GetMapping.class, "/materials", "engineer:dict:view");
        assertRoute("pageMaterials", new Class<?>[]{Boolean.class, String.class, Integer.class, Integer.class}, GetMapping.class, "/materials/page", "engineer:dict:view");
        assertRoute("createMaterial", new Class<?>[]{MaterialMasterSaveReq.class}, PostMapping.class, "/materials", "engineer:dict:material-manage");
        assertRoute("updateMaterial", new Class<?>[]{Long.class, MaterialMasterSaveReq.class}, PutMapping.class, "/materials/{id}", "engineer:dict:material-manage");
        assertRoute("deleteMaterial", new Class<?>[]{Long.class}, DeleteMapping.class, "/materials/{id}", "engineer:dict:material-manage");
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
        return material;
    }

    private DictMaterialPriceSpec spec(Long id, Long materialId, String code, String name,
                                       Double unifiedPrice, Double platePrice, Double barPrice,
                                       Double tubePrice, int sortNo) {
        DictMaterialPriceSpec spec = new DictMaterialPriceSpec();
        spec.setId(id);
        spec.setMaterialId(materialId);
        spec.setSpecCode(code);
        spec.setSpecName(name);
        spec.setUnifiedPrice(unifiedPrice);
        spec.setPlatePrice(platePrice);
        spec.setBarPrice(barPrice);
        spec.setTubePrice(tubePrice);
        spec.setSortNo(sortNo);
        return spec;
    }

    private MaterialPriceSpecDTO specReq(String code, String name, Double unifiedPrice,
                                         Double platePrice, Double barPrice, Double tubePrice) {
        MaterialPriceSpecDTO dto = new MaterialPriceSpecDTO();
        dto.setSpecCode(code);
        dto.setSpecName(name);
        dto.setUnifiedPrice(unifiedPrice);
        dto.setPlatePrice(platePrice);
        dto.setBarPrice(barPrice);
        dto.setTubePrice(tubePrice);
        return dto;
    }

    private DictMaterialType materialType(Long id, String code, String name, boolean enabled) {
        DictMaterialType type = new DictMaterialType();
        type.setId(id);
        type.setTypeCode(code);
        type.setTypeName(name);
        type.setEnabled(enabled);
        return type;
    }

    private MaterialMasterSaveReq materialReq(String code, String name, Long typeId) {
        MaterialMasterSaveReq req = new MaterialMasterSaveReq();
        req.setMaterialCode(code);
        req.setMaterialName(name);
        req.setMaterialTypeId(typeId);
        req.setDensity(7.85);
        return req;
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
        } else if (mappingType == DeleteMapping.class) {
            assertThat(method.getAnnotation(DeleteMapping.class).value()).containsExactly(path);
        } else {
            throw new IllegalArgumentException("Unsupported mapping type " + mappingType);
        }
    }
}
