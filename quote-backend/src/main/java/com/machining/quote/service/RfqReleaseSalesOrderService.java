package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.DeliveryBatchResultDTO;
import com.machining.quote.dto.DeliveryGenerateReq;
import com.machining.quote.dto.ReleaseSourceType;
import com.machining.quote.entity.CustomerMaster;
import com.machining.quote.entity.ItemAssemblyRelation;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.entity.SalesOrderItemAssemblyRelation;
import com.machining.quote.entity.SalesOrderSourceRfq;
import com.machining.quote.mapper.CustomerMasterMapper;
import com.machining.quote.mapper.ItemAssemblyRelationMapper;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemAssemblyRelationMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.mapper.SalesOrderSourceRfqMapper;
import com.machining.quote.util.MaterialDataResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RfqReleaseSalesOrderService {
    private static final Logger log = LoggerFactory.getLogger(RfqReleaseSalesOrderService.class);
    private static final String SOURCE_RFQ_RELEASE = "RFQ_RELEASE";
    private static final String SOURCE_RFQ = "RFQ";
    private static final String HEADER_RELEASED = "已生成提单";
    private static final String MATERIAL_CONFIRMED = "已确认";
    private static final String MATERIAL_UNCONFIRMED = "未确认";
    private static final String MES_SUCCESS = "SUCCESS";
    private static final String MES_PENDING = "PENDING";
    private static final String CONFIRMED_MATERIAL_MESSAGE = "前置匹配复用已确认 MES 物料号";
    private static final String ITEM_TYPE_ASSEMBLY = "组合件";
    private static final String ITEM_TYPE_NORMAL = "普通件";
    private static final String RELATION_SPECIFIED = "已指定父子关系";
    private static final String RELATION_UNSPECIFIED = "未指定父子关系";
    private static final String RELATION_NOT_APPLICABLE = "不适用";
    private static final Set<String> ALLOWED_ORDER_TYPES = Set.of("预测订单", "备货订单", "正式订单");

    @Autowired
    private RfqReleaseBatchValidationService validationService;
    @Autowired
    private SalesOrderNumberService numberService;
    @Autowired
    private SalesOrderHeaderMapper headerMapper;
    @Autowired
    private SalesOrderItemMapper salesItemMapper;
    @Autowired
    private SalesOrderSourceRfqMapper sourceRfqMapper;
    @Autowired
    private SalesOrderItemAssemblyRelationMapper salesAssemblyMapper;
    @Autowired
    private OrderRfqMapper orderMapper;
    @Autowired
    private ItemQuoteVersionMapper itemMapper;
    @Autowired
    private ItemAssemblyRelationMapper sourceAssemblyMapper;
    @Autowired
    private OrderDeliveryTicketMapper ticketMapper;
    @Autowired
    private CustomerMasterMapper customerMapper;
    @Autowired
    private LeadTimeService leadTimeService;
    @Autowired
    private AuditService auditService;
    @Autowired(required = false)
    private DataScopeService dataScopeService;
    @Autowired(required = false)
    private ProcessRouteSnapshotService processRouteSnapshotService = new ProcessRouteSnapshotService();
    @Autowired(required = false)
    private JsonSnapshotCopyService jsonSnapshotCopyService = new JsonSnapshotCopyService();

    @Transactional
    public DeliveryBatchResultDTO generate(DeliveryGenerateReq req) {
        long startedAt = System.currentTimeMillis();
        String batchKey = validationService.requireReleaseBatchKey(req);
        headerMapper.lockReleaseBatch(batchKey);

        SalesOrderHeader existing = headerMapper.selectByReleaseBatchKey(batchKey);
        if (existing != null) {
            RfqReleaseBatchValidationService.BatchContext replayContext = validationService.validateReplay(req);
            if (!Objects.equals(existing.getSourceReleaseRequestHash(), replayContext.requestHash())) {
                throw new RuntimeException("提单批次参数与首次提交不一致");
            }
            DeliveryBatchResultDTO replay = buildExistingResult(existing, replayContext);
            audit(existing, replayContext, replay, true, startedAt);
            return replay;
        }

        RfqReleaseBatchValidationService.BatchContext context = validationService.validate(req);
        DeliveryBatchResultDTO result = create(req, context);
        audit(headerMapper.selectById(result.getSalesOrderId()), context, result, false, startedAt);
        log.info("RFQ release sales order completed salesOrderId={} rfqCount={} ticketCount={} durationMs={}",
                result.getSalesOrderId(), result.getSourceRfqIds().size(), result.getTicketCount(),
                System.currentTimeMillis() - startedAt);
        return result;
    }

    public List<OrderDeliveryTicket> loadTickets(DeliveryBatchResultDTO result) {
        if (result == null || result.getTicketIds() == null || result.getTicketIds().isEmpty()) {
            return List.of();
        }
        Map<Integer, OrderDeliveryTicket> byId = ticketMapper.selectBatchIds(result.getTicketIds()).stream()
                .collect(Collectors.toMap(OrderDeliveryTicket::getId, Function.identity()));
        return result.getTicketIds().stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    private DeliveryBatchResultDTO create(
            DeliveryGenerateReq req,
            RfqReleaseBatchValidationService.BatchContext context) {
        CustomerMaster customer = customerMapper.selectEnabledByCodeForUpdate(context.customerCode());
        if (customer == null || isBlank(customer.getCustomerCode()) || isBlank(customer.getCustomerName())) {
            throw new RuntimeException("RFQ customerCode has no enabled U9 customer cache: " + context.customerCode());
        }
        Map<Integer, OrderRfq> lockedOrders = lockOrders(context);
        List<ReleaseLine> releaseLines = lockAndValidateLines(req, context, lockedOrders);

        List<ItemAssemblyRelation> sourceRelations = selectedAssemblyRelations(releaseLines);
        Set<Integer> selectedRelationParents = sourceRelations.stream()
                .map(ItemAssemblyRelation::getParentItemId)
                .collect(Collectors.toSet());
        backfillSourceOrderFields(releaseLines);

        LocalDateTime now = LocalDateTime.now();
        SalesOrderHeader header = insertHeader(context, customer, releaseLines.size(), now);
        insertSourceRfqs(header.getId(), context.rfqIds(), now);

        Map<Integer, SalesOrderItem> salesItemBySourceId = new LinkedHashMap<>();
        List<OrderDeliveryTicket> tickets = new ArrayList<>();
        int lineNo = 1;
        for (ReleaseLine line : releaseLines) {
            OrderRfq order = lockedOrders.get(line.item().getRfqId());
            OrderDeliveryTicket ticket = insertTicket(order, line, context);
            SalesOrderItem salesItem = insertSalesItem(
                    header.getId(), lineNo++, line, ticket.getId(), selectedRelationParents, now);
            tickets.add(ticket);
            salesItemBySourceId.put(line.item().getId(), salesItem);
        }
        insertAssemblyRelations(header.getId(), sourceRelations, salesItemBySourceId, now);

        for (ReleaseLine line : releaseLines) {
            refreshItemTicketStatus(line.item());
        }
        for (Integer rfqId : context.rfqIds()) {
            refreshOrderTicketStatus(rfqId);
        }
        return buildResult(header, context.rfqIds(), tickets);
    }

    private Map<Integer, OrderRfq> lockOrders(RfqReleaseBatchValidationService.BatchContext context) {
        Map<Integer, OrderRfq> result = new LinkedHashMap<>();
        for (Integer rfqId : context.rfqIds().stream().sorted().toList()) {
            OrderRfq order = orderMapper.selectByIdForUpdate(rfqId);
            if (order == null) {
                throw new RuntimeException("议价提单批次包含不存在或无权访问的询价单");
            }
            if (dataScopeService != null) {
                dataScopeService.assertCanAccessOrder(order);
            }
            if (!QuoteStatusWorkflow.canGenerateDeliveryTicket(order.getStatus())) {
                throw new RuntimeException("只有已报价订单可以生成提单");
            }
            result.put(rfqId, order);
        }
        return result;
    }

    private List<ReleaseLine> lockAndValidateLines(
            DeliveryGenerateReq req,
            RfqReleaseBatchValidationService.BatchContext context,
            Map<Integer, OrderRfq> lockedOrders) {
        List<Integer> itemIds = req.getItems().stream()
                .map(this::resolveItemId)
                .sorted()
                .toList();
        if (new LinkedHashSet<>(itemIds).size() != itemIds.size()) {
            throw new RuntimeException("同一零件不能在一次议价提单中重复提交");
        }
        Map<Integer, ItemQuoteVersion> lockedItems = new HashMap<>();
        for (Integer itemId : itemIds) {
            ItemQuoteVersion item = itemMapper.selectByIdForUpdate(itemId);
            if (item != null) {
                lockedItems.put(itemId, item);
            }
        }

        List<ReleaseLine> result = new ArrayList<>();
        for (DeliveryGenerateReq.DeliveryItemReq requestLine : req.getItems()) {
            ItemQuoteVersion item = lockedItems.get(resolveItemId(requestLine));
            validationService.validateLockedItem(requestLine, item, context);
            OrderRfq order = item == null ? null : lockedOrders.get(item.getRfqId());
            if (order == null) {
                throw new RuntimeException("提单零件不属于本次询价单批次");
            }
            int acceptedQty = requestLine.getAcceptedQty() == null ? safeInt(item.getQty()) : requestLine.getAcceptedQty();
            if (acceptedQty <= 0) {
                throw new RuntimeException("零件 " + itemLabel(item) + " 的通过数量必须大于 0");
            }
            int remainingQty = Math.max(0, safeInt(item.getQty()) - ticketedQty(item));
            if (acceptedQty > remainingQty) {
                throw new RuntimeException("零件 " + itemLabel(item) + " 的提单数量 " + acceptedQty
                        + " 超过剩余可提数量 " + remainingQty);
            }
            result.add(new ReleaseLine(item, acceptedQty, effectiveFields(item, context)));
        }
        result.sort(Comparator
                .comparing((ReleaseLine line) -> line.item().getRfqId())
                .thenComparing(line -> line.item().getSalesOrderLineNo(), Comparator.nullsLast(Integer::compareTo))
                .thenComparing(line -> line.item().getId()));
        return result;
    }

    private EffectiveOrderFields effectiveFields(
            ItemQuoteVersion item,
            RfqReleaseBatchValidationService.BatchContext context) {
        LocalDate salesOrderDate = item.getSalesOrderDate() == null
                ? context.salesOrderDate() : item.getSalesOrderDate();
        String customerOrderNo = firstText(item.getCustomerOrderNo(), context.customerOrderNo());
        String customerProjectNo = firstText(item.getCustomerProjectNo(), context.customerProjectNo());
        String customerOrderTitle = firstText(item.getCustomerOrderTitle(), context.customerOrderTitle());
        String orderType = firstText(item.getOrderType(), context.orderType());
        String internalProjectNo = firstText(item.getInternalProjectNo(), context.internalProjectNo());
        if (salesOrderDate == null || isBlank(customerOrderNo) || isBlank(orderType) || isBlank(internalProjectNo)) {
            throw new RuntimeException("零件 " + itemLabel(item) + " 的有效订单属性不完整");
        }
        if (!ALLOWED_ORDER_TYPES.contains(orderType)) {
            throw new RuntimeException("零件 " + itemLabel(item) + " 的订单类型不合法：" + orderType);
        }
        return new EffectiveOrderFields(
                salesOrderDate,
                customerOrderNo,
                customerProjectNo,
                customerOrderTitle,
                orderType,
                internalProjectNo);
    }

    private void backfillSourceOrderFields(List<ReleaseLine> releaseLines) {
        for (ReleaseLine line : releaseLines) {
            ItemQuoteVersion item = line.item();
            EffectiveOrderFields fields = line.fields();
            if (item.getSalesOrderDate() == null) {
                item.setSalesOrderDate(fields.salesOrderDate());
            }
            if (isBlank(item.getCustomerOrderNo())) {
                item.setCustomerOrderNo(fields.customerOrderNo());
            }
            if (isBlank(item.getCustomerProjectNo())) {
                item.setCustomerProjectNo(fields.customerProjectNo());
            }
            if (isBlank(item.getCustomerOrderTitle())) {
                item.setCustomerOrderTitle(fields.customerOrderTitle());
            }
            if (isBlank(item.getOrderType())) {
                item.setOrderType(fields.orderType());
            }
            if (isBlank(item.getInternalProjectNo())) {
                item.setInternalProjectNo(fields.internalProjectNo());
            }
        }
    }

    private SalesOrderHeader insertHeader(
            RfqReleaseBatchValidationService.BatchContext context,
            CustomerMaster customer,
            int itemCount,
            LocalDateTime now) {
        SalesOrderNumberService.SalesOrderNumbers numbers = numberService.forRfqRelease(
                context.salesOrderDate(), context.releaseBatchKey());
        SalesOrderHeader header = new SalesOrderHeader();
        header.setSalesOrderNo(numbers.salesOrderNo());
        header.setDocumentNo(numbers.documentNo());
        header.setDocumentTypeCode(context.documentTypeCode());
        header.setCustomerId(customer.getId());
        header.setCustomerCode(context.customerCode());
        header.setCustomerShortName(firstText(context.customerShortName(), customer.getCustomerShortName()));
        header.setCustomerName(context.customerName());
        header.setSourceType(SOURCE_RFQ_RELEASE);
        header.setSourceReleaseBatchKey(context.releaseBatchKey());
        header.setSourceReleaseRequestHash(context.requestHash());
        header.setSalesOrderDate(context.salesOrderDate());
        header.setCustomerOrderNo(context.customerOrderNo());
        header.setCustomerProjectNo(context.customerProjectNo());
        header.setCustomerOrderTitle(context.customerOrderTitle());
        header.setOrderType(context.orderType());
        header.setInternalProjectNo(context.internalProjectNo());
        header.setStatus(HEADER_RELEASED);
        header.setTotalItemCount(itemCount);
        header.setMatchedItemCount(0);
        header.setUnmatchedItemCount(0);
        if (dataScopeService != null) {
            dataScopeService.assignCurrentOwnership(header);
        }
        header.setCreatedAt(now);
        header.setUpdatedAt(now);
        headerMapper.insert(header);
        return header;
    }

    private void insertSourceRfqs(Long salesOrderId, List<Integer> rfqIds, LocalDateTime now) {
        for (Integer rfqId : rfqIds) {
            SalesOrderSourceRfq source = new SalesOrderSourceRfq();
            source.setSalesOrderId(salesOrderId);
            source.setRfqId(rfqId);
            source.setCreatedAt(now);
            sourceRfqMapper.insert(source);
        }
    }

    private OrderDeliveryTicket insertTicket(
            OrderRfq order,
            ReleaseLine line,
            RfqReleaseBatchValidationService.BatchContext context) {
        ItemQuoteVersion item = line.item();
        EffectiveOrderFields fields = line.fields();
        OrderDeliveryTicket ticket = new OrderDeliveryTicket();
        ticket.setRfqId(order.getId());
        ticket.setRfqNo(order.getRfqNo());
        ticket.setCustomerName(order.getCustomerName());
        ticket.setItemId(item.getId());
        ticket.setSourceType(SOURCE_RFQ);
        ticket.setSourceOrderId(order.getId().longValue());
        ticket.setSourceItemId(item.getId().longValue());
        ticket.setOrderType(fields.orderType());
        ticket.setInternalProjectNo(fields.internalProjectNo());
        ticket.setCustomerOrderNo(fields.customerOrderNo());
        ticket.setCustomerProjectNo(fields.customerProjectNo());
        ticket.setProjectNo(isBlank(context.projectNo()) ? resolveProjectNo(order) : context.projectNo());
        ticket.setItemType(resolveItemType(item));
        ticket.setPartNo(item.getPartNo());
        ticket.setDescription(item.getDescription());
        ticket.setQty(line.acceptedQty());
        ticket.setMaterialName(item.getMaterialName());
        ticket.setEngineeringMaterialCode(MaterialDataResolver.materialCode(item));
        ticket.setEngineeringMaterialName(MaterialDataResolver.engineeringMaterialName(item));
        ticket.setEngineeringMaterialTypeCode(MaterialDataResolver.materialTypeCode(item));
        ticket.setEngineeringMaterialTypeName(MaterialDataResolver.materialTypeName(item));
        ticket.setEngineeringMaterialCategory(MaterialDataResolver.materialCategory(item));
        ticket.setMaterialShape(MaterialDataResolver.materialShape(item));
        ticket.setSurfaceTreatment(item.getSurfaceTreatment());
        ticket.setVersion(item.getVersion());
        ticket.setStartDate(fields.salesOrderDate());
        ticket.setDeliveryDate(leadTimeService.resolveDeliveryDate(
                fields.salesOrderDate(), leadTimeService.effectiveDeliveryLeadTime(item)));
        ticket.setRemark(item.getRemark());
        ticket.setArrangeType(context.arrangeType());
        ticket.setDrawingUrl(item.getDrawingUrl());
        ticket.setProcessRoute(processRouteSnapshotService.copySnapshot(item.getProcessRoute()));
        ticket.setMaterialData(deepCopySnapshot(item.getMaterialData()));
        ticket.setMaterialGrossWeight(item.getMaterialGrossWeight());
        ticket.setPartNetWeight(item.getPartNetWeight());
        initializeTicketMaterial(ticket, item);
        ticketMapper.insert(ticket);
        return ticket;
    }

    private SalesOrderItem insertSalesItem(
            Long salesOrderId,
            int lineNo,
            ReleaseLine line,
            Integer ticketId,
            Set<Integer> selectedRelationParents,
            LocalDateTime now) {
        ItemQuoteVersion source = line.item();
        EffectiveOrderFields fields = line.fields();
        SalesOrderItem item = new SalesOrderItem();
        item.setSalesOrderId(salesOrderId);
        item.setSourceType(SOURCE_RFQ);
        item.setSourceOrderId(source.getRfqId().longValue());
        item.setSourceItemId(source.getId().longValue());
        item.setSourceTicketId(ticketId);
        item.setSalesOrderDate(fields.salesOrderDate());
        item.setLineNo(lineNo);
        item.setPartNo(source.getPartNo());
        item.setDescription(source.getDescription());
        item.setQty(line.acceptedQty());
        item.setVersion(source.getVersion());
        item.setOrderType(fields.orderType());
        item.setCustomerOrderNo(fields.customerOrderNo());
        item.setCustomerProjectNo(fields.customerProjectNo());
        item.setCustomerOrderTitle(fields.customerOrderTitle());
        item.setInternalProjectNo(fields.internalProjectNo());
        copyConfirmedMaterial(source, item);
        item.setCustomerDeliveryDate(source.getCustomerDeliveryDate());
        item.setInternalDeliveryDate(source.getInternalDeliveryDate());
        item.setMaterialName(source.getMaterialName());
        item.setEngineeringMaterialCode(MaterialDataResolver.materialCode(source));
        item.setEngineeringMaterialName(MaterialDataResolver.engineeringMaterialName(source));
        item.setEngineeringMaterialTypeCode(MaterialDataResolver.materialTypeCode(source));
        item.setEngineeringMaterialTypeName(MaterialDataResolver.materialTypeName(source));
        item.setEngineeringMaterialCategory(MaterialDataResolver.materialCategory(source));
        item.setMaterialShape(MaterialDataResolver.materialShape(source));
        item.setSurfaceTreatment(source.getSurfaceTreatment());
        item.setTaxIncludedUnitPrice(decimal(source.getTaxIncludedUnitPrice()));
        item.setUntaxedUnitPrice(decimal(source.getUntaxedUnitPrice()));
        item.setTaxIncludedTotalPrice(total(source.getTaxIncludedUnitPrice(), source.getTaxIncludedTotalPrice(), line));
        item.setUntaxedTotalPrice(total(source.getUntaxedUnitPrice(), source.getUntaxedTotalPrice(), line));
        item.setShippedQty(0);
        item.setUnshippedQty(line.acceptedQty());
        item.setReleasedQty(line.acceptedQty());
        item.setRemainingQty(0);
        item.setDrawingUrl(source.getDrawingUrl());
        item.setHasDrawing(!isBlank(source.getDrawingUrl()));
        item.setMaterialGrossWeight(source.getMaterialGrossWeight());
        item.setPartNetWeight(source.getPartNetWeight());
        item.setQuoteMode(source.getQuoteMode());
        item.setAssemblyFee(decimal(source.getAssemblyFee()));
        item.setBaseCost(decimal(source.getBaseCost()));
        item.setItemType(resolveItemType(source));
        item.setAssemblyRelationStatus(resolveAssemblyStatus(source, selectedRelationParents));
        item.setMaterialData(deepCopySnapshot(source.getMaterialData()));
        item.setProcessRoute(processRouteSnapshotService.copySnapshot(source.getProcessRoute()));
        item.setFeeData(deepCopySnapshot(source.getFeeData()));
        item.setRemark(source.getRemark());
        item.setStatus(QuoteStatusWorkflow.RELEASED);
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        salesItemMapper.insert(item);
        return item;
    }

    private void copyConfirmedMaterial(ItemQuoteVersion source, SalesOrderItem target) {
        if (!isBlank(source.getMaterialNo()) && MATERIAL_CONFIRMED.equals(trim(source.getMaterialNoStatus()))) {
            target.setMaterialNo(source.getMaterialNo().trim());
            target.setMaterialNoStatus(MATERIAL_CONFIRMED);
            target.setMaterialNoSource(source.getMaterialNoSource());
            target.setMaterialNoSourceTicketId(source.getMaterialNoSourceTicketId());
            target.setMaterialNoSyncedAt(source.getMaterialNoSyncedAt());
            target.setMaterialNoMessage(source.getMaterialNoMessage());
            return;
        }
        target.setMaterialNo(null);
        target.setMaterialNoStatus(MATERIAL_UNCONFIRMED);
    }

    private void initializeTicketMaterial(OrderDeliveryTicket ticket, ItemQuoteVersion source) {
        if (!isBlank(source.getMaterialNo()) && MATERIAL_CONFIRMED.equals(trim(source.getMaterialNoStatus()))) {
            ticket.setMaterialNo(source.getMaterialNo().trim());
            ticket.setMesSyncStatus(MES_SUCCESS);
            ticket.setMesSyncMessage(CONFIRMED_MATERIAL_MESSAGE);
            ticket.setMesSyncedAt(source.getMaterialNoSyncedAt());
            return;
        }
        ticket.setMaterialNo(null);
        ticket.setMesSyncStatus(MES_PENDING);
    }

    private List<ItemAssemblyRelation> selectedAssemblyRelations(List<ReleaseLine> releaseLines) {
        Set<Integer> selectedIds = releaseLines.stream()
                .map(line -> line.item().getId())
                .collect(Collectors.toSet());
        if (selectedIds.isEmpty()) {
            return List.of();
        }
        return sourceAssemblyMapper.selectList(new QueryWrapper<ItemAssemblyRelation>()
                        .in("parent_item_id", selectedIds)
                        .orderByAsc("parent_item_id")
                        .orderByAsc("sort_no")
                        .orderByAsc("id"))
                .stream()
                .filter(relation -> selectedIds.contains(relation.getChildItemId()))
                .toList();
    }

    private void insertAssemblyRelations(
            Long salesOrderId,
            List<ItemAssemblyRelation> sourceRelations,
            Map<Integer, SalesOrderItem> salesItemBySourceId,
            LocalDateTime now) {
        for (ItemAssemblyRelation source : sourceRelations) {
            SalesOrderItem parent = salesItemBySourceId.get(source.getParentItemId());
            SalesOrderItem child = salesItemBySourceId.get(source.getChildItemId());
            if (parent == null || child == null) {
                throw new RuntimeException("组合件父子关系映射不完整");
            }
            SalesOrderItemAssemblyRelation target = new SalesOrderItemAssemblyRelation();
            target.setSalesOrderId(salesOrderId);
            target.setParentItemId(parent.getId());
            target.setChildItemId(child.getId());
            target.setQtyPerParent(source.getChildQtyPerParent() == null
                    ? BigDecimal.ONE : BigDecimal.valueOf(source.getChildQtyPerParent()));
            target.setSortNo(source.getSortNo());
            target.setSourceParentItemId(source.getParentItemId());
            target.setSourceChildItemId(source.getChildItemId());
            target.setCreatedAt(now);
            target.setUpdatedAt(now);
            salesAssemblyMapper.insert(target);
        }
    }

    private void refreshItemTicketStatus(ItemQuoteVersion item) {
        int generatedQty = ticketedQty(item);
        if (generatedQty <= 0) {
            item.setStatus(item.getTaxIncludedUnitPrice() == null
                    ? QuoteStatusWorkflow.UNPRICED : QuoteStatusWorkflow.QUOTED);
        } else if (generatedQty < safeInt(item.getQty())) {
            item.setStatus(QuoteStatusWorkflow.QUOTED);
        } else {
            item.setStatus(QuoteStatusWorkflow.ITEM_RELEASED);
        }
        itemMapper.updateById(item);
    }

    private void refreshOrderTicketStatus(Integer rfqId) {
        OrderRfq order = orderMapper.selectById(rfqId);
        if (order == null) {
            return;
        }
        List<ItemQuoteVersion> items = itemMapper.selectList(new QueryWrapper<ItemQuoteVersion>()
                .eq("rfq_id", rfqId)
                .orderByAsc("id"));
        List<OrderDeliveryTicket> tickets = ticketMapper.selectList(new QueryWrapper<OrderDeliveryTicket>()
                .eq("rfq_id", rfqId));
        if (tickets.isEmpty()) {
            order.setStatus(resolveQuoteStatus(items));
            orderMapper.updateById(order);
            return;
        }
        Map<Integer, Integer> qtyByItem = new HashMap<>();
        for (OrderDeliveryTicket ticket : tickets) {
            if (ticket.getItemId() != null) {
                qtyByItem.merge(ticket.getItemId(), safeInt(ticket.getQty()), Integer::sum);
            }
        }
        List<ItemQuoteVersion> quotedItems = items.stream()
                .filter(item -> item.getTaxIncludedUnitPrice() != null)
                .toList();
        boolean allQuotedItemsTicketed = !quotedItems.isEmpty() && quotedItems.stream()
                .allMatch(item -> qtyByItem.getOrDefault(item.getId(), 0) >= safeInt(item.getQty()));
        order.setStatus(allQuotedItemsTicketed ? HEADER_RELEASED : "部分已提单");
        orderMapper.updateById(order);
    }

    private String resolveQuoteStatus(List<ItemQuoteVersion> items) {
        if (items == null || items.isEmpty()) {
            return QuoteStatusWorkflow.UNPRICED;
        }
        return items.stream().allMatch(item -> item.getTaxIncludedUnitPrice() != null)
                ? QuoteStatusWorkflow.QUOTED : QuoteStatusWorkflow.UNPRICED;
    }

    private int ticketedQty(ItemQuoteVersion item) {
        return ticketMapper.selectList(new QueryWrapper<OrderDeliveryTicket>()
                        .eq("rfq_id", item.getRfqId())
                        .eq("item_id", item.getId()))
                .stream()
                .mapToInt(ticket -> safeInt(ticket.getQty()))
                .sum();
    }

    private DeliveryBatchResultDTO buildResult(
            SalesOrderHeader header,
            List<Integer> sourceRfqIds,
            List<OrderDeliveryTicket> tickets) {
        DeliveryBatchResultDTO result = new DeliveryBatchResultDTO();
        result.setSuccess(true);
        result.setSalesOrderId(header.getId());
        result.setSalesOrderNo(header.getSalesOrderNo());
        result.setDocumentNo(header.getDocumentNo());
        result.setSourceRfqIds(sourceRfqIds);
        result.setTicketCount(tickets.size());
        int confirmed = (int) tickets.stream()
                .filter(ticket -> MES_SUCCESS.equals(ticket.getMesSyncStatus()))
                .count();
        result.setConfirmedMaterialCount(confirmed);
        result.setPendingMaterialCount(tickets.size() - confirmed);
        result.setTicketIds(tickets.stream().map(OrderDeliveryTicket::getId).toList());
        result.setMessage("已生成销售订单 " + header.getSalesOrderNo() + "，并生成 " + tickets.size() + " 条提单行。");
        return result;
    }

    private DeliveryBatchResultDTO buildExistingResult(
            SalesOrderHeader header,
            RfqReleaseBatchValidationService.BatchContext context) {
        List<Integer> sourceRfqIds = sourceRfqMapper.selectList(new QueryWrapper<SalesOrderSourceRfq>()
                        .eq("sales_order_id", header.getId())
                        .orderByAsc("rfq_id"))
                .stream()
                .map(SalesOrderSourceRfq::getRfqId)
                .toList();
        List<Integer> ticketIds = salesItemMapper.selectList(new QueryWrapper<SalesOrderItem>()
                        .eq("sales_order_id", header.getId())
                        .isNotNull("source_ticket_id")
                        .orderByAsc("line_no")
                        .orderByAsc("id"))
                .stream()
                .map(SalesOrderItem::getSourceTicketId)
                .toList();
        List<OrderDeliveryTicket> tickets = ticketIds.isEmpty() ? List.of() : ticketMapper.selectBatchIds(ticketIds);
        Map<Integer, OrderDeliveryTicket> ticketById = tickets.stream()
                .collect(Collectors.toMap(OrderDeliveryTicket::getId, Function.identity()));
        List<OrderDeliveryTicket> orderedTickets = ticketIds.stream()
                .map(ticketById::get)
                .filter(Objects::nonNull)
                .toList();
        if (orderedTickets.size() != ticketIds.size()) {
            throw new RuntimeException("提单批次历史结果不完整");
        }
        return buildResult(header, sourceRfqIds.isEmpty() ? context.rfqIds() : sourceRfqIds, orderedTickets);
    }

    private void audit(
            SalesOrderHeader header,
            RfqReleaseBatchValidationService.BatchContext context,
            DeliveryBatchResultDTO result,
            boolean idempotentReplay,
            long startedAt) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("salesOrderId", result.getSalesOrderId());
        summary.put("salesOrderNo", result.getSalesOrderNo());
        summary.put("rfqCount", result.getSourceRfqIds().size());
        summary.put("itemCount", result.getTicketCount());
        summary.put("ticketCount", result.getTicketCount());
        summary.put("confirmedMaterialCount", result.getConfirmedMaterialCount());
        summary.put("pendingMaterialCount", result.getPendingMaterialCount());
        summary.put("releaseBatchKeySummary", context.releaseBatchKeySummary());
        summary.put("idempotentReplay", idempotentReplay);
        summary.put("durationMs", System.currentTimeMillis() - startedAt);
        auditService.record("DELIVERY_TICKET_GENERATE", "sales_order_header", header.getId(), null, summary);
    }

    private int resolveItemId(DeliveryGenerateReq.DeliveryItemReq line) {
        if (line == null) {
            throw new RuntimeException("提单零件不能为空");
        }
        Long sourceItemId = line.getSourceItemId();
        if (sourceItemId != null) {
            if (sourceItemId > Integer.MAX_VALUE || sourceItemId < Integer.MIN_VALUE) {
                throw new RuntimeException("提单零件 ID 无效");
            }
            return sourceItemId.intValue();
        }
        if (line.getItemId() == null) {
            throw new RuntimeException("提单零件 ID 不能为空");
        }
        return line.getItemId();
    }

    private BigDecimal total(Double unitPrice, Double sourceTotal, ReleaseLine line) {
        if (unitPrice != null) {
            return BigDecimal.valueOf(unitPrice).multiply(BigDecimal.valueOf(line.acceptedQty()));
        }
        if (line.acceptedQty() == safeInt(line.item().getQty())) {
            return decimal(sourceTotal);
        }
        return null;
    }

    private BigDecimal decimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    private String resolveAssemblyStatus(ItemQuoteVersion item, Set<Integer> relationParents) {
        if (!"ASSEMBLY".equalsIgnoreCase(item.getQuoteMode())) {
            return RELATION_NOT_APPLICABLE;
        }
        return relationParents.contains(item.getId()) ? RELATION_SPECIFIED : RELATION_UNSPECIFIED;
    }

    private String resolveItemType(ItemQuoteVersion item) {
        return "ASSEMBLY".equalsIgnoreCase(item.getQuoteMode()) ? ITEM_TYPE_ASSEMBLY : ITEM_TYPE_NORMAL;
    }

    private String resolveProjectNo(OrderRfq order) {
        if (!isBlank(order.getRfqNo())) {
            return order.getRfqNo();
        }
        return trim(order.getProjectName());
    }

    private String itemLabel(ItemQuoteVersion item) {
        return item.getPartNo() + " / " + trim(item.getVersion());
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String firstText(String primary, String fallback) {
        return isBlank(primary) ? trimToNull(fallback) : primary.trim();
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String trimToNull(String value) {
        String normalized = trim(value);
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private Object deepCopySnapshot(Object value) {
        if (jsonSnapshotCopyService == null) {
            jsonSnapshotCopyService = new JsonSnapshotCopyService();
        }
        return jsonSnapshotCopyService.deepCopySnapshot(value);
    }

    private record ReleaseLine(
            ItemQuoteVersion item,
            int acceptedQty,
            EffectiveOrderFields fields) {
    }

    private record EffectiveOrderFields(
            LocalDate salesOrderDate,
            String customerOrderNo,
            String customerProjectNo,
            String customerOrderTitle,
            String orderType,
            String internalProjectNo) {
    }
}
