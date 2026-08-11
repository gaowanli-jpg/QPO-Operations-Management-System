package com.machining.quote.service.u9;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.dto.u9.U9CommonEntityQueryReq;
import com.machining.quote.dto.u9.U9CommonEntityRowDTO;
import com.machining.quote.entity.CustomerMaster;
import com.machining.quote.entity.U9MasterDataSyncRun;
import com.machining.quote.mapper.CustomerMasterMapper;
import com.machining.quote.mapper.U9MasterDataSyncRunMapper;
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
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class U9MasterDataSyncService {
    private static final Logger log = LoggerFactory.getLogger(U9MasterDataSyncService.class);

    public static final String CUSTOMER_ENTITY = "UFIDA.U9.CBO.SCM.Customer.Customer";
    private static final String SYNC_TYPE_CUSTOMER = "CUSTOMER";
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

    @Autowired
    private U9CommonEntityClient commonEntityClient;
    @Autowired
    private CustomerMasterMapper customerMapper;
    @Autowired
    private U9MasterDataSyncRunMapper syncRunMapper;

    @Value("${erp.u9.org-code:101}")
    private String orgCode = "101";
    @Value("${erp.u9.customer-sync.enabled:false}")
    private boolean syncEnabled;

    private Clock clock = Clock.system(DISPLAY_ZONE);

    @Scheduled(
            initialDelayString = "${erp.u9.customer-sync.startup-delay-ms:30000}",
            fixedDelayString = "${erp.u9.customer-sync.fixed-delay-ms:1800000}"
    )
    public void syncCustomersScheduled() {
        if (!syncEnabled) {
            return;
        }
        SyncResult result = syncCustomers(null);
        if (!result.success()) {
            log.warn("U9 customer scheduled sync failed syncRunId={} message={}", result.syncRunId(), result.message());
        }
    }

    public SyncResult syncCustomersManual(Long createdBy) {
        return syncCustomers(createdBy);
    }

    public SyncResult syncCustomers(Long createdBy) {
        String expectedOrg = normalizeOrgCode();
        LocalDateTime startedAt = now();
        U9MasterDataSyncRun run = startRun(expectedOrg, startedAt, createdBy);
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

    private U9MasterDataSyncRun startRun(String expectedOrg, LocalDateTime startedAt, Long createdBy) {
        U9MasterDataSyncRun run = new U9MasterDataSyncRun();
        run.setSyncType(SYNC_TYPE_CUSTOMER);
        run.setU9OrgCode(expectedOrg);
        run.setStatus(STATUS_RUNNING);
        run.setStartedAt(startedAt);
        run.setFetchedCount(0);
        run.setUpsertedCount(0);
        run.setDisabledCount(0);
        run.setCreatedBy(createdBy);
        run.setCreatedAt(startedAt);
        syncRunMapper.insert(run);
        log.info("U9 customer sync started syncRunId={}", run.getId());
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
}
