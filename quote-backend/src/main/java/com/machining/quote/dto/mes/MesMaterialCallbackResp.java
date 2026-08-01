package com.machining.quote.dto.mes;

import lombok.Data;

@Data
public class MesMaterialCallbackResp {
    private Boolean success;
    private String message;
    private Integer ticketId;
    private String mesRequestId;
    private String materialNo;
}
