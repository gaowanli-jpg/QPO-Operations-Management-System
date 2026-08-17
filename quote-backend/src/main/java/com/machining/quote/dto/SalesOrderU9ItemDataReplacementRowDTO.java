package com.machining.quote.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SalesOrderU9ItemDataReplacementRowDTO {
    private Integer excelRowNumber;
    private Integer lineNo;
    private Long salesOrderItemId;
    private String partNo;
    private String version;
    private SalesOrderU9ItemDataReplacementRowStatus status;
    private List<SalesOrderU9ItemDataReplacementChangeDTO> changes = new ArrayList<>();
    private String reason;
}
