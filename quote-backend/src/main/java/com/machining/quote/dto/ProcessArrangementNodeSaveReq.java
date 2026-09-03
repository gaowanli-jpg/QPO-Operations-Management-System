package com.machining.quote.dto;

import lombok.Data;

@Data
public class ProcessArrangementNodeSaveReq {
    private Long parentId;
    private String nodeName;
    private Integer sortNo;
}
