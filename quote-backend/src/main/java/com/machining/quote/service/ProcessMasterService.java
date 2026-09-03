package com.machining.quote.service;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ProcessMasterService {
    private static final String SOURCE_LOCAL = "LOCAL";
    private static final String SOURCE_MES = "MES";

    @Autowired
    private DictProcessCategoryMapper categoryMapper;
    @Autowired
    private DictProcessDetailMapper detailMapper;
    @Autowired
    private AuditService auditService;

    public List<ProcessCategoryDTO> listCategories(Boolean enabledOnly) {
        QueryWrapper<DictProcessCategory> qw = new QueryWrapper<>();
        applyCategoryFilters(qw, enabledOnly, null);
        qw.orderByAsc("sort_no").orderByAsc("id");
        return categoryMapper.selectList(qw).stream().map(this::toCategoryDTO).toList();
    }

    public PageResult<ProcessCategoryDTO> pageCategories(Boolean enabledOnly, String keyword, Integer page, Integer pageSize) {
        int safePage = normalizePage(page);
        int safePageSize = normalizePageSize(pageSize);
        QueryWrapper<DictProcessCategory> qw = new QueryWrapper<>();
        applyCategoryFilters(qw, enabledOnly, keyword);
        Long total = categoryMapper.selectCount(qw);
        if (total == null || total == 0) {
            return PageResult.of(List.of(), 0, safePage, safePageSize);
        }
        qw.orderByAsc("sort_no").orderByAsc("id");
        qw.last("LIMIT " + safePageSize + " OFFSET " + ((safePage - 1L) * safePageSize));
        return PageResult.of(categoryMapper.selectList(qw).stream().map(this::toCategoryDTO).toList(), total, safePage, safePageSize);
    }

    public List<ProcessDetailDTO> listDetails(Long categoryId, Boolean enabledOnly) {
        QueryWrapper<DictProcessDetail> qw = new QueryWrapper<>();
        applyDetailFilters(qw, categoryId, enabledOnly, null);
        qw.orderByAsc("sort_no").orderByAsc("id");
        return detailMapper.selectList(qw).stream().map(this::toDetailDTO).toList();
    }

    public PageResult<ProcessDetailDTO> pageDetails(Long categoryId, Boolean enabledOnly, String keyword, Integer page, Integer pageSize) {
        int safePage = normalizePage(page);
        int safePageSize = normalizePageSize(pageSize);
        QueryWrapper<DictProcessDetail> qw = new QueryWrapper<>();
        applyDetailFilters(qw, categoryId, enabledOnly, keyword);
        Long total = detailMapper.selectCount(qw);
        if (total == null || total == 0) {
            return PageResult.of(List.of(), 0, safePage, safePageSize);
        }
        qw.orderByAsc("sort_no").orderByAsc("id");
        qw.last("LIMIT " + safePageSize + " OFFSET " + ((safePage - 1L) * safePageSize));
        return PageResult.of(detailMapper.selectList(qw).stream().map(this::toDetailDTO).toList(), total, safePage, safePageSize);
    }

    private void applyCategoryFilters(QueryWrapper<DictProcessCategory> qw, Boolean enabledOnly, String keyword) {
        if (Boolean.TRUE.equals(enabledOnly)) {
            qw.eq("enabled", true);
        }
        if (!hasText(keyword)) {
            return;
        }
        String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
        qw.and(wrapper -> wrapper
                .apply("LOWER(process_code) LIKE {0}", pattern)
                .or()
                .apply("LOWER(category_name) LIKE {0}", pattern)
                .or()
                .apply("LOWER(source_type) LIKE {0}", pattern)
                .or()
                .apply("LOWER(remarks) LIKE {0}", pattern));
    }

    private void applyDetailFilters(QueryWrapper<DictProcessDetail> qw, Long categoryId, Boolean enabledOnly, String keyword) {
        if (categoryId != null) {
            qw.eq("category_id", categoryId);
        }
        if (Boolean.TRUE.equals(enabledOnly)) {
            qw.eq("enabled", true);
        }
        if (!hasText(keyword)) {
            return;
        }
        String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
        qw.and(wrapper -> wrapper
                .apply("LOWER(process_code) LIKE {0}", pattern)
                .or()
                .apply("LOWER(detail_name) LIKE {0}", pattern)
                .or()
                .apply("LOWER(detail_remarks) LIKE {0}", pattern));
    }

    @Transactional
    public ProcessCategoryDTO saveCategory(Long id, ProcessCategorySaveReq req) {
        DictProcessCategory before = id == null ? null : requireCategory(id);
        if (before != null && isMesSource(before.getSourceType())) {
            throw new IllegalArgumentException("MES process category cannot be maintained manually; sync MES master data instead");
        }
        DictProcessCategory entity = normalizeCategory(req);
        entity.setId(id);
        ensureCategoryCodeUnique(entity.getProcessCode(), id);
        if (id == null) {
            categoryMapper.insert(entity);
            auditService.record(
                    "DICT_PROCESS_CATEGORY_CREATE",
                    "dict_process_category",
                    entity.getId(),
                    null,
                    categoryAuditSummary(entity)
            );
        } else {
            categoryMapper.updateById(entity);
            auditService.record(
                    "DICT_PROCESS_CATEGORY_UPDATE",
                    "dict_process_category",
                    id,
                    categoryAuditSummary(before),
                    categoryAuditSummary(entity)
            );
        }
        return toCategoryDTO(entity);
    }

    @Transactional
    public ProcessDetailDTO saveDetail(Long id, ProcessDetailSaveReq req) {
        DictProcessDetail before = id == null ? null : requireDetail(id);
        DictProcessDetail entity = normalizeDetail(req, before);
        entity.setId(id);
        ensureDetailCodeUnique(entity.getProcessCode(), id);
        if (id == null) {
            detailMapper.insert(entity);
            auditService.record(
                    "DICT_PROCESS_DETAIL_CREATE",
                    "dict_process_detail",
                    entity.getId(),
                    null,
                    detailAuditSummary(entity)
            );
        } else {
            detailMapper.updateById(entity);
            auditService.record(
                    "DICT_PROCESS_DETAIL_UPDATE",
                    "dict_process_detail",
                    id,
                    detailAuditSummary(before),
                    detailAuditSummary(entity)
            );
        }
        return toDetailDTO(entity);
    }

    @Transactional
    public void deleteDetail(Long id) {
        DictProcessDetail before = requireDetail(id);
        detailMapper.deleteById(id);
        auditService.record(
                "DICT_PROCESS_DETAIL_DELETE",
                "dict_process_detail",
                id,
                detailAuditSummary(before),
                null
        );
    }

    private DictProcessCategory normalizeCategory(ProcessCategorySaveReq req) {
        if (req == null) {
            throw new IllegalArgumentException("工艺大类请求不能为空");
        }
        DictProcessCategory entity = new DictProcessCategory();
        entity.setProcessCode(upper(required(req.getProcessCode(), "工艺编码"), "工艺编码", 64));
        entity.setCategoryName(limit(required(req.getCategoryName(), "工艺名称"), "工艺名称", 120));
        BigDecimal hourlyRate = req.getHourlyRate();
        if (hourlyRate == null || hourlyRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("工艺小时价格不能小于 0");
        }
        entity.setHourlyRate(hourlyRate);
        entity.setRemarks(limit(optional(req.getRemarks()), "备注", 500));
        String sourceType = upper(optional(req.getSourceType()), "来源类型", 30);
        if (sourceType.isBlank()) {
            sourceType = SOURCE_LOCAL;
        }
        if (!SOURCE_LOCAL.equals(sourceType) && !SOURCE_MES.equals(sourceType)) {
            throw new IllegalArgumentException("来源类型只支持 LOCAL 或 MES");
        }
        entity.setSourceType(sourceType);
        entity.setMesExternalId(limit(optional(req.getMesExternalId()), "MES外部ID", 120));
        entity.setEnabled(req.getEnabled() == null || req.getEnabled());
        entity.setSortNo(req.getSortNo() == null ? 0 : req.getSortNo());
        return entity;
    }

    private DictProcessDetail normalizeDetail(ProcessDetailSaveReq req, DictProcessDetail before) {
        if (req == null) {
            throw new IllegalArgumentException("具体工艺请求不能为空");
        }
        DictProcessDetail entity = new DictProcessDetail();
        entity.setCategoryId(null);
        String requestedCode = normalizeProcessDetailCode(req.getProcessCode());
        String existingCode = before == null ? null : normalizeProcessDetailCode(before.getProcessCode());
        if (before != null && requestedCode.isBlank()) {
            requestedCode = existingCode;
        }
        if (requestedCode.isBlank()) {
            throw new IllegalArgumentException("工艺编码不能为空");
        }
        if (existingCode != null && !existingCode.isBlank() && !existingCode.equals(requestedCode)) {
            throw new IllegalArgumentException("工艺编码是具体工艺稳定身份，已有编码不允许修改");
        }
        entity.setProcessCode(existingCode == null || existingCode.isBlank() ? requestedCode : existingCode);

        BigDecimal hourlyRate = req.getHourlyRate();
        if (hourlyRate == null) {
            throw new IllegalArgumentException("具体小时价格不能为空");
        }
        if (hourlyRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("具体小时价格不能小于 0");
        }
        entity.setHourlyRate(hourlyRate);
        entity.setDetailName(limit(required(req.getDetailName(), "具体工艺名称"), "具体工艺名称", 120));
        entity.setDetailRemarks(limit(optional(req.getDetailRemarks()), "具体备注", 500));
        entity.setEnabled(req.getEnabled() == null ? before == null || Boolean.TRUE.equals(before.getEnabled()) : req.getEnabled());
        entity.setSortNo(req.getSortNo() == null ? before == null ? 0 : nullToZero(before.getSortNo()) : req.getSortNo());
        return entity;
    }

    private DictProcessCategory requireCategory(Long id) {
        DictProcessCategory category = categoryMapper.selectById(id);
        if (category == null) {
            throw new IllegalArgumentException("工艺大类不存在");
        }
        return category;
    }

    private DictProcessDetail requireDetail(Long id) {
        DictProcessDetail detail = detailMapper.selectById(id);
        if (detail == null) {
            throw new IllegalArgumentException("具体工艺不存在");
        }
        return detail;
    }

    private void ensureCategoryCodeUnique(String processCode, Long id) {
        QueryWrapper<DictProcessCategory> qw = new QueryWrapper<>();
        qw.eq("process_code", processCode);
        if (id != null) {
            qw.ne("id", id);
        }
        if (categoryMapper.selectCount(qw) > 0) {
            throw new IllegalArgumentException("工艺编码已存在");
        }
    }

    private void ensureDetailCodeUnique(String processCode, Long id) {
        QueryWrapper<DictProcessDetail> qw = new QueryWrapper<>();
        qw.eq("process_code", processCode);
        if (id != null) {
            qw.ne("id", id);
        }
        if (detailMapper.selectCount(qw) > 0) {
            throw new IllegalArgumentException("工艺编码已存在");
        }
    }

    private ProcessCategoryDTO toCategoryDTO(DictProcessCategory entity) {
        ProcessCategoryDTO dto = new ProcessCategoryDTO();
        dto.setId(entity.getId());
        dto.setProcessCode(entity.getProcessCode());
        dto.setCategoryName(entity.getCategoryName());
        dto.setHourlyRate(entity.getHourlyRate());
        dto.setRemarks(entity.getRemarks());
        dto.setSourceType(entity.getSourceType());
        dto.setMesExternalId(entity.getMesExternalId());
        dto.setEnabled(entity.getEnabled());
        dto.setSortNo(entity.getSortNo());
        dto.setMesSyncedAt(entity.getMesSyncedAt());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    private ProcessDetailDTO toDetailDTO(DictProcessDetail entity) {
        ProcessDetailDTO dto = new ProcessDetailDTO();
        dto.setId(entity.getId());
        dto.setCategoryId(entity.getCategoryId());
        dto.setProcessCode(entity.getProcessCode());
        dto.setCategoryName(null);
        dto.setDetailName(entity.getDetailName());
        dto.setDetailRemarks(entity.getDetailRemarks());
        dto.setEnabled(entity.getEnabled());
        dto.setDetailEnabled(entity.getEnabled());
        dto.setCategoryEnabled(null);
        dto.setEffectiveEnabled(Boolean.TRUE.equals(entity.getEnabled()));
        dto.setSortNo(entity.getSortNo());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setCategoryHourlyRate(null);
        dto.setDetailHourlyRate(entity.getHourlyRate());
        dto.setEffectiveHourlyRate(entity.getHourlyRate());
        dto.setHourlyRate(entity.getHourlyRate());
        return dto;
    }

    private Map<String, Object> categoryAuditSummary(DictProcessCategory entity) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", entity.getId());
        summary.put("processCode", entity.getProcessCode());
        summary.put("categoryName", entity.getCategoryName());
        summary.put("hourlyRate", entity.getHourlyRate());
        summary.put("enabled", entity.getEnabled());
        return summary;
    }

    private Map<String, Object> detailAuditSummary(DictProcessDetail entity) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", entity.getId());
        summary.put("categoryId", entity.getCategoryId());
        summary.put("processCode", entity.getProcessCode());
        summary.put("detailName", entity.getDetailName());
        summary.put("hourlyRate", entity.getHourlyRate());
        summary.put("enabled", entity.getEnabled());
        return summary;
    }

    private String required(String value, String label) {
        String trimmed = optional(value);
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        return trimmed;
    }

    private String optional(String value) {
        return value == null ? "" : value.trim();
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 20;
        }
        return Math.min(pageSize, 100);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String limit(String value, String label, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(label + "长度不能超过 " + maxLength + " 个字符");
        }
        return value;
    }

    private String upper(String value, String label, int maxLength) {
        return limit(value, label, maxLength).toUpperCase(Locale.ROOT);
    }

    private String normalizeProcessDetailCode(String value) {
        String normalized = optional(value).toUpperCase(Locale.ROOT);
        if (!normalized.isBlank() && !normalized.matches("[A-Z0-9_-]{1,80}")) {
            throw new IllegalArgumentException("工艺编码只允许 1-80 位大写字母、数字、下划线或中划线");
        }
        return normalized;
    }

    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    private boolean isMesSource(String value) {
        return SOURCE_MES.equalsIgnoreCase(optional(value));
    }
}
