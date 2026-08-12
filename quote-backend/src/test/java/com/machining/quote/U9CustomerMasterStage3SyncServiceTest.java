package com.machining.quote;

import com.machining.quote.dto.u9.U9CommonEntityQueryReq;
import com.machining.quote.dto.u9.U9CommonEntityRowDTO;
import com.machining.quote.entity.CustomerMaster;
import com.machining.quote.entity.U9MasterDataSyncRun;
import com.machining.quote.mapper.CustomerMasterMapper;
import com.machining.quote.mapper.U9MasterDataSyncRunMapper;
import com.machining.quote.service.u9.U9CommonEntityClient;
import com.machining.quote.service.u9.U9MasterDataSyncService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class U9CustomerMasterStage3SyncServiceTest {
    private static final String ORG_101 = "101";

    @Test
    void customerSyncFetchesPagesDeduplicatesUpsertsAndDisablesMissingCustomers() {
        U9CommonEntityClient commonEntityClient = mock(U9CommonEntityClient.class);
        CustomerMasterMapper customerMapper = mock(CustomerMasterMapper.class);
        U9MasterDataSyncRunMapper runMapper = mock(U9MasterDataSyncRunMapper.class);
        U9MasterDataSyncService service = service(commonEntityClient, customerMapper, runMapper);

        ArgumentCaptor<U9CommonEntityQueryReq> requestCaptor = ArgumentCaptor.forClass(U9CommonEntityQueryReq.class);
        when(commonEntityClient.query(requestCaptor.capture())).thenAnswer(invocation -> customerPage(invocation.getArgument(0)));
        when(customerMapper.selectList(any())).thenReturn(List.of(
                customer(1L, "GSS1", "GSS1-old", "Old Name", true, "old-hash"),
                customer(2L, "OLD1", "OLD", "Missing Old Customer", true, "old-missing-hash")
        ));
        when(customerMapper.insert(any(CustomerMaster.class))).thenReturn(1);
        when(customerMapper.updateById(any(CustomerMaster.class))).thenReturn(1);
        when(runMapper.insert(any(U9MasterDataSyncRun.class))).thenAnswer(invocation -> {
            U9MasterDataSyncRun run = invocation.getArgument(0);
            run.setId(9001L);
            return 1;
        });
        when(runMapper.updateById(any(U9MasterDataSyncRun.class))).thenReturn(1);

        U9MasterDataSyncService.SyncResult result = service.syncCustomersManual(88L);

        assertThat(result.success()).isTrue();
        assertThat(result.syncRunId()).isEqualTo(9001L);
        assertThat(result.fetchedCount()).isEqualTo(201);
        assertThat(result.upsertedCount()).isEqualTo(200);
        assertThat(result.disabledCount()).isEqualTo(1);

        List<U9CommonEntityQueryReq> requests = requestCaptor.getAllValues();
        assertThat(requests).hasSize(2);
        assertThat(requests).extracting(U9CommonEntityQueryReq::getPageIndex).containsExactly(1, 2);
        assertThat(requests.get(0).getPageSize()).isEqualTo(200);
        assertThat(requests.get(0).getEntityFullName()).isEqualTo(U9MasterDataSyncService.CUSTOMER_ENTITY);
        assertThat(requests.get(0).getOrders()).isEqualTo("Code");
        assertThat(requests.get(0).getReturnFields()).containsExactly(
                "ID", "Code", "Name", "ShortName", "Org.Code", "Org.Name", "Effective.IsEffective");
        assertThat(requests.get(0).getFilters()).singleElement().satisfies(filter -> {
            assertThat(filter.getField()).isEqualTo("Org.Code");
            assertThat(filter.getOperator()).isEqualTo("eq");
            assertThat(filter.getValue()).isEqualTo(ORG_101);
        });

        ArgumentCaptor<CustomerMaster> insertCaptor = ArgumentCaptor.forClass(CustomerMaster.class);
        ArgumentCaptor<CustomerMaster> updateCaptor = ArgumentCaptor.forClass(CustomerMaster.class);
        verify(customerMapper, org.mockito.Mockito.times(199)).insert(insertCaptor.capture());
        verify(customerMapper, org.mockito.Mockito.times(2)).updateById(updateCaptor.capture());

        assertThat(insertCaptor.getAllValues())
                .extracting(CustomerMaster::getCustomerCode)
                .contains("NEW2", "C0001")
                .doesNotContain("GSS1", "OLD1");
        assertThat(updateCaptor.getAllValues())
                .anySatisfy(customer -> {
                    assertThat(customer.getId()).isEqualTo(1L);
                    assertThat(customer.getCustomerCode()).isEqualTo("GSS1");
                    assertThat(customer.getCustomerName()).isEqualTo("Grand Silicon Semiconductor");
                    assertThat(customer.getCustomerShortName()).isEqualTo("GSS1");
                    assertThat(customer.getEnabled()).isTrue();
                    assertThat(customer.getU9CustomerId()).isEqualTo("U9-GSS1");
                    assertThat(customer.getLastSyncedAt()).isEqualTo(LocalDateTime.of(2026, 8, 11, 12, 0));
                })
                .anySatisfy(customer -> {
                    assertThat(customer.getId()).isEqualTo(2L);
                    assertThat(customer.getCustomerCode()).isEqualTo("OLD1");
                    assertThat(customer.getEnabled()).isFalse();
                });

        ArgumentCaptor<U9MasterDataSyncRun> runUpdateCaptor = ArgumentCaptor.forClass(U9MasterDataSyncRun.class);
        verify(runMapper).updateById(runUpdateCaptor.capture());
        assertThat(runUpdateCaptor.getValue()).satisfies(run -> {
            assertThat(run.getId()).isEqualTo(9001L);
            assertThat(run.getSyncType()).isEqualTo("CUSTOMER");
            assertThat(run.getStatus()).isEqualTo("SUCCESS");
            assertThat(run.getFetchedCount()).isEqualTo(201);
            assertThat(run.getUpsertedCount()).isEqualTo(200);
            assertThat(run.getDisabledCount()).isEqualTo(1);
            assertThat(run.getMessage()).doesNotContain("Authorization", "token", "Data.Headers");
        });
    }

    @Test
    void customerSyncFailureKeepsExistingCacheAndStoresOnlySanitizedError() {
        U9CommonEntityClient commonEntityClient = mock(U9CommonEntityClient.class);
        CustomerMasterMapper customerMapper = mock(CustomerMasterMapper.class);
        U9MasterDataSyncRunMapper runMapper = mock(U9MasterDataSyncRunMapper.class);
        U9MasterDataSyncService service = service(commonEntityClient, customerMapper, runMapper);
        when(commonEntityClient.query(any())).thenThrow(new IllegalStateException(
                "U9 failed token=hidden-token Authorization: Bearer hidden-auth Data.Headers={Cookie=hidden-cookie}"));
        when(runMapper.insert(any(U9MasterDataSyncRun.class))).thenAnswer(invocation -> {
            U9MasterDataSyncRun run = invocation.getArgument(0);
            run.setId(9002L);
            return 1;
        });
        when(runMapper.updateById(any(U9MasterDataSyncRun.class))).thenReturn(1);

        U9MasterDataSyncService.SyncResult result = service.syncCustomersManual(99L);

        assertThat(result.success()).isFalse();
        assertThat(result.syncRunId()).isEqualTo(9002L);
        assertThat(result.message())
                .doesNotContain("hidden-token", "hidden-auth", "hidden-cookie", "Authorization", "Data.Headers");
        verify(customerMapper, never()).selectList(any());
        verify(customerMapper, never()).insert(any(CustomerMaster.class));
        verify(customerMapper, never()).updateById(any(CustomerMaster.class));

        ArgumentCaptor<U9MasterDataSyncRun> runUpdateCaptor = ArgumentCaptor.forClass(U9MasterDataSyncRun.class);
        verify(runMapper).updateById(runUpdateCaptor.capture());
        assertThat(runUpdateCaptor.getValue()).satisfies(run -> {
            assertThat(run.getStatus()).isEqualTo("FAILED");
            assertThat(run.getMessage())
                    .doesNotContain("hidden-token", "hidden-auth", "hidden-cookie", "Authorization", "Data.Headers");
        });
    }

    @Test
    void customerSyncTreatsEmptyU9ResultAsFailureWithoutDisablingCache() {
        U9CommonEntityClient commonEntityClient = mock(U9CommonEntityClient.class);
        CustomerMasterMapper customerMapper = mock(CustomerMasterMapper.class);
        U9MasterDataSyncRunMapper runMapper = mock(U9MasterDataSyncRunMapper.class);
        U9MasterDataSyncService service = service(commonEntityClient, customerMapper, runMapper);
        when(commonEntityClient.query(any())).thenReturn(List.of());
        when(runMapper.insert(any(U9MasterDataSyncRun.class))).thenAnswer(invocation -> {
            U9MasterDataSyncRun run = invocation.getArgument(0);
            run.setId(9003L);
            return 1;
        });
        when(runMapper.updateById(any(U9MasterDataSyncRun.class))).thenReturn(1);

        U9MasterDataSyncService.SyncResult result = service.syncCustomersManual(100L);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("returned no valid customers");
        verify(customerMapper, never()).selectList(any());
        verify(customerMapper, never()).insert(any(CustomerMaster.class));
        verify(customerMapper, never()).updateById(any(CustomerMaster.class));
    }

    @Test
    void customerSyncSupportsManualScheduledAndDelayedStartupTriggers() throws Exception {
        assertThat(U9MasterDataSyncService.class.getMethod("syncCustomersManual", Long.class)).isNotNull();

        Method scheduledMethod = U9MasterDataSyncService.class.getMethod("syncCustomersScheduled");
        Scheduled scheduled = scheduledMethod.getAnnotation(Scheduled.class);
        assertThat(scheduled).isNotNull();
        assertThat(scheduled.initialDelayString()).isEmpty();
        assertThat(scheduled.fixedDelayString()).contains("erp.u9.customer-sync.check-ms");
    }

    private U9MasterDataSyncService service(
            U9CommonEntityClient commonEntityClient,
            CustomerMasterMapper customerMapper,
            U9MasterDataSyncRunMapper runMapper) {
        U9MasterDataSyncService service = new U9MasterDataSyncService();
        ReflectionTestUtils.setField(service, "commonEntityClient", commonEntityClient);
        ReflectionTestUtils.setField(service, "customerMapper", customerMapper);
        ReflectionTestUtils.setField(service, "syncRunMapper", runMapper);
        ReflectionTestUtils.setField(service, "orgCode", ORG_101);
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(Instant.parse("2026-08-11T04:00:00Z"), ZoneId.of("Asia/Shanghai")));
        return service;
    }

    private List<U9CommonEntityRowDTO> customerPage(U9CommonEntityQueryReq req) {
        if (req.getPageIndex() == 1) {
            List<U9CommonEntityRowDTO> rows = new ArrayList<>();
            rows.add(row(Map.of(
                    "ID", "U9-GSS1",
                    "Code", "GSS1",
                    "Name", "Grand Silicon Semiconductor",
                    "ShortName", "GSS1",
                    "Org.Code", ORG_101,
                    "Org.Name", "QPO Org 101",
                    "Effective.IsEffective", true)));
            rows.add(row(Map.of(
                    "ID", "U9-GSS1-DUP",
                    "Code", "GSS1",
                    "Name", "Duplicate Should Be Ignored",
                    "ShortName", "DUP",
                    "Org.Code", ORG_101,
                    "Org.Name", "QPO Org 101",
                    "Effective.IsEffective", true)));
            for (int i = 1; i <= 198; i++) {
                rows.add(row(Map.of(
                        "ID", "U9-C" + String.format("%04d", i),
                        "Code", "C" + String.format("%04d", i),
                        "Name", "Customer " + i,
                        "ShortName", "C" + String.format("%04d", i),
                        "Org.Code", ORG_101,
                        "Org.Name", "QPO Org 101",
                        "Effective.IsEffective", true)));
            }
            return rows;
        }
        if (req.getPageIndex() == 2) {
            return List.of(row(Map.of(
                    "ID", "U9-NEW2",
                    "Code", "NEW2",
                    "Name", "Second Page Customer",
                    "ShortName", "NEW2",
                    "Org.Code", ORG_101,
                    "Org.Name", "QPO Org 101",
                    "Effective.IsEffective", true)));
        }
        return List.of();
    }

    private CustomerMaster customer(Long id, String code, String shortName, String name, boolean enabled, String hash) {
        CustomerMaster customer = new CustomerMaster();
        customer.setId(id);
        customer.setU9OrgCode(ORG_101);
        customer.setCustomerCode(code);
        customer.setCustomerShortName(shortName);
        customer.setCustomerName(name);
        customer.setEnabled(enabled);
        customer.setSourceHash(hash);
        return customer;
    }

    private static U9CommonEntityRowDTO row(Map<String, Object> values) {
        U9CommonEntityRowDTO row = new U9CommonEntityRowDTO();
        row.setValues(new LinkedHashMap<>(values));
        return row;
    }
}
