package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MesMaterialCallbackMigrationTest {
    private static final Path MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration", "V1__qpo_baseline_schema.sql");
    private static final Path ORDER_DELIVERY_TICKET = Path.of(
            "src", "main", "java", "com", "machining", "quote", "entity", "OrderDeliveryTicket.java");
    private static final Path DELIVERED_ITEM_DTO = Path.of(
            "src", "main", "java", "com", "machining", "quote", "dto", "DeliveredItemDTO.java");
    private static final Path ORDER_DELIVERY_TICKET_MAPPER = Path.of(
            "src", "main", "java", "com", "machining", "quote", "mapper", "OrderDeliveryTicketMapper.java");
    private static final Path MES_REQUEST_ENTITY = Path.of(
            "src", "main", "java", "com", "machining", "quote", "entity", "OrderDeliveryTicketMesRequest.java");
    private static final Path MES_REQUEST_MAPPER = Path.of(
            "src", "main", "java", "com", "machining", "quote", "mapper", "OrderDeliveryTicketMesRequestMapper.java");

    @Test
    void migrationAddsTicketCallbackFieldsAndRequestLedger() throws IOException {
        assertThat(MIGRATION).exists();
        String migration = read(MIGRATION);

        assertThat(migration)
                .contains(
                        "mes_request_id character varying(80)",
                        "mes_external_task_id character varying(120)",
                        "mes_callback_received_at timestamp without time zone",
                        "mes_callback_message text",
                        "uk_order_delivery_ticket_mes_request_id",
                        "mes_request_id IS NOT NULL",
                        "CREATE TABLE order_delivery_ticket_mes_request",
                        "ticket_id integer NOT NULL",
                        "FOREIGN KEY (ticket_id) REFERENCES order_delivery_ticket(id)",
                        "mes_request_id character varying(80) NOT NULL",
                        "request_status character varying(30) DEFAULT 'PROCESSING'::character varying NOT NULL",
                        "CALLBACK_PENDING",
                        "uk_delivery_mes_request_request_id",
                        "idx_delivery_mes_request_ticket",
                        "idx_delivery_mes_request_external_task");
    }

    @Test
    void deliveryTicketAndDtoExposeMesCallbackFields() throws IOException {
        assertThat(read(ORDER_DELIVERY_TICKET))
                .contains(
                        "private String mesRequestId;",
                        "private String mesExternalTaskId;",
                        "private LocalDateTime mesCallbackReceivedAt;",
                        "private String mesCallbackMessage;");

        assertThat(read(DELIVERED_ITEM_DTO))
                .contains(
                        "private String mesRequestId;",
                        "private String mesExternalTaskId;",
                        "private LocalDateTime mesCallbackReceivedAt;",
                        "private String mesCallbackMessage;");
    }

    @Test
    void deliveredItemMapperReturnsCallbackFieldsToThePage() throws IOException {
        String mapper = read(ORDER_DELIVERY_TICKET_MAPPER);

        assertThat(mapper)
                .contains(
                        "ticket.mes_request_id",
                        "ticket.mes_external_task_id",
                        "ticket.mes_callback_received_at",
                        "ticket.mes_callback_message");

        assertThat(mapper.indexOf("ticket.mes_request_id"))
                .isLessThan(mapper.indexOf("ticket.wms_allocate_status"));
    }

    @Test
    void mesRequestEntityAndMapperProvideRequestLevelLookup() throws IOException {
        assertThat(read(MES_REQUEST_ENTITY))
                .contains(
                        "@TableName(\"order_delivery_ticket_mes_request\")",
                        "private Long id;",
                        "private Integer ticketId;",
                        "private String mesRequestId;",
                        "private Long systemTaskId;",
                        "private String requestStatus;",
                        "private String mesExternalTaskId;",
                        "private String materialNo;",
                        "private LocalDateTime callbackReceivedAt;");

        assertThat(read(MES_REQUEST_MAPPER))
                .contains(
                        "interface OrderDeliveryTicketMesRequestMapper",
                        "extends BaseMapper<OrderDeliveryTicketMesRequest>",
                        "@Mapper");
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
