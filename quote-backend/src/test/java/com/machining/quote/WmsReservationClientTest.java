package com.machining.quote;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.machining.quote.dto.wms.WmsSaleLadingReserveReq;
import com.machining.quote.dto.wms.WmsSaleLadingReserveResp;
import com.machining.quote.service.DataCenterEndpointRegistry;
import com.machining.quote.service.DataCenterIntegrationClient;
import com.machining.quote.service.wms.WmsReservationClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WmsReservationClientTest {
    @Test
    void singleItemRequestStillSerializesItemsArrayWithRemarkAndBigDecimalQuantity() throws Exception {
        WmsSaleLadingReserveReq request = request("QPO-WMS-1", "SO-1", item("500901000700", "5", "waterproof dressing"));

        JsonNode root = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(request));

        assertThat(root.get("externalBizId").asText()).isEqualTo("QPO-WMS-1");
        assertThat(root.get("saleNo").asText()).isEqualTo("SO-1");
        assertThat(root.get("items").isArray()).isTrue();
        assertThat(root.get("items")).hasSize(1);
        assertThat(root.at("/items/0/productCode").asText()).isEqualTo("500901000700");
        assertThat(root.at("/items/0/reserveCount").decimalValue()).isEqualByComparingTo(new BigDecimal("5"));
        assertThat(root.at("/items/0/remark").asText()).isEqualTo("waterproof dressing");
        assertThat(request.getItems().get(0).getReserveCount()).isInstanceOf(BigDecimal.class);
    }

    @Test
    void reserveDelegatesOnlyToWmsInventoryAllocateDataCenterEndpoint() {
        DataCenterIntegrationClient dataCenterClient = mock(DataCenterIntegrationClient.class);
        WmsReservationClient client = new WmsReservationClient();
        ReflectionTestUtils.setField(client, "dataCenterIntegrationClient", dataCenterClient);
        WmsSaleLadingReserveReq request = request("QPO-WMS-1", "SO-1", item("500901000700", "5", "remark"));
        WmsSaleLadingReserveResp expected = new WmsSaleLadingReserveResp();
        expected.setCode(200);
        when(dataCenterClient.call(
                eq(DataCenterEndpointRegistry.WMS_INVENTORY_ALLOCATE),
                eq(request),
                eq(WmsSaleLadingReserveResp.class)
        )).thenReturn(expected);

        WmsSaleLadingReserveResp response = client.reserve(request);

        assertThat(response).isSameAs(expected);
        verify(dataCenterClient).call(
                DataCenterEndpointRegistry.WMS_INVENTORY_ALLOCATE,
                request,
                WmsSaleLadingReserveResp.class
        );
    }

    @Test
    void reservationClientDoesNotContainLegacyDirectWmsFallback() throws Exception {
        String source = Files.readString(Path.of(
                "src", "main", "java", "com", "machining", "quote", "service", "wms", "WmsReservationClient.java"
        ), StandardCharsets.UTF_8);

        assertThat(source)
                .contains(
                        "dataCenterIntegrationClient.call",
                        "DataCenterEndpointRegistry.WMS_INVENTORY_ALLOCATE",
                        "WmsSaleLadingReserveResp.class"
                )
                .doesNotContain(
                        "RestTemplate",
                        "new RestTemplate",
                        "@Value",
                        "erp.wms",
                        "WmsAllocationReq",
                        "WmsAllocationResp"
                );
    }

    private WmsSaleLadingReserveReq request(String externalBizId, String saleNo, WmsSaleLadingReserveReq.Item... items) {
        WmsSaleLadingReserveReq request = new WmsSaleLadingReserveReq();
        request.setExternalBizId(externalBizId);
        request.setSaleNo(saleNo);
        request.setItems(List.of(items));
        return request;
    }

    private WmsSaleLadingReserveReq.Item item(String productCode, String reserveCount, String remark) {
        WmsSaleLadingReserveReq.Item item = new WmsSaleLadingReserveReq.Item();
        item.setProductCode(productCode);
        item.setReserveCount(new BigDecimal(reserveCount));
        item.setRemark(remark);
        return item;
    }
}
