package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WmsReservationMigrationTest {
    private static final Path V10 = Path.of(
            "src", "main", "resources", "db", "migration", "V10__wms_batch_reservation_contract.sql"
    );
    private static final Path V13 = Path.of(
            "src", "main", "resources", "db", "migration", "V13__wms_failed_reservation_state_repair.sql"
    );
    private static final Path BATCH_ENTITY = Path.of(
            "src", "main", "java", "com", "machining", "quote", "entity", "WmsReservationBatch.java"
    );
    private static final Path ITEM_ENTITY = Path.of(
            "src", "main", "java", "com", "machining", "quote", "entity", "WmsReservationItem.java"
    );
    private static final Path TICKET_ENTITY = Path.of(
            "src", "main", "java", "com", "machining", "quote", "entity", "WmsReservationTicket.java"
    );
    private static final Path BATCH_MAPPER = Path.of(
            "src", "main", "java", "com", "machining", "quote", "mapper", "WmsReservationBatchMapper.java"
    );
    private static final Path ITEM_MAPPER = Path.of(
            "src", "main", "java", "com", "machining", "quote", "mapper", "WmsReservationItemMapper.java"
    );
    private static final Path TICKET_MAPPER = Path.of(
            "src", "main", "java", "com", "machining", "quote", "mapper", "WmsReservationTicketMapper.java"
    );

    @Test
    void v10CreatesReservationTablesAndKeepsLegacyProductionOrderColumn() throws IOException {
        String sql = read(V10);

        assertThat(sql)
                .contains(
                        "CREATE TABLE wms_reservation_batch",
                        "CREATE TABLE wms_reservation_item",
                        "CREATE TABLE wms_reservation_ticket",
                        "external_biz_id VARCHAR(80) NOT NULL",
                        "request_fingerprint VARCHAR(64) NOT NULL",
                        "request_payload JSONB NOT NULL",
                        "attempt_no INTEGER NOT NULL DEFAULT 1",
                        "last_error_type VARCHAR(60)",
                        "CONSTRAINT chk_wms_reservation_batch_status CHECK (status IN",
                        "'RESERVED'",
                        "'SUBMITTING'",
                        "'RESPONSE_RECEIVED'",
                        "'SUCCESS'",
                        "'FAILED'",
                        "'RESULT_UNKNOWN'",
                        "'APPLY_FAILED'",
                        "CONSTRAINT chk_wms_reservation_item_status CHECK (status IN",
                        "CONSTRAINT chk_wms_reservation_ticket_status CHECK (status IN"
                )
                .doesNotContain("DROP COLUMN wms_" + "production_order_no");
    }

    @Test
    void v10AddsUniqueIndexesForeignKeysAndAllocationIdempotencyKey() throws IOException {
        String sql = read(V10);

        assertThat(sql)
                .contains(
                        "CREATE UNIQUE INDEX uk_wms_reservation_batch_external_biz_id",
                        "ON wms_reservation_batch(external_biz_id)",
                        "batch_id BIGINT NOT NULL REFERENCES wms_reservation_batch(id)",
                        "CONSTRAINT uk_wms_reservation_item_product UNIQUE (batch_id, product_code)",
                        "CONSTRAINT uk_wms_reservation_ticket_ticket UNIQUE (batch_id, ticket_id)",
                        "CREATE UNIQUE INDEX uk_wms_reservation_active_ticket",
                        "ON wms_reservation_ticket(ticket_id)",
                        "WHERE status IN ('RESERVED', 'SUBMITTING', 'RESULT_UNKNOWN', 'RESPONSE_RECEIVED', 'APPLY_FAILED')",
                        "ALTER TABLE order_delivery_ticket_allocation",
                        "ADD COLUMN wms_reservation_ticket_id BIGINT",
                        "REFERENCES wms_reservation_ticket(id)",
                        "CREATE UNIQUE INDEX uk_delivery_allocation_wms_reservation_type",
                        "ON order_delivery_ticket_allocation(wms_reservation_ticket_id, allocation_type)",
                        "WHERE wms_reservation_ticket_id IS NOT NULL"
                );
    }

    @Test
    void v10PinsWmsEndpointToSaleLadingReserveContract() throws IOException {
        String sql = read(V10);

        assertThat(sql)
                .contains(
                        "'WMS_INVENTORY_ALLOCATE'",
                        "'/api/v1/wms/admin-api/wms/datacenter/sale-lading/reserve'",
                        "'WMS_SALE_LADING_RESERVE_V1'",
                        "'WMS_SALE_LADING_RESERVE_RESPONSE_V1'",
                        "timeout_ms = GREATEST(COALESCE(system_data_center_endpoint_config.timeout_ms, 0), 60000)"
                )
                .doesNotContain(
                        "'WMS_ALLOCATION_V1'",
                        "'WMS_ALLOCATION_RESPONSE_V1'",
                        "'/api/wms/inventory/allocate'"
                );
    }

    @Test
    void v13BackfillsFailedReservationRowsSoTicketsCanRetry() throws IOException {
        String sql = read(V13);

        assertThat(sql)
                .contains(
                        "UPDATE wms_reservation_item i",
                        "FROM wms_reservation_batch b",
                        "b.status = 'FAILED'",
                        "UPDATE wms_reservation_ticket t",
                        "SET status = 'FAILED'",
                        "UPDATE order_delivery_ticket odt",
                        "SET wms_allocate_status = 'FAILED'",
                        "COALESCE(odt.wms_allocate_status, 'PENDING') <> 'SUCCESS'"
                );
    }

    @Test
    void reservationEntitiesAndMappersExposeRequiredContracts() throws IOException {
        String batchEntity = read(BATCH_ENTITY);
        String itemEntity = read(ITEM_ENTITY);
        String ticketEntity = read(TICKET_ENTITY);
        String batchMapper = read(BATCH_MAPPER);
        String itemMapper = read(ITEM_MAPPER);
        String ticketMapper = read(TICKET_MAPPER);

        assertThat(batchEntity)
                .contains(
                        "@TableName(value = \"wms_reservation_batch\", autoResultMap = true)",
                        "@TableField(typeHandler = JsonbTypeHandler.class)",
                        "private Object requestPayload;",
                        "private String requestFingerprint;",
                        "private Integer attemptNo;",
                        "private String lastErrorType;"
                );
        assertThat(itemEntity)
                .contains(
                        "@TableName(\"wms_reservation_item\")",
                        "private BigDecimal reserveCount;",
                        "private BigDecimal inProductionCount;",
                        "private BigDecimal surplusCount;"
                );
        assertThat(ticketEntity)
                .contains(
                        "@TableName(\"wms_reservation_ticket\")",
                        "private Long batchItemId;",
                        "private Integer ticketId;",
                        "private Long salesOrderItemId;",
                        "private Integer salesOrderLineNo;",
                        "private BigDecimal stockFrozenCount;"
                );
        assertThat(batchMapper)
                .contains(
                        "extends BaseMapper<WmsReservationBatch>",
                        "WmsReservationBatch selectByExternalBizIdForUpdate",
                        "WHERE external_biz_id = #{externalBizId} FOR UPDATE"
                );
        assertThat(itemMapper).contains("extends BaseMapper<WmsReservationItem>");
        assertThat(ticketMapper)
                .contains(
                        "extends BaseMapper<WmsReservationTicket>",
                        "WmsReservationTicket selectActiveByTicketId",
                        "List<WmsReservationTicket> selectByBatchId",
                        "WHERE ticket_id = #{ticketId}",
                        "WHERE batch_id = #{batchId}"
                );
    }

    private static String read(Path path) throws IOException {
        assertThat(path).exists();
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
