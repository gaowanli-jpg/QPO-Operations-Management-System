package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DataCenterConfigStage2Test {
    private static final Path SCHEMA_BASELINE = Path.of(
            "src", "main", "resources", "db", "migration", "V1__qpo_baseline_schema.sql"
    );
    private static final Path SEED_BASELINE = Path.of(
            "src", "main", "resources", "db", "migration", "V2__qpo_baseline_seed_data.sql"
    );
    private static final Path WMS_RESERVATION_MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration", "V10__wms_batch_reservation_contract.sql"
    );
    private static final Path PERMISSION_SERVICE = Path.of(
            "src", "main", "java", "com", "machining", "quote", "service", "PermissionService.java"
    );
    private static final Path FRONTEND_OUTBOUND_PANEL = Path.of(
            "..", "quote-frontend", "src", "components", "system-config", "SystemIntegrationConfigPanel.vue"
    );
    private static final Path PERMISSION_DOC = Path.of(
            "..", "docs", "QPO权限用户体系", "02-权限点清单与现有角色映射.md"
    );
    private static final Path ADMIN_DOC = Path.of(
            "..", "docs", "QPO权限用户体系", "03-权限体系部署与管理员使用说明.md"
    );

    @Test
    void migrationCreatesDataCenterConnectionAndEndpointTables() throws IOException {
        String schema = read(SCHEMA_BASELINE);

        assertThat(schema)
                .contains(
                        "CREATE TABLE system_data_center_config",
                        "CREATE TABLE system_data_center_endpoint_config",
                        "client_secret_cipher text",
                        "CONSTRAINT ck_system_data_center_grant_type CHECK",
                        "CONSTRAINT uk_system_data_center_endpoint_code UNIQUE (endpoint_code)",
                        "CREATE UNIQUE INDEX uk_system_data_center_active",
                        "CREATE INDEX idx_system_data_center_endpoint_target"
                )
                .doesNotContain(
                        "client_secret=X",
                        "access_token=ey"
                );
    }

    @Test
    void migrationSeedsDefaultDataCenterEndpointCodes() throws IOException {
        String seed = read(SEED_BASELINE);

        assertThat(seed)
                .contains(
                        "'MES_MATERIAL_RESOLVE'",
                        "'WMS_INVENTORY_ALLOCATE'",
                        "'U9_SALES_ORDER_SYNC'",
                        "'U9_COMMON_ENTITY_QUERY'",
                        "'MES_PRODUCTION_DISPATCH'",
                        "'MES_MATERIAL_V1'",
                        "'WMS_ALLOCATION_V1'",
                        "'U9_SALES_ORDER_V1'",
                        "'U9_COMMON_ENTITY_QUERY_V1'",
                        "'MES_PRODUCTION_DISPATCH_V1'"
                )
                .doesNotContain("access_token=ey", "client_secret=X");
        assertThat(seed).doesNotContain("ON CONFLICT (endpoint_code) DO UPDATE");
    }

    @Test
    void wmsReservationMigrationMovesInventoryEndpointToSaleLadingReserve() throws IOException {
        String migration = read(WMS_RESERVATION_MIGRATION);

        assertThat(migration)
                .contains(
                        "'WMS_INVENTORY_ALLOCATE'",
                        "'/api/v1/wms/admin-api/wms/datacenter/sale-lading/reserve'",
                        "'WMS_SALE_LADING_RESERVE_V1'",
                        "'WMS_SALE_LADING_RESERVE_RESPONSE_V1'",
                        "timeout_ms = GREATEST(COALESCE(system_data_center_endpoint_config.timeout_ms, 0), 60000)"
                )
                .doesNotContain("/api/wms/inventory/allocate");
    }

    @Test
    void dataCenterEntitiesMappersAndDtosExist() throws IOException {
        String configEntity = read(Path.of(
                "src", "main", "java", "com", "machining", "quote", "entity", "SystemDataCenterConfig.java"
        ));
        String endpointEntity = read(Path.of(
                "src", "main", "java", "com", "machining", "quote", "entity", "SystemDataCenterEndpointConfig.java"
        ));
        String configMapper = read(Path.of(
                "src", "main", "java", "com", "machining", "quote", "mapper", "SystemDataCenterConfigMapper.java"
        ));
        String endpointMapper = read(Path.of(
                "src", "main", "java", "com", "machining", "quote", "mapper", "SystemDataCenterEndpointConfigMapper.java"
        ));
        String configDto = read(Path.of(
                "src", "main", "java", "com", "machining", "quote", "dto", "SystemDataCenterConfigDTO.java"
        ));
        String endpointDto = read(Path.of(
                "src", "main", "java", "com", "machining", "quote", "dto", "SystemDataCenterEndpointConfigDTO.java"
        ));
        String configSaveReq = read(Path.of(
                "src", "main", "java", "com", "machining", "quote", "dto", "SystemDataCenterConfigSaveReq.java"
        ));
        String endpointSaveReq = read(Path.of(
                "src", "main", "java", "com", "machining", "quote", "dto", "SystemDataCenterEndpointConfigSaveReq.java"
        ));

        assertThat(configEntity)
                .contains("@TableName(\"system_data_center_config\")")
                .contains("private String tokenUrl;")
                .contains("private String clientSecretCipher;")
                .contains("private Integer tokenExpireSkewSeconds;");
        assertThat(endpointEntity)
                .contains("@TableName(value = \"system_data_center_endpoint_config\", autoResultMap = true)")
                .contains("private String endpointCode;")
                .contains("private String targetSystemCode;")
                .contains("private String requestAdapter;")
                .contains("private Object requestSchemaJson;");
        assertThat(configMapper).contains("extends BaseMapper<SystemDataCenterConfig>");
        assertThat(endpointMapper).contains("extends BaseMapper<SystemDataCenterEndpointConfig>");
        assertThat(configDto)
                .contains("private String clientSecret;")
                .contains("private Boolean clientSecretConfigured;");
        assertThat(endpointDto)
                .contains("private String endpointCode;")
                .contains("private Object responseSchemaJson;");
        assertThat(configSaveReq).contains("private String clientSecret;");
        assertThat(endpointSaveReq).contains("private String endpointCode;");
    }

    @Test
    void permissionsAreReusedForOutboundDataCenterConfig() throws IOException {
        String permissionService = read(PERMISSION_SERVICE);
        String permissionDoc = read(PERMISSION_DOC);
        String adminDoc = read(ADMIN_DOC);

        assertThat(permissionService)
                .contains(
                        "\"system:config:integration:update\", \"维护出站接口/数据中心配置\"",
                        "\"system:config:integration:test\", \"测试出站接口/数据中心配置\"",
                        "\"system:config:integration:activate\", \"激活出站接口/数据中心配置\""
                )
                .doesNotContain("system:config:data-center");
        assertThat(permissionDoc)
                .contains(
                        "`system:config:integration:update`",
                        "出站接口/数据中心配置",
                        "数据中心连接配置",
                        "功能接口配置"
                )
                .doesNotContain("common:data-center");
        assertThat(adminDoc).contains("出站接口/数据中心配置");
    }

    @Test
    void outboundPageCopyShowsDataCenterConnectionAndEndpointConfig() throws IOException {
        String panel = read(FRONTEND_OUTBOUND_PANEL);

        assertThat(panel)
                .contains(
                        "数据中心连接配置",
                        "功能接口配置",
                        "MES_MATERIAL_RESOLVE",
                        "WMS_INVENTORY_ALLOCATE",
                        "U9_SALES_ORDER_SYNC",
                        "MES_PRODUCTION_DISPATCH",
                        "v-permission=\"'system:config:integration:update'\""
                )
                .doesNotContain(
                        "当前版本未接入",
                        "新增 U9 配置",
                        "新增 MES 配置",
                        "新增 WMS 配置"
                );
    }

    private static String read(Path path) throws IOException {
        assertThat(path).exists();
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
