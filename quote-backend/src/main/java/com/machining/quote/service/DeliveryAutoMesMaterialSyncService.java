package com.machining.quote.service;

import com.machining.quote.dto.ReleaseSourceType;
import com.machining.quote.entity.SystemTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class DeliveryAutoMesMaterialSyncService {
    private static final Logger log = LoggerFactory.getLogger(DeliveryAutoMesMaterialSyncService.class);

    @Autowired(required = false)
    private SystemTaskService systemTaskService;

    public void submitAfterCommit(
            ReleaseSourceType sourceType,
            Long sourceOrderId,
            List<Integer> ticketIds,
            String triggerScene) {
        List<Integer> normalizedTicketIds = normalizeTicketIds(ticketIds);
        if (normalizedTicketIds.isEmpty()) {
            log.info("Auto MES material sync skipped empty tickets triggerScene={} sourceType={} sourceOrderId={}",
                    triggerScene, sourceType, sourceOrderId);
            return;
        }

        Runnable trigger = () -> submitTask(sourceType, sourceOrderId, normalizedTicketIds, triggerScene);
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    trigger.run();
                }
            });
            log.info("Auto MES material sync registered after commit triggerScene={} sourceType={} sourceOrderId={} ticketCount={}",
                    triggerScene, sourceType, sourceOrderId, normalizedTicketIds.size());
            return;
        }

        trigger.run();
    }

    private void submitTask(
            ReleaseSourceType sourceType,
            Long sourceOrderId,
            List<Integer> ticketIds,
            String triggerScene) {
        if (systemTaskService == null) {
            log.warn("Auto MES material sync skipped unavailable task service triggerScene={} sourceType={} sourceOrderId={} ticketCount={}",
                    triggerScene, sourceType, sourceOrderId, ticketIds.size());
            return;
        }
        try {
            SystemTask task = systemTaskService.submitMesMaterialBatchSyncTask(ticketIds);
            Long taskId = task == null ? null : task.getId();
            log.info("Auto MES material sync submitted triggerScene={} sourceType={} sourceOrderId={} ticketCount={} taskId={}",
                    triggerScene, sourceType, sourceOrderId, ticketIds.size(), taskId);
        } catch (RuntimeException ex) {
            log.warn("Auto MES material sync submit failed triggerScene={} sourceType={} sourceOrderId={} ticketCount={} reason={}",
                    triggerScene, sourceType, sourceOrderId, ticketIds.size(), ex.getMessage());
        }
    }

    private List<Integer> normalizeTicketIds(List<Integer> ticketIds) {
        if (ticketIds == null || ticketIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Integer> uniqueIds = new LinkedHashSet<>();
        for (Integer ticketId : ticketIds) {
            if (ticketId != null && ticketId > 0) {
                uniqueIds.add(ticketId);
            }
        }
        return new ArrayList<>(uniqueIds);
    }
}
