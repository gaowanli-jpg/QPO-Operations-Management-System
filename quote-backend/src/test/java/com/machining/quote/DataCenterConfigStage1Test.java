package com.machining.quote;

import com.machining.quote.config.DataCenterProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DataCenterConfigStage1Test {
    private static final Path ENV_EXAMPLE = Path.of("..", "erp.env.example");
    private static final Path APPLICATION_YAML = Path.of("src", "main", "resources", "application.yaml");
    private static final Path SYSTEM_CONFIG_DOC = Path.of(
            "..", "docs", "QPO系统配置", "05-数据中心统一对接开发文档.md"
    );

    @Test
    void envExampleDocumentsDataCenterFallbackWithoutRealSecrets() throws IOException {
        String env = read(ENV_EXAMPLE);

        assertThat(env)
                .contains(
                        "ERP_DATA_CENTER_ENABLED=true",
                        "ERP_DATA_CENTER_BASE_URL=http://192.168.70.250:8080",
                        "ERP_DATA_CENTER_TOKEN_URL=http://192.168.70.250:8080/realms/api-platform/protocol/openid-connect/token",
                        "ERP_DATA_CENTER_CLIENT_ID=app_8ed28b2a1faf4a0b",
                        "ERP_DATA_CENTER_CLIENT_SECRET=",
                        "ERP_DATA_CENTER_GRANT_TYPE=client_credentials",
                        "ERP_DATA_CENTER_CONNECT_TIMEOUT_MS=5000",
                        "ERP_DATA_CENTER_READ_TIMEOUT_MS=30000",
                        "ERP_DATA_CENTER_TOKEN_EXPIRE_SKEW_SECONDS=60",
                        "ERP_DATA_CENTER_MES_MATERIAL_PATH=",
                        "ERP_DATA_CENTER_WMS_ALLOCATION_PATH=",
                        "ERP_DATA_CENTER_U9_SALES_ORDER_SYNC_PATH=",
                        "ERP_DATA_CENTER_U9_COMMON_ENTITY_QUERY_PATH=",
                        "ERP_DATA_CENTER_MES_PRODUCTION_DISPATCH_PATH="
                )
                .contains(
                        "deprecated direct-system fallback",
                        "ERP_MES_BASE_URL=",
                        "ERP_WMS_BASE_URL=",
                        "ERP_U9_BASE_URL="
                )
                .doesNotContain(
                        "access_token=",
                        "client_secret="
                );
        String secretLine = env.lines()
                .filter(line -> line.startsWith("ERP_DATA_CENTER_CLIENT_SECRET="))
                .findFirst()
                .orElse("");
        assertThat(secretLine).isEqualTo("ERP_DATA_CENTER_CLIENT_SECRET=");
    }

    @Test
    void applicationYamlMapsDataCenterEnvironmentVariables() throws IOException {
        String yaml = read(APPLICATION_YAML);

        assertThat(yaml)
                .contains(
                        "data-center:",
                        "enabled: ${ERP_DATA_CENTER_ENABLED:true}",
                        "base-url: ${ERP_DATA_CENTER_BASE_URL:http://192.168.70.250:8080}",
                        "token-url: ${ERP_DATA_CENTER_TOKEN_URL:http://192.168.70.250:8080/realms/api-platform/protocol/openid-connect/token}",
                        "client-id: ${ERP_DATA_CENTER_CLIENT_ID:app_8ed28b2a1faf4a0b}",
                        "client-secret: ${ERP_DATA_CENTER_CLIENT_SECRET:}",
                        "grant-type: ${ERP_DATA_CENTER_GRANT_TYPE:client_credentials}",
                        "connect-timeout-ms: ${ERP_DATA_CENTER_CONNECT_TIMEOUT_MS:5000}",
                        "read-timeout-ms: ${ERP_DATA_CENTER_READ_TIMEOUT_MS:30000}",
                        "token-expire-skew-seconds: ${ERP_DATA_CENTER_TOKEN_EXPIRE_SKEW_SECONDS:60}",
                        "mes-material-path: ${ERP_DATA_CENTER_MES_MATERIAL_PATH:}",
                        "wms-allocation-path: ${ERP_DATA_CENTER_WMS_ALLOCATION_PATH:}",
                        "u9-sales-order-sync-path: ${ERP_DATA_CENTER_U9_SALES_ORDER_SYNC_PATH:}",
                        "u9-common-entity-query-path: ${ERP_DATA_CENTER_U9_COMMON_ENTITY_QUERY_PATH:}",
                        "mes-production-dispatch-path: ${ERP_DATA_CENTER_MES_PRODUCTION_DISPATCH_PATH:}"
                )
                .doesNotContain(
                        "client-secret: ${ERP_DATA_CENTER_CLIENT_SECRET:X",
                        "access_" + "token="
                );
    }

    @Test
    void dataCenterPropertiesBindFromErpDataCenterPrefix() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("erp.data-center.enabled", "true")
                .withProperty("erp.data-center.base-url", "http://dc.example")
                .withProperty("erp.data-center.token-url", "http://dc.example/token")
                .withProperty("erp.data-center.client-id", "client-a")
                .withProperty("erp.data-center.client-secret", "secret-a")
                .withProperty("erp.data-center.grant-type", "client_credentials")
                .withProperty("erp.data-center.connect-timeout-ms", "6000")
                .withProperty("erp.data-center.read-timeout-ms", "31000")
                .withProperty("erp.data-center.token-expire-skew-seconds", "90")
                .withProperty("erp.data-center.mes-material-path", "/gateway/mes/material")
                .withProperty("erp.data-center.wms-allocation-path", "/gateway/wms/allocation")
                .withProperty("erp.data-center.u9-sales-order-sync-path", "/gateway/u9/sales-orders")
                .withProperty("erp.data-center.u9-common-entity-query-path", "/gateway/u9/common-entity")
                .withProperty("erp.data-center.mes-production-dispatch-path", "/gateway/mes/production");

        DataCenterProperties properties = Binder.get(environment)
                .bind("erp.data-center", DataCenterProperties.class)
                .orElseThrow(() -> new AssertionError("erp.data-center properties should bind"));

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getBaseUrl()).isEqualTo("http://dc.example");
        assertThat(properties.getTokenUrl()).isEqualTo("http://dc.example/token");
        assertThat(properties.getClientId()).isEqualTo("client-a");
        assertThat(properties.getClientSecret()).isEqualTo("secret-a");
        assertThat(properties.getGrantType()).isEqualTo("client_credentials");
        assertThat(properties.getConnectTimeoutMs()).isEqualTo(6000);
        assertThat(properties.getReadTimeoutMs()).isEqualTo(31000);
        assertThat(properties.getTokenExpireSkewSeconds()).isEqualTo(90);
        assertThat(properties.getMesMaterialPath()).isEqualTo("/gateway/mes/material");
        assertThat(properties.getWmsAllocationPath()).isEqualTo("/gateway/wms/allocation");
        assertThat(properties.getU9SalesOrderSyncPath()).isEqualTo("/gateway/u9/sales-orders");
        assertThat(properties.getU9CommonEntityQueryPath()).isEqualTo("/gateway/u9/common-entity");
        assertThat(properties.getMesProductionDispatchPath()).isEqualTo("/gateway/mes/production");
    }

    @Test
    void systemConfigDocStatesEnvIsFallbackOnlyAndContainsNoRealSecret() throws IOException {
        String doc = read(SYSTEM_CONFIG_DOC);

        assertThat(doc)
                .contains(
                        "erp.env` 作为本地开发、首次部署和页面配置缺失时的 fallback",
                        "真实 `ERP_DATA_CENTER_CLIENT_SECRET` 只写入本机或服务器真实 `erp.env`",
                        "文档不包含真实 `client_secret` 或真实 access token"
                )
                .doesNotContain(
                        "client_" + "secret=X",
                        "access_" + "token=ey"
                );
    }

    private static String read(Path path) throws IOException {
        assertThat(path).exists();
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
