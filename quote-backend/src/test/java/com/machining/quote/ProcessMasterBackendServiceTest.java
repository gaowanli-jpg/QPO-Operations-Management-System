package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.ProcessCategoryDTO;
import com.machining.quote.dto.ProcessCategorySaveReq;
import com.machining.quote.dto.ProcessDetailDTO;
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

import java.lang.reflect.Method;
import java.math.BigDecimal;
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
    void controllerExposesProcessMasterEndpointsWithExistingDictPermissions() throws Exception {
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
    void entitiesMappersDtosAndAuditLabelsExposeQpoOwnedProcessDetailCode() throws Exception {
        assertThat(read("entity/DictProcessCategory.java"))
                .contains(
                        "@TableName(\"dict_process_category\")",
                        "private String processCode;",
                        "private String categoryName;",
                        "private BigDecimal hourlyRate;",
                        "private Boolean enabled;");
        assertThat(read("entity/DictProcessDetail.java"))
                .contains(
                        "@TableName(\"dict_process_detail\")",
                        "private Long id;",
                        "private Long categoryId;",
                        "private String processCode;",
                        "private BigDecimal hourlyRate;",
                        "private String detailName;",
                        "private Boolean enabled;");
        assertThat(read("dto/ProcessDetailSaveReq.java"))
                .contains("private String processCode;", "private String detailName;", "private BigDecimal hourlyRate;");
        assertThat(read("dto/ProcessDetailDTO.java"))
                .contains(
                        "private String processCode;",
                        "private BigDecimal hourlyRate;",
                        "private BigDecimal detailHourlyRate;",
                        "private BigDecimal effectiveHourlyRate;",
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
        when(categoryMapper.selectCount(any(QueryWrapper.class))).thenReturn(31L);
        when(categoryMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(category(7L, true)));

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
    void pageDetailsUsesQpoOwnedCodeSearchAndDetailEnabledOnly() throws Exception {
        DictProcessCategoryMapper categoryMapper = mock(DictProcessCategoryMapper.class);
        DictProcessDetailMapper detailMapper = mock(DictProcessDetailMapper.class);
        AuditService auditService = mock(AuditService.class);
        ProcessMasterService service = service(categoryMapper, detailMapper, auditService);
        DictProcessDetail detail = detail(10L, "pack_FINE", new BigDecimal("12.30"));
        when(detailMapper.selectCount(any(QueryWrapper.class))).thenReturn(22L);
        when(detailMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(detail));

        Method pageDetails = ProcessMasterService.class.getMethod(
                "pageDetails", Long.class, Boolean.class, String.class, Integer.class, Integer.class);
        PageResult<ProcessDetailDTO> page = (PageResult<ProcessDetailDTO>) pageDetails.invoke(
                service, null, true, "pack", 3, 10);

        assertThat(page.getTotal()).isEqualTo(22);
        assertThat(page.getPage()).isEqualTo(3);
        assertThat(page.getPageSize()).isEqualTo(10);
        assertThat(page.getRecords()).singleElement().satisfies(row -> {
            assertThat(row.getProcessCode()).isEqualTo("pack_FINE");
            assertThat(row.getDetailName()).isEqualTo("具体工艺10");
            assertThat(row.getHourlyRate()).isEqualByComparingTo("12.30");
            assertThat(row.getEffectiveHourlyRate()).isEqualByComparingTo("12.30");
            assertThat(row.getDetailHourlyRate()).isEqualByComparingTo("12.30");
            assertThat(row.getCategoryHourlyRate()).isNull();
            assertThat(row.getCategoryEnabled()).isNull();
            assertThat(row.getEffectiveEnabled()).isTrue();
        });
        ArgumentCaptor<QueryWrapper<DictProcessDetail>> queryCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(detailMapper).selectCount(queryCaptor.capture());
        String sql = queryCaptor.getValue().getTargetSql().toLowerCase();
        assertThat(sql).contains("process_code", "detail_name", "detail_remarks");
        assertThat(sql).doesNotContain("dict_process_category");
        verify(auditService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void createDetailRequiresQpoOwnedCodeAndHourlyRateWithoutCategory() {
        DictProcessDetailMapper detailMapper = mock(DictProcessDetailMapper.class);
        AuditService auditService = mock(AuditService.class);
        ProcessMasterService service = service(mock(DictProcessCategoryMapper.class), detailMapper, auditService);
        when(detailMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        doAnswer(invocation -> {
            DictProcessDetail entity = invocation.getArgument(0);
            entity.setId(99L);
            return 1;
        }).when(detailMapper).insert(any(DictProcessDetail.class));

        ProcessDetailSaveReq req = new ProcessDetailSaveReq();
        req.setCategoryId(88L);
        req.setProcessCode(" pack-fine ");
        req.setDetailName(" 精细包装 ");
        req.setHourlyRate(BigDecimal.ZERO);
        req.setDetailRemarks("完整具体备注不应进入审计摘要");

        var result = service.saveDetail(null, req);

        assertThat(result.getProcessCode()).isEqualTo("PACK-FINE");
        assertThat(result.getCategoryId()).isNull();
        assertThat(result.getDetailName()).isEqualTo("精细包装");
        assertThat(result.getHourlyRate()).isEqualByComparingTo("0");
        assertThat(result.getDetailHourlyRate()).isEqualByComparingTo("0");
        assertThat(result.getEffectiveHourlyRate()).isEqualByComparingTo("0");
        assertThat(result.getEffectiveEnabled()).isTrue();
        ArgumentCaptor<DictProcessDetail> entityCaptor = ArgumentCaptor.forClass(DictProcessDetail.class);
        verify(detailMapper).insert(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getProcessCode()).isEqualTo("PACK-FINE");
        assertThat(entityCaptor.getValue().getCategoryId()).isNull();
        ArgumentCaptor<Object> afterCaptor = ArgumentCaptor.forClass(Object.class);
        verify(auditService).record(eq("DICT_PROCESS_DETAIL_CREATE"), eq("dict_process_detail"), eq(99L), isNull(), afterCaptor.capture());
        assertThat(String.valueOf(afterCaptor.getValue()))
                .contains("processCode=PACK-FINE", "detailName=精细包装", "hourlyRate=0")
                .doesNotContain("完整具体备注不应进入审计摘要");
    }

    @Test
    void detailValidationRejectsMissingOrDuplicateCodeMutableCodeAndMissingHourlyRate() {
        DictProcessDetailMapper detailMapper = mock(DictProcessDetailMapper.class);
        ProcessMasterService service = service(mock(DictProcessCategoryMapper.class), detailMapper, mock(AuditService.class));

        ProcessDetailSaveReq missingCode = validDetailReq();
        missingCode.setProcessCode(" ");
        assertThatThrownBy(() -> service.saveDetail(null, missingCode))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("工艺编码");

        ProcessDetailSaveReq invalidCode = validDetailReq();
        invalidCode.setProcessCode("cnc.1");
        assertThatThrownBy(() -> service.saveDetail(null, invalidCode))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("工艺编码只允许");

        ProcessDetailSaveReq missingRate = validDetailReq();
        missingRate.setHourlyRate(null);
        assertThatThrownBy(() -> service.saveDetail(null, missingRate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("具体小时价格不能为空");

        ProcessDetailSaveReq negative = validDetailReq();
        negative.setHourlyRate(new BigDecimal("-0.01"));
        assertThatThrownBy(() -> service.saveDetail(null, negative))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("具体小时价格不能小于 0");

        when(detailMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);
        ProcessDetailSaveReq duplicate = validDetailReq();
        assertThatThrownBy(() -> service.saveDetail(null, duplicate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("工艺编码已存在");

        DictProcessDetail before = detail(88L, "CNC", BigDecimal.TEN);
        when(detailMapper.selectById(88L)).thenReturn(before);
        ProcessDetailSaveReq changedCode = validDetailReq();
        changedCode.setProcessCode("MILL");
        assertThatThrownBy(() -> service.saveDetail(88L, changedCode))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("工艺编码是具体工艺稳定身份");
        verify(detailMapper, never()).updateById(any(DictProcessDetail.class));
    }

    @Test
    void disabledCategoryDoesNotAffectSpecificProcessAvailability() {
        DictProcessCategoryMapper categoryMapper = mock(DictProcessCategoryMapper.class);
        DictProcessDetailMapper detailMapper = mock(DictProcessDetailMapper.class);
        ProcessMasterService service = service(categoryMapper, detailMapper, mock(AuditService.class));
        DictProcessCategory disabledCategory = category(8L, false);
        when(categoryMapper.selectById(8L)).thenReturn(disabledCategory);
        DictProcessDetail detail = detail(2L, "CNC_FINE", new BigDecimal("80.00"));
        detail.setCategoryId(8L);
        when(detailMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(detail));

        var enabledOnlyDetails = service.listDetails(null, true);

        assertThat(enabledOnlyDetails).singleElement().satisfies(row -> {
            assertThat(row.getProcessCode()).isEqualTo("CNC_FINE");
            assertThat(row.getDetailEnabled()).isTrue();
            assertThat(row.getCategoryEnabled()).isNull();
            assertThat(row.getEffectiveEnabled()).isTrue();
        });
    }

    @Test
    void deleteDetailHardDeletesQpoOwnedDetailAndRecordsSummaryOnlyAudit() {
        DictProcessDetailMapper detailMapper = mock(DictProcessDetailMapper.class);
        AuditService auditService = mock(AuditService.class);
        ProcessMasterService service = service(mock(DictProcessCategoryMapper.class), detailMapper, auditService);
        DictProcessDetail before = detail(99L, "PACK_FINE", new BigDecimal("120.00"));
        before.setDetailName("包装-临时客户名");
        before.setDetailRemarks("完整删除备注不应该进入审计摘要");
        when(detailMapper.selectById(99L)).thenReturn(before);

        service.deleteDetail(99L);

        verify(detailMapper).deleteById(99L);
        ArgumentCaptor<Object> beforeCaptor = ArgumentCaptor.forClass(Object.class);
        verify(auditService).record(eq("DICT_PROCESS_DETAIL_DELETE"), eq("dict_process_detail"), eq(99L), beforeCaptor.capture(), isNull());
        assertThat(String.valueOf(beforeCaptor.getValue()))
                .contains("detailName=包装-临时客户名", "processCode=PACK_FINE", "hourlyRate=120.00")
                .doesNotContain("完整删除备注不应该进入审计摘要");
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

    private static ProcessDetailSaveReq validDetailReq() {
        ProcessDetailSaveReq req = new ProcessDetailSaveReq();
        req.setProcessCode("CNC");
        req.setDetailName("精细包装");
        req.setHourlyRate(BigDecimal.TEN);
        return req;
    }

    private static DictProcessDetail detail(Long id, String processCode, BigDecimal hourlyRate) {
        DictProcessDetail detail = new DictProcessDetail();
        detail.setId(id);
        detail.setProcessCode(processCode);
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
