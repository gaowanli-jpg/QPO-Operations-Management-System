package com.machining.quote.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class OrderItemMaintenanceUpdateReq {
    private List<OrderItemMaintenanceLineReq> items = new ArrayList<>();
}
