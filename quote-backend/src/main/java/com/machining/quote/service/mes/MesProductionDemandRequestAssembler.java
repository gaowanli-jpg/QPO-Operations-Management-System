package com.machining.quote.service.mes;

import com.machining.quote.dto.mes.MesProductionDispatchReq;

import java.util.List;

public class MesProductionDemandRequestAssembler {
    private final MesProductionDemandRequestValidator validator = new MesProductionDemandRequestValidator();

    public MesProductionDispatchReq assemble(
            String bizId,
            String customerCode,
            String customerName,
            String internalProjectNo,
            List<MesProductionDispatchReq.Item> items) {
        MesProductionDispatchReq req = new MesProductionDispatchReq();
        req.setBizId(trimToNull(bizId));
        req.setCustomerCode(trimToNull(customerCode));
        req.setCustomerName(trimToNull(customerName));
        req.setInternalProjectNo(trimToNull(internalProjectNo));
        req.setItems(items);
        validator.validate(req);
        return req;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
