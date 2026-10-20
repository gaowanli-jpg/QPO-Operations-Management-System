package com.machining.quote.service.mes;

import com.machining.quote.dto.mes.MesProductionOrderCreateReq;
import com.machining.quote.dto.mes.MesProductionOrderCreateResp;
import com.machining.quote.service.DataCenterEndpointRegistry;
import com.machining.quote.service.DataCenterIntegrationClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MesProductionOrderCreateClient {
    private static final Logger log = LoggerFactory.getLogger(MesProductionOrderCreateClient.class);

    @Autowired
    private DataCenterIntegrationClient dataCenterIntegrationClient;

    public MesProductionOrderCreateResp create(MesProductionOrderCreateReq req) {
        log.debug("MES production order create request bizId={} itemCount={} productCode={}",
                req == null ? null : req.getBizId(), itemCount(req), primaryProductCode(req));
        return dataCenterIntegrationClient.call(
                DataCenterEndpointRegistry.MES_STOCK_PICKING_ORDER_CREATE,
                req,
                MesProductionOrderCreateResp.class
        );
    }

    private int itemCount(MesProductionOrderCreateReq req) {
        return req == null || req.getItems() == null ? 0 : req.getItems().size();
    }

    private String primaryProductCode(MesProductionOrderCreateReq req) {
        if (req == null || req.getItems() == null || req.getItems().isEmpty() || req.getItems().get(0) == null) {
            return null;
        }
        return req.getItems().get(0).getProductCode();
    }
}
