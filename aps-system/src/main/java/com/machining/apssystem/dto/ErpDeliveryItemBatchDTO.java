package com.machining.apssystem.dto;

import lombok.Data;

import java.util.List;

@Data
public class ErpDeliveryItemBatchDTO {
    private boolean success;
    private List<ErpDeliveryItemDTO> records;
    private int count;
    private Integer nextAfterTicketId;
    private boolean hasMore;
}
