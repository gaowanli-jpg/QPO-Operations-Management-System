package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.PricingMarkupFeeSaveReq;
import com.machining.quote.dto.QuoteSubmitReq;
import com.machining.quote.entity.DictMaterial;
import com.machining.quote.entity.DictPricingMarkupFee;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.DictMaterialMapper;
import com.machining.quote.mapper.DictPricingMarkupFeeMapper;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.service.EngineerService;
import com.machining.quote.service.ExcelExportService;
import com.machining.quote.service.PricingMarkupFeeService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest(properties = {
        "erp.auth.enabled=false",
        "erp.upload-dir=${java.io.tmpdir}/pricing-markup-fee-e2e/uploads/",
        "erp.backup-dir=${java.io.tmpdir}/pricing-markup-fee-e2e/backups/"
})
@Transactional
class PricingMarkupFeeEndToEndTest {
    private final DataFormatter formatter = new DataFormatter();

    @Autowired
    private DictPricingMarkupFeeMapper pricingMarkupFeeMapper;
    @Autowired
    private PricingMarkupFeeService pricingMarkupFeeService;
    @Autowired
    private DictMaterialMapper materialMapper;
    @Autowired
    private OrderRfqMapper orderMapper;
    @Autowired
    private ItemQuoteVersionMapper itemMapper;
    @Autowired
    private EngineerService engineerService;
    @Autowired
    private ExcelExportService excelExportService;

