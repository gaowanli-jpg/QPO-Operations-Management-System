package com.machining.quote.service.u9;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.dto.SalesOrderU9DepartmentOptionDTO;
import com.machining.quote.dto.SalesOrderU9SellerOptionDTO;
import com.machining.quote.dto.SalesOrderU9SyncOptionsResp;
import com.machining.quote.entity.U9SyncDepartmentOptionCache;
import com.machining.quote.entity.U9SyncSellerOptionCache;
import com.machining.quote.mapper.U9SyncDepartmentOptionCacheMapper;
import com.machining.quote.mapper.U9SyncSellerOptionCacheMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Service
public class U9MasterDataService {
    public static final String OPERATOR_ENTITY = "UFIDA.U9.CBO.HR.Operator.Operators";
    public static final String DEPARTMENT_ENTITY = "UFIDA.U9.CBO.HR.Department.Department";
    public static final int PAGE_SIZE = 200;
    public static final String SALES_OPERATOR_TYPE = "销售业务员";
    public static final String SALES_DEPARTMENT_ROOT = "2002";
    private static final Duration CACHE_TTL = Duration.ofHours(1);
    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired
    private U9SyncSellerOptionCacheMapper sellerOptionMapper;
    @Autowired
    private U9SyncDepartmentOptionCacheMapper departmentOptionMapper;
    @Autowired(required = false)
    private U9OrgCodeResolver u9OrgCodeResolver;
    private Clock clock = Clock.system(DISPLAY_ZONE);

    public SalesOrderU9SyncOptionsResp options() {
        String expectedOrg = normalizeOrgCode();
        List<U9SyncSellerOptionCache> sellers = enabledSellers(expectedOrg);
        List<U9SyncDepartmentOptionCache> departments = enabledDepartments(expectedOrg);
        LocalDateTime refreshedAt = latestSyncedAt(sellers, departments);

        SalesOrderU9SyncOptionsResp resp = new SalesOrderU9SyncOptionsResp();
        resp.setOrgCode(expectedOrg);
        resp.setRefreshedAt(refreshedAt);
        resp.setSellers(sellers.stream()
                .sorted(Comparator.comparing(U9SyncSellerOptionCache::getSellerCode, Comparator.nullsLast(String::compareTo)))
                .map(this::toSellerOption)
                .toList());
        resp.setDepartments(departments.stream()
                .sorted(Comparator.comparing(U9SyncDepartmentOptionCache::getDepartmentCode, Comparator.nullsLast(String::compareTo)))
                .map(this::toDepartmentOption)
                .toList());
        applyCacheStatus(resp, sellers, departments, refreshedAt);
        return resp;
    }

    private List<U9SyncSellerOptionCache> enabledSellers(String expectedOrg) {
        QueryWrapper<U9SyncSellerOptionCache> query = new QueryWrapper<>();
        query.eq("u9_org_code", expectedOrg)
                .eq("enabled", true)
                .orderByAsc("seller_code")
                .orderByAsc("id");
        return safeList(sellerOptionMapper.selectList(query)).stream()
                .filter(row -> Boolean.TRUE.equals(row.getEnabled()))
                .filter(row -> hasText(row.getSellerCode()))
                .toList();
    }

    private List<U9SyncDepartmentOptionCache> enabledDepartments(String expectedOrg) {
        QueryWrapper<U9SyncDepartmentOptionCache> query = new QueryWrapper<>();
        query.eq("u9_org_code", expectedOrg)
                .eq("enabled", true)
                .orderByAsc("department_code")
                .orderByAsc("id");
        return safeList(departmentOptionMapper.selectList(query)).stream()
                .filter(row -> Boolean.TRUE.equals(row.getEnabled()))
                .filter(row -> hasText(row.getDepartmentCode()))
                .toList();
    }

    private LocalDateTime latestSyncedAt(
            List<U9SyncSellerOptionCache> sellers,
            List<U9SyncDepartmentOptionCache> departments) {
        return Stream.concat(
                        safeList(sellers).stream().map(U9SyncSellerOptionCache::getLastSyncedAt),
                        safeList(departments).stream().map(U9SyncDepartmentOptionCache::getLastSyncedAt))
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }

    private void applyCacheStatus(
            SalesOrderU9SyncOptionsResp resp,
            List<U9SyncSellerOptionCache> sellers,
            List<U9SyncDepartmentOptionCache> departments,
            LocalDateTime refreshedAt) {
        if (safeList(sellers).isEmpty() && safeList(departments).isEmpty()) {
            resp.setStale(true);
            resp.setWarning("U9同步页选项缓存为空，请刷新 U9 选项缓存后再选择 Seller / SaleDepartment。");
            return;
        }
        if (refreshedAt == null) {
            resp.setStale(true);
            resp.setWarning("U9同步页选项缺少同步时间，请刷新 U9 选项缓存。");
            return;
        }
        Duration age = Duration.between(refreshedAt.atZone(DISPLAY_ZONE).toInstant(), clock.instant());
        if (age.compareTo(CACHE_TTL) > 0) {
            resp.setStale(true);
            resp.setWarning("U9同步页选项缓存已超过60分钟，请刷新后确认 Seller / SaleDepartment。");
            return;
        }
        resp.setStale(false);
        resp.setWarning(null);
    }

    private SalesOrderU9SellerOptionDTO toSellerOption(U9SyncSellerOptionCache row) {
        return new SalesOrderU9SellerOptionDTO(
                trimToNull(row.getSellerCode()),
                trimToNull(row.getSellerName()),
                trimToNull(row.getDepartmentCode()),
                trimToNull(row.getDepartmentName())
        );
    }

    private SalesOrderU9DepartmentOptionDTO toDepartmentOption(U9SyncDepartmentOptionCache row) {
        return new SalesOrderU9DepartmentOptionDTO(
                trimToNull(row.getDepartmentCode()),
                trimToNull(row.getDepartmentName()),
                firstNonBlank(row.getTreeDisplayName(), row.getDepartmentName())
        );
    }

    private String normalizeOrgCode() {
        return firstNonBlank(u9OrgCodeResolver == null ? null : u9OrgCodeResolver.effectiveOrgCode(), "101");
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
        return trimToNull(value) != null;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
