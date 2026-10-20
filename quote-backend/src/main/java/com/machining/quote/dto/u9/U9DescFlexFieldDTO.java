package com.machining.quote.dto.u9;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class U9DescFlexFieldDTO {
    @JsonProperty("PrivateDescSeg1")
    private String PrivateDescSeg1;
    @JsonProperty("PrivateDescSeg2")
    private String PrivateDescSeg2;
    @JsonProperty("PrivateDescSeg3")
    private String PrivateDescSeg3;
    @JsonProperty("PrivateDescSeg4")
    private String PrivateDescSeg4;
    @JsonProperty("PrivateDescSeg5")
    private String PrivateDescSeg5;
    @JsonProperty("PrivateDescSeg9")
    private String PrivateDescSeg9;
    @JsonProperty("PubDescSeg2")
    private String PubDescSeg2;
}
