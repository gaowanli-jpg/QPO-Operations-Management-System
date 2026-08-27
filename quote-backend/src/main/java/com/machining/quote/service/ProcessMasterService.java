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
import java.util.Objects;

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
        qw.orderByAsc("category_id").orderByAsc("sort_no").orderByAsc("id");
        return detailMapper.selectList(qw).stream()
                .map(this::toDetailDTO)
                .filter(dto -> !Boolean.TRUE.equals(enabledOnly) || Boolean.TRUE.equals(dto.getEffectiveEnabled()))
                .toList();
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
        qw.orderByAsc("category_id").orderByAsc("sort_no").orderByAsc("id");
        qw.last("LIMIT " + safePageSize + " OFFSET " + ((safePage - 1L) * safePageSize));
        List<ProcessDetailDTO> records = detailMapper.selectList(qw).stream()
                .map(this::toDetailDTO)
                .toList();
        return PageResult.of(records, total, safePage, safePageSize);
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
            qw.eq("enabled", true)
                    .apply("EXISTS (SELECT 1 FROM dict_process_category c WHERE c.id = dict_process_detail.category_id AND c.enabled = TRUE)");
        }
        if (!hasText(keyword)) {
            return;
        }
        String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
        qw.and(wrapper -> wrapper
                .apply("LOWER(detail_name) LIKE {0}", pattern)
                .or()
                .apply("LOWER(detail_remarks) LIKE {0}", pattern)
                .or()
                .apply("EXISTS (SELECT 1 FROM dict_process_category c WHERE c.id = dict_process_detail.category_id AND (LOWER(c.process_code) LIKE {0} OR LOWER(c.category_name) LIKE {0}))", pattern));
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
        DictProcessCategory beforeCategory = before == null ? null : requireCategory(before.getCategoryId());
        validateMesProcessDetailIdentityUnchanged(before, beforeCategory, req);
        DictProcessDetail entity = normalizeDetail(req, before);
        entity.setId(id);
        DictProcessCategory category = requireCategory(entity.getCategoryId());
        if (before == null && isMesSource(category.getSourceType())) {
            throw new IllegalArgumentException("MES process detail must be created by MES sync, not manually");
        }
        ensureDetailNameUnique(entity.getCategoryId(), entity.getDetailName(), id);
        if (id == null) {
            detailMapper.insert(entity);
            auditService.record(
                    "DICT_PROCESS_DETAIL_CREATE",
                    "dict_process_detail",
                    entity.getId(),
                    null,
                    detailAuditSummary(entity, category)
            );
        } else {
            detailMapper.updateById(entity);
            auditService.record(
                    "DICT_PROCESS_DETAIL_UPDATE",
                    "dict_process_detail",
                    id,
                    detailAuditSummary(before, beforeCategory),
                    detailAuditSummary(entity, category)
            );
        }
        return toDetailDTO(entity, category);
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
        Long categoryId = req.getCategoryId() == null && before != null ? before.getCategoryId() : req.getCategoryId();
        if (categoryId == null) {
            throw new IllegalArgumentException("工艺大类不能为空");
        }
        DictProcessDetail entity = new DictProcessDetail();
        entity.setCategoryId(categoryId);
        BigDecimal hourlyRate = req.getHourlyRate();
        if (hourlyRate != null && hourlyRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("具体小时价格不能小于 0");
        }
        entity.setHourlyRate(hourlyRate);
        entity.setDetailName(limit(required(req.getDetailName(), "具体工艺名称"), "具体工艺名称", 120));
        entity.setDetailRemarks(limit(optional(req.getDetailRemarks()), "具体备注", 500));
        entity.setEnabled(req.getEnabled() == null ? before == null || Boolean.TRUE.equals(before.getEnabled()) : req.getEnabled());
        entity.setSortNo(req.getSortNo() == null ? before == null ? 0 : Objects.requireNonNullElse(before.getSortNo(), 0) : req.getSortNo());
        return entity;
    }

    private void validateMesProcessDetailIdentityUnchanged(
            DictProcessDetail before,
            DictProcessCategory beforeCategory,
            ProcessDetailSaveReq req) {
        if (before == null || !isMesSource(beforeCategory == null ? null : beforeCategory.getSourceType()) || req == null) {
            return;
        }
        Long requestedCategoryId = req.getCategoryId() == null ? before.getCategoryId() : req.getCategoryId();
        String requestedDetailName = optional(req.getDetailName());
        if (!Objects.equals(before.getCategoryId(), requestedCategoryId)
                || !Objects.equals(optional(before.getDetailName()), requestedDetailName)) {
            throw new IllegalArgumentException("MES process detail identity fields are read-only in QPO; update MES and sync again");
        }
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

    private void ensureDetailNameUnique(Long categoryId, String detailName, Long id) {
        QueryWrapper<DictProcessDetail> qw = new QueryWrapper<>();
        qw.eq("category_id", categoryId).eq("detail_name", detailName);
        if (id != null) {
            qw.ne("id", id);
        }
        if (detailMapper.selectCount(qw) > 0) {
            throw new IllegalArgumentException("同一工艺大类下具体工艺名称已存在");
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
        DictProcessCategory category = categoryMapper.selectById(entity.getCategoryId());
        return toDetailDTO(entity, category);
    }

    private ProcessDetailDTO toDetailDTO(DictProcessDetail entity, DictProcessCategory category) {
        ProcessDetailDTO dto = new ProcessDetailDTO();
        dto.setId(entity.getId());
        dto.setCategoryId(entity.getCategoryId());
        dto.setDetailName(entity.getDetailName());
        dto.setDetailRemarks(entity.getDetailRemarks());
        dto.setEnabled(entity.getEnabled());
        dto.setDetailEnabled(entity.getEnabled());
        dto.setCategoryEnabled(category != null && Boolean.TRUE.equals(category.getEnabled()));
        dto.setEffectiveEnabled(effectiveEnabled(entity, category));
        dto.setSortNo(entity.getSortNo());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        if (category != null) {
            dto.setProcessCode(category.getProcessCode());
            dto.setCategoryName(category.getCategoryName());
            dto.setCategoryHourlyRate(category.getHourlyRate());
        }
        dto.setDetailHourlyRate(entity.getHourlyRate());
        BigDecimal effectiveHourlyRate = effectiveHourlyRate(entity, category);
        dto.setEffectiveHourlyRate(effectiveHourlyRate);
        dto.setHourlyRate(effectiveHourlyRate);
        return dto;
    }

    private BigDecimal effectiveHourlyRate(DictProcessDetail detail, DictProcessCategory category) {
        BigDecimal detailRate = detail == null ? null : detail.getHourlyRate();
        if (detailRate != null) {
            return detailRate;
        }
        return category == null ? BigDecimal.ZERO : Objects.requireNonNullElse(category.getHourlyRate(), BigDecimal.ZERO);
    }

    private boolean effectiveEnabled(DictProcessDetail detail, DictProcessCategory category) {
        return detail != null
                && Boolean.TRUE.equals(detail.getEnabled())
                && category != null
                && Boolean.TRUE.equals(category.getEnabled());
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

    private Map<String, Object> detailAuditSummary(DictProcessDetail entity, DictProcessCategory category) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", entity.getId());
        summary.put("categoryId", entity.getCategoryId());
        summary.put("processCode", category == null ? null : category.getProcessCode());
        summary.put("categoryName", category == null ? null : category.getCategoryName());
        summary.put("detailName", entity.getDetailName());
        summary.put("detailHourlyRate", entity.getHourlyRate());
        summary.put("categoryHourlyRate", category == null ? null : category.getHourlyRate());
        summary.put("effectiveHourlyRate", effectiveHourlyRate(entity, category));
        summary.put("enabled", entity.getEnabled());
        summary.put("detailEnabled", entity.getEnabled());
        summary.put("categoryEnabled", category != null && Boolean.TRUE.equals(category.getEnabled()));
        summary.put("effectiveEnabled", effectiveEnabled(entity, category));
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

    private boolean isMesSource(String value) {
        return SOURCE_MES.equalsIgnoreCase(optional(value));
    }
}
