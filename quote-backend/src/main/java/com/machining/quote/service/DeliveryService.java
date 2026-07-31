package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.DeliveryGenerateReq;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.ProcessSheetDTO;
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
import com.machining.quote.util.MaterialDataResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DeliveryService {
    private static final Logger log = LoggerFactory.getLogger(DeliveryService.class);
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> ALLOWED_ORDER_TYPES = Set.of("预测订单", "备货订单", "正式订单");

    @Autowired
    private OrderRfqMapper orderRfqMapper;
    @Autowired
    private ItemQuoteVersionMapper itemMapper;
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

    @Transactional
    public List<OrderDeliveryTicket> generateTickets(DeliveryGenerateReq req) {
        Integer rfqId = req == null ? null : req.getRfqId();
        int selectedItemCount = req == null || req.getItems() == null ? 0 : req.getItems().size();
        long startedAt = System.currentTimeMillis();
        log.info("Delivery ticket generation started rfqId={} selectedItemCount={}", rfqId, selectedItemCount);
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
        if (req.getRfqId() == null) {
            throw new RuntimeException("询价单 ID 不能为空");
        }

        OrderRfq order = orderRfqMapper.selectByIdForUpdate(req.getRfqId());
        if (order == null) {
            throw new RuntimeException("询价单不存在");
        }

        assertOrderDataScope(order);
        if (!QuoteStatusWorkflow.canGenerateDeliveryTicket(order.getStatus())) {
            if (QuoteStatusWorkflow.PRICED.equals(order.getStatus())) {
                throw new RuntimeException("请先在报价处理模块保存报价单后再生成提单");
            }
            throw new RuntimeException("只有已报价订单可以生成提单");
        }
        String orderType = normalizeOrderType(req.getOrderType());
        LocalDate startDate = req.getStartDate() == null ? LocalDate.now() : req.getStartDate();
        String projectNo = isBlank(req.getProjectNo()) ? resolveProjectNo(order) : req.getProjectNo().trim();
        String internalProjectNo = req.getInternalProjectNo() == null ? "" : req.getInternalProjectNo().trim();
        if (internalProjectNo.isEmpty()) {
            throw new RuntimeException("内部项目号不能为空");
        }
        if (internalProjectNo.length() > 100) {
            throw new RuntimeException("内部项目号不能超过 100 个字符");
        }
        String customerOrderNo = req.getCustomerOrderNo() == null ? "" : req.getCustomerOrderNo().trim();
        if (customerOrderNo.isEmpty()) {
            throw new RuntimeException("客户订单号不能为空");
        }
        if (customerOrderNo.length() > 100) {
            throw new RuntimeException("客户订单号不能超过 100 个字符");
        }
        String customerProjectNo = req.getCustomerProjectNo() == null ? "" : req.getCustomerProjectNo().trim();
        if (customerProjectNo.length() > 100) {
            throw new RuntimeException("客户项目号不能超过 100 个字符");
        }
        String arrangeType = isBlank(req.getArrangeType()) ? "计划" : req.getArrangeType().trim();

        Map<Integer, ItemQuoteVersion> lockedItemMap = lockRequestedItems(req.getItems());
        Map<Integer, DemandLine> demandByItemId = new LinkedHashMap<>();
        for (DeliveryGenerateReq.DeliveryItemReq line : req.getItems()) {
            ItemQuoteVersion item = lockedItemMap.get(line.getItemId());
            if (item == null) {
                throw new RuntimeException("零件不存在，ID=" + line.getItemId());
            }
            if (!req.getRfqId().equals(item.getRfqId())) {
                throw new RuntimeException("零件 " + itemLabel(item) + " 不属于当前询价单");
            }

            int acceptedQty = line.getAcceptedQty() == null ? safeInt(item.getQty()) : line.getAcceptedQty();
            if (acceptedQty <= 0) {
                throw new RuntimeException("零件 " + itemLabel(item) + " 的通过数量必须大于 0");
            }
            if (acceptedQty > safeInt(item.getQty())) {
                throw new RuntimeException("零件 " + itemLabel(item) + " 的通过数量不能超过询价数量");
            }
            addDeliveryDemand(item, acceptedQty, demandByItemId);
        }

        for (DemandLine demand : demandByItemId.values()) {
            ItemQuoteVersion item = demand.item();
            if (item.getFinalUnitPrice() == null) {
                throw new RuntimeException("零件 " + itemLabel(item) + " 尚未核价，不能生成提单");
            }
            if (demand.qty() > safeInt(item.getQty())) {
                throw new RuntimeException("零件 " + itemLabel(item) + " 的提单数量 " + demand.qty() + " 超过询价数量 " + safeInt(item.getQty()));
            }
            int ticketedQty = ticketedQty(item);
            int remainingQty = remainingQty(item, ticketedQty);
            if (remainingQty <= 0) {
                throw new RuntimeException("零件 " + itemLabel(item) + " 已全部提单，不能重复生成");
            }
            if (demand.qty() > remainingQty) {
                log.warn("Delivery ticket quantity exceeds remaining rfqId={} itemId={} partNo={} requestedQty={} ticketedQty={} remainingQty={}",
                        item.getRfqId(), item.getId(), item.getPartNo(), demand.qty(), ticketedQty, remainingQty);
                throw new RuntimeException("零件 " + itemLabel(item) + " 的提单数量 " + demand.qty()
                        + " 超过剩余可提数量 " + remainingQty);
            }
        }

        List<OrderDeliveryTicket> createdTickets = new ArrayList<>();
        for (DemandLine demand : demandByItemId.values()) {
            ItemQuoteVersion item = demand.item();
            int acceptedQty = demand.qty();

            OrderDeliveryTicket ticket = insertTicket(order, item, orderType, internalProjectNo, customerOrderNo, customerProjectNo, projectNo, startDate, acceptedQty,
                    item.getRemark(), arrangeType);
            createdTickets.add(ticket);
        }

        for (DemandLine demand : demandByItemId.values()) {
            refreshItemTicketStatus(demand.item());
        }
        refreshOrderTicketStatus(order.getId());
        auditService.record("DELIVERY_TICKET_GENERATE", "order_rfq", order.getId(), null, createdTickets);
        log.info("Delivery ticket generation completed rfqId={} ticketCount={} dynamicProcessSheetCount={} durationMs={}",
                order.getId(), createdTickets.size(), createdTickets.size(), System.currentTimeMillis() - startedAt);
        return createdTickets;
        } catch (RuntimeException e) {
            if (isBusinessFailure(e)) {
                log.warn("Delivery ticket generation failed rfqId={} selectedItemCount={} durationMs={} reason={}",
                        rfqId, selectedItemCount, System.currentTimeMillis() - startedAt, e.getMessage());
            } else {
                log.error("Delivery ticket generation failed rfqId={} selectedItemCount={} durationMs={}",
                        rfqId, selectedItemCount, System.currentTimeMillis() - startedAt, e);
            }
            throw e;
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
    private void assertOrderDataScope(OrderRfq order) {
        if (dataScopeService != null) {
            dataScopeService.assertCanAccessOrder(order);
        }
    }

    private boolean isSalesOrderRequest(DeliveryGenerateReq req) {
        if (req == null || req.getItems() == null) {
            return false;
        }
        return req.getItems().stream()
                .anyMatch(line -> ReleaseSourceType.SALES_ORDER.equals(line.getSourceType()));
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
        LocalDate startDate = req.getStartDate() == null ? LocalDate.now() : req.getStartDate();
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
            if (item.getFinalUnitPrice() == null) {
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
        log.info("Delivery ticket generation completed sourceType={} sourceOrderId={} ticketCount={} durationMs={}",
                ReleaseSourceType.SALES_ORDER.name(), salesOrderId, createdTickets.size(), System.currentTimeMillis() - startedAt);
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
        ticket.setEngineeringMaterialName(resolveSalesOrderEngineeringMaterialName(item));
        ticket.setSurfaceTreatment(item.getSurfaceTreatment());
        ticket.setVersion(item.getVersion());
        ticket.setStartDate(startDate);
        ticket.setDeliveryDate(item.getInternalDeliveryDate() == null ? startDate : item.getInternalDeliveryDate());
        ticket.setRemark(item.getRemark());
        ticket.setArrangeType(arrangeType);
        ticket.setDrawingUrl(item.getDrawingUrl());
        ticket.setProcessRoute(item.getProcessRoute());
        ticket.setMaterialData(item.getMaterialData());
        ticket.setMaterialGrossWeight(item.getMaterialGrossWeight());
        ticket.setPartNetWeight(item.getPartNetWeight());
        ticket.setMaterialNo(item.getMaterialNo());
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
        if (!isBlank(item.getEngineeringMaterialName())) {
            return item.getEngineeringMaterialName().trim();
        }
        Object name = MaterialDataResolver.normalizeMap(item.getMaterialData()).get("name");
        return name == null ? "" : String.valueOf(name).trim();
    }

    private String salesOrderItemLabel(SalesOrderItem item) {
        return item.getPartNo() + " / " + normalizeVersion(item.getVersion());
    }

    private OrderDeliveryTicket insertTicket(OrderRfq order, ItemQuoteVersion item, String orderType, String internalProjectNo,
                                             String customerOrderNo, String customerProjectNo, String projectNo,
                                             LocalDate startDate, int qty, String remark, String arrangeType) {
        OrderDeliveryTicket ticket = new OrderDeliveryTicket();
        ticket.setRfqId(order.getId());
        ticket.setRfqNo(order.getRfqNo());
        ticket.setCustomerName(order.getCustomerName());
        ticket.setItemId(item.getId());
        ticket.setSourceType(ReleaseSourceType.RFQ.name());
        ticket.setSourceOrderId(order.getId() == null ? null : order.getId().longValue());
        ticket.setSourceItemId(item.getId() == null ? null : item.getId().longValue());
        ticket.setOrderType(orderType);
        ticket.setInternalProjectNo(internalProjectNo);
        ticket.setCustomerOrderNo(customerOrderNo);
        ticket.setCustomerProjectNo(customerProjectNo);
        ticket.setProjectNo(projectNo);
        ticket.setItemType(resolveItemType(item));
        ticket.setPartNo(item.getPartNo());
        ticket.setDescription(item.getDescription());
        ticket.setQty(qty);
        ticket.setMaterialName(item.getMaterialName());
        ticket.setEngineeringMaterialName(MaterialDataResolver.engineeringMaterialName(item));
        ticket.setSurfaceTreatment(item.getSurfaceTreatment());
        ticket.setVersion(item.getVersion());
        ticket.setStartDate(startDate);
        ticket.setDeliveryDate(leadTimeService.resolveDeliveryDate(startDate, leadTimeService.effectiveDeliveryLeadTime(item)));
        ticket.setRemark(remark);
        ticket.setArrangeType(arrangeType);
        ticket.setDrawingUrl(item.getDrawingUrl());
        ticket.setProcessRoute(item.getProcessRoute());
        ticket.setMaterialData(item.getMaterialData());
        ticket.setMaterialGrossWeight(item.getMaterialGrossWeight());
        ticket.setPartNetWeight(item.getPartNetWeight());
        ticketMapper.insert(ticket);
        return ticket;
    }

    private void addDeliveryDemand(ItemQuoteVersion item, int qty, Map<Integer, DemandLine> demandByItemId) {
        if (item == null || qty <= 0) {
            return;
        }
        demandByItemId.merge(item.getId(), new DemandLine(item, qty),
                (left, right) -> new DemandLine(left.item(), left.qty() + right.qty()));
    }

    private Map<Integer, ItemQuoteVersion> lockRequestedItems(List<DeliveryGenerateReq.DeliveryItemReq> lines) {
        List<Integer> itemIds = lines.stream()
                .map(DeliveryGenerateReq.DeliveryItemReq::getItemId)
                .filter(id -> id != null)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
        Map<Integer, ItemQuoteVersion> result = new HashMap<>();
        for (Integer itemId : itemIds) {
            ItemQuoteVersion item = itemMapper.selectByIdForUpdate(itemId);
            if (item != null) {
                result.put(itemId, item);
            }
        }
        return result;
    }

    private void refreshItemTicketStatus(ItemQuoteVersion item) {
        QueryWrapper<OrderDeliveryTicket> qw = new QueryWrapper<>();
        qw.eq("rfq_id", item.getRfqId());
        if (item.getId() != null) {
            qw.eq("item_id", item.getId());
        } else {
            qw.eq("part_no", item.getPartNo());
            qw.eq("version", normalizeVersion(item.getVersion()));
        }
        List<OrderDeliveryTicket> tickets = ticketMapper.selectList(qw);
        int generatedQty = tickets.stream().mapToInt(ticket -> safeInt(ticket.getQty())).sum();
        if (generatedQty <= 0) {
            item.setStatus(item.getFinalUnitPrice() == null ? QuoteStatusWorkflow.UNPRICED : QuoteStatusWorkflow.QUOTED);
        } else if (generatedQty < safeInt(item.getQty())) {
            item.setStatus(QuoteStatusWorkflow.QUOTED);
        } else {
            item.setStatus(QuoteStatusWorkflow.ITEM_RELEASED);
        }
        itemMapper.updateById(item);
    }

    private void refreshOrderTicketStatus(Integer rfqId) {
        if (rfqId == null) {
            return;
        }
        OrderRfq order = orderRfqMapper.selectById(rfqId);
        if (order == null) {
            return;
        }

        QueryWrapper<ItemQuoteVersion> itemQw = new QueryWrapper<>();
        itemQw.eq("rfq_id", rfqId).orderByAsc("id");
        List<ItemQuoteVersion> items = itemMapper.selectList(itemQw);
        if (items.isEmpty()) {
            order.setStatus(QuoteStatusWorkflow.UNPRICED);
            orderRfqMapper.updateById(order);
            return;
        }

        QueryWrapper<OrderDeliveryTicket> ticketQw = new QueryWrapper<>();
        ticketQw.eq("rfq_id", rfqId);
        List<OrderDeliveryTicket> tickets = ticketMapper.selectList(ticketQw);
        int totalTicketQty = tickets.stream().mapToInt(ticket -> safeInt(ticket.getQty())).sum();
        if (totalTicketQty <= 0) {
            order.setStatus(resolveQuoteStatus(items));
            orderRfqMapper.updateById(order);
            return;
        }

        Map<Integer, Integer> qtyByItemId = new HashMap<>();
        Map<String, Integer> legacyQtyByPartVersion = new HashMap<>();
        for (OrderDeliveryTicket ticket : tickets) {
            int qty = safeInt(ticket.getQty());
            if (ticket.getItemId() != null) {
                qtyByItemId.merge(ticket.getItemId(), qty, Integer::sum);
            } else if (!isBlank(ticket.getPartNo())) {
                legacyQtyByPartVersion.merge(itemKey(ticket.getPartNo(), ticket.getVersion()), qty, Integer::sum);
            }
        }

        List<ItemQuoteVersion> quotedItems = items.stream()
                .filter(item -> item.getFinalUnitPrice() != null)
                .toList();
        if (quotedItems.isEmpty()) {
            order.setStatus("部分已提单");
            orderRfqMapper.updateById(order);
            return;
        }

        boolean allQuotedItemsTicketed = quotedItems.stream().allMatch(item -> {
            int generatedQty = qtyByItemId.getOrDefault(item.getId(), 0)
                    + legacyQtyByPartVersion.getOrDefault(itemKey(item.getPartNo(), item.getVersion()), 0);
            return generatedQty >= safeInt(item.getQty());
        });
        order.setStatus(allQuotedItemsTicketed ? "已生成提单" : "部分已提单");
        orderRfqMapper.updateById(order);
    }

    private String resolveQuoteStatus(List<ItemQuoteVersion> items) {
        List<ItemQuoteVersion> drawableItems = items.stream()
                .filter(item -> !isBlank(item.getDrawingUrl()))
                .toList();
        if (drawableItems.isEmpty()) {
            return QuoteStatusWorkflow.UNPRICED;
        }
        boolean allQuoted = drawableItems.stream().allMatch(item -> item.getFinalUnitPrice() != null);
        if (allQuoted) {
            return QuoteStatusWorkflow.QUOTED;
        }
        return QuoteStatusWorkflow.UNPRICED;
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
        sheet.setEngineeringMaterialName(ticket.getEngineeringMaterialName());
        sheet.setMaterialGrossWeight(ticket.getMaterialGrossWeight());
        sheet.setPartNetWeight(ticket.getPartNetWeight() == null ? 0.0 : ticket.getPartNetWeight());
        sheet.setSubstituteMaterial("");
        sheet.setSurfaceTreatment(ticket.getSurfaceTreatment());
        sheet.setHeatTreatment("");
        sheet.setDeliveryDate(ticket.getDeliveryDate());
        sheet.setRemark(ticket.getRemark());
        sheet.setProcessRoute(ticket.getProcessRoute());
        return sheet;
    }

    private int ticketedQty(ItemQuoteVersion item) {
        QueryWrapper<OrderDeliveryTicket> qw = new QueryWrapper<>();
        qw.eq("rfq_id", item.getRfqId());
        return ticketMapper.selectList(qw).stream()
                .filter(ticket -> matchesTicketItem(item, ticket))
                .mapToInt(ticket -> safeInt(ticket.getQty()))
                .sum();
    }

    private boolean matchesTicketItem(ItemQuoteVersion item, OrderDeliveryTicket ticket) {
        if (item.getId() != null && item.getId().equals(ticket.getItemId())) {
            return true;
        }
        return ticket.getItemId() == null
                && normalizePartNo(item.getPartNo()).equals(normalizePartNo(ticket.getPartNo()))
                && normalizeVersion(item.getVersion()).equals(normalizeVersion(ticket.getVersion()));
    }

    private int remainingQty(ItemQuoteVersion item, int ticketedQty) {
        return Math.max(0, safeInt(item.getQty()) - ticketedQty);
    }

    private String itemKey(String partNo, String version) {
        return normalizePartNo(partNo) + "||" + normalizeVersion(version);
    }

    private String itemLabel(ItemQuoteVersion item) {
        return item.getPartNo() + " / " + normalizeVersion(item.getVersion());
    }

    private String resolveItemType(ItemQuoteVersion item) {
        return "ASSEMBLY".equalsIgnoreCase(item.getQuoteMode()) ? "组合件" : "普通件";
    }

    private String normalizePartNo(String partNo) {
        return partNo == null ? "" : partNo.trim().toUpperCase();
    }

    private String normalizeVersion(String version) {
        String normalized = version == null ? "" : version.trim().toUpperCase();
        return normalized;
    }

    private String resolveProjectNo(OrderRfq order) {
        if (!isBlank(order.getRfqNo())) {
            return order.getRfqNo();
        }
        return isBlank(order.getProjectName()) ? "" : order.getProjectName();
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

    private record DemandLine(ItemQuoteVersion item, int qty) {
    }

    private record SalesOrderDemandLine(SalesOrderItem item, int qty) {
    }
}
