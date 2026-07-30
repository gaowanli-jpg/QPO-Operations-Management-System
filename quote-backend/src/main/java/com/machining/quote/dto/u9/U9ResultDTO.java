package com.machining.quote.dto.u9;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class U9ResultDTO {
    @JsonProperty("u9c_version")
    private String u9cVersion;
    @JsonProperty("m_isSucess")
    private Boolean mIsSucess;
    @JsonProperty("m_otherID")
    private String mOtherID;
    @JsonProperty("m_iD")
    private String mID;
    @JsonProperty("m_code")
    private String mCode;
    @JsonProperty("m_errorMsg")
    private String mErrorMsg;
    @JsonProperty("m_datas")
    private String mDatas;
}
