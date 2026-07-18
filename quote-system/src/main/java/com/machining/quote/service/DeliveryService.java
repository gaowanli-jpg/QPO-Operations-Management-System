package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.dto.DeliveryGenerateReq;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderProcessSheet;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderProcessSheetMapper;
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
    private OrderProcessSheetMapper processSheetMapper;
    @Autowired
    private AuditService auditService;
    @Autowired
    private LeadTimeService leadTimeService;

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
            assertNoDuplicateTicket(item);
        }

        List<OrderDeliveryTicket> createdTickets = new ArrayList<>();
        for (DemandLine demand : demandByItemId.values()) {
            ItemQuoteVersion item = demand.item();
            int acceptedQty = demand.qty();

            createdTickets.add(insertTicket(order, item, internalProjectNo, customerOrderNo, customerProjectNo, projectNo, startDate, acceptedQty,
                    item.getRemark(), arrangeType));

            insertProcessSheet(item, acceptedQty, leadTimeService.resolveDeliveryDate(startDate, leadTimeService.effectiveDeliveryLeadTime(item)), internalProjectNo);
        }

        for (DemandLine demand : demandByItemId.values()) {
            refreshItemTicketStatus(demand.item());
        }
        refreshOrderTicketStatus(order.getId());
        auditService.record("DELIVERY_TICKET_GENERATE", "order_rfq", order.getId(), null, createdTickets);
        log.info("Delivery ticket generation completed rfqId={} ticketCount={} processSheetCount={} durationMs={}",
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
        QueryWrapper<OrderDeliveryTicket> qw = new QueryWrapper<>();
        if (rfqId != null) {
            qw.eq("rfq_id", rfqId);
        }
        qw.orderByAsc("id");
        return ticketMapper.selectList(qw);
    }

    public List<OrderProcessSheet> listProcessSheets(Integer rfqId) {
        QueryWrapper<OrderProcessSheet> qw = new QueryWrapper<>();
        if (rfqId != null) {
            qw.eq("rfq_id", rfqId);
        }
        qw.orderByAsc("id");
        return processSheetMapper.selectList(qw);
    }

    @Transactional
    public void deleteTicket(Integer ticketId) {
        OrderDeliveryTicket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new RuntimeException("提单行不存在");
        }
        if (ticket.getRfqId() != null) {
            OrderRfq lockedOrder = orderRfqMapper.selectByIdForUpdate(ticket.getRfqId());
            if (lockedOrder == null) {
                throw new RuntimeException("提单所属订单已被删除");
            }
            ticket = ticketMapper.selectById(ticketId);
            if (ticket == null) {
                throw new RuntimeException("提单行已被删除");
            }
        }

        ItemQuoteVersion item = findItemByTicket(ticket);
        ticketMapper.deleteById(ticketId);

        if (item != null) {
            refreshItemTicketStatus(item);
        }
        refreshOrderTicketStatus(ticket.getRfqId());
        auditService.record("DELIVERY_TICKET_DELETE", "order_delivery_ticket", ticketId, ticket, null);
    }

    @Transactional
    public void deleteProcessSheet(Integer sheetId) {
        OrderProcessSheet sheet = processSheetMapper.selectById(sheetId);
        if (sheet == null) {
            throw new RuntimeException("工艺表行不存在");
        }
        if (sheet.getRfqId() != null) {
            OrderRfq lockedOrder = orderRfqMapper.selectByIdForUpdate(sheet.getRfqId());
            if (lockedOrder == null) {
                throw new RuntimeException("工艺表所属订单已被删除");
            }
            sheet = processSheetMapper.selectById(sheetId);
            if (sheet == null) {
                throw new RuntimeException("工艺表行已被删除");
            }
        }
        processSheetMapper.deleteById(sheetId);
        auditService.record("PROCESS_SHEET_DELETE", "order_process_sheet", sheetId, sheet, null);
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
        ticket.setQty(qty);
        ticket.setMaterialName(item.getMaterialName());
        ticket.setEngineeringMaterialName(MaterialDataResolver.engineeringMaterialName(item));
        ticket.setSurfaceTreatment(item.getSurfaceTreatment());
        ticket.setVersion(item.getVersion());
        ticket.setStartDate(startDate);
        ticket.setDeliveryDate(leadTimeService.resolveDeliveryDate(startDate, leadTimeService.effectiveDeliveryLeadTime(item)));
        ticket.setRemark(remark);
        ticket.setArrangeType(arrangeType);
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

    private ItemQuoteVersion findItemByTicket(OrderDeliveryTicket ticket) {
        if (ticket.getItemId() != null) {
            ItemQuoteVersion item = itemMapper.selectByIdForUpdate(ticket.getItemId());
            if (item != null) {
                return item;
            }
        }
        QueryWrapper<ItemQuoteVersion> qw = new QueryWrapper<>();
        qw.eq("rfq_id", ticket.getRfqId()).eq("part_no", ticket.getPartNo());
        if (!isBlank(ticket.getVersion())) {
            qw.eq("version", normalizeVersion(ticket.getVersion()));
        }
        qw.last("LIMIT 1 FOR UPDATE");
        return itemMapper.selectOne(qw);
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
            item.setStatus(item.getFinalUnitPrice() == null ? "待核价" : "已报价");
        } else if (generatedQty < safeInt(item.getQty())) {
            item.setStatus("已部分提单");
        } else {
            item.setStatus("已生成提单");
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
            order.setStatus("待工程部评估");
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
            return "待工程部评估";
        }
        boolean allQuoted = drawableItems.stream().allMatch(item -> item.getFinalUnitPrice() != null);
        if (allQuoted) {
            return "已报价";
        }
        return "待工程部评估";
    }

    private void insertProcessSheet(ItemQuoteVersion item, int acceptedQty, LocalDate deliveryDate, String internalProjectNo) {
        QueryWrapper<OrderProcessSheet> qw = new QueryWrapper<>();
        qw.eq("rfq_id", item.getRfqId()).eq("item_id", item.getId());
        if (processSheetMapper.selectCount(qw) > 0) {
            return;
        }

        OrderProcessSheet sheet = new OrderProcessSheet();
        sheet.setRfqId(item.getRfqId());
        sheet.setItemId(item.getId());
        sheet.setInternalProjectNo(internalProjectNo);
        sheet.setItemType(resolveItemType(item));
        sheet.setPartNo(item.getPartNo());
        sheet.setVersion(item.getVersion());
        sheet.setDescription(item.getDescription());
        sheet.setQty(acceptedQty);
        sheet.setMaterialName(item.getMaterialName());
        sheet.setEngineeringMaterialName(MaterialDataResolver.engineeringMaterialName(item));
        sheet.setMaterialGrossWeight(item.getMaterialGrossWeight());
        sheet.setPartNetWeight(item.getPartNetWeight() == null ? 0.0 : item.getPartNetWeight());
        sheet.setSubstituteMaterial("");
        sheet.setSurfaceTreatment(item.getSurfaceTreatment());
        sheet.setHeatTreatment("");
        sheet.setDeliveryDate(deliveryDate);
        sheet.setRemark(item.getRemark());
        sheet.setProcessRoute(item.getProcessRoute());
        processSheetMapper.insert(sheet);
    }

    private void assertNoDuplicateTicket(ItemQuoteVersion item) {
        QueryWrapper<OrderDeliveryTicket> qw = new QueryWrapper<>();
        qw.eq("rfq_id", item.getRfqId()).eq("item_id", item.getId());
        if (ticketMapper.selectCount(qw) > 0) {
            throw new RuntimeException("零件 " + itemLabel(item) + " 已生成过提单，请勿重复生成");
        }
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
