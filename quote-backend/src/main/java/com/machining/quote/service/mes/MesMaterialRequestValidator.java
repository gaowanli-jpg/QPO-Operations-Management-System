package com.machining.quote.service.mes;

import com.machining.quote.dto.mes.MesMaterialResolveReq;

public class MesMaterialRequestValidator {
    public void validate(MesMaterialResolveReq req) {
        if (req == null) {
            throw new IllegalArgumentException("MES material request cannot be null");
        }
        requireText(req.getBizId(), "MES material bizId cannot be blank");
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new IllegalArgumentException("MES material items cannot be empty");
        }
        for (int i = 0; i < req.getItems().size(); i++) {
            MesMaterialResolveReq.Item item = req.getItems().get(i);
            if (item == null) {
                throw new IllegalArgumentException("MES material item cannot be null index=" + i);
            }
            requireText(item.getDrawingNo(), "MES material drawingNo cannot be blank index=" + i);
            if (item.getDrawingVersion() == null) {
                throw new IllegalArgumentException("MES material drawingVersion cannot be null index=" + i);
            }
        }
    }

    private void requireText(String value, String message) {
        if (trimToNull(value) == null) {
            throw new IllegalArgumentException(message);
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
