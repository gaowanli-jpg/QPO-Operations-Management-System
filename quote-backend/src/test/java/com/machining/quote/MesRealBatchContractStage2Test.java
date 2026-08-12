package com.machining.quote;

import com.machining.quote.entity.MesIntegrationBatch;
import com.machining.quote.entity.MesMaterialBatchItem;
import com.machining.quote.entity.MesProductionBatchItem;
import com.machining.quote.mapper.MesIntegrationBatchMapper;
import com.machining.quote.mapper.MesIntegrationReconcileRunMapper;
import com.machining.quote.mapper.MesMaterialBatchItemMapper;
import com.machining.quote.mapper.MesProductionBatchItemMapper;
import com.machining.quote.mapper.OrderDeliveryTicketAllocationMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMesRequestMapper;
import com.machining.quote.service.mes.MesBatchFingerprintService;
import com.machining.quote.service.mes.MesBatchStateService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MesRealBatchContractStage2Test {
    private static final String TEST_SCHEMA_PREFIX = "qpo_mes_batch_stage2_";
    private static final Path MIGRATION_DIR = Path.of("src", "main", "resources", "db", "migration");

    @Test
    void flywayMigrationCreatesBatchTablesAndEnforcesActiveRowUniqueness() throws Exception {
        try (Connection connection = openDatabase()) {
            String schema = createTestSchema(connection);
            try {
                migrate(connection, schema);

                assertThat(tableExists(connection, schema, "mes_integration_batch")).isTrue();
                assertThat(tableExists(connection, schema, "mes_material_batch_item")).isTrue();
                assertThat(tableExists(connection, schema, "mes_production_batch_item")).isTrue();
                assertThat(tableExists(connection, schema, "mes_integration_reconcile_run")).isTrue();
                assertThat(columnExists(connection, schema, "order_delivery_ticket_mes_request", "batch_id")).isTrue();
                assertThat(columnExists(connection, schema, "order_delivery_ticket_mes_request", "batch_item_id")).isTrue();
                assertThat(indexExists(connection, schema, "uk_mes_integration_batch_biz_id")).isTrue();
                assertThat(indexExists(connection, schema, "uk_mes_material_active_ticket")).isTrue();
                assertThat(indexExists(connection, schema, "uk_mes_production_active_allocation")).isTrue();
                assertThat(columns(connection, schema, "mes_integration_batch"))
                        .doesNotContain("request_json", "response_json", "authorization", "access_token", "cookie", "client_secret");

                long materialBatchOne = insertBatch(connection, schema, "MATERIAL_PREPARATION", "QPO-MAT-20260812-01TESTA");
                assertUniqueViolation(connection, () -> insertBatch(connection, schema, "MATERIAL_PREPARATION", "QPO-MAT-20260812-01TESTA"));
                assertCheckViolation(connection, () -> insertBatch(connection, schema, "MATERIAL_PREPARATION", "QPO-MAT-20260812-01TESTZ", "SENT"));
                long materialBatchTwo = insertBatch(connection, schema, "MATERIAL_PREPARATION", "QPO-MAT-20260812-01TESTB");
                long productionBatchOne = insertBatch(connection, schema, "PRODUCTION_DEMAND", "QPO-DEM-20260812-01TESTA");
                long productionBatchTwo = insertBatch(connection, schema, "PRODUCTION_DEMAND", "QPO-DEM-20260812-01TESTB");
                int ticketId = insertTicket(connection, schema);
                int allocationId = insertAllocation(connection, schema, ticketId);

                insertMaterialItem(connection, schema, materialBatchOne, ticketId, "QPO-MATL-01TESTA", "RESERVED");
                assertUniqueViolation(connection, () -> insertMaterialItem(connection, schema, materialBatchTwo, ticketId, "QPO-MATL-01TESTB", "SUBMITTING"));
                updateMaterialItemStatus(connection, schema, "QPO-MATL-01TESTA", "FAILED");
                insertMaterialItem(connection, schema, materialBatchTwo, ticketId, "QPO-MATL-01TESTC", "RESERVED");

                insertProductionItem(connection, schema, productionBatchOne, allocationId, "QPO-DEML-01TESTA", "RESULT_UNKNOWN");
                assertUniqueViolation(connection, () -> insertProductionItem(connection, schema, productionBatchTwo, allocationId, "QPO-DEML-01TESTB", "RESERVED"));
                updateProductionItemStatus(connection, schema, "QPO-DEML-01TESTA", "SUCCESS");
                insertProductionItem(connection, schema, productionBatchTwo, allocationId, "QPO-DEML-01TESTC", "RESERVED");
            } finally {
                dropTestSchema(connection, schema);
            }
        }
    }

    @Test
    void fingerprintServiceProducesStableSha256WithoutDependingOnMapOrder() {
        MesBatchFingerprintService service = new MesBatchFingerprintService();
        Map<String, Object> left = new LinkedHashMap<>();
        left.put("bizId", "QPO-MAT-20260812-01TEST");
        left.put("items", List.of(Map.of("drawingNo", "P001", "drawingVersion", "A")));
        left.put("customerCode", "GSS1");

        Map<String, Object> right = new LinkedHashMap<>();
        right.put("customerCode", "GSS1");
        right.put("items", List.of(Map.of("drawingVersion", "A", "drawingNo", "P001")));
        right.put("bizId", "QPO-MAT-20260812-01TEST");

        assertThat(service.fingerprint(left)).isEqualTo(service.fingerprint(right)).hasSize(64);
        assertThat(service.groupKeyHash("GSS1|P001|A")).hasSize(64);
    }

    @Test
    void reserveMaterialBatchCreatesOneBizIdWithDistinctInternalLineIdsAndNoExternalClientDependency() {
        MesIntegrationBatchMapper batchMapper = mock(MesIntegrationBatchMapper.class);
        MesMaterialBatchItemMapper materialMapper = mock(MesMaterialBatchItemMapper.class);
        MesProductionBatchItemMapper productionMapper = mock(MesProductionBatchItemMapper.class);
        MesIntegrationReconcileRunMapper reconcileMapper = mock(MesIntegrationReconcileRunMapper.class);
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        OrderDeliveryTicketAllocationMapper allocationMapper = mock(OrderDeliveryTicketAllocationMapper.class);
        OrderDeliveryTicketMesRequestMapper requestMapper = mock(OrderDeliveryTicketMesRequestMapper.class);
        when(batchMapper.insert(any())).thenAnswer(invocation -> {
            MesIntegrationBatch batch = invocation.getArgument(0);
            batch.setId(7001L);
            return 1;
        });
        when(materialMapper.insert(any())).thenAnswer(invocation -> {
            MesMaterialBatchItem item = invocation.getArgument(0);
            item.setId(item.getTicketId().longValue() + 8000L);
            return 1;
        });

        MesBatchStateService service = new MesBatchStateService(
                batchMapper,
                materialMapper,
                productionMapper,
                reconcileMapper,
                ticketMapper,
                allocationMapper,
                requestMapper,
                new MesBatchFingerprintService());

        MesBatchStateService.MaterialBatchReservation reservation = new MesBatchStateService.MaterialBatchReservation(
                9101L,
                1001L,
                "GSS1|MAT|A",
                "0".repeat(64),
                List.of(
                        new MesBatchStateService.MaterialItemReservation(101, 5001L, "P001", "A", "P001|A"),
                        new MesBatchStateService.MaterialItemReservation(102, 5002L, "P002", "", "P002|")));

        MesBatchStateService.MaterialBatchReservationResult result = service.reserveMaterialBatch(reservation);

        assertThat(result.batch().getId()).isEqualTo(7001L);
        assertThat(result.batch().getBatchType()).isEqualTo("MATERIAL_PREPARATION");
        assertThat(result.batch().getBizId()).startsWith("QPO-MAT-" + LocalDate.now().toString().replace("-", "") + "-").hasSizeLessThanOrEqualTo(80);
        assertThat(result.batch().getItemCount()).isEqualTo(2);
        assertThat(result.items()).hasSize(2);
        assertThat(result.items()).extracting(MesMaterialBatchItem::getBatchId).containsOnly(7001L);
        assertThat(result.items()).extracting(MesMaterialBatchItem::getLineRequestId)
                .allSatisfy(id -> assertThat(id).startsWith("QPO-MATL-").hasSizeLessThanOrEqualTo(80))
                .doesNotHaveDuplicates()
                .doesNotContain(result.batch().getBizId());
        verify(ticketMapper).markMesMaterialLineReserved(101, result.items().get(0).getLineRequestId());
        verify(ticketMapper).markMesMaterialLineReserved(102, result.items().get(1).getLineRequestId());
        verify(requestMapper).linkMesBatchItem(5001L, 7001L, result.items().get(0).getId());
        verify(requestMapper).linkMesBatchItem(5002L, 7001L, result.items().get(1).getId());
        verify(productionMapper, never()).insert(any(MesProductionBatchItem.class));
    }

    @Test
    void reserveProductionBatchCreatesLineIdsForAllocationCompatibility() {
        MesIntegrationBatchMapper batchMapper = mock(MesIntegrationBatchMapper.class);
        MesMaterialBatchItemMapper materialMapper = mock(MesMaterialBatchItemMapper.class);
        MesProductionBatchItemMapper productionMapper = mock(MesProductionBatchItemMapper.class);
        MesIntegrationReconcileRunMapper reconcileMapper = mock(MesIntegrationReconcileRunMapper.class);
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        OrderDeliveryTicketAllocationMapper allocationMapper = mock(OrderDeliveryTicketAllocationMapper.class);
        OrderDeliveryTicketMesRequestMapper requestMapper = mock(OrderDeliveryTicketMesRequestMapper.class);
        when(batchMapper.insert(any())).thenAnswer(invocation -> {
            MesIntegrationBatch batch = invocation.getArgument(0);
            batch.setId(7101L);
            return 1;
        });
        when(productionMapper.insert(any())).thenAnswer(invocation -> {
            MesProductionBatchItem item = invocation.getArgument(0);
            item.setId(item.getAllocationId().longValue() + 9000L);
            return 1;
        });

        MesBatchStateService service = new MesBatchStateService(
                batchMapper,
                materialMapper,
                productionMapper,
                reconcileMapper,
                ticketMapper,
                allocationMapper,
                requestMapper,
                new MesBatchFingerprintService());

        MesBatchStateService.ProductionBatchReservation reservation = new MesBatchStateService.ProductionBatchReservation(
                9201L,
                1002L,
                "GSS1|DEM|A",
                "1".repeat(64),
                List.of(new MesBatchStateService.ProductionItemReservation(
                        201,
                        "SO-001",
                        "10",
                        "SY100399000001",
                        "SO-001|10|SY100399000001",
                        new BigDecimal("5"))));

        MesBatchStateService.ProductionBatchReservationResult result = service.reserveProductionBatch(reservation);

        assertThat(result.batch().getBatchType()).isEqualTo("PRODUCTION_DEMAND");
        assertThat(result.batch().getBizId()).startsWith("QPO-DEM-" + LocalDate.now().toString().replace("-", "") + "-").hasSizeLessThanOrEqualTo(80);
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.getBatchId()).isEqualTo(7101L);
            assertThat(item.getLineRequestId()).startsWith("QPO-DEML-").hasSizeLessThanOrEqualTo(80);
            assertThat(item.getLineRequestId()).isNotEqualTo(result.batch().getBizId());
            assertThat(item.getDemandQty()).isEqualByComparingTo("5");
        });
        verify(allocationMapper).markMesProductionLineReserved(201, result.items().get(0).getLineRequestId());
        verify(materialMapper, never()).insert(any(MesMaterialBatchItem.class));
    }

    private static Connection openDatabase() throws SQLException {
        String url = System.getenv("DB_URL");
        String username = System.getenv("DB_USERNAME");
        String password = System.getenv("DB_PASSWORD");
        Assumptions.assumeTrue(notBlank(url) && notBlank(username) && password != null,
                "DB_URL, DB_USERNAME and DB_PASSWORD are required for PostgreSQL migration tests");
        Connection connection = DriverManager.getConnection(url, username, password);
        connection.setAutoCommit(false);
        return connection;
    }

    private static String createTestSchema(Connection connection) throws SQLException {
        String schema = TEST_SCHEMA_PREFIX + UUID.randomUUID().toString().replace("-", "");
        execute(connection, "CREATE SCHEMA " + schema);
        connection.commit();
        return schema;
    }

    private static void dropTestSchema(Connection connection, String schema) throws SQLException {
        if (!schema.startsWith(TEST_SCHEMA_PREFIX)) {
            throw new IllegalArgumentException("Refusing to drop non-test schema: " + schema);
        }
        connection.rollback();
        execute(connection, "DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        connection.commit();
    }

    private static void migrate(Connection connection, String schema) {
        Flyway.configure()
                .dataSource(connectionString(connection), System.getenv("DB_USERNAME"), System.getenv("DB_PASSWORD"))
                .schemas(schema)
                .defaultSchema(schema)
                .locations("filesystem:" + MIGRATION_DIR)
                .baselineOnMigrate(false)
                .load()
                .migrate();
    }

    private static String connectionString(Connection connection) {
        try {
            return connection.getMetaData().getURL();
        } catch (SQLException error) {
            throw new RuntimeException(error);
        }
    }

    private static boolean tableExists(Connection connection, String schema, String table) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT EXISTS (
                    SELECT 1 FROM information_schema.tables
                    WHERE table_schema = ? AND table_name = ?
                )
                """)) {
            statement.setString(1, schema);
            statement.setString(2, table);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getBoolean(1);
            }
        }
    }

    private static boolean columnExists(Connection connection, String schema, String table, String column) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT EXISTS (
                    SELECT 1 FROM information_schema.columns
                    WHERE table_schema = ? AND table_name = ? AND column_name = ?
                )
                """)) {
            statement.setString(1, schema);
            statement.setString(2, table);
            statement.setString(3, column);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getBoolean(1);
            }
        }
    }

    private static boolean indexExists(Connection connection, String schema, String index) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT EXISTS (
                    SELECT 1 FROM pg_indexes
                    WHERE schemaname = ? AND indexname = ?
                )
                """)) {
            statement.setString(1, schema);
            statement.setString(2, index);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getBoolean(1);
            }
        }
    }

    private static List<String> columns(Connection connection, String schema, String table) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = ? AND table_name = ?
                ORDER BY ordinal_position
                """)) {
            statement.setString(1, schema);
            statement.setString(2, table);
            try (ResultSet resultSet = statement.executeQuery()) {
                java.util.ArrayList<String> rows = new java.util.ArrayList<>();
                while (resultSet.next()) {
                    rows.add(resultSet.getString(1));
                }
                return rows;
            }
        }
    }

    private static long insertBatch(Connection connection, String schema, String type, String bizId) throws SQLException {
        return insertBatch(connection, schema, type, bizId, "RESERVED");
    }

    private static long insertBatch(Connection connection, String schema, String type, String bizId, String status) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO %s.mes_integration_batch (
                    batch_type, biz_id, group_key_hash, request_fingerprint, item_count, status, attempt_no
                ) VALUES (?, ?, ?, ?, 1, ?, 1)
                RETURNING id
                """.formatted(schema))) {
            statement.setString(1, type);
            statement.setString(2, bizId);
            statement.setString(3, "a".repeat(64));
            statement.setString(4, "b".repeat(64));
            statement.setString(5, status);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getLong(1);
            }
        }
    }

    private static int insertTicket(Connection connection, String schema) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO %s.order_delivery_ticket (qty, part_no, version)
                VALUES (1, 'P001', 'A') RETURNING id
                """.formatted(schema))) {
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getInt(1);
            }
        }
    }

    private static int insertAllocation(Connection connection, String schema, int ticketId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO %s.order_delivery_ticket_allocation (ticket_id, allocation_type, qty)
                VALUES (?, 'PRODUCTION_REQUIRED', 1) RETURNING id
                """.formatted(schema))) {
            statement.setInt(1, ticketId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getInt(1);
            }
        }
    }

    private static void insertMaterialItem(Connection connection, String schema, long batchId, int ticketId, String lineRequestId, String status) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO %s.mes_material_batch_item (
                    batch_id, ticket_id, line_request_id, drawing_no, drawing_version, item_key, status
                ) VALUES (?, ?, ?, 'P001', 'A', ?, ?)
                """.formatted(schema))) {
            statement.setLong(1, batchId);
            statement.setInt(2, ticketId);
            statement.setString(3, lineRequestId);
            statement.setString(4, lineRequestId + '|');
            statement.setString(5, status);
            statement.executeUpdate();
        }
    }

    private static void updateMaterialItemStatus(Connection connection, String schema, String lineRequestId, String status) throws SQLException {
        execute(connection, "UPDATE " + schema + ".mes_material_batch_item SET status = '" + status + "' WHERE line_request_id = '" + lineRequestId + "'");
    }

    private static void insertProductionItem(Connection connection, String schema, long batchId, int allocationId, String lineRequestId, String status) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO %s.mes_production_batch_item (
                    batch_id, allocation_id, line_request_id, sale_order_no, sale_order_item_no,
                    product_code, item_key, demand_qty, status
                ) VALUES (?, ?, ?, 'SO-001', '10', 'SY100399000001', ?, 1, ?)
                """.formatted(schema))) {
            statement.setLong(1, batchId);
            statement.setInt(2, allocationId);
            statement.setString(3, lineRequestId);
            statement.setString(4, lineRequestId + '|');
            statement.setString(5, status);
            statement.executeUpdate();
        }
    }

    private static void updateProductionItemStatus(Connection connection, String schema, String lineRequestId, String status) throws SQLException {
        execute(connection, "UPDATE " + schema + ".mes_production_batch_item SET status = '" + status + "' WHERE line_request_id = '" + lineRequestId + "'");
    }

    private static void assertUniqueViolation(Connection connection, SqlRunnable runnable) throws SQLException {
        assertSqlState(connection, runnable, "23505");
    }

    private static void assertCheckViolation(Connection connection, SqlRunnable runnable) throws SQLException {
        assertSqlState(connection, runnable, "23514");
    }

    private static void assertSqlState(Connection connection, SqlRunnable runnable, String sqlState) throws SQLException {
        var savepoint = connection.setSavepoint();
        try {
            assertThatThrownBy(runnable::run)
                    .isInstanceOf(SQLException.class)
                    .extracting(error -> ((SQLException) error).getSQLState())
                    .isEqualTo(sqlState);
        } finally {
            connection.rollback(savepoint);
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    @FunctionalInterface
    private interface SqlRunnable {
        void run() throws SQLException;
    }
}
