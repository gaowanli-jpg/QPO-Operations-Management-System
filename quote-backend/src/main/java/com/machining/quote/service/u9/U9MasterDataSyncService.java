package com.machining.quote.service.u9;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.dto.u9.U9CommonEntityQueryReq;
import com.machining.quote.dto.u9.U9CommonEntityRowDTO;
import com.machining.quote.entity.CustomerMaster;
import com.machining.quote.entity.U9MasterDataSyncRun;
import com.machining.quote.entity.U9SyncDepartmentOptionCache;
import com.machining.quote.entity.U9SyncSellerOptionCache;
import com.machining.quote.mapper.CustomerMasterMapper;
import com.machining.quote.mapper.U9MasterDataSyncRunMapper;
import com.machining.quote.mapper.U9SyncDepartmentOptionCacheMapper;
import com.machining.quote.mapper.U9SyncSellerOptionCacheMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
public class U9MasterDataSyncService {
    private static final Logger log = LoggerFactory.getLogger(U9MasterDataSyncService.class);

    public static final String CUSTOMER_ENTITY = "UFIDA.U9.CBO.SCM.Customer.Customer";
    private static final String SYNC_TYPE_CUSTOMER = "CUSTOMER";
    private static final String SYNC_TYPE_U9_OPTIONS = "U9_SYNC_OPTIONS";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final int PAGE_SIZE = 200;
    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Shanghai");
    private static final List<String> CUSTOMER_FIELDS = List.of(
            "ID",
            "Code",
            "Name",
            "ShortName",
            "Org.Code",
            "Org.Name",
            "Effective.IsEffective"
    );
    private static final List<String> OPERATOR_FIELDS = List.of(
            "ID",
            "Code",
            "Name",
            "Org.Code",
            "Dept.Code",
            "Dept.Name",
            "Effective.IsEffective",
            "OperatorLine.OperatorType"
    );
    private static final List<String> DEPARTMENT_FIELDS = List.of(
            "ID",
            "Code",
            "Name",
            "Org.Code",
            "ParentNode.Code",
            "Effective.IsEffective",
            "Level",
            "IsLeaf",
            "TreeDisplayName"
    );

    @Autowired
    private U9CommonEntityClient commonEntityClient;
    @Autowired
    private CustomerMasterMapper customerMapper;
    @Autowired
    private U9SyncSellerOptionCacheMapper sellerOptionMapper;
    @Autowired
    private U9SyncDepartmentOptionCacheMapper departmentOptionMapper;
    @Autowired
    private U9MasterDataSyncRunMapper syncRunMapper;
    @Autowired
    private U9CustomerSyncScheduleConfigService customerSyncScheduleConfigService;

    @Value("${erp.u9.org-code:101}")
    private String orgCode = "101";

    private Clock clock = Clock.system(DISPLAY_ZONE);
    private LocalDateTime customerSyncSchedulerStartedAt;
    private final AtomicBoolean scheduledCustomerSyncRunning = new AtomicBoolean(false);

    @PostConstruct
    public void initCustomerSyncSchedulerStartedAt() {
        customerSyncSchedulerStartedAt = now();
    }

    @Scheduled(fixedDelayString = "${erp.u9.customer-sync.check-ms:60000}")
    public void syncCustomersScheduled() {
        if (customerSyncScheduleConfigService == null) {
            return;
        }
        if (customerSyncSchedulerStartedAt == null) {
            customerSyncSchedulerStartedAt = now();
        }
        if (!customerSyncScheduleConfigService.shouldRun(customerSyncSchedulerStartedAt)) {
            return;
        }
        if (!scheduledCustomerSyncRunning.compareAndSet(false, true)) {
            log.info("U9 customer scheduled sync skipped because previous run is still active");
            return;
        }
        try {
            SyncResult result = syncCustomers(null);
            customerSyncScheduleConfigService.updateScheduledResult(result);
            if (!result.success()) {
                log.warn("U9 customer scheduled sync failed syncRunId={} message={}", result.syncRunId(), result.message());
            }
        } finally {
            scheduledCustomerSyncRunning.set(false);
        }
    }

    public SyncResult syncCustomersManual(Long createdBy) {
        return syncCustomers(createdBy);
    }

