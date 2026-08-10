package com.machining.quote;

import com.machining.quote.service.SalesOrderDirtySnapshotRepairRunner;
import com.machining.quote.service.SalesOrderDirtySnapshotRepairService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.ConfigurableApplicationContext;

import java.time.LocalDateTime;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOrderDirtySnapshotRepairRunnerTest {
    @Test
    void dryRunRunnerPreviewsWithoutApplyingRepair() throws Exception {
        SalesOrderDirtySnapshotRepairService service = mock(SalesOrderDirtySnapshotRepairService.class);
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        SalesOrderDirtySnapshotRepairRunner runner = new SalesOrderDirtySnapshotRepairRunner(service, context);
        runner.setSalesOrderId(56L);
        runner.setExecutor("ops-user");
        runner.setApply(false);
        runner.setExitAfterRun(false);
        when(service.preview(56L, "ops-user")).thenReturn(result(false));

        runner.run(mock(ApplicationArguments.class));

        verify(service).preview(56L, "ops-user");
        verify(service, never()).repair(56L, "ops-user");
    }

    @Test
    void applyRunnerPreviewsBeforeApplyingRepair() throws Exception {
        SalesOrderDirtySnapshotRepairService service = mock(SalesOrderDirtySnapshotRepairService.class);
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        SalesOrderDirtySnapshotRepairRunner runner = new SalesOrderDirtySnapshotRepairRunner(service, context);
        runner.setSalesOrderId(56L);
        runner.setExecutor("ops-user");
        runner.setApply(true);
        runner.setExitAfterRun(false);
        when(service.preview(56L, "ops-user")).thenReturn(result(false));
        when(service.repair(56L, "ops-user")).thenReturn(result(true));

        runner.run(mock(ApplicationArguments.class));

        var inOrder = org.mockito.Mockito.inOrder(service);
        inOrder.verify(service).preview(56L, "ops-user");
        inOrder.verify(service).repair(56L, "ops-user");
    }

    private SalesOrderDirtySnapshotRepairService.RepairResult result(boolean applied) {
        return new SalesOrderDirtySnapshotRepairService.RepairResult(
                applied,
                "ops-user",
                LocalDateTime.of(2026, 8, 10, 17, 0),
                12,
                1,
                10,
                applied ? 10 : 0,
                10,
                applied ? 10 : 0,
                Map.of());
    }
}
