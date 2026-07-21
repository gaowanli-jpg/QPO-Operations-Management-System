package com.machining.quote;

import com.alibaba.excel.EasyExcel;
import com.machining.quote.config.AuthContext;
import com.machining.quote.controller.MarketController;
import com.machining.quote.dto.RfqExcelDTO;
import com.machining.quote.entity.ErpUser;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.entity.SystemTask;
import com.machining.quote.mapper.ErpUserMapper;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.mapper.SystemTaskMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.AuthService;
import com.machining.quote.service.JwtService;
import com.machining.quote.service.QuoteProcessService;
import com.machining.quote.service.QuoteTemplateService;
import com.machining.quote.service.RfqNumberService;
import com.machining.quote.service.SystemTaskService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditActionCoverageTests {
    private static final Path WMS_ALLOCATION_PLAN = Path.of(
            "..", "docs", "QPO-MES-WMS对接", "04-WMS锁库分配与锁库零件模块开发文档.md");
    private static final List<String> VALID_TEMPLATE_HEADERS = List.of(
            "序号", "图号", "物料描述", "图纸状态", "材料名称", "表面处理", "版本", "数量", "单位",
            "工艺名称", "类型", "工时/金额", "单价", "费用",
            "运输费率(%)", "运输金额", "基础成本",
            "含税单价", "含税总价", "交货周期", "备注"
    );

    @TempDir
    private Path tempDir;

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void authLoginSuccessWritesAuditLog() {
        AuditService auditService = mock(AuditService.class);
        ErpUserMapper userMapper = mock(ErpUserMapper.class);
        JwtService jwtService = mock(JwtService.class);
        AuthService authService = authService(userMapper, jwtService, auditService);
        ErpUser user = user("admin", authService.hashPassword("correct-password"), 0);
        when(userMapper.selectByUsernameForUpdate("admin")).thenReturn(user);
        when(jwtService.createToken(any(AuthContext.SessionUser.class))).thenReturn("token-1");

        Map<String, Object> result = authService.login("admin", "correct-password");

        assertThat(result).containsEntry("token", "token-1");
        verify(auditService).record(
                eq("AUTH_LOGIN_SUCCESS"),
                eq("erp_user"),
                eq(7),
                isNull(),
                any(Map.class),
                eq("AUTH"),
                eq("SUCCESS"),
                contains("admin"),
                isNull()
        );
    }

    @Test
    void authWrongPasswordAndLockWritesAuditLogs() {
        AuditService auditService = mock(AuditService.class);
        ErpUserMapper userMapper = mock(ErpUserMapper.class);
        JwtService jwtService = mock(JwtService.class);
        AuthService authService = authService(userMapper, jwtService, auditService);
        ErpUser user = user("locked-user", authService.hashPassword("correct-password"), 4);
        when(userMapper.selectByUsernameForUpdate("locked-user")).thenReturn(user);

        assertThatThrownBy(() -> authService.login("locked-user", "bad-password"))
                .isInstanceOf(RuntimeException.class);

        verify(auditService).record(
                eq("AUTH_LOGIN_FAILED"),
                eq("erp_user"),
                eq(7),
                isNull(),
                any(Map.class),
                eq("AUTH"),
                eq("FAILED"),
                contains("locked-user"),
                isNull()
        );
        verify(auditService).record(
                eq("AUTH_ACCOUNT_LOCKED"),
                eq("erp_user"),
                eq(7),
                isNull(),
                any(Map.class),
                eq("AUTH"),
                eq("FAILED"),
                contains("locked-user"),
                isNull()
        );
    }

    @Test
    void authLogoutWritesAuditLog() {
        AuditService auditService = mock(AuditService.class);
        ErpUserMapper userMapper = mock(ErpUserMapper.class);
        JwtService jwtService = mock(JwtService.class);
        AuthService authService = authService(userMapper, jwtService, auditService);
        when(jwtService.parseToken("token-1")).thenReturn(new AuthContext.SessionUser(7, "admin", "Admin", "ADMIN"));

        authService.logout("token-1");

        verify(auditService).record(
                eq("AUTH_LOGOUT"),
                eq("erp_user"),
                eq(7),
                isNull(),
                any(Map.class),
                eq("AUTH"),
                eq("SUCCESS"),
                contains("admin"),
                isNull()
        );
    }

    @Test
    void quoteTemplateUploadWritesAuditSummary() throws Exception {
        AuditService auditService = mock(AuditService.class);
        QuoteTemplateService service = new QuoteTemplateService();
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());
        ReflectionTestUtils.setField(service, "auditService", auditService);
        MockMultipartFile file = new MockMultipartFile(
                "templateFile",
                "customer-template.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                workbookBytes(VALID_TEMPLATE_HEADERS)
        );

        service.uploadTemplate(file);

        verify(auditService).record(
                eq("QUOTE_TEMPLATE_UPLOAD"),
                eq("quote_template"),
                eq("customer-template.xlsx"),
                isNull(),
                any(Map.class),
                eq("SYSTEM"),
                eq("SUCCESS"),
                contains("customer-template.xlsx"),
                isNull()
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void rfqImportWritesAuditSummary() {
        AuditService auditService = mock(AuditService.class);
        OrderRfqMapper orderRfqMapper = mock(OrderRfqMapper.class);
        ItemQuoteVersionMapper itemQuoteVersionMapper = mock(ItemQuoteVersionMapper.class);
        RfqNumberService rfqNumberService = mock(RfqNumberService.class);
        MarketController controller = new MarketController();
        ReflectionTestUtils.setField(controller, "auditService", auditService);
        ReflectionTestUtils.setField(controller, "orderRfqMapper", orderRfqMapper);
        ReflectionTestUtils.setField(controller, "itemQuoteVersionMapper", itemQuoteVersionMapper);
        ReflectionTestUtils.setField(controller, "rfqNumberService", rfqNumberService);
        when(rfqNumberService.nextRfqNo()).thenReturn("RFQ-20260717-001");
        doAnswer(invocation -> {
            OrderRfq order = invocation.getArgument(0);
            order.setId(88);
            return 1;
        }).when(orderRfqMapper).insert(any(OrderRfq.class));

        controller.importRfq("ACME", "备货订单", rfqFile(), List.of());

        ArgumentCaptor<Object> afterDataCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<OrderRfq> orderCaptor = ArgumentCaptor.forClass(OrderRfq.class);
        ArgumentCaptor<ItemQuoteVersion> itemCaptor = ArgumentCaptor.forClass(ItemQuoteVersion.class);
        verify(auditService).record(
                eq("RFQ_IMPORT"),
                eq("order_rfq"),
                eq(88),
                isNull(),
                afterDataCaptor.capture(),
                eq("MARKET"),
                eq("SUCCESS"),
                messageCaptor.capture(),
                isNull()
        );
        assertThat(messageCaptor.getValue())
                .contains("RFQ-20260717-001")
                .contains("备货订单");
        Map<String, Object> afterData = (Map<String, Object>) afterDataCaptor.getValue();
        assertThat(afterData)
                .containsEntry("customerName", "ACME")
                .containsEntry("orderType", "备货订单")
                .containsEntry("rfqNo", "RFQ-20260717-001")
                .containsEntry("itemCount", 2)
                .containsEntry("uploadedDrawingCount", 0);
        verify(orderRfqMapper).insert(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getOrderType()).isEqualTo("备货订单");
        verify(itemQuoteVersionMapper, times(2)).insert(itemCaptor.capture());
        assertThat(itemCaptor.getAllValues())
                .extracting(ItemQuoteVersion::getOrderType)
                .containsExactly("备货订单", "备货订单");
    }

    @Test
    void rfqImportRejectsMissingOrderType() {
        MarketController controller = new MarketController();

        assertThatThrownBy(() -> controller.importRfq("ACME", "", rfqFile(), List.of()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("请选择订单类型");
    }

    @Test
    void quoteRefreshTaskSubmitWritesAuditWithTaskId() {
        AuditService auditService = mock(AuditService.class);
        SystemTaskService service = systemTaskService(auditService);
        AuthContext.set(new AuthContext.SessionUser(2, "engineer", "Engineer", "ENGINEER"));

        SystemTask task = service.submitRefreshQuotesTask(33);

        assertThat(task.getId()).isEqualTo(99L);
        verify(auditService).record(
                eq("QUOTE_REFRESH_TASK_SUBMIT"),
                eq("system_task"),
                eq(99L),
                isNull(),
                any(Map.class),
                eq("ENGINEER"),
                eq("SUCCESS"),
                contains("99"),
                isNull()
        );
    }

    @Test
    void quoteExportTaskSubmitWritesAuditWithTaskId() {
        AuditService auditService = mock(AuditService.class);
        SystemTaskService service = systemTaskService(auditService);
        AuthContext.set(new AuthContext.SessionUser(3, "market", "Market", "MARKET"));

        SystemTask task = service.submitQuoteExportTask(44, "simple");

        assertThat(task.getId()).isEqualTo(99L);
        QuoteProcessService quoteProcessService = (QuoteProcessService) ReflectionTestUtils.getField(service, "quoteProcessService");
        verify(quoteProcessService).assertQuoteExportable(44);
        verify(auditService).record(
                eq("QUOTE_EXPORT_TASK_SUBMIT"),
                eq("system_task"),
                eq(99L),
                isNull(),
                any(Map.class),
                eq("MARKET"),
                eq("SUCCESS"),
                startsWith("QUOTE_EXPORT_TASK_SUBMIT"),
                isNull()
        );
    }

    @Test
    void quoteExportTaskRejectsRetiredCustomerLeadTimeExportType() {
        AuditService auditService = mock(AuditService.class);
        SystemTaskService service = systemTaskService(auditService);

        assertThatThrownBy(() -> service.submitQuoteExportTask(44, "CUSTOMER_LEAD_TIME"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("\u4e0d\u652f\u6301\u7684\u62a5\u4ef7\u5355\u5bfc\u51fa\u7c7b\u578b");
    }

    @Test
    void criticalAuditActionsExposeExpectedModulesAndLabels() {
        assertThat(AuditService.inferModule("AUTH_LOGIN_SUCCESS")).isEqualTo("AUTH");
        assertThat(AuditService.inferModule("AUTH_LOGIN_FAILED")).isEqualTo("AUTH");
        assertThat(AuditService.inferModule("AUTH_ACCOUNT_LOCKED")).isEqualTo("AUTH");
        assertThat(AuditService.inferModule("AUTH_LOGOUT")).isEqualTo("AUTH");
        assertThat(AuditService.inferModule("QUOTE_TEMPLATE_UPLOAD")).isEqualTo("SYSTEM");
        assertThat(AuditService.inferModule("RFQ_IMPORT")).isEqualTo("MARKET");
        assertThat(AuditService.inferModule("QUOTE_EXPORT_TASK_SUBMIT")).isEqualTo("MARKET");
        assertThat(AuditService.inferModule("QUOTE_REFRESH_TASK_SUBMIT")).isEqualTo("ENGINEER");
        assertThat(AuditService.inferModule("DELIVERY_MES_MATERIAL_SYNC_SUBMIT")).isEqualTo("DELIVERY");
        assertThat(AuditService.inferModule("DELIVERY_MES_MATERIAL_SYNC_SUCCESS")).isEqualTo("DELIVERY");
        assertThat(AuditService.inferModule("DELIVERY_MES_MATERIAL_SYNC_FAILED")).isEqualTo("DELIVERY");
        assertThat(AuditService.inferModule("DELIVERY_WMS_ALLOCATION_SUBMIT")).isEqualTo("DELIVERY");
        assertThat(AuditService.inferModule("DELIVERY_WMS_ALLOCATION_SUCCESS")).isEqualTo("DELIVERY");
        assertThat(AuditService.inferModule("DELIVERY_WMS_ALLOCATION_FAILED")).isEqualTo("DELIVERY");

        assertThat(AuditService.labelForAction("AUTH_ACCOUNT_LOCKED")).isNotEqualTo("AUTH_ACCOUNT_LOCKED");
        assertThat(AuditService.labelForAction("RFQ_IMPORT")).isNotEqualTo("RFQ_IMPORT");
        assertThat(AuditService.labelForAction("QUOTE_EXPORT_TASK_SUBMIT")).isNotEqualTo("QUOTE_EXPORT_TASK_SUBMIT");
        assertThat(AuditService.labelForAction("QUOTE_REFRESH_TASK_SUBMIT")).isNotEqualTo("QUOTE_REFRESH_TASK_SUBMIT");
        assertThat(AuditService.labelForAction("DELIVERY_MES_MATERIAL_SYNC_SUBMIT")).isNotEqualTo("DELIVERY_MES_MATERIAL_SYNC_SUBMIT");
        assertThat(AuditService.labelForAction("DELIVERY_MES_MATERIAL_SYNC_SUCCESS")).isNotEqualTo("DELIVERY_MES_MATERIAL_SYNC_SUCCESS");
        assertThat(AuditService.labelForAction("DELIVERY_MES_MATERIAL_SYNC_FAILED")).isNotEqualTo("DELIVERY_MES_MATERIAL_SYNC_FAILED");
        assertThat(AuditService.labelForAction("DELIVERY_WMS_ALLOCATION_SUBMIT")).isNotEqualTo("DELIVERY_WMS_ALLOCATION_SUBMIT");
        assertThat(AuditService.labelForAction("DELIVERY_WMS_ALLOCATION_SUCCESS")).isNotEqualTo("DELIVERY_WMS_ALLOCATION_SUCCESS");
        assertThat(AuditService.labelForAction("DELIVERY_WMS_ALLOCATION_FAILED")).isNotEqualTo("DELIVERY_WMS_ALLOCATION_FAILED");
    }

    @Test
    void wmsAllocationAuditActionsAndFileLogEventsAreReserved() throws Exception {
        String plan = Files.readString(WMS_ALLOCATION_PLAN, StandardCharsets.UTF_8);

        assertThat(plan)
                .as("WMS allocation must be auditable before implementation starts")
                .contains(
                        "DELIVERY_WMS_ALLOCATION_SUBMIT",
                        "DELIVERY_WMS_ALLOCATION_SUCCESS",
                        "DELIVERY_WMS_ALLOCATION_FAILED",
                        "WMS allocation submitted",
                        "WMS allocation started",
                        "WMS allocation completed",
                        "WMS allocation rejected",
                        "WMS allocation failed",
                        "qpo-error.log"
                );
    }

    private AuthService authService(ErpUserMapper userMapper, JwtService jwtService, AuditService auditService) {
        AuthService authService = new AuthService();
        ReflectionTestUtils.setField(authService, "userMapper", userMapper);
        ReflectionTestUtils.setField(authService, "jwtService", jwtService);
        ReflectionTestUtils.setField(authService, "auditService", auditService);
        return authService;
    }

    private ErpUser user(String username, String passwordHash, int failedAttempts) {
        ErpUser user = new ErpUser();
        user.setId(7);
        user.setUsername(username);
        user.setDisplayName(username);
        user.setRole("ADMIN");
        user.setPasswordHash(passwordHash);
        user.setEnabled(true);
        user.setForcePasswordChange(false);
        user.setFailedLoginAttempts(failedAttempts);
        return user;
    }

    private SystemTaskService systemTaskService(AuditService auditService) {
        SystemTaskMapper taskMapper = mock(SystemTaskMapper.class);
        ThreadPoolTaskExecutor taskExecutor = mock(ThreadPoolTaskExecutor.class);
        AtomicReference<SystemTask> insertedTask = new AtomicReference<>();
        doAnswer(invocation -> {
            SystemTask task = invocation.getArgument(0);
            task.setId(99L);
            insertedTask.set(task);
            return 1;
        }).when(taskMapper).insert(any(SystemTask.class));
        when(taskMapper.selectById(99L)).thenAnswer(invocation -> insertedTask.get());

        SystemTaskService service = new SystemTaskService();
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "taskExecutor", taskExecutor);
        ReflectionTestUtils.setField(service, "auditService", auditService);
        ReflectionTestUtils.setField(service, "quoteProcessService", mock(QuoteProcessService.class));
        ReflectionTestUtils.setField(service, "taskOutputDir", tempDir.toString());
        return service;
    }

    private MockMultipartFile rfqFile() {
        RfqExcelDTO first = new RfqExcelDTO();
        first.setPartNo("P-001");
        first.setDescription("Part 1");
        first.setQty(1);
        first.setUnit("pcs");
        RfqExcelDTO second = new RfqExcelDTO();
        second.setPartNo("P-002");
        second.setDescription("Part 2");
        second.setQty(2);
        second.setUnit("pcs");
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        EasyExcel.write(outputStream, RfqExcelDTO.class).sheet("rfq").doWrite(List.of(first, second));
        return new MockMultipartFile(
                "excelFile",
                "rfq.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                outputStream.toByteArray()
        );
    }

    private byte[] workbookBytes(List<String> headers) throws Exception {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Row row = workbook.createSheet("quote").createRow(7);
            for (int i = 0; i < headers.size(); i++) {
                row.createCell(i).setCellValue(headers.get(i));
            }
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }
}
