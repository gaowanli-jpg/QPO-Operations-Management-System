package com.machining.quote;

import com.machining.quote.dto.PricingItemRemarkUpdateReq;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.EngineerService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EngineerPricingRemarkUpdateTest {

    @Test
    @SuppressWarnings("unchecked")
    void updatePricingItemRemarkOnlyWritesRemarkAndAuditsSafeSummary() {
        EngineerService service = new EngineerService();
        ItemQuoteVersionMapper itemMapper = mock(ItemQuoteVersionMapper.class);
        OrderRfqMapper orderMapper = mock(OrderRfqMapper.class);
        AuditService auditService = mock(AuditService.class);
        ReflectionTestUtils.setField(service, "itemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "auditService", auditService);

        ItemQuoteVersion before = item(34, "old remark");
        ItemQuoteVersion locked = item(34, "old remark");
        ItemQuoteVersion after = item(34, "new remark");
        when(itemMapper.selectById(34)).thenReturn(before, after);
        when(itemMapper.selectByIdForUpdate(34)).thenReturn(locked);
        when(orderMapper.selectByIdForUpdate(12)).thenReturn(order(12));

        PricingItemRemarkUpdateReq req = new PricingItemRemarkUpdateReq();
        req.setRemark(" new remark ");

        ItemQuoteVersion result = service.updatePricingItemRemark(34, req);

        assertThat(result.getRemark()).isEqualTo("new remark");
        verify(itemMapper).update(isNull(), any());
        verify(itemMapper, never()).updateById(any(ItemQuoteVersion.class));
        verify(auditService).record(
                eq("ITEM_QUOTE_REMARK_UPDATE"),
                eq("item_quote_version"),
                eq(34),
                any(Map.class),
                any(Map.class)
        );

        Map<String, Object> beforeSummary = (Map<String, Object>) ReflectionTestUtils.invokeMethod(
                service, "pricingRemarkAuditSummary", before);
        Map<String, Object> afterSummary = (Map<String, Object>) ReflectionTestUtils.invokeMethod(
                service, "pricingRemarkAuditSummary", after);
        assertThat(beforeSummary)
                .containsEntry("itemId", 34)
                .containsEntry("rfqId", 12)
                .containsEntry("partNo", "PN-001")
                .containsEntry("version", "A")
                .containsEntry("remark", "old remark")
                .doesNotContainKeys("materialData", "processRoute", "feeData");
        assertThat(afterSummary).containsEntry("remark", "new remark");
    }

    @Test
    void updatePricingItemRemarkRejectsOversizedRemark() {
        EngineerService service = new EngineerService();
        PricingItemRemarkUpdateReq req = new PricingItemRemarkUpdateReq();
        req.setRemark("x".repeat(501));

        assertThatThrownBy(() -> service.updatePricingItemRemark(34, req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("remark")
                .hasMessageContaining("500");
    }

    @Test
    void controllerRemarkEndpointReusesPricingSavePermission() throws Exception {
        String source = Files.readString(
                Path.of("src", "main", "java", "com", "machining", "quote", "controller", "EngineerController.java"),
                StandardCharsets.UTF_8);
        int endpointIndex = source.indexOf("/pricing/items/{itemId}/remark");

        assertThat(endpointIndex).isGreaterThanOrEqualTo(0);
        assertThat(source.substring(Math.max(0, endpointIndex - 160), Math.min(source.length(), endpointIndex + 240)))
                .contains("@PutMapping")
                .contains("@RequirePermission(\"engineer:pricing:save-item\")")
                .contains("updatePricingItemRemark");
    }

    @Test
    void auditCatalogContainsRemarkUpdateLabel() {
        assertThat(AuditService.inferModule("ITEM_QUOTE_REMARK_UPDATE")).isEqualTo("ENGINEER");
        assertThat(AuditService.labelForAction("ITEM_QUOTE_REMARK_UPDATE"))
                .isNotEqualTo("ITEM_QUOTE_REMARK_UPDATE");
    }

    private ItemQuoteVersion item(int id, String remark) {
        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setId(id);
        item.setRfqId(12);
        item.setPartNo("PN-001");
        item.setVersion("A");
        item.setDescription("Widget");
        item.setMaterialName("6061-T6");
        item.setSurfaceTreatment("Anodized");
        item.setRemark(remark);
        item.setMaterialData(Map.of("secret", "material snapshot"));
        item.setProcessRoute(Map.of("secret", "process snapshot"));
        item.setFeeData(Map.of("secret", "fee snapshot"));
        return item;
    }

    private OrderRfq order(int id) {
        OrderRfq order = new OrderRfq();
        order.setId(id);
        return order;
    }
}
