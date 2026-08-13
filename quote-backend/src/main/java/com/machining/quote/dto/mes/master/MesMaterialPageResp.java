package com.machining.quote.dto.mes.master;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@lombok.Data
public class MesMaterialPageResp {
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private static final List<String> LIST_KEYS = List.of("list", "records", "rows", "items");

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

    public Data toData() {
        if (data == null) {
            return new Data();
        }
        if (data instanceof List<?> list) {
            Data result = new Data();
            result.setTotal((long) list.size());
            result.setList(convertRows(list));
            return result;
        }
        if (data instanceof Map<?, ?> map) {
            Data result = MAPPER.convertValue(map, Data.class);
            if (result.getList() == null || result.getList().isEmpty()) {
                Object list = firstList(map);
                if (list instanceof List<?> rawRows) {
                    result.setList(convertRows(rawRows));
                }
            }
            if (result.getTotal() == null && result.getList() != null) {
                result.setTotal((long) result.getList().size());
            }
            return result;
        }
        return MAPPER.convertValue(data, Data.class);
    }

    private Object firstList(Map<?, ?> map) {
        for (String key : LIST_KEYS) {
            Object value = map.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private List<Row> convertRows(List<?> values) {
        List<Row> rows = new ArrayList<>();
        for (Object value : values) {
            rows.add(MAPPER.convertValue(value, Row.class));
        }
        return rows;
    }

    @lombok.Data
    public static class Data {
        @JsonAlias({"Total"})
        private Long total = 0L;
        @JsonAlias({"records", "rows", "items"})
        private List<Row> list = new ArrayList<>();
    }

    @lombok.Data
    public static class Row {
        private String id;
        private String code;
        private String name;
        private String materialType;
        private String purchaseType;
        private String productionType;
        private String standard;
        private String docNum;
        private BigDecimal weight;
        private BigDecimal density;
        private String status;
        private String categoryId;
        private String unitId;
        private String attribute;
        private String version;
        private Object drawingUrls;
        private String remark;
        private String createTime;
        private String updateTime;
    }
}
