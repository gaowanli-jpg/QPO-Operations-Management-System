package com.machining.quote.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.machining.quote.config.AuthContext;
import com.machining.quote.config.RequestTraceContext;
import com.machining.quote.entity.AuditLog;
import com.machining.quote.mapper.AuditLogMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class AuditService {
    private static final String MASKED = "[已脱敏]";
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password",
            "newpassword",
            "oldpassword",
            "confirmpassword",
            "passwordhash",
            "token",
            "authorization",
            "xauthtoken",
            "templatefile",
            "file",
            "files",
            "drawings"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private AuditLogMapper auditLogMapper;

    public void record(String action, String targetType, Object targetId, Object beforeData, Object afterData) {
        record(
                action,
                targetType,
                targetId,
                beforeData,
                afterData,
                inferModule(action),
                "SUCCESS",
                labelForAction(action),
                null
        );
    }

    public void record(
            String action,
            String targetType,
            Object targetId,
            Object beforeData,
            Object afterData,
            String module,
            String result,
            String message,
            String errorMessage) {
        AuthContext.SessionUser user = AuthContext.get();
        RequestTraceContext.RequestTrace trace = RequestTraceContext.get();
        AuditLog log = new AuditLog();
        log.setUsername(user == null ? "system" : user.username());
        log.setRole(user == null ? "SYSTEM" : user.role());
        log.setAction(action);
        log.setModule(isBlank(module) ? inferModule(action) : module.trim().toUpperCase(Locale.ROOT));
        log.setResult(isBlank(result) ? "SUCCESS" : result.trim().toUpperCase(Locale.ROOT));
        log.setMessage(isBlank(message) ? fallbackMessage(action, targetType, targetId) : message.trim());
        log.setTargetType(targetType);
        log.setTargetId(targetId == null ? null : String.valueOf(targetId));
        log.setBeforeData(toText(beforeData));
        log.setAfterData(toText(afterData));
        if (trace != null) {
            log.setRequestId(trace.requestId());
            log.setIpAddress(trace.ipAddress());
            log.setUserAgent(trace.userAgent());
            log.setRequestMethod(trace.method());
            log.setRequestPath(trace.path());
        }
        log.setErrorMessage(isBlank(errorMessage) ? null : errorMessage.trim());
        log.setCreatedAt(LocalDateTime.now());
        auditLogMapper.insert(log);
    }

    private String toText(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(redact(value));
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    private Object redact(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> source) {
            Map<String, Object> masked = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : source.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (isSensitiveKey(key)) {
                    masked.put(key, MASKED);
                } else {
                    masked.put(key, redact(entry.getValue()));
                }
            }
            return masked;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> masked = new ArrayList<>();
            for (Object item : iterable) {
                masked.add(redact(item));
            }
            return masked;
        }
        if (value != null && value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            List<Object> masked = new ArrayList<>();
            for (int i = 0; i < length; i++) {
                masked.add(redact(java.lang.reflect.Array.get(value, i)));
            }
            return masked;
        }
        if (!isSimpleValue(value)) {
            try {
                Map<String, Object> converted = objectMapper.convertValue(
                        value,
                        new TypeReference<LinkedHashMap<String, Object>>() {}
                );
                return redact(converted);
            } catch (IllegalArgumentException ignored) {
                return value;
            }
        }
        return value;
    }

    private boolean isSimpleValue(Object value) {
        return value instanceof CharSequence
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Enum<?>
                || value instanceof TemporalAccessor
                || value.getClass().getPackageName().startsWith("java.");
    }

    private boolean isSensitiveKey(String key) {
        return SENSITIVE_KEYS.contains(normalizeKey(key));
    }

    private String normalizeKey(String key) {
        return key == null ? "" : key.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
    }

    public static String inferModule(String action) {
        if (startsWith(action, "QUOTE_TEMPLATE_")) return "SYSTEM";
        if ("QUOTE_REFRESH_TASK_SUBMIT".equalsIgnoreCase(action)) return "ENGINEER";
        if ("RFQ_IMPORT".equalsIgnoreCase(action)) return "MARKET";
        if ("ORDER_QUOTE_COMPLETE".equalsIgnoreCase(action)) return "ENGINEER";
        if (startsWith(action, "DICT_")) return "DICT";
        if (startsWith(action, "USER_")) return "SYSTEM";
        if (startsWith(action, "ORDER_")) return "ORDER";
        if (startsWith(action, "DELIVERY_") || startsWith(action, "PROCESS_SHEET_")) return "DELIVERY";
        if (startsWith(action, "QUOTE_") || startsWith(action, "INTERNAL_TARGET_")) return "MARKET";
        if (startsWith(action, "ITEM_") || startsWith(action, "REPEAT_")) return "ENGINEER";
        if (startsWith(action, "AUTH_")) return "AUTH";
        return "SYSTEM";
    }

    public static String labelForAction(String action) {
        if (action == null) return "系统操作";
        return switch (action) {
            case "AUTH_LOGIN_SUCCESS" -> "登录成功";
            case "AUTH_LOGIN_FAILED" -> "登录失败";
            case "AUTH_ACCOUNT_LOCKED" -> "账号锁定";
            case "AUTH_LOGOUT" -> "登出";
            case "USER_CREATE" -> "新增用户";
            case "USER_UPDATE" -> "编辑用户";
            case "USER_CHANGE_PASSWORD" -> "修改密码";
            case "USER_DELETE" -> "删除用户";
            case "QUOTE_TEMPLATE_UPLOAD" -> "上传报价模板";
            case "RFQ_IMPORT" -> "导入 RFQ";
            case "QUOTE_EXPORT_TASK_SUBMIT" -> "提交报价单导出任务";
            case "QUOTE_REFRESH_TASK_SUBMIT" -> "提交一键刷新核价任务";
            case "DICT_MATERIAL_CREATE" -> "新增材料";
            case "DICT_MATERIAL_UPDATE" -> "更新材料";
            case "DICT_MATERIAL_DELETE" -> "删除材料";
            case "DICT_PROCESS_CREATE" -> "新增工艺";
            case "DICT_PROCESS_UPDATE" -> "更新工艺";
            case "DICT_PROCESS_DELETE" -> "删除工艺";
            case "DICT_MARKUP_FEE_CREATE" -> "新增百分比费用";
            case "DICT_MARKUP_FEE_UPDATE" -> "更新百分比费用";
            case "DICT_MARKUP_FEE_DELETE" -> "删除百分比费用";
            case "DICT_EXTRA_FEE_CREATE" -> "新增非工时费用";
            case "DICT_EXTRA_FEE_UPDATE" -> "更新非工时费用";
            case "DICT_EXTRA_FEE_DELETE" -> "删除非工时费用";
            case "ITEM_QUOTE_SAVE" -> "保存逐件核价";
            case "ORDER_QUOTE_COMPLETE" -> "完成工程报价";
            case "QUOTE_LEAD_TIME_UPDATE" -> "更新客户交期";
            case "INTERNAL_TARGET_LEAD_TIME_UPDATE" -> "更新内部目标交期";
            case "DELIVERY_TICKET_GENERATE" -> "生成提单";
            case "ORDER_DELETE_COMPLETELY" -> "彻底删除订单";
            case "ORDER_SUPPLEMENT_DRAWINGS" -> "补传图纸";
            default -> action;
        };
    }

    public static String fallbackMessage(String action, String targetType, Object targetId) {
        String label = labelForAction(action);
        if (isBlank(targetType) && targetId == null) {
            return label;
        }
        return label + " · " + (targetType == null ? "-" : targetType) + "#" + (targetId == null ? "-" : targetId);
    }

    private static boolean startsWith(String value, String prefix) {
        return value != null && value.toUpperCase(Locale.ROOT).startsWith(prefix);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
