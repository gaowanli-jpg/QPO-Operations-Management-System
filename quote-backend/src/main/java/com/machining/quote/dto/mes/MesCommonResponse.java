package com.machining.quote.dto.mes;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class MesCommonResponse<T> {
    private Object code;
    @JsonAlias("msg")
    private String message;
    private T data;

    @JsonIgnore
    public boolean isBusinessSuccess() {
        if (code instanceof Number number) {
            return number.intValue() == 0;
        }
        return code != null && "0".equals(String.valueOf(code).trim());
    }
}
