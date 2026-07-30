package com.machining.quote.dto.u9;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
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
    @JsonProperty("TC")
    private String TC;
    @JsonProperty("AC")
    private String AC;
    @JsonProperty("ShipToSite")
    private String ShipToSite;
    @JsonProperty("BillToSite")
    private String BillToSite;
    @JsonProperty("PayerCustomer")
    private String PayerCustomer;
    @JsonProperty("PayerToSite")
    private String PayerToSite;
    @JsonProperty("IsPriceIncludeTax")
    private Boolean IsPriceIncludeTax;
    @JsonProperty("Memo")
    private String Memo;
    @JsonProperty("SOLineDTOList")
    private List<U9SalesOrderLineDTO> SOLineDTOList = new ArrayList<>();

    @JsonIgnore
    private String customerOrderNo;
}
