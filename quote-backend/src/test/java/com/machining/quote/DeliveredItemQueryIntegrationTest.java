package com.machining.quote;

import com.machining.quote.config.AuthContext;
import com.machining.quote.dto.DeliveredItemDTO;
import com.machining.quote.dto.DeliveredItemQueryReq;
import com.machining.quote.dto.PageResult;
import com.machining.quote.service.DeliveredItemService;
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
    private JdbcTemplate jdbcTemplate;

    private final List<Integer> ticketIds = new ArrayList<>();
    private final List<Integer> itemIds = new ArrayList<>();
    private final List<Integer> rfqIds = new ArrayList<>();
    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> roleIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        AuthContext.clear();
        deleteByIds("order_delivery_ticket", ticketIds);
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
        jdbcTemplate.update(
                "INSERT INTO system_role_data_scope_customer (role_id, customer_name) VALUES (?, ?) ON CONFLICT DO NOTHING",
                roleId, "ACME");
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

    private int createTicket(String rfqNo, String customerName, String partNo, String version, String materialNo, String drawingUrl) {
        Integer rfqId = jdbcTemplate.queryForObject("""
                INSERT INTO order_rfq (rfq_no, customer_name, status)
                VALUES (?, ?, '已报价')
                RETURNING id
                """, Integer.class, rfqNo, customerName);
        rfqIds.add(rfqId);
        Integer itemId = jdbcTemplate.queryForObject("""
                INSERT INTO item_quote_version (rfq_id, part_no, description, qty, version, drawing_url, process_route, material_data)
                VALUES (?, ?, 'Delivered item test', 10, ?, ?, '[]'::jsonb, '{}'::jsonb)
                RETURNING id
                """, Integer.class, rfqId, partNo, version, drawingUrl);
        itemIds.add(itemId);
        Integer ticketId = jdbcTemplate.queryForObject("""
                INSERT INTO order_delivery_ticket (
                    rfq_id, item_id, part_no, version, qty, material_no, mes_sync_status, wms_allocate_status,
                    process_route, material_data, delivery_date
                )
                VALUES (?, ?, ?, ?, 4, ?, 'PENDING', 'PENDING', '[]'::jsonb, '{}'::jsonb, CURRENT_DATE)
                RETURNING id
                """, Integer.class, rfqId, itemId, partNo, version, materialNo);
        ticketIds.add(ticketId);
        return ticketId;
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
