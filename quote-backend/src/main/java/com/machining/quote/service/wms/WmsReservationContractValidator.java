package com.machining.quote.service.wms;

import com.machining.quote.dto.wms.WmsSaleLadingReserveReq;
import com.machining.quote.dto.wms.WmsSaleLadingReserveResp;
import com.machining.quote.service.DataCenterCallException;
import com.machining.quote.service.DataCenterEndpointRegistry;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class WmsReservationContractValidator {
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    public WmsSaleLadingReserveResp.Data validate(
            WmsSaleLadingReserveReq request,
            WmsSaleLadingReserveResp response) {
        Map<String, WmsSaleLadingReserveReq.Item> requestItems = requestItems(request);
        if (response == null) {
            throw unknown("WMS reserve response body is empty");
        }
        Integer code = response.getCode();
        if (code == null) {
            throw unknown("WMS reserve response code is empty");
        }
        if (code != 200) {
            throw definite("WMS reserve failed code=" + code + " msg=" + abbreviate(response.getMsg()));
        }
        WmsSaleLadingReserveResp.Data data = response.getData();
        if (data == null) {
            throw unknown("WMS reserve response data is empty");
        }
        if (!equalsText(request.getSaleNo(), data.getSaleOrderNo())) {
            throw unknown("WMS reserve saleOrderNo mismatch");
        }
        if (!equalsText(request.getExternalBizId(), data.getExternalBizId())) {
            throw unknown("WMS reserve externalBizId mismatch");
        }
        List<WmsSaleLadingReserveResp.Item> responseItems = data.getItems();
        if (responseItems == null || responseItems.isEmpty()) {
            throw unknown("WMS reserve response items are empty");
        }
        Map<String, WmsSaleLadingReserveResp.Item> responseByProduct = new HashMap<>();
        for (WmsSaleLadingReserveResp.Item item : responseItems) {
            String productCode = text(item == null ? null : item.getProductCode());
            if (productCode == null) {
                throw unknown("WMS reserve response productCode is empty");
            }
            if (responseByProduct.put(productCode, item) != null) {
                throw unknown("WMS reserve duplicate productCode=" + productCode);
            }
        }
        if (!responseByProduct.keySet().equals(requestItems.keySet())) {
            throw unknown("WMS reserve productCode set mismatch");
        }
        for (Map.Entry<String, WmsSaleLadingReserveReq.Item> entry : requestItems.entrySet()) {
            String productCode = entry.getKey();
            WmsSaleLadingReserveReq.Item requestItem = entry.getValue();
            WmsSaleLadingReserveResp.Item responseItem = responseByProduct.get(productCode);
            requireSameQuantity("reserveCount", requestItem.getReserveCount(), responseItem.getReserveCount(), productCode);
            validateQuantitySemantics(responseItem, productCode);
        }
        return data;
    }

    private Map<String, WmsSaleLadingReserveReq.Item> requestItems(WmsSaleLadingReserveReq request) {
        if (request == null) {
            throw definite("WMS reserve request cannot be null");
        }
        List<WmsSaleLadingReserveReq.Item> items = request.getItems();
        if (items == null || items.isEmpty()) {
            throw definite("WMS reserve request items cannot be empty");
        }
        Map<String, WmsSaleLadingReserveReq.Item> result = new HashMap<>();
        for (WmsSaleLadingReserveReq.Item item : items) {
            String productCode = text(item == null ? null : item.getProductCode());
            if (productCode == null) {
                throw definite("WMS reserve request productCode cannot be empty");
            }
            if (result.put(productCode, item) != null) {
                throw definite("WMS reserve request duplicate productCode=" + productCode);
            }
        }
        return result;
    }

    private void requireSameQuantity(String field, BigDecimal expected, BigDecimal actual, String productCode) {
        requireWholeNonNegative(field, actual, productCode);
        requireWholeNonNegative(field, expected, productCode);
        if (expected.compareTo(actual) != 0) {
            throw unknown("WMS reserve " + field + " mismatch productCode=" + productCode);
        }
    }

    private void validateQuantitySemantics(WmsSaleLadingReserveResp.Item item, String productCode) {
        BigDecimal reserveCount = requireWholeNonNegative("reserveCount", item.getReserveCount(), productCode);
        BigDecimal stockCount = requireWholeNonNegative("stockCount", item.getStockCount(), productCode);
        BigDecimal inProductionCount = requireWholeNonNegative("inProductionCount", item.getInProductionCount(), productCode);
        BigDecimal frozenCount = requireWholeNonNegative("frozenCount", item.getFrozenCount(), productCode);
        BigDecimal surplusCount = requireWholeNonNegative("surplusCount", item.getSurplusCount(), productCode);
        if (frozenCount.add(surplusCount).compareTo(reserveCount) != 0) {
            throw unknown("WMS reserve frozenCount + surplusCount must equal reserveCount productCode=" + productCode);
        }
        if (inProductionCount.compareTo(frozenCount) > 0) {
            throw unknown("WMS reserve inProductionCount cannot exceed frozenCount productCode=" + productCode);
        }
        if (frozenCount.subtract(inProductionCount).compareTo(stockCount) > 0) {
            throw unknown("WMS reserve stockCount cannot be less than stock frozen count productCode=" + productCode);
        }
    }

    private BigDecimal requireWholeNonNegative(String field, BigDecimal value, String productCode) {
        if (value == null) {
            throw unknown("WMS reserve " + field + " is empty productCode=" + productCode);
        }
        if (value.compareTo(ZERO) < 0) {
            throw unknown("WMS reserve quantities must be non-negative productCode=" + productCode + " field=" + field);
        }
        if (value.stripTrailingZeros().scale() > 0) {
            throw unknown("WMS reserve quantities must be integer productCode=" + productCode + " field=" + field);
        }
        return value;
    }

    private DataCenterCallException definite(String message) {
        return failure(DataCenterCallException.FailureCertainty.DEFINITE_FAILURE, message);
    }

    private DataCenterCallException unknown(String message) {
        return failure(DataCenterCallException.FailureCertainty.RESULT_UNKNOWN, message);
    }

    private DataCenterCallException failure(DataCenterCallException.FailureCertainty certainty, String message) {
        return new DataCenterCallException(
                DataCenterEndpointRegistry.WMS_INVENTORY_ALLOCATE,
                certainty,
                null,
                message,
                null
        );
    }

    private boolean equalsText(String left, String right) {
        String a = text(left);
        String b = text(right);
        return a != null && a.equals(b);
    }

    private String text(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String abbreviate(String value) {
        String text = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        return text.length() <= 200 ? text : text.substring(0, 200);
    }
}
