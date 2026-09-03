package com.machining.quote.service.mes;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.dto.mes.master.MesMaterialPageResp;
import com.machining.quote.dto.mes.master.MesProcessTypeListResp;
import com.machining.quote.entity.DictMaterial;
import com.machining.quote.entity.DictMaterialPriceSpec;
import com.machining.quote.entity.DictMaterialType;
import com.machining.quote.entity.DictProcessCategory;
import com.machining.quote.entity.MesMasterDataSyncRun;
import com.machining.quote.mapper.DictMaterialMapper;
import com.machining.quote.mapper.DictMaterialPriceSpecMapper;
import com.machining.quote.mapper.DictMaterialTypeMapper;
import com.machining.quote.mapper.DictProcessCategoryMapper;
import com.machining.quote.mapper.MesMasterDataSyncRunMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.util.MaterialPriceSpecSupport;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class MesMasterDataSyncService {
    private static final Logger log = LoggerFactory.getLogger(MesMasterDataSyncService.class);

    public static final String SYNC_TYPE_PROCESS_TYPE = "MES_PROCESS_TYPE";
    public static final String SYNC_TYPE_MATERIAL = "MES_MATERIAL";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String SOURCE_MES = "MES";
    private static final String SOURCE_LOCAL = "LOCAL";
    private static final String AUDIT_ACTION_PROCESS_SYNC = "DICT_PROCESS_MES_SYNC";
    private static final String AUDIT_ACTION_MATERIAL_SYNC = "DICT_MATERIAL_MES_SYNC";
    private static final int MATERIAL_PAGE_SIZE = 200;
    private static final int MATERIAL_DELETE_SCAN_BATCH_SIZE = 500;
    private static final BigDecimal MES_DENSITY_G_PER_CM3_TO_KG_PER_M3 = BigDecimal.valueOf(1000);
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
    private DictMaterialPriceSpecMapper materialPriceSpecMapper;
    @Autowired
    private DictMaterialTypeMapper materialTypeMapper;
    @Autowired
    private DictProcessCategoryMapper categoryMapper;
    @Autowired
    private MesMasterDataSyncRunMapper syncRunMapper;
    @Autowired
    private AuditService auditService;
    @Autowired
    private MesMasterDataSyncScheduleConfigService scheduleConfigService;
    @Autowired(required = false)
    private PlatformTransactionManager transactionManager;
    @Autowired(required = false)
    @Qualifier("systemTaskExecutor")
    private TaskExecutor taskExecutor;

    private LocalDateTime mesMasterDataSyncSchedulerStartedAt;
    private final AtomicBoolean scheduledMaterialSyncRunning = new AtomicBoolean(false);
    private final AtomicBoolean scheduledProcessTypeSyncRunning = new AtomicBoolean(false);

    @PostConstruct
    public void initMesMasterDataSyncSchedulerStartedAt() {
        mesMasterDataSyncSchedulerStartedAt = LocalDateTime.now();
    }

    @Scheduled(fixedDelayString = "${erp.mes.master-data-sync.check-ms:60000}")
    public void syncMesMasterDataScheduled() {
        if (scheduleConfigService == null) {
            return;
        }
        if (mesMasterDataSyncSchedulerStartedAt == null) {
            mesMasterDataSyncSchedulerStartedAt = LocalDateTime.now();
        }
        runScheduledSync(SYNC_TYPE_MATERIAL, scheduledMaterialSyncRunning);
        runScheduledSync(SYNC_TYPE_PROCESS_TYPE, scheduledProcessTypeSyncRunning);
    }

    private void runScheduledSync(String syncType, AtomicBoolean runningGuard) {
        if (!scheduleConfigService.shouldRun(syncType, mesMasterDataSyncSchedulerStartedAt)) {
            return;
        }
        if (!runningGuard.compareAndSet(false, true)) {
            log.info("MES master data scheduled sync skipped because previous run is still active syncType={}", syncType);
            return;
        }
        try {
            SyncSubmitResult result = submitScheduled(syncType);
            if (!result.success()) {
                scheduleConfigService.updateScheduledResult(syncType,
                        new SyncResult(false, result.syncRunId(), 0, 0, 0, 0, 0, 0, 0, result.message()));
                log.warn("MES master data scheduled sync submit failed syncType={} syncRunId={} message={}",
                        syncType, result.syncRunId(), result.message());
            }
        } catch (RuntimeException error) {
            String message = safeMessage(error.getMessage());
            scheduleConfigService.updateScheduledResult(syncType,
                    new SyncResult(false, null, 0, 0, 0, 0, 0, 0, 0, message));
            log.warn("MES master data scheduled sync failed before run record syncType={} message={}", syncType, message, error);
        } finally {
            runningGuard.set(false);
        }
    }

    public SyncSubmitResult submitProcessTypesManual(Long createdBy) {
        return submitSync(
                SYNC_TYPE_PROCESS_TYPE,
                createdBy,
                "MES工艺主数据同步已提交",
                "已有MES工艺主数据同步正在执行",
                false);
    }

    public SyncSubmitResult submitMaterialsManual(Long createdBy) {
        return submitSync(
                SYNC_TYPE_MATERIAL,
                createdBy,
                "MES材料主数据同步已提交",
                "已有MES材料主数据同步正在执行",
                false);
    }

    public SyncSubmitResult submitScheduled(String syncType) {
        return submitSync(
                syncType,
                null,
                "MES master data scheduled sync submitted",
                "MES master data sync already running",
                true);
    }

    public MesMasterDataSyncRun getLatestRun(String syncType) {
        String normalizedType = validateSyncType(syncType);
        QueryWrapper<MesMasterDataSyncRun> query = new QueryWrapper<>();
        query.eq("sync_type", normalizedType).orderByDesc("started_at").orderByDesc("id").last("LIMIT 1");
        return syncRunMapper.selectOne(query);
    }

    public MesMasterDataSyncRun getRun(String syncType, Long syncRunId) {
        String normalizedType = validateSyncType(syncType);
        if (syncRunId == null) {
            throw new IllegalArgumentException("syncRunId is required");
        }
        MesMasterDataSyncRun run = syncRunMapper.selectById(syncRunId);
        if (run == null || !normalizedType.equals(run.getSyncType())) {
            throw new IllegalArgumentException("MES master data sync run not found");
        }
        return run;
    }

    private SyncSubmitResult submitSync(
            String syncType,
            Long createdBy,
            String submittedMessage,
            String runningMessage,
            boolean scheduled) {
        String normalizedType = validateSyncType(syncType);
        MesMasterDataSyncRun activeRun = findActiveRun(normalizedType);
        if (activeRun != null) {
            return submitResult(true, false, true, activeRun, runningMessage);
        }
        LocalDateTime startedAt = LocalDateTime.now();
        MesMasterDataSyncRun run;
        try {
            run = executeInTransaction(
                    TransactionDefinition.PROPAGATION_REQUIRES_NEW,
                    () -> startRun(normalizedType, startedAt, createdBy));
        } catch (RuntimeException error) {
            MesMasterDataSyncRun concurrentRun = findActiveRun(normalizedType);
            if (concurrentRun != null) {
                return submitResult(true, false, true, concurrentRun, runningMessage);
            }
            throw error;
        }
        try {
            if (taskExecutor == null) {
                throw new IllegalStateException("MES master data sync executor unavailable");
            }
            log.info("MES master data sync submitted taskId={} syncRunId={} syncType={} triggerSource={}",
                    run.getId(), run.getId(), normalizedType, scheduled ? "SCHEDULED" : "MANUAL");
            taskExecutor.execute(() -> runSubmittedSync(normalizedType, run, scheduled));
            return submitResult(true, true, false, run, submittedMessage);
        } catch (RuntimeException error) {
            String message = safeMessage("MES master data sync task submit failed: " + error.getMessage());
            Counters counters = new Counters();
            executeInTransaction(TransactionDefinition.PROPAGATION_REQUIRES_NEW,
                    () -> finishRun(run, STATUS_FAILED, startedAt, counters, message));
            log.error("MES master data sync failed taskId={} syncRunId={} syncType={} durationMs=0",
                    run.getId(), run.getId(), normalizedType, error);
            return new SyncSubmitResult(false, false, false, run.getId(), normalizedType, STATUS_FAILED, message);
        }
    }

    private MesMasterDataSyncRun findActiveRun(String syncType) {
        QueryWrapper<MesMasterDataSyncRun> query = new QueryWrapper<>();
        query.eq("sync_type", syncType).eq("status", STATUS_RUNNING)
                .orderByDesc("started_at").orderByDesc("id").last("LIMIT 1");
        return syncRunMapper.selectOne(query);
    }

    private SyncSubmitResult submitResult(
            boolean success,
            boolean submitted,
            boolean alreadyRunning,
            MesMasterDataSyncRun run,
            String message) {
        return new SyncSubmitResult(
                success,
                submitted,
                alreadyRunning,
                run == null ? null : run.getId(),
                run == null ? null : run.getSyncType(),
                run == null ? null : run.getStatus(),
                message);
    }

    private void runSubmittedSync(String syncType, MesMasterDataSyncRun run, boolean scheduled) {
        long startedMillis = System.currentTimeMillis();
        log.info("MES master data sync started taskId={} syncRunId={} syncType={} triggerSource={}",
                run.getId(), run.getId(), syncType, scheduled ? "SCHEDULED" : "MANUAL");
        SyncResult result = SYNC_TYPE_MATERIAL.equals(syncType)
                ? executeMaterialsSync(run, run.getStartedAt() == null ? LocalDateTime.now() : run.getStartedAt())
                : executeProcessTypesSync(run, run.getStartedAt() == null ? LocalDateTime.now() : run.getStartedAt());
        log.info("MES master data sync worker finished taskId={} syncRunId={} syncType={} status={} durationMs={}",
                run.getId(), run.getId(), syncType, result.success() ? STATUS_SUCCESS : STATUS_FAILED,
                System.currentTimeMillis() - startedMillis);
        if (scheduled && scheduleConfigService != null) {
            scheduleConfigService.updateScheduledResult(syncType, result);
        }
    }

    public SyncResult syncProcessTypesManual(Long createdBy) {
        LocalDateTime startedAt = LocalDateTime.now();
        MesMasterDataSyncRun run = executeInTransaction(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW,
                () -> startRun(SYNC_TYPE_PROCESS_TYPE, startedAt, createdBy));
        return executeProcessTypesSync(run, startedAt);
    }

    private SyncResult executeProcessTypesSync(MesMasterDataSyncRun run, LocalDateTime startedAt) {
        Counters counters = new Counters();
        try {
            List<MesProcessTypeListResp.Row> rows = client.listProcessTypes();
            List<ProcessTypeSource> sources = normalizeProcessSources(rows, counters);
            if (sources.isEmpty()) {
                throw new SyncFailure("MES process type sync returned no valid process types", counters);
            }
            PersistResult persisted = executeInTransaction(
                    TransactionDefinition.PROPAGATION_REQUIRED,
                    () -> persistProcessTypes(sources, startedAt));
            counters.upsertedCount = persisted.upsertedCount();
            counters.disabledCount = persisted.disabledCount();
            counters.deletedCount = persisted.deletedCount();
            executeInTransaction(TransactionDefinition.PROPAGATION_REQUIRES_NEW, () -> {
                finishRun(run, STATUS_SUCCESS, startedAt, counters, null);
                audit(run, counters, true, null, SYNC_TYPE_PROCESS_TYPE, AUDIT_ACTION_PROCESS_SYNC);
            });
            log.info("MES process type sync completed syncRunId={} fetchedCount={} validCount={} invalidCount={} duplicateCount={} upsertedCount={} disabledCount={} deletedCount={}",
                    run.getId(), counters.fetchedCount, counters.validCount, counters.invalidCount,
                    counters.duplicateCount, counters.upsertedCount, counters.disabledCount, counters.deletedCount);
            return result(true, run.getId(), counters, null);
        } catch (RuntimeException error) {
            Counters failureCounters = error instanceof SyncFailure syncFailure ? syncFailure.counters() : counters;
            String message = safeMessage(error.getMessage());
            executeInTransaction(TransactionDefinition.PROPAGATION_REQUIRES_NEW, () -> {
                finishRun(run, STATUS_FAILED, startedAt, failureCounters, message);
                audit(run, failureCounters, false, message, SYNC_TYPE_PROCESS_TYPE, AUDIT_ACTION_PROCESS_SYNC);
            });
            log.warn("MES process type sync failed syncRunId={} fetchedCount={} validCount={} invalidCount={} duplicateCount={} message={}",
                    run.getId(), failureCounters.fetchedCount, failureCounters.validCount,
                    failureCounters.invalidCount, failureCounters.duplicateCount, message);
            return result(false, run.getId(), failureCounters, message);
        }
    }

    public SyncResult syncMaterialsManual(Long createdBy) {
        LocalDateTime startedAt = LocalDateTime.now();
        MesMasterDataSyncRun run = executeInTransaction(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW,
                () -> startRun(SYNC_TYPE_MATERIAL, startedAt, createdBy));
        return executeMaterialsSync(run, startedAt);
    }

    private SyncResult executeMaterialsSync(MesMasterDataSyncRun run, LocalDateTime startedAt) {
        Counters counters = new Counters();
        try {
            PersistResult persisted = syncMaterialPages(startedAt, counters);
            counters.upsertedCount = persisted.upsertedCount();
            counters.disabledCount = persisted.disabledCount();
            counters.deletedCount = persisted.deletedCount();
            executeInTransaction(TransactionDefinition.PROPAGATION_REQUIRES_NEW, () -> {
                finishRun(run, STATUS_SUCCESS, startedAt, counters, null);
                audit(run, counters, true, null, SYNC_TYPE_MATERIAL, AUDIT_ACTION_MATERIAL_SYNC);
            });
            log.info("MES material sync completed syncRunId={} fetchedCount={} validCount={} invalidCount={} duplicateCount={} upsertedCount={} disabledCount={} deletedCount={}",
                    run.getId(), counters.fetchedCount, counters.validCount, counters.invalidCount,
                    counters.duplicateCount, counters.upsertedCount, counters.disabledCount, counters.deletedCount);
            return result(true, run.getId(), counters, null);
        } catch (RuntimeException error) {
            Counters failureCounters = error instanceof SyncFailure syncFailure ? syncFailure.counters() : counters;
            String message = safeMessage(error.getMessage());
            executeInTransaction(TransactionDefinition.PROPAGATION_REQUIRES_NEW, () -> {
                finishRun(run, STATUS_FAILED, startedAt, failureCounters, message);
                audit(run, failureCounters, false, message, SYNC_TYPE_MATERIAL, AUDIT_ACTION_MATERIAL_SYNC);
            });
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
        run.setDeletedCount(0);
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
        int disabledCount = 0;
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
            if (!source.enabled()) {
                disabledCount++;
            }
        }

        Set<String> returnedCodes = sources.stream().map(ProcessTypeSource::code).collect(Collectors.toSet());
        int deletedCount = 0;
        for (DictProcessCategory existing : existingByCode.values()) {
            if (SOURCE_MES.equalsIgnoreCase(trimToNull(existing.getSourceType()))
                    && !returnedCodes.contains(trimToNull(existing.getProcessCode()))) {
                if (deleteProcessCategory(existing)) {
                    deletedCount++;
                }
            }
        }
        return new PersistResult(upsertedCount, disabledCount, deletedCount);
    }

    private boolean deleteProcessCategory(DictProcessCategory category) {
        if (category == null || category.getId() == null) {
            return false;
        }
        categoryMapper.deleteById(category.getId());
        return true;
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

    private PersistResult syncMaterialPages(LocalDateTime syncedAt, Counters counters) {
        Set<String> seenCodes = new LinkedHashSet<>();
        Set<String> returnedCodes = new LinkedHashSet<>();
        int upsertedCount = 0;
        int disabledCount = 0;
        int pageNo = 1;
        while (true) {
            MesMaterialPageResp.Data page = client.pageMaterials(pageNo, MATERIAL_PAGE_SIZE);
            List<MesMaterialPageResp.Row> pageRows = page == null || page.getList() == null ? List.of() : page.getList();
            List<MaterialSource> sources = normalizeMaterialSources(pageRows, counters, seenCodes);
            returnedCodes.addAll(sources.stream().map(MaterialSource::lookupCode).collect(Collectors.toSet()));
            if (!sources.isEmpty()) {
                PersistResult pagePersisted = executeInTransaction(
                        TransactionDefinition.PROPAGATION_REQUIRED,
                        () -> persistMaterialPage(sources, syncedAt));
                upsertedCount += pagePersisted.upsertedCount();
                disabledCount += pagePersisted.disabledCount();
                counters.upsertedCount = upsertedCount;
                counters.disabledCount = disabledCount;
            }
            Long total = page == null ? null : page.getTotal();
            if (pageRows.isEmpty()
                    || pageRows.size() < MATERIAL_PAGE_SIZE
                    || (total != null && total >= 0 && counters.fetchedCount >= total)) {
                break;
            }
            pageNo++;
        }
        if (counters.validCount == 0) {
            throw new SyncFailure("MES material sync returned no valid materials", counters);
        }
        int deletedCount = deleteMissingMesMaterials(returnedCodes);
        counters.deletedCount = deletedCount;
        return new PersistResult(upsertedCount, disabledCount, deletedCount);
    }

    private List<MaterialSource> normalizeMaterialSources(
            List<MesMaterialPageResp.Row> rows,
            Counters counters,
            Set<String> seenCodes) {
        Map<String, MaterialSource> pageSourcesByCode = new LinkedHashMap<>();
        List<MesMaterialPageResp.Row> safeRows = rows == null ? List.of() : rows;
        counters.fetchedCount += safeRows.size();
        for (MesMaterialPageResp.Row row : safeRows) {
            String code = trimToNull(row == null ? null : row.getCode());
            String name = trimToNull(row == null ? null : row.getName());
            if (code == null || name == null) {
                counters.invalidCount++;
                continue;
            }
            String lookupCode = keyCode(code);
            if (!seenCodes.add(lookupCode)) {
                counters.duplicateCount++;
                continue;
            }
            String materialTypeCode = materialTypeCode(row.getMaterialType());
            BigDecimal densityKgPerCubicMeter = mesDensityKgPerCubicMeter(row.getDensity());
            counters.validCount++;
            pageSourcesByCode.put(lookupCode, new MaterialSource(
                    trimToNull(row.getId()),
                    code,
                    lookupCode,
                    name,
                    materialTypeCode,
                    materialTypeName(materialTypeCode, row.getMaterialType()),
                    firstNonBlank(row.getAttribute(), row.getCategoryId()),
                    toDouble(densityKgPerCubicMeter),
                    decimalText(densityKgPerCubicMeter),
                    trimToNull(row.getStandard()),
                    trimToNull(row.getDocNum()),
                    decimalText(row.getWeight()),
                    trimToNull(row.getPurchaseType()),
                    trimToNull(row.getProductionType()),
                    enabledFromStatus(row.getStatus())));
        }
        return pageSourcesByCode.values().stream().toList();
    }

    private PersistResult persistMaterialPage(List<MaterialSource> sources, LocalDateTime syncedAt) {
        Map<String, Long> typeIds = ensureMaterialTypes(sources, syncedAt);
        List<DictMaterial> existingRows = selectExistingMaterialsByLookupCodes(
                sources.stream().map(MaterialSource::lookupCode).collect(Collectors.toCollection(LinkedHashSet::new)));
        Map<String, DictMaterial> existingByCode = (existingRows == null ? List.<DictMaterial>of() : existingRows).stream()
                .filter(row -> keyCode(row.getMaterialCode()) != null)
                .collect(Collectors.toMap(
                        row -> keyCode(row.getMaterialCode()),
                        row -> row,
                        (first, ignored) -> first,
                        LinkedHashMap::new));

        int upsertedCount = 0;
        int disabledCount = 0;
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
            ensureDefaultMaterialPriceSpecs(material.getId(), syncedAt);
            upsertedCount++;
            if (!source.enabled()) {
                disabledCount++;
            }
        }

        return new PersistResult(upsertedCount, disabledCount, 0);
    }

    private void ensureDefaultMaterialPriceSpecs(Long materialId, LocalDateTime syncedAt) {
        if (materialId == null) {
            return;
        }
        QueryWrapper<DictMaterialPriceSpec> query = new QueryWrapper<>();
        query.eq("material_id", materialId);
        List<DictMaterialPriceSpec> existingRows = materialPriceSpecMapper.selectList(query);
        Set<String> existingCodes = (existingRows == null ? List.<DictMaterialPriceSpec>of() : existingRows).stream()
                .map(DictMaterialPriceSpec::getSpecCode)
                .filter(Objects::nonNull)
                .map(code -> code.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
        for (DictMaterialPriceSpec spec : MaterialPriceSpecSupport.defaultEntities(materialId, syncedAt)) {
            if (!existingCodes.contains(spec.getSpecCode())) {
                materialPriceSpecMapper.insert(spec);
            }
        }
    }

    private List<DictMaterial> selectExistingMaterialsByLookupCodes(Collection<String> lookupCodes) {
        List<String> codes = lookupCodes == null ? List.of() : lookupCodes.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (codes.isEmpty()) {
            return List.of();
        }
        QueryWrapper<DictMaterial> query = new QueryWrapper<>();
        query.apply("UPPER(TRIM(material_code)) IN (" + applyPlaceholders(codes.size()) + ")", codes.toArray(new Object[0]));
        return materialMapper.selectList(query);
    }

    private int deleteMissingMesMaterials(Set<String> returnedCodes) {
        int deletedCount = 0;
        long lastId = 0L;
        while (true) {
            QueryWrapper<DictMaterial> query = new QueryWrapper<>();
            query.select("id", "material_code", "source_type")
                    .eq("source_type", SOURCE_MES)
                    .gt("id", lastId)
                    .orderByAsc("id")
                    .last("LIMIT " + MATERIAL_DELETE_SCAN_BATCH_SIZE);
            List<DictMaterial> existingRows = materialMapper.selectList(query);
            List<DictMaterial> safeRows = existingRows == null ? List.of() : existingRows;
            if (safeRows.isEmpty()) {
                break;
            }
            long previousLastId = lastId;
            for (DictMaterial existing : safeRows) {
                Long id = existing.getId();
                if (id == null || id <= previousLastId) {
                    continue;
                }
                lastId = Math.max(lastId, id);
                if (!SOURCE_MES.equalsIgnoreCase(trimToNull(existing.getSourceType()))) {
                    continue;
                }
                String lookupCode = keyCode(existing.getMaterialCode());
                if (lookupCode != null && !returnedCodes.contains(lookupCode)) {
                    materialMapper.deleteById(id);
                    deletedCount++;
                }
            }
            if (lastId == previousLastId) {
                break;
            }
        }
        return deletedCount;
    }

    private String applyPlaceholders(int size) {
        StringBuilder placeholders = new StringBuilder();
        for (int index = 0; index < size; index++) {
            if (index > 0) {
                placeholders.append(", ");
            }
            placeholders.append('{').append(index).append('}');
        }
        return placeholders.toString();
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
        run.setDeletedCount(counters.deletedCount);
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
        summary.put("deletedCount", counters.deletedCount);
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
                counters.deletedCount,
                message);
    }

    private <T> T executeInTransaction(int propagationBehavior, Supplier<T> action) {
        if (transactionManager == null) {
            return action.get();
        }
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(propagationBehavior);
        return transactionTemplate.execute(status -> action.get());
    }

    private void executeInTransaction(int propagationBehavior, Runnable action) {
        executeInTransaction(propagationBehavior, () -> {
            action.run();
            return null;
        });
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

    private BigDecimal mesDensityKgPerCubicMeter(BigDecimal value) {
        return value == null ? null : value.multiply(MES_DENSITY_G_PER_CM3_TO_KG_PER_M3).stripTrailingZeros();
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

    private String validateSyncType(String syncType) {
        String value = trimToNull(syncType);
        if (!SYNC_TYPE_MATERIAL.equals(value) && !SYNC_TYPE_PROCESS_TYPE.equals(value)) {
            throw new IllegalArgumentException("Unsupported MES sync type: " + syncType);
        }
        return value;
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
            int deletedCount,
            String message) {
    }

    public record SyncSubmitResult(
            boolean success,
            boolean submitted,
            boolean alreadyRunning,
            Long syncRunId,
            String syncType,
            String status,
            String message) {
    }

    private static class Counters {
        private int fetchedCount;
        private int validCount;
        private int invalidCount;
        private int duplicateCount;
        private int upsertedCount;
        private int disabledCount;
        private int deletedCount;
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

    private record PersistResult(int upsertedCount, int disabledCount, int deletedCount) {
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
