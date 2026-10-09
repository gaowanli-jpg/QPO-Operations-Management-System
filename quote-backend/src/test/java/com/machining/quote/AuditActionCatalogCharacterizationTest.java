package com.machining.quote;

import com.machining.quote.service.AuditService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AuditActionCatalogCharacterizationTest {
    private static final Path SYSTEM_TASK_SERVICE = Path.of(
            "src", "main", "java", "com", "machining", "quote", "service", "SystemTaskService.java");
    private static final Path AUTO_MES_MATERIAL_SYNC_SERVICE = Path.of(
            "src", "main", "java", "com", "machining", "quote", "service", "DeliveryAutoMesMaterialSyncService.java");
    private static final Path AUDIT_LOG_QUERY_SERVICE = Path.of(
            "src", "main", "java", "com", "machining", "quote", "service", "AuditLogQueryService.java");
    private static final Path WMS_ALLOCATION_PLAN = Path.of(
            "..", "docs", "QPO-MES-WMS对接", "04-WMS锁库分配与锁库零件模块开发文档.md");

    @Test
    void criticalAuditActionsExposeExpectedModulesAndLabels() {
        assertThat(AuditService.inferModule("AUTH_LOGIN_SUCCESS")).isEqualTo("AUTH");
        assertThat(AuditService.inferModule("AUTH_LOGIN_FAILED")).isEqualTo("AUTH");
        assertThat(AuditService.inferModule("AUTH_ACCOUNT_LOCKED")).isEqualTo("AUTH");
        assertThat(AuditService.inferModule("AUTH_LOGOUT")).isEqualTo("AUTH");
        assertThat(AuditService.inferModule("AUTH_DATACENTER_SSO_SUCCESS")).isEqualTo("AUTH");
        assertThat(AuditService.inferModule("AUTH_DATACENTER_SSO_FAILED")).isEqualTo("AUTH");
        assertThat(AuditService.inferModule("QUOTE_TEMPLATE_UPLOAD")).isEqualTo("SYSTEM");
        assertThat(AuditService.inferModule("RFQ_IMPORT")).isEqualTo("MARKET");
        assertThat(AuditService.inferModule("QUOTE_EXPORT_TASK_SUBMIT")).isEqualTo("MARKET");
        assertThat(AuditService.inferModule("QUOTE_LEAD_TIME_BATCH_UPDATE")).isEqualTo("MARKET");
        assertThat(AuditService.inferModule("QUOTE_LEAD_TIME_DATA_REPLACEMENT")).isEqualTo("MARKET");
        assertThat(AuditService.inferModule("QUOTE_REFRESH_TASK_SUBMIT")).isEqualTo("ENGINEER");
        assertThat(AuditService.inferModule("DELIVERY_MES_MATERIAL_SYNC_SUBMIT")).isEqualTo("DELIVERY");
        assertThat(AuditService.inferModule("DELIVERY_MES_MATERIAL_SYNC_SUCCESS")).isEqualTo("DELIVERY");
        assertThat(AuditService.inferModule("DELIVERY_MES_MATERIAL_SYNC_ACCEPTED")).isEqualTo("DELIVERY");
        assertThat(AuditService.inferModule("DELIVERY_MES_MATERIAL_SYNC_FAILED")).isEqualTo("DELIVERY");
        assertThat(AuditService.inferModule("DELIVERY_MES_MATERIAL_CALLBACK_SUCCESS")).isEqualTo("DELIVERY");
        assertThat(AuditService.inferModule("DELIVERY_MES_MATERIAL_CALLBACK_FAILED")).isEqualTo("DELIVERY");
        assertThat(AuditService.inferModule("DELIVERY_MES_MATERIAL_CALLBACK_CONFLICT")).isEqualTo("DELIVERY");
        assertThat(AuditService.inferModule("DELIVERY_MES_MATERIAL_DEDUP_REUSE")).isEqualTo("DELIVERY");
        assertThat(AuditService.inferModule("DELIVERY_MES_MATERIAL_DEDUP_FOLLOW")).isEqualTo("DELIVERY");
        assertThat(AuditService.inferModule("DELIVERY_MES_MATERIAL_DEDUP_FANOUT")).isEqualTo("DELIVERY");
        assertThat(AuditService.inferModule("DELIVERY_WMS_ALLOCATION_SUBMIT")).isEqualTo("DELIVERY");
        assertThat(AuditService.inferModule("DELIVERY_WMS_ALLOCATION_SUCCESS")).isEqualTo("DELIVERY");
        assertThat(AuditService.inferModule("DELIVERY_WMS_ALLOCATION_FAILED")).isEqualTo("DELIVERY");
        assertThat(AuditService.inferModule("DELIVERY_WMS_ALLOCATION_RESULT_UNKNOWN")).isEqualTo("DELIVERY");
        assertThat(AuditService.inferModule("DELIVERY_WMS_ALLOCATION_APPLY_FAILED")).isEqualTo("DELIVERY");
        assertThat(AuditService.inferModule("DELIVERY_WMS_ALLOCATION_RECONCILED")).isEqualTo("DELIVERY");
        assertThat(AuditService.inferModule("PRODUCTION_ITEM_MES_PRODUCTION_SUBMIT")).isEqualTo("DELIVERY");
        assertThat(AuditService.inferModule("PRODUCTION_ITEM_MES_PRODUCTION_SUCCESS")).isEqualTo("DELIVERY");
        assertThat(AuditService.inferModule("PRODUCTION_ITEM_MES_PRODUCTION_FAILED")).isEqualTo("DELIVERY");
        assertThat(AuditService.inferModule("DELIVERY_TICKET_EXPORT")).isEqualTo("DELIVERY");
        assertThat(AuditService.inferModule("DELIVERY_PROCESS_SHEET_EXPORT")).isEqualTo("DELIVERY");
        assertThat(AuditService.inferModule("DICT_PROCESS_MES_SYNC")).isEqualTo("DICT");
        assertThat(AuditService.inferModule("DICT_MATERIAL_MES_SYNC")).isEqualTo("DICT");
        assertThat(AuditService.inferModule("DICT_PRICING_MARKUP_FEE_CREATE")).isEqualTo("DICT");
        assertThat(AuditService.inferModule("DICT_PRICING_MARKUP_FEE_UPDATE")).isEqualTo("DICT");
        assertThat(AuditService.inferModule("DICT_PRICING_MARKUP_FEE_DELETE")).isEqualTo("DICT");
        assertThat(AuditService.inferModule("SALES_ORDER_UPDATE")).isEqualTo("MARKET");
        assertThat(AuditService.inferModule("SALES_ORDER_WMS_ALLOCATION_SUBMIT")).isEqualTo("MARKET");
        assertThat(AuditService.inferModule("SALES_ORDER_ITEM_UPDATE")).isEqualTo("MARKET");
        assertThat(AuditService.inferModule("SALES_ORDER_ITEM_DATA_REPLACEMENT")).isEqualTo("MARKET");
        assertThat(AuditService.inferModule("SALES_ORDER_ITEM_LINKED_DISCARD")).isEqualTo("MARKET");
        assertThat(AuditService.inferModule("SALES_ORDER_ITEM_LINKED_RESTORE")).isEqualTo("MARKET");
        assertThat(AuditService.inferModule("SALES_ORDER_CHANGE_ITEM_UPDATE")).isEqualTo("CHANGE");
        assertThat(AuditService.inferModule("SALES_ORDER_CHANGE_ITEM_DATA_REPLACEMENT")).isEqualTo("CHANGE");
        assertThat(AuditService.inferModule("SALES_ORDER_PRODUCTION_TASK_NOTICE_EXPORT")).isEqualTo("MARKET");
        assertThat(AuditService.inferModule("SYSTEM_U9_ORG_CONFIG_UPDATE")).isEqualTo("SYSTEM");
        assertThat(AuditService.inferModule("CUSTOMER_SUPPLIER_CODE_UPDATE")).isEqualTo("SYSTEM");

        assertThat(AuditService.labelForAction("AUTH_ACCOUNT_LOCKED")).isNotEqualTo("AUTH_ACCOUNT_LOCKED");
        assertThat(AuditService.labelForAction("RFQ_IMPORT")).isNotEqualTo("RFQ_IMPORT");
        assertThat(AuditService.labelForAction("QUOTE_EXPORT_TASK_SUBMIT")).isNotEqualTo("QUOTE_EXPORT_TASK_SUBMIT");
        assertThat(AuditService.labelForAction("QUOTE_LEAD_TIME_BATCH_UPDATE")).isNotEqualTo("QUOTE_LEAD_TIME_BATCH_UPDATE");
        assertThat(AuditService.labelForAction("QUOTE_LEAD_TIME_DATA_REPLACEMENT")).isNotEqualTo("QUOTE_LEAD_TIME_DATA_REPLACEMENT");
        assertThat(AuditService.labelForAction("QUOTE_REFRESH_TASK_SUBMIT")).isNotEqualTo("QUOTE_REFRESH_TASK_SUBMIT");
        assertThat(AuditService.labelForAction("DELIVERY_MES_MATERIAL_SYNC_SUBMIT")).isNotEqualTo("DELIVERY_MES_MATERIAL_SYNC_SUBMIT");
        assertThat(AuditService.labelForAction("DELIVERY_MES_MATERIAL_SYNC_SUCCESS")).isNotEqualTo("DELIVERY_MES_MATERIAL_SYNC_SUCCESS");
        assertThat(AuditService.labelForAction("DELIVERY_MES_MATERIAL_SYNC_ACCEPTED")).isNotEqualTo("DELIVERY_MES_MATERIAL_SYNC_ACCEPTED");
        assertThat(AuditService.labelForAction("DELIVERY_MES_MATERIAL_SYNC_FAILED")).isNotEqualTo("DELIVERY_MES_MATERIAL_SYNC_FAILED");
        assertThat(AuditService.labelForAction("DELIVERY_MES_MATERIAL_CALLBACK_SUCCESS")).isNotEqualTo("DELIVERY_MES_MATERIAL_CALLBACK_SUCCESS");
        assertThat(AuditService.labelForAction("DELIVERY_MES_MATERIAL_CALLBACK_FAILED")).isNotEqualTo("DELIVERY_MES_MATERIAL_CALLBACK_FAILED");
        assertThat(AuditService.labelForAction("DELIVERY_MES_MATERIAL_CALLBACK_CONFLICT")).isNotEqualTo("DELIVERY_MES_MATERIAL_CALLBACK_CONFLICT");
        assertThat(AuditService.labelForAction("DELIVERY_MES_MATERIAL_DEDUP_REUSE")).isNotEqualTo("DELIVERY_MES_MATERIAL_DEDUP_REUSE");
        assertThat(AuditService.labelForAction("DELIVERY_MES_MATERIAL_DEDUP_FOLLOW")).isNotEqualTo("DELIVERY_MES_MATERIAL_DEDUP_FOLLOW");
        assertThat(AuditService.labelForAction("DELIVERY_MES_MATERIAL_DEDUP_FANOUT")).isNotEqualTo("DELIVERY_MES_MATERIAL_DEDUP_FANOUT");
        assertThat(AuditService.labelForAction("DELIVERY_WMS_ALLOCATION_SUBMIT")).isNotEqualTo("DELIVERY_WMS_ALLOCATION_SUBMIT");
        assertThat(AuditService.labelForAction("DELIVERY_WMS_ALLOCATION_SUCCESS")).isNotEqualTo("DELIVERY_WMS_ALLOCATION_SUCCESS");
        assertThat(AuditService.labelForAction("DELIVERY_WMS_ALLOCATION_FAILED")).isNotEqualTo("DELIVERY_WMS_ALLOCATION_FAILED");
        assertThat(AuditService.labelForAction("DELIVERY_WMS_ALLOCATION_RESULT_UNKNOWN")).isNotEqualTo("DELIVERY_WMS_ALLOCATION_RESULT_UNKNOWN");
        assertThat(AuditService.labelForAction("DELIVERY_WMS_ALLOCATION_APPLY_FAILED")).isNotEqualTo("DELIVERY_WMS_ALLOCATION_APPLY_FAILED");
        assertThat(AuditService.labelForAction("DELIVERY_WMS_ALLOCATION_RECONCILED")).isNotEqualTo("DELIVERY_WMS_ALLOCATION_RECONCILED");
        assertThat(AuditService.labelForAction("PRODUCTION_ITEM_MES_PRODUCTION_SUBMIT")).isNotEqualTo("PRODUCTION_ITEM_MES_PRODUCTION_SUBMIT");
        assertThat(AuditService.labelForAction("PRODUCTION_ITEM_MES_PRODUCTION_SUCCESS")).isNotEqualTo("PRODUCTION_ITEM_MES_PRODUCTION_SUCCESS");
        assertThat(AuditService.labelForAction("PRODUCTION_ITEM_MES_PRODUCTION_FAILED")).isNotEqualTo("PRODUCTION_ITEM_MES_PRODUCTION_FAILED");
        assertThat(AuditService.labelForAction("DELIVERY_TICKET_EXPORT")).isNotEqualTo("DELIVERY_TICKET_EXPORT");
        assertThat(AuditService.labelForAction("DELIVERY_PROCESS_SHEET_EXPORT")).isNotEqualTo("DELIVERY_PROCESS_SHEET_EXPORT");
        assertThat(AuditService.labelForAction("DICT_PROCESS_MES_SYNC")).isNotEqualTo("DICT_PROCESS_MES_SYNC");
        assertThat(AuditService.labelForAction("DICT_MATERIAL_MES_SYNC")).isNotEqualTo("DICT_MATERIAL_MES_SYNC");
        assertThat(AuditService.labelForAction("DICT_PRICING_MARKUP_FEE_CREATE")).isNotEqualTo("DICT_PRICING_MARKUP_FEE_CREATE");
        assertThat(AuditService.labelForAction("DICT_PRICING_MARKUP_FEE_UPDATE")).isNotEqualTo("DICT_PRICING_MARKUP_FEE_UPDATE");
        assertThat(AuditService.labelForAction("DICT_PRICING_MARKUP_FEE_DELETE")).isNotEqualTo("DICT_PRICING_MARKUP_FEE_DELETE");
        assertThat(AuditService.labelForAction("SALES_ORDER_UPDATE")).isNotEqualTo("SALES_ORDER_UPDATE");
        assertThat(AuditService.labelForAction("SALES_ORDER_WMS_ALLOCATION_SUBMIT")).isEqualTo("销售订单获取WMS库存");
        assertThat(AuditService.labelForAction("SALES_ORDER_ITEM_UPDATE")).isNotEqualTo("SALES_ORDER_ITEM_UPDATE");
        assertThat(AuditService.labelForAction("SALES_ORDER_ITEM_DATA_REPLACEMENT")).isNotEqualTo("SALES_ORDER_ITEM_DATA_REPLACEMENT");
        assertThat(AuditService.labelForAction("SALES_ORDER_ITEM_LINKED_DISCARD")).isNotEqualTo("SALES_ORDER_ITEM_LINKED_DISCARD");
        assertThat(AuditService.labelForAction("SALES_ORDER_ITEM_LINKED_RESTORE")).isNotEqualTo("SALES_ORDER_ITEM_LINKED_RESTORE");
        assertThat(AuditService.labelForAction("SALES_ORDER_CHANGE_ITEM_UPDATE")).isNotEqualTo("SALES_ORDER_CHANGE_ITEM_UPDATE");
        assertThat(AuditService.labelForAction("SALES_ORDER_CHANGE_ITEM_DATA_REPLACEMENT")).isNotEqualTo("SALES_ORDER_CHANGE_ITEM_DATA_REPLACEMENT");
        assertThat(AuditService.labelForAction("SALES_ORDER_PRODUCTION_TASK_NOTICE_EXPORT")).isNotEqualTo("SALES_ORDER_PRODUCTION_TASK_NOTICE_EXPORT");
        assertThat(AuditService.labelForAction("SALES_ORDER_U9_RESYNC_RESET")).isEqualTo("重置销售订单U9同步状态");
        assertThat(AuditService.labelForAction("SYSTEM_U9_ORG_CONFIG_UPDATE")).isNotEqualTo("SYSTEM_U9_ORG_CONFIG_UPDATE");
        assertThat(AuditService.labelForAction("CUSTOMER_SUPPLIER_CODE_UPDATE")).isEqualTo("维护客户供应商代码");
        assertThat(AuditService.labelForAction("AUTH_DATACENTER_SSO_SUCCESS")).contains("数据中心SSO");
        assertThat(AuditService.labelForAction("AUTH_DATACENTER_SSO_FAILED")).contains("数据中心SSO");
    }

    @Test
    void mesMasterDataAuditActionsExposeStableLabels() {
        assertThat(AuditService.labelForAction("DICT_PROCESS_MES_SYNC")).contains("MES");
        assertThat(AuditService.labelForAction("DICT_PROCESS_MES_SYNC")).contains("工艺");
        assertThat(AuditService.labelForAction("DICT_MATERIAL_MES_SYNC")).contains("MES");
        assertThat(AuditService.labelForAction("DICT_MATERIAL_MES_SYNC")).contains("材料");
    }

    @Test
    void pricingMarkupFeeAuditActionsAndTargetTypeAreQueryable() throws Exception {
        String queryService = Files.readString(AUDIT_LOG_QUERY_SERVICE, StandardCharsets.UTF_8);

        assertThat(queryService)
                .contains("dict_pricing_markup_fee")
                .doesNotContain("dict_markup_fee", "dict_assembly_markup_fee");
        assertThat(AuditService.labelForAction("DICT_PRICING_MARKUP_FEE_CREATE"))
                .contains("pricing markup fee");
        assertThat(AuditService.labelForAction("DICT_PRICING_MARKUP_FEE_UPDATE"))
                .contains("pricing markup fee");
        assertThat(AuditService.labelForAction("DICT_PRICING_MARKUP_FEE_DELETE"))
                .contains("pricing markup fee");
    }

    @Test
    void autoMesMaterialTriggerReusesManualBatchSubmitAuditAction() throws Exception {
        String systemTaskService = Files.readString(SYSTEM_TASK_SERVICE, StandardCharsets.UTF_8);
        String autoTriggerService = Files.readString(AUTO_MES_MATERIAL_SYNC_SERVICE, StandardCharsets.UTF_8);

        assertThat(autoTriggerService)
                .contains("submitMesMaterialBatchSyncTask(ticketIds)")
                .doesNotContain(
                        "DELIVERY_AUTO_MES_MATERIAL_SYNC_SUBMIT",
                        "RFQ_RELEASE_AUTO_MES_MATERIAL_SYNC_SUBMIT",
                        "SALES_ORDER_IMPORT_AUTO_MES_MATERIAL_SYNC_SUBMIT");
        assertThat(systemTaskService)
                .contains(
                        "recordTaskSubmitAudit(\"DELIVERY_MES_MATERIAL_SYNC_SUBMIT\"",
                        "createTask(\"MES_MATERIAL_BATCH_SYNC\"")
                .doesNotContain(
                        "DELIVERY_AUTO_MES_MATERIAL_SYNC_SUBMIT",
                        "RFQ_RELEASE_AUTO_MES_MATERIAL_SYNC_SUBMIT",
                        "SALES_ORDER_IMPORT_AUTO_MES_MATERIAL_SYNC_SUBMIT");
    }

    @Test
    void supplementalDrawingWorkflowGovernanceUsesExistingAuditActionsAndSafeSummaries() throws Exception {
        String logDoc = Files.readString(Path.of("..", "docs", "QPO日志模块", "03-日志运维说明.md"), StandardCharsets.UTF_8);
        String engineerService = Files.readString(Path.of(
                "src", "main", "java", "com", "machining", "quote", "service", "EngineerService.java"), StandardCharsets.UTF_8);
        String quoteProcessService = Files.readString(Path.of(
                "src", "main", "java", "com", "machining", "quote", "service", "QuoteProcessService.java"), StandardCharsets.UTF_8);
        String rfqReleaseService = Files.readString(Path.of(
                "src", "main", "java", "com", "machining", "quote", "service", "RfqReleaseSalesOrderService.java"), StandardCharsets.UTF_8);

        assertThat(logDoc)
                .contains("补图补核价追加提单阶段 7 收口")
                .contains("补图、补核价、补充报价和追加提单沿用 `ORDER_SUPPLEMENT_DRAWINGS`、`ORDER_QUOTE_COMPLETE`、`ITEM_QUOTE_SAVE`、`QUOTE_PROCESS_SAVE_QUOTE`、`DELIVERY_TICKET_GENERATE` 等现有审计动作")
                .contains("摘要只允许记录 `statusBefore/statusAfter`、`restoredUnpricedCount`、`supplemental=true`、`supplementalItemCount`、`pricedSupplementalItemCount`、`orderStatusBefore/orderStatusAfter`、`ticketCount` 等治理字段")
                .contains("不得记录完整图纸、完整报价快照、完整工艺路线 JSON、token、Authorization、Cookie 或外部系统密钥");
        assertThat(engineerService)
                .contains("auditService.record(\"ITEM_QUOTE_SAVE\"")
                .contains("auditService.record(\"ORDER_QUOTE_COMPLETE\"")
                .contains("pricedSupplementalItemCount")
                .doesNotContain("ORDER_SUPPLEMENTAL_QUOTE_COMPLETE");
        assertThat(quoteProcessService)
                .contains("auditService.record(", "\"QUOTE_PROCESS_SAVE_QUOTE\"")
                .contains("supplementalItemCount")
                .contains("orderStatusBefore")
                .doesNotContain("SUPPLEMENTAL_QUOTE_PROCESS_SAVE_QUOTE");
        assertThat(rfqReleaseService)
                .contains("auditService.record(\"DELIVERY_TICKET_GENERATE\"")
                .contains("ticketCount")
                .doesNotContain("SUPPLEMENTAL_DELIVERY_TICKET_GENERATE");
    }

    @Test
    void mesWmsAuditActionsExposeStableChineseLabels() {
        assertThat(AuditService.labelForAction("DELIVERY_MES_MATERIAL_SYNC_SUBMIT")).isEqualTo("提交MES物料号同步");
        assertThat(AuditService.labelForAction("DELIVERY_MES_MATERIAL_SYNC_SUCCESS")).isEqualTo("MES物料号同步成功");
        assertThat(AuditService.labelForAction("DELIVERY_MES_MATERIAL_SYNC_ACCEPTED")).isEqualTo("MES物料号已受理待回调");
        assertThat(AuditService.labelForAction("DELIVERY_MES_MATERIAL_SYNC_FAILED")).isEqualTo("MES物料号同步失败");
        assertThat(AuditService.labelForAction("DELIVERY_MES_MATERIAL_CALLBACK_SUCCESS")).isEqualTo("MES物料号回调成功");
        assertThat(AuditService.labelForAction("DELIVERY_MES_MATERIAL_CALLBACK_FAILED")).isEqualTo("MES物料号回调失败");
        assertThat(AuditService.labelForAction("DELIVERY_MES_MATERIAL_CALLBACK_CONFLICT")).isEqualTo("MES物料号回调冲突");
        assertThat(AuditService.labelForAction("DELIVERY_MES_MATERIAL_DEDUP_REUSE")).isEqualTo("复用同图号版本MES物料号");
        assertThat(AuditService.labelForAction("DELIVERY_MES_MATERIAL_DEDUP_FOLLOW")).isEqualTo("关联同图号版本MES主请求");
        assertThat(AuditService.labelForAction("DELIVERY_MES_MATERIAL_DEDUP_FANOUT")).isEqualTo("分发同图号版本MES物料号");
        assertThat(AuditService.labelForAction("DELIVERY_WMS_ALLOCATION_SUBMIT")).isEqualTo("提交WMS锁库");
        assertThat(AuditService.labelForAction("DELIVERY_WMS_ALLOCATION_SUCCESS")).isEqualTo("WMS锁库成功");
        assertThat(AuditService.labelForAction("DELIVERY_WMS_ALLOCATION_FAILED")).isEqualTo("WMS锁库失败");
        assertThat(AuditService.labelForAction("DELIVERY_WMS_ALLOCATION_RESULT_UNKNOWN")).isEqualTo("WMS锁库结果待核对");
        assertThat(AuditService.labelForAction("DELIVERY_WMS_ALLOCATION_APPLY_FAILED")).isEqualTo("WMS锁库本地写入待修复");
        assertThat(AuditService.labelForAction("DELIVERY_WMS_ALLOCATION_RECONCILED")).isEqualTo("WMS锁库结果已核对");
    }

    @Test
    void deliveryDocumentExportActionsExposeStableChineseLabels() {
        assertThat(AuditService.labelForAction("DELIVERY_TICKET_EXPORT")).isEqualTo("导出提单表");
        assertThat(AuditService.labelForAction("DELIVERY_PROCESS_SHEET_EXPORT")).isEqualTo("导出工艺表");
    }

    @Test
    void quoteProcessLeadTimeAuditActionsExposeStableChineseLabels() {
        assertThat(AuditService.labelForAction("QUOTE_LEAD_TIME_BATCH_UPDATE")).isEqualTo("批量保存报价交期");
        assertThat(AuditService.labelForAction("QUOTE_LEAD_TIME_DATA_REPLACEMENT")).isEqualTo("报价处理表格数据替换");
    }

    @Test
    void salesOrderChangeAuditActionsExposeStableChineseLabels() {
        assertThat(AuditService.labelForAction("SALES_ORDER_CHANGE_ITEM_UPDATE")).isEqualTo("销售变更保存零件行");
        assertThat(AuditService.labelForAction("SALES_ORDER_CHANGE_ITEM_DATA_REPLACEMENT")).isEqualTo("销售变更表格数据替换");
    }

    @Test
    void salesOrderManagementMaintenanceAuditActionsExposeStableChineseLabelsAndTargets() throws Exception {
        String queryService = Files.readString(AUDIT_LOG_QUERY_SERVICE, StandardCharsets.UTF_8);

        assertThat(AuditService.labelForAction("SALES_ORDER_UPDATE")).isEqualTo("保存销售订单信息");
        assertThat(AuditService.labelForAction("SALES_ORDER_ITEM_UPDATE")).isEqualTo("保存销售订单零件行");
        assertThat(AuditService.labelForAction("SALES_ORDER_ITEM_DATA_REPLACEMENT")).isEqualTo("销售订单表格数据替换");
        assertThat(AuditService.labelForAction("SALES_ORDER_ITEM_LINKED_DISCARD")).isEqualTo("销售订单零件关联废弃");
        assertThat(AuditService.labelForAction("SALES_ORDER_ITEM_LINKED_RESTORE")).isEqualTo("销售订单零件关联恢复");
        assertThat(queryService)
                .contains("sales_order_header")
                .contains("sales_order_item");
    }

    @Test
    void salesOrderProductionTaskNoticeExportAuditActionExposesStableChineseLabel() {
        assertThat(AuditService.labelForAction("SALES_ORDER_DRAWING_REPLACE")).isEqualTo("替换销售订单图纸");
        assertThat(AuditService.labelForAction("SALES_ORDER_PRODUCTION_TASK_NOTICE_EXPORT")).isEqualTo("导出生产任务通知单");
        assertThat(AuditService.labelForAction("SALES_ORDER_ITEM_DETAIL_EXPORT")).isEqualTo("导出销售订单明细表");
    }

    @Test
    void wmsAllocationAuditActionsAndFileLogEventsAreReserved() throws Exception {
        String plan = Files.readString(WMS_ALLOCATION_PLAN, StandardCharsets.UTF_8);

        assertThat(plan)
                .as("WMS allocation must be auditable before implementation starts")
                .contains(
                        "DELIVERY_WMS_ALLOCATION_SUBMIT",
                        "DELIVERY_WMS_ALLOCATION_SUCCESS",
                        "DELIVERY_WMS_ALLOCATION_FAILED",
                        "DELIVERY_WMS_ALLOCATION_RESULT_UNKNOWN",
                        "DELIVERY_WMS_ALLOCATION_APPLY_FAILED",
                        "DELIVERY_WMS_ALLOCATION_RECONCILED",
                        "WMS reservation batch started",
                        "WMS reservation batch completed",
                        "WMS reservation group failed",
                        "WMS reservation recovery failed",
                        "taskId",
                        "batchId",
                        "externalBizId",
                        "saleOrderNo",
                        "durationMs",
                        "qpo-error.log"
                );
    }
}
