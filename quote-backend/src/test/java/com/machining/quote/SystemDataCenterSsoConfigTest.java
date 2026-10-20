package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.machining.quote.config.DataCenterSsoProperties;
import com.machining.quote.dto.system.SystemDataCenterSsoConfigDTO;
import com.machining.quote.dto.system.SystemDataCenterSsoConfigSaveReq;
import com.machining.quote.entity.SystemDataCenterSsoConfig;
import com.machining.quote.mapper.SystemDataCenterSsoConfigMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.SystemDataCenterSsoConfigService;
import com.machining.quote.service.auth.DataCenterSsoConfigResolver;
import com.machining.quote.service.auth.DataCenterSsoRuntimeConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemDataCenterSsoConfigTest {

    @Test
    void migrationCreatesSsoConfigAndNonceTablesWithPermissions() throws Exception {
        String migration = Files.readString(Path.of(
                "src", "main", "resources", "db", "migration", "V22__data_center_sso_login_config.sql"
        ), StandardCharsets.UTF_8);

        assertThat(migration)
                .contains("CREATE TABLE system_data_center_sso_config")
                .contains("shared_secret_cipher")
                .contains("CREATE UNIQUE INDEX uk_system_data_center_sso_active")
                .contains("WHERE active = true")
                .contains("CREATE TABLE auth_data_center_sso_nonce")
                .contains("CREATE UNIQUE INDEX uk_auth_data_center_sso_nonce")
                .contains("system:config:sso:update")
                .contains("system:config:sso:test")
                .contains("system:config:sso:activate")
                .doesNotContain("V1__qpo_baseline_schema")
                .doesNotContain("V2__qpo_baseline_seed_data");
    }

    @Test
    void controllerExposesSsoConfigEndpointsWithExpectedPermissions() throws Exception {
        String source = Files.readString(Path.of(
                "src", "main", "java", "com", "machining", "quote", "controller", "SystemDataCenterSsoConfigController.java"
        ), StandardCharsets.UTF_8);

        assertThat(source)
                .contains("@RequestMapping(\"/api/system/data-center-sso-configs\")")
                .contains("@GetMapping")
                .contains("@PostMapping")
                .contains("@PutMapping(\"/{id}\")")
                .contains("@PostMapping(\"/{id}/test\")")
                .contains("@PutMapping(\"/{id}/activate\")")
                .contains("@PutMapping(\"/{id}/disable\")")
                .contains("@RequirePermission(\"system:config:view\")")
                .contains("@RequirePermission(\"system:config:sso:update\")")
                .contains("@RequirePermission(\"system:config:sso:test\")")
                .contains("@RequirePermission(\"system:config:sso:activate\")");
    }

    @Test
    void saveRedactsSharedSecretAndKeepsExistingSecretWhenBlank() {
        SystemDataCenterSsoConfigMapper mapper = mock(SystemDataCenterSsoConfigMapper.class);
        AuditService auditService = mock(AuditService.class);
        SystemDataCenterSsoConfigService service = service(mapper, auditService);
        doAnswer(invocation -> {
            SystemDataCenterSsoConfig entity = invocation.getArgument(0);
            entity.setId(31L);
            return 1;
        }).when(mapper).insert(any(SystemDataCenterSsoConfig.class));

        SystemDataCenterSsoConfigDTO created = service.saveConfig(null, req("new-secret"));

        assertThat(created.getId()).isEqualTo(31L);
        assertThat(created.getSharedSecretConfigured()).isTrue();
        assertThat(created.toString()).doesNotContain("new-secret");
        ArgumentCaptor<SystemDataCenterSsoConfig> createCaptor = ArgumentCaptor.forClass(SystemDataCenterSsoConfig.class);
        verify(mapper).insert(createCaptor.capture());
        assertThat(createCaptor.getValue().getSharedSecretCipher()).isEqualTo("new-secret");
        ArgumentCaptor<Object> createAudit = ArgumentCaptor.forClass(Object.class);
        verify(auditService).record(eq("SYSTEM_DATACENTER_SSO_CONFIG_CREATE"), eq("system_data_center_sso_config"), eq(31L), isNull(), createAudit.capture());
        assertThat(String.valueOf(createAudit.getValue()))
                .contains("secretChanged=true")
                .doesNotContain("new-secret");

        SystemDataCenterSsoConfig existing = config(32L, "old-secret");
        when(mapper.selectById(32L)).thenReturn(existing);
        SystemDataCenterSsoConfigSaveReq updateReq = req(null);
        updateReq.setConfigName("updated");

        SystemDataCenterSsoConfigDTO updated = service.saveConfig(32L, updateReq);

        assertThat(updated.getSharedSecretConfigured()).isTrue();
        assertThat(updated.toString()).doesNotContain("old-secret");
        ArgumentCaptor<SystemDataCenterSsoConfig> updateCaptor = ArgumentCaptor.forClass(SystemDataCenterSsoConfig.class);
        verify(mapper).updateById(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getSharedSecretCipher()).isEqualTo("old-secret");
    }

    @Test
    void activateRequiresSuccessfulTestAndOnlyOneActiveConfig() {
        SystemDataCenterSsoConfigMapper mapper = mock(SystemDataCenterSsoConfigMapper.class);
        SystemDataCenterSsoConfigService service = service(mapper, mock(AuditService.class));
        SystemDataCenterSsoConfig failed = config(41L, "secret");
        failed.setLastTestStatus("FAILED");
        when(mapper.selectById(41L)).thenReturn(failed);

        assertThatThrownBy(() -> service.activateConfig(41L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("test");

        SystemDataCenterSsoConfig success = config(42L, "secret");
        success.setLastTestStatus("SUCCESS");
        when(mapper.selectById(42L)).thenReturn(success);

        SystemDataCenterSsoConfigDTO activated = service.activateConfig(42L);

        assertThat(activated.getActive()).isTrue();
        ArgumentCaptor<UpdateWrapper<SystemDataCenterSsoConfig>> wrapperCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(mapper).update(any(SystemDataCenterSsoConfig.class), wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment()).contains("active");
    }

    @Test
    void resolverPrefersActiveDatabaseConfigBeforeFallbackProperties() {
        SystemDataCenterSsoConfigMapper mapper = mock(SystemDataCenterSsoConfigMapper.class);
        DataCenterSsoProperties properties = new DataCenterSsoProperties();
        properties.setEnabled(true);
        properties.setAppCode("QPO_ENV");
        properties.setSharedSecret("env-secret");
        properties.setTimestampSkewSeconds(90);
        properties.setNonceTtlMinutes(5);
        properties.setDefaultRedirect("/env");
        properties.setAllowedRedirectPrefixes(List.of("/env"));
        DataCenterSsoConfigResolver resolver = new DataCenterSsoConfigResolver(mapper, properties);
        when(mapper.selectActive()).thenReturn(config(51L, "db-secret"));

        DataCenterSsoRuntimeConfig dbConfig = resolver.resolve();

        assertThat(dbConfig.source()).isEqualTo("DB");
        assertThat(dbConfig.appCode()).isEqualTo("QPO");
        assertThat(dbConfig.sharedSecret()).isEqualTo("db-secret");
        assertThat(dbConfig.defaultRedirect()).isEqualTo("/dashboard");

        when(mapper.selectActive()).thenReturn(null);
        DataCenterSsoRuntimeConfig fallback = resolver.resolve();

        assertThat(fallback.source()).isEqualTo("ENV");
        assertThat(fallback.appCode()).isEqualTo("QPO_ENV");
        assertThat(fallback.sharedSecret()).isEqualTo("env-secret");
    }

    @Test
    void auditActionsExposeSystemModuleAndReadableLabels() {
        assertThat(AuditService.inferModule("SYSTEM_DATACENTER_SSO_CONFIG_CREATE")).isEqualTo("SYSTEM");
        assertThat(AuditService.labelForAction("SYSTEM_DATACENTER_SSO_CONFIG_CREATE")).contains("数据中心登录配置");
        assertThat(AuditService.labelForAction("SYSTEM_DATACENTER_SSO_CONFIG_TEST")).contains("数据中心登录配置");
        assertThat(AuditService.labelForAction("SYSTEM_DATACENTER_SSO_CONFIG_ACTIVATE")).contains("数据中心登录配置");
    }

    private SystemDataCenterSsoConfigService service(SystemDataCenterSsoConfigMapper mapper, AuditService auditService) {
        SystemDataCenterSsoConfigService service = new SystemDataCenterSsoConfigService();
        ReflectionTestUtils.setField(service, "configMapper", mapper);
        ReflectionTestUtils.setField(service, "auditService", auditService);
        return service;
    }

    private SystemDataCenterSsoConfigSaveReq req(String secret) {
        SystemDataCenterSsoConfigSaveReq req = new SystemDataCenterSsoConfigSaveReq();
        req.setConfigName("Data Center SSO");
        req.setEnabled(true);
        req.setAppCode("QPO");
        req.setSharedSecret(secret);
        req.setTimestampSkewSeconds(120);
        req.setNonceTtlMinutes(10);
        req.setDefaultRedirect("/dashboard");
        req.setAllowedRedirectPrefixes("/,/dashboard,/orders");
        req.setRemark("test");
        return req;
    }

    private SystemDataCenterSsoConfig config(Long id, String secret) {
        SystemDataCenterSsoConfig config = new SystemDataCenterSsoConfig();
        config.setId(id);
        config.setConfigName("Data Center SSO");
        config.setEnabled(true);
        config.setActive(false);
        config.setAppCode("QPO");
        config.setSharedSecretCipher(secret);
        config.setTimestampSkewSeconds(120);
        config.setNonceTtlMinutes(10);
        config.setDefaultRedirect("/dashboard");
        config.setAllowedRedirectPrefixes("/,/dashboard,/orders");
        config.setLastTestStatus("SUCCESS");
        config.setCreatedAt(LocalDateTime.now().minusDays(1));
        config.setUpdatedAt(LocalDateTime.now());
        return config;
    }
}
