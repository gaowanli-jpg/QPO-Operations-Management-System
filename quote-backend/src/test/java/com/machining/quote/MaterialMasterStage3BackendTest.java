package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.config.RequirePermission;
import com.machining.quote.controller.DictController;
import com.machining.quote.dto.MaterialMasterDTO;
import com.machining.quote.dto.MaterialMasterSaveReq;
import com.machining.quote.dto.MaterialTypeDTO;
import com.machining.quote.dto.MaterialTypeSaveReq;
import com.machining.quote.entity.DictMaterial;
import com.machining.quote.entity.DictMaterialType;
import com.machining.quote.mapper.DictMaterialMapper;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MaterialMasterStage3BackendTest {

    @Test
    void listMaterialsReturnsMaterialTypeFieldsWithoutAuditLog() {
        MaterialMasterService service = service();
        DictMaterialMapper materialMapper = mapper(service, "materialMapper");
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
        material.setMesSyncedAt(syncedAt);
        DictMaterialType type = materialType(1L, "RAW_MATERIAL", "原材料", true);
        when(materialMapper.selectList(any())).thenReturn(List.of(material));
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
        assertThat(row.getMesSyncedAt()).isEqualTo(syncedAt);
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
        req.setPlatePrice(7.1);
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
        verify(materialMapper).insert(any(DictMaterial.class));
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
                    assertThat(summary.containsKey("platePrice")).isFalse();
                    assertThat(summary.containsKey("barPrice")).isFalse();
                    assertThat(summary.containsKey("tubePrice")).isFalse();
                    return true;
                })
        );
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
        assertRoute("createMaterial", new Class<?>[]{MaterialMasterSaveReq.class}, PostMapping.class, "/materials", "engineer:dict:material-manage");
        assertRoute("updateMaterial", new Class<?>[]{Long.class, MaterialMasterSaveReq.class}, PutMapping.class, "/materials/{id}", "engineer:dict:material-manage");
        assertRoute("deleteMaterial", new Class<?>[]{Long.class}, DeleteMapping.class, "/materials/{id}", "engineer:dict:material-manage");
    }

    private MaterialMasterService service() {
        MaterialMasterService service = new MaterialMasterService();
        ReflectionTestUtils.setField(service, "materialMapper", mock(DictMaterialMapper.class));
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
        req.setPlatePrice(7.1);
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
