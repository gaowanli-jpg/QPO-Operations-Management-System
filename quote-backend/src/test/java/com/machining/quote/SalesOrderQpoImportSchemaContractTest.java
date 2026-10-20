package com.machining.quote;

import com.machining.quote.dto.QuoteSubmitReq;
import com.machining.quote.entity.DictMaterial;
import com.machining.quote.entity.DictMaterialPriceSpec;
import com.machining.quote.entity.DictMaterialType;
import com.machining.quote.entity.DictPricingMarkupFee;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.mapper.DictExtraFeeItemMapper;
import com.machining.quote.mapper.DictMaterialMapper;
import com.machining.quote.mapper.DictMaterialPriceSpecMapper;
import com.machining.quote.mapper.DictMaterialTypeMapper;
import com.machining.quote.mapper.DictPricingMarkupFeeMapper;
import com.machining.quote.mapper.ItemAssemblyRelationMapper;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.service.EngineerService;
import com.machining.quote.service.PricingFeeCalculator;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.math.BigDecimal;
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

class SalesOrderQpoImportSchemaContractTest {
    private static final Path INDEPENDENT_MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration", "V1__qpo_baseline_schema.sql");
    private static final Path SCHEMA = Path.of("src", "main", "resources", "db", "migration", "V1__qpo_baseline_schema.sql");
    private static final Path DOCUMENT_TYPE_DEFAULT_MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration", "V17__sales_order_document_type_default_so06.sql");
    private static final Path ORDER_ENTITY = Path.of("src", "main", "java", "com", "machining", "quote", "entity", "OrderRfq.java");
    private static final Path ITEM_ENTITY = Path.of("src", "main", "java", "com", "machining", "quote", "entity", "ItemQuoteVersion.java");
    private static final Path SALES_ORDER_HEADER_ENTITY = Path.of(
            "src", "main", "java", "com", "machining", "quote", "entity", "SalesOrderHeader.java");
    private static final Path SALES_ORDER_ITEM_ENTITY = Path.of(
            "src", "main", "java", "com", "machining", "quote", "entity", "SalesOrderItem.java");
    private static final Path SALES_ORDER_HEADER_MAPPER = Path.of(
            "src", "main", "java", "com", "machining", "quote", "mapper", "SalesOrderHeaderMapper.java");
    private static final Path SALES_ORDER_ITEM_MAPPER = Path.of(
            "src", "main", "java", "com", "machining", "quote", "mapper", "SalesOrderItemMapper.java");
    private static final Path BATCH_ENTITY = Path.of(
            "src", "main", "java", "com", "machining", "quote", "entity", "SalesOrderImportBatch.java");
    private static final Path RECORD_ENTITY = Path.of(
            "src", "main", "java", "com", "machining", "quote", "entity", "SalesOrderImportRecord.java");
    private static final Path PERMISSION_DOC = Path.of(
            "..", "docs", "QPO\u6743\u9650\u7528\u6237\u4f53\u7cfb", "02-\u6743\u9650\u70b9\u6e05\u5355\u4e0e\u73b0\u6709\u89d2\u8272\u6620\u5c04.md");
    private static final Path LOG_DOC = Path.of(
            "..", "docs", "QPO\u65e5\u5fd7\u6a21\u5757", "03-\u65e5\u5fd7\u8fd0\u7ef4\u8bf4\u660e.md");
    private static final Path DISCUSSION_DOC = Path.of(
            "..", "docs", "QPO\u62a5\u4ef7\u72b6\u6001\u6d41\u8f6c", "10-\u9500\u552e\u8ba2\u5355\u76f4\u63d0\u6d41\u7a0b\u65b9\u6848\u8ba8\u8bba\u6587\u6863.md");

    @Test
    void migrationAndSchemaCreateIndependentSalesOrderTables() throws IOException {
        String migration = read(INDEPENDENT_MIGRATION);
        String schema = read(SCHEMA);

        assertThat(migration)
                .contains("CREATE TABLE sales_order_header")
                .contains("sales_order_no character varying(100) NOT NULL")
                .contains("document_type_code character varying(50) DEFAULT 'SO01'::character varying NOT NULL")
                .contains("customer_id bigint")
                .contains("customer_name character varying(200)")
                .contains("status character varying(30) DEFAULT")
                .contains("CREATE TABLE sales_order_item")
                .contains("sales_order_id bigint NOT NULL")
                .contains("FOREIGN KEY (sales_order_id) REFERENCES sales_order_header(id) ON DELETE CASCADE")
                .contains("sales_material_no character varying(120)")
                .contains("material_no character varying(120)")
                .contains("u9_sync_status character varying(30)")
                .contains("tax_included_unit_price numeric(14,4)")
                .contains("untaxed_total_price numeric(14,2)")
                .contains("has_drawing boolean DEFAULT false")
                .contains("CREATE TABLE sales_order_import_batch")
                .contains("import_mode character varying(20) DEFAULT 'U9'::character varying")
                .contains("CREATE TABLE sales_order_import_record")
                .contains("sales_order_id bigint")
                .contains("sales_order_no character varying(100)")
                .contains("item_count integer DEFAULT 0");
        assertThat(schema)
                .contains("CREATE TABLE sales_order_header")
                .contains("CREATE TABLE sales_order_item")
                .contains("sales_order_id bigint NOT NULL")
                .contains("material_no character varying(120)")
                .contains("sales_order_id bigint");
    }

