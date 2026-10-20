package com.machining.quote;

import com.machining.quote.controller.MarketController;
import com.machining.quote.dto.DeliveryGenerateReq;
import com.machining.quote.dto.ReleaseItemBatchQueryReq;
import com.machining.quote.dto.ReleaseItemFieldUpdateReq;
import com.machining.quote.dto.ReleaseOrderSource;
import com.machining.quote.dto.ReleaseSourceType;
import com.machining.quote.service.DeliveryService;
import com.machining.quote.service.InternalTargetLeadTimeService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ReleaseRfqOnlyBoundaryTest {

    @Test
    void publicDeliveryGenerationRejectsSalesOrderSources() {
        DeliveryGenerateReq req = new DeliveryGenerateReq();
        DeliveryGenerateReq.DeliveryItemReq item = new DeliveryGenerateReq.DeliveryItemReq();
        item.setSourceType(ReleaseSourceType.SALES_ORDER);
        item.setSourceOrderId(1001L);
        req.setItems(List.of(item));

        assertThatThrownBy(() -> new DeliveryService().generateRfqTickets(req))
                .hasMessageContaining("RFQ");
    }

    @Test
    void releaseBatchItemQueryRejectsSalesOrderSources() {
        ReleaseItemBatchQueryReq req = new ReleaseItemBatchQueryReq();
        req.setSources(List.of(ReleaseOrderSource.salesOrder(1001L)));

        assertThatThrownBy(() -> controller().listReleaseItemsBatch(req))
                .hasMessageContaining("RFQ");
    }

    @Test
    void releaseBatchPartSelectionRejectsSalesOrderSources() {
        assertThatThrownBy(() -> controller().parsePartSelectionForOrders(
                null,
                null,
                "[{\"sourceType\":\"SALES_ORDER\",\"sourceOrderId\":1001}]"))
                .hasMessageContaining("RFQ");
    }

    @Test
    void releaseDrawingReplacementRejectsSalesOrderSourcesBeforeFileHandling() {
        assertThatThrownBy(() -> controller().replaceReleaseDrawings(
                "[{\"sourceType\":\"SALES_ORDER\",\"sourceOrderId\":1001}]",
                null,
                List.of(),
                List.of()))
                .hasMessageContaining("RFQ");
    }

    @Test
    void releaseItemFieldUpdateRejectsSalesOrderSources() {
        ReleaseItemFieldUpdateReq req = new ReleaseItemFieldUpdateReq();
        req.setSourceType(ReleaseSourceType.SALES_ORDER);

        assertThatThrownBy(() -> controller().updateReleaseItemFields(1001L, req))
                .hasMessageContaining("RFQ");
    }

    private MarketController controller() {
        MarketController controller = new MarketController();
        ReflectionTestUtils.setField(controller, "deliveryService", mock(DeliveryService.class));
        ReflectionTestUtils.setField(controller, "internalTargetLeadTimeService", mock(InternalTargetLeadTimeService.class));
        return controller;
    }
}
