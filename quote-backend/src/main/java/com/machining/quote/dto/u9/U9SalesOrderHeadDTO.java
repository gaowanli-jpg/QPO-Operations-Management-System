package com.machining.quote.dto.u9;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class U9SalesOrderHeadDTO {
    @JsonProperty("OtherID")
    private String OtherID;
    @JsonProperty("DocNo")
    private String DocNo;
    @JsonProperty("BusinessDate")
    private String BusinessDate;
    @JsonProperty("OrderBy")
    private String OrderBy;
    @JsonProperty("Seller")
    private String Seller;
    @JsonProperty("SaleDepartment")
    private String SaleDepartment;
    @JsonProperty("DocumentType")
    private String DocumentType;
    @JsonProperty("IsPriceIncludeTax")
    private Boolean IsPriceIncludeTax;
    @JsonProperty("SOLineDTOList")
    private List<U9SalesOrderLineDTO> SOLineDTOList = new ArrayList<>();
}
