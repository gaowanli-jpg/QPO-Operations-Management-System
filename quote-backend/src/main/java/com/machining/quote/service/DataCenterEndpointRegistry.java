package com.machining.quote.service;

import com.machining.quote.config.DataCenterProperties;
import com.machining.quote.entity.SystemDataCenterEndpointConfig;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class DataCenterEndpointRegistry {
    public static final String MES_MATERIAL_RESOLVE = "MES_MATERIAL_RESOLVE";
    public static final String WMS_INVENTORY_ALLOCATE = "WMS_INVENTORY_ALLOCATE";
    public static final String U9_SALES_ORDER_SYNC = "U9_SALES_ORDER_SYNC";
    public static final String U9_COMMON_ENTITY_QUERY = "U9_COMMON_ENTITY_QUERY";
    public static final String MES_PRODUCTION_DISPATCH = "MES_PRODUCTION_DISPATCH";
    public static final String MES_PROCESS_TYPE_LIST = "MES_PROCESS_TYPE_LIST";
    public static final String MES_MATERIAL_PAGE = "MES_MATERIAL_PAGE";

    private final Map<String, EndpointDefinition> definitions = Map.of(
            MES_MATERIAL_RESOLVE,
            new EndpointDefinition(
                    MES_MATERIAL_RESOLVE,
                    "MES物料号获取",
                    "MES",
                    "POST",
                    "MES_MATERIAL_V1",
                    "MES_MATERIAL_RESPONSE_V1",
                    60000
            ),
            WMS_INVENTORY_ALLOCATE,
            new EndpointDefinition(
                    WMS_INVENTORY_ALLOCATE,
                    "WMS库存锁定",
                    "WMS",
                    "POST",
                    "WMS_SALE_LADING_RESERVE_V1",
                    "WMS_SALE_LADING_RESERVE_RESPONSE_V1",
                    60000
            ),
            U9_SALES_ORDER_SYNC,
            new EndpointDefinition(
                    U9_SALES_ORDER_SYNC,
                    "U9同步",
                    "U9",
                    "POST",
                    "U9_SALES_ORDER_V1",
                    "U9_SALES_ORDER_RESPONSE_V1",
                    60000
            ),
            U9_COMMON_ENTITY_QUERY,
            new EndpointDefinition(
                    U9_COMMON_ENTITY_QUERY,
                    "U9通用实体查询",
                    "U9",
                    "POST",
                    "U9_COMMON_ENTITY_QUERY_V1",
                    "U9_COMMON_ENTITY_RESPONSE_V1",
                    60000
            ),
            MES_PRODUCTION_DISPATCH,
            new EndpointDefinition(
                    MES_PRODUCTION_DISPATCH,
                    "下发MES生产",
                    "MES",
                    "POST",
                    "MES_PRODUCTION_DISPATCH_V1",
                    "MES_PRODUCTION_DISPATCH_RESPONSE_V1",
                    60000
            ),
            MES_PROCESS_TYPE_LIST,
            new EndpointDefinition(
                    MES_PROCESS_TYPE_LIST,
                    "MES工艺列表",
                    "MES",
                    "GET",
                    "MES_PROCESS_TYPE_LIST_V1",
                    "MES_PROCESS_TYPE_LIST_RESPONSE_V1",
                    60000
            ),
            MES_MATERIAL_PAGE,
            new EndpointDefinition(
                    MES_MATERIAL_PAGE,
                    "MES材料分页",
                    "MES",
                    "GET",
                    "MES_MATERIAL_PAGE_V1",
                    "MES_MATERIAL_PAGE_RESPONSE_V1",
                    60000
            )
    );

    public List<EndpointDefinition> definitions() {
        return List.of(
                definitions.get(MES_MATERIAL_RESOLVE),
                definitions.get(WMS_INVENTORY_ALLOCATE),
                definitions.get(U9_SALES_ORDER_SYNC),
                definitions.get(U9_COMMON_ENTITY_QUERY),
                definitions.get(MES_PRODUCTION_DISPATCH),
                definitions.get(MES_PROCESS_TYPE_LIST),
                definitions.get(MES_MATERIAL_PAGE)
        );
    }

    public EndpointDefinition requireDefinition(String endpointCode) {
        String normalized = normalize(endpointCode);
        EndpointDefinition definition = definitions.get(normalized);
        if (definition == null) {
            throw new IllegalArgumentException("Unsupported data center endpointCode=" + endpointCode);
        }
        return definition;
    }

    public void applyFixedDefinition(SystemDataCenterEndpointConfig endpoint) {
        EndpointDefinition definition = requireDefinition(endpoint.getEndpointCode());
        endpoint.setEndpointCode(definition.endpointCode());
        endpoint.setDisplayName(definition.displayName());
        endpoint.setTargetSystemCode(definition.targetSystemCode());
        endpoint.setRequestAdapter(definition.requestAdapter());
        endpoint.setResponseAdapter(definition.responseAdapter());
        applyMinimumTimeout(endpoint, definition);
    }

    public SystemDataCenterEndpointConfig newEndpoint(String endpointCode) {
        EndpointDefinition definition = requireDefinition(endpointCode);
        SystemDataCenterEndpointConfig endpoint = new SystemDataCenterEndpointConfig();
        endpoint.setEndpointCode(definition.endpointCode());
        endpoint.setDisplayName(definition.displayName());
        endpoint.setTargetSystemCode(definition.targetSystemCode());
        endpoint.setMethod(definition.defaultMethod());
        endpoint.setEnabled(true);
        endpoint.setRequestAdapter(definition.requestAdapter());
        endpoint.setResponseAdapter(definition.responseAdapter());
        applyMinimumTimeout(endpoint, definition);
        endpoint.setRequestSchemaJson(new LinkedHashMap<>());
        endpoint.setResponseSchemaJson(new LinkedHashMap<>());
        return endpoint;
    }

    public String fallbackPath(String endpointCode, DataCenterProperties properties) {
        if (properties == null) {
            return null;
        }
        return switch (requireDefinition(endpointCode).endpointCode()) {
            case MES_MATERIAL_RESOLVE -> trimToNull(properties.getMesMaterialPath());
            case WMS_INVENTORY_ALLOCATE -> trimToNull(properties.getWmsAllocationPath());
            case U9_SALES_ORDER_SYNC -> trimToNull(properties.getU9SalesOrderSyncPath());
            case U9_COMMON_ENTITY_QUERY -> trimToNull(properties.getU9CommonEntityQueryPath());
            case MES_PRODUCTION_DISPATCH -> trimToNull(properties.getMesProductionDispatchPath());
            case MES_PROCESS_TYPE_LIST -> trimToNull(properties.getMesProcessTypeListPath());
            case MES_MATERIAL_PAGE -> trimToNull(properties.getMesMaterialPagePath());
            default -> null;
        };
    }

    private void applyMinimumTimeout(SystemDataCenterEndpointConfig endpoint, EndpointDefinition definition) {
        Integer minimum = definition.minimumTimeoutMs();
        if (minimum != null && minimum > 0
                && (endpoint.getTimeoutMs() == null || endpoint.getTimeoutMs() < minimum)) {
            endpoint.setTimeoutMs(minimum);
        }
    }

    private String normalize(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Data center endpointCode cannot be blank");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record EndpointDefinition(
            String endpointCode,
            String displayName,
            String targetSystemCode,
            String defaultMethod,
            String requestAdapter,
            String responseAdapter,
            Integer minimumTimeoutMs) {
        public EndpointDefinition(
                String endpointCode,
                String displayName,
                String targetSystemCode,
                String defaultMethod,
                String requestAdapter,
                String responseAdapter) {
            this(endpointCode, displayName, targetSystemCode, defaultMethod, requestAdapter, responseAdapter, null);
        }
    }
}
