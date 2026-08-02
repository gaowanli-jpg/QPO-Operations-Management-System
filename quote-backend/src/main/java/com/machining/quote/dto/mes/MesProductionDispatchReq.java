package com.machining.quote.dto.mes;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class MesProductionDispatchReq {
    private String requestId;
    private Integer allocationId;
    private Integer ticketId;
    private String sourceType;
    private Long sourceOrderId;
    private Long sourceItemId;
    private String sourceOrderNo;
    private String customerName;
    private String partNo;
    private String version;
    private String materialNo;
    private String itemType;
    private String allocationType;
    private Integer productionQty;
    private Integer ticketQty;
    private String orderType;
    private String internalProjectNo;
    private String customerOrderNo;
    private String customerProjectNo;
    private String materialName;
    private String engineeringMaterialName;
    private String surfaceTreatment;
    private String description;
    private String remark;
    private LocalDate deliveryDate;
    private Object processRoute;
    private Object materialData;
    private Double materialGrossWeight;
    private Double partNetWeight;
    private List<MesProductionAssemblyRelationDTO> assemblyRelations;
}
