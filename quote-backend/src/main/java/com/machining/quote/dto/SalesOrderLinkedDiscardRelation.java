package com.machining.quote.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SalesOrderLinkedDiscardRelation {
    private Integer recordId;
    private Long salesOrderItemId;
}
