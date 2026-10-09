package com.machining.quote.service.wms;

import com.machining.quote.dto.wms.WmsSaleLadingReserveResp;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderDeliveryTicketAllocation;
import com.machining.quote.entity.WmsReservationBatch;
import com.machining.quote.entity.WmsReservationTicket;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderDeliveryTicketAllocationMapper;
import com.machining.quote.mapper.WmsReservationBatchMapper;
import com.machining.quote.mapper.WmsReservationTicketMapper;
import com.machining.quote.service.DataCenterCallException;
import com.machining.quote.service.DataScopeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class WmsReservationBatchService {
    private static final Logger log = LoggerFactory.getLogger(WmsReservationBatchService.class);

    private static final String TASK_TYPE = "WMS_ALLOCATION_BATCH";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_RESULT_UNKNOWN = "RESULT_UNKNOWN";
    private static final String STATUS_APPLY_FAILED = "APPLY_FAILED";
    private static final String STATUS_SKIPPED = "SKIPPED";
    private static final String STATUS_RESPONSE_RECEIVED = "RESPONSE_RECEIVED";

    @Autowired
    private WmsReservationPlanService planService;
    @Autowired
    private WmsReservationStateService stateService;
    @Autowired
    private WmsReservationTicketMapper ticketMapper;
    @Autowired
    private WmsReservationBatchMapper batchMapper;
    @Autowired
    private OrderDeliveryTicketMapper ticketSourceMapper;
    @Autowired
    private OrderDeliveryTicketAllocationMapper allocationMapper;
    @Autowired(required = false)
    private DataScopeService dataScopeService;

    public Map<String, Object> allocateBatch(List<Integer> ticketIds, Long taskId) {
        List<Integer> normalizedTicketIds = normalizeTicketIds(ticketIds);
        long startedAt = System.currentTimeMillis();
        log.info("WMS reservation batch started taskId={} batchId={} externalBizId={} saleOrderNo={} count={} status={}",
                taskId, null, null, null, normalizedTicketIds.size(), "STARTED");
        List<Row> rows = new ArrayList<>();
        LinkedHashSet<Long> recoveredBatchIds = new LinkedHashSet<>();
        List<Integer> newTicketIds = new ArrayList<>();

        for (Integer ticketId : normalizedTicketIds) {
            WmsReservationTicket active = ticketMapper.selectActiveByTicketId(ticketId);
            if (active != null && active.getBatchId() != null && !STATUS_FAILED.equalsIgnoreCase(active.getStatus())) {
                recoveredBatchIds.add(active.getBatchId());
            } else {
                newTicketIds.add(ticketId);
            }
        }

        for (Long batchId : recoveredBatchIds) {
            rows.addAll(recoverBatch(batchId));
        }

        List<Integer> candidateTicketIds = new ArrayList<>();
        for (Integer ticketId : newTicketIds) {
            String skipReason = newBatchSkipReason(ticketId);
            if (skipReason != null) {
                rows.add(Row.skipped(ticketId, null, null, null, skipReason));
            } else {
                candidateTicketIds.add(ticketId);
            }
        }
        if (!candidateTicketIds.isEmpty()) {
            rows.addAll(createAndSendBatches(candidateTicketIds, taskId));
        }
        Map<String, Object> summary = summary(taskId, rows);
        log.info("WMS reservation batch completed taskId={} batchId={} externalBizId={} saleOrderNo={} count={} status={} durationMs={} batchCount={} successCount={} failedCount={} unknownCount={} applyFailedCount={} skippedCount={}",
                taskId,
                null,
                null,
                null,
                normalizedTicketIds.size(),
                "COMPLETED",
                durationMs(startedAt),
                summary.get("batchCount"),
                summary.get("successCount"),
                summary.get("failedCount"),
                summary.get("unknownCount"),
                summary.get("applyFailedCount"),
                summary.get("skippedCount"));
        return summary;
    }

    private List<Row> createAndSendBatches(List<Integer> ticketIds, Long taskId) {
        WmsReservationPlanService.ReservationPlan plan = planService.prepare(ticketIds);
        List<Row> rows = new ArrayList<>();
        for (WmsReservationPlanService.ReservationGroup group : plan.getGroups()) {
            rows.addAll(sendNewGroup(taskId, group));
        }
        for (WmsReservationPlanService.RowFailure failure : plan.getFailures()) {
            rows.add(Row.failed(failure.getTicketId(), null, null, null, failure.getMessage()));
        }
        return rows;
    }

    private List<Row> sendNewGroup(Long taskId, WmsReservationPlanService.ReservationGroup group) {
        Long batchId = null;
        String externalBizId = null;
        long startedAt = System.currentTimeMillis();
        try {
            WmsReservationStateService.ReservationBatchContext context = stateService.reserveBatch(taskId, group);
            batchId = context.batchId();
            externalBizId = context.externalBizId();
            WmsSaleLadingReserveResp response = stateService.sendWithReplay(batchId);
            stateService.applyResponse(batchId);
            log.info("WMS reservation group completed taskId={} batchId={} externalBizId={} saleOrderNo={} count={} status={} durationMs={}",
                    taskId, batchId, externalBizId, group.getSaleNo(), group.getTickets().size(), STATUS_SUCCESS, durationMs(startedAt));
            return groupRows(group, STATUS_SUCCESS, batchId, externalBizId, message(response, "WMS reservation applied"));
        } catch (RuntimeException error) {
            log.warn("WMS reservation group failed taskId={} batchId={} externalBizId={} saleOrderNo={} count={} status={} durationMs={} message={}",
                    taskId, batchId, externalBizId, group.getSaleNo(), group.getTickets().size(), statusFor(error), durationMs(startedAt), safeMessage(error));
            return groupRows(group, statusFor(error), batchId, externalBizId, safeMessage(error));
        }
    }

    private List<Row> recoverBatch(Long batchId) {
        long startedAt = System.currentTimeMillis();
        WmsReservationBatch batch = batchMapper.selectById(batchId);
        List<WmsReservationTicket> tickets = ticketMapper.selectByBatchId(batchId);
        assertCanAccessReservationTickets(tickets);
        String status = normalizeStatus(batch == null ? null : batch.getStatus());
        String saleNo = batch == null ? null : batch.getSaleOrderNo();
        String externalBizId = batch == null ? null : batch.getExternalBizId();
        try {
            if (STATUS_RESULT_UNKNOWN.equals(status)) {
                WmsSaleLadingReserveResp response = stateService.sendWithReplay(batchId);
                stateService.applyResponse(batchId);
                return ticketRows(tickets, STATUS_SUCCESS, batchId, saleNo, externalBizId, message(response, "WMS reservation replay applied"));
            }
            if (STATUS_RESPONSE_RECEIVED.equals(status) || STATUS_APPLY_FAILED.equals(status)) {
                stateService.applyResponse(batchId);
                return ticketRows(tickets, STATUS_SUCCESS, batchId, saleNo, externalBizId, "WMS reservation local apply replayed");
            }
            if (STATUS_SUCCESS.equals(status)) {
                return ticketRows(tickets, STATUS_SKIPPED, batchId, saleNo, externalBizId, "WMS reservation already completed");
            }
            if (STATUS_FAILED.equals(status)) {
                return ticketRows(tickets, STATUS_FAILED, batchId, saleNo, externalBizId, "WMS reservation failed; create a new batch after data correction");
            }
            return ticketRows(tickets, STATUS_RESULT_UNKNOWN, batchId, saleNo, externalBizId, "WMS reservation batch status requires review: " + status);
        } catch (RuntimeException error) {
            log.warn("WMS reservation recovery failed taskId={} batchId={} externalBizId={} saleOrderNo={} count={} status={} durationMs={} message={}",
                    null, batchId, externalBizId, saleNo, tickets == null ? 0 : tickets.size(), statusFor(error), durationMs(startedAt), safeMessage(error));
            return ticketRows(tickets, statusFor(error), batchId, saleNo, externalBizId, safeMessage(error));
        }
    }

    private void assertCanAccessReservationTickets(List<WmsReservationTicket> tickets) {
        if (dataScopeService == null || tickets == null) {
            return;
        }
        for (WmsReservationTicket reservationTicket : tickets) {
            OrderDeliveryTicket ticket = ticketSourceMapper.selectById(reservationTicket.getTicketId());
            dataScopeService.assertCanAccessDeliveryTicket(ticket);
        }
    }

    private String newBatchSkipReason(Integer ticketId) {
        OrderDeliveryTicket ticket = ticketSourceMapper.selectById(ticketId);
        if (ticket == null) {
            return "Delivery ticket does not exist";
        }
        if (Boolean.TRUE.equals(ticket.getDiscarded())) {
            return "Delivery ticket is discarded";
        }
        if (!"SUCCESS".equalsIgnoreCase(ticket.getMesSyncStatus())
                || ticket.getMaterialNo() == null || ticket.getMaterialNo().isBlank()) {
            return "MES material number is not confirmed";
        }
        String status = normalizeStatus(ticket.getWmsAllocateStatus());
        if (status.isEmpty()) {
            status = "PENDING";
        }
        if (STATUS_SUCCESS.equals(status)) {
            return "WMS reservation already completed";
        }
        if (status != null && !"PENDING".equals(status) && !STATUS_FAILED.equals(status)) {
            return "WMS reservation status requires recovery review";
        }
        List<OrderDeliveryTicketAllocation> allocations = allocationMapper.selectByTicketId(ticketId);
        if (allocations != null && allocations.stream().anyMatch(this::hasStartedMesProduction)) {
            return "MES production has already started or completed";
        }
        return null;
    }

    private boolean hasStartedMesProduction(OrderDeliveryTicketAllocation allocation) {
        if (allocation == null
                || !"PRODUCTION_REQUIRED".equalsIgnoreCase(allocation.getAllocationType())
                || !"ACTIVE".equalsIgnoreCase(allocation.getStatus())) {
            return false;
        }
        String status = normalizeStatus(allocation.getMesProductionStatus());
        return "PROCESSING".equals(status)
                || "SUBMITTING".equals(status)
                || "RESULT_UNKNOWN".equals(status)
                || STATUS_SUCCESS.equals(status);
    }

    private List<Row> groupRows(WmsReservationPlanService.ReservationGroup group,
                                String status,
                                Long batchId,
                                String externalBizId,
                                String message) {
        return group.getTickets().stream()
                .map(ticket -> new Row(ticket.getTicketId(), status, message, batchId, group.getSaleNo(), externalBizId))
                .toList();
    }

    private List<Row> ticketRows(List<WmsReservationTicket> tickets,
                                 String status,
                                 Long batchId,
                                 String saleNo,
                                 String externalBizId,
                                 String message) {
        if (tickets == null || tickets.isEmpty()) {
            return List.of(new Row(null, status, message, batchId, saleNo, externalBizId));
        }
        return tickets.stream()
                .map(ticket -> new Row(ticket.getTicketId(), status, message, batchId, saleNo, externalBizId))
                .toList();
    }

    private Map<String, Object> summary(Long taskId, List<Row> sourceRows) {
        List<Map<String, Object>> rows = sourceRows.stream().map(Row::toMap).toList();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("taskType", TASK_TYPE);
        summary.put("taskId", taskId);
        summary.put("totalCount", rows.size());
        summary.put("successCount", count(sourceRows, STATUS_SUCCESS));
        summary.put("failedCount", count(sourceRows, STATUS_FAILED));
        summary.put("unknownCount", count(sourceRows, STATUS_RESULT_UNKNOWN));
        summary.put("applyFailedCount", count(sourceRows, STATUS_APPLY_FAILED));
        summary.put("skippedCount", count(sourceRows, STATUS_SKIPPED));
        summary.put("batchCount", Math.toIntExact(sourceRows.stream().map(Row::batchId).filter(Objects::nonNull).distinct().count()));
        summary.put("results", rows);
        return summary;
    }

    private int count(List<Row> rows, String status) {
        return Math.toIntExact(rows.stream().filter(row -> status.equals(row.status())).count());
    }

    private long durationMs(long startedAt) {
        return Math.max(0L, System.currentTimeMillis() - startedAt);
    }

    private String statusFor(RuntimeException error) {
        if (error instanceof DataCenterCallException callException
                && callException.getCertainty() == DataCenterCallException.FailureCertainty.RESULT_UNKNOWN) {
            return STATUS_RESULT_UNKNOWN;
        }
        String message = safeMessage(error);
        if (message.toUpperCase().contains(STATUS_APPLY_FAILED)) {
            return STATUS_APPLY_FAILED;
        }
        return STATUS_FAILED;
    }

    private String message(WmsSaleLadingReserveResp response, String fallback) {
        return response == null || isBlank(response.getMsg()) ? fallback : response.getMsg().trim();
    }

    private String safeMessage(RuntimeException error) {
        String message = error == null ? null : error.getMessage();
        if (isBlank(message)) {
            return error == null ? "WMS reservation failed" : error.getClass().getSimpleName();
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    private List<Integer> normalizeTicketIds(List<Integer> ticketIds) {
        if (ticketIds == null) {
            throw new RuntimeException("请选择至少一个已提零件");
        }
        LinkedHashSet<Integer> normalized = new LinkedHashSet<>();
        for (Integer ticketId : ticketIds) {
            if (ticketId != null && ticketId > 0) {
                normalized.add(ticketId);
            }
        }
        if (normalized.isEmpty()) {
            throw new RuntimeException("请选择至少一个已提零件");
        }
        return new ArrayList<>(normalized);
    }

    private String normalizeStatus(String status) {
        return status == null ? "" : status.trim().toUpperCase();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record Row(Integer ticketId, String status, String message, Long batchId, String saleNo, String externalBizId) {
        private static Row failed(Integer ticketId, Long batchId, String saleNo, String externalBizId, String message) {
            return new Row(ticketId, STATUS_FAILED, message, batchId, saleNo, externalBizId);
        }

        private static Row skipped(Integer ticketId, Long batchId, String saleNo, String externalBizId, String message) {
            return new Row(ticketId, STATUS_SKIPPED, message, batchId, saleNo, externalBizId);
        }

        private Map<String, Object> toMap() {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("ticketId", ticketId);
            row.put("status", status);
            row.put("message", message == null ? "" : message);
            if (batchId != null) {
                row.put("batchId", batchId);
            }
            if (saleNo != null) {
                row.put("saleNo", saleNo);
            }
            if (externalBizId != null) {
                row.put("externalBizId", externalBizId);
            }
            return row;
        }
    }
}
