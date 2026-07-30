package com.machining.quote.dto;

import lombok.Data;

@Data
public class ReleaseItemSource {
    private ReleaseSourceType sourceType;
    private Long sourceOrderId;
    private Long sourceItemId;
}
