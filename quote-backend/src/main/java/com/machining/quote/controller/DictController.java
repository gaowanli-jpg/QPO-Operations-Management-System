package com.machining.quote.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.config.AuthContext;
import com.machining.quote.config.RequirePermission;
import com.machining.quote.dto.MaterialMasterDTO;
import com.machining.quote.dto.MaterialMasterSaveReq;
import com.machining.quote.dto.MaterialTypeDTO;
import com.machining.quote.dto.MaterialTypeSaveReq;
import com.machining.quote.dto.MesMasterDataSyncScheduleConfigDTO;
import com.machining.quote.dto.MesMasterDataSyncScheduleConfigReq;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.PricingMarkupFeeSaveReq;
import com.machining.quote.entity.DictExtraFeeItem;
import com.machining.quote.entity.DictPricingMarkupFee;
import com.machining.quote.entity.MesMasterDataSyncRun;
import com.machining.quote.mapper.DictExtraFeeItemMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.MaterialMasterService;
import com.machining.quote.service.PricingMarkupFeeService;
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
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/dict")
public class DictController {
    @Autowired
    private MaterialMasterService materialMasterService;
    @Autowired
    private PricingMarkupFeeService pricingMarkupFeeService;
    @Autowired
    private DictExtraFeeItemMapper extraFeeItemMapper;
    @Autowired
    private AuditService auditService;
    @Autowired
    private MesMasterDataSyncService mesMasterDataSyncService;
    @Autowired
    private MesMasterDataSyncScheduleConfigService mesMasterDataSyncScheduleConfigService;

    @GetMapping("/materials")
    @RequirePermission("engineer:dict:view")
    public List<MaterialMasterDTO> listMaterials(
            @RequestParam(value = "enabledOnly", required = false) Boolean enabledOnly) {
        return materialMasterService.listMaterials(enabledOnly);
    }

    @GetMapping("/materials/page")
    @RequirePermission("engineer:dict:view")
    public PageResult<MaterialMasterDTO> pageMaterials(
            @RequestParam(value = "enabledOnly", required = false) Boolean enabledOnly,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return materialMasterService.pageMaterials(enabledOnly, keyword, page, pageSize);
    }

    @PostMapping("/materials")
    @RequirePermission("engineer:dict:material-manage")
    public Map<String, Object> createMaterial(@RequestBody MaterialMasterSaveReq material) {
        MaterialMasterDTO data = materialMasterService.saveMaterial(null, material);
        return Map.of("success", true, "message", "材料已新增", "data", data);
    }

    @PostMapping("/materials/sync-mes")
    @RequirePermission("engineer:dict:material-manage")
    public Map<String, Object> syncMesMaterials() {
        MesMasterDataSyncService.SyncSubmitResult result = mesMasterDataSyncService.submitMaterialsManual(currentUserId());
        return syncSubmitSummary(result);
    }

    @GetMapping("/materials/sync-runs/latest")
    @RequirePermission("engineer:dict:material-manage")
    public Map<String, Object> latestMesMaterialSyncRun() {
        MesMasterDataSyncRun run = mesMasterDataSyncService.getLatestRun(MesMasterDataSyncService.SYNC_TYPE_MATERIAL);
        return syncRunSummary(MesMasterDataSyncService.SYNC_TYPE_MATERIAL, run);
    }

    @GetMapping("/materials/sync-runs/{syncRunId}")
    @RequirePermission("engineer:dict:material-manage")
    public Map<String, Object> mesMaterialSyncRun(@PathVariable Long syncRunId) {
        MesMasterDataSyncRun run = mesMasterDataSyncService.getRun(MesMasterDataSyncService.SYNC_TYPE_MATERIAL, syncRunId);
        return syncRunSummary(MesMasterDataSyncService.SYNC_TYPE_MATERIAL, run);
    }

    @GetMapping("/materials/sync-schedule")
    @RequirePermission("engineer:dict:material-manage")
    public MesMasterDataSyncScheduleConfigDTO mesMaterialSyncSchedule() {
        return mesMasterDataSyncScheduleConfigService.getSetting(MesMasterDataSyncService.SYNC_TYPE_MATERIAL);
    }

    @PutMapping("/materials/sync-schedule")
    @RequirePermission("engineer:dict:material-manage")
    public MesMasterDataSyncScheduleConfigDTO updateMesMaterialSyncSchedule(
            @RequestBody MesMasterDataSyncScheduleConfigReq req) {
        return mesMasterDataSyncScheduleConfigService.updateSetting(MesMasterDataSyncService.SYNC_TYPE_MATERIAL, req);
    }

    @PutMapping("/materials/{id}")
    @RequirePermission("engineer:dict:material-manage")
    public Map<String, Object> updateMaterial(@PathVariable Long id, @RequestBody MaterialMasterSaveReq material) {
        MaterialMasterDTO data = materialMasterService.saveMaterial(id, material);
        return Map.of("success", true, "message", "材料已更新", "data", data);
    }

