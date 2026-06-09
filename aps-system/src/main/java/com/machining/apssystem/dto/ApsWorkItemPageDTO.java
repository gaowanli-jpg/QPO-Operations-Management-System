package com.machining.apssystem.dto;

import lombok.Data;

import java.util.List;

@Data
public class ApsWorkItemPageDTO {
    private List<ApsWorkItemDTO> records;
    private long total;
    private int page;
    private int pageSize;
}
