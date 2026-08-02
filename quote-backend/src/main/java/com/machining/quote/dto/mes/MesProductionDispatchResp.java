package com.machining.quote.dto.mes;

import lombok.Data;

@Data
public class MesProductionDispatchResp {
    private Boolean success;
    private String requestId;
    private String externalId;
    private String message;
}
