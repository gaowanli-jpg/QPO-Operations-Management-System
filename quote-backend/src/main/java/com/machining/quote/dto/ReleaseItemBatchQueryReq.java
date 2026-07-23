package com.machining.quote.dto;

import lombok.Data;

import java.util.List;

@Data
public class ReleaseItemBatchQueryReq {
    private List<Integer> rfqIds;
    private Integer page;
    private Integer pageSize;
    private String keyword;
    private String status;
    private Boolean remainingOnly;
}
