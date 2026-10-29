package com.machining.quote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.machining.quote.dto.mes.MesProductionDispatchResp;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MesProductionDispatchRespDeserializationTest {
    private final ObjectMapper strictMapper = new ObjectMapper();

    @Test
    void toleratesUnknownFieldsAndSingleItemObjectFromMes() throws Exception {
        MesProductionDispatchResp response = strictMapper.readValue("""
                {
                  "code": 0,
                  "msg": "accepted",
                  "traceId": "dc-trace-1",
                  "data": {
                    "bizId": "QPO-DEM-1",
                    "extra": "ignored",
                    "items": {
                      "id": "MES-TASK-1",
                      "planNo": "PLAN-1",
                      "saleOrderNo": "SO-1",
                      "saleOrderItemNo": "1",
                      "productCode": "MAT-1",
                      "status": "SUCCESS",
                      "extra": "ignored"
                    }
                  }
                }
                """, MesProductionDispatchResp.class);

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getRequestId()).isEqualTo("QPO-DEM-1");
        assertThat(response.getExternalId()).isEqualTo("MES-TASK-1");
        assertThat(response.getData().getItems()).hasSize(1);
        assertThat(response.getData().getItems().get(0).getSaleOrderItemNo()).isEqualTo("1");
    }

    @Test
    void rejectsConflictingRootAndDataRequestIds() throws Exception {
        MesProductionDispatchResp response = strictMapper.readValue("""
                {"code":0,"requestId":"QPO-DEM-1","data":{"bizId":"OTHER","items":[]}}
                """, MesProductionDispatchResp.class);

        assertThat(response.getRequestId()).isEqualTo("QPO-DEM-1");
        assertThat(response.matchesRequestId("QPO-DEM-1")).isFalse();
    }

    @Test
    void treatsOnlyNumericZeroAsSuccessCode() throws Exception {
        MesProductionDispatchResp fractional = strictMapper.readValue("{\"code\":0.5}", MesProductionDispatchResp.class);
        MesProductionDispatchResp decimalZero = strictMapper.readValue("{\"code\":0.0}", MesProductionDispatchResp.class);
        MesProductionDispatchResp stringZero = strictMapper.readValue("{\"code\":\"0.00\"}", MesProductionDispatchResp.class);

        assertThat(fractional.getSuccess()).isFalse();
        assertThat(decimalZero.getSuccess()).isTrue();
        assertThat(stringZero.getSuccess()).isTrue();
    }
}
