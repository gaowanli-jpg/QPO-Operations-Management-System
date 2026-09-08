package com.machining.quote;

import com.machining.quote.entity.OrderRfq;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.service.BusinessFileService;
import com.machining.quote.service.DataScopeService;
import com.machining.quote.service.DrawingFolderAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DrawingFolderAccessServiceTest {
    private static final String FOLDER_URL = "/uploads/drawing-folders/RFQ/101/folder-1/";

    @Test
    void listsFolderFilesWhenFolderIsReferencedByAccessibleRfqOrder() {
        BusinessFileService businessFileService = mock(BusinessFileService.class);
        OrderRfqMapper orderRfqMapper = mock(OrderRfqMapper.class);
        DataScopeService dataScopeService = mock(DataScopeService.class);
        DrawingFolderAccessService service = service(businessFileService, orderRfqMapper, mock(SalesOrderHeaderMapper.class),
                mock(ItemQuoteVersionMapper.class), mock(SalesOrderItemMapper.class), mock(OrderDeliveryTicketMapper.class), dataScopeService);
        OrderRfq order = new OrderRfq();
        order.setId(101);
        order.setDrawingFolderUrl(FOLDER_URL);
        BusinessFileService.DrawingFolderFile file = new BusinessFileService.DrawingFolderFile(
                "A.pdf", FOLDER_URL + "A.pdf", "application/pdf", 12L, LocalDateTime.now());

        when(businessFileService.normalizeDrawingFolderUrl(FOLDER_URL)).thenReturn(FOLDER_URL);
        when(orderRfqMapper.selectList(any())).thenReturn(List.of(order));
        when(businessFileService.listDrawingFolderFiles(FOLDER_URL)).thenReturn(List.of(file));

        assertThat(service.listFiles(FOLDER_URL)).containsExactly(file);
        verify(dataScopeService).assertCanAccessOrder(order);
        verify(businessFileService).listDrawingFolderFiles(FOLDER_URL);
    }

    @Test
    void listsFolderFilesWhenFolderIsReferencedByAccessibleSalesOrderHeader() {
        BusinessFileService businessFileService = mock(BusinessFileService.class);
        SalesOrderHeaderMapper headerMapper = mock(SalesOrderHeaderMapper.class);
        DataScopeService dataScopeService = mock(DataScopeService.class);
        DrawingFolderAccessService service = service(businessFileService, mock(OrderRfqMapper.class), headerMapper,
                mock(ItemQuoteVersionMapper.class), mock(SalesOrderItemMapper.class), mock(OrderDeliveryTicketMapper.class), dataScopeService);
        SalesOrderHeader header = new SalesOrderHeader();
        header.setId(301L);
        header.setDrawingFolderUrl(FOLDER_URL);

        when(businessFileService.normalizeDrawingFolderUrl(FOLDER_URL)).thenReturn(FOLDER_URL);
        when(headerMapper.selectList(any())).thenReturn(List.of(header));
        when(businessFileService.listDrawingFolderFiles(FOLDER_URL)).thenReturn(List.of());

        assertThat(service.listFiles(FOLDER_URL)).isEmpty();
        verify(dataScopeService).assertCanAccessSalesOrder(header);
        verify(businessFileService).listDrawingFolderFiles(FOLDER_URL);
    }

    @Test
    void rejectsUnboundFolderWithoutListingMinioObjects() {
        BusinessFileService businessFileService = mock(BusinessFileService.class);
        DrawingFolderAccessService service = service(businessFileService, mock(OrderRfqMapper.class), mock(SalesOrderHeaderMapper.class),
                mock(ItemQuoteVersionMapper.class), mock(SalesOrderItemMapper.class), mock(OrderDeliveryTicketMapper.class), mock(DataScopeService.class));

        when(businessFileService.normalizeDrawingFolderUrl(FOLDER_URL)).thenReturn(FOLDER_URL);

        assertThatThrownBy(() -> service.listFiles(FOLDER_URL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("图纸文件夹不存在或未绑定订单");
        verify(businessFileService, never()).listDrawingFolderFiles(eq(FOLDER_URL));
    }

    private DrawingFolderAccessService service(
            BusinessFileService businessFileService,
            OrderRfqMapper orderRfqMapper,
            SalesOrderHeaderMapper salesOrderHeaderMapper,
            ItemQuoteVersionMapper itemQuoteVersionMapper,
            SalesOrderItemMapper salesOrderItemMapper,
            OrderDeliveryTicketMapper orderDeliveryTicketMapper,
            DataScopeService dataScopeService) {
        DrawingFolderAccessService service = new DrawingFolderAccessService();
        ReflectionTestUtils.setField(service, "businessFileService", businessFileService);
        ReflectionTestUtils.setField(service, "orderRfqMapper", orderRfqMapper);
        ReflectionTestUtils.setField(service, "salesOrderHeaderMapper", salesOrderHeaderMapper);
        ReflectionTestUtils.setField(service, "itemQuoteVersionMapper", itemQuoteVersionMapper);
        ReflectionTestUtils.setField(service, "salesOrderItemMapper", salesOrderItemMapper);
        ReflectionTestUtils.setField(service, "orderDeliveryTicketMapper", orderDeliveryTicketMapper);
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        return service;
    }
}
