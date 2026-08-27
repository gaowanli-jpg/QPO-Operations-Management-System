package com.machining.quote;

import com.machining.quote.entity.DictMaterial;
import com.machining.quote.entity.DictMaterialType;
import com.machining.quote.entity.DictPricingMarkupFee;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.DictExtraFeeItemMapper;
import com.machining.quote.mapper.DictMaterialMapper;
import com.machining.quote.mapper.DictMaterialTypeMapper;
import com.machining.quote.mapper.DictPricingMarkupFeeMapper;
import com.machining.quote.mapper.ItemAssemblyRelationMapper;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.service.EngineerService;
import com.machining.quote.service.PricingFeeCalculator;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EngineerServiceMaterialCalculationTests {

    @Test
    void calculatesPlateWeightFromMetricDimensionsAndDatabaseMaterialCodeSnapshot() {
        EngineerService service = new EngineerService();
        DictMaterialMapper materialMapper = mock(DictMaterialMapper.class);
        when(materialMapper.selectList(any()))
                .thenReturn(List.of(material("MAT-000005", "45#", 1L, "钢铁", 7850.0, 7.1)));
        ReflectionTestUtils.setField(service, "materialMapper", materialMapper);
        setOptionalField(service, "materialTypeMapper", materialTypeMapper());

        Map<String, Object> materialData = new LinkedHashMap<>();
        materialData.put("materialCode", "MAT-000005");
        materialData.put("name", "前端伪造材料");
        materialData.put("materialName", "前端伪造材料");
        materialData.put("materialTypeCode", "FAKE_TYPE");
        materialData.put("materialTypeName", "前端伪造类型");
        materialData.put("materialCategory", "前端伪造分类");
        materialData.put("shape", "板材");
        materialData.put("density", 1.0);
        materialData.put("unitPrice", 9999.0);
        materialData.put("cost", 9999.0);
        materialData.put("dimensionUnit", "m");
        materialData.put("dimensions", Map.of("L", 0.2, "W", 0.1, "T", 0.01));

        Double cost = ReflectionTestUtils.invokeMethod(service, "calculateMaterialCost", materialData);

        assertThat(materialData.get("materialId")).isEqualTo(5L);
        assertThat(materialData.get("materialCode")).isEqualTo("MAT-000005");
        assertThat(materialData.get("materialName")).isEqualTo("45#");
        assertThat(materialData.get("name")).isEqualTo("45#");
        assertThat(materialData.get("materialTypeId")).isEqualTo(1L);
        assertThat(materialData.get("materialTypeCode")).isEqualTo("RAW_MATERIAL");
        assertThat(materialData.get("materialTypeName")).isEqualTo("原材料");
        assertThat(materialData.get("materialCategory")).isEqualTo("钢铁");
        assertThat((Double) materialData.get("volume")).isCloseTo(0.0002, within(0.000000001));
        assertThat((Double) materialData.get("weight")).isCloseTo(1.57, within(0.000001));
        assertThat(cost).isCloseTo(11.147, within(0.000001));
        assertThat(materialData.get("densityUnit")).isEqualTo("kg/m3");
        assertThat(materialData.get("unitPriceUnit")).isEqualTo("元/kg");
    }

    @Test
    void keepsSubmittedWeightWhenDimensionUnitIsMissingButStillUsesMaterialCodePrice() {
        EngineerService service = new EngineerService();
        DictMaterialMapper materialMapper = mock(DictMaterialMapper.class);
        when(materialMapper.selectList(any()))
                .thenReturn(List.of(material("MAT-000005", "45#", 1L, "钢铁", 7850.0, 7.1)));
        ReflectionTestUtils.setField(service, "materialMapper", materialMapper);
        setOptionalField(service, "materialTypeMapper", materialTypeMapper());

        Map<String, Object> materialData = new LinkedHashMap<>();
        materialData.put("materialCode", "MAT-000005");
        materialData.put("shape", "板材");
        materialData.put("weight", 1.57);
        materialData.put("dimensions", Map.of("L", 200, "W", 100, "T", 10));

        Double cost = ReflectionTestUtils.invokeMethod(service, "calculateMaterialCost", materialData);

        assertThat(materialData).doesNotContainKey("volume");
        assertThat((Double) materialData.get("weight")).isEqualTo(1.57);
        assertThat(cost).isCloseTo(11.147, within(0.000001));
        assertThat(materialData.get("materialCode")).isEqualTo("MAT-000005");
        assertThat(materialData.get("materialTypeName")).isEqualTo("原材料");
    }

    @Test
    void rejectsSelectedMaterialWhenShapePriceIsMissing() {
        EngineerService service = new EngineerService();
        DictMaterialMapper materialMapper = mock(DictMaterialMapper.class);
        when(materialMapper.selectList(any()))
                .thenReturn(List.of(material("MES-MAT-001", "MES Material No Price", 1L, "MES", 7850.0, null)));
        ReflectionTestUtils.setField(service, "materialMapper", materialMapper);
        setOptionalField(service, "materialTypeMapper", materialTypeMapper());

        Map<String, Object> materialData = new LinkedHashMap<>();
        materialData.put("materialCode", "MES-MAT-001");
        materialData.put("shape", "鏉挎潗");
        materialData.put("weight", 1.0);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "calculateMaterialCost", materialData))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("MES-MAT-001")
                .hasMessageContaining("MES Material No Price")
                .hasMessageContaining("鏉挎潗");
    }

    @Test
    void rejectsSelectedMaterialWhenShapePriceIsZero() {
        EngineerService service = new EngineerService();
        DictMaterialMapper materialMapper = mock(DictMaterialMapper.class);
        when(materialMapper.selectList(any()))
                .thenReturn(List.of(material("MES-MAT-002", "MES Material Zero Price", 1L, "MES", 7850.0, 0.0)));
        ReflectionTestUtils.setField(service, "materialMapper", materialMapper);
        setOptionalField(service, "materialTypeMapper", materialTypeMapper());

        Map<String, Object> materialData = new LinkedHashMap<>();
        materialData.put("materialCode", "MES-MAT-002");
        materialData.put("shape", "鏉挎潗");
        materialData.put("weight", 1.0);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "calculateMaterialCost", materialData))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("MES-MAT-002")
                .hasMessageContaining("MES Material Zero Price")
                .hasMessageContaining("鏉挎潗");
    }

    @Test
    void fallsBackToMesDensityWhenShapeDensityIsMissing() {
        EngineerService service = new EngineerService();
        DictMaterialMapper materialMapper = mock(DictMaterialMapper.class);
        DictMaterial material = material("MES-MAT-DENSITY", "MES Density Material", 1L, "MES", 0.0, 7.1);
        material.setDensity(null);
        material.setPlateDensity(null);
        material.setMesDensityRaw("7850.0");
        when(materialMapper.selectList(any())).thenReturn(List.of(material));
        ReflectionTestUtils.setField(service, "materialMapper", materialMapper);
        setOptionalField(service, "materialTypeMapper", materialTypeMapper());

        Map<String, Object> materialData = new LinkedHashMap<>();
        materialData.put("materialCode", "MES-MAT-DENSITY");
        materialData.put("shape", "PLATE");
        materialData.put("dimensionUnit", "m");
        materialData.put("dimensions", Map.of("L", 0.2, "W", 0.1, "T", 0.01));

        Double cost = ReflectionTestUtils.invokeMethod(service, "calculateMaterialCost", materialData);

        assertThat((Double) materialData.get("weight")).isCloseTo(1.57, within(0.000001));
        assertThat(materialData.get("density")).isEqualTo(7850.0);
        assertThat(cost).isCloseTo(11.147, within(0.000001));
    }

    @Test
    void rejectsMaterialWhenShapeAndMesDensitiesAreMissing() {
        EngineerService service = new EngineerService();
        DictMaterialMapper materialMapper = mock(DictMaterialMapper.class);
        DictMaterial material = material("MES-MAT-NO-DENSITY", "MES Material No Density", 1L, "MES", 0.0, 7.1);
        material.setDensity(null);
        material.setPlateDensity(null);
        material.setMesDensityRaw(null);
        when(materialMapper.selectList(any())).thenReturn(List.of(material));
        ReflectionTestUtils.setField(service, "materialMapper", materialMapper);
        setOptionalField(service, "materialTypeMapper", materialTypeMapper());

        Map<String, Object> materialData = new LinkedHashMap<>();
        materialData.put("materialCode", "MES-MAT-NO-DENSITY");
        materialData.put("shape", "PLATE");
        materialData.put("dimensionUnit", "m");
        materialData.put("dimensions", Map.of("L", 0.2, "W", 0.1, "T", 0.01));

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "calculateMaterialCost", materialData))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("density")
                .hasMessageContaining("MES-MAT-NO-DENSITY")
                .hasMessageContaining("MES Material No Density")
                .hasMessageContaining("PLATE");
    }

    @Test
    void positiveBarAndTubePricesStillCalculateFromShapeSpecificPrice() {
        EngineerService service = new EngineerService();
        DictMaterialMapper materialMapper = mock(DictMaterialMapper.class);
        DictMaterial material = material("MAT-SHAPE", "Shape Price Material", 1L, "Steel", 7850.0, 7.1);
        material.setBarPrice(8.2);
        material.setTubePrice(9.3);
        when(materialMapper.selectList(any())).thenReturn(List.of(material));
        ReflectionTestUtils.setField(service, "materialMapper", materialMapper);
        setOptionalField(service, "materialTypeMapper", materialTypeMapper());

        Map<String, Object> barMaterialData = new LinkedHashMap<>();
        barMaterialData.put("materialCode", "MAT-SHAPE");
        barMaterialData.put("shape", "妫掓潗");
        barMaterialData.put("weight", 2.0);
        Double barCost = ReflectionTestUtils.invokeMethod(service, "calculateMaterialCost", barMaterialData);

        Map<String, Object> tubeMaterialData = new LinkedHashMap<>();
        tubeMaterialData.put("materialCode", "MAT-SHAPE");
        tubeMaterialData.put("shape", "绠℃潗");
        tubeMaterialData.put("weight", 2.0);
        Double tubeCost = ReflectionTestUtils.invokeMethod(service, "calculateMaterialCost", tubeMaterialData);

        assertThat(barCost).isCloseTo(16.4, within(0.000001));
        assertThat(tubeCost).isCloseTo(18.6, within(0.000001));
    }

    @Test
    void rejectsNormalMaterialDataWithoutMaterialCode() {
        EngineerService service = new EngineerService();
        ReflectionTestUtils.setField(service, "materialMapper", mock(DictMaterialMapper.class));

        Map<String, Object> materialData = new LinkedHashMap<>();
        materialData.put("name", "45#");
        materialData.put("shape", "板材");
        materialData.put("weight", 1.57);
        materialData.put("cost", 9999.0);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "calculateMaterialCost", materialData))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("重新选择材料");
    }

    @Test
    void saveCalculatedQuoteWritesEngineeringMaterialSnapshotFields() throws Exception {
        EngineerService service = new EngineerService();
        DictMaterialMapper materialMapper = mock(DictMaterialMapper.class);
        when(materialMapper.selectList(any()))
                .thenReturn(List.of(material("MAT-000005", "45#", 1L, "钢铁", 7850.0, 7.1)));
        ReflectionTestUtils.setField(service, "materialMapper", materialMapper);
        setOptionalField(service, "materialTypeMapper", materialTypeMapper());
        ReflectionTestUtils.setField(service, "pricingFeeCalculator", emptyPricingCalculator());
        ReflectionTestUtils.setField(service, "extraFeeItemMapper", emptyExtraFeeMapper());
        ReflectionTestUtils.setField(service, "assemblyRelationMapper", mock(ItemAssemblyRelationMapper.class));
        ReflectionTestUtils.setField(service, "itemMapper", mock(ItemQuoteVersionMapper.class));

        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setId(1001);
        item.setRfqId(2001);
        item.setPartNo("P-001");
        item.setQty(2);

        Map<String, Object> materialData = new LinkedHashMap<>();
        materialData.put("materialCode", "MAT-000005");
        materialData.put("name", "前端伪造材料");
        materialData.put("shape", "板材");
        materialData.put("weight", 1.57);
        materialData.put("density", 1.0);
        materialData.put("unitPrice", 9999.0);
        materialData.put("cost", 9999.0);

        ReflectionTestUtils.invokeMethod(service, "saveCalculatedQuote", item, materialData, List.of(), 1.25);

        assertThat(getRequiredField(item, "engineeringMaterialCode")).isEqualTo("MAT-000005");
        assertThat(item.getEngineeringMaterialName()).isEqualTo("45#");
        assertThat(getRequiredField(item, "engineeringMaterialTypeCode")).isEqualTo("RAW_MATERIAL");
        assertThat(getRequiredField(item, "engineeringMaterialTypeName")).isEqualTo("原材料");
        assertThat(getRequiredField(item, "engineeringMaterialCategory")).isEqualTo("钢铁");
        assertThat(item.getMaterialShape()).isEqualTo("板材");
        @SuppressWarnings("unchecked")
        Map<String, Object> savedMaterialData = (Map<String, Object>) item.getMaterialData();
        assertThat(savedMaterialData)
                .containsEntry("materialCode", "MAT-000005")
                .containsEntry("materialName", "45#")
                .containsEntry("materialTypeCode", "RAW_MATERIAL")
                .containsEntry("materialTypeName", "原材料")
                .containsEntry("materialCategory", "钢铁")
                .containsEntry("shape", "板材");
    }

    @Test
    @SuppressWarnings("unchecked")
    void saveCalculatedQuoteUsesMultiplicativeMarkupFactorsFromCategories() throws Exception {
        EngineerService service = new EngineerService();
        DictMaterialMapper materialMapper = mock(DictMaterialMapper.class);
        when(materialMapper.selectList(any()))
                .thenReturn(List.of(material("MAT-PRICE", "Price Material", 1L, "Steel", 1.0, 100.0)));
        ReflectionTestUtils.setField(service, "materialMapper", materialMapper);
        setOptionalField(service, "materialTypeMapper", materialTypeMapper());
        ReflectionTestUtils.setField(service, "pricingFeeCalculator", pricingCalculator(
                pricingFee(1L, "NORMAL", "CUSTOMER_WEIGHT", "Customer weight", "10", 1),
                pricingFee(2L, "NORMAL", "BASIC_PERCENT", "Freight", "5", 1),
                pricingFee(3L, "NORMAL", "BASIC_PERCENT", "tax word but basic", "5", 2),
                pricingFee(4L, "NORMAL", "BASIC_PERCENT", "Profit", "15", 3),
                pricingFee(5L, "NORMAL", "TAX", "Category rate", "13", 4)));
        ReflectionTestUtils.setField(service, "extraFeeItemMapper", emptyExtraFeeMapper());
        ReflectionTestUtils.setField(service, "assemblyRelationMapper", mock(ItemAssemblyRelationMapper.class));
        ReflectionTestUtils.setField(service, "itemMapper", mock(ItemQuoteVersionMapper.class));

        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setId(1002);
        item.setRfqId(2002);
        item.setPartNo("P-PRICE");
        item.setQty(2);
        Map<String, Object> materialData = new LinkedHashMap<>();
        materialData.put("materialCode", "MAT-PRICE");
        materialData.put("shape", "PLATE");
        materialData.put("weight", 1.0);

        ReflectionTestUtils.invokeMethod(service, "saveCalculatedQuote", item, materialData, List.of(), 0.5);

        assertThat(item.getUntaxedUnitPrice()).isCloseTo(139.46625, within(0.000001));
        assertThat(item.getTaxIncludedUnitPrice()).isCloseTo(157.5968625, within(0.000001));
        assertThat(item.getUntaxedTotalPrice()).isCloseTo(278.9325, within(0.000001));
        assertThat(item.getTaxIncludedTotalPrice()).isCloseTo(315.193725, within(0.000001));
        List<Map<String, Object>> feeRows = (List<Map<String, Object>>) item.getFeeData();
        assertThat(feeRows).hasSize(5);
        assertThat(feeRows).extracting(row -> row.get("feeCategory"))
                .containsExactly("CUSTOMER_WEIGHT", "BASIC_PERCENT", "BASIC_PERCENT", "BASIC_PERCENT", "TAX");
        assertThat(feeRows).last().satisfies(row -> {
            assertThat(row).containsEntry("feeName", "Category rate");
            assertThat(row).containsEntry("pricingScope", "NORMAL");
            assertThat(row).containsEntry("includedInUntaxedPrice", false);
            assertThat(row).containsEntry("includedInTaxIncludedPrice", true);
        });
    }

    @Test
    void pricingCalculatorUsesTaxCategoryEvenWhenFeeNameHasNoTaxWords() {
        PricingFeeCalculator calculator = pricingCalculator(pricingFee(1L, "NORMAL", "TAX", "Category rate", "13", 1));

        PricingFeeCalculator.PricingResult result = calculator.calculate(100.0, "NORMAL");

        assertThat(result.getUntaxedUnitPrice()).isCloseTo(100.0, within(0.000001));
        assertThat(result.getTaxIncludedUnitPrice()).isCloseTo(113.0, within(0.000001));
        assertThat(result.getTaxFactor()).isCloseTo(1.13, within(0.000001));
        assertThat(result.getFeeRows()).singleElement().satisfies(row -> assertThat(row)
                .containsEntry("feeCategory", "TAX")
                .containsEntry("feeName", "Category rate"));
    }

    @Test
    void pricingCalculatorTreatsBasicPercentNameContainingTaxAsBasicFee() {
        PricingFeeCalculator calculator = pricingCalculator(pricingFee(1L, "NORMAL", "BASIC_PERCENT", "tax word but basic", "13", 1));

        PricingFeeCalculator.PricingResult result = calculator.calculate(100.0, "NORMAL");

        assertThat(result.getUntaxedUnitPrice()).isCloseTo(113.0, within(0.000001));
        assertThat(result.getTaxIncludedUnitPrice()).isCloseTo(113.0, within(0.000001));
        assertThat(result.getBasicPercentFactor()).isCloseTo(1.13, within(0.000001));
        assertThat(result.getTaxFactor()).isCloseTo(1.0, within(0.000001));
        assertThat(result.getFeeRows()).singleElement().satisfies(row -> assertThat(row)
                .containsEntry("feeCategory", "BASIC_PERCENT")
                .containsEntry("feeName", "tax word but basic"));
    }

    @Test
    void repeatQuoteUsesSourceEngineeringMaterialCodeAndRecalculatesFromCurrentDictionary() throws Exception {
        EngineerService service = repeatQuoteServiceWithMaterial("MAT-000005", "45#-当前字典", "钢铁", 7850.0, 8.0);
        ItemQuoteVersion source = pricedSourceItem();
        source.setEngineeringMaterialCode("MAT-000005");
        source.setEngineeringMaterialName("45#-历史快照");
        source.setEngineeringMaterialTypeCode("RAW_MATERIAL");
        source.setEngineeringMaterialTypeName("原材料");
        source.setEngineeringMaterialCategory("旧分类");
        source.setMaterialShape("板材");
        source.setMaterialData(Map.of(
                "name", "45#-历史快照",
                "shape", "板材",
                "weight", 2.0,
                "cost", 9999.0));

        ItemQuoteVersion current = currentRepeatTarget();
        List<Map<String, Object>> appliedItems = new ArrayList<>();

        String skippedReason = ReflectionTestUtils.invokeMethod(
                service,
                "applyRepeatNormalQuote",
                repeatSource(source),
                current,
                new HashSet<Integer>(),
                appliedItems);

        assertThat(skippedReason).isNull();
        assertThat(getRequiredField(current, "engineeringMaterialCode")).isEqualTo("MAT-000005");
        assertThat(current.getEngineeringMaterialName()).isEqualTo("45#-当前字典");
        assertThat(getRequiredField(current, "engineeringMaterialTypeCode")).isEqualTo("RAW_MATERIAL");
        assertThat(getRequiredField(current, "engineeringMaterialTypeName")).isEqualTo("原材料");
        assertThat(getRequiredField(current, "engineeringMaterialCategory")).isEqualTo("钢铁");
        assertThat(current.getMaterialShape()).isEqualTo("板材");
        @SuppressWarnings("unchecked")
        Map<String, Object> savedMaterialData = (Map<String, Object>) current.getMaterialData();
        assertThat(savedMaterialData)
                .containsEntry("materialCode", "MAT-000005")
                .containsEntry("materialName", "45#-当前字典")
                .containsEntry("materialTypeName", "原材料")
                .containsEntry("materialCategory", "钢铁")
                .containsEntry("shape", "板材");
        assertThat((Double) savedMaterialData.get("cost")).isCloseTo(16.0, within(0.000001));
        assertThat(current.getTaxIncludedUnitPrice()).isCloseTo(16.0, within(0.000001));
        assertThat(appliedItems).singleElement()
                .satisfies(item -> assertThat(item.get("taxIncludedUnitPrice")).isEqualTo(16.0));
    }

    @Test
    void repeatQuoteSkipsSourceWithoutEngineeringMaterialCode() throws Exception {
        EngineerService service = repeatQuoteServiceWithMaterial("MAT-000005", "45#", "钢铁", 7850.0, 8.0);
        ItemQuoteVersion source = pricedSourceItem();
        source.setMaterialData(Map.of("name", "45#", "shape", "板材", "weight", 2.0));
        ItemQuoteVersion current = currentRepeatTarget();

        String skippedReason = ReflectionTestUtils.invokeMethod(
                service,
                "applyRepeatNormalQuote",
                repeatSource(source),
                current,
                new HashSet<Integer>(),
                new ArrayList<Map<String, Object>>());

        assertThat(skippedReason).contains("材料编码").contains("手动");
        assertThat(current.getTaxIncludedUnitPrice()).isNull();
        assertThat(current.getMaterialData()).isNull();
    }

    private DictMaterial material(String code, String name, Long typeId, String category, double density, Double unitPrice) {
        DictMaterial material = new DictMaterial();
        material.setId(5L);
        material.setMaterialCode(code);
        material.setMaterialName(name);
        material.setMaterialTypeId(typeId);
        material.setMaterialCategory(category);
        material.setEnabled(true);
        material.setDensity(density);
        material.setPlateDensity(density);
        material.setPlatePrice(unitPrice);
        return material;
    }

    private EngineerService repeatQuoteServiceWithMaterial(
            String code,
            String name,
            String category,
            double density,
            double unitPrice) {
        EngineerService service = new EngineerService();
        DictMaterialMapper materialMapper = mock(DictMaterialMapper.class);
        when(materialMapper.selectList(any()))
                .thenReturn(List.of(material(code, name, 1L, category, density, unitPrice)));
        ReflectionTestUtils.setField(service, "materialMapper", materialMapper);
        setOptionalField(service, "materialTypeMapper", materialTypeMapper());
        ReflectionTestUtils.setField(service, "pricingFeeCalculator", emptyPricingCalculator());
        ReflectionTestUtils.setField(service, "extraFeeItemMapper", emptyExtraFeeMapper());
        ReflectionTestUtils.setField(service, "assemblyRelationMapper", mock(ItemAssemblyRelationMapper.class));
        ReflectionTestUtils.setField(service, "itemMapper", mock(ItemQuoteVersionMapper.class));
        return service;
    }

    private ItemQuoteVersion pricedSourceItem() {
        ItemQuoteVersion source = new ItemQuoteVersion();
        source.setId(9001);
        source.setRfqId(7001);
        source.setPartNo("P-REPEAT");
        source.setVersion("A");
        source.setQty(2);
        source.setTaxIncludedUnitPrice(999.0);
        source.setPartNetWeight(1.5);
        return source;
    }

    private ItemQuoteVersion currentRepeatTarget() {
        ItemQuoteVersion current = new ItemQuoteVersion();
        current.setId(1001);
        current.setRfqId(8001);
        current.setPartNo("P-REPEAT");
        current.setVersion("A");
        current.setQty(2);
        current.setDrawingUrl("drawings/P-REPEAT.pdf");
        return current;
    }

    private Object repeatSource(ItemQuoteVersion source) throws Exception {
        OrderRfq order = new OrderRfq();
        order.setId(source.getRfqId());
        Class<?> repeatSourceClass = Class.forName("com.machining.quote.service.EngineerService$RepeatSource");
        Constructor<?> constructor = repeatSourceClass.getDeclaredConstructor(OrderRfq.class, ItemQuoteVersion.class);
        constructor.setAccessible(true);
        return constructor.newInstance(order, source);
    }

    private DictMaterialTypeMapper materialTypeMapper() {
        DictMaterialType type = new DictMaterialType();
        type.setId(1L);
        type.setTypeCode("RAW_MATERIAL");
        type.setTypeName("原材料");
        type.setEnabled(true);
        DictMaterialTypeMapper mapper = mock(DictMaterialTypeMapper.class);
        when(mapper.selectById(1L)).thenReturn(type);
        return mapper;
    }

    private PricingFeeCalculator emptyPricingCalculator() {
        return pricingCalculator();
    }

    private PricingFeeCalculator pricingCalculator(DictPricingMarkupFee... fees) {
        DictPricingMarkupFeeMapper mapper = mock(DictPricingMarkupFeeMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of(fees));
        PricingFeeCalculator calculator = new PricingFeeCalculator();
        ReflectionTestUtils.setField(calculator, "pricingMarkupFeeMapper", mapper);
        return calculator;
    }

    private DictPricingMarkupFee pricingFee(Long id, String scope, String category, String name, String ratePercent, int sortNo) {
        DictPricingMarkupFee fee = new DictPricingMarkupFee();
        fee.setId(id);
        fee.setPricingScope(scope);
        fee.setFeeCategory(category);
        fee.setFeeName(name);
        fee.setRatePercent(new BigDecimal(ratePercent));
        fee.setSortNo(sortNo);
        fee.setEnabled(true);
        fee.setSystemRequired(false);
        return fee;
    }

    private DictExtraFeeItemMapper emptyExtraFeeMapper() {
        DictExtraFeeItemMapper mapper = mock(DictExtraFeeItemMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of());
        return mapper;
    }

    private static void setOptionalField(Object target, String fieldName, Object value) {
        try {
            ReflectionTestUtils.setField(target, fieldName, value);
        } catch (IllegalArgumentException ignored) {
            // Stage 6 RED path: field does not exist yet.
        }
    }

    private static Object getRequiredField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
