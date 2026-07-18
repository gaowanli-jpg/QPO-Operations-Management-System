package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.dto.AuditLogDTO;
import com.machining.quote.dto.AuditLogQueryReq;
import com.machining.quote.dto.PageResult;
import com.machining.quote.entity.AuditLog;
import com.machining.quote.mapper.AuditLogMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AuditLogQueryService {
    private static final List<String> ROLES = List.of("ADMIN", "MARKET", "ENGINEER", "SYSTEM");
    private static final List<String> MODULES = List.of("AUTH", "SYSTEM", "DICT", "ENGINEER", "MARKET", "ORDER", "DELIVERY");
    private static final List<String> TARGET_TYPES = List.of(
            "erp_user",
            "dict_material",
            "dict_process",
            "dict_markup_fee",
            "dict_extra_fee_item",
            "order_rfq",
            "item_quote_version",
            "order_delivery_ticket",
            "order_process_sheet"
    );
    private static final Map<String, List<String>> MODULE_ACTION_PREFIXES = Map.of(
            "AUTH", List.of("AUTH_"),
            "SYSTEM", List.of("USER_", "QUOTE_TEMPLATE_", "AUDIT_"),
            "DICT", List.of("DICT_"),
            "ENGINEER", List.of("ITEM_", "REPEAT_", "QUOTE_REFRESH_TASK_SUBMIT"),
            "MARKET", List.of("QUOTE_", "INTERNAL_TARGET_", "RFQ_"),
            "ORDER", List.of("ORDER_"),
            "DELIVERY", List.of("DELIVERY_", "PROCESS_SHEET_")
    );
    public static final int EXPORT_LIMIT = 5000;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private AuditLogMapper auditLogMapper;
    @Autowired(required = false)
    private DataScopeService dataScopeService;

    public PageResult<AuditLogDTO> listLogs(AuditLogQueryReq req) {
        AuditLogQueryReq safeReq = req == null ? new AuditLogQueryReq() : req;
        int safePage = normalizePage(safeReq.getPage());
        int safePageSize = normalizePageSize(safeReq.getPageSize());

        QueryWrapper<AuditLog> countQuery = buildQuery(safeReq);
        Long total = auditLogMapper.selectCount(countQuery);
        if (total == null || total == 0) {
            return PageResult.of(List.of(), 0, safePage, safePageSize);
        }

        QueryWrapper<AuditLog> listQuery = buildQuery(safeReq);
        listQuery.orderByDesc("created_at").orderByDesc("id");
        listQuery.last("LIMIT " + safePageSize + " OFFSET " + ((safePage - 1L) * safePageSize));
        List<AuditLogDTO> records = auditLogMapper.selectList(listQuery).stream()
                .map(this::toDTO)
                .toList();
        return PageResult.of(records, total, safePage, safePageSize);
    }

    public AuditLogDTO detail(Integer id) {
        QueryWrapper<AuditLog> detailQuery = new QueryWrapper<>();
        detailQuery.eq("id", id);
        applyAuditDataScope(detailQuery);
        AuditLog log = auditLogMapper.selectOne(detailQuery);
        if (log == null) {
            throw new RuntimeException("操作日志不存在");
        }
        return toDTO(log);
    }


    public byte[] exportCsv(AuditLogQueryReq req) {
        AuditLogQueryReq safeReq = req == null ? new AuditLogQueryReq() : req;
        QueryWrapper<AuditLog> exportQuery = buildQuery(safeReq);
        exportQuery.orderByDesc("created_at").orderByDesc("id");
        exportQuery.last("LIMIT " + EXPORT_LIMIT);

        StringBuilder csv = new StringBuilder("\uFEFF");
        appendCsvRow(csv, List.of(
                "日志ID",
                "时间",
                "操作人",
                "角色",
                "模块",
                "动作",
                "结果",
                "对象类型",
                "对象ID",
                "摘要",
                "错误信息",
                "Request ID",
                "IP",
                "方法",
                "路径",
                "User-Agent",
                "操作前数据",
                "操作后数据"
        ));

        List<AuditLogDTO> records = auditLogMapper.selectList(exportQuery).stream()
                .map(this::toDTO)
                .toList();
        for (AuditLogDTO record : records) {
            appendCsvRow(csv, List.of(
                    text(record.getId()),
                    formatDateTime(record.getCreatedAt()),
                    text(record.getUsername()),
                    text(record.getRole()),
                    text(record.getModule()),
                    text(record.getActionLabel()),
                    text(record.getResult()),
                    text(record.getTargetType()),
                    text(record.getTargetId()),
                    text(record.getMessage()),
                    text(record.getErrorMessage()),
                    text(record.getRequestId()),
                    text(record.getIpAddress()),
                    text(record.getRequestMethod()),
                    text(record.getRequestPath()),
                    text(record.getUserAgent()),
                    text(record.getBeforeData()),
                    text(record.getAfterData())
            ));
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }
    public Map<String, Object> options() {
        QueryWrapper<AuditLog> actionQuery = new QueryWrapper<>();
        actionQuery.select("DISTINCT action");
        actionQuery.isNotNull("action");
        applyAuditDataScope(actionQuery);
        actionQuery.orderByAsc("action");
        List<Map<String, String>> actions = auditLogMapper.selectObjs(actionQuery).stream()
                .map(String::valueOf)
                .filter(action -> !isBlank(action))
                .map(this::actionOption)
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("roles", ROLES);
        result.put("modules", MODULES);
        result.put("actions", actions);
        result.put("targetTypes", TARGET_TYPES);
        result.put("results", List.of("SUCCESS", "FAILED"));
        return result;
    }

    private QueryWrapper<AuditLog> buildQuery(AuditLogQueryReq req) {
        QueryWrapper<AuditLog> qw = new QueryWrapper<>();
        LocalDateTime[] range = resolveRange(req);
        qw.ge("created_at", range[0]);
        qw.le("created_at", range[1]);
        if (!isBlank(req.getUsername())) {
            qw.like("username", req.getUsername().trim());
        }
        if (!isBlank(req.getRole())) {
            qw.eq("role", req.getRole().trim().toUpperCase(Locale.ROOT));
        }
        if (!isBlank(req.getModule())) {
            applyModuleFilter(qw, req.getModule().trim().toUpperCase(Locale.ROOT));
        }
        if (!isBlank(req.getAction())) {
            qw.eq("action", req.getAction().trim());
        }
        if (!isBlank(req.getTargetType())) {
            qw.eq("target_type", req.getTargetType().trim());
        }
        if (!isBlank(req.getTargetId())) {
            qw.eq("target_id", req.getTargetId().trim());
        }
        if (!isBlank(req.getRequestId())) {
            qw.eq("request_id", req.getRequestId().trim());
        }
        if (!isBlank(req.getResult())) {
            String result = req.getResult().trim().toUpperCase(Locale.ROOT);
            if ("SUCCESS".equals(result)) {
                qw.and(wrapper -> wrapper.eq("result", result).or().isNull("result"));
            } else {
                qw.eq("result", result);
            }
        }
        if (!isBlank(req.getKeyword())) {
            String keyword = req.getKeyword().trim();
            qw.and(wrapper -> wrapper
                    .like("username", keyword)
                    .or().like("action", keyword)
                    .or().like("target_type", keyword)
                    .or().like("target_id", keyword)
                    .or().like("message", keyword)
                    .or().like("error_message", keyword)
                    .or().like("before_data", keyword)
                    .or().like("after_data", keyword));
        }
        applyAuditDataScope(qw);
        return qw;
    }

    private void applyAuditDataScope(QueryWrapper<AuditLog> qw) {
        if (dataScopeService != null) {
            dataScopeService.applyAuditScope(qw);
        }
    }
    private void applyModuleFilter(QueryWrapper<AuditLog> qw, String module) {
        List<String> prefixes = MODULE_ACTION_PREFIXES.getOrDefault(module, List.of());
        qw.and(wrapper -> {
            wrapper.eq("module", module);
            for (String prefix : prefixes) {
                if ("MARKET".equals(module) && "QUOTE_".equals(prefix)) {
                    wrapper.or(nested -> nested
                            .likeRight("action", prefix)
                            .notLikeRight("action", "QUOTE_TEMPLATE_")
                            .ne("action", "QUOTE_REFRESH_TASK_SUBMIT"));
                } else {
                    wrapper.or().likeRight("action", prefix);
                }
            }
            if ("ENGINEER".equals(module)) {
                wrapper.or().eq("action", "ORDER_QUOTE_COMPLETE");
                wrapper.or().eq("action", "QUOTE_REFRESH_TASK_SUBMIT");
            }
        });
    }

    private LocalDateTime[] resolveRange(AuditLogQueryReq req) {
        LocalDateTime end = parseDateTime(req.getEndTime(), LocalDateTime.now());
        LocalDateTime start = parseDateTime(req.getStartTime(), end.minusDays(7));
        return new LocalDateTime[]{start, end};
    }

    private LocalDateTime parseDateTime(String value, LocalDateTime fallback) {
        if (isBlank(value)) {
            return fallback;
        }
        String text = value.trim().replace("T", " ");
        if (text.length() >= 19) {
            text = text.substring(0, 19);
        }
        try {
            return LocalDateTime.parse(text, DATE_TIME);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDate.parse(text.substring(0, Math.min(text.length(), 10))).atTime(LocalTime.MIN);
            } catch (Exception ignoredAgain) {
                return fallback;
            }
        }
    }

    private AuditLogDTO toDTO(AuditLog log) {
        AuditLogDTO dto = new AuditLogDTO();
        dto.setId(log.getId());
        dto.setUsername(log.getUsername());
        dto.setRole(log.getRole());
        dto.setAction(log.getAction());
        dto.setActionLabel(AuditService.labelForAction(log.getAction()));
        dto.setModule(isBlank(log.getModule()) ? AuditService.inferModule(log.getAction()) : log.getModule());
        dto.setTargetType(log.getTargetType());
        dto.setTargetId(log.getTargetId());
        dto.setResult(isBlank(log.getResult()) ? "SUCCESS" : log.getResult());
        dto.setMessage(isBlank(log.getMessage()) ? AuditService.fallbackMessage(log.getAction(), log.getTargetType(), log.getTargetId()) : log.getMessage());
        dto.setBeforeData(log.getBeforeData());
        dto.setAfterData(log.getAfterData());
        dto.setIpAddress(log.getIpAddress());
        dto.setUserAgent(log.getUserAgent());
        dto.setRequestMethod(log.getRequestMethod());
        dto.setRequestPath(log.getRequestPath());
        dto.setRequestId(log.getRequestId());
        dto.setErrorMessage(log.getErrorMessage());
        dto.setCreatedAt(log.getCreatedAt());
        return dto;
    }

    private Map<String, String> actionOption(String action) {
        Map<String, String> option = new LinkedHashMap<>();
        option.put("value", action);
        option.put("label", AuditService.labelForAction(action));
        option.put("module", AuditService.inferModule(action));
        return option;
    }


    private void appendCsvRow(StringBuilder csv, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                csv.append(',');
            }
            csv.append(csvCell(values.get(i)));
        }
        csv.append('\n');
    }

    private String csvCell(String value) {
        String text = value == null ? "" : value;
        String escaped = text.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n") || escaped.contains("\r")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? "" : DATE_TIME.format(value);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
