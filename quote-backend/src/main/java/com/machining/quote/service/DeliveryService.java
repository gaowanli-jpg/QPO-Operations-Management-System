package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.DeliveryBatchResultDTO;
import com.machining.quote.dto.DeliveryGenerateReq;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.ProcessSheetDTO;
import com.machining.quote.dto.ReleaseSourceType;
import com.machining.quote.dto.RfqReleaseAppendSalesOrderReq;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.util.MaterialDataResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class DeliveryService {
    private static final Logger log = LoggerFactory.getLogger(DeliveryService.class);
    private static final String MATERIAL_CONFIRMED = "已确认";
    private static final String MES_SUCCESS = "SUCCESS";
    private static final String MES_PENDING = "PENDING";
    private static final String CONFIRMED_MATERIAL_REUSE_MESSAGE = "前置匹配复用已确认 MES 物料号";
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> ALLOWED_ORDER_TYPES = Set.of("预测订单", "备货订单", "正式订单");

    @Autowired
    private OrderRfqMapper orderRfqMapper;
    @Autowired
    private OrderDeliveryTicketMapper ticketMapper;
    @Autowired
    private AuditService auditService;
    @Autowired
    private LeadTimeService leadTimeService;
    @Autowired(required = false)
    private DataScopeService dataScopeService;
    @Autowired(required = false)
    private SalesOrderHeaderMapper salesOrderHeaderMapper;
    @Autowired(required = false)
    private SalesOrderItemMapper salesOrderItemMapper;
    @Autowired
    private RfqReleaseSalesOrderService rfqReleaseSalesOrderService;
    @Autowired
    private RfqReleaseAppendSalesOrderService rfqReleaseAppendSalesOrderService;
    @Autowired(required = false)
    @Lazy
    private DeliveryAutoMesMaterialSyncService autoMesMaterialSyncService;
    @Autowired(required = false)
    private ProcessRouteSnapshotService processRouteSnapshotService = new ProcessRouteSnapshotService();
    @Autowired(required = false)
    private JsonSnapshotCopyService jsonSnapshotCopyService = new JsonSnapshotCopyService();

    @Transactional
    public List<OrderDeliveryTicket> generateRfqTickets(DeliveryGenerateReq req) {
        requireRfqOnlyRequest(req);
        return rfqReleaseSalesOrderService.loadTickets(generateRfqReleaseAndAutoSubmitMes(req));
    }

    @Transactional
    public DeliveryBatchResultDTO generateRfqReleaseBatch(DeliveryGenerateReq req) {
        return generateRfqReleaseAndAutoSubmitMes(req);
    }

    @Transactional
    public DeliveryBatchResultDTO appendRfqReleaseToSalesOrder(RfqReleaseAppendSalesOrderReq req) {
        return rfqReleaseAppendSalesOrderService.appendToSalesOrder(req);
    }

    @Transactional
    public List<OrderDeliveryTicket> generateTickets(DeliveryGenerateReq req) {
        List<Integer> rfqIds = req == null || req.getRfqIds() == null ? List.of() : req.getRfqIds();
        int selectedItemCount = req == null || req.getItems() == null ? 0 : req.getItems().size();
        long startedAt = System.currentTimeMillis();
        log.info("Delivery ticket generation started rfqIds={} selectedItemCount={}", rfqIds, selectedItemCount);
        try {
            if (req == null) {
                throw new RuntimeException("提单参数不能为空");
            }
            if (req.getItems() == null || req.getItems().isEmpty()) {
                throw new RuntimeException("请至少选择一个通过议价的零件");
            }
            if (isSalesOrderRequest(req)) {
                return generateSalesOrderTickets(req, startedAt);
            }
            normalizeLegacyRfqRequest(req);
            return rfqReleaseSalesOrderService.loadTickets(generateRfqReleaseAndAutoSubmitMes(req));
        } catch (RuntimeException e) {
            if (isBusinessFailure(e)) {
                log.warn("Delivery ticket generation failed rfqIds={} selectedItemCount={} durationMs={} reason={}",
                        rfqIds, selectedItemCount, System.currentTimeMillis() - startedAt, e.getMessage());
            } else {
                log.error("Delivery ticket generation failed rfqIds={} selectedItemCount={} durationMs={}",
                        rfqIds, selectedItemCount, System.currentTimeMillis() - startedAt, e);
            }
            throw e;
        }
    }

    private DeliveryBatchResultDTO generateRfqReleaseAndAutoSubmitMes(DeliveryGenerateReq req) {
        DeliveryBatchResultDTO result = rfqReleaseSalesOrderService.generate(req);
        submitAutoMesForRfqRelease(result);
        return result;
    }

    private void submitAutoMesForRfqRelease(DeliveryBatchResultDTO result) {
        if (autoMesMaterialSyncService == null
                || result == null
                || !result.isSuccess()
                || result.isIdempotentReplay()
                || result.getTicketIds() == null
                || result.getTicketIds().isEmpty()) {
            return;
        }
        try {
            autoMesMaterialSyncService.submitAfterCommit(
                    ReleaseSourceType.RFQ,
                    result.getSalesOrderId(),
                    result.getTicketIds(),
                    "RFQ_RELEASE");
        } catch (RuntimeException ex) {
            log.warn("RFQ release auto MES material sync submit failed salesOrderId={} ticketCount={} reason={}",
                    result.getSalesOrderId(), result.getTicketIds().size(), ex.getMessage());
        }
    }

    private void normalizeLegacyRfqRequest(DeliveryGenerateReq req) {
        if (req.getRfqIds() == null || req.getRfqIds().size() != 1) {
            return;
        }
        Integer rfqId = req.getRfqIds().get(0);
        if (req.getSalesOrderDate() == null) {
            req.setSalesOrderDate(LocalDate.now());
        }
        if (isBlank(req.getReleaseBatchKey())) {
            req.setReleaseBatchKey("legacy-rfq-" + rfqId + "-" + UUID.randomUUID());
        }
        for (DeliveryGenerateReq.DeliveryItemReq line : req.getItems()) {
            if (line == null) {
                continue;
            }
            if (line.getSourceType() == null) {
                line.setSourceType(ReleaseSourceType.RFQ);
            }
            if (line.getSourceOrderId() == null) {
                line.setSourceOrderId(rfqId.longValue());
            }
            if (line.getSourceItemId() == null && line.getItemId() != null) {
                line.setSourceItemId(line.getItemId().longValue());
            }
        }
    }

    public List<OrderDeliveryTicket> listTickets(Integer rfqId) {
        assertOrderDataScope(rfqId);
        QueryWrapper<OrderDeliveryTicket> qw = new QueryWrapper<>();
        if (rfqId != null) {
            qw.eq("rfq_id", rfqId);
        }
        qw.orderByAsc("id");
        return ticketMapper.selectList(qw);
    }

    public List<OrderDeliveryTicket> listTickets(ReleaseSourceType sourceType, Long sourceOrderId) {
        assertSourceOrderDataScope(sourceType, sourceOrderId);
        QueryWrapper<OrderDeliveryTicket> qw = ticketQuery(sourceType, sourceOrderId)
                .orderByAsc("id");
        return ticketMapper.selectList(qw);
    }

    public PageResult<OrderDeliveryTicket> pageTickets(Integer rfqId, Integer page, Integer pageSize) {
        assertOrderDataScope(rfqId);
        int safePage = safePage(page);
        int safePageSize = safePageSize(pageSize);
        long offset = (long) (safePage - 1) * safePageSize;
        QueryWrapper<OrderDeliveryTicket> countQuery = ticketQuery(rfqId);
        Long total = ticketMapper.selectCount(countQuery);
        QueryWrapper<OrderDeliveryTicket> pageQuery = ticketQuery(rfqId)
                .orderByAsc("id")
                .last("LIMIT " + safePageSize + " OFFSET " + offset);
        return PageResult.of(ticketMapper.selectList(pageQuery), total == null ? 0L : total, safePage, safePageSize);
    }

    public PageResult<OrderDeliveryTicket> pageTickets(ReleaseSourceType sourceType, Long sourceOrderId, Integer page, Integer pageSize) {
        assertSourceOrderDataScope(sourceType, sourceOrderId);
        int safePage = safePage(page);
        int safePageSize = safePageSize(pageSize);
        long offset = (long) (safePage - 1) * safePageSize;
        QueryWrapper<OrderDeliveryTicket> countQuery = ticketQuery(sourceType, sourceOrderId);
        Long total = ticketMapper.selectCount(countQuery);
        QueryWrapper<OrderDeliveryTicket> pageQuery = ticketQuery(sourceType, sourceOrderId)
                .orderByAsc("id")
                .last("LIMIT " + safePageSize + " OFFSET " + offset);
        return PageResult.of(ticketMapper.selectList(pageQuery), total == null ? 0L : total, safePage, safePageSize);
    }

    public List<ProcessSheetDTO> listProcessSheets(Integer rfqId) {
        assertOrderDataScope(rfqId);
        QueryWrapper<OrderDeliveryTicket> qw = new QueryWrapper<>();
        if (rfqId != null) {
            qw.eq("rfq_id", rfqId);
        }
        qw.orderByAsc("id");
        return ticketMapper.selectList(qw).stream()
                .map(this::buildProcessSheet)
                .toList();
    }

    public List<ProcessSheetDTO> listProcessSheets(ReleaseSourceType sourceType, Long sourceOrderId) {
        return listTickets(sourceType, sourceOrderId).stream()
                .map(this::buildProcessSheet)
                .toList();
    }

    @Transactional
    public List<OrderDeliveryTicket> generateImportedSalesOrderTickets(Long salesOrderId) {
        long startedAt = System.currentTimeMillis();
        if (salesOrderHeaderMapper == null || salesOrderItemMapper == null) {
            throw new RuntimeException("销售订单提单服务不可用");
        }
        if (salesOrderId == null) {
            throw new RuntimeException("销售订单来源 ID 不能为空");
        }
        SalesOrderHeader header = salesOrderHeaderMapper.selectByIdForUpdate(salesOrderId);
        if (header == null) {
            throw new RuntimeException("销售订单不存在");
        }
        assertSalesOrderDataScope(header);
        List<SalesOrderDemandLine> demands = listSalesOrderItems(header.getId()).stream()
                .filter(item -> item != null && item.getId() != null)
                .map(item -> new SalesOrderDemandLine(item, salesOrderRemainingQty(item)))
                .filter(demand -> demand.qty() > 0)
                .toList();
        if (demands.isEmpty()) {
            refreshSalesOrderHeaderStatus(header);
            log.info("Sales order import auto delivery skipped sourceOrderId={} reason=no_remaining_qty durationMs={}",
                    salesOrderId, System.currentTimeMillis() - startedAt);
            return List.of();
        }
        if (!QuoteStatusWorkflow.canGenerateDeliveryTicket(header.getStatus())) {
            throw new RuntimeException("只有已报价或部分已提单的销售订单可以自动生成提单");
        }

        String projectNo = resolveSalesOrderProjectNo(header);
        List<OrderDeliveryTicket> createdTickets = new ArrayList<>();
        for (SalesOrderDemandLine demand : demands) {
            SalesOrderItem item = demand.item();
            String orderType = firstText(item == null ? null : item.getOrderType(), firstText(header.getOrderType(), "正式订单"));
            LocalDate startDate = item != null && item.getSalesOrderDate() != null
                    ? item.getSalesOrderDate()
                    : (header.getSalesOrderDate() == null ? LocalDate.now() : header.getSalesOrderDate());
            String internalProjectNo = firstText(item == null ? null : item.getInternalProjectNo(), header.getInternalProjectNo());
            if (isBlank(internalProjectNo)) {
                throw new RuntimeException("内部项目号不能为空");
            }
            String customerOrderNo = firstText(item == null ? null : item.getCustomerOrderNo(), header.getCustomerOrderNo());
            if (isBlank(customerOrderNo)) {
                throw new RuntimeException("客户订单号不能为空");
            }
            String customerProjectNo = firstText(item == null ? null : item.getCustomerProjectNo(), header.getCustomerProjectNo());
            OrderDeliveryTicket ticket = insertSalesOrderTicket(
                    header,
                    item,
                    orderType,
                    internalProjectNo,
                    customerOrderNo,
                    customerProjectNo,
                    projectNo,
                    startDate,
                    demand.qty(),
                    "计划");
            createdTickets.add(ticket);
            applySalesOrderRelease(demand.item(), demand.qty());
        }
        refreshSalesOrderHeaderStatus(header);
        recordSalesOrderImportAutoDeliveryAudit(header, createdTickets, startedAt);
        log.info("Sales order import auto delivery completed sourceOrderId={} ticketCount={} processRouteCount={} durationMs={}",
                salesOrderId, createdTickets.size(), countProcessRoutes(createdTickets), System.currentTimeMillis() - startedAt);
        return createdTickets;
    }

    @Transactional
    public List<OrderDeliveryTicket> generateImportedSalesOrderTicketsForItems(
            Long salesOrderId,
            Collection<Long> salesOrderItemIds,
            String triggerScene) {
        long startedAt = System.currentTimeMillis();
        if (salesOrderHeaderMapper == null || salesOrderItemMapper == null) {
            throw new RuntimeException("销售订单提单服务不可用");
        }
        if (salesOrderId == null) {
            throw new RuntimeException("销售订单来源 ID 不能为空");
        }
        LinkedHashSet<Long> requestedItemIds = normalizeSalesOrderItemIds(salesOrderItemIds);
        if (requestedItemIds.isEmpty()) {
            return List.of();
        }
        SalesOrderHeader header = salesOrderHeaderMapper.selectByIdForUpdate(salesOrderId);
        if (header == null) {
            throw new RuntimeException("销售订单不存在");
        }
        assertSalesOrderDataScope(header);
        if (!QuoteStatusWorkflow.canGenerateDeliveryTicket(header.getStatus())) {
            throw new RuntimeException("只有已报价或部分已提单的销售订单可以自动生成提单");
        }

        Map<Long, SalesOrderItem> itemById = new LinkedHashMap<>();
        for (Long itemId : requestedItemIds.stream().sorted().toList()) {
            SalesOrderItem item = salesOrderItemMapper.selectByIdForUpdate(itemId);
            if (item != null) {
                itemById.put(itemId, item);
            }
        }
        List<SalesOrderDemandLine> demands = new ArrayList<>();
        for (Long itemId : requestedItemIds) {
            SalesOrderItem item = itemById.get(itemId);
            if (item == null) {
                throw new RuntimeException("销售订单零件不存在，ID=" + itemId);
            }
            if (!Objects.equals(salesOrderId, item.getSalesOrderId())) {
                throw new RuntimeException("销售订单零件 " + salesOrderItemLabel(item) + " 不属于当前销售订单");
            }
            int remainingQty = salesOrderRemainingQty(item);
            if (remainingQty > 0) {
                demands.add(new SalesOrderDemandLine(item, remainingQty));
            }
        }
        if (demands.isEmpty()) {
            refreshSalesOrderHeaderStatus(header);
            log.info("Sales order scoped auto delivery skipped sourceOrderId={} requestedItemCount={} reason=no_remaining_qty durationMs={}",
                    salesOrderId, requestedItemIds.size(), System.currentTimeMillis() - startedAt);
            return List.of();
        }

        String projectNo = resolveSalesOrderProjectNo(header);
        List<OrderDeliveryTicket> createdTickets = new ArrayList<>();
        for (SalesOrderDemandLine demand : demands) {
            SalesOrderItem item = demand.item();
            String orderType = firstText(item == null ? null : item.getOrderType(), firstText(header.getOrderType(), "正式订单"));
            LocalDate startDate = item != null && item.getSalesOrderDate() != null
                    ? item.getSalesOrderDate()
                    : (header.getSalesOrderDate() == null ? LocalDate.now() : header.getSalesOrderDate());
            String internalProjectNo = firstText(item == null ? null : item.getInternalProjectNo(), header.getInternalProjectNo());
            if (isBlank(internalProjectNo)) {
                throw new RuntimeException("内部项目号不能为空");
            }
            String customerOrderNo = firstText(item == null ? null : item.getCustomerOrderNo(), header.getCustomerOrderNo());
            if (isBlank(customerOrderNo)) {
                throw new RuntimeException("客户订单号不能为空");
            }
            String customerProjectNo = firstText(item == null ? null : item.getCustomerProjectNo(), header.getCustomerProjectNo());
            OrderDeliveryTicket ticket = insertSalesOrderTicket(
                    header,
                    item,
                    orderType,
                    internalProjectNo,
                    customerOrderNo,
                    customerProjectNo,
                    projectNo,
                    startDate,
                    demand.qty(),
                    "计划");
            createdTickets.add(ticket);
            applySalesOrderRelease(demand.item(), demand.qty());
        }
        refreshSalesOrderHeaderStatus(header);
        recordSalesOrderScopedAutoDeliveryAudit(header, createdTickets, triggerScene, startedAt);
        log.info("Sales order scoped auto delivery completed sourceOrderId={} requestedItemCount={} ticketCount={} processRouteCount={} triggerScene={} durationMs={}",
                salesOrderId, requestedItemIds.size(), createdTickets.size(), countProcessRoutes(createdTickets),
                safeTriggerScene(triggerScene), System.currentTimeMillis() - startedAt);
        return createdTickets;
    }

    public PageResult<ProcessSheetDTO> pageProcessSheets(Integer rfqId, Integer page, Integer pageSize) {
        PageResult<OrderDeliveryTicket> ticketPage = pageTickets(rfqId, page, pageSize);
        List<ProcessSheetDTO> records = ticketPage.getRecords().stream()
                .map(this::buildProcessSheet)
                .toList();
        return PageResult.of(records, ticketPage.getTotal(), ticketPage.getPage(), ticketPage.getPageSize());
    }

    public PageResult<ProcessSheetDTO> pageProcessSheets(ReleaseSourceType sourceType, Long sourceOrderId, Integer page, Integer pageSize) {
        PageResult<OrderDeliveryTicket> ticketPage = pageTickets(sourceType, sourceOrderId, page, pageSize);
        List<ProcessSheetDTO> records = ticketPage.getRecords().stream()
                .map(this::buildProcessSheet)
                .toList();
        return PageResult.of(records, ticketPage.getTotal(), ticketPage.getPage(), ticketPage.getPageSize());
    }

    private QueryWrapper<OrderDeliveryTicket> ticketQuery(Integer rfqId) {
        QueryWrapper<OrderDeliveryTicket> qw = new QueryWrapper<>();
        if (rfqId != null) {
            qw.eq("rfq_id", rfqId);
        }
        return qw;
    }

    private QueryWrapper<OrderDeliveryTicket> ticketQuery(ReleaseSourceType sourceType, Long sourceOrderId) {
        QueryWrapper<OrderDeliveryTicket> qw = new QueryWrapper<>();
        if (sourceOrderId == null) {
            return qw.apply("1 = 0");
        }
        ReleaseSourceType effectiveType = sourceType == null ? ReleaseSourceType.RFQ : sourceType;
        if (effectiveType == ReleaseSourceType.SALES_ORDER) {
            return qw.eq("source_type", ReleaseSourceType.SALES_ORDER.name())
                    .eq("source_order_id", sourceOrderId);
        }
        Integer rfqId = sourceOrderId > Integer.MAX_VALUE || sourceOrderId < Integer.MIN_VALUE
                ? null
                : sourceOrderId.intValue();
        if (rfqId == null) {
            return qw.apply("1 = 0");
        }
        return qw.and(wrapper -> wrapper
                .eq("source_type", ReleaseSourceType.RFQ.name())
                .eq("source_order_id", sourceOrderId)
                .or()
                .eq("rfq_id", rfqId));
    }

    private void assertOrderDataScope(Integer rfqId) {
        if (rfqId != null && dataScopeService != null) {
            dataScopeService.assertCanAccessOrder(rfqId, orderRfqMapper);
        }
    }

    private void assertSourceOrderDataScope(ReleaseSourceType sourceType, Long sourceOrderId) {
        if (sourceType == ReleaseSourceType.SALES_ORDER || sourceOrderId == null) {
            return;
        }
        if (sourceOrderId <= Integer.MAX_VALUE && sourceOrderId >= Integer.MIN_VALUE) {
            assertOrderDataScope(sourceOrderId.intValue());
        }
    }

    private String normalizeOrderType(String orderType) {
        String normalized = orderType == null ? "" : orderType.trim();
        if (normalized.isEmpty()) {
            throw new RuntimeException("请选择订单类型");
        }
        if (!ALLOWED_ORDER_TYPES.contains(normalized)) {
            throw new RuntimeException("订单类型不合法：" + normalized);
        }
        return normalized;
    }
    private boolean isSalesOrderRequest(DeliveryGenerateReq req) {
        if (req == null || req.getItems() == null) {
            return false;
        }
        return req.getItems().stream()
                .anyMatch(line -> ReleaseSourceType.SALES_ORDER.equals(line.getSourceType()));
    }

    private void requireRfqOnlyRequest(DeliveryGenerateReq req) {
        if (req != null && req.getItems() != null && req.getItems().stream()
                .anyMatch(line -> line != null && ReleaseSourceType.SALES_ORDER.equals(line.getSourceType()))) {
            throw new RuntimeException("议价提单只支持 RFQ 询价订单，销售订单由导入流程自动生成提单");
        }
    }

    private List<OrderDeliveryTicket> generateSalesOrderTickets(DeliveryGenerateReq req, long startedAt) {
        if (salesOrderHeaderMapper == null || salesOrderItemMapper == null) {
            throw new RuntimeException("销售订单提单服务不可用");
        }
        Long salesOrderId = resolveSalesOrderId(req.getItems());
        SalesOrderHeader header = salesOrderHeaderMapper.selectByIdForUpdate(salesOrderId);
        if (header == null) {
            throw new RuntimeException("销售订单不存在");
        }
        assertSalesOrderDataScope(header);
        if (!QuoteStatusWorkflow.canGenerateDeliveryTicket(header.getStatus())) {
            throw new RuntimeException("只有已报价或部分已提单的销售订单可以生成提单");
        }

        String orderType = normalizeOrderType(firstText(req.getOrderType(), header.getOrderType()));
        LocalDate startDate = req.getSalesOrderDate() == null ? LocalDate.now() : req.getSalesOrderDate();
        String projectNo = isBlank(req.getProjectNo()) ? resolveSalesOrderProjectNo(header) : req.getProjectNo().trim();
        String internalProjectNo = firstText(req.getInternalProjectNo(), header.getInternalProjectNo());
        if (isBlank(internalProjectNo)) {
            throw new RuntimeException("内部项目号不能为空");
        }
        if (internalProjectNo.length() > 100) {
            throw new RuntimeException("内部项目号不能超过 100 个字符");
        }
        String customerOrderNo = firstText(req.getCustomerOrderNo(), header.getCustomerOrderNo());
        if (isBlank(customerOrderNo)) {
            throw new RuntimeException("客户订单号不能为空");
        }
        if (customerOrderNo.length() > 100) {
            throw new RuntimeException("客户订单号不能超过 100 个字符");
        }
        String customerProjectNo = firstText(req.getCustomerProjectNo(), header.getCustomerProjectNo());
        if (customerProjectNo.length() > 100) {
            throw new RuntimeException("客户项目号不能超过 100 个字符");
        }
        String arrangeType = isBlank(req.getArrangeType()) ? "计划" : req.getArrangeType().trim();

        Map<Long, SalesOrderItem> lockedItemMap = lockRequestedSalesOrderItems(req.getItems());
        Map<Long, SalesOrderDemandLine> demandByItemId = new LinkedHashMap<>();
        for (DeliveryGenerateReq.DeliveryItemReq line : req.getItems()) {
            if (!ReleaseSourceType.SALES_ORDER.equals(line.getSourceType())) {
                throw new RuntimeException("销售订单提单请求不能混入 RFQ 零件");
            }
            Long sourceItemId = resolveSourceItemId(line);
            SalesOrderItem item = lockedItemMap.get(sourceItemId);
            if (item == null) {
                throw new RuntimeException("销售订单零件不存在，ID=" + sourceItemId);
            }
            if (!salesOrderId.equals(item.getSalesOrderId())) {
                throw new RuntimeException("销售订单零件 " + salesOrderItemLabel(item) + " 不属于当前销售订单");
            }
            int acceptedQty = line.getAcceptedQty() == null ? salesOrderRemainingQty(item) : line.getAcceptedQty();
            if (acceptedQty <= 0) {
                throw new RuntimeException("销售订单零件 " + salesOrderItemLabel(item) + " 的通过数量必须大于 0");
            }
            if (acceptedQty > safeInt(item.getQty())) {
                throw new RuntimeException("销售订单零件 " + salesOrderItemLabel(item) + " 的通过数量不能超过订单数量");
            }
            demandByItemId.merge(sourceItemId, new SalesOrderDemandLine(item, acceptedQty),
                    (left, right) -> new SalesOrderDemandLine(left.item(), left.qty() + right.qty()));
        }

        for (SalesOrderDemandLine demand : demandByItemId.values()) {
            SalesOrderItem item = demand.item();
            if (item.getTaxIncludedUnitPrice() == null) {
                throw new RuntimeException("销售订单零件 " + salesOrderItemLabel(item) + " 尚未报价，不能生成提单");
            }
            int remainingQty = salesOrderRemainingQty(item);
            if (remainingQty <= 0) {
                throw new RuntimeException("销售订单零件 " + salesOrderItemLabel(item) + " 已全部提单，不能重复生成");
            }
            if (demand.qty() > remainingQty) {
                throw new RuntimeException("销售订单零件 " + salesOrderItemLabel(item) + " 的提单数量 " + demand.qty()
                        + " 超过剩余可提数量 " + remainingQty);
            }
        }

        List<OrderDeliveryTicket> createdTickets = new ArrayList<>();
        for (SalesOrderDemandLine demand : demandByItemId.values()) {
            OrderDeliveryTicket ticket = insertSalesOrderTicket(
                    header,
                    demand.item(),
                    orderType,
                    internalProjectNo,
                    customerOrderNo,
                    customerProjectNo,
                    projectNo,
                    startDate,
                    demand.qty(),
                    arrangeType);
            createdTickets.add(ticket);
            applySalesOrderRelease(demand.item(), demand.qty());
        }
        refreshSalesOrderHeaderStatus(header);
        auditService.record("DELIVERY_TICKET_GENERATE", "sales_order_header", salesOrderId, null, createdTickets);
        log.info("Delivery ticket generation completed sourceType={} sourceOrderId={} ticketCount={} processRouteCount={} durationMs={}",
                ReleaseSourceType.SALES_ORDER.name(), salesOrderId, createdTickets.size(), countProcessRoutes(createdTickets),
                System.currentTimeMillis() - startedAt);
        return createdTickets;
    }

    private Long resolveSalesOrderId(List<DeliveryGenerateReq.DeliveryItemReq> lines) {
        Long salesOrderId = null;
        for (DeliveryGenerateReq.DeliveryItemReq line : lines) {
            if (!ReleaseSourceType.SALES_ORDER.equals(line.getSourceType())) {
                continue;
            }
            if (line.getSourceOrderId() == null) {
                throw new RuntimeException("销售订单来源 ID 不能为空");
            }
            if (salesOrderId != null && !salesOrderId.equals(line.getSourceOrderId())) {
                throw new RuntimeException("一次提单请求只能包含同一个销售订单");
            }
            salesOrderId = line.getSourceOrderId();
        }
        if (salesOrderId == null) {
            throw new RuntimeException("销售订单来源 ID 不能为空");
        }
        return salesOrderId;
    }

    private Map<Long, SalesOrderItem> lockRequestedSalesOrderItems(List<DeliveryGenerateReq.DeliveryItemReq> lines) {
        List<Long> itemIds = lines.stream()
                .filter(line -> ReleaseSourceType.SALES_ORDER.equals(line.getSourceType()))
                .map(this::resolveSourceItemId)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
        Map<Long, SalesOrderItem> result = new HashMap<>();
        for (Long itemId : itemIds) {
            SalesOrderItem item = salesOrderItemMapper.selectByIdForUpdate(itemId);
            if (item != null) {
                result.put(itemId, item);
            }
        }
        return result;
    }

    private Long resolveSourceItemId(DeliveryGenerateReq.DeliveryItemReq line) {
        Long sourceItemId = line.getSourceItemId() != null
                ? line.getSourceItemId()
                : (line.getItemId() == null ? null : line.getItemId().longValue());
        if (sourceItemId == null) {
            throw new RuntimeException("销售订单零件来源 ID 不能为空");
        }
        return sourceItemId;
    }

    private void assertSalesOrderDataScope(SalesOrderHeader header) {
        if (dataScopeService != null) {
            dataScopeService.assertCanAccessSalesOrder(header);
        }
    }

    private List<SalesOrderItem> listSalesOrderItems(Long salesOrderId) {
        if (salesOrderId == null) {
            return List.of();
        }
        QueryWrapper<SalesOrderItem> query = new QueryWrapper<>();
        query.eq("sales_order_id", salesOrderId).orderByAsc("line_no").orderByAsc("id");
        return salesOrderItemMapper.selectList(query);
    }

    private OrderDeliveryTicket insertSalesOrderTicket(
            SalesOrderHeader header,
            SalesOrderItem item,
            String orderType,
            String internalProjectNo,
            String customerOrderNo,
            String customerProjectNo,
            String projectNo,
            LocalDate startDate,
            int qty,
            String arrangeType) {
        OrderDeliveryTicket ticket = new OrderDeliveryTicket();
        ticket.setRfqId(null);
        ticket.setRfqNo(header.getSalesOrderNo());
        ticket.setCustomerName(header.getCustomerName());
        ticket.setItemId(null);
        ticket.setSourceType(ReleaseSourceType.SALES_ORDER.name());
        ticket.setSourceOrderId(header.getId());
        ticket.setSourceItemId(item.getId());
        ticket.setOrderType(orderType);
        ticket.setInternalProjectNo(internalProjectNo);
        ticket.setCustomerOrderNo(customerOrderNo);
        ticket.setCustomerProjectNo(customerProjectNo);
        ticket.setProjectNo(projectNo);
        ticket.setItemType(resolveSalesOrderItemType(item));
        ticket.setPartNo(item.getPartNo());
        ticket.setDescription(item.getDescription());
        ticket.setQty(qty);
        ticket.setMaterialName(item.getMaterialName());
        ticket.setEngineeringMaterialCode(resolveSalesOrderEngineeringMaterialCode(item));
        ticket.setEngineeringMaterialName(resolveSalesOrderEngineeringMaterialName(item));
        ticket.setEngineeringMaterialTypeCode(resolveSalesOrderEngineeringMaterialTypeCode(item));
        ticket.setEngineeringMaterialTypeName(resolveSalesOrderEngineeringMaterialTypeName(item));
        ticket.setEngineeringMaterialCategory(resolveSalesOrderEngineeringMaterialCategory(item));
        ticket.setMaterialShape(resolveSalesOrderMaterialShape(item));
        ticket.setSurfaceTreatment(item.getSurfaceTreatment());
        ticket.setVersion(item.getVersion());
        ticket.setStartDate(startDate);
        ticket.setDeliveryDate(item.getInternalDeliveryDate() == null ? startDate : item.getInternalDeliveryDate());
        ticket.setRemark(item.getRemark());
        ticket.setArrangeType(arrangeType);
        ticket.setDrawingUrl(item.getDrawingUrl());
        ticket.setProcessRoute(processRouteSnapshotService.copySnapshot(item.getProcessRoute()));
        ticket.setMaterialData(deepCopySnapshot(item.getMaterialData()));
        ticket.setMaterialGrossWeight(item.getMaterialGrossWeight());
        ticket.setPartNetWeight(item.getPartNetWeight());
        initializeMesMaterialState(ticket, item.getMaterialNo(), item.getMaterialNoStatus(), item.getMaterialNoSyncedAt());
        ticketMapper.insert(ticket);
        return ticket;
    }

    private void applySalesOrderRelease(SalesOrderItem item, int qty) {
        int releasedQty = safeInt(item.getReleasedQty()) + qty;
        int remainingQty = Math.max(0, safeInt(item.getQty()) - releasedQty);
        item.setReleasedQty(releasedQty);
        item.setRemainingQty(remainingQty);
        item.setStatus(remainingQty > 0 ? QuoteStatusWorkflow.PARTIALLY_RELEASED : QuoteStatusWorkflow.RELEASED);
        salesOrderItemMapper.updateById(item);
    }

    private void refreshSalesOrderHeaderStatus(SalesOrderHeader header) {
        if (header == null || header.getId() == null) {
            return;
        }
        QueryWrapper<SalesOrderItem> itemQw = new QueryWrapper<>();
        itemQw.eq("sales_order_id", header.getId()).orderByAsc("id");
        List<SalesOrderItem> items = salesOrderItemMapper.selectList(itemQw);
        if (items.isEmpty()) {
            header.setStatus(QuoteStatusWorkflow.QUOTED);
            salesOrderHeaderMapper.updateById(header);
            return;
        }
        boolean anyReleased = items.stream().anyMatch(item -> safeInt(item.getReleasedQty()) > 0);
        boolean allReleased = items.stream()
                .allMatch(item -> safeInt(item.getReleasedQty()) >= safeInt(item.getQty()) || safeInt(item.getRemainingQty()) == 0);
        if (!anyReleased) {
            header.setStatus(QuoteStatusWorkflow.QUOTED);
        } else {
            header.setStatus(allReleased ? QuoteStatusWorkflow.RELEASED : QuoteStatusWorkflow.PARTIALLY_RELEASED);
        }
        salesOrderHeaderMapper.updateById(header);
    }

    private int salesOrderRemainingQty(SalesOrderItem item) {
        if (item.getRemainingQty() != null && item.getRemainingQty() > 0) {
            return item.getRemainingQty();
        }
        return Math.max(0, safeInt(item.getQty()) - safeInt(item.getReleasedQty()));
    }

    private void recordSalesOrderImportAutoDeliveryAudit(
            SalesOrderHeader header,
            List<OrderDeliveryTicket> createdTickets,
            long startedAt) {
        if (auditService == null) {
            return;
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("trigger", "SALES_ORDER_IMPORT_AUTO");
        summary.put("sourceType", ReleaseSourceType.SALES_ORDER.name());
        summary.put("sourceOrderId", header.getId());
        summary.put("salesOrderNo", header.getSalesOrderNo());
        summary.put("ticketCount", createdTickets == null ? 0 : createdTickets.size());
        summary.put("processRouteCount", countProcessRoutes(createdTickets));
        summary.put("ticketIds", createdTickets == null ? List.of() : createdTickets.stream()
                .map(OrderDeliveryTicket::getId)
                .toList());
        summary.put("durationMs", System.currentTimeMillis() - startedAt);
        auditService.record("DELIVERY_TICKET_GENERATE", "sales_order_header", header.getId(), null, summary);
    }

    private void recordSalesOrderScopedAutoDeliveryAudit(
            SalesOrderHeader header,
            List<OrderDeliveryTicket> createdTickets,
            String triggerScene,
            long startedAt) {
        if (auditService == null) {
            return;
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("trigger", safeTriggerScene(triggerScene));
        summary.put("sourceType", ReleaseSourceType.SALES_ORDER.name());
        summary.put("sourceOrderId", header.getId());
        summary.put("salesOrderNo", header.getSalesOrderNo());
        summary.put("ticketCount", createdTickets == null ? 0 : createdTickets.size());
        summary.put("processRouteCount", countProcessRoutes(createdTickets));
        summary.put("ticketIds", createdTickets == null ? List.of() : createdTickets.stream()
                .map(OrderDeliveryTicket::getId)
                .toList());
        summary.put("durationMs", System.currentTimeMillis() - startedAt);
        auditService.record("DELIVERY_TICKET_GENERATE", "sales_order_header", header.getId(), null, summary);
    }

    private LinkedHashSet<Long> normalizeSalesOrderItemIds(Collection<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return new LinkedHashSet<>();
        }
        return itemIds.stream()
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private String safeTriggerScene(String triggerScene) {
        return isBlank(triggerScene) ? "SALES_ORDER_IMPORT_AUTO" : triggerScene.trim();
    }

    private String resolveSalesOrderProjectNo(SalesOrderHeader header) {
        if (!isBlank(header.getSalesOrderNo())) {
            return header.getSalesOrderNo();
        }
        return firstText(header.getInternalProjectNo(), header.getCustomerProjectNo());
    }

    private String resolveSalesOrderItemType(SalesOrderItem item) {
        if (!isBlank(item.getItemType())) {
            return item.getItemType().trim();
        }
        return "ASSEMBLY".equalsIgnoreCase(item.getQuoteMode()) ? "组合件" : "普通件";
    }

    private String resolveSalesOrderEngineeringMaterialName(SalesOrderItem item) {
        return MaterialDataResolver.effectiveSalesOrderMaterialName(item);
    }

    private String resolveSalesOrderEngineeringMaterialCode(SalesOrderItem item) {
        if (item == null) {
            return "";
        }
        return firstText(item.getEngineeringMaterialCode(), MaterialDataResolver.materialCode(item.getMaterialData()));
    }

    private String resolveSalesOrderEngineeringMaterialTypeCode(SalesOrderItem item) {
        if (item == null) {
            return "";
        }
        return firstText(item.getEngineeringMaterialTypeCode(), MaterialDataResolver.materialTypeCode(item.getMaterialData()));
    }

    private String resolveSalesOrderEngineeringMaterialTypeName(SalesOrderItem item) {
        if (item == null) {
            return "";
        }
        return firstText(item.getEngineeringMaterialTypeName(), MaterialDataResolver.materialTypeName(item.getMaterialData()));
    }

    private String resolveSalesOrderEngineeringMaterialCategory(SalesOrderItem item) {
        if (item == null) {
            return "";
        }
        return firstText(item.getEngineeringMaterialCategory(), MaterialDataResolver.materialCategory(item.getMaterialData()));
    }

    private String resolveSalesOrderMaterialShape(SalesOrderItem item) {
        if (item == null) {
            return "";
        }
        if (!isBlank(item.getMaterialShape())) {
            return item.getMaterialShape().trim();
        }
        return MaterialDataResolver.materialShape(item.getMaterialData());
    }

    private String salesOrderItemLabel(SalesOrderItem item) {
        return item.getPartNo() + " / " + normalizeVersion(item.getVersion());
    }

    private void initializeMesMaterialState(
            OrderDeliveryTicket ticket,
            String materialNo,
            String materialNoStatus,
            LocalDateTime materialNoSyncedAt) {
        if (!isBlank(materialNo)
                && MATERIAL_CONFIRMED.equals(materialNoStatus == null ? null : materialNoStatus.trim())) {
            ticket.setMaterialNo(materialNo.trim());
            ticket.setMesSyncStatus(MES_SUCCESS);
            ticket.setMesSyncMessage(CONFIRMED_MATERIAL_REUSE_MESSAGE);
            ticket.setMesSyncedAt(materialNoSyncedAt);
            return;
        }
        ticket.setMaterialNo(null);
        ticket.setMesSyncStatus(MES_PENDING);
        ticket.setMesSyncMessage(null);
        ticket.setMesSyncedAt(null);
    }

    private ProcessSheetDTO buildProcessSheet(OrderDeliveryTicket ticket) {
        ProcessSheetDTO sheet = new ProcessSheetDTO();
        sheet.setId(ticket.getId());
        sheet.setRfqId(ticket.getRfqId());
        sheet.setItemId(ticket.getItemId());
        sheet.setInternalProjectNo(ticket.getInternalProjectNo());
        sheet.setItemType(ticket.getItemType());
        sheet.setPartNo(ticket.getPartNo());
        sheet.setVersion(ticket.getVersion());
        sheet.setDescription(ticket.getDescription());
        sheet.setQty(ticket.getQty());
        sheet.setMaterialName(ticket.getMaterialName());
        sheet.setEngineeringMaterialCode(ticket.getEngineeringMaterialCode());
        sheet.setEngineeringMaterialName(ticket.getEngineeringMaterialName());
        sheet.setEngineeringMaterialTypeCode(ticket.getEngineeringMaterialTypeCode());
        sheet.setEngineeringMaterialTypeName(ticket.getEngineeringMaterialTypeName());
        sheet.setEngineeringMaterialCategory(ticket.getEngineeringMaterialCategory());
        sheet.setMaterialGrossWeight(ticket.getMaterialGrossWeight());
        sheet.setPartNetWeight(ticket.getPartNetWeight() == null ? 0.0 : ticket.getPartNetWeight());
        sheet.setSubstituteMaterial("");
        sheet.setSurfaceTreatment(ticket.getSurfaceTreatment());
        sheet.setHeatTreatment("");
        sheet.setDeliveryDate(ticket.getDeliveryDate());
        sheet.setRemark(ticket.getRemark());
        sheet.setProcessRoute(processRouteSnapshotService.copySnapshot(ticket.getProcessRoute()));
        return sheet;
    }

    private int countProcessRoutes(List<OrderDeliveryTicket> tickets) {
        if (tickets == null || tickets.isEmpty()) {
            return 0;
        }
        return tickets.stream()
                .mapToInt(ticket -> processRouteSnapshotService.countSnapshotRows(ticket.getProcessRoute()))
                .sum();
    }

    private Object deepCopySnapshot(Object value) {
        if (jsonSnapshotCopyService == null) {
            jsonSnapshotCopyService = new JsonSnapshotCopyService();
        }
        return jsonSnapshotCopyService.deepCopySnapshot(value);
    }

    private String normalizePartNo(String partNo) {
        return partNo == null ? "" : partNo.trim().toUpperCase();
    }

    private String normalizeVersion(String version) {
        String normalized = version == null ? "" : version.trim().toUpperCase();
        return normalized;
    }

    private String firstText(String preferred, String fallback) {
        if (!isBlank(preferred)) {
            return preferred.trim();
        }
        return fallback == null ? "" : fallback.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean isBusinessFailure(RuntimeException e) {
        return e.getClass().equals(RuntimeException.class) && e.getCause() == null && !isBlank(e.getMessage());
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
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

    private record SalesOrderDemandLine(SalesOrderItem item, int qty) {
    }
}
