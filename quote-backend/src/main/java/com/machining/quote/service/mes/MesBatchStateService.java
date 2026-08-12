package com.machining.quote.service.mes;

import com.machining.quote.entity.MesIntegrationBatch;
import com.machining.quote.entity.MesMaterialBatchItem;
import com.machining.quote.entity.MesProductionBatchItem;
import com.machining.quote.mapper.MesIntegrationBatchMapper;
import com.machining.quote.mapper.MesIntegrationReconcileRunMapper;
import com.machining.quote.mapper.MesMaterialBatchItemMapper;
import com.machining.quote.mapper.MesProductionBatchItemMapper;
import com.machining.quote.mapper.OrderDeliveryTicketAllocationMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMesRequestMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

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

    public MesBatchStateService(MesIntegrationBatchMapper batchMapper,
                                MesMaterialBatchItemMapper materialBatchItemMapper,
                                MesProductionBatchItemMapper productionBatchItemMapper,
                                MesIntegrationReconcileRunMapper reconcileRunMapper,
                                OrderDeliveryTicketMapper ticketMapper,
                                OrderDeliveryTicketAllocationMapper allocationMapper,
                                OrderDeliveryTicketMesRequestMapper mesRequestMapper,
                                MesBatchFingerprintService fingerprintService) {
        this.batchMapper = batchMapper;
        this.materialBatchItemMapper = materialBatchItemMapper;
        this.productionBatchItemMapper = productionBatchItemMapper;
        this.reconcileRunMapper = reconcileRunMapper;
        this.ticketMapper = ticketMapper;
        this.allocationMapper = allocationMapper;
        this.mesRequestMapper = mesRequestMapper;
        this.fingerprintService = fingerprintService;
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
            productionBatchItemMapper.insert(item);
            allocationMapper.markMesProductionLineReserved(source.allocationId(), item.getLineRequestId());
            items.add(item);
        }
        return new ProductionBatchReservationResult(batch, List.copyOf(items));
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
