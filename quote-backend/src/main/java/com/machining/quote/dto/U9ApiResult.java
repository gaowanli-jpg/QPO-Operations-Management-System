package com.machining.quote.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class U9ApiResult<T> {
    @JsonProperty("ResCode")
    private Integer ResCode;
    @JsonProperty("Success")
    private Boolean Success;
    @JsonProperty("ResMsg")
    private String ResMsg;
    @JsonProperty("Data")
    private List<T> Data;
    @JsonProperty("Exception")
    private String Exception;
}
