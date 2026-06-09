package com.machining.apssystem.dto;

import lombok.Data;

import java.util.List;

@Data
public class ApsPartInventoryPageDTO {
    private List<ApsPartInventoryDTO> records;
    private long total;
    private int page;
    private int pageSize;
}
