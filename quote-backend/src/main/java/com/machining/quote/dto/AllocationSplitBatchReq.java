package com.machining.quote.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AllocationSplitBatchReq {
    private List<AllocationSplitQtyReq> items = new ArrayList<>();
}
