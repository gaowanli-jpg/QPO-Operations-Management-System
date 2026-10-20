package com.machining.quote.dto.aps;

import lombok.Data;

@Data
public class ApsProcessDTO {
    private Integer sourceId;
    private String processName;
    private String processType;
    private String calcType;
    private Integer sortNo;
}
