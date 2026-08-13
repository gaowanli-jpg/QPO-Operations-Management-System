package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.dto.mes.master.MesProcessTypeListResp;
import com.machining.quote.entity.DictProcessCategory;
import com.machining.quote.entity.DictProcessDetail;
import com.machining.quote.entity.MesMasterDataSyncRun;
import com.machining.quote.mapper.DictProcessCategoryMapper;
import com.machining.quote.mapper.DictProcessDetailMapper;
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

class MesProcessTypeSyncServiceTest {

    @Test
    void syncProcessTypesUpsertsMesCategoryCreatesDefaultDetailAndAuditsCounts() {
        MesMasterDataClient client = mock(MesMasterDataClient.class);
        DictProcessCategoryMapper categoryMapper = mock(DictProcessCategoryMapper.class);
        DictProcessDetailMapper detailMapper = mock(DictProcessDetailMapper.class);
        MesMasterDataSyncRunMapper syncRunMapper = mock(MesMasterDataSyncRunMapper.class);
        AuditService auditService = mock(AuditService.class);
        MesMasterDataSyncService service = service(client, categoryMapper, detailMapper, syncRunMapper, auditService);
        when(client.listProcessTypes()).thenReturn(List.of(row("mes-1", " P001 ", " 车削 ", "80.00", "ENABLE")));
        when(categoryMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        when(detailMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        doAnswer(invocation -> {
            MesMasterDataSyncRun run = invocation.getArgument(0);
            run.setId(900L);
            return 1;
        }).when(syncRunMapper).insert(any(MesMasterDataSyncRun.class));
        doAnswer(invocation -> {
            DictProcessCategory category = invocation.getArgument(0);
            category.setId(101L);
            return 1;
        }).when(categoryMapper).insert(any(DictProcessCategory.class));
        doAnswer(invocation -> {
            DictProcessDetail detail = invocation.getArgument(0);
            detail.setId(501L);
            return 1;
        }).when(detailMapper).insert(any(DictProcessDetail.class));

        MesMasterDataSyncService.SyncResult result = service.syncProcessTypesManual(7L);

        assertThat(result.success()).isTrue();
        assertThat(result.syncRunId()).isEqualTo(900L);
        assertThat(result.fetchedCount()).isEqualTo(1);
        assertThat(result.validCount()).isEqualTo(1);
        assertThat(result.invalidCount()).isZero();
        assertThat(result.duplicateCount()).isZero();
        assertThat(result.upsertedCount()).isEqualTo(1);
        assertThat(result.disabledCount()).isZero();

        ArgumentCaptor<DictProcessCategory> categoryCaptor = ArgumentCaptor.forClass(DictProcessCategory.class);
        verify(categoryMapper).insert(categoryCaptor.capture());
        assertThat(categoryCaptor.getValue()).satisfies(category -> {
            assertThat(category.getProcessCode()).isEqualTo("P001");
            assertThat(category.getCategoryName()).isEqualTo("车削");
            assertThat(category.getHourlyRate()).isEqualByComparingTo(new BigDecimal("80.00"));
            assertThat(category.getSourceType()).isEqualTo("MES");
            assertThat(category.getMesExternalId()).isEqualTo("mes-1");
            assertThat(category.getEnabled()).isTrue();
            assertThat(category.getMesSyncedAt()).isNotNull();
        });

        ArgumentCaptor<DictProcessDetail> detailCaptor = ArgumentCaptor.forClass(DictProcessDetail.class);
        verify(detailMapper).insert(detailCaptor.capture());
        assertThat(detailCaptor.getValue()).satisfies(detail -> {
            assertThat(detail.getCategoryId()).isEqualTo(101L);
            assertThat(detail.getDetailName()).isEqualTo("车削");
            assertThat(detail.getEnabled()).isTrue();
        });

        ArgumentCaptor<MesMasterDataSyncRun> runCaptor = ArgumentCaptor.forClass(MesMasterDataSyncRun.class);
        verify(syncRunMapper).updateById(runCaptor.capture());
        assertThat(runCaptor.getValue()).satisfies(run -> {
            assertThat(run.getStatus()).isEqualTo("SUCCESS");
            assertThat(run.getSyncType()).isEqualTo("MES_PROCESS_TYPE");
            assertThat(run.getFetchedCount()).isEqualTo(1);
            assertThat(run.getValidCount()).isEqualTo(1);
            assertThat(run.getUpsertedCount()).isEqualTo(1);
            assertThat(run.getDisabledCount()).isZero();
            assertThat(run.getMessage()).isEqualTo("OK");
            assertThat(run.getCreatedBy()).isEqualTo(7L);
        });

        ArgumentCaptor<Object> afterCaptor = ArgumentCaptor.forClass(Object.class);
        verify(auditService).record(eq("DICT_PROCESS_MES_SYNC"), eq("mes_master_data_sync_run"), eq(900L), isNull(), afterCaptor.capture());
        assertThat(afterCaptor.getValue()).isInstanceOf(Map.class);
        Map<?, ?> audit = (Map<?, ?>) afterCaptor.getValue();
        assertThat(audit.get("syncType")).isEqualTo("MES_PROCESS_TYPE");
        assertThat(audit.get("fetchedCount")).isEqualTo(1);
        assertThat(audit.get("validCount")).isEqualTo(1);
        assertThat(audit.get("upsertedCount")).isEqualTo(1);
        assertThat(audit.get("disabledCount")).isEqualTo(0);
        assertThat(audit.containsKey("rows")).isFalse();
        assertThat(audit.containsKey("response")).isFalse();
        assertThat(audit.containsKey("rawResponse")).isFalse();
        assertThat(audit.containsKey("authorization")).isFalse();
        assertThat(audit.containsKey("token")).isFalse();
    }

    @Test
    void syncDisablesMissingMesCategoriesButLeavesLocalCategoriesUntouched() {
        MesMasterDataClient client = mock(MesMasterDataClient.class);
        DictProcessCategoryMapper categoryMapper = mock(DictProcessCategoryMapper.class);
        DictProcessDetailMapper detailMapper = mock(DictProcessDetailMapper.class);
        MesMasterDataSyncRunMapper syncRunMapper = mock(MesMasterDataSyncRunMapper.class);
        MesMasterDataSyncService service = service(client, categoryMapper, detailMapper, syncRunMapper, mock(AuditService.class));
        DictProcessCategory existingMes = category(10L, "P001", "Old", "MES", true);
        DictProcessCategory staleMes = category(11L, "OLD", "旧MES", "MES", true);
        DictProcessCategory local = category(12L, "LOCAL-ONLY", "本地工艺", "LOCAL", true);
        when(client.listProcessTypes()).thenReturn(List.of(row("mes-1", "P001", "车削", "80.00", "ENABLE")));
        when(categoryMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(existingMes, staleMes, local));
        when(detailMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(detail(88L, 10L, "车削", true)));
        doAnswer(invocation -> {
            MesMasterDataSyncRun run = invocation.getArgument(0);
            run.setId(901L);
            return 1;
        }).when(syncRunMapper).insert(any(MesMasterDataSyncRun.class));

        MesMasterDataSyncService.SyncResult result = service.syncProcessTypesManual(8L);

        assertThat(result.disabledCount()).isEqualTo(1);
        ArgumentCaptor<DictProcessCategory> updates = ArgumentCaptor.forClass(DictProcessCategory.class);
        verify(categoryMapper, org.mockito.Mockito.times(2)).updateById(updates.capture());
        assertThat(updates.getAllValues())
                .extracting(DictProcessCategory::getId)
                .containsExactlyInAnyOrder(10L, 11L)
                .doesNotContain(12L);
        DictProcessCategory staleUpdate = updates.getAllValues().stream()
                .filter(category -> Long.valueOf(11L).equals(category.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(staleUpdate.getEnabled()).isFalse();
        assertThat(local.getEnabled()).isTrue();
        verify(categoryMapper, never()).deleteById(any(java.io.Serializable.class));
        verify(detailMapper, never()).deleteById(any(java.io.Serializable.class));
    }

    @Test
    void emptyMesResponseFailsRunAndDoesNotDisableExistingMesCategories() {
        MesMasterDataClient client = mock(MesMasterDataClient.class);
        DictProcessCategoryMapper categoryMapper = mock(DictProcessCategoryMapper.class);
        DictProcessDetailMapper detailMapper = mock(DictProcessDetailMapper.class);
        MesMasterDataSyncRunMapper syncRunMapper = mock(MesMasterDataSyncRunMapper.class);
        MesMasterDataSyncService service = service(client, categoryMapper, detailMapper, syncRunMapper, mock(AuditService.class));
        when(client.listProcessTypes()).thenReturn(List.of());
        doAnswer(invocation -> {
            MesMasterDataSyncRun run = invocation.getArgument(0);
            run.setId(902L);
            return 1;
        }).when(syncRunMapper).insert(any(MesMasterDataSyncRun.class));

        MesMasterDataSyncService.SyncResult result = service.syncProcessTypesManual(9L);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("returned no valid process types");
        verify(categoryMapper, never()).selectList(any(QueryWrapper.class));
        verify(categoryMapper, never()).updateById(any(DictProcessCategory.class));
        verify(detailMapper, never()).insert(any(DictProcessDetail.class));
        ArgumentCaptor<MesMasterDataSyncRun> runCaptor = ArgumentCaptor.forClass(MesMasterDataSyncRun.class);
        verify(syncRunMapper).updateById(runCaptor.capture());
        assertThat(runCaptor.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(runCaptor.getValue().getFetchedCount()).isZero();
    }

    @Test
    void syncMapsChineseDisabledStatusToDisabledCategory() {
        MesMasterDataClient client = mock(MesMasterDataClient.class);
        DictProcessCategoryMapper categoryMapper = mock(DictProcessCategoryMapper.class);
        DictProcessDetailMapper detailMapper = mock(DictProcessDetailMapper.class);
        MesMasterDataSyncRunMapper syncRunMapper = mock(MesMasterDataSyncRunMapper.class);
        MesMasterDataSyncService service = service(client, categoryMapper, detailMapper, syncRunMapper, mock(AuditService.class));
        when(client.listProcessTypes()).thenReturn(List.of(row("mes-disabled", "P002", "线切割", "60.00", "禁用")));
        when(categoryMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        when(detailMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        doAnswer(invocation -> {
            MesMasterDataSyncRun run = invocation.getArgument(0);
            run.setId(903L);
            return 1;
        }).when(syncRunMapper).insert(any(MesMasterDataSyncRun.class));

        service.syncProcessTypesManual(10L);

        ArgumentCaptor<DictProcessCategory> categoryCaptor = ArgumentCaptor.forClass(DictProcessCategory.class);
        verify(categoryMapper).insert(categoryCaptor.capture());
        assertThat(categoryCaptor.getValue().getEnabled()).isFalse();
    }

    @Test
    void syncMapsMesZeroStatusToEnabledCategory() {
        MesMasterDataClient client = mock(MesMasterDataClient.class);
        DictProcessCategoryMapper categoryMapper = mock(DictProcessCategoryMapper.class);
        DictProcessDetailMapper detailMapper = mock(DictProcessDetailMapper.class);
        MesMasterDataSyncRunMapper syncRunMapper = mock(MesMasterDataSyncRunMapper.class);
        MesMasterDataSyncService service = service(client, categoryMapper, detailMapper, syncRunMapper, mock(AuditService.class));
        when(client.listProcessTypes()).thenReturn(List.of(row("mes-zero", "P003", "MES正常工艺", "70.00", "0")));
        when(categoryMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        when(detailMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        doAnswer(invocation -> {
            MesMasterDataSyncRun run = invocation.getArgument(0);
            run.setId(904L);
            return 1;
        }).when(syncRunMapper).insert(any(MesMasterDataSyncRun.class));

        service.syncProcessTypesManual(11L);

        ArgumentCaptor<DictProcessCategory> categoryCaptor = ArgumentCaptor.forClass(DictProcessCategory.class);
        verify(categoryMapper).insert(categoryCaptor.capture());
        assertThat(categoryCaptor.getValue().getEnabled()).isTrue();
    }

    @Test
    void syncMapsMesOneStatusToDisabledCategory() {
        MesMasterDataClient client = mock(MesMasterDataClient.class);
        DictProcessCategoryMapper categoryMapper = mock(DictProcessCategoryMapper.class);
        DictProcessDetailMapper detailMapper = mock(DictProcessDetailMapper.class);
        MesMasterDataSyncRunMapper syncRunMapper = mock(MesMasterDataSyncRunMapper.class);
        MesMasterDataSyncService service = service(client, categoryMapper, detailMapper, syncRunMapper, mock(AuditService.class));
        when(client.listProcessTypes()).thenReturn(List.of(row("mes-one", "P004", "MES disabled process", "80.00", "1")));
        when(categoryMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        when(detailMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        doAnswer(invocation -> {
            MesMasterDataSyncRun run = invocation.getArgument(0);
            run.setId(905L);
            return 1;
        }).when(syncRunMapper).insert(any(MesMasterDataSyncRun.class));

        service.syncProcessTypesManual(12L);

        ArgumentCaptor<DictProcessCategory> categoryCaptor = ArgumentCaptor.forClass(DictProcessCategory.class);
        verify(categoryMapper).insert(categoryCaptor.capture());
        assertThat(categoryCaptor.getValue().getEnabled()).isFalse();
    }

    @Test
    void auditServiceKnowsMesProcessSyncLabel() {
        assertThat(AuditService.inferModule("DICT_PROCESS_MES_SYNC")).isEqualTo("DICT");
        assertThat(AuditService.labelForAction("DICT_PROCESS_MES_SYNC")).contains("MES");
    }

    private static MesMasterDataSyncService service(
            MesMasterDataClient client,
            DictProcessCategoryMapper categoryMapper,
            DictProcessDetailMapper detailMapper,
            MesMasterDataSyncRunMapper syncRunMapper,
            AuditService auditService) {
        MesMasterDataSyncService service = new MesMasterDataSyncService();
        ReflectionTestUtils.setField(service, "client", client);
        ReflectionTestUtils.setField(service, "categoryMapper", categoryMapper);
        ReflectionTestUtils.setField(service, "detailMapper", detailMapper);
        ReflectionTestUtils.setField(service, "syncRunMapper", syncRunMapper);
        ReflectionTestUtils.setField(service, "auditService", auditService);
        return service;
    }

    private static MesProcessTypeListResp.Row row(String id, String code, String name, String hourPrice, String status) {
        MesProcessTypeListResp.Row row = new MesProcessTypeListResp.Row();
        row.setId(id);
        row.setCode(code);
        row.setName(name);
        row.setHourPrice(new BigDecimal(hourPrice));
        row.setStatus(status);
        return row;
    }

    private static DictProcessCategory category(Long id, String code, String name, String sourceType, boolean enabled) {
        DictProcessCategory category = new DictProcessCategory();
        category.setId(id);
        category.setProcessCode(code);
        category.setCategoryName(name);
        category.setHourlyRate(BigDecimal.TEN);
        category.setSourceType(sourceType);
        category.setEnabled(enabled);
        category.setSortNo(0);
        return category;
    }

    private static DictProcessDetail detail(Long id, Long categoryId, String name, boolean enabled) {
        DictProcessDetail detail = new DictProcessDetail();
        detail.setId(id);
        detail.setCategoryId(categoryId);
        detail.setDetailName(name);
        detail.setEnabled(enabled);
        detail.setSortNo(0);
        return detail;
    }
}
