package com.machining.quote.dto.aps;

import lombok.Data;

import java.util.List;

@Data
public class ApsDeliveryItemBatchDTO {
    private boolean success = true;
    private List<ApsDeliveryItemDTO> records;
    private int count;
    private Integer nextAfterTicketId;
    private boolean hasMore;
}
