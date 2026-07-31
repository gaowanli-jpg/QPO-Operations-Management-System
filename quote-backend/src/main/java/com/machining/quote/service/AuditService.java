package com.machining.quote.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.machining.quote.config.AuthContext;
import com.machining.quote.config.RequestTraceContext;
import com.machining.quote.entity.AuditLog;
import com.machining.quote.mapper.AuditLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final String MASKED = "[已脱敏]";
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password",
            "newpassword",
            "oldpassword",
            "confirmpassword",
            "passwordhash",
            "token",
            "authtoken",
            "jwttoken",
            "clientsecret",
            "accesssecret",
            "secretkey",
            "miniosecret",
            "erpminiokey",
            "erpminiosecretkey",
            "authorization",
            "authorizationheader",
            "xauthtoken",
            "cookie",
            "setcookie",
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
        AuditLog auditLog = new AuditLog();
        auditLog.setUsername(user == null ? "system" : user.username());
        auditLog.setRole(user == null ? "SYSTEM" : user.role());
        auditLog.setAction(action);
        auditLog.setModule(isBlank(module) ? inferModule(action) : module.trim().toUpperCase(Locale.ROOT));
        auditLog.setResult(isBlank(result) ? "SUCCESS" : result.trim().toUpperCase(Locale.ROOT));
        auditLog.setMessage(isBlank(message) ? fallbackMessage(action, targetType, targetId) : message.trim());
        auditLog.setTargetType(targetType);
        auditLog.setTargetId(targetId == null ? null : String.valueOf(targetId));
        auditLog.setBeforeData(toText(beforeData));
        auditLog.setAfterData(toText(afterData));
        if (trace != null) {
            auditLog.setRequestId(trace.requestId());
            auditLog.setIpAddress(trace.ipAddress());
            auditLog.setUserAgent(trace.userAgent());
            auditLog.setRequestMethod(trace.method());
            auditLog.setRequestPath(trace.path());
        }
        auditLog.setErrorMessage(isBlank(errorMessage) ? null : errorMessage.trim());
        auditLog.setCreatedAt(LocalDateTime.now());
        try {
            auditLogMapper.insert(auditLog);
        } catch (RuntimeException e) {
            log.error("Audit log record failed action={} module={} objectType={} objectId={} requestId={}",
                    auditLog.getAction(), auditLog.getModule(), auditLog.getTargetType(), auditLog.getTargetId(),
                    auditLog.getRequestId(), e);
            throw e;
        }
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
        if (startsWith(action, "AUDIT_")) return "SYSTEM";
        if (startsWith(action, "QUOTE_TEMPLATE_")) return "SYSTEM";
        if (startsWith(action, "SYSTEM_STORAGE_CONFIG_")) return "SYSTEM";
        if (startsWith(action, "SYSTEM_INTEGRATION_CONFIG_")) return "SYSTEM";
        if ("QUOTE_REFRESH_TASK_SUBMIT".equalsIgnoreCase(action)) return "ENGINEER";
        if ("RFQ_IMPORT".equalsIgnoreCase(action)) return "MARKET";
        if ("SALES_ORDER_IMPORT_U9".equalsIgnoreCase(action)) return "MARKET";
        if ("SALES_ORDER_IMPORT_QPO".equalsIgnoreCase(action)) return "MARKET";
        if ("SALES_ORDER_U9_SYNC".equalsIgnoreCase(action)) return "MARKET";
        if ("ORDER_QUOTE_COMPLETE".equalsIgnoreCase(action)) return "ENGINEER";
        if ("PRICING_SHEET_EXPORT".equalsIgnoreCase(action)) return "ENGINEER";
        if (startsWith(action, "DICT_")) return "DICT";
        if (startsWith(action, "USER_")
                || startsWith(action, "SYSTEM_USER_")
                || startsWith(action, "SYSTEM_ROLE_")
                || startsWith(action, "SYSTEM_DEPT_")
                || startsWith(action, "SYSTEM_POST_")
                || startsWith(action, "CUSTOMER_MASTER_")) return "SYSTEM";
        if (startsWith(action, "ORDER_")) return "ORDER";
        if (startsWith(action, "DELIVERY_") || startsWith(action, "PROCESS_SHEET_")) return "DELIVERY";
        if (startsWith(action, "QUOTE_") || startsWith(action, "INTERNAL_TARGET_") || startsWith(action, "RELEASE_")) return "MARKET";
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
            case "USER_PROFILE_UPDATE" -> "更新个人资料";
            case "USER_DELETE" -> "删除用户";
            case "SYSTEM_USER_CREATE" -> "新增用户";
            case "SYSTEM_USER_UPDATE" -> "编辑用户";
            case "SYSTEM_USER_DISABLE" -> "禁用用户";
            case "SYSTEM_USER_ENABLE" -> "启用用户";
            case "SYSTEM_USER_DELETE" -> "删除用户";
            case "SYSTEM_USER_ASSIGN_ROLE" -> "用户分配角色";
            case "SYSTEM_USER_ORG_UPDATE" -> "更新用户组织身份";
            case "SYSTEM_ROLE_CREATE" -> "新增角色";
            case "SYSTEM_ROLE_UPDATE" -> "编辑角色";
            case "SYSTEM_ROLE_DELETE" -> "删除角色";
            case "SYSTEM_ROLE_ASSIGN_PERMISSION" -> "角色分配权限";
            case "SYSTEM_ROLE_ASSIGN_DATA_SCOPE" -> "角色分配数据范围";
            case "SYSTEM_DEPT_CREATE" -> "新增部门";
            case "SYSTEM_DEPT_UPDATE" -> "编辑部门";
            case "SYSTEM_DEPT_ENABLE" -> "启用部门";
            case "SYSTEM_DEPT_DISABLE" -> "禁用部门";
            case "SYSTEM_DEPT_DELETE" -> "删除部门";
            case "SYSTEM_POST_CREATE" -> "新增岗位";
            case "SYSTEM_POST_UPDATE" -> "编辑岗位";
            case "SYSTEM_POST_ENABLE" -> "启用岗位";
            case "SYSTEM_POST_DISABLE" -> "禁用岗位";
            case "SYSTEM_POST_DELETE" -> "删除岗位";
            case "CUSTOMER_MASTER_CREATE" -> "新增客户资料";
            case "CUSTOMER_MASTER_UPDATE" -> "更新客户资料";
            case "CUSTOMER_MASTER_DELETE" -> "删除客户资料";
            case "SYSTEM_STORAGE_CONFIG_CREATE" -> "新增文件存储配置";
            case "SYSTEM_STORAGE_CONFIG_UPDATE" -> "编辑文件存储配置";
            case "SYSTEM_STORAGE_CONFIG_TEST" -> "测试文件存储配置";
            case "SYSTEM_STORAGE_CONFIG_TEST_FAILED" -> "测试文件存储配置失败";
            case "SYSTEM_STORAGE_CONFIG_ACTIVATE" -> "激活文件存储配置";
            case "SYSTEM_STORAGE_CONFIG_DISABLE" -> "停用文件存储配置";
            case "SYSTEM_INTEGRATION_CONFIG_CREATE" -> "新增系统对接配置";
            case "SYSTEM_INTEGRATION_CONFIG_UPDATE" -> "编辑系统对接配置";
            case "SYSTEM_INTEGRATION_CONFIG_TEST" -> "测试系统对接配置";
            case "SYSTEM_INTEGRATION_CONFIG_TEST_FAILED" -> "测试系统对接配置失败";
            case "SYSTEM_INTEGRATION_CONFIG_ACTIVATE" -> "激活系统对接配置";
            case "SYSTEM_INTEGRATION_CONFIG_DISABLE" -> "停用系统对接配置";
            case "QUOTE_TEMPLATE_UPLOAD" -> "上传报价模板";
            case "RFQ_IMPORT" -> "导入 RFQ";
            case "SALES_ORDER_IMPORT_U9" -> "导入销售订单并同步U9";
            case "SALES_ORDER_IMPORT_QPO" -> "导入销售订单到独立主表";
            case "SALES_ORDER_U9_SYNC" -> "销售订单同步U9";
            case "QUOTE_EXPORT_TASK_SUBMIT" -> "提交报价单导出任务";
            case "QUOTE_REFRESH_TASK_SUBMIT" -> "提交一键刷新核价任务";
            case "AUDIT_RETENTION_CONFIG_UPDATE" -> "更新操作日志保留策略";
            case "AUDIT_LOG_CLEANUP_RUN" -> "执行操作日志自动清理";
            case "AUDIT_LOG_CLEANUP_FAILED" -> "操作日志自动清理失败";
            case "DICT_MATERIAL_CREATE" -> "新增材料";
            case "DICT_MATERIAL_UPDATE" -> "更新材料";
            case "DICT_MATERIAL_DELETE" -> "删除材料";
            case "DICT_PROCESS_CREATE" -> "新增工艺";
            case "DICT_PROCESS_UPDATE" -> "更新工艺";
            case "DICT_PROCESS_DELETE" -> "删除工艺";
            case "DICT_MARKUP_FEE_CREATE" -> "新增百分比费用";
            case "DICT_MARKUP_FEE_UPDATE" -> "更新百分比费用";
            case "DICT_MARKUP_FEE_DELETE" -> "删除百分比费用";
            case "DICT_ASSEMBLY_MARKUP_FEE_CREATE" -> "新增组装件百分比费用";
            case "DICT_ASSEMBLY_MARKUP_FEE_UPDATE" -> "更新组装件百分比费用";
            case "DICT_ASSEMBLY_MARKUP_FEE_DELETE" -> "删除组装件百分比费用";
            case "DICT_EXTRA_FEE_CREATE" -> "新增非工时费用";
            case "DICT_EXTRA_FEE_UPDATE" -> "更新非工时费用";
            case "DICT_EXTRA_FEE_DELETE" -> "删除非工时费用";
            case "ITEM_QUOTE_SAVE" -> "保存逐件核价";
            case "ORDER_QUOTE_COMPLETE" -> "完成工程报价";
            case "PRICING_SHEET_EXPORT" -> "生成核价单";
            case "QUOTE_PROCESS_SAVE_QUOTE" -> "保存报价单";
            case "ORDER_STATUS_ROLLBACK_QUOTED" -> "已报价退回已核价";
            case "ORDER_STATUS_ROLLBACK_PRICED" -> "已核价退回未核价";
            case "ORDER_DISCARD" -> "废弃订单";
            case "ORDER_RESTORE" -> "恢复废弃订单";
            case "QUOTE_LEAD_TIME_UPDATE" -> "更新客户交期";
            case "INTERNAL_TARGET_LEAD_TIME_UPDATE" -> "更新内部目标交期";
            case "RELEASE_ITEM_VERSION_UPDATE" -> "议价提单修改零件版本";
            case "RELEASE_DRAWING_REPLACE" -> "议价提单替换图纸";
            case "DELIVERY_TICKET_GENERATE" -> "生成提单";
            case "DELIVERY_TICKET_EXPORT" -> "导出提单表";
            case "DELIVERY_PROCESS_SHEET_EXPORT" -> "导出工艺表";
            case "DELIVERY_MES_MATERIAL_SYNC_SUBMIT" -> "提交MES物料号同步";
            case "DELIVERY_MES_MATERIAL_SYNC_SUCCESS" -> "MES物料号同步成功";
            case "DELIVERY_MES_MATERIAL_SYNC_FAILED" -> "MES物料号同步失败";
            case "DELIVERY_WMS_ALLOCATION_SUBMIT" -> "提交WMS锁库";
            case "DELIVERY_WMS_ALLOCATION_SUCCESS" -> "WMS锁库成功";
            case "DELIVERY_WMS_ALLOCATION_FAILED" -> "WMS锁库失败";
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
