package com.machining.quote.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class U9SalesOrderLineDTO {
    @JsonProperty("DocLineNo")
    private Integer DocLineNo;
    @JsonProperty("CustomerItemNo")
    private String CustomerItemNo;
    @JsonProperty("CustomerItemName")
    private String CustomerItemName;
    @JsonProperty("ItemCode")
    private String ItemCode;
    @JsonProperty("ItemVersion")
    private String ItemVersion;
    @JsonProperty("FreeType")
    private Integer FreeType;
    @JsonProperty("FreeReason")
    private Integer FreeReason;
    @JsonProperty("Project")
    private String Project;
    @JsonProperty("Task")
    private String Task;
    @JsonProperty("OrderByQtyTU")
    private BigDecimal OrderByQtyTU;
    @JsonProperty("OrderByQtyPU")
    private BigDecimal OrderByQtyPU;
    @JsonProperty("TU")
    private String TU;
    @JsonProperty("PU")
    private String PU;
    @JsonProperty("TBUToPBURate")
    private BigDecimal TBUToPBURate;
    @JsonProperty("OrderPriceTC")
    private BigDecimal OrderPriceTC;
    @JsonProperty("FinallyPriceTC")
    private BigDecimal FinallyPriceTC;
    @JsonProperty("TaxSchedule")
    private String TaxSchedule;
    @JsonProperty("PriceSource")
    private Integer PriceSource;
    @JsonProperty("ShipTogetherFlag")
    private Integer ShipTogetherFlag;
    @JsonProperty("ShipToSite")
    private String ShipToSite;
    @JsonProperty("SOShiplineDTOList")
    private List<U9SalesOrderShipLineDTO> SOShiplineDTOList = new ArrayList<>();
}
