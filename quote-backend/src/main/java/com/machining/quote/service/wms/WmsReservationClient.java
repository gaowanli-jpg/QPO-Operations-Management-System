package com.machining.quote.service.wms;

import com.machining.quote.dto.wms.WmsSaleLadingReserveReq;
import com.machining.quote.dto.wms.WmsSaleLadingReserveResp;
import com.machining.quote.service.DataCenterEndpointRegistry;
import com.machining.quote.service.DataCenterIntegrationClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class WmsReservationClient {
    @Autowired
    private DataCenterIntegrationClient dataCenterIntegrationClient;

    public WmsSaleLadingReserveResp reserve(WmsSaleLadingReserveReq request) {
        return dataCenterIntegrationClient.call(
                DataCenterEndpointRegistry.WMS_INVENTORY_ALLOCATE,
                request,
                WmsSaleLadingReserveResp.class
        );
    }
}
