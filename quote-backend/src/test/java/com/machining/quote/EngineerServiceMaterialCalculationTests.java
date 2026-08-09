package com.machining.quote;

import com.machining.quote.entity.DictMaterial;
import com.machining.quote.entity.DictMaterialType;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.mapper.DictExtraFeeItemMapper;
import com.machining.quote.mapper.DictMarkupFeeMapper;
import com.machining.quote.mapper.DictMaterialMapper;
import com.machining.quote.mapper.DictMaterialTypeMapper;
import com.machining.quote.mapper.ItemAssemblyRelationMapper;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.service.EngineerService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
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
        ReflectionTestUtils.setField(service, "markupFeeMapper", emptyMarkupMapper());
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

    private DictMaterial material(String code, String name, Long typeId, String category, double density, double unitPrice) {
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

    private DictMarkupFeeMapper emptyMarkupMapper() {
        DictMarkupFeeMapper mapper = mock(DictMarkupFeeMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of());
        return mapper;
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
