package com.machining.quote;

import com.machining.quote.config.AuthContext;
import com.machining.quote.controller.SalesOrderQpoImportController;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.ReleaseSourceType;
import com.machining.quote.dto.SalesOrderQpoExcelLineDTO;
import com.machining.quote.dto.SalesOrderQpoExcelParseResult;
import com.machining.quote.dto.SalesOrderQpoImportDefaultsDTO;
import com.machining.quote.dto.SalesOrderQpoImportResp;
import com.machining.quote.dto.SalesOrderQpoOrderGroupDTO;
import com.machining.quote.dto.SalesOrderAutoSnapshotCompletionResult;
import com.machining.quote.entity.CustomerMaster;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderImportBatch;
import com.machining.quote.entity.SalesOrderImportRecord;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.CustomerMasterMapper;
import com.machining.quote.mapper.ItemAssemblyRelationMapper;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderImportBatchMapper;
import com.machining.quote.mapper.SalesOrderImportRecordMapper;
import com.machining.quote.mapper.SalesOrderItemAssemblyRelationMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.BusinessFileService;
import com.machining.quote.service.DataScopeService;
import com.machining.quote.service.DeliveryService;
import com.machining.quote.service.SalesOrderQpoExcelParser;
import com.machining.quote.service.SalesOrderQpoImportService;
import com.machining.quote.service.SalesOrderAutoSnapshotCompletionService;
import com.machining.quote.service.SalesOrderNumberService;
import com.machining.quote.storage.StorageType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Answers;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

abstract class SalesOrderQpoImportTestSupport {
    @AfterEach
    void clearAuthContext() {
        AuthContext.clear();
    }

    protected SalesOrderQpoImportDefaultsDTO validImportDefaults() {
        SalesOrderQpoImportDefaultsDTO defaults = new SalesOrderQpoImportDefaultsDTO();
        defaults.setSalesOrderDate(LocalDate.of(2026, 9, 18));
        defaults.setCustomerOrderNo("PO-IMPORT-DEFAULT");
        defaults.setInternalProjectNo("IP-IMPORT-DEFAULT");
        defaults.setOrderType("正式订单");
        return defaults;
    }

    protected SalesOrderQpoImportService service(
            SalesOrderQpoExcelParser parser,
            CustomerMasterMapper customerMapper,
            SalesOrderImportBatchMapper batchMapper,
            SalesOrderImportRecordMapper recordMapper,
            SalesOrderHeaderMapper headerMapper,
            SalesOrderItemMapper itemMapper,
            DataScopeService dataScopeService,
            AuditService auditService) {
        return service(
                parser,
                customerMapper,
                batchMapper,
                recordMapper,
                headerMapper,
                itemMapper,
                dataScopeService,
                auditService,
                mock(SalesOrderAutoSnapshotCompletionService.class),
                mock(DeliveryService.class)
        );
    }

    protected SalesOrderQpoImportService service(
            SalesOrderQpoExcelParser parser,
            CustomerMasterMapper customerMapper,
            SalesOrderImportBatchMapper batchMapper,
            SalesOrderImportRecordMapper recordMapper,
            SalesOrderHeaderMapper headerMapper,
            SalesOrderItemMapper itemMapper,
            DataScopeService dataScopeService,
            AuditService auditService,
            SalesOrderAutoSnapshotCompletionService autoSnapshotCompletionService) {
        return service(
                parser,
                customerMapper,
                batchMapper,
                recordMapper,
                headerMapper,
                itemMapper,
                dataScopeService,
                auditService,
                autoSnapshotCompletionService,
                mock(DeliveryService.class)
        );
    }

