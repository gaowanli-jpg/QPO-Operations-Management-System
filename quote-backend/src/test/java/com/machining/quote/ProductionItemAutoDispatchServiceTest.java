package com.machining.quote;

import com.machining.quote.entity.ProductionItemAutoDispatchConfig;
import com.machining.quote.entity.SystemTask;
import com.machining.quote.mapper.OrderDeliveryTicketAllocationMapper;
import com.machining.quote.mapper.SystemTaskMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.ProductionItemAutoDispatchConfigService;
import com.machining.quote.service.ProductionItemAutoDispatchResult;
import com.machining.quote.service.ProductionItemAutoDispatchService;
import com.machining.quote.service.mes.MesProductionDispatchService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductionItemAutoDispatchServiceTest {

    @Test
    void keepsLoopingUntilNoCandidatesRemain() {
        Fixture fixture = fixture(config(10, 1, 5));
        when(fixture.allocationMapper.selectAutoDispatchCandidateIds(anyInt()))
                .thenReturn(List.of(1, 2), List.of(3, 4), List.of());
        when(fixture.dispatchService.dispatchBatch(anyList(), anyLong()))
                .thenReturn(batchResult(2, 0, 0, 0));

        fixture.service.submitRun(ProductionItemAutoDispatchService.TRIGGER_SCHEDULED);

        verify(fixture.dispatchService, times(2)).dispatchBatch(anyList(), anyLong());
        verify(fixture.configService).updateResult(eq(1L), eq("SUCCESS"), any(), any(ProductionItemAutoDispatchResult.class));
        assertThat(fixture.service.isRunning()).isFalse();
    }

    @Test
    void doesNotStopAfterFirstBatchWhenCandidatesRemain() {
        Fixture fixture = fixture(config(10, 1, 5));
        when(fixture.allocationMapper.selectAutoDispatchCandidateIds(anyInt()))
                .thenReturn(List.of(1), List.of(2), List.of(3), List.of(4), List.of());
        when(fixture.dispatchService.dispatchBatch(anyList(), anyLong()))
                .thenReturn(batchResult(1, 0, 0, 0));

        fixture.service.submitRun(ProductionItemAutoDispatchService.TRIGGER_SCHEDULED);

        verify(fixture.dispatchService, times(4)).dispatchBatch(anyList(), anyLong());
        ArgumentCaptor<ProductionItemAutoDispatchResult> captor =
                ArgumentCaptor.forClass(ProductionItemAutoDispatchResult.class);
        verify(fixture.configService).updateResult(eq(1L), eq("SUCCESS"), any(), captor.capture());
        assertThat(captor.getValue().total()).isEqualTo(4);
        assertThat(captor.getValue().rounds()).isEqualTo(4);
    }

    @Test
    void tripsFailureBreakerWhenEveryRoundFails() {
        Fixture fixture = fixture(config(10, 1, 3));
        when(fixture.allocationMapper.selectAutoDispatchCandidateIds(anyInt()))
                .thenReturn(List.of(1, 2));
        when(fixture.dispatchService.dispatchBatch(anyList(), anyLong()))
                .thenReturn(batchResult(0, 2, 0, 0));

        fixture.service.submitRun(ProductionItemAutoDispatchService.TRIGGER_SCHEDULED);

        verify(fixture.dispatchService, times(3)).dispatchBatch(anyList(), anyLong());
        verify(fixture.configService).updateResult(eq(1L), eq("FAILED"), any(), any(ProductionItemAutoDispatchResult.class));
    }

    @Test
    void resetsFailureStreakWhenARoundSucceeds() {
        Fixture fixture = fixture(config(10, 1, 3));
        when(fixture.allocationMapper.selectAutoDispatchCandidateIds(anyInt()))
                .thenReturn(List.of(1), List.of(2), List.of(3), List.of(4), List.of(5), List.of(6), List.of());
        when(fixture.dispatchService.dispatchBatch(anyList(), anyLong()))
                .thenReturn(batchResult(0, 1, 0, 0))
                .thenReturn(batchResult(0, 1, 0, 0))
                .thenReturn(batchResult(1, 0, 0, 0))
                .thenReturn(batchResult(0, 1, 0, 0))
                .thenReturn(batchResult(0, 1, 0, 0))
                .thenReturn(batchResult(0, 1, 0, 0));

        fixture.service.submitRun(ProductionItemAutoDispatchService.TRIGGER_SCHEDULED);

        verify(fixture.dispatchService, times(6)).dispatchBatch(anyList(), anyLong());
    }

    @Test
    void stopsAtMaxRoundsAndMarksPartial() {
        Fixture fixture = fixture(config(2, 1, 10));
        when(fixture.allocationMapper.selectAutoDispatchCandidateIds(anyInt()))
                .thenReturn(List.of(1, 2));
        when(fixture.dispatchService.dispatchBatch(anyList(), anyLong()))
                .thenReturn(batchResult(2, 0, 0, 0));

        fixture.service.submitRun(ProductionItemAutoDispatchService.TRIGGER_SCHEDULED);

        verify(fixture.dispatchService, times(2)).dispatchBatch(anyList(), anyLong());
        verify(fixture.configService).updateResult(eq(1L), eq("PARTIAL"), any(), any(ProductionItemAutoDispatchResult.class));
    }

    @Test
    void marksPartialWhenSomeRowsFailed() {
        Fixture fixture = fixture(config(10, 1, 5));
        when(fixture.allocationMapper.selectAutoDispatchCandidateIds(anyInt()))
                .thenReturn(List.of(1, 2), List.of());
        when(fixture.dispatchService.dispatchBatch(anyList(), anyLong()))
                .thenReturn(batchResult(1, 1, 0, 0));

        fixture.service.submitRun(ProductionItemAutoDispatchService.TRIGGER_SCHEDULED);

        verify(fixture.configService).updateResult(eq(1L), eq("PARTIAL"), any(), any(ProductionItemAutoDispatchResult.class));
    }

    @Test
    void countsResultUnknownAsFailureSoItSurfacesInStatus() {
        Fixture fixture = fixture(config(10, 1, 5));
        when(fixture.allocationMapper.selectAutoDispatchCandidateIds(anyInt()))
                .thenReturn(List.of(1), List.of());
        when(fixture.dispatchService.dispatchBatch(anyList(), anyLong()))
                .thenReturn(batchResult(0, 0, 0, 1));

        fixture.service.submitRun(ProductionItemAutoDispatchService.TRIGGER_SCHEDULED);

        ArgumentCaptor<ProductionItemAutoDispatchResult> captor =
                ArgumentCaptor.forClass(ProductionItemAutoDispatchResult.class);
        verify(fixture.configService).updateResult(eq(1L), eq("PARTIAL"), any(), captor.capture());
        assertThat(captor.getValue().failed()).isEqualTo(1);
    }

    @Test
    void continuesNextRoundWhenDispatchBatchThrows() {
        Fixture fixture = fixture(config(10, 1, 5));
        when(fixture.allocationMapper.selectAutoDispatchCandidateIds(anyInt()))
                .thenReturn(List.of(1), List.of(2), List.of());
        when(fixture.dispatchService.dispatchBatch(anyList(), anyLong()))
                .thenThrow(new RuntimeException("data center unavailable"))
                .thenReturn(batchResult(1, 0, 0, 0));

        fixture.service.submitRun(ProductionItemAutoDispatchService.TRIGGER_SCHEDULED);

        verify(fixture.dispatchService, times(2)).dispatchBatch(anyList(), anyLong());
        verify(fixture.configService).updateResult(eq(1L), eq("PARTIAL"), any(), any(ProductionItemAutoDispatchResult.class));
    }

    @Test
    void rejectsSecondConcurrentRun() {
        Fixture fixture = fixture(config(10, 1, 5));
        ReflectionTestUtils.setField(fixture.service, "running", new java.util.concurrent.atomic.AtomicBoolean(true));

        assertThatThrownBy(() -> fixture.service.submitRun(ProductionItemAutoDispatchService.TRIGGER_MANUAL))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("正在执行中");
        verify(fixture.taskMapper, never()).insert(any(SystemTask.class));
    }

    @Test
    void scheduledTaskIsAttributedToSystem() {
        Fixture fixture = fixture(config(10, 1, 5));
        when(fixture.allocationMapper.selectAutoDispatchCandidateIds(anyInt())).thenReturn(List.of());

        fixture.service.submitRun(ProductionItemAutoDispatchService.TRIGGER_SCHEDULED);

        ArgumentCaptor<SystemTask> captor = ArgumentCaptor.forClass(SystemTask.class);
        verify(fixture.taskMapper).insert(captor.capture());
        assertThat(captor.getValue().getTaskType()).isEqualTo("MES_PRODUCTION_AUTO_DISPATCH");
        assertThat(captor.getValue().getCreatedBy()).isEqualTo("SYSTEM");
        assertThat(captor.getValue().getStatus()).isEqualTo("PENDING");
    }

    private Map<String, Object> batchResult(int success, int failed, int skipped, int unknown) {
        return Map.of(
                "successCount", success,
                "failedCount", failed,
                "skippedCount", skipped,
                "unknownCount", unknown
        );
    }

    private ProductionItemAutoDispatchConfig config(int maxRounds, int intervalMs, int failureBreaker) {
        ProductionItemAutoDispatchConfig config = new ProductionItemAutoDispatchConfig();
        config.setId(ProductionItemAutoDispatchConfigService.CONFIG_ID);
        config.setEnabled(true);
        config.setScheduleMode("DAILY_TIME");
        config.setBatchSize(200);
        config.setBatchIntervalMs(intervalMs);
        config.setMaxRounds(maxRounds);
        config.setWarnDurationMs(3_600_000L);
        config.setFailureBreaker(failureBreaker);
        config.setIncludeFailed(false);
        return config;
    }

    private Fixture fixture(ProductionItemAutoDispatchConfig config) {
        OrderDeliveryTicketAllocationMapper allocationMapper = mock(OrderDeliveryTicketAllocationMapper.class);
        MesProductionDispatchService dispatchService = mock(MesProductionDispatchService.class);
        SystemTaskMapper taskMapper = mock(SystemTaskMapper.class);
        ProductionItemAutoDispatchConfigService configService = mock(ProductionItemAutoDispatchConfigService.class);
        when(configService.loadConfig()).thenReturn(config);
        when(allocationMapper.countAutoDispatchCandidates()).thenReturn(0L);
        doAnswer(invocation -> {
            SystemTask task = invocation.getArgument(0);
            task.setId(1L);
            return 1;
        }).when(taskMapper).insert(any(SystemTask.class));

        ProductionItemAutoDispatchService service = new ProductionItemAutoDispatchService();
        ReflectionTestUtils.setField(service, "allocationMapper", allocationMapper);
        ReflectionTestUtils.setField(service, "mesProductionDispatchService", dispatchService);
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "configService", configService);
        ReflectionTestUtils.setField(service, "auditService", mock(AuditService.class));
        ReflectionTestUtils.setField(service, "executor", (TaskExecutor) Runnable::run);
        return new Fixture(service, allocationMapper, dispatchService, taskMapper, configService);
    }

    private record Fixture(
            ProductionItemAutoDispatchService service,
            OrderDeliveryTicketAllocationMapper allocationMapper,
            MesProductionDispatchService dispatchService,
            SystemTaskMapper taskMapper,
            ProductionItemAutoDispatchConfigService configService) {
    }
}
