package com.machining.quote.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SalesOrderItemUpdateReq {
    private List<SalesOrderItemUpdateLineReq> items = new ArrayList<>();
}
