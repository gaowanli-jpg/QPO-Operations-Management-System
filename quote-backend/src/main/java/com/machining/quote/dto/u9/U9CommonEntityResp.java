package com.machining.quote.dto.u9;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class U9CommonEntityResp {
    @JsonProperty("ResCode")
    private Integer resCode;
    @JsonProperty("Success")
    private Boolean success;
    @JsonProperty("ResMsg")
    private String resMsg;
    @JsonProperty("Data")
    private List<U9CommonEntityRowDTO> data = new ArrayList<>();
    @JsonProperty("Exception")
    private String exception;
}
