package com.machining.quote;

import com.machining.quote.config.AuthContext;
import com.machining.quote.config.AuthInterceptor;
import com.machining.quote.config.RequirePermission;
import com.machining.quote.controller.DictController;
import com.machining.quote.controller.ProcessMasterController;
import com.machining.quote.dto.MesMasterDataSyncScheduleConfigDTO;
import com.machining.quote.dto.MesMasterDataSyncScheduleConfigReq;
import com.machining.quote.dto.PermissionInfoDTO;
import com.machining.quote.entity.MesMasterDataSyncRun;
import com.machining.quote.service.AuthService;
import com.machining.quote.service.PermissionService;
import com.machining.quote.service.mes.MesMasterDataSyncScheduleConfigService;
import com.machining.quote.service.mes.MesMasterDataSyncService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MesMasterDataSyncControllerTest {
    @AfterEach
    void clearAuthContext() {
        AuthContext.clear();
    }

    @Test
    void processSyncEndpointUsesProcessManagePermissionAndCurrentUser() throws Exception {
        Method method = ProcessMasterController.class.getMethod("syncMesProcessTypes");

        assertThat(method.getAnnotation(PostMapping.class).value())
                .containsExactly("/process-categories/sync-mes");
        assertThat(method.getAnnotation(RequirePermission.class).value())
                .containsExactly("engineer:dict:process-manage");

        MesMasterDataSyncService syncService = mock(MesMasterDataSyncService.class);
        when(syncService.submitProcessTypesManual(88L))
                .thenReturn(new MesMasterDataSyncService.SyncSubmitResult(
                        true, true, false, 9001L, "MES_PROCESS_TYPE", "RUNNING", "MES工艺主数据同步已提交"));
        ProcessMasterController controller = new ProcessMasterController();
        ReflectionTestUtils.setField(controller, "mesMasterDataSyncService", syncService);

        AuthContext.set(new AuthContext.SessionUser(88, "engineer", "Engineer", "ENGINEER"));
        Map<String, Object> response = controller.syncMesProcessTypes();

        assertThat(response)
                .containsEntry("success", true)
                .containsEntry("submitted", true)
                .containsEntry("alreadyRunning", false)
                .containsEntry("syncRunId", 9001L)
                .containsEntry("syncType", "MES_PROCESS_TYPE")
                .containsEntry("status", "RUNNING")
                .containsEntry("message", "MES工艺主数据同步已提交");
        verify(syncService).submitProcessTypesManual(88L);
        verify(syncService, never()).syncProcessTypesManual(88L);
    }

    @Test
    void materialSyncEndpointUsesMaterialManagePermissionAndReturnsFailureSummary() throws Exception {
        Method method = DictController.class.getMethod("syncMesMaterials");

        assertThat(method.getAnnotation(PostMapping.class).value())
                .containsExactly("/materials/sync-mes");
        assertThat(method.getAnnotation(RequirePermission.class).value())
                .containsExactly("engineer:dict:material-manage");

        MesMasterDataSyncService syncService = mock(MesMasterDataSyncService.class);
        when(syncService.submitMaterialsManual(89L))
                .thenReturn(new MesMasterDataSyncService.SyncSubmitResult(
                        true, false, true, 9002L, "MES_MATERIAL", "RUNNING", "已有MES材料主数据同步正在执行"));
        DictController controller = new DictController();
        ReflectionTestUtils.setField(controller, "mesMasterDataSyncService", syncService);

        AuthContext.set(new AuthContext.SessionUser(89, "engineer", "Engineer", "ENGINEER"));
        Map<String, Object> response = controller.syncMesMaterials();

        assertThat(response)
                .containsEntry("success", true)
                .containsEntry("submitted", false)
                .containsEntry("alreadyRunning", true)
                .containsEntry("syncRunId", 9002L)
                .containsEntry("syncType", "MES_MATERIAL")
                .containsEntry("status", "RUNNING")
                .containsEntry("message", "已有MES材料主数据同步正在执行");
        verify(syncService).submitMaterialsManual(89L);
        verify(syncService, never()).syncMaterialsManual(89L);
    }

    @Test
    void materialRunStatusEndpointsUseMaterialManagePermissionAndReturnCounters() throws Exception {
        Method latest = DictController.class.getMethod("latestMesMaterialSyncRun");
        Method byId = DictController.class.getMethod("mesMaterialSyncRun", Long.class);

        assertThat(latest.getAnnotation(GetMapping.class).value())
                .containsExactly("/materials/sync-runs/latest");
        assertThat(byId.getAnnotation(GetMapping.class).value())
                .containsExactly("/materials/sync-runs/{syncRunId}");
        assertThat(latest.getAnnotation(RequirePermission.class).value())
                .containsExactly("engineer:dict:material-manage");
        assertThat(byId.getAnnotation(RequirePermission.class).value())
                .containsExactly("engineer:dict:material-manage");

        MesMasterDataSyncService syncService = mock(MesMasterDataSyncService.class);
        MesMasterDataSyncRun run = run(9010L, "MES_MATERIAL", "SUCCESS");
        when(syncService.getLatestRun("MES_MATERIAL")).thenReturn(run);
        when(syncService.getRun("MES_MATERIAL", 9010L)).thenReturn(run);
        DictController controller = new DictController();
        ReflectionTestUtils.setField(controller, "mesMasterDataSyncService", syncService);

        assertThat(controller.latestMesMaterialSyncRun())
                .containsEntry("success", true)
                .containsEntry("syncRunId", 9010L)
                .containsEntry("syncType", "MES_MATERIAL")
                .containsEntry("status", "SUCCESS")
                .containsEntry("deletedCount", 3);
        assertThat(controller.mesMaterialSyncRun(9010L)).containsEntry("syncRunId", 9010L);
        verify(syncService).getLatestRun("MES_MATERIAL");
        verify(syncService).getRun("MES_MATERIAL", 9010L);
    }

    @Test
    void processRunStatusEndpointsUseProcessManagePermissionAndReturnCounters() throws Exception {
        Method latest = ProcessMasterController.class.getMethod("latestMesProcessTypeSyncRun");
        Method byId = ProcessMasterController.class.getMethod("mesProcessTypeSyncRun", Long.class);

        assertThat(latest.getAnnotation(GetMapping.class).value())
                .containsExactly("/process-categories/sync-runs/latest");
        assertThat(byId.getAnnotation(GetMapping.class).value())
                .containsExactly("/process-categories/sync-runs/{syncRunId}");
        assertThat(latest.getAnnotation(RequirePermission.class).value())
                .containsExactly("engineer:dict:process-manage");
        assertThat(byId.getAnnotation(RequirePermission.class).value())
                .containsExactly("engineer:dict:process-manage");

        MesMasterDataSyncService syncService = mock(MesMasterDataSyncService.class);
        MesMasterDataSyncRun run = run(9020L, "MES_PROCESS_TYPE", "FAILED");
        when(syncService.getLatestRun("MES_PROCESS_TYPE")).thenReturn(run);
        when(syncService.getRun("MES_PROCESS_TYPE", 9020L)).thenReturn(run);
        ProcessMasterController controller = new ProcessMasterController();
        ReflectionTestUtils.setField(controller, "mesMasterDataSyncService", syncService);

        assertThat(controller.latestMesProcessTypeSyncRun())
                .containsEntry("success", false)
                .containsEntry("syncRunId", 9020L)
                .containsEntry("syncType", "MES_PROCESS_TYPE")
                .containsEntry("status", "FAILED")
                .containsEntry("message", "safe failure");
        assertThat(controller.mesProcessTypeSyncRun(9020L)).containsEntry("syncRunId", 9020L);
        verify(syncService).getLatestRun("MES_PROCESS_TYPE");
        verify(syncService).getRun("MES_PROCESS_TYPE", 9020L);
    }

    @Test
    void materialScheduleEndpointsUseMaterialManagePermissionAndDelegateToService() throws Exception {
        Method get = DictController.class.getMethod("mesMaterialSyncSchedule");
        Method put = DictController.class.getMethod("updateMesMaterialSyncSchedule", MesMasterDataSyncScheduleConfigReq.class);

        assertThat(get.getAnnotation(GetMapping.class).value())
                .containsExactly("/materials/sync-schedule");
        assertThat(put.getAnnotation(PutMapping.class).value())
                .containsExactly("/materials/sync-schedule");
        assertThat(get.getAnnotation(RequirePermission.class).value())
                .containsExactly("engineer:dict:material-manage");
        assertThat(put.getAnnotation(RequirePermission.class).value())
                .containsExactly("engineer:dict:material-manage");

        MesMasterDataSyncScheduleConfigService scheduleService = mock(MesMasterDataSyncScheduleConfigService.class);
        MesMasterDataSyncScheduleConfigDTO dto = new MesMasterDataSyncScheduleConfigDTO();
        dto.setSyncType("MES_MATERIAL");
        dto.setEnabled(true);
        when(scheduleService.getSetting("MES_MATERIAL")).thenReturn(dto);

        MesMasterDataSyncScheduleConfigReq req = new MesMasterDataSyncScheduleConfigReq();
        req.setEnabled(false);
        when(scheduleService.updateSetting("MES_MATERIAL", req)).thenReturn(dto);

        DictController controller = new DictController();
        ReflectionTestUtils.setField(controller, "mesMasterDataSyncScheduleConfigService", scheduleService);

        assertThat(controller.mesMaterialSyncSchedule()).isSameAs(dto);
        assertThat(controller.updateMesMaterialSyncSchedule(req)).isSameAs(dto);
        verify(scheduleService).getSetting("MES_MATERIAL");
        verify(scheduleService).updateSetting("MES_MATERIAL", req);
    }

    @Test
    void processScheduleEndpointsUseProcessManagePermissionAndDelegateToService() throws Exception {
        Method get = ProcessMasterController.class.getMethod("mesProcessTypeSyncSchedule");
        Method put = ProcessMasterController.class.getMethod("updateMesProcessTypeSyncSchedule", MesMasterDataSyncScheduleConfigReq.class);

        assertThat(get.getAnnotation(GetMapping.class).value())
                .containsExactly("/process-categories/sync-schedule");
        assertThat(put.getAnnotation(PutMapping.class).value())
                .containsExactly("/process-categories/sync-schedule");
        assertThat(get.getAnnotation(RequirePermission.class).value())
                .containsExactly("engineer:dict:process-manage");
        assertThat(put.getAnnotation(RequirePermission.class).value())
                .containsExactly("engineer:dict:process-manage");

        MesMasterDataSyncScheduleConfigService scheduleService = mock(MesMasterDataSyncScheduleConfigService.class);
        MesMasterDataSyncScheduleConfigDTO dto = new MesMasterDataSyncScheduleConfigDTO();
        dto.setSyncType("MES_PROCESS_TYPE");
        dto.setEnabled(true);
        when(scheduleService.getSetting("MES_PROCESS_TYPE")).thenReturn(dto);

        MesMasterDataSyncScheduleConfigReq req = new MesMasterDataSyncScheduleConfigReq();
        req.setEnabled(false);
        when(scheduleService.updateSetting("MES_PROCESS_TYPE", req)).thenReturn(dto);

        ProcessMasterController controller = new ProcessMasterController();
        ReflectionTestUtils.setField(controller, "mesMasterDataSyncScheduleConfigService", scheduleService);

        assertThat(controller.mesProcessTypeSyncSchedule()).isSameAs(dto);
        assertThat(controller.updateMesProcessTypeSyncSchedule(req)).isSameAs(dto);
        verify(scheduleService).getSetting("MES_PROCESS_TYPE");
        verify(scheduleService).updateSetting("MES_PROCESS_TYPE", req);
    }

    @Test
    void interceptorRejectsMesSyncEndpointsWhenManagePermissionIsMissing() throws Exception {
        AuthInterceptor interceptor = interceptor(permissionInfo(false, "engineer:dict:view"));

        MockHttpServletResponse processResponse = new MockHttpServletResponse();
        boolean processAllowed = interceptor.preHandle(
                request("/api/dict/process-categories/sync-mes"),
                processResponse,
                handler(new ProcessMasterController(), "syncMesProcessTypes"));

        assertThat(processAllowed).isFalse();
        assertThat(processResponse.getStatus()).isEqualTo(403);

        MockHttpServletResponse materialResponse = new MockHttpServletResponse();
        boolean materialAllowed = interceptor.preHandle(
                request("/api/dict/materials/sync-mes"),
                materialResponse,
                handler(new DictController(), "syncMesMaterials"));

        assertThat(materialAllowed).isFalse();
        assertThat(materialResponse.getStatus()).isEqualTo(403);

        MockHttpServletResponse materialScheduleResponse = new MockHttpServletResponse();
        boolean materialScheduleAllowed = interceptor.preHandle(
                request("PUT", "/api/dict/materials/sync-schedule"),
                materialScheduleResponse,
                handler(new DictController(), "updateMesMaterialSyncSchedule", MesMasterDataSyncScheduleConfigReq.class));

        assertThat(materialScheduleAllowed).isFalse();
        assertThat(materialScheduleResponse.getStatus()).isEqualTo(403);

        MockHttpServletResponse processScheduleResponse = new MockHttpServletResponse();
        boolean processScheduleAllowed = interceptor.preHandle(
                request("PUT", "/api/dict/process-categories/sync-schedule"),
                processScheduleResponse,
                handler(new ProcessMasterController(), "updateMesProcessTypeSyncSchedule", MesMasterDataSyncScheduleConfigReq.class));

        assertThat(processScheduleAllowed).isFalse();
        assertThat(processScheduleResponse.getStatus()).isEqualTo(403);
    }

    @Test
    void mesSyncAuditSummaryStaysLimitedToRunIdAndCounters() throws Exception {
        String source = Files.readString(
                Path.of("src", "main", "java", "com", "machining", "quote", "service", "mes", "MesMasterDataSyncService.java"),
                StandardCharsets.UTF_8);

        assertThat(source)
                .contains("startRun(SYNC_TYPE_PROCESS_TYPE")
                .contains("startRun(SYNC_TYPE_MATERIAL")
                .contains("finishRun(run, STATUS_SUCCESS")
                .contains("finishRun(run, STATUS_FAILED")
                .contains("audit(run, counters, true")
                .contains("audit(run, failureCounters, false")
                .contains("summary.put(\"syncRunId\"")
                .contains("summary.put(\"fetchedCount\"")
                .contains("summary.put(\"validCount\"")
                .contains("summary.put(\"invalidCount\"")
                .contains("summary.put(\"duplicateCount\"")
                .contains("summary.put(\"upsertedCount\"")
                .contains("summary.put(\"disabledCount\"")
                .contains("summary.put(\"deletedCount\"")
                .doesNotContain("summary.put(\"response")
                .doesNotContain("summary.put(\"body")
                .doesNotContain("summary.put(\"token")
                .doesNotContain("summary.put(\"authorization");
    }

    private AuthInterceptor interceptor(PermissionInfoDTO permissionInfo) {
        AuthService authService = mock(AuthService.class);
        PermissionService permissionService = mock(PermissionService.class);
        AuthContext.SessionUser user = new AuthContext.SessionUser(7, "engineer", "Engineer", "ENGINEER");
        when(authService.findByToken("token")).thenReturn(user);
        when(permissionService.getPermissionInfo(7, "ENGINEER")).thenReturn(permissionInfo);

        AuthInterceptor interceptor = new AuthInterceptor();
        ReflectionTestUtils.setField(interceptor, "authEnabled", true);
        ReflectionTestUtils.setField(interceptor, "authService", authService);
        ReflectionTestUtils.setField(interceptor, "permissionService", permissionService);
        return interceptor;
    }

    private PermissionInfoDTO permissionInfo(boolean superAdmin, String... permissions) {
        PermissionInfoDTO info = new PermissionInfoDTO();
        info.setSuperAdmin(superAdmin);
        info.setPermissions(List.of(permissions));
        info.setRoles(superAdmin ? List.of("SUPER_ADMIN") : List.of("ENGINEER_USER"));
        return info;
    }

    private MockHttpServletRequest request(String path) {
        return request("POST", path);
    }

    private MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.addHeader("Authorization", "Bearer token");
        return request;
    }

    private HandlerMethod handler(Object controller, String methodName) throws NoSuchMethodException {
        Method method = controller.getClass().getMethod(methodName);
        return new HandlerMethod(controller, method);
    }

    private HandlerMethod handler(Object controller, String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = controller.getClass().getMethod(methodName, parameterTypes);
        return new HandlerMethod(controller, method);
    }

    private MesMasterDataSyncRun run(Long id, String syncType, String status) {
        MesMasterDataSyncRun run = new MesMasterDataSyncRun();
        run.setId(id);
        run.setSyncType(syncType);
        run.setStatus(status);
        run.setStartedAt(LocalDateTime.of(2026, 8, 27, 14, 22, 10));
        run.setFinishedAt("RUNNING".equals(status) ? null : LocalDateTime.of(2026, 8, 27, 14, 25, 42));
        run.setFetchedCount(20);
        run.setValidCount(18);
        run.setInvalidCount(1);
        run.setDuplicateCount(1);
        run.setUpsertedCount(17);
        run.setDisabledCount(2);
        run.setDeletedCount(3);
        run.setMessage("FAILED".equals(status) ? "safe failure" : "OK");
        run.setCreatedBy(7L);
        run.setCreatedAt(LocalDateTime.of(2026, 8, 27, 14, 22, 10));
        return run;
    }
}