    public SyncResult syncCustomers(Long createdBy) {
        String expectedOrg = normalizeOrgCode();
        LocalDateTime startedAt = now();
        U9MasterDataSyncRun run = startRun(SYNC_TYPE_CUSTOMER, expectedOrg, startedAt, createdBy);
        try {
            FetchResult fetched = fetchCustomers(expectedOrg);
            PersistResult persisted = persistCustomers(expectedOrg, fetched.customers(), startedAt, createdBy);
            finishRun(run, STATUS_SUCCESS, startedAt, fetched.fetchedCount(), persisted.upsertedCount(), persisted.disabledCount(), null);
            log.info("U9 customer sync completed syncRunId={} fetchedCount={} upsertedCount={} disabledCount={}",
                    run.getId(), fetched.fetchedCount(), persisted.upsertedCount(), persisted.disabledCount());
            return new SyncResult(true, run.getId(), fetched.fetchedCount(), persisted.upsertedCount(), persisted.disabledCount(), null);
        } catch (RuntimeException error) {
            String safeMessage = safeMessage(error.getMessage());
            finishRun(run, STATUS_FAILED, startedAt, 0, 0, 0, safeMessage);
            log.warn("U9 customer sync failed syncRunId={} message={}", run.getId(), safeMessage);
            return new SyncResult(false, run.getId(), 0, 0, 0, safeMessage);
        }
    }

    public SyncResult syncOptionsManual(Long createdBy) {
        return syncOptions(createdBy);
    }

    public SyncResult syncOptions(Long createdBy) {
        String expectedOrg = normalizeOrgCode();
        LocalDateTime startedAt = now();
        U9MasterDataSyncRun run = startRun(SYNC_TYPE_U9_OPTIONS, expectedOrg, startedAt, createdBy);
        try {
            OptionsFetchResult fetched = fetchOptions(expectedOrg);
            PersistResult sellers = persistSellerOptions(expectedOrg, fetched.sellers(), startedAt);
            PersistResult departments = persistDepartmentOptions(expectedOrg, fetched.departments(), startedAt);
            int upserted = sellers.upsertedCount() + departments.upsertedCount();
            int disabled = sellers.disabledCount() + departments.disabledCount();
            finishRun(run, STATUS_SUCCESS, startedAt, fetched.fetchedCount(), upserted, disabled, null);
            log.info("U9 sync options refresh completed syncRunId={} fetchedCount={} upsertedCount={} disabledCount={}",
                    run.getId(), fetched.fetchedCount(), upserted, disabled);
            return new SyncResult(true, run.getId(), fetched.fetchedCount(), upserted, disabled, null);
        } catch (RuntimeException error) {
            String safeMessage = safeMessage(error.getMessage());
            finishRun(run, STATUS_FAILED, startedAt, 0, 0, 0, safeMessage);
            log.warn("U9 sync options refresh failed syncRunId={} message={}", run.getId(), safeMessage);
            return new SyncResult(false, run.getId(), 0, 0, 0, safeMessage);
        }
    }

    private U9MasterDataSyncRun startRun(String syncType, String expectedOrg, LocalDateTime startedAt, Long createdBy) {
        U9MasterDataSyncRun run = new U9MasterDataSyncRun();
        run.setSyncType(syncType);
        run.setU9OrgCode(expectedOrg);
        run.setStatus(STATUS_RUNNING);
        run.setStartedAt(startedAt);
        run.setFetchedCount(0);
        run.setUpsertedCount(0);
        run.setDisabledCount(0);
        run.setCreatedBy(createdBy);
        run.setCreatedAt(startedAt);
        syncRunMapper.insert(run);
        log.info("U9 master data sync started syncRunId={} syncType={}", run.getId(), syncType);
        return run;
    }

    private void finishRun(
            U9MasterDataSyncRun run,
            String status,
            LocalDateTime startedAt,
            int fetchedCount,
            int upsertedCount,
            int disabledCount,
            String message) {
        run.setStatus(status);
        run.setStartedAt(startedAt);
        run.setFinishedAt(now());
        run.setFetchedCount(fetchedCount);
        run.setUpsertedCount(upsertedCount);
        run.setDisabledCount(disabledCount);
        run.setMessage(message == null ? "OK" : safeMessage(message));
        syncRunMapper.updateById(run);
    }