    @Test
    void systemTaxAndCustomerWeightRowsCannotBeDeleted() {
        resetPricingFees();
        DictPricingMarkupFee tax = insertPricingFee("NORMAL", "TAX", unique("Stage6 tax"), 13.0, 10, true, true);
        DictPricingMarkupFee customerWeight = insertPricingFee("NORMAL", "CUSTOMER_WEIGHT", unique("Stage6 customer weight"), 0.0, 20, true, true);

        assertThatThrownBy(() -> pricingMarkupFeeService.deleteFee(tax.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("system item cannot be deleted");
        assertThatThrownBy(() -> pricingMarkupFeeService.deleteFee(customerWeight.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("system item cannot be deleted");

        assertThat(pricingMarkupFeeMapper.selectById(tax.getId())).isNotNull();
        assertThat(pricingMarkupFeeMapper.selectById(customerWeight.getId())).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void normalAndAssemblyPricingUseUnifiedMultiplicativeFeesAndExportSavedSnapshots() throws Exception {
        resetPricingFees();
        DictPricingMarkupFee normalCustomerWeight = insertPricingFee("NORMAL", "CUSTOMER_WEIGHT", unique("Stage6 normal customer weight"), 10.0, 10, true, true);
        PricingMarkupFeeSaveReq normalBasicReq = feeReq("NORMAL", "BASIC_PERCENT", unique("Stage6 normal basic"), 5.0, 20, true);
        DictPricingMarkupFee normalBasic = pricingMarkupFeeService.saveFee(null, normalBasicReq);
        DictPricingMarkupFee normalTax = insertPricingFee("NORMAL", "TAX", unique("Stage6 normal tax"), 13.0, 30, true, true);
        insertPricingFee("ASSEMBLY", "CUSTOMER_WEIGHT", unique("Stage6 assembly customer weight"), 0.0, 10, true, true);
        insertPricingFee("ASSEMBLY", "BASIC_PERCENT", unique("Stage6 assembly basic"), 10.0, 20, true, false);
        insertPricingFee("ASSEMBLY", "TAX", unique("Stage6 assembly tax"), 13.0, 30, true, true);

        OrderRfq order = createOrder();
        ItemQuoteVersion normalItem = createItem(order, "STAGE6-NORMAL", 2);
        submitNormalQuote(normalItem, 100.0);

        ItemQuoteVersion savedNormal = itemMapper.selectById(normalItem.getId());
        assertThat(savedNormal.getUntaxedUnitPrice()).isCloseTo(115.5, within(0.000001));
        assertThat(savedNormal.getTaxIncludedUnitPrice()).isCloseTo(130.52, within(0.000001));
        assertThat(savedNormal.getTaxIncludedTotalPrice()).isCloseTo(261.03, within(0.000001));
        List<Map<String, Object>> normalFees = (List<Map<String, Object>>) savedNormal.getFeeData();
        assertThat(normalFees).extracting(row -> row.get("feeCategory"))
                .containsExactly("CUSTOMER_WEIGHT", "BASIC_PERCENT", "TAX");
        assertThat(normalFees).allSatisfy(row -> assertThat(row)
                .containsKeys("pricingScope", "feeCategory", "factor", "baseAmount", "amountAfter", "cost"));
        assertThat(numberValue(normalFees.get(2).get("amountAfter"))).isCloseTo(130.515, within(0.000001));

        ItemQuoteVersion assemblyParent = createItem(order, "STAGE6-ASSEMBLY", 1);
        submitAssemblyQuote(assemblyParent, 100.0, savedNormal, 1.0);

        ItemQuoteVersion savedAssembly = itemMapper.selectById(assemblyParent.getId());
        assertThat(savedAssembly.getUntaxedUnitPrice()).isCloseTo(240.52, within(0.000001));
        assertThat(savedAssembly.getTaxIncludedUnitPrice()).isCloseTo(254.82, within(0.000001));
        List<Map<String, Object>> assemblyFees = (List<Map<String, Object>>) savedAssembly.getFeeData();
        assertThat(assemblyFees).extracting(row -> row.get("pricingScope"))
                .containsOnly("ASSEMBLY");
        assertThat(assemblyFees).extracting(row -> row.get("feeCategory"))
                .containsExactly("CUSTOMER_WEIGHT", "BASIC_PERCENT", "TAX");

        normalCustomerWeight.setRatePercent(BigDecimal.valueOf(900.0));
        pricingMarkupFeeMapper.updateById(normalCustomerWeight);
        normalBasic.setRatePercent(BigDecimal.valueOf(900.0));
        pricingMarkupFeeMapper.updateById(normalBasic);
        normalTax.setRatePercent(BigDecimal.valueOf(900.0));
        pricingMarkupFeeMapper.updateById(normalTax);
        order.setStatus(QuoteStatusWorkflow.QUOTED);
        orderMapper.updateById(order);

        MockHttpServletResponse response = new MockHttpServletResponse();
        excelExportService.exportQuoteSheet(order.getId(), response);

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row exportedNormalRow = rowByPartNo(sheet, "STAGE6-NORMAL");
            assertThat(number(exportedNormalRow, headerCol(sheet, "含税单价"))).isCloseTo(130.52, within(0.000001));
            assertThat(number(exportedNormalRow, headerCol(sheet, "含税总价"))).isCloseTo(261.03, within(0.000001));
            assertThat(number(exportedNormalRow, headerCol(sheet, "基础成本"))).isCloseTo(100.0, within(0.000001));
        }
    }

    private void resetPricingFees() {
        UpdateWrapper<DictPricingMarkupFee> update = new UpdateWrapper<>();
        update.set("enabled", false);
        pricingMarkupFeeMapper.update(null, update);
    }

    private DictPricingMarkupFee insertPricingFee(
            String pricingScope,
            String feeCategory,
            String feeName,
            double ratePercent,
            int sortNo,
            boolean enabled,
            boolean systemRequired) {
        DictPricingMarkupFee fee = new DictPricingMarkupFee();
        fee.setPricingScope(pricingScope);
        fee.setFeeCategory(feeCategory);
        fee.setFeeName(feeName);
        fee.setRatePercent(BigDecimal.valueOf(ratePercent));
        fee.setSortNo(sortNo);
        fee.setEnabled(enabled);
        fee.setSystemRequired(systemRequired);
        pricingMarkupFeeMapper.insert(fee);
        return fee;
    }

    private PricingMarkupFeeSaveReq feeReq(String pricingScope, String feeCategory, String feeName,
                                           double ratePercent, int sortNo, boolean enabled) {
        PricingMarkupFeeSaveReq req = new PricingMarkupFeeSaveReq();
        req.setPricingScope(pricingScope);
        req.setFeeCategory(feeCategory);
        req.setFeeName(feeName);
        req.setRatePercent(BigDecimal.valueOf(ratePercent));
        req.setSortNo(sortNo);
        req.setEnabled(enabled);
        return req;
    }

    private OrderRfq createOrder() {
        OrderRfq order = new OrderRfq();
        order.setRfqNo(unique("RFQ-STAGE6"));
        order.setCustomerName("Stage6 百分比费用客户");
        order.setStatus(QuoteStatusWorkflow.PRICED);
        orderMapper.insert(order);
        return order;
    }

    private ItemQuoteVersion createItem(OrderRfq order, String partNo, int qty) {
        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setRfqId(order.getId());
        item.setPartNo(partNo);
        item.setVersion("A");
        item.setDescription(partNo + " description");
        item.setQty(qty);
        item.setUnit("件");
        item.setDrawingUrl("/uploads/stage6/" + partNo + ".pdf");
        item.setMaterialName("Stage6 material");
        item.setStatus(QuoteStatusWorkflow.UNPRICED);
        itemMapper.insert(item);
        return item;
    }

    private void submitNormalQuote(ItemQuoteVersion item, double baseCost) {
        QuoteSubmitReq req = new QuoteSubmitReq();
        req.setItemId(item.getId());
        req.setQuoteMode("NORMAL");
        req.setPartNetWeight(baseCost);
        req.setMaterialData(materialData(baseCost));
        req.setProcessRoute(List.of());
        engineerService.calculateAndSaveQuote(req);
    }

    private void submitAssemblyQuote(ItemQuoteVersion parent, double assemblyFee, ItemQuoteVersion child, double qtyPerParent) {
        QuoteSubmitReq.AssemblyChildDTO childReq = new QuoteSubmitReq.AssemblyChildDTO();
        childReq.setChildItemId(child.getId());
        childReq.setQtyPerParent(qtyPerParent);
        childReq.setSortNo(10);

        QuoteSubmitReq req = new QuoteSubmitReq();
        req.setItemId(parent.getId());
        req.setQuoteMode("ASSEMBLY");
        req.setAssemblyFee(assemblyFee);
        req.setAssemblyChildren(List.of(childReq));
        engineerService.calculateAndSaveQuote(req);
    }

    private Map<String, Object> materialData(double baseCost) {
        DictMaterial material = ensureFlatMaterial();
        Map<String, Object> materialData = new LinkedHashMap<>();
        materialData.put("materialCode", material.getMaterialCode());
        materialData.put("shape", "板材");
        materialData.put("weight", baseCost);
        return materialData;
    }

    private DictMaterial ensureFlatMaterial() {
        String materialCode = "MAT-STAGE6-FLAT";
        DictMaterial existing = materialMapper.selectOne(new QueryWrapper<DictMaterial>().eq("material_code", materialCode));
        if (existing != null) {
            return existing;
        }
        DictMaterial material = new DictMaterial();
        material.setMaterialCode(materialCode);
        material.setMaterialName("Stage6 flat material");
        material.setMaterialTypeId(1L);
        material.setMaterialCategory("Steel");
        material.setDensity(1.0);
        material.setPlatePrice(1.0);
        material.setBarPrice(1.0);
        material.setTubePrice(1.0);
        material.setEnabled(true);
        material.setSortNo(0);
        materialMapper.insert(material);
        return material;
    }

    private Row rowByPartNo(Sheet sheet, String partNo) {
        for (Row row : sheet) {
            if (partNo.equals(text(row, 1))) {
                return row;
            }
        }
        throw new AssertionError("Missing exported row for " + partNo);
    }

    private int headerCol(Sheet sheet, String header) {
        Row headerRow = sheet.getRow(1);
        for (Cell cell : headerRow) {
            if (header.equals(formatter.formatCellValue(cell))) {
                return cell.getColumnIndex();
            }
        }
        throw new AssertionError("Missing header " + header);
    }

    private String text(Row row, int col) {
        return formatter.formatCellValue(row.getCell(col));
    }

    private double number(Row row, int col) {
        return row.getCell(col).getNumericCellValue();
    }

    private double numberValue(Object value) {
        return ((Number) value).doubleValue();
    }

    private String unique(String prefix) {
        return prefix + "-" + System.nanoTime();
    }
}
