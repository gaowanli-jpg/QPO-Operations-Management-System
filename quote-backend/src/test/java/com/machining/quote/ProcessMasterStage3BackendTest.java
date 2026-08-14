package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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

class ProcessMasterStage3BackendTest {

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
                        "private String detailName;",
                        "private Boolean enabled;");
        assertThat(read("mapper/DictProcessCategoryMapper.java"))
                .contains("interface DictProcessCategoryMapper extends BaseMapper<DictProcessCategory>");
        assertThat(read("mapper/DictProcessDetailMapper.java"))
                .contains("interface DictProcessDetailMapper extends BaseMapper<DictProcessDetail>");
        assertThat(read("dto/ProcessCategorySaveReq.java"))
                .contains("private String processCode;", "private String categoryName;", "private BigDecimal hourlyRate;");
        assertThat(read("dto/ProcessDetailSaveReq.java"))
                .contains("private Long categoryId;", "private String detailName;");

        assertThat(AuditService.inferModule("DICT_PROCESS_CATEGORY_CREATE")).isEqualTo("DICT");
        assertThat(AuditService.labelForAction("DICT_PROCESS_CATEGORY_CREATE")).contains("工艺大类");
        assertThat(AuditService.labelForAction("DICT_PROCESS_DETAIL_CREATE")).contains("具体工艺");
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
    void createDetailRequiresEnabledCategoryChecksNameUniquenessAndRecordsSummaryOnlyAudit() {
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
        assertThatThrownBy(() -> service.saveDetail(null, underDisabled))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已停用");
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
        localMaintenance.setEnabled(false);
        localMaintenance.setSortNo(20);

        var saved = service.saveDetail(99L, localMaintenance);

        assertThat(saved.getCategoryId()).isEqualTo(7L);
        assertThat(saved.getDetailName()).isEqualTo("MES detail");
        assertThat(saved.getDetailRemarks()).isEqualTo("QPO local remark");
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
