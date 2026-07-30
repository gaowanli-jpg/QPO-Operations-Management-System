package com.machining.quote.dto;

import lombok.Data;

@Data
public class CustomerOptionDTO {
    private Long id;
    private String customerCode;
    private String customerShortName;
    private String customerName;
}
