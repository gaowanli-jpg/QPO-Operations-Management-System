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
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.service.DeliveryService;
import com.machining.quote.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "erp.auth.enabled=false")
@Transactional
class CoreWorkflowIntegrationTests {
    @Autowired
    private OrderRfqMapper orderMapper;
    @Autowired
    private ItemQuoteVersionMapper itemMapper;
    @Autowired
    private InventoryService inventoryService;
    @Autowired
    private DeliveryService deliveryService;

    @Test
    void deliveryGenerationConsumesPendingAndInStockInventory() {
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
        saveReq.setInStockQty(1);
        inventoryService.saveOrUpdate(saveReq);

        InventoryMaster inventory = inventoryService.findByCustomerAndPartNo("TEST客户", "P-100");
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
                .contains("待产冲销", "库存冲销");

        InventoryMaster after = inventoryService.findByCustomerAndPartNo("TEST客户", "P-100");
        assertThat(after.getInStockQty()).isZero();
        assertThat(after.getPendingQty()).isZero();

        QueryWrapper<ItemQuoteVersion> itemQw = new QueryWrapper<>();
        itemQw.eq("id", item.getId());
        assertThat(itemMapper.selectOne(itemQw).getStatus()).isEqualTo("已生成提单");
    }
}
