package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.dto.DeliveryGenerateReq;
import com.machining.quote.dto.InventorySaveReq;
import com.machining.quote.dto.InventoryStockOpReq;
import com.machining.quote.entity.InventoryMaster;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.service.DeliveryService;
import com.machining.quote.service.EngineerService;
import com.machining.quote.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "erp.auth.enabled=false",
        "erp.upload-dir=${java.io.tmpdir}/quote-test/uploads/",
        "erp.backup-dir=${java.io.tmpdir}/quote-test/backups/"
})
@Transactional
class CoreWorkflowIntegrationTests {
    @Autowired
    private OrderRfqMapper orderMapper;
    @Autowired
    private ItemQuoteVersionMapper itemMapper;
    @Autowired
    private OrderDeliveryTicketMapper ticketMapper;
    @Autowired
    private InventoryService inventoryService;
    @Autowired
    private DeliveryService deliveryService;
    @Autowired
    private EngineerService engineerService;

    @Test
    void deliveryGenerationConsumesInStockBeforePendingInventory() {
        OrderRfq order = new OrderRfq();
        order.setRfqNo("TEST-" + System.nanoTime());
        order.setCustomerName("TEST客户");
        order.setProjectName("TEST项目");
        order.setStatus("已报价");
        orderMapper.insert(order);

        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setRfqId(order.getId());
        item.setPartNo("P-100");
        item.setDescription("测试零件");
        item.setQty(5);
        item.setMaterialName("45#钢");
        item.setVersion("A");
        item.setDrawingUrl("/uploads/test/P-100.pdf");
        item.setFinalUnitPrice(10.0);
        item.setFinalTotalPrice(50.0);
        item.setStatus("已报价");
        itemMapper.insert(item);

        InventorySaveReq saveReq = new InventorySaveReq();
        saveReq.setCustomerName("TEST客户");
        saveReq.setPartNo("P-100");
        saveReq.setVersion("A");
        saveReq.setInStockQty(1);
        inventoryService.saveOrUpdate(saveReq);

        InventoryMaster inventory = inventoryService.findByCustomerPartAndVersion("TEST客户", "P-100", "A");
        InventoryStockOpReq pendingReq = new InventoryStockOpReq();
        pendingReq.setInventoryId(inventory.getId());
        pendingReq.setStockType("PENDING");
        pendingReq.setPendingRefNo("WO-1");
        pendingReq.setQty(2);
        inventoryService.addStock(pendingReq);

        DeliveryGenerateReq req = new DeliveryGenerateReq();
        req.setRfqId(order.getId());
        DeliveryGenerateReq.DeliveryItemReq line = new DeliveryGenerateReq.DeliveryItemReq();
        line.setItemId(item.getId());
        line.setAcceptedQty(5);
        req.setItems(List.of(line));

        List<OrderDeliveryTicket> tickets = deliveryService.generateTickets(req);

        assertThat(tickets).hasSize(3);
        assertThat(tickets).extracting(OrderDeliveryTicket::getArrangeType)
                .containsExactly("库存冲销", "待产冲销", "计划");
        assertThat(tickets).extracting(OrderDeliveryTicket::getQty)
                .containsExactly(1, 2, 2);

        InventoryMaster after = inventoryService.findByCustomerPartAndVersion("TEST客户", "P-100", "A");
        assertThat(after.getInStockQty()).isZero();
        assertThat(after.getPendingQty()).isZero();

        QueryWrapper<ItemQuoteVersion> itemQw = new QueryWrapper<>();
        itemQw.eq("id", item.getId());
        assertThat(itemMapper.selectOne(itemQw).getStatus()).isEqualTo("已生成提单");
    }

    @Test
    void repeatOrderRequiresSamePartNoAndVersion() {
        OrderRfq historicalOrder = new OrderRfq();
        historicalOrder.setRfqNo("HIST-" + System.nanoTime());
        historicalOrder.setCustomerName("版本客户");
        historicalOrder.setStatus("已生成提单");
        orderMapper.insert(historicalOrder);

        ItemQuoteVersion historicalItem = new ItemQuoteVersion();
        historicalItem.setRfqId(historicalOrder.getId());
        historicalItem.setPartNo("P-200");
        historicalItem.setVersion("A");
        historicalItem.setDescription("历史版本A");
        historicalItem.setQty(1);
        historicalItem.setDrawingUrl("/uploads/test/P-200.pdf");
        historicalItem.setMaterialData(Map.of("name", "45#钢", "shape", "板材", "cost", 10));
        historicalItem.setProcessRoute(List.of(Map.of("processName", "CNC", "inputVal", 1, "calcType", "工时")));
        historicalItem.setFinalUnitPrice(20.0);
        historicalItem.setFinalTotalPrice(20.0);
        historicalItem.setStatus("已生成提单");
        itemMapper.insert(historicalItem);

        OrderDeliveryTicket ticket = new OrderDeliveryTicket();
        ticket.setRfqId(historicalOrder.getId());
        ticket.setItemId(historicalItem.getId());
        ticket.setPartNo("P-200");
        ticket.setVersion("A");
        ticket.setQty(1);
        ticket.setArrangeType("计划");
        ticketMapper.insert(ticket);

        OrderRfq currentOrder = new OrderRfq();
        currentOrder.setRfqNo("CUR-" + System.nanoTime());
        currentOrder.setCustomerName("版本客户");
        currentOrder.setStatus("待工程部评估");
        orderMapper.insert(currentOrder);

        ItemQuoteVersion sameVersion = new ItemQuoteVersion();
        sameVersion.setRfqId(currentOrder.getId());
        sameVersion.setPartNo("P-200");
        sameVersion.setVersion("A");
        sameVersion.setDescription("当前版本A");
        sameVersion.setQty(1);
        sameVersion.setDrawingUrl("/uploads/test/P-200.pdf");
        sameVersion.setStatus("待核价");
        itemMapper.insert(sameVersion);

        ItemQuoteVersion differentVersion = new ItemQuoteVersion();
        differentVersion.setRfqId(currentOrder.getId());
        differentVersion.setPartNo("P-200");
        differentVersion.setVersion("B");
        differentVersion.setDescription("当前版本B");
        differentVersion.setQty(1);
        differentVersion.setDrawingUrl("/uploads/test/P-200.pdf");
        differentVersion.setStatus("待核价");
        itemMapper.insert(differentVersion);

        Map<String, Object> result = engineerService.analyzeRepeatOrder(currentOrder.getId());

        assertThat(result.get("repeatCount")).isEqualTo(1);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> repeatItems = (List<Map<String, Object>>) result.get("repeatItems");
        assertThat(repeatItems).extracting(item -> item.get("version")).containsExactly("A");
    }
}
