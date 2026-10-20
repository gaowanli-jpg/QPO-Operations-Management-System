package com.machining.quote;

import com.machining.quote.dto.wms.WmsSaleLadingReserveReq;
import com.machining.quote.dto.wms.WmsSaleLadingReserveResp;
import com.machining.quote.service.DataCenterCallException;
import com.machining.quote.service.DataCenterEndpointRegistry;
import com.machining.quote.service.wms.WmsReservationContractValidator;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static com.machining.quote.service.DataCenterCallException.FailureCertainty.DEFINITE_FAILURE;
import static com.machining.quote.service.DataCenterCallException.FailureCertainty.RESULT_UNKNOWN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WmsReservationContractValidatorTest {
    private final WmsReservationContractValidator validator = new WmsReservationContractValidator();

    @Test
    void validResponseReturnsDataForLocalAllocation() {
        WmsSaleLadingReserveReq request = request(
                item("500901000700", "5", "first"),
                item("500901000699", "3", "second")
        );
        WmsSaleLadingReserveResp response = success(
                responseItem("500901000700", "5", "3", "1", "4", "1"),
                responseItem("500901000699", "3", "1", "0", "1", "2")
        );

        WmsSaleLadingReserveResp.Data data = validator.validate(request, response);

        assertThat(data.getSaleOrderNo()).isEqualTo("SO-1");
        assertThat(data.getExternalBizId()).isEqualTo("QPO-WMS-1");
        assertThat(data.getItems()).hasSize(2);
    }

    @Test
    void nonSuccessRootCodeIsDefiniteFailure() {
        WmsSaleLadingReserveResp response = success(responseItem("500901000700", "5", "3", "1", "4", "1"));
        response.setCode(400);
        response.setMsg("reserve rejected");

        assertCallException(
                () -> validator.validate(request(item("500901000700", "5", "first")), response),
                DEFINITE_FAILURE,
                "code=400"
        );
    }

    @Test
    void successfulButMissingOrMismatchedIdentityIsResultUnknown() {
        WmsSaleLadingReserveReq request = request(item("500901000700", "5", "first"));

        assertCallException(() -> validator.validate(request, null), RESULT_UNKNOWN, "empty");
        assertCallException(() -> validator.validate(request, successWithData(null)), RESULT_UNKNOWN, "data");

        WmsSaleLadingReserveResp saleNoMismatch = success(responseItem("500901000700", "5", "3", "1", "4", "1"));
        saleNoMismatch.getData().setSaleOrderNo("SO-OTHER");
        assertCallException(() -> validator.validate(request, saleNoMismatch), RESULT_UNKNOWN, "saleOrderNo");

        WmsSaleLadingReserveResp bizIdMismatch = success(responseItem("500901000700", "5", "3", "1", "4", "1"));
        bizIdMismatch.getData().setExternalBizId("QPO-WMS-OTHER");
        assertCallException(() -> validator.validate(request, bizIdMismatch), RESULT_UNKNOWN, "externalBizId");
    }

    @Test
    void successfulButItemSetDriftDuplicateProductsOrReserveMismatchIsResultUnknown() {
        WmsSaleLadingReserveReq request = request(
                item("500901000700", "5", "first"),
                item("500901000699", "3", "second")
        );

        assertCallException(
                () -> validator.validate(request, success(responseItem("500901000700", "5", "3", "1", "4", "1"))),
                RESULT_UNKNOWN,
                "productCode"
        );
        assertCallException(
                () -> validator.validate(request, success(
                        responseItem("500901000700", "5", "3", "1", "4", "1"),
                        responseItem("500901000700", "5", "3", "1", "4", "1")
                )),
                RESULT_UNKNOWN,
                "duplicate"
        );
        assertCallException(
                () -> validator.validate(request, success(
                        responseItem("500901000700", "4", "3", "1", "4", "0"),
                        responseItem("500901000699", "3", "1", "0", "1", "2")
                )),
                RESULT_UNKNOWN,
                "reserveCount"
        );
    }

    @Test
    void successfulButInvalidQuantitySemanticsAreResultUnknown() {
        WmsSaleLadingReserveReq request = request(item("500901000700", "5", "first"));

        assertCallException(
                () -> validator.validate(request, success(responseItem("500901000700", "5", "-1", "1", "4", "1"))),
                RESULT_UNKNOWN,
                "non-negative"
        );
        assertCallException(
                () -> validator.validate(request, success(responseItem("500901000700", "5.5", "3", "1", "4.5", "1"))),
                RESULT_UNKNOWN,
                "integer"
        );
        assertCallException(
                () -> validator.validate(request, success(responseItem("500901000700", "5", "3", "1", "3", "1"))),
                RESULT_UNKNOWN,
                "frozenCount + surplusCount"
        );
        assertCallException(
                () -> validator.validate(request, success(responseItem("500901000700", "5", "3", "5", "4", "1"))),
                RESULT_UNKNOWN,
                "inProductionCount"
        );
        assertCallException(
                () -> validator.validate(request, success(responseItem("500901000700", "5", "2", "1", "4", "1"))),
                RESULT_UNKNOWN,
                "stockCount"
        );
    }

    private void assertCallException(ThrowingCallable callable,
                                     DataCenterCallException.FailureCertainty certainty,
                                     String messageFragment) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(DataCenterCallException.class, error -> {
                    assertThat(error.getEndpointCode()).isEqualTo(DataCenterEndpointRegistry.WMS_INVENTORY_ALLOCATE);
                    assertThat(error.getCertainty()).isEqualTo(certainty);
                    assertThat(error.getMessage()).contains(messageFragment);
                });
    }

    private WmsSaleLadingReserveReq request(WmsSaleLadingReserveReq.Item... items) {
        WmsSaleLadingReserveReq request = new WmsSaleLadingReserveReq();
        request.setExternalBizId("QPO-WMS-1");
        request.setSaleNo("SO-1");
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

    private WmsSaleLadingReserveResp success(WmsSaleLadingReserveResp.Item... items) {
        WmsSaleLadingReserveResp.Data data = new WmsSaleLadingReserveResp.Data();
        data.setSaleOrderNo("SO-1");
        data.setExternalBizId("QPO-WMS-1");
        data.setItems(List.of(items));
        return successWithData(data);
    }

    private WmsSaleLadingReserveResp successWithData(WmsSaleLadingReserveResp.Data data) {
        WmsSaleLadingReserveResp response = new WmsSaleLadingReserveResp();
        response.setCode(200);
        response.setMsg("");
        response.setData(data);
        return response;
    }

    private WmsSaleLadingReserveResp.Item responseItem(
            String productCode,
            String reserveCount,
            String stockCount,
            String inProductionCount,
            String frozenCount,
            String surplusCount) {
        WmsSaleLadingReserveResp.Item item = new WmsSaleLadingReserveResp.Item();
        item.setProductCode(productCode);
        item.setReserveCount(new BigDecimal(reserveCount));
        item.setStockCount(new BigDecimal(stockCount));
        item.setInProductionCount(new BigDecimal(inProductionCount));
        item.setFrozenCount(new BigDecimal(frozenCount));
        item.setSurplusCount(new BigDecimal(surplusCount));
        return item;
    }
}
