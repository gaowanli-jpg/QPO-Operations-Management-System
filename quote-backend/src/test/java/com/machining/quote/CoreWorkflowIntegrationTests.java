package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
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
import com.machining.quote.entity.CustomerMaster;
import com.machining.quote.entity.DictProcessCategory;
import com.machining.quote.entity.DictProcessDetail;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.dto.ProcessSheetDTO;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.DictMaterialMapper;
import com.machining.quote.mapper.CustomerMasterMapper;
import com.machining.quote.mapper.DictProcessCategoryMapper;
import com.machining.quote.mapper.DictProcessDetailMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.service.aps.ApsSourceService;
import com.machining.quote.service.AuditLogRetentionService;
import com.machining.quote.service.DeliveryService;
import com.machining.quote.service.EngineerService;
import com.machining.quote.service.InternalTargetLeadTimeService;
import com.machining.quote.service.OrderManagementService;
import com.machining.quote.service.QuoteProcessService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest(properties = {
        "erp.auth.enabled=false",
        "erp.upload-dir=${java.io.tmpdir}/quote-test/uploads/",
        "erp.backup-dir=${java.io.tmpdir}/quote-test/backups/",
        "spring.task.scheduling.enabled=false"
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

    @MockBean
    private AuditLogRetentionService auditLogRetentionService;
    @Autowired
    private CustomerMasterMapper customerMapper;
    @Autowired
    private DictProcessCategoryMapper processCategoryMapper;
    @Autowired
    private DictProcessDetailMapper processDetailMapper;

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
        assertThat(summary.get("processSheetCount")).isEqualTo(1);

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
    void releaseOrderSelectionOnlyReturnsOrdersThatCanStillGenerateTickets() {
        String customerName = "提单候选客户-" + System.nanoTime();
        OrderRfq quoted = orderWithItemsTicketsAndSheets(customerName, "REL-A", "已报价", 1, 1, 0, 0, 0);
        OrderRfq partial = orderWithItemsTicketsAndSheets(customerName, "REL-B", "部分已提单", 1, 1, 0, 1, 1);
        orderWithItemsTicketsAndSheets(customerName, "REL-C", "已生成提单", 1, 1, 0, 1, 1);

        PageResult<OrderRfq> candidates = orderManagementService.listReleaseCandidateOrders(
                customerName, null, null, 1, 20);

        assertThat(candidates.getRecords())
                .extracting(OrderRfq::getId)
                .containsExactly(partial.getId(), quoted.getId());

        PageResult<OrderRfq> fullyReleased = orderManagementService.listReleaseCandidateOrders(
                customerName, null, "已生成提单", 1, 20);
        assertThat(fullyReleased.getTotal()).isZero();
        assertThat(fullyReleased.getRecords()).isEmpty();
    }

    @Test
    void quoteProcessOrderSelectionReturnsPricedAndQuotedOrders() {
        String customerName = "报价处理候选客户-" + System.nanoTime();
        OrderRfq priced = orderWithItemsTicketsAndSheets(customerName, "QP-A", "已核价", 1, 1, 0, 0, 0);
        OrderRfq quoted = orderWithItemsTicketsAndSheets(customerName, "QP-B", "已报价", 1, 1, 0, 0, 0);
        orderWithItemsTicketsAndSheets(customerName, "QP-C", "未核价", 1, 0, 0, 0, 0);
        orderWithItemsTicketsAndSheets(customerName, "QP-D", "部分已提单", 1, 1, 0, 1, 1);

        PageResult<OrderRfq> candidates = orderManagementService.listQuoteProcessCandidateOrders(
                customerName, null, null, 1, 20);

        assertThat(candidates.getRecords())
                .extracting(OrderRfq::getId)
                .containsExactly(quoted.getId(), priced.getId());

        PageResult<OrderRfq> pricedOnly = orderManagementService.listQuoteProcessCandidateOrders(
                customerName, null, "已核价", 1, 20);
        assertThat(pricedOnly.getRecords())
                .extracting(OrderRfq::getId)
                .containsExactly(priced.getId());

        PageResult<OrderRfq> partialReleased = orderManagementService.listQuoteProcessCandidateOrders(
                customerName, null, "部分已提单", 1, 20);
        assertThat(partialReleased.getTotal()).isZero();
        assertThat(partialReleased.getRecords()).isEmpty();
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
        material.setMaterialCode("MAT-TEST-" + System.nanoTime());
        material.setMaterialName(materialName);
        material.setMaterialTypeId(1L);
        material.setMaterialCategory("\u94a2\u94c1");
        material.setEnabled(true);
        material.setSortNo(0);
        material.setRemarks("");
        material.setPlateDensity(7850.0);
        material.setPlatePrice(7.1);
        materialMapper.insert(material);

        QuoteSubmitReq req = new QuoteSubmitReq();
        req.setItemId(item.getId());
        req.setPartNetWeight(1.23);
        Map<String, Object> materialData = new LinkedHashMap<>();
        materialData.put("materialCode", material.getMaterialCode());
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
        order.setRfqNo("QUOTE-date-" + System.nanoTime());
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
        order.setRfqNo("QUOTE-BAD-date-" + System.nanoTime());
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
        order.setRfqNo("INTERNAL-date-" + System.nanoTime());
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
        req.setRfqIds(List.of(order.getId()));
        req.setOrderType("正式订单");
        req.setInternalProjectNo("IP-date");
        req.setCustomerOrderNo("CO-date");
        DeliveryGenerateReq.DeliveryItemReq line = new DeliveryGenerateReq.DeliveryItemReq();
        line.setItemId(item.getId());
        line.setAcceptedQty(1);
        req.setItems(List.of(line));

        ensureCustomer(order.getCustomerName());
        List<OrderDeliveryTicket> tickets = deliveryService.generateTickets(req);

        assertThat(tickets).extracting(OrderDeliveryTicket::getDeliveryDate)
                .containsOnly(LocalDate.of(2026, 4, 10));
        assertThat(deliveryService.listProcessSheets(order.getId())).extracting(ProcessSheetDTO::getDeliveryDate)
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
        item.setTaxIncludedUnitPrice(10.0);
        item.setTaxIncludedTotalPrice(50.0);
        item.setStatus("已报价");
        itemMapper.insert(item);

        DeliveryGenerateReq req = new DeliveryGenerateReq();
        req.setRfqIds(List.of(order.getId()));
        req.setOrderType("正式订单");
        req.setInternalProjectNo("IP-001");
        req.setCustomerOrderNo("CO-001");
        req.setCustomerProjectNo("CP-001");
        DeliveryGenerateReq.DeliveryItemReq line = new DeliveryGenerateReq.DeliveryItemReq();
        line.setItemId(item.getId());
        line.setAcceptedQty(5);
        req.setItems(List.of(line));

        ensureCustomer(order.getCustomerName());
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
        assertThat(itemMapper.selectOne(itemQw).getStatus()).isEqualTo("已提单");

        assertThat(deliveryService.listProcessSheets(order.getId())).extracting(ProcessSheetDTO::getDeliveryDate)
                .containsOnly(LocalDate.of(2026, 3, 22));
        assertThat(deliveryService.listProcessSheets(order.getId())).extracting(ProcessSheetDTO::getInternalProjectNo)
                .containsOnly("IP-001");
        assertThat(deliveryService.listProcessSheets(order.getId())).extracting(ProcessSheetDTO::getItemType)
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
        req.setRfqIds(List.of(order.getId()));
        req.setOrderType("正式订单");
        req.setInternalProjectNo("IP-SEQUENCE");
        req.setCustomerOrderNo("CO-SEQUENCE");
        req.setItems(List.of(
                deliveryLine(first),
                deliveryLine(second),
                deliveryLine(third)
        ));

        ensureCustomer(order.getCustomerName());
        deliveryService.generateTickets(req);

        assertThat(deliveryService.listTickets(order.getId()))
                .extracting(OrderDeliveryTicket::getPartNo)
                .containsExactly("SEQ-A", "SEQ-B", "SEQ-C");
        assertThat(deliveryService.listProcessSheets(order.getId()))
                .extracting(ProcessSheetDTO::getPartNo)
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
        material.setMaterialCode("MAT-ENG-" + System.nanoTime());
        material.setMaterialName(engineeringMaterial);
        material.setMaterialTypeId(1L);
        material.setMaterialCategory("\u94a2\u94c1");
        material.setEnabled(true);
        material.setSortNo(0);
        material.setRemarks("");
        material.setPlateDensity(7850.0);
        material.setPlatePrice(7.1);
        materialMapper.insert(material);

        Map<String, Object> materialData = new LinkedHashMap<>();
        materialData.put("materialCode", material.getMaterialCode());
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
        QuoteSubmitReq.ProcessSubmitDTO process = new QuoteSubmitReq.ProcessSubmitDTO();
        process.setSortNo(10);
        process.setProcessDetailId(ensureTestProcess("TEST_APS_CNC", "APS 测试工艺", "APS-CNC", 10.0).detailId());
        process.setUnitWorkHours(2.0);
        quoteReq.setProcessRoute(List.of(process));
        engineerService.calculateAndSaveQuote(quoteReq);

        ItemQuoteVersion savedItem = itemMapper.selectById(item.getId());
        assertThat(savedItem.getMaterialName()).isEqualTo("IMPORTED-MATERIAL-DESCRIPTION");
        assertThat(savedItem.getEngineeringMaterialName()).isEqualTo(engineeringMaterial);
        Map<String, Object> marketRow = internalTargetLeadTimeService.listItemRows(order.getId()).get(0);
        assertThat(marketRow.get("materialName")).isEqualTo("IMPORTED-MATERIAL-DESCRIPTION");
        assertThat(marketRow.get("engineeringMaterialName"))
                .isEqualTo(engineeringMaterial);

        DeliveryGenerateReq deliveryReq = new DeliveryGenerateReq();
        deliveryReq.setRfqIds(List.of(order.getId()));
        deliveryReq.setOrderType("正式订单");
        deliveryReq.setInternalProjectNo("IP-APS-MAT");
        deliveryReq.setCustomerOrderNo("CO-APS-MAT");
        deliveryReq.setCustomerProjectNo("CP-APS-MAT");
        DeliveryGenerateReq.DeliveryItemReq line = new DeliveryGenerateReq.DeliveryItemReq();
        line.setItemId(item.getId());
        line.setAcceptedQty(3);
        deliveryReq.setItems(List.of(line));

        ensureCustomer(order.getCustomerName());
        OrderDeliveryTicket ticket = deliveryService.generateTickets(deliveryReq).get(0);
        assertThat(ticket.getMaterialName()).isEqualTo("IMPORTED-MATERIAL-DESCRIPTION");
        assertThat(ticket.getEngineeringMaterialName()).isEqualTo(engineeringMaterial);
        assertThat(ticket.getMaterialData()).isNotNull();
        assertThat(ticket.getProcessRoute()).isNotNull();
        assertThat(ticket.getMaterialGrossWeight()).isEqualTo(2.5);
        assertThat(ticket.getPartNetWeight()).isEqualTo(1.2);

        ProcessSheetDTO sheet = deliveryService.listProcessSheets(order.getId()).get(0);
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
        assertThat(row.getProcessSnapshotSource()).isEqualTo("ORDER_DELIVERY_TICKET");
        assertThat(row.getProcessRows()).extracting(ApsDeliveryItemDTO.ProcessRow::getProcessName)
                .containsExactly("APS-CNC");
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
        material.setMaterialCode("MAT-STATUS-" + System.nanoTime());
        material.setMaterialName(materialName);
        material.setMaterialTypeId(1L);
        material.setMaterialCategory("\u94a2\u94c1");
        material.setEnabled(true);
        material.setSortNo(0);
        material.setRemarks("");
        material.setPlateDensity(7850.0);
        material.setPlatePrice(7.1);
        materialMapper.insert(material);

        QuoteSubmitReq quoteReq = new QuoteSubmitReq();
        quoteReq.setItemId(item.getId());
        quoteReq.setPartNetWeight(1.0);
        quoteReq.setMaterialData(Map.of(
                "materialCode", material.getMaterialCode(),
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

        ensureCustomer(order.getCustomerName());
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
        item.setProcessRoute(List.of(standardProcessRoute("TEST_CNC", "测试数控", "CNC", 10.0, 2.0)));
        itemMapper.updateById(item);

        ensureCustomer(order.getCustomerName());
        List<OrderDeliveryTicket> firstBatch = deliveryService.generateTickets(deliveryReq(order, item, 4, "预测订单"));
        assertThat(firstBatch).hasSize(1);
        assertThat(firstBatch.get(0).getQty()).isEqualTo(4);
        assertThat(firstBatch.get(0).getOrderType()).isEqualTo("预测订单");
        assertThat(itemMapper.selectById(item.getId()).getStatus()).isEqualTo(QuoteStatusWorkflow.QUOTED);
        assertThat(orderMapper.selectById(order.getId()).getStatus()).isEqualTo(QuoteStatusWorkflow.PARTIALLY_RELEASED);

        List<OrderDeliveryTicket> secondBatch = deliveryService.generateTickets(deliveryReq(order, item, 6, "正式订单"));
        assertThat(secondBatch).hasSize(1);
        assertThat(secondBatch.get(0).getQty()).isEqualTo(6);
        assertThat(secondBatch.get(0).getOrderType()).isEqualTo("预测订单");

        QueryWrapper<OrderDeliveryTicket> ticketQw = new QueryWrapper<>();
        ticketQw.eq("rfq_id", order.getId()).eq("item_id", item.getId());
        List<OrderDeliveryTicket> tickets = ticketMapper.selectList(ticketQw);
        assertThat(tickets).extracting(OrderDeliveryTicket::getQty)
                .containsExactly(4, 6);
        assertThat(tickets).extracting(OrderDeliveryTicket::getOrderType)
                .containsExactly("预测订单", "预测订单");
        assertThat(tickets).allSatisfy(ticket -> assertThat(ticket.getProcessRoute()).isNotNull());

        List<ProcessSheetDTO> sheets = deliveryService.listProcessSheets(order.getId());
        assertThat(sheets).extracting(ProcessSheetDTO::getQty).containsExactly(4, 6);
        assertThat(sheets).allSatisfy(sheet -> assertThat(sheet.getProcessRoute()).isNotNull());

        List<ApsDeliveryItemDTO> apsRows = apsSourceService.listDeliveryItems(tickets.get(0).getId() - 1, 10).getRecords().stream()
                .filter(row -> row.getTicketId().equals(tickets.get(0).getId()) || row.getTicketId().equals(tickets.get(1).getId()))
                .toList();
        assertThat(apsRows).extracting(ApsDeliveryItemDTO::getQty).containsExactly(4, 6);
        assertThat(apsRows).allSatisfy(row -> assertThat(row.getProcessSnapshotSource()).isEqualTo("ORDER_DELIVERY_TICKET"));
        assertThat(itemMapper.selectById(item.getId()).getStatus()).isEqualTo("已提单");
        assertThat(orderMapper.selectById(order.getId()).getStatus()).isEqualTo(QuoteStatusWorkflow.RELEASED);
    }

    @Test
    void releasedOrderCanOpenReleaseWorkspaceForExportsButCannotGenerateAgain() {
        OrderRfq order = new OrderRfq();
        order.setRfqNo("RELEASED-WORKSPACE-" + System.nanoTime());
        order.setCustomerName("Released workspace customer");
        order.setStatus(QuoteStatusWorkflow.QUOTED);
        orderMapper.insert(order);

        ItemQuoteVersion item = quotedItem(order, "RELEASED-WORKSPACE-100");
        ensureCustomer(order.getCustomerName());
        deliveryService.generateTickets(deliveryReq(order, item, 1));
        assertThat(orderMapper.selectById(order.getId()).getStatus()).isEqualTo(QuoteStatusWorkflow.RELEASED);

        assertThat(internalTargetLeadTimeService.listItemRows(order.getId()))
                .as("Fully released orders still need to open the release workspace so users can export tickets and process sheets")
                .hasSize(1)
                .first()
                .extracting(row -> row.get("remainingQty"))
                .isEqualTo(0);

        assertThat(deliveryService.listTickets(order.getId())).hasSize(1);
        assertThat(deliveryService.listProcessSheets(order.getId())).hasSize(1);
        assertThatThrownBy(() -> deliveryService.generateTickets(deliveryReq(order, item, 1)))
                .as("Opening the workspace for exports must not reopen ticket generation")
                .hasMessageContaining("已报价");
    }

    @Test
    void publishedDeliveryTicketsCannotBeDeleted() throws Exception {
        OrderRfq order = new OrderRfq();
        order.setRfqNo("IMMUTABLE-TICKET-" + System.nanoTime());
        order.setCustomerName("不可逆提单客户");
        order.setStatus(QuoteStatusWorkflow.QUOTED);
        orderMapper.insert(order);

        ItemQuoteVersion item = quotedItem(order, "IMMUTABLE-100");
        ensureCustomer(order.getCustomerName());
        List<OrderDeliveryTicket> tickets = deliveryService.generateTickets(deliveryReq(order, item, 1));

        assertThat(ticketMapper.selectById(tickets.get(0).getId())).isNotNull();
        assertThat(source("src/main/java/com/machining/quote/controller/MarketController.java"))
                .as("提单行和工艺表行不再暴露删除接口")
                .doesNotContain("@DeleteMapping(\"/tickets/{ticketId}\")")
                .doesNotContain("@DeleteMapping(\"/process-sheets/{sheetId}\")");
        assertThat(source("src/main/java/com/machining/quote/service/DeliveryService.java"))
                .as("提单发布记录是 APS 同步来源，不允许保留删除服务方法")
                .doesNotContain("public void deleteTicket(")
                .doesNotContain("public void deleteProcessSheet(")
                .doesNotContain("DELIVERY_TICKET_DELETE")
                .doesNotContain("PROCESS_SHEET_DELETE");
        assertThat(source("src/main/java/com/machining/quote/controller/OrderManagementController.java"))
                .as("询价订单不再暴露重置提单/工艺接口")
                .doesNotContain("reset-production");
        assertThat(source("src/main/java/com/machining/quote/service/OrderManagementService.java"))
                .as("询价订单不再保留重置提单/工艺服务方法")
                .doesNotContain("resetProductionData")
                .doesNotContain("Order production reset completed");
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
        applyStandardMaterialSnapshot(historicalItem, 10.0);
        historicalItem.setProcessRoute(List.of(standardProcessRoute("TEST_CNC", "测试数控", "CNC", 10.0, 1.0)));
        historicalItem.setTaxIncludedUnitPrice(20.0);
        historicalItem.setTaxIncludedTotalPrice(20.0);
        historicalItem.setStatus(QuoteStatusWorkflow.ITEM_RELEASED);
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
    void repeatQuoteReusesReleasedHistoryAcrossCustomersByPartVersionOnly() {
        OrderRfq historicalOrder = new OrderRfq();
        historicalOrder.setRfqNo("HIST-CROSS-" + System.nanoTime());
        historicalOrder.setCustomerName("历史客户");
        historicalOrder.setStatus(QuoteStatusWorkflow.RELEASED);
        orderMapper.insert(historicalOrder);
        releasedReusableItem(historicalOrder, "CROSS-100", "A", 20.0);

        OrderRfq currentOrder = new OrderRfq();
        currentOrder.setRfqNo("CUR-CROSS-" + System.nanoTime());
        currentOrder.setCustomerName("当前客户");
        currentOrder.setStatus("待工程部评估");
        orderMapper.insert(currentOrder);
        ItemQuoteVersion currentItem = pendingItem(currentOrder, "CROSS-100", "A");

        Map<String, Object> result = engineerService.applyRepeatQuotes(currentOrder.getId());

        ItemQuoteVersion saved = itemMapper.selectById(currentItem.getId());
        assertThat(result.get("appliedCount")).isEqualTo(1);
        assertThat(saved.getTaxIncludedUnitPrice()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(QuoteStatusWorkflow.PRICED);
    }

    @Test
    @SuppressWarnings("unchecked")
    void repeatQuoteCopiesConfirmedMaterialNoToRfqMainItem() {
        OrderRfq historicalOrder = new OrderRfq();
        historicalOrder.setRfqNo("HIST-MATERIAL-NO-" + System.nanoTime());
        historicalOrder.setCustomerName("历史物料号客户");
        historicalOrder.setStatus(QuoteStatusWorkflow.RELEASED);
        orderMapper.insert(historicalOrder);
        ItemQuoteVersion historicalItem = releasedReusableItem(historicalOrder, "RFQ-MAT-001", "A", 20.0);
        historicalItem.setMaterialNo("MES-RFQ-001");
        historicalItem.setMaterialNoStatus("已确认");
        historicalItem.setMaterialNoSource("MES_TICKET");
        historicalItem.setMaterialNoSourceTicketId(7001L);
        itemMapper.updateById(historicalItem);

        OrderRfq currentOrder = new OrderRfq();
        currentOrder.setRfqNo("CUR-MATERIAL-NO-" + System.nanoTime());
        currentOrder.setCustomerName("当前物料号客户");
        currentOrder.setStatus("待工程部评估");
        orderMapper.insert(currentOrder);
        ItemQuoteVersion currentItem = pendingItem(currentOrder, "RFQ-MAT-001", "A");

        Map<String, Object> result = engineerService.applyRepeatQuotes(currentOrder.getId());

        ItemQuoteVersion saved = itemMapper.selectById(currentItem.getId());
        assertThat(result.get("appliedCount")).isEqualTo(1);
        assertThat(result.get("materialNoCopiedCount")).isEqualTo(1);
        assertThat(result.get("materialNoSkippedCount")).isEqualTo(0);
        assertThat(result.get("materialNoConflictCount")).isEqualTo(0);
        assertThat(saved.getMaterialNo()).isEqualTo("MES-RFQ-001");
        assertThat(saved.getMaterialNoStatus()).isEqualTo("已确认");
        assertThat(saved.getMaterialNoSource()).isEqualTo("MES_TICKET");
        assertThat(saved.getMaterialNoSourceTicketId()).isEqualTo(7001L);
        List<Map<String, Object>> appliedItems = (List<Map<String, Object>>) result.get("appliedItems");
        assertThat(appliedItems).singleElement()
                .satisfies(row -> assertThat(row.get("materialNoReuseStatus")).isEqualTo("COPIED"));
    }

    @Test
    void repeatQuoteReusesQuoteSnapshotWhenSourceMaterialNoIsUnconfirmed() {
        OrderRfq historicalOrder = new OrderRfq();
        historicalOrder.setRfqNo("HIST-UNCONFIRMED-MATERIAL-" + System.nanoTime());
        historicalOrder.setCustomerName("未确认物料号客户");
        historicalOrder.setStatus(QuoteStatusWorkflow.RELEASED);
        orderMapper.insert(historicalOrder);
        ItemQuoteVersion historicalItem = releasedReusableItem(historicalOrder, "RFQ-MAT-002", "A", 22.0);
        historicalItem.setMaterialNo(null);
        historicalItem.setMaterialNoStatus("未确认");
        itemMapper.updateById(historicalItem);

        OrderRfq currentOrder = new OrderRfq();
        currentOrder.setRfqNo("CUR-UNCONFIRMED-MATERIAL-" + System.nanoTime());
        currentOrder.setCustomerName("当前未确认物料号客户");
        currentOrder.setStatus("待工程部评估");
        orderMapper.insert(currentOrder);
        ItemQuoteVersion currentItem = pendingItem(currentOrder, "RFQ-MAT-002", "A");

        Map<String, Object> result = engineerService.applyRepeatQuotes(currentOrder.getId());

        ItemQuoteVersion saved = itemMapper.selectById(currentItem.getId());
        assertThat(result.get("appliedCount")).isEqualTo(1);
        assertThat(result.get("materialNoCopiedCount")).isEqualTo(0);
        assertThat(result.get("materialNoSkippedCount")).isEqualTo(1);
        assertThat(result.get("materialNoConflictCount")).isEqualTo(0);
        assertThat(saved.getTaxIncludedUnitPrice()).isNotNull();
        assertThat(saved.getMaterialNo()).isNull();
        assertThat(saved.getMaterialNoStatus()).isEqualTo("未确认");
    }

    @Test
    @SuppressWarnings("unchecked")
    void repeatQuoteRepricesHistoricalProcessRouteWithCurrentCategoryRate() {
        OrderRfq historicalOrder = new OrderRfq();
        historicalOrder.setRfqNo("HIST-PROCESS-REPRICE-" + System.nanoTime());
        historicalOrder.setCustomerName("历史工艺重算客户");
        historicalOrder.setStatus(QuoteStatusWorkflow.RELEASED);
        orderMapper.insert(historicalOrder);
        ItemQuoteVersion historicalItem = releasedReusableItem(historicalOrder, "RFQ-PROCESS-REPRICE", "A", 20.0);

        Map<String, Object> historicalRoute = ((List<Map<String, Object>>) historicalItem.getProcessRoute()).get(0);
        DictProcessCategory category = processCategoryMapper.selectById(((Number) historicalRoute.get("processCategoryId")).longValue());
        category.setHourlyRate(new BigDecimal("25.00"));
        processCategoryMapper.updateById(category);

        OrderRfq currentOrder = new OrderRfq();
        currentOrder.setRfqNo("CUR-PROCESS-REPRICE-" + System.nanoTime());
        currentOrder.setCustomerName("当前工艺重算客户");
        currentOrder.setStatus("待工程部评估");
        orderMapper.insert(currentOrder);
        ItemQuoteVersion currentItem = pendingItem(currentOrder, "RFQ-PROCESS-REPRICE", "A");

        Map<String, Object> result = engineerService.applyRepeatQuotes(currentOrder.getId());

        ItemQuoteVersion saved = itemMapper.selectById(currentItem.getId());
        List<Map<String, Object>> routes = (List<Map<String, Object>>) saved.getProcessRoute();
        assertThat(result.get("appliedCount")).isEqualTo(1);
        assertThat(routes).singleElement().satisfies(route -> {
            assertThat(route).containsEntry("routeType", "PROCESS");
            assertThat(route.get("processDetailId")).isEqualTo(historicalRoute.get("processDetailId"));
            assertThat((Double) route.get("unitWorkHours")).isEqualTo(1.0);
            assertThat((Double) route.get("hourlyRate")).isEqualTo(25.0);
            assertThat((Double) route.get("lineCost")).isEqualTo(25.0);
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void repeatQuoteSkipsWhenHistoricalProcessDetailIsDisabled() {
        OrderRfq historicalOrder = new OrderRfq();
        historicalOrder.setRfqNo("HIST-PROCESS-DISABLED-" + System.nanoTime());
        historicalOrder.setCustomerName("历史工艺禁用客户");
        historicalOrder.setStatus(QuoteStatusWorkflow.RELEASED);
        orderMapper.insert(historicalOrder);
        ItemQuoteVersion historicalItem = releasedReusableItem(historicalOrder, "RFQ-PROCESS-DISABLED", "A", 20.0);

        Map<String, Object> historicalRoute = ((List<Map<String, Object>>) historicalItem.getProcessRoute()).get(0);
        DictProcessDetail detail = processDetailMapper.selectById(((Number) historicalRoute.get("processDetailId")).longValue());
        detail.setEnabled(false);
        processDetailMapper.updateById(detail);

        OrderRfq currentOrder = new OrderRfq();
        currentOrder.setRfqNo("CUR-PROCESS-DISABLED-" + System.nanoTime());
        currentOrder.setCustomerName("当前工艺禁用客户");
        currentOrder.setStatus("待工程部评估");
        orderMapper.insert(currentOrder);
        ItemQuoteVersion currentItem = pendingItem(currentOrder, "RFQ-PROCESS-DISABLED", "A");

        Map<String, Object> result = engineerService.applyRepeatQuotes(currentOrder.getId());

        ItemQuoteVersion saved = itemMapper.selectById(currentItem.getId());
        List<Map<String, Object>> skippedItems = (List<Map<String, Object>>) result.get("skippedItems");
        assertThat(result.get("appliedCount")).isEqualTo(0);
        assertThat(saved.getTaxIncludedUnitPrice()).isNull();
        assertThat(saved.getProcessRoute()).isNull();
        assertThat(skippedItems).extracting(row -> row.get("reason").toString())
                .anyMatch(reason -> reason.contains("具体工艺已停用") && reason.contains("需手动补齐"));
    }

    @Test
    void repeatQuoteKeepsExistingRfqMaterialNoWhenHistoricalMaterialNoConflicts() {
        OrderRfq historicalOrder = new OrderRfq();
        historicalOrder.setRfqNo("HIST-CONFLICT-MATERIAL-" + System.nanoTime());
        historicalOrder.setCustomerName("冲突物料号客户");
        historicalOrder.setStatus(QuoteStatusWorkflow.RELEASED);
        orderMapper.insert(historicalOrder);
        ItemQuoteVersion historicalItem = releasedReusableItem(historicalOrder, "RFQ-MAT-003", "A", 24.0);
        historicalItem.setMaterialNo("MES-RFQ-HISTORY");
        historicalItem.setMaterialNoStatus("已确认");
        itemMapper.updateById(historicalItem);

        OrderRfq currentOrder = new OrderRfq();
        currentOrder.setRfqNo("CUR-CONFLICT-MATERIAL-" + System.nanoTime());
        currentOrder.setCustomerName("当前冲突物料号客户");
        currentOrder.setStatus("待工程部评估");
        orderMapper.insert(currentOrder);
        ItemQuoteVersion currentItem = pendingItem(currentOrder, "RFQ-MAT-003", "A");
        currentItem.setMaterialNo("MES-RFQ-CURRENT");
        currentItem.setMaterialNoStatus("已确认");
        itemMapper.updateById(currentItem);

        Map<String, Object> result = engineerService.applyRepeatQuotes(currentOrder.getId());

        ItemQuoteVersion saved = itemMapper.selectById(currentItem.getId());
        assertThat(result.get("appliedCount")).isEqualTo(1);
        assertThat(result.get("materialNoCopiedCount")).isEqualTo(0);
        assertThat(result.get("materialNoSkippedCount")).isEqualTo(0);
        assertThat(result.get("materialNoConflictCount")).isEqualTo(1);
        assertThat(saved.getMaterialNo()).isEqualTo("MES-RFQ-CURRENT");
        assertThat(saved.getMaterialNoStatus()).isEqualTo("已确认");
        assertThat(saved.getTaxIncludedUnitPrice()).isNotNull();
    }

    @Test
    void repeatQuoteIgnoresNonReleasedHistoryEvenWhenDeliveryTicketExists() {
        String customerName = "非已提单重复客户-" + System.nanoTime();
        OrderRfq historicalOrder = new OrderRfq();
        historicalOrder.setRfqNo("HIST-NON-REL-" + System.nanoTime());
        historicalOrder.setCustomerName(customerName);
        historicalOrder.setStatus(QuoteStatusWorkflow.QUOTED);
        orderMapper.insert(historicalOrder);
        ItemQuoteVersion historicalItem = reusableItem(historicalOrder, "NONREL-100", "A", 20.0, QuoteStatusWorkflow.QUOTED);

        OrderDeliveryTicket ticket = new OrderDeliveryTicket();
        ticket.setRfqId(historicalOrder.getId());
        ticket.setItemId(historicalItem.getId());
        ticket.setPartNo(historicalItem.getPartNo());
        ticket.setVersion(historicalItem.getVersion());
        ticket.setQty(1);
        ticket.setArrangeType("计划");
        ticketMapper.insert(ticket);

        OrderRfq currentOrder = new OrderRfq();
        currentOrder.setRfqNo("CUR-NON-REL-" + System.nanoTime());
        currentOrder.setCustomerName(customerName);
        currentOrder.setStatus("待工程部评估");
        orderMapper.insert(currentOrder);
        ItemQuoteVersion currentItem = pendingItem(currentOrder, "NONREL-100", "A");

        Map<String, Object> result = engineerService.applyRepeatQuotes(currentOrder.getId());

        ItemQuoteVersion saved = itemMapper.selectById(currentItem.getId());
        assertThat(result.get("appliedCount")).isEqualTo(0);
        assertThat(saved.getTaxIncludedUnitPrice()).isNull();
        assertThat(saved.getStatus()).isEqualTo("待核价");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> skippedItems = (List<Map<String, Object>>) result.get("skippedItems");
        assertThat(skippedItems).extracting(row -> row.get("reason").toString())
                .contains("未找到已提单历史重复件");
    }

    @Test
    @SuppressWarnings("unchecked")
    void repeatAnalysisUsesNewestReleasedHistoryForSamePartVersion() {
        OrderRfq olderOrder = new OrderRfq();
        olderOrder.setRfqNo("HIST-OLDER-" + System.nanoTime());
        olderOrder.setCustomerName("旧客户");
        olderOrder.setStatus(QuoteStatusWorkflow.RELEASED);
        orderMapper.insert(olderOrder);
        ItemQuoteVersion older = releasedReusableItem(olderOrder, "LATEST-100", "A", 10.0);

        OrderRfq newerOrder = new OrderRfq();
        newerOrder.setRfqNo("HIST-NEWER-" + System.nanoTime());
        newerOrder.setCustomerName("新客户");
        newerOrder.setStatus(QuoteStatusWorkflow.RELEASED);
        orderMapper.insert(newerOrder);
        ItemQuoteVersion newer = releasedReusableItem(newerOrder, "LATEST-100", "A", 30.0);

        OrderRfq currentOrder = new OrderRfq();
        currentOrder.setRfqNo("CUR-LATEST-" + System.nanoTime());
        currentOrder.setCustomerName("当前客户");
        currentOrder.setStatus("待工程部评估");
        orderMapper.insert(currentOrder);
        pendingItem(currentOrder, "LATEST-100", "A");

        Map<String, Object> result = engineerService.analyzeRepeatOrder(currentOrder.getId());

        assertThat(result.get("repeatCount")).isEqualTo(1);
        List<Map<String, Object>> repeatItems = (List<Map<String, Object>>) result.get("repeatItems");
        Map<String, Object> row = repeatItems.get(0);
        assertThat(row.get("sourceItemId")).isEqualTo(newer.getId());
        assertThat(row.get("sourceItemId")).isNotEqualTo(older.getId());
        assertThat(row.get("sourceTaxIncludedUnitPrice")).isEqualTo(30.0);
    }

    @Test
    void repeatQuoteBatchesLargeCurrentPartVersionSets() {
        OrderRfq historicalOrder = new OrderRfq();
        historicalOrder.setRfqNo("HIST-BATCH-" + System.nanoTime());
        historicalOrder.setCustomerName("批量历史客户");
        historicalOrder.setStatus(QuoteStatusWorkflow.RELEASED);
        orderMapper.insert(historicalOrder);

        OrderRfq currentOrder = new OrderRfq();
        currentOrder.setRfqNo("CUR-BATCH-" + System.nanoTime());
        currentOrder.setCustomerName("批量当前客户");
        currentOrder.setStatus("待工程部评估");
        orderMapper.insert(currentOrder);

        int itemCount = 505;
        for (int i = 0; i < itemCount; i++) {
            String partNo = "BATCH-" + i;
            releasedReusableItem(historicalOrder, partNo, "A", 20.0 + i);
            pendingItem(currentOrder, partNo, "A");
        }

        Map<String, Object> result = engineerService.applyRepeatQuotes(currentOrder.getId());

        assertThat(result.get("appliedCount")).isEqualTo(itemCount);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void repeatSourceImplementationUsesReleasedItemLookupWithoutDeliveryTicketCandidatePath() throws Exception {
        String service = source("src/main/java/com/machining/quote/service/EngineerService.java");
        String repeatMethod = methodBody(service, "private Map<String, RepeatSource> findRepeatSources", "private boolean hasReusableQuote");

        assertThat(repeatMethod)
                .as("重复件候选入口应直接查 item_quote_version，不能再依赖 order_delivery_ticket")
                .doesNotContain("OrderDeliveryTicket")
                .doesNotContain("ticketMapper");
        assertThat(repeatMethod)
                .as("重复件候选不应使用 QueryWrapper OR 拼接大量图号版本条件")
                .doesNotContain(".or(");
        assertThat(service)
                .as("当前订单图号版本集合需要固定批次拆分，避免单条 SQL 参数过长")
                .contains("REPEAT_SOURCE_KEY_BATCH_SIZE");

        String mapper = source("src/main/java/com/machining/quote/mapper/ItemQuoteVersionMapper.java");
        String provider = optionalSource("src/main/java/com/machining/quote/mapper/RepeatQuoteSourceSqlProvider.java");
        String repeatSqlSource = mapper + "\n" + provider;
        assertThat(repeatSqlSource)
                .contains("selectLatestReleasedRepeatSources")
                .contains("JOIN LATERAL")
                .contains("VALUES");
        assertThat(provider)
                .as("重复件候选必须排除导入默认空 JSON 快照，不能只判断 IS NOT NULL")
                .contains("h.material_data IS NOT NULL")
                .contains("h.material_data <> '{}'::jsonb")
                .contains("h.process_route IS NOT NULL")
                .contains("h.process_route <> '[]'::jsonb")
                .doesNotContain("h.fee_data IS NOT NULL")
                .doesNotContain("h.fee_data <> '[]'::jsonb");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void repeatReleasedItemLookupUsesPartialIndexWithoutCustomerOrTicketIndex() throws Exception {
        Path migrationPath = Path.of(
                "src", "main", "resources", "db", "migration", "V1__qpo_baseline_schema.sql");
        assertThat(migrationPath)
                .as("阶段 1 必须新增重复件已提单历史零件部分索引迁移")
                .exists();

        String migration = Files.readString(migrationPath, StandardCharsets.UTF_8);
        String schema = source("src/main/resources/db/migration/V1__qpo_baseline_schema.sql");
        String allMigrations;
        try (var files = Files.list(Path.of("src", "main", "resources", "db", "migration"))) {
            allMigrations = String.join("\n", files
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .sorted()
                    .map(path -> {
                        try {
                            return Files.readString(path, StandardCharsets.UTF_8);
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }
                    })
                    .toList());
        }
        String combined = migration + "\n" + schema + "\n" + allMigrations;

        assertThat(combined)
                .contains("idx_item_quote_version_repeat_released_key")
                .contains("ON item_quote_version USING btree (part_no, version, id DESC)")
                .contains("'已提单'::text")
                .contains("tax_included_unit_price IS NOT NULL")
                .contains("material_data IS NOT NULL")
                .contains("material_data <> '{}'::jsonb")
                .contains("process_route IS NOT NULL")
                .contains("process_route <> '[]'::jsonb")
                .contains("DROP INDEX IF EXISTS idx_item_quote_version_repeat_released_key");
        assertThat(combined)
                .as("重复件匹配不能退回普通全量联合索引")
                .doesNotContain("item_quote_version(part_no, version, status, id DESC)");
        String repeatIndex = combined.substring(combined.indexOf("idx_item_quote_version_repeat_released_key"));
        repeatIndex = repeatIndex.substring(0, repeatIndex.indexOf(";"));
        assertThat(repeatIndex)
                .as("重复件匹配索引不能新增客户或提单表专用索引")
                .doesNotContain("customer")
                .doesNotContain("order_delivery_ticket")
                .doesNotContain("fee_data");
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
        applyStandardMaterialSnapshot(historicalItem, 10.0);
        historicalItem.setProcessRoute(List.of(standardProcessRoute("TEST_CNC", "测试数控", "CNC", 10.0, 1.0)));
        historicalItem.setTaxIncludedUnitPrice(20.0);
        historicalItem.setTaxIncludedTotalPrice(20.0);
        historicalItem.setStatus(QuoteStatusWorkflow.ITEM_RELEASED);
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
        assertThat(savedItem.getTaxIncludedUnitPrice()).isNotNull();
        assertThat(savedItem.getStatus())
                .as("重复历史核价只套用工程核价结果，仍需市场保存报价单后才进入已报价")
                .isEqualTo(QuoteStatusWorkflow.PRICED);
        assertThat(savedOrder.getStatus()).isEqualTo("待工程部评估");
        assertThat(result.get("message")).asString().contains("完成核价提交");

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
        item.setTaxIncludedUnitPrice(10.0);
        item.setTaxIncludedTotalPrice(10.0);
        item.setStatus("已报价");
        itemMapper.insert(item);
        return itemMapper.selectById(item.getId());
    }

    private ItemQuoteVersion pendingItem(OrderRfq order, String partNo, String version) {
        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setRfqId(order.getId());
        item.setPartNo(partNo);
        item.setVersion(version);
        item.setDescription(partNo + "描述");
        item.setQty(1);
        item.setDrawingUrl("/uploads/test/" + partNo + ".pdf");
        item.setStatus("待核价");
        itemMapper.insert(item);
        return itemMapper.selectById(item.getId());
    }

    private ItemQuoteVersion releasedReusableItem(OrderRfq order, String partNo, String version, double unitPrice) {
        return reusableItem(order, partNo, version, unitPrice, QuoteStatusWorkflow.ITEM_RELEASED);
    }

    private ItemQuoteVersion reusableItem(OrderRfq order, String partNo, String version, double unitPrice, String status) {
        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setRfqId(order.getId());
        item.setPartNo(partNo);
        item.setVersion(version);
        item.setDescription(partNo + "历史描述");
        item.setQty(1);
        item.setDrawingUrl("/uploads/test/" + partNo + ".pdf");
        applyStandardMaterialSnapshot(item, unitPrice);
        item.setProcessRoute(List.of(standardProcessRoute("TEST_CNC", "测试数控", "CNC", 10.0, 1.0)));
        item.setTaxIncludedUnitPrice(unitPrice);
        item.setTaxIncludedTotalPrice(unitPrice);
        item.setStatus(status);
        itemMapper.insert(item);
        return itemMapper.selectById(item.getId());
    }

    private void applyStandardMaterialSnapshot(ItemQuoteVersion item, double materialCost) {
        DictMaterial material = ensureRepeatMaterial();
        item.setMaterialName(material.getMaterialName());
        item.setEngineeringMaterialCode(material.getMaterialCode());
        item.setEngineeringMaterialName(material.getMaterialName());
        item.setEngineeringMaterialTypeCode("RAW_MATERIAL");
        item.setEngineeringMaterialTypeName("\u539f\u6750\u6599");
        item.setEngineeringMaterialCategory(material.getMaterialCategory());
        item.setMaterialShape("\u677f\u6750");
        item.setMaterialData(standardMaterialData(material, materialCost));
    }

    private Map<String, Object> standardMaterialData(DictMaterial material, double materialCost) {
        Map<String, Object> materialData = new LinkedHashMap<>();
        materialData.put("materialCode", material.getMaterialCode());
        materialData.put("materialName", material.getMaterialName());
        materialData.put("name", material.getMaterialName());
        materialData.put("materialTypeCode", "RAW_MATERIAL");
        materialData.put("materialTypeName", "\u539f\u6750\u6599");
        materialData.put("materialCategory", material.getMaterialCategory());
        materialData.put("shape", "\u677f\u6750");
        materialData.put("weight", materialCost);
        materialData.put("cost", materialCost);
        return materialData;
    }

    private DictMaterial ensureRepeatMaterial() {
        DictMaterial existing = materialMapper.selectOne(new QueryWrapper<DictMaterial>()
                .eq("material_code", "MAT-CORE-REPEAT"));
        if (existing != null) {
            return existing;
        }
        DictMaterial material = new DictMaterial();
        material.setMaterialCode("MAT-CORE-REPEAT");
        material.setMaterialName("Core repeat test material");
        material.setMaterialTypeId(1L);
        material.setMaterialCategory("\u94a2\u94c1");
        material.setDensity(1.0);
        material.setPlateDensity(1.0);
        material.setPlatePrice(1.0);
        material.setBarDensity(1.0);
        material.setBarPrice(1.0);
        material.setTubeDensity(1.0);
        material.setTubePrice(1.0);
        material.setEnabled(true);
        material.setSortNo(0);
        material.setRemarks("test material");
        materialMapper.insert(material);
        return material;
    }

    private Map<String, Object> standardProcessRoute(String processCode,
                                                     String categoryName,
                                                     String detailName,
                                                     double hourlyRate,
                                                     double unitWorkHours) {
        TestProcess process = ensureTestProcess(processCode, categoryName, detailName, hourlyRate);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("routeType", "PROCESS");
        row.put("sourceType", "process");
        row.put("sortNo", 10);
        row.put("processCategoryId", process.categoryId());
        row.put("processDetailId", process.detailId());
        row.put("processCode", processCode);
        row.put("categoryName", categoryName);
        row.put("detailName", detailName);
        row.put("processName", detailName);
        row.put("calcType", "工时");
        row.put("hourlyRate", hourlyRate);
        row.put("unitWorkHours", unitWorkHours);
        row.put("inputVal", unitWorkHours);
        row.put("unitRate", hourlyRate);
        row.put("lineCost", hourlyRate * unitWorkHours);
        return row;
    }

    private TestProcess ensureTestProcess(String processCode, String categoryName, String detailName, double hourlyRate) {
        DictProcessCategory category = processCategoryMapper.selectOne(new QueryWrapper<DictProcessCategory>()
                .eq("process_code", processCode));
        if (category == null) {
            category = new DictProcessCategory();
            category.setProcessCode(processCode);
            category.setCategoryName(categoryName);
            category.setHourlyRate(BigDecimal.valueOf(hourlyRate));
            category.setRemarks("test process category");
            category.setSourceType("LOCAL");
            category.setEnabled(true);
            category.setSortNo(0);
            processCategoryMapper.insert(category);
        }

        DictProcessDetail detail = processDetailMapper.selectOne(new QueryWrapper<DictProcessDetail>()
                .eq("category_id", category.getId())
                .eq("detail_name", detailName));
        if (detail == null) {
            detail = new DictProcessDetail();
            detail.setCategoryId(category.getId());
            detail.setDetailName(detailName);
            detail.setDetailRemarks("test process detail");
            detail.setEnabled(true);
            detail.setSortNo(0);
            processDetailMapper.insert(detail);
        }
        return new TestProcess(category.getId(), detail.getId());
    }

    private DeliveryGenerateReq.DeliveryItemReq deliveryLine(ItemQuoteVersion item) {
        DeliveryGenerateReq.DeliveryItemReq line = new DeliveryGenerateReq.DeliveryItemReq();
        line.setItemId(item.getId());
        line.setAcceptedQty(item.getQty());
        return line;
    }

    private void ensureCustomer(String customerName) {
        CustomerMaster customer = customerMapper.selectOne(new QueryWrapper<CustomerMaster>()
                .eq("customer_name", customerName)
                .last("LIMIT 1"));
        if (customer == null) {
            customer = new CustomerMaster();
            customer.setCustomerCode("TEST-" + System.nanoTime());
            customer.setCustomerShortName(customerName);
            customer.setCustomerName(customerName);
            customer.setEnabled(true);
            customerMapper.insert(customer);
        }
        UpdateWrapper<OrderRfq> update = new UpdateWrapper<>();
        update.eq("customer_name", customerName)
                .and(wrapper -> wrapper.isNull("customer_code").or().eq("customer_code", ""))
                .set("customer_code", customer.getCustomerCode())
                .set("customer_short_name", customer.getCustomerShortName());
        orderMapper.update(null, update);
    }

    private DeliveryGenerateReq deliveryReq(OrderRfq order, ItemQuoteVersion item, int acceptedQty) {
        return deliveryReq(order, item, acceptedQty, "正式订单");
    }

    private DeliveryGenerateReq deliveryReq(OrderRfq order, ItemQuoteVersion item, int acceptedQty, String orderType) {
        DeliveryGenerateReq req = new DeliveryGenerateReq();
        req.setRfqIds(List.of(order.getId()));
        req.setOrderType(orderType);
        req.setInternalProjectNo("IP-" + order.getId() + "-" + acceptedQty);
        req.setCustomerOrderNo("CO-" + order.getId() + "-" + acceptedQty);
        req.setCustomerProjectNo("CP-" + order.getId());
        req.setArrangeType("计划");
        req.setSalesOrderDate(LocalDate.of(2026, 4, 1));
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
                item.setTaxIncludedUnitPrice(10.0);
                item.setTaxIncludedTotalPrice(10.0);
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

        return order;
    }

    private String source(String relativePath) throws Exception {
        return Files.readString(Path.of(relativePath), StandardCharsets.UTF_8);
    }

    private String optionalSource(String relativePath) throws Exception {
        Path path = Path.of(relativePath);
        return Files.exists(path) ? Files.readString(path, StandardCharsets.UTF_8) : "";
    }

    private String methodBody(String source, String startToken, String endToken) {
        int start = source.indexOf(startToken);
        if (start < 0) {
            return "";
        }
        int end = source.indexOf(endToken, start);
        return end < 0 ? source.substring(start) : source.substring(start, end);
    }

    private record TestProcess(Long categoryId, Long detailId) {
    }
}
