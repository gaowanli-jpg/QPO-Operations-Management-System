package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.machining.quote.dto.SalesOrderU9ResyncResetResp;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.DataScopeService;
import com.machining.quote.service.SalesOrderU9SyncStateService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOrderU9SyncStateServiceTest {

    @Test
    void resetsSyncedOrderAndClearsPreviousU9Binding() {
        SalesOrderHeaderMapper headerMapper = mock(SalesOrderHeaderMapper.class);
        AuditService auditService = mock(AuditService.class);
        SalesOrderHeader header = header(101L, "SO-101", "已同步", "U9-ID-1", "U9-SO-1");
        when(headerMapper.selectByIdForUpdate(101L)).thenReturn(header);
        when(headerMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        SalesOrderU9SyncStateService service = service(headerMapper, auditService);

        SalesOrderU9ResyncResetResp response = service.resetForResync(101L, "已在 U9 删除原销售订单");

        assertThat(response.getSalesOrderId()).isEqualTo(101L);
        assertThat(response.getSalesOrderNo()).isEqualTo("SO-101");
        assertThat(response.getFromStatus()).isEqualTo("已同步");
        assertThat(response.getToStatus()).isEqualTo("未同步");
        assertThat(response.getPreviousU9OrderId()).isEqualTo("U9-ID-1");
        assertThat(response.getPreviousU9OrderCode()).isEqualTo("U9-SO-1");
        assertThat(header.getU9SyncStatus()).isEqualTo("未同步");
        assertThat(header.getU9OrderId()).isNull();
        assertThat(header.getU9OrderCode()).isNull();
        assertThat(header.getU9SyncAttemptId()).isNull();
        assertThat(header.getU9SyncRequestHash()).isNull();
        assertThat(header.getU9SyncStartedAt()).isNull();
        assertThat(header.getU9SyncFinishedAt()).isNull();

        ArgumentCaptor<UpdateWrapper<SalesOrderHeader>> updateCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(headerMapper).update(isNull(), updateCaptor.capture());
        assertThat(updateCaptor.getValue().getSqlSet())
                .contains("u9_sync_status")
                .contains("u9_order_id")
                .contains("u9_order_code")
                .contains("u9_sync_attempt_id")
                .contains("u9_sync_request_hash")
                .contains("u9_sync_started_at")
                .contains("u9_sync_finished_at");
        verify(auditService).record(
                eq("SALES_ORDER_U9_RESYNC_RESET"),
                eq("sales_order_header"),
                eq(101L),
                anyMap(),
                anyMap(),
                eq("MARKET"),
                eq("SUCCESS"),
                eq("销售订单已重置为未同步，可重新同步 U9"),
                isNull());
    }

    @Test
    void rejectsResetForNonSyncedOrder() {
        SalesOrderHeaderMapper headerMapper = mock(SalesOrderHeaderMapper.class);
        SalesOrderHeader header = header(101L, "SO-101", "同步失败", "", "");
        when(headerMapper.selectByIdForUpdate(101L)).thenReturn(header);
        SalesOrderU9SyncStateService service = service(headerMapper, mock(AuditService.class));

        assertThatThrownBy(() -> service.resetForResync(101L, "重试"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("只有已同步 U9");
        verify(headerMapper, never()).update(isNull(), any(UpdateWrapper.class));
    }

    private SalesOrderU9SyncStateService service(
            SalesOrderHeaderMapper headerMapper,
            AuditService auditService) {
        SalesOrderU9SyncStateService service = new SalesOrderU9SyncStateService();
        ReflectionTestUtils.setField(service, "headerMapper", headerMapper);
        ReflectionTestUtils.setField(service, "auditService", auditService);
        ReflectionTestUtils.setField(service, "dataScopeService", mock(DataScopeService.class));
        return service;
    }

    private SalesOrderHeader header(
            Long id,
            String salesOrderNo,
            String u9Status,
            String u9OrderId,
            String u9OrderCode) {
        SalesOrderHeader header = new SalesOrderHeader();
        header.setId(id);
        header.setSalesOrderNo(salesOrderNo);
        header.setU9SyncStatus(u9Status);
        header.setU9OrderId(u9OrderId);
        header.setU9OrderCode(u9OrderCode);
        return header;
    }
}
