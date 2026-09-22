package com.machining.quote;

import com.machining.quote.config.AuthContext;
import com.machining.quote.controller.SalesOrderQpoImportController;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.ReleaseSourceType;
import com.machining.quote.dto.SalesOrderQpoExcelLineDTO;
import com.machining.quote.dto.SalesOrderQpoExcelParseResult;
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

class SalesOrderQpoImportValidationTest extends SalesOrderQpoImportTestSupport {

    @Test
    void serviceDoesNotRejectDuplicateCustomerOrderAndInternalProjectCombinations() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/machining/quote/service/SalesOrderQpoImportService.java"),
                StandardCharsets.UTF_8);

        assertThat(source)
                .doesNotContain("validateDuplicateImports")
                .doesNotContain("headerMapper.selectCount")
                .doesNotContain("销售订单重复导入");
    }

    @Test
    void serviceRejectsCustomerWithoutDataPermission() {
        CustomerMasterMapper customerMapper = mock(CustomerMasterMapper.class);
        DataScopeService dataScopeService = mock(DataScopeService.class);
        SalesOrderQpoImportService service = service(
                mock(SalesOrderQpoExcelParser.class),
                customerMapper,
                mock(SalesOrderImportBatchMapper.class),
                mock(SalesOrderImportRecordMapper.class),
                mock(SalesOrderHeaderMapper.class),
                mock(SalesOrderItemMapper.class),
                dataScopeService,
                mock(AuditService.class)
        );
        CustomerMaster customer = customer();
        when(customerMapper.selectById(7L)).thenReturn(customer);
        org.mockito.Mockito.doThrow(new RuntimeException("No data permission for customer ACME Manufacturing"))
                .when(dataScopeService).assertCanAccessCustomer(customer);

        assertThatThrownBy(() -> service.importQpo(
                new MockMultipartFile("excelFile", "sales.xlsx", "application/vnd.ms-excel", "x".getBytes(StandardCharsets.UTF_8)),
                7L,
                "SO01"
        ))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("No data permission");
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
                .doesNotContain("new OrderRfq")
                .doesNotContain("new ItemQuoteVersion")
                .doesNotContain("orderMapper.insert")
                .doesNotContain("rfqNumberService.nextRfqNo")
                .doesNotContain("SOURCE_SALES_ORDER");
    }

    @Test
    void serviceSourceRunsAutoSnapshotCompletionServiceForProductionSnapshotInsteadOfLegacyRepeatEnrichment() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/machining/quote/service/SalesOrderQpoImportService.java"),
                StandardCharsets.UTF_8);

        assertThat(source)
                .contains("SalesOrderAutoSnapshotCompletionService")
                .contains("completeImportedOrder")
                .contains("WAITING_SNAPSHOT_MESSAGE")
                .contains("item.setMatchStatus(UNMATCHED)")
                .contains("item.setMatchMessage(WAITING_SNAPSHOT_MESSAGE)")
                .doesNotContain("SalesOrderRepeatEnrichmentService")
                .doesNotContain("repeatEnrichmentService")
                .doesNotContain("enrichSalesOrderItems")
                .doesNotContain("SalesOrderRepeatEnrichmentResult");
    }

    @Test
    void serviceRejectsMissingCustomerBeforeIndependentImportWork() {
        CustomerMasterMapper customerMapper = mock(CustomerMasterMapper.class);
        SalesOrderQpoImportService service = service(
                mock(SalesOrderQpoExcelParser.class),
                customerMapper,
                mock(SalesOrderImportBatchMapper.class),
                mock(SalesOrderImportRecordMapper.class),
                mock(SalesOrderHeaderMapper.class),
                mock(SalesOrderItemMapper.class),
                mock(DataScopeService.class),
                mock(AuditService.class)
        );
        when(customerMapper.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> service.importQpo(
                new MockMultipartFile("excelFile", "sales.xlsx", "application/vnd.ms-excel", "x".getBytes(StandardCharsets.UTF_8)),
                404L,
                "SO01"
        ))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("\u5ba2\u6237");
    }
}
