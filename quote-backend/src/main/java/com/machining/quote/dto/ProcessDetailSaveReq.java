package com.machining.quote.dto;

import lombok.Data;

@Data
public class ProcessDetailSaveReq {
    private Long categoryId;
    private String detailName;
    private String detailRemarks;
    private Boolean enabled;
    private Integer sortNo;
}
