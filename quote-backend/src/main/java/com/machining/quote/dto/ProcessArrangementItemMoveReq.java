package com.machining.quote.dto;

import lombok.Data;

@Data
public class ProcessArrangementItemMoveReq {
    private Long parentNodeId;
    private Integer sortNo;
}