    protected SalesOrderQpoImportService service(
            SalesOrderQpoExcelParser parser,
            CustomerMasterMapper customerMapper,
            SalesOrderImportBatchMapper batchMapper,
            SalesOrderImportRecordMapper recordMapper,
            SalesOrderHeaderMapper headerMapper,
            SalesOrderItemMapper itemMapper,
            DataScopeService dataScopeService,
            AuditService auditService,
            SalesOrderAutoSnapshotCompletionService autoSnapshotCompletionService,
            DeliveryService deliveryService) {
        SalesOrderQpoImportService service = new SalesOrderQpoImportService();
        ReflectionTestUtils.setField(service, "excelParser", parser);
        ReflectionTestUtils.setField(service, "customerMapper", customerMapper);
        ReflectionTestUtils.setField(service, "batchMapper", batchMapper);
        ReflectionTestUtils.setField(service, "recordMapper", recordMapper);
        ReflectionTestUtils.setField(service, "headerMapper", headerMapper);
        ReflectionTestUtils.setField(service, "itemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        ReflectionTestUtils.setField(service, "auditService", auditService);
        ReflectionTestUtils.setField(service, "autoSnapshotCompletionService", autoSnapshotCompletionService);
        ReflectionTestUtils.setField(service, "deliveryService", deliveryService);
        ReflectionTestUtils.setField(service, "salesOrderNumberService", new SalesOrderNumberService());
        return service;
    }

    protected void wireSalesOrderAutoSnapshotCompletionService(
            SalesOrderAutoSnapshotCompletionService service,
            SalesOrderHeaderMapper headerMapper,
            SalesOrderItemMapper itemMapper,
            ItemQuoteVersionMapper sourceMapper,
            ItemAssemblyRelationMapper historyRelationMapper,
            SalesOrderItemAssemblyRelationMapper salesOrderRelationMapper) {
        ReflectionTestUtils.setField(service, "headerMapper", headerMapper);
        ReflectionTestUtils.setField(service, "salesOrderItemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "itemQuoteVersionMapper", sourceMapper);
        ReflectionTestUtils.setField(service, "historyAssemblyRelationMapper", historyRelationMapper);
        ReflectionTestUtils.setField(service, "salesOrderAssemblyRelationMapper", salesOrderRelationMapper);
    }

    protected void wireImportAutoDeliveryService(
            DeliveryService service,
            SalesOrderHeaderMapper headerMapper,
            SalesOrderItemMapper itemMapper,
            OrderDeliveryTicketMapper ticketMapper,
            AuditService auditService,
            List<OrderDeliveryTicket> insertedTickets) {
        ReflectionTestUtils.setField(service, "salesOrderHeaderMapper", headerMapper);
        ReflectionTestUtils.setField(service, "salesOrderItemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "ticketMapper", ticketMapper);
        ReflectionTestUtils.setField(service, "auditService", auditService);
        int[] nextTicketId = {9001};
        doAnswer(invocation -> {
            OrderDeliveryTicket ticket = invocation.getArgument(0);
            ticket.setId(nextTicketId[0]++);
            insertedTickets.add(ticket);
            return 1;
        }).when(ticketMapper).insert(any(OrderDeliveryTicket.class));
    }

    protected List<SalesOrderItem> sortedItems(Map<Long, SalesOrderItem> items) {
        return items.values().stream()
                .sorted(Comparator.comparing(SalesOrderItem::getId))
                .toList();
    }

    protected long longValue(Object value) {
        return ((Number) value).longValue();
    }

    protected <T> void mergeNonNull(T target, T update) {
        if (target == null || update == null) {
            return;
        }
        for (Field field : update.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            try {
                Object value = field.get(update);
                if (value != null) {
                    field.set(target, value);
                }
            } catch (IllegalAccessException ex) {
                throw new AssertionError("Unable to merge test entity field: " + field.getName(), ex);
            }
        }
    }

    protected SalesOrderAutoSnapshotCompletionResult matchResp(int total, int matched, int unmatched, String warningMessage) {
        SalesOrderAutoSnapshotCompletionResult resp = new SalesOrderAutoSnapshotCompletionResult();
        resp.setSuccess(true);
        resp.setTotalItemCount(total);
        resp.setMatchedItemCount(matched);
        resp.setUnmatchedItemCount(unmatched);
        resp.setUpdatedItemCount(total);
        resp.setWarningMessage(warningMessage);
        return resp;
    }

    protected CustomerMaster customer() {
        CustomerMaster customer = new CustomerMaster();
        customer.setId(7L);
        customer.setCustomerCode("C001");
        customer.setCustomerShortName("ACME");
        customer.setCustomerName("ACME Manufacturing");
        customer.setEnabled(true);
        return customer;
    }

    protected SalesOrderQpoExcelParseResult parseResult() {
        SalesOrderQpoExcelParseResult result = new SalesOrderQpoExcelParseResult();
        SalesOrderQpoOrderGroupDTO first = new SalesOrderQpoOrderGroupDTO();
        first.setGroupKey("PO-001|IP-001");
        first.setCustomerOrderNo("PO-001");
        first.setInternalProjectNo("IP-001");
        first.setSalesOrderDate(LocalDate.of(2026, 7, 1));
        first.setCustomerProjectNo("CP-001");
        first.setCustomerOrderTitle("GSQ1");
        first.setOrderType("正式订单");
        first.setLines(List.of(line(1, "P-001", "A", "MAT-001", 10), line(2, "P-002", "B", "", 4)));

        SalesOrderQpoOrderGroupDTO second = new SalesOrderQpoOrderGroupDTO();
        second.setGroupKey("PO-002|IP-002");
        second.setCustomerOrderNo("PO-002");
        second.setInternalProjectNo("IP-002");
        second.setSalesOrderDate(LocalDate.of(2026, 7, 2));
        second.setCustomerProjectNo("CP-002");
        second.setCustomerOrderTitle("GSQ2");
        second.setOrderType("备货订单");
        second.setLines(List.of(line(3, "P-003", "C", "MAT-003", 5)));

        result.setOrders(List.of(first, second));
        result.setOrderCount(2);
        result.setLineCount(3);
        result.setSourceRowCount(3);
        return result;
    }

    protected SalesOrderQpoExcelParseResult parseResultWithSamePartDifferentVersions() {
        SalesOrderQpoExcelParseResult result = new SalesOrderQpoExcelParseResult();
        SalesOrderQpoOrderGroupDTO group = new SalesOrderQpoOrderGroupDTO();
        group.setGroupKey("PO-001|IP-001");
        group.setCustomerOrderNo("PO-001");
        group.setInternalProjectNo("IP-001");
        group.setSalesOrderDate(LocalDate.of(2026, 7, 1));
        group.setCustomerProjectNo("CP-001");
        group.setCustomerOrderTitle("GSQ1");
        group.setOrderType("正式订单");
        group.setLines(List.of(line(1, "P-100", "A", "MAT-001", 10), line(2, "P-100", "B", "MAT-002", 4)));

        result.setOrders(List.of(group));
        result.setOrderCount(1);
        result.setLineCount(2);
        result.setSourceRowCount(2);
        return result;
    }

    protected SalesOrderQpoExcelParseResult parseResultWithBlankPrices() {
        SalesOrderQpoExcelParseResult result = new SalesOrderQpoExcelParseResult();
        SalesOrderQpoOrderGroupDTO group = new SalesOrderQpoOrderGroupDTO();
        group.setGroupKey("PO-001|IP-001");
        group.setCustomerOrderNo("PO-001");
        group.setInternalProjectNo("IP-001");
        group.setSalesOrderDate(LocalDate.of(2026, 7, 1));
        group.setCustomerProjectNo("CP-001");
        group.setCustomerOrderTitle("GSQ1");
        group.setOrderType("正式订单");
        group.setLines(List.of(blankPriceLine(1, "P-BLANK", "A"), line(2, "P-PRICED", "A", "MAT-OLD", 3)));

        result.setOrders(List.of(group));
        result.setOrderCount(1);
        result.setLineCount(2);
        result.setSourceRowCount(2);
        return result;
    }

    protected SalesOrderQpoExcelParseResult parseResultWithMatchedUnmatchedImportFacts() {
        SalesOrderQpoExcelParseResult result = new SalesOrderQpoExcelParseResult();
        SalesOrderQpoOrderGroupDTO group = new SalesOrderQpoOrderGroupDTO();
        group.setGroupKey("PO-001|IP-001");
        group.setCustomerOrderNo("PO-001");
        group.setInternalProjectNo("IP-001");
        group.setSalesOrderDate(LocalDate.of(2026, 7, 1));
        group.setCustomerProjectNo("CP-001");
        group.setCustomerOrderTitle("GSQ1");
        group.setOrderType("正式订单");

        SalesOrderQpoExcelLineDTO matched = blankPriceLine(1, "P-MATCH", "A");
        matched.setOrderType("正式订单");
        matched.setDescription("imported matched description");
        matched.setMaterialName("imported material");
        matched.setSurfaceTreatment("imported surface");
        matched.setRemark("imported matched remark");
        matched.setInternalDeliveryDate(LocalDate.of(2026, 9, 18));

        SalesOrderQpoExcelLineDTO unmatched = line(2, "P-NO-HISTORY", "A", "", 1);
        unmatched.setOrderType("正式订单");
        unmatched.setDescription("unmatched imported description");
        unmatched.setMaterialName("unmatched imported material");
        unmatched.setSurfaceTreatment("unmatched imported surface");
        unmatched.setRemark("unmatched imported remark");
        group.setLines(List.of(matched, unmatched));

        result.setOrders(List.of(group));
        result.setOrderCount(1);
        result.setLineCount(2);
        result.setSourceRowCount(2);
        return result;
    }

    protected ItemQuoteVersion releasedProductionSnapshotSource() {
        ItemQuoteVersion source = new ItemQuoteVersion();
        source.setId(900);
        source.setRfqId(50);
        source.setPartNo("P-MATCH");
        source.setVersion("A");
        source.setStatus(QuoteStatusWorkflow.ITEM_RELEASED);
        source.setRepeatMatchEligible(true);
        source.setTaxIncludedUnitPrice(15.0);
        source.setTaxIncludedTotalPrice(15.0);
        source.setMaterialName("historical imported material");
        source.setMaterialNo("MAT-HIST-001");
        source.setMaterialNoStatus("已确认");
        source.setEngineeringMaterialCode("ENG-MAT-001");
        source.setEngineeringMaterialName("7075-T651");
        source.setEngineeringMaterialTypeCode("RAW_MATERIAL");
        source.setEngineeringMaterialTypeName("原材料");
        source.setEngineeringMaterialCategory("铝材");
        source.setMaterialShape("板材");
        source.setSurfaceTreatment("historical surface");
        source.setMaterialGrossWeight(2.4);
        source.setPartNetWeight(1.8);
        source.setQuoteMode("NORMAL");
        source.setMaterialData(materialSnapshot("7075-T651"));
        source.setProcessRoute(processRouteSnapshot("CNC"));
        source.setFeeData(feeSnapshot());
        return source;
    }

    protected Map<String, Object> materialSnapshot(String name) {
        Map<String, Object> material = new LinkedHashMap<>();
        material.put("materialCode", "ENG-MAT-001");
        material.put("materialName", name);
        material.put("name", name);
        material.put("materialTypeCode", "RAW_MATERIAL");
        material.put("materialTypeName", "原材料");
        material.put("materialCategory", "铝材");
        material.put("shape", "板材");
        return material;
    }

    protected List<Map<String, Object>> processRouteSnapshot(String processCode) {
        Map<String, Object> route = new LinkedHashMap<>();
        route.put("routeType", "PROCESS");
        route.put("sourceType", "process");
        route.put("sortNo", 10);
        route.put("processCode", processCode);
        route.put("categoryName", "machining");
        route.put("detailName", "cnc");
        route.put("hourlyRate", 12.0);
        route.put("unitWorkHours", 1.0);
        route.put("lineCost", 12.0);
        return new ArrayList<>(List.of(route));
    }

    protected List<Map<String, Object>> feeSnapshot() {
        Map<String, Object> fee = new LinkedHashMap<>();
        fee.put("feeName", "profit");
        fee.put("ratePercent", 10);
        return new ArrayList<>(List.of(fee));
    }

    protected BusinessFileService salesOrderImportDrawingStorage() {
        return mock(BusinessFileService.class, invocation -> {
            if ("saveSalesOrderImportDrawing".equals(invocation.getMethod().getName())) {
                MultipartFile drawing = invocation.getArgument(2);
                String fileName = drawing.getOriginalFilename() == null
                        ? "drawing.pdf"
                        : drawing.getOriginalFilename().replaceAll(".*[\\\\/]", "");
                return new BusinessFileService.StoredBusinessFile(
                        "/uploads/ACME/SOQPO/" + fileName,
                        StorageType.LOCAL,
                        1L,
                        "ACME/SOQPO/" + fileName,
                        fileName,
                        "application/pdf",
                        drawing.getSize(),
                        null);
            }
            return Answers.RETURNS_DEFAULTS.answer(invocation);
        });
    }

    protected SalesOrderQpoExcelLineDTO line(int lineNo, String partNo, String version, String salesMaterialNo, int qty) {
        SalesOrderQpoExcelLineDTO line = new SalesOrderQpoExcelLineDTO();
        line.setSourceRowNumber(lineNo + 1);
        line.setSalesOrderLineNo(lineNo);
        line.setSalesOrderDate(LocalDate.of(2026, 7, 1));
        line.setCustomerOrderNo(lineNo == 3 ? "PO-002" : "PO-001");
        line.setCustomerProjectNo(lineNo == 3 ? "CP-002" : "CP-001");
        line.setCustomerOrderTitle(lineNo == 3 ? "GSQ2" : "GSQ1");
        line.setOrderType(lineNo == 3 ? "备货订单" : "正式订单");
        line.setSalesMaterialNo(salesMaterialNo);
        line.setInternalProjectNo(lineNo == 3 ? "IP-002" : "IP-001");
        line.setCustomerDeliveryDate(LocalDate.of(2026, 8, 1));
        line.setInternalDeliveryDate(LocalDate.of(2026, 8, 5));
        line.setPartNo(partNo);
        line.setQty(qty);
        line.setShippedQty(0);
        line.setUnshippedQty(qty);
        line.setDescription("desc-" + partNo);
        line.setMaterialName("6061");
        line.setSurfaceTreatment("/");
        line.setVersion(version);
        line.setTaxIncludedUnitPrice(new BigDecimal("11.30"));
        line.setUntaxedUnitPrice(new BigDecimal("10.00"));
        line.setTaxIncludedTotalPrice(new BigDecimal("113.00"));
        line.setUntaxedTotalPrice(new BigDecimal("100.00"));
        line.setRemark("remark");
        return line;
    }

    protected SalesOrderQpoExcelLineDTO blankPriceLine(int lineNo, String partNo, String version) {
        SalesOrderQpoExcelLineDTO line = line(lineNo, partNo, version, "", 2);
        line.setTaxIncludedUnitPrice(null);
        line.setUntaxedUnitPrice(null);
        line.setTaxIncludedTotalPrice(null);
        line.setUntaxedTotalPrice(null);
        return line;
    }
}
