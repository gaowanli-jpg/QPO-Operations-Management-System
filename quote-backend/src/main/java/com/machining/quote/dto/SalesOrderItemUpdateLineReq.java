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
    public static final String FIELD_TAX_INCLUDED_UNIT_PRICE = "taxIncludedUnitPrice";
    public static final String FIELD_VERSION = "version";
    public static final String FIELD_MATERIAL_NAME = "materialName";
    public static final String FIELD_SURFACE_TREATMENT = "surfaceTreatment";
    public static final String FIELD_CUSTOMER_ORDER_NO = "customerOrderNo";
    public static final String FIELD_CUSTOMER_PROJECT_NO = "customerProjectNo";
    public static final String FIELD_CUSTOMER_ORDER_TITLE = "customerOrderTitle";
    public static final String FIELD_INTERNAL_PROJECT_NO = "internalProjectNo";
    public static final String FIELD_DESCRIPTION = "description";
    public static final String FIELD_STATUS = "status";
    public static final String FIELD_ORDER_TYPE = "orderType";
    public static final String FIELD_REMARK = "remark";

    private Long salesOrderItemId;
    private Integer qty;
    private LocalDate customerDeliveryDate;
    private LocalDate internalDeliveryDate;
    private BigDecimal untaxedUnitPrice;
    private BigDecimal taxIncludedUnitPrice;
    private String version;
    private String materialName;
    private String surfaceTreatment;
    private String customerOrderNo;
    private String customerProjectNo;
    private String customerOrderTitle;
    private String internalProjectNo;
    private String description;
    private String status;
    private String orderType;
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

    public BigDecimal getTaxIncludedUnitPrice() {
        return taxIncludedUnitPrice;
    }

    public void setTaxIncludedUnitPrice(BigDecimal taxIncludedUnitPrice) {
        this.taxIncludedUnitPrice = taxIncludedUnitPrice;
        providedFields.add(FIELD_TAX_INCLUDED_UNIT_PRICE);
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
        providedFields.add(FIELD_VERSION);
    }

    public String getMaterialName() {
        return materialName;
    }

    public void setMaterialName(String materialName) {
        this.materialName = materialName;
        providedFields.add(FIELD_MATERIAL_NAME);
    }

    public String getSurfaceTreatment() {
        return surfaceTreatment;
    }

    public void setSurfaceTreatment(String surfaceTreatment) {
        this.surfaceTreatment = surfaceTreatment;
        providedFields.add(FIELD_SURFACE_TREATMENT);
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

    public String getInternalProjectNo() {
        return internalProjectNo;
    }

    public void setInternalProjectNo(String internalProjectNo) {
        this.internalProjectNo = internalProjectNo;
        providedFields.add(FIELD_INTERNAL_PROJECT_NO);
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        providedFields.add(FIELD_DESCRIPTION);
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
        providedFields.add(FIELD_STATUS);
    }

    public String getOrderType() {
        return orderType;
    }

    public void setOrderType(String orderType) {
        this.orderType = orderType;
        providedFields.add(FIELD_ORDER_TYPE);
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
