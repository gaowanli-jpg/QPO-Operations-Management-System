package com.machining.quote.dto.u9;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class U9SalesOrderShipLineDTO {
    @JsonProperty("DocSubLineNo")
    private Integer DocSubLineNo;
    @JsonProperty("ItemCode")
    private String ItemCode;
    @JsonProperty("RequireDate")
    private String RequireDate;
    @JsonProperty("DeliveryDate")
    private String DeliveryDate;
    @JsonProperty("PlanDate")
    private String PlanDate;
    @JsonProperty("ShipPlanQtyTU")
    private BigDecimal ShipPlanQtyTU;
    @JsonProperty("ShipPlanQtyPU")
    private BigDecimal ShipPlanQtyPU;
    @JsonProperty("TU")
    private String TU;
    @JsonProperty("PU")
    private String PU;
    @JsonProperty("TUBaseToPUBaseRate")
    private BigDecimal TUBaseToPUBaseRate;
    @JsonProperty("ShipToSite")
    private String ShipToSite;
    @JsonProperty("Wh")
    private String Wh;
    @JsonProperty("Memo")
    private String Memo;
}
