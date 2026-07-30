package com.machining.quote;

import com.machining.quote.dto.QuoteSubmitReq;
import com.machining.quote.entity.DictMarkupFee;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.mapper.DictExtraFeeItemMapper;
import com.machining.quote.mapper.DictMarkupFeeMapper;
import com.machining.quote.mapper.ItemAssemblyRelationMapper;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.EngineerService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOrderQpoImportStage1Test {
    private static final Path MIGRATION = Path.of("src", "main", "resources", "db", "migration", "V63__sales_order_qpo_import_fields.sql");
    private static final Path SCHEMA = Path.of("src", "main", "resources", "schema.sql");
    private static final Path ORDER_ENTITY = Path.of("src", "main", "java", "com", "machining", "quote", "entity", "OrderRfq.java");
    private static final Path ITEM_ENTITY = Path.of("src", "main", "java", "com", "machining", "quote", "entity", "ItemQuoteVersion.java");
    private static final Path BATCH_ENTITY = Path.of("src", "main", "java", "com", "machining", "quote", "entity", "SalesOrderImportBatch.java");
    private static final Path RECORD_ENTITY = Path.of("src", "main", "java", "com", "machining", "quote", "entity", "SalesOrderImportRecord.java");
    private static final Path PERMISSION_DOC = Path.of("..", "docs", "QPO权限用户体系", "02-权限点清单与现有角色映射.md");
    private static final Path LOG_DOC = Path.of("..", "docs", "QPO日志模块", "03-日志运维说明.md");
    private static final Path DISCUSSION_DOC = Path.of("..", "docs", "QPO报价状态流转", "10-销售订单直提流程方案讨论文档.md");

    @Test
    void migrationAndSchemaCarrySalesOrderQpoFields() throws IOException {
        String migration = read(MIGRATION);
        String schema = read(SCHEMA);

        assertThat(migration)
                .contains("ALTER TABLE order_rfq ADD COLUMN IF NOT EXISTS source_type VARCHAR(30) NOT NULL DEFAULT 'RFQ'")
                .contains("ALTER TABLE order_rfq ADD COLUMN IF NOT EXISTS customer_code VARCHAR(200)")
                .contains("ALTER TABLE order_rfq ADD COLUMN IF NOT EXISTS sales_document_type_code VARCHAR(50)")
                .contains("ALTER TABLE order_rfq ADD COLUMN IF NOT EXISTS sales_order_import_batch_id BIGINT")
                .contains("ALTER TABLE item_quote_version ADD COLUMN IF NOT EXISTS tax_included_unit_price NUMERIC(12, 2)")
                .contains("ALTER TABLE item_quote_version ADD COLUMN IF NOT EXISTS untaxed_unit_price NUMERIC(12, 2)")
                .contains("ALTER TABLE item_quote_version ADD COLUMN IF NOT EXISTS tax_included_total_price NUMERIC(12, 2)")
                .contains("ALTER TABLE item_quote_version ADD COLUMN IF NOT EXISTS untaxed_total_price NUMERIC(12, 2)")
                .contains("ALTER TABLE sales_order_import_batch ADD COLUMN IF NOT EXISTS import_mode VARCHAR(20) DEFAULT 'U9'")
                .contains("ALTER TABLE sales_order_import_record ADD COLUMN IF NOT EXISTS rfq_id INTEGER")
                .contains("CREATE INDEX IF NOT EXISTS idx_order_rfq_source_status_created")
                .contains("CREATE INDEX IF NOT EXISTS idx_item_quote_version_sales_order_record");
        assertThat(schema)
                .contains("source_type VARCHAR(30) NOT NULL DEFAULT 'RFQ'")
                .contains("tax_included_unit_price NUMERIC(12, 2)")
                .contains("untaxed_total_price NUMERIC(12, 2)")
                .contains("import_mode VARCHAR(20) DEFAULT 'U9'");
    }

    @Test
    void entitiesExposeSalesOrderQpoCarrierFields() throws IOException {
        String order = read(ORDER_ENTITY);
        String item = read(ITEM_ENTITY);
        String batch = read(BATCH_ENTITY);
        String record = read(RECORD_ENTITY);

        assertThat(order)
                .contains("import java.time.LocalDate;")
                .contains("private String sourceType;")
                .contains("private String customerCode;")
                .contains("private String customerShortName;")
                .contains("private String salesDocumentTypeCode;")
                .contains("private LocalDate salesOrderDate;")
                .contains("private String customerOrderNo;")
                .contains("private String customerProjectNo;")
                .contains("private String customerOrderTitle;")
                .contains("private String internalProjectNo;")
                .contains("private Long salesOrderImportBatchId;")
                .contains("private Long salesOrderImportRecordId;");
        assertThat(item)
                .contains("import java.time.LocalDate;")
                .contains("private Integer salesOrderLineNo;")
                .contains("private String salesMaterialNo;")
                .contains("private LocalDate customerDeliveryDate;")
                .contains("private LocalDate internalDeliveryDate;")
                .contains("private Double taxIncludedUnitPrice;")
                .contains("private Double untaxedUnitPrice;")
                .contains("private Double taxIncludedTotalPrice;")
                .contains("private Double untaxedTotalPrice;")
                .contains("private Integer shippedQty;")
                .contains("private Integer unshippedQty;")
                .contains("private LocalDate actualShipDate;")
                .contains("private Long salesOrderImportRecordId;");
        assertThat(batch).contains("private String importMode;");
        assertThat(record)
                .contains("private Integer rfqId;")
                .contains("private String rfqNo;")
                .contains("private String qpoStatus;")
                .contains("private Integer matchedItemCount;")
                .contains("private Integer unmatchedItemCount;")
                .contains("private String warningMessage;");
    }

    @Test
    void existingEngineerQuoteSaveWritesTraceableTaxAndUntaxedPrices() {
        EngineerService service = new EngineerService();
        ItemQuoteVersionMapper itemMapper = mock(ItemQuoteVersionMapper.class);
        ItemAssemblyRelationMapper assemblyRelationMapper = mock(ItemAssemblyRelationMapper.class);
        DictMarkupFeeMapper markupFeeMapper = mock(DictMarkupFeeMapper.class);
        DictExtraFeeItemMapper extraFeeItemMapper = mock(DictExtraFeeItemMapper.class);
        ReflectionTestUtils.setField(service, "itemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "assemblyRelationMapper", assemblyRelationMapper);
        ReflectionTestUtils.setField(service, "markupFeeMapper", markupFeeMapper);
        ReflectionTestUtils.setField(service, "extraFeeItemMapper", extraFeeItemMapper);
        when(markupFeeMapper.selectList(any())).thenReturn(List.of(markup("利润", 10.0), markup("税费", 13.0)));
        when(extraFeeItemMapper.selectList(any())).thenReturn(List.of());

        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setId(10);
        item.setRfqId(20);
        item.setPartNo("P-001");
        item.setQty(2);
        Map<String, Object> materialData = new LinkedHashMap<>();
        materialData.put("cost", 100.0);

        ReflectionTestUtils.invokeMethod(
                service,
                "saveCalculatedQuote",
                item,
                materialData,
                List.<QuoteSubmitReq.ProcessSubmitDTO>of(),
                0.5
        );

        assertThat((Double) ReflectionTestUtils.getField(item, "finalUnitPrice")).isCloseTo(123.0, within(0.000001));
        assertThat((Double) ReflectionTestUtils.getField(item, "finalTotalPrice")).isCloseTo(246.0, within(0.000001));
        assertThat((Double) ReflectionTestUtils.getField(item, "taxIncludedUnitPrice")).isCloseTo(123.0, within(0.000001));
        assertThat((Double) ReflectionTestUtils.getField(item, "taxIncludedTotalPrice")).isCloseTo(246.0, within(0.000001));
        assertThat((Double) ReflectionTestUtils.getField(item, "untaxedUnitPrice")).isCloseTo(110.0, within(0.000001));
        assertThat((Double) ReflectionTestUtils.getField(item, "untaxedTotalPrice")).isCloseTo(220.0, within(0.000001));
        verify(itemMapper).updateById(item);
    }

    @Test
    void auditAndDocsDescribeQpoImportSeparatelyFromU9Sync() throws IOException {
        assertThat(AuditService.inferModule("SALES_ORDER_IMPORT_QPO")).isEqualTo("MARKET");
        assertThat(AuditService.labelForAction("SALES_ORDER_IMPORT_QPO")).isEqualTo("导入销售订单进入QPO主流程");

        String permissionDoc = read(PERMISSION_DOC);
        String logDoc = read(LOG_DOC);
        String discussionDoc = read(DISCUSSION_DOC);
        assertThat(permissionDoc)
                .contains("`market:rfq:import-sales-order`")
                .contains("销售订单导入进入 QPO 主流程")
                .contains("不调用 U9");
        assertThat(logDoc)
                .contains("SALES_ORDER_IMPORT_QPO")
                .contains("Sales order QPO import")
                .contains("不记录完整 Excel");
        assertThat(discussionDoc)
                .contains("untaxed_unit_price")
                .contains("tax_included_total_price")
                .contains("第一步不调用 U9");
    }

    private DictMarkupFee markup(String name, double ratePercent) {
        DictMarkupFee fee = new DictMarkupFee();
        fee.setFeeName(name);
        fee.setRatePercent(ratePercent);
        fee.setEnabled(true);
        return fee;
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
