package com.machining.apssystem.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApsWorkItemProcessDTO {
    private Long id;

    @JsonProperty("work_item_id")
    private Long workItemId;

    @JsonProperty("sort_no")
    private Integer sortNo;

    @JsonProperty("process_name")
    private String processName;

    @JsonProperty("calc_type")
    private String calcType;

    @JsonProperty("source_type")
    private String sourceType;

    @JsonProperty("input_val")
    private BigDecimal inputVal;

    @JsonProperty("work_hours")
    private BigDecimal workHours;

    @JsonProperty("unit_rate")
    private BigDecimal unitRate;

    @JsonProperty("line_cost")
    private BigDecimal lineCost;

    @JsonProperty("raw_row")
    private String rawRow;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;
}
