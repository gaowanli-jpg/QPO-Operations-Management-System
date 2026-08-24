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
    private static final Path WMS_ALLOCATION_PLAN = Path.of(
            "..", "docs", "QPO-MES-WMS对接", "04-WMS锁库分配与锁库零件模块开发文档.md");

    @Test
    void criticalAuditActionsExposeExpectedModulesAndLabels() {
        assertThat(AuditService.inferModule("AUTH_LOGIN_SUCCESS")).isEqualTo("AUTH");
        assertThat(AuditService.inferModule("AUTH_LOGIN_FAILED")).isEqualTo("AUTH");
        assertThat(AuditService.inferModule("AUTH_ACCOUNT_LOCKED")).isEqualTo("AUTH");
        assertThat(AuditService.inferModule("AUTH_LOGOUT")).isEqualTo("AUTH");
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
        assertThat(AuditService.inferModule("SALES_ORDER_CHANGE_ITEM_UPDATE")).isEqualTo("CHANGE");
        assertThat(AuditService.inferModule("SALES_ORDER_CHANGE_ITEM_DATA_REPLACEMENT")).isEqualTo("CHANGE");
        assertThat(AuditService.inferModule("SYSTEM_U9_ORG_CONFIG_UPDATE")).isEqualTo("SYSTEM");

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
        assertThat(AuditService.labelForAction("SALES_ORDER_CHANGE_ITEM_UPDATE")).isNotEqualTo("SALES_ORDER_CHANGE_ITEM_UPDATE");
        assertThat(AuditService.labelForAction("SALES_ORDER_CHANGE_ITEM_DATA_REPLACEMENT")).isNotEqualTo("SALES_ORDER_CHANGE_ITEM_DATA_REPLACEMENT");
        assertThat(AuditService.labelForAction("SYSTEM_U9_ORG_CONFIG_UPDATE")).isNotEqualTo("SYSTEM_U9_ORG_CONFIG_UPDATE");
    }

    @Test
    void mesMasterDataAuditActionsExposeStableLabels() {
        assertThat(AuditService.labelForAction("DICT_PROCESS_MES_SYNC")).contains("MES");
        assertThat(AuditService.labelForAction("DICT_PROCESS_MES_SYNC")).contains("工艺");
        assertThat(AuditService.labelForAction("DICT_MATERIAL_MES_SYNC")).contains("MES");
        assertThat(AuditService.labelForAction("DICT_MATERIAL_MES_SYNC")).contains("材料");
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
