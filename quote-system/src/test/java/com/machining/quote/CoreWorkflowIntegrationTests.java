package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.DeliveryGenerateReq;
import com.machining.quote.dto.InternalTargetLeadTimeSetReq;
import com.machining.quote.dto.InternalTargetLeadTimeUpdateReq;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.QuoteLeadTimeSetReq;
import com.machining.quote.dto.QuoteLeadTimeUpdateReq;
import com.machining.quote.dto.QuoteSubmitReq;
import com.machining.quote.dto.aps.ApsDeliveryItemDTO;
import com.machining.quote.entity.DictMaterial;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderProcessSheet;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.DictMaterialMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderProcessSheetMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.service.ApsSourceService;
import com.machining.quote.service.DeliveryService;
import com.machining.quote.service.EngineerService;
import com.machining.quote.service.InternalTargetLeadTimeService;
import com.machining.quote.service.OrderManagementService;
import com.machining.quote.service.QuoteProcessService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    private DeliveryService deliveryService;
    @Autowired
    private EngineerService engineerService;
    @Autowired
    private QuoteProcessService quoteProcessService;
    @Autowired
    private InternalTargetLeadTimeService internalTargetLeadTimeService;
    @Autowired
    private OrderManagementService orderManagementService;
    @Autowired
    private ApsSourceService apsSourceService;

    @Test
    void orderSummaryPaginationUsesAggregatedCountsAndFilters() {
        String customerName = "分页客户-" + System.nanoTime();
        OrderRfq first = orderWithItemsTicketsAndSheets(customerName, "PAGE-A", "已报价", 2, 1, 1, 1, 2);
        OrderRfq second = orderWithItemsTicketsAndSheets(customerName, "PAGE-B", "已生成提单", 1, 1, 0, 0, 0);
        orderWithItemsTicketsAndSheets("其他分页客户-" + System.nanoTime(), "PAGE-C", "已报价", 1, 0, 0, 0, 0);

        PageResult<Map<String, Object>> firstPage = orderManagementService.listOrderSummaries(
                customerName, null, null, 1, 1);

        assertThat(firstPage.getTotal()).isEqualTo(2);
        assertThat(firstPage.getPage()).isEqualTo(1);
        assertThat(firstPage.getPageSize()).isEqualTo(1);
        assertThat(firstPage.getRecords()).hasSize(1);
        assertThat(firstPage.getRecords().get(0).get("id")).isEqualTo(second.getId());

        PageResult<Map<String, Object>> secondPage = orderManagementService.listOrderSummaries(
                customerName, null, null, 2, 1);
        Map<String, Object> summary = secondPage.getRecords().get(0);
        assertThat(summary.get("id")).isEqualTo(first.getId());
        assertThat(summary.get("itemCount")).isEqualTo(2);
        assertThat(summary.get("quotedCount")).isEqualTo(1);
        assertThat(summary.get("missingDrawingCount")).isEqualTo(1);
        assertThat(summary.get("ticketCount")).isEqualTo(1);
        assertThat(summary.get("ticketQty")).isEqualTo(3);
        assertThat(summary.get("processSheetCount")).isEqualTo(2);

        PageResult<Map<String, Object>> keywordFiltered = orderManagementService.listOrderSummaries(
                customerName, "PAGE-A", null, 1, 20);
        assertThat(keywordFiltered.getTotal()).isEqualTo(1);
        assertThat(keywordFiltered.getRecords().get(0).get("id")).isEqualTo(first.getId());

        PageResult<Map<String, Object>> statusFiltered = orderManagementService.listOrderSummaries(
                customerName, null, "已生成提单", 1, 20);
        assertThat(statusFiltered.getTotal()).isEqualTo(1);
        assertThat(statusFiltered.getRecords().get(0).get("id")).isEqualTo(second.getId());
    }

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
    void quoteLeadTimeCanBeSetInBatchAndClearedToCustomerLeadTime() {
        OrderRfq order = new OrderRfq();
        order.setRfqNo("QUOTE-DATE-" + System.nanoTime());
        order.setCustomerName("报价交期客户");
        order.setStatus(QuoteStatusWorkflow.PRICED);
        orderMapper.insert(order);

        ItemQuoteVersion item = quotedItem(order, "QD-100");
        item.setLeadTime("2026.03.20");
        itemMapper.updateById(item);

        QuoteLeadTimeSetReq setReq = new QuoteLeadTimeSetReq();
        setReq.setRfqId(order.getId());
        setReq.setItemIds(List.of(item.getId()));
        setReq.setQuoteLeadTime("2026.4.5");
        quoteProcessService.setLeadTimes(setReq);

        ItemQuoteVersion saved = itemMapper.selectById(item.getId());
        assertThat(saved.getQuoteLeadTime()).isEqualTo("2026-04-05");

        QuoteLeadTimeUpdateReq clearReq = new QuoteLeadTimeUpdateReq();
        clearReq.setRfqId(order.getId());
        clearReq.setItemId(item.getId());
        clearReq.setQuoteLeadTime("");
        quoteProcessService.updateLeadTime(clearReq);

        Map<String, Object> process = quoteProcessService.getQuoteProcess(order.getId());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) process.get("itemList");
        assertThat(itemMapper.selectById(item.getId()).getQuoteLeadTime()).isNull();
        assertThat(rows.get(0).get("effectiveLeadTime")).isEqualTo("2026.03.20");
    }

    @Test
    void quoteLeadTimeBatchSetRejectsInvalidDateWithoutWriting() {
        OrderRfq order = new OrderRfq();
        order.setRfqNo("QUOTE-BAD-DATE-" + System.nanoTime());
        order.setCustomerName("报价非法日期客户");
        order.setStatus(QuoteStatusWorkflow.PRICED);
        orderMapper.insert(order);

        ItemQuoteVersion item = quotedItem(order, "QBAD-100");

        QuoteLeadTimeSetReq setReq = new QuoteLeadTimeSetReq();
        setReq.setRfqId(order.getId());
        setReq.setItemIds(List.of(item.getId()));
        setReq.setQuoteLeadTime("7天");

        assertThatThrownBy(() -> quoteProcessService.setLeadTimes(setReq))
                .hasMessageContaining("日期格式不正确");
        assertThat(itemMapper.selectById(item.getId()).getQuoteLeadTime()).isNull();
    }

    @Test
    void internalTargetLeadTimeCanBeSetClearedAndUsedForDeliveryOutputs() {
        OrderRfq order = new OrderRfq();
        order.setRfqNo("INTERNAL-DATE-" + System.nanoTime());
        order.setCustomerName("内部交期客户");
        order.setStatus("已报价");
        orderMapper.insert(order);

        ItemQuoteVersion item = quotedItem(order, "ID-100");
        item.setLeadTime("2026-03-20");
        item.setQuoteLeadTime("2026-03-25");
        itemMapper.updateById(item);

        InternalTargetLeadTimeSetReq setReq = new InternalTargetLeadTimeSetReq();
        setReq.setRfqId(order.getId());
        setReq.setItemIds(List.of(item.getId()));
        setReq.setInternalTargetLeadTime("2026.04.10");
        internalTargetLeadTimeService.setLeadTimes(setReq);
        assertThat(itemMapper.selectById(item.getId()).getInternalTargetLeadTime()).isEqualTo("2026-04-10");

        InternalTargetLeadTimeUpdateReq clearReq = new InternalTargetLeadTimeUpdateReq();
        clearReq.setRfqId(order.getId());
        clearReq.setItemId(item.getId());
        clearReq.setInternalTargetLeadTime("");
        internalTargetLeadTimeService.updateLeadTime(clearReq);
        assertThat(itemMapper.selectById(item.getId()).getInternalTargetLeadTime()).isNull();
        assertThat(internalTargetLeadTimeService.listItemRows(order.getId()).get(0).get("effectiveDeliveryLeadTime"))
                .isEqualTo("2026-03-25");

        internalTargetLeadTimeService.setLeadTimes(setReq);
        DeliveryGenerateReq req = new DeliveryGenerateReq();
        req.setRfqId(order.getId());
        req.setInternalProjectNo("IP-DATE");
        req.setCustomerOrderNo("CO-DATE");
        DeliveryGenerateReq.DeliveryItemReq line = new DeliveryGenerateReq.DeliveryItemReq();
        line.setItemId(item.getId());
        line.setAcceptedQty(1);
        req.setItems(List.of(line));

        List<OrderDeliveryTicket> tickets = deliveryService.generateTickets(req);

        assertThat(tickets).extracting(OrderDeliveryTicket::getDeliveryDate)
                .containsOnly(LocalDate.of(2026, 4, 10));
        QueryWrapper<OrderProcessSheet> sheetQw = new QueryWrapper<>();
        sheetQw.eq("item_id", item.getId());
        assertThat(processSheetMapper.selectList(sheetQw)).extracting(OrderProcessSheet::getDeliveryDate)
                .containsOnly(LocalDate.of(2026, 4, 10));
    }

    @Test
    void deliveryGenerationCreatesTicketsAndProcessSheets() {
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

        DeliveryGenerateReq req = new DeliveryGenerateReq();
        req.setRfqId(order.getId());
        req.setInternalProjectNo("IP-001");
        req.setCustomerOrderNo("CO-001");
        req.setCustomerProjectNo("CP-001");
        DeliveryGenerateReq.DeliveryItemReq line = new DeliveryGenerateReq.DeliveryItemReq();
        line.setItemId(item.getId());
        line.setAcceptedQty(5);
        req.setItems(List.of(line));

        List<OrderDeliveryTicket> tickets = deliveryService.generateTickets(req);

        assertThat(tickets).hasSize(1);
        assertThat(tickets).extracting(OrderDeliveryTicket::getArrangeType)
                .containsExactly("计划");
        assertThat(tickets).extracting(OrderDeliveryTicket::getQty)
                .containsExactly(5);
        assertThat(tickets).extracting(OrderDeliveryTicket::getDeliveryDate)
                .containsOnly(LocalDate.of(2026, 3, 22));
        assertThat(tickets).extracting(OrderDeliveryTicket::getInternalProjectNo)
                .containsOnly("IP-001");
        assertThat(tickets).extracting(OrderDeliveryTicket::getCustomerOrderNo)
                .containsOnly("CO-001");
        assertThat(tickets).extracting(OrderDeliveryTicket::getCustomerProjectNo)
                .containsOnly("CP-001");
        assertThat(tickets).extracting(OrderDeliveryTicket::getItemType)
                .containsOnly("普通件");

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
    void generatedTicketAndProcessSheetListsKeepImportedItemOrder() {
        OrderRfq order = new OrderRfq();
        order.setRfqNo("ORDER-SEQUENCE-" + System.nanoTime());
        order.setCustomerName("ORDER-SEQUENCE-CUSTOMER");
        order.setStatus(QuoteStatusWorkflow.QUOTED);
        orderMapper.insert(order);

        ItemQuoteVersion first = quotedItem(order, "SEQ-A");
        ItemQuoteVersion second = quotedItem(order, "SEQ-B");
        ItemQuoteVersion third = quotedItem(order, "SEQ-C");

        DeliveryGenerateReq req = new DeliveryGenerateReq();
        req.setRfqId(order.getId());
        req.setInternalProjectNo("IP-SEQUENCE");
        req.setCustomerOrderNo("CO-SEQUENCE");
        req.setItems(List.of(
                deliveryLine(first),
                deliveryLine(second),
                deliveryLine(third)
        ));

        deliveryService.generateTickets(req);

        assertThat(deliveryService.listTickets(order.getId()))
                .extracting(OrderDeliveryTicket::getPartNo)
                .containsExactly("SEQ-A", "SEQ-B", "SEQ-C");
        assertThat(deliveryService.listProcessSheets(order.getId()))
                .extracting(OrderProcessSheet::getPartNo)
                .containsExactly("SEQ-A", "SEQ-B", "SEQ-C");
    }

    @Test
    void deliveryGenerationUsesEngineeringMaterialForTicketProcessSheetAndApsSource() {
        OrderRfq order = new OrderRfq();
        order.setRfqNo("APS-MAT-" + System.nanoTime());
        order.setCustomerName("APS-MAT-CUSTOMER");
        order.setStatus(QuoteStatusWorkflow.QUOTED);
        orderMapper.insert(order);

        String engineeringMaterial = "ENG-MATERIAL-" + System.nanoTime();
        DictMaterial material = new DictMaterial();
        material.setMaterialName(engineeringMaterial);
        material.setRemarks("");
        material.setPlateDensity(7850.0);
        material.setPlatePrice(7.1);
        materialMapper.insert(material);

        Map<String, Object> materialData = new LinkedHashMap<>();
        materialData.put("name", engineeringMaterial);
        materialData.put("shape", "BAR");
        materialData.put("weight", 2.5);

        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setRfqId(order.getId());
        item.setPartNo("APS-MAT-PART");
        item.setVersion("A");
        item.setDescription("imported material description");
        item.setQty(5);
        item.setMaterialName("IMPORTED-MATERIAL-DESCRIPTION");
        item.setUnit("PCS");
        item.setLeadTime("2026-06-20");
        item.setDrawingUrl("/uploads/test/APS-MAT-PART.pdf");
        item.setStatus("PENDING");
        itemMapper.insert(item);

        QuoteSubmitReq quoteReq = new QuoteSubmitReq();
        quoteReq.setItemId(item.getId());
        quoteReq.setPartNetWeight(1.2);
        quoteReq.setMaterialData(materialData);
        quoteReq.setProcessRoute(List.of());
        engineerService.calculateAndSaveQuote(quoteReq);

        ItemQuoteVersion savedItem = itemMapper.selectById(item.getId());
        assertThat(savedItem.getMaterialName()).isEqualTo("IMPORTED-MATERIAL-DESCRIPTION");
        assertThat(savedItem.getEngineeringMaterialName()).isEqualTo(engineeringMaterial);
        Map<String, Object> marketRow = internalTargetLeadTimeService.listItemRows(order.getId()).get(0);
        assertThat(marketRow.get("materialName")).isEqualTo("IMPORTED-MATERIAL-DESCRIPTION");
        assertThat(marketRow.get("engineeringMaterialName"))
                .isEqualTo(engineeringMaterial);

        DeliveryGenerateReq deliveryReq = new DeliveryGenerateReq();
        deliveryReq.setRfqId(order.getId());
        deliveryReq.setInternalProjectNo("IP-APS-MAT");
        deliveryReq.setCustomerOrderNo("CO-APS-MAT");
        deliveryReq.setCustomerProjectNo("CP-APS-MAT");
        DeliveryGenerateReq.DeliveryItemReq line = new DeliveryGenerateReq.DeliveryItemReq();
        line.setItemId(item.getId());
        line.setAcceptedQty(3);
        deliveryReq.setItems(List.of(line));

        OrderDeliveryTicket ticket = deliveryService.generateTickets(deliveryReq).get(0);
        assertThat(ticket.getMaterialName()).isEqualTo("IMPORTED-MATERIAL-DESCRIPTION");
        assertThat(ticket.getEngineeringMaterialName()).isEqualTo(engineeringMaterial);

        QueryWrapper<OrderProcessSheet> sheetQw = new QueryWrapper<>();
        sheetQw.eq("item_id", item.getId());
        OrderProcessSheet sheet = processSheetMapper.selectOne(sheetQw);
        assertThat(sheet.getMaterialName()).isEqualTo("IMPORTED-MATERIAL-DESCRIPTION");
        assertThat(sheet.getEngineeringMaterialName()).isEqualTo(engineeringMaterial);
        assertThat(sheet.getMaterialGrossWeight()).isEqualTo(2.5);
        assertThat(sheet.getPartNetWeight()).isEqualTo(1.2);

        ApsDeliveryItemDTO row = apsSourceService.listDeliveryItems(ticket.getId() - 1, 5)
                .getRecords()
                .stream()
                .filter(record -> ticket.getId().equals(record.getTicketId()))
                .findFirst()
                .orElseThrow();

        assertThat(row.getMaterialName()).isEqualTo(engineeringMaterial);
        assertThat(row.getMaterialType()).isEqualTo("BAR");
        assertThat(row.getMaterialGrossWeight()).isEqualTo(2.5);
        assertThat(row.getMaterialDemandWeight()).isEqualTo(7.5);
        assertThat(row.getPartNetWeight()).isEqualTo(1.2);
        assertThat(row.getDescription()).isEqualTo("imported material description");
        assertThat(row.getCustomerOrderNo()).isEqualTo("CO-APS-MAT");
        assertThat(row.getCustomerProjectNo()).isEqualTo("CP-APS-MAT");
    }

    @Test
    void itemStatusFlowsFromUnpricedToPricedQuotedAndReleased() {
        OrderRfq order = new OrderRfq();
        order.setRfqNo("ITEM-STATUS-" + System.nanoTime());
        order.setCustomerName("零件状态客户");
        order.setStatus(QuoteStatusWorkflow.UNPRICED);
        orderMapper.insert(order);

        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setRfqId(order.getId());
        item.setPartNo("ITEM-STATUS-100");
        item.setVersion("A");
        item.setDescription("零件状态流测试件");
        item.setQty(2);
        item.setLeadTime("2026-03-20");
        item.setQuoteLeadTime("2026-03-25");
        item.setDrawingUrl("/uploads/test/ITEM-STATUS-100.pdf");
        item.setStatus(QuoteStatusWorkflow.UNPRICED);
        itemMapper.insert(item);

        String materialName = "STATUS-MAT-" + System.nanoTime();
        DictMaterial material = new DictMaterial();
        material.setMaterialName(materialName);
        material.setRemarks("");
        material.setPlateDensity(7850.0);
        material.setPlatePrice(7.1);
        materialMapper.insert(material);

        QuoteSubmitReq quoteReq = new QuoteSubmitReq();
        quoteReq.setItemId(item.getId());
        quoteReq.setPartNetWeight(1.0);
        quoteReq.setMaterialData(Map.of(
                "name", materialName,
                "shape", "板材",
                "dimensionUnit", "m",
                "dimensions", Map.of("L", 0.2, "W", 0.1, "T", 0.01)
        ));
        quoteReq.setProcessRoute(List.of());

        engineerService.calculateAndSaveQuote(quoteReq);
        assertThat(itemMapper.selectById(item.getId()).getStatus())
                .as("单件核价保存只代表工程已核价，不能提前进入市场已报价")
                .isEqualTo(QuoteStatusWorkflow.PRICED);

        engineerService.completeOrderQuote(order.getId());
        quoteProcessService.saveQuoteSheet(order.getId());
        assertThat(itemMapper.selectById(item.getId()).getStatus())
                .as("市场保存报价单后，零件才进入已报价")
                .isEqualTo(QuoteStatusWorkflow.QUOTED);

        deliveryService.generateTickets(deliveryReq(order, item, 1));
        assertThat(itemMapper.selectById(item.getId()).getStatus())
                .as("部分提单时零件仍留在已报价池，并通过已提/剩余数量表达进度")
                .isEqualTo(QuoteStatusWorkflow.QUOTED);

        deliveryService.generateTickets(deliveryReq(order, item, 1));
        assertThat(itemMapper.selectById(item.getId()).getStatus())
                .as("累计提单数量达到原始数量后，零件才变为已提单")
                .isEqualTo("已提单");
    }

    @Test
    void batchedDeliveryUsesRemainingQuantityInsteadOfRejectingRepeatedItem() {
        OrderRfq order = new OrderRfq();
        order.setRfqNo("BATCH-DELIVERY-" + System.nanoTime());
        order.setCustomerName("分批提单客户");
        order.setStatus(QuoteStatusWorkflow.QUOTED);
        orderMapper.insert(order);

        ItemQuoteVersion item = quotedItem(order, "BATCH-100");
        item.setQty(10);
        item.setStatus(QuoteStatusWorkflow.QUOTED);
        item.setProcessRoute(List.of(Map.of("processName", "CNC", "inputVal", 2, "calcType", "工时")));
        itemMapper.updateById(item);

        List<OrderDeliveryTicket> firstBatch = deliveryService.generateTickets(deliveryReq(order, item, 4));
        assertThat(firstBatch).hasSize(1);
        assertThat(firstBatch.get(0).getQty()).isEqualTo(4);
        assertThat(itemMapper.selectById(item.getId()).getStatus()).isEqualTo(QuoteStatusWorkflow.QUOTED);
        assertThat(orderMapper.selectById(order.getId()).getStatus()).isEqualTo(QuoteStatusWorkflow.PARTIALLY_RELEASED);

        List<OrderDeliveryTicket> secondBatch = deliveryService.generateTickets(deliveryReq(order, item, 6));
        assertThat(secondBatch).hasSize(1);
        assertThat(secondBatch.get(0).getQty()).isEqualTo(6);

        QueryWrapper<OrderDeliveryTicket> ticketQw = new QueryWrapper<>();
        ticketQw.eq("rfq_id", order.getId()).eq("item_id", item.getId());
        assertThat(ticketMapper.selectList(ticketQw)).extracting(OrderDeliveryTicket::getQty)
                .containsExactly(4, 6);
        assertThat(itemMapper.selectById(item.getId()).getStatus()).isEqualTo("已提单");
        assertThat(orderMapper.selectById(order.getId()).getStatus()).isEqualTo(QuoteStatusWorkflow.RELEASED);
    }

    @Test
    void publishedDeliveryTicketsCannotBeDeleted() {
        OrderRfq order = new OrderRfq();
        order.setRfqNo("IMMUTABLE-TICKET-" + System.nanoTime());
        order.setCustomerName("不可逆提单客户");
        order.setStatus(QuoteStatusWorkflow.QUOTED);
        orderMapper.insert(order);

        ItemQuoteVersion item = quotedItem(order, "IMMUTABLE-100");
        List<OrderDeliveryTicket> tickets = deliveryService.generateTickets(deliveryReq(order, item, 1));

        assertThatThrownBy(() -> deliveryService.deleteTicket(tickets.get(0).getId()))
                .as("提单发布记录是 APS 同步来源，不允许删除回退")
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("不可删除");
        assertThat(ticketMapper.selectById(tickets.get(0).getId())).isNotNull();
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

    @Test
    void repeatQuoteApplicationDoesNotCompleteOrderAutomatically() {
        String customerName = "重复审核客户-" + System.nanoTime();

        OrderRfq historicalOrder = new OrderRfq();
        historicalOrder.setRfqNo("HIST-AUTO-" + System.nanoTime());
        historicalOrder.setCustomerName(customerName);
        historicalOrder.setStatus("已生成提单");
        orderMapper.insert(historicalOrder);

        ItemQuoteVersion historicalItem = new ItemQuoteVersion();
        historicalItem.setRfqId(historicalOrder.getId());
        historicalItem.setPartNo("AUTO-100");
        historicalItem.setVersion("A");
        historicalItem.setDescription("历史重复件");
        historicalItem.setQty(1);
        historicalItem.setDrawingUrl("/uploads/test/AUTO-100.pdf");
        historicalItem.setMaterialName("45#钢");
        historicalItem.setMaterialData(Map.of("name", "45#钢", "shape", "板材", "cost", 10));
        historicalItem.setProcessRoute(List.of(Map.of("processName", "CNC", "inputVal", 1, "calcType", "工时")));
        historicalItem.setFinalUnitPrice(20.0);
        historicalItem.setFinalTotalPrice(20.0);
        historicalItem.setStatus("已生成提单");
        itemMapper.insert(historicalItem);

        OrderDeliveryTicket ticket = new OrderDeliveryTicket();
        ticket.setRfqId(historicalOrder.getId());
        ticket.setItemId(historicalItem.getId());
        ticket.setPartNo(historicalItem.getPartNo());
        ticket.setVersion(historicalItem.getVersion());
        ticket.setQty(1);
        ticket.setArrangeType("计划");
        ticketMapper.insert(ticket);

        OrderRfq currentOrder = new OrderRfq();
        currentOrder.setRfqNo("CUR-AUTO-" + System.nanoTime());
        currentOrder.setCustomerName(customerName);
        currentOrder.setStatus("待工程部评估");
        orderMapper.insert(currentOrder);

        ItemQuoteVersion currentItem = new ItemQuoteVersion();
        currentItem.setRfqId(currentOrder.getId());
        currentItem.setPartNo("AUTO-100");
        currentItem.setVersion("A");
        currentItem.setDescription("当前重复件");
        currentItem.setQty(1);
        currentItem.setDrawingUrl("/uploads/test/AUTO-100.pdf");
        currentItem.setStatus("待核价");
        itemMapper.insert(currentItem);

        Map<String, Object> result = engineerService.applyRepeatQuotes(currentOrder.getId());

        ItemQuoteVersion savedItem = itemMapper.selectById(currentItem.getId());
        OrderRfq savedOrder = orderMapper.selectById(currentOrder.getId());
        assertThat(result.get("appliedCount")).isEqualTo(1);
        assertThat(savedItem.getFinalUnitPrice()).isNotNull();
        assertThat(savedItem.getStatus()).isEqualTo("已报价");
        assertThat(savedOrder.getStatus()).isEqualTo("待工程部评估");

        engineerService.completeOrderQuote(currentOrder.getId());
        assertThat(orderMapper.selectById(currentOrder.getId()).getStatus()).isEqualTo(QuoteStatusWorkflow.PRICED);
    }

    private ItemQuoteVersion quotedItem(OrderRfq order, String partNo) {
        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setRfqId(order.getId());
        item.setPartNo(partNo);
        item.setVersion("A");
        item.setDescription(partNo + "描述");
        item.setQty(1);
        item.setDrawingUrl("/uploads/test/" + partNo + ".pdf");
        item.setFinalUnitPrice(10.0);
        item.setFinalTotalPrice(10.0);
        item.setStatus("已报价");
        itemMapper.insert(item);
        return itemMapper.selectById(item.getId());
    }

    private DeliveryGenerateReq.DeliveryItemReq deliveryLine(ItemQuoteVersion item) {
        DeliveryGenerateReq.DeliveryItemReq line = new DeliveryGenerateReq.DeliveryItemReq();
        line.setItemId(item.getId());
        line.setAcceptedQty(item.getQty());
        return line;
    }

    private DeliveryGenerateReq deliveryReq(OrderRfq order, ItemQuoteVersion item, int acceptedQty) {
        DeliveryGenerateReq req = new DeliveryGenerateReq();
        req.setRfqId(order.getId());
        req.setInternalProjectNo("IP-" + order.getId() + "-" + acceptedQty);
        req.setCustomerOrderNo("CO-" + order.getId() + "-" + acceptedQty);
        req.setCustomerProjectNo("CP-" + order.getId());
        req.setArrangeType("计划");
        req.setStartDate(LocalDate.of(2026, 4, 1));
        DeliveryGenerateReq.DeliveryItemReq line = new DeliveryGenerateReq.DeliveryItemReq();
        line.setItemId(item.getId());
        line.setAcceptedQty(acceptedQty);
        req.setItems(List.of(line));
        return req;
    }

    private OrderRfq orderWithItemsTicketsAndSheets(
            String customerName,
            String rfqPrefix,
            String status,
            int itemCount,
            int quotedCount,
            int missingDrawingCount,
            int ticketCount,
            int processSheetCount) {
        OrderRfq order = new OrderRfq();
        order.setRfqNo(rfqPrefix + "-" + System.nanoTime());
        order.setCustomerName(customerName);
        order.setStatus(status);
        orderMapper.insert(order);

        for (int i = 0; i < itemCount; i++) {
            ItemQuoteVersion item = new ItemQuoteVersion();
            item.setRfqId(order.getId());
            item.setPartNo(rfqPrefix + "-P" + i);
            item.setVersion("A");
            item.setDescription("分页测试零件");
            item.setQty(1);
            item.setStatus(i < quotedCount ? "已报价" : "待核价");
            if (i < quotedCount) {
                item.setFinalUnitPrice(10.0);
                item.setFinalTotalPrice(10.0);
            }
            if (i >= missingDrawingCount) {
                item.setDrawingUrl("/uploads/test/" + rfqPrefix + "-P" + i + ".pdf");
            }
            itemMapper.insert(item);
        }

        for (int i = 0; i < ticketCount; i++) {
            OrderDeliveryTicket ticket = new OrderDeliveryTicket();
            ticket.setRfqId(order.getId());
            ticket.setPartNo(rfqPrefix + "-T" + i);
            ticket.setQty(3);
            ticketMapper.insert(ticket);
        }

        for (int i = 0; i < processSheetCount; i++) {
            OrderProcessSheet sheet = new OrderProcessSheet();
            sheet.setRfqId(order.getId());
            sheet.setPartNo(rfqPrefix + "-S" + i);
            sheet.setQty(1);
            processSheetMapper.insert(sheet);
        }
        return order;
    }
}
