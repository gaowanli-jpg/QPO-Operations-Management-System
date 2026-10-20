package com.machining.quote;

import com.machining.quote.config.AuthContext;
import com.machining.quote.dto.DeliveredItemDTO;
import com.machining.quote.dto.DeliveredItemQueryReq;
import com.machining.quote.dto.LockedItemDTO;
import com.machining.quote.dto.PageResult;
import com.machining.quote.service.DeliveredItemService;
import com.machining.quote.service.LockedItemService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "erp.auth.enabled=false",
        "erp.auth.jwt-secret=test-jwt-secret-for-delivered-item-queries-32chars",
        "erp.upload-dir=${java.io.tmpdir}/quote-test/uploads/",
        "erp.backup-dir=${java.io.tmpdir}/quote-test/backups/"
})
class DeliveredItemQueryIntegrationTest {
    @Autowired
    private DeliveredItemService deliveredItemService;
    @Autowired
    private LockedItemService lockedItemService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Integer> ticketIds = new ArrayList<>();
    private final List<Integer> allocationIds = new ArrayList<>();
    private final List<Integer> itemIds = new ArrayList<>();
    private final List<Integer> rfqIds = new ArrayList<>();
    private final List<Long> salesOrderItemIds = new ArrayList<>();
    private final List<Long> salesOrderIds = new ArrayList<>();
    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> roleIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        AuthContext.clear();
        deleteByIds("order_delivery_ticket_allocation", allocationIds);
        deleteByIds("sales_order_item", salesOrderItemIds);
        deleteByIds("order_delivery_ticket", ticketIds);
        deleteByIds("sales_order_header", salesOrderIds);
        deleteByIds("item_quote_version", itemIds);
        deleteByIds("order_rfq", rfqIds);
        deleteByIds("system_user", userIds);
        deleteByIds("system_role", roleIds);
    }

    @Test
    void listTicketsHydratesJoinedFieldsAndFiltersByCustomerDataScope() {
        int legacyUserId = uniqueLegacyId();
        long roleId = insertRole("CUSTOMER");
        long userId = insertUser(legacyUserId);
        linkUserRole(userId, roleId);
        insertCustomerScope(roleId, "ACME");
        AuthContext.set(new AuthContext.SessionUser(legacyUserId, "delivered-query", "Delivered Query", "MARKET"));

        createTicket("QPO-DI-ACME-" + UUID.randomUUID(), "ACME", "P-100", "V1", "MAT-100", "/uploads/acme/P-100.pdf");
        createTicket("QPO-DI-GLOBEX-" + UUID.randomUUID(), "Globex", "P-200", "V2", "MAT-200", "/uploads/globex/P-200.pdf");

        DeliveredItemQueryReq req = new DeliveredItemQueryReq();
        req.setPage(1);
        req.setPageSize(20);
        PageResult<DeliveredItemDTO> page = deliveredItemService.listTickets(req);

        assertThat(page.getRecords())
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.getCustomerName()).isEqualTo("ACME");
                    assertThat(row.getRfqNo()).startsWith("QPO-DI-ACME-");
                    assertThat(row.getDrawingUrl()).isEqualTo("/uploads/acme/P-100.pdf");
                    assertThat(row.getMaterialNo()).isEqualTo("MAT-100");
                });
    }

    @Test
    void noneDataScopeReturnsEmptyListAndScopedDetailRejectsOtherCustomers() {
        int legacyUserId = uniqueLegacyId();
        long roleId = insertRole("NONE");
        long userId = insertUser(legacyUserId);
        linkUserRole(userId, roleId);
        AuthContext.set(new AuthContext.SessionUser(legacyUserId, "delivered-none", "Delivered None", "MARKET"));
        int ticketId = createTicket("QPO-DI-NONE-" + UUID.randomUUID(), "NoScopeCustomer", "P-300", "A", "MAT-300", "/uploads/no/P-300.pdf");

        DeliveredItemQueryReq req = new DeliveredItemQueryReq();
        req.setPage(1);
        req.setPageSize(20);

        assertThat(deliveredItemService.listTickets(req).getRecords()).isEmpty();
        assertThatThrownBy(() -> deliveredItemService.getTicket(ticketId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("No data permission");
    }

    @Test
    void defaultListShowsOnlyActiveFlowWhileCompletedAndAllScopesRemainQueryable() {
        int legacyUserId = uniqueLegacyId();
        long roleId = insertRole("ALL");
        long userId = insertUser(legacyUserId);
        linkUserRole(userId, roleId);
        AuthContext.set(new AuthContext.SessionUser(legacyUserId, "delivered-active", "Delivered Active", "MARKET"));

        int completed = createTicket("QPO-DI-DONE-" + UUID.randomUUID(), "ACME", "DONE", "A", "MAT-DONE", "/uploads/done.pdf",
                "SUCCESS", "SUCCESS");
        createAllocation(completed, "MAT-DONE", "DONE", "A", "ACME");
        createTicket("QPO-DI-NOMAT-" + UUID.randomUUID(), "ACME", "NO-MAT", "A", null, "/uploads/no-mat.pdf",
                "PENDING", "PENDING");
        createTicket("QPO-DI-CALLBACK-" + UUID.randomUUID(), "ACME", "WAIT-MES", "A", null, "/uploads/wait.pdf",
                "CALLBACK_PENDING", "PENDING");
        createTicket("QPO-DI-WMSFAIL-" + UUID.randomUUID(), "ACME", "WMS-FAIL", "A", "MAT-WMS", "/uploads/wms-fail.pdf",
                "SUCCESS", "FAILED");
        createTicket("QPO-DI-MISSING-ALLOC-" + UUID.randomUUID(), "ACME", "MISSING-ALLOC", "A", "MAT-MISS", "/uploads/miss.pdf",
                "SUCCESS", "SUCCESS");

        DeliveredItemQueryReq activeReq = new DeliveredItemQueryReq();
        activeReq.setPage(1);
        activeReq.setPageSize(50);
        PageResult<DeliveredItemDTO> activePage = deliveredItemService.listTickets(activeReq);

        assertThat(partNos(activePage))
                .contains("NO-MAT", "WAIT-MES")
                .doesNotContain("DONE", "WMS-FAIL", "MISSING-ALLOC");

        DeliveredItemQueryReq completedReq = new DeliveredItemQueryReq();
        completedReq.setPage(1);
        completedReq.setPageSize(50);
        completedReq.setFlowScope("COMPLETED");
        assertThat(partNos(deliveredItemService.listTickets(completedReq)))
                .contains("DONE", "WMS-FAIL", "MISSING-ALLOC")
                .doesNotContain("NO-MAT", "WAIT-MES");

        DeliveredItemQueryReq allReq = new DeliveredItemQueryReq();
        allReq.setPage(1);
        allReq.setPageSize(50);
        allReq.setFlowScope("ALL");
        assertThat(partNos(deliveredItemService.listTickets(allReq)))
                .contains("DONE", "NO-MAT", "WAIT-MES", "WMS-FAIL", "MISSING-ALLOC");

        assertThat(deliveredItemService.getTicket(completed).getPartNo())
                .as("Detail lookup remains available for locked-item traceability")
                .isEqualTo("DONE");
    }

    @Test
    void flowStageFiltersActivePoolByMesStageWithoutBypassingDataScope() {
        int legacyUserId = uniqueLegacyId();
        long roleId = insertRole("CUSTOMER");
        long userId = insertUser(legacyUserId);
        linkUserRole(userId, roleId);
        insertCustomerScope(roleId, "StageCustomer");
        AuthContext.set(new AuthContext.SessionUser(legacyUserId, "delivered-stage", "Delivered Stage", "MARKET"));

        int completed = createTicket("QPO-DI-STAGE-DONE-" + UUID.randomUUID(), "StageCustomer", "STAGE-DONE", "A", "MAT-DONE", "/uploads/stage-done.pdf",
                "SUCCESS", "SUCCESS");
        createAllocation(completed, "MAT-DONE", "STAGE-DONE", "A", "StageCustomer");
        createTicket("QPO-DI-STAGE-NOMAT-" + UUID.randomUUID(), "StageCustomer", "STAGE-NO-MAT", "A", null, "/uploads/stage-no-mat.pdf",
                "PENDING", "PENDING");
        createTicket("QPO-DI-STAGE-MESFAIL-" + UUID.randomUUID(), "StageCustomer", "STAGE-MES-FAIL", "A", "MAT-MES-FAIL", "/uploads/stage-mes-fail.pdf",
                "FAILED", "PENDING");
        createTicket("QPO-DI-STAGE-WMSPENDING-" + UUID.randomUUID(), "StageCustomer", "STAGE-WMS-PENDING", "A", "MAT-WMS-PENDING", "/uploads/stage-wms-pending.pdf",
                "SUCCESS", "PENDING");
        createTicket("QPO-DI-STAGE-WMSFAIL-" + UUID.randomUUID(), "StageCustomer", "STAGE-WMS-FAIL", "A", "MAT-WMS-FAIL", "/uploads/stage-wms-fail.pdf",
                "SUCCESS", "FAILED");
        createTicket("QPO-DI-STAGE-OTHER-" + UUID.randomUUID(), "OtherCustomer", "STAGE-OTHER-CUSTOMER", "A", null, "/uploads/stage-other.pdf",
                "PENDING", "PENDING");

        DeliveredItemQueryReq needMaterialReq = new DeliveredItemQueryReq();
        needMaterialReq.setPage(1);
        needMaterialReq.setPageSize(50);
        needMaterialReq.setFlowStage("NEED_MATERIAL");
        assertThat(partNos(deliveredItemService.listTickets(needMaterialReq)))
                .contains("STAGE-NO-MAT", "STAGE-MES-FAIL")
                .doesNotContain("STAGE-WMS-PENDING", "STAGE-WMS-FAIL", "STAGE-DONE", "STAGE-OTHER-CUSTOMER");

        DeliveredItemQueryReq mesFailedReq = new DeliveredItemQueryReq();
        mesFailedReq.setPage(1);
        mesFailedReq.setPageSize(50);
        mesFailedReq.setFlowStage("MES_FAILED");
        assertThat(partNos(deliveredItemService.listTickets(mesFailedReq)))
                .containsExactly("STAGE-MES-FAIL");
    }

    @Test
    void rfqDerivedDeliveredTicketExposesSalesOrderNavigationFields() {
        int legacyUserId = uniqueLegacyId();
        long roleId = insertRole("CUSTOMER");
        long userId = insertUser(legacyUserId);
        linkUserRole(userId, roleId);
        insertCustomerScope(roleId, "DerivedCustomer");
        AuthContext.set(new AuthContext.SessionUser(legacyUserId, "delivered-derived", "Delivered Derived", "MARKET"));

        String rfqNo = "QPO-DI-DERIVED-" + UUID.randomUUID();
        int ticketId = createTicket(rfqNo, "DerivedCustomer", "DERIVED-PART", "A", null, null);
        long salesOrderId = createSalesOrder("SO-DERIVED-" + UUID.randomUUID(), "C-DERIVED", "DerivedCustomer");
        createSalesOrderItem(salesOrderId, ticketId, "DERIVED-PART", "A", "/uploads/sales/derived-part.pdf");

        DeliveredItemQueryReq req = new DeliveredItemQueryReq();
        req.setPage(1);
        req.setPageSize(20);
        req.setPartNo("DERIVED-PART");
        DeliveredItemDTO row = deliveredItemService.listTickets(req).getRecords().get(0);

        assertThat(row.getId()).isEqualTo(ticketId);
        assertThat(row.getSourceType()).isEqualTo("RFQ");
        assertThat(row.getSalesOrderId()).isEqualTo(salesOrderId);
        assertThat(row.getSalesOrderNo()).startsWith("SO-DERIVED-");
        assertThat(row.getSalesOrderCustomerCode()).isEqualTo("C-DERIVED");
        assertThat(row.getSalesOrderCustomerName()).isEqualTo("DerivedCustomer");
        assertThat(row.getDrawingUrl()).isEqualTo("/uploads/sales/derived-part.pdf");
    }

    @Test
    void lockedAllocationCanTraceCompletedTicketDetailWithoutBypassingDataScope() {
        String customerName = "TraceCustomer";
        int legacyUserId = uniqueLegacyId();
        long roleId = insertRole("CUSTOMER");
        long userId = insertUser(legacyUserId);
        linkUserRole(userId, roleId);
        insertCustomerScope(roleId, customerName);
        AuthContext.set(new AuthContext.SessionUser(legacyUserId, "locked-trace", "Locked Trace", "MARKET"));

        int completed = createTicket("QPO-DI-TRACE-" + UUID.randomUUID(), customerName, "TRACE-PART", "A", "MAT-TRACE", "/uploads/trace.pdf",
                "SUCCESS", "SUCCESS");
        createAllocation(completed, "MAT-TRACE", "TRACE-PART", "A", customerName);

        DeliveredItemQueryReq defaultReq = new DeliveredItemQueryReq();
        defaultReq.setPage(1);
        defaultReq.setPageSize(50);
        assertThat(partNos(deliveredItemService.listTickets(defaultReq)))
                .as("Completed flow rows are hidden from the default active pool")
                .doesNotContain("TRACE-PART");

        List<LockedItemDTO> allocations = lockedItemService.listTicketAllocations(completed);
        assertThat(allocations)
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.getTicketId()).isEqualTo(completed);
                    assertThat(row.getMaterialNo()).isEqualTo("MAT-TRACE");
                    assertThat(row.getPartNo()).isEqualTo("TRACE-PART");
                });

        DeliveredItemDTO detail = deliveredItemService.getTicket(completed);
        assertThat(detail.getPartNo()).isEqualTo("TRACE-PART");
        assertThat(detail.getMaterialNo()).isEqualTo("MAT-TRACE");
        assertThat(detail.getMesSyncStatus()).isEqualTo("SUCCESS");
        assertThat(detail.getWmsAllocateStatus()).isEqualTo("SUCCESS");
        assertThat(detail.getSourceType()).isEqualTo("RFQ");
        assertThat(detail.getSourceOrderId()).isNotNull();
        assertThat(detail.getSourceItemId()).isNotNull();
        assertThat(detail.getProcessRoute()).isNotNull();
        assertThat(detail.getMaterialData()).isNotNull();

        AuthContext.clear();
        int noScopeUserId = uniqueLegacyId();
        long noScopeRoleId = insertRole("NONE");
        long noScopeSystemUserId = insertUser(noScopeUserId);
        linkUserRole(noScopeSystemUserId, noScopeRoleId);
        AuthContext.set(new AuthContext.SessionUser(noScopeUserId, "locked-none", "Locked None", "MARKET"));

        assertThat(lockedItemService.listTicketAllocations(completed)).isEmpty();
        assertThatThrownBy(() -> deliveredItemService.getTicket(completed))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("No data permission");
    }

    private int createTicket(String rfqNo, String customerName, String partNo, String version, String materialNo, String drawingUrl) {
        return createTicket(rfqNo, customerName, partNo, version, materialNo, drawingUrl, "PENDING", "PENDING");
    }

    private int createTicket(
            String rfqNo,
            String customerName,
            String partNo,
            String version,
            String materialNo,
            String drawingUrl,
            String mesSyncStatus,
            String wmsAllocateStatus) {
        Integer rfqId = jdbcTemplate.queryForObject("""
                INSERT INTO order_rfq (rfq_no, customer_code, customer_name, status)
                VALUES (?, ?, ?, '已报价')
                RETURNING id
                """, Integer.class, rfqNo, customerName, customerName);
        rfqIds.add(rfqId);
        Integer itemId = jdbcTemplate.queryForObject("""
                INSERT INTO item_quote_version (rfq_id, part_no, description, qty, version, drawing_url, process_route, material_data)
                VALUES (?, ?, 'Delivered item test', 10, ?, ?, '[]'::jsonb, '{}'::jsonb)
                RETURNING id
                """, Integer.class, rfqId, partNo, version, drawingUrl);
        itemIds.add(itemId);
        Integer ticketId = jdbcTemplate.queryForObject("""
                INSERT INTO order_delivery_ticket (
                    rfq_id, rfq_no, customer_name, item_id, source_type, source_order_id, source_item_id,
                    part_no, version, qty, material_no, mes_sync_status, wms_allocate_status,
                    process_route, material_data, material_gross_weight, part_net_weight, delivery_date
                )
                VALUES (?, ?, ?, ?, 'RFQ', ?, ?, ?, ?, 4, ?, ?, ?, '[]'::jsonb, '{}'::jsonb, 1.250, 1.100, CURRENT_DATE)
                RETURNING id
                """, Integer.class, rfqId, rfqNo, customerName, itemId, rfqId, itemId, partNo, version, materialNo, mesSyncStatus, wmsAllocateStatus);
        ticketIds.add(ticketId);
        return ticketId;
    }

    private int createAllocation(Integer ticketId, String materialNo, String partNo, String version, String customerName) {
        Integer allocationId = jdbcTemplate.queryForObject("""
                INSERT INTO order_delivery_ticket_allocation (
                    ticket_id, source_type, source_order_id, source_item_id, allocation_type,
                    qty, material_no, part_no, version, customer_name, process_route, material_data, status
                )
                SELECT id, source_type, source_order_id, source_item_id, 'IN_STOCK_LOCK',
                       4, ?, ?, ?, ?, process_route, material_data, 'ACTIVE'
                FROM order_delivery_ticket
                WHERE id = ?
                RETURNING id
                """, Integer.class, materialNo, partNo, version, customerName, ticketId);
        allocationIds.add(allocationId);
        return allocationId;
    }

    private long createSalesOrder(String salesOrderNo, String customerCode, String customerName) {
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO sales_order_header (
                    sales_order_no, document_no, customer_code, customer_name, status, source_type,
                    source_release_batch_key, source_release_request_hash,
                    sales_order_date, customer_order_no, order_type, internal_project_no
                )
                VALUES (?, ?, ?, ?, '已生成提单', 'RFQ_RELEASE', ?, ?, CURRENT_DATE, 'PO-DERIVED', '正式订单', 'IP-DERIVED')
                RETURNING id
                """, Long.class, salesOrderNo, "DOC-" + salesOrderNo, customerCode, customerName,
                "test-batch-" + salesOrderNo,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        salesOrderIds.add(id);
        return id;
    }

    private long createSalesOrderItem(long salesOrderId, int sourceTicketId, String partNo, String version, String drawingUrl) {
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO sales_order_item (
                    sales_order_id, source_type, source_ticket_id, line_no, part_no, description,
                    qty, version, drawing_url, has_drawing, status
                )
                VALUES (?, 'RFQ', ?, 1, ?, 'Derived sales item', 1, ?, ?, TRUE, '已生成提单')
                RETURNING id
                """, Long.class, salesOrderId, sourceTicketId, partNo, version, drawingUrl);
        salesOrderItemIds.add(id);
        return id;
    }

    private List<String> partNos(PageResult<DeliveredItemDTO> page) {
        return page.getRecords().stream()
                .map(DeliveredItemDTO::getPartNo)
                .toList();
    }

    private long insertRole(String dataScope) {
        String code = "DI_TEST_" + dataScope + "_" + UUID.randomUUID().toString().replace("-", "");
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO system_role (role_code, role_name, data_scope, enabled, deleted)
                VALUES (?, ?, ?, TRUE, FALSE)
                RETURNING id
                """, Long.class, code, code, dataScope);
        roleIds.add(id);
        return id;
    }

    private long insertUser(int legacyUserId) {
        String username = "di_test_" + legacyUserId;
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO system_user (legacy_user_id, username, password_hash, display_name, enabled, deleted)
                VALUES (?, ?, 'test', ?, TRUE, FALSE)
                RETURNING id
                """, Long.class, legacyUserId, username, username);
        userIds.add(id);
        return id;
    }

    private void linkUserRole(long userId, long roleId) {
        jdbcTemplate.update("INSERT INTO system_user_role (user_id, role_id) VALUES (?, ?) ON CONFLICT DO NOTHING", userId, roleId);
    }

    private void insertCustomerScope(long roleId, String customerName) {
        jdbcTemplate.update("""
                INSERT INTO system_role_data_scope_customer (role_id, customer_code, customer_name, customer_name_snapshot)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (role_id, customer_code) DO NOTHING
                """, roleId, customerName, customerName, customerName);
    }

    private int uniqueLegacyId() {
        return Math.toIntExact(1_800_000_000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 100_000_000L));
    }

    private void deleteByIds(String tableName, List<? extends Number> ids) {
        ListIterator<? extends Number> iterator = ids.listIterator(ids.size());
        while (iterator.hasPrevious()) {
            Number id = iterator.previous();
            jdbcTemplate.update("DELETE FROM " + tableName + " WHERE id = ?", id);
        }
    }
}
