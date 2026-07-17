package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.dto.AuditLogQueryReq;
import com.machining.quote.dto.AuditLogRetentionConfigDTO;
import com.machining.quote.dto.AuditLogRetentionConfigReq;
import com.machining.quote.dto.AuditLogRetentionRunDTO;
import com.machining.quote.dto.PageResult;
import com.machining.quote.service.AuditLogQueryService;
import com.machining.quote.controller.AuditLogController;
import com.machining.quote.service.AuditLogRetentionService;
import com.machining.quote.entity.AuditLog;
import com.machining.quote.mapper.AuditLogMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditLogQueryServiceTests {

    @Test
    void listLogsDefaultsToRecentSevenDaysAndMapsLegacyRows() throws Exception {
        AuditLogMapper mapper = mock(AuditLogMapper.class);
        AuditLog log = new AuditLog();
        log.setId(7);
        log.setUsername("market");
        log.setRole("MARKET");
        log.setAction("INTERNAL_TARGET_LEAD_TIME_UPDATE");
        log.setTargetType("item_quote_version");
        log.setTargetId("42");
        log.setCreatedAt(LocalDateTime.now());
        when(mapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);
        when(mapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(log));

        Object service = queryService(mapper);
        Object req = Class.forName("com.machining.quote.dto.AuditLogQueryReq").getConstructor().newInstance();

        Method listLogs = service.getClass().getMethod("listLogs", req.getClass());
        Object result = listLogs.invoke(service, req);

        assertThat(result).isInstanceOf(PageResult.class);
        PageResult<?> page = (PageResult<?>) result;
        assertThat(page.getPage()).isEqualTo(1);
        assertThat(page.getPageSize()).isEqualTo(20);
        assertThat(page.getTotal()).isEqualTo(1);
        Object dto = page.getRecords().get(0);
        assertThat(readProperty(dto, "module")).isEqualTo("MARKET");
        assertThat(readProperty(dto, "result")).isEqualTo("SUCCESS");
        assertThat(readProperty(dto, "message")).asString().contains("item_quote_version", "42");

        ArgumentCaptor<QueryWrapper<AuditLog>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(mapper).selectCount(captor.capture());
        assertThat(captor.getValue().getSqlSegment()).contains("created_at");
    }

    @Test
    void listLogsAppliesModuleResultAndKeywordFilters() throws Exception {
        AuditLogMapper mapper = mock(AuditLogMapper.class);
        when(mapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);

        Object service = queryService(mapper);
        Object req = Class.forName("com.machining.quote.dto.AuditLogQueryReq").getConstructor().newInstance();
        writeProperty(req, "module", "DICT");
        writeProperty(req, "result", "FAILED");
        writeProperty(req, "keyword", "7075");

        Method listLogs = service.getClass().getMethod("listLogs", req.getClass());
        listLogs.invoke(service, req);

        ArgumentCaptor<QueryWrapper<AuditLog>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(mapper).selectCount(captor.capture());
        QueryWrapper<AuditLog> query = captor.getValue();
        String sql = query.getSqlSegment();
        assertThat(sql).contains("result", "username", "action", "message");
        assertThat(query.getParamNameValuePairs().values()).contains("DICT", "FAILED", "%7075%");
        assertThat(sql).contains("module").contains("action");
    }

    @Test
    void marketModuleFilterExcludesEngineerRefreshTaskSubmitAction() throws Exception {
        AuditLogMapper mapper = mock(AuditLogMapper.class);
        when(mapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);

        Object service = queryService(mapper);
        Object req = Class.forName("com.machining.quote.dto.AuditLogQueryReq").getConstructor().newInstance();
        writeProperty(req, "module", "MARKET");

        Method listLogs = service.getClass().getMethod("listLogs", req.getClass());
        listLogs.invoke(service, req);

        ArgumentCaptor<QueryWrapper<AuditLog>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(mapper).selectCount(captor.capture());
        QueryWrapper<AuditLog> query = captor.getValue();
        assertThat(query.getSqlSegment()).contains("<>");
    }

    @Test
    void optionsReturnsActionsModulesRolesAndTargetTypes() throws Exception {
        AuditLogMapper mapper = mock(AuditLogMapper.class);
        when(mapper.selectObjs(any(QueryWrapper.class))).thenReturn(List.of("DICT_MATERIAL_UPDATE", "USER_CREATE"));

        Object service = queryService(mapper);
        Method options = service.getClass().getMethod("options");
        Object result = options.invoke(service);

        assertThat(result).isInstanceOf(Map.class);
        Map<?, ?> map = (Map<?, ?>) result;
        assertThat(asStringList(map.get("modules"))).contains("SYSTEM", "DICT", "MARKET");
        assertThat(asStringList(map.get("roles"))).contains("ADMIN", "MARKET", "ENGINEER", "SYSTEM");
        assertThat((List<?>) map.get("actions")).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void exportCsvCapsRowsAndEmitsUtf8BomForExcel() throws Exception {
        AuditLogMapper mapper = mock(AuditLogMapper.class);
        AuditLog log = new AuditLog();
        log.setId(12);
        log.setUsername("engineer");
        log.setRole("ENGINEER");
        log.setModule("ENGINEER");
        log.setAction("ITEM_QUOTE_SAVE");
        log.setTargetType("item_quote_version");
        log.setTargetId("55");
        log.setResult("SUCCESS");
        log.setMessage("保存核价 P-001");
        log.setBeforeData("{\"partNo\":\"P-001\",\"qty\":1}");
        log.setAfterData("{\"partNo\":\"P-001\",\"qty\":2}");
        log.setIpAddress("127.0.0.1");
        log.setRequestPath("/api/engineer/submit-quote");
        log.setCreatedAt(LocalDateTime.of(2026, 7, 17, 18, 30, 0));
        when(mapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(log));

        AuditLogQueryService service = (AuditLogQueryService) queryService(mapper);
        AuditLogQueryReq req = new AuditLogQueryReq();
        req.setKeyword("P-001");
        byte[] csvBytes = service.exportCsv(req);

        assertThat(csvBytes).startsWith(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        String csv = new String(csvBytes, StandardCharsets.UTF_8);
        assertThat(csv)
                .startsWith("\uFEFF日志ID,时间,操作人,角色,模块,动作,结果")
                .contains("保存逐件核价")
                .contains("\"{\"\"partNo\"\":\"\"P-001\"\",\"\"qty\"\":2}\"");

        ArgumentCaptor<QueryWrapper<AuditLog>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(mapper).selectList(captor.capture());
        assertThat(captor.getValue().getSqlSegment()).contains("LIMIT 5000");
    }

    @Test
    void controllerExportCsvWritesAttachmentHeaders() throws Exception {
        AuditLogQueryService service = mock(AuditLogQueryService.class);
        byte[] csvBytes = "\uFEFF日志ID,时间\n1,2026-07-17".getBytes(StandardCharsets.UTF_8);
        when(service.exportCsv(any(AuditLogQueryReq.class))).thenReturn(csvBytes);
        AuditLogController controller = new AuditLogController();
        ReflectionTestUtils.setField(controller, "auditLogQueryService", service);
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.export(new AuditLogQueryReq(), response);

        assertThat(response.getContentType()).isEqualTo("text/csv;charset=UTF-8");
        assertThat(response.getHeader("Content-Disposition")).contains("audit-logs.csv");
        assertThat(response.getContentAsByteArray()).isEqualTo(csvBytes);
        verify(service).exportCsv(any(AuditLogQueryReq.class));
    }

    @Test
    void controllerExposesRetentionSettingUpdateAndManualCleanup() {
        AuditLogQueryService queryService = mock(AuditLogQueryService.class);
        AuditLogRetentionService retentionService = mock(AuditLogRetentionService.class);
        AuditLogRetentionConfigDTO setting = new AuditLogRetentionConfigDTO();
        setting.setEnabled(true);
        setting.setRetentionDays(180);
        setting.setCleanupTime("02:00");
        AuditLogRetentionRunDTO run = new AuditLogRetentionRunDTO();
        run.setStatus("SUCCESS");
        run.setDeletedCount(3);
        when(retentionService.getSetting()).thenReturn(setting);
        when(retentionService.updateSetting(any(AuditLogRetentionConfigReq.class))).thenReturn(setting);
        when(retentionService.runCleanupNow()).thenReturn(run);
        AuditLogController controller = new AuditLogController();
        ReflectionTestUtils.setField(controller, "auditLogQueryService", queryService);
        ReflectionTestUtils.setField(controller, "auditLogRetentionService", retentionService);

        assertThat(controller.retentionSetting()).isSameAs(setting);
        assertThat(controller.updateRetentionSetting(new AuditLogRetentionConfigReq()).get("data")).isSameAs(setting);
        assertThat(controller.runRetentionCleanup().get("data")).isSameAs(run);

        verify(retentionService).getSetting();
        verify(retentionService).updateSetting(any(AuditLogRetentionConfigReq.class));
        verify(retentionService).runCleanupNow();
    }

    private Object queryService(AuditLogMapper mapper) throws Exception {
        Object service = Class.forName("com.machining.quote.service.AuditLogQueryService")
                .getConstructor()
                .newInstance();
        ReflectionTestUtils.setField(service, "auditLogMapper", mapper);
        return service;
    }

    private Object readProperty(Object target, String property) {
        try {
            String getter = "get" + Character.toUpperCase(property.charAt(0)) + property.substring(1);
            return target.getClass().getMethod(getter).invoke(target);
        } catch (ReflectiveOperationException e) {
            fail("DTO should expose property: " + property, e);
            return null;
        }
    }

    private void writeProperty(Object target, String property, Object value) {
        try {
            String setter = "set" + Character.toUpperCase(property.charAt(0)) + property.substring(1);
            target.getClass().getMethod(setter, value.getClass()).invoke(target, value);
        } catch (ReflectiveOperationException e) {
            fail("Request should expose setter: " + property, e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> asStringList(Object value) {
        return (List<String>) value;
    }
}
