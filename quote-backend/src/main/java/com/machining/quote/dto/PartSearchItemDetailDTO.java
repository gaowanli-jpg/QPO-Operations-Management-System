package com.machining.quote.dto;

import lombok.Data;

import java.util.Map;

@Data
public class PartSearchItemDetailDTO {
    private String sourceType;
    private Map<String, Object> orderSummary;
    private Object itemDetail;
}
