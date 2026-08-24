package com.machining.quote;

import com.machining.quote.service.AuditService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOrderChangeStage2Test {
    private static final Path MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration", "V20__sales_order_change_records.sql");
    private static final Path RECORD_ENTITY = Path.of(
            "src", "main", "java", "com", "machining", "quote", "entity", "SalesOrderChangeRecord.java");
    private static final Path RECORD_ITEM_ENTITY = Path.of(
            "src", "main", "java", "com", "machining", "quote", "entity", "SalesOrderChangeRecordItem.java");
    private static final Path RECORD_MAPPER = Path.of(
            "src", "main", "java", "com", "machining", "quote", "mapper", "SalesOrderChangeRecordMapper.java");
    private static final Path RECORD_ITEM_MAPPER = Path.of(
            "src", "main", "java", "com", "machining", "quote", "mapper", "SalesOrderChangeRecordItemMapper.java");
    private static final Path PERMISSION_DOC = Path.of(
            "..", "docs", "QPO权限用户体系", "02-权限点清单与现有角色映射.md");
    private static final Path AUDIT_DOC = Path.of(
            "..", "docs", "QPO日志模块", "01-操作日志模块开发文档.md");
    private static final Path CHANGE_DOC = Path.of(
            "..", "docs", "QPO报价状态流转", "47-销售变更模块开发文档.md");

    @Test
    void migrationCreatesSalesOrderChangeRecordTablesWithoutSensitivePayloadColumns() throws Exception {
        String migration = read(MIGRATION);

        assertThat(migration)
                .contains(
                        "CREATE TABLE IF NOT EXISTS sales_order_change_record",
                        "CREATE TABLE IF NOT EXISTS sales_order_change_record_item",
                        "sales_order_id BIGINT NOT NULL",
                        "sales_order_no VARCHAR(100) NOT NULL",
                        "change_type VARCHAR(40) NOT NULL",
                        "reason VARCHAR(500) NOT NULL",
                        "updated_count INTEGER NOT NULL DEFAULT 0",
                        "changed_fields_json TEXT",
                        "before_snapshot_json TEXT",
                        "after_snapshot_json TEXT",
                        "idx_sales_order_change_record_order",
                        "idx_sales_order_change_record_item_record",
                        "idx_sales_order_change_record_item_item")
                .doesNotContain(
                        "drawing_url",
                        "drawing_urls",
                        "u9_request",
                        "u9_response",
                        "authorization",
                        "token",
                        "cookie",
                        "password");
    }

    @Test
    void entitiesAndMappersExposeChangeRecordTablesForFutureSaveServices() throws Exception {
        assertThat(read(RECORD_ENTITY))
                .contains(
                        "@TableName(\"sales_order_change_record\")",
                        "class SalesOrderChangeRecord",
                        "private Long salesOrderId;",
                        "private String salesOrderNo;",
                        "private String changeType;",
                        "private String reason;",
                        "private Integer updatedCount;",
                        "private String changedFieldsJson;",
                        "private Long createdBy;",
                        "private String createdByName;")
                .doesNotContain("drawing", "u9Request", "u9Response", "authorization", "token");
        assertThat(read(RECORD_ITEM_ENTITY))
                .contains(
                        "@TableName(\"sales_order_change_record_item\")",
                        "class SalesOrderChangeRecordItem",
                        "private Long changeRecordId;",
                        "private Long salesOrderItemId;",
                        "private Integer lineNo;",
                        "private String partNo;",
                        "private String version;",
                        "private String changedFieldsJson;",
                        "private String beforeSnapshotJson;",
                        "private String afterSnapshotJson;")
                .doesNotContain("drawing", "u9Request", "u9Response", "authorization", "token");
        assertThat(read(RECORD_MAPPER))
                .contains("extends BaseMapper<SalesOrderChangeRecord>", "@Mapper");
        assertThat(read(RECORD_ITEM_MAPPER))
                .contains("extends BaseMapper<SalesOrderChangeRecordItem>", "@Mapper");
    }

    @Test
    void auditActionsExposeChangeModuleAndReadableChineseLabels() {
        assertThat(AuditService.inferModule("SALES_ORDER_CHANGE_ITEM_UPDATE")).isEqualTo("CHANGE");
        assertThat(AuditService.inferModule("SALES_ORDER_CHANGE_ITEM_DATA_REPLACEMENT")).isEqualTo("CHANGE");
        assertThat(AuditService.labelForAction("SALES_ORDER_CHANGE_ITEM_UPDATE")).isEqualTo("销售变更保存零件行");
        assertThat(AuditService.labelForAction("SALES_ORDER_CHANGE_ITEM_DATA_REPLACEMENT")).isEqualTo("销售变更表格数据替换");
    }

    @Test
    void permissionAndAuditDocsDescribeStage2Boundary() throws Exception {
        assertThat(read(PERMISSION_DOC))
                .contains("销售变更阶段 2", "change:sales-order:update", "不新增 `common:*`");
        assertThat(read(AUDIT_DOC))
                .contains(
                        "销售变更记录与审计边界",
                        "SALES_ORDER_CHANGE_ITEM_UPDATE",
                        "SALES_ORDER_CHANGE_ITEM_DATA_REPLACEMENT",
                        "sales_order_change_record",
                        "不得记录图纸 URL",
                        "完整 U9 请求",
                        "完整 U9 响应",
                        "Authorization");
        assertThat(read(CHANGE_DOC))
                .contains(
                        "V20__sales_order_change_records.sql",
                        "sales_order_change_record",
                        "sales_order_change_record_item");
    }

    private static String read(Path path) throws Exception {
        assertThat(path).exists();
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
