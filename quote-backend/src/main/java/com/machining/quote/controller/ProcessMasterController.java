package com.machining.quote.controller;

import com.machining.quote.config.AuthContext;
import com.machining.quote.config.RequirePermission;
import com.machining.quote.dto.MesMasterDataSyncScheduleConfigDTO;
import com.machining.quote.dto.MesMasterDataSyncScheduleConfigReq;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.ProcessCategoryDTO;
import com.machining.quote.dto.ProcessCategorySaveReq;
import com.machining.quote.dto.ProcessDetailDTO;
import com.machining.quote.dto.ProcessDetailSaveReq;
import com.machining.quote.entity.MesMasterDataSyncRun;
import com.machining.quote.service.ProcessMasterService;
import com.machining.quote.service.mes.MesMasterDataSyncScheduleConfigService;
import com.machining.quote.service.mes.MesMasterDataSyncService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dict")
public class ProcessMasterController {
    @Autowired
    private ProcessMasterService processMasterService;
    @Autowired
    private MesMasterDataSyncService mesMasterDataSyncService;
    @Autowired
    private MesMasterDataSyncScheduleConfigService mesMasterDataSyncScheduleConfigService;

    @GetMapping("/process-categories")
    @RequirePermission("engineer:dict:view")
    public List<ProcessCategoryDTO> listProcessCategories(
            @RequestParam(value = "enabledOnly", required = false) Boolean enabledOnly) {
        return processMasterService.listCategories(enabledOnly);
    }

    @GetMapping("/process-categories/page")
    @RequirePermission("engineer:dict:view")
    public PageResult<ProcessCategoryDTO> pageProcessCategories(
            @RequestParam(value = "enabledOnly", required = false) Boolean enabledOnly,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return processMasterService.pageCategories(enabledOnly, keyword, page, pageSize);
    }

    @PostMapping("/process-categories")
    @RequirePermission("engineer:dict:process-manage")
    public Map<String, Object> createProcessCategory(@RequestBody ProcessCategorySaveReq req) {
        ProcessCategoryDTO data = processMasterService.saveCategory(null, req);
        return Map.of("success", true, "message", "工艺大类已新增", "data", data);
    }

    @PostMapping("/process-categories/sync-mes")
    @RequirePermission("engineer:dict:process-manage")
    public Map<String, Object> syncMesProcessTypes() {
        MesMasterDataSyncService.SyncSubmitResult result = mesMasterDataSyncService.submitProcessTypesManual(currentUserId());
        return syncSubmitSummary(result);
    }

    @GetMapping("/process-categories/sync-runs/latest")
    @RequirePermission("engineer:dict:process-manage")
    public Map<String, Object> latestMesProcessTypeSyncRun() {
        MesMasterDataSyncRun run = mesMasterDataSyncService.getLatestRun(MesMasterDataSyncService.SYNC_TYPE_PROCESS_TYPE);
        return syncRunSummary(MesMasterDataSyncService.SYNC_TYPE_PROCESS_TYPE, run);
    }

    @GetMapping("/process-categories/sync-runs/{syncRunId}")
    @RequirePermission("engineer:dict:process-manage")
    public Map<String, Object> mesProcessTypeSyncRun(@PathVariable Long syncRunId) {
        MesMasterDataSyncRun run = mesMasterDataSyncService.getRun(MesMasterDataSyncService.SYNC_TYPE_PROCESS_TYPE, syncRunId);
        return syncRunSummary(MesMasterDataSyncService.SYNC_TYPE_PROCESS_TYPE, run);
    }

    @GetMapping("/process-categories/sync-schedule")
    @RequirePermission("engineer:dict:process-manage")
    public MesMasterDataSyncScheduleConfigDTO mesProcessTypeSyncSchedule() {
        return mesMasterDataSyncScheduleConfigService.getSetting(MesMasterDataSyncService.SYNC_TYPE_PROCESS_TYPE);
    }

    @PutMapping("/process-categories/sync-schedule")
    @RequirePermission("engineer:dict:process-manage")
    public MesMasterDataSyncScheduleConfigDTO updateMesProcessTypeSyncSchedule(
            @RequestBody MesMasterDataSyncScheduleConfigReq req) {
        return mesMasterDataSyncScheduleConfigService.updateSetting(MesMasterDataSyncService.SYNC_TYPE_PROCESS_TYPE, req);
    }