    @Test
    void entitiesExposeIndependentTablesAndDeprecateLegacyRfqCarrierFields() throws IOException {
        String header = read(SALES_ORDER_HEADER_ENTITY);
        String item = read(SALES_ORDER_ITEM_ENTITY);
        String headerMapper = read(SALES_ORDER_HEADER_MAPPER);
        String itemMapper = read(SALES_ORDER_ITEM_MAPPER);
        String batch = read(BATCH_ENTITY);
        String record = read(RECORD_ENTITY);
        String order = read(ORDER_ENTITY);
        String rfqItem = read(ITEM_ENTITY);

        assertThat(header)
                .contains("@TableName(\"sales_order_header\")")
                .contains("private String salesOrderNo;")
                .contains("private String documentTypeCode;")
                .contains("private Long customerId;")
                .contains("private String customerName;")
                .contains("private String status;")
                .contains("private String u9SyncStatus;");
        assertThat(item)
                .contains("@TableName(value = \"sales_order_item\", autoResultMap = true)")
                .contains("private Long salesOrderId;")
                .contains("private LocalDate salesOrderDate;")
                .contains("private String partNo;")
                .contains("private String salesMaterialNo;")
                .contains("private String customerOrderTitle;")
                .contains("private String materialNo;")
                .contains("private BigDecimal taxIncludedUnitPrice;")
                .contains("private BigDecimal untaxedTotalPrice;")
                .contains("private Boolean hasDrawing;")
                .contains("@TableField(typeHandler = JsonbTypeHandler.class)")
                .contains("private String status;");
        assertThat(headerMapper).contains("extends BaseMapper<SalesOrderHeader>");
        assertThat(itemMapper).contains("extends BaseMapper<SalesOrderItem>");
        assertThat(batch).contains("private String importMode;");
        assertThat(record)
                .contains("private Long salesOrderId;")
                .contains("private String salesOrderNo;")
                .contains("private Integer itemCount;")
                .contains("@Deprecated")
                .contains("private Integer rfqId;")
                .contains("private String rfqNo;");
        assertThat(order)
                .contains("@Deprecated")
                .contains("private String sourceType;")
                .contains("private Long salesOrderImportRecordId;");
        assertThat(rfqItem)
                .contains("@Deprecated")
                .contains("private Integer salesOrderLineNo;")
                .contains("private Long salesOrderImportRecordId;");
    }

    @Test
    void migrationMovesSalesOrderDocumentTypeDefaultToSo06WithoutEditingBaseline() throws IOException {
        String baseline = read(SCHEMA);
        String migration = read(DOCUMENT_TYPE_DEFAULT_MIGRATION);

        assertThat(baseline)
                .contains("document_type_code character varying(50) DEFAULT 'SO01'::character varying NOT NULL");
        assertThat(migration)
                .contains("ALTER TABLE sales_order_header")
                .contains("ALTER COLUMN document_type_code SET DEFAULT 'SO06'::character varying");
    }

