package com.machining.quote.service.u9;

import com.machining.quote.dto.SalesOrderU9DepartmentOptionDTO;
import com.machining.quote.dto.SalesOrderU9SellerOptionDTO;
import com.machining.quote.dto.SalesOrderU9SyncOptionsResp;
import com.machining.quote.dto.u9.U9CommonEntityQueryReq;
import com.machining.quote.dto.u9.U9CommonEntityRowDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class U9MasterDataService {
    public static final String OPERATOR_ENTITY = "UFIDA.U9.CBO.HR.Operator.Operators";
    public static final String DEPARTMENT_ENTITY = "UFIDA.U9.CBO.HR.Department.Department";
    private static final int PAGE_SIZE = 200;
    private static final String SALES_OPERATOR_TYPE = "销售业务员";
    private static final String SALES_DEPARTMENT_ROOT = "2002";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Shanghai");

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
    @Value("${erp.u9.org-code:101}")
    private String orgCode = "101";
    private Clock clock = Clock.system(DISPLAY_ZONE);
    private final Map<String, CacheEntry> cache = new LinkedHashMap<>();

    public synchronized SalesOrderU9SyncOptionsResp options() {
        String expectedOrg = normalizeOrgCode();
        Instant now = clock.instant();
        CacheEntry cached = cache.get(expectedOrg);
        if (cached != null && Duration.between(cached.refreshedInstant(), now).compareTo(CACHE_TTL) < 0) {
            return copy(cached.options(), false, null);
        }
        try {
            SalesOrderU9SyncOptionsResp fresh = loadFresh(expectedOrg, now);
            cache.put(expectedOrg, new CacheEntry(copy(fresh, false, null), now));
            return fresh;
        } catch (RuntimeException error) {
            String safeMessage = U9CommonEntityClient.sanitize(error.getMessage());
            if (cached != null) {
                return copy(cached.options(), true, "U9主数据暂时不可用，已使用过期缓存：" + safeMessage);
            }
            throw new IllegalStateException("U9主数据暂不可用：" + safeMessage);
        }
    }

    private SalesOrderU9SyncOptionsResp loadFresh(String expectedOrg, Instant now) {
        List<U9CommonEntityRowDTO> operatorRows = queryAll(OPERATOR_ENTITY, OPERATOR_FIELDS, expectedOrg);
        List<U9CommonEntityRowDTO> departmentRows = queryAll(DEPARTMENT_ENTITY, DEPARTMENT_FIELDS, expectedOrg);
        List<SalesOrderU9SellerOptionDTO> sellers = sellerOptions(operatorRows, expectedOrg);
        List<SalesOrderU9DepartmentOptionDTO> departments = departmentOptions(departmentRows, sellers, expectedOrg);

        SalesOrderU9SyncOptionsResp resp = new SalesOrderU9SyncOptionsResp();
        resp.setOrgCode(expectedOrg);
        resp.setStale(false);
        resp.setRefreshedAt(LocalDateTime.ofInstant(now, DISPLAY_ZONE));
        resp.setSellers(sellers);
        resp.setDepartments(departments);
        return resp;
    }

    private List<U9CommonEntityRowDTO> queryAll(String entityFullName, List<String> returnFields, String expectedOrg) {
        List<U9CommonEntityRowDTO> result = new ArrayList<>();
        int pageIndex = 1;
        while (true) {
            U9CommonEntityQueryReq req = U9CommonEntityQueryReq.builder()
                    .page(pageIndex, PAGE_SIZE)
                    .orders("Code")
                    .entityFullName(entityFullName)
                    .returnFields(returnFields)
                    .filter("Org.Code", "eq", expectedOrg)
                    .build();
            List<U9CommonEntityRowDTO> page = commonEntityClient.query(req);
            result.addAll(page == null ? List.of() : page);
            if (page == null || page.size() < PAGE_SIZE) {
                break;
            }
            pageIndex++;
        }
        return result;
    }

    private List<SalesOrderU9SellerOptionDTO> sellerOptions(List<U9CommonEntityRowDTO> rows, String expectedOrg) {
        Map<String, SalesOrderU9SellerOptionDTO> byCode = new LinkedHashMap<>();
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
            byCode.put(code, new SalesOrderU9SellerOptionDTO(
                    code,
                    name,
                    trimToNull(row.text("Dept.Code")),
                    trimToNull(row.text("Dept.Name"))
            ));
        }
        return byCode.values().stream()
                .sorted(Comparator.comparing(SalesOrderU9SellerOptionDTO::getCode))
                .toList();
    }

    private List<SalesOrderU9DepartmentOptionDTO> departmentOptions(
            List<U9CommonEntityRowDTO> rows,
            List<SalesOrderU9SellerOptionDTO> sellers,
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
            allDepartments.put(code, new DepartmentSource(
                    code,
                    name,
                    trimToNull(row.text("ParentNode.Code")),
                    firstNonBlank(row.text("TreeDisplayName"), name)
            ));
        }

        Set<String> includedCodes = salesDepartmentTreeCodes(allDepartments);
        for (SalesOrderU9SellerOptionDTO seller : sellers == null ? List.<SalesOrderU9SellerOptionDTO>of() : sellers) {
            String code = trimToNull(seller.getDepartmentCode());
            if (code != null) {
                includedCodes.add(code);
                if (!allDepartments.containsKey(code) && trimToNull(seller.getDepartmentName()) != null) {
                    allDepartments.put(code, new DepartmentSource(code, seller.getDepartmentName(), null, seller.getDepartmentName()));
                }
            }
        }

        return includedCodes.stream()
                .map(allDepartments::get)
                .filter(source -> source != null)
                .sorted(Comparator.comparing(DepartmentSource::code))
                .map(source -> new SalesOrderU9DepartmentOptionDTO(source.code(), source.name(), source.treeDisplayName()))
                .toList();
    }

    private Set<String> salesDepartmentTreeCodes(Map<String, DepartmentSource> allDepartments) {
        Set<String> included = new LinkedHashSet<>();
        if (allDepartments.containsKey(SALES_DEPARTMENT_ROOT)) {
            included.add(SALES_DEPARTMENT_ROOT);
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

    private boolean hasSalesOperatorType(U9CommonEntityRowDTO row) {
        return containsText(row.value("OperatorLine.OperatorType"), SALES_OPERATOR_TYPE)
                || containsText(row.value("OperatorLine"), SALES_OPERATOR_TYPE);
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

    private SalesOrderU9SyncOptionsResp copy(SalesOrderU9SyncOptionsResp source, boolean stale, String warning) {
        SalesOrderU9SyncOptionsResp target = new SalesOrderU9SyncOptionsResp();
        target.setOrgCode(source.getOrgCode());
        target.setStale(stale);
        target.setRefreshedAt(source.getRefreshedAt());
        target.setWarning(warning);
        target.setSellers(source.getSellers() == null ? List.of() : List.copyOf(source.getSellers()));
        target.setDepartments(source.getDepartments() == null ? List.of() : List.copyOf(source.getDepartments()));
        return target;
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

    private record DepartmentSource(String code, String name, String parentCode, String treeDisplayName) {
    }

    private record CacheEntry(SalesOrderU9SyncOptionsResp options, Instant refreshedInstant) {
    }
}
