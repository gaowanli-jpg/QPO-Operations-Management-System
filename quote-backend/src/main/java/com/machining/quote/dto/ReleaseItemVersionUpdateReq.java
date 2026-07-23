package com.machining.quote.dto;

import lombok.Data;

@Data
public class ReleaseItemVersionUpdateReq {
    private Integer rfqId;
    private String version;
}
