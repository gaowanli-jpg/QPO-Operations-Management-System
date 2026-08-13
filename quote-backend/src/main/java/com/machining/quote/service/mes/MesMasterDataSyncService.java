package com.machining.quote.service.mes;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.dto.mes.master.MesMaterialPageResp;
import com.machining.quote.dto.mes.master.MesProcessTypeListResp;
import com.machining.quote.entity.DictMaterial;
import com.machining.quote.entity.DictMaterialType;
import com.machining.quote.entity.DictProcessCategory;
import com.machining.quote.entity.DictProcessDetail;
import com.machining.quote.entity.MesMasterDataSyncRun;
import com.machining.quote.mapper.DictMaterialMapper;
import com.machining.quote.mapper.DictMaterialTypeMapper;
import com.machining.quote.mapper.DictProcessCategoryMapper;
import com.machining.quote.mapper.DictProcessDetailMapper;
import com.machining.quote.mapper.MesMasterDataSyncRunMapper;
import com.machining.quote.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class MesMasterDataSyncService {
    private static final Logger log = LoggerFactory.getLogger(MesMasterDataSyncService.class);

    private static final String SYNC_TYPE_PROCESS_TYPE = "MES_PROCESS_TYPE";
    private static final String SYNC_TYPE_MATERIAL = "MES_MATERIAL";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String SOURCE_MES = "MES";
    private static final String SOURCE_LOCAL = "LOCAL";
    private static final String AUDIT_ACTION_PROCESS_SYNC = "DICT_PROCESS_MES_SYNC";
    private static final String AUDIT_ACTION_MATERIAL_SYNC = "DICT_MATERIAL_MES_SYNC";
    private static final int MATERIAL_PAGE_SIZE = 200;
    private static final Map<String, String> KNOWN_MATERIAL_TYPE_NAMES = Map.of(
            "MATERIAL", "原材料",
            "PRODUCT", "成品",
            "SEMI", "半成品",
            "MES_UNKNOWN", "MES_UNKNOWN"
    );
    private static final Pattern AUTHORIZATION_FIELD = Pattern.compile(
            "(?i)authorization[^,;\\s}]*\\s*[:=]\\s*\\\"?Bearer\\s+[^\\s,\\\";}]+\\\"?"
    );
    private static final Pattern BEARER_VALUE = Pattern.compile("(?i)Bearer\\s+[^\\s,;}]+");
    private static final Pattern SENSITIVE_ASSIGNMENT = Pattern.compile(
            "(?i)\\b(token|access_token|refresh_token|client[_-]?secret|clientsecret|password|cookie)\\s*[:=]\\s*[^\\s,;}]+"
    );
    private static final Pattern RESPONSE_BODY = Pattern.compile("(?i)(response|body)\\s*[:=]\\s*\\{.*");

    @Autowired
    private MesMasterDataClient client;
    @Autowired
    private DictMaterialMapper materialMapper;
    @Autowired
    private DictMaterialTypeMapper materialTypeMapper;
    @Autowired
    private DictProcessCategoryMapper categoryMapper;
    @Autowired
    private DictProcessDetailMapper detailMapper;
    @Autowired
    private MesMasterDataSyncRunMapper syncRunMapper;
    @Autowired
    private AuditService auditService;

    @Transactional
    public SyncResult syncProcessTypesManual(Long createdBy) {
        LocalDateTime startedAt = LocalDateTime.now();
        MesMasterDataSyncRun run = startRun(SYNC_TYPE_PROCESS_TYPE, startedAt, createdBy);
        Counters counters = new Counters();
        try {
            List<MesProcessTypeListResp.Row> rows = client.listProcessTypes();
            List<ProcessTypeSource> sources = normalizeProcessSources(rows, counters);
            if (sources.isEmpty()) {
                throw new SyncFailure("MES process type sync returned no valid process types", counters);
            }
            PersistResult persisted = persistProcessTypes(sources, startedAt);
            counters.upsertedCount = persisted.upsertedCount();
            counters.disabledCount = persisted.disabledCount();
            finishRun(run, STATUS_SUCCESS, startedAt, counters, null);
            audit(run, counters, true, null, SYNC_TYPE_PROCESS_TYPE, AUDIT_ACTION_PROCESS_SYNC);
            log.info("MES process type sync completed syncRunId={} fetchedCount={} validCount={} invalidCount={} duplicateCount={} upsertedCount={} disabledCount={}",
                    run.getId(), counters.fetchedCount, counters.validCount, counters.invalidCount,
                    counters.duplicateCount, counters.upsertedCount, counters.disabledCount);
            return result(true, run.getId(), counters, null);
        } catch (RuntimeException error) {
            Counters failureCounters = error instanceof SyncFailure syncFailure ? syncFailure.counters() : counters;
            String message = safeMessage(error.getMessage());
            finishRun(run, STATUS_FAILED, startedAt, failureCounters, message);
            audit(run, failureCounters, false, message, SYNC_TYPE_PROCESS_TYPE, AUDIT_ACTION_PROCESS_SYNC);
            log.warn("MES process type sync failed syncRunId={} fetchedCount={} validCount={} invalidCount={} duplicateCount={} message={}",
                    run.getId(), failureCounters.fetchedCount, failureCounters.validCount,
                    failureCounters.invalidCount, failureCounters.duplicateCount, message);
            return result(false, run.getId(), failureCounters, message);
        }
    }

    @Transactional
    public SyncResult syncMaterialsManual(Long createdBy) {
        LocalDateTime startedAt = LocalDateTime.now();
        MesMasterDataSyncRun run = startRun(SYNC_TYPE_MATERIAL, startedAt, createdBy);
        Counters counters = new Counters();
        try {
            List<MesMaterialPageResp.Row> rows = fetchAllMaterialRows(counters);
            List<MaterialSource> sources = normalizeMaterialSources(rows, counters);
            if (sources.isEmpty()) {
                throw new SyncFailure("MES material sync returned no valid materials", counters);
            }
            PersistResult persisted = persistMaterials(sources, startedAt);
            counters.upsertedCount = persisted.upsertedCount();
            counters.disabledCount = persisted.disabledCount();
            finishRun(run, STATUS_SUCCESS, startedAt, counters, null);
            audit(run, counters, true, null, SYNC_TYPE_MATERIAL, AUDIT_ACTION_MATERIAL_SYNC);
            log.info("MES material sync completed syncRunId={} fetchedCount={} validCount={} invalidCount={} duplicateCount={} upsertedCount={} disabledCount={}",
                    run.getId(), counters.fetchedCount, counters.validCount, counters.invalidCount,
                    counters.duplicateCount, counters.upsertedCount, counters.disabledCount);
            return result(true, run.getId(), counters, null);
        } catch (RuntimeException error) {
            Counters failureCounters = error instanceof SyncFailure syncFailure ? syncFailure.counters() : counters;
            String message = safeMessage(error.getMessage());
            finishRun(run, STATUS_FAILED, startedAt, failureCounters, message);
            audit(run, failureCounters, false, message, SYNC_TYPE_MATERIAL, AUDIT_ACTION_MATERIAL_SYNC);
            log.warn("MES material sync failed syncRunId={} fetchedCount={} validCount={} invalidCount={} duplicateCount={} message={}",
                    run.getId(), failureCounters.fetchedCount, failureCounters.validCount,
                    failureCounters.invalidCount, failureCounters.duplicateCount, message);
            return result(false, run.getId(), failureCounters, message);
        }
    }

    private MesMasterDataSyncRun startRun(String syncType, LocalDateTime startedAt, Long createdBy) {
        MesMasterDataSyncRun run = new MesMasterDataSyncRun();
        run.setSyncType(syncType);
        run.setStatus(STATUS_RUNNING);
        run.setStartedAt(startedAt);
        run.setFetchedCount(0);
        run.setValidCount(0);
        run.setInvalidCount(0);
        run.setDuplicateCount(0);
        run.setUpsertedCount(0);
        run.setDisabledCount(0);
        run.setCreatedBy(createdBy);
        run.setCreatedAt(startedAt);
        syncRunMapper.insert(run);
        return run;
    }

    private List<ProcessTypeSource> normalizeProcessSources(List<MesProcessTypeListResp.Row> rows, Counters counters) {
        Map<String, ProcessTypeSource> byCode = new LinkedHashMap<>();
        List<MesProcessTypeListResp.Row> safeRows = rows == null ? List.of() : rows;
        counters.fetchedCount = safeRows.size();
        for (MesProcessTypeListResp.Row row : safeRows) {
            String code = trimToNull(row == null ? null : row.getCode());
            String name = trimToNull(row == null ? null : row.getName());
            if (code == null || name == null) {
                counters.invalidCount++;
                continue;
            }
            if (byCode.containsKey(code)) {
                counters.duplicateCount++;
                continue;
            }
            byCode.put(code, new ProcessTypeSource(
                    trimToNull(row.getId()),
                    code,
                    name,
                    safeRate(row.getHourPrice()),
                    enabledFromStatus(row.getStatus())));
        }
        counters.validCount = byCode.size();
        return byCode.values().stream().toList();
    }

    private PersistResult persistProcessTypes(List<ProcessTypeSource> sources, LocalDateTime syncedAt) {
        List<DictProcessCategory> existingRows = categoryMapper.selectList(new QueryWrapper<>());
        Map<String, DictProcessCategory> existingByCode = (existingRows == null ? List.<DictProcessCategory>of() : existingRows).stream()
                .filter(row -> trimToNull(row.getProcessCode()) != null)
                .collect(Collectors.toMap(
                        row -> trimToNull(row.getProcessCode()),
                        row -> row,
                        (first, ignored) -> first,
                        LinkedHashMap::new));

        int upsertedCount = 0;
        for (ProcessTypeSource source : sources) {
            DictProcessCategory existing = existingByCode.get(source.code());
            if (existing != null && !SOURCE_MES.equalsIgnoreCase(trimToNull(existing.getSourceType()))) {
                continue;
            }
            DictProcessCategory category = existing == null ? new DictProcessCategory() : existing;
            boolean created = existing == null;
            applyProcessSource(category, source, syncedAt, created);
            if (created) {
                categoryMapper.insert(category);
            } else {
                categoryMapper.updateById(category);
            }
            upsertedCount++;
            ensureDefaultDetail(category, source.name(), syncedAt);
        }

        Set<String> returnedCodes = sources.stream().map(ProcessTypeSource::code).collect(Collectors.toSet());
        int disabledCount = 0;
        for (DictProcessCategory existing : existingByCode.values()) {
            if (SOURCE_MES.equalsIgnoreCase(trimToNull(existing.getSourceType()))
                    && !returnedCodes.contains(trimToNull(existing.getProcessCode()))
                    && Boolean.TRUE.equals(existing.getEnabled())) {
                existing.setEnabled(false);
                existing.setMesSyncedAt(syncedAt);
                existing.setUpdatedAt(syncedAt);
                categoryMapper.updateById(existing);
                disabledCount++;
            }
        }
        return new PersistResult(upsertedCount, disabledCount);
    }

    private void applyProcessSource(
            DictProcessCategory category,
            ProcessTypeSource source,
            LocalDateTime syncedAt,
            boolean created) {
        category.setProcessCode(source.code());
        category.setCategoryName(source.name());
        category.setHourlyRate(source.hourlyRate());
        category.setSourceType(SOURCE_MES);
        category.setMesExternalId(source.mesExternalId());
        category.setEnabled(source.enabled());
        category.setMesSyncedAt(syncedAt);
        category.setUpdatedAt(syncedAt);
        if (created) {
            category.setCreatedAt(syncedAt);
            category.setSortNo(0);
        }
    }

    private void ensureDefaultDetail(DictProcessCategory category, String detailName, LocalDateTime syncedAt) {
        if (category.getId() == null || trimToNull(detailName) == null) {
            return;
        }
        QueryWrapper<DictProcessDetail> query = new QueryWrapper<>();
        query.eq("category_id", category.getId());
        List<DictProcessDetail> details = detailMapper.selectList(query);
        DictProcessDetail existing = (details == null ? List.<DictProcessDetail>of() : details).stream()
                .filter(detail -> Objects.equals(trimToNull(detail.getDetailName()), detailName))
                .findFirst()
                .orElse(null);
        if (existing == null) {
            DictProcessDetail created = new DictProcessDetail();
            created.setCategoryId(category.getId());
            created.setDetailName(detailName);
            created.setDetailRemarks("");
            created.setEnabled(true);
            created.setSortNo(0);
            created.setCreatedAt(syncedAt);
            created.setUpdatedAt(syncedAt);
            detailMapper.insert(created);
            return;
        }
        if (!Boolean.TRUE.equals(existing.getEnabled())) {
            existing.setEnabled(true);
            existing.setUpdatedAt(syncedAt);
            detailMapper.updateById(existing);
        }
    }

    private List<MesMaterialPageResp.Row> fetchAllMaterialRows(Counters counters) {
        List<MesMaterialPageResp.Row> rows = new ArrayList<>();
        int pageNo = 1;
        while (true) {
            MesMaterialPageResp.Data page = client.pageMaterials(pageNo, MATERIAL_PAGE_SIZE);
            List<MesMaterialPageResp.Row> pageRows = page == null || page.getList() == null ? List.of() : page.getList();
            rows.addAll(pageRows);
            Long total = page == null ? null : page.getTotal();
            if (pageRows.isEmpty()
                    || pageRows.size() < MATERIAL_PAGE_SIZE
                    || (total != null && total >= 0 && rows.size() >= total)) {
                break;
            }
            pageNo++;
        }
        counters.fetchedCount = rows.size();
        return rows;
    }

    private List<MaterialSource> normalizeMaterialSources(List<MesMaterialPageResp.Row> rows, Counters counters) {
        Map<String, MaterialSource> byCode = new LinkedHashMap<>();
        List<MesMaterialPageResp.Row> safeRows = rows == null ? List.of() : rows;
        counters.fetchedCount = safeRows.size();
        for (MesMaterialPageResp.Row row : safeRows) {
            String code = trimToNull(row == null ? null : row.getCode());
            String name = trimToNull(row == null ? null : row.getName());
            if (code == null || name == null) {
                counters.invalidCount++;
                continue;
            }
            String lookupCode = keyCode(code);
            if (byCode.containsKey(lookupCode)) {
                counters.duplicateCount++;
                continue;
            }
            String materialTypeCode = materialTypeCode(row.getMaterialType());
            byCode.put(lookupCode, new MaterialSource(
                    trimToNull(row.getId()),
                    code,
                    lookupCode,
                    name,
                    materialTypeCode,
                    materialTypeName(materialTypeCode, row.getMaterialType()),
                    firstNonBlank(row.getStandard(), row.getAttribute(), row.getCategoryId()),
                    toDouble(row.getDensity()),
                    decimalText(row.getDensity()),
                    trimToNull(row.getStandard()),
                    trimToNull(row.getDocNum()),
                    decimalText(row.getWeight()),
                    trimToNull(row.getPurchaseType()),
                    trimToNull(row.getProductionType()),
                    enabledFromStatus(row.getStatus())));
        }
        counters.validCount = byCode.size();
        return byCode.values().stream().toList();
    }

    private PersistResult persistMaterials(List<MaterialSource> sources, LocalDateTime syncedAt) {
        Map<String, Long> typeIds = ensureMaterialTypes(sources, syncedAt);
        List<DictMaterial> existingRows = materialMapper.selectList(new QueryWrapper<>());
        Map<String, DictMaterial> existingByCode = (existingRows == null ? List.<DictMaterial>of() : existingRows).stream()
                .filter(row -> keyCode(row.getMaterialCode()) != null)
                .collect(Collectors.toMap(
                        row -> keyCode(row.getMaterialCode()),
                        row -> row,
                        (first, ignored) -> first,
                        LinkedHashMap::new));

        int upsertedCount = 0;
        for (MaterialSource source : sources) {
            DictMaterial existing = existingByCode.get(source.lookupCode());
            DictMaterial material = existing == null ? new DictMaterial() : existing;
            boolean created = existing == null;
            applyMaterialSource(material, source, typeIds.get(source.materialTypeCode()), syncedAt, created);
            if (created) {
                materialMapper.insert(material);
            } else {
                materialMapper.updateById(material);
            }
            upsertedCount++;
        }

        Set<String> returnedCodes = sources.stream().map(MaterialSource::lookupCode).collect(Collectors.toSet());
        int disabledCount = 0;
        for (DictMaterial existing : existingByCode.values()) {
            if (SOURCE_MES.equalsIgnoreCase(trimToNull(existing.getSourceType()))
                    && !returnedCodes.contains(keyCode(existing.getMaterialCode()))
                    && Boolean.TRUE.equals(existing.getEnabled())) {
                existing.setEnabled(false);
                existing.setMesSyncedAt(syncedAt);
                existing.setUpdatedAt(syncedAt);
                materialMapper.updateById(existing);
                disabledCount++;
            }
        }
        return new PersistResult(upsertedCount, disabledCount);
    }

    private Map<String, Long> ensureMaterialTypes(List<MaterialSource> sources, LocalDateTime syncedAt) {
        List<DictMaterialType> existingRows = materialTypeMapper.selectList(new QueryWrapper<>());
        Map<String, DictMaterialType> existingByCode = (existingRows == null ? List.<DictMaterialType>of() : existingRows).stream()
                .filter(row -> keyCode(row.getTypeCode()) != null)
                .collect(Collectors.toMap(
                        row -> keyCode(row.getTypeCode()),
                        row -> row,
                        (first, ignored) -> first,
                        LinkedHashMap::new));
        Map<String, DictMaterialType> existingByName = (existingRows == null ? List.<DictMaterialType>of() : existingRows).stream()
                .filter(row -> trimToNull(row.getTypeName()) != null)
                .collect(Collectors.toMap(
                        row -> trimToNull(row.getTypeName()),
                        row -> row,
                        (first, ignored) -> first,
                        LinkedHashMap::new));
        Map<String, String> requiredTypes = new LinkedHashMap<>();
        requiredTypes.put("MATERIAL", KNOWN_MATERIAL_TYPE_NAMES.get("MATERIAL"));
        requiredTypes.put("PRODUCT", KNOWN_MATERIAL_TYPE_NAMES.get("PRODUCT"));
        requiredTypes.put("SEMI", KNOWN_MATERIAL_TYPE_NAMES.get("SEMI"));
        for (MaterialSource source : sources) {
            requiredTypes.putIfAbsent(source.materialTypeCode(), source.materialTypeName());
        }

        Map<String, Long> ids = new LinkedHashMap<>();
        for (Map.Entry<String, String> required : requiredTypes.entrySet()) {
            String code = required.getKey();
            String name = firstNonBlank(required.getValue(), code);
            DictMaterialType existing = existingByCode.get(code);
            if (existing == null) {
                existing = existingByName.get(name);
            }
            if (existing == null) {
                DictMaterialType created = new DictMaterialType();
                created.setTypeCode(code);
                created.setTypeName(name);
                created.setRemarks("");
                created.setEnabled(true);
                created.setSortNo(0);
                created.setCreatedAt(syncedAt);
                created.setUpdatedAt(syncedAt);
                materialTypeMapper.insert(created);
                existingByCode.put(code, created);
                existingByName.put(name, created);
                ids.put(code, created.getId());
                continue;
            }
            if (!Objects.equals(existing.getTypeCode(), code)
                    || !Objects.equals(existing.getTypeName(), name)
                    || !Boolean.TRUE.equals(existing.getEnabled())) {
                existing.setTypeCode(code);
                existing.setTypeName(name);
                existing.setEnabled(true);
                if (existing.getSortNo() == null) {
                    existing.setSortNo(0);
                }
                existing.setUpdatedAt(syncedAt);
                materialTypeMapper.updateById(existing);
            }
            existingByCode.put(code, existing);
            existingByName.put(name, existing);
            ids.put(code, existing.getId());
        }
        return ids;
    }

    private void applyMaterialSource(
            DictMaterial material,
            MaterialSource source,
            Long materialTypeId,
            LocalDateTime syncedAt,
            boolean created) {
        material.setMaterialCode(source.code());
        material.setMaterialName(source.name());
        material.setMaterialTypeId(materialTypeId);
        material.setMaterialCategory(source.materialCategory());
        material.setDensity(source.density());
        if (source.density() != null) {
            if (created || material.getPlateDensity() == null) {
                material.setPlateDensity(source.density());
            }
            if (created || material.getBarDensity() == null) {
                material.setBarDensity(source.density());
            }
            if (created || material.getTubeDensity() == null) {
                material.setTubeDensity(source.density());
            }
        }
        material.setSourceType(SOURCE_MES);
        material.setMesExternalId(source.mesExternalId());
        material.setMesMaterialType(source.materialTypeCode());
        material.setMesPurchaseType(source.purchaseType());
        material.setMesProductionType(source.productionType());
        material.setMesDocNum(source.docNum());
        material.setMesStandard(source.standard());
        material.setMesWeight(source.weightRaw());
        material.setMesDensityRaw(source.densityRaw());
        material.setMesSyncedAt(syncedAt);
        material.setEnabled(source.enabled());
        material.setUpdatedAt(syncedAt);
        if (created) {
            material.setSortNo(0);
            material.setCreatedAt(syncedAt);
        }
    }

    private void finishRun(
            MesMasterDataSyncRun run,
            String status,
            LocalDateTime startedAt,
            Counters counters,
            String message) {
        run.setStatus(status);
        run.setStartedAt(startedAt);
        run.setFinishedAt(LocalDateTime.now());
        run.setFetchedCount(counters.fetchedCount);
        run.setValidCount(counters.validCount);
        run.setInvalidCount(counters.invalidCount);
        run.setDuplicateCount(counters.duplicateCount);
        run.setUpsertedCount(counters.upsertedCount);
        run.setDisabledCount(counters.disabledCount);
        run.setMessage(message == null ? "OK" : safeMessage(message));
        syncRunMapper.updateById(run);
    }

    private void audit(
            MesMasterDataSyncRun run,
            Counters counters,
            boolean success,
            String message,
            String syncType,
            String auditAction) {
        auditService.record(
                auditAction,
                "mes_master_data_sync_run",
                run.getId(),
                null,
                auditSummary(run, counters, success, message, syncType));
    }

    private Map<String, Object> auditSummary(
            MesMasterDataSyncRun run,
            Counters counters,
            boolean success,
            String message,
            String syncType) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("syncRunId", run.getId());
        summary.put("syncType", syncType);
        summary.put("success", success);
        summary.put("fetchedCount", counters.fetchedCount);
        summary.put("validCount", counters.validCount);
        summary.put("invalidCount", counters.invalidCount);
        summary.put("duplicateCount", counters.duplicateCount);
        summary.put("upsertedCount", counters.upsertedCount);
        summary.put("disabledCount", counters.disabledCount);
        if (trimToNull(message) != null) {
            summary.put("message", safeMessage(message));
        }
        return summary;
    }

    private SyncResult result(boolean success, Long runId, Counters counters, String message) {
        return new SyncResult(
                success,
                runId,
                counters.fetchedCount,
                counters.validCount,
                counters.invalidCount,
                counters.duplicateCount,
                counters.upsertedCount,
                counters.disabledCount,
                message);
    }

    private BigDecimal safeRate(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        return value;
    }

    private boolean enabledFromStatus(String status) {
        String value = trimToNull(status);
        if (value == null) {
            return true;
        }
        String normalized = value.toUpperCase(Locale.ROOT);
        if (Set.of("1", "FALSE", "N", "NO", "DISABLE", "DISABLED", "INACTIVE", "STOP", "STOPPED",
                        "OFF", "CLOSE", "CLOSED", "关闭", "停用", "禁用")
                .contains(normalized)) {
            return false;
        }
        return true;
    }

    private String materialTypeCode(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return "MES_UNKNOWN";
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    private String materialTypeName(String typeCode, String rawValue) {
        String knownName = KNOWN_MATERIAL_TYPE_NAMES.get(typeCode);
        if (knownName != null) {
            return knownName;
        }
        return firstNonBlank(rawValue, typeCode);
    }

    private Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    private String decimalText(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }

    private String keyCode(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private String safeMessage(String value) {
        if (value == null || value.isBlank()) {
            return "MES master data sync failed";
        }
        String text = value.replaceAll("\\s+", " ").trim();
        text = RESPONSE_BODY.matcher(text).replaceAll("[REDACTED]");
        text = AUTHORIZATION_FIELD.matcher(text).replaceAll("[REDACTED]");
        text = BEARER_VALUE.matcher(text).replaceAll("[REDACTED]");
        text = SENSITIVE_ASSIGNMENT.matcher(text).replaceAll("[REDACTED]");
        return text.length() <= 200 ? text : text.substring(0, 200) + "...";
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record SyncResult(
            boolean success,
            Long syncRunId,
            int fetchedCount,
            int validCount,
            int invalidCount,
            int duplicateCount,
            int upsertedCount,
            int disabledCount,
            String message) {
    }

    private static class Counters {
        private int fetchedCount;
        private int validCount;
        private int invalidCount;
        private int duplicateCount;
        private int upsertedCount;
        private int disabledCount;
    }

    private record ProcessTypeSource(
            String mesExternalId,
            String code,
            String name,
            BigDecimal hourlyRate,
            boolean enabled) {
    }

    private record MaterialSource(
            String mesExternalId,
            String code,
            String lookupCode,
            String name,
            String materialTypeCode,
            String materialTypeName,
            String materialCategory,
            Double density,
            String densityRaw,
            String standard,
            String docNum,
            String weightRaw,
            String purchaseType,
            String productionType,
            boolean enabled) {
    }

    private record PersistResult(int upsertedCount, int disabledCount) {
    }

    private static class SyncFailure extends RuntimeException {
        private final Counters counters;

        private SyncFailure(String message, Counters counters) {
            super(message);
            this.counters = counters;
        }

        private Counters counters() {
            return counters;
        }
    }
}
