package com.machining.quote;

import com.machining.quote.entity.DictMaterial;
import com.machining.quote.mapper.DictMaterialMapper;
import com.machining.quote.service.EngineerService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EngineerServiceMaterialCalculationTests {

    @Test
    void calculatesPlateWeightFromMetricDimensionsAndDensity() {
        EngineerService service = new EngineerService();
        DictMaterialMapper materialMapper = mock(DictMaterialMapper.class);
        when(materialMapper.selectList(any())).thenReturn(List.of(material("45#", 7850.0, 7.1)));
        ReflectionTestUtils.setField(service, "materialMapper", materialMapper);

        Map<String, Object> materialData = new LinkedHashMap<>();
        materialData.put("name", "45#");
        materialData.put("shape", "板材");
        materialData.put("dimensionUnit", "m");
        materialData.put("dimensions", Map.of("L", 0.2, "W", 0.1, "T", 0.01));

        Double cost = ReflectionTestUtils.invokeMethod(service, "calculateMaterialCost", materialData);

        assertThat((Double) materialData.get("volume")).isCloseTo(0.0002, within(0.000000001));
        assertThat((Double) materialData.get("weight")).isCloseTo(1.57, within(0.000001));
        assertThat(cost).isCloseTo(11.147, within(0.000001));
        assertThat(materialData.get("densityUnit")).isEqualTo("kg/m3");
        assertThat(materialData.get("unitPriceUnit")).isEqualTo("元/kg");
    }

    @Test
    void keepsLegacySubmittedWeightWhenDimensionUnitIsMissing() {
        EngineerService service = new EngineerService();
        DictMaterialMapper materialMapper = mock(DictMaterialMapper.class);
        when(materialMapper.selectList(any())).thenReturn(List.of(material("45#", 7850.0, 7.1)));
        ReflectionTestUtils.setField(service, "materialMapper", materialMapper);

        Map<String, Object> materialData = new LinkedHashMap<>();
        materialData.put("name", "45#");
        materialData.put("shape", "板材");
        materialData.put("weight", 1.57);
        materialData.put("dimensions", Map.of("L", 200, "W", 100, "T", 10));

        Double cost = ReflectionTestUtils.invokeMethod(service, "calculateMaterialCost", materialData);

        assertThat(materialData).doesNotContainKey("volume");
        assertThat((Double) materialData.get("weight")).isEqualTo(1.57);
        assertThat(cost).isCloseTo(11.147, within(0.000001));
    }

    private DictMaterial material(String name, double density, double unitPrice) {
        DictMaterial material = new DictMaterial();
        material.setMaterialName(name);
        material.setDensity(density);
        material.setPlateDensity(density);
        material.setPlatePrice(unitPrice);
        return material;
    }
}
