package com.machining.quote;

import com.machining.quote.dto.mes.MesDemandCancelTarget;
import com.machining.quote.dto.mes.MesDemandDeleteResp;
import com.machining.quote.mapper.MesProductionBatchItemMapper;
import com.machining.quote.mapper.OrderDeliveryTicketAllocationMapper;
import com.machining.quote.service.DataCenterCallException;
import com.machining.quote.service.DataCenterEndpointRegistry;
import com.machining.quote.service.mes.MesDemandCancelService;
import com.machining.quote.service.mes.MesDemandDeleteClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MesDemandCancelServiceTest {

    private OrderDeliveryTicketAllocationMapper allocationMapper;
    private MesProductionBatchItemMapper batchItemMapper;
    private MesDemandDeleteClient client;
    private MesDemandCancelService service;

    @BeforeEach
    void setUp() {
        allocationMapper = mock(OrderDeliveryTicketAllocationMapper.class);
        batchItemMapper = mock(MesProductionBatchItemMapper.class);
        client = mock(MesDemandDeleteClient.class);
        service = new MesDemandCancelService(allocationMapper, batchItemMapper, client, null);
        when(allocationMapper.markMesDemandCancelStatus(any(), anyString(), any())).thenReturn(1);
        when(batchItemMapper.markCancelledById(anyLong(), anyString(), any())).thenReturn(1);
    }

    @Test
    void successCodeMarksAllocationCancelled() {
        when(allocationMapper.selectMesDemandCancelTargets(7L)).thenReturn(List.of(target(1L, 11, "1001")));
        when(client.delete(anyString(), eq(1001L))).thenReturn(resp(0));

        Map<String, Object> summary = service.cancel(7L, "客户取消");

        assertThat(summary).containsEntry("demandTaskCount", 1).containsEntry("cancelledCount", 1);
        verify(batchItemMapper).markCancelledById(1L, "CANCELLED", "MES 需求任务已删除");
        verify(allocationMapper).markMesDemandCancelStatus(11, "CANCELLED", "MES 需求任务已删除");
    }

    @Test
    void idempotentSuccessAndNotFoundAreTreatedAsCancelled() {
        when(allocationMapper.selectMesDemandCancelTargets(7L)).thenReturn(List.of(
                target(1L, 11, "1001"),
                target(2L, 12, "1002")));
        when(client.delete(anyString(), eq(1001L))).thenReturn(resp("1030900002"));
        when(client.delete(anyString(), eq(1002L))).thenReturn(resp("1030700000"));

        Map<String, Object> summary = service.cancel(7L, "客户取消");

        assertThat(summary).containsEntry("cancelledCount", 2).containsEntry("failedCount", 0);
        verify(allocationMapper).markMesDemandCancelStatus(11, "CANCELLED", "MES 需求任务已删除");
        verify(allocationMapper).markMesDemandCancelStatus(12, "CANCELLED", "MES 需求任务已删除");
    }

    @Test
    void stateRejectedMarksFailedWithoutRetry() {
        when(allocationMapper.selectMesDemandCancelTargets(7L)).thenReturn(List.of(target(1L, 11, "1001")));
        when(client.delete(anyString(), eq(1001L))).thenReturn(resp("1030700006"));

        Map<String, Object> summary = service.cancel(7L, "客户取消");

        assertThat(summary).containsEntry("failedCount", 1);
        verify(client, times(1)).delete(anyString(), eq(1001L));
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(allocationMapper).markMesDemandCancelStatus(eq(11), eq("CANCEL_FAILED"), message.capture());
        assertThat(message.getValue()).contains("1030700006");
    }

    @Test
    void timeoutRetriesWithSameBizId() {
        when(allocationMapper.selectMesDemandCancelTargets(7L)).thenReturn(List.of(target(1L, 11, "1001")));
        when(client.delete(anyString(), eq(1001L)))
                .thenThrow(unknownResult())
                .thenReturn(resp(0));

        Map<String, Object> summary = service.cancel(7L, "客户取消");

        assertThat(summary).containsEntry("cancelledCount", 1);
        ArgumentCaptor<String> bizIds = ArgumentCaptor.forClass(String.class);
        verify(client, times(2)).delete(bizIds.capture(), eq(1001L));
        List<String> captured = bizIds.getAllValues();
        assertThat(captured).hasSize(2);
        assertThat(captured.get(0)).isEqualTo(captured.get(1));
        assertThat(captured.get(0)).startsWith("QPO-DEMDEL-");
    }

    @Test
    void twoUnknownAttemptsMarkFailedWithUnknownResult() {
        when(allocationMapper.selectMesDemandCancelTargets(7L)).thenReturn(List.of(target(1L, 11, "1001")));
        when(client.delete(anyString(), eq(1001L))).thenThrow(unknownResult());

        Map<String, Object> summary = service.cancel(7L, "客户取消");

        assertThat(summary).containsEntry("failedCount", 1);
        verify(client, times(2)).delete(anyString(), eq(1001L));
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(allocationMapper).markMesDemandCancelStatus(eq(11), eq("CANCEL_FAILED"), message.capture());
        assertThat(message.getValue()).contains("结果未知");
    }

    @Test
    void allocationAggregatesFailedWhenAnySuccessfulRecordFails() {
        when(allocationMapper.selectMesDemandCancelTargets(7L)).thenReturn(List.of(
                target(1L, 11, "1001"),
                target(2L, 11, "1002")));
        when(client.delete(anyString(), eq(1001L))).thenReturn(resp(0));
        when(client.delete(anyString(), eq(1002L))).thenReturn(resp("1030700006"));

        service.cancel(7L, "客户取消");

        verify(allocationMapper).markMesDemandCancelStatus(eq(11), eq("CANCEL_FAILED"), anyString());
        verify(allocationMapper, never()).markMesDemandCancelStatus(eq(11), eq("CANCELLED"), anyString());
    }

    @Test
    void consecutiveTransportFailuresStopRemainingRows() {
        List<MesDemandCancelTarget> targets = new ArrayList<>();
        for (int index = 0; index < 6; index++) {
            targets.add(target((long) index + 1, 100 + index, String.valueOf(2000 + index)));
        }
        when(allocationMapper.selectMesDemandCancelTargets(7L)).thenReturn(targets);
        when(client.delete(anyString(), anyLong())).thenThrow(unknownResult());

        Map<String, Object> summary = service.cancel(7L, "客户取消");

        assertThat(summary).containsEntry("demandTaskCount", 6).containsEntry("failedCount", 6);
        // 前 5 条各重试一次共 10 次调用，第 6 条被熔断跳过。
        verify(client, times(10)).delete(anyString(), anyLong());
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(allocationMapper).markMesDemandCancelStatus(eq(105), eq("CANCEL_FAILED"), message.capture());
        assertThat(message.getValue()).contains("MES 不可用，未执行");
    }

    @Test
    void noTargetsSkipsMesCall() {
        when(allocationMapper.selectMesDemandCancelTargets(7L)).thenReturn(List.of());

        Map<String, Object> summary = service.cancel(7L, "客户取消");

        assertThat(summary).containsEntry("demandTaskCount", 0);
        verify(client, never()).delete(anyString(), any());
        verify(allocationMapper, never()).markMesDemandCancelStatus(any(), anyString(), any());
    }

    @Test
    void reopenCancelledDelegatesToMapper() {
        when(allocationMapper.reopenMesDemandCancelled(7L)).thenReturn(3);

        assertThat(service.reopenCancelled(7L)).isEqualTo(3);
        assertThat(service.reopenCancelled(null)).isZero();
    }

    @Test
    void responseCodeNormalizesUnderscoredDigits() {
        MesDemandDeleteResp resp = new MesDemandDeleteResp();
        resp.setCode("1_030_900_002");

        assertThat(resp.isIdempotentSuccess()).isTrue();
        assertThat(resp.isDeleted()).isTrue();
    }

    private MesDemandCancelTarget target(Long batchItemId, Integer allocationId, String mesRecordId) {
        MesDemandCancelTarget target = new MesDemandCancelTarget();
        target.setBatchItemId(batchItemId);
        target.setAllocationId(allocationId);
        target.setMesRecordId(mesRecordId);
        target.setBizId("QPO-DEM-20261030-ABCDEF123456");
        return target;
    }

    private MesDemandDeleteResp resp(Object code) {
        MesDemandDeleteResp resp = new MesDemandDeleteResp();
        resp.setCode(code);
        return resp;
    }

    private DataCenterCallException unknownResult() {
        return new DataCenterCallException(
                DataCenterEndpointRegistry.MES_DEMAND_DELETE,
                DataCenterCallException.FailureCertainty.RESULT_UNKNOWN,
                null,
                "Data center call failed message=read timed out",
                null);
    }
}
