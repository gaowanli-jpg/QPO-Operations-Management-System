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
import com.machining.quote.entity.SalesOrderHeader;
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
import java.util.Objects;
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
        OrderDeliveryTicketAllocationMapper allocationMapper = mock(OrderDeliveryTicketAllocationMapper.class);
        when(allocationMapper.selectById(any())).thenAnswer(invocation -> {
            OrderDeliveryTicketAllocation allocation = new OrderDeliveryTicketAllocation();
            allocation.setId(invocation.getArgument(0));
            allocation.setDiscarded(false);
            return allocation;
        });
        ReflectionTestUtils.setField(service, "allocationMapper", allocationMapper);
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
        wireLinkedSalesOrderContexts(service, salesOrderItemMapper);
    }

    private void wireLinkedSalesOrderContexts(
            MesProductionDispatchService service,
            SalesOrderItemMapper salesOrderItemMapper) {
        com.machining.quote.service.SalesOrderAllocationContextLoader contextLoader =
                mock(com.machining.quote.service.SalesOrderAllocationContextLoader.class);
        when(contextLoader.load(any())).thenAnswer(invocation -> {
            List<OrderDeliveryTicketAllocation> allocations = invocation.getArgument(0);
            Map<Integer, com.machining.quote.service.SalesOrderAllocationContextLoader.Context> contexts =
                    new LinkedHashMap<>();
            for (OrderDeliveryTicketAllocation allocation : allocations) {
                SalesOrderItem item = null;
                if (salesOrderItemMapper != null && allocation.getId() != null) {
                    List<SalesOrderItem> linked = salesOrderItemMapper.selectByAllocationIds(
                            List.of(allocation.getId().longValue()));
                    if (linked != null && !linked.isEmpty()) {
                        item = linked.get(0);
                    }
                }
                if (item == null) {
                    item = salesOrderItemFromAllocation(allocation);
                }
                fillLinkedItem(item, allocation);
                SalesOrderHeader header = new SalesOrderHeader();
                header.setId(allocation.getSourceOrderId());
                header.setDocumentNo(allocation.getRfqNo());
                header.setSalesOrderNo(allocation.getRfqNo());
                header.setCustomerCode("C001");
                header.setCustomerName(allocation.getCustomerName());
                header.setSalesOrderDate(allocation.getDeliveryDate());
                contexts.put(allocation.getId(), new com.machining.quote.service.SalesOrderAllocationContextLoader.Context(
                        item, header, true, null));
            }
            return contexts;
        });
        ReflectionTestUtils.setField(service, "contextLoader", contextLoader);
    }

    private SalesOrderItem salesOrderItemFromAllocation(OrderDeliveryTicketAllocation allocation) {
        SalesOrderItem item = new SalesOrderItem();
        item.setId(allocation.getSourceItemId());
        return item;
    }

    private void fillLinkedItem(SalesOrderItem item, OrderDeliveryTicketAllocation allocation) {
        item.setAllocationId(allocation.getId() == null ? null : allocation.getId().longValue());
        item.setSalesOrderId(firstNonNull(item.getSalesOrderId(), allocation.getSourceOrderId()));
        item.setLineNo(firstNonNull(item.getLineNo(), allocation.getId()));
        item.setQty(firstNonNull(item.getQty(), allocation.getQty()));
        item.setMaterialNo(firstNonBlank(item.getMaterialNo(), allocation.getMaterialNo()));
        item.setPartNo(firstNonBlank(item.getPartNo(), allocation.getPartNo()));
        item.setVersion(firstNonBlank(item.getVersion(), allocation.getVersion()));
        item.setOrderType(firstNonBlank(item.getOrderType(), allocation.getOrderType()));
        item.setItemType(firstNonBlank(item.getItemType(), allocation.getItemType()));
        item.setInternalProjectNo(firstNonBlank(item.getInternalProjectNo(), allocation.getInternalProjectNo()));
        item.setCustomerOrderNo(firstNonBlank(item.getCustomerOrderNo(), allocation.getCustomerOrderNo()));
        item.setCustomerProjectNo(firstNonBlank(item.getCustomerProjectNo(), allocation.getCustomerProjectNo()));
        item.setMaterialName(firstNonBlank(item.getMaterialName(), allocation.getMaterialName()));
        item.setEngineeringMaterialCode(firstNonBlank(item.getEngineeringMaterialCode(), allocation.getEngineeringMaterialCode()));
        item.setEngineeringMaterialName(firstNonBlank(item.getEngineeringMaterialName(), allocation.getEngineeringMaterialName()));
        item.setEngineeringMaterialTypeCode(firstNonBlank(item.getEngineeringMaterialTypeCode(), allocation.getEngineeringMaterialTypeCode()));
        item.setEngineeringMaterialTypeName(firstNonBlank(item.getEngineeringMaterialTypeName(), allocation.getEngineeringMaterialTypeName()));
        item.setEngineeringMaterialCategory(firstNonBlank(item.getEngineeringMaterialCategory(), allocation.getEngineeringMaterialCategory()));
        item.setMaterialShape(firstNonBlank(item.getMaterialShape(), allocation.getMaterialShape()));
        item.setSurfaceTreatment(firstNonBlank(item.getSurfaceTreatment(), allocation.getSurfaceTreatment()));
        item.setRemark(firstNonBlank(item.getRemark(), allocation.getRemark()));
        item.setDescription(firstNonBlank(item.getDescription(), allocation.getDescription()));
        item.setInternalDeliveryDate(item.getInternalDeliveryDate() == null ? allocation.getDeliveryDate() : item.getInternalDeliveryDate());
        item.setMaterialData(item.getMaterialData() == null ? allocation.getMaterialData() : item.getMaterialData());
        item.setProcessRoute(item.getProcessRoute() == null ? allocation.getProcessRoute() : item.getProcessRoute());
        item.setMaterialGrossWeight(item.getMaterialGrossWeight() == null ? allocation.getMaterialGrossWeight() : item.getMaterialGrossWeight());
        item.setPartNetWeight(item.getPartNetWeight() == null ? allocation.getPartNetWeight() : item.getPartNetWeight());
    }

    private <T> T firstNonNull(T first, T fallback) {
        return first == null ? fallback : first;
    }

    private String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first;
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
        MesProductionDispatchResp.Item item = new MesProductionDispatchResp.Item();
        item.setId(externalId);
        item.setStatus(0);
        MesProductionDispatchResp.DataEnvelope data = new MesProductionDispatchResp.DataEnvelope();
        data.setItems(List.of(item));
        resp.setData(data);
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