    private FetchResult fetchCustomers(String expectedOrg) {
        Map<String, CustomerSource> byCode = new LinkedHashMap<>();
        int fetchedCount = 0;
        int pageIndex = 1;
        while (true) {
            U9CommonEntityQueryReq req = U9CommonEntityQueryReq.builder()
                    .page(pageIndex, PAGE_SIZE)
                    .orders("Code")
                    .entityFullName(CUSTOMER_ENTITY)
                    .returnFields(CUSTOMER_FIELDS)
                    .filter("Org.Code", "eq", expectedOrg)
                    .build();
            List<U9CommonEntityRowDTO> page = commonEntityClient.query(req);
            List<U9CommonEntityRowDTO> safePage = page == null ? List.of() : page;
            fetchedCount += safePage.size();
            for (U9CommonEntityRowDTO row : safePage) {
                CustomerSource source = source(row, expectedOrg);
                if (source != null) {
                    byCode.putIfAbsent(source.code(), source);
                }
            }
            if (safePage.size() < PAGE_SIZE) {
                break;
            }
            pageIndex++;
        }
        if (byCode.isEmpty()) {
            throw new IllegalStateException("U9 customer sync returned no valid customers for org " + expectedOrg);
        }
        return new FetchResult(fetchedCount, byCode.values().stream()
                .sorted(Comparator.comparing(CustomerSource::code))
                .toList());
    }

    private OptionsFetchResult fetchOptions(String expectedOrg) {
        List<U9CommonEntityRowDTO> operatorRows = queryAll(
                U9MasterDataService.OPERATOR_ENTITY,
                OPERATOR_FIELDS,
                expectedOrg);
        List<U9CommonEntityRowDTO> departmentRows = queryAll(
                U9MasterDataService.DEPARTMENT_ENTITY,
                DEPARTMENT_FIELDS,
                expectedOrg);
        List<SellerSource> sellers = sellerSources(operatorRows, expectedOrg);
        List<DepartmentSource> departments = departmentSources(departmentRows, sellers, expectedOrg);
        if (sellers.isEmpty() && departments.isEmpty()) {
            throw new IllegalStateException("U9 sync options returned no valid Seller or SaleDepartment for org " + expectedOrg);
        }
        return new OptionsFetchResult(operatorRows.size() + departmentRows.size(), sellers, departments);
    }

    private List<U9CommonEntityRowDTO> queryAll(String entityFullName, List<String> returnFields, String expectedOrg) {
        Map<String, U9CommonEntityRowDTO> rowsBySequence = new LinkedHashMap<>();
        int pageIndex = 1;
        while (true) {
            U9CommonEntityQueryReq req = U9CommonEntityQueryReq.builder()
                    .page(pageIndex, U9MasterDataService.PAGE_SIZE)
                    .orders("Code")
                    .entityFullName(entityFullName)
                    .returnFields(returnFields)
                    .filter("Org.Code", "eq", expectedOrg)
                    .build();
            List<U9CommonEntityRowDTO> page = commonEntityClient.query(req);
            List<U9CommonEntityRowDTO> safePage = page == null ? List.of() : page;
            for (int i = 0; i < safePage.size(); i++) {
                rowsBySequence.put(pageIndex + ":" + i, safePage.get(i));
            }
            if (safePage.size() < U9MasterDataService.PAGE_SIZE) {
                break;
            }
            pageIndex++;
        }
        return rowsBySequence.values().stream().toList();
    }

