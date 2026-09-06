package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.machining.quote.config.AuthContext;
import com.machining.quote.controller.CustomerMasterController;
import com.machining.quote.dto.CustomerMasterDTO;
import com.machining.quote.dto.CustomerOptionDTO;
import com.machining.quote.dto.CustomerSupplierCodeUpdateReq;
import com.machining.quote.dto.PageResult;
import com.machining.quote.entity.CustomerMaster;
import com.machining.quote.mapper.CustomerMasterMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.CustomerMasterService;
import com.machining.quote.service.u9.U9MasterDataSyncService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CustomerMasterBackendStage2Test {
    @Test
    void controllerExposesU9CustomerCacheAndSupplierCodeOnlyMutation() throws Exception {
        assertThat(CustomerMasterController.class).isNotNull();
        String source = Files.readString(
                Path.of("src/main/java/com/machining/quote/controller/CustomerMasterController.java"),
                StandardCharsets.UTF_8);

        assertThat(source)
                .contains("@RequestMapping(\"/api/system/customers\")")
                .contains("@RequirePermission(\"system:customer:view\")")
                .contains("@PutMapping(\"/{id}/supplier-code\")")
                .contains("@RequirePermission(\"system:customer:supplier-code-update\")")
                .contains("@PostMapping(\"/sync\")")
                .contains("@GetMapping(\"/sync-schedule\")")
                .contains("@PutMapping(\"/sync-schedule\")")
                .contains("@RequirePermission(\"system:customer:sync\")")
                .doesNotContain("@PostMapping\r\n")
                .doesNotContain("@PostMapping\n")
                .doesNotContain("@DeleteMapping")
                .doesNotContain("CustomerMasterSaveReq")
                .doesNotContain("system:customer:create")
                .doesNotContain("system:customer:delete")
                .doesNotContain("common:customer");
    }

    @Test
    void supplierCodeEndpointUsesDedicatedPermission() throws Exception {
        Method method = CustomerMasterController.class.getMethod(
                "updateSupplierCode",
                Long.class,
                CustomerSupplierCodeUpdateReq.class);

        assertThat(method.getAnnotation(com.machining.quote.config.RequirePermission.class).value())
                .containsExactly("system:customer:supplier-code-update");
    }

    @Test
    void serviceListsU9CustomerCacheWithSyncMetadata() {
        CustomerMasterMapper mapper = mock(CustomerMasterMapper.class);
        CustomerMasterService service = service(mapper);
        LocalDateTime syncedAt = LocalDateTime.of(2026, 8, 11, 16, 30);

        when(mapper.selectCount(any(QueryWrapper.class))).thenReturn(2L);
        when(mapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(
                customer(1L, "GSS1", "GSS1", "Grand Silicon Semiconductor", true, syncedAt, "SUP-GSS"),
                customer(2L, "C002", "Beta", "Beta Technology", false, syncedAt.minusHours(1), null)
        ));

        PageResult<CustomerMasterDTO> page = service.pageCustomers("all", "gss", 1, 20);
        List<CustomerOptionDTO> options = service.listOptions("code", "GSS");

        assertThat(page.getTotal()).isEqualTo(2);
        assertThat(page.getRecords()).first().satisfies(dto -> {
            assertThat(dto.getCustomerCode()).isEqualTo("GSS1");
            assertThat(dto.getCustomerName()).isEqualTo("Grand Silicon Semiconductor");
            assertThat(dto.getU9OrgCode()).isEqualTo("101");
            assertThat(dto.getU9CustomerId()).isEqualTo("U9-GSS1");
            assertThat(dto.getSupplierCode()).isEqualTo("SUP-GSS");
            assertThat(dto.getLastSyncedAt()).isEqualTo(syncedAt);
        });
        assertThat(options).extracting(CustomerOptionDTO::getCustomerName)
                .containsExactly("Grand Silicon Semiconductor", "Beta Technology");
    }

    @Test
    void serviceNoLongerContainsFullLocalCustomerMutationBusiness() throws Exception {
        String source = source("service/CustomerMasterService.java");

        assertThat(source)
                .contains("updateSupplierCode(")
                .doesNotContain("CustomerMasterSaveReq")
                .doesNotContain("saveCustomer(")
                .doesNotContain("deleteCustomer(")
                .doesNotContain("customerMapper.insert")
                .doesNotContain("customerMapper.deleteById")
                .doesNotContain("CUSTOMER_MASTER_CREATE")
                .doesNotContain("CUSTOMER_MASTER_UPDATE")
                .doesNotContain("CUSTOMER_MASTER_DELETE");
    }

    @Test
    void serviceUpdatesOptionalSupplierCodeAndAuditsSafeSummary() {
        CustomerMasterMapper mapper = mock(CustomerMasterMapper.class);
        AuditService auditService = mock(AuditService.class);
        CustomerMasterService service = service(mapper);
        ReflectionTestUtils.setField(service, "auditService", auditService);
        LocalDateTime syncedAt = LocalDateTime.of(2026, 8, 11, 16, 30);
        CustomerMaster existing = customer(1L, "GSS1", "GSS1", "Grand Silicon Semiconductor", true, syncedAt, "OLD-SUP");

        when(mapper.selectById(1L)).thenReturn(existing);
        when(mapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        CustomerSupplierCodeUpdateReq req = new CustomerSupplierCodeUpdateReq();
        req.setSupplierCode("  SUP-GSS-001  ");
        CustomerMasterDTO dto = service.updateSupplierCode(1L, req);

        assertThat(dto.getSupplierCode()).isEqualTo("SUP-GSS-001");
        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<UpdateWrapper> updateWrapperCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(mapper).update(isNull(), updateWrapperCaptor.capture());
        assertThat(updateWrapperCaptor.getValue().getSqlSet()).contains("supplier_code=");
        ArgumentCaptor<Object> beforeCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> afterCaptor = ArgumentCaptor.forClass(Object.class);
        verify(auditService).record(
                eq("CUSTOMER_SUPPLIER_CODE_UPDATE"),
                eq("customer_master"),
                eq(1L),
                beforeCaptor.capture(),
                afterCaptor.capture(),
                eq("SYSTEM"),
                eq("SUCCESS"),
                anyString(),
                isNull()
        );
        assertThat(String.valueOf(beforeCaptor.getValue())).contains("customerCode=GSS1", "supplierCode=OLD-SUP");
        assertThat(String.valueOf(afterCaptor.getValue())).contains("customerCode=GSS1", "supplierCode=SUP-GSS-001");
    }

    @Test
    void serviceClearsBlankSupplierCodeAndRejectsTooLongCode() {
        CustomerMasterMapper mapper = mock(CustomerMasterMapper.class);
        CustomerMasterService service = service(mapper);
        ReflectionTestUtils.setField(service, "auditService", mock(AuditService.class));
        CustomerMaster existing = customer(1L, "GSS1", "GSS1", "Grand Silicon Semiconductor", true, LocalDateTime.now(), "OLD-SUP");
        when(mapper.selectById(1L)).thenReturn(existing);
        when(mapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        CustomerSupplierCodeUpdateReq clearReq = new CustomerSupplierCodeUpdateReq();
        clearReq.setSupplierCode("   ");
        assertThat(service.updateSupplierCode(1L, clearReq).getSupplierCode()).isNull();
        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<UpdateWrapper> updateWrapperCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(mapper).update(isNull(), updateWrapperCaptor.capture());
        assertThat(updateWrapperCaptor.getValue().getSqlSet()).contains("supplier_code=");

        CustomerSupplierCodeUpdateReq longReq = new CustomerSupplierCodeUpdateReq();
        longReq.setSupplierCode("S".repeat(65));
        assertThatThrownBy(() -> service.updateSupplierCode(1L, longReq))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("供应商代码不能超过64个字符");
    }

    @Test
    @SuppressWarnings("unchecked")
    void manualSyncEndpointUsesCurrentUserAndAuditsSafeSummary() throws Exception {
        CustomerMasterController controller = new CustomerMasterController();
        CustomerMasterService customerService = mock(CustomerMasterService.class);
        U9MasterDataSyncService syncService = mock(U9MasterDataSyncService.class);
        AuditService auditService = mock(AuditService.class);
        ReflectionTestUtils.setField(controller, "customerMasterService", customerService);
        ReflectionTestUtils.setField(controller, "u9MasterDataSyncService", syncService);
        ReflectionTestUtils.setField(controller, "auditService", auditService);

        when(syncService.syncCustomersManual(88L))
                .thenReturn(new U9MasterDataSyncService.SyncResult(true, 9004L, 12, 3, 1, null));

        AuthContext.set(new AuthContext.SessionUser(88, "admin", "Admin", "ADMIN"));
        Map<String, Object> response;
        try {
            Method method = CustomerMasterController.class.getMethod("syncCustomers");
            response = (Map<String, Object>) method.invoke(controller);
        } finally {
            AuthContext.clear();
        }

        assertThat(response)
                .containsEntry("success", true)
                .containsEntry("syncRunId", 9004L)
                .containsEntry("fetchedCount", 12)
                .containsEntry("upsertedCount", 3)
                .containsEntry("disabledCount", 1);
        verify(syncService).syncCustomersManual(88L);
        verifyNoInteractions(customerService);

        ArgumentCaptor<Object> afterCaptor = ArgumentCaptor.forClass(Object.class);
        verify(auditService).record(
                eq("U9_CUSTOMER_SYNC"),
                eq("u9_master_data_sync_run"),
                eq(9004L),
                isNull(),
                afterCaptor.capture(),
                eq("SYSTEM"),
                eq("SUCCESS"),
                anyString(),
                isNull()
        );
        assertThat(afterCaptor.getValue()).isInstanceOf(Map.class);
        Map<?, ?> summary = (Map<?, ?>) afterCaptor.getValue();
        assertThat(summary.get("syncRunId")).isEqualTo(9004L);
        assertThat(summary.get("syncType")).isEqualTo("CUSTOMER");
        assertThat(summary.get("status")).isEqualTo("SUCCESS");
        assertThat(summary.get("fetchedCount")).isEqualTo(12);
        assertThat(summary.get("upsertedCount")).isEqualTo(3);
        assertThat(summary.get("disabledCount")).isEqualTo(1);
        assertThat(String.valueOf(summary)).doesNotContain("token", "Authorization", "Data.Headers");
    }

    private CustomerMasterService service(CustomerMasterMapper mapper) {
        CustomerMasterService service = new CustomerMasterService();
        ReflectionTestUtils.setField(service, "customerMapper", mapper);
        return service;
    }

    private CustomerMaster customer(Long id, String code, String shortName, String name, boolean enabled, LocalDateTime syncedAt) {
        return customer(id, code, shortName, name, enabled, syncedAt, null);
    }

    private CustomerMaster customer(Long id, String code, String shortName, String name, boolean enabled, LocalDateTime syncedAt, String supplierCode) {
        CustomerMaster customer = new CustomerMaster();
        customer.setId(id);
        customer.setU9OrgCode("101");
        customer.setU9CustomerId("U9-" + code);
        customer.setCustomerCode(code);
        customer.setCustomerShortName(shortName);
        customer.setCustomerName(name);
        customer.setSupplierCode(supplierCode);
        customer.setEnabled(enabled);
        customer.setLastSyncedAt(syncedAt);
        customer.setUpdatedAt(syncedAt);
        return customer;
    }

    private String source(String path) throws Exception {
        return Files.readString(
                Path.of("src/main/java/com/machining/quote", path.replace("/", java.io.File.separator)),
                StandardCharsets.UTF_8);
    }
}
