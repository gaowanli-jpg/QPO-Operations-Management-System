package com.machining.quote.service.mes;

import com.machining.quote.dto.mes.MesProductionBomDTO;
import com.machining.quote.dto.mes.MesProductionDispatchReq;

import java.math.BigDecimal;

public class MesProductionDemandRequestValidator {
    public void validate(MesProductionDispatchReq req) {
        if (req == null) {
            throw new IllegalArgumentException("MES production request cannot be null");
        }
        requireText(req.getBizId(), "MES production bizId cannot be blank");
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new IllegalArgumentException("MES production items cannot be empty");
        }
        for (int i = 0; i < req.getItems().size(); i++) {
            MesProductionDispatchReq.Item item = req.getItems().get(i);
            if (item == null) {
                throw new IllegalArgumentException("MES production item cannot be null index=" + i);
            }
            requireText(item.getSaleOrderNo(), "MES production saleOrderNo cannot be blank index=" + i);
            requireText(item.getSaleOrderItemNo(), "MES production saleOrderItemNo cannot be blank index=" + i);
            requireText(item.getProductCode(), "MES production productCode cannot be blank index=" + i);
            requireNonNegative(item.getDemandQty(), "demandQty", i);
            requireNonNegative(item.getMakeQty(), "makeQty", i);
            requireNonNegative(item.getBuyQty(), "buyQty", i);
            requireNonNegative(item.getStockQty(), "stockQty", i);
            if (item.getDemandQty() == null || item.getDemandQty() <= 0) {
                throw new IllegalArgumentException("MES production demandQty must be greater than 0 index=" + i);
            }
            int sum = item.getMakeQty() + item.getBuyQty() + item.getStockQty();
            if (item.getDemandQty() != sum) {
                throw new IllegalArgumentException("MES production quantity equation mismatch index=" + i);
            }
            if (item.getRequiredDate() == null) {
                throw new IllegalArgumentException("MES production requiredDate cannot be null index=" + i);
            }
            validateBom(item, i);
        }
    }

    private void validateBom(MesProductionDispatchReq.Item item, int itemIndex) {
        if (item.getBom() == null) {
            return;
        }
        for (int i = 0; i < item.getBom().size(); i++) {
            MesProductionBomDTO bom = item.getBom().get(i);
            if (bom == null) {
                throw new IllegalArgumentException("MES production bom item cannot be null itemIndex=" + itemIndex + " bomIndex=" + i);
            }
            requireText(bom.getItemCode(), "MES production bom itemCode cannot be blank itemIndex=" + itemIndex + " bomIndex=" + i);
            if (bom.getQuantity() == null || bom.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("MES production bom quantity must be greater than 0 itemIndex=" + itemIndex + " bomIndex=" + i);
            }
        }
    }

    private void requireNonNegative(Integer value, String field, int index) {
        if (value == null || value < 0) {
            throw new IllegalArgumentException("MES production " + field + " must be non-negative index=" + index);
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
