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

class SalesOrderQpoImportControllerContractTest extends SalesOrderQpoImportTestSupport {

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
}
