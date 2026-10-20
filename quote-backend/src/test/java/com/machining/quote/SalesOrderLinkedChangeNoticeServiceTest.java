package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.machining.quote.dto.SalesOrderLinkedChangeNoticeHandleReq;
import com.machining.quote.dto.SalesOrderLinkedChangeNoticeQueryReq;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.entity.SalesOrderLinkedChangeNotice;
import com.machining.quote.mapper.SalesOrderLinkedChangeNoticeMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.DataScopeService;
import com.machining.quote.service.SalesOrderLinkedChangeNoticeFactory;
import com.machining.quote.service.SalesOrderLinkedChangeNoticeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOrderLinkedChangeNoticeServiceTest {
    private SalesOrderLinkedChangeNoticeMapper noticeMapper;
    private SalesOrderLinkedChangeNoticeService service;

    @BeforeEach
    void setUp() {
        noticeMapper = mock(SalesOrderLinkedChangeNoticeMapper.class);
        service = new SalesOrderLinkedChangeNoticeService();
        ReflectionTestUtils.setField(service, "noticeMapper", noticeMapper);
        ReflectionTestUtils.setField(service, "factory", new SalesOrderLinkedChangeNoticeFactory());
    }

    @Test
    void itemWithoutDownstreamActionStillCreatesOneNotice() {
        List<SalesOrderLinkedChangeNotice> inserted = new ArrayList<>();
        when(noticeMapper.insertBatch(anyList())).thenAnswer(invocation -> {
            inserted.addAll(invocation.getArgument(0));
            return inserted.size();
        });

        int count = service.createForItem(order(), item(11L), List.of(), List.of(),
                "ITEM", "no downstream action", LocalDateTime.now(), 7L);

        assertThat(count).isEqualTo(1);
        assertThat(inserted).singleElement().satisfies(row -> {
            assertThat(row.getSalesOrderItemId()).isEqualTo(11L);
            assertThat(row.getDownstreamImpactCount()).isZero();
            assertThat(row.getWmsSubmitted()).isFalse();
            assertThat(row.getMesProductionSubmitted()).isFalse();
            assertThat(row.getStockPickingSubmitted()).isFalse();
            assertThat(row.getHandlingStatus()).isEqualTo("UNHANDLED");
            assertThat(row.getCustomerOrderNo()).isEqualTo("ORDER-ITEM");
            assertThat(row.getInternalProjectNo()).isEqualTo("PROJECT-ITEM");
        });
    }

    @Test
    void orderLevelCreationUsesOneEventForEveryItem() {
        List<SalesOrderLinkedChangeNotice> inserted = new ArrayList<>();
        when(noticeMapper.insertBatch(anyList())).thenAnswer(invocation -> {
            List<SalesOrderLinkedChangeNotice> rows = invocation.getArgument(0);
            inserted.addAll(rows);
            return rows.size();
        });

        int count = service.createForItems(order(), List.of(item(11L), item(12L)),
                Map.of(), Map.of(), "order discard", LocalDateTime.now(), 7L);

        assertThat(count).isEqualTo(2);
        assertThat(inserted).extracting(SalesOrderLinkedChangeNotice::getSalesOrderItemId)
                .containsExactly(11L, 12L);
        assertThat(inserted).extracting(SalesOrderLinkedChangeNotice::getLinkedDiscardEventKey)
                .containsOnly(inserted.get(0).getLinkedDiscardEventKey());
        verify(noticeMapper, times(1)).insertBatch(anyList());
    }

    @Test
    void listAppliesSalesOrderScopeAndBothImpactFilters() {
        DataScopeService dataScopeService = mock(DataScopeService.class);
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        when(noticeMapper.selectCount(any())).thenReturn(0L);

        SalesOrderLinkedChangeNoticeQueryReq withoutImpact = new SalesOrderLinkedChangeNoticeQueryReq();
        withoutImpact.setHasDownstreamImpact(false);
        service.page(withoutImpact);

        SalesOrderLinkedChangeNoticeQueryReq withImpact = new SalesOrderLinkedChangeNoticeQueryReq();
        withImpact.setHasDownstreamImpact(true);
        service.page(withImpact);

        ArgumentCaptor<QueryWrapper<SalesOrderLinkedChangeNotice>> queries = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(noticeMapper, times(2)).selectCount(queries.capture());
        verify(dataScopeService, times(2)).applySalesOrderIdScope(any(), eq("sales_order_id"));
        assertThat(queries.getAllValues().get(0).getSqlSegment()).contains("downstream_impact_count");
        assertThat(queries.getAllValues().get(1).getSqlSegment()).contains("downstream_impact_count");
    }

    @Test
    void listAppliesCustomerOrderAndInternalProjectFilters() {
        when(noticeMapper.selectCount(any())).thenReturn(0L);

        SalesOrderLinkedChangeNoticeQueryReq req = new SalesOrderLinkedChangeNoticeQueryReq();
        req.setCustomerOrderNo("PO-2026");
        req.setInternalProjectNo("IP-2026");
        service.page(req);

        ArgumentCaptor<QueryWrapper<SalesOrderLinkedChangeNotice>> query = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(noticeMapper).selectCount(query.capture());
        assertThat(query.getValue().getSqlSegment())
                .contains("customer_order_no")
                .contains("internal_project_no");
    }

    @Test
    void handleAndReopenOnlyChangeHumanNotificationState() {
        AuditService auditService = mock(AuditService.class);
        ReflectionTestUtils.setField(service, "auditService", auditService);
        SalesOrderLinkedChangeNotice unhandled = notice(31L, "UNHANDLED");
        SalesOrderLinkedChangeNotice handled = notice(31L, "HANDLED");
        handled.setHandleRemark("已通知生产计划员");
        when(noticeMapper.selectOne(any())).thenReturn(unhandled, handled, handled, unhandled);
        when(noticeMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        SalesOrderLinkedChangeNoticeHandleReq req = new SalesOrderLinkedChangeNoticeHandleReq();
        req.setRemark("已通知生产计划员");
        assertThat(service.handle(31L, req).getHandlingStatus()).isEqualTo("HANDLED");
        assertThat(service.reopen(31L, new SalesOrderLinkedChangeNoticeHandleReq()).getHandlingStatus())
                .isEqualTo("UNHANDLED");

        verify(noticeMapper, times(2)).update(isNull(), any(UpdateWrapper.class));
        verify(auditService, times(2)).record(any(), eq("sales_order_linked_change_notice"), eq(31L),
                any(), any(), eq("CHANGE"), eq("SUCCESS"), any(), isNull());
    }

    @Test
    void repeatedHandleOnHandledNoticeIsIdempotent() {
        SalesOrderLinkedChangeNotice handled = notice(32L, "HANDLED");
        when(noticeMapper.selectOne(any())).thenReturn(handled);

        assertThat(service.handle(32L, new SalesOrderLinkedChangeNoticeHandleReq()))
                .isSameAs(handled);
        verify(noticeMapper, never()).update(isNull(), any(UpdateWrapper.class));
    }

    private SalesOrderHeader order() {
        SalesOrderHeader order = new SalesOrderHeader();
        order.setId(1L);
        order.setSalesOrderNo("SO-1");
        order.setCustomerId(2L);
        order.setCustomerCode("C-2");
        order.setCustomerName("Customer");
        order.setCustomerOrderNo("ORDER-DEFAULT");
        order.setInternalProjectNo("PROJECT-DEFAULT");
        return order;
    }

    private SalesOrderItem item(Long id) {
        SalesOrderItem item = new SalesOrderItem();
        item.setId(id);
        item.setLineNo(id.intValue());
        item.setPartNo("PART-" + id);
        item.setVersion("A");
        item.setDescription("Description");
        item.setMaterialName("Material");
        item.setCustomerOrderNo("ORDER-ITEM");
        item.setInternalProjectNo("PROJECT-ITEM");
        return item;
    }

    private SalesOrderLinkedChangeNotice notice(Long id, String handlingStatus) {
        SalesOrderLinkedChangeNotice notice = new SalesOrderLinkedChangeNotice();
        notice.setId(id);
        notice.setHandlingStatus(handlingStatus);
        notice.setSalesOrderNo("SO-1");
        notice.setPartNo("PART-1");
        notice.setVersion("A");
        notice.setLinkedDiscardedAt(LocalDateTime.now());
        return notice;
    }
}
