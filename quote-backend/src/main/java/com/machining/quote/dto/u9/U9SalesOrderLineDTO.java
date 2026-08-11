package com.machining.quote.dto.u9;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class U9SalesOrderLineDTO {
    @JsonProperty("DocLineNo")
    private Integer DocLineNo;
    @JsonProperty("ItemCode")
    private String ItemCode;
    @JsonProperty("FreeType")
    private Integer FreeType;
    @JsonProperty("OrderByQtyTU")
    private BigDecimal OrderByQtyTU;
    @JsonProperty("OrderByQtyPU")
    private BigDecimal OrderByQtyPU;
    @JsonProperty("FinallyPriceTC")
    private BigDecimal FinallyPriceTC;
    @JsonProperty("ShipTogetherFlag")
    private Integer ShipTogetherFlag;
    @JsonProperty("DescFlexField")
    private U9DescFlexFieldDTO DescFlexField;
    @JsonProperty("SOShiplineDTOList")
    private List<U9SalesOrderShipLineDTO> SOShiplineDTOList = new ArrayList<>();
}
