package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.config.AuthContext;
import com.machining.quote.config.DataScopeAccessDeniedException;
import com.machining.quote.config.PreviewFingerprintConflictException;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.ReleaseItemDataReplacementAcceptedQtyDTO;
import com.machining.quote.dto.ReleaseItemDataReplacementChangeDTO;
import com.machining.quote.dto.ReleaseItemDataReplacementConfirmDTO;
import com.machining.quote.dto.ReleaseItemDataReplacementExcelErrorDTO;
import com.machining.quote.dto.ReleaseItemDataReplacementExcelParseResult;
import com.machining.quote.dto.ReleaseItemDataReplacementExcelRowDTO;
import com.machining.quote.dto.ReleaseItemDataReplacementField;
import com.machining.quote.dto.ReleaseItemDataReplacementPreviewDTO;
import com.machining.quote.dto.ReleaseItemDataReplacementRowDTO;
import com.machining.quote.dto.ReleaseItemDataReplacementRowStatus;
import com.machining.quote.dto.ReleaseItemDataReplacementSummaryDTO;
import com.machining.quote.dto.ReleaseOrderSource;
import com.machining.quote.dto.ReleaseSourceType;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.util.DrawingPartNoResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReleaseItemDataReplacementService {
    private static final Logger log = LoggerFactory.getLogger(ReleaseItemDataReplacementService.class);
    private static final String DATA_SCOPE_DENIED_MESSAGE = "当前账号无权访问所选询价单";
    private static final String FINGERPRINT_CONFLICT_MESSAGE = "数据已发生变化，请重新预览";
    private static final int MAX_AUDIT_TARGET_ID_LENGTH = 120;

    private final ReleaseItemDataReplacementExcelParser excelParser;
    private final OrderRfqMapper orderMapper;
    private final ItemQuoteVersionMapper itemMapper;
    private final OrderDeliveryTicketMapper ticketMapper;
    private final DataScopeService dataScopeService;
    private final ReleaseItemMutationPolicy mutationPolicy;
    private final AuditService auditService;
    private final InternalTargetLeadTimeService releaseItemRowService;

    public ReleaseItemDataReplacementService(
            ReleaseItemDataReplacementExcelParser excelParser,
            OrderRfqMapper orderMapper,
            ItemQuoteVersionMapper itemMapper,
            OrderDeliveryTicketMapper ticketMapper,
            DataScopeService dataScopeService,
            ReleaseItemMutationPolicy mutationPolicy) {
        this(
                excelParser,
                orderMapper,
                itemMapper,
                ticketMapper,
                dataScopeService,
                mutationPolicy,
                null,
                null);
    }

    @Autowired
    public ReleaseItemDataReplacementService(
            ReleaseItemDataReplacementExcelParser excelParser,
            OrderRfqMapper orderMapper,
            ItemQuoteVersionMapper itemMapper,
            OrderDeliveryTicketMapper ticketMapper,
            DataScopeService dataScopeService,
            ReleaseItemMutationPolicy mutationPolicy,
            AuditService auditService,
            InternalTargetLeadTimeService releaseItemRowService) {
        this.excelParser = excelParser;
        this.orderMapper = orderMapper;
        this.itemMapper = itemMapper;
        this.ticketMapper = ticketMapper;
        this.dataScopeService = dataScopeService;
        this.mutationPolicy = mutationPolicy;
        this.auditService = auditService;
        this.releaseItemRowService = releaseItemRowService;
    }

    public ReleaseItemDataReplacementPreviewDTO preview(
            List<ReleaseOrderSource> sources,
            InputStream excelInput) {
        List<Integer> rfqIds = normalizeRfqIds(sources);
        Map<Integer, OrderRfq> ordersById = loadAndValidateOrders(rfqIds);
        ReleaseItemDataReplacementExcelParseResult parsed = excelParser.parse(excelInput);
        List<ItemQuoteVersion> items = listItems(rfqIds);
        List<OrderDeliveryTicket> tickets = listTickets(rfqIds, items);
        return buildPreview(rfqIds, ordersById, parsed, items, tickets);
    }

    @Transactional
    public ReleaseItemDataReplacementConfirmDTO confirm(
            List<ReleaseOrderSource> sources,
            InputStream excelInput,
            String previewFingerprint) {
        long startedAt = System.currentTimeMillis();
        int requestedSourceCount = sources == null ? 0 : sources.size();
        try {
            requireConfirmationDependencies();
            List<Integer> rfqIds = normalizeRfqIds(sources);
            Map<Integer, OrderRfq> ordersById = lockAndAuthorizeOrders(rfqIds);
            ReleaseItemDataReplacementExcelParseResult parsed = excelParser.parse(excelInput);
            log.info("Release item data replacement started sourceCount={} importedCount={}",
                    rfqIds.size(), safeList(parsed.getRows()).size());

            List<ItemQuoteVersion> itemsBeforeLock = listItems(rfqIds);
            lockMatchedItems(parsed, itemsBeforeLock);
            List<ItemQuoteVersion> lockedItems = listItems(rfqIds);
            List<OrderDeliveryTicket> tickets = listTickets(rfqIds, lockedItems);
            ReleaseItemDataReplacementPreviewDTO currentPreview = buildPreview(
                    rfqIds,
                    ordersById,
                    parsed,
                    lockedItems,
                    tickets);

            if (!fingerprintsEqual(previewFingerprint, currentPreview.getPreviewFingerprint())) {
                throw new PreviewFingerprintConflictException(FINGERPRINT_CONFLICT_MESSAGE);
            }
            validateOrderBusinessRules(rfqIds, ordersById);
            if (!currentPreview.isCanConfirm()) {
                throw new IllegalArgumentException("当前替换数据不能确认，请重新预览");
            }

            ReleaseItemDataReplacementConfirmDTO result = applyConfirmation(
                    rfqIds,
                    ordersById,
                    lockedItems,
                    tickets,
                    currentPreview);
            log.info("Release item data replacement completed sourceCount={} updatedItemCount={} "
                            + "quantityAppliedCount={} unmatchedCount={} skippedCount={} durationMs={}",
                    rfqIds.size(),
                    result.getSummary().getUpdatedItemCount(),
                    result.getSummary().getQuantityAppliedCount(),
                    result.getSummary().getUnmatchedCount(),
                    result.getSummary().getSkippedCount(),
                    System.currentTimeMillis() - startedAt);
            return result;
        } catch (PreviewFingerprintConflictException | DataScopeAccessDeniedException | IllegalArgumentException e) {
            log.warn("Release item data replacement rejected sourceCount={} reason={}",
                    requestedSourceCount, e.getClass().getSimpleName());
            throw e;
        } catch (RuntimeException e) {
            log.error("Release item data replacement failed sourceCount={} durationMs={}",
                    requestedSourceCount, System.currentTimeMillis() - startedAt, e);
            throw e;
        }
    }

    private ReleaseItemDataReplacementPreviewDTO buildPreview(
            List<Integer> rfqIds,
            Map<Integer, OrderRfq> ordersById,
            ReleaseItemDataReplacementExcelParseResult parsed,
            List<ItemQuoteVersion> items,
            List<OrderDeliveryTicket> tickets) {
        Map<Integer, Integer> ticketedQtyByItemId = ticketedQuantities(items, tickets);
        Map<ItemKey, List<ItemQuoteVersion>> candidatesByKey = groupCandidates(items);

        List<ReleaseItemDataReplacementRowDTO> rows = new ArrayList<>();
        for (ReleaseItemDataReplacementExcelRowDTO excelRow : safeList(parsed.getRows())) {
            rows.addAll(previewRows(
                    excelRow,
                    candidatesByKey,
                    ordersById,
                    ticketedQtyByItemId));
        }

        ReleaseItemDataReplacementPreviewDTO preview = new ReleaseItemDataReplacementPreviewDTO();
        preview.setSuccess(true);
        preview.setRecognizedFields(new ArrayList<>(safeList(parsed.getRecognizedFields())));
        preview.setRows(rows);
        preview.setSummary(summarize(rows, safeList(parsed.getRows()).size()));
        preview.setCanConfirm(preview.getSummary().getErrorCount() == 0
                && (preview.getSummary().getChangedCount() > 0
                || preview.getSummary().getQuantityOnlyCount() > 0));
        preview.setPreviewFingerprint(fingerprint(
                rfqIds,
                ordersById,
                parsed,
                rows,
                candidatesByKey,
                ticketedQtyByItemId));
        return preview;
    }

    private List<Integer> normalizeRfqIds(List<ReleaseOrderSource> sources) {
        if (sources == null || sources.isEmpty()) {
            throw new IllegalArgumentException("至少选择一个 RFQ 询价订单");
        }
        Set<Integer> rfqIds = new LinkedHashSet<>();
        for (ReleaseOrderSource source : sources) {
            if (source == null || source.getSourceType() != ReleaseSourceType.RFQ) {
                throw new IllegalArgumentException("表格数据替换只支持 RFQ 询价订单");
            }
            Long sourceOrderId = source.getSourceOrderId();
            if (sourceOrderId == null || sourceOrderId <= 0 || sourceOrderId > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("RFQ 询价订单 ID 不正确");
            }
            rfqIds.add(sourceOrderId.intValue());
        }
        return rfqIds.stream().sorted().toList();
    }

    private Map<Integer, OrderRfq> loadAndValidateOrders(List<Integer> rfqIds) {
        Map<Integer, OrderRfq> ordersById = indexOrders(
                rfqIds,
                safeList(orderMapper.selectBatchIds(rfqIds)));
        assertOrderDataScope(rfqIds, ordersById);
        validateOrderBusinessRules(rfqIds, ordersById);
        return ordersById;
    }

    private Map<Integer, OrderRfq> lockAndAuthorizeOrders(List<Integer> rfqIds) {
        List<OrderRfq> lockedOrders = new ArrayList<>();
        for (Integer rfqId : rfqIds) {
            OrderRfq order = orderMapper.selectByIdForUpdate(rfqId);
            if (order == null) {
                throw new PreviewFingerprintConflictException(FINGERPRINT_CONFLICT_MESSAGE);
            }
            lockedOrders.add(order);
        }
        Map<Integer, OrderRfq> ordersById = indexOrders(rfqIds, lockedOrders);
        assertOrderDataScope(rfqIds, ordersById);
        return ordersById;
    }

    private Map<Integer, OrderRfq> indexOrders(List<Integer> rfqIds, List<OrderRfq> orders) {
        Map<Integer, OrderRfq> ordersById = orders.stream()
                .filter(Objects::nonNull)
                .filter(order -> order.getId() != null)
                .collect(Collectors.toMap(
                        OrderRfq::getId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new));
        if (ordersById.size() != rfqIds.size() || !ordersById.keySet().containsAll(rfqIds)) {
            throw new IllegalArgumentException("所选询价单不存在或已删除");
        }
        return ordersById;
    }

    private void assertOrderDataScope(
            List<Integer> rfqIds,
            Map<Integer, OrderRfq> ordersById) {
        for (Integer rfqId : rfqIds) {
            try {
                dataScopeService.assertCanAccessOrder(ordersById.get(rfqId));
            } catch (RuntimeException e) {
                throw new DataScopeAccessDeniedException(DATA_SCOPE_DENIED_MESSAGE);
            }
        }
    }

    private void validateOrderBusinessRules(
            List<Integer> rfqIds,
            Map<Integer, OrderRfq> ordersById) {
        String customerName = null;
        boolean first = true;
        for (Integer rfqId : rfqIds) {
            OrderRfq order = ordersById.get(rfqId);
            if (!QuoteStatusWorkflow.canReadReleaseWorkspace(order.getStatus())) {
                throw new IllegalArgumentException("所选询价单状态不能进入议价提单");
            }
            String currentCustomer = trimToNull(order.getCustomerName());
            if (first) {
                customerName = currentCustomer;
                first = false;
            } else if (!Objects.equals(customerName, currentCustomer)) {
                throw new IllegalArgumentException("一次表格数据替换只能选择同一客户的询价单");
            }
        }
    }

    private void lockMatchedItems(
            ReleaseItemDataReplacementExcelParseResult parsed,
            List<ItemQuoteVersion> items) {
        Set<ItemKey> importedKeys = safeList(parsed.getRows()).stream()
                .map(row -> itemKey(row.getNormalizedPartNo(), row.getNormalizedVersion()))
                .filter(key -> !key.partNo().isEmpty() && !key.version().isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<Integer> matchingItemIds = items.stream()
                .filter(Objects::nonNull)
                .filter(item -> importedKeys.contains(itemKey(item.getPartNo(), item.getVersion())))
                .map(ItemQuoteVersion::getId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        for (Integer itemId : matchingItemIds) {
            if (itemMapper.selectByIdForUpdate(itemId) == null) {
                throw new PreviewFingerprintConflictException(FINGERPRINT_CONFLICT_MESSAGE);
            }
        }
    }

    private ReleaseItemDataReplacementConfirmDTO applyConfirmation(
            List<Integer> rfqIds,
            Map<Integer, OrderRfq> ordersById,
            List<ItemQuoteVersion> items,
            List<OrderDeliveryTicket> tickets,
            ReleaseItemDataReplacementPreviewDTO preview) {
        Map<Integer, ItemQuoteVersion> itemsById = items.stream()
                .filter(Objects::nonNull)
                .filter(item -> item.getId() != null)
                .collect(Collectors.toMap(
                        ItemQuoteVersion::getId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new));
        Map<Integer, Integer> ticketedQtyByItemId = ticketedQuantities(items, tickets);
        Map<String, Integer> changedFieldCounts = new LinkedHashMap<>();
        int updatedItemCount = 0;

        for (ReleaseItemDataReplacementRowDTO row : preview.getRows()) {
            if (row.getStatus() != ReleaseItemDataReplacementRowStatus.CHANGED) {
                continue;
            }
            ItemQuoteVersion item = itemsById.get(row.getItemId());
            if (item == null) {
                throw new PreviewFingerprintConflictException(FINGERPRINT_CONFLICT_MESSAGE);
            }
            for (ReleaseItemDataReplacementChangeDTO change : row.getChanges()) {
                applyChange(item, change);
                changedFieldCounts.merge(change.getField(), 1, Integer::sum);
            }
            if (itemMapper.updateById(item) != 1) {
                throw new IllegalStateException("议价提单表格数据替换保存失败");
            }
            updatedItemCount++;
        }

        ReleaseItemDataReplacementConfirmDTO result = new ReleaseItemDataReplacementConfirmDTO();
        result.setSuccess(true);
        ReleaseItemDataReplacementSummaryDTO summary = preview.getSummary();
        summary.setUpdatedItemCount(updatedItemCount);
        List<ReleaseItemDataReplacementAcceptedQtyDTO> acceptedQtyUpdates = new ArrayList<>();
        List<Map<String, Object>> affectedRows = new ArrayList<>();
        for (ReleaseItemDataReplacementRowDTO row : preview.getRows()) {
            if (row.getStatus() != ReleaseItemDataReplacementRowStatus.CHANGED
                    && row.getStatus() != ReleaseItemDataReplacementRowStatus.QUANTITY_ONLY) {
                continue;
            }
            ItemQuoteVersion item = itemsById.get(row.getItemId());
            if (item == null) {
                throw new PreviewFingerprintConflictException(FINGERPRINT_CONFLICT_MESSAGE);
            }
            affectedRows.add(releaseItemRowService.buildReplacementAffectedRow(
                    item,
                    ticketedQtyByItemId.getOrDefault(item.getId(), 0),
                    ordersById.get(item.getRfqId())));
            if (row.getAcceptedQty() != null) {
                acceptedQtyUpdates.add(acceptedQtyUpdate(item, row.getAcceptedQty()));
            }
        }
        summary.setQuantityAppliedCount(acceptedQtyUpdates.size());
        result.setSummary(summary);
        result.setAffectedRows(affectedRows);
        result.setAcceptedQtyUpdates(acceptedQtyUpdates);

        if (updatedItemCount > 0) {
            auditService.record(
                    "RELEASE_ITEM_DATA_REPLACEMENT",
                    "item_quote_version",
                    auditTargetId(rfqIds),
                    null,
                    auditSummary(rfqIds, summary, changedFieldCounts));
        }
        return result;
    }

    private void applyChange(
            ItemQuoteVersion item,
            ReleaseItemDataReplacementChangeDTO change) {
        Object value = change.getAfterValue();
        switch (change.getField()) {
            case "internalTargetLeadTime" -> item.setInternalTargetLeadTime(stringValue(value));
            case "materialName" -> item.setMaterialName(stringValue(value));
            case "surfaceTreatment" -> item.setSurfaceTreatment(stringValue(value));
            case "remark" -> item.setRemark(stringValue(value));
            case "salesOrderDate" -> item.setSalesOrderDate((LocalDate) value);
            case "customerOrderNo" -> item.setCustomerOrderNo(stringValue(value));
            case "customerProjectNo" -> item.setCustomerProjectNo(stringValue(value));
            case "customerOrderTitle" -> item.setCustomerOrderTitle(stringValue(value));
            case "orderType" -> item.setOrderType(stringValue(value));
            case "internalProjectNo" -> item.setInternalProjectNo(stringValue(value));
            default -> throw new IllegalArgumentException("存在不支持的替换字段");
        }
    }

    private ReleaseItemDataReplacementAcceptedQtyDTO acceptedQtyUpdate(
            ItemQuoteVersion item,
            Integer acceptedQty) {
        ReleaseItemDataReplacementAcceptedQtyDTO update = new ReleaseItemDataReplacementAcceptedQtyDTO();
        update.setReleaseItemKey("RFQ:" + item.getId());
        update.setItemId(item.getId());
        update.setAcceptedQty(acceptedQty);
        return update;
    }

    private String auditTargetId(List<Integer> rfqIds) {
        String joined = rfqIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        if (joined.length() <= MAX_AUDIT_TARGET_ID_LENGTH) {
            return joined;
        }
        return "rfq-batch-" + sha256(joined).substring(0, 24);
    }

    private Map<String, Object> auditSummary(
            List<Integer> rfqIds,
            ReleaseItemDataReplacementSummaryDTO summary,
            Map<String, Integer> changedFieldCounts) {
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("rfqIds", rfqIds);
        audit.put("importedCount", summary.getImportedCount());
        audit.put("matchedCount", summary.getMatchedCount());
        audit.put("updatedItemCount", summary.getUpdatedItemCount());
        audit.put("quantityAppliedCount", summary.getQuantityAppliedCount());
        audit.put("unmatchedCount", summary.getUnmatchedCount());
        audit.put("skippedCount", summary.getSkippedCount());
        audit.put("changedFieldCounts", changedFieldCounts);
        return audit;
    }

    private boolean fingerprintsEqual(String expected, String actual) {
        if (expected == null || !expected.trim().matches("(?i)[0-9a-f]{64}")) {
            throw new IllegalArgumentException("预览指纹格式不正确");
        }
        byte[] expectedBytes = HexFormat.of().parseHex(expected.trim());
        byte[] actualBytes = HexFormat.of().parseHex(actual);
        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }

    private void requireConfirmationDependencies() {
        if (auditService == null || releaseItemRowService == null) {
            throw new IllegalStateException("表格数据替换确认服务未正确初始化");
        }
    }

    private List<ItemQuoteVersion> listItems(List<Integer> rfqIds) {
        QueryWrapper<ItemQuoteVersion> query = new QueryWrapper<>();
        query.in("rfq_id", rfqIds)
                .and(wrapper -> wrapper
                        .ne("status", QuoteStatusWorkflow.DISCARDED)
                        .or()
                        .isNull("status"))
                .orderByAsc("rfq_id")
                .orderByAsc("id");
        return safeList(itemMapper.selectList(query));
    }

    private List<OrderDeliveryTicket> listTickets(
            List<Integer> rfqIds,
            List<ItemQuoteVersion> items) {
        if (items.isEmpty()) {
            return List.of();
        }
        List<Integer> itemIds = items.stream()
                .map(ItemQuoteVersion::getId)
                .filter(Objects::nonNull)
                .toList();
        List<Long> sourceOrderIds = rfqIds.stream().map(Integer::longValue).toList();
        QueryWrapper<OrderDeliveryTicket> query = new QueryWrapper<>();
        query.and(wrapper -> {
            boolean hasClause = false;
            if (!itemIds.isEmpty()) {
                wrapper.in("item_id", itemIds);
                hasClause = true;
            }
            if (!rfqIds.isEmpty()) {
                if (hasClause) {
                    wrapper.or();
                }
                wrapper.in("rfq_id", rfqIds);
                hasClause = true;
            }
            if (!sourceOrderIds.isEmpty()) {
                if (hasClause) {
                    wrapper.or();
                }
                wrapper.eq("source_type", ReleaseSourceType.RFQ.name())
                        .in("source_order_id", sourceOrderIds);
            }
        });
        return safeList(ticketMapper.selectList(query));
    }

    private Map<ItemKey, List<ItemQuoteVersion>> groupCandidates(List<ItemQuoteVersion> items) {
        Map<ItemKey, List<ItemQuoteVersion>> candidates = new LinkedHashMap<>();
        for (ItemQuoteVersion item : items) {
            if (item == null) {
                continue;
            }
            ItemKey key = itemKey(item.getPartNo(), item.getVersion());
            candidates.computeIfAbsent(key, ignored -> new ArrayList<>()).add(item);
        }
        candidates.values().forEach(group -> group.sort(Comparator.comparing(
                ItemQuoteVersion::getId,
                Comparator.nullsLast(Integer::compareTo))));
        return candidates;
    }

    private Map<Integer, Integer> ticketedQuantities(
            List<ItemQuoteVersion> items,
            List<OrderDeliveryTicket> tickets) {
        Map<Integer, ItemQuoteVersion> itemsById = items.stream()
                .filter(Objects::nonNull)
                .filter(item -> item.getId() != null)
                .collect(Collectors.toMap(
                        ItemQuoteVersion::getId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new));
        Map<RfqItemKey, List<ItemQuoteVersion>> itemsByRfqKey = new LinkedHashMap<>();
        for (ItemQuoteVersion item : items) {
            if (item == null || item.getRfqId() == null) {
                continue;
            }
            itemsByRfqKey.computeIfAbsent(
                    new RfqItemKey(item.getRfqId(), itemKey(item.getPartNo(), item.getVersion())),
                    ignored -> new ArrayList<>()).add(item);
        }

        Map<Integer, Integer> quantities = new LinkedHashMap<>();
        itemsById.keySet().forEach(itemId -> quantities.put(itemId, 0));
        for (OrderDeliveryTicket ticket : tickets) {
            ItemQuoteVersion matched = exactTicketItem(ticket, itemsById);
            if (matched == null) {
                matched = historicalTicketItem(ticket, itemsByRfqKey);
            }
            if (matched != null && matched.getId() != null) {
                quantities.merge(matched.getId(), safeInt(ticket.getQty()), Integer::sum);
            }
        }
        return quantities;
    }

    private ItemQuoteVersion exactTicketItem(
            OrderDeliveryTicket ticket,
            Map<Integer, ItemQuoteVersion> itemsById) {
        if (ticket == null) {
            return null;
        }
        ItemQuoteVersion byItemId = ticket.getItemId() == null ? null : itemsById.get(ticket.getItemId());
        if (byItemId != null) {
            return byItemId;
        }
        if (!ReleaseSourceType.RFQ.name().equalsIgnoreCase(ticket.getSourceType())
                || ticket.getSourceItemId() == null
                || ticket.getSourceItemId() > Integer.MAX_VALUE) {
            return null;
        }
        ItemQuoteVersion bySourceItemId = itemsById.get(ticket.getSourceItemId().intValue());
        if (bySourceItemId == null || ticket.getSourceOrderId() == null) {
            return null;
        }
        return Objects.equals(ticket.getSourceOrderId(), bySourceItemId.getRfqId().longValue())
                ? bySourceItemId
                : null;
    }

    private ItemQuoteVersion historicalTicketItem(
            OrderDeliveryTicket ticket,
            Map<RfqItemKey, List<ItemQuoteVersion>> itemsByRfqKey) {
        if (ticket == null || ticket.getItemId() != null) {
            return null;
        }
        Integer rfqId = ticket.getRfqId();
        if (rfqId == null
                && ReleaseSourceType.RFQ.name().equalsIgnoreCase(ticket.getSourceType())
                && ticket.getSourceOrderId() != null
                && ticket.getSourceOrderId() <= Integer.MAX_VALUE) {
            rfqId = ticket.getSourceOrderId().intValue();
        }
        if (rfqId == null) {
            return null;
        }
        List<ItemQuoteVersion> candidates = itemsByRfqKey.getOrDefault(
                new RfqItemKey(rfqId, itemKey(ticket.getPartNo(), ticket.getVersion())),
                List.of());
        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    private List<ReleaseItemDataReplacementRowDTO> previewRows(
            ReleaseItemDataReplacementExcelRowDTO excelRow,
            Map<ItemKey, List<ItemQuoteVersion>> candidatesByKey,
            Map<Integer, OrderRfq> ordersById,
            Map<Integer, Integer> ticketedQtyByItemId) {
        ReleaseItemDataReplacementRowDTO row = baseRow(excelRow);
        Map<String, Object> values = safeMap(excelRow.getValues());
        row.setAcceptedQty(integerValue(values.get(ReleaseItemDataReplacementField.ACCEPTED_QTY.getPropertyName())));

        if (excelRow.hasErrors()) {
            row.setStatus(ReleaseItemDataReplacementRowStatus.ERROR);
            row.setReason(excelRow.getErrors().stream()
                    .map(ReleaseItemDataReplacementExcelErrorDTO::getMessage)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("；")));
            return List.of(row);
        }

        List<ItemQuoteVersion> candidates = candidatesByKey.getOrDefault(
                itemKey(excelRow.getNormalizedPartNo(), excelRow.getNormalizedVersion()),
                List.of());
        if (candidates.isEmpty()) {
            row.setStatus(ReleaseItemDataReplacementRowStatus.UNMATCHED);
            row.setReason("当前所选询价单范围内未找到对应零件");
            return List.of(row);
        }

        List<ReleaseItemDataReplacementRowDTO> rows = new ArrayList<>();
        for (ItemQuoteVersion item : candidates) {
            rows.add(previewMatchedRow(excelRow, item, ordersById, ticketedQtyByItemId));
        }
        return rows;
    }

    private ReleaseItemDataReplacementRowDTO previewMatchedRow(
            ReleaseItemDataReplacementExcelRowDTO excelRow,
            ItemQuoteVersion item,
            Map<Integer, OrderRfq> ordersById,
            Map<Integer, Integer> ticketedQtyByItemId) {
        ReleaseItemDataReplacementRowDTO row = baseRow(excelRow);
        Map<String, Object> values = safeMap(excelRow.getValues());
        row.setAcceptedQty(integerValue(values.get(ReleaseItemDataReplacementField.ACCEPTED_QTY.getPropertyName())));
        OrderRfq order = ordersById.get(item.getRfqId());
        row.setRfqId(item.getRfqId());
        row.setRfqNo(order == null ? null : order.getRfqNo());
        row.setItemId(item.getId());
        int ticketedQty = ticketedQtyByItemId.getOrDefault(item.getId(), 0);
        ReleaseItemMutationPolicy.Result mutation = mutationPolicy.evaluate(item, ticketedQty);
        if (!mutation.mutable()) {
            row.setStatus(ReleaseItemDataReplacementRowStatus.SKIPPED);
            row.setReason(mutation.reason());
            return row;
        }
        row.setChanges(changes(item, values));
        if (!row.getChanges().isEmpty()) {
            row.setStatus(ReleaseItemDataReplacementRowStatus.CHANGED);
        } else if (row.getAcceptedQty() != null) {
            row.setStatus(ReleaseItemDataReplacementRowStatus.QUANTITY_ONLY);
        } else {
            row.setStatus(ReleaseItemDataReplacementRowStatus.UNCHANGED);
        }
        row.setReason("");
        return row;
    }

    private List<ReleaseItemDataReplacementChangeDTO> changes(
            ItemQuoteVersion item,
            Map<String, Object> values) {
        List<ReleaseItemDataReplacementChangeDTO> changes = new ArrayList<>();
        for (ReleaseItemDataReplacementField field : ReleaseItemDataReplacementField.persistentFields()) {
            String propertyName = field.getPropertyName();
            if (!values.containsKey(propertyName)) {
                continue;
            }
            Object beforeValue = currentValue(item, field);
            Object afterValue = importedValue(field, values.get(propertyName));
            if (Objects.equals(beforeValue, afterValue)) {
                continue;
            }
            ReleaseItemDataReplacementChangeDTO change = new ReleaseItemDataReplacementChangeDTO();
            change.setField(propertyName);
            change.setLabel(field.getHeader());
            change.setBeforeValue(beforeValue);
            change.setAfterValue(afterValue);
            changes.add(change);
        }
        return changes;
    }

    private Object currentValue(ItemQuoteVersion item, ReleaseItemDataReplacementField field) {
        return switch (field) {
            case INTERNAL_TARGET_LEAD_TIME -> item.getInternalTargetLeadTime();
            case MATERIAL_NAME -> item.getMaterialName();
            case SURFACE_TREATMENT -> item.getSurfaceTreatment();
            case REMARK -> item.getRemark();
            case SALES_ORDER_DATE -> item.getSalesOrderDate();
            case CUSTOMER_ORDER_NO -> item.getCustomerOrderNo();
            case CUSTOMER_PROJECT_NO -> item.getCustomerProjectNo();
            case CUSTOMER_ORDER_TITLE -> item.getCustomerOrderTitle();
            case ORDER_TYPE -> item.getOrderType();
            case INTERNAL_PROJECT_NO -> item.getInternalProjectNo();
            default -> null;
        };
    }

    private Object importedValue(ReleaseItemDataReplacementField field, Object value) {
        if (field == ReleaseItemDataReplacementField.INTERNAL_TARGET_LEAD_TIME && value instanceof LocalDate date) {
            return date.toString();
        }
        return value;
    }

    private ReleaseItemDataReplacementRowDTO baseRow(ReleaseItemDataReplacementExcelRowDTO excelRow) {
        ReleaseItemDataReplacementRowDTO row = new ReleaseItemDataReplacementRowDTO();
        row.setExcelRowNumber(excelRow.getExcelRowNumber());
        row.setPartNo(excelRow.getPartNo());
        row.setVersion(excelRow.getVersion());
        return row;
    }

    private ReleaseItemDataReplacementSummaryDTO summarize(
            List<ReleaseItemDataReplacementRowDTO> rows,
            int importedCount) {
        ReleaseItemDataReplacementSummaryDTO summary = new ReleaseItemDataReplacementSummaryDTO();
        summary.setImportedCount(importedCount);
        for (ReleaseItemDataReplacementRowDTO row : rows) {
            if (row.getItemId() != null) {
                summary.setMatchedCount(summary.getMatchedCount() + 1);
            }
            switch (row.getStatus()) {
                case CHANGED -> summary.setChangedCount(summary.getChangedCount() + 1);
                case QUANTITY_ONLY -> summary.setQuantityOnlyCount(summary.getQuantityOnlyCount() + 1);
                case UNCHANGED -> summary.setUnchangedCount(summary.getUnchangedCount() + 1);
                case UNMATCHED -> summary.setUnmatchedCount(summary.getUnmatchedCount() + 1);
                case SKIPPED -> summary.setSkippedCount(summary.getSkippedCount() + 1);
                case ERROR -> summary.setErrorCount(summary.getErrorCount() + 1);
            }
        }
        return summary;
    }

    private String fingerprint(
            List<Integer> rfqIds,
            Map<Integer, OrderRfq> ordersById,
            ReleaseItemDataReplacementExcelParseResult parsed,
            List<ReleaseItemDataReplacementRowDTO> previewRows,
            Map<ItemKey, List<ItemQuoteVersion>> candidatesByKey,
            Map<Integer, Integer> ticketedQtyByItemId) {
        StringBuilder canonical = new StringBuilder();
        AuthContext.SessionUser user = AuthContext.get();
        appendToken(canonical, "user", user == null || user.id() == null ? "anonymous" : user.id());
        for (Integer rfqId : rfqIds) {
            OrderRfq order = ordersById.get(rfqId);
            appendToken(canonical, "rfqId", rfqId);
            appendToken(canonical, "rfqStatus", order == null ? null : order.getStatus());
            appendToken(canonical, "rfqCustomer", order == null ? null : trimToNull(order.getCustomerName()));
        }

        List<String> recognizedFields = new ArrayList<>(safeList(parsed.getRecognizedFields()));
        recognizedFields.sort(String::compareTo);
        recognizedFields.forEach(field -> appendToken(canonical, "field", field));
        Map<Integer, ReleaseItemDataReplacementRowDTO> previewByExcelRow = previewRows.stream()
                .filter(row -> row.getExcelRowNumber() != null)
                .collect(Collectors.toMap(
                        ReleaseItemDataReplacementRowDTO::getExcelRowNumber,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new));

        List<ReleaseItemDataReplacementExcelRowDTO> excelRows = new ArrayList<>(safeList(parsed.getRows()));
        excelRows.sort(Comparator
                .comparing((ReleaseItemDataReplacementExcelRowDTO row) -> safeText(row.getNormalizedPartNo()))
                .thenComparing(row -> safeText(row.getNormalizedVersion()))
                .thenComparing(row -> canonicalValues(safeMap(row.getValues())))
                .thenComparing(row -> row.getExcelRowNumber() == null ? Integer.MAX_VALUE : row.getExcelRowNumber()));
        for (ReleaseItemDataReplacementExcelRowDTO excelRow : excelRows) {
            ItemKey key = itemKey(excelRow.getNormalizedPartNo(), excelRow.getNormalizedVersion());
            appendToken(canonical, "partNo", key.partNo());
            appendToken(canonical, "version", key.version());
            safeMap(excelRow.getValues()).entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> appendToken(
                            canonical,
                            "value:" + entry.getKey(),
                            canonicalValue(entry.getValue())));
            safeList(excelRow.getErrors()).stream()
                    .map(ReleaseItemDataReplacementExcelErrorDTO::getMessage)
                    .filter(Objects::nonNull)
                    .sorted()
                    .forEach(error -> appendToken(canonical, "parseError", error));

            ReleaseItemDataReplacementRowDTO previewRow = previewByExcelRow.get(excelRow.getExcelRowNumber());
            appendToken(canonical, "result", previewRow == null ? null : previewRow.getStatus());
            appendToken(canonical, "matchedItemId", previewRow == null ? null : previewRow.getItemId());
            for (ItemQuoteVersion candidate : candidatesByKey.getOrDefault(key, List.of())) {
                appendCandidateState(
                        canonical,
                        candidate,
                        recognizedFields,
                        ticketedQtyByItemId.getOrDefault(candidate.getId(), 0));
            }
        }
        return sha256(canonical.toString());
    }

    private void appendCandidateState(
            StringBuilder canonical,
            ItemQuoteVersion item,
            List<String> recognizedFields,
            int ticketedQty) {
        appendToken(canonical, "candidateId", item.getId());
        appendToken(canonical, "candidateRfqId", item.getRfqId());
        appendToken(canonical, "candidateStatus", item.getStatus());
        appendToken(canonical, "candidateQty", item.getQty());
        appendToken(canonical, "ticketedQty", ticketedQty);
        appendToken(canonical, "remainingQty", Math.max(0, safeInt(item.getQty()) - ticketedQty));
        for (String propertyName : recognizedFields) {
            ReleaseItemDataReplacementField.replaceableFields().stream()
                    .filter(field -> field.isPersistent() && field.getPropertyName().equals(propertyName))
                    .findFirst()
                    .ifPresent(field -> appendToken(
                            canonical,
                            "current:" + propertyName,
                            canonicalValue(currentValue(item, field))));
        }
    }

    private String canonicalValues(Map<String, Object> values) {
        return values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + canonicalValue(entry.getValue()))
                .collect(Collectors.joining("|"));
    }

    private String canonicalValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private void appendToken(StringBuilder canonical, String key, Object value) {
        String text = canonicalValue(value);
        canonical.append(key.length()).append(':').append(key)
                .append(text.length()).append(':').append(text).append('\n');
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private ItemKey itemKey(String partNo, String version) {
        return new ItemKey(
                DrawingPartNoResolver.normalizePartNo(partNo),
                DrawingPartNoResolver.normalizeVersion(version));
    }

    private Integer integerValue(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private <T> List<T> safeList(Collection<T> values) {
        return values == null ? List.of() : new ArrayList<>(values);
    }

    private Map<String, Object> safeMap(Map<String, Object> values) {
        return values == null ? Map.of() : values;
    }

    private record ItemKey(String partNo, String version) {
    }

    private record RfqItemKey(Integer rfqId, ItemKey itemKey) {
    }
}
