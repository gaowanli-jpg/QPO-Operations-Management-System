package com.machining.apssystem.dto;

import lombok.Data;

@Data
public class ErpProcessDTO {
    private Integer sourceId;
    private String processName;
    private String processType;
    private String calcType;
    private Integer sortNo;
}
