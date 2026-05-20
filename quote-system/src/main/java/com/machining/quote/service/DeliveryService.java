package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.dto.DeliveryGenerateReq;
import com.machining.quote.entity.InventoryMaster;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderProcessSheet;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderProcessSheetMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DeliveryService {
    private static final Pattern FIRST_NUMBER = Pattern.compile("(\\d+)");
    private static final Pattern DELIVERY_DATE = Pattern.compile("(\\d{4})\\s*[.\\-/年]\\s*(\\d{1,2})\\s*[.\\-/月]\\s*(\\d{1,2})\\s*日?");
    private static final Pattern EXCEL_SERIAL_DATE = Pattern.compile("\\d{5}(?:\\.0+)?");

    @Autowired
    private OrderRfqMapper orderRfqMapper;
    @Autowired
    private ItemQuoteVersionMapper itemMapper;
    @Autowired
    private OrderDeliveryTicketMapper ticketMapper;
    @Autowired
    private OrderProcessSheetMapper processSheetMapper;
    @Autowired
    private InventoryService inventoryService;
    @Autowired
    private AuditService auditService;

    @Transactional
    public List<OrderDeliveryTicket> generateTickets(DeliveryGenerateReq req) {
        if (req.getRfqId() == null) {
            throw new RuntimeException("询价单 ID 不能为空");
        }
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new RuntimeException("请至少选择一个通过议价的零件");
        }

        OrderRfq order = orderRfqMapper.selectById(req.getRfqId());
        if (order == null) {
            throw new RuntimeException("询价单不存在");
        }

        LocalDate startDate = req.getStartDate() == null ? LocalDate.now() : req.getStartDate();
        String projectNo = isBlank(req.getProjectNo()) ? order.getProjectName() : req.getProjectNo().trim();
        String arrangeType = isBlank(req.getArrangeType()) ? "计划" : req.getArrangeType().trim();

        List<OrderDeliveryTicket> createdTickets = new ArrayList<>();
        for (DeliveryGenerateReq.DeliveryItemReq line : req.getItems()) {
            ItemQuoteVersion item = itemMapper.selectById(line.getItemId());
            if (item == null) {
                throw new RuntimeException("零件不存在，ID=" + line.getItemId());
            }
            if (!req.getRfqId().equals(item.getRfqId())) {
                throw new RuntimeException("零件 " + item.getPartNo() + " 不属于当前询价单");
            }

            int acceptedQty = line.getAcceptedQty() == null ? safeInt(item.getQty()) : line.getAcceptedQty();
            if (acceptedQty <= 0) {
                throw new RuntimeException("零件 " + item.getPartNo() + " 的通过数量必须大于 0");
            }
            if (acceptedQty > safeInt(item.getQty())) {
                throw new RuntimeException("零件 " + item.getPartNo() + " 的通过数量不能超过询价数量");
            }
            assertNoDuplicateTicket(req.getRfqId(), item.getPartNo());

            int remaining = acceptedQty;
            InventoryMaster inventory = inventoryService.findByCustomerAndPartNo(order.getCustomerName(), item.getPartNo());
            if (inventory != null) {
                int stockUsed = inventoryService.consumeInStock(inventory.getId(), remaining);
                if (stockUsed > 0) {
                    createdTickets.add(insertTicket(order, item, projectNo, startDate, stockUsed,
                            "已入库库存", "库存冲销"));
                    remaining -= stockUsed;
                }

                for (InventoryService.PendingConsumption consumption : inventoryService.consumePending(inventory.getId(), remaining)) {
                    createdTickets.add(insertTicket(order, item, projectNo, startDate, consumption.qty(),
                            remarkOrDefault(consumption.pendingRefNo(), "冲销待产"), "待产冲销"));
                    remaining -= consumption.qty();
                }
            }

            if (remaining > 0) {
                createdTickets.add(insertTicket(order, item, projectNo, startDate, remaining,
                        item.getRemark(), arrangeType));
            }

            item.setStatus(acceptedQty < safeInt(item.getQty()) ? "已部分提单" : "已生成提单");
            itemMapper.updateById(item);

            insertProcessSheet(item, acceptedQty, resolveDeliveryDate(startDate, item.getLeadTime()));
        }

        refreshOrderTicketStatus(order.getId());
        auditService.record("DELIVERY_TICKET_GENERATE", "order_rfq", order.getId(), null, createdTickets);
        return createdTickets;
    }

    public List<OrderDeliveryTicket> listTickets(Integer rfqId) {
        QueryWrapper<OrderDeliveryTicket> qw = new QueryWrapper<>();
        if (rfqId != null) {
            qw.eq("rfq_id", rfqId);
        }
        qw.orderByDesc("id");
        return ticketMapper.selectList(qw);
    }

    public List<OrderProcessSheet> listProcessSheets(Integer rfqId) {
        QueryWrapper<OrderProcessSheet> qw = new QueryWrapper<>();
        if (rfqId != null) {
            qw.eq("rfq_id", rfqId);
        }
        qw.orderByDesc("id");
        return processSheetMapper.selectList(qw);
    }

    @Transactional
    public void deleteTicket(Integer ticketId) {
        OrderDeliveryTicket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new RuntimeException("提单行不存在");
        }

        ItemQuoteVersion item = findItemByTicket(ticket);
        restoreInventoryForDeletedTicket(ticket);
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
        processSheetMapper.deleteById(sheetId);
        auditService.record("PROCESS_SHEET_DELETE", "order_process_sheet", sheetId, sheet, null);
    }

    private OrderDeliveryTicket insertTicket(OrderRfq order, ItemQuoteVersion item, String projectNo,
                                             LocalDate startDate, int qty, String remark, String arrangeType) {
        OrderDeliveryTicket ticket = new OrderDeliveryTicket();
        ticket.setRfqId(order.getId());
        ticket.setItemId(item.getId());
        ticket.setOrderType(isBlank(item.getOrderType()) ? "新订单" : item.getOrderType());
        ticket.setProjectNo(projectNo);
        ticket.setPartNo(item.getPartNo());
        ticket.setQty(qty);
        ticket.setMaterialName(item.getMaterialName());
        ticket.setSurfaceTreatment(item.getSurfaceTreatment());
        ticket.setVersion(item.getVersion());
        ticket.setStartDate(startDate);
        ticket.setDeliveryDate(resolveDeliveryDate(startDate, item.getLeadTime()));
        ticket.setRemark(remark);
        ticket.setArrangeType(arrangeType);
        ticketMapper.insert(ticket);
        return ticket;
    }

    private ItemQuoteVersion findItemByTicket(OrderDeliveryTicket ticket) {
        if (ticket.getItemId() != null) {
            ItemQuoteVersion item = itemMapper.selectById(ticket.getItemId());
            if (item != null) {
                return item;
            }
        }
        QueryWrapper<ItemQuoteVersion> qw = new QueryWrapper<>();
        qw.eq("rfq_id", ticket.getRfqId()).eq("part_no", ticket.getPartNo()).last("LIMIT 1");
        return itemMapper.selectOne(qw);
    }

    private void restoreInventoryForDeletedTicket(OrderDeliveryTicket ticket) {
        if (ticket.getQty() == null || ticket.getQty() <= 0) {
            return;
        }
        String arrangeType = ticket.getArrangeType();
        if (isBlank(arrangeType) || (!arrangeType.contains("待产冲销") && !arrangeType.contains("库存冲销"))) {
            return;
        }

        OrderRfq order = ticket.getRfqId() == null ? null : orderRfqMapper.selectById(ticket.getRfqId());
        String customerName = order == null ? "" : order.getCustomerName();
        if (arrangeType.contains("待产冲销")) {
            inventoryService.restorePending(customerName, ticket.getPartNo(), ticket.getRemark(), ticket.getQty());
        } else if (arrangeType.contains("库存冲销")) {
            inventoryService.restoreInStock(customerName, ticket.getPartNo(), ticket.getQty());
        }
    }

    private void refreshItemTicketStatus(ItemQuoteVersion item) {
        QueryWrapper<OrderDeliveryTicket> qw = new QueryWrapper<>();
        qw.eq("rfq_id", item.getRfqId());
        if (item.getId() != null) {
            qw.and(wrapper -> wrapper.eq("item_id", item.getId()).or().eq("part_no", item.getPartNo()));
        } else {
            qw.eq("part_no", item.getPartNo());
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
        Map<String, Integer> legacyQtyByPartNo = new HashMap<>();
        for (OrderDeliveryTicket ticket : tickets) {
            int qty = safeInt(ticket.getQty());
            if (ticket.getItemId() != null) {
                qtyByItemId.merge(ticket.getItemId(), qty, Integer::sum);
            } else if (!isBlank(ticket.getPartNo())) {
                legacyQtyByPartNo.merge(ticket.getPartNo(), qty, Integer::sum);
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
                    + legacyQtyByPartNo.getOrDefault(item.getPartNo(), 0);
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
        boolean anyQuoted = drawableItems.stream().anyMatch(item -> item.getFinalUnitPrice() != null);
        if (allQuoted) {
            return "已报价";
        }
        if (anyQuoted) {
            return "部分已报价";
        }
        return "待工程部评估";
    }

    private void insertProcessSheet(ItemQuoteVersion item, int acceptedQty, LocalDate deliveryDate) {
        QueryWrapper<OrderProcessSheet> qw = new QueryWrapper<>();
        qw.eq("rfq_id", item.getRfqId()).eq("item_id", item.getId());
        if (processSheetMapper.selectCount(qw) > 0) {
            return;
        }

        OrderProcessSheet sheet = new OrderProcessSheet();
        sheet.setRfqId(item.getRfqId());
        sheet.setItemId(item.getId());
        sheet.setPartNo(item.getPartNo());
        sheet.setDescription(item.getDescription());
        sheet.setQty(acceptedQty);
        sheet.setMaterialName(item.getMaterialName());
        sheet.setSubstituteMaterial("");
        sheet.setSurfaceTreatment(item.getSurfaceTreatment());
        sheet.setHeatTreatment("");
        sheet.setDeliveryDate(deliveryDate);
        sheet.setRemark(item.getRemark());
        sheet.setProcessRoute(item.getProcessRoute());
        processSheetMapper.insert(sheet);
    }

    private void assertNoDuplicateTicket(Integer rfqId, String partNo) {
        QueryWrapper<OrderDeliveryTicket> qw = new QueryWrapper<>();
        qw.eq("rfq_id", rfqId).eq("part_no", partNo);
        if (ticketMapper.selectCount(qw) > 0) {
            throw new RuntimeException("零件 " + partNo + " 已生成过提单，请勿重复扣减库存");
        }
    }

    private LocalDate resolveDeliveryDate(LocalDate startDate, String leadTime) {
        if (isBlank(leadTime)) {
            return startDate;
        }
        LocalDate exactDate = parseDeliveryDate(leadTime);
        if (exactDate != null) {
            return exactDate;
        }
        LocalDate excelSerialDate = parseExcelSerialDate(leadTime);
        if (excelSerialDate != null) {
            return excelSerialDate;
        }
        Matcher matcher = FIRST_NUMBER.matcher(leadTime);
        if (!matcher.find()) {
            return startDate;
        }
        return startDate.plusDays(Long.parseLong(matcher.group(1)));
    }

    private LocalDate parseDeliveryDate(String leadTime) {
        Matcher matcher = DELIVERY_DATE.matcher(leadTime.trim());
        if (!matcher.find()) {
            return null;
        }
        try {
            int year = Integer.parseInt(matcher.group(1));
            int month = Integer.parseInt(matcher.group(2));
            int day = Integer.parseInt(matcher.group(3));
            return LocalDate.of(year, month, day);
        } catch (DateTimeException | NumberFormatException e) {
            return null;
        }
    }

    private LocalDate parseExcelSerialDate(String leadTime) {
        String normalized = leadTime.trim();
        if (!EXCEL_SERIAL_DATE.matcher(normalized).matches()) {
            return null;
        }
        long serial = (long) Double.parseDouble(normalized);
        return excelSerialToDate(serial);
    }

    private LocalDate excelSerialToDate(long serial) {
        LocalDate date = LocalDate.of(1899, 12, 31).plusDays(serial);
        return serial >= 60 ? date.minusDays(1) : date;
    }

    private String remarkOrDefault(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }
}
