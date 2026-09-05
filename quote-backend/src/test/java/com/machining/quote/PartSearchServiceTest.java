package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.PartSearchItemDetailDTO;
import com.machining.quote.dto.PartSearchItemRowDTO;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.service.DataScopeService;
import com.machining.quote.service.PartSearchService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PartSearchServiceTest {
    @Test
    @SuppressWarnings("unchecked")
    void rfqSearchRequiresPartNoAndAllowsAllVersionsWhenVersionIsBlank() {
        Fixture fixture = fixture();
        ItemQuoteVersion versionA = rfqItem(101, 11, "P-100", "A", 12.34, 13.95, "已报价");
        ItemQuoteVersion versionB = rfqItem(102, 12, "P-100", "B", 15.00, 16.95, "已提单");
        versionB.setDrawingUrl("/uploads/rfq/P-100-B.pdf");
        when(fixture.itemMapper.selectCount(any(QueryWrapper.class))).thenReturn(2L);
        when(fixture.itemMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(versionB, versionA));
        when(fixture.orderMapper.selectBatchIds(List.of(12, 11))).thenReturn(List.of(
                rfq(12, "RFQ-12", "已生成提单"),
                rfq(11, "RFQ-11", "已报价")));

        PageResult<PartSearchItemRowDTO> result = fixture.service.searchRfqItems(" P-100 ", "  ", 1, 20);

        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getRecords())
                .extracting(PartSearchItemRowDTO::getPartNo, PartSearchItemRowDTO::getVersion, PartSearchItemRowDTO::getOrderNo)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("P-100", "B", "RFQ-12"),
                        org.assertj.core.groups.Tuple.tuple("P-100", "A", "RFQ-11"));
        assertThat(result.getRecords().get(0).getUntaxedUnitPrice()).isEqualByComparingTo("15.00");
        assertThat(result.getRecords().get(0).getTaxIncludedUnitPrice()).isEqualByComparingTo("16.95");
        assertThat(result.getRecords().get(0).getDrawingUrl()).isEqualTo("/uploads/rfq/P-100-B.pdf");

        ArgumentCaptor<QueryWrapper<ItemQuoteVersion>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(fixture.itemMapper).selectList(captor.capture());
        assertThat(captor.getValue().getSqlSegment())
                .contains("part_no")
                .contains("id DESC")
                .doesNotContain("version =");
    }

    @Test
    @SuppressWarnings("unchecked")
    void rfqSearchAddsExactVersionFilterWhenVersionIsProvided() {
        Fixture fixture = fixture();
        ItemQuoteVersion versionB = rfqItem(102, 12, "P-100", "B", 15.00, 16.95, "已提单");
        when(fixture.itemMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);
        when(fixture.itemMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(versionB));
        when(fixture.orderMapper.selectBatchIds(List.of(12))).thenReturn(List.of(rfq(12, "RFQ-12", "已生成提单")));

        PageResult<PartSearchItemRowDTO> result = fixture.service.searchRfqItems("P-100", "B", 2, 50);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getVersion()).isEqualTo("B");
        ArgumentCaptor<QueryWrapper<ItemQuoteVersion>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(fixture.itemMapper).selectList(captor.capture());
        assertThat(captor.getValue().getSqlSegment())
                .contains("part_no")
                .contains("version")
                .contains("LIMIT 50 OFFSET 50");
    }

    @Test
    @SuppressWarnings("unchecked")
    void salesSearchUsesSalesOrderNumberAndSalesOrderScope() {
        Fixture fixture = fixture();
        SalesOrderItem item = salesItem(201L, 31L, "S-200", "C", "88.88", "100.44", "已报价");
        item.setDrawingUrl("/uploads/sales/S-200-C.pdf");
        when(fixture.salesItemMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);
        when(fixture.salesItemMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(item));
        when(fixture.salesHeaderMapper.selectBatchIds(List.of(31L))).thenReturn(List.of(salesOrder(31L, "SO-31", "已生成提单")));

        PageResult<PartSearchItemRowDTO> result = fixture.service.searchSalesItems("S-200", "C", 1, 20);

        assertThat(result.getRecords()).hasSize(1);
        PartSearchItemRowDTO row = result.getRecords().get(0);
        assertThat(row.getOrderNo()).isEqualTo("SO-31");
        assertThat(row.getOrderStatus()).isEqualTo("已生成提单");
        assertThat(row.getItemStatus()).isEqualTo("已报价");
        assertThat(row.getTaxIncludedUnitPrice()).isEqualByComparingTo("100.44");
        assertThat(row.getDrawingUrl()).isEqualTo("/uploads/sales/S-200-C.pdf");

        ArgumentCaptor<QueryWrapper<SalesOrderItem>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(fixture.salesItemMapper).selectList(captor.capture());
        assertThat(captor.getValue().getSqlSegment())
                .contains("part_no")
                .contains("version")
                .contains("1 = 0");
    }

    @Test
    void detailReturnsReadOnlyItemAndOrderSnapshot() {
        Fixture fixture = fixture();
        ItemQuoteVersion item = rfqItem(301, 41, "D-300", "A", 9.50, 10.74, "已报价");
        OrderRfq order = rfq(41, "RFQ-41", "已报价");
        when(fixture.itemMapper.selectById(301)).thenReturn(item);
        when(fixture.orderMapper.selectById(41)).thenReturn(order);

        PartSearchItemDetailDTO detail = fixture.service.getRfqItemDetail(301);

        assertThat(detail.getSourceType()).isEqualTo("RFQ");
        assertThat(detail.getOrderSummary()).containsEntry("orderNo", "RFQ-41");
        assertThat(detail.getItemDetail()).isSameAs(item);
        verify(fixture.dataScopeService).assertCanAccessOrder(order);
    }

    @Test
    void blankPartNoIsRejectedBeforeQueryingTables() {
        Fixture fixture = fixture();

        assertThatThrownBy(() -> fixture.service.searchRfqItems(" ", null, 1, 20))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("图号不能为空");
    }

    private Fixture fixture() {
        Fixture fixture = new Fixture();
        fixture.service = new PartSearchService();
        fixture.itemMapper = mock(ItemQuoteVersionMapper.class);
        fixture.orderMapper = mock(OrderRfqMapper.class);
        fixture.salesItemMapper = mock(SalesOrderItemMapper.class);
        fixture.salesHeaderMapper = mock(SalesOrderHeaderMapper.class);
        fixture.dataScopeService = mock(DataScopeService.class);
        ReflectionTestUtils.setField(fixture.service, "itemMapper", fixture.itemMapper);
        ReflectionTestUtils.setField(fixture.service, "orderMapper", fixture.orderMapper);
        ReflectionTestUtils.setField(fixture.service, "salesItemMapper", fixture.salesItemMapper);
        ReflectionTestUtils.setField(fixture.service, "salesHeaderMapper", fixture.salesHeaderMapper);
        ReflectionTestUtils.setField(fixture.service, "dataScopeService", fixture.dataScopeService);
        org.mockito.Mockito.doAnswer(invocation -> {
            QueryWrapper<?> wrapper = invocation.getArgument(0);
            wrapper.apply("1 = 0");
            return null;
        }).when(fixture.dataScopeService).applySalesOrderIdScope(any(QueryWrapper.class), org.mockito.ArgumentMatchers.eq("sales_order_id"));
        return fixture;
    }

    private ItemQuoteVersion rfqItem(Integer id, Integer rfqId, String partNo, String version,
                                     Double untaxedPrice, Double taxIncludedPrice, String status) {
        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setId(id);
        item.setRfqId(rfqId);
        item.setPartNo(partNo);
        item.setVersion(version);
        item.setUntaxedUnitPrice(untaxedPrice);
        item.setTaxIncludedUnitPrice(taxIncludedPrice);
        item.setStatus(status);
        return item;
    }

    private SalesOrderItem salesItem(Long id, Long salesOrderId, String partNo, String version,
                                     String untaxedPrice, String taxIncludedPrice, String status) {
        SalesOrderItem item = new SalesOrderItem();
        item.setId(id);
        item.setSalesOrderId(salesOrderId);
        item.setPartNo(partNo);
        item.setVersion(version);
        item.setUntaxedUnitPrice(new BigDecimal(untaxedPrice));
        item.setTaxIncludedUnitPrice(new BigDecimal(taxIncludedPrice));
        item.setStatus(status);
        return item;
    }

    private OrderRfq rfq(Integer id, String rfqNo, String status) {
        OrderRfq order = new OrderRfq();
        order.setId(id);
        order.setRfqNo(rfqNo);
        order.setStatus(status);
        return order;
    }

    private SalesOrderHeader salesOrder(Long id, String salesOrderNo, String status) {
        SalesOrderHeader order = new SalesOrderHeader();
        order.setId(id);
        order.setSalesOrderNo(salesOrderNo);
        order.setStatus(status);
        return order;
    }

    private static class Fixture {
        private PartSearchService service;
        private ItemQuoteVersionMapper itemMapper;
        private OrderRfqMapper orderMapper;
        private SalesOrderItemMapper salesItemMapper;
        private SalesOrderHeaderMapper salesHeaderMapper;
        private DataScopeService dataScopeService;
    }
}
