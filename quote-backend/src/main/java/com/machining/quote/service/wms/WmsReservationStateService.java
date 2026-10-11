package com.machining.quote.service.wms;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.BeanUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.machining.quote.dto.wms.WmsSaleLadingReserveReq;
import com.machining.quote.dto.wms.WmsSaleLadingReserveResp;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderDeliveryTicketAllocation;
import com.machining.quote.entity.SalesOrderItem;
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
import com.machining.quote.service.AllocationSplitService;
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
    @Autowired
    private com.machining.quote.service.SalesOrderAllocationLinkService salesOrderAllocationLinkService;
    @Autowired
    private AllocationSplitService allocationSplitService;

    @Transactional
    public ReservationBatchContext reserveBatch(Long taskId, WmsReservationPlanService.ReservationGroup group) {
        requireGroup(group);
        assertNoActiveAllocations(group);
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
        for (WmsReservationPlanService.AllocationReservation source : stableAllocations(group.getAllocations())) {
            Long itemId = itemIdByProduct.get(source.getProductCode());
            if (itemId == null) {
                throw new IllegalStateException("WMS reservation allocation product has no batch item productCode=" + source.getProductCode());
            }
            WmsReservationTicket ticket = new WmsReservationTicket();
            ticket.setBatchId(batch.getId());
            ticket.setBatchItemId(itemId);
            ticket.setTicketId(source.getTicketId());
            ticket.setAllocationId(source.getAllocationId());
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
            List<WmsReservationPlanService.AllocationSplit> splits = planService.split(group, responseData);
            Map<Long, WmsReservationPlanService.AllocationSplit> splitByReservation = splits.stream()
                    .collect(Collectors.toMap(split -> reservationKey(split.getAllocationId(), split.getTicketId()), Function.identity()));
            for (WmsReservationTicket ticket : tickets) {
                WmsReservationPlanService.AllocationSplit split = splitByReservation.get(
                        reservationKey(ticket.getAllocationId(), ticket.getTicketId()));
                if (split == null) {
                    throw new IllegalStateException("WMS reservation split missing allocationId=" + ticket.getAllocationId()
                            + " ticketId=" + ticket.getTicketId());
                }
                WmsReservationTicket update = new WmsReservationTicket();
                update.setId(ticket.getId());
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
            if (reservationTicket.getAllocationId() == null) {
                applyTicket(reservationTicket);
            } else {
                applyAllocation(reservationTicket);
            }
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
        salesOrderAllocationLinkService.lockSalesOrderForTicket(reservationTicket.getTicketId());
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
        List<OrderDeliveryTicketAllocation> ticketAllocations = allocationMapper.selectByTicketIdForUpdate(ticket.getId());
        if (ticketAllocations != null && ticketAllocations.stream().anyMatch(this::hasStartedMesProduction)) {
            throw new IllegalStateException("MES production started before WMS response was applied ticketId=" + ticket.getId());
        }
        BigDecimal splitTotal = reservationTicket.getStockFrozenCount()
                .add(reservationTicket.getInProductionCount())
                .add(reservationTicket.getSurplusCount());
        if (splitTotal.compareTo(BigDecimal.valueOf(ticket.getQty())) != 0) {
            throw new IllegalStateException("WMS allocation split does not preserve ticket quantity ticketId=" + ticket.getId());
        }
        Set<String> existingTypes = allocationMapper.selectByWmsReservationTicketId(reservationTicket.getId()).stream()
                .map(OrderDeliveryTicketAllocation::getAllocationType)
                .collect(Collectors.toCollection(HashSet::new));
        insertPositiveAllocation(ticket, reservationTicket, TYPE_IN_STOCK, reservationTicket.getStockFrozenCount(), existingTypes);
        insertPositiveAllocation(ticket, reservationTicket, TYPE_IN_PRODUCTION, reservationTicket.getInProductionCount(), existingTypes);
        applyProductionRemainder(ticket, reservationTicket, ticketAllocations);
        markLegacyWmsSplit(ticket.getId(), reservationTicket.getId());
        salesOrderAllocationLinkService.syncWmsAllocations(
                ticket, allocationMapper.selectByTicketIdForUpdate(ticket.getId()));

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

    private void applyAllocation(WmsReservationTicket reservationTicket) {
        requireTicketBuckets(reservationTicket);
        salesOrderAllocationLinkService.lockSalesOrderForAllocation(reservationTicket.getAllocationId());
        OrderDeliveryTicketAllocation allocation = allocationMapper
                .selectByIdForUpdate(reservationTicket.getAllocationId());
        if (allocation == null
                || !TYPE_PRODUCTION_REQUIRED.equalsIgnoreCase(trim(allocation.getAllocationType()))
                || !ALLOCATION_ACTIVE.equalsIgnoreCase(trim(allocation.getStatus()))
                || Boolean.TRUE.equals(allocation.getDiscarded())) {
            throw new IllegalStateException("WMS source allocation is not an active production row allocationId="
                    + reservationTicket.getAllocationId());
        }
        if (!Objects.equals(trim(allocation.getMaterialNo()), trim(reservationTicket.getProductCode()))) {
            throw new IllegalStateException("WMS reservation material snapshot changed allocationId=" + allocation.getId());
        }
        boolean sameWmsApplication = "WMS".equalsIgnoreCase(trim(allocation.getAllocationSplitSource()))
                && Objects.equals(allocation.getWmsReservationTicketId(), reservationTicket.getId());
        if (!sameWmsApplication && (allocation.getQty() == null
                || BigDecimal.valueOf(allocation.getQty()).compareTo(reservationTicket.getReserveCount()) != 0)) {
            throw new IllegalStateException("WMS reservation quantity snapshot changed allocationId=" + allocation.getId());
        }
        if (!sameWmsApplication && hasStartedMesProduction(allocation)) {
            throw new IllegalStateException("MES production started before WMS response was applied allocationId=" + allocation.getId());
        }
        BigDecimal splitTotal = reservationTicket.getStockFrozenCount()
                .add(reservationTicket.getInProductionCount())
                .add(reservationTicket.getSurplusCount());
        if (!sameWmsApplication && splitTotal.compareTo(BigDecimal.valueOf(allocation.getQty())) != 0) {
            throw new IllegalStateException("WMS allocation split does not preserve allocation quantity allocationId=" + allocation.getId());
        }
        if (allocationSplitService == null) {
            applyAllocationLegacy(reservationTicket, allocation);
            return;
        }
        allocationSplitService.applyWmsSplit(reservationTicket);

        WmsReservationTicket reservationUpdate = new WmsReservationTicket();
        reservationUpdate.setId(reservationTicket.getId());
        reservationUpdate.setStatus(STATUS_SUCCESS);
        reservationUpdate.setAppliedAt(LocalDateTime.now());
        reservationUpdate.setUpdatedAt(LocalDateTime.now());
        ticketMapper.updateById(reservationUpdate);
    }

    private void applyProductionRemainder(OrderDeliveryTicket ticket,
                                          WmsReservationTicket reservationTicket,
                                          List<OrderDeliveryTicketAllocation> ticketAllocations) {
        List<OrderDeliveryTicketAllocation> productionRows = ticketAllocations == null
                ? new ArrayList<>()
                : ticketAllocations.stream()
                        .filter(row -> TYPE_PRODUCTION_REQUIRED.equalsIgnoreCase(trim(row.getAllocationType())))
                        .filter(row -> ALLOCATION_ACTIVE.equalsIgnoreCase(trim(row.getStatus())))
                        .sorted(Comparator.comparing(OrderDeliveryTicketAllocation::getId))
                        .collect(Collectors.toCollection(ArrayList::new));
        BigDecimal remainder = reservationTicket.getSurplusCount();
        OrderDeliveryTicketAllocation primary = productionRows.isEmpty() ? null : productionRows.remove(0);
        boolean productionQtyChangedAfterWmsSubmission = primary != null
                && !Objects.equals(primary.getQty(), reservationTicket.getReserveCount().intValueExact());
        if (remainder.signum() > 0) {
            if (primary == null) {
                insertPositiveAllocation(ticket, reservationTicket, TYPE_PRODUCTION_REQUIRED, remainder, new HashSet<>());
            } else {
                if (!productionQtyChangedAfterWmsSubmission) {
                    primary.setQty(remainder.intValueExact());
                }
                primary.setTicketQty(reservationTicket.getReserveCount().intValueExact());
                primary.setMaterialNo(ticket.getMaterialNo());
                primary.setWmsReservationTicketId(reservationTicket.getId());
                primary.setAllocationSplitSource(AllocationSplitService.SOURCE_WMS);
                primary.setMesProductionStatus("PENDING");
                primary.setMesProductionRequestId(null);
                primary.setMesProductionExternalId(null);
                primary.setMesProductionSyncedAt(null);
                primary.setMesProductionMessage(null);
                primary.setUpdatedAt(LocalDateTime.now());
                allocationMapper.updateById(primary);
            }
        }
        if (remainder.signum() == 0 && primary != null) {
            if (!productionQtyChangedAfterWmsSubmission) {
                primary.setQty(0);
            }
            primary.setTicketQty(reservationTicket.getReserveCount().intValueExact());
            primary.setWmsReservationTicketId(reservationTicket.getId());
            primary.setAllocationSplitSource(AllocationSplitService.SOURCE_WMS);
            primary.setMesProductionStatus("PENDING");
            primary.setMesProductionRequestId(null);
            primary.setMesProductionExternalId(null);
            primary.setMesProductionSyncedAt(null);
            primary.setMesProductionMessage(null);
            primary.setUpdatedAt(LocalDateTime.now());
            allocationMapper.updateById(primary);
        }
        if (remainder.signum() == 0 && primary == null) {
            OrderDeliveryTicketAllocation zeroProduction = allocationRow(
                    ticket, reservationTicket, TYPE_PRODUCTION_REQUIRED, 0);
            allocationMapper.insert(zeroProduction);
        }
        for (OrderDeliveryTicketAllocation replaced : productionRows) {
            replaced.setStatus("SUPERSEDED");
            replaced.setUpdatedAt(LocalDateTime.now());
            allocationMapper.updateById(replaced);
        }
    }

    private void markLegacyWmsSplit(Integer ticketId, Long reservationTicketId) {
        List<OrderDeliveryTicketAllocation> rows = allocationMapper.selectByTicketIdForUpdate(ticketId);
        OrderDeliveryTicketAllocation parent = rows.stream()
                .filter(row -> TYPE_PRODUCTION_REQUIRED.equalsIgnoreCase(trim(row.getAllocationType())))
                .filter(row -> ALLOCATION_ACTIVE.equalsIgnoreCase(trim(row.getStatus())))
                .filter(row -> Objects.equals(row.getWmsReservationTicketId(), reservationTicketId))
                .findFirst()
                .orElse(null);
        if (parent == null) {
            return;
        }
        if (!AllocationSplitService.SOURCE_WMS.equalsIgnoreCase(trim(parent.getAllocationSplitSource()))) {
            parent.setAllocationSplitSource(AllocationSplitService.SOURCE_WMS);
            parent.setUpdatedAt(LocalDateTime.now());
            allocationMapper.updateById(parent);
        }
        for (OrderDeliveryTicketAllocation row : rows) {
            if (TYPE_IN_STOCK.equalsIgnoreCase(trim(row.getAllocationType()))
                    || TYPE_IN_PRODUCTION.equalsIgnoreCase(trim(row.getAllocationType()))) {
                if (!AllocationSplitService.SOURCE_WMS.equalsIgnoreCase(trim(row.getAllocationSplitSource()))
                        || !Objects.equals(row.getSplitParentAllocationId(), parent.getId())) {
                    row.setAllocationSplitSource(AllocationSplitService.SOURCE_WMS);
                    row.setSplitParentAllocationId(parent.getId());
                    row.setUpdatedAt(LocalDateTime.now());
                    allocationMapper.updateById(row);
                }
            }
        }
    }

    private void applyAllocationLegacy(WmsReservationTicket reservationTicket,
                                       OrderDeliveryTicketAllocation allocation) {
        Set<String> existingTypes = allocationMapper.selectByWmsReservationTicketId(reservationTicket.getId()).stream()
                .map(OrderDeliveryTicketAllocation::getAllocationType)
                .collect(Collectors.toCollection(HashSet::new));
        insertPositiveAllocation(allocation, reservationTicket, TYPE_IN_STOCK,
                reservationTicket.getStockFrozenCount(), existingTypes);
        insertPositiveAllocation(allocation, reservationTicket, TYPE_IN_PRODUCTION,
                reservationTicket.getInProductionCount(), existingTypes);
        allocation.setQty(reservationTicket.getSurplusCount().intValueExact());
        allocation.setWmsReservationTicketId(reservationTicket.getId());
        allocation.setMesProductionStatus("PENDING");
        allocation.setMesProductionRequestId(null);
        allocation.setMesProductionExternalId(null);
        allocation.setMesProductionSyncedAt(null);
        allocation.setMesProductionMessage(null);
        allocation.setUpdatedAt(LocalDateTime.now());
        allocationMapper.updateById(allocation);
        List<OrderDeliveryTicketAllocation> appliedAllocations = allocationMapper
                .selectByWmsReservationTicketId(reservationTicket.getId());
        if (appliedAllocations.stream().noneMatch(row -> Objects.equals(row.getId(), allocation.getId()))) {
            appliedAllocations = new ArrayList<>(appliedAllocations);
            appliedAllocations.add(allocation);
        }
        salesOrderAllocationLinkService.syncWmsAllocationsForAllocation(allocation.getId(), appliedAllocations);
    }

    private void insertPositiveAllocation(OrderDeliveryTicketAllocation source,
                                          WmsReservationTicket reservationTicket,
                                          String type,
                                          BigDecimal quantity,
                                          Set<String> existingTypes) {
        if (quantity == null || quantity.signum() <= 0 || existingTypes.contains(type)) {
            return;
        }
        OrderDeliveryTicketAllocation row = allocationRow(source, reservationTicket, type, quantity.intValueExact());
        allocationMapper.insert(row);
        existingTypes.add(type);
    }

    private OrderDeliveryTicketAllocation allocationRow(OrderDeliveryTicketAllocation source,
                                                        WmsReservationTicket reservationTicket,
                                                        String type,
                                                        Integer qty) {
        OrderDeliveryTicketAllocation row = new OrderDeliveryTicketAllocation();
        BeanUtils.copyProperties(source, row);
        row.setId(null);
        row.setWmsReservationTicketId(reservationTicket.getId());
        row.setAllocationSource("WMS_RESERVATION");
        row.setAllocationSplitSource(AllocationSplitService.SOURCE_WMS);
        row.setAllocationType(type);
        row.setQty(qty);
        row.setSourcePayload(sourcePayload(reservationTicket, type, qty));
        row.setMesProductionStatus("PENDING");
        row.setMesProductionRequestId(null);
        row.setMesProductionExternalId(null);
        row.setMesProductionSyncedAt(null);
        row.setMesProductionMessage(null);
        row.setStatus(ALLOCATION_ACTIVE);
        LocalDateTime now = LocalDateTime.now();
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        return row;
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
        row.setAllocationSource("WMS_RESERVATION");
        row.setAllocationSplitSource(AllocationSplitService.SOURCE_WMS);
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
        batch.setTicketCount(group.getAllocations().size());
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
            WmsReservationPlanService.AllocationReservation planAllocation = new WmsReservationPlanService.AllocationReservation();
            planAllocation.setAllocationId(ticket.getAllocationId());
            planAllocation.setTicketId(ticket.getTicketId());
            planAllocation.setSalesOrderItemId(ticket.getSalesOrderItemId());
            planAllocation.setSalesOrderLineNo(ticket.getSalesOrderLineNo());
            planAllocation.setProductCode(ticket.getProductCode());
            planAllocation.setReserveCount(ticket.getReserveCount());
            group.getAllocations().add(planAllocation);
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

            if (ticket.getAllocationId() == null) {
                OrderDeliveryTicket ticketUpdate = new OrderDeliveryTicket();
                ticketUpdate.setId(ticket.getTicketId());
                ticketUpdate.setWmsAllocateStatus(STATUS_FAILED);
                ticketUpdate.setWmsAllocateMessage(safeMessage);
                ticketSourceMapper.updateById(ticketUpdate);
            }
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

    private void assertNoActiveAllocations(WmsReservationPlanService.ReservationGroup group) {
        for (WmsReservationPlanService.AllocationReservation source : stableAllocations(group.getAllocations())) {
            SalesOrderItem item = salesOrderAllocationLinkService.lockSalesOrderForAllocation(source.getAllocationId());
            if (!Objects.equals(item.getSalesOrderId(), group.getSaleOrderId())) {
                throw new IllegalStateException("WMS reservation allocation belongs to another sales order allocationId="
                        + source.getAllocationId());
            }
            OrderDeliveryTicketAllocation allocation = allocationMapper.selectByIdForUpdate(source.getAllocationId());
            if (allocation == null
                    || !TYPE_PRODUCTION_REQUIRED.equalsIgnoreCase(trim(allocation.getAllocationType()))
                    || !ALLOCATION_ACTIVE.equalsIgnoreCase(trim(allocation.getStatus()))
                    || Boolean.TRUE.equals(allocation.getDiscarded())) {
                throw new IllegalStateException("production allocation is unavailable allocationId=" + source.getAllocationId());
            }
            if (allocation.getQty() == null || allocation.getQty() <= 0
                    || BigDecimal.valueOf(allocation.getQty()).compareTo(source.getReserveCount()) != 0) {
                throw new IllegalStateException("WMS allocation quantity changed before request allocationId=" + source.getAllocationId());
            }
            if (!Objects.equals(trim(allocation.getMaterialNo()), trim(source.getProductCode()))) {
                throw new IllegalStateException("WMS allocation material changed before request allocationId=" + source.getAllocationId());
            }
            if (allocation.getWmsReservationTicketId() != null) {
                throw new IllegalStateException("WMS reservation already applied allocationId=" + source.getAllocationId());
            }
            if (hasStartedMesProduction(allocation)) {
                throw new IllegalStateException("MES production is already in progress or completed allocationId=" + source.getAllocationId());
            }
            WmsReservationTicket active = ticketMapper.selectActiveByAllocationId(source.getAllocationId());
            if (active != null) {
                throw new IllegalStateException("allocation already has active WMS reservation allocationId=" + source.getAllocationId()
                        + " activeStatus=" + active.getStatus());
            }
        }
    }

    private boolean hasStartedMesProduction(OrderDeliveryTicketAllocation allocation) {
        if (allocation == null
                || !TYPE_PRODUCTION_REQUIRED.equalsIgnoreCase(trim(allocation.getAllocationType()))
                || !"ACTIVE".equalsIgnoreCase(trim(allocation.getStatus()))) {
            return false;
        }
        String status = trim(allocation.getMesProductionStatus());
        return "PROCESSING".equalsIgnoreCase(status)
                || "SUBMITTING".equalsIgnoreCase(status)
                || "RESULT_UNKNOWN".equalsIgnoreCase(status)
                || "SUCCESS".equalsIgnoreCase(status);
    }

    private List<WmsReservationPlanService.AllocationReservation> stableAllocations(
            List<WmsReservationPlanService.AllocationReservation> allocations) {
        return allocations.stream()
                .sorted(Comparator.comparing(WmsReservationPlanService.AllocationReservation::getSalesOrderLineNo,
                                Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(WmsReservationPlanService.AllocationReservation::getAllocationId,
                                Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(WmsReservationPlanService.AllocationReservation::getTicketId,
                                Comparator.nullsLast(Integer::compareTo)))
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

    private Long reservationKey(Integer allocationId, Integer ticketId) {
        if (allocationId != null) {
            return allocationId.longValue();
        }
        if (ticketId != null) {
            return -ticketId.longValue();
        }
        throw new IllegalStateException("WMS reservation snapshot has no allocation or ticket identifier");
    }

    private void requireGroup(WmsReservationPlanService.ReservationGroup group) {
        if (group == null || trim(group.getSaleNo()) == null || group.getItems().isEmpty() || group.getAllocations().isEmpty()) {
            throw new IllegalArgumentException("WMS reservation group must include saleNo, items, and allocations");
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