    @Test
    void existingEngineerQuoteSaveStillWritesTraceableTaxAndUntaxedPrices() {
        EngineerService service = new EngineerService();
        ItemQuoteVersionMapper itemMapper = mock(ItemQuoteVersionMapper.class);
        ItemAssemblyRelationMapper assemblyRelationMapper = mock(ItemAssemblyRelationMapper.class);
        DictMaterialMapper materialMapper = mock(DictMaterialMapper.class);
        DictMaterialPriceSpecMapper materialPriceSpecMapper = mock(DictMaterialPriceSpecMapper.class);
        DictMaterialTypeMapper materialTypeMapper = mock(DictMaterialTypeMapper.class);
        DictPricingMarkupFeeMapper pricingMarkupFeeMapper = mock(DictPricingMarkupFeeMapper.class);
        DictExtraFeeItemMapper extraFeeItemMapper = mock(DictExtraFeeItemMapper.class);
        PricingFeeCalculator pricingFeeCalculator = new PricingFeeCalculator();
        ReflectionTestUtils.setField(service, "itemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "assemblyRelationMapper", assemblyRelationMapper);
        ReflectionTestUtils.setField(service, "materialMapper", materialMapper);
        ReflectionTestUtils.setField(service, "materialPriceSpecMapper", materialPriceSpecMapper);
        ReflectionTestUtils.setField(service, "materialTypeMapper", materialTypeMapper);
        ReflectionTestUtils.setField(service, "pricingFeeCalculator", pricingFeeCalculator);
        ReflectionTestUtils.setField(pricingFeeCalculator, "pricingMarkupFeeMapper", pricingMarkupFeeMapper);
        ReflectionTestUtils.setField(service, "extraFeeItemMapper", extraFeeItemMapper);
        when(materialMapper.selectList(any())).thenReturn(List.of(material()));
        when(materialPriceSpecMapper.selectOne(any())).thenReturn(priceSpec(100.0));
        when(materialTypeMapper.selectById(1L)).thenReturn(materialType());
        when(pricingMarkupFeeMapper.selectList(any())).thenReturn(List.of(
                markup("NORMAL", "BASIC_PERCENT", "profit", 10.0, 1),
                markup("NORMAL", "TAX", "category rate", 13.0, 2)));
        when(extraFeeItemMapper.selectList(any())).thenReturn(List.of());

        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setId(10);
        item.setRfqId(20);
        item.setPartNo("P-001");
        item.setQty(2);
        Map<String, Object> materialData = new LinkedHashMap<>();
        materialData.put("materialCode", "MAT-TEST-PRICE");
        materialData.put("priceSpecCode", "SPEC_1");
        materialData.put("shape", "板材");
        materialData.put("weight", 1.0);

        ReflectionTestUtils.invokeMethod(
                service,
                "saveCalculatedQuote",
                item,
                materialData,
                List.<QuoteSubmitReq.ProcessSubmitDTO>of(),
                0.5
        );

        assertThat((Double) ReflectionTestUtils.getField(item, "taxIncludedUnitPrice")).isCloseTo(124.3, within(0.000001));
        assertThat((Double) ReflectionTestUtils.getField(item, "taxIncludedTotalPrice")).isCloseTo(248.6, within(0.000001));
        assertThat((Double) ReflectionTestUtils.getField(item, "untaxedUnitPrice")).isCloseTo(110.0, within(0.000001));
        assertThat((Double) ReflectionTestUtils.getField(item, "untaxedTotalPrice")).isCloseTo(220.0, within(0.000001));
        verify(itemMapper).updateById(item);
    }

    @Test
    void docsDescribeIndependentSalesOrderBoundary() throws IOException {
        String permissionDoc = read(PERMISSION_DOC);
        String logDoc = read(LOG_DOC);
        String discussionDoc = read(DISCUSSION_DOC);
        assertThat(permissionDoc)
                .contains("sales_order_header")
                .contains("sales_order_item")
                .contains("\u4e0d\u518d\u521b\u5efa QPO `order_rfq`")
                .contains("\u4e0d\u518d\u521b\u5efa QPO `item_quote_version`");
        assertThat(logDoc)
                .contains("SALES_ORDER_IMPORT_QPO")
                .contains("\u72ec\u7acb\u4e3b\u8868")
                .contains("\u4e0d\u8bb0\u5f55\u5b8c\u6574 Excel");
        assertThat(discussionDoc)
                .contains("sales_order_header")
                .contains("sales_order_item")
                .contains("\u9500\u552e\u8ba2\u5355\u5bfc\u5165\u4e0d\u518d\u521b\u5efa `order_rfq`")
                .contains("\u9500\u552e\u8ba2\u5355\u5bfc\u5165\u4e0d\u518d\u521b\u5efa `item_quote_version`");
    }

    private DictPricingMarkupFee markup(String scope, String category, String name, double ratePercent, int sortNo) {
        DictPricingMarkupFee fee = new DictPricingMarkupFee();
        fee.setPricingScope(scope);
        fee.setFeeCategory(category);
        fee.setFeeName(name);
        fee.setRatePercent(BigDecimal.valueOf(ratePercent));
        fee.setSortNo(sortNo);
        fee.setEnabled(true);
        return fee;
    }

    private DictMaterial material() {
        DictMaterial material = new DictMaterial();
        material.setId(1L);
        material.setMaterialCode("MAT-TEST-PRICE");
        material.setMaterialName("测试钢");
        material.setMaterialTypeId(1L);
        material.setMaterialCategory("钢铁");
        material.setDensity(7850.0);
        material.setEnabled(true);
        return material;
    }

    private DictMaterialPriceSpec priceSpec(Double unifiedPrice) {
        DictMaterialPriceSpec spec = new DictMaterialPriceSpec();
        spec.setId(10L);
        spec.setMaterialId(1L);
        spec.setSpecCode("SPEC_1");
        spec.setSpecName("规格一");
        spec.setUnifiedPrice(unifiedPrice);
        spec.setSortNo(10);
        return spec;
    }

    private DictMaterialType materialType() {
        DictMaterialType type = new DictMaterialType();
        type.setId(1L);
        type.setTypeCode("RAW_MATERIAL");
        type.setTypeName("原材料");
        type.setEnabled(true);
        return type;
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
