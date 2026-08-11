package com.machining.quote.dto.u9;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class U9CommonEntityQueryReq {
    @JsonProperty("PageSize")
    private Integer pageSize;
    @JsonProperty("PageIndex")
    private Integer pageIndex;
    @JsonProperty("Orders")
    private String orders;
    @JsonProperty("EntityFullName")
    private String entityFullName;
    @JsonProperty("ReturnFields")
    private List<String> returnFields = new ArrayList<>();
    @JsonProperty("Filters")
    private List<Filter> filters = new ArrayList<>();

    public static Builder builder() {
        return new Builder();
    }

    @Data
    public static class Filter {
        @JsonProperty("Field")
        private String field;
        @JsonProperty("Operator")
        private String operator;
        @JsonProperty("Value")
        private String value;
        @JsonProperty("Logic")
        private String logic;
    }

    public static class Builder {
        private final U9CommonEntityQueryReq req = new U9CommonEntityQueryReq();

        public Builder page(int pageIndex, int pageSize) {
            req.setPageIndex(pageIndex);
            req.setPageSize(pageSize);
            return this;
        }

        public Builder orders(String orders) {
            req.setOrders(orders);
            return this;
        }

        public Builder entityFullName(String entityFullName) {
            req.setEntityFullName(entityFullName);
            return this;
        }

        public Builder returnFields(List<String> returnFields) {
            req.setReturnFields(returnFields == null ? new ArrayList<>() : new ArrayList<>(returnFields));
            return this;
        }

        public Builder filter(String field, String operator, String value) {
            Filter filter = new Filter();
            filter.setField(field);
            filter.setOperator(operator);
            filter.setValue(value);
            filter.setLogic("And");
            req.getFilters().add(filter);
            return this;
        }

        public U9CommonEntityQueryReq build() {
            return req;
        }
    }
}
