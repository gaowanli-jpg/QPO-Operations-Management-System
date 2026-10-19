package com.machining.quote.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.machining.quote.entity.OrderDeliveryTicketAllocation;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.LinkedHashSet;
import java.util.Set;

@Component
public class SalesOrderChangeNoticePayloadFactory {
    @Autowired
    private ObjectMapper objectMapper;

    public String snapshotJson(SalesOrderHeader order,
                               SalesOrderItem item,
                               SalesOrderChangeNoticeTargetResolver.Target target,
                               String changeType,
                               String reason,
                               Set<String> changedFields) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("salesOrderNo", firstNonBlank(order.getSalesOrderNo(), order.getDocumentNo()));
        payload.put("saleOrderItemNo", item.getLineNo() == null ? null : String.valueOf(item.getLineNo()));
        payload.put("allocationId", target.allocation().getId());
        payload.put("targetType", target.targetType());
        payload.put("targetExternalId", target.targetExternalId());
        payload.put("sourceBizId", target.sourceBizId());
        payload.put("changeType", changeType);
        payload.put("changedFields", changedFields == null ? List.of() : new ArrayList<>(changedFields));
        payload.put("reason", reason);
        payload.put("item", currentItem(item));
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("销售变更通知快照生成失败", e);
        }
    }

    public Map<String, Object> currentItem(SalesOrderItem item) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("salesOrderDate", item.getSalesOrderDate());
        data.put("partNo", item.getPartNo());
        data.put("version", item.getVersion());
        data.put("materialNo", item.getMaterialNo());
        data.put("qty", item.getQty());
        data.put("orderType", item.getOrderType());
        data.put("itemType", item.getItemType());
        data.put("customerOrderNo", item.getCustomerOrderNo());
        data.put("customerProjectNo", item.getCustomerProjectNo());
        data.put("customerOrderTitle", item.getCustomerOrderTitle());
        data.put("internalProjectNo", item.getInternalProjectNo());
        data.put("customerDeliveryDate", item.getCustomerDeliveryDate());
        data.put("internalDeliveryDate", item.getInternalDeliveryDate());
        data.put("materialName", item.getMaterialName());
        data.put("engineeringMaterialCode", item.getEngineeringMaterialCode());
        data.put("engineeringMaterialName", item.getEngineeringMaterialName());
        data.put("engineeringMaterialTypeCode", item.getEngineeringMaterialTypeCode());
        data.put("engineeringMaterialTypeName", item.getEngineeringMaterialTypeName());
        data.put("engineeringMaterialCategory", item.getEngineeringMaterialCategory());
        data.put("materialShape", item.getMaterialShape());
        data.put("surfaceTreatment", item.getSurfaceTreatment());
        data.put("processRoute", item.getProcessRoute());
        data.put("materialData", item.getMaterialData());
        data.put("description", item.getDescription());
        data.put("remark", item.getRemark());
        data.put("materialGrossWeight", item.getMaterialGrossWeight());
        data.put("partNetWeight", item.getPartNetWeight());
        data.put("manufacturingProcessType", item.getManufacturingProcessType());
        data.put("reservedField1", item.getReservedField1());
        data.put("reservedField2", item.getReservedField2());
        data.put("reservedField3", item.getReservedField3());
        return data;
    }

    public Set<String> changedFields(Map<String, Object> before, Map<String, Object> after) {
        if (before == null || after == null) return Set.of();
        Set<String> changed = new LinkedHashSet<>();
        for (Map.Entry<String, Object> entry : after.entrySet()) {
            if (!Objects.deepEquals(before.get(entry.getKey()), entry.getValue())) {
                changed.add(entry.getKey());
            }
        }
        return nonPriceFields(changed);
    }

    public Set<String> nonPriceFields(Set<String> changedFields) {
        if (changedFields == null) return Set.of();
        return changedFields.stream()
                .filter(field -> !"untaxedUnitPrice".equals(field))
                .filter(field -> !"taxIncludedUnitPrice".equals(field))
                .filter(field -> !field.toLowerCase().contains("price"))
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.trim().isEmpty()) return value.trim();
        return null;
    }
}
