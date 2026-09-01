package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.PricingMaterialOptionDTO;
import com.machining.quote.dto.PricingOptionPageReq;
import com.machining.quote.dto.PricingProcessOptionDTO;
import com.machining.quote.entity.DictExtraFeeItem;
import com.machining.quote.entity.DictMaterial;
import com.machining.quote.entity.DictMaterialPriceSpec;
import com.machining.quote.entity.DictMaterialType;
import com.machining.quote.entity.DictProcessCategory;
import com.machining.quote.entity.DictProcessDetail;
import com.machining.quote.mapper.DictExtraFeeItemMapper;
import com.machining.quote.mapper.DictMaterialMapper;
import com.machining.quote.mapper.DictMaterialPriceSpecMapper;
import com.machining.quote.mapper.DictMaterialTypeMapper;
import com.machining.quote.mapper.DictProcessCategoryMapper;
import com.machining.quote.mapper.DictProcessDetailMapper;
import com.machining.quote.service.EngineeringPricingOptionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EngineeringPricingOptionServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void pageMaterialOptionsReturnsEnabledRowsWithTypeFieldsAndStrictPaging() {
        DictMaterialMapper materialMapper = mock(DictMaterialMapper.class);
        DictMaterialPriceSpecMapper specMapper = mock(DictMaterialPriceSpecMapper.class);
        DictMaterialTypeMapper materialTypeMapper = mock(DictMaterialTypeMapper.class);
        EngineeringPricingOptionService service = service(materialMapper, specMapper, materialTypeMapper,
                mock(DictProcessDetailMapper.class), mock(DictProcessCategoryMapper.class), mock(DictExtraFeeItemMapper.class));
        DictMaterial material = material(10L, "MAT-000010", "SUS304", 1L);
        material.setMaterialCategory("不锈钢");
        material.setDensity(7.93);
        material.setMesDensityRaw("7.92");
        material.setRemarks("raw remark");
        DictMaterialType type = materialType(1L, "RAW", "原材料");
        when(materialMapper.selectCount(any(QueryWrapper.class))).thenReturn(25L);
        when(materialMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(material));
        when(specMapper.selectList(any())).thenReturn(List.of(
                spec(100L, 10L, "SPEC_1", "规格一", 11.5, null, 12.2, 13.3, 10)));
        when(materialTypeMapper.selectBatchIds(any())).thenReturn(List.of(type));

        PricingOptionPageReq req = new PricingOptionPageReq();
        req.setKeyword("raw");
        req.setPage(2);
        req.setPageSize(20);

        PageResult<PricingMaterialOptionDTO> page = service.pageMaterialOptions(req);

        assertThat(page.getTotal()).isEqualTo(25);
        assertThat(page.getPage()).isEqualTo(2);
        assertThat(page.getPageSize()).isEqualTo(20);
        assertThat(page.getRecords()).singleElement().satisfies(row -> {
            assertThat(row.getId()).isEqualTo(10L);
            assertThat(row.getMaterialCode()).isEqualTo("MAT-000010");
            assertThat(row.getMaterialName()).isEqualTo("SUS304");
            assertThat(row.getMaterialTypeId()).isEqualTo(1L);
            assertThat(row.getMaterialTypeCode()).isEqualTo("RAW");
            assertThat(row.getMaterialTypeName()).isEqualTo("原材料");
            assertThat(row.getMaterialCategory()).isEqualTo("不锈钢");
            assertThat(row.getDensity()).isEqualTo(7.93);
            assertThat(row.getMesDensityRaw()).isEqualTo("7.92");
            assertThat(row.getPriceSpecs()).hasSize(3);
            assertThat(row.getPriceSpecs().get(0).getUnifiedPrice()).isEqualTo(11.5);
            assertThat(row.getPriceSpecs().get(0).getEffectivePlatePrice()).isEqualTo(11.5);
            assertThat(row.getPriceSpecs().get(0).getEffectiveBarPrice()).isEqualTo(12.2);
            assertThat(row.getPriceSpecs().get(0).getEffectiveTubePrice()).isEqualTo(13.3);
            assertThat(row.getRemarks()).isEqualTo("raw remark");
        });
        ArgumentCaptor<QueryWrapper<DictMaterial>> countCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        ArgumentCaptor<QueryWrapper<DictMaterial>> listCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(materialMapper).selectCount(countCaptor.capture());
        verify(materialMapper).selectList(listCaptor.capture());
        assertThat(sql(countCaptor.getValue()))
                .contains("enabled", "material_code", "material_name", "material_category", "remarks", "dict_material_type");
        assertThat(sql(listCaptor.getValue()))
                .contains("order by", "material_type_id", "sort_no", "id", "limit 20 offset 20");
    }

    @Test
    void pageMaterialOptionsDefaultsToTenRowsAndRejectsUnsupportedPageSize() {
        DictMaterialMapper materialMapper = mock(DictMaterialMapper.class);
        EngineeringPricingOptionService service = service(materialMapper, mock(DictMaterialPriceSpecMapper.class), mock(DictMaterialTypeMapper.class),
                mock(DictProcessDetailMapper.class), mock(DictProcessCategoryMapper.class), mock(DictExtraFeeItemMapper.class));
        when(materialMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);

        PageResult<PricingMaterialOptionDTO> defaulted = service.pageMaterialOptions(new PricingOptionPageReq());
        assertThat(defaulted.getPage()).isEqualTo(1);
        assertThat(defaulted.getPageSize()).isEqualTo(10);

        PricingOptionPageReq tenRows = new PricingOptionPageReq();
        tenRows.setPageSize(10);
        assertThat(service.pageMaterialOptions(tenRows).getPageSize()).isEqualTo(10);

        PricingOptionPageReq req = new PricingOptionPageReq();
        req.setPageSize(30);
        assertThatThrownBy(() -> service.pageMaterialOptions(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pageSize")
                .hasMessageContaining("10 / 20 / 50 / 100");
    }

    @Test
    @SuppressWarnings("unchecked")
    void pageMaterialOptionsCanHydrateByExactMaterialCodes() {
        DictMaterialMapper materialMapper = mock(DictMaterialMapper.class);
        DictMaterialPriceSpecMapper specMapper = mock(DictMaterialPriceSpecMapper.class);
        DictMaterialTypeMapper materialTypeMapper = mock(DictMaterialTypeMapper.class);
        EngineeringPricingOptionService service = service(materialMapper, specMapper, materialTypeMapper,
                mock(DictProcessDetailMapper.class), mock(DictProcessCategoryMapper.class), mock(DictExtraFeeItemMapper.class));
        DictMaterial material = material(10L, "MAT-000010", "SUS304", 1L);
        when(materialMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);
        when(materialMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(material));
        when(specMapper.selectList(any())).thenReturn(List.of());
        when(materialTypeMapper.selectBatchIds(any())).thenReturn(List.of(materialType(1L, "RAW", "原材料")));

        PricingOptionPageReq req = new PricingOptionPageReq();
        req.setMaterialCodes(" mat-000010 , mat-000011 ");
        req.setPageSize(100);

        PageResult<PricingMaterialOptionDTO> page = service.pageMaterialOptions(req);

        assertThat(page.getRecords()).singleElement()
                .extracting(PricingMaterialOptionDTO::getMaterialCode)
                .isEqualTo("MAT-000010");
        ArgumentCaptor<QueryWrapper<DictMaterial>> countCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(materialMapper).selectCount(countCaptor.capture());
        assertThat(sql(countCaptor.getValue()))
                .contains("enabled", "upper(material_code)", "in");
    }

    @Test
    @SuppressWarnings("unchecked")
    void pageMaterialOptionsSortsByPersistedMaterialUsageBeforeManualSort() {
        DictMaterialMapper materialMapper = mock(DictMaterialMapper.class);
        EngineeringPricingOptionService service = service(materialMapper, mock(DictMaterialPriceSpecMapper.class), mock(DictMaterialTypeMapper.class),
                mock(DictProcessDetailMapper.class), mock(DictProcessCategoryMapper.class), mock(DictExtraFeeItemMapper.class));
        when(materialMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);
        when(materialMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());

        service.pageMaterialOptions(new PricingOptionPageReq());

        ArgumentCaptor<QueryWrapper<DictMaterial>> listCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(materialMapper).selectList(listCaptor.capture());
        assertThat(sql(listCaptor.getValue()))
                .contains("pricing_option_usage_stat")
                .contains("option_type = 'material'")
                .contains("upper(material_code)")
                .contains("usage_count")
                .containsSubsequence("usage_count", "desc", "material_type_id", "sort_no", "id");
    }

    @Test
    @SuppressWarnings("unchecked")
    void pageProcessOptionsCombinesEnabledProcessDetailsAndEnabledExtraFees() {
        DictProcessDetailMapper detailMapper = mock(DictProcessDetailMapper.class);
        DictProcessCategoryMapper categoryMapper = mock(DictProcessCategoryMapper.class);
        DictExtraFeeItemMapper extraFeeItemMapper = mock(DictExtraFeeItemMapper.class);
        EngineeringPricingOptionService service = service(mock(DictMaterialMapper.class), mock(DictMaterialPriceSpecMapper.class), mock(DictMaterialTypeMapper.class),
                detailMapper, categoryMapper, extraFeeItemMapper);
        DictProcessCategory category = category(7L, "CNC", "数控加工", new BigDecimal("60.00"));
        DictProcessDetail detail = detail(10L, 7L, "CNC 精加工", new BigDecimal("80.00"));
        DictExtraFeeItem extra = extraFeeItem(5, "喷砂");
        when(detailMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);
        when(extraFeeItemMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);
        when(detailMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(detail));
        when(extraFeeItemMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(extra));
        when(categoryMapper.selectBatchIds(any())).thenReturn(List.of(category));

        PricingOptionPageReq req = new PricingOptionPageReq();
        req.setKeyword("cnc");
        req.setPage(1);
        req.setPageSize(20);

        PageResult<PricingProcessOptionDTO> page = service.pageProcessOptions(req);

        assertThat(page.getTotal()).isEqualTo(2);
        assertThat(page.getPage()).isEqualTo(1);
        assertThat(page.getPageSize()).isEqualTo(20);
        assertThat(page.getRecords()).hasSize(2);
        PricingProcessOptionDTO process = page.getRecords().get(0);
        assertThat(process.getOptionKey()).isEqualTo("process:10");
        assertThat(process.getSourceType()).isEqualTo("process");
        assertThat(process.getProcessDetailId()).isEqualTo(10L);
        assertThat(process.getProcessCategoryId()).isEqualTo(7L);
        assertThat(process.getProcessCode()).isEqualTo("CNC");
        assertThat(process.getCategoryName()).isEqualTo("数控加工");
        assertThat(process.getDetailName()).isEqualTo("CNC 精加工");
        assertThat(process.getProcessName()).isEqualTo("CNC 精加工");
        assertThat(process.getCalcType()).isEqualTo("工时");
        assertThat(process.getEffectiveHourlyRate()).isEqualByComparingTo("80.00");
        assertThat(process.getLabel()).contains("CNC 精加工", "CNC", "数控加工", "80.00元/小时");
        PricingProcessOptionDTO fee = page.getRecords().get(1);
        assertThat(fee.getOptionKey()).isEqualTo("extra:5");
        assertThat(fee.getSourceType()).isEqualTo("extra");
        assertThat(fee.getFeeItemId()).isEqualTo(5);
        assertThat(fee.getFeeName()).isEqualTo("喷砂");
        assertThat(fee.getProcessName()).isEqualTo("喷砂");
        assertThat(fee.getCalcType()).isEqualTo("非工时费用");

        ArgumentCaptor<QueryWrapper<DictProcessDetail>> detailCountCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        ArgumentCaptor<QueryWrapper<DictExtraFeeItem>> extraCountCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(detailMapper).selectCount(detailCountCaptor.capture());
        verify(extraFeeItemMapper).selectCount(extraCountCaptor.capture());
        assertThat(sql(detailCountCaptor.getValue()))
                .contains("enabled", "dict_process_category", "detail_name", "detail_remarks", "process_code", "category_name");
        assertThat(sql(extraCountCaptor.getValue()))
                .contains("enabled", "fee_name", "remarks");
    }

    @Test
    @SuppressWarnings("unchecked")
    void pageProcessOptionsSortsProcessCategoriesAndExtraFeesByPersistedUsage() {
        DictProcessDetailMapper detailMapper = mock(DictProcessDetailMapper.class);
        DictExtraFeeItemMapper extraFeeItemMapper = mock(DictExtraFeeItemMapper.class);
        EngineeringPricingOptionService service = service(mock(DictMaterialMapper.class), mock(DictMaterialPriceSpecMapper.class), mock(DictMaterialTypeMapper.class),
                detailMapper, mock(DictProcessCategoryMapper.class), extraFeeItemMapper);
        when(detailMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);
        when(extraFeeItemMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);
        when(detailMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        when(extraFeeItemMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());

        service.pageProcessOptions(new PricingOptionPageReq());

        ArgumentCaptor<QueryWrapper<DictProcessDetail>> detailListCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        ArgumentCaptor<QueryWrapper<DictExtraFeeItem>> extraListCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(detailMapper).selectList(detailListCaptor.capture());
        verify(extraFeeItemMapper).selectList(extraListCaptor.capture());
        assertThat(sql(detailListCaptor.getValue()))
                .contains("pricing_option_usage_stat")
                .contains("option_type = 'process_category'")
                .contains("process_code")
                .contains("usage_count")
                .containsSubsequence("usage_count", "desc", "category_id", "sort_no", "id");
        assertThat(sql(extraListCaptor.getValue()))
                .contains("pricing_option_usage_stat")
                .contains("option_type = 'extra_fee'")
                .contains("fee_code")
                .contains("usage_count")
                .containsSubsequence("usage_count", "desc", "sort_no", "id");
    }

    @Test
    @SuppressWarnings("unchecked")
    void pageProcessOptionsCanHydrateByExactProcessDetailIdsWithoutExtraFees() {
        DictProcessDetailMapper detailMapper = mock(DictProcessDetailMapper.class);
        DictProcessCategoryMapper categoryMapper = mock(DictProcessCategoryMapper.class);
        DictExtraFeeItemMapper extraFeeItemMapper = mock(DictExtraFeeItemMapper.class);
        EngineeringPricingOptionService service = service(mock(DictMaterialMapper.class), mock(DictMaterialPriceSpecMapper.class), mock(DictMaterialTypeMapper.class),
                detailMapper, categoryMapper, extraFeeItemMapper);
        DictProcessCategory category = category(7L, "MTA_PICK", "下料", new BigDecimal("60.00"));
        DictProcessDetail detail = detail(10L, 7L, "激光下料", new BigDecimal("200.00"));
        when(detailMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);
        when(detailMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(detail));
        when(categoryMapper.selectBatchIds(any())).thenReturn(List.of(category));

        PricingOptionPageReq req = new PricingOptionPageReq();
        req.setProcessDetailIds("10, 11");
        req.setPageSize(100);

        PageResult<PricingProcessOptionDTO> page = service.pageProcessOptions(req);

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords()).singleElement().satisfies(row -> {
            assertThat(row.getProcessDetailId()).isEqualTo(10L);
            assertThat(row.getHourlyRate()).isEqualByComparingTo("200.00");
        });
        ArgumentCaptor<QueryWrapper<DictProcessDetail>> detailCountCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(detailMapper).selectCount(detailCountCaptor.capture());
        assertThat(sql(detailCountCaptor.getValue()))
                .contains("enabled", "dict_process_category", "id in");
        verifyNoInteractions(extraFeeItemMapper);
    }

    @Test
    @SuppressWarnings("unchecked")
    void exactProcessHydrationDoesNotFallbackToExtraFeesWhenPageIsOutOfRange() {
        DictProcessDetailMapper detailMapper = mock(DictProcessDetailMapper.class);
        DictExtraFeeItemMapper extraFeeItemMapper = mock(DictExtraFeeItemMapper.class);
        EngineeringPricingOptionService service = service(mock(DictMaterialMapper.class), mock(DictMaterialPriceSpecMapper.class), mock(DictMaterialTypeMapper.class),
                detailMapper, mock(DictProcessCategoryMapper.class), extraFeeItemMapper);
        when(detailMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);

        PricingOptionPageReq req = new PricingOptionPageReq();
        req.setProcessDetailIds("10");
        req.setPage(2);
        req.setPageSize(10);

        PageResult<PricingProcessOptionDTO> page = service.pageProcessOptions(req);

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords()).isEmpty();
        verify(detailMapper).selectCount(any(QueryWrapper.class));
        verifyNoInteractions(extraFeeItemMapper);
    }

    @Test
    void optionServiceDoesNotDependOnAuditService() throws Exception {
        Path path = Path.of("src", "main", "java", "com", "machining", "quote", "service", "EngineeringPricingOptionService.java");
        assertThat(path).exists();
        String source = Files.readString(path, StandardCharsets.UTF_8);
        assertThat(source)
                .doesNotContain("AuditService")
                .doesNotContain(".record(");
    }

    private static EngineeringPricingOptionService service(
            DictMaterialMapper materialMapper,
            DictMaterialPriceSpecMapper materialPriceSpecMapper,
            DictMaterialTypeMapper materialTypeMapper,
            DictProcessDetailMapper detailMapper,
            DictProcessCategoryMapper categoryMapper,
            DictExtraFeeItemMapper extraFeeItemMapper) {
        EngineeringPricingOptionService service = new EngineeringPricingOptionService();
        ReflectionTestUtils.setField(service, "materialMapper", materialMapper);
        ReflectionTestUtils.setField(service, "materialPriceSpecMapper", materialPriceSpecMapper);
        ReflectionTestUtils.setField(service, "materialTypeMapper", materialTypeMapper);
        ReflectionTestUtils.setField(service, "processDetailMapper", detailMapper);
        ReflectionTestUtils.setField(service, "processCategoryMapper", categoryMapper);
        ReflectionTestUtils.setField(service, "extraFeeItemMapper", extraFeeItemMapper);
        return service;
    }

    private static DictMaterial material(Long id, String code, String name, Long typeId) {
        DictMaterial material = new DictMaterial();
        material.setId(id);
        material.setMaterialCode(code);
        material.setMaterialName(name);
        material.setMaterialTypeId(typeId);
        material.setEnabled(true);
        material.setSortNo(1);
        return material;
    }

    private static DictMaterialPriceSpec spec(Long id, Long materialId, String code, String name,
                                              Double unifiedPrice, Double platePrice, Double barPrice,
                                              Double tubePrice, int sortNo) {
        DictMaterialPriceSpec spec = new DictMaterialPriceSpec();
        spec.setId(id);
        spec.setMaterialId(materialId);
        spec.setSpecCode(code);
        spec.setSpecName(name);
        spec.setUnifiedPrice(unifiedPrice);
        spec.setPlatePrice(platePrice);
        spec.setBarPrice(barPrice);
        spec.setTubePrice(tubePrice);
        spec.setSortNo(sortNo);
        return spec;
    }

    private static DictMaterialType materialType(Long id, String code, String name) {
        DictMaterialType type = new DictMaterialType();
        type.setId(id);
        type.setTypeCode(code);
        type.setTypeName(name);
        type.setEnabled(true);
        return type;
    }

    private static DictProcessCategory category(Long id, String code, String name, BigDecimal hourlyRate) {
        DictProcessCategory category = new DictProcessCategory();
        category.setId(id);
        category.setProcessCode(code);
        category.setCategoryName(name);
        category.setHourlyRate(hourlyRate);
        category.setEnabled(true);
        category.setSortNo(1);
        return category;
    }

    private static DictProcessDetail detail(Long id, Long categoryId, String name, BigDecimal hourlyRate) {
        DictProcessDetail detail = new DictProcessDetail();
        detail.setId(id);
        detail.setCategoryId(categoryId);
        detail.setDetailName(name);
        detail.setHourlyRate(hourlyRate);
        detail.setEnabled(true);
        detail.setSortNo(2);
        return detail;
    }

    private static DictExtraFeeItem extraFeeItem(Integer id, String name) {
        DictExtraFeeItem item = new DictExtraFeeItem();
        item.setId(id);
        item.setFeeName(name);
        item.setEnabled(true);
        item.setSortNo(3);
        item.setRemarks("fee remark");
        return item;
    }

    private static String sql(QueryWrapper<?> wrapper) {
        return wrapper.getCustomSqlSegment().replaceAll("\\s+", " ").toLowerCase();
    }
}