    @PutMapping("/process-categories/{id}")
    @RequirePermission("engineer:dict:process-manage")
    public Map<String, Object> updateProcessCategory(
            @PathVariable Long id,
            @RequestBody ProcessCategorySaveReq req) {
        ProcessCategoryDTO data = processMasterService.saveCategory(id, req);
        return Map.of("success", true, "message", "工艺大类已更新", "data", data);
    }

    @GetMapping("/process-details")
    @RequirePermission("engineer:dict:view")
    public List<ProcessDetailDTO> listProcessDetails(
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "enabledOnly", required = false) Boolean enabledOnly) {
        return processMasterService.listDetails(categoryId, enabledOnly);
    }

    @GetMapping("/process-details/page")
    @RequirePermission("engineer:dict:view")
    public PageResult<ProcessDetailDTO> pageProcessDetails(
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "enabledOnly", required = false) Boolean enabledOnly,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return processMasterService.pageDetails(categoryId, enabledOnly, keyword, page, pageSize);
    }

    @PostMapping("/process-details")
    @RequirePermission("engineer:dict:process-manage")
    public Map<String, Object> createProcessDetail(@RequestBody ProcessDetailSaveReq req) {
        ProcessDetailDTO data = processMasterService.saveDetail(null, req);
        return Map.of("success", true, "message", "具体工艺已新增", "data", data);
    }

    @PutMapping("/process-details/{id}")
    @RequirePermission("engineer:dict:process-manage")
    public Map<String, Object> updateProcessDetail(
            @PathVariable Long id,
            @RequestBody ProcessDetailSaveReq req) {
        ProcessDetailDTO data = processMasterService.saveDetail(id, req);
        return Map.of("success", true, "message", "具体工艺已更新", "data", data);
    }

    @DeleteMapping("/process-details/{id}")
    @RequirePermission("engineer:dict:process-manage")
    public Map<String, Object> deleteProcessDetail(@PathVariable Long id) {
        processMasterService.deleteDetail(id);
        return Map.of("success", true, "message", "具体工艺已删除");
    }

    private Long currentUserId() {
        AuthContext.SessionUser user = AuthContext.get();
        return user == null || user.id() == null ? null : user.id().longValue();
    }

    private Map<String, Object> syncSummary(String syncType, MesMasterDataSyncService.SyncResult result) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("success", result.success());
        summary.put("syncRunId", result.syncRunId());
        summary.put("syncType", syncType);
        summary.put("status", result.success() ? "SUCCESS" : "FAILED");
        summary.put("fetchedCount", result.fetchedCount());
        summary.put("validCount", result.validCount());
        summary.put("invalidCount", result.invalidCount());
        summary.put("duplicateCount", result.duplicateCount());
        summary.put("upsertedCount", result.upsertedCount());
        summary.put("disabledCount", result.disabledCount());
        summary.put("deletedCount", result.deletedCount());
        return summary;
    }

    private Map<String, Object> syncSubmitSummary(MesMasterDataSyncService.SyncSubmitResult result) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("success", result.success());
        summary.put("submitted", result.submitted());
        summary.put("alreadyRunning", result.alreadyRunning());
        summary.put("syncRunId", result.syncRunId());
        summary.put("syncType", result.syncType());
        summary.put("status", result.status());
        summary.put("message", result.message());
        return summary;
    }

    private Map<String, Object> syncRunSummary(String syncType, MesMasterDataSyncRun run) {
        Map<String, Object> summary = new LinkedHashMap<>();
        if (run == null) {
            summary.put("success", true);
            summary.put("syncRunId", null);
            summary.put("syncType", syncType);
            summary.put("status", "NONE");
            summary.put("message", "No MES master data sync run found");
            return summary;
        }
        summary.put("success", !"FAILED".equals(run.getStatus()));
        summary.put("syncRunId", run.getId());
        summary.put("syncType", run.getSyncType());
        summary.put("status", run.getStatus());
        summary.put("fetchedCount", count(run.getFetchedCount()));
        summary.put("validCount", count(run.getValidCount()));
        summary.put("invalidCount", count(run.getInvalidCount()));
        summary.put("duplicateCount", count(run.getDuplicateCount()));
        summary.put("upsertedCount", count(run.getUpsertedCount()));
        summary.put("disabledCount", count(run.getDisabledCount()));
        summary.put("deletedCount", count(run.getDeletedCount()));
        summary.put("message", run.getMessage());
        summary.put("startedAt", run.getStartedAt());
        summary.put("finishedAt", run.getFinishedAt());
        return summary;
    }

    private int count(Integer value) {
        return value == null ? 0 : value;
    }
}
