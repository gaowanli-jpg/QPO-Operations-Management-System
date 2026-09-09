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

class RepeatQuoteMaterialNoWorkflowTest extends CoreWorkflowIntegrationTestSupport {

    @Test
    @SuppressWarnings("unchecked")
    void repeatQuoteDoesNotCopyConfirmedMaterialNoToRfqMainItem() {
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
        assertThat(result.get("materialNoCopiedCount")).isEqualTo(0);
        assertThat(result.get("materialNoSkippedCount")).isEqualTo(1);
        assertThat(result.get("materialNoConflictCount")).isEqualTo(0);
        assertThat(saved.getMaterialNo()).isNull();
        assertThat(saved.getMaterialNoStatus()).isEqualTo("未确认");
        assertThat(saved.getMaterialNoSource()).isNull();
        assertThat(saved.getMaterialNoSourceTicketId()).isNull();
        List<Map<String, Object>> appliedItems = (List<Map<String, Object>>) result.get("appliedItems");
        assertThat(appliedItems).singleElement()
                .satisfies(row -> assertThat(row.get("materialNoReuseStatus")).isEqualTo("NOT_APPLICABLE"));
    }

    @Test
    void repeatQuoteLeavesExistingRfqMaterialNoForMesWithoutConflict() {
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
        assertThat(result.get("materialNoSkippedCount")).isEqualTo(1);
        assertThat(result.get("materialNoConflictCount")).isEqualTo(0);
        assertThat(saved.getMaterialNo()).isEqualTo("MES-RFQ-CURRENT");
        assertThat(saved.getMaterialNoStatus()).isEqualTo("已确认");
        assertThat(saved.getTaxIncludedUnitPrice()).isNotNull();
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
    @SuppressWarnings("unchecked")
    void repeatAnalysisRecommendedMaterialUsesEngineeringMaterialCodeIdentity() {
        OrderRfq historicalOrder = new OrderRfq();
        historicalOrder.setRfqNo("HIST-MAT-CODE-" + System.nanoTime());
        historicalOrder.setCustomerName("Material coded source");
        historicalOrder.setStatus(QuoteStatusWorkflow.RELEASED);
        orderMapper.insert(historicalOrder);
        ItemQuoteVersion source = releasedReusableItem(historicalOrder, "MAT-CODE-100", "A", 12.0);

        OrderRfq currentOrder = new OrderRfq();
        currentOrder.setRfqNo("CUR-MAT-CODE-" + System.nanoTime());
        currentOrder.setCustomerName("Material coded current");
        currentOrder.setStatus("待工程部评估");
        orderMapper.insert(currentOrder);
        pendingItem(currentOrder, "MAT-CODE-100", "A");

        Map<String, Object> result = engineerService.analyzeRepeatOrder(currentOrder.getId());

        Map<String, Object> recommendedConfig = (Map<String, Object>) result.get("recommendedConfig");
        assertThat(recommendedConfig)
                .containsEntry("materialCode", source.getEngineeringMaterialCode())
                .containsEntry("materialName", source.getEngineeringMaterialName())
                .containsEntry("materialTypeCode", source.getEngineeringMaterialTypeCode())
                .containsEntry("materialTypeName", source.getEngineeringMaterialTypeName())
                .containsEntry("materialCategory", source.getEngineeringMaterialCategory())
                .containsEntry("shape", source.getMaterialShape());
    }

    @Test
    @SuppressWarnings("unchecked")
    void repeatAnalysisDoesNotRecommendLegacyMaterialNameWithoutCode() {
        OrderRfq historicalOrder = new OrderRfq();
        historicalOrder.setRfqNo("HIST-MAT-LEGACY-" + System.nanoTime());
        historicalOrder.setCustomerName("Material legacy source");
        historicalOrder.setStatus(QuoteStatusWorkflow.RELEASED);
        orderMapper.insert(historicalOrder);
        ItemQuoteVersion source = releasedReusableItem(historicalOrder, "MAT-LEGACY-100", "A", 12.0);
        source.setEngineeringMaterialCode("");
        source.setEngineeringMaterialName("");
        source.setEngineeringMaterialTypeCode("");
        source.setEngineeringMaterialTypeName("");
        source.setEngineeringMaterialCategory("");
        source.setMaterialShape("");
        source.setMaterialData(Map.of("name", "Legacy material only", "shape", "\u677f\u6750", "cost", 12.0));
        itemMapper.updateById(source);

        OrderRfq currentOrder = new OrderRfq();
        currentOrder.setRfqNo("CUR-MAT-LEGACY-" + System.nanoTime());
        currentOrder.setCustomerName("Material legacy current");
        currentOrder.setStatus("待工程部评估");
        orderMapper.insert(currentOrder);
        pendingItem(currentOrder, "MAT-LEGACY-100", "A");

        Map<String, Object> result = engineerService.analyzeRepeatOrder(currentOrder.getId());

        Map<String, Object> recommendedConfig = (Map<String, Object>) result.get("recommendedConfig");
        assertThat(recommendedConfig).doesNotContainKeys(
                "materialCode",
                "materialName",
                "materialTypeCode",
                "materialTypeName",
                "materialCategory"
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void repeatAnalysisRecommendsFirstHistoricalOrderEngineerRouteTemplate() {
        OrderRfq assemblyOnlyOrder = new OrderRfq();
        assemblyOnlyOrder.setRfqNo("HIST-GLOBAL-ASSY-" + System.nanoTime());
        assemblyOnlyOrder.setCustomerName("Global template assembly source");
        assemblyOnlyOrder.setStatus(QuoteStatusWorkflow.RELEASED);
        orderMapper.insert(assemblyOnlyOrder);
        ItemQuoteVersion assemblyOnlySource = releasedReusableItem(assemblyOnlyOrder, "GLOBAL-TPL-ASSY", "A", 18.0);
        assemblyOnlySource.setProcessRoute(List.of(Map.of(
                "sortNo", 10,
                "sourceType", "extra",
                "processName", "AssemblyFee",
                "calcType", "non-hour fee"
        )));
        itemMapper.updateById(assemblyOnlySource);

        Map<String, Object> engineerRoute = standardProcessRoute(
                "TEMPLATE_CNC",
                "Template process",
                "Template CNC",
                66.0,
                1.5
        );
        OrderRfq templatedOrder = new OrderRfq();
        templatedOrder.setRfqNo("HIST-GLOBAL-TEMPLATE-" + System.nanoTime());
        templatedOrder.setCustomerName("Global template normal source");
        templatedOrder.setStatus(QuoteStatusWorkflow.RELEASED);
        templatedOrder.setEngineerConfig(Map.of("processRoute", List.of(engineerRoute)));
        orderMapper.insert(templatedOrder);
        ItemQuoteVersion templatedSource = releasedReusableItem(templatedOrder, "GLOBAL-TPL-NORMAL", "A", 22.0);
        templatedSource.setProcessRoute(List.of(standardProcessRoute(
                "SOURCE_ITEM_ROUTE",
                "Source item process",
                "Source item CNC",
                33.0,
                1.0
        )));
        itemMapper.updateById(templatedSource);

        OrderRfq currentOrder = new OrderRfq();
        currentOrder.setRfqNo("CUR-GLOBAL-TEMPLATE-" + System.nanoTime());
        currentOrder.setCustomerName("Current global template order");
        currentOrder.setStatus("寰呭伐绋嬮儴璇勪及");
        currentOrder.setEngineerConfig(Map.of());
        orderMapper.insert(currentOrder);
        pendingItem(currentOrder, "GLOBAL-TPL-ASSY", "A");
        pendingItem(currentOrder, "GLOBAL-TPL-NORMAL", "A");

        Map<String, Object> result = engineerService.analyzeRepeatOrder(currentOrder.getId());

        Map<String, Object> recommendedConfig = (Map<String, Object>) result.get("recommendedConfig");
        List<Map<String, Object>> processRoute = (List<Map<String, Object>>) recommendedConfig.get("processRoute");
        assertThat(processRoute).singleElement().satisfies(route -> {
            assertThat(((Number) route.get("processDetailId")).longValue())
                    .isEqualTo(((Number) engineerRoute.get("processDetailId")).longValue());
            assertThat(route.get("processName")).isEqualTo("Template CNC");
            assertThat(route.get("processCode")).isEqualTo("TEMPLATE_CNC");
        });
    }
}
