package com.machining.quote.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ReleaseItemOrderFieldUpdateReq {
    private Integer rfqId;
    private LocalDate salesOrderDate;
    private String customerOrderNo;
    private String customerProjectNo;
    private String customerOrderTitle;
    private String orderType;
    private String internalProjectNo;
}