    private List<SellerSource> sellerSources(List<U9CommonEntityRowDTO> rows, String expectedOrg) {
        Map<String, SellerSource> byCode = new LinkedHashMap<>();
        for (U9CommonEntityRowDTO row : rows == null ? List.<U9CommonEntityRowDTO>of() : rows) {
            if (!expectedOrg.equals(trimToNull(row.text("Org.Code")))
                    || !Boolean.TRUE.equals(row.bool("Effective.IsEffective"))
                    || !hasSalesOperatorType(row)) {
                continue;
            }
            String code = trimToNull(row.text("Code"));
            String name = trimToNull(row.text("Name"));
            if (code == null || name == null || byCode.containsKey(code)) {
                continue;
            }
            String departmentCode = trimToNull(row.text("Dept.Code"));
            String departmentName = trimToNull(row.text("Dept.Name"));
            String u9SellerId = trimToNull(row.text("ID"));
            String hash = hash(expectedOrg, u9SellerId, code, name, departmentCode, departmentName, U9MasterDataService.SALES_OPERATOR_TYPE, "true");
            byCode.put(code, new SellerSource(
                    expectedOrg,
                    u9SellerId,
                    code,
                    name,
                    departmentCode,
                    departmentName,
                    true,
                    U9MasterDataService.SALES_OPERATOR_TYPE,
                    hash));
        }
        return byCode.values().stream()
                .sorted(Comparator.comparing(SellerSource::code))
                .toList();
    }

