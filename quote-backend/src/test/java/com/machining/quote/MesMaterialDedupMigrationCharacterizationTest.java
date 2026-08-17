package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MesMaterialDedupMigrationCharacterizationTest {
    private static final Path MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration", "V1__qpo_baseline_schema.sql");
    private static final Path ORDER_DELIVERY_TICKET = Path.of(
            "src", "main", "java", "com", "machining", "quote", "entity", "OrderDeliveryTicket.java");
    private static final Path MES_REQUEST_ENTITY = Path.of(
            "src", "main", "java", "com", "machining", "quote", "entity", "OrderDeliveryTicketMesRequest.java");
    private static final Path MES_WAITER_ENTITY = Path.of(
            "src", "main", "java", "com", "machining", "quote", "entity", "OrderDeliveryTicketMesWaiter.java");
    private static final Path MES_WAITER_MAPPER = Path.of(
            "src", "main", "java", "com", "machining", "quote", "mapper", "OrderDeliveryTicketMesWaiterMapper.java");
    private static final Path DELIVERED_ITEM_DTO = Path.of(
            "src", "main", "java", "com", "machining", "quote", "dto", "DeliveredItemDTO.java");
    private static final Path ORDER_DELIVERY_TICKET_MAPPER = Path.of(
            "src", "main", "java", "com", "machining", "quote", "mapper", "OrderDeliveryTicketMapper.java");
    private static final Path CALLBACK_DOC = Path.of(
            "..", "docs", "QPO报价状态流转", "18-MES物料号异步回调开发文档.md");

    @Test
    void migrationAddsDedupColumnsWaiterTableAndActiveMasterIndex() throws IOException {
        assertThat(MIGRATION).exists();
        String migration = read(MIGRATION);

        assertThat(migration)
                .contains(
                        "mes_dedup_key character varying(220)",
                        "mes_request_role character varying(20)",
                        "mes_master_ticket_id integer",
                        "mes_master_request_id character varying(80)",
                        "dedup_key character varying(220)",
                        "request_role character varying(20)",
                        "follower_count integer",
                        "CREATE TABLE order_delivery_ticket_mes_waiter",
                        "master_ticket_id integer NOT NULL",
                        "FOREIGN KEY (master_ticket_id) REFERENCES order_delivery_ticket(id)",
                        "master_mes_request_id character varying(80) NOT NULL",
                        "waiter_ticket_id integer NOT NULL",
                        "FOREIGN KEY (waiter_ticket_id) REFERENCES order_delivery_ticket(id)",
                        "uk_delivery_mes_request_active_dedup",
                        "PROCESSING'::character varying",
                        "CALLBACK_PENDING'::character varying",
                        "uk_delivery_mes_waiter_active_ticket",
                        "idx_delivery_mes_waiter_master_request",
                        "idx_delivery_mes_waiter_dedup");

        assertThat(migration)
                .contains("uk_order_delivery_ticket_mes_request_id")
                .contains("CREATE TABLE order_delivery_ticket_mes_waiter");
    }

    @Test
    void entitiesAndDtoExposeDedupFields() throws IOException {
        assertThat(read(ORDER_DELIVERY_TICKET))
                .contains(
                        "private String mesDedupKey;",
                        "private String mesRequestRole;",
                        "private Integer mesMasterTicketId;",
                        "private String mesMasterRequestId;");

        assertThat(read(MES_REQUEST_ENTITY))
                .contains(
                        "private String dedupKey;",
                        "private String requestRole;",
                        "private Integer followerCount;");

        assertThat(read(DELIVERED_ITEM_DTO))
                .contains(
                        "private String mesDedupKey;",
                        "private String mesRequestRole;",
                        "private Integer mesMasterTicketId;",
                        "private String mesMasterRequestId;");
    }

    @Test
    void waiterEntityAndMapperCanPersistFollowerRelationships() throws IOException {
        assertThat(read(MES_WAITER_ENTITY))
                .contains(
                        "@TableName(\"order_delivery_ticket_mes_waiter\")",
                        "private Long id;",
                        "private String dedupKey;",
                        "private Integer masterTicketId;",
                        "private String masterMesRequestId;",
                        "private Integer waiterTicketId;",
                        "private String waitStatus;",
                        "private String materialNo;",
                        "private String waitMessage;",
                        "private LocalDateTime completedAt;");

        assertThat(read(MES_WAITER_MAPPER))
                .contains(
                        "interface OrderDeliveryTicketMesWaiterMapper",
                        "extends BaseMapper<OrderDeliveryTicketMesWaiter>",
                        "@Mapper");
    }

    @Test
    void deliveredItemMapperReturnsDedupFieldsToThePage() throws IOException {
        String mapper = read(ORDER_DELIVERY_TICKET_MAPPER);

        assertThat(mapper)
                .contains(
                        "ticket.mes_dedup_key",
                        "ticket.mes_request_role",
                        "ticket.mes_master_ticket_id",
                        "ticket.mes_master_request_id");

        assertThat(mapper.indexOf("ticket.mes_dedup_key"))
                .isLessThan(mapper.indexOf("ticket.wms_allocate_status"));
    }

    @Test
    void callbackDocumentReferencesPartVersionDedupDesign() throws IOException {
        assertThat(read(CALLBACK_DOC))
                .contains(
                        "同图号版本去重",
                        "dedupKey",
                        "order_delivery_ticket_mes_waiter",
                        "MES 只回调主请求",
                        "QPO fan-out 写回等待行");
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
