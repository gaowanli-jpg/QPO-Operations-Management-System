package com.machining.quote.service.wms;

import com.machining.quote.dto.wms.WmsSaleLadingReserveResp;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderDeliveryTicketAllocation;
import com.machining.quote.entity.WmsReservationBatch;
import com.machining.quote.entity.WmsReservationTicket;
import com.machining.quote.mapper.OrderDeliveryTicketAllocationMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.WmsReservationBatchMapper;
import com.machining.quote.mapper.WmsReservationTicketMapper;
import com.machining.quote.service.DataCenterCallException;
import com.machining.quote.service.DataScopeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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
        List<Integer> normalizedTicketIds = normalizeIds(ticketIds, "at least one delivered item is required");
        List<Row> rows = new ArrayList<>();
        LinkedHashSet<Long> recoveryBatchIds = new LinkedHashSet<>();
        LinkedHashSet<Integer> legacyTicketIds = new LinkedHashSet<>();
        List<Integer> allocationIds = new ArrayList<>();
        for (Integer ticketId : normalizedTicketIds) {
            WmsReservationTicket active = ticketMapper.selectActiveLegacyByTicketId(ticketId);
            if (active != null && active.getBatchId() != null) {
                recoveryBatchIds.add(active.getBatchId());
                legacyTicketIds.add(ticketId);
                continue;
            }
            List<OrderDeliveryTicketAllocation> allocations = allocationMapper
                    .selectActiveProductionAllocationsByTicketId(ticketId);
            if (allocations == null || allocations.isEmpty()) {
                rows.add(Row.skipped(null, ticketId, null, null, null,
                        "No active production allocation is linked to the delivery ticket"));
                continue;
            }
            allocations.stream()
                    .map(OrderDeliveryTicketAllocation::getId)
                    .filter(Objects::nonNull)
                    .filter(id -> !legacyTicketIds.contains(ticketIdForAllocation(id)))
                    .forEach(allocationIds::add);
        }
        for (Long batchId : recoveryBatchIds) {
            rows.addAll(recoverBatch(batchId));
        }
        rows.addAll(allocateAllocations(allocationIds, taskId));
        return summary(taskId, rows);
    }

    public Map<String, Object> allocateAllocationBatch(List<Integer> allocationIds, Long taskId) {
        List<Integer> normalizedAllocationIds = normalizeIds(allocationIds, "at least one production allocation is required");
        long startedAt = System.currentTimeMillis();
        log.info("WMS reservation allocation batch started taskId={} allocationCount={}", taskId, normalizedAllocationIds.size());
        List<Row> rows = allocateAllocations(normalizedAllocationIds, taskId);
        Map<String, Object> summary = summary(taskId, rows);
        log.info("WMS reservation allocation batch completed taskId={} allocationCount={} durationMs={} batchCount={} successCount={} failedCount={} unknownCount={} applyFailedCount={} skippedCount={}",
                taskId,
                normalizedAllocationIds.size(),
                durationMs(startedAt),
                summary.get("batchCount"),
                summary.get("successCount"),
                summary.get("failedCount"),
                summary.get("unknownCount"),
                summary.get("applyFailedCount"),
                summary.get("skippedCount"));
        return summary;
    }

    private List<Row> allocateAllocations(List<Integer> allocationIds, Long taskId) {
        List<Row> rows = new ArrayList<>();
        LinkedHashSet<Long> recoveryBatchIds = new LinkedHashSet<>();
        LinkedHashSet<Integer> legacyTicketIds = new LinkedHashSet<>();
        List<Integer> candidates = new ArrayList<>();
        for (Integer allocationId : allocationIds.stream().filter(Objects::nonNull).distinct().toList()) {
            Integer ticketId = ticketIdForAllocation(allocationId);
            WmsReservationTicket activeLegacy = ticketId == null
                    ? null
                    : ticketMapper.selectActiveLegacyByTicketId(ticketId);
            if (activeLegacy != null) {
                legacyTicketIds.add(ticketId);
                if (activeLegacy.getBatchId() != null) {
                    recoveryBatchIds.add(activeLegacy.getBatchId());
                } else {
                    rows.add(Row.skipped(allocationId, ticketId, null, null, null,
                            "Legacy WMS reservation has no recoverable batch"));
                }
            }
            WmsReservationTicket active = ticketMapper.selectActiveByAllocationId(allocationId);
            if (active != null && active.getBatchId() != null) {
                recoveryBatchIds.add(active.getBatchId());
                continue;
            }
            if (legacyTicketIds.contains(ticketId)) {
                continue;
            }
            String skipReason = newBatchSkipReason(allocationId);
            if (skipReason != null) {
                rows.add(Row.skipped(allocationId, ticketId, null, null, null, skipReason));
            } else {
                candidates.add(allocationId);
            }
        }
        for (Long batchId : recoveryBatchIds) {
            rows.addAll(recoverBatch(batchId));
        }
        if (!candidates.isEmpty()) {
            rows.addAll(createAndSendBatches(candidates, taskId));
        }
        return rows;
    }

    private List<Row> createAndSendBatches(List<Integer> allocationIds, Long taskId) {
        WmsReservationPlanService.ReservationPlan plan = planService.prepareAllocations(allocationIds);
        List<Row> rows = new ArrayList<>();
        for (WmsReservationPlanService.ReservationGroup group : plan.getGroups()) {
            rows.addAll(sendNewGroup(taskId, group));
        }
        for (WmsReservationPlanService.RowFailure failure : plan.getFailures()) {
            rows.add(Row.failed(failure.getAllocationId(), failure.getTicketId(), null, null, null, failure.getMessage()));
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
            log.info("WMS reservation group completed taskId={} batchId={} externalBizId={} saleOrderNo={} allocationCount={} status={} durationMs={}",
                    taskId, batchId, externalBizId, group.getSaleNo(), group.getAllocations().size(), STATUS_SUCCESS, durationMs(startedAt));
            return groupRows(group, STATUS_SUCCESS, batchId, externalBizId, message(response, "WMS reservation applied"));
        } catch (RuntimeException error) {
            log.warn("WMS reservation group failed taskId={} batchId={} externalBizId={} saleOrderNo={} allocationCount={} status={} durationMs={} message={}",
                    taskId, batchId, externalBizId, group.getSaleNo(), group.getAllocations().size(), statusFor(error), durationMs(startedAt), safeMessage(error));
            return groupRows(group, statusFor(error), batchId, externalBizId, safeMessage(error));
        }
    }

    private List<Row> recoverBatch(Long batchId) {
        long startedAt = System.currentTimeMillis();
        WmsReservationBatch batch = batchMapper.selectById(batchId);
        List<WmsReservationTicket> reservations = ticketMapper.selectByBatchId(batchId);
        assertCanAccessReservations(reservations);
        String status = normalizeStatus(batch == null ? null : batch.getStatus());
        String saleNo = batch == null ? null : batch.getSaleOrderNo();
        String externalBizId = batch == null ? null : batch.getExternalBizId();
        try {
            if (STATUS_RESULT_UNKNOWN.equals(status)) {
                WmsSaleLadingReserveResp response = stateService.sendWithReplay(batchId);
                stateService.applyResponse(batchId);
                return reservationRows(reservations, STATUS_SUCCESS, batchId, saleNo, externalBizId,
                        message(response, "WMS reservation replay applied"));
            }
            if (STATUS_RESPONSE_RECEIVED.equals(status) || STATUS_APPLY_FAILED.equals(status)) {
                stateService.applyResponse(batchId);
                return reservationRows(reservations, STATUS_SUCCESS, batchId, saleNo, externalBizId,
                        "WMS reservation local apply replayed");
            }
            if (STATUS_SUCCESS.equals(status)) {
                return reservationRows(reservations, STATUS_SKIPPED, batchId, saleNo, externalBizId,
                        "WMS reservation already completed");
            }
            if (STATUS_FAILED.equals(status)) {
                return reservationRows(reservations, STATUS_FAILED, batchId, saleNo, externalBizId,
                        "WMS reservation failed; create a new batch after data correction");
            }
            return reservationRows(reservations, STATUS_RESULT_UNKNOWN, batchId, saleNo, externalBizId,
                    "WMS reservation batch status requires review: " + status);
        } catch (RuntimeException error) {
            log.warn("WMS reservation recovery failed taskId={} batchId={} externalBizId={} saleOrderNo={} count={} status={} durationMs={} message={}",
                    null, batchId, externalBizId, saleNo, reservations == null ? 0 : reservations.size(), statusFor(error), durationMs(startedAt), safeMessage(error));
            return reservationRows(reservations, statusFor(error), batchId, saleNo, externalBizId, safeMessage(error));
        }
    }

    private void assertCanAccessReservations(List<WmsReservationTicket> reservations) {
        if (dataScopeService == null || reservations == null) {
            return;
        }
        for (WmsReservationTicket reservation : reservations) {
            Integer ticketId = reservation.getTicketId();
            if (reservation.getAllocationId() != null) {
                OrderDeliveryTicketAllocation allocation = allocationMapper.selectById(reservation.getAllocationId());
                if (allocation != null && allocation.getTicketId() != null) {
                    ticketId = allocation.getTicketId();
                }
            }
            OrderDeliveryTicket ticket = ticketId == null ? null : ticketSourceMapper.selectById(ticketId);
            dataScopeService.assertCanAccessDeliveryTicket(ticket);
        }
    }

    private String newBatchSkipReason(Integer allocationId) {
        OrderDeliveryTicketAllocation allocation = allocationMapper.selectById(allocationId);
        if (allocation == null) {
            return "Production allocation does not exist";
        }
        if (!"PRODUCTION_REQUIRED".equalsIgnoreCase(trim(allocation.getAllocationType()))
                || !"ACTIVE".equalsIgnoreCase(defaultText(allocation.getStatus(), "ACTIVE"))
                || Boolean.TRUE.equals(allocation.getDiscarded())) {
            return "Allocation is not an active production row";
        }
        if (allocation.getQty() == null || allocation.getQty() <= 0) {
            return "Allocation quantity is not positive";
        }
        if (trim(allocation.getMaterialNo()) == null) {
            return "Material number is not confirmed";
        }
        String mesStatus = normalizeStatus(allocation.getMesProductionStatus());
        if ("PROCESSING".equals(mesStatus) || "SUBMITTING".equals(mesStatus)
                || STATUS_RESULT_UNKNOWN.equals(mesStatus) || STATUS_SUCCESS.equals(mesStatus)) {
            return "MES production has started or completed";
        }
        if (allocation.getWmsReservationTicketId() != null) {
            return "WMS reservation has already been applied";
        }
        return null;
    }

    private Integer ticketIdForAllocation(Integer allocationId) {
        OrderDeliveryTicketAllocation allocation = allocationMapper.selectById(allocationId);
        return allocation == null ? null : allocation.getTicketId();
    }

    private List<Row> groupRows(WmsReservationPlanService.ReservationGroup group,
                                String status,
                                Long batchId,
                                String externalBizId,
                                String message) {
        return group.getAllocations().stream()
                .map(allocation -> new Row(allocation.getAllocationId(), allocation.getTicketId(), status,
                        message, batchId, group.getSaleNo(), externalBizId))
                .toList();
    }

    private List<Row> reservationRows(List<WmsReservationTicket> reservations,
                                     String status,
                                     Long batchId,
                                     String saleNo,
                                     String externalBizId,
                                     String message) {
        if (reservations == null || reservations.isEmpty()) {
            return List.of(new Row(null, null, status, message, batchId, saleNo, externalBizId));
        }
        return reservations.stream()
                .map(reservation -> new Row(reservation.getAllocationId(), reservation.getTicketId(), status,
                        message, batchId, saleNo, externalBizId))
                .toList();
    }

    private Map<String, Object> summary(Long taskId, List<Row> sourceRows) {
        List<Map<String, Object>> rows = sourceRows.stream().map(Row::toMap).toList();
        Map<String, Object> summary = new java.util.LinkedHashMap<>();
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

    private List<Integer> normalizeIds(List<Integer> ids, String emptyMessage) {
        if (ids == null) {
            throw new RuntimeException(emptyMessage);
        }
        LinkedHashSet<Integer> normalized = new LinkedHashSet<>();
        for (Integer id : ids) {
            if (id != null && id > 0) {
                normalized.add(id);
            }
        }
        if (normalized.isEmpty()) {
            throw new RuntimeException(emptyMessage);
        }
        return new ArrayList<>(normalized);
    }

    private String normalizeStatus(String status) {
        return status == null ? "" : status.trim().toUpperCase();
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String defaultText(String value, String fallback) {
        String trimmed = trim(value);
        return trimmed == null ? fallback : trimmed;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record Row(Integer allocationId,
                       Integer ticketId,
                       String status,
                       String message,
                       Long batchId,
                       String saleNo,
                       String externalBizId) {
        private static Row failed(Integer allocationId, Integer ticketId, Long batchId,
                                  String saleNo, String externalBizId, String message) {
            return new Row(allocationId, ticketId, STATUS_FAILED, message, batchId, saleNo, externalBizId);
        }

        private static Row skipped(Integer allocationId, Integer ticketId, Long batchId,
                                   String saleNo, String externalBizId, String message) {
            return new Row(allocationId, ticketId, STATUS_SKIPPED, message, batchId, saleNo, externalBizId);
        }

        private Map<String, Object> toMap() {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("allocationId", allocationId);
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