    private List<DepartmentSource> departmentSources(
            List<U9CommonEntityRowDTO> rows,
            List<SellerSource> sellers,
            String expectedOrg) {
        Map<String, DepartmentSource> allDepartments = new LinkedHashMap<>();
        for (U9CommonEntityRowDTO row : rows == null ? List.<U9CommonEntityRowDTO>of() : rows) {
            if (!expectedOrg.equals(trimToNull(row.text("Org.Code")))
                    || !Boolean.TRUE.equals(row.bool("Effective.IsEffective"))) {
                continue;
            }
            String code = trimToNull(row.text("Code"));
            String name = trimToNull(row.text("Name"));
            if (code == null || name == null || allDepartments.containsKey(code)) {
                continue;
            }
            String u9DepartmentId = trimToNull(row.text("ID"));
            String parentCode = trimToNull(row.text("ParentNode.Code"));
            String treeDisplayName = firstNonBlank(row.text("TreeDisplayName"), name);
            String hash = hash(expectedOrg, u9DepartmentId, code, name, parentCode, treeDisplayName, "true");
            allDepartments.put(code, new DepartmentSource(
                    expectedOrg,
                    u9DepartmentId,
                    code,
                    name,
                    parentCode,
                    treeDisplayName,
                    true,
                    hash));
        }

        Set<String> includedCodes = salesDepartmentTreeCodes(allDepartments);
        for (SellerSource seller : sellers == null ? List.<SellerSource>of() : sellers) {
            String code = trimToNull(seller.departmentCode());
            if (code != null) {
                includedCodes.add(code);
                if (!allDepartments.containsKey(code) && trimToNull(seller.departmentName()) != null) {
                    String hash = hash(expectedOrg, null, code, seller.departmentName(), null, seller.departmentName(), "true");
                    allDepartments.put(code, new DepartmentSource(
                            expectedOrg,
                            null,
                            code,
                            seller.departmentName(),
                            null,
                            seller.departmentName(),
                            true,
                            hash));
                }
            }
        }

        return includedCodes.stream()
                .map(allDepartments::get)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(DepartmentSource::code))
                .toList();
    }

    private Set<String> salesDepartmentTreeCodes(Map<String, DepartmentSource> allDepartments) {
        Set<String> included = new LinkedHashSet<>();
        if (allDepartments.containsKey(U9MasterDataService.SALES_DEPARTMENT_ROOT)) {
            included.add(U9MasterDataService.SALES_DEPARTMENT_ROOT);
        }
        boolean changed;
        do {
            changed = false;
            for (DepartmentSource source : allDepartments.values()) {
                if (!included.contains(source.code()) && included.contains(source.parentCode())) {
                    included.add(source.code());
                    changed = true;
                }
            }
        } while (changed);
        return included;
    }

    private PersistResult persistSellerOptions(String expectedOrg, List<SellerSource> sources, LocalDateTime syncedAt) {
        QueryWrapper<U9SyncSellerOptionCache> query = new QueryWrapper<>();
        query.eq("u9_org_code", expectedOrg);
        List<U9SyncSellerOptionCache> existingRows = sellerOptionMapper.selectList(query);
        Map<String, U9SyncSellerOptionCache> existingByCode = (existingRows == null ? List.<U9SyncSellerOptionCache>of() : existingRows).stream()
                .filter(row -> hasText(row.getSellerCode()))
                .collect(Collectors.toMap(
                        row -> row.getSellerCode().trim(),
                        row -> row,
                        (first, ignored) -> first,
                        LinkedHashMap::new));

        int upsertedCount = 0;
        for (SellerSource source : sources == null ? List.<SellerSource>of() : sources) {
            U9SyncSellerOptionCache existing = existingByCode.get(source.code());
            if (existing == null) {
                U9SyncSellerOptionCache created = new U9SyncSellerOptionCache();
                applySellerSource(created, source, syncedAt);
                created.setCreatedAt(syncedAt);
                sellerOptionMapper.insert(created);
                upsertedCount++;
            } else if (applySellerSource(existing, source, syncedAt)) {
                sellerOptionMapper.updateById(existing);
                upsertedCount++;
            }
        }

        Set<String> returnedCodes = (sources == null ? List.<SellerSource>of() : sources).stream()
                .map(SellerSource::code)
                .collect(Collectors.toSet());
        int disabledCount = 0;
        for (U9SyncSellerOptionCache existing : existingByCode.values()) {
            if (!returnedCodes.contains(existing.getSellerCode()) && Boolean.TRUE.equals(existing.getEnabled())) {
                existing.setEnabled(false);
                existing.setLastSyncedAt(syncedAt);
                existing.setUpdatedAt(syncedAt);
                sellerOptionMapper.updateById(existing);
                disabledCount++;
            }
        }
        return new PersistResult(upsertedCount, disabledCount);
    }

    private PersistResult persistDepartmentOptions(String expectedOrg, List<DepartmentSource> sources, LocalDateTime syncedAt) {
        QueryWrapper<U9SyncDepartmentOptionCache> query = new QueryWrapper<>();
        query.eq("u9_org_code", expectedOrg);
        List<U9SyncDepartmentOptionCache> existingRows = departmentOptionMapper.selectList(query);
        Map<String, U9SyncDepartmentOptionCache> existingByCode = (existingRows == null ? List.<U9SyncDepartmentOptionCache>of() : existingRows).stream()
                .filter(row -> hasText(row.getDepartmentCode()))
                .collect(Collectors.toMap(
                        row -> row.getDepartmentCode().trim(),
                        row -> row,
                        (first, ignored) -> first,
                        LinkedHashMap::new));

        int upsertedCount = 0;
        for (DepartmentSource source : sources == null ? List.<DepartmentSource>of() : sources) {
            U9SyncDepartmentOptionCache existing = existingByCode.get(source.code());
            if (existing == null) {
                U9SyncDepartmentOptionCache created = new U9SyncDepartmentOptionCache();
                applyDepartmentSource(created, source, syncedAt);
                created.setCreatedAt(syncedAt);
                departmentOptionMapper.insert(created);
                upsertedCount++;
            } else if (applyDepartmentSource(existing, source, syncedAt)) {
                departmentOptionMapper.updateById(existing);
                upsertedCount++;
            }
        }

        Set<String> returnedCodes = (sources == null ? List.<DepartmentSource>of() : sources).stream()
                .map(DepartmentSource::code)
                .collect(Collectors.toSet());
        int disabledCount = 0;
        for (U9SyncDepartmentOptionCache existing : existingByCode.values()) {
            if (!returnedCodes.contains(existing.getDepartmentCode()) && Boolean.TRUE.equals(existing.getEnabled())) {
                existing.setEnabled(false);
                existing.setLastSyncedAt(syncedAt);
                existing.setUpdatedAt(syncedAt);
                departmentOptionMapper.updateById(existing);
                disabledCount++;
            }
        }
        return new PersistResult(upsertedCount, disabledCount);
    }

    private boolean applySellerSource(U9SyncSellerOptionCache row, SellerSource source, LocalDateTime syncedAt) {
        boolean changed = false;
        changed |= setIfChanged(row::getU9OrgCode, row::setU9OrgCode, source.orgCode());
        changed |= setIfChanged(row::getU9SellerId, row::setU9SellerId, source.u9SellerId());
        changed |= setIfChanged(row::getSellerCode, row::setSellerCode, source.code());
        changed |= setIfChanged(row::getSellerName, row::setSellerName, source.name());
        changed |= setIfChanged(row::getDepartmentCode, row::setDepartmentCode, source.departmentCode());
        changed |= setIfChanged(row::getDepartmentName, row::setDepartmentName, source.departmentName());
        changed |= setIfChanged(row::getEnabled, row::setEnabled, source.enabled());
        changed |= setIfChanged(row::getOperatorType, row::setOperatorType, source.operatorType());
        changed |= setIfChanged(row::getSourceHash, row::setSourceHash, source.sourceHash());
        changed |= setIfChanged(row::getLastSyncedAt, row::setLastSyncedAt, syncedAt);
        row.setUpdatedAt(syncedAt);
        return changed;
    }

    private boolean applyDepartmentSource(U9SyncDepartmentOptionCache row, DepartmentSource source, LocalDateTime syncedAt) {
        boolean changed = false;
        changed |= setIfChanged(row::getU9OrgCode, row::setU9OrgCode, source.orgCode());
        changed |= setIfChanged(row::getU9DepartmentId, row::setU9DepartmentId, source.u9DepartmentId());
        changed |= setIfChanged(row::getDepartmentCode, row::setDepartmentCode, source.code());
        changed |= setIfChanged(row::getDepartmentName, row::setDepartmentName, source.name());
        changed |= setIfChanged(row::getParentDepartmentCode, row::setParentDepartmentCode, source.parentCode());
        changed |= setIfChanged(row::getTreeDisplayName, row::setTreeDisplayName, source.treeDisplayName());
        changed |= setIfChanged(row::getEnabled, row::setEnabled, source.enabled());
        changed |= setIfChanged(row::getSourceHash, row::setSourceHash, source.sourceHash());
        changed |= setIfChanged(row::getLastSyncedAt, row::setLastSyncedAt, syncedAt);
        row.setUpdatedAt(syncedAt);
        return changed;
    }

    private PersistResult persistCustomers(String expectedOrg, List<CustomerSource> sources, LocalDateTime syncedAt, Long createdBy) {
        QueryWrapper<CustomerMaster> query = new QueryWrapper<>();
        query.eq("u9_org_code", expectedOrg);
        List<CustomerMaster> existingRows = customerMapper.selectList(query);
        Map<String, CustomerMaster> existingByCode = (existingRows == null ? List.<CustomerMaster>of() : existingRows).stream()
                .filter(customer -> hasText(customer.getCustomerCode()))
                .collect(Collectors.toMap(
                        customer -> customer.getCustomerCode().trim(),
                        customer -> customer,
                        (first, ignored) -> first,
                        LinkedHashMap::new));

        int upsertedCount = 0;
        for (CustomerSource source : sources == null ? List.<CustomerSource>of() : sources) {
            CustomerMaster existing = existingByCode.get(source.code());
            if (existing == null) {
                CustomerMaster created = new CustomerMaster();
                applySource(created, source, syncedAt, createdBy);
                created.setCreatedBy(createdBy);
                created.setCreatedAt(syncedAt);
                customerMapper.insert(created);
                upsertedCount++;
            } else if (applySource(existing, source, syncedAt, createdBy)) {
                customerMapper.updateById(existing);
                upsertedCount++;
            }
        }

        Set<String> returnedCodes = (sources == null ? List.<CustomerSource>of() : sources).stream()
                .map(CustomerSource::code)
                .collect(Collectors.toSet());
        int disabledCount = 0;
        for (CustomerMaster existing : existingByCode.values()) {
            if (!returnedCodes.contains(existing.getCustomerCode()) && Boolean.TRUE.equals(existing.getEnabled())) {
                existing.setEnabled(false);
                existing.setLastSyncedAt(syncedAt);
                existing.setUpdatedBy(createdBy);
                existing.setUpdatedAt(syncedAt);
                customerMapper.updateById(existing);
                disabledCount++;
            }
        }
        return new PersistResult(upsertedCount, disabledCount);
    }

    private boolean applySource(CustomerMaster customer, CustomerSource source, LocalDateTime syncedAt, Long updatedBy) {
        boolean changed = false;
        changed |= setIfChanged(customer::getU9OrgCode, customer::setU9OrgCode, source.orgCode());
        changed |= setIfChanged(customer::getU9CustomerId, customer::setU9CustomerId, source.u9CustomerId());
        changed |= setIfChanged(customer::getCustomerCode, customer::setCustomerCode, source.code());
        changed |= setIfChanged(customer::getCustomerShortName, customer::setCustomerShortName, source.shortName());
        changed |= setIfChanged(customer::getCustomerName, customer::setCustomerName, source.name());
        changed |= setIfChanged(customer::getEnabled, customer::setEnabled, source.enabled());
        changed |= setIfChanged(customer::getSourceHash, customer::setSourceHash, source.sourceHash());
        changed |= setIfChanged(customer::getLastSyncedAt, customer::setLastSyncedAt, syncedAt);
        customer.setUpdatedBy(updatedBy);
        customer.setUpdatedAt(syncedAt);
        return changed;
    }

    private <T> boolean setIfChanged(java.util.function.Supplier<T> getter, java.util.function.Consumer<T> setter, T value) {
        if (Objects.equals(getter.get(), value)) {
            return false;
        }
        setter.accept(value);
        return true;
    }

    private CustomerSource source(U9CommonEntityRowDTO row, String expectedOrg) {
        if (row == null) {
            return null;
        }
        String org = trimToNull(row.text("Org.Code"));
        String code = trimToNull(row.text("Code"));
        String name = trimToNull(row.text("Name"));
        if (!expectedOrg.equals(org) || code == null || name == null) {
            return null;
        }
        String shortName = firstNonBlank(row.text("ShortName"), name, code);
        String u9CustomerId = trimToNull(row.text("ID"));
        Boolean effective = row.bool("Effective.IsEffective");
        boolean enabled = Boolean.TRUE.equals(effective);
        String hash = hash(expectedOrg, u9CustomerId, code, shortName, name, String.valueOf(enabled));
        return new CustomerSource(expectedOrg, u9CustomerId, code, shortName, name, enabled, hash);
    }

    private boolean hasSalesOperatorType(U9CommonEntityRowDTO row) {
        return containsText(row.value("OperatorLine.OperatorType"), U9MasterDataService.SALES_OPERATOR_TYPE)
                || containsText(row.value("OperatorLine"), U9MasterDataService.SALES_OPERATOR_TYPE);
    }

    private boolean containsText(Object value, String expected) {
        if (value == null) {
            return false;
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().anyMatch(item -> containsText(item, expected));
        }
        if (value instanceof Map<?, ?> map) {
            return map.values().stream().anyMatch(item -> containsText(item, expected));
        }
        return expected.equals(String.valueOf(value).trim());
    }

    private String hash(String... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String value : values) {
                digest.update((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '|');
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private String safeMessage(String value) {
        String sanitized = U9CommonEntityClient.sanitize(value);
        return sanitized.replaceAll("(?i)data\\.headers", "[REDACTED]");
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), DISPLAY_ZONE);
    }

    private String normalizeOrgCode() {
        return firstNonBlank(orgCode, "101");
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public record SyncResult(
            boolean success,
            Long syncRunId,
            int fetchedCount,
            int upsertedCount,
            int disabledCount,
            String message) {
    }

    private record FetchResult(int fetchedCount, List<CustomerSource> customers) {
    }

    private record OptionsFetchResult(
            int fetchedCount,
            List<SellerSource> sellers,
            List<DepartmentSource> departments) {
    }

    private record PersistResult(int upsertedCount, int disabledCount) {
    }

    private record CustomerSource(
            String orgCode,
            String u9CustomerId,
            String code,
            String shortName,
            String name,
            Boolean enabled,
            String sourceHash) {
    }

    private record SellerSource(
            String orgCode,
            String u9SellerId,
            String code,
            String name,
            String departmentCode,
            String departmentName,
            Boolean enabled,
            String operatorType,
            String sourceHash) {
    }

    private record DepartmentSource(
            String orgCode,
            String u9DepartmentId,
            String code,
            String name,
            String parentCode,
            String treeDisplayName,
            Boolean enabled,
            String sourceHash) {
    }
}
