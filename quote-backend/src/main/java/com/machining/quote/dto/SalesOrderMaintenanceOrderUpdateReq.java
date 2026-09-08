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

    private String documentTypeCode;
    private LocalDate salesOrderDate;
    private String customerOrderNo;
    private String customerProjectNo;
    private String customerOrderTitle;
    private String orderType;
    private String internalProjectNo;
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
