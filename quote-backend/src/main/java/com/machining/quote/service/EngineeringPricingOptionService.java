package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.PricingMaterialOptionDTO;
import com.machining.quote.dto.PricingOptionPageReq;
import com.machining.quote.dto.PricingProcessOptionDTO;
import com.machining.quote.entity.DictExtraFeeItem;
import com.machining.quote.entity.DictMaterial;
import com.machining.quote.entity.DictMaterialType;
import com.machining.quote.entity.DictProcessCategory;
import com.machining.quote.entity.DictProcessDetail;
import com.machining.quote.mapper.DictExtraFeeItemMapper;
import com.machining.quote.mapper.DictMaterialMapper;
import com.machining.quote.mapper.DictMaterialTypeMapper;
import com.machining.quote.mapper.DictProcessCategoryMapper;
import com.machining.quote.mapper.DictProcessDetailMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class EngineeringPricingOptionService {
    private static final int DEFAULT_PAGE_SIZE = 10;

    @Autowired
    private DictMaterialMapper materialMapper;
    @Autowired
    private DictMaterialTypeMapper materialTypeMapper;
    @Autowired
    private DictProcessDetailMapper processDetailMapper;
    @Autowired
    private DictProcessCategoryMapper processCategoryMapper;
    @Autowired
    private DictExtraFeeItemMapper extraFeeItemMapper;

    public PageResult<PricingMaterialOptionDTO> pageMaterialOptions(PricingOptionPageReq req) {
        PricingOptionPageReq safeReq = req == null ? new PricingOptionPageReq() : req;
        int safePage = normalizePage(safeReq.getPage());
        int safePageSize = normalizePageSize(safeReq.getPageSize());
        QueryWrapper<DictMaterial> countQuery = materialQuery(safeReq.getKeyword());
        Long total = materialMapper.selectCount(countQuery);
        if (total == null || total == 0) {
            return PageResult.of(List.of(), 0, safePage, safePageSize);
        }
        QueryWrapper<DictMaterial> pageQuery = materialQuery(safeReq.getKeyword());
        pageQuery.orderByAsc("material_type_id").orderByAsc("sort_no").orderByAsc("id");
        pageQuery.last(limitOffset(safePage, safePageSize));
        List<DictMaterial> materials = materialMapper.selectList(pageQuery);
        return PageResult.of(toMaterialOptions(materials), total, safePage, safePageSize);
    }

    public PageResult<PricingProcessOptionDTO> pageProcessOptions(PricingOptionPageReq req) {
        PricingOptionPageReq safeReq = req == null ? new PricingOptionPageReq() : req;
        int safePage = normalizePage(safeReq.getPage());
        int safePageSize = normalizePageSize(safeReq.getPageSize());
        String keyword = safeReq.getKeyword();
        long processTotal = Objects.requireNonNullElse(processDetailMapper.selectCount(processDetailQuery(keyword)), 0L);
        long extraTotal = Objects.requireNonNullElse(extraFeeItemMapper.selectCount(extraFeeItemQuery(keyword)), 0L);
        long total = processTotal + extraTotal;
        if (total == 0) {
            return PageResult.of(List.of(), 0, safePage, safePageSize);
        }

        long offset = (safePage - 1L) * safePageSize;
        List<PricingProcessOptionDTO> records = List.of();
        if (offset < processTotal) {
            int detailLimit = (int) Math.min(safePageSize, processTotal - offset);
            List<PricingProcessOptionDTO> detailRecords = toProcessOptions(selectProcessDetails(keyword, detailLimit, offset));
            int remaining = safePageSize - detailRecords.size();
            if (remaining > 0) {
                List<PricingProcessOptionDTO> feeRecords = toExtraFeeOptions(selectExtraFeeItems(keyword, remaining, 0));
                records = concat(detailRecords, feeRecords);
            } else {
                records = detailRecords;
            }
        } else {
            long extraOffset = offset - processTotal;
            records = toExtraFeeOptions(selectExtraFeeItems(keyword, safePageSize, extraOffset));
        }
        return PageResult.of(records, total, safePage, safePageSize);
    }

    private List<DictProcessDetail> selectProcessDetails(String keyword, int limit, long offset) {
        if (limit <= 0) {
            return List.of();
        }
        QueryWrapper<DictProcessDetail> query = processDetailQuery(keyword);
        query.orderByAsc("category_id").orderByAsc("sort_no").orderByAsc("id");
        query.last("LIMIT " + limit + " OFFSET " + offset);
        return processDetailMapper.selectList(query);
    }

    private List<DictExtraFeeItem> selectExtraFeeItems(String keyword, int limit, long offset) {
        if (limit <= 0) {
            return List.of();
        }
        QueryWrapper<DictExtraFeeItem> query = extraFeeItemQuery(keyword);
        query.orderByAsc("sort_no").orderByAsc("id");
        query.last("LIMIT " + limit + " OFFSET " + offset);
        return extraFeeItemMapper.selectList(query);
    }

    private QueryWrapper<DictMaterial> materialQuery(String keyword) {
        QueryWrapper<DictMaterial> query = new QueryWrapper<>();
        query.eq("enabled", true);
        if (!hasText(keyword)) {
            return query;
        }
        String pattern = pattern(keyword);
        query.and(wrapper -> wrapper
                .apply("LOWER(material_code) LIKE {0}", pattern)
                .or()
                .apply("LOWER(material_name) LIKE {0}", pattern)
                .or()
                .apply("LOWER(material_category) LIKE {0}", pattern)
                .or()
                .apply("LOWER(remarks) LIKE {0}", pattern)
                .or()
                .apply("EXISTS (SELECT 1 FROM dict_material_type mt WHERE mt.id = dict_material.material_type_id AND (LOWER(mt.type_code) LIKE {0} OR LOWER(mt.type_name) LIKE {0}))", pattern));
        return query;
    }

    private QueryWrapper<DictProcessDetail> processDetailQuery(String keyword) {
        QueryWrapper<DictProcessDetail> query = new QueryWrapper<>();
        query.eq("enabled", true)
                .apply("EXISTS (SELECT 1 FROM dict_process_category c WHERE c.id = dict_process_detail.category_id AND c.enabled = TRUE)");
        if (!hasText(keyword)) {
            return query;
        }
        String pattern = pattern(keyword);
        query.and(wrapper -> wrapper
                .apply("LOWER(detail_name) LIKE {0}", pattern)
                .or()
                .apply("LOWER(detail_remarks) LIKE {0}", pattern)
                .or()
                .apply("EXISTS (SELECT 1 FROM dict_process_category c WHERE c.id = dict_process_detail.category_id AND (LOWER(c.process_code) LIKE {0} OR LOWER(c.category_name) LIKE {0}))", pattern));
        return query;
    }

    private QueryWrapper<DictExtraFeeItem> extraFeeItemQuery(String keyword) {
        QueryWrapper<DictExtraFeeItem> query = new QueryWrapper<>();
        query.eq("enabled", true);
        if (!hasText(keyword)) {
            return query;
        }
        String pattern = pattern(keyword);
        query.and(wrapper -> wrapper
                .apply("LOWER(fee_name) LIKE {0}", pattern)
                .or()
                .apply("LOWER(remarks) LIKE {0}", pattern));
        return query;
    }

    private List<PricingMaterialOptionDTO> toMaterialOptions(List<DictMaterial> materials) {
        if (materials == null || materials.isEmpty()) {
            return List.of();
        }
        List<Long> typeIds = materials.stream()
                .map(DictMaterial::getMaterialTypeId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, DictMaterialType> typeById = typeIds.isEmpty()
                ? Map.of()
                : toMap(materialTypeMapper.selectBatchIds(typeIds), DictMaterialType::getId);
        return materials.stream()
                .map(material -> toMaterialOption(material, typeById.get(material.getMaterialTypeId())))
                .toList();
    }

    private PricingMaterialOptionDTO toMaterialOption(DictMaterial material, DictMaterialType type) {
        PricingMaterialOptionDTO dto = new PricingMaterialOptionDTO();
        dto.setId(material.getId());
        dto.setMaterialCode(material.getMaterialCode());
        dto.setMaterialName(material.getMaterialName());
        dto.setMaterialTypeId(material.getMaterialTypeId());
        dto.setMaterialTypeCode(type == null ? null : type.getTypeCode());
        dto.setMaterialTypeName(type == null ? null : type.getTypeName());
        dto.setMaterialCategory(material.getMaterialCategory());
        dto.setDensity(material.getDensity());
        dto.setMesDensityRaw(material.getMesDensityRaw());
        dto.setPlatePrice(material.getPlatePrice());
        dto.setBarPrice(material.getBarPrice());
        dto.setTubePrice(material.getTubePrice());
        dto.setRemarks(material.getRemarks());
        return dto;
    }

    private List<PricingProcessOptionDTO> toProcessOptions(List<DictProcessDetail> details) {
        if (details == null || details.isEmpty()) {
            return List.of();
        }
        List<Long> categoryIds = details.stream()
                .map(DictProcessDetail::getCategoryId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, DictProcessCategory> categoryById = categoryIds.isEmpty()
                ? Map.of()
                : toMap(processCategoryMapper.selectBatchIds(categoryIds), DictProcessCategory::getId);
        return details.stream()
                .map(detail -> toProcessOption(detail, categoryById.get(detail.getCategoryId())))
                .toList();
    }

    private PricingProcessOptionDTO toProcessOption(DictProcessDetail detail, DictProcessCategory category) {
        BigDecimal categoryRate = category == null ? BigDecimal.ZERO : Objects.requireNonNullElse(category.getHourlyRate(), BigDecimal.ZERO);
        BigDecimal effectiveRate = detail.getHourlyRate() == null ? categoryRate : detail.getHourlyRate();
        PricingProcessOptionDTO dto = new PricingProcessOptionDTO();
        dto.setOptionKey("process:" + detail.getId());
        dto.setSourceType("process");
        dto.setProcessDetailId(detail.getId());
        dto.setProcessCategoryId(detail.getCategoryId());
        dto.setProcessCode(category == null ? null : category.getProcessCode());
        dto.setCategoryName(category == null ? null : category.getCategoryName());
        dto.setDetailName(detail.getDetailName());
        dto.setProcessName(detail.getDetailName());
        dto.setLabel(processLabel(detail, category, effectiveRate));
        dto.setCalcType("工时");
        dto.setDetailHourlyRate(detail.getHourlyRate());
        dto.setCategoryHourlyRate(categoryRate);
        dto.setEffectiveHourlyRate(effectiveRate);
        dto.setHourlyRate(effectiveRate);
        return dto;
    }

    private List<PricingProcessOptionDTO> toExtraFeeOptions(List<DictExtraFeeItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream().map(this::toExtraFeeOption).toList();
    }

    private PricingProcessOptionDTO toExtraFeeOption(DictExtraFeeItem item) {
        PricingProcessOptionDTO dto = new PricingProcessOptionDTO();
        dto.setOptionKey("extra:" + item.getId());
        dto.setSourceType("extra");
        dto.setFeeItemId(item.getId());
        dto.setFeeName(item.getFeeName());
        dto.setProcessName(item.getFeeName());
        dto.setLabel(item.getFeeName() + "（非工时费用）");
        dto.setCalcType("非工时费用");
        return dto;
    }

    private String processLabel(DictProcessDetail detail, DictProcessCategory category, BigDecimal effectiveRate) {
        String processCode = category == null ? "" : Objects.toString(category.getProcessCode(), "");
        String categoryName = category == null ? "" : Objects.toString(category.getCategoryName(), "");
        return detail.getDetailName() + "（" + processCode + " / " + categoryName + " / "
                + effectiveRate.setScale(2, RoundingMode.HALF_UP).toPlainString() + "元/小时）";
    }

    private <T, K> Map<K, T> toMap(Collection<T> rows, Function<T, K> keyMapper) {
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        return rows.stream().collect(Collectors.toMap(keyMapper, Function.identity(), (left, right) -> left));
    }

    private <T> List<T> concat(List<T> left, List<T> right) {
        if (left == null || left.isEmpty()) {
            return right == null ? List.of() : right;
        }
        if (right == null || right.isEmpty()) {
            return left;
        }
        return java.util.stream.Stream.concat(left.stream(), right.stream()).toList();
    }

    private String limitOffset(int page, int pageSize) {
        return "LIMIT " + pageSize + " OFFSET " + ((page - 1L) * pageSize);
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null) {
            return DEFAULT_PAGE_SIZE;
        }
        if (pageSize == 10 || pageSize == 20 || pageSize == 50 || pageSize == 100) {
            return pageSize;
        }
        throw new IllegalArgumentException("pageSize 只允许 10 / 20 / 50 / 100");
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String pattern(String keyword) {
        return "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
    }
}
