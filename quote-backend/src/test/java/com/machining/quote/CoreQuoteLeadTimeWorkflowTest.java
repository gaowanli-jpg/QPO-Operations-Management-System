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

class CoreQuoteLeadTimeWorkflowTest extends CoreWorkflowIntegrationTestSupport {

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
        material.setMaterialTypeId(ensureRepeatMaterialType().getId());
        material.setMaterialCategory("\u94a2\u94c1");
        material.setEnabled(true);
        material.setSortNo(0);
        material.setRemarks("");
        material.setDensity(7850.0);
        material.setPlatePrice(7.1);
        material.setBarPrice(7.1);
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
        material.setMaterialTypeId(ensureRepeatMaterialType().getId());
        material.setMaterialCategory("\u94a2\u94c1");
        material.setEnabled(true);
        material.setSortNo(0);
        material.setRemarks("");
        material.setDensity(7850.0);
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
}
