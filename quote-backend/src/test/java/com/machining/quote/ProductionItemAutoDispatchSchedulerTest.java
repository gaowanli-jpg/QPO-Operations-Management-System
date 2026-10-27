package com.machining.quote;

import com.machining.quote.entity.SystemTask;
import com.machining.quote.service.ProductionItemAutoDispatchConfigService;
import com.machining.quote.service.ProductionItemAutoDispatchScheduler;
import com.machining.quote.service.ProductionItemAutoDispatchService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductionItemAutoDispatchSchedulerTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 27, 17, 0);

    @Test
    void doesNothingWhenPolicyIsNotDue() {
        ProductionItemAutoDispatchConfigService configService = mock(ProductionItemAutoDispatchConfigService.class);
        ProductionItemAutoDispatchService autoDispatchService = mock(ProductionItemAutoDispatchService.class);
        when(configService.now()).thenReturn(NOW);
        when(configService.shouldRun(NOW)).thenReturn(false);
        ProductionItemAutoDispatchScheduler scheduler = scheduler(configService, autoDispatchService);

        scheduler.dispatchScheduled();

        verify(configService, never()).claimDailyRun(any());
        verify(autoDispatchService, never()).submitRun(any());
    }

    @Test
    void skipsWhenPreviousRunIsStillActive() {
        ProductionItemAutoDispatchConfigService configService = mock(ProductionItemAutoDispatchConfigService.class);
        ProductionItemAutoDispatchService autoDispatchService = mock(ProductionItemAutoDispatchService.class);
        when(configService.now()).thenReturn(NOW);
        when(configService.shouldRun(NOW)).thenReturn(true);
        when(autoDispatchService.isRunning()).thenReturn(true);
        ProductionItemAutoDispatchScheduler scheduler = scheduler(configService, autoDispatchService);

        scheduler.dispatchScheduled();

        verify(configService, never()).claimDailyRun(any());
        verify(autoDispatchService, never()).submitRun(any());
    }

    @Test
    void claimsTheDayBeforeSubmitting() {
        ProductionItemAutoDispatchConfigService configService = mock(ProductionItemAutoDispatchConfigService.class);
        ProductionItemAutoDispatchService autoDispatchService = mock(ProductionItemAutoDispatchService.class);
        when(configService.now()).thenReturn(NOW);
        when(configService.shouldRun(NOW)).thenReturn(true);
        when(configService.claimDailyRun(NOW)).thenReturn(true);
        when(autoDispatchService.isRunning()).thenReturn(false);
        when(autoDispatchService.submitRun(ProductionItemAutoDispatchService.TRIGGER_SCHEDULED))
                .thenReturn(new SystemTask());
        ProductionItemAutoDispatchScheduler scheduler = scheduler(configService, autoDispatchService);

        scheduler.dispatchScheduled();

        verify(configService).claimDailyRun(NOW);
        verify(autoDispatchService).submitRun(ProductionItemAutoDispatchService.TRIGGER_SCHEDULED);
    }

    @Test
    void doesNotSubmitWhenDayIsAlreadyClaimed() {
        ProductionItemAutoDispatchConfigService configService = mock(ProductionItemAutoDispatchConfigService.class);
        ProductionItemAutoDispatchService autoDispatchService = mock(ProductionItemAutoDispatchService.class);
        when(configService.now()).thenReturn(NOW);
        when(configService.shouldRun(NOW)).thenReturn(true);
        when(configService.claimDailyRun(NOW)).thenReturn(false);
        when(autoDispatchService.isRunning()).thenReturn(false);
        ProductionItemAutoDispatchScheduler scheduler = scheduler(configService, autoDispatchService);

        scheduler.dispatchScheduled();

        verify(autoDispatchService, never()).submitRun(any());
    }

    @Test
    void rollsBackDailyClaimWhenSubmitFails() {
        ProductionItemAutoDispatchConfigService configService = mock(ProductionItemAutoDispatchConfigService.class);
        ProductionItemAutoDispatchService autoDispatchService = mock(ProductionItemAutoDispatchService.class);
        when(configService.now()).thenReturn(NOW);
        when(configService.shouldRun(NOW)).thenReturn(true);
        when(configService.claimDailyRun(NOW)).thenReturn(true);
        when(autoDispatchService.isRunning()).thenReturn(false);
        when(autoDispatchService.submitRun(any())).thenThrow(new RuntimeException("executor saturated"));
        ProductionItemAutoDispatchScheduler scheduler = scheduler(configService, autoDispatchService);

        scheduler.dispatchScheduled();

        verify(configService).resetDailyRunClaim();
    }

    private ProductionItemAutoDispatchScheduler scheduler(
            ProductionItemAutoDispatchConfigService configService,
            ProductionItemAutoDispatchService autoDispatchService) {
        ProductionItemAutoDispatchScheduler scheduler = new ProductionItemAutoDispatchScheduler();
        ReflectionTestUtils.setField(scheduler, "configService", configService);
        ReflectionTestUtils.setField(scheduler, "autoDispatchService", autoDispatchService);
        return scheduler;
    }
}
