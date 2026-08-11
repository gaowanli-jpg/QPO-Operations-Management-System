package com.machining.quote;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.machining.quote.controller.MarketController;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.DeliveryBatchResultDTO;
import com.machining.quote.dto.DeliveryGenerateReq;
import com.machining.quote.dto.ReleaseSourceType;
import com.machining.quote.dto.RfqExcelDTO;
import com.machining.quote.entity.CustomerMaster;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.entity.U9SyncDepartmentOptionCache;
import com.machining.quote.entity.U9SyncSellerOptionCache;
import com.machining.quote.mapper.CustomerMasterMapper;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.mapper.U9SyncDepartmentOptionCacheMapper;
import com.machining.quote.mapper.U9SyncSellerOptionCacheMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.DeliveryService;
import com.machining.quote.service.RfqNumberService;
import com.machining.quote.service.SalesOrderU9SyncStateService;
import com.machining.quote.service.u9.U9SalesOrderContractValidator;
import com.machining.quote.service.u9.U9SalesOrderRequestAssembler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class U9CustomerLinkStage7Test {
    @Test
    void rfqImportRequiresEnabledU9CustomerCodeAndUsesCacheSnapshot() {
        MarketController controller = marketController();
        CustomerMasterMapper customerMapper = customerMapper(controller);
        CustomerMaster customer = customer("C001", "U9-SHORT", "U9 Customer Name", true);
        when(customerMapper.selectEnabledByCode("C001")).thenReturn(customer);

        controller.importRfq("C001", "client-short", "Free Text Customer", rfqFile(), List.of(), List.of());

        var orderCaptor = org.mockito.ArgumentCaptor.forClass(OrderRfq.class);
        verify(orderRfqMapper(controller)).insert(orderCaptor.capture());
        OrderRfq inserted = orderCaptor.getValue();
        assertThat(inserted.getCustomerCode()).isEqualTo("C001");
        assertThat(inserted.getCustomerShortName()).isEqualTo("U9-SHORT");
        assertThat(inserted.getCustomerName()).isEqualTo("U9 Customer Name");

        MarketController missingCodeController = marketController();
        assertThatThrownBy(() -> missingCodeController.importRfq(null, null, "Free Text Customer", rfqFile(), List.of(), List.of()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("customerCode");
        verify(orderRfqMapper(missingCodeController), never()).insert(any(OrderRfq.class));

        MarketController disabledController = marketController();
        when(customerMapper(disabledController).selectEnabledByCode("C-DISABLED")).thenReturn(null);
        assertThatThrownBy(() -> disabledController.importRfq("C-DISABLED", null, "Disabled", rfqFile(), List.of(), List.of()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("C-DISABLED");
        verify(orderRfqMapper(disabledController), never()).insert(any(OrderRfq.class));
    }

    @Test
    void u9SyncReserveRejectsInactiveCustomerSellerAndDepartmentBeforeRequestReservation() {
        SyncFixture fixture = syncFixture();
        SalesOrderHeader header = syncHeader();
        SalesOrderItem item = syncItem(header.getId());
        when(fixture.headerMapper.selectByIdForUpdate(header.getId())).thenReturn(header);
        when(fixture.itemMapper.selectList(any())).thenReturn(List.of(item));
        when(fixture.headerMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        when(fixture.customerMapper.selectEnabledByCode("C001")).thenReturn(null);
        assertThatThrownBy(() -> fixture.service.reserve(header.getId()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("C001");
        verify(fixture.itemMapper, never()).selectList(any());

        SyncFixture sellerFixture = syncFixture();
        SalesOrderHeader sellerHeader = syncHeader();
        when(sellerFixture.headerMapper.selectByIdForUpdate(sellerHeader.getId())).thenReturn(sellerHeader);
        when(sellerFixture.headerMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);
        when(sellerFixture.customerMapper.selectEnabledByCode("C001")).thenReturn(customer("C001", "C1", "Customer", true));
        when(sellerFixture.sellerMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);
        assertThatThrownBy(() -> sellerFixture.service.reserve(sellerHeader.getId()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("S001");
        verify(sellerFixture.itemMapper, never()).selectList(any());

        SyncFixture departmentFixture = syncFixture();
        SalesOrderHeader departmentHeader = syncHeader();
        departmentHeader.setU9SellerCode(null);
        when(departmentFixture.headerMapper.selectByIdForUpdate(departmentHeader.getId())).thenReturn(departmentHeader);
        when(departmentFixture.headerMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);
        when(departmentFixture.customerMapper.selectEnabledByCode("C001")).thenReturn(customer("C001", "C1", "Customer", true));
        when(departmentFixture.departmentMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);
        assertThatThrownBy(() -> departmentFixture.service.reserve(departmentHeader.getId()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("D001");
        verify(departmentFixture.itemMapper, never()).selectList(any());
    }

    private MarketController marketController() {
        MarketController controller = new MarketController();
        OrderRfqMapper orderMapper = mock(OrderRfqMapper.class);
        ItemQuoteVersionMapper itemMapper = mock(ItemQuoteVersionMapper.class);
        CustomerMasterMapper customerMapper = mock(CustomerMasterMapper.class);
        RfqNumberService rfqNumberService = mock(RfqNumberService.class);
        ReflectionTestUtils.setField(controller, "orderRfqMapper", orderMapper);
        ReflectionTestUtils.setField(controller, "itemQuoteVersionMapper", itemMapper);
        ReflectionTestUtils.setField(controller, "customerMasterMapper", customerMapper);
        ReflectionTestUtils.setField(controller, "rfqNumberService", rfqNumberService);
        ReflectionTestUtils.setField(controller, "auditService", mock(AuditService.class));
        when(rfqNumberService.nextRfqNo()).thenReturn("RFQ-STAGE7-001");
        doAnswer(invocation -> {
            OrderRfq order = invocation.getArgument(0);
            order.setId(88);
            return 1;
        }).when(orderMapper).insert(any(OrderRfq.class));
        return controller;
    }

    private OrderRfqMapper orderRfqMapper(MarketController controller) {
        return (OrderRfqMapper) ReflectionTestUtils.getField(controller, "orderRfqMapper");
    }

    private CustomerMasterMapper customerMapper(MarketController controller) {
        return (CustomerMasterMapper) ReflectionTestUtils.getField(controller, "customerMasterMapper");
    }

    private MockMultipartFile rfqFile() {
        RfqExcelDTO row = new RfqExcelDTO();
        row.setPartNo("P-001");
        row.setDescription("Part 1");
        row.setQty(1);
        row.setUnit("pcs");
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        EasyExcel.write(outputStream, RfqExcelDTO.class).sheet("rfq").doWrite(List.of(row));
        return new MockMultipartFile(
                "excelFile",
                "rfq.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                outputStream.toByteArray());
    }

    private SyncFixture syncFixture() {
        SyncFixture fixture = new SyncFixture();
        ReflectionTestUtils.setField(fixture.service, "headerMapper", fixture.headerMapper);
        ReflectionTestUtils.setField(fixture.service, "itemMapper", fixture.itemMapper);
        ReflectionTestUtils.setField(fixture.service, "customerMapper", fixture.customerMapper);
        ReflectionTestUtils.setField(fixture.service, "sellerOptionMapper", fixture.sellerMapper);
        ReflectionTestUtils.setField(fixture.service, "departmentOptionMapper", fixture.departmentMapper);
        ReflectionTestUtils.setField(fixture.service, "requestAssembler", new U9SalesOrderRequestAssembler());
        ReflectionTestUtils.setField(fixture.service, "contractValidator", new U9SalesOrderContractValidator());
        return fixture;
    }

    private SalesOrderHeader syncHeader() {
        SalesOrderHeader header = new SalesOrderHeader();
        header.setId(7001L);
        header.setSalesOrderNo("SO-STAGE7-001");
        header.setDocumentNo("SO-STAGE7-001");
        header.setDocumentTypeCode("SO01");
        header.setCustomerCode("C001");
        header.setCustomerName("Customer Snapshot");
        header.setSalesOrderDate(LocalDate.of(2026, 8, 11));
        header.setStatus(QuoteStatusWorkflow.RELEASED);
        header.setU9SyncStatus(SalesOrderU9SyncStateService.STATUS_UNSYNCED);
        header.setU9SellerCode("S001");
        header.setU9SellerName("Seller Snapshot");
        header.setU9SaleDepartmentCode("D001");
        header.setU9SaleDepartmentName("Department Snapshot");
        return header;
    }

    private SalesOrderItem syncItem(Long salesOrderId) {
        SalesOrderItem item = new SalesOrderItem();
        item.setId(8001L);
        item.setSalesOrderId(salesOrderId);
        item.setLineNo(1);
        item.setPartNo("P-001");
        item.setMaterialNo("MAT-001");
        item.setMaterialNoStatus("\u5df2\u786e\u8ba4");
        item.setQty(1);
        item.setReleasedQty(1);
        item.setRemainingQty(0);
        item.setUntaxedUnitPrice(new BigDecimal("100"));
        item.setStatus(QuoteStatusWorkflow.RELEASED);
        return item;
    }

    private CustomerMaster customer(String code, String shortName, String name, boolean enabled) {
        CustomerMaster customer = new CustomerMaster();
        customer.setId(100L);
        customer.setCustomerCode(code);
        customer.setCustomerShortName(shortName);
        customer.setCustomerName(name);
        customer.setEnabled(enabled);
        return customer;
    }

    private static class SyncFixture {
        private final SalesOrderU9SyncStateService service = new SalesOrderU9SyncStateService();
        private final SalesOrderHeaderMapper headerMapper = mock(SalesOrderHeaderMapper.class);
        private final SalesOrderItemMapper itemMapper = mock(SalesOrderItemMapper.class);
        private final CustomerMasterMapper customerMapper = mock(CustomerMasterMapper.class);
        private final U9SyncSellerOptionCacheMapper sellerMapper = mock(U9SyncSellerOptionCacheMapper.class);
        private final U9SyncDepartmentOptionCacheMapper departmentMapper = mock(U9SyncDepartmentOptionCacheMapper.class);
    }
}

@SpringBootTest(properties = {
        "erp.auth.enabled=false",
        "erp.upload-dir=${java.io.tmpdir}/u9-customer-stage7/uploads/",
        "erp.backup-dir=${java.io.tmpdir}/u9-customer-stage7/backups/"
})
@Transactional
class U9CustomerLinkStage7IntegrationTest {
    @Autowired
    private DeliveryService deliveryService;
    @Autowired
    private CustomerMasterMapper customerMapper;
    @Autowired
    private OrderRfqMapper orderMapper;
    @Autowired
    private ItemQuoteVersionMapper itemMapper;
    @Autowired
    private SalesOrderHeaderMapper headerMapper;

    @Test
    void rfqReleaseSalesOrderCopiesRfqCustomerCodeSnapshotInsteadOfLookingUpByName() {
        CustomerMaster customer = customer("C-STAGE7-" + System.nanoTime(), "U9-Current-Name", true);
        OrderRfq rfq = rfq(customer.getCustomerCode(), "RFQ-Snapshot-Name", "RFQ-STAGE7-" + System.nanoTime());
        ItemQuoteVersion item = item(rfq);
        DeliveryGenerateReq req = releaseRequest(rfq, item);

        DeliveryBatchResultDTO result = deliveryService.generateRfqReleaseBatch(req);

        SalesOrderHeader header = headerMapper.selectById(result.getSalesOrderId());
        assertThat(header.getCustomerId()).isEqualTo(customer.getId());
        assertThat(header.getCustomerCode()).isEqualTo(customer.getCustomerCode());
        assertThat(header.getCustomerName()).isEqualTo("RFQ-Snapshot-Name");

        OrderRfq missingCode = rfq(null, "RFQ-Snapshot-Name", "RFQ-MISSING-CODE-" + System.nanoTime());
        ItemQuoteVersion missingItem = item(missingCode);
        assertThatThrownBy(() -> deliveryService.generateRfqReleaseBatch(releaseRequest(missingCode, missingItem)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("customerCode");
    }

    private CustomerMaster customer(String code, String name, boolean enabled) {
        CustomerMaster customer = new CustomerMaster();
        customer.setCustomerCode(code);
        customer.setCustomerShortName(code);
        customer.setCustomerName(name);
        customer.setEnabled(enabled);
        customerMapper.insert(customer);
        return customer;
    }

    private OrderRfq rfq(String customerCode, String customerName, String rfqNo) {
        OrderRfq order = new OrderRfq();
        order.setRfqNo(rfqNo);
        order.setCustomerCode(customerCode);
        order.setCustomerShortName(customerCode);
        order.setCustomerName(customerName);
        order.setStatus(QuoteStatusWorkflow.QUOTED);
        orderMapper.insert(order);
        return order;
    }

    private ItemQuoteVersion item(OrderRfq order) {
        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setRfqId(order.getId());
        item.setPartNo("P-STAGE7");
        item.setVersion("A");
        item.setDescription("stage7 item");
        item.setQty(1);
        item.setTaxIncludedUnitPrice(113.0);
        item.setUntaxedUnitPrice(100.0);
        item.setStatus(QuoteStatusWorkflow.QUOTED);
        itemMapper.insert(item);
        return item;
    }

    private DeliveryGenerateReq releaseRequest(OrderRfq order, ItemQuoteVersion item) {
        DeliveryGenerateReq req = new DeliveryGenerateReq();
        req.setRfqIds(List.of(order.getId()));
        req.setDocumentTypeCode("SO01");
        req.setSalesOrderDate(LocalDate.of(2026, 8, 11));
        req.setCustomerOrderNo("PO-STAGE7");
        req.setOrderType("\u6b63\u5f0f\u8ba2\u5355");
        req.setInternalProjectNo("IP-STAGE7");
        req.setReleaseBatchKey("stage7-" + order.getRfqNo());
        DeliveryGenerateReq.DeliveryItemReq line = new DeliveryGenerateReq.DeliveryItemReq();
        line.setItemId(item.getId());
        line.setSourceType(ReleaseSourceType.RFQ);
        line.setSourceOrderId(order.getId().longValue());
        line.setSourceItemId(item.getId().longValue());
        line.setAcceptedQty(1);
        req.setItems(List.of(line));
        return req;
    }
}