    @DeleteMapping("/materials/{id}")
    @RequirePermission("engineer:dict:material-manage")
    public Map<String, Object> deleteMaterial(@PathVariable Long id) {
        materialMasterService.deleteMaterial(id);
        return Map.of("success", true, "message", "材料已停用");
    }

    @GetMapping("/material-types")
    @RequirePermission("engineer:dict:view")
    public List<MaterialTypeDTO> listMaterialTypes(
            @RequestParam(value = "enabledOnly", required = false) Boolean enabledOnly) {
        return materialMasterService.listMaterialTypes(enabledOnly);
    }

    @PostMapping("/material-types")
    @RequirePermission("engineer:dict:material-manage")
    public Map<String, Object> createMaterialType(@RequestBody MaterialTypeSaveReq req) {
        MaterialTypeDTO data = materialMasterService.saveMaterialType(null, req);
        return Map.of("success", true, "message", "材料类型已新增", "data", data);
    }

    @PutMapping("/material-types/{id}")
    @RequirePermission("engineer:dict:material-manage")
    public Map<String, Object> updateMaterialType(@PathVariable Long id, @RequestBody MaterialTypeSaveReq req) {
        MaterialTypeDTO data = materialMasterService.saveMaterialType(id, req);
        return Map.of("success", true, "message", "材料类型已更新", "data", data);
    }

    @DeleteMapping("/material-types/{id}")
    @RequirePermission("engineer:dict:material-manage")
    public Map<String, Object> deleteMaterialType(@PathVariable Long id) {
        materialMasterService.deleteMaterialType(id);
        return Map.of("success", true, "message", "材料类型已删除或停用");
    }

    @GetMapping("/pricing-markup-fees")
    @RequirePermission("engineer:dict:view")
    public List<DictPricingMarkupFee> listPricingMarkupFees(
            @RequestParam("pricingScope") String pricingScope,
            @RequestParam("feeCategory") String feeCategory) {
        return pricingMarkupFeeService.listFees(pricingScope, feeCategory);
    }

    @PostMapping("/pricing-markup-fees")
    @RequirePermission("engineer:dict:fee-manage")
    public Map<String, Object> createPricingMarkupFee(@RequestBody PricingMarkupFeeSaveReq req) {
        DictPricingMarkupFee data = pricingMarkupFeeService.saveFee(null, req);
        return Map.of("success", true, "message", "pricing markup fee created", "data", data);
    }

    @PutMapping("/pricing-markup-fees/{id}")
    @RequirePermission("engineer:dict:fee-manage")
    public Map<String, Object> updatePricingMarkupFee(@PathVariable Long id, @RequestBody PricingMarkupFeeSaveReq req) {
        DictPricingMarkupFee data = pricingMarkupFeeService.saveFee(id, req);
        return Map.of("success", true, "message", "pricing markup fee updated", "data", data);
    }

    @DeleteMapping("/pricing-markup-fees/{id}")
    @RequirePermission("engineer:dict:fee-manage")
    public Map<String, Object> deletePricingMarkupFee(@PathVariable Long id) {
        pricingMarkupFeeService.deleteFee(id);
        return Map.of("success", true, "message", "pricing markup fee deleted");
    }

    @GetMapping("/extra-fee-items")
    @RequirePermission("engineer:dict:view")
    public List<DictExtraFeeItem> listExtraFeeItems() {
        QueryWrapper<DictExtraFeeItem> qw = new QueryWrapper<>();
        qw.eq("enabled", true).orderByAsc("sort_no").orderByAsc("id");
        return extraFeeItemMapper.selectList(qw);
    }

    @GetMapping("/extra-fee-items/all")
    @RequirePermission("engineer:dict:view")
    public List<DictExtraFeeItem> listAllExtraFeeItems() {
        QueryWrapper<DictExtraFeeItem> qw = new QueryWrapper<>();
        qw.orderByAsc("sort_no").orderByAsc("id");
        return extraFeeItemMapper.selectList(qw);
    }

    @GetMapping("/extra-fee-items/page")
    @RequirePermission("engineer:dict:view")
    public PageResult<DictExtraFeeItem> pageExtraFeeItems(
            @RequestParam(value = "includeDisabled", required = false) Boolean includeDisabled,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        int safePage = normalizePage(page);
        int safePageSize = normalizePageSize(pageSize);
        QueryWrapper<DictExtraFeeItem> qw = new QueryWrapper<>();
        applyExtraFeeFilters(qw, includeDisabled, keyword);
        Long total = extraFeeItemMapper.selectCount(qw);
        if (total == null || total == 0) {
            return PageResult.of(List.of(), 0, safePage, safePageSize);
        }
        qw.orderByAsc("sort_no").orderByAsc("id");
        qw.last("LIMIT " + safePageSize + " OFFSET " + ((safePage - 1L) * safePageSize));
        return PageResult.of(extraFeeItemMapper.selectList(qw), total, safePage, safePageSize);
    }

