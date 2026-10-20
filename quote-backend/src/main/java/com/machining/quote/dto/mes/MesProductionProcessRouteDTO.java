package com.machining.quote.dto.mes;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.math.BigDecimal;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MesProductionProcessRouteDTO {
    private String programNum;
    private String processName;
    private String processTypeCode;
    private BigDecimal workHour;
    private String processDescription;

    @JsonIgnore
    private String processCode;
    @JsonIgnore
    private String categoryName;
    @JsonIgnore
    private String detailName;
    @JsonIgnore
    private Double unitWorkHours;
    @JsonIgnore
    private Integer sortNo;

    public MesProductionProcessRouteDTO() {
    }

    public MesProductionProcessRouteDTO(
            String programNum,
            String processName,
            String processTypeCode,
            BigDecimal workHour,
            String processDescription) {
        this.programNum = programNum;
        this.processName = processName;
        this.processTypeCode = processTypeCode;
        this.workHour = workHour;
        this.processDescription = processDescription;
    }

    public void setProcessCode(String processCode) {
        this.processCode = processCode;
        if (isBlank(this.processTypeCode)) {
            this.processTypeCode = trimToNull(processCode);
        }
    }

    public void setDetailName(String detailName) {
        this.detailName = detailName;
        if (isBlank(this.processName)) {
            this.processName = trimToNull(detailName);
        }
    }

    public void setUnitWorkHours(Double unitWorkHours) {
        this.unitWorkHours = unitWorkHours;
        if (this.workHour == null && unitWorkHours != null) {
            this.workHour = BigDecimal.valueOf(unitWorkHours);
        }
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
        if (isBlank(this.programNum) && sortNo != null) {
            this.programNum = String.valueOf(sortNo);
        }
    }

    private String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
