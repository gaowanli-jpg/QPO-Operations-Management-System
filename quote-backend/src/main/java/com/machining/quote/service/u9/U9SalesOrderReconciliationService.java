package com.machining.quote.service.u9;

import com.machining.quote.dto.u9.U9CommonEntityQueryReq;
import com.machining.quote.dto.u9.U9CommonEntityRowDTO;
import com.machining.quote.dto.u9.U9SalesOrderHeadDTO;
import com.machining.quote.dto.u9.U9SalesOrderLineDTO;
import com.machining.quote.service.SalesOrderU9SyncStateService.PreparedOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class U9SalesOrderReconciliationService {
    public static final String SO_ENTITY = "UFIDA.U9.SM.SO.SO";
    public static final String SO_LINE_ENTITY = "UFIDA.U9.SM.SO.SOLine";
    private static final int HEAD_PAGE_SIZE = 10;
    private static final int LINE_PAGE_SIZE = 200;

    private static final List<String> HEAD_FIELDS = List.of(
            "ID",
            "DocNo",
            "OrderBy.Code",
            "Seller.Code",
            "SaleDepartment.Code",
            "DocumentType.Code",
            "IsPriceIncludeTax",
            "Org.Code",
            "Status"
    );
    private static final List<String> LINE_FIELDS = List.of(
            "ID",
            "SO.ID",
            "DocLineNo",
            "ItemInfo.ItemID.Code",
            "OrderByQtyTU",
            "OrderByQtyPU",
            "FinallyPriceTC",
            "DescFlexField.PrivateDescSeg1",
            "DescFlexField.PrivateDescSeg2",
            "DescFlexField.PrivateDescSeg3",
            "DescFlexField.PrivateDescSeg4",
            "DescFlexField.PrivateDescSeg5",
            "DescFlexField.PrivateDescSeg9",
            "DescFlexField.PubDescSeg2"
    );

    @Autowired
    private U9CommonEntityClient commonEntityClient;
    @Value("${erp.u9.org-code:101}")
    private String orgCode = "101";
    @Value("${erp.u9.reconciliation-grace-seconds:120}")
    private Long graceSeconds = 120L;
    private Clock clock = Clock.systemDefaultZone();

    public Result checkExisting(PreparedOrder prepared) {
        return reconcileInternal(prepared, false);
    }

    public Result reconcile(PreparedOrder prepared) {
        return reconcileInternal(prepared, true);
    }

    private Result reconcileInternal(PreparedOrder prepared, boolean applyGrace) {
        if (prepared == null || prepared.request() == null) {
            return Result.queryFailed("缺少 U9 核对请求快照");
        }
        List<U9CommonEntityRowDTO> heads;
        try {
            heads = queryHeads(prepared.request().getDocNo());
        } catch (RuntimeException error) {
            return Result.queryFailed("U9 订单头查询失败：" + U9CommonEntityClient.sanitize(error.getMessage()));
        }
        if (heads.isEmpty()) {
            if (applyGrace && withinGrace(prepared.startedAt())) {
                return Result.notFoundWait("仍在 120 秒保护期内，暂不确认失败");
            }
            return Result.notFoundConfirmed("U9 明确未找到同 DocNo 销售订单");
        }
        if (heads.size() > 1) {
            return Result.multipleFound("U9 存在多张同 DocNo 销售订单");
        }

        U9CommonEntityRowDTO head = heads.get(0);
        String soId = trimToNull(head.text("ID"));
        if (soId == null) {
            return Result.foundConflict("U9 订单缺少 ID，无法核对订单行");
        }

        List<U9CommonEntityRowDTO> lines;
        try {
            lines = queryLines(soId);
        } catch (RuntimeException error) {
            return Result.queryFailed("U9 订单行查询失败：" + U9CommonEntityClient.sanitize(error.getMessage()));
        }

        List<String> mismatches = compare(prepared.request(), head, lines);
        if (!mismatches.isEmpty()) {
            return Result.foundConflict(String.join("；", mismatches));
        }
        return Result.foundAndMatched(soId, firstNonBlank(head.text("DocNo"), prepared.request().getDocNo()), List.of());
    }

    private List<U9CommonEntityRowDTO> queryHeads(String docNo) {
        U9CommonEntityQueryReq req = U9CommonEntityQueryReq.builder()
                .page(1, HEAD_PAGE_SIZE)
                .orders("ID desc")
                .entityFullName(SO_ENTITY)
                .returnFields(HEAD_FIELDS)
                .filter("DocNo", "eq", docNo)
                .build();
        return safeList(commonEntityClient.query(req));
    }

    private List<U9CommonEntityRowDTO> queryLines(String soId) {
        List<U9CommonEntityRowDTO> result = new ArrayList<>();
        int pageIndex = 1;
        while (true) {
            U9CommonEntityQueryReq req = U9CommonEntityQueryReq.builder()
                    .page(pageIndex, LINE_PAGE_SIZE)
                    .orders("DocLineNo")
                    .entityFullName(SO_LINE_ENTITY)
                    .returnFields(LINE_FIELDS)
                    .filter("SO.ID", "eq", soId)
                    .build();
            List<U9CommonEntityRowDTO> page = safeList(commonEntityClient.query(req));
            result.addAll(page);
            if (page.size() < LINE_PAGE_SIZE) {
                break;
            }
            pageIndex++;
        }
        return result;
    }

    private List<String> compare(U9SalesOrderHeadDTO expected, U9CommonEntityRowDTO actualHead, List<U9CommonEntityRowDTO> actualLines) {
        List<String> errors = new ArrayList<>();
        compareText(errors, "组织", expectedOrg(), actualHead.text("Org.Code"));
        compareText(errors, "客户编码", expected.getOrderBy(), actualHead.text("OrderBy.Code"));
        compareText(errors, "单据类型", expected.getDocumentType(), actualHead.text("DocumentType.Code"));
        if (!isBlank(expected.getSeller())) {
            compareText(errors, "销售员", expected.getSeller(), actualHead.text("Seller.Code"));
        }
        if (!isBlank(expected.getSaleDepartment())) {
            compareText(errors, "销售部门", expected.getSaleDepartment(), actualHead.text("SaleDepartment.Code"));
        }

        List<U9SalesOrderLineDTO> expectedLines = safeList(expected.getSOLineDTOList()).stream()
                .sorted(Comparator.comparing(U9SalesOrderLineDTO::getDocLineNo, Comparator.nullsLast(Integer::compareTo)))
                .toList();
        if (expectedLines.size() != safeList(actualLines).size()) {
            errors.add("订单行数量不一致，QPO=" + expectedLines.size() + " U9=" + safeList(actualLines).size());
            return errors;
        }
        Map<Integer, U9CommonEntityRowDTO> actualByLineNo = safeList(actualLines).stream()
                .filter(row -> integer(row.value("DocLineNo")) != null)
                .collect(Collectors.toMap(
                        row -> integer(row.value("DocLineNo")),
                        row -> row,
                        (left, right) -> left,
                        LinkedHashMap::new));
        Set<Integer> expectedLineNos = expectedLines.stream()
                .map(U9SalesOrderLineDTO::getDocLineNo)
                .collect(Collectors.toSet());
        if (!actualByLineNo.keySet().containsAll(expectedLineNos)) {
            errors.add("订单行行号不一致");
            return errors;
        }
        for (U9SalesOrderLineDTO expectedLine : expectedLines) {
            U9CommonEntityRowDTO actualLine = actualByLineNo.get(expectedLine.getDocLineNo());
            compareText(errors, "行" + expectedLine.getDocLineNo() + "物料号", expectedLine.getItemCode(), actualLine.text("ItemInfo.ItemID.Code"));
            compareDecimal(errors, "行" + expectedLine.getDocLineNo() + "销售数量", expectedLine.getOrderByQtyTU(), decimal(actualLine.value("OrderByQtyTU")));
            compareDecimal(errors, "行" + expectedLine.getDocLineNo() + "计价数量", expectedLine.getOrderByQtyPU(), decimal(actualLine.value("OrderByQtyPU")));
            compareDecimal(errors, "行" + expectedLine.getDocLineNo() + "未税单价", expectedLine.getFinallyPriceTC(), decimal(actualLine.value("FinallyPriceTC")));
        }
        return errors;
    }

    private void compareText(List<String> errors, String label, String expected, String actual) {
        String expectedText = trimToNull(expected);
        String actualText = trimToNull(actual);
        if (expectedText != null && !expectedText.equals(actualText)) {
            errors.add(label + "不一致，QPO=" + expectedText + " U9=" + (actualText == null ? "空" : actualText));
        }
    }

    private void compareDecimal(List<String> errors, String label, BigDecimal expected, BigDecimal actual) {
        if (expected == null || actual == null || expected.compareTo(actual) != 0) {
            errors.add(label + "不一致，QPO=" + decimalText(expected) + " U9=" + decimalText(actual));
        }
    }

    private boolean withinGrace(LocalDateTime startedAt) {
        if (startedAt == null) {
            return false;
        }
        long seconds = graceSeconds == null || graceSeconds < 0 ? 120L : graceSeconds;
        return Duration.between(startedAt, LocalDateTime.now(clock)).compareTo(Duration.ofSeconds(seconds)) < 0;
    }

    private String expectedOrg() {
        return firstNonBlank(orgCode, "101");
    }

    private BigDecimal decimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(String.valueOf(number));
        }
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private Integer integer(Object value) {
        BigDecimal decimal = decimal(value);
        return decimal == null ? null : decimal.intValue();
    }

    private String decimalText(BigDecimal value) {
        return value == null ? "空" : value.stripTrailingZeros().toPlainString();
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public enum ResultCode {
        FOUND_AND_MATCHED,
        FOUND_CONFLICT,
        MULTIPLE_FOUND,
        NOT_FOUND_WAIT,
        NOT_FOUND_CONFIRMED,
        QUERY_FAILED
    }

    public record Result(
            ResultCode code,
            String message,
            String u9OrderId,
            String u9OrderCode,
            List<String> warnings) {
        public boolean matched() {
            return ResultCode.FOUND_AND_MATCHED.equals(code);
        }

        public static Result foundAndMatched(String u9OrderId, String u9OrderCode, List<String> warnings) {
            return new Result(ResultCode.FOUND_AND_MATCHED, "U9 单据已找到且字段匹配", u9OrderId, u9OrderCode, safeWarnings(warnings));
        }

        public static Result foundConflict(String message) {
            return new Result(ResultCode.FOUND_CONFLICT, message, null, null, List.of());
        }

        public static Result multipleFound(String message) {
            return new Result(ResultCode.MULTIPLE_FOUND, message, null, null, List.of());
        }

        public static Result notFoundWait(String message) {
            return new Result(ResultCode.NOT_FOUND_WAIT, message, null, null, List.of());
        }

        public static Result notFoundConfirmed(String message) {
            return new Result(ResultCode.NOT_FOUND_CONFIRMED, message, null, null, List.of());
        }

        public static Result queryFailed(String message) {
            return new Result(ResultCode.QUERY_FAILED, message, null, null, List.of());
        }

        private static List<String> safeWarnings(List<String> warnings) {
            return warnings == null ? List.of() : List.copyOf(warnings);
        }
    }
}
