package com.machining.quote.dto.mes.master;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class MesProcessTypeListResp {
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private static final List<String> ROW_KEYS = List.of("list", "records", "rows", "items", "data");

    @JsonAlias({"resCode", "ResCode", "Code"})
    private Object code;
    @JsonAlias({"Success"})
    private Boolean success;
    @JsonAlias({"msg", "message", "resMsg", "ResMsg"})
    private String message;
    @JsonAlias({"Data"})
    private Object data;
    @JsonAlias({"Exception"})
    private String exception;

    public List<Row> rows() {
        Object source = rowSource(data);
        if (source == null) {
            return List.of();
        }
        if (source instanceof List<?> list) {
            List<Row> rows = new ArrayList<>();
            for (Object item : list) {
                rows.add(MAPPER.convertValue(item, Row.class));
            }
            return rows;
        }
        return List.of(MAPPER.convertValue(source, Row.class));
    }

    private Object rowSource(Object value) {
        if (value instanceof Map<?, ?> map) {
            for (String key : ROW_KEYS) {
                Object nested = map.get(key);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return value;
    }

    @Data
    public static class Row {
        private String id;
        private String code;
        private String name;
        private BigDecimal hourPrice;
        private String deptId;
        private String deptName;
        private String status;
        private String remark;
        private String createTime;
        private String updateTime;
    }
}
