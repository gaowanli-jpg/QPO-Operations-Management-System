package com.machining.quote;

import com.machining.quote.config.AuthContext;
import com.machining.quote.entity.AuditLog;
import com.machining.quote.mapper.AuditLogMapper;
import com.machining.quote.service.AuditService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuditServiceTests {

    @AfterEach
    void tearDown() {
        AuthContext.clear();
        clearTraceContextIfPresent();
    }

    @Test
    void legacyRecordSignatureEnrichesLogAndRedactsSensitiveJson() {
        AuditLogMapper mapper = mock(AuditLogMapper.class);
        AuditService service = service(mapper);
        AuthContext.set(new AuthContext.SessionUser(1, "admin", "管理员", "ADMIN"));
        setTraceContextIfPresent("req-123", "10.0.0.8", "Mozilla/5.0", "POST", "/api/dict/materials/12");

        Map<String, Object> before = new LinkedHashMap<>();
        before.put("oldPassword", "old-secret");
        before.put("materialName", "7075");
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("materialName", "7075-T6");
        after.put("password", "new-secret");
        after.put("nested", Map.of(
                "token", "jwt-token",
                "jwtToken", "jwt-alias",
                "authorizationHeader", "Bearer secret",
                "cookie", "erp_token=cookie-secret",
                "setCookie", "erp_token=set-cookie-secret",
                "safe", "kept"
        ));

        service.record("DICT_MATERIAL_UPDATE", "dict_material", 12, before, after);

        AuditLog log = insertedLog(mapper);
        assertThat(log.getUsername()).isEqualTo("admin");
        assertThat(log.getRole()).isEqualTo("ADMIN");
        assertThat(log.getAction()).isEqualTo("DICT_MATERIAL_UPDATE");
        assertThat(readProperty(log, "module")).isEqualTo("DICT");
        assertThat(readProperty(log, "result")).isEqualTo("SUCCESS");
        assertThat(readProperty(log, "message")).isEqualTo("更新材料");
        assertThat(readProperty(log, "ipAddress")).isEqualTo("10.0.0.8");
        assertThat(readProperty(log, "userAgent")).isEqualTo("Mozilla/5.0");
        assertThat(readProperty(log, "requestMethod")).isEqualTo("POST");
        assertThat(readProperty(log, "requestPath")).isEqualTo("/api/dict/materials/12");
        assertThat(readProperty(log, "requestId")).isEqualTo("req-123");
        assertThat(log.getBeforeData()).contains("[已脱敏]").doesNotContain("old-secret");
        assertThat(log.getAfterData())
                .contains("[已脱敏]", "kept")
                .doesNotContain("new-secret", "jwt-token", "jwt-alias", "Bearer secret", "cookie-secret", "set-cookie-secret");
    }

    @Test
    void richRecordSignatureSupportsFailureResultAndMessageFields() throws Exception {
        AuditLogMapper mapper = mock(AuditLogMapper.class);
        AuditService service = service(mapper);

        Method record = AuditService.class.getMethod(
                "record",
                String.class,
                String.class,
                Object.class,
                Object.class,
                Object.class,
                String.class,
                String.class,
                String.class,
                String.class
        );
        record.invoke(
                service,
                "USER_DELETE",
                "erp_user",
                8,
                Map.of("username", "guest"),
                null,
                "SYSTEM",
                "FAILED",
                "删除用户失败",
                "用户不存在"
        );

        AuditLog log = insertedLog(mapper);
        assertThat(readProperty(log, "module")).isEqualTo("SYSTEM");
        assertThat(readProperty(log, "result")).isEqualTo("FAILED");
        assertThat(readProperty(log, "message")).isEqualTo("删除用户失败");
        assertThat(readProperty(log, "errorMessage")).isEqualTo("用户不存在");
    }

    @Test
    void moduleInferenceKeepsLegacyBusinessActionsInExpectedModules() {
        assertThat(AuditService.inferModule("ORDER_QUOTE_COMPLETE")).isEqualTo("ENGINEER");
        assertThat(AuditService.inferModule("PRICING_SHEET_EXPORT")).isEqualTo("ENGINEER");
        assertThat(AuditService.inferModule("QUOTE_TEMPLATE_UPLOAD")).isEqualTo("SYSTEM");
        assertThat(AuditService.inferModule("ORDER_DELETE_COMPLETELY")).isEqualTo("ORDER");
        assertThat(AuditService.inferModule("SYSTEM_DEPT_CREATE")).isEqualTo("SYSTEM");
        assertThat(AuditService.inferModule("SYSTEM_POST_DELETE")).isEqualTo("SYSTEM");
    }

    @Test
    void organizationActionsHaveReadableAuditLabels() {
        assertThat(AuditService.labelForAction("SYSTEM_DEPT_CREATE")).contains("部门");
        assertThat(AuditService.labelForAction("SYSTEM_DEPT_UPDATE")).contains("部门");
        assertThat(AuditService.labelForAction("SYSTEM_DEPT_DELETE")).contains("部门");
        assertThat(AuditService.labelForAction("SYSTEM_POST_CREATE")).contains("岗位");
        assertThat(AuditService.labelForAction("SYSTEM_POST_UPDATE")).contains("岗位");
        assertThat(AuditService.labelForAction("SYSTEM_POST_DELETE")).contains("岗位");
        assertThat(AuditService.labelForAction("SYSTEM_USER_ORG_UPDATE")).contains("组织");
        assertThat(AuditService.labelForAction("USER_PROFILE_UPDATE")).contains("个人资料");
        assertThat(AuditService.labelForAction("PRICING_SHEET_EXPORT")).contains("核价单");
        assertThat(AuditService.labelForAction("ORDER_STATUS_ROLLBACK_QUOTED")).contains("已报价");
        assertThat(AuditService.labelForAction("ORDER_STATUS_ROLLBACK_PRICED")).contains("已核价");
        assertThat(AuditService.labelForAction("ORDER_DISCARD")).contains("废弃");
        assertThat(AuditService.labelForAction("ORDER_RESTORE")).contains("恢复");
    }

    @Test
    void recordRedactsSensitiveFieldsFromPlainObjects() {
        AuditLogMapper mapper = mock(AuditLogMapper.class);
        AuditService service = service(mapper);

        service.record("USER_UPDATE", "erp_user", 9, null, new PlainPasswordPayload());

        AuditLog log = insertedLog(mapper);
        assertThat(log.getAfterData()).contains("[已脱敏]", "可见备注").doesNotContain("plain-secret", "jwt-secret");
    }

    static class PlainPasswordPayload {
        public String password = "plain-secret";
        public String token = "jwt-secret";
        public String note = "可见备注";
    }

    private AuditService service(AuditLogMapper mapper) {
        AuditService service = new AuditService();
        ReflectionTestUtils.setField(service, "auditLogMapper", mapper);
        return service;
    }

    private AuditLog insertedLog(AuditLogMapper mapper) {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(mapper).insert(captor.capture());
        return captor.getValue();
    }

    private Object readProperty(AuditLog log, String property) {
        try {
            String getter = "get" + Character.toUpperCase(property.charAt(0)) + property.substring(1);
            return log.getClass().getMethod(getter).invoke(log);
        } catch (ReflectiveOperationException e) {
            fail("AuditLog should expose property: " + property, e);
            return null;
        }
    }

    private void setTraceContextIfPresent(String requestId, String ip, String userAgent, String method, String path) {
        try {
            Class<?> contextClass = Class.forName("com.machining.quote.config.RequestTraceContext");
            Class<?> traceClass = Class.forName("com.machining.quote.config.RequestTraceContext$RequestTrace");
            Object trace = traceClass
                    .getConstructor(String.class, String.class, String.class, String.class, String.class)
                    .newInstance(requestId, ip, userAgent, method, path);
            contextClass.getMethod("set", traceClass).invoke(null, trace);
        } catch (ClassNotFoundException ignored) {
            // Missing context is part of the red state; field assertions will fail.
        } catch (ReflectiveOperationException e) {
            fail("RequestTraceContext should accept a request trace", e);
        }
    }

    private void clearTraceContextIfPresent() {
        try {
            Class<?> contextClass = Class.forName("com.machining.quote.config.RequestTraceContext");
            contextClass.getMethod("clear").invoke(null);
        } catch (ClassNotFoundException ignored) {
        } catch (ReflectiveOperationException e) {
            fail("RequestTraceContext should be clearable", e);
        }
    }
}
