package com.machining.quote.service.mes;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderDeliveryTicketMesRequest;
import com.machining.quote.entity.OrderDeliveryTicketMesWaiter;
import com.machining.quote.entity.SystemTask;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMesRequestMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMesWaiterMapper;
import com.machining.quote.mapper.SystemTaskMapper;
import com.machining.quote.service.SourceItemMaterialNoSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class MesMaterialProcessingRecoveryService {
    private static final Logger log = LoggerFactory.getLogger(MesMaterialProcessingRecoveryService.class);

    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_CALLBACK_PENDING = "CALLBACK_PENDING";
    private static final String STATUS_SKIPPED = "SKIPPED";
    private static final String STATUS_STALE = "STALE";
    private static final String STATUS_IGNORED = "IGNORED";
    private static final String TASK_PENDING = "PENDING";
    private static final String TASK_RUNNING = "RUNNING";

    @Autowired(required = false)
    private OrderDeliveryTicketMapper ticketMapper;

    @Autowired(required = false)
    private OrderDeliveryTicketMesRequestMapper requestMapper;

    @Autowired(required = false)
    private OrderDeliveryTicketMesWaiterMapper waiterMapper;

    @Autowired(required = false)
    private SystemTaskMapper taskMapper;

    @Autowired(required = false)
    private SourceItemMaterialNoSyncService sourceSyncService;
    @Autowired(required = false)
    private ProductionAllocationLifecycleService productionAllocationLifecycleService;

    public RecoveryResult recoverProcessing(OrderDeliveryTicket ticket, Long taskId) {
        if (ticket == null || !STATUS_PROCESSING.equalsIgnoreCase(trim(ticket.getMesSyncStatus()))) {
            return RecoveryResult.notHandled();
        }
        RecoveryContext context = resolveContext(ticket);
        if (context.request == null) {
            String message = "MES material task was interrupted; please retry";
            markTicketFailed(ticket, message);
            return RecoveryResult.handled(STATUS_FAILED, message, recoverySummary("NO_REQUEST", taskId, null));
        }
        String requestStatus = normalizeStatus(context.request.getRequestStatus());
        if (STATUS_SUCCESS.equals(requestStatus)) {
            return recoverSuccess(ticket, context, taskId);
        }
        if (STATUS_CALLBACK_PENDING.equals(requestStatus)) {
            String message = firstNonBlank(context.request.getCallbackMessage(), context.request.getResponseMessage(),
                    "MES material is waiting for callback");
            markTicketCallbackPending(ticket, context.request, message);
            markCurrentWaiter(context.waiter, STATUS_CALLBACK_PENDING, null, message);
            return RecoveryResult.handled(STATUS_CALLBACK_PENDING, message,
                    recoverySummary("REQUEST_CALLBACK_PENDING", taskId, context.request));
        }
        if (STATUS_FAILED.equals(requestStatus) || STATUS_STALE.equals(requestStatus) || STATUS_IGNORED.equals(requestStatus)) {
            String message = firstNonBlank(context.request.getCallbackMessage(), context.request.getResponseMessage(),
                    context.request.getRequestMessage(), "MES material request failed; please retry");
            markTicketFailed(ticket, message);
            markCurrentWaiter(context.waiter, STATUS_FAILED, null, message);
            return RecoveryResult.handled(STATUS_FAILED, message,
                    recoverySummary("REQUEST_FAILED", taskId, context.request));
        }
        if (isLinkedTaskActive(context.request)) {
            String message = "MES material task is processing; please refresh later or wait for callback";
            return RecoveryResult.handled(STATUS_SKIPPED, message,
                    recoverySummary("REQUEST_STILL_ACTIVE", taskId, context.request));
        }
        String message = "MES material task was interrupted; please retry";
        closeRequestAsFailed(context.request, message);
        markTicketFailed(ticket, message);
        markCurrentWaiter(context.waiter, STATUS_FAILED, null, message);
        log.warn("MES material processing recovered as failed ticketId={} taskId={} mesRequestId={} reason=interrupted",
                ticket.getId(), taskId, context.request.getMesRequestId());
        return RecoveryResult.handled(STATUS_FAILED, message,
                recoverySummary("REQUEST_INTERRUPTED", taskId, context.request));
    }

    private RecoveryResult recoverSuccess(OrderDeliveryTicket ticket, RecoveryContext context, Long taskId) {
        String materialNo = trim(context.request.getMaterialNo());
        if (isBlank(materialNo)) {
            String message = "MES material request succeeded without material number; please retry";
            closeRequestAsFailed(context.request, message);
            markTicketFailed(ticket, message);
            markCurrentWaiter(context.waiter, STATUS_FAILED, null, message);
            return RecoveryResult.handled(STATUS_FAILED, message,
                    recoverySummary("SUCCESS_WITHOUT_MATERIAL_NO", taskId, context.request));
        }
        String message = firstNonBlank(context.request.getCallbackMessage(), context.request.getResponseMessage(),
                "MES material status recovered");
        if (sourceSyncService != null) {
            sourceSyncService.syncConfirmedMaterialNo(ticket, materialNo, message);
        }
        String mesRequestId = firstNonBlank(ticket.getMesRequestId(), context.request.getMesRequestId());
        if (productionAllocationLifecycleService != null) {
            productionAllocationLifecycleService.recordMaterialSuccess(
                    ticket.getId(), materialNo, message, mesRequestId, context.request.getMesExternalTaskId(), false);
        } else {
            OrderDeliveryTicket update = new OrderDeliveryTicket();
            update.setId(ticket.getId());
            update.setMaterialNo(materialNo);
            update.setMesSyncStatus(STATUS_SUCCESS);
            update.setMesSyncMessage(message);
            update.setMesSyncedAt(LocalDateTime.now());
            update.setMesRequestId(mesRequestId);
            update.setMesExternalTaskId(trimToNull(context.request.getMesExternalTaskId()));
            if (ticketMapper != null) {
                ticketMapper.updateById(update);
            }
        }
        markCurrentWaiter(context.waiter, STATUS_SUCCESS, materialNo, message);
        log.info("MES material processing recovered as success ticketId={} taskId={} mesRequestId={} materialNo={}",
                ticket.getId(), taskId, context.request.getMesRequestId(), materialNo);
        return RecoveryResult.handled(STATUS_SUCCESS, message,
                recoverySummary("REQUEST_SUCCESS", taskId, context.request));
    }

    private RecoveryContext resolveContext(OrderDeliveryTicket ticket) {
        OrderDeliveryTicketMesWaiter waiter = loadCurrentWaiter(ticket.getId());
        String requestId = firstNonBlank(ticket.getMesRequestId(), ticket.getMesMasterRequestId(),
                waiter == null ? null : waiter.getMasterMesRequestId());
        OrderDeliveryTicketMesRequest request = loadRequestByMesRequestId(requestId);
        if (request == null && ticket.getId() != null) {
            request = loadLatestRequestByTicketId(ticket.getId());
        }
        return new RecoveryContext(request, waiter);
    }

    private OrderDeliveryTicketMesRequest loadRequestByMesRequestId(String mesRequestId) {
        if (requestMapper == null || isBlank(mesRequestId)) {
            return null;
        }
        QueryWrapper<OrderDeliveryTicketMesRequest> wrapper = new QueryWrapper<>();
        wrapper.eq("mes_request_id", mesRequestId)
                .orderByDesc("id")
                .last("LIMIT 1");
        return requestMapper.selectOne(wrapper);
    }

    private OrderDeliveryTicketMesRequest loadLatestRequestByTicketId(Integer ticketId) {
        if (requestMapper == null || ticketId == null) {
            return null;
        }
        QueryWrapper<OrderDeliveryTicketMesRequest> wrapper = new QueryWrapper<>();
        wrapper.eq("ticket_id", ticketId)
                .orderByDesc("id")
                .last("LIMIT 1");
        return requestMapper.selectOne(wrapper);
    }

    private OrderDeliveryTicketMesWaiter loadCurrentWaiter(Integer ticketId) {
        if (waiterMapper == null || ticketId == null) {
            return null;
        }
        QueryWrapper<OrderDeliveryTicketMesWaiter> wrapper = new QueryWrapper<>();
        wrapper.eq("waiter_ticket_id", ticketId)
                .orderByDesc("id")
                .last("LIMIT 1");
        return waiterMapper.selectOne(wrapper);
    }

    private boolean isLinkedTaskActive(OrderDeliveryTicketMesRequest request) {
        if (request == null || request.getSystemTaskId() == null || taskMapper == null) {
            return false;
        }
        SystemTask task = taskMapper.selectById(request.getSystemTaskId());
        if (task == null) {
            return false;
        }
        String status = normalizeStatus(task.getStatus());
        return TASK_PENDING.equals(status) || TASK_RUNNING.equals(status);
    }

    private void closeRequestAsFailed(OrderDeliveryTicketMesRequest request, String message) {
        if (requestMapper == null || request == null || isBlank(request.getMesRequestId())) {
            return;
        }
        OrderDeliveryTicketMesRequest update = new OrderDeliveryTicketMesRequest();
        update.setRequestStatus(STATUS_FAILED);
        update.setResponseMessage(abbreviate(message));
        update.setCompletedAt(LocalDateTime.now());
        update.setUpdatedAt(LocalDateTime.now());
        UpdateWrapper<OrderDeliveryTicketMesRequest> wrapper = new UpdateWrapper<>();
        wrapper.eq("mes_request_id", request.getMesRequestId())
                .in("request_status", STATUS_PROCESSING, STATUS_CALLBACK_PENDING);
        requestMapper.update(update, wrapper);
    }

    private void markTicketFailed(OrderDeliveryTicket ticket, String message) {
        if (ticketMapper == null || ticket == null || ticket.getId() == null) {
            return;
        }
        OrderDeliveryTicket update = new OrderDeliveryTicket();
        update.setId(ticket.getId());
        update.setMesSyncStatus(STATUS_FAILED);
        update.setMesSyncMessage(abbreviate(message));
        ticketMapper.updateById(update);
    }

    private void markTicketCallbackPending(
            OrderDeliveryTicket ticket,
            OrderDeliveryTicketMesRequest request,
            String message) {
        if (ticketMapper == null || ticket == null || ticket.getId() == null) {
            return;
        }
        OrderDeliveryTicket update = new OrderDeliveryTicket();
        update.setId(ticket.getId());
        update.setMesSyncStatus(STATUS_CALLBACK_PENDING);
        update.setMesSyncMessage(abbreviate(message));
        update.setMesRequestId(firstNonBlank(ticket.getMesRequestId(), request == null ? null : request.getMesRequestId()));
        update.setMesExternalTaskId(request == null ? null : trimToNull(request.getMesExternalTaskId()));
        ticketMapper.updateById(update);
    }

    private void markCurrentWaiter(OrderDeliveryTicketMesWaiter waiter, String status, String materialNo, String message) {
        if (waiterMapper == null || waiter == null || waiter.getId() == null) {
            return;
        }
        OrderDeliveryTicketMesWaiter update = new OrderDeliveryTicketMesWaiter();
        update.setWaitStatus(status);
        update.setMaterialNo(trimToNull(materialNo));
        update.setWaitMessage(abbreviate(message));
        update.setUpdatedAt(LocalDateTime.now());
        if (STATUS_SUCCESS.equals(status) || STATUS_FAILED.equals(status)) {
            update.setCompletedAt(LocalDateTime.now());
        }
        UpdateWrapper<OrderDeliveryTicketMesWaiter> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", waiter.getId());
        waiterMapper.update(update, wrapper);
    }

    private Map<String, Object> recoverySummary(String recoveryType, Long taskId, OrderDeliveryTicketMesRequest request) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("recoveredProcessing", true);
        summary.put("recoveryType", recoveryType);
        summary.put("taskId", taskId);
        if (request != null) {
            summary.put("mesRequestId", request.getMesRequestId());
            summary.put("requestStatus", request.getRequestStatus());
            summary.put("sourceTaskId", request.getSystemTaskId());
        }
        return summary;
    }

    private String normalizeStatus(String status) {
        String normalized = trim(status);
        return normalized == null ? "" : normalized.toUpperCase(Locale.ROOT);
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private String trimToNull(String value) {
        String trimmed = trim(value);
        return isBlank(trimmed) ? null : trimmed;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String abbreviate(String message) {
        if (message == null) {
            return null;
        }
        String trimmed = message.trim();
        return trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 500);
    }

    private record RecoveryContext(OrderDeliveryTicketMesRequest request, OrderDeliveryTicketMesWaiter waiter) {
    }

    public static class RecoveryResult {
        private final boolean handled;
        private final String status;
        private final String message;
        private final Map<String, Object> summary;

        private RecoveryResult(boolean handled, String status, String message, Map<String, Object> summary) {
            this.handled = handled;
            this.status = status;
            this.message = message;
            this.summary = summary;
        }

        public static RecoveryResult notHandled() {
            return new RecoveryResult(false, null, null, null);
        }

        public static RecoveryResult handled(String status, String message, Map<String, Object> summary) {
            return new RecoveryResult(true, status, message, summary);
        }

        public boolean isHandled() {
            return handled;
        }

        public String getStatus() {
            return status;
        }

        public String getMessage() {
            return message;
        }

        public Map<String, Object> getSummary() {
            return summary;
        }
    }
}
