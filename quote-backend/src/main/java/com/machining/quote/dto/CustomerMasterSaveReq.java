package com.machining.quote.dto;

import lombok.Data;

@Data
public class CustomerMasterSaveReq {
    private Long id;
    private String customerCode;
    private String customerShortName;
    private String customerName;
    private Boolean enabled;
}
