package com.machining.quote;

import com.machining.quote.config.AuthContext;
import com.machining.quote.controller.SalesOrderQpoImportController;
import com.machining.quote.entity.CustomerMaster;
import com.machining.quote.mapper.CustomerMasterMapper;
import com.machining.quote.service.SalesOrderQpoImportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOrderQpoImportStage3Test {
    @AfterEach
    void clearAuthContext() {
        AuthContext.clear();
    }

    @Test
    void controllerKeepsSalesOrderImportPermissionButUsesIndependentBoundaryService() throws Exception {
        assertThat(SalesOrderQpoImportController.class).isNotNull();
        String source = Files.readString(
                Path.of("src/main/java/com/machining/quote/controller/SalesOrderQpoImportController.java"),
                StandardCharsets.UTF_8);

        assertThat(source)
                .contains("@RequestMapping(\"/api/market/sales-orders\")")
                .contains("@PostMapping(\"/import-qpo\")")
                .contains("@RequirePermission(\"market:rfq:import-sales-order\")")
                .contains("importQpo(")
                .doesNotContain("SALES_ORDER_IMPORT_U9");
    }

    @Test
    void serviceRejectsQpoImportUntilIndependentWriterExists() {
        CustomerMasterMapper customerMapper = mock(CustomerMasterMapper.class);
        SalesOrderQpoImportService service = new SalesOrderQpoImportService();
        ReflectionTestUtils.setField(service, "customerMapper", customerMapper);
        when(customerMapper.selectById(7L)).thenReturn(customer());

        assertThatThrownBy(() -> service.importQpo(
                new MockMultipartFile(
                        "excelFile",
                        "sales-order.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "xlsx".getBytes(StandardCharsets.UTF_8)
                ),
                7L,
                "SO01"
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("\u72ec\u7acb\u4e3b\u8868")
                .hasMessageContaining("RFQ");
        verify(customerMapper).selectById(7L);
    }

    @Test
    void serviceSourceDoesNotCreateRfqOrdersOrItemQuoteVersions() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/machining/quote/service/SalesOrderQpoImportService.java"),
                StandardCharsets.UTF_8);

        assertThat(source)
                .doesNotContain("OrderRfqMapper")
                .doesNotContain("ItemQuoteVersionMapper")
                .doesNotContain("RfqNumberService")
                .doesNotContain("orderMapper.insert")
                .doesNotContain("itemMapper.insert")
                .doesNotContain("rfqNumberService.nextRfqNo")
                .doesNotContain("SOURCE_SALES_ORDER");
    }

    @Test
    void serviceRejectsMissingCustomerBeforeIndependentImportWork() {
        CustomerMasterMapper customerMapper = mock(CustomerMasterMapper.class);
        SalesOrderQpoImportService service = new SalesOrderQpoImportService();
        ReflectionTestUtils.setField(service, "customerMapper", customerMapper);
        when(customerMapper.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> service.importQpo(
                new MockMultipartFile("excelFile", "sales.xlsx", "application/vnd.ms-excel", "x".getBytes(StandardCharsets.UTF_8)),
                404L,
                "SO01"
        ))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("\u5ba2\u6237");
    }

    private CustomerMaster customer() {
        CustomerMaster customer = new CustomerMaster();
        customer.setId(7L);
        customer.setCustomerCode("C001");
        customer.setCustomerShortName("ACME");
        customer.setCustomerName("ACME Manufacturing");
        customer.setEnabled(true);
        return customer;
    }
}
