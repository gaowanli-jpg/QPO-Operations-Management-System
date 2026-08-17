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
import com.machining.quote.entity.DictMaterialType;
import com.machining.quote.entity.CustomerMaster;
import com.machining.quote.entity.DictProcessCategory;
import com.machining.quote.entity.DictProcessDetail;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.dto.ProcessSheetDTO;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.DictMaterialMapper;
import com.machining.quote.mapper.DictMaterialTypeMapper;
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

class CoreDeliveryWorkflowIntegrationTest extends CoreWorkflowIntegrationTestSupport {

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
        material.setMaterialTypeId(ensureRepeatMaterialType().getId());
        material.setMaterialCategory("\u94a2\u94c1");
        material.setEnabled(true);
        material.setSortNo(0);
        material.setRemarks("");
        material.setPlateDensity(7850.0);
        material.setPlatePrice(7.1);
        material.setBarDensity(7850.0);
        material.setBarPrice(7.1);
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
}
