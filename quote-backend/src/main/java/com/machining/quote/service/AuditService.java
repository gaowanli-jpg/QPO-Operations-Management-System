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
            "tokenhash",
            "authtoken",
            "callbacktoken",
            "jwttoken",
            "clientsecret",
            "accesssecret",
            "secretkey",
            "sharedsecret",
            "sharedsecretcipher",
            "signature",
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
        if (startsWith(action, "SYSTEM_DATACENTER_SSO_CONFIG_")) return "SYSTEM";
        if (startsWith(action, "SYSTEM_DATA_CENTER_CONFIG_")) return "SYSTEM";
        if (startsWith(action, "SYSTEM_DATA_CENTER_ENDPOINT_")) return "SYSTEM";
        if (startsWith(action, "SYSTEM_INBOUND_CALLBACK_")) return "SYSTEM";
        if (startsWith(action, "SYSTEM_U9_")) return "SYSTEM";
        if ("U9_CUSTOMER_SYNC".equalsIgnoreCase(action)) return "SYSTEM";
        if ("U9_CUSTOMER_SYNC_SCHEDULE_UPDATE".equalsIgnoreCase(action)) return "SYSTEM";
        if ("CUSTOMER_SUPPLIER_CODE_UPDATE".equalsIgnoreCase(action)) return "SYSTEM";
        if ("U9_SYNC_OPTIONS_REFRESH".equalsIgnoreCase(action)) return "MARKET";
        if ("QUOTE_REFRESH_TASK_SUBMIT".equalsIgnoreCase(action)) return "ENGINEER";
        if ("RFQ_IMPORT".equalsIgnoreCase(action)) return "MARKET";
        if ("SALES_ORDER_IMPORT_QPO".equalsIgnoreCase(action)) return "MARKET";
        if ("SALES_ORDER_DIRTY_SNAPSHOT_REPAIR".equalsIgnoreCase(action)) return "MARKET";
        if ("SALES_ORDER_UPDATE".equalsIgnoreCase(action)) return "MARKET";
        if ("SALES_ORDER_ITEM_UPDATE".equalsIgnoreCase(action)) return "MARKET";
        if ("SALES_ORDER_ITEM_DATA_REPLACEMENT".equalsIgnoreCase(action)) return "MARKET";
        if ("SALES_ORDER_U9_SYNC".equalsIgnoreCase(action)) return "MARKET";
        if ("SALES_ORDER_U9_ASSIGNMENT_UPDATE".equalsIgnoreCase(action)) return "MARKET";
        if ("SALES_ORDER_U9_ORDER_DATE_UPDATE".equalsIgnoreCase(action)) return "MARKET";
        if ("SALES_ORDER_U9_ITEM_UPDATE".equalsIgnoreCase(action)) return "MARKET";
        if ("SALES_ORDER_U9_ITEM_DATA_REPLACEMENT".equalsIgnoreCase(action)) return "MARKET";
        if ("SALES_ORDER_U9_RECONCILE".equalsIgnoreCase(action)) return "MARKET";
        if ("SALES_ORDER_DISCARD".equalsIgnoreCase(action)) return "MARKET";
        if ("SALES_ORDER_RESTORE".equalsIgnoreCase(action)) return "MARKET";
        if ("SALES_ORDER_DRAWING_SUPPLEMENT".equalsIgnoreCase(action)) return "MARKET";
        if ("SALES_ORDER_DRAWING_REPLACE".equalsIgnoreCase(action)) return "MARKET";
        if ("SALES_ORDER_PRODUCTION_TASK_NOTICE_EXPORT".equalsIgnoreCase(action)) return "MARKET";
        if ("SALES_ORDER_APPEND_RELEASE".equalsIgnoreCase(action)) return "MARKET";
        if ("SALES_ORDER_ITEM_U9_CHANGE_EXCLUDE".equalsIgnoreCase(action)) return "MARKET";
        if ("SALES_ORDER_ITEM_U9_CHANGE_RESTORE".equalsIgnoreCase(action)) return "MARKET";
        if (startsWith(action, "SALES_ORDER_CHANGE_")) return "CHANGE";
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
        if (startsWith(action, "DELIVERY_")
                || startsWith(action, "LOCKED_ITEM_")
                || startsWith(action, "MES_INTEGRATION_BATCH_")
                || startsWith(action, "PRODUCTION_ITEM_")
                || startsWith(action, "PROCESS_SHEET_")) return "DELIVERY";
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
            case "AUTH_DATACENTER_SSO_SUCCESS" -> "数据中心SSO登录成功";
            case "AUTH_DATACENTER_SSO_FAILED" -> "数据中心SSO登录失败";
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
            case "U9_CUSTOMER_SYNC" -> "同步 U9 客户主数据";
            case "U9_CUSTOMER_SYNC_SCHEDULE_UPDATE" -> "更新 U9 客户定时同步策略";
            case "CUSTOMER_SUPPLIER_CODE_UPDATE" -> "维护客户供应商代码";
            case "U9_SYNC_OPTIONS_REFRESH" -> "刷新 U9 同步页选项缓存";
            case "SYSTEM_STORAGE_CONFIG_CREATE" -> "新增文件存储配置";
            case "SYSTEM_STORAGE_CONFIG_UPDATE" -> "编辑文件存储配置";
            case "SYSTEM_STORAGE_CONFIG_TEST" -> "测试文件存储配置";
            case "SYSTEM_STORAGE_CONFIG_TEST_FAILED" -> "测试文件存储配置失败";
            case "SYSTEM_STORAGE_CONFIG_ACTIVATE" -> "激活文件存储配置";
            case "SYSTEM_STORAGE_CONFIG_DISABLE" -> "停用文件存储配置";
            case "SYSTEM_INTEGRATION_CONFIG_CREATE" -> "新增出站接口配置";
            case "SYSTEM_INTEGRATION_CONFIG_UPDATE" -> "编辑出站接口配置";
            case "SYSTEM_INTEGRATION_CONFIG_TEST" -> "测试出站接口配置";
            case "SYSTEM_INTEGRATION_CONFIG_TEST_FAILED" -> "测试出站接口配置失败";
            case "SYSTEM_INTEGRATION_CONFIG_ACTIVATE" -> "激活出站接口配置";
            case "SYSTEM_INTEGRATION_CONFIG_DISABLE" -> "停用出站接口配置";
            case "SYSTEM_DATACENTER_SSO_CONFIG_CREATE" -> "新增数据中心登录配置";
            case "SYSTEM_DATACENTER_SSO_CONFIG_UPDATE" -> "编辑数据中心登录配置";
            case "SYSTEM_DATACENTER_SSO_CONFIG_TEST" -> "测试数据中心登录配置";
            case "SYSTEM_DATACENTER_SSO_CONFIG_TEST_FAILED" -> "测试数据中心登录配置失败";
            case "SYSTEM_DATACENTER_SSO_CONFIG_ACTIVATE" -> "激活数据中心登录配置";
            case "SYSTEM_DATACENTER_SSO_CONFIG_DISABLE" -> "停用数据中心登录配置";
            case "SYSTEM_DATA_CENTER_CONFIG_CREATE" -> "新增数据中心连接配置";
            case "SYSTEM_DATA_CENTER_CONFIG_UPDATE" -> "编辑数据中心连接配置";
            case "SYSTEM_DATA_CENTER_CONFIG_TEST" -> "测试数据中心Token获取";
            case "SYSTEM_DATA_CENTER_CONFIG_TEST_FAILED" -> "测试数据中心Token获取失败";
            case "SYSTEM_DATA_CENTER_CONFIG_ACTIVATE" -> "激活数据中心连接配置";
            case "SYSTEM_DATA_CENTER_CONFIG_DISABLE" -> "停用数据中心连接配置";
            case "SYSTEM_DATA_CENTER_ENDPOINT_UPDATE" -> "编辑数据中心功能接口配置";
            case "SYSTEM_DATA_CENTER_ENDPOINT_TEST" -> "测试数据中心功能接口配置";
            case "SYSTEM_DATA_CENTER_ENDPOINT_TEST_FAILED" -> "测试数据中心功能接口配置失败";
            case "SYSTEM_INBOUND_CALLBACK_CONFIG_UPDATE" -> "保存入站接口配置";
            case "SYSTEM_INBOUND_CALLBACK_TOKEN_ROTATE" -> "轮换入站开放接口Token";
            case "SYSTEM_INBOUND_CALLBACK_CONFIG_VALIDATE" -> "校验入站接口配置";
            case "SYSTEM_U9_ORG_CONFIG_UPDATE" -> "维护U9组织编码";
            case "QUOTE_TEMPLATE_UPLOAD" -> "上传报价模板";
            case "QUOTE_TEMPLATE_CONFIG_UPDATE" -> "保存报价模板配置";
            case "RFQ_IMPORT" -> "导入 RFQ";
            case "SALES_ORDER_IMPORT_QPO" -> "导入销售订单到独立主表";
            case "SALES_ORDER_DIRTY_SNAPSHOT_REPAIR" -> "\u9500\u552e\u8ba2\u5355\u810f\u5feb\u7167\u4fee\u590d";
            case "SALES_ORDER_UPDATE" -> "保存销售订单信息";
            case "SALES_ORDER_ITEM_UPDATE" -> "保存销售订单零件行";
            case "SALES_ORDER_ITEM_DATA_REPLACEMENT" -> "销售订单表格数据替换";
            case "SALES_ORDER_U9_SYNC" -> "销售订单同步U9";
            case "SALES_ORDER_U9_ASSIGNMENT_UPDATE" -> "保存销售订单U9销售员和部门";
            case "SALES_ORDER_U9_ORDER_DATE_UPDATE" -> "保存销售订单U9下单日期";
            case "SALES_ORDER_U9_ITEM_UPDATE" -> "保存销售订单U9零件行字段";
            case "SALES_ORDER_U9_ITEM_DATA_REPLACEMENT" -> "销售订单U9表格数据替换";
            case "SALES_ORDER_U9_RECONCILE" -> "销售订单U9结果核对";
            case "SALES_ORDER_DISCARD" -> "废弃销售订单";
            case "SALES_ORDER_RESTORE" -> "恢复销售订单";
            case "SALES_ORDER_DRAWING_SUPPLEMENT" -> "补传销售订单图纸";
            case "SALES_ORDER_DRAWING_REPLACE" -> "替换销售订单图纸";
            case "SALES_ORDER_PRODUCTION_TASK_NOTICE_EXPORT" -> "导出生产任务通知单";
            case "SALES_ORDER_ITEM_DETAIL_EXPORT" -> "导出销售订单明细表";
            case "SALES_ORDER_APPEND_RELEASE" -> "追加销售提单";
            case "SALES_ORDER_ITEM_U9_CHANGE_EXCLUDE" -> "销售订单零件排除U9与销售变更";
            case "SALES_ORDER_ITEM_U9_CHANGE_RESTORE" -> "恢复销售订单零件U9与销售变更";
            case "SALES_ORDER_CHANGE_ITEM_UPDATE" -> "销售变更保存零件行";
            case "SALES_ORDER_CHANGE_ITEM_DATA_REPLACEMENT" -> "销售变更表格数据替换";
            case "QUOTE_EXPORT_TASK_SUBMIT" -> "提交报价单导出任务";
            case "QUOTE_REFRESH_TASK_SUBMIT" -> "提交一键刷新核价任务";
            case "AUDIT_RETENTION_CONFIG_UPDATE" -> "更新操作日志保留策略";
            case "AUDIT_LOG_CLEANUP_RUN" -> "执行操作日志自动清理";
            case "AUDIT_LOG_CLEANUP_FAILED" -> "操作日志自动清理失败";
            case "DICT_MATERIAL_CREATE" -> "新增材料";
            case "DICT_MATERIAL_UPDATE" -> "更新材料";
            case "DICT_MATERIAL_DELETE" -> "删除材料";
            case "DICT_MATERIAL_DISABLE" -> "停用材料";
            case "DICT_MATERIAL_TYPE_CREATE" -> "新增材料类型";
            case "DICT_MATERIAL_TYPE_UPDATE" -> "更新材料类型";
            case "DICT_MATERIAL_TYPE_DELETE" -> "删除材料类型";
            case "DICT_MATERIAL_TYPE_DISABLE" -> "停用材料类型";
            case "DICT_MATERIAL_MES_SYNC" -> "同步 MES 材料主数据";
            case "DICT_MES_MASTER_DATA_SYNC_SCHEDULE_UPDATE" -> "更新 MES 主数据定时同步策略";
            case "DICT_PROCESS_CATEGORY_CREATE" -> "新增工艺大类";
            case "DICT_PROCESS_CATEGORY_UPDATE" -> "更新工艺大类";
            case "DICT_PROCESS_MES_SYNC" -> "同步 MES 工艺主数据";
            case "DICT_PROCESS_DETAIL_CREATE" -> "新增具体工艺";
            case "DICT_PROCESS_DETAIL_UPDATE" -> "更新具体工艺";
            case "DICT_PROCESS_DETAIL_DELETE" -> "删除具体工艺";
            case "DICT_PROCESS_ARRANGEMENT_NODE_CREATE" -> "新增工艺编排节点";
            case "DICT_PROCESS_ARRANGEMENT_NODE_UPDATE" -> "更新工艺编排节点";
            case "DICT_PROCESS_ARRANGEMENT_NODE_DELETE" -> "删除工艺编排节点";
            case "DICT_PROCESS_ARRANGEMENT_ITEM_PLACE" -> "工艺编排放入工艺";
            case "DICT_PROCESS_ARRANGEMENT_ITEM_MOVE" -> "工艺编排移动工艺";
            case "DICT_PROCESS_ARRANGEMENT_ITEM_REMOVE" -> "工艺编排移除工艺";
            case "DICT_MARKUP_FEE_CREATE" -> "新增百分比费用";
            case "DICT_MARKUP_FEE_UPDATE" -> "更新百分比费用";
            case "DICT_MARKUP_FEE_DELETE" -> "删除百分比费用";
            case "DICT_ASSEMBLY_MARKUP_FEE_CREATE" -> "新增组装件百分比费用";
            case "DICT_ASSEMBLY_MARKUP_FEE_UPDATE" -> "更新组装件百分比费用";
            case "DICT_ASSEMBLY_MARKUP_FEE_DELETE" -> "删除组装件百分比费用";
            case "DICT_PRICING_MARKUP_FEE_CREATE" -> "Create pricing markup fee";
            case "DICT_PRICING_MARKUP_FEE_UPDATE" -> "Update pricing markup fee";
            case "DICT_PRICING_MARKUP_FEE_DELETE" -> "Delete pricing markup fee";
            case "DICT_EXTRA_FEE_CREATE" -> "新增非工时费用";
            case "DICT_EXTRA_FEE_UPDATE" -> "更新非工时费用";
            case "DICT_EXTRA_FEE_DELETE" -> "删除非工时费用";
            case "ITEM_QUOTE_SAVE" -> "保存逐件核价";
            case "ITEM_QUOTE_REMARK_UPDATE" -> "保存零件核价备注";
            case "ORDER_QUOTE_COMPLETE" -> "完成工程报价";
            case "PRICING_SHEET_EXPORT" -> "生成核价单";
            case "QUOTE_PROCESS_SAVE_QUOTE" -> "保存报价单";
            case "ORDER_STATUS_ROLLBACK_QUOTED" -> "已报价退回已核价";
            case "ORDER_STATUS_ROLLBACK_PRICED" -> "已核价退回未核价";
            case "ORDER_DISCARD" -> "废弃订单";
            case "ORDER_RESTORE" -> "恢复废弃订单";
            case "ORDER_ITEM_UPDATE" -> "保存询价订单零件信息";
            case "ORDER_ITEM_DATA_REPLACEMENT" -> "询价订单表格数据替换";
            case "ORDER_DRAWING_REPLACE" -> "询价订单替换图纸";
            case "ORDER_ITEM_DISCARD" -> "废弃询价订单零件";
            case "ORDER_ITEM_RESTORE" -> "恢复询价订单零件";
            case "ORDER_ITEM_APPEND_IMPORT" -> "新增询价订单零件";
            case "QUOTE_LEAD_TIME_UPDATE" -> "更新客户交期";
            case "QUOTE_LEAD_TIME_BATCH_UPDATE" -> "批量保存报价交期";
            case "QUOTE_LEAD_TIME_DATA_REPLACEMENT" -> "报价处理表格数据替换";
            case "INTERNAL_TARGET_LEAD_TIME_UPDATE" -> "更新内部目标交期";
            case "RELEASE_ITEM_VERSION_UPDATE" -> "议价提单修改零件版本";
            case "RELEASE_ITEM_FIELD_UPDATE" -> "议价提单修改零件信息";
            case "RELEASE_ITEM_ORDER_FIELDS_UPDATE" -> "议价提单修改零件订单属性";
            case "RELEASE_ITEM_DATA_REPLACEMENT" -> "议价提单表格数据替换";
            case "RELEASE_DRAWING_REPLACE" -> "议价提单替换图纸";
            case "DELIVERY_TICKET_GENERATE" -> "生成提单";
            case "DELIVERY_TICKET_EXPORT" -> "导出提单表";
            case "DELIVERY_PROCESS_SHEET_EXPORT" -> "导出工艺表";
            case "DELIVERY_MES_MATERIAL_SYNC_SUBMIT" -> "提交MES物料号同步";
            case "DELIVERY_MES_MATERIAL_SYNC_SUCCESS" -> "MES物料号同步成功";
            case "DELIVERY_MES_MATERIAL_SYNC_ACCEPTED" -> "MES物料号已受理待回调";
            case "DELIVERY_MES_MATERIAL_SYNC_FAILED" -> "MES物料号同步失败";
            case "DELIVERY_MES_MATERIAL_CALLBACK_SUCCESS" -> "MES物料号回调成功";
            case "DELIVERY_MES_MATERIAL_CALLBACK_FAILED" -> "MES物料号回调失败";
            case "DELIVERY_MES_MATERIAL_CALLBACK_CONFLICT" -> "MES物料号回调冲突";
            case "DELIVERY_MES_MATERIAL_DEDUP_REUSE" -> "复用同图号版本MES物料号";
            case "DELIVERY_MES_MATERIAL_DEDUP_FOLLOW" -> "关联同图号版本MES主请求";
            case "DELIVERY_MES_MATERIAL_DEDUP_FANOUT" -> "分发同图号版本MES物料号";
            case "MES_INTEGRATION_BATCH_RECONCILE" -> "MES批次结果核对";
            case "DELIVERY_WMS_ALLOCATION_SUBMIT" -> "提交WMS锁库";
            case "DELIVERY_WMS_ALLOCATION_SUCCESS" -> "WMS锁库成功";
            case "DELIVERY_WMS_ALLOCATION_FAILED" -> "WMS锁库失败";
            case "DELIVERY_WMS_ALLOCATION_RESULT_UNKNOWN" -> "WMS锁库结果待核对";
            case "DELIVERY_WMS_ALLOCATION_APPLY_FAILED" -> "WMS锁库本地写入待修复";
            case "DELIVERY_WMS_ALLOCATION_RECONCILED" -> "WMS锁库结果已核对";
            case "DELIVERED_ITEM_DISCARD" -> "废弃已提零件";
            case "DELIVERED_ITEM_RESTORE" -> "恢复已提零件";
            case "PRODUCTION_ITEM_MES_PRODUCTION_SUBMIT", "LOCKED_ITEM_MES_PRODUCTION_SUBMIT" -> "提交MES生产下发";
            case "PRODUCTION_ITEM_MES_PRODUCTION_SUCCESS", "LOCKED_ITEM_MES_PRODUCTION_SUCCESS" -> "MES生产下发成功";
            case "PRODUCTION_ITEM_MES_PRODUCTION_FAILED", "LOCKED_ITEM_MES_PRODUCTION_FAILED" -> "MES生产下发失败";
            case "LOCKED_ITEM_STOCK_PICKING_SUBMIT" -> "提交提库存工单";
            case "LOCKED_ITEM_STOCK_PICKING_SUCCESS" -> "提库存工单下发成功";
            case "LOCKED_ITEM_STOCK_PICKING_FAILED" -> "提库存工单下发失败";
            case "LOCKED_ITEM_STOCK_PICKING_RESULT_UNKNOWN" -> "提库存工单结果待核对";
            case "LOCKED_ITEM_DISCARD" -> "废弃锁库零件";
            case "LOCKED_ITEM_RESTORE" -> "恢复锁库零件";
            case "PRODUCTION_ITEM_DISCARD" -> "废弃待产零件";
            case "PRODUCTION_ITEM_RESTORE" -> "恢复待产零件";
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
