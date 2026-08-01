package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MesMaterialCallbackStage1Test {
    private static final Path MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration", "V73__delivery_ticket_mes_callback.sql");
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
                        "ADD COLUMN IF NOT EXISTS mes_request_id VARCHAR(80)",
                        "ADD COLUMN IF NOT EXISTS mes_external_task_id VARCHAR(120)",
                        "ADD COLUMN IF NOT EXISTS mes_callback_received_at TIMESTAMP",
                        "ADD COLUMN IF NOT EXISTS mes_callback_message TEXT",
                        "uk_order_delivery_ticket_mes_request_id",
                        "WHERE mes_request_id IS NOT NULL",
                        "CREATE TABLE IF NOT EXISTS order_delivery_ticket_mes_request",
                        "ticket_id INTEGER NOT NULL REFERENCES order_delivery_ticket(id)",
                        "mes_request_id VARCHAR(80) NOT NULL",
                        "request_status VARCHAR(30) NOT NULL DEFAULT 'PROCESSING'",
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
