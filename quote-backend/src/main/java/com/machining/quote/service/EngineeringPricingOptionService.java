package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.MaterialPriceSpecDTO;
import com.machining.quote.dto.PricingMaterialOptionDTO;
import com.machining.quote.dto.PricingOptionPageReq;
import com.machining.quote.dto.PricingProcessOptionDTO;
import com.machining.quote.entity.DictExtraFeeItem;
import com.machining.quote.entity.DictMaterial;
import com.machining.quote.entity.DictMaterialPriceSpec;
import com.machining.quote.entity.DictMaterialType;
import com.machining.quote.entity.DictProcessCategory;
import com.machining.quote.entity.DictProcessDetail;
import com.machining.quote.entity.PricingOptionUsageStat;
import com.machining.quote.mapper.DictExtraFeeItemMapper;
import com.machining.quote.mapper.DictMaterialMapper;
import com.machining.quote.mapper.DictMaterialPriceSpecMapper;
import com.machining.quote.mapper.DictMaterialTypeMapper;
import com.machining.quote.mapper.DictProcessCategoryMapper;
import com.machining.quote.mapper.DictProcessDetailMapper;
import com.machining.quote.mapper.PricingOptionUsageStatMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.machining.quote.util.MaterialPriceSpecSupport;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class EngineeringPricingOptionService {
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final String OPTION_TYPE_MATERIAL = PricingOptionUsageStatService.OPTION_TYPE_MATERIAL;
    private static final String OPTION_TYPE_PROCESS_CATEGORY = PricingOptionUsageStatService.OPTION_TYPE_PROCESS_CATEGORY;
    private static final String OPTION_TYPE_EXTRA_FEE = PricingOptionUsageStatService.OPTION_TYPE_EXTRA_FEE;
    private static final String SOURCE_TYPE_PROCESS = "process";
    private static final String SOURCE_TYPE_EXTRA = "extra";

    @Autowired
    private DictMaterialMapper materialMapper;
    @Autowired
    private DictMaterialPriceSpecMapper materialPriceSpecMapper;
    @Autowired
    private DictMaterialTypeMapper materialTypeMapper;
    @Autowired
    private DictProcessDetailMapper processDetailMapper;
    @Autowired
    private DictProcessCategoryMapper processCategoryMapper;
    @Autowired
    private DictExtraFeeItemMapper extraFeeItemMapper;
    @Autowired(required = false)
    private PricingOptionUsageStatMapper usageStatMapper;

    public PageResult<PricingMaterialOptionDTO> pageMaterialOptions(PricingOptionPageReq req) {
        PricingOptionPageReq safeReq = req == null ? new PricingOptionPageReq() : req;
        int safePage = normalizePage(safeReq.getPage());
        int safePageSize = normalizePageSize(safeReq.getPageSize());
        List<String> materialCodes = parseMaterialCodes(safeReq.getMaterialCodes());
        QueryWrapper<DictMaterial> countQuery = materialQuery(safeReq.getKeyword(), materialCodes);
        Long total = materialMapper.selectCount(countQuery);
        if (total == null || total == 0) {
            return PageResult.of(List.of(), 0, safePage, safePageSize);
        }
        QueryWrapper<DictMaterial> pageQuery = materialQuery(safeReq.getKeyword(), materialCodes);
        pageQuery.last(materialUsageOrderBy() + " " + limitOffset(safePage, safePageSize));
        List<DictMaterial> materials = materialMapper.selectList(pageQuery);
        List<PricingMaterialOptionDTO> records = toMaterialOptions(materials);
        applyMaterialUsageCounts(records);
        return PageResult.of(records, total, safePage, safePageSize);
    }

    public PageResult<PricingProcessOptionDTO> pageProcessOptions(PricingOptionPageReq req) {
        PricingOptionPageReq safeReq = req == null ? new PricingOptionPageReq() : req;
        int safePage = normalizePage(safeReq.getPage());
        int safePageSize = normalizePageSize(safeReq.getPageSize());
        String keyword = safeReq.getKeyword();
        List<Long> processDetailIds = parseProcessDetailIds(safeReq.getProcessDetailIds());
        List<Integer> feeItemIds = parseFeeItemIds(safeReq.getFeeItemIds());
        String sourceType = normalizeSourceType(safeReq.getSourceType());
        boolean exactLookup = !processDetailIds.isEmpty() || !feeItemIds.isEmpty();
        boolean queryProcess = (sourceType == null || SOURCE_TYPE_PROCESS.equals(sourceType))
                && (!exactLookup || !processDetailIds.isEmpty());
        boolean queryExtra = (sourceType == null || SOURCE_TYPE_EXTRA.equals(sourceType))
                && (!exactLookup || !feeItemIds.isEmpty());
        long processTotal = queryProcess
                ? Objects.requireNonNullElse(processDetailMapper.selectCount(processDetailQuery(keyword, processDetailIds)), 0L)
                : 0L;
        long extraTotal = queryExtra
                ? Objects.requireNonNullElse(extraFeeItemMapper.selectCount(extraFeeItemQuery(keyword, feeItemIds)), 0L)
                : 0L;
        long total = processTotal + extraTotal;
        if (total == 0) {
            return PageResult.of(List.of(), 0, safePage, safePageSize);
        }

        long offset = (safePage - 1L) * safePageSize;
        int candidateLimit = (int) Math.min(Integer.MAX_VALUE, offset + safePageSize);
        List<PricingProcessOptionDTO> detailRecords = !queryProcess || processTotal == 0
                ? List.of()
                : toProcessOptions(selectProcessDetails(keyword, processDetailIds, candidateLimit, 0));
        List<PricingProcessOptionDTO> feeRecords = !queryExtra || extraTotal == 0
                ? List.of()
                : toExtraFeeOptions(selectExtraFeeItems(keyword, feeItemIds, candidateLimit, 0));
        applyProcessUsageCounts(detailRecords);
        applyExtraFeeUsageCounts(feeRecords);
        List<PricingProcessOptionDTO> records = concat(detailRecords, feeRecords).stream()
                .sorted(Comparator.comparingLong(this::usageCount).reversed())
                .skip(offset)
                .limit(safePageSize)
                .toList();
        return PageResult.of(records, total, safePage, safePageSize);
    }

    private List<DictProcessDetail> selectProcessDetails(String keyword, List<Long> processDetailIds, int limit, long offset) {
        if (limit <= 0) {
            return List.of();
        }
        QueryWrapper<DictProcessDetail> query = processDetailQuery(keyword, processDetailIds);
        query.last(processUsageOrderBy() + " LIMIT " + limit + " OFFSET " + offset);
        return processDetailMapper.selectList(query);
    }

    private List<DictExtraFeeItem> selectExtraFeeItems(String keyword, List<Integer> feeItemIds, int limit, long offset) {
        if (limit <= 0) {
            return List.of();
        }
        QueryWrapper<DictExtraFeeItem> query = extraFeeItemQuery(keyword, feeItemIds);
        query.last(extraFeeUsageOrderBy() + " LIMIT " + limit + " OFFSET " + offset);
        return extraFeeItemMapper.selectList(query);
    }

    private QueryWrapper<DictMaterial> materialQuery(String keyword, List<String> materialCodes) {
        QueryWrapper<DictMaterial> query = new QueryWrapper<>();
        query.eq("enabled", true);
        if (materialCodes != null && !materialCodes.isEmpty()) {
            query.in("UPPER(material_code)", materialCodes);
        }
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

    private QueryWrapper<DictProcessDetail> processDetailQuery(String keyword, List<Long> processDetailIds) {
        QueryWrapper<DictProcessDetail> query = new QueryWrapper<>();
        query.eq("enabled", true)
                .apply("EXISTS (SELECT 1 FROM dict_process_category c WHERE c.id = dict_process_detail.category_id AND c.enabled = TRUE)");
        if (processDetailIds != null && !processDetailIds.isEmpty()) {
            query.in("id", processDetailIds);
        }
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

    private QueryWrapper<DictExtraFeeItem> extraFeeItemQuery(String keyword, List<Integer> feeItemIds) {
        QueryWrapper<DictExtraFeeItem> query = new QueryWrapper<>();
        query.eq("enabled", true);
        if (feeItemIds != null && !feeItemIds.isEmpty()) {
            query.in("id", feeItemIds);
        }
        if (!hasText(keyword)) {
            return query;
        }
        String pattern = pattern(keyword);
        query.and(wrapper -> wrapper
                .apply("LOWER(fee_name) LIKE {0}", pattern)
                .or()
                .apply("LOWER(fee_code) LIKE {0}", pattern)
                .or()
                .apply("LOWER(remarks) LIKE {0}", pattern));
        return query;
    }

    private void applyMaterialUsageCounts(List<PricingMaterialOptionDTO> records) {
        Map<String, Long> counts = usageCounts(OPTION_TYPE_MATERIAL, records.stream()
                .map(PricingMaterialOptionDTO::getMaterialCode)
                .map(this::normalizedKey)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
        records.forEach(record -> record.setUsageCount(usageCountFor(counts, normalizedKey(record.getMaterialCode()))));
    }

    private void applyProcessUsageCounts(List<PricingProcessOptionDTO> records) {
        Map<String, Long> counts = usageCounts(OPTION_TYPE_PROCESS_CATEGORY, records.stream()
                .map(PricingProcessOptionDTO::getProcessCode)
                .map(this::normalizedKey)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
        records.forEach(record -> record.setUsageCount(usageCountFor(counts, normalizedKey(record.getProcessCode()))));
    }

    private void applyExtraFeeUsageCounts(List<PricingProcessOptionDTO> records) {
        Map<String, Long> counts = usageCounts(OPTION_TYPE_EXTRA_FEE, records.stream()
                .map(PricingProcessOptionDTO::getFeeCode)
                .map(this::normalizedKey)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
        records.forEach(record -> record.setUsageCount(usageCountFor(counts, normalizedKey(record.getFeeCode()))));
    }

    private long usageCountFor(Map<String, Long> counts, String optionKey) {
        return optionKey == null ? 0L : counts.getOrDefault(optionKey, 0L);
    }

    private Map<String, Long> usageCounts(String optionType, List<String> optionKeys) {
        if (usageStatMapper == null || optionKeys == null || optionKeys.isEmpty()) {
            return Map.of();
        }
        List<PricingOptionUsageStat> rows = usageStatMapper.selectUsageCounts(optionType, optionKeys);
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        return rows.stream()
                .filter(row -> normalizedKey(row.getOptionKey()) != null)
                .collect(Collectors.toMap(
                        row -> normalizedKey(row.getOptionKey()),
                        row -> Objects.requireNonNullElse(row.getUsageCount(), 0L),
                        Long::max));
    }

    private long usageCount(PricingProcessOptionDTO record) {
        return record == null || record.getUsageCount() == null ? 0L : record.getUsageCount();
    }

    private String materialUsageOrderBy() {
        return "ORDER BY " + usageCountSubquery(OPTION_TYPE_MATERIAL, "UPPER(material_code)")
                + " DESC, material_type_id ASC, sort_no ASC, id ASC";
    }

    private String processUsageOrderBy() {
        return "ORDER BY " + usageCountSubquery(OPTION_TYPE_PROCESS_CATEGORY,
                "(SELECT UPPER(c.process_code) FROM dict_process_category c WHERE c.id = dict_process_detail.category_id)")
                + " DESC, category_id ASC, sort_no ASC, id ASC";
    }

    private String extraFeeUsageOrderBy() {
        return "ORDER BY " + usageCountSubquery(OPTION_TYPE_EXTRA_FEE, "UPPER(fee_code)")
                + " DESC, sort_no ASC, id ASC";
    }

    private String usageCountSubquery(String optionType, String keyExpression) {
        return "COALESCE((SELECT s.usage_count FROM pricing_option_usage_stat s "
                + "WHERE s.option_type = '" + optionType + "' AND s.option_key = " + keyExpression + "), 0)";
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
        List<Long> materialIds = materials.stream()
                .map(DictMaterial::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, List<MaterialPriceSpecDTO>> specsByMaterialId = loadPriceSpecsByMaterialIds(materialIds);
        return materials.stream()
                .map(material -> toMaterialOption(material, typeById.get(material.getMaterialTypeId()), specsByMaterialId.get(material.getId())))
                .toList();
    }

    private Map<Long, List<MaterialPriceSpecDTO>> loadPriceSpecsByMaterialIds(List<Long> materialIds) {
        if (materialIds == null || materialIds.isEmpty()) {
            return Map.of();
        }
        QueryWrapper<DictMaterialPriceSpec> query = new QueryWrapper<>();
        query.in("material_id", materialIds).orderByAsc("material_id").orderByAsc("sort_no").orderByAsc("id");
        return MaterialPriceSpecSupport.groupDtosByMaterialId(materialIds, materialPriceSpecMapper.selectList(query));
    }

    private PricingMaterialOptionDTO toMaterialOption(DictMaterial material, DictMaterialType type, List<MaterialPriceSpecDTO> priceSpecs) {
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
        dto.setPriceSpecs(MaterialPriceSpecSupport.normalizeDtos(priceSpecs));
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
        String feeCode = normalizedKey(item.getFeeCode());
        dto.setOptionKey("extra:" + (feeCode == null ? item.getId() : feeCode));
        dto.setSourceType("extra");
        dto.setFeeItemId(item.getId());
        dto.setFeeCode(item.getFeeCode());
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

    private String normalizedKey(String value) {
        if (!hasText(value)) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private List<String> parseMaterialCodes(String value) {
        if (!hasText(value)) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .map(item -> item.toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private List<Long> parseProcessDetailIds(String value) {
        if (!hasText(value)) {
            return List.of();
        }
        try {
            return Arrays.stream(value.split(","))
                    .map(String::trim)
                    .filter(item -> !item.isEmpty())
                    .map(Long::valueOf)
                    .filter(item -> item > 0)
                    .distinct()
                    .toList();
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("processDetailIds 只允许数字逗号分隔");
        }
    }

    private List<Integer> parseFeeItemIds(String value) {
        if (!hasText(value)) {
            return List.of();
        }
        try {
            return Arrays.stream(value.split(","))
                    .map(String::trim)
                    .filter(item -> !item.isEmpty())
                    .map(Integer::valueOf)
                    .filter(item -> item > 0)
                    .distinct()
                    .toList();
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("feeItemIds 只允许数字逗号分隔");
        }
    }

    private String normalizeSourceType(String sourceType) {
        if (!hasText(sourceType)) {
            return null;
        }
        String normalized = sourceType.trim().toLowerCase(Locale.ROOT);
        if ("all".equals(normalized)) {
            return null;
        }
        if (SOURCE_TYPE_PROCESS.equals(normalized) || SOURCE_TYPE_EXTRA.equals(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("sourceType 只支持 process / extra / all");
    }
}
