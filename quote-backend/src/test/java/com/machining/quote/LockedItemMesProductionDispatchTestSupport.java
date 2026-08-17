package com.machining.quote;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.machining.quote.dto.LockedItemMesProductionBatchReq;
import com.machining.quote.dto.mes.MesProductionAssemblyRelationDTO;
import com.machining.quote.dto.mes.MesProductionDispatchReq;
import com.machining.quote.dto.mes.MesProductionDispatchResp;
import com.machining.quote.entity.ItemAssemblyRelation;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderDeliveryTicketAllocation;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.entity.SalesOrderItemAssemblyRelation;
import com.machining.quote.entity.SystemTask;
import com.machining.quote.mapper.ItemAssemblyRelationMapper;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketAllocationMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.SalesOrderItemAssemblyRelationMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.mapper.SystemTaskMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.DataScopeService;
import com.machining.quote.service.SystemTaskService;
import com.machining.quote.service.mes.MesProductionDispatchClient;
import com.machining.quote.service.mes.MesProductionDispatchService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

abstract class LockedItemMesProductionDispatchTestSupport {
    protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    protected void wireTaskService(
            SystemTaskService service,
            SystemTaskMapper taskMapper,
            ThreadPoolTaskExecutor taskExecutor,
            AuditService auditService,
            MesProductionDispatchService dispatchService) {
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "taskExecutor", taskExecutor);
        ReflectionTestUtils.setField(service, "auditService", auditService);
        ReflectionTestUtils.setField(service, "mesProductionDispatchService", dispatchService);
    }

    protected void wireDispatchService(
            MesProductionDispatchService service,
            OrderDeliveryTicketAllocationMapper allocationMapper,
            OrderDeliveryTicketMapper ticketMapper,
            MesProductionDispatchClient client,
            AuditService auditService,
            DataScopeService dataScopeService) {
        wireDispatchService(service, allocationMapper, ticketMapper, client, auditService, dataScopeService,
                null, null, null, null);
    }

    protected void wireDispatchService(
            MesProductionDispatchService service,
            OrderDeliveryTicketAllocationMapper allocationMapper,
            OrderDeliveryTicketMapper ticketMapper,
            MesProductionDispatchClient client,
            AuditService auditService,
            DataScopeService dataScopeService,
            ItemAssemblyRelationMapper itemAssemblyRelationMapper,
            ItemQuoteVersionMapper itemQuoteVersionMapper,
            SalesOrderItemAssemblyRelationMapper salesOrderItemAssemblyRelationMapper,
            SalesOrderItemMapper salesOrderItemMapper) {
        ReflectionTestUtils.setField(service, "allocationMapper", allocationMapper);
        ReflectionTestUtils.setField(service, "ticketMapper", ticketMapper);
        ReflectionTestUtils.setField(service, "mesProductionDispatchClient", client);
        ReflectionTestUtils.setField(service, "auditService", auditService);
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        ReflectionTestUtils.setField(service, "itemAssemblyRelationMapper", itemAssemblyRelationMapper);
        ReflectionTestUtils.setField(service, "itemQuoteVersionMapper", itemQuoteVersionMapper);
        ReflectionTestUtils.setField(service, "salesOrderItemAssemblyRelationMapper", salesOrderItemAssemblyRelationMapper);
        ReflectionTestUtils.setField(service, "salesOrderItemMapper", salesOrderItemMapper);
    }

    protected OrderDeliveryTicketAllocation allocation(Integer id, String allocationType, String productionStatus) {
        OrderDeliveryTicketAllocation row = new OrderDeliveryTicketAllocation();
        row.setId(id);
        row.setTicketId(100 + id);
        row.setRfqId(88);
        row.setItemId(77);
        row.setSourceType("SALES_ORDER");
        row.setSourceOrderId(3001L);
        row.setSourceItemId(9001L);
        row.setAllocationType(allocationType);
        row.setQty(4);
        row.setTicketQty(10);
        row.setMaterialNo("MAT-001");
        row.setOrderType("SO01");
        row.setItemType("ASSEMBLY");
        row.setPartNo("P-100");
        row.setVersion("A");
        row.setRfqNo("SO-1");
        row.setCustomerName("ACME");
        row.setInternalProjectNo("IP-9");
        row.setCustomerOrderNo("CO-9");
        row.setCustomerProjectNo("CP-9");
        row.setMaterialName("6061");
        row.setEngineeringMaterialCode("MAT-7075");
        row.setEngineeringMaterialName("7075");
        row.setEngineeringMaterialTypeCode("RAW_MATERIAL");
        row.setEngineeringMaterialTypeName("原材料");
        row.setEngineeringMaterialCategory("铝材");
        row.setMaterialShape("板材");
        row.setSurfaceTreatment("Anodized");
        row.setRemark("Rush");
        row.setDescription("Bracket");
        row.setDeliveryDate(LocalDate.of(2026, 8, 12));
        row.setProcessRoute(List.of(Map.of("processName", "CNC")));
        row.setMaterialData(Map.of("shape", "plate"));
        row.setMaterialGrossWeight(12.3);
        row.setPartNetWeight(10.2);
        row.setMesProductionStatus(productionStatus);
        return row;
    }

    protected MesProductionDispatchResp success(String externalId, String message) {
        MesProductionDispatchResp resp = new MesProductionDispatchResp();
        resp.setSuccess(true);
        resp.setExternalId(externalId);
        resp.setMessage(message);
        return resp;
    }

    protected ItemQuoteVersion rfqItem(Integer id, String partNo, String version) {
        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setId(id);
        item.setPartNo(partNo);
        item.setVersion(version);
        item.setMaterialNo("MES-" + partNo);
        return item;
    }

    protected SalesOrderItem salesItem(Long id, String partNo, String version) {
        SalesOrderItem item = new SalesOrderItem();
        item.setId(id);
        item.setPartNo(partNo);
        item.setVersion(version);
        item.setMaterialNo("MES-" + partNo);
        return item;
    }
}
