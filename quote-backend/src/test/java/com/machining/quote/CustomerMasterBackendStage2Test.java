package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.controller.CustomerMasterController;
import com.machining.quote.dto.CustomerMasterDTO;
import com.machining.quote.dto.CustomerMasterSaveReq;
import com.machining.quote.dto.CustomerOptionDTO;
import com.machining.quote.dto.PageResult;
import com.machining.quote.entity.CustomerMaster;
import com.machining.quote.mapper.CustomerMasterMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.CustomerMasterService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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

class CustomerMasterBackendStage2Test {
    @Test
    void controllerExposesCustomerMasterCrudWithIndependentPermissions() throws Exception {
        assertThat(CustomerMasterController.class).isNotNull();
        String source = Files.readString(
                Path.of("src/main/java/com/machining/quote/controller/CustomerMasterController.java"),
                StandardCharsets.UTF_8);

        assertThat(source)
                .contains("@RequestMapping(\"/api/system/customers\")")
                .contains("@RequirePermission(\"system:customer:view\")")
                .contains("@RequirePermission(\"system:customer:create\")")
                .contains("@RequirePermission(\"system:customer:update\")")
                .contains("@RequirePermission(\"system:customer:delete\")")
                .doesNotContain("common:customer");
    }

    @Test
    void serviceCreatesCustomerAfterRequiredAndUniqueValidation() {
        CustomerMasterMapper mapper = mock(CustomerMasterMapper.class);
        AuditService auditService = mock(AuditService.class);
        CustomerMasterService service = service(mapper, auditService);

        when(mapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        doAnswer(invocation -> {
            CustomerMaster customer = invocation.getArgument(0);
            customer.setId(9L);
            return 1;
        }).when(mapper).insert(any(CustomerMaster.class));

        CustomerMasterSaveReq req = new CustomerMasterSaveReq();
        req.setCustomerCode(" c001 ");
        req.setCustomerShortName(" acme ");
        req.setCustomerName(" ACME Manufacturing ");

        CustomerMasterDTO result = service.saveCustomer(req);

        assertThat(result.getId()).isEqualTo(9L);
        assertThat(result.getCustomerCode()).isEqualTo("c001");
        assertThat(result.getCustomerShortName()).isEqualTo("acme");
        assertThat(result.getCustomerName()).isEqualTo("ACME Manufacturing");
        verify(mapper).insert(any(CustomerMaster.class));
        verify(auditService).record(eq("CUSTOMER_MASTER_CREATE"), eq("customer_master"), eq(9L), isNull(), any());

        CustomerMasterSaveReq missing = new CustomerMasterSaveReq();
        missing.setCustomerCode("");
        missing.setCustomerShortName("A");
        missing.setCustomerName("B");
        assertThatThrownBy(() -> service.saveCustomer(missing))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("客户编码");
    }

    @Test
    void serviceRejectsDuplicateFieldsAndSupportsFieldSearchOptions() {
        CustomerMasterMapper mapper = mock(CustomerMasterMapper.class);
        CustomerMasterService service = service(mapper, mock(AuditService.class));

        CustomerMasterSaveReq duplicate = new CustomerMasterSaveReq();
        duplicate.setCustomerCode("C001");
        duplicate.setCustomerShortName("A");
        duplicate.setCustomerName("Alpha");
        when(mapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> service.saveCustomer(duplicate))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("客户编码");

        when(mapper.selectCount(any(QueryWrapper.class))).thenReturn(2L);
        when(mapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(
                customer(1L, "C001", "A", "Alpha"),
                customer(2L, "C002", "B", "Beta")
        ));

        PageResult<CustomerMasterDTO> page = service.pageCustomers("shortName", "A", 1, 20);
        List<CustomerOptionDTO> options = service.listOptions("all", "a");

        assertThat(page.getTotal()).isEqualTo(2);
        assertThat(page.getRecords()).extracting(CustomerMasterDTO::getCustomerShortName).containsExactly("A", "B");
        assertThat(options).extracting(CustomerOptionDTO::getCustomerName).containsExactly("Alpha", "Beta");
    }

    @Test
    void deleteRejectsOrderOrDataScopeReferencesAndAuditsSuccessfulDelete() {
        CustomerMasterMapper mapper = mock(CustomerMasterMapper.class);
        AuditService auditService = mock(AuditService.class);
        CustomerMasterService service = service(mapper, auditService);
        when(mapper.selectById(7L)).thenReturn(customer(7L, "C001", "A", "Alpha"));
        when(mapper.countOrderReferences("Alpha")).thenReturn(1L);

        assertThatThrownBy(() -> service.deleteCustomer(7L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("订单");
        verify(mapper, never()).deleteById(7L);

        when(mapper.countOrderReferences("Alpha")).thenReturn(0L);
        when(mapper.countDataScopeReferences("Alpha")).thenReturn(0L);
        service.deleteCustomer(7L);

        verify(mapper).deleteById(7L);
        verify(auditService).record(eq("CUSTOMER_MASTER_DELETE"), eq("customer_master"), eq(7L), any(), isNull());
    }

    @Test
    void existingCustomerSelectorsKeepPermissionsButUseCustomerMasterSource() throws Exception {
        String marketController = source("controller/MarketController.java");
        String orderController = source("controller/OrderManagementController.java");
        String engineerController = source("controller/EngineerController.java");
        String deliveryDocumentController = source("controller/DeliveryDocumentController.java");
        String deliveredController = source("controller/OrderDeliveryController.java");
        String orderService = source("service/OrderManagementService.java");
        String deliveredService = source("service/DeliveredItemService.java");
        String lockedService = source("service/LockedItemService.java");
        String dataScopeService = source("service/DataScopeService.java");

        assertThat(marketController)
                .contains("@RequirePermission(\"market:rfq:customer-select\")")
                .contains("@RequirePermission(\"market:quote-process:customer-select\")")
                .contains("@RequirePermission(\"market:release:customer-select\")");
        assertThat(orderController)
                .contains("@RequirePermission(\"market:order:customer-select\")")
                .contains("@RequirePermission(\"market:history-order:customer-select\")");
        assertThat(engineerController).contains("@RequirePermission(\"engineer:setup:customer-select\")");
        assertThat(deliveryDocumentController).contains("@RequirePermission(\"market:delivery-document:customer-select\")");
        assertThat(deliveredController)
                .contains("@RequirePermission(\"market:delivered-item:customer-select\")")
                .contains("@RequirePermission(\"market:locked-item:customer-select\")");

        assertThat(orderService).contains("CustomerMasterService").contains("listCustomerOptions(field, keyword)");
        assertThat(deliveredService).contains("CustomerMasterService").contains("listCustomerOptions(field, keyword)");
        assertThat(lockedService).contains("CustomerMasterService").contains("listCustomerOptions(field, keyword)");
        assertThat(dataScopeService).contains("CustomerMasterMapper").contains("customer_master");
    }

    private CustomerMasterService service(CustomerMasterMapper mapper, AuditService auditService) {
        CustomerMasterService service = new CustomerMasterService();
        ReflectionTestUtils.setField(service, "customerMapper", mapper);
        ReflectionTestUtils.setField(service, "auditService", auditService);
        return service;
    }

    private CustomerMaster customer(Long id, String code, String shortName, String name) {
        CustomerMaster customer = new CustomerMaster();
        customer.setId(id);
        customer.setCustomerCode(code);
        customer.setCustomerShortName(shortName);
        customer.setCustomerName(name);
        customer.setEnabled(true);
        return customer;
    }

    private String source(String path) throws Exception {
        return Files.readString(
                Path.of("src/main/java/com/machining/quote", path.replace("/", java.io.File.separator)),
                StandardCharsets.UTF_8);
    }
}
