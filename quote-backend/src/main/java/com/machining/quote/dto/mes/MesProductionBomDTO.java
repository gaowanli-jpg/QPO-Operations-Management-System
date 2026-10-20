package com.machining.quote.dto.mes;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.math.BigDecimal;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MesProductionBomDTO {
    private String itemCode;
    private BigDecimal quantity;

    public MesProductionBomDTO() {
    }

    public MesProductionBomDTO(String itemCode, BigDecimal quantity) {
        this.itemCode = itemCode;
        this.quantity = quantity;
    }
}
