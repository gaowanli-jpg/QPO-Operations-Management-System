package com.machining.quote.dto;

import lombok.Data;

import java.util.List;

@Data
public class ProcessArrangementItemBatchReq {
    private Long parentNodeId;
    private List<ProcessArrangementItemReq> items;
}
