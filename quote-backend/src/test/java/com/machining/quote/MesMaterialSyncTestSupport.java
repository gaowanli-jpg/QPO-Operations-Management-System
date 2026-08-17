package com.machining.quote;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.machining.quote.controller.OrderDeliveryController;
import com.machining.quote.dto.mes.MesMaterialResolveReq;
import com.machining.quote.dto.mes.MesMaterialResolveResp;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderDeliveryTicketMesRequest;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMesRequestMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.BusinessFileService;
import com.machining.quote.service.SourceItemMaterialNoSyncService;
import com.machining.quote.service.mes.MesMaterialClient;
import com.machining.quote.service.mes.MesMaterialSyncService;
import com.machining.quote.service.SystemTaskService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.PostMapping;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

abstract class MesMaterialSyncTestSupport {
    protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    protected static final Path ORDER_DELIVERY_CONTROLLER = Path.of(
            "src", "main", "java", "com", "machining", "quote", "controller", "OrderDeliveryController.java");
    protected static final Path SYSTEM_TASK_SERVICE = Path.of(
            "src", "main", "java", "com", "machining", "quote", "service", "SystemTaskService.java");
    protected static final Path MES_SYNC_SERVICE = Path.of(
            "src", "main", "java", "com", "machining", "quote", "service", "mes", "MesMaterialSyncService.java");

    protected String read(Path path) throws IOException {
        assertThat(Files.exists(path))
                .as("%s should exist", path)
                .isTrue();
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    protected JsonNode serialize(Object value) {
        try {
            return OBJECT_MAPPER.valueToTree(value);
        } catch (IllegalArgumentException e) {
            throw new AssertionError(e);
        }
    }

    protected MesMaterialSyncService syncService() {
        return new MesMaterialSyncService();
    }

    protected void wireSyncService(
            MesMaterialSyncService service,
            OrderDeliveryTicketMapper ticketMapper,
            OrderRfqMapper orderMapper,
            ItemQuoteVersionMapper itemMapper,
            MesMaterialClient client,
            AuditService auditService) {
        BusinessFileService businessFileService = mock(BusinessFileService.class);
        when(businessFileService.externalReadableUrl(anyString())).thenAnswer(invocation -> {
            String drawingUrl = invocation.getArgument(0);
            if (drawingUrl.startsWith("http://") || drawingUrl.startsWith("https://")) {
                return drawingUrl;
            }
            return "http://qpo.local" + (drawingUrl.startsWith("/") ? drawingUrl : "/" + drawingUrl);
        });
        wireSyncService(service, ticketMapper, orderMapper, itemMapper, client, auditService, businessFileService);
    }

    protected void wireSyncService(
            MesMaterialSyncService service,
            OrderDeliveryTicketMapper ticketMapper,
            OrderRfqMapper orderMapper,
            ItemQuoteVersionMapper itemMapper,
            MesMaterialClient client,
            AuditService auditService,
            BusinessFileService businessFileService) {
        ReflectionTestUtils.setField(service, "ticketMapper", ticketMapper);
        ReflectionTestUtils.setField(service, "orderRfqMapper", orderMapper);
        ReflectionTestUtils.setField(service, "itemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "mesMaterialClient", client);
        ReflectionTestUtils.setField(service, "auditService", auditService);
        ReflectionTestUtils.setField(service, "businessFileService", businessFileService);
    }

    protected OrderDeliveryTicket ticket() {
        OrderDeliveryTicket ticket = new OrderDeliveryTicket();
        ticket.setId(123);
        ticket.setRfqId(88);
        ticket.setItemId(77);
        ticket.setPartNo("P-100");
        ticket.setVersion("A");
        ticket.setInternalProjectNo("IP-9");
        ticket.setRemark("release remark");
        ticket.setMesSyncStatus("PENDING");
        return ticket;
    }

    protected OrderRfq order() {
        OrderRfq order = new OrderRfq();
        order.setId(88);
        order.setRfqNo("RFQ-88");
        order.setCustomerCode("C-ACME");
        order.setCustomerName("ACME");
        return order;
    }

    protected ItemQuoteVersion item(String drawingUrl) {
        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setId(77);
        item.setDrawingUrl(drawingUrl);
        return item;
    }

    protected MesMaterialResolveResp mesProductResponse(String productCode, String status, String taskNo, String message) {
        MesMaterialResolveResp response = new MesMaterialResolveResp();
        response.setCode(0);
        response.setMessage(message);
        MesMaterialResolveResp.Data data = new MesMaterialResolveResp.Data();
        MesMaterialResolveResp.Item item = new MesMaterialResolveResp.Item();
        item.setTaskId(2L);
        item.setTaskNo(taskNo);
        item.setDrawingNo("P-100");
        item.setDrawingVersion("A");
        item.setStatus(status);
        item.setProductId(productCode == null ? null : 20L);
        item.setProductCode(productCode);
        item.setProductName(productCode == null ? null : "业务演示成品");
        data.setItems(java.util.List.of(item));
        response.setData(data);
        return response;
    }
}
