package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.InternalTargetLeadTimeSetReq;
import com.machining.quote.dto.InternalTargetLeadTimeShiftReq;
import com.machining.quote.dto.InternalTargetLeadTimeUpdateReq;
import com.machining.quote.dto.OrderItemPageDTO;
import com.machining.quote.dto.OrderItemPageReq;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.ReleaseItemBatchQueryReq;
import com.machining.quote.dto.ReleaseItemFieldUpdateReq;
import com.machining.quote.dto.ReleaseItemOrderFieldUpdateReq;
import com.machining.quote.dto.ReleaseItemVersionUpdateReq;
import com.machining.quote.dto.ReleaseOrderSource;
import com.machining.quote.dto.ReleaseSourceType;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.util.DrawingPartNoResolver;
import com.machining.quote.util.MaterialDataResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class InternalTargetLeadTimeService {
    private static final Logger log = LoggerFactory.getLogger(InternalTargetLeadTimeService.class);
    private static final Set<String> RELEASE_ORDER_TYPES = Set.of("预测订单", "备货订单", "正式订单");
    private static final int CUSTOMER_ORDER_NO_MAX_LENGTH = 120;
    private static final int CUSTOMER_PROJECT_NO_MAX_LENGTH = 120;
    private static final int CUSTOMER_ORDER_TITLE_MAX_LENGTH = 255;
    private static final int ORDER_TYPE_MAX_LENGTH = 20;
    private static final int INTERNAL_PROJECT_NO_MAX_LENGTH = 120;
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 100;

    @Autowired
    private OrderRfqMapper orderRfqMapper;
    @Autowired
    private ItemQuoteVersionMapper itemMapper;
    @Autowired
    private OrderDeliveryTicketMapper ticketMapper;
    @Autowired
    private SalesOrderHeaderMapper salesOrderHeaderMapper;
    @Autowired
    private SalesOrderItemMapper salesOrderItemMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private LeadTimeService leadTimeService;
    @Autowired
    private AuditService auditService;
    @Autowired
    private BusinessFileService businessFileService;
    @Autowired
    private OrderItemPageService orderItemPageService;
    @Autowired(required = false)
    private DataScopeService dataScopeService;

    @Transactional
    public Map<String, Object> shiftLeadTimes(InternalTargetLeadTimeShiftReq req) {
        requireOrderForUpdate(req.getRfqId());
        if (req.getItemIds() == null || req.getItemIds().isEmpty()) {
            throw new RuntimeException("Please select at least one item.");
        }
        int days = req.getDays() == null ? 0 : req.getDays();
        if (days == 0) {
            throw new RuntimeException("閻犲鍟弳锝嗗緞閳哄倹娈跺☉鎾崇Х閸忔ɑ绋?0");
        }

        List<Map<String, Object>> skipped = new ArrayList<>();
        int adjustedCount = 0;
        for (Integer itemId : req.getItemIds()) {
            ItemQuoteVersion item = itemMapper.selectByIdForUpdate(itemId);
            if (item == null || !req.getRfqId().equals(item.getRfqId())) {
                skipped.add(skipRow(itemId, "", "", "闂傚棙婀瑰▎銏＄▔瀹ュ懐鎽犻柛锔哄妽閸ㄣ劍绋夊鍛剑濞存粌楠哥紞瀣礈?RFQ"));
                continue;
            }

            String baseLeadTime = leadTimeService.effectiveDeliveryLeadTime(item);
            try {
                Object before = buildItemRow(item);
                item.setInternalTargetLeadTime(leadTimeService.shiftLeadTime(baseLeadTime, days));
                itemMapper.updateById(item);
                auditService.record("INTERNAL_TARGET_LEAD_TIME_SHIFT", "item_quote_version", item.getId(), before, buildItemRow(itemMapper.selectById(item.getId())));
                adjustedCount++;
            } catch (RuntimeException e) {
                skipped.add(skipRow(item.getId(), item.getPartNo(), item.getVersion(), e.getMessage()));
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("adjustedCount", adjustedCount);
        result.put("skippedCount", skipped.size());
        result.put("skippedItems", skipped);
        log.info("Internal target lead time shifted rfqId={} itemCount={} adjustedCount={} skippedCount={} days={}",
                req.getRfqId(), req.getItemIds().size(), adjustedCount, skipped.size(), days);
        if (!skipped.isEmpty()) {
            log.warn("Internal target lead time shifted with warnings rfqId={} skippedCount={}",
                    req.getRfqId(), skipped.size());
        }
        return result;
    }

    @Transactional
    public Map<String, Object> setLeadTimes(InternalTargetLeadTimeSetReq req) {
        requireOrderForUpdate(req.getRfqId());
        if (req.getItemIds() == null || req.getItemIds().isEmpty()) {
            throw new RuntimeException("Please select at least one item.");
        }
        String normalizedDate = leadTimeService.normalizeRequiredDate(req.getInternalTargetLeadTime());

        List<Map<String, Object>> skipped = new ArrayList<>();
        int adjustedCount = 0;
        for (Integer itemId : req.getItemIds()) {
            ItemQuoteVersion item = itemMapper.selectByIdForUpdate(itemId);
            if (item == null || !req.getRfqId().equals(item.getRfqId())) {
                skipped.add(skipRow(itemId, "", "", "闂傚棙婀瑰▎銏＄▔瀹ュ懐鎽犻柛锔哄妽閸ㄣ劍绋夊鍛剑濞存粌楠哥紞瀣礈?RFQ"));
                continue;
            }

            Object before = buildItemRow(item);
            item.setInternalTargetLeadTime(normalizedDate);
            itemMapper.updateById(item);
            auditService.record("INTERNAL_TARGET_LEAD_TIME_SET", "item_quote_version", item.getId(), before, buildItemRow(itemMapper.selectById(item.getId())));
            adjustedCount++;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("adjustedCount", adjustedCount);
        result.put("skippedCount", skipped.size());
        result.put("skippedItems", skipped);
        log.info("Internal target lead time set rfqId={} itemCount={} adjustedCount={} skippedCount={} targetLeadTime={}",
                req.getRfqId(), req.getItemIds().size(), adjustedCount, skipped.size(), normalizedDate);
        if (!skipped.isEmpty()) {
            log.warn("Internal target lead time set with warnings rfqId={} skippedCount={}",
                    req.getRfqId(), skipped.size());
        }
        return result;
    }

    @Transactional
    public Map<String, Object> updateLeadTime(InternalTargetLeadTimeUpdateReq req) {
        requireOrderForUpdate(req.getRfqId());
        if (req.getItemId() == null) {
            throw new RuntimeException("Item ID is required");
        }
        ItemQuoteVersion item = itemMapper.selectByIdForUpdate(req.getItemId());
        if (item == null || !req.getRfqId().equals(item.getRfqId())) {
            throw new RuntimeException("闂傚棙婀瑰▎銏＄▔瀹ュ懐鎽犻柛锔哄妽閸ㄣ劍绋夊鍛剑濞存粌楠哥紞瀣礈?RFQ");
        }

        Object before = buildItemRow(item);
        String normalizedLeadTime = leadTimeService.normalizeForQuote(req.getInternalTargetLeadTime());
        UpdateWrapper<ItemQuoteVersion> update = new UpdateWrapper<>();
        update.eq("id", item.getId()).set("internal_target_lead_time", normalizedLeadTime);
        itemMapper.update(null, update);
        ItemQuoteVersion saved = itemMapper.selectById(item.getId());
        Map<String, Object> savedRow = buildItemRow(saved);
        auditService.record("INTERNAL_TARGET_LEAD_TIME_UPDATE", "item_quote_version", item.getId(), before, savedRow);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("data", savedRow);
        return result;
    }

    @Transactional
    public Map<String, Object> updateReleaseItemVersion(Integer itemId, ReleaseItemVersionUpdateReq req) {
        Integer rfqId = req == null ? null : req.getRfqId();
        requireOrderForUpdate(rfqId);
        if (itemId == null) {
            throw new RuntimeException("Item ID is required");
        }
        ItemQuoteVersion item = itemMapper.selectByIdForUpdate(itemId);
        if (item == null || !Objects.equals(rfqId, item.getRfqId())) {
            throw new RuntimeException("闂傚棙婀瑰▎銏＄▔瀹ュ懐鎽犻柛锔哄妽閸ㄣ劍绋夊鍛剑濞存粌楠哥紞瀣礈?RFQ");
        }
        assertReleaseItemMutable(item, listTicketsForItems(rfqId, List.of(item)));

        Map<String, Object> before = buildItemRow(item);
        item.setVersion(normalizeVersion(req.getVersion()));
        itemMapper.updateById(item);
        ItemQuoteVersion saved = itemMapper.selectById(item.getId());
        Map<String, Object> savedRow = buildItemRow(saved == null ? item : saved);
        auditService.record("RELEASE_ITEM_VERSION_UPDATE", "item_quote_version", item.getId(), before, savedRow);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("data", savedRow);
        log.info("Release item version updated rfqId={} itemId={} partNo={}",
                rfqId, item.getId(), normalizePartNo(item.getPartNo()));
        return result;
    }

    @Transactional
    public Map<String, Object> updateReleaseItemFields(Long itemId, ReleaseItemFieldUpdateReq req) {
        if (itemId == null) {
            throw new RuntimeException("Item ID is required");
        }
        ReleaseSourceType sourceType = req == null || req.getSourceType() == null
                ? ReleaseSourceType.RFQ
                : req.getSourceType();
        if (sourceType == ReleaseSourceType.SALES_ORDER) {
            return updateSalesOrderReleaseItemFields(itemId, req);
        }
        return updateRfqReleaseItemFields(itemId.intValue(), req);
    }

    @Transactional
    public Map<String, Object> updateReleaseItemOrderFields(
            Integer itemId,
            ReleaseItemOrderFieldUpdateReq req) {
        if (itemId == null) {
            throw new RuntimeException("Item ID is required");
        }
        if (req == null) {
            throw new RuntimeException("Request body is required");
        }

        requireOrderForUpdate(req.getRfqId());
        ItemQuoteVersion item = itemMapper.selectByIdForUpdate(itemId);
        if (item == null || !Objects.equals(req.getRfqId(), item.getRfqId())) {
            throw new RuntimeException("Item does not belong to selected RFQ");
        }
        assertReleaseItemMutable(item, listTicketsForItems(req.getRfqId(), List.of(item)));

        LocalDate salesOrderDate = req.getSalesOrderDate();
        String customerOrderNo = normalizeReleaseOrderText(
                req.getCustomerOrderNo(), "客户订单号", CUSTOMER_ORDER_NO_MAX_LENGTH);
        String customerProjectNo = normalizeReleaseOrderText(
                req.getCustomerProjectNo(), "客户项目号", CUSTOMER_PROJECT_NO_MAX_LENGTH);
        String customerOrderTitle = normalizeReleaseOrderText(
                req.getCustomerOrderTitle(), "客户下单抬头", CUSTOMER_ORDER_TITLE_MAX_LENGTH);
        String orderType = normalizeReleaseOrderType(req.getOrderType());
        String internalProjectNo = normalizeReleaseOrderText(
                req.getInternalProjectNo(), "内部项目号", INTERNAL_PROJECT_NO_MAX_LENGTH);
        List<String> changedFields = changedReleaseOrderFields(
                item,
                salesOrderDate,
                customerOrderNo,
                customerProjectNo,
                customerOrderTitle,
                orderType,
                internalProjectNo);

        Map<String, Object> before = buildItemRow(item);
        item.setSalesOrderDate(salesOrderDate);
        item.setCustomerOrderNo(customerOrderNo);
        item.setCustomerProjectNo(customerProjectNo);
        item.setCustomerOrderTitle(customerOrderTitle);
        item.setOrderType(orderType);
        item.setInternalProjectNo(internalProjectNo);
        itemMapper.updateById(item);

        ItemQuoteVersion saved = itemMapper.selectById(item.getId());
        Map<String, Object> savedRow = buildItemRow(saved == null ? item : saved);
        auditService.record(
                "RELEASE_ITEM_ORDER_FIELDS_UPDATE",
                "item_quote_version",
                item.getId(),
                before,
                savedRow);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("data", savedRow);
        result.put("changedFields", changedFields);
        log.info("Release item order fields updated rfqId={} itemId={} partNo={} changedFields={}",
                req.getRfqId(), item.getId(), normalizePartNo(item.getPartNo()), changedFields);
        return result;
    }

    private List<String> changedReleaseOrderFields(
            ItemQuoteVersion item,
            LocalDate salesOrderDate,
            String customerOrderNo,
            String customerProjectNo,
            String customerOrderTitle,
            String orderType,
            String internalProjectNo) {
        List<String> changedFields = new ArrayList<>();
        if (!Objects.equals(item.getSalesOrderDate(), salesOrderDate)) {
            changedFields.add("salesOrderDate");
        }
        if (!Objects.equals(item.getCustomerOrderNo(), customerOrderNo)) {
            changedFields.add("customerOrderNo");
        }
        if (!Objects.equals(item.getCustomerProjectNo(), customerProjectNo)) {
            changedFields.add("customerProjectNo");
        }
        if (!Objects.equals(item.getCustomerOrderTitle(), customerOrderTitle)) {
            changedFields.add("customerOrderTitle");
        }
        if (!Objects.equals(item.getOrderType(), orderType)) {
            changedFields.add("orderType");
        }
        if (!Objects.equals(item.getInternalProjectNo(), internalProjectNo)) {
            changedFields.add("internalProjectNo");
        }
        return changedFields;
    }

    private String normalizeReleaseOrderType(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        if (normalized.length() > ORDER_TYPE_MAX_LENGTH || !RELEASE_ORDER_TYPES.contains(normalized)) {
            throw new RuntimeException("订单类型只能是预测订单、备货订单或正式订单");
        }
        return normalized;
    }

    private String normalizeReleaseOrderText(String value, String fieldName, int maxLength) {
        String normalized = trimToNull(value);
        if (normalized != null && normalized.length() > maxLength) {
            throw new RuntimeException(fieldName + "长度不能超过" + maxLength + "个字符");
        }
        return normalized;
    }

    private Map<String, Object> updateRfqReleaseItemFields(Integer itemId, ReleaseItemFieldUpdateReq req) {
        Integer rfqId = req == null ? null : req.getRfqId();
        requireOrderForUpdate(rfqId);
        ItemQuoteVersion item = itemMapper.selectByIdForUpdate(itemId);
        if (item == null || !Objects.equals(rfqId, item.getRfqId())) {
            throw new RuntimeException("Item does not belong to selected RFQ");
        }
        assertReleaseItemMutable(item, listTicketsForItems(rfqId, List.of(item)));

        Map<String, Object> before = buildItemRow(item);
        item.setDescription(trimToNull(req == null ? null : req.getDescription()));
        item.setSurfaceTreatment(trimToNull(req == null ? null : req.getSurfaceTreatment()));
        item.setRemark(trimToNull(req == null ? null : req.getRemark()));
        itemMapper.updateById(item);
        ItemQuoteVersion saved = itemMapper.selectById(item.getId());
        Map<String, Object> savedRow = buildItemRow(saved == null ? item : saved);
        auditService.record("RELEASE_ITEM_FIELD_UPDATE", "item_quote_version", item.getId(), before, savedRow);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("data", savedRow);
        log.info("Release item fields updated sourceType={} rfqId={} itemId={} partNo={}",
                ReleaseSourceType.RFQ.name(), rfqId, item.getId(), normalizePartNo(item.getPartNo()));
        return result;
    }

    private Map<String, Object> updateSalesOrderReleaseItemFields(Long itemId, ReleaseItemFieldUpdateReq req) {
        Long sourceOrderId = req == null ? null : req.getSourceOrderId();
        if (sourceOrderId == null) {
            throw new RuntimeException("Sales order ID is required");
        }
        Map<Long, SalesOrderHeader> orders = requireSalesOrdersForUpdate(Set.of(sourceOrderId));
        SalesOrderHeader order = orders.get(sourceOrderId);
        SalesOrderItem item = salesOrderItemMapper.selectByIdForUpdate(itemId);
        if (item == null || !Objects.equals(sourceOrderId, item.getSalesOrderId())) {
            throw new RuntimeException("Item does not belong to selected sales order");
        }
        if (!isSalesOrderReleaseItemMutable(item)) {
            throw new RuntimeException(salesOrderReleaseMutationBlockedReason(item));
        }

        Map<String, Object> before = buildSalesOrderItemRow(item, order);
        item.setDescription(trimToNull(req == null ? null : req.getDescription()));
        item.setSurfaceTreatment(trimToNull(req == null ? null : req.getSurfaceTreatment()));
        item.setRemark(trimToNull(req == null ? null : req.getRemark()));
        salesOrderItemMapper.updateById(item);
        SalesOrderItem saved = salesOrderItemMapper.selectById(item.getId());
        Map<String, Object> savedRow = buildSalesOrderItemRow(saved == null ? item : saved, order);
        auditService.record("RELEASE_ITEM_FIELD_UPDATE", "sales_order_item", item.getId(), before, savedRow);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("data", savedRow);
        log.info("Release item fields updated sourceType={} salesOrderId={} itemId={} partNo={}",
                ReleaseSourceType.SALES_ORDER.name(), sourceOrderId, item.getId(), normalizePartNo(item.getPartNo()));
        return result;
    }

    @Transactional
    public Map<String, Object> replaceReleaseDrawings(List<ReleaseOrderSource> sources, List<MultipartFile> drawings) {
        return replaceReleaseDrawings(sources, drawings, null);
    }

    @Transactional
    public Map<String, Object> replaceReleaseDrawings(
            List<ReleaseOrderSource> sources,
            List<MultipartFile> drawings,
            List<MultipartFile> versionDrawings) {
        List<ReleaseOrderSource> normalizedSources = normalizeReleaseSourcesFromInput(new ArrayList<>(sources == null ? List.of() : sources));
        if (normalizedSources.isEmpty()) {
            throw new RuntimeException("Please select at least one order.");
        }
        if (isEmptyFiles(drawings) && isEmptyFiles(versionDrawings)) {
            throw new RuntimeException("Please upload at least one PDF drawing.");
        }

        Map<Integer, OrderRfq> rfqOrderById = requireRfqOrdersForUpdate(sourceRfqIds(normalizedSources));
        Map<Long, SalesOrderHeader> salesOrderById = requireSalesOrdersForUpdate(sourceSalesOrderIds(normalizedSources));
        List<ItemQuoteVersion> rfqItems = listItemsBatch(rfqOrderById.keySet().stream().toList());
        List<OrderDeliveryTicket> rfqTickets = listTicketsForItems(rfqItems);
        List<SalesOrderItem> salesOrderItems = listSalesOrderItemsBatch(salesOrderById.keySet().stream().toList());
        Map<String, List<ReleaseDrawingTarget>> targetsByPartNo = new LinkedHashMap<>();
        Map<String, List<ReleaseDrawingTarget>> targetsByPartNoVersion = new LinkedHashMap<>();
        Set<DrawingPartNoResolver.DrawingIdentity> targetIdentities = new HashSet<>();
        for (ItemQuoteVersion item : rfqItems) {
            int ticketedQty = ticketedQty(item, rfqTickets);
            int remainingQty = Math.max(0, safeInt(item.getQty()) - ticketedQty);
            ReleaseDrawingTarget target = ReleaseDrawingTarget.rfq(
                    item,
                    rfqOrderById.get(item.getRfqId()),
                    isReleaseItemMutable(item, ticketedQty),
                    releaseMutationBlockedReason(item, remainingQty));
            String partNo = normalizePartNo(target.partNo());
            if (!partNo.isEmpty()) {
                targetsByPartNo.computeIfAbsent(partNo, ignored -> new ArrayList<>()).add(target);
            }
            addVersionDrawingTarget(target, targetsByPartNoVersion, targetIdentities);
        }
        for (SalesOrderItem item : salesOrderItems) {
            SalesOrderHeader order = salesOrderById.get(item.getSalesOrderId());
            ReleaseDrawingTarget target = ReleaseDrawingTarget.salesOrder(
                    item,
                    order,
                    isSalesOrderReleaseItemMutable(item),
                    salesOrderReleaseMutationBlockedReason(item));
            String partNo = normalizePartNo(target.partNo());
            if (!partNo.isEmpty()) {
                targetsByPartNo.computeIfAbsent(partNo, ignored -> new ArrayList<>()).add(target);
            }
            addVersionDrawingTarget(target, targetsByPartNoVersion, targetIdentities);
        }

        List<Map<String, Object>> replacedItems = new ArrayList<>();
        List<Map<String, Object>> unmatchedDrawings = new ArrayList<>();
        List<Map<String, Object>> skippedFiles = new ArrayList<>();
        List<Map<String, Object>> skippedItems = new ArrayList<>();
        int matchedCount = 0;
        Set<String> versionReplacedTargetKeys = new HashSet<>();
        int uploadedDrawingCount = fileCount(drawings);
        int versionUploadedDrawingCount = fileCount(versionDrawings);
        int uploadCount = uploadedDrawingCount + versionUploadedDrawingCount;

        try {
            if (versionDrawings != null) {
                for (MultipartFile drawing : versionDrawings) {
                    matchedCount += replaceReleaseDrawing(
                            drawing,
                            true,
                            targetsByPartNo,
                            targetsByPartNoVersion,
                            targetIdentities,
                            Set.of(),
                            versionReplacedTargetKeys,
                            replacedItems,
                            unmatchedDrawings,
                            skippedFiles,
                            skippedItems);
                }
            }
            if (drawings != null) {
                for (MultipartFile drawing : drawings) {
                    matchedCount += replaceReleaseDrawing(
                            drawing,
                            false,
                            targetsByPartNo,
                            targetsByPartNoVersion,
                            targetIdentities,
                            versionReplacedTargetKeys,
                            null,
                            replacedItems,
                            unmatchedDrawings,
                            skippedFiles,
                            skippedItems);
                }
            }
        } catch (ReleaseDrawingSaveException e) {
            log.error("Release drawings replace failed sources={} uploadCount={}", sourceLogValue(normalizedSources), uploadCount, e);
            throw new RuntimeException(e.getMessage(), e);
        } catch (RuntimeException e) {
            log.error("Release drawings replace failed sources={} uploadCount={}", sourceLogValue(normalizedSources), uploadCount, e);
            throw e;
        }

        List<Integer> rfqIds = new ArrayList<>(rfqOrderById.keySet());
        List<Long> salesOrderIds = new ArrayList<>(salesOrderById.keySet());
        Map<String, Object> auditSummary = new LinkedHashMap<>();
        auditSummary.put("sources", normalizedSources.stream().map(this::sourceAuditRow).toList());
        auditSummary.put("rfqIds", rfqIds);
        auditSummary.put("salesOrderIds", salesOrderIds);
        auditSummary.put("rfqNos", rfqOrderById.values().stream().map(OrderRfq::getRfqNo).toList());
        auditSummary.put("salesOrderNos", salesOrderById.values().stream().map(SalesOrderHeader::getSalesOrderNo).toList());
        auditSummary.put("uploadCount", uploadCount);
        auditSummary.put("uploadedDrawingCount", uploadedDrawingCount);
        auditSummary.put("versionUploadedDrawingCount", versionUploadedDrawingCount);
        auditSummary.put("drawingMatchModes", Map.of(
                "partNo", uploadedDrawingCount,
                "partNoVersion", versionUploadedDrawingCount
        ));
        auditSummary.put("matchedCount", matchedCount);
        auditSummary.put("replacedCount", replacedItems.size());
        auditSummary.put("unmatchedCount", unmatchedDrawings.size());
        auditSummary.put("skippedCount", skippedFiles.size() + skippedItems.size());
        auditSummary.put("replacedItems", replacedItems);
        auditSummary.put("unmatchedDrawings", unmatchedDrawings);
        auditSummary.put("skippedFiles", skippedFiles);
        auditSummary.put("skippedItems", skippedItems);
        String auditTargetId = normalizedSources.stream()
                .map(source -> source.getSourceType().name() + ":" + source.getSourceOrderId())
                .collect(java.util.stream.Collectors.joining(","));
        auditService.record(
                "RELEASE_DRAWING_REPLACE",
                "release_drawing_replace",
                auditTargetId,
                null,
                auditSummary,
                "MARKET",
                "SUCCESS",
                "RELEASE_DRAWING_REPLACE sources=" + sourceLogValue(normalizedSources) + " replacedCount=" + replacedItems.size(),
                null
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "Drawing replacement completed, replaced " + replacedItems.size() + " items.");
        result.put("uploadCount", uploadCount);
        result.put("uploadedDrawingCount", uploadedDrawingCount);
        result.put("versionUploadedDrawingCount", versionUploadedDrawingCount);
        result.put("drawingMatchModes", Map.of(
                "partNo", uploadedDrawingCount,
                "partNoVersion", versionUploadedDrawingCount
        ));
        result.put("matchedCount", matchedCount);
        result.put("replacedCount", replacedItems.size());
        result.put("unmatchedCount", unmatchedDrawings.size());
        result.put("skippedCount", skippedFiles.size() + skippedItems.size());
        result.put("replacedItems", replacedItems);
        result.put("unmatchedDrawings", unmatchedDrawings);
        result.put("skippedFiles", skippedFiles);
        result.put("skippedItems", skippedItems);
        log.info("Release drawings replace completed sources={} uploadCount={} matchedCount={} replacedCount={} unmatchedCount={} skippedCount={}",
                sourceLogValue(normalizedSources), uploadCount, matchedCount, replacedItems.size(), unmatchedDrawings.size(),
                skippedFiles.size() + skippedItems.size());
        if (!unmatchedDrawings.isEmpty() || !skippedFiles.isEmpty() || !skippedItems.isEmpty()) {
            log.warn("Release drawings replace completed with warnings sources={} unmatchedCount={} skippedCount={}",
                    sourceLogValue(normalizedSources), unmatchedDrawings.size(), skippedFiles.size() + skippedItems.size());
        }
        return result;
    }

    public List<Map<String, Object>> listItemRows(Integer rfqId) {
        requireOrder(rfqId);
        return buildItemRows(rfqId);
    }

    public Map<String, Object> releaseSummary(Integer rfqId) {
        OrderRfq order = requireOrder(rfqId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderInfo", order);
        result.put("summary", orderItemPageService.summarizeItems(rfqId));
        return result;
    }

    public PageResult<Map<String, Object>> pageItemRows(
            Integer rfqId,
            Integer page,
            Integer pageSize,
            String keyword,
            String status,
            Boolean remainingOnly) {
        requireOrder(rfqId);
        OrderItemPageReq req = new OrderItemPageReq();
        req.setRfqId(rfqId);
        req.setPage(page);
        req.setPageSize(pageSize);
        req.setKeyword(keyword);
        req.setStatus(status);
        OrderItemPageDTO itemPage = orderItemPageService.pageItems(req);
        List<ItemQuoteVersion> items = itemPage.getRecords();
        List<OrderDeliveryTicket> tickets = listTicketsForItems(rfqId, items);
        List<Map<String, Object>> rows = items.stream()
                .map(item -> buildItemRow(item, ticketedQty(item, tickets)))
                .filter(row -> !Boolean.TRUE.equals(remainingOnly) || safeInt(row.get("remainingQty")) > 0)
                .toList();
        return PageResult.of(rows, itemPage.getTotal(), itemPage.getPage(), itemPage.getPageSize());
    }

    public PageResult<Map<String, Object>> pageItemRowsBatch(ReleaseItemBatchQueryReq req) {
        List<ReleaseOrderSource> sources = normalizeReleaseSources(req);
        if (sources.stream().anyMatch(source -> source.getSourceType() == ReleaseSourceType.SALES_ORDER)) {
            return pageMixedSourceItemRows(req, sources);
        }

        List<OrderRfq> orders = requireOrders(sources.stream()
                .map(ReleaseOrderSource::getSourceOrderId)
                .filter(Objects::nonNull)
                .map(Long::intValue)
                .toList());
        if (orders.isEmpty()) {
            return PageResult.of(
                    List.of(),
                    0,
                    safePage(req == null ? null : req.getPage()),
                    safePageSize(req == null ? null : req.getPageSize()));
        }

        ReleaseItemBatchQueryReq queryReq = new ReleaseItemBatchQueryReq();
        queryReq.setRfqIds(orders.stream().map(OrderRfq::getId).toList());
        queryReq.setPage(req == null ? null : req.getPage());
        queryReq.setPageSize(req == null ? null : req.getPageSize());
        queryReq.setKeyword(req == null ? null : req.getKeyword());
        queryReq.setStatus(req == null ? null : req.getStatus());
        queryReq.setRemainingOnly(req == null ? null : req.getRemainingOnly());

        OrderItemPageDTO itemPage = orderItemPageService.pageItemsBatch(queryReq);
        List<ItemQuoteVersion> items = itemPage.getRecords();
        List<OrderDeliveryTicket> tickets = listTicketsForItems(items);
        Map<Integer, OrderRfq> orderById = new LinkedHashMap<>();
        orders.forEach(order -> orderById.put(order.getId(), order));
        List<Map<String, Object>> rows = items.stream()
                .map(item -> buildBatchItemRow(item, ticketedQty(item, tickets), orderById))
                .filter(row -> !Boolean.TRUE.equals(queryReq.getRemainingOnly()) || safeInt(row.get("remainingQty")) > 0)
                .toList();
        return PageResult.of(rows, itemPage.getTotal(), itemPage.getPage(), itemPage.getPageSize());
    }

    private PageResult<Map<String, Object>> pageMixedSourceItemRows(
            ReleaseItemBatchQueryReq req,
            List<ReleaseOrderSource> sources) {
        int page = safePage(req == null ? null : req.getPage());
        int pageSize = safePageSize(req == null ? null : req.getPageSize());
        long offset = (long) (page - 1) * pageSize;

        Map<Integer, OrderRfq> rfqOrders = accessibleRfqOrderMap(sourceRfqIds(sources));
        Map<Long, SalesOrderHeader> salesOrders = accessibleSalesOrderMap(sourceSalesOrderIds(sources));
        if (rfqOrders.isEmpty() && salesOrders.isEmpty()) {
            return PageResult.of(List.of(), 0, page, pageSize);
        }

        FilteredReleaseSql filteredSql = buildMixedReleaseItemSql(rfqOrders.keySet(), salesOrders.keySet(), req);
        if (filteredSql.sql().isBlank()) {
            return PageResult.of(List.of(), 0, page, pageSize);
        }

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM (" + filteredSql.sql() + ") release_items",
                Long.class,
                filteredSql.params().toArray());
        String pageSql = "SELECT * FROM (" + filteredSql.sql()
                + ") release_items ORDER BY source_type ASC, source_order_id ASC, source_item_id ASC "
                + "LIMIT " + pageSize + " OFFSET " + offset;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(pageSql, filteredSql.params().toArray()).stream()
                .map(this::normalizeMixedReleaseRow)
                .filter(row -> !Boolean.TRUE.equals(req == null ? null : req.getRemainingOnly())
                        || safeInt(row.get("remainingQty")) > 0)
                .toList();
        return PageResult.of(rows, total == null ? 0L : total, page, pageSize);
    }

    private FilteredReleaseSql buildMixedReleaseItemSql(
            Set<Integer> rfqIds,
            Set<Long> salesOrderIds,
            ReleaseItemBatchQueryReq req) {
        List<String> branches = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (rfqIds != null && !rfqIds.isEmpty()) {
            String ticketedQtySql = """
                    COALESCE((
                        SELECT SUM(ticket.qty)
                        FROM order_delivery_ticket ticket
                        WHERE ticket.rfq_id = item.rfq_id
                          AND (
                              ticket.item_id = item.id
                              OR (
                                  ticket.item_id IS NULL
                                  AND UPPER(BTRIM(COALESCE(ticket.part_no, ''))) = UPPER(BTRIM(COALESCE(item.part_no, '')))
                                  AND UPPER(BTRIM(COALESCE(ticket.version, ''))) = UPPER(BTRIM(COALESCE(item.version, '')))
                              )
                          )
                    ), 0)
                    """;
            StringBuilder sql = new StringBuilder("""
                    SELECT
                        'RFQ' AS source_type,
                        item.rfq_id AS source_order_id,
                        item.id AS source_item_id,
                        rfq.rfq_no AS source_order_no,
                        rfq.rfq_no AS rfq_no,
                        rfq.customer_name AS customer_name,
                        rfq.status AS order_status,
                        item.part_no,
                        item.version,
                        item.drawing_url,
                        item.description,
                        item.qty,
                        """);
            sql.append(ticketedQtySql).append(" AS ticketed_qty,\n")
                    .append("GREATEST(COALESCE(item.qty, 0) - ").append(ticketedQtySql).append(", 0) AS remaining_qty,\n")
                    .append("""
                        item.material_name,
                        item.engineering_material_name,
                        item.surface_treatment,
                        item.lead_time,
                        item.quote_lead_time,
                        item.internal_target_lead_time,
                        item.tax_included_unit_price,
                        item.tax_included_total_price,
                        item.status,
                        item.remark,
                        item.quote_mode,
                        item.assembly_fee
                    FROM item_quote_version item
                    JOIN order_rfq rfq ON rfq.id = item.rfq_id
                    WHERE item.rfq_id IN (
                    """)
                    .append(placeholders(params, rfqIds))
                    .append(")");
            appendMixedItemFilters(sql, params, req, "item");
            branches.add(sql.toString());
        }
        if (salesOrderIds != null && !salesOrderIds.isEmpty()) {
            StringBuilder sql = new StringBuilder("""
                    SELECT
                        'SALES_ORDER' AS source_type,
                        item.sales_order_id AS source_order_id,
                        item.id AS source_item_id,
                        header.sales_order_no AS source_order_no,
                        CAST(NULL AS VARCHAR) AS rfq_no,
                        header.customer_name AS customer_name,
                        header.status AS order_status,
                        item.part_no,
                        item.version,
                        item.drawing_url,
                        item.description,
                        item.qty,
                        COALESCE(item.released_qty, 0) AS ticketed_qty,
                        GREATEST(
                            COALESCE(NULLIF(item.remaining_qty, 0), item.qty - COALESCE(item.released_qty, 0), item.qty),
                            0
                        ) AS remaining_qty,
                        item.material_name,
                        item.engineering_material_name,
                        item.surface_treatment,
                        CAST(item.customer_delivery_date AS VARCHAR) AS lead_time,
                        CAST(item.internal_delivery_date AS VARCHAR) AS quote_lead_time,
                        CAST(NULL AS VARCHAR) AS internal_target_lead_time,
                        item.tax_included_unit_price,
                        item.tax_included_total_price,
                        item.status,
                        item.remark,
                        item.quote_mode,
                        item.assembly_fee
                    FROM sales_order_item item
                    JOIN sales_order_header header ON header.id = item.sales_order_id
                    WHERE item.sales_order_id IN (
                    """);
            sql.append(placeholders(params, salesOrderIds)).append(")");
            appendMixedItemFilters(sql, params, req, "item");
            branches.add(sql.toString());
        }
        return new FilteredReleaseSql(String.join("\nUNION ALL\n", branches), params);
    }

    private void appendMixedItemFilters(
            StringBuilder sql,
            List<Object> params,
            ReleaseItemBatchQueryReq req,
            String itemAlias) {
        if (req == null) {
            return;
        }
        if (!isBlank(req.getKeyword())) {
            String keyword = "%" + req.getKeyword().trim() + "%";
            sql.append(" AND (")
                    .append(itemAlias).append(".part_no LIKE ? OR ")
                    .append(itemAlias).append(".version LIKE ? OR ")
                    .append(itemAlias).append(".description LIKE ?)");
            params.add(keyword);
            params.add(keyword);
            params.add(keyword);
        }
        if (!isBlank(req.getStatus())) {
            sql.append(" AND ").append(itemAlias).append(".status = ?");
            params.add(req.getStatus().trim());
        }
    }

    private Map<String, Object> normalizeMixedReleaseRow(Map<String, Object> raw) {
        Map<String, Object> row = new LinkedHashMap<>();
        String sourceType = text(raw, "source_type");
        Long sourceOrderId = longValue(raw, "source_order_id");
        Long sourceItemId = longValue(raw, "source_item_id");
        int ticketedQty = number(raw, "ticketed_qty");
        int remainingQty = number(raw, "remaining_qty");
        Object taxIncludedUnitPrice = value(raw, "tax_included_unit_price");
        String status = text(raw, "status");

        row.put("id", sourceItemId);
        row.put("sourceType", sourceType);
        row.put("sourceOrderId", sourceOrderId);
        row.put("sourceItemId", sourceItemId);
        row.put("releaseItemKey", sourceType + ":" + sourceItemId);
        row.put("rfqId", ReleaseSourceType.RFQ.name().equals(sourceType) ? sourceOrderId : null);
        row.put("rfqNo", ReleaseSourceType.RFQ.name().equals(sourceType) ? text(raw, "rfq_no") : "");
        row.put("sourceOrderNo", text(raw, "source_order_no"));
        row.put("customerName", text(raw, "customer_name"));
        row.put("orderStatus", text(raw, "order_status"));
        row.put("partNo", text(raw, "part_no"));
        row.put("version", text(raw, "version"));
        String drawingUrl = text(raw, "drawing_url");
        row.put("drawingUrl", drawingUrl);
        row.put("hasDrawing", !isBlank(drawingUrl));
        row.put("description", value(raw, "description"));
        row.put("qty", number(raw, "qty"));
        row.put("ticketedQty", ticketedQty);
        row.put("remainingQty", remainingQty);
        row.put("materialName", value(raw, "material_name"));
        row.put("engineeringMaterialName", value(raw, "engineering_material_name"));
        row.put("surfaceTreatment", value(raw, "surface_treatment"));
        row.put("leadTime", value(raw, "lead_time"));
        row.put("quoteLeadTime", value(raw, "quote_lead_time"));
        row.put("internalTargetLeadTime", value(raw, "internal_target_lead_time"));
        row.put("effectiveDeliveryLeadTime", value(raw, "internal_target_lead_time") != null
                ? value(raw, "internal_target_lead_time")
                : value(raw, "quote_lead_time"));
        row.put("taxIncludedUnitPrice", taxIncludedUnitPrice);
        row.put("taxIncludedTotalPrice", value(raw, "tax_included_total_price"));
        row.put("status", status);
        row.put("remark", value(raw, "remark"));
        row.put("quoteMode", value(raw, "quote_mode"));
        row.put("assemblyFee", value(raw, "assembly_fee"));
        boolean canSelect = taxIncludedUnitPrice != null
                && remainingQty > 0
                && !QuoteStatusWorkflow.ITEM_RELEASED.equals(status)
                && !QuoteStatusWorkflow.RELEASED.equals(status)
                && !QuoteStatusWorkflow.isLegacyItemReleasedStatus(status);
        row.put("canSelect", canSelect);
        row.put("releaseBlockedReason", canSelect ? "" : releaseBlockedReasonFromValues(taxIncludedUnitPrice, remainingQty, status));
        return row;
    }

    public Map<String, Object> matchPartSelection(Integer rfqId, List<Map<String, String>> importedItems) {
        requireOrder(rfqId);
        Set<String> importedKeys = new HashSet<>();
        for (Map<String, String> item : importedItems == null ? List.<Map<String, String>>of() : importedItems) {
            String key = itemKey(item.getOrDefault("normalizedPartNo", item.get("partNo")),
                    item.getOrDefault("normalizedVersion", item.get("version")));
            if (!"||".equals(key)) {
                importedKeys.add(key);
            }
        }
        List<OrderDeliveryTicket> tickets = listTickets(rfqId);
        List<Map<String, Object>> selectedRows = new ArrayList<>();
        List<Map<String, Object>> skippedRows = new ArrayList<>();
        int matchedCount = 0;
        for (ItemQuoteVersion item : listItems(rfqId)) {
            if (!importedKeys.contains(itemKey(item.getPartNo(), item.getVersion()))) {
                continue;
            }
            matchedCount++;
            Map<String, Object> row = buildItemRow(item, ticketedQty(item, tickets));
            if (Boolean.TRUE.equals(row.get("canSelect"))) {
                selectedRows.add(row);
            } else {
                skippedRows.add(row);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("importedCount", importedKeys.size());
        result.put("matchedCount", matchedCount);
        result.put("selectedCount", selectedRows.size());
        result.put("skippedCount", skippedRows.size());
        result.put("data", selectedRows);
        result.put("skippedItems", skippedRows);
        return result;
    }

    public Map<String, Object> matchPartSelectionBatch(List<ReleaseOrderSource> sources, List<Map<String, String>> importedItems) {
        sources = normalizeReleaseSourcesFromInput(sources);
        if (sources.stream().anyMatch(source -> source.getSourceType() == ReleaseSourceType.SALES_ORDER)) {
            return matchMixedPartSelection(sources, importedItems);
        }
        List<Integer> rfqIds = sources.stream()
                .map(ReleaseOrderSource::getSourceOrderId)
                .filter(Objects::nonNull)
                .map(Long::intValue)
                .toList();
        List<OrderRfq> orders = requireOrders(rfqIds);
        Map<Integer, OrderRfq> orderById = new LinkedHashMap<>();
        orders.forEach(order -> orderById.put(order.getId(), order));

        Set<String> importedKeys = new HashSet<>();
        Map<String, Set<String>> importedKeysByRfqNo = new LinkedHashMap<>();
        for (Map<String, String> item : importedItems == null ? List.<Map<String, String>>of() : importedItems) {
            String key = itemKey(item.getOrDefault("normalizedPartNo", item.get("partNo")),
                    item.getOrDefault("normalizedVersion", item.get("version")));
            if ("||".equals(key)) {
                continue;
            }
            String rfqNo = normalizeRfqNo(item.getOrDefault("normalizedRfqNo", item.get("rfqNo")));
            if (rfqNo.isEmpty()) {
                importedKeys.add(key);
            } else {
                importedKeysByRfqNo.computeIfAbsent(rfqNo, ignored -> new HashSet<>()).add(key);
            }
        }

        List<ItemQuoteVersion> items = listItemsBatch(orderById.keySet().stream().toList());
        List<OrderDeliveryTicket> tickets = listTicketsForItems(items);
        List<Map<String, Object>> selectedRows = new ArrayList<>();
        List<Map<String, Object>> skippedRows = new ArrayList<>();
        int matchedCount = 0;
        for (ItemQuoteVersion item : items) {
            OrderRfq order = orderById.get(item.getRfqId());
            String key = itemKey(item.getPartNo(), item.getVersion());
            String rfqNo = order == null ? "" : normalizeRfqNo(order.getRfqNo());
            boolean matchesAnySelectedOrder = importedKeys.contains(key);
            boolean matchesSpecifiedOrder = importedKeysByRfqNo.getOrDefault(rfqNo, Set.of()).contains(key);
            if (!matchesAnySelectedOrder && !matchesSpecifiedOrder) {
                continue;
            }
            matchedCount++;
            Map<String, Object> row = buildBatchItemRow(item, ticketedQty(item, tickets), orderById);
            if (Boolean.TRUE.equals(row.get("canSelect"))) {
                selectedRows.add(row);
            } else {
                skippedRows.add(row);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("importedCount", importedKeys.size() + importedKeysByRfqNo.values().stream().mapToInt(Set::size).sum());
        result.put("matchedCount", matchedCount);
        result.put("selectedCount", selectedRows.size());
        result.put("skippedCount", skippedRows.size());
        result.put("data", selectedRows);
        result.put("skippedItems", skippedRows);
        return result;
    }

    private Map<String, Object> matchMixedPartSelection(
            List<ReleaseOrderSource> sources,
            List<Map<String, String>> importedItems) {
        Map<Integer, OrderRfq> orderById = accessibleRfqOrderMap(sourceRfqIds(sources));
        Map<Long, SalesOrderHeader> salesOrderById = accessibleSalesOrderMap(sourceSalesOrderIds(sources));

        Set<String> importedKeys = new HashSet<>();
        Map<String, Set<String>> importedKeysByOrderNo = new LinkedHashMap<>();
        for (Map<String, String> item : importedItems == null ? List.<Map<String, String>>of() : importedItems) {
            String key = itemKey(item.getOrDefault("normalizedPartNo", item.get("partNo")),
                    item.getOrDefault("normalizedVersion", item.get("version")));
            if ("||".equals(key)) {
                continue;
            }
            String orderNo = normalizeRfqNo(item.getOrDefault("normalizedRfqNo", item.get("rfqNo")));
            if (orderNo.isEmpty()) {
                importedKeys.add(key);
            } else {
                importedKeysByOrderNo.computeIfAbsent(orderNo, ignored -> new HashSet<>()).add(key);
            }
        }

        List<Map<String, Object>> selectedRows = new ArrayList<>();
        List<Map<String, Object>> skippedRows = new ArrayList<>();
        int matchedCount = matchRfqPartSelection(orderById, importedKeys, importedKeysByOrderNo, selectedRows, skippedRows);
        matchedCount += matchSalesOrderPartSelection(salesOrderById, importedKeys, importedKeysByOrderNo, selectedRows, skippedRows);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("importedCount", importedKeys.size() + importedKeysByOrderNo.values().stream().mapToInt(Set::size).sum());
        result.put("matchedCount", matchedCount);
        result.put("selectedCount", selectedRows.size());
        result.put("skippedCount", skippedRows.size());
        result.put("data", selectedRows);
        result.put("skippedItems", skippedRows);
        return result;
    }

    private int matchRfqPartSelection(
            Map<Integer, OrderRfq> orderById,
            Set<String> importedKeys,
            Map<String, Set<String>> importedKeysByOrderNo,
            List<Map<String, Object>> selectedRows,
            List<Map<String, Object>> skippedRows) {
        if (orderById.isEmpty()) {
            return 0;
        }
        List<ItemQuoteVersion> items = listItemsBatch(orderById.keySet().stream().toList());
        List<OrderDeliveryTicket> tickets = listTicketsForItems(items);
        int matchedCount = 0;
        for (ItemQuoteVersion item : items) {
            OrderRfq order = orderById.get(item.getRfqId());
            String key = itemKey(item.getPartNo(), item.getVersion());
            String orderNo = order == null ? "" : normalizeRfqNo(order.getRfqNo());
            if (!importedKeys.contains(key) && !importedKeysByOrderNo.getOrDefault(orderNo, Set.of()).contains(key)) {
                continue;
            }
            matchedCount++;
            Map<String, Object> row = buildBatchItemRow(item, ticketedQty(item, tickets), orderById);
            if (Boolean.TRUE.equals(row.get("canSelect"))) {
                selectedRows.add(row);
            } else {
                skippedRows.add(row);
            }
        }
        return matchedCount;
    }

    private int matchSalesOrderPartSelection(
            Map<Long, SalesOrderHeader> salesOrderById,
            Set<String> importedKeys,
            Map<String, Set<String>> importedKeysByOrderNo,
            List<Map<String, Object>> selectedRows,
            List<Map<String, Object>> skippedRows) {
        if (salesOrderById.isEmpty()) {
            return 0;
        }
        int matchedCount = 0;
        for (SalesOrderItem item : listSalesOrderItemsBatch(salesOrderById.keySet().stream().toList())) {
            SalesOrderHeader order = salesOrderById.get(item.getSalesOrderId());
            String key = itemKey(item.getPartNo(), item.getVersion());
            String salesOrderNo = order == null ? "" : normalizeRfqNo(order.getSalesOrderNo());
            String customerOrderNo = order == null ? "" : normalizeRfqNo(order.getCustomerOrderNo());
            boolean matchesAnySelectedOrder = importedKeys.contains(key);
            boolean matchesSpecifiedOrder = importedKeysByOrderNo.getOrDefault(salesOrderNo, Set.of()).contains(key)
                    || importedKeysByOrderNo.getOrDefault(customerOrderNo, Set.of()).contains(key);
            if (!matchesAnySelectedOrder && !matchesSpecifiedOrder) {
                continue;
            }
            matchedCount++;
            Map<String, Object> row = buildSalesOrderItemRow(item, order);
            if (Boolean.TRUE.equals(row.get("canSelect"))) {
                selectedRows.add(row);
            } else {
                skippedRows.add(row);
            }
        }
        return matchedCount;
    }

    private List<ReleaseOrderSource> normalizeReleaseSources(ReleaseItemBatchQueryReq req) {
        if (req == null) {
            return List.of();
        }
        if (req.getSources() != null && !req.getSources().isEmpty()) {
            return normalizeReleaseSourcesFromInput(new ArrayList<>(req.getSources()));
        }
        return normalizeReleaseSourcesFromInput(req.getRfqIds());
    }

    private List<ReleaseOrderSource> normalizeReleaseSourcesFromInput(List<?> input) {
        List<ReleaseOrderSource> sources = new ArrayList<>();
        for (Object value : input == null ? List.of() : input) {
            ReleaseOrderSource source = normalizeReleaseSource(value);
            if (source != null && source.getSourceOrderId() != null) {
                sources.add(source);
            }
        }
        Map<String, ReleaseOrderSource> deduped = new LinkedHashMap<>();
        for (ReleaseOrderSource source : sources) {
            deduped.put(source.getSourceType() + ":" + source.getSourceOrderId(), source);
        }
        return new ArrayList<>(deduped.values());
    }

    private ReleaseOrderSource normalizeReleaseSource(Object value) {
        if (value instanceof ReleaseOrderSource source) {
            ReleaseOrderSource normalized = new ReleaseOrderSource();
            normalized.setSourceType(source.getSourceType() == null ? ReleaseSourceType.RFQ : source.getSourceType());
            normalized.setSourceOrderId(source.getSourceOrderId());
            return normalized;
        }
        if (value instanceof Number number) {
            return ReleaseOrderSource.rfq(number.intValue());
        }
        if (value instanceof Map<?, ?> map) {
            Object rawType = map.get("sourceType");
            Object rawOrderId = map.get("sourceOrderId");
            if (rawOrderId == null) {
                rawOrderId = map.get("id");
            }
            ReleaseOrderSource source = new ReleaseOrderSource();
            source.setSourceType(parseSourceType(rawType));
            source.setSourceOrderId(rawOrderId instanceof Number number ? number.longValue() : parseLong(rawOrderId));
            return source;
        }
        return null;
    }

    private ReleaseSourceType parseSourceType(Object rawType) {
        if (rawType instanceof ReleaseSourceType type) {
            return type;
        }
        String value = rawType == null ? "" : rawType.toString().trim();
        if (ReleaseSourceType.SALES_ORDER.name().equalsIgnoreCase(value)) {
            return ReleaseSourceType.SALES_ORDER;
        }
        return ReleaseSourceType.RFQ;
    }

    private Set<Integer> sourceRfqIds(List<ReleaseOrderSource> sources) {
        return sources == null ? Set.of() : sources.stream()
                .filter(source -> source.getSourceType() == ReleaseSourceType.RFQ)
                .map(ReleaseOrderSource::getSourceOrderId)
                .filter(Objects::nonNull)
                .map(Long::intValue)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<Long> sourceSalesOrderIds(List<ReleaseOrderSource> sources) {
        return sources == null ? Set.of() : sources.stream()
                .filter(source -> source.getSourceType() == ReleaseSourceType.SALES_ORDER)
                .map(ReleaseOrderSource::getSourceOrderId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }
    private Map<Integer, OrderRfq> accessibleRfqOrderMap(Set<Integer> rfqIds) {
        Map<Integer, OrderRfq> result = new LinkedHashMap<>();
        for (OrderRfq order : requireOrders(new ArrayList<>(rfqIds == null ? Set.of() : rfqIds))) {
            result.put(order.getId(), order);
        }
        return result;
    }

    private Map<Long, SalesOrderHeader> accessibleSalesOrderMap(Set<Long> salesOrderIds) {
        if (salesOrderIds == null || salesOrderIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, SalesOrderHeader> byId = new LinkedHashMap<>();
        salesOrderHeaderMapper.selectBatchIds(new ArrayList<>(salesOrderIds))
                .forEach(order -> byId.put(order.getId(), order));
        Map<Long, SalesOrderHeader> result = new LinkedHashMap<>();
        for (Long id : salesOrderIds) {
            SalesOrderHeader order = byId.get(id);
            if (order == null || !canAccessSalesOrder(order)) {
                continue;
            }
            assertCanReadSalesOrderReleaseWorkspace(order);
            result.put(id, order);
        }
        return result;
    }

    private List<SalesOrderItem> listSalesOrderItemsBatch(List<Long> salesOrderIds) {
        if (salesOrderIds == null || salesOrderIds.isEmpty()) {
            return List.of();
        }
        QueryWrapper<SalesOrderItem> query = new QueryWrapper<>();
        query.in("sales_order_id", salesOrderIds)
                .orderByAsc("sales_order_id")
                .orderByAsc("id");
        return salesOrderItemMapper.selectList(query);
    }

    private Map<Integer, OrderRfq> requireRfqOrdersForUpdate(Set<Integer> rfqIds) {
        if (rfqIds == null || rfqIds.isEmpty()) {
            return Map.of();
        }
        Map<Integer, OrderRfq> result = new LinkedHashMap<>();
        for (OrderRfq order : requireOrdersForUpdate(new ArrayList<>(rfqIds))) {
            result.put(order.getId(), order);
        }
        return result;
    }

    private Map<Long, SalesOrderHeader> requireSalesOrdersForUpdate(Set<Long> salesOrderIds) {
        if (salesOrderIds == null || salesOrderIds.isEmpty()) {
            return Map.of();
        }
        if (salesOrderHeaderMapper == null) {
            throw new RuntimeException("Sales order release service is unavailable");
        }
        Map<Long, SalesOrderHeader> result = new LinkedHashMap<>();
        for (Long salesOrderId : salesOrderIds) {
            SalesOrderHeader order = salesOrderHeaderMapper.selectByIdForUpdate(salesOrderId);
            if (order == null) {
                throw new RuntimeException("Sales order does not exist");
            }
            assertSalesOrderDataScope(order);
            assertCanReadSalesOrderReleaseWorkspace(order);
            result.put(order.getId(), order);
        }
        return result;
    }

    private OrderRfq requireOrder(Integer rfqId) {
        if (rfqId == null) {
            throw new RuntimeException("RFQ ID is required");
        }
        OrderRfq order = orderRfqMapper.selectById(rfqId);
        if (order == null) {
            throw new RuntimeException("RFQ order does not exist");
        }
        assertOrderDataScope(order);
        assertCanReadReleaseWorkspace(order);
        return order;
    }

    private List<OrderRfq> requireOrders(List<Integer> rfqIds) {
        List<Integer> normalizedIds = new ArrayList<>(new java.util.LinkedHashSet<>(
                rfqIds == null ? List.of() : rfqIds.stream()
                        .filter(id -> id != null)
                        .toList()));
        if (normalizedIds.isEmpty()) {
            return List.of();
        }
        Map<Integer, OrderRfq> orderById = new LinkedHashMap<>();
        orderRfqMapper.selectBatchIds(normalizedIds).forEach(order -> orderById.put(order.getId(), order));
        List<OrderRfq> orders = new ArrayList<>();
        for (Integer rfqId : normalizedIds) {
            OrderRfq order = orderById.get(rfqId);
            if (order == null || !canAccessOrder(order)) {
                continue;
            }
            assertCanReadReleaseWorkspace(order);
            orders.add(order);
        }
        return orders;
    }

    private List<OrderRfq> requireOrdersForUpdate(List<Integer> rfqIds) {
        List<Integer> normalizedIds = new ArrayList<>(new java.util.LinkedHashSet<>(
                rfqIds == null ? List.of() : rfqIds.stream()
                        .filter(id -> id != null)
                        .toList()));
        if (normalizedIds.isEmpty()) {
            throw new RuntimeException("RFQ ID is required");
        }
        List<OrderRfq> orders = new ArrayList<>();
        for (Integer rfqId : normalizedIds) {
            orders.add(requireOrderForUpdate(rfqId));
        }
        return orders;
    }

    private OrderRfq requireOrderForUpdate(Integer rfqId) {
        if (rfqId == null) {
            throw new RuntimeException("RFQ ID is required");
        }
        OrderRfq order = orderRfqMapper.selectByIdForUpdate(rfqId);
        if (order == null) {
            throw new RuntimeException("RFQ order does not exist");
        }
        assertOrderDataScope(order);
        assertCanGenerateRelease(order);
        return order;
    }

    private void assertOrderDataScope(OrderRfq order) {
        if (dataScopeService != null) {
            dataScopeService.assertCanAccessOrder(order);
        }
    }

    private void assertSalesOrderDataScope(SalesOrderHeader order) {
        if (dataScopeService != null) {
            dataScopeService.assertCanAccessOrder(toOrderScopeProxy(order));
        }
    }

    private boolean canAccessOrder(OrderRfq order) {
        if (dataScopeService == null) {
            return true;
        }
        try {
            dataScopeService.assertCanAccessOrder(order);
            return true;
        } catch (RuntimeException e) {
            if (isDataScopeDenied(e)) {
                return false;
            }
            throw e;
        }
    }

    private boolean isDataScopeDenied(RuntimeException e) {
        String message = e.getMessage();
        return message != null && message.startsWith("No data permission for order ");
    }

    private boolean canAccessSalesOrder(SalesOrderHeader order) {
        if (dataScopeService == null || order == null) {
            return true;
        }
        try {
            dataScopeService.assertCanAccessOrder(toOrderScopeProxy(order));
            return true;
        } catch (RuntimeException e) {
            if (isDataScopeDenied(e)) {
                return false;
            }
            throw e;
        }
    }

    private OrderRfq toOrderScopeProxy(SalesOrderHeader order) {
        OrderRfq proxy = new OrderRfq();
        proxy.setId(order.getId() == null ? null : order.getId().intValue());
        proxy.setCustomerName(order.getCustomerName());
        proxy.setCreatedBy(order.getCreatedBy() == null ? null : order.getCreatedBy().intValue());
        proxy.setOwnerUserId(order.getOwnerUserId());
        return proxy;
    }

    private void assertCanReadSalesOrderReleaseWorkspace(SalesOrderHeader order) {
        if (order == null || QuoteStatusWorkflow.canReadReleaseWorkspace(order.getStatus())) {
            return;
        }
        throw new RuntimeException("Only quoted sales orders can enter release workspace");
    }
    private void assertCanReadReleaseWorkspace(OrderRfq order) {
        if (order == null || QuoteStatusWorkflow.canReadReleaseWorkspace(order.getStatus())) {
            return;
        }
        if (QuoteStatusWorkflow.PRICED.equals(order.getStatus())) {
            throw new RuntimeException("Please save quote sheet before entering release workspace");
        }
        throw new RuntimeException("Only quoted, partially released, or released orders can enter release workspace");
    }

    private void assertCanGenerateRelease(OrderRfq order) {
        if (order == null || QuoteStatusWorkflow.canGenerateDeliveryTicket(order.getStatus())) {
            return;
        }
        if (QuoteStatusWorkflow.PRICED.equals(order.getStatus())) {
            throw new RuntimeException("Please save quote sheet before entering release workspace");
        }
        throw new RuntimeException("Only quoted, partially released, or released orders can enter release workspace");
    }

    private List<ItemQuoteVersion> listItems(Integer rfqId) {
        QueryWrapper<ItemQuoteVersion> qw = new QueryWrapper<>();
        qw.eq("rfq_id", rfqId).orderByAsc("id");
        return itemMapper.selectList(qw);
    }

    private List<ItemQuoteVersion> listItemsBatch(List<Integer> rfqIds) {
        if (rfqIds == null || rfqIds.isEmpty()) {
            return List.of();
        }
        QueryWrapper<ItemQuoteVersion> qw = new QueryWrapper<>();
        qw.in("rfq_id", rfqIds)
                .orderByAsc("rfq_id")
                .orderByAsc("id");
        return itemMapper.selectList(qw);
    }

    private List<Map<String, Object>> buildItemRows(Integer rfqId) {
        List<ItemQuoteVersion> items = listItems(rfqId);
        List<OrderDeliveryTicket> tickets = listTickets(rfqId);
        return items.stream()
                .map(item -> buildItemRow(item, ticketedQty(item, tickets)))
                .toList();
    }

    private List<OrderDeliveryTicket> listTickets(Integer rfqId) {
        QueryWrapper<OrderDeliveryTicket> qw = new QueryWrapper<>();
        qw.eq("rfq_id", rfqId);
        return ticketMapper.selectList(qw);
    }

    private List<OrderDeliveryTicket> listTicketsForItems(Integer rfqId, List<ItemQuoteVersion> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<Integer> itemIds = items.stream()
                .map(ItemQuoteVersion::getId)
                .filter(id -> id != null)
                .toList();
        QueryWrapper<OrderDeliveryTicket> qw = new QueryWrapper<>();
        qw.eq("rfq_id", rfqId);
        if (!itemIds.isEmpty()) {
            qw.and(wrapper -> wrapper.in("item_id", itemIds).or().isNull("item_id"));
        }
        return ticketMapper.selectList(qw);
    }

    private List<OrderDeliveryTicket> listTicketsForItems(List<ItemQuoteVersion> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<Integer> rfqIds = items.stream()
                .map(ItemQuoteVersion::getRfqId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        List<Integer> itemIds = items.stream()
                .map(ItemQuoteVersion::getId)
                .filter(id -> id != null)
                .toList();
        QueryWrapper<OrderDeliveryTicket> qw = new QueryWrapper<>();
        qw.in("rfq_id", rfqIds);
        if (!itemIds.isEmpty()) {
            qw.and(wrapper -> wrapper.in("item_id", itemIds).or().isNull("item_id"));
        }
        return ticketMapper.selectList(qw);
    }

    private int ticketedQty(ItemQuoteVersion item, List<OrderDeliveryTicket> tickets) {
        return tickets.stream()
                .filter(ticket -> matchesTicketItem(item, ticket))
                .mapToInt(ticket -> safeInt(ticket.getQty()))
                .sum();
    }

    private boolean matchesTicketItem(ItemQuoteVersion item, OrderDeliveryTicket ticket) {
        if (!Objects.equals(item.getRfqId(), ticket.getRfqId())) {
            return false;
        }
        if (item.getId() != null && item.getId().equals(ticket.getItemId())) {
            return true;
        }
        return ticket.getItemId() == null
                && normalizePartNo(item.getPartNo()).equals(normalizePartNo(ticket.getPartNo()))
                && normalizeVersion(item.getVersion()).equals(normalizeVersion(ticket.getVersion()));
    }

    private Map<String, Object> buildItemRow(ItemQuoteVersion item) {
        return buildItemRow(item, 0);
    }

    private Map<String, Object> buildItemRow(ItemQuoteVersion item, int ticketedQty) {
        Map<String, Object> row = new LinkedHashMap<>();
        String effectiveDeliveryLeadTime = leadTimeService.effectiveDeliveryLeadTime(item);
        int sourceQty = safeInt(item.getQty());
        int remainingQty = Math.max(0, sourceQty - ticketedQty);
        row.put("id", item.getId());
        row.put("sourceType", "RFQ");
        row.put("sourceOrderId", item.getRfqId());
        row.put("sourceItemId", item.getId());
        row.put("releaseItemKey", "RFQ:" + item.getId());
        row.put("rfqId", item.getRfqId());
        row.put("partNo", item.getPartNo());
        row.put("version", item.getVersion());
        row.put("salesOrderDate", item.getSalesOrderDate());
        row.put("customerOrderNo", item.getCustomerOrderNo());
        row.put("customerProjectNo", item.getCustomerProjectNo());
        row.put("customerOrderTitle", item.getCustomerOrderTitle());
        row.put("orderType", item.getOrderType());
        row.put("internalProjectNo", item.getInternalProjectNo());
        String drawingUrl = item.getDrawingUrl();
        row.put("drawingUrl", drawingUrl);
        row.put("hasDrawing", drawingUrl != null && !drawingUrl.trim().isEmpty());
        row.put("description", item.getDescription());
        row.put("qty", item.getQty());
        row.put("ticketedQty", ticketedQty);
        row.put("remainingQty", remainingQty);
        row.put("unit", item.getUnit());
        row.put("materialName", item.getMaterialName());
        row.put("engineeringMaterialName", MaterialDataResolver.engineeringMaterialName(item));
        row.put("surfaceTreatment", item.getSurfaceTreatment());
        row.put("leadTime", item.getLeadTime());
        row.put("quoteLeadTime", item.getQuoteLeadTime());
        row.put("internalTargetLeadTime", item.getInternalTargetLeadTime());
        row.put("effectiveDeliveryLeadTime", effectiveDeliveryLeadTime);
        row.put("deliveryParsedDate", leadTimeService.parsedDateText(effectiveDeliveryLeadTime));
        row.put("canAutoShiftInternalLeadTime", leadTimeService.canAutoShift(effectiveDeliveryLeadTime));
        row.put("taxIncludedUnitPrice", item.getTaxIncludedUnitPrice());
        row.put("taxIncludedTotalPrice", item.getTaxIncludedTotalPrice());
        row.put("status", item.getStatus());
        row.put("remark", item.getRemark());
        row.put("quoteMode", item.getQuoteMode());
        row.put("assemblyFee", item.getAssemblyFee());
        boolean canSelect = item.getTaxIncludedUnitPrice() != null
                && remainingQty > 0
                && !QuoteStatusWorkflow.ITEM_RELEASED.equals(item.getStatus())
                && !QuoteStatusWorkflow.RELEASED.equals(item.getStatus())
                && !QuoteStatusWorkflow.isLegacyItemReleasedStatus(item.getStatus());
        row.put("canSelect", canSelect);
        row.put("releaseBlockedReason", canSelect ? "" : releaseBlockedReason(item, remainingQty));
        return row;
    }

    private Map<String, Object> buildBatchItemRow(ItemQuoteVersion item, int ticketedQty, Map<Integer, OrderRfq> orderById) {
        Map<String, Object> row = buildItemRow(item, ticketedQty);
        OrderRfq order = orderById.get(item.getRfqId());
        row.put("rfqNo", order == null ? "" : order.getRfqNo());
        row.put("sourceOrderNo", order == null ? "" : order.getRfqNo());
        row.put("customerName", order == null ? "" : order.getCustomerName());
        row.put("orderStatus", order == null ? "" : order.getStatus());
        return row;
    }

    private Map<String, Object> buildSalesOrderItemRow(SalesOrderItem item, SalesOrderHeader order) {
        Map<String, Object> row = new LinkedHashMap<>();
        int sourceQty = safeInt(item.getQty());
        int ticketedQty = safeInt(item.getReleasedQty());
        int remainingQty = item.getRemainingQty() == null || item.getRemainingQty() <= 0
                ? Math.max(0, sourceQty - ticketedQty)
                : item.getRemainingQty();
        row.put("id", item.getId());
        row.put("sourceType", "SALES_ORDER");
        row.put("sourceOrderId", item.getSalesOrderId());
        row.put("sourceItemId", item.getId());
        row.put("releaseItemKey", "SALES_ORDER:" + item.getId());
        row.put("rfqId", null);
        row.put("rfqNo", "");
        row.put("sourceOrderNo", order == null ? "" : order.getSalesOrderNo());
        row.put("customerName", order == null ? "" : order.getCustomerName());
        row.put("orderStatus", order == null ? "" : order.getStatus());
        row.put("partNo", item.getPartNo());
        row.put("version", item.getVersion());
        row.put("drawingUrl", item.getDrawingUrl());
        row.put("hasDrawing", Boolean.TRUE.equals(item.getHasDrawing()) || !isBlank(item.getDrawingUrl()));
        row.put("description", item.getDescription());
        row.put("qty", item.getQty());
        row.put("ticketedQty", ticketedQty);
        row.put("remainingQty", remainingQty);
        row.put("materialName", item.getMaterialName());
        row.put("engineeringMaterialName", item.getEngineeringMaterialName());
        row.put("surfaceTreatment", item.getSurfaceTreatment());
        row.put("leadTime", item.getCustomerDeliveryDate() == null ? null : item.getCustomerDeliveryDate().toString());
        row.put("quoteLeadTime", item.getInternalDeliveryDate() == null ? null : item.getInternalDeliveryDate().toString());
        row.put("internalTargetLeadTime", null);
        row.put("effectiveDeliveryLeadTime", item.getInternalDeliveryDate() == null ? null : item.getInternalDeliveryDate().toString());
        row.put("taxIncludedUnitPrice", item.getTaxIncludedUnitPrice());
        row.put("taxIncludedTotalPrice", item.getTaxIncludedTotalPrice());
        row.put("status", item.getStatus());
        row.put("remark", item.getRemark());
        row.put("quoteMode", item.getQuoteMode());
        row.put("assemblyFee", item.getAssemblyFee());
        boolean canSelect = item.getTaxIncludedUnitPrice() != null
                && remainingQty > 0
                && !QuoteStatusWorkflow.ITEM_RELEASED.equals(item.getStatus())
                && !QuoteStatusWorkflow.RELEASED.equals(item.getStatus())
                && !QuoteStatusWorkflow.isLegacyItemReleasedStatus(item.getStatus());
        row.put("canSelect", canSelect);
        row.put("releaseBlockedReason", canSelect ? "" : releaseBlockedReasonFromValues(item.getTaxIncludedUnitPrice(), remainingQty, item.getStatus()));
        return row;
    }
    private String releaseBlockedReason(ItemQuoteVersion item, int remainingQty) {
        if (item.getTaxIncludedUnitPrice() == null) {
            return "Item is not quoted";
        }
        if (remainingQty <= 0) {
            return "Item is fully released";
        }
        if (QuoteStatusWorkflow.ITEM_RELEASED.equals(item.getStatus())
                || QuoteStatusWorkflow.RELEASED.equals(item.getStatus())
                || QuoteStatusWorkflow.isLegacyItemReleasedStatus(item.getStatus())) {
            return "Item is released";
        }
        return "";
    }

    private String releaseBlockedReasonFromValues(Object taxIncludedUnitPrice, int remainingQty, String status) {
        if (taxIncludedUnitPrice == null) {
            return "Item is not quoted";
        }
        if (remainingQty <= 0) {
            return "Item is fully released";
        }
        if (QuoteStatusWorkflow.ITEM_RELEASED.equals(status)
                || QuoteStatusWorkflow.RELEASED.equals(status)
                || QuoteStatusWorkflow.isLegacyItemReleasedStatus(status)) {
            return "Item is released";
        }
        return "";
    }

    private String placeholders(List<Object> params, Iterable<?> values) {
        List<String> placeholders = new ArrayList<>();
        for (Object value : values) {
            placeholders.add("?");
            params.add(value);
        }
        return String.join(", ", placeholders);
    }

    private Object value(Map<String, Object> row, String key) {
        if (row == null || key == null) {
            return null;
        }
        if (row.containsKey(key)) {
            return row.get(key);
        }
        String camel = toCamelCase(key);
        if (row.containsKey(camel)) {
            return row.get(camel);
        }
        String upper = key.toUpperCase(java.util.Locale.ROOT);
        if (row.containsKey(upper)) {
            return row.get(upper);
        }
        return null;
    }

    private String text(Map<String, Object> row, String key) {
        Object value = value(row, key);
        return value == null ? "" : value.toString();
    }

    private int number(Map<String, Object> row, String key) {
        Object value = value(row, key);
        return value instanceof Number number ? number.intValue() : 0;
    }

    private Long longValue(Map<String, Object> row, String key) {
        Object value = value(row, key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return parseLong(value);
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String toCamelCase(String key) {
        StringBuilder result = new StringBuilder();
        boolean uppercaseNext = false;
        for (char current : key.toCharArray()) {
            if (current == '_') {
                uppercaseNext = true;
            } else if (uppercaseNext) {
                result.append(Character.toUpperCase(current));
                uppercaseNext = false;
            } else {
                result.append(current);
            }
        }
        return result.toString();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record FilteredReleaseSql(String sql, List<Object> params) {
    }

    private record ReleaseDrawingTarget(
            ReleaseSourceType sourceType,
            Long sourceOrderId,
            Long sourceItemId,
            String sourceOrderNo,
            String customerName,
            String partNo,
            String version,
            boolean mutable,
            String blockedReason,
            ItemQuoteVersion rfqItem,
            SalesOrderItem salesOrderItem,
            OrderRfq rfqOrder,
            SalesOrderHeader salesOrder) {
        private static ReleaseDrawingTarget rfq(
                ItemQuoteVersion item,
                OrderRfq order,
                boolean mutable,
                String blockedReason) {
            return new ReleaseDrawingTarget(
                    ReleaseSourceType.RFQ,
                    item == null || item.getRfqId() == null ? null : item.getRfqId().longValue(),
                    item == null || item.getId() == null ? null : item.getId().longValue(),
                    order == null ? "" : order.getRfqNo(),
                    order == null ? "" : order.getCustomerName(),
                    item == null ? "" : item.getPartNo(),
                    item == null ? "" : item.getVersion(),
                    mutable,
                    blockedReason,
                    item,
                    null,
                    order,
                    null);
        }

        private static ReleaseDrawingTarget salesOrder(
                SalesOrderItem item,
                SalesOrderHeader order,
                boolean mutable,
                String blockedReason) {
            return new ReleaseDrawingTarget(
                    ReleaseSourceType.SALES_ORDER,
                    item == null ? null : item.getSalesOrderId(),
                    item == null ? null : item.getId(),
                    order == null ? "" : order.getSalesOrderNo(),
                    order == null ? "" : order.getCustomerName(),
                    item == null ? "" : item.getPartNo(),
                    item == null ? "" : item.getVersion(),
                    mutable,
                    blockedReason,
                    null,
                    item,
                    null,
                    order);
        }
    }

    private String normalizePartNo(String partNo) {
        return partNo == null ? "" : partNo.trim().toUpperCase();
    }

    private String normalizeVersion(String version) {
        return version == null ? "" : version.trim().toUpperCase();
    }

    private String normalizeRfqNo(String rfqNo) {
        return rfqNo == null ? "" : rfqNo.trim().toUpperCase();
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private int safeInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private int safePage(Integer page) {
        return page == null || page < 1 ? DEFAULT_PAGE : page;
    }

    private int safePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private String itemKey(String partNo, String version) {
        return normalizePartNo(partNo) + "||" + normalizeVersion(version);
    }

    private void addVersionDrawingTarget(
            ReleaseDrawingTarget target,
            Map<String, List<ReleaseDrawingTarget>> targetsByPartNoVersion,
            Set<DrawingPartNoResolver.DrawingIdentity> targetIdentities) {
        String partNo = normalizePartNo(target.partNo());
        String version = normalizeVersion(target.version());
        if (partNo.isEmpty() || version.isEmpty()) {
            return;
        }
        targetsByPartNoVersion.computeIfAbsent(itemKey(partNo, version), ignored -> new ArrayList<>()).add(target);
        targetIdentities.add(new DrawingPartNoResolver.DrawingIdentity(partNo, version));
    }

    private int replaceReleaseDrawing(
            MultipartFile drawing,
            boolean matchByPartNoVersion,
            Map<String, List<ReleaseDrawingTarget>> targetsByPartNo,
            Map<String, List<ReleaseDrawingTarget>> targetsByPartNoVersion,
            Set<DrawingPartNoResolver.DrawingIdentity> targetIdentities,
            Set<String> protectedTargetKeys,
            Set<String> replacedTargetKeys,
            List<Map<String, Object>> replacedItems,
            List<Map<String, Object>> unmatchedDrawings,
            List<Map<String, Object>> skippedFiles,
            List<Map<String, Object>> skippedItems) {
        String cleanFileName = cleanFilename(drawing == null ? null : drawing.getOriginalFilename());
        if (drawing == null || cleanFileName.isEmpty() || !cleanFileName.toLowerCase().endsWith(".pdf")) {
            skippedFiles.add(buildFileResult(cleanFileName, "", "Not a PDF file"));
            return 0;
        }

        String partNoFromFileName;
        List<ReleaseDrawingTarget> matchedTargets;
        if (matchByPartNoVersion) {
            DrawingPartNoResolver.DrawingIdentity identity =
                    DrawingPartNoResolver.extractPartNoVersion(cleanFileName, targetIdentities);
            partNoFromFileName = identity == null
                    ? DrawingPartNoResolver.extractPartNo(cleanFileName, targetsByPartNo.keySet())
                    : identity.partNo();
            matchedTargets = identity == null ? null : targetsByPartNoVersion.get(itemKey(identity.partNo(), identity.version()));
        } else {
            partNoFromFileName = DrawingPartNoResolver.extractPartNo(cleanFileName, targetsByPartNo.keySet());
            matchedTargets = targetsByPartNo.get(partNoFromFileName);
        }

        if (matchedTargets == null || matchedTargets.isEmpty()) {
            unmatchedDrawings.add(buildFileResult(cleanFileName, partNoFromFileName, "No matching item in selected orders"));
            return 0;
        }

        Map<String, String> newUrlBySource = new LinkedHashMap<>();
        for (ReleaseDrawingTarget target : matchedTargets) {
            if (protectedTargetKeys != null && protectedTargetKeys.contains(targetKey(target))) {
                skippedItems.add(buildDrawingItemResult(target, cleanFileName, "Already replaced by version drawing"));
                continue;
            }
            if (!target.mutable()) {
                skippedItems.add(buildDrawingItemResult(target, cleanFileName, target.blockedReason()));
                continue;
            }
            String sourceKey = target.sourceType().name() + ":" + target.sourceOrderId();
            String fileUrl = newUrlBySource.computeIfAbsent(sourceKey,
                    ignored -> saveReleaseDrawing(drawing, cleanFileName, target));
            applyReleaseDrawing(target, fileUrl);
            if (replacedTargetKeys != null) {
                replacedTargetKeys.add(targetKey(target));
            }
            replacedItems.add(buildDrawingItemResult(target, cleanFileName, "Replaced"));
        }
        return matchedTargets.size();
    }

    private String targetKey(ReleaseDrawingTarget target) {
        return target.sourceType().name() + ":" + target.sourceItemId();
    }

    private boolean isEmptyFiles(List<MultipartFile> files) {
        return files == null || files.isEmpty();
    }

    private int fileCount(List<MultipartFile> files) {
        return files == null ? 0 : files.size();
    }

    private void assertReleaseItemMutable(ItemQuoteVersion item, List<OrderDeliveryTicket> tickets) {
        int itemTicketedQty = ticketedQty(item, tickets == null ? List.of() : tickets);
        if (!isReleaseItemMutable(item, itemTicketedQty)) {
            int remainingQty = Math.max(0, safeInt(item.getQty()) - itemTicketedQty);
            throw new RuntimeException(releaseMutationBlockedReason(item, remainingQty));
        }
    }

    private boolean isReleaseItemMutable(ItemQuoteVersion item, int ticketedQty) {
        if (item == null) {
            return false;
        }
        int remainingQty = Math.max(0, safeInt(item.getQty()) - ticketedQty);
        return remainingQty > 0
                && !QuoteStatusWorkflow.ITEM_RELEASED.equals(item.getStatus())
                && !QuoteStatusWorkflow.RELEASED.equals(item.getStatus())
                && !QuoteStatusWorkflow.isLegacyItemReleasedStatus(item.getStatus());
    }

    private String releaseMutationBlockedReason(ItemQuoteVersion item, int remainingQty) {
        if (remainingQty <= 0) {
            return "Item is fully released";
        }
        if (item != null && (QuoteStatusWorkflow.ITEM_RELEASED.equals(item.getStatus())
                || QuoteStatusWorkflow.RELEASED.equals(item.getStatus())
                || QuoteStatusWorkflow.isLegacyItemReleasedStatus(item.getStatus()))) {
            return "Item is released";
        }
        return "Item cannot be updated";
    }

    private boolean isSalesOrderReleaseItemMutable(SalesOrderItem item) {
        if (item == null) {
            return false;
        }
        return salesOrderReleaseRemainingQty(item) > 0
                && !QuoteStatusWorkflow.ITEM_RELEASED.equals(item.getStatus())
                && !QuoteStatusWorkflow.RELEASED.equals(item.getStatus())
                && !QuoteStatusWorkflow.isLegacyItemReleasedStatus(item.getStatus());
    }

    private int salesOrderReleaseRemainingQty(SalesOrderItem item) {
        if (item == null) {
            return 0;
        }
        if (item.getRemainingQty() != null && item.getRemainingQty() > 0) {
            return item.getRemainingQty();
        }
        return Math.max(0, safeInt(item.getQty()) - safeInt(item.getReleasedQty()));
    }

    private String salesOrderReleaseMutationBlockedReason(SalesOrderItem item) {
        if (salesOrderReleaseRemainingQty(item) <= 0) {
            return "Item is fully released";
        }
        if (item != null && (QuoteStatusWorkflow.ITEM_RELEASED.equals(item.getStatus())
                || QuoteStatusWorkflow.RELEASED.equals(item.getStatus())
                || QuoteStatusWorkflow.isLegacyItemReleasedStatus(item.getStatus()))) {
            return "Item is released";
        }
        return "Item cannot be updated";
    }

    private String saveReleaseDrawing(MultipartFile drawing, String cleanFileName, OrderRfq order) {
        if (order == null) {
            throw new RuntimeException("Order does not exist");
        }
        try {
            return businessFileService
                    .saveDrawing(order, drawing, "RELEASE_DRAWING_REPLACE", String.valueOf(order.getId()))
                    .publicPath();
        } catch (RuntimeException e) {
            throw new ReleaseDrawingSaveException("Drawing save failed: " + cleanFileName, e);
        }
    }

    private String saveReleaseDrawing(MultipartFile drawing, String cleanFileName, ReleaseDrawingTarget target) {
        if (target.sourceType() == ReleaseSourceType.RFQ) {
            return saveReleaseDrawing(drawing, cleanFileName, target.rfqOrder());
        }
        if (target.salesOrder() == null) {
            throw new RuntimeException("Sales order does not exist");
        }
        try {
            String batchNo = isBlank(target.sourceOrderNo())
                    ? String.valueOf(target.sourceOrderId())
                    : target.sourceOrderNo();
            return businessFileService
                    .saveSalesOrderImportDrawing(
                            target.customerName(),
                            batchNo,
                            drawing,
                            "RELEASE_DRAWING_REPLACE",
                            String.valueOf(target.sourceOrderId()))
                    .publicPath();
        } catch (RuntimeException e) {
            throw new ReleaseDrawingSaveException("Drawing save failed: " + cleanFileName, e);
        }
    }

    private void applyReleaseDrawing(ReleaseDrawingTarget target, String fileUrl) {
        if (target.sourceType() == ReleaseSourceType.RFQ) {
            ItemQuoteVersion item = target.rfqItem();
            item.setDrawingUrl(fileUrl);
            itemMapper.updateById(item);
            return;
        }
        SalesOrderItem item = target.salesOrderItem();
        item.setDrawingUrl(fileUrl);
        item.setHasDrawing(true);
        salesOrderItemMapper.updateById(item);
    }

    private String cleanFilename(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        return originalFilename.replaceAll(".*[\\\\/]", "").trim();
    }

    private Map<String, Object> buildDrawingItemResult(
            ItemQuoteVersion item,
            OrderRfq order,
            String fileName,
            String reason) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rfqId", item == null ? null : item.getRfqId());
        result.put("rfqNo", order == null ? "" : order.getRfqNo());
        result.put("itemId", item == null ? null : item.getId());
        result.put("partNo", item == null ? "" : item.getPartNo());
        result.put("version", item == null ? "" : item.getVersion());
        result.put("fileName", fileName);
        result.put("reason", reason);
        return result;
    }

    private Map<String, Object> buildDrawingItemResult(
            ReleaseDrawingTarget target,
            String fileName,
            String reason) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sourceType", target.sourceType().name());
        result.put("sourceOrderId", target.sourceOrderId());
        result.put("sourceOrderNo", target.sourceOrderNo());
        result.put("sourceItemId", target.sourceItemId());
        result.put("partNo", target.partNo());
        result.put("version", target.version());
        result.put("fileName", fileName);
        result.put("reason", reason);
        if (target.sourceType() == ReleaseSourceType.RFQ) {
            result.put("rfqId", target.sourceOrderId() == null ? null : target.sourceOrderId().intValue());
            result.put("rfqNo", target.sourceOrderNo());
            result.put("itemId", target.sourceItemId() == null ? null : target.sourceItemId().intValue());
        }
        return result;
    }

    private Map<String, Object> buildFileResult(String fileName, String partNo, String reason) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fileName", fileName);
        result.put("partNo", partNo);
        result.put("reason", reason);
        return result;
    }

    private Map<String, Object> skipRow(Integer itemId, String partNo, String version, String reason) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("itemId", itemId);
        row.put("partNo", partNo);
        row.put("version", version);
        row.put("reason", reason);
        return row;
    }

    private Map<String, Object> sourceAuditRow(ReleaseOrderSource source) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("sourceType", source.getSourceType().name());
        row.put("sourceOrderId", source.getSourceOrderId());
        return row;
    }

    private String sourceLogValue(List<ReleaseOrderSource> sources) {
        return sources == null ? "[]" : sources.stream()
                .map(source -> source.getSourceType().name() + ":" + source.getSourceOrderId())
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    private static class ReleaseDrawingSaveException extends RuntimeException {
        private ReleaseDrawingSaveException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
