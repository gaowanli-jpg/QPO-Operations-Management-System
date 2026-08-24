package com.machining.quote.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SalesOrderChangeItemUpdateReq {
    private String reason;
    private List<SalesOrderU9ItemUpdateLineReq> items = new ArrayList<>();
}
