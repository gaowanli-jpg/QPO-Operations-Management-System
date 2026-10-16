package com.machining.quote.service.mes;

import com.machining.quote.entity.MesIntegrationBatch;
import com.machining.quote.entity.MesMaterialBatchItem;
import com.machining.quote.entity.MesProductionBatchItem;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderDeliveryTicketAllocation;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.MesIntegrationBatchMapper;
import com.machining.quote.mapper.MesIntegrationReconcileRunMapper;
import com.machining.quote.mapper.MesMaterialBatchItemMapper;
import com.machining.quote.mapper.MesProductionBatchItemMapper;
import com.machining.quote.mapper.OrderDeliveryTicketAllocationMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMesRequestMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.mapper.WmsReservationTicketMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class MesBatchStateService {
    public static final String BATCH_TYPE_MATERIAL = "MATERIAL_PREPARATION";
    public static final String BATCH_TYPE_PRODUCTION = "PRODUCTION_DEMAND";
    public static final String STATUS_RESERVED = "RESERVED";

    private static final DateTimeFormatter BIZ_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final char[] CROCKFORD32 = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MesIntegrationBatchMapper batchMapper;
    private final MesMaterialBatchItemMapper materialBatchItemMapper;
    private final MesProductionBatchItemMapper productionBatchItemMapper;
    @SuppressWarnings("unused")
    private final MesIntegrationReconcileRunMapper reconcileRunMapper;
    private final OrderDeliveryTicketMapper ticketMapper;
    private final OrderDeliveryTicketAllocationMapper allocationMapper;
    private final OrderDeliveryTicketMesRequestMapper mesRequestMapper;
    private final MesBatchFingerprintService fingerprintService;
    private final WmsReservationTicketMapper wmsReservationTicketMapper;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private SalesOrderItemMapper salesOrderItemMapper;

    public MesBatchStateService(MesIntegrationBatchMapper batchMapper,
                                MesMaterialBatchItemMapper materialBatchItemMapper,
                                MesProductionBatchItemMapper productionBatchItemMapper,
                                MesIntegrationReconcileRunMapper reconcileRunMapper,
                                OrderDeliveryTicketMapper ticketMapper,
                                OrderDeliveryTicketAllocationMapper allocationMapper,
                                OrderDeliveryTicketMesRequestMapper mesRequestMapper,
                                MesBatchFingerprintService fingerprintService) {
        this(batchMapper, materialBatchItemMapper, productionBatchItemMapper, reconcileRunMapper,
                ticketMapper, allocationMapper, mesRequestMapper, fingerprintService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public MesBatchStateService(MesIntegrationBatchMapper batchMapper,
                                MesMaterialBatchItemMapper materialBatchItemMapper,
                                MesProductionBatchItemMapper productionBatchItemMapper,
                                MesIntegrationReconcileRunMapper reconcileRunMapper,
                                OrderDeliveryTicketMapper ticketMapper,
                                OrderDeliveryTicketAllocationMapper allocationMapper,
                                OrderDeliveryTicketMesRequestMapper mesRequestMapper,
                                MesBatchFingerprintService fingerprintService,
                                WmsReservationTicketMapper wmsReservationTicketMapper) {
        this.batchMapper = batchMapper;
        this.materialBatchItemMapper = materialBatchItemMapper;
        this.productionBatchItemMapper = productionBatchItemMapper;
        this.reconcileRunMapper = reconcileRunMapper;
        this.ticketMapper = ticketMapper;
        this.allocationMapper = allocationMapper;
        this.mesRequestMapper = mesRequestMapper;
        this.fingerprintService = fingerprintService;
        this.wmsReservationTicketMapper = wmsReservationTicketMapper;
    }

    @Transactional
    public MaterialBatchReservationResult reserveMaterialBatch(MaterialBatchReservation reservation) {
        return reserveMaterialBatch(newMaterialBizId(), reservation);
    }

    @Transactional
    public MaterialBatchReservationResult reserveMaterialBatch(String bizId, MaterialBatchReservation reservation) {
        requireItems(reservation.items(), "material batch items");
        MesIntegrationBatch batch = newBatch(
                BATCH_TYPE_MATERIAL,
                trimToEmpty(bizId),
                reservation.systemTaskId(),
                reservation.createdBy(),
                reservation.groupKey(),
                reservation.requestFingerprint(),
                reservation.items().size());
        batchMapper.insert(batch);
        requireBatchId(batch);

        List<MesMaterialBatchItem> items = new ArrayList<>();
        for (MaterialItemReservation source : reservation.items()) {
            MesMaterialBatchItem item = new MesMaterialBatchItem();
            item.setBatchId(batch.getId());
            item.setTicketId(source.ticketId());
            item.setLineRequestId(newMaterialLineRequestId());
            item.setMesRequestRowId(source.mesRequestRowId());
            item.setDrawingNo(trimToEmpty(source.drawingNo()));
            item.setDrawingVersion(trimToEmpty(source.drawingVersion()));
            item.setItemKey(trimToEmpty(source.itemKey()));
            item.setStatus(STATUS_RESERVED);
            materialBatchItemMapper.insert(item);
            ticketMapper.markMesMaterialLineReserved(source.ticketId(), item.getLineRequestId());
            if (source.mesRequestRowId() != null) {
                mesRequestMapper.linkMesBatchItem(source.mesRequestRowId(), batch.getId(), item.getId());
            }
            items.add(item);
        }
        return new MaterialBatchReservationResult(batch, List.copyOf(items));
    }

    @Transactional
    public ProductionBatchReservationResult reserveProductionBatch(ProductionBatchReservation reservation) {
        return reserveProductionBatch(newProductionBizId(), reservation);
    }

    @Transactional
    public ProductionBatchReservationResult reserveProductionBatch(String bizId, ProductionBatchReservation reservation) {
        requireItems(reservation.items(), "production batch items");
        Map<Integer, OrderDeliveryTicketAllocation> allocationsById = loadProductionAllocations(reservation.items());
        Map<Integer, SalesOrderItem> salesItemsByAllocationId = loadProductionItems(reservation.items());
        Map<Integer, OrderDeliveryTicket> ticketsById = lockProductionTickets(
                reservation.items(), allocationsById, salesItemsByAllocationId);
        MesIntegrationBatch batch = newBatch(
                BATCH_TYPE_PRODUCTION,
                trimToEmpty(bizId),
                reservation.systemTaskId(),
                reservation.createdBy(),
                reservation.groupKey(),
                reservation.requestFingerprint(),
                reservation.items().size());
        batchMapper.insert(batch);
        requireBatchId(batch);

        List<MesProductionBatchItem> items = new ArrayList<>();
        for (ProductionItemReservation source : reservation.items()) {
            MesProductionBatchItem item = new MesProductionBatchItem();
            item.setBatchId(batch.getId());
            item.setAllocationId(source.allocationId());
            item.setLineRequestId(newProductionLineRequestId());
            item.setSaleOrderNo(trimToEmpty(source.saleOrderNo()));
            item.setSaleOrderItemNo(trimToEmpty(source.saleOrderItemNo()));
            item.setProductCode(trimToEmpty(source.productCode()));
            item.setItemKey(trimToEmpty(source.itemKey()));
            item.setDemandQty(source.demandQty());
            item.setStatus(STATUS_RESERVED);
            reserveProductionAllocation(
                    source,
                    item.getLineRequestId(),
                    allocationsById,
                    ticketsById,
                    salesItemsByAllocationId);
            productionBatchItemMapper.insert(item);
            items.add(item);
        }
        return new ProductionBatchReservationResult(batch, List.copyOf(items));
    }

    private Map<Integer, OrderDeliveryTicket> lockProductionTickets(
            List<ProductionItemReservation> reservations,
            Map<Integer, OrderDeliveryTicketAllocation> allocationsById,
            Map<Integer, SalesOrderItem> salesItemsByAllocationId) {
        Map<Integer, List<ProductionItemReservation>> reservationsByTicketId = new LinkedHashMap<>();
        for (ProductionItemReservation reservation : reservations) {
            OrderDeliveryTicketAllocation allocation = allocationsById.get(reservation.allocationId());
            if (allocation != null && allocation.getTicketId() != null) {
                reservationsByTicketId.computeIfAbsent(allocation.getTicketId(), ignored -> new ArrayList<>())
                        .add(reservation);
            }
        }
        List<Integer> ticketIds = reservationsByTicketId.keySet().stream().toList();
        Map<Integer, OrderDeliveryTicket> ticketsById = new LinkedHashMap<>();
        if (!ticketIds.isEmpty()) {
            List<OrderDeliveryTicket> tickets = ticketMapper.selectBatchIdsForUpdate(ticketIds);
            if (tickets != null) {
                for (OrderDeliveryTicket ticket : tickets) {
                    if (ticket != null && ticket.getId() != null) {
                        ticketsById.put(ticket.getId(), ticket);
                    }
                }
            }
            for (Integer ticketId : ticketIds) {
                if (ticketsById.containsKey(ticketId)) {
                    continue;
                }
                OrderDeliveryTicket ticket = ticketMapper.selectByIdForUpdate(ticketId);
                if (ticket != null && ticket.getId() != null) {
                    ticketsById.put(ticket.getId(), ticket);
                }
            }
        }
        for (Map.Entry<Integer, List<ProductionItemReservation>> entry : reservationsByTicketId.entrySet()) {
            Integer ticketId = entry.getKey();
            OrderDeliveryTicket ticket = ticketsById.get(ticketId);
            if (ticket == null || Boolean.TRUE.equals(ticket.getDiscarded())) {
                throw new IllegalStateException("Delivery ticket is unavailable before MES reservation ticketId=" + ticketId);
            }
            for (ProductionItemReservation reservation : entry.getValue()) {
                SalesOrderItem salesItem = salesItemsByAllocationId.get(reservation.allocationId());
                String materialNo = salesItem == null ? ticket.getMaterialNo() : salesItem.getMaterialNo();
                if (salesOrderItemMapper != null && salesItem == null) {
                    throw new IllegalStateException("MES sales order item is not linked allocationId=" + reservation.allocationId());
                }
                if (!"SUCCESS".equalsIgnoreCase(ticket.getMesSyncStatus())
                        || materialNo == null || materialNo.isBlank()) {
                    throw new IllegalStateException("MES material number is not confirmed ticketId=" + ticketId);
                }
            }
            if (wmsReservationTicketMapper != null
                    && wmsReservationTicketMapper.selectActiveByTicketId(ticketId) != null) {
                throw new IllegalStateException("WMS inventory operation is still active for ticketId=" + ticketId);
            }
        }
        return ticketsById;
    }

    private void reserveProductionAllocation(
            ProductionItemReservation source,
            String lineRequestId,
            Map<Integer, OrderDeliveryTicketAllocation> allocationsById,
            Map<Integer, OrderDeliveryTicket> ticketsById,
            Map<Integer, SalesOrderItem> salesItemsByAllocationId) {
        OrderDeliveryTicketAllocation observed = allocationsById.get(source.allocationId());
        if (observed == null || observed.getTicketId() == null) {
            throw new IllegalStateException("Production allocation does not exist allocationId=" + source.allocationId());
        }
        OrderDeliveryTicket ticket = ticketsById.get(observed.getTicketId());
        if (ticket == null || Boolean.TRUE.equals(ticket.getDiscarded())) {
            throw new IllegalStateException("Delivery ticket is unavailable before MES reservation ticketId=" + observed.getTicketId());
        }
        if (wmsReservationTicketMapper != null
                && wmsReservationTicketMapper.selectActiveByTicketId(observed.getTicketId()) != null) {
            throw new IllegalStateException("WMS inventory operation is still active for ticketId=" + observed.getTicketId());
        }
        OrderDeliveryTicketAllocation allocation = allocationsById.get(source.allocationId());
        if (allocation == null
                || !"PRODUCTION_REQUIRED".equals(allocation.getAllocationType())
                || !"ACTIVE".equalsIgnoreCase(allocation.getStatus())
                || allocation.getTicketId() == null) {
            throw new IllegalStateException("Production allocation changed before MES reservation allocationId=" + source.allocationId());
        }
        if (salesOrderItemMapper != null) {
            SalesOrderItem salesItem = salesItemsByAllocationId.get(source.allocationId());
            if (salesItem == null
                    || !Objects.equals(salesItem.getAllocationId(), source.allocationId().longValue())
                    || salesItem.getQty() == null
                    || BigDecimal.valueOf(salesItem.getQty()).compareTo(source.demandQty()) != 0
                    || !Objects.equals(trimToEmpty(salesItem.getMaterialNo()), trimToEmpty(source.productCode()))) {
                throw new IllegalStateException("Sales order item changed before MES reservation allocationId=" + source.allocationId());
            }
        } else if (allocation.getQty() == null
                || BigDecimal.valueOf(allocation.getQty()).compareTo(source.demandQty()) != 0) {
            throw new IllegalStateException("Production allocation changed before MES reservation allocationId=" + source.allocationId());
        }
        String status = allocation.getMesProductionStatus();
        if (status != null && !status.isBlank()
                && !"PENDING".equalsIgnoreCase(status)
                && !"FAILED".equalsIgnoreCase(status)) {
            throw new IllegalStateException("MES production allocation is not available allocationId=" + source.allocationId()
                    + " status=" + status);
        }
        if (allocationMapper.markMesProductionLineReserved(source.allocationId(), lineRequestId) <= 0) {
            throw new IllegalStateException("Production allocation was concurrently reserved allocationId=" + source.allocationId());
        }
    }

    private Map<Integer, OrderDeliveryTicketAllocation> loadProductionAllocations(
            List<ProductionItemReservation> reservations) {
        List<Integer> allocationIds = reservations.stream()
                .map(ProductionItemReservation::allocationId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (allocationIds.isEmpty()) {
            return Map.of();
        }
        List<OrderDeliveryTicketAllocation> allocations = allocationMapper.selectBatchIdsForUpdate(allocationIds);
        Map<Integer, OrderDeliveryTicketAllocation> result = new LinkedHashMap<>();
        if (allocations != null) {
            for (OrderDeliveryTicketAllocation allocation : allocations) {
                if (allocation != null && allocation.getId() != null) {
                    result.put(allocation.getId(), allocation);
                }
            }
        }
        for (Integer allocationId : allocationIds) {
            if (result.containsKey(allocationId)) {
                continue;
            }
            OrderDeliveryTicketAllocation allocation = allocationMapper.selectByIdForUpdate(allocationId);
            if (allocation == null) {
                allocation = allocationMapper.selectById(allocationId);
            }
            if (allocation != null && allocation.getId() != null) {
                result.put(allocation.getId(), allocation);
            }
        }
        return result;
    }

    private Map<Integer, SalesOrderItem> loadProductionItems(List<ProductionItemReservation> reservations) {
        if (salesOrderItemMapper == null) {
            return Map.of();
        }
        List<Long> allocationIds = reservations.stream()
                .map(ProductionItemReservation::allocationId)
                .filter(Objects::nonNull)
                .distinct()
                .map(Integer::longValue)
                .toList();
        if (allocationIds.isEmpty()) {
            return Map.of();
        }
        List<SalesOrderItem> items = salesOrderItemMapper.selectByAllocationIdsForUpdate(allocationIds);
        Map<Integer, SalesOrderItem> result = new LinkedHashMap<>();
        if (items != null) {
            for (SalesOrderItem item : items) {
                if (item != null && item.getAllocationId() != null) {
                    result.put(item.getAllocationId().intValue(), item);
                }
            }
        }
        return result;
    }

    private MesIntegrationBatch newBatch(String batchType,
                                         String bizId,
                                         Long systemTaskId,
                                         Long createdBy,
                                         String groupKey,
                                         String requestFingerprint,
                                         int itemCount) {
        LocalDateTime now = LocalDateTime.now();
        MesIntegrationBatch batch = new MesIntegrationBatch();
        batch.setBatchType(batchType);
        batch.setBizId(bizId);
        batch.setSystemTaskId(systemTaskId);
        batch.setGroupKeyHash(fingerprintService.groupKeyHash(groupKey));
        batch.setRequestFingerprint(requireHash(requestFingerprint));
        batch.setItemCount(itemCount);
        batch.setStatus(STATUS_RESERVED);
        batch.setAttemptNo(1);
        batch.setCreatedBy(createdBy);
        batch.setCreatedAt(now);
        batch.setUpdatedAt(now);
        return batch;
    }

    private String requireHash(String value) {
        if (value == null || value.length() != 64) {
            throw new IllegalArgumentException("MES request fingerprint must be a 64 character SHA-256 hex value");
        }
        return value;
    }

    private void requireItems(List<?> items, String label) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be empty");
        }
    }

    private void requireBatchId(MesIntegrationBatch batch) {
        if (batch.getId() == null) {
            throw new IllegalStateException("MES batch insert did not return id");
        }
    }

    private String newMaterialBizId() {
        return "QPO-MAT-" + LocalDate.now().format(BIZ_DATE) + "-" + ulid();
    }

    private String newProductionBizId() {
        return "QPO-DEM-" + LocalDate.now().format(BIZ_DATE) + "-" + ulid();
    }

    private String newMaterialLineRequestId() {
        return "QPO-MATL-" + ulid();
    }

    private String newProductionLineRequestId() {
        return "QPO-DEML-" + ulid();
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

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    public record MaterialBatchReservation(
            Long systemTaskId,
            Long createdBy,
            String groupKey,
            String requestFingerprint,
            List<MaterialItemReservation> items) {
    }

    public record MaterialItemReservation(
            Integer ticketId,
            Long mesRequestRowId,
            String drawingNo,
            String drawingVersion,
            String itemKey) {
    }

    public record MaterialBatchReservationResult(
            MesIntegrationBatch batch,
            List<MesMaterialBatchItem> items) {
    }

    public record ProductionBatchReservation(
            Long systemTaskId,
            Long createdBy,
            String groupKey,
            String requestFingerprint,
            List<ProductionItemReservation> items) {
    }

    public record ProductionItemReservation(
            Integer allocationId,
            String saleOrderNo,
            String saleOrderItemNo,
            String productCode,
            String itemKey,
            BigDecimal demandQty) {
    }

    public record ProductionBatchReservationResult(
            MesIntegrationBatch batch,
            List<MesProductionBatchItem> items) {
    }
}
