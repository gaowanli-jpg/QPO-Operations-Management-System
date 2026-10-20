package com.machining.quote.service.mes;

import com.machining.quote.dto.mes.MesMaterialResolveReq;

import java.util.List;

public class MesMaterialRequestAssembler {
    private final MesMaterialRequestValidator validator = new MesMaterialRequestValidator();

    public MesMaterialResolveReq assemble(
            String bizId,
            String customerCode,
            String customerName,
            String internalProjectNo,
            List<MesMaterialResolveReq.Item> items) {
        MesMaterialResolveReq req = new MesMaterialResolveReq();
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
