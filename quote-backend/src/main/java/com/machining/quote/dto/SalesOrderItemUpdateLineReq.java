package com.machining.quote.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class SalesOrderItemUpdateLineReq {
    public static final String FIELD_QTY = "qty";
    public static final String FIELD_CUSTOMER_DELIVERY_DATE = "customerDeliveryDate";
    public static final String FIELD_INTERNAL_DELIVERY_DATE = "internalDeliveryDate";
    public static final String FIELD_UNTAXED_UNIT_PRICE = "untaxedUnitPrice";
    public static final String FIELD_CUSTOMER_ORDER_NO = "customerOrderNo";
    public static final String FIELD_CUSTOMER_PROJECT_NO = "customerProjectNo";
    public static final String FIELD_INTERNAL_PROJECT_NO = "internalProjectNo";
    public static final String FIELD_REMARK = "remark";

    private Long salesOrderItemId;
    private Integer qty;
    private LocalDate customerDeliveryDate;
    private LocalDate internalDeliveryDate;
    private BigDecimal untaxedUnitPrice;
    private String customerOrderNo;
    private String customerProjectNo;
    private String internalProjectNo;
    private String remark;
    private final Set<String> providedFields = new LinkedHashSet<>();

    public Long getSalesOrderItemId() {
        return salesOrderItemId;
    }

    public void setSalesOrderItemId(Long salesOrderItemId) {
        this.salesOrderItemId = salesOrderItemId;
    }

    public Integer getQty() {
        return qty;
    }

    public void setQty(Integer qty) {
        this.qty = qty;
        providedFields.add(FIELD_QTY);
    }

    public LocalDate getCustomerDeliveryDate() {
        return customerDeliveryDate;
    }

    public void setCustomerDeliveryDate(LocalDate customerDeliveryDate) {
        this.customerDeliveryDate = customerDeliveryDate;
        providedFields.add(FIELD_CUSTOMER_DELIVERY_DATE);
    }

    public LocalDate getInternalDeliveryDate() {
        return internalDeliveryDate;
    }

    public void setInternalDeliveryDate(LocalDate internalDeliveryDate) {
        this.internalDeliveryDate = internalDeliveryDate;
        providedFields.add(FIELD_INTERNAL_DELIVERY_DATE);
    }

    public BigDecimal getUntaxedUnitPrice() {
        return untaxedUnitPrice;
    }

    public void setUntaxedUnitPrice(BigDecimal untaxedUnitPrice) {
        this.untaxedUnitPrice = untaxedUnitPrice;
        providedFields.add(FIELD_UNTAXED_UNIT_PRICE);
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

    public String getInternalProjectNo() {
        return internalProjectNo;
    }

    public void setInternalProjectNo(String internalProjectNo) {
        this.internalProjectNo = internalProjectNo;
        providedFields.add(FIELD_INTERNAL_PROJECT_NO);
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
        providedFields.add(FIELD_REMARK);
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
