package com.machining.quote.dto.mes;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
    @JsonIgnore
    private String childMaterialNo;
    private BigDecimal qtyPerParent;
    private Integer sortNo;
    private Integer sourceParentItemId;
    private Integer sourceChildItemId;
}
