package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.dto.mes.master.MesMaterialPageResp;
import com.machining.quote.entity.DictMaterial;
import com.machining.quote.entity.DictMaterialType;
import com.machining.quote.entity.MesMasterDataSyncRun;
import com.machining.quote.mapper.DictMaterialMapper;
import com.machining.quote.mapper.DictMaterialTypeMapper;
import com.machining.quote.mapper.MesMasterDataSyncRunMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.mes.MesMasterDataClient;
import com.machining.quote.service.mes.MesMasterDataSyncService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MesMaterialMasterSyncServiceTest {

    @Test
    void syncMaterialsUpsertsThreeMesTypesPreservesExistingPricesAndAuditsCounts() {
        MesMasterDataClient client = mock(MesMasterDataClient.class);
        DictMaterialMapper materialMapper = mock(DictMaterialMapper.class);
        DictMaterialTypeMapper materialTypeMapper = mock(DictMaterialTypeMapper.class);
        MesMasterDataSyncRunMapper syncRunMapper = mock(MesMasterDataSyncRunMapper.class);
        AuditService auditService = mock(AuditService.class);
        MesMasterDataSyncService service = service(client, materialMapper, materialTypeMapper, syncRunMapper, auditService);
        DictMaterial existing = material(10L, "MAT-001", "旧材料名", 91L, "LOCAL", true);
        existing.setPlatePrice(11.1);
        existing.setBarPrice(12.2);
        existing.setTubePrice(13.3);
        existing.setPlateDensity(7.7);
        when(client.pageMaterials(1, 200)).thenReturn(page(3,
                row("mes-1", " MAT-001 ", " 45#钢 ", "MATERIAL", "BUY", "MAKE", "GB/T 699", "DOC-1", "12.50", "7.85", "ENABLE"),
                row("mes-2", "MAT-002", "成品件", "PRODUCT", "BUY", "MAKE", "STD-P", "DOC-2", "2.00", "8.01", "ENABLE"),
                row("mes-3", "MAT-003", "半成品件", "SEMI", "BUY", "MAKE", "STD-S", "DOC-3", "3.00", "7.66", "ENABLE")));
        when(materialTypeMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        when(materialMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(existing));
        doAnswer(invocation -> {
            MesMasterDataSyncRun run = invocation.getArgument(0);
            run.setId(910L);
            return 1;
        }).when(syncRunMapper).insert(any(MesMasterDataSyncRun.class));
        doAnswer(invocation -> {
            DictMaterialType type = invocation.getArgument(0);
            switch (type.getTypeCode()) {
                case "MATERIAL" -> type.setId(101L);
                case "PRODUCT" -> type.setId(102L);
                case "SEMI" -> type.setId(103L);
                default -> type.setId(199L);
            }
            return 1;
        }).when(materialTypeMapper).insert(any(DictMaterialType.class));
        doAnswer(invocation -> {
            DictMaterial material = invocation.getArgument(0);
            material.setId("MAT-002".equals(material.getMaterialCode()) ? 202L : 203L);
            return 1;
        }).when(materialMapper).insert(any(DictMaterial.class));

        MesMasterDataSyncService.SyncResult result = service.syncMaterialsManual(7L);

        assertThat(result.success()).isTrue();
        assertThat(result.syncRunId()).isEqualTo(910L);
        assertThat(result.fetchedCount()).isEqualTo(3);
        assertThat(result.validCount()).isEqualTo(3);
        assertThat(result.upsertedCount()).isEqualTo(3);
        verify(client).pageMaterials(1, 200);
        verify(client, never()).pageMaterials(2, 200);

        ArgumentCaptor<DictMaterialType> typeCaptor = ArgumentCaptor.forClass(DictMaterialType.class);
        verify(materialTypeMapper, org.mockito.Mockito.times(3)).insert(typeCaptor.capture());
        assertThat(typeCaptor.getAllValues())
                .extracting(DictMaterialType::getTypeCode, DictMaterialType::getTypeName)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("MATERIAL", "原材料"),
                        org.assertj.core.groups.Tuple.tuple("PRODUCT", "成品"),
                        org.assertj.core.groups.Tuple.tuple("SEMI", "半成品"));

        ArgumentCaptor<DictMaterial> updateCaptor = ArgumentCaptor.forClass(DictMaterial.class);
        verify(materialMapper).updateById(updateCaptor.capture());
        assertThat(updateCaptor.getValue()).satisfies(material -> {
            assertThat(material.getId()).isEqualTo(10L);
            assertThat(material.getMaterialCode()).isEqualTo("MAT-001");
            assertThat(material.getMaterialName()).isEqualTo("45#钢");
            assertThat(material.getMaterialTypeId()).isEqualTo(101L);
            assertThat(material.getDensity()).isEqualTo(7.85);
            assertThat(material.getPlateDensity()).isEqualTo(7.7);
            assertThat(material.getPlatePrice()).isEqualTo(11.1);
            assertThat(material.getBarPrice()).isEqualTo(12.2);
            assertThat(material.getTubePrice()).isEqualTo(13.3);
            assertThat(material.getSourceType()).isEqualTo("MES");
            assertThat(material.getMesExternalId()).isEqualTo("mes-1");
            assertThat(material.getMesMaterialType()).isEqualTo("MATERIAL");
            assertThat(material.getMesPurchaseType()).isEqualTo("BUY");
            assertThat(material.getMesProductionType()).isEqualTo("MAKE");
            assertThat(material.getMesStandard()).isEqualTo("GB/T 699");
            assertThat(material.getMesDocNum()).isEqualTo("DOC-1");
            assertThat(material.getMesWeight()).isEqualTo("12.50");
            assertThat(material.getMesDensityRaw()).isEqualTo("7.85");
            assertThat(material.getMesSyncedAt()).isNotNull();
        });

        ArgumentCaptor<DictMaterial> insertCaptor = ArgumentCaptor.forClass(DictMaterial.class);
        verify(materialMapper, org.mockito.Mockito.times(2)).insert(insertCaptor.capture());
        DictMaterial product = insertCaptor.getAllValues().stream()
                .filter(material -> "MAT-002".equals(material.getMaterialCode()))
                .findFirst()
                .orElseThrow();
        assertThat(product).satisfies(material -> {
            assertThat(material.getMaterialTypeId()).isEqualTo(102L);
            assertThat(material.getPlatePrice()).isNull();
            assertThat(material.getBarPrice()).isNull();
            assertThat(material.getTubePrice()).isNull();
            assertThat(material.getDensity()).isEqualTo(8.01);
            assertThat(material.getPlateDensity()).isEqualTo(8.01);
            assertThat(material.getBarDensity()).isEqualTo(8.01);
            assertThat(material.getTubeDensity()).isEqualTo(8.01);
            assertThat(material.getMesDensityRaw()).isEqualTo("8.01");
        });

        ArgumentCaptor<MesMasterDataSyncRun> runCaptor = ArgumentCaptor.forClass(MesMasterDataSyncRun.class);
        verify(syncRunMapper).updateById(runCaptor.capture());
        assertThat(runCaptor.getValue()).satisfies(run -> {
            assertThat(run.getStatus()).isEqualTo("SUCCESS");
            assertThat(run.getSyncType()).isEqualTo("MES_MATERIAL");
            assertThat(run.getFetchedCount()).isEqualTo(3);
            assertThat(run.getValidCount()).isEqualTo(3);
            assertThat(run.getUpsertedCount()).isEqualTo(3);
            assertThat(run.getCreatedBy()).isEqualTo(7L);
        });

        ArgumentCaptor<Object> afterCaptor = ArgumentCaptor.forClass(Object.class);
        verify(auditService).record(eq("DICT_MATERIAL_MES_SYNC"), eq("mes_master_data_sync_run"), eq(910L), isNull(), afterCaptor.capture());
        Map<?, ?> audit = (Map<?, ?>) afterCaptor.getValue();
        assertThat(audit.get("syncType")).isEqualTo("MES_MATERIAL");
        assertThat(audit.get("fetchedCount")).isEqualTo(3);
        assertThat(audit.get("validCount")).isEqualTo(3);
        assertThat(audit.get("upsertedCount")).isEqualTo(3);
        assertThat(audit.containsKey("rows")).isFalse();
        assertThat(audit.containsKey("response")).isFalse();
        assertThat(audit.containsKey("rawResponse")).isFalse();
        assertThat(audit.containsKey("authorization")).isFalse();
        assertThat(audit.containsKey("token")).isFalse();
    }

    @Test
    void syncMaterialsCreatesUnknownTypeFromMesValue() {
        MesMasterDataClient client = mock(MesMasterDataClient.class);
        DictMaterialMapper materialMapper = mock(DictMaterialMapper.class);
        DictMaterialTypeMapper materialTypeMapper = mock(DictMaterialTypeMapper.class);
        MesMasterDataSyncRunMapper syncRunMapper = mock(MesMasterDataSyncRunMapper.class);
        MesMasterDataSyncService service = service(client, materialMapper, materialTypeMapper, syncRunMapper, mock(AuditService.class));
        when(client.pageMaterials(1, 200)).thenReturn(page(1,
                row("mes-x", "MAT-X", "特殊料", "SPECIAL", null, null, null, null, null, null, "ENABLE")));
        when(materialTypeMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        when(materialMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        doAnswer(invocation -> {
            MesMasterDataSyncRun run = invocation.getArgument(0);
            run.setId(911L);
            return 1;
        }).when(syncRunMapper).insert(any(MesMasterDataSyncRun.class));
        doAnswer(invocation -> {
            DictMaterialType type = invocation.getArgument(0);
            switch (type.getTypeCode()) {
                case "MATERIAL" -> type.setId(201L);
                case "PRODUCT" -> type.setId(202L);
                case "SEMI" -> type.setId(203L);
                case "SPECIAL" -> type.setId(204L);
                default -> type.setId(299L);
            }
            return 1;
        }).when(materialTypeMapper).insert(any(DictMaterialType.class));

        service.syncMaterialsManual(8L);

        ArgumentCaptor<DictMaterialType> typeCaptor = ArgumentCaptor.forClass(DictMaterialType.class);
        verify(materialTypeMapper, org.mockito.Mockito.times(4)).insert(typeCaptor.capture());
        assertThat(typeCaptor.getAllValues())
                .extracting(DictMaterialType::getTypeCode, DictMaterialType::getTypeName)
                .contains(
                        org.assertj.core.groups.Tuple.tuple("MATERIAL", "原材料"),
                        org.assertj.core.groups.Tuple.tuple("PRODUCT", "成品"),
                        org.assertj.core.groups.Tuple.tuple("SEMI", "半成品"),
                        org.assertj.core.groups.Tuple.tuple("SPECIAL", "SPECIAL"));
        ArgumentCaptor<DictMaterial> materialCaptor = ArgumentCaptor.forClass(DictMaterial.class);
        verify(materialMapper).insert(materialCaptor.capture());
        assertThat(materialCaptor.getValue().getMaterialTypeId()).isEqualTo(204L);
    }

    @Test
    void syncMaterialsReusesLegacyMaterialTypeByNameWhenMesCodeDiffers() {
        MesMasterDataClient client = mock(MesMasterDataClient.class);
        DictMaterialMapper materialMapper = mock(DictMaterialMapper.class);
        DictMaterialTypeMapper materialTypeMapper = mock(DictMaterialTypeMapper.class);
        MesMasterDataSyncRunMapper syncRunMapper = mock(MesMasterDataSyncRunMapper.class);
        MesMasterDataSyncService service = service(client, materialMapper, materialTypeMapper, syncRunMapper, mock(AuditService.class));
        DictMaterialType legacyRawMaterial = materialType(301L, "RAW_MATERIAL", "原材料");
        when(client.pageMaterials(1, 200)).thenReturn(page(1,
                row("mes-legacy", "MAT-LEGACY", "旧类型兼容材料", "MATERIAL", null, null, null, null, "1.00", "7.85", "ENABLE")));
        when(materialTypeMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(legacyRawMaterial));
        when(materialMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        doAnswer(invocation -> {
            MesMasterDataSyncRun run = invocation.getArgument(0);
            run.setId(914L);
            return 1;
        }).when(syncRunMapper).insert(any(MesMasterDataSyncRun.class));
        doAnswer(invocation -> {
            DictMaterialType type = invocation.getArgument(0);
            switch (type.getTypeCode()) {
                case "PRODUCT" -> type.setId(302L);
                case "SEMI" -> type.setId(303L);
                default -> type.setId(399L);
            }
            return 1;
        }).when(materialTypeMapper).insert(any(DictMaterialType.class));

        MesMasterDataSyncService.SyncResult result = service.syncMaterialsManual(11L);

        assertThat(result.success()).isTrue();
        ArgumentCaptor<DictMaterialType> typeUpdates = ArgumentCaptor.forClass(DictMaterialType.class);
        verify(materialTypeMapper).updateById(typeUpdates.capture());
        assertThat(typeUpdates.getValue()).satisfies(type -> {
            assertThat(type.getId()).isEqualTo(301L);
            assertThat(type.getTypeCode()).isEqualTo("MATERIAL");
            assertThat(type.getTypeName()).isEqualTo("原材料");
        });
        ArgumentCaptor<DictMaterialType> typeInserts = ArgumentCaptor.forClass(DictMaterialType.class);
        verify(materialTypeMapper, org.mockito.Mockito.times(2)).insert(typeInserts.capture());
        assertThat(typeInserts.getAllValues())
                .extracting(DictMaterialType::getTypeCode)
                .containsExactlyInAnyOrder("PRODUCT", "SEMI")
                .doesNotContain("MATERIAL");
        ArgumentCaptor<DictMaterial> materialCaptor = ArgumentCaptor.forClass(DictMaterial.class);
        verify(materialMapper).insert(materialCaptor.capture());
        assertThat(materialCaptor.getValue().getMaterialTypeId()).isEqualTo(301L);
    }

    @Test
    void syncMaterialsMapsMesZeroStatusToEnabledMaterial() {
        MesMasterDataClient client = mock(MesMasterDataClient.class);
        DictMaterialMapper materialMapper = mock(DictMaterialMapper.class);
        DictMaterialTypeMapper materialTypeMapper = mock(DictMaterialTypeMapper.class);
        MesMasterDataSyncRunMapper syncRunMapper = mock(MesMasterDataSyncRunMapper.class);
        MesMasterDataSyncService service = service(client, materialMapper, materialTypeMapper, syncRunMapper, mock(AuditService.class));
        when(client.pageMaterials(1, 200)).thenReturn(page(1,
                row("mes-zero", "MAT-ZERO", "MES正常材料", "MATERIAL", null, null, null, null, "1.00", "7.85", "0")));
        when(materialTypeMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(materialType(401L, "MATERIAL", "原材料")));
        when(materialMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        doAnswer(invocation -> {
            MesMasterDataSyncRun run = invocation.getArgument(0);
            run.setId(915L);
            return 1;
        }).when(syncRunMapper).insert(any(MesMasterDataSyncRun.class));

        service.syncMaterialsManual(12L);

        ArgumentCaptor<DictMaterial> materialCaptor = ArgumentCaptor.forClass(DictMaterial.class);
        verify(materialMapper).insert(materialCaptor.capture());
        assertThat(materialCaptor.getValue().getEnabled()).isTrue();
    }

    @Test
    void syncMaterialsMapsMesOneStatusToDisabledMaterial() {
        MesMasterDataClient client = mock(MesMasterDataClient.class);
        DictMaterialMapper materialMapper = mock(DictMaterialMapper.class);
        DictMaterialTypeMapper materialTypeMapper = mock(DictMaterialTypeMapper.class);
        MesMasterDataSyncRunMapper syncRunMapper = mock(MesMasterDataSyncRunMapper.class);
        MesMasterDataSyncService service = service(client, materialMapper, materialTypeMapper, syncRunMapper, mock(AuditService.class));
        when(client.pageMaterials(1, 200)).thenReturn(page(1,
                row("mes-one", "MAT-ONE", "MES disabled material", "MATERIAL", null, null, null, null, "1.00", "7.85", "1")));
        when(materialTypeMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(materialType(402L, "MATERIAL", "Material")));
        when(materialMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        doAnswer(invocation -> {
            MesMasterDataSyncRun run = invocation.getArgument(0);
            run.setId(916L);
            return 1;
        }).when(syncRunMapper).insert(any(MesMasterDataSyncRun.class));

        service.syncMaterialsManual(13L);

        ArgumentCaptor<DictMaterial> materialCaptor = ArgumentCaptor.forClass(DictMaterial.class);
        verify(materialMapper).insert(materialCaptor.capture());
        assertThat(materialCaptor.getValue().getEnabled()).isFalse();
    }

    @Test
    void emptyFirstMaterialPageFailsRunAndDoesNotDisableExistingMesMaterials() {
        MesMasterDataClient client = mock(MesMasterDataClient.class);
        DictMaterialMapper materialMapper = mock(DictMaterialMapper.class);
        DictMaterialTypeMapper materialTypeMapper = mock(DictMaterialTypeMapper.class);
        MesMasterDataSyncRunMapper syncRunMapper = mock(MesMasterDataSyncRunMapper.class);
        MesMasterDataSyncService service = service(client, materialMapper, materialTypeMapper, syncRunMapper, mock(AuditService.class));
        when(client.pageMaterials(1, 200)).thenReturn(page(0));
        doAnswer(invocation -> {
            MesMasterDataSyncRun run = invocation.getArgument(0);
            run.setId(912L);
            return 1;
        }).when(syncRunMapper).insert(any(MesMasterDataSyncRun.class));

        MesMasterDataSyncService.SyncResult result = service.syncMaterialsManual(9L);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("returned no valid materials");
        verify(materialTypeMapper, never()).selectList(any(QueryWrapper.class));
        verify(materialMapper, never()).selectList(any(QueryWrapper.class));
        verify(materialMapper, never()).updateById(any(DictMaterial.class));
        ArgumentCaptor<MesMasterDataSyncRun> runCaptor = ArgumentCaptor.forClass(MesMasterDataSyncRun.class);
        verify(syncRunMapper).updateById(runCaptor.capture());
        assertThat(runCaptor.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(runCaptor.getValue().getFetchedCount()).isZero();
    }

    @Test
    void syncMaterialsDisablesMissingMesMaterialsButLeavesLocalMaterialsUntouched() {
        MesMasterDataClient client = mock(MesMasterDataClient.class);
        DictMaterialMapper materialMapper = mock(DictMaterialMapper.class);
        DictMaterialTypeMapper materialTypeMapper = mock(DictMaterialTypeMapper.class);
        MesMasterDataSyncRunMapper syncRunMapper = mock(MesMasterDataSyncRunMapper.class);
        MesMasterDataSyncService service = service(client, materialMapper, materialTypeMapper, syncRunMapper, mock(AuditService.class));
        DictMaterialType existingType = materialType(301L, "MATERIAL", "原材料");
        DictMaterial keep = material(31L, "MAT-KEEP", "保留", 301L, "MES", true);
        DictMaterial staleMes = material(32L, "MAT-OLD", "旧MES", 301L, "MES", true);
        DictMaterial local = material(33L, "LOCAL-ONLY", "本地", 301L, "LOCAL", true);
        when(client.pageMaterials(1, 200)).thenReturn(page(1,
                row("mes-keep", "MAT-KEEP", "保留更新", "MATERIAL", null, null, null, null, null, null, "ENABLE")));
        when(materialTypeMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(existingType));
        when(materialMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(keep, staleMes, local));
        doAnswer(invocation -> {
            MesMasterDataSyncRun run = invocation.getArgument(0);
            run.setId(913L);
            return 1;
        }).when(syncRunMapper).insert(any(MesMasterDataSyncRun.class));

        MesMasterDataSyncService.SyncResult result = service.syncMaterialsManual(10L);

        assertThat(result.disabledCount()).isEqualTo(1);
        ArgumentCaptor<DictMaterial> updates = ArgumentCaptor.forClass(DictMaterial.class);
        verify(materialMapper, org.mockito.Mockito.times(2)).updateById(updates.capture());
        assertThat(updates.getAllValues())
                .extracting(DictMaterial::getId)
                .containsExactlyInAnyOrder(31L, 32L)
                .doesNotContain(33L);
        DictMaterial staleUpdate = updates.getAllValues().stream()
                .filter(material -> Long.valueOf(32L).equals(material.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(staleUpdate.getEnabled()).isFalse();
        assertThat(local.getEnabled()).isTrue();
    }

    @Test
    void auditServiceKnowsMesMaterialSyncLabel() {
        assertThat(AuditService.inferModule("DICT_MATERIAL_MES_SYNC")).isEqualTo("DICT");
        assertThat(AuditService.labelForAction("DICT_MATERIAL_MES_SYNC")).contains("MES");
    }

    private static MesMasterDataSyncService service(
            MesMasterDataClient client,
            DictMaterialMapper materialMapper,
            DictMaterialTypeMapper materialTypeMapper,
            MesMasterDataSyncRunMapper syncRunMapper,
            AuditService auditService) {
        MesMasterDataSyncService service = new MesMasterDataSyncService();
        ReflectionTestUtils.setField(service, "client", client);
        ReflectionTestUtils.setField(service, "materialMapper", materialMapper);
        ReflectionTestUtils.setField(service, "materialTypeMapper", materialTypeMapper);
        ReflectionTestUtils.setField(service, "syncRunMapper", syncRunMapper);
        ReflectionTestUtils.setField(service, "auditService", auditService);
        return service;
    }

    private static MesMaterialPageResp.Data page(long total, MesMaterialPageResp.Row... rows) {
        MesMaterialPageResp.Data data = new MesMaterialPageResp.Data();
        data.setTotal(total);
        data.setList(List.of(rows));
        return data;
    }

    private static MesMaterialPageResp.Row row(
            String id,
            String code,
            String name,
            String materialType,
            String purchaseType,
            String productionType,
            String standard,
            String docNum,
            String weight,
            String density,
            String status) {
        MesMaterialPageResp.Row row = new MesMaterialPageResp.Row();
        row.setId(id);
        row.setCode(code);
        row.setName(name);
        row.setMaterialType(materialType);
        row.setPurchaseType(purchaseType);
        row.setProductionType(productionType);
        row.setStandard(standard);
        row.setDocNum(docNum);
        if (weight != null) {
            row.setWeight(new BigDecimal(weight));
        }
        if (density != null) {
            row.setDensity(new BigDecimal(density));
        }
        row.setStatus(status);
        return row;
    }

    private static DictMaterial material(Long id, String code, String name, Long typeId, String sourceType, boolean enabled) {
        DictMaterial material = new DictMaterial();
        material.setId(id);
        material.setMaterialCode(code);
        material.setMaterialName(name);
        material.setMaterialTypeId(typeId);
        material.setSourceType(sourceType);
        material.setEnabled(enabled);
        material.setSortNo(0);
        return material;
    }

    private static DictMaterialType materialType(Long id, String code, String name) {
        DictMaterialType type = new DictMaterialType();
        type.setId(id);
        type.setTypeCode(code);
        type.setTypeName(name);
        type.setEnabled(true);
        type.setSortNo(0);
        return type;
    }
}
