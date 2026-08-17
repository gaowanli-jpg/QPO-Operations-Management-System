package com.machining.quote;

import com.machining.quote.dto.ReleaseSourceType;
import com.machining.quote.entity.SystemTask;
import com.machining.quote.service.DeliveryAutoMesMaterialSyncService;
import com.machining.quote.service.SystemTaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DeliveryAutoMesMaterialSyncServiceTest {

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void submitAfterCommitImmediatelySubmitsWhenNoTransaction() {
        SystemTaskService taskService = mock(SystemTaskService.class);
        when(taskService.submitMesMaterialBatchSyncTask(List.of(3, 2, 4))).thenReturn(task(9001L));
        DeliveryAutoMesMaterialSyncService service = service(taskService);

        service.submitAfterCommit(
                ReleaseSourceType.RFQ,
                101L,
                Arrays.asList(3, 2, 3, null, 0, -1, 4),
                "RFQ_RELEASE");

        ArgumentCaptor<List<Integer>> captor = ArgumentCaptor.forClass(List.class);
        verify(taskService).submitMesMaterialBatchSyncTask(captor.capture());
        assertThat(captor.getValue()).containsExactly(3, 2, 4);
    }

    @Test
    void submitAfterCommitSkipsEmptyTicketIds() {
        SystemTaskService taskService = mock(SystemTaskService.class);
        DeliveryAutoMesMaterialSyncService service = service(taskService);

        service.submitAfterCommit(ReleaseSourceType.RFQ, 101L, Arrays.asList(null, 0, -3), "RFQ_RELEASE");

        verify(taskService, never()).submitMesMaterialBatchSyncTask(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void submitAfterCommitDefersSubmissionUntilTransactionCommit() {
        SystemTaskService taskService = mock(SystemTaskService.class);
        when(taskService.submitMesMaterialBatchSyncTask(List.of(8, 9))).thenReturn(task(9002L));
        DeliveryAutoMesMaterialSyncService service = service(taskService);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        service.submitAfterCommit(ReleaseSourceType.RFQ, 101L, List.of(8, 9), "RFQ_RELEASE");

        verifyNoInteractions(taskService);
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertThat(synchronizations).hasSize(1);

        synchronizations.get(0).afterCommit();

        verify(taskService).submitMesMaterialBatchSyncTask(List.of(8, 9));
    }

    @Test
    void submitAfterCommitSwallowsSystemTaskFailure() {
        SystemTaskService taskService = mock(SystemTaskService.class);
        doThrow(new RuntimeException("task submit failed"))
                .when(taskService).submitMesMaterialBatchSyncTask(List.of(6));
        DeliveryAutoMesMaterialSyncService service = service(taskService);

        assertThatCode(() -> service.submitAfterCommit(ReleaseSourceType.RFQ, 101L, List.of(6), "RFQ_RELEASE"))
                .doesNotThrowAnyException();

        verify(taskService).submitMesMaterialBatchSyncTask(List.of(6));
    }

    private DeliveryAutoMesMaterialSyncService service(SystemTaskService taskService) {
        DeliveryAutoMesMaterialSyncService service = new DeliveryAutoMesMaterialSyncService();
        ReflectionTestUtils.setField(service, "systemTaskService", taskService);
        return service;
    }

    private SystemTask task(Long id) {
        SystemTask task = new SystemTask();
        task.setId(id);
        return task;
    }
}
