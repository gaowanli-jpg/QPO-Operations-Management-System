package com.machining.quote.dto.mes;

import lombok.Data;

@Data
public class MesProductionProcessRouteDTO {
    private String processCode;
    private String categoryName;
    private String detailName;
    private Double unitWorkHours;
    private Integer sortNo;
}
