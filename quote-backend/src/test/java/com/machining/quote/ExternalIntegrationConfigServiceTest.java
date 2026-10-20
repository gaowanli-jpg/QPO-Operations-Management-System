package com.machining.quote;

import com.machining.quote.dto.SystemIntegrationConfigDTO;
import com.machining.quote.entity.SystemExternalIntegrationConfig;
import com.machining.quote.integration.ExternalAuthType;
import com.machining.quote.integration.ExternalIntegrationConfig;
import com.machining.quote.integration.ExternalIntegrationConfigService;
import com.machining.quote.integration.ExternalSystemCode;
import com.machining.quote.mapper.SystemExternalIntegrationConfigMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExternalIntegrationConfigServiceTest {
    private static final Path SERVICE_SOURCE = Path.of(
            "src", "main", "java", "com", "machining", "quote", "integration", "ExternalIntegrationConfigService.java"
    );

    @Test
    void activeMesConfigUsesDatabaseConfigWhenPresent() {
        SystemExternalIntegrationConfigMapper mapper = mock(SystemExternalIntegrationConfigMapper.class);
        when(mapper.selectOne(any())).thenReturn(entity(
                11L,
                "MES",
                "MES test",
                "BEARER_TOKEN",
                Map.of(
                        "baseUrl", "http://mes.internal/",
                        "materialPath", "mes/material",
                        "authToken", "mes-secret",
                        "timeoutMs", 15000
                )
        ));
        ExternalIntegrationConfigService service = new ExternalIntegrationConfigService(mapper);

        ExternalIntegrationConfig config = service.activeConfig(ExternalSystemCode.MES);

        assertThat(config.id()).isEqualTo(11L);
        assertThat(config.systemCode()).isEqualTo(ExternalSystemCode.MES);
        assertThat(config.authType()).isEqualTo(ExternalAuthType.BEARER_TOKEN);
        assertThat(config.baseUrl()).isEqualTo("http://mes.internal");
        assertThat(config.materialPath()).isEqualTo("/mes/material");
        assertThat(config.authToken()).isEqualTo("mes-secret");
        assertThat(config.timeoutMs()).isEqualTo(15000);
    }

    @Test
    void activeWmsConfigUsesDatabaseConfigWhenPresent() {
        SystemExternalIntegrationConfigMapper mapper = mock(SystemExternalIntegrationConfigMapper.class);
        when(mapper.selectOne(any())).thenReturn(entity(
                12L,
                "WMS",
                "WMS test",
                "BEARER_TOKEN",
                Map.of(
                        "baseUrl", "http://wms.internal",
                        "allocationPath", "/wms/allocate",
                        "authToken", "wms-secret",
                        "timeoutMs", "16000"
                )
        ));
        ExternalIntegrationConfigService service = new ExternalIntegrationConfigService(mapper);

        ExternalIntegrationConfig config = service.activeConfig("wms");

        assertThat(config.systemCode()).isEqualTo(ExternalSystemCode.WMS);
        assertThat(config.baseUrl()).isEqualTo("http://wms.internal");
        assertThat(config.allocationPath()).isEqualTo("/wms/allocate");
        assertThat(config.authToken()).isEqualTo("wms-secret");
        assertThat(config.timeoutMs()).isEqualTo(16000);
    }

    @Test
    void activeU9ConfigUsesDatabaseConfigWhenPresent() {
        SystemExternalIntegrationConfigMapper mapper = mock(SystemExternalIntegrationConfigMapper.class);
        when(mapper.selectOne(any())).thenReturn(entity(
                13L,
                "U9",
                "U9 test",
                "U9_AUTH_LOGIN",
                Map.of(
                        "baseUrl", "http://u9.internal/",
                        "authLoginPath", "webapi/AuthLogin",
                        "salesOrderCreatePath", "/webapi/SO/CreateOrder",
                        "userCode", "qpo",
                        "entCode", "D260729",
                        "orgCode", "ORG01",
                        "clientId", "client",
                        "clientSecret", "u9-secret",
                        "connectTimeoutMs", 6000,
                        "readTimeoutMs", 32000
                )
        ));
        ExternalIntegrationConfigService service = new ExternalIntegrationConfigService(mapper);

        ExternalIntegrationConfig config = service.activeConfig(ExternalSystemCode.U9);

        assertThat(config.systemCode()).isEqualTo(ExternalSystemCode.U9);
        assertThat(config.authType()).isEqualTo(ExternalAuthType.U9_AUTH_LOGIN);
        assertThat(config.baseUrl()).isEqualTo("http://u9.internal");
        assertThat(config.authLoginPath()).isEqualTo("/webapi/AuthLogin");
        assertThat(config.salesOrderCreatePath()).isEqualTo("/webapi/SO/CreateOrder");
        assertThat(config.userCode()).isEqualTo("qpo");
        assertThat(config.entCode()).isEqualTo("D260729");
        assertThat(config.orgCode()).isEqualTo("ORG01");
        assertThat(config.clientId()).isEqualTo("client");
        assertThat(config.clientSecret()).isEqualTo("u9-secret");
        assertThat(config.connectTimeoutMs()).isEqualTo(6000);
        assertThat(config.readTimeoutMs()).isEqualTo(32000);
    }

    @Test
    void activeConfigFallsBackToEnvironmentWhenDatabaseHasNoActiveConfig() {
        SystemExternalIntegrationConfigMapper mapper = mock(SystemExternalIntegrationConfigMapper.class);
        when(mapper.selectOne(any())).thenReturn(null);
        ExternalIntegrationConfigService service = new ExternalIntegrationConfigService(mapper);
        ReflectionTestUtils.setField(service, "mesBaseUrl", "http://env-mes");
        ReflectionTestUtils.setField(service, "mesAuthToken", "env-mes-token");
        ReflectionTestUtils.setField(service, "mesMaterialPath", "/env/mes/material");
        ReflectionTestUtils.setField(service, "mesTimeoutMs", 17000);
        ReflectionTestUtils.setField(service, "u9BaseUrl", "http://env-u9");
        ReflectionTestUtils.setField(service, "u9UserCode", "env-user");
        ReflectionTestUtils.setField(service, "u9EntCode", "env-ent");
        ReflectionTestUtils.setField(service, "u9OrgCode", "env-org");
        ReflectionTestUtils.setField(service, "u9ClientId", "env-client");
        ReflectionTestUtils.setField(service, "u9ClientSecret", "env-secret");
        ReflectionTestUtils.setField(service, "u9ConnectTimeoutMs", 7000);
        ReflectionTestUtils.setField(service, "u9ReadTimeoutMs", 33000);

        ExternalIntegrationConfig mes = service.activeConfig(ExternalSystemCode.MES);
        ExternalIntegrationConfig wms = service.activeConfig(ExternalSystemCode.WMS);
        ExternalIntegrationConfig u9 = service.activeConfig(ExternalSystemCode.U9);

        assertThat(mes.baseUrl()).isEqualTo("http://env-mes");
        assertThat(mes.authToken()).isEqualTo("env-mes-token");
        assertThat(mes.materialPath()).isEqualTo("/env/mes/material");
        assertThat(mes.timeoutMs()).isEqualTo(17000);
        assertThat(wms.name()).isEqualTo("WMS data-center only");
        assertThat(wms.enabled()).isFalse();
        assertThat(wms.baseUrl()).isNull();
        assertThat(wms.authToken()).isNull();
        assertThat(wms.allocationPath()).isNull();
        assertThat(wms.timeoutMs()).isNull();
        assertThat(u9.baseUrl()).isEqualTo("http://env-u9");
        assertThat(u9.userCode()).isEqualTo("env-user");
        assertThat(u9.entCode()).isEqualTo("env-ent");
        assertThat(u9.orgCode()).isEqualTo("env-org");
        assertThat(u9.clientId()).isEqualTo("env-client");
        assertThat(u9.clientSecret()).isEqualTo("env-secret");
        assertThat(u9.connectTimeoutMs()).isEqualTo(7000);
        assertThat(u9.readTimeoutMs()).isEqualTo(33000);
    }

    @Test
    void dtoRedactsSensitiveSecrets() {
        ExternalIntegrationConfigService service = new ExternalIntegrationConfigService(null);
        SystemExternalIntegrationConfig mesEntity = entity(
                21L,
                "MES",
                "MES prod",
                "BEARER_TOKEN",
                Map.of(
                        "baseUrl", "http://mes",
                        "authToken", "plain-token",
                        "materialPath", "/material"
                )
        );
        SystemExternalIntegrationConfig u9Entity = entity(
                22L,
                "U9",
                "U9 prod",
                "U9_AUTH_LOGIN",
                Map.of(
                        "baseUrl", "http://u9",
                        "clientSecret", "plain-secret",
                        "clientId", "client",
                        "userCode", "user",
                        "entCode", "ent",
                        "orgCode", "org"
                )
        );

        SystemIntegrationConfigDTO mesDto = service.toDTO(mesEntity);
        SystemIntegrationConfigDTO u9Dto = service.toDTO(u9Entity);

        assertThat(mesDto.getAuthToken()).isEqualTo("******");
        assertThat(mesDto.getConfigJson()).containsEntry("authToken", "******");
        assertThat(mesDto.toString()).doesNotContain("plain-token");
        assertThat(u9Dto.getClientSecret()).isEqualTo("******");
        assertThat(u9Dto.getConfigJson()).containsEntry("clientSecret", "******");
        assertThat(u9Dto.toString()).doesNotContain("plain-secret");
    }

    @Test
    void serviceSourceDoesNotReadLegacyWmsEnvironmentFallback() throws IOException {
        String source = Files.readString(SERVICE_SOURCE, StandardCharsets.UTF_8);

        assertThat(source)
                .doesNotContain(
                        "erp.wms",
                        "wmsBaseUrl",
                        "wmsAuthToken",
                        "wmsAllocationPath",
                        "wmsTimeoutMs",
                        "WMS environment fallback",
                        "/api/wms/inventory/allocate"
                );
    }

    private SystemExternalIntegrationConfig entity(
            Long id,
            String systemCode,
            String name,
            String authType,
            Map<String, Object> configJson) {
        SystemExternalIntegrationConfig entity = new SystemExternalIntegrationConfig();
        entity.setId(id);
        entity.setSystemCode(systemCode);
        entity.setName(name);
        entity.setActive(true);
        entity.setEnabled(true);
        entity.setAuthType(authType);
        entity.setConfigJson(new LinkedHashMap<>(configJson));
        entity.setRemark("remark");
        entity.setLastTestStatus("SUCCESS");
        entity.setLastTestMessage("ok");
        entity.setLastTestAt(LocalDateTime.of(2026, 7, 30, 10, 0));
        entity.setCreatedAt(LocalDateTime.of(2026, 7, 30, 9, 0));
        entity.setUpdatedAt(LocalDateTime.of(2026, 7, 30, 10, 0));
        return entity;
    }
}
