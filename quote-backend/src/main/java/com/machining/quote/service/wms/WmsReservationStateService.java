package com.machining.quote.service.wms;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.machining.quote.dto.wms.WmsSaleLadingReserveReq;
import com.machining.quote.dto.wms.WmsSaleLadingReserveResp;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderDeliveryTicketAllocation;
import com.machining.quote.entity.WmsReservationBatch;
import com.machining.quote.entity.WmsReservationItem;
import com.machining.quote.entity.WmsReservationTicket;
import com.machining.quote.mapper.OrderDeliveryTicketAllocationMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.WmsReservationBatchMapper;
import com.machining.quote.mapper.WmsReservationItemMapper;
import com.machining.quote.mapper.WmsReservationTicketMapper;
import com.machining.quote.service.DataCenterCallException;
import com.machining.quote.service.JsonSnapshotCopyService;
import com.machining.quote.service.ProcessRouteSnapshotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.security.SecureRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class WmsReservationStateService {
    public static final int MAX_TRANSPORT_ATTEMPTS = 2;

    private static final String STATUS_RESERVED = "RESERVED";
    private static final String STATUS_SUBMITTING = "SUBMITTING";
    private static final String STATUS_RESPONSE_RECEIVED = "RESPONSE_RECEIVED";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_RESULT_UNKNOWN = "RESULT_UNKNOWN";
    private static final String STATUS_APPLY_FAILED = "APPLY_FAILED";

    private static final String ALLOCATION_ACTIVE = "ACTIVE";
    private static final String TYPE_IN_STOCK = "IN_STOCK_LOCK";
    private static final String TYPE_IN_PRODUCTION = "IN_PRODUCTION_LOCK";
    private static final String TYPE_PRODUCTION_REQUIRED = "PRODUCTION_REQUIRED";

    private static final DateTimeFormatter BIZ_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final char[] CROCKFORD32 = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Autowired
    private WmsReservationBatchMapper batchMapper;
    @Autowired
    private WmsReservationItemMapper itemMapper;
    @Autowired
    private WmsReservationTicketMapper ticketMapper;
    @Autowired
    private OrderDeliveryTicketMapper ticketSourceMapper;
    @Autowired
    private OrderDeliveryTicketAllocationMapper allocationMapper;
    @Autowired(required = false)
    private WmsReservationClient client;
    @Autowired(required = false)
    private WmsReservationContractValidator validator = new WmsReservationContractValidator();
    @Autowired(required = false)
    private WmsReservationFingerprintService fingerprintService = new WmsReservationFingerprintService();
    @Autowired(required = false)
    private WmsReservationPlanService planService = new WmsReservationPlanService();
    @Autowired(required = false)
    private TransactionTemplate transactionTemplate;
    @Autowired(required = false)
    private ProcessRouteSnapshotService processRouteSnapshotService = new ProcessRouteSnapshotService();
    @Autowired(required = false)
    private JsonSnapshotCopyService jsonSnapshotCopyService = new JsonSnapshotCopyService();

    @Transactional
    public ReservationBatchContext reserveBatch(Long taskId, WmsReservationPlanService.ReservationGroup group) {
        requireGroup(group);
        assertNoActiveTickets(group.getTickets());
        WmsSaleLadingReserveReq request = requestSnapshot(group, newWmsBizId());
        String fingerprint = fingerprintService.fingerprint(request.getSaleNo(), request.getItems());
        if (!Objects.equals(fingerprint, group.getRequestFingerprint())) {
            throw new IllegalStateException("WMS reservation request fingerprint mismatch");
        }

        WmsReservationBatch batch = newBatch(taskId, group, request, fingerprint);
        batchMapper.insert(batch);
        requireId(batch.getId(), "WMS reservation batch insert did not return id");

        Map<String, Long> itemIdByProduct = new LinkedHashMap<>();
        for (WmsReservationPlanService.ReservationItem source : group.getItems()) {
            WmsReservationItem item = new WmsReservationItem();
            item.setBatchId(batch.getId());
            item.setProductCode(source.getProductCode());
            item.setReserveCount(source.getReserveCount());
            item.setRemark(source.getRemark());
            item.setStatus(STATUS_RESERVED);
            LocalDateTime now = LocalDateTime.now();
            item.setCreatedAt(now);
            item.setUpdatedAt(now);
            itemMapper.insert(item);
            requireId(item.getId(), "WMS reservation item insert did not return id");
            itemIdByProduct.put(item.getProductCode(), item.getId());
        }

        List<WmsReservationTicket> tickets = new ArrayList<>();
        for (WmsReservationPlanService.TicketReservation source : stableTickets(group.getTickets())) {
            Long itemId = itemIdByProduct.get(source.getProductCode());
            if (itemId == null) {
                throw new IllegalStateException("WMS reservation ticket product has no batch item productCode=" + source.getProductCode());
            }
            WmsReservationTicket ticket = new WmsReservationTicket();
            ticket.setBatchId(batch.getId());
            ticket.setBatchItemId(itemId);
            ticket.setTicketId(source.getTicketId());
            ticket.setSalesOrderItemId(source.getSalesOrderItemId());
            ticket.setSalesOrderLineNo(source.getSalesOrderLineNo());
            ticket.setProductCode(source.getProductCode());
            ticket.setReserveCount(source.getReserveCount());
            ticket.setStatus(STATUS_RESERVED);
            LocalDateTime now = LocalDateTime.now();
            ticket.setCreatedAt(now);
            ticket.setUpdatedAt(now);
            ticketMapper.insert(ticket);
            requireId(ticket.getId(), "WMS reservation ticket insert did not return id");
            tickets.add(ticket);
        }
        return new ReservationBatchContext(batch.getId(), batch.getExternalBizId(), copyRequest(request), List.copyOf(tickets));
    }

    public WmsSaleLadingReserveReq loadReplayRequest(Long batchId) {
        WmsReservationBatch batch = loadBatch(batchId);
        WmsSaleLadingReserveReq request = toRequest(batch.getRequestPayload());
        String fingerprint = fingerprintService.fingerprint(request.getSaleNo(), request.getItems());
        if (!Objects.equals(fingerprint, batch.getRequestFingerprint())) {
            throw new IllegalStateException("WMS reservation replay request fingerprint mismatch batchId=" + batchId);
        }
        return copyRequest(request);
    }

    public void saveValidatedResponse(Long batchId, WmsSaleLadingReserveResp response) {
        WmsSaleLadingReserveReq request = loadReplayRequest(batchId);
        WmsSaleLadingReserveResp.Data responseData;
        try {
            responseData = validator.validate(request, response);
        } catch (DataCenterCallException error) {
            if (error.getCertainty() == DataCenterCallException.FailureCertainty.RESULT_UNKNOWN) {
                markResultUnknown(batchId, error.getMessage());
            } else {
                markFailed(batchId, error.getMessage());
            }
            throw error;
        }

        executeWithoutResult(() -> {
            List<WmsReservationItem> items = loadItems(batchId);
            List<WmsReservationTicket> tickets = ticketMapper.selectByBatchId(batchId);
            Map<String, WmsSaleLadingReserveResp.Item> responseByProduct = responseData.getItems().stream()
                    .collect(Collectors.toMap(WmsSaleLadingReserveResp.Item::getProductCode, Function.identity()));
            for (WmsReservationItem item : items) {
                WmsSaleLadingReserveResp.Item responseItem = responseByProduct.get(item.getProductCode());
                if (responseItem == null) {
                    throw new IllegalStateException("WMS reservation response missing persisted item productCode=" + item.getProductCode());
                }
                WmsReservationItem update = new WmsReservationItem();
                update.setId(item.getId());
                update.setStockCount(responseItem.getStockCount());
                update.setInProductionCount(responseItem.getInProductionCount());
                update.setFrozenCount(responseItem.getFrozenCount());
                update.setSurplusCount(responseItem.getSurplusCount());
                update.setStatus(STATUS_RESPONSE_RECEIVED);
                update.setUpdatedAt(LocalDateTime.now());
                itemMapper.updateById(update);
            }
            WmsReservationPlanService.ReservationGroup group = toGroup(loadBatch(batchId), items, tickets);
            List<WmsReservationPlanService.TicketAllocationSplit> splits = planService.split(group, responseData);
            Map<Integer, WmsReservationPlanService.TicketAllocationSplit> splitByTicket = splits.stream()
                    .collect(Collectors.toMap(WmsReservationPlanService.TicketAllocationSplit::getTicketId, Function.identity()));
            for (WmsReservationTicket ticket : tickets) {
                WmsReservationPlanService.TicketAllocationSplit split = splitByTicket.get(ticket.getTicketId());
                if (split == null) {
                    throw new IllegalStateException("WMS reservation split missing ticketId=" + ticket.getTicketId());
                }
                WmsReservationTicket update = new WmsReservationTicket();
                update.setId(ticket.getId());
                update.setTicketId(ticket.getTicketId());
                update.setStockFrozenCount(split.getStockFrozenCount());
                update.setInProductionCount(split.getInProductionCount());
                update.setSurplusCount(split.getSurplusCount());
                update.setStatus(STATUS_RESPONSE_RECEIVED);
                update.setUpdatedAt(LocalDateTime.now());
                ticketMapper.updateById(update);
            }
            markResponseReceived(batchId, response);
        });
    }

    public void applyResponse(Long batchId) {
        try {
            executeWithoutResult(() -> applyResponseInTransaction(batchId));
        } catch (RuntimeException error) {
            markApplyFailed(batchId, error.getMessage());
            throw error;
        }
    }

    public WmsSaleLadingReserveResp sendWithReplay(Long batchId) {
        WmsSaleLadingReserveReq request = loadReplayRequest(batchId);
        while (true) {
            WmsReservationBatch batch = loadBatch(batchId);
            if (STATUS_RESERVED.equals(batch.getStatus()) || STATUS_RESULT_UNKNOWN.equals(batch.getStatus())) {
                markSubmittingLoaded(batch, STATUS_RESULT_UNKNOWN.equals(batch.getStatus()) ? "TRANSPORT_REPLAY" : "INITIAL_SEND");
            }
            WmsSaleLadingReserveResp response;
            try {
                response = client.reserve(request);
            } catch (DataCenterCallException error) {
                if (error.getCertainty() == DataCenterCallException.FailureCertainty.RESULT_UNKNOWN) {
                    markResultUnknown(batchId, error.getMessage());
                    WmsReservationBatch current = loadBatch(batchId);
                    if (valueOrOne(current.getAttemptNo()) < MAX_TRANSPORT_ATTEMPTS) {
                        continue;
                    }
                    throw error;
                }
                markFailed(batchId, error.getMessage());
                throw error;
            }
            saveValidatedResponse(batchId, response);
            return response;
        }
    }

    public void markSubmitting(Long batchId, String lastErrorType) {
        markSubmittingLoaded(loadBatch(batchId), lastErrorType);
    }

    public void transitionBatchStatus(Long batchId, String nextStatus, String lastErrorType) {
        WmsReservationBatch batch = loadBatch(batchId);
        if (!isAllowedTransition(batch.getStatus(), nextStatus)) {
            throw new IllegalStateException("Illegal WMS reservation status transition " + batch.getStatus() + " -> " + nextStatus);
        }
        WmsReservationBatch update = new WmsReservationBatch();
        update.setId(batch.getId());
        update.setStatus(nextStatus);
        update.setLastErrorType(abbreviate(lastErrorType, 60));
        update.setUpdatedAt(LocalDateTime.now());
        if (STATUS_SUBMITTING.equals(nextStatus) && STATUS_RESULT_UNKNOWN.equals(batch.getStatus())) {
            update.setAttemptNo(valueOrOne(batch.getAttemptNo()) + 1);
            update.setSubmittedAt(LocalDateTime.now());
        }
        batchMapper.updateById(update);
    }

    private void applyResponseInTransaction(Long batchId) {
        WmsReservationBatch batch = loadBatch(batchId);
        if (!STATUS_RESPONSE_RECEIVED.equals(batch.getStatus()) && !STATUS_APPLY_FAILED.equals(batch.getStatus())) {
            throw new IllegalStateException("WMS reservation batch is not ready to apply status=" + batch.getStatus());
        }
        List<WmsReservationItem> items = loadItems(batchId);
        List<WmsReservationTicket> tickets = ticketMapper.selectByBatchId(batchId);
        for (WmsReservationTicket reservationTicket : tickets) {
            applyTicket(reservationTicket);
        }
        for (WmsReservationItem item : items) {
            WmsReservationItem update = new WmsReservationItem();
            update.setId(item.getId());
            update.setStatus(STATUS_SUCCESS);
            update.setUpdatedAt(LocalDateTime.now());
            itemMapper.updateById(update);
        }
        markSuccess(batchId);
    }

    private void applyTicket(WmsReservationTicket reservationTicket) {
        requireTicketBuckets(reservationTicket);
        OrderDeliveryTicket ticket = ticketSourceMapper.selectByIdForUpdate(reservationTicket.getTicketId());
        if (ticket == null) {
            throw new IllegalStateException("delivery ticket not found ticketId=" + reservationTicket.getTicketId());
        }
        if (!Objects.equals(trim(ticket.getMaterialNo()), trim(reservationTicket.getProductCode()))) {
            throw new IllegalStateException("WMS reservation material snapshot changed ticketId=" + reservationTicket.getTicketId());
        }
        if (ticket.getQty() == null || BigDecimal.valueOf(ticket.getQty()).compareTo(reservationTicket.getReserveCount()) != 0) {
            throw new IllegalStateException("WMS reservation quantity snapshot changed ticketId=" + reservationTicket.getTicketId());
        }
        Set<String> existingTypes = allocationMapper.selectByWmsReservationTicketId(reservationTicket.getId()).stream()
                .map(OrderDeliveryTicketAllocation::getAllocationType)
                .collect(Collectors.toCollection(HashSet::new));
        insertPositiveAllocation(ticket, reservationTicket, TYPE_IN_STOCK, reservationTicket.getStockFrozenCount(), existingTypes);
        insertPositiveAllocation(ticket, reservationTicket, TYPE_IN_PRODUCTION, reservationTicket.getInProductionCount(), existingTypes);
        insertPositiveAllocation(ticket, reservationTicket, TYPE_PRODUCTION_REQUIRED, reservationTicket.getSurplusCount(), existingTypes);

        OrderDeliveryTicket ticketUpdate = new OrderDeliveryTicket();
        ticketUpdate.setId(ticket.getId());
        ticketUpdate.setWmsAllocateStatus(STATUS_SUCCESS);
        ticketUpdate.setWmsAllocateMessage("WMS reservation applied");
        ticketUpdate.setWmsAllocatedAt(LocalDateTime.now());
        ticketSourceMapper.updateById(ticketUpdate);

        WmsReservationTicket reservationUpdate = new WmsReservationTicket();
        reservationUpdate.setId(reservationTicket.getId());
        reservationUpdate.setStatus(STATUS_SUCCESS);
        reservationUpdate.setAppliedAt(LocalDateTime.now());
        reservationUpdate.setUpdatedAt(LocalDateTime.now());
        ticketMapper.updateById(reservationUpdate);
    }

    private void insertPositiveAllocation(OrderDeliveryTicket ticket,
                                          WmsReservationTicket reservationTicket,
                                          String type,
                                          BigDecimal quantity,
                                          Set<String> existingTypes) {
        if (quantity == null || quantity.signum() <= 0 || existingTypes.contains(type)) {
            return;
        }
        OrderDeliveryTicketAllocation row = allocationRow(ticket, reservationTicket, type, quantity.intValueExact());
        allocationMapper.insert(row);
        existingTypes.add(type);
    }

    private OrderDeliveryTicketAllocation allocationRow(OrderDeliveryTicket ticket,
                                                        WmsReservationTicket reservationTicket,
                                                        String type,
                                                        Integer qty) {
        OrderDeliveryTicketAllocation row = new OrderDeliveryTicketAllocation();
        row.setWmsReservationTicketId(reservationTicket.getId());
        row.setTicketId(ticket.getId());
        row.setRfqId(ticket.getRfqId());
        row.setItemId(ticket.getItemId());
        row.setSourceType(ticket.getSourceType());
        row.setSourceOrderId(ticket.getSourceOrderId());
        row.setSourceItemId(ticket.getSourceItemId());
        row.setAllocationType(type);
        row.setQty(qty);
        row.setTicketQty(ticket.getQty());
        row.setMaterialNo(ticket.getMaterialNo());
        row.setOrderType(ticket.getOrderType());
        row.setItemType(ticket.getItemType());
        row.setPartNo(ticket.getPartNo());
        row.setVersion(ticket.getVersion());
        row.setRfqNo(ticket.getRfqNo());
        row.setCustomerName(ticket.getCustomerName());
        row.setInternalProjectNo(ticket.getInternalProjectNo());
        row.setCustomerOrderNo(ticket.getCustomerOrderNo());
        row.setCustomerProjectNo(ticket.getCustomerProjectNo());
        row.setMaterialName(ticket.getMaterialName());
        row.setEngineeringMaterialCode(ticket.getEngineeringMaterialCode());
        row.setEngineeringMaterialTypeCode(ticket.getEngineeringMaterialTypeCode());
        row.setEngineeringMaterialTypeName(ticket.getEngineeringMaterialTypeName());
        row.setEngineeringMaterialCategory(ticket.getEngineeringMaterialCategory());
        row.setEngineeringMaterialName(ticket.getEngineeringMaterialName());
        row.setMaterialShape(ticket.getMaterialShape());
        row.setSurfaceTreatment(ticket.getSurfaceTreatment());
        row.setRemark(ticket.getRemark());
        row.setDescription(ticket.getDescription());
        row.setDeliveryDate(ticket.getDeliveryDate());
        row.setProcessRoute(processRouteSnapshotService.copySnapshot(ticket.getProcessRoute()));
        row.setMaterialData(deepCopySnapshot(ticket.getMaterialData()));
        row.setMaterialGrossWeight(ticket.getMaterialGrossWeight());
        row.setPartNetWeight(ticket.getPartNetWeight());
        row.setSourcePayload(sourcePayload(reservationTicket, type, qty));
        row.setMesProductionStatus("PENDING");
        row.setStatus(ALLOCATION_ACTIVE);
        LocalDateTime now = LocalDateTime.now();
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        return row;
    }

    private Map<String, Object> sourcePayload(WmsReservationTicket reservationTicket, String type, Integer qty) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", "WMS_SALE_LADING_RESERVE");
        payload.put("batchId", reservationTicket.getBatchId());
        payload.put("reservationTicketId", reservationTicket.getId());
        payload.put("allocationType", type);
        payload.put("productCode", reservationTicket.getProductCode());
        payload.put("qty", qty);
        return payload;
    }

    private void requireTicketBuckets(WmsReservationTicket ticket) {
        if (ticket.getStockFrozenCount() == null || ticket.getInProductionCount() == null || ticket.getSurplusCount() == null) {
            throw new IllegalStateException("WMS reservation ticket split is missing ticketId=" + ticket.getTicketId());
        }
        BigDecimal total = ticket.getStockFrozenCount().add(ticket.getInProductionCount()).add(ticket.getSurplusCount());
        if (total.compareTo(ticket.getReserveCount()) != 0) {
            throw new IllegalStateException("WMS reservation ticket split does not match reserve count ticketId=" + ticket.getTicketId());
        }
    }

    private WmsReservationBatch newBatch(Long taskId,
                                         WmsReservationPlanService.ReservationGroup group,
                                         WmsSaleLadingReserveReq request,
                                         String fingerprint) {
        WmsReservationBatch batch = new WmsReservationBatch();
        batch.setExternalBizId(request.getExternalBizId());
        batch.setSystemTaskId(taskId);
        batch.setSaleOrderId(group.getSaleOrderId());
        batch.setSaleOrderNo(group.getSaleNo());
        batch.setRequestFingerprint(fingerprint);
        batch.setRequestPayload(copyRequest(request));
        batch.setItemCount(group.getItems().size());
        batch.setTicketCount(group.getTickets().size());
        batch.setStatus(STATUS_RESERVED);
        batch.setAttemptNo(1);
        LocalDateTime now = LocalDateTime.now();
        batch.setCreatedAt(now);
        batch.setUpdatedAt(now);
        return batch;
    }

    private WmsSaleLadingReserveReq requestSnapshot(WmsReservationPlanService.ReservationGroup group, String externalBizId) {
        WmsSaleLadingReserveReq request = new WmsSaleLadingReserveReq();
        request.setExternalBizId(externalBizId);
        request.setSaleNo(group.getSaleNo());
        List<WmsSaleLadingReserveReq.Item> items = group.getItems().stream()
                .sorted(Comparator.comparing(WmsReservationPlanService.ReservationItem::getProductCode))
                .map(item -> {
                    WmsSaleLadingReserveReq.Item requestItem = new WmsSaleLadingReserveReq.Item();
                    requestItem.setProductCode(item.getProductCode());
                    requestItem.setReserveCount(item.getReserveCount());
                    requestItem.setRemark(item.getRemark());
                    requestItem.setCustomerProjectNo(item.getCustomerProjectNo());
                    requestItem.setCustomerOrderNo(item.getCustomerOrderNo());
                    return requestItem;
                })
                .toList();
        request.setItems(items);
        return request;
    }

    private WmsSaleLadingReserveReq toRequest(Object payload) {
        if (payload == null) {
            throw new IllegalStateException("WMS reservation request payload is empty");
        }
        return OBJECT_MAPPER.convertValue(payload, WmsSaleLadingReserveReq.class);
    }

    private WmsSaleLadingReserveReq copyRequest(WmsSaleLadingReserveReq request) {
        return OBJECT_MAPPER.convertValue(request, WmsSaleLadingReserveReq.class);
    }

    private WmsReservationPlanService.ReservationGroup toGroup(WmsReservationBatch batch,
                                                               List<WmsReservationItem> items,
                                                               List<WmsReservationTicket> tickets) {
        WmsReservationPlanService.ReservationGroup group = new WmsReservationPlanService.ReservationGroup();
        group.setSaleOrderId(batch.getSaleOrderId());
        group.setSaleNo(batch.getSaleOrderNo());
        for (WmsReservationItem item : items.stream().sorted(Comparator.comparing(WmsReservationItem::getProductCode)).toList()) {
            WmsReservationPlanService.ReservationItem planItem = new WmsReservationPlanService.ReservationItem();
            planItem.setProductCode(item.getProductCode());
            planItem.setReserveCount(item.getReserveCount());
            planItem.setRemark(item.getRemark());
            group.getItems().add(planItem);
        }
        for (WmsReservationTicket ticket : tickets.stream()
                .sorted(Comparator.comparing(WmsReservationTicket::getSalesOrderLineNo, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(WmsReservationTicket::getTicketId))
                .toList()) {
            WmsReservationPlanService.TicketReservation planTicket = new WmsReservationPlanService.TicketReservation();
            planTicket.setTicketId(ticket.getTicketId());
            planTicket.setSalesOrderItemId(ticket.getSalesOrderItemId());
            planTicket.setSalesOrderLineNo(ticket.getSalesOrderLineNo());
            planTicket.setProductCode(ticket.getProductCode());
            planTicket.setReserveCount(ticket.getReserveCount());
            group.getTickets().add(planTicket);
        }
        return group;
    }

    private void markSubmittingLoaded(WmsReservationBatch batch, String lastErrorType) {
        if (!isAllowedTransition(batch.getStatus(), STATUS_SUBMITTING)) {
            throw new IllegalStateException("Illegal WMS reservation status transition " + batch.getStatus() + " -> " + STATUS_SUBMITTING);
        }
        WmsReservationBatch update = new WmsReservationBatch();
        update.setId(batch.getId());
        update.setStatus(STATUS_SUBMITTING);
        update.setLastErrorType(abbreviate(lastErrorType, 60));
        update.setSubmittedAt(LocalDateTime.now());
        update.setUpdatedAt(LocalDateTime.now());
        if (STATUS_RESULT_UNKNOWN.equals(batch.getStatus())) {
            update.setAttemptNo(valueOrOne(batch.getAttemptNo()) + 1);
        }
        batchMapper.updateById(update);
    }

    private void markResponseReceived(Long batchId, WmsSaleLadingReserveResp response) {
        WmsReservationBatch update = new WmsReservationBatch();
        update.setId(batchId);
        update.setStatus(STATUS_RESPONSE_RECEIVED);
        update.setResponseCode(response == null || response.getCode() == null ? null : String.valueOf(response.getCode()));
        update.setResponseMsg(abbreviate(response == null ? null : response.getMsg(), 500));
        update.setRespondedAt(LocalDateTime.now());
        update.setUpdatedAt(LocalDateTime.now());
        batchMapper.updateById(update);
    }

    private void markResultUnknown(Long batchId, String message) {
        markTerminalLike(batchId, STATUS_RESULT_UNKNOWN, "RESULT_UNKNOWN", message, false);
    }

    private void markFailed(Long batchId, String message) {
        markTerminalLike(batchId, STATUS_FAILED, "DEFINITE_FAILURE", message, true);
    }

    private void markApplyFailed(Long batchId, String message) {
        markTerminalLike(batchId, STATUS_APPLY_FAILED, "APPLY_FAILED", message, false);
    }

    private void markSuccess(Long batchId) {
        WmsReservationBatch update = new WmsReservationBatch();
        update.setId(batchId);
        update.setStatus(STATUS_SUCCESS);
        update.setCompletedAt(LocalDateTime.now());
        update.setUpdatedAt(LocalDateTime.now());
        batchMapper.updateById(update);
    }

    private void markTerminalLike(Long batchId, String status, String errorType, String message, boolean completed) {
        executeWithoutResult(() -> {
            WmsReservationBatch update = new WmsReservationBatch();
            update.setId(batchId);
            update.setStatus(status);
            update.setLastErrorType(errorType);
            update.setResponseMsg(abbreviate(message, 500));
            if (completed) {
                update.setCompletedAt(LocalDateTime.now());
            }
            update.setUpdatedAt(LocalDateTime.now());
            batchMapper.updateById(update);
            if (STATUS_FAILED.equals(status)) {
                markPersistedRowsFailed(batchId, message);
            }
        });
    }

    private void markPersistedRowsFailed(Long batchId, String message) {
        String safeMessage = abbreviate(message, 500);
        LocalDateTime now = LocalDateTime.now();
        List<WmsReservationItem> items = loadItems(batchId);
        if (items != null) {
            for (WmsReservationItem item : items) {
                WmsReservationItem update = new WmsReservationItem();
                update.setId(item.getId());
                update.setStatus(STATUS_FAILED);
                update.setUpdatedAt(now);
                itemMapper.updateById(update);
            }
        }
        List<WmsReservationTicket> tickets = ticketMapper.selectByBatchId(batchId);
        if (tickets == null) {
            return;
        }
        for (WmsReservationTicket ticket : tickets) {
            WmsReservationTicket update = new WmsReservationTicket();
            update.setId(ticket.getId());
            update.setStatus(STATUS_FAILED);
            update.setMessage(safeMessage);
            update.setUpdatedAt(now);
            ticketMapper.updateById(update);

            OrderDeliveryTicket ticketUpdate = new OrderDeliveryTicket();
            ticketUpdate.setId(ticket.getTicketId());
            ticketUpdate.setWmsAllocateStatus(STATUS_FAILED);
            ticketUpdate.setWmsAllocateMessage(safeMessage);
            ticketSourceMapper.updateById(ticketUpdate);
        }
    }

    private boolean isAllowedTransition(String current, String next) {
        return switch (normalizeStatus(current) + "->" + normalizeStatus(next)) {
            case "RESERVED->SUBMITTING",
                 "SUBMITTING->RESPONSE_RECEIVED",
                 "SUBMITTING->FAILED",
                 "SUBMITTING->RESULT_UNKNOWN",
                 "RESULT_UNKNOWN->SUBMITTING",
                 "RESPONSE_RECEIVED->SUCCESS",
                 "RESPONSE_RECEIVED->APPLY_FAILED",
                 "APPLY_FAILED->SUCCESS" -> true;
            default -> false;
        };
    }

    private void assertNoActiveTickets(List<WmsReservationPlanService.TicketReservation> tickets) {
        for (WmsReservationPlanService.TicketReservation ticket : tickets) {
            WmsReservationTicket active = ticketMapper.selectActiveByTicketId(ticket.getTicketId());
            if (active != null) {
                throw new IllegalStateException("ticket already has active WMS reservation ticketId=" + ticket.getTicketId()
                        + " activeStatus=" + active.getStatus());
            }
        }
    }

    private List<WmsReservationPlanService.TicketReservation> stableTickets(List<WmsReservationPlanService.TicketReservation> tickets) {
        return tickets.stream()
                .sorted(Comparator.comparing(WmsReservationPlanService.TicketReservation::getSalesOrderLineNo,
                                Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(WmsReservationPlanService.TicketReservation::getTicketId))
                .toList();
    }

    private List<WmsReservationItem> loadItems(Long batchId) {
        QueryWrapper<WmsReservationItem> query = new QueryWrapper<>();
        query.eq("batch_id", batchId).orderByAsc("product_code");
        return itemMapper.selectList(query);
    }

    private WmsReservationBatch loadBatch(Long batchId) {
        if (batchId == null) {
            throw new IllegalArgumentException("WMS reservation batch id is required");
        }
        WmsReservationBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new IllegalStateException("WMS reservation batch not found batchId=" + batchId);
        }
        return batch;
    }

    private void requireGroup(WmsReservationPlanService.ReservationGroup group) {
        if (group == null || trim(group.getSaleNo()) == null || group.getItems().isEmpty() || group.getTickets().isEmpty()) {
            throw new IllegalArgumentException("WMS reservation group must include saleNo, items, and tickets");
        }
    }

    private void executeWithoutResult(Runnable action) {
        if (transactionTemplate == null) {
            action.run();
            return;
        }
        transactionTemplate.executeWithoutResult(status -> action.run());
    }

    private void requireId(Object id, String message) {
        if (id == null) {
            throw new IllegalStateException(message);
        }
    }

    private int valueOrOne(Integer value) {
        return value == null || value <= 0 ? 1 : value;
    }

    private Object deepCopySnapshot(Object value) {
        if (jsonSnapshotCopyService == null) {
            jsonSnapshotCopyService = new JsonSnapshotCopyService();
        }
        return jsonSnapshotCopyService.deepCopySnapshot(value);
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

    private String abbreviate(String value, int maxLength) {
        String text = value == null ? null : value.replaceAll("\\s+", " ").trim();
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    private String newWmsBizId() {
        return "QPO-WMS-" + LocalDate.now().format(BIZ_DATE) + "-" + ulid();
    }

    private String ulid() {
        byte[] bytes = new byte[16];
        long timestamp = System.currentTimeMillis();
        bytes[0] = (byte) (timestamp >>> 40);
        bytes[1] = (byte) (timestamp >>> 32);
        bytes[2] = (byte) (timestamp >>> 24);
        bytes[3] = (byte) (timestamp >>> 16);
        bytes[4] = (byte) (timestamp >>> 8);
        bytes[5] = (byte) timestamp;
        byte[] random = new byte[10];
        RANDOM.nextBytes(random);
        System.arraycopy(random, 0, bytes, 6, random.length);

        BigInteger value = new BigInteger(1, bytes);
        char[] encoded = new char[26];
        for (int i = encoded.length - 1; i >= 0; i--) {
            encoded[i] = CROCKFORD32[value.and(BigInteger.valueOf(31)).intValue()];
            value = value.shiftRight(5);
        }
        return new String(encoded);
    }

    public record ReservationBatchContext(
            Long batchId,
            String externalBizId,
            WmsSaleLadingReserveReq request,
            List<WmsReservationTicket> tickets) {
    }
}