    @PostMapping("/extra-fee-items")
    @RequirePermission("engineer:dict:process-manage")
    public Map<String, Object> createExtraFeeItem(@RequestBody DictExtraFeeItem item) {
        normalizeExtraFeeItem(item, null);
        ensureExtraFeeCodeUnique(item.getFeeCode(), null);
        extraFeeItemMapper.insert(item);
        auditService.record("DICT_EXTRA_FEE_CREATE", "dict_extra_fee_item", item.getId(), null, item);
        return Map.of("success", true, "message", "非工时费用工艺已新增", "data", item);
    }

    @PutMapping("/extra-fee-items/{id}")
    @RequirePermission("engineer:dict:process-manage")
    public Map<String, Object> updateExtraFeeItem(@PathVariable Integer id, @RequestBody DictExtraFeeItem item) {
        DictExtraFeeItem before = extraFeeItemMapper.selectById(id);
        if (before == null) {
            throw new RuntimeException("非工时费用工艺不存在");
        }
        item.setId(id);
        normalizeExtraFeeItem(item, before);
        ensureExtraFeeCodeUnique(item.getFeeCode(), id);
        extraFeeItemMapper.updateById(item);
        auditService.record("DICT_EXTRA_FEE_UPDATE", "dict_extra_fee_item", id, before, item);
        return Map.of("success", true, "message", "非工时费用工艺已更新", "data", item);
    }

    @DeleteMapping("/extra-fee-items/{id}")
    @RequirePermission("engineer:dict:process-manage")
    public Map<String, Object> deleteExtraFeeItem(@PathVariable Integer id) {
        DictExtraFeeItem before = extraFeeItemMapper.selectById(id);
        extraFeeItemMapper.deleteById(id);
        auditService.record("DICT_EXTRA_FEE_DELETE", "dict_extra_fee_item", id, before, null);
        return Map.of("success", true, "message", "非工时费用工艺已删除");
    }

    private void normalizeExtraFeeItem(DictExtraFeeItem item, DictExtraFeeItem before) {
        if (item == null) {
            throw new RuntimeException("非工时费用工艺请求不能为空");
        }
        String normalizedCode = normalizeExtraFeeCode(item.getFeeCode());
        String existingCode = before == null ? null : normalizeExtraFeeCode(before.getFeeCode());
        if (isBlank(normalizedCode)) {
            throw new RuntimeException("工艺编码不能为空");
        }
        if (!isBlank(existingCode) && !existingCode.equals(normalizedCode)) {
            throw new RuntimeException("工艺编码是非工时费用稳定身份，已有编码不允许修改");
        }
        item.setFeeCode(isBlank(existingCode) ? normalizedCode : existingCode);
        if (isBlank(item.getFeeName())) {
            throw new RuntimeException("工艺名称不能为空");
        }
        item.setFeeName(item.getFeeName().trim());
        if (item.getSortNo() == null) {
            item.setSortNo(0);
        }
        if (item.getEnabled() == null) {
            item.setEnabled(true);
        }
        if (item.getRemarks() == null) {
            item.setRemarks("");
        }
    }

    private void ensureExtraFeeCodeUnique(String feeCode, Integer currentId) {
        QueryWrapper<DictExtraFeeItem> query = new QueryWrapper<>();
        query.eq("fee_code", feeCode);
        if (currentId != null) {
            query.ne("id", currentId);
        }
        Long count = extraFeeItemMapper.selectCount(query);
        if (count != null && count > 0) {
            throw new RuntimeException("工艺编码已存在");
        }
    }

    private void applyExtraFeeFilters(QueryWrapper<DictExtraFeeItem> qw, Boolean includeDisabled, String keyword) {
        if (!Boolean.TRUE.equals(includeDisabled)) {
            qw.eq("enabled", true);
        }
        if (isBlank(keyword)) {
            return;
        }
        String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
        qw.and(wrapper -> wrapper
                .apply("LOWER(fee_code) LIKE {0}", pattern)
                .or()
                .apply("LOWER(fee_name) LIKE {0}", pattern)
                .or()
                .apply("LOWER(remarks) LIKE {0}", pattern));
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 20;
        }
        return Math.min(pageSize, 100);
    }

    private String normalizeExtraFeeCode(String value) {
        if (isBlank(value)) {
            return "";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9_-]{1,80}")) {
            throw new RuntimeException("工艺编码只允许 1-80 位大写字母、数字、下划线或中划线");
        }
        return normalized;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
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
