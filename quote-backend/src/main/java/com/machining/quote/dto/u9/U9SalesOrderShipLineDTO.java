package com.machining.quote.dto.u9;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class U9SalesOrderShipLineDTO {
    @JsonProperty("DocSubLineNo")
    private Integer DocSubLineNo;
    @JsonProperty("ItemCode")
    private String ItemCode;
    @JsonProperty("RequireDate")
    private String RequireDate;
    @JsonProperty("DeliveryDate")
    private String DeliveryDate;
    @JsonProperty("ShipPlanQtyTU")
    private BigDecimal ShipPlanQtyTU;
    @JsonProperty("ShipPlanQtyPU")
    private BigDecimal ShipPlanQtyPU;
}
