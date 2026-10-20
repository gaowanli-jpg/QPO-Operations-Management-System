package com.machining.quote.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ReleaseItemDataReplacementRowDTO {
    private Integer excelRowNumber;
    private String partNo;
    private String version;
    private Integer rfqId;
    private String rfqNo;
    private Integer itemId;
    private ReleaseItemDataReplacementRowStatus status;
    private List<ReleaseItemDataReplacementChangeDTO> changes = new ArrayList<>();
    private Integer acceptedQty;
    private String reason;
}
