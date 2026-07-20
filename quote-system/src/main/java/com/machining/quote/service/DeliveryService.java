package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.DeliveryGenerateReq;
import com.machining.quote.dto.ProcessSheetDTO;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderRfqMapper;
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

@Service
public class DeliveryService {
    private static final Logger log = LoggerFactory.getLogger(DeliveryService.class);

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

    @Transactional
    public List<OrderDeliveryTicket> generateTickets(DeliveryGenerateReq req) {
        Integer rfqId = req == null ? null : req.getRfqId();
        int selectedItemCount = req == null || req.getItems() == null ? 0 : req.getItems().size();
        long startedAt = System.currentTimeMillis();
        log.info("Delivery ticket generation started rfqId={} selectedItemCount={}", rfqId, selectedItemCount);
        try {
        if (req.getRfqId() == null) {
            throw new RuntimeException("询价单 ID 不能为空");
        }
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new RuntimeException("请至少选择一个通过议价的零件");
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

            OrderDeliveryTicket ticket = insertTicket(order, item, internalProjectNo, customerOrderNo, customerProjectNo, projectNo, startDate, acceptedQty,
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

    private void assertOrderDataScope(Integer rfqId) {
        if (rfqId != null && dataScopeService != null) {
            dataScopeService.assertCanAccessOrder(rfqId, orderRfqMapper);
        }
    }

    private void assertOrderDataScope(OrderRfq order) {
        if (dataScopeService != null) {
            dataScopeService.assertCanAccessOrder(order);
        }
    }
    private OrderDeliveryTicket insertTicket(OrderRfq order, ItemQuoteVersion item, String internalProjectNo,
                                             String customerOrderNo, String customerProjectNo, String projectNo,
                                             LocalDate startDate, int qty, String remark, String arrangeType) {
        OrderDeliveryTicket ticket = new OrderDeliveryTicket();
        ticket.setRfqId(order.getId());
        ticket.setItemId(item.getId());
        ticket.setOrderType(isBlank(item.getOrderType()) ? "新订单" : item.getOrderType());
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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean isBusinessFailure(RuntimeException e) {
        return e.getClass().equals(RuntimeException.class) && e.getCause() == null && !isBlank(e.getMessage());
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private record DemandLine(ItemQuoteVersion item, int qty) {
    }
}
