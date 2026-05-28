package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.dto.DeliveryGenerateReq;
import com.machining.quote.dto.InventorySaveReq;
import com.machining.quote.dto.InventoryStockOpReq;
import com.machining.quote.dto.QuoteSubmitReq;
import com.machining.quote.entity.DictMaterial;
import com.machining.quote.entity.InventoryMaster;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderProcessSheet;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.DictMaterialMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderProcessSheetMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.service.DeliveryService;
import com.machining.quote.service.EngineerService;
import com.machining.quote.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

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
    private DictMaterialMapper materialMapper;
    @Autowired
    private ItemQuoteVersionMapper itemMapper;
    @Autowired
    private OrderDeliveryTicketMapper ticketMapper;
    @Autowired
    private OrderProcessSheetMapper processSheetMapper;
    @Autowired
    private InventoryService inventoryService;
    @Autowired
    private DeliveryService deliveryService;
    @Autowired
    private EngineerService engineerService;

    @Test
    void quoteSavePersistsGrossAndNetWeights() {
        OrderRfq order = new OrderRfq();
        order.setRfqNo("WEIGHT-" + System.nanoTime());
        order.setCustomerName("重量客户");
        order.setStatus("待工程部评估");
        orderMapper.insert(order);

        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setRfqId(order.getId());
        item.setPartNo("W-100");
        item.setVersion("A");
        item.setDescription("重量测试件");
        item.setQty(2);
        item.setDrawingUrl("/uploads/test/W-100.pdf");
        item.setStatus("待核价");
        itemMapper.insert(item);

        String materialName = "TEST-MAT-" + System.nanoTime();
        DictMaterial material = new DictMaterial();
        material.setMaterialName(materialName);
        material.setRemarks("");
        material.setPlateDensity(7850.0);
        material.setPlatePrice(7.1);
        materialMapper.insert(material);

        QuoteSubmitReq req = new QuoteSubmitReq();
        req.setItemId(item.getId());
        req.setPartNetWeight(1.23);
        Map<String, Object> materialData = new LinkedHashMap<>();
        materialData.put("name", materialName);
        materialData.put("shape", "板材");
        materialData.put("dimensionUnit", "m");
        materialData.put("dimensions", Map.of("L", 0.2, "W", 0.1, "T", 0.01));
        req.setMaterialData(materialData);
        req.setProcessRoute(List.of());

        engineerService.calculateAndSaveQuote(req);

        ItemQuoteVersion saved = itemMapper.selectById(item.getId());
        assertThat(saved.getMaterialGrossWeight()).isCloseTo(1.57, within(0.000001));
        assertThat(saved.getPartNetWeight()).isCloseTo(1.23, within(0.000001));
    }

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
        item.setLeadTime("2026-03-20");
        item.setQuoteLeadTime("2026-03-25");
        item.setInternalTargetLeadTime("2026-03-22");
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
        req.setInternalProjectNo("IP-001");
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
        assertThat(tickets).extracting(OrderDeliveryTicket::getDeliveryDate)
                .containsOnly(LocalDate.of(2026, 3, 22));
        assertThat(tickets).extracting(OrderDeliveryTicket::getInternalProjectNo)
                .containsOnly("IP-001");
        assertThat(tickets).extracting(OrderDeliveryTicket::getItemType)
                .containsOnly("普通件");

        InventoryMaster after = inventoryService.findByCustomerPartAndVersion("TEST客户", "P-100", "A");
        assertThat(after.getInStockQty()).isZero();
        assertThat(after.getPendingQty()).isZero();

        QueryWrapper<ItemQuoteVersion> itemQw = new QueryWrapper<>();
        itemQw.eq("id", item.getId());
        assertThat(itemMapper.selectOne(itemQw).getStatus()).isEqualTo("已生成提单");

        QueryWrapper<OrderProcessSheet> sheetQw = new QueryWrapper<>();
        sheetQw.eq("item_id", item.getId());
        assertThat(processSheetMapper.selectList(sheetQw)).extracting(OrderProcessSheet::getDeliveryDate)
                .containsOnly(LocalDate.of(2026, 3, 22));
        assertThat(processSheetMapper.selectList(sheetQw)).extracting(OrderProcessSheet::getInternalProjectNo)
                .containsOnly("IP-001");
        assertThat(processSheetMapper.selectList(sheetQw)).extracting(OrderProcessSheet::getItemType)
                .containsOnly("普通件");
    }

    @Test
    void deleteInventoryRemovesMasterAndPendingRows() {
        InventorySaveReq saveReq = new InventorySaveReq();
        saveReq.setCustomerName("删除库存客户");
        saveReq.setPartNo("DEL-100");
        saveReq.setVersion("A");
        saveReq.setInStockQty(3);
        inventoryService.saveOrUpdate(saveReq);

        InventoryMaster inventory = inventoryService.findByCustomerPartAndVersion("删除库存客户", "DEL-100", "A");
        InventoryStockOpReq pendingReq = new InventoryStockOpReq();
        pendingReq.setInventoryId(inventory.getId());
        pendingReq.setStockType("PENDING");
        pendingReq.setPendingRefNo("WO-DEL");
        pendingReq.setQty(2);
        inventoryService.addStock(pendingReq);

        assertThat(inventoryService.listPending(inventory.getId())).hasSize(1);

        inventoryService.deleteInventory(inventory.getId());

        assertThat(inventoryService.findByCustomerPartAndVersion("删除库存客户", "DEL-100", "A")).isNull();
        assertThat(inventoryService.listPending(inventory.getId())).isEmpty();
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
