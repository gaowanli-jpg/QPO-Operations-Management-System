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

class RepeatQuoteApplicationWorkflowTest extends CoreWorkflowIntegrationTestSupport {

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
        historicalItem.setRepeatMatchEligible(true);
        itemMapper.updateById(historicalItem);

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
    void repeatQuoteCopiesHistoricalProcessRouteSnapshotWithoutCurrentDictionaryRepricing() {
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
        DictProcessDetail detail = processDetailMapper.selectById(((Number) historicalRoute.get("processDetailId")).longValue());
        detail.setHourlyRate(BigDecimal.ZERO);
        processDetailMapper.updateById(detail);

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
            assertThat((Double) route.get("hourlyRate")).isEqualTo(10.0);
            assertThat((Double) route.get("lineCost")).isEqualTo(10.0);
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
    void repeatQuoteReusesTicketedHistoryEvenWhenItemStatusStillQuoted() {
        String customerName = "部分已提单重复客户-" + System.nanoTime();
        OrderRfq historicalOrder = new OrderRfq();
        historicalOrder.setRfqNo("HIST-NON-REL-" + System.nanoTime());
        historicalOrder.setCustomerName(customerName);
        historicalOrder.setStatus(QuoteStatusWorkflow.PARTIALLY_RELEASED);
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
        historicalItem.setRepeatMatchEligible(true);
        itemMapper.updateById(historicalItem);

        OrderRfq currentOrder = new OrderRfq();
        currentOrder.setRfqNo("CUR-NON-REL-" + System.nanoTime());
        currentOrder.setCustomerName(customerName);
        currentOrder.setStatus("待工程部评估");
        orderMapper.insert(currentOrder);
        ItemQuoteVersion currentItem = pendingItem(currentOrder, "NONREL-100", "A");

        Map<String, Object> result = engineerService.applyRepeatQuotes(currentOrder.getId());

        ItemQuoteVersion saved = itemMapper.selectById(currentItem.getId());
        assertThat(result.get("appliedCount")).isEqualTo(1);
        assertThat(saved.getTaxIncludedUnitPrice()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(QuoteStatusWorkflow.PRICED);
        assertThat(saved.getMaterialData()).isNotNull();
        assertThat(saved.getProcessRoute()).isNotNull();
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
        historicalItem.setRepeatMatchEligible(true);
        itemMapper.updateById(historicalItem);

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
}
