package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.ProcessCategoryDTO;
import com.machining.quote.dto.ProcessDetailDTO;
import com.machining.quote.dto.ProcessCategorySaveReq;
import com.machining.quote.dto.ProcessDetailSaveReq;
import com.machining.quote.entity.DictProcessCategory;
import com.machining.quote.entity.DictProcessDetail;
import com.machining.quote.mapper.DictProcessCategoryMapper;
import com.machining.quote.mapper.DictProcessDetailMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.ProcessMasterService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessMasterBackendServiceTest {

    @Test
    void controllerExposesTwoLevelProcessMasterEndpointsWithExistingDictPermissions() throws Exception {
        String controller = read("controller/ProcessMasterController.java");

        assertThat(controller)
                .contains(
                        "@RequestMapping(\"/api/dict\")",
                        "@GetMapping(\"/process-categories\")",
                        "@PostMapping(\"/process-categories\")",
                        "@PutMapping(\"/process-categories/{id}\")",
                        "@GetMapping(\"/process-details\")",
                        "@PostMapping(\"/process-details\")",
                        "@PutMapping(\"/process-details/{id}\")",
                        "@DeleteMapping(\"/process-details/{id}\")",
                        "@RequirePermission(\"engineer:dict:view\")",
                        "@RequirePermission(\"engineer:dict:process-manage\")");
        assertThat(controller)
                .as("Plain reads must not write operation logs; writes are logged inside the service")
                .doesNotContain("AuditService");
    }

    @Test
    void entitiesMappersDtosAndAuditLabelsExistForTwoLevelProcessMaster() throws Exception {
        assertThat(read("entity/DictProcessCategory.java"))
                .contains(
                        "@TableName(\"dict_process_category\")",
                        "private Long id;",
                        "private String processCode;",
                        "private String categoryName;",
                        "private BigDecimal hourlyRate;",
                        "private Boolean enabled;");
        assertThat(read("entity/DictProcessDetail.java"))
                .contains(
                        "@TableName(\"dict_process_detail\")",
                        "private Long id;",
                        "private Long categoryId;",
                        "private BigDecimal hourlyRate;",
                        "private String detailName;",
                        "private Boolean enabled;");
        assertThat(read("mapper/DictProcessCategoryMapper.java"))
                .contains("interface DictProcessCategoryMapper extends BaseMapper<DictProcessCategory>");
        assertThat(read("mapper/DictProcessDetailMapper.java"))
                .contains("interface DictProcessDetailMapper extends BaseMapper<DictProcessDetail>");
        assertThat(read("dto/ProcessCategorySaveReq.java"))
                .contains("private String processCode;", "private String categoryName;", "private BigDecimal hourlyRate;");
        assertThat(read("dto/ProcessDetailSaveReq.java"))
                .contains("private Long categoryId;", "private String detailName;", "private BigDecimal hourlyRate;");
        assertThat(read("dto/ProcessDetailDTO.java"))
                .contains(
                        "private BigDecimal hourlyRate;",
                        "private BigDecimal detailHourlyRate;",
                        "private BigDecimal categoryHourlyRate;",
                        "private BigDecimal effectiveHourlyRate;",
                        "private Boolean detailEnabled;",
                        "private Boolean categoryEnabled;",
                        "private Boolean effectiveEnabled;");

        assertThat(AuditService.inferModule("DICT_PROCESS_CATEGORY_CREATE")).isEqualTo("DICT");
        assertThat(AuditService.labelForAction("DICT_PROCESS_CATEGORY_CREATE")).contains("工艺大类");
        assertThat(AuditService.labelForAction("DICT_PROCESS_DETAIL_CREATE")).contains("具体工艺");
        assertThat(AuditService.labelForAction("DICT_PROCESS_DETAIL_DELETE")).contains("具体工艺");
    }

    @Test
    void createCategoryNormalizesValidatesUniquenessAndRecordsSummaryOnlyAudit() {
        DictProcessCategoryMapper categoryMapper = mock(DictProcessCategoryMapper.class);
        AuditService auditService = mock(AuditService.class);
        ProcessMasterService service = service(categoryMapper, mock(DictProcessDetailMapper.class), auditService);
        when(categoryMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        doAnswer(invocation -> {
            DictProcessCategory entity = invocation.getArgument(0);
            entity.setId(88L);
            return 1;
        }).when(categoryMapper).insert(any(DictProcessCategory.class));

        ProcessCategorySaveReq req = new ProcessCategorySaveReq();
        req.setProcessCode(" packaging ");
        req.setCategoryName(" 包装 ");
        req.setHourlyRate(new BigDecimal("10.5000"));
        req.setRemarks("完整长备注不应进入审计摘要");

        var result = service.saveCategory(null, req);

        assertThat(result.getProcessCode()).isEqualTo("PACKAGING");
        assertThat(result.getCategoryName()).isEqualTo("包装");
        assertThat(result.getSourceType()).isEqualTo("LOCAL");
        assertThat(result.getEnabled()).isTrue();
        ArgumentCaptor<DictProcessCategory> entityCaptor = ArgumentCaptor.forClass(DictProcessCategory.class);
        verify(categoryMapper).insert(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getProcessCode()).isEqualTo("PACKAGING");
        ArgumentCaptor<Object> afterCaptor = ArgumentCaptor.forClass(Object.class);
        verify(auditService).record(eq("DICT_PROCESS_CATEGORY_CREATE"), eq("dict_process_category"), eq(88L), isNull(), afterCaptor.capture());
        assertThat(String.valueOf(afterCaptor.getValue()))
                .contains("processCode=PACKAGING", "categoryName=包装")
                .doesNotContain("完整长备注不应进入审计摘要");
    }

    @Test
    @SuppressWarnings("unchecked")
    void pageCategoriesSupportsServerSidePaginationAndKeywordSearchWithoutAuditLog() throws Exception {
        DictProcessCategoryMapper categoryMapper = mock(DictProcessCategoryMapper.class);
        AuditService auditService = mock(AuditService.class);
        ProcessMasterService service = service(categoryMapper, mock(DictProcessDetailMapper.class), auditService);
        DictProcessCategory category = category(7L, true);
        when(categoryMapper.selectCount(any(QueryWrapper.class))).thenReturn(31L);
        when(categoryMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(category));

        Method pageCategories = ProcessMasterService.class.getMethod(
                "pageCategories", Boolean.class, String.class, Integer.class, Integer.class);
        PageResult<ProcessCategoryDTO> page = (PageResult<ProcessCategoryDTO>) pageCategories.invoke(
                service, true, "pack", 2, 20);

        assertThat(page.getTotal()).isEqualTo(31);
        assertThat(page.getPage()).isEqualTo(2);
        assertThat(page.getPageSize()).isEqualTo(20);
        assertThat(page.getRecords()).singleElement().satisfies(row -> {
            assertThat(row.getProcessCode()).isEqualTo("PACKAGING");
            assertThat(row.getCategoryName()).isEqualTo("包装");
        });
        verify(auditService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void pageDetailsSupportsServerSidePaginationCategoryFilterAndKeywordSearchWithoutAuditLog() throws Exception {
        DictProcessCategoryMapper categoryMapper = mock(DictProcessCategoryMapper.class);
        DictProcessDetailMapper detailMapper = mock(DictProcessDetailMapper.class);
        AuditService auditService = mock(AuditService.class);
        ProcessMasterService service = service(categoryMapper, detailMapper, auditService);
        DictProcessCategory category = category(7L, true);
        DictProcessDetail detail = detail(10L, 7L, null);
        when(detailMapper.selectCount(any(QueryWrapper.class))).thenReturn(22L);
        when(detailMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(detail));
        when(categoryMapper.selectById(7L)).thenReturn(category);

        Method pageDetails = ProcessMasterService.class.getMethod(
                "pageDetails", Long.class, Boolean.class, String.class, Integer.class, Integer.class);
        PageResult<ProcessDetailDTO> page = (PageResult<ProcessDetailDTO>) pageDetails.invoke(
                service, 7L, true, "包装", 3, 10);

        assertThat(page.getTotal()).isEqualTo(22);
        assertThat(page.getPage()).isEqualTo(3);
        assertThat(page.getPageSize()).isEqualTo(10);
        assertThat(page.getRecords()).singleElement().satisfies(row -> {
            assertThat(row.getDetailName()).isEqualTo("具体工艺10");
            assertThat(row.getProcessCode()).isEqualTo("PACKAGING");
            assertThat(row.getCategoryName()).isEqualTo("包装");
        });
        verify(auditService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void categoryValidationRejectsDuplicateNegativeRateAndOversizedFields() {
        DictProcessCategoryMapper categoryMapper = mock(DictProcessCategoryMapper.class);
        ProcessMasterService service = service(categoryMapper, mock(DictProcessDetailMapper.class), mock(AuditService.class));

        ProcessCategorySaveReq duplicate = validCategoryReq();
        when(categoryMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);
        assertThatThrownBy(() -> service.saveCategory(null, duplicate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("工艺编码");

        ProcessCategorySaveReq negative = validCategoryReq();
        negative.setHourlyRate(new BigDecimal("-1"));
        when(categoryMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        assertThatThrownBy(() -> service.saveCategory(null, negative))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("小时价格");

        ProcessCategorySaveReq tooLong = validCategoryReq();
        tooLong.setProcessCode("A".repeat(65));
        assertThatThrownBy(() -> service.saveCategory(null, tooLong))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("工艺编码");
        verify(categoryMapper, never()).insert(any(DictProcessCategory.class));
    }

    @Test
    void mesSourcedProcessCategoryRejectsManualMaintenanceBecauseMesOwnsIt() {
        DictProcessCategoryMapper categoryMapper = mock(DictProcessCategoryMapper.class);
        ProcessMasterService service = service(categoryMapper, mock(DictProcessDetailMapper.class), mock(AuditService.class));
        DictProcessCategory before = category(7L, true);
        before.setSourceType("MES");
        before.setMesExternalId("mes-process-7");
        when(categoryMapper.selectById(7L)).thenReturn(before);

        ProcessCategorySaveReq req = validCategoryReq();
        req.setCategoryName("Manual renamed category");

        assertThatThrownBy(() -> service.saveCategory(7L, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MES process category");
        verify(categoryMapper, never()).updateById(any(DictProcessCategory.class));
    }

    @Test
    void createDetailChecksNameUniquenessAndRecordsSummaryOnlyAudit() {
        DictProcessCategoryMapper categoryMapper = mock(DictProcessCategoryMapper.class);
        DictProcessDetailMapper detailMapper = mock(DictProcessDetailMapper.class);
        AuditService auditService = mock(AuditService.class);
        ProcessMasterService service = service(categoryMapper, detailMapper, auditService);
        DictProcessCategory category = category(7L, true);
        when(categoryMapper.selectById(7L)).thenReturn(category);
        when(detailMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        doAnswer(invocation -> {
            DictProcessDetail entity = invocation.getArgument(0);
            entity.setId(99L);
            return 1;
        }).when(detailMapper).insert(any(DictProcessDetail.class));

        ProcessDetailSaveReq req = new ProcessDetailSaveReq();
        req.setCategoryId(7L);
        req.setDetailName(" 精细包装 ");
        req.setDetailRemarks("完整具体备注不应进入审计摘要");

        var result = service.saveDetail(null, req);

        assertThat(result.getDetailName()).isEqualTo("精细包装");
        assertThat(result.getProcessCode()).isEqualTo("PACKAGING");
        assertThat(result.getCategoryName()).isEqualTo("包装");
        assertThat(result.getHourlyRate()).isEqualByComparingTo("10.0000");
        assertThat(result.getCategoryHourlyRate()).isEqualByComparingTo("10.0000");
        assertThat(result.getEffectiveHourlyRate()).isEqualByComparingTo("10.0000");
        assertThat(result.getDetailHourlyRate()).isNull();
        ArgumentCaptor<Object> afterCaptor = ArgumentCaptor.forClass(Object.class);
        verify(auditService).record(eq("DICT_PROCESS_DETAIL_CREATE"), eq("dict_process_detail"), eq(99L), isNull(), afterCaptor.capture());
        assertThat(String.valueOf(afterCaptor.getValue()))
                .contains("detailName=精细包装", "processCode=PACKAGING")
                .doesNotContain("完整具体备注不应进入审计摘要");

        DictProcessCategory disabled = category(8L, false);
        when(categoryMapper.selectById(8L)).thenReturn(disabled);
        ProcessDetailSaveReq underDisabled = new ProcessDetailSaveReq();
        underDisabled.setCategoryId(8L);
        underDisabled.setDetailName("不可启用");
        underDisabled.setEnabled(true);
        var disabledCategoryDetail = service.saveDetail(null, underDisabled);
        assertThat(disabledCategoryDetail.getDetailEnabled()).isTrue();
        assertThat(disabledCategoryDetail.getCategoryEnabled()).isFalse();
        assertThat(disabledCategoryDetail.getEffectiveEnabled()).isFalse();
    }

    @Test
    void createDetailAllowsCustomNamesUnderMesSourcedCategory() {
        DictProcessCategoryMapper categoryMapper = mock(DictProcessCategoryMapper.class);
        DictProcessDetailMapper detailMapper = mock(DictProcessDetailMapper.class);
        AuditService auditService = mock(AuditService.class);
        ProcessMasterService service = service(categoryMapper, detailMapper, auditService);
        DictProcessCategory category = category(7L, true);
        category.setSourceType("MES");
        when(categoryMapper.selectById(7L)).thenReturn(category);
        when(detailMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        doAnswer(invocation -> {
            DictProcessDetail entity = invocation.getArgument(0);
            entity.setId(100L);
            return 1;
        }).when(detailMapper).insert(any(DictProcessDetail.class));

        ProcessDetailSaveReq req = validDetailReq();
        req.setDetailName("精细包装-客户A");

        ProcessDetailDTO result = service.saveDetail(null, req);

        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getCategoryId()).isEqualTo(7L);
        assertThat(result.getDetailName()).isEqualTo("精细包装-客户A");
        verify(detailMapper).insert(any(DictProcessDetail.class));
        verify(auditService).record(eq("DICT_PROCESS_DETAIL_CREATE"), eq("dict_process_detail"), eq(100L), isNull(), any());
    }

    @Test
    void deleteDetailHardDeletesAnyDetailAndRecordsSummaryOnlyAudit() {
        DictProcessCategoryMapper categoryMapper = mock(DictProcessCategoryMapper.class);
        DictProcessDetailMapper detailMapper = mock(DictProcessDetailMapper.class);
        AuditService auditService = mock(AuditService.class);
        ProcessMasterService service = service(categoryMapper, detailMapper, auditService);
        DictProcessCategory category = category(7L, true);
        category.setSourceType("MES");
        DictProcessDetail before = detail(99L, 7L, new BigDecimal("120.00"));
        before.setDetailName("包装-临时客户名");
        before.setDetailRemarks("完整删除备注不应该进入审计摘要");
        when(detailMapper.selectById(99L)).thenReturn(before);
        when(categoryMapper.selectById(7L)).thenReturn(category);

        service.deleteDetail(99L);

        verify(detailMapper).deleteById(99L);
        ArgumentCaptor<Object> beforeCaptor = ArgumentCaptor.forClass(Object.class);
        verify(auditService).record(eq("DICT_PROCESS_DETAIL_DELETE"), eq("dict_process_detail"), eq(99L), beforeCaptor.capture(), isNull());
        assertThat(String.valueOf(beforeCaptor.getValue()))
                .contains("detailName=包装-临时客户名", "processCode=PACKAGING", "effectiveHourlyRate=120.00")
                .doesNotContain("完整删除备注不应该进入审计摘要");
    }

    @Test
    void detailHourlyRateOverridesCategoryRateOnlyWhenNonNullAndZeroIsValid() {
        DictProcessCategoryMapper categoryMapper = mock(DictProcessCategoryMapper.class);
        DictProcessDetailMapper detailMapper = mock(DictProcessDetailMapper.class);
        ProcessMasterService service = service(categoryMapper, detailMapper, mock(AuditService.class));
        DictProcessCategory category = category(7L, true);
        category.setHourlyRate(new BigDecimal("80.00"));
        when(categoryMapper.selectById(7L)).thenReturn(category);

        DictProcessDetail inherited = detail(1L, 7L, null);
        when(detailMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(inherited));
        var inheritedDto = service.listDetails(null, null).get(0);
        assertThat(inheritedDto.getHourlyRate()).isEqualByComparingTo("80.00");
        assertThat(inheritedDto.getEffectiveHourlyRate()).isEqualByComparingTo("80.00");
        assertThat(inheritedDto.getCategoryHourlyRate()).isEqualByComparingTo("80.00");
        assertThat(inheritedDto.getDetailHourlyRate()).isNull();

        DictProcessDetail zero = detail(2L, 7L, new BigDecimal("0.00"));
        when(detailMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(zero));
        var zeroDto = service.listDetails(null, null).get(0);
        assertThat(zeroDto.getHourlyRate()).isEqualByComparingTo("0.00");
        assertThat(zeroDto.getEffectiveHourlyRate()).isEqualByComparingTo("0.00");
        assertThat(zeroDto.getDetailHourlyRate()).isEqualByComparingTo("0.00");

        DictProcessDetail custom = detail(3L, 7L, new BigDecimal("125.50"));
        when(detailMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(custom));
        var customDto = service.listDetails(null, null).get(0);
        assertThat(customDto.getHourlyRate()).isEqualByComparingTo("125.50");
        assertThat(customDto.getEffectiveHourlyRate()).isEqualByComparingTo("125.50");
        assertThat(customDto.getDetailHourlyRate()).isEqualByComparingTo("125.50");
    }

    @Test
    void listDetailsExposesEffectiveAvailabilityAndEnabledOnlyFiltersDisabledCategories() {
        DictProcessCategoryMapper categoryMapper = mock(DictProcessCategoryMapper.class);
        DictProcessDetailMapper detailMapper = mock(DictProcessDetailMapper.class);
        ProcessMasterService service = service(categoryMapper, detailMapper, mock(AuditService.class));
        DictProcessCategory enabledCategory = category(7L, true);
        DictProcessCategory disabledCategory = category(8L, false);
        disabledCategory.setProcessCode("CNC");
        disabledCategory.setCategoryName("CNC");
        when(categoryMapper.selectById(7L)).thenReturn(enabledCategory);
        when(categoryMapper.selectById(8L)).thenReturn(disabledCategory);
        DictProcessDetail usableDetail = detail(1L, 7L, null);
        DictProcessDetail blockedByCategory = detail(2L, 8L, null);
        when(detailMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(usableDetail, blockedByCategory));

        var allDetails = service.listDetails(null, false);

        assertThat(allDetails).hasSize(2);
        assertThat(allDetails.get(0).getDetailEnabled()).isTrue();
        assertThat(allDetails.get(0).getCategoryEnabled()).isTrue();
        assertThat(allDetails.get(0).getEffectiveEnabled()).isTrue();
        assertThat(allDetails.get(1).getDetailEnabled()).isTrue();
        assertThat(allDetails.get(1).getCategoryEnabled()).isFalse();
        assertThat(allDetails.get(1).getEffectiveEnabled()).isFalse();

        var enabledOnlyDetails = service.listDetails(null, true);

        assertThat(enabledOnlyDetails).extracting("id").containsExactly(1L);
    }

    @Test
    void saveDetailAllowsNullAndZeroHourlyRateButRejectsNegativeRate() {
        DictProcessCategoryMapper categoryMapper = mock(DictProcessCategoryMapper.class);
        DictProcessDetailMapper detailMapper = mock(DictProcessDetailMapper.class);
        ProcessMasterService service = service(categoryMapper, detailMapper, mock(AuditService.class));
        when(categoryMapper.selectById(7L)).thenReturn(category(7L, true));
        when(detailMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);

        ProcessDetailSaveReq inherited = validDetailReq();
        inherited.setHourlyRate(null);
        assertThat(service.saveDetail(null, inherited).getDetailHourlyRate()).isNull();

        ProcessDetailSaveReq zero = validDetailReq();
        zero.setHourlyRate(BigDecimal.ZERO);
        var zeroDto = service.saveDetail(null, zero);
        assertThat(zeroDto.getDetailHourlyRate()).isEqualByComparingTo("0");
        assertThat(zeroDto.getHourlyRate()).isEqualByComparingTo("0");

        ProcessDetailSaveReq negative = validDetailReq();
        negative.setHourlyRate(new BigDecimal("-0.01"));
        assertThatThrownBy(() -> service.saveDetail(null, negative))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("具体小时价格");
    }

    @Test
    void mesProcessDetailRejectsIdentityChangesButAllowsLocalRemarkStateAndSortMaintenance() {
        DictProcessCategoryMapper categoryMapper = mock(DictProcessCategoryMapper.class);
        DictProcessDetailMapper detailMapper = mock(DictProcessDetailMapper.class);
        ProcessMasterService service = service(categoryMapper, detailMapper, mock(AuditService.class));
        DictProcessCategory category = category(7L, true);
        category.setSourceType("MES");
        DictProcessCategory anotherCategory = category(8L, true);
        anotherCategory.setSourceType("MES");
        when(categoryMapper.selectById(7L)).thenReturn(category);
        when(categoryMapper.selectById(8L)).thenReturn(anotherCategory);
        when(detailMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);

        DictProcessDetail before = new DictProcessDetail();
        before.setId(99L);
        before.setCategoryId(7L);
        before.setDetailName("MES detail");
        before.setEnabled(true);
        before.setSortNo(0);
        when(detailMapper.selectById(99L)).thenReturn(before);

        ProcessDetailSaveReq changedIdentity = new ProcessDetailSaveReq();
        changedIdentity.setCategoryId(8L);
        changedIdentity.setDetailName("Manual detail");
        assertThatThrownBy(() -> service.saveDetail(99L, changedIdentity))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MES process detail identity");
        verify(detailMapper, never()).updateById(any(DictProcessDetail.class));

        ProcessDetailSaveReq localMaintenance = new ProcessDetailSaveReq();
        localMaintenance.setCategoryId(7L);
        localMaintenance.setDetailName("MES detail");
        localMaintenance.setDetailRemarks("QPO local remark");
        localMaintenance.setHourlyRate(new BigDecimal("120.00"));
        localMaintenance.setEnabled(false);
        localMaintenance.setSortNo(20);

        var saved = service.saveDetail(99L, localMaintenance);

        assertThat(saved.getCategoryId()).isEqualTo(7L);
        assertThat(saved.getDetailName()).isEqualTo("MES detail");
        assertThat(saved.getDetailRemarks()).isEqualTo("QPO local remark");
        assertThat(saved.getDetailHourlyRate()).isEqualByComparingTo("120.00");
        assertThat(saved.getHourlyRate()).isEqualByComparingTo("120.00");
        assertThat(saved.getEnabled()).isFalse();
        assertThat(saved.getSortNo()).isEqualTo(20);
    }

    private static ProcessMasterService service(
            DictProcessCategoryMapper categoryMapper,
            DictProcessDetailMapper detailMapper,
            AuditService auditService) {
        ProcessMasterService service = new ProcessMasterService();
        ReflectionTestUtils.setField(service, "categoryMapper", categoryMapper);
        ReflectionTestUtils.setField(service, "detailMapper", detailMapper);
        ReflectionTestUtils.setField(service, "auditService", auditService);
        return service;
    }

    private static ProcessCategorySaveReq validCategoryReq() {
        ProcessCategorySaveReq req = new ProcessCategorySaveReq();
        req.setProcessCode("PACKAGING");
        req.setCategoryName("包装");
        req.setHourlyRate(new BigDecimal("10.0000"));
        return req;
    }

    private static ProcessDetailSaveReq validDetailReq() {
        ProcessDetailSaveReq req = new ProcessDetailSaveReq();
        req.setCategoryId(7L);
        req.setDetailName("精细包装");
        return req;
    }

    private static DictProcessDetail detail(Long id, Long categoryId, BigDecimal hourlyRate) {
        DictProcessDetail detail = new DictProcessDetail();
        detail.setId(id);
        detail.setCategoryId(categoryId);
        detail.setDetailName("具体工艺" + id);
        detail.setHourlyRate(hourlyRate);
        detail.setEnabled(true);
        detail.setSortNo(0);
        return detail;
    }

    private static DictProcessCategory category(Long id, boolean enabled) {
        DictProcessCategory category = new DictProcessCategory();
        category.setId(id);
        category.setProcessCode("PACKAGING");
        category.setCategoryName("包装");
        category.setHourlyRate(new BigDecimal("10.0000"));
        category.setEnabled(enabled);
        category.setSortNo(1);
        return category;
    }

    private static String read(String relativePath) throws Exception {
        Path path = Path.of("src", "main", "java", "com", "machining", "quote").resolve(relativePath);
        assertThat(path).exists();
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
