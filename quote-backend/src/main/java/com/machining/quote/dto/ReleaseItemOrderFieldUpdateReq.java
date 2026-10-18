package com.machining.quote.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ReleaseItemOrderFieldUpdateReq {
    private Integer rfqId;
    private LocalDate salesOrderDate;
    private String customerOrderNo;
    private String customerProjectNo;
    private String customerOrderTitle;
    private String orderType;
    private String internalProjectNo;
    private String manufacturingProcessType;
    private String reservedField1;
    private String reservedField2;
    private String reservedField3;

    public String getManufacturingProcessType() {
        return manufacturingProcessType;
    }

    public void setManufacturingProcessType(String manufacturingProcessType) {
        this.manufacturingProcessType = manufacturingProcessType;
    }

    public String getReservedField1() {
        return reservedField1;
    }

    public void setReservedField1(String reservedField1) {
        this.reservedField1 = reservedField1;
    }

    public String getReservedField2() {
        return reservedField2;
    }

    public void setReservedField2(String reservedField2) {
        this.reservedField2 = reservedField2;
    }

    public String getReservedField3() {
        return reservedField3;
    }

    public void setReservedField3(String reservedField3) {
        this.reservedField3 = reservedField3;
    }
}
