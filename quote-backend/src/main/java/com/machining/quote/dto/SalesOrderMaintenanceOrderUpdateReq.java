package com.machining.quote.dto;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class SalesOrderMaintenanceOrderUpdateReq {
    public static final String FIELD_DOCUMENT_TYPE_CODE = "documentTypeCode";
    public static final String FIELD_SALES_ORDER_DATE = "salesOrderDate";
    public static final String FIELD_CUSTOMER_ORDER_NO = "customerOrderNo";
    public static final String FIELD_CUSTOMER_PROJECT_NO = "customerProjectNo";
    public static final String FIELD_CUSTOMER_ORDER_TITLE = "customerOrderTitle";
    public static final String FIELD_ORDER_TYPE = "orderType";
    public static final String FIELD_INTERNAL_PROJECT_NO = "internalProjectNo";
    public static final String FIELD_MANUFACTURING_PROCESS_TYPE = "manufacturingProcessType";
    public static final String FIELD_RESERVED_FIELD_1 = "reservedField1";
    public static final String FIELD_RESERVED_FIELD_2 = "reservedField2";
    public static final String FIELD_RESERVED_FIELD_3 = "reservedField3";

    private String documentTypeCode;
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
    private final Set<String> providedFields = new LinkedHashSet<>();

    public String getDocumentTypeCode() {
        return documentTypeCode;
    }

    public void setDocumentTypeCode(String documentTypeCode) {
        this.documentTypeCode = documentTypeCode;
        providedFields.add(FIELD_DOCUMENT_TYPE_CODE);
    }

    public LocalDate getSalesOrderDate() {
        return salesOrderDate;
    }

    public void setSalesOrderDate(LocalDate salesOrderDate) {
        this.salesOrderDate = salesOrderDate;
        providedFields.add(FIELD_SALES_ORDER_DATE);
    }

    public String getCustomerOrderNo() {
        return customerOrderNo;
    }

    public void setCustomerOrderNo(String customerOrderNo) {
        this.customerOrderNo = customerOrderNo;
        providedFields.add(FIELD_CUSTOMER_ORDER_NO);
    }

    public String getCustomerProjectNo() {
        return customerProjectNo;
    }

    public void setCustomerProjectNo(String customerProjectNo) {
        this.customerProjectNo = customerProjectNo;
        providedFields.add(FIELD_CUSTOMER_PROJECT_NO);
    }

    public String getCustomerOrderTitle() {
        return customerOrderTitle;
    }

    public void setCustomerOrderTitle(String customerOrderTitle) {
        this.customerOrderTitle = customerOrderTitle;
        providedFields.add(FIELD_CUSTOMER_ORDER_TITLE);
    }

    public String getOrderType() {
        return orderType;
    }

    public void setOrderType(String orderType) {
        this.orderType = orderType;
        providedFields.add(FIELD_ORDER_TYPE);
    }

    public String getInternalProjectNo() {
        return internalProjectNo;
    }

    public void setInternalProjectNo(String internalProjectNo) {
        this.internalProjectNo = internalProjectNo;
        providedFields.add(FIELD_INTERNAL_PROJECT_NO);
    }

    public String getManufacturingProcessType() {
        return manufacturingProcessType;
    }

    public void setManufacturingProcessType(String manufacturingProcessType) {
        this.manufacturingProcessType = manufacturingProcessType;
        providedFields.add(FIELD_MANUFACTURING_PROCESS_TYPE);
    }

    public String getReservedField1() {
        return reservedField1;
    }

    public void setReservedField1(String reservedField1) {
        this.reservedField1 = reservedField1;
        providedFields.add(FIELD_RESERVED_FIELD_1);
    }

    public String getReservedField2() {
        return reservedField2;
    }

    public void setReservedField2(String reservedField2) {
        this.reservedField2 = reservedField2;
        providedFields.add(FIELD_RESERVED_FIELD_2);
    }

    public String getReservedField3() {
        return reservedField3;
    }

    public void setReservedField3(String reservedField3) {
        this.reservedField3 = reservedField3;
        providedFields.add(FIELD_RESERVED_FIELD_3);
    }

    public Set<String> getProvidedFields() {
        return Collections.unmodifiableSet(providedFields);
    }

    public boolean hasField(String field) {
        return providedFields.contains(field);
    }

    public boolean hasEditableFields() {
        return !providedFields.isEmpty();
    }
}
