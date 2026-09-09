package com.machining.quote.dto.mes;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MesProductionDispatchReq {
    private String bizId;
    private String customerCode;
    private String customerName;
    private String internalProjectNo;
    private List<Item> items;

    @JsonIgnore
    private Integer allocationId;
    @JsonIgnore
    private Integer ticketId;
    @JsonIgnore
    private String sourceType;
    @JsonIgnore
    private Long sourceOrderId;
    @JsonIgnore
    private Long sourceItemId;
    @JsonIgnore
    private String partNo;
    @JsonIgnore
    private String version;
    @JsonIgnore
    private String itemType;
    @JsonIgnore
    private String allocationType;
    @JsonIgnore
    private Integer ticketQty;
    @JsonIgnore
    private String orderType;
    @JsonIgnore
    private String customerOrderNo;
    @JsonIgnore
    private String customerProjectNo;
    @JsonIgnore
    private String materialName;
    @JsonIgnore
    private String engineeringMaterialCode;
    @JsonIgnore
    private String engineeringMaterialName;
    @JsonIgnore
    private String engineeringMaterialTypeCode;
    @JsonIgnore
    private String engineeringMaterialTypeName;
    @JsonIgnore
    private String engineeringMaterialCategory;
    @JsonIgnore
    private String materialShape;
    @JsonIgnore
    private String surfaceTreatment;
    @JsonIgnore
    private String description;
    @JsonIgnore
    private Double materialGrossWeight;
    @JsonIgnore
    private Double partNetWeight;
    @JsonIgnore
    private List<MesProductionAssemblyRelationDTO> assemblyRelations;

    @JsonIgnore
    public String getRequestId() {
        return bizId;
    }

    @JsonIgnore
    public void setRequestId(String requestId) {
        this.bizId = requestId;
    }

    @JsonIgnore
    public String getSourceOrderNo() {
        Item item = firstItem();
        return item == null ? null : item.getSaleOrderNo();
    }

    @JsonIgnore
    public void setSourceOrderNo(String sourceOrderNo) {
        writableFirstItem().setSaleOrderNo(sourceOrderNo);
    }

    @JsonIgnore
    public void setSourceItemId(Long sourceItemId) {
        this.sourceItemId = sourceItemId;
    }

    @JsonIgnore
    public String getSaleOrderItemNo() {
        Item item = firstItem();
        return item == null ? null : item.getSaleOrderItemNo();
    }

    @JsonIgnore
    public void setSaleOrderItemNo(String saleOrderItemNo) {
        writableFirstItem().setSaleOrderItemNo(saleOrderItemNo);
    }

    @JsonIgnore
    public String getPartNo() {
        return partNo;
    }

    @JsonIgnore
    public void setPartNo(String partNo) {
        this.partNo = partNo;
    }

    @JsonIgnore
    public String getVersion() {
        return version;
    }

    @JsonIgnore
    public void setVersion(String version) {
        this.version = version;
    }

    @JsonIgnore
    public String getMaterialNo() {
        Item item = firstItem();
        return item == null ? null : item.getProductCode();
    }

    @JsonIgnore
    public void setMaterialNo(String materialNo) {
        writableFirstItem().setProductCode(materialNo);
    }

    @JsonIgnore
    public Integer getProductionQty() {
        Item item = firstItem();
        return item == null ? null : item.getDemandQty();
    }

    @JsonIgnore
    public void setProductionQty(Integer productionQty) {
        Item item = writableFirstItem();
        item.setDemandQty(productionQty);
        item.setMakeQty(productionQty);
        if (item.getBuyQty() == null) {
            item.setBuyQty(0);
        }
        if (item.getStockQty() == null) {
            item.setStockQty(0);
        }
    }

    @JsonIgnore
    public String getSurfaceTreatment() {
        return surfaceTreatment;
    }

    @JsonIgnore
    public void setSurfaceTreatment(String surfaceTreatment) {
        this.surfaceTreatment = surfaceTreatment;
    }

    @JsonIgnore
    public String getDescription() {
        return description;
    }

    @JsonIgnore
    public void setDescription(String description) {
        this.description = description;
    }

    @JsonIgnore
    public String getRemark() {
        Item item = firstItem();
        return item == null ? null : item.getRemark();
    }

    @JsonIgnore
    public void setRemark(String remark) {
        writableFirstItem().setRemark(remark);
    }

    @JsonIgnore
    public LocalDate getDeliveryDate() {
        Item item = firstItem();
        return item == null || item.getRequiredDate() == null ? null : item.getRequiredDate().toLocalDate();
    }

    @JsonIgnore
    public void setDeliveryDate(LocalDate deliveryDate) {
        if (deliveryDate != null) {
            writableFirstItem().setRequiredDate(deliveryDate.atTime(23, 59, 59));
        }
    }

    @JsonIgnore
    public List<MesProductionProcessRouteDTO> getProcessRoute() {
        Item item = firstItem();
        return item == null ? null : item.getProcessRoute();
    }

    @JsonIgnore
    public void setProcessRoute(List<MesProductionProcessRouteDTO> processRoute) {
        writableFirstItem().setProcessRoute(processRoute);
    }

    @JsonIgnore
    public List<MesProductionBomDTO> getBom() {
        Item item = firstItem();
        return item == null ? null : item.getBom();
    }

    @JsonIgnore
    public void setBom(List<MesProductionBomDTO> bom) {
        writableFirstItem().setBom(bom);
    }

    private Item firstItem() {
        return items == null || items.isEmpty() ? null : items.get(0);
    }

    private Item writableFirstItem() {
        if (items == null || items.isEmpty()) {
            Item item = new Item();
            items = new java.util.ArrayList<>();
            items.add(item);
            return item;
        }
        return items.get(0);
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Item {
        private String saleOrderNo;
        private String saleOrderItemNo;
        private String productCode;
        private Integer demandQty;
        private Integer makeQty;
        private Integer buyQty;
        private Integer stockQty;
        private LocalDateTime requiredDate;
        private String remark;
        private String customerRequirements;
        private List<MesProductionBomDTO> bom;
        private List<MesProductionProcessRouteDTO> processRoute;
    }
}
