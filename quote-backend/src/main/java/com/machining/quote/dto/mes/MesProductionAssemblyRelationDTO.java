package com.machining.quote.dto.mes;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MesProductionAssemblyRelationDTO {
    private Long parentItemId;
    private Long childItemId;
    private String parentPartNo;
    private String parentVersion;
    private String childPartNo;
    private String childVersion;
    private BigDecimal qtyPerParent;
    private Integer sortNo;
    private Integer sourceParentItemId;
    private Integer sourceChildItemId;
}
