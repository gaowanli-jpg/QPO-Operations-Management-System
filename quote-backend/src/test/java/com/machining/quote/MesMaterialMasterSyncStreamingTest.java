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
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MesMaterialMasterSyncStreamingTest {

    @Test
    void syncMaterialsPersistsEachMesPageBeforeFetchingTheNextPage() {
        MesMasterDataClient client = mock(MesMasterDataClient.class);
        DictMaterialMapper materialMapper = mock(DictMaterialMapper.class);
        DictMaterialTypeMapper materialTypeMapper = mock(DictMaterialTypeMapper.class);
        MesMasterDataSyncRunMapper syncRunMapper = mock(MesMasterDataSyncRunMapper.class);
        MesMasterDataSyncService service = service(client, materialMapper, materialTypeMapper, syncRunMapper);
        when(materialTypeMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(materialType(601L, "MATERIAL", "原材料")));
        when(materialMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        doAnswer(invocation -> {
            MesMasterDataSyncRun run = invocation.getArgument(0);
            run.setId(919L);
            return 1;
        }).when(syncRunMapper).insert(any(MesMasterDataSyncRun.class));
        when(client.pageMaterials(1, 200)).thenReturn(page(201, materialRows("MAT-STREAM-", 1, 200)));
        when(client.pageMaterials(2, 200)).thenAnswer(invocation -> {
            verify(materialMapper, org.mockito.Mockito.times(200)).insert(any(DictMaterial.class));
            return page(201,
                    row("mes-stream-201", "MAT-STREAM-201", "流式材料201", "MATERIAL", "7.86"));
        });

        MesMasterDataSyncService.SyncResult result = service.syncMaterialsManual(16L);

        assertThat(result.success()).isTrue();
        assertThat(result.fetchedCount()).isEqualTo(201);
        assertThat(result.validCount()).isEqualTo(201);
        assertThat(result.upsertedCount()).isEqualTo(201);
        verify(materialMapper, org.mockito.Mockito.times(201)).insert(any(DictMaterial.class));
    }

    private static MesMasterDataSyncService service(
            MesMasterDataClient client,
            DictMaterialMapper materialMapper,
            DictMaterialTypeMapper materialTypeMapper,
            MesMasterDataSyncRunMapper syncRunMapper) {
        MesMasterDataSyncService service = new MesMasterDataSyncService();
        ReflectionTestUtils.setField(service, "client", client);
        ReflectionTestUtils.setField(service, "materialMapper", materialMapper);
        ReflectionTestUtils.setField(service, "materialTypeMapper", materialTypeMapper);
        ReflectionTestUtils.setField(service, "syncRunMapper", syncRunMapper);
        ReflectionTestUtils.setField(service, "auditService", mock(AuditService.class));
        return service;
    }

    private static DictMaterialType materialType(Long id, String code, String name) {
        DictMaterialType type = new DictMaterialType();
        type.setId(id);
        type.setTypeCode(code);
        type.setTypeName(name);
        type.setEnabled(true);
        return type;
    }

    private static MesMaterialPageResp.Data page(long total, MesMaterialPageResp.Row... rows) {
        MesMaterialPageResp.Data data = new MesMaterialPageResp.Data();
        data.setTotal(total);
        data.setList(List.of(rows));
        return data;
    }

    private static MesMaterialPageResp.Row[] materialRows(String codePrefix, int start, int count) {
        MesMaterialPageResp.Row[] rows = new MesMaterialPageResp.Row[count];
        for (int index = 0; index < count; index++) {
            int sequence = start + index;
            rows[index] = row("mes-stream-" + sequence, codePrefix + sequence, "流式材料" + sequence, "MATERIAL", "7.85");
        }
        return rows;
    }

    private static MesMaterialPageResp.Row row(String id, String code, String name, String materialType, String density) {
        MesMaterialPageResp.Row row = new MesMaterialPageResp.Row();
        row.setId(id);
        row.setCode(code);
        row.setName(name);
        row.setMaterialType(materialType);
        row.setDensity(new BigDecimal(density));
        row.setStatus("ENABLE");
        return row;
    }
}
