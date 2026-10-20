package com.machining.quote.dto;

import lombok.Data;

@Data
public class ReleaseItemDataReplacementAcceptedQtyDTO {
    private String releaseItemKey;
    private Integer itemId;
    private Integer acceptedQty;
}
