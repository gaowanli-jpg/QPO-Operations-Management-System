package com.machining.quote.util;

import com.machining.quote.dto.MaterialPriceSpecDTO;
import com.machining.quote.entity.DictMaterialPriceSpec;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class MaterialPriceSpecSupport {
    public static final String SPEC_1 = "SPEC_1";
    public static final String SPEC_2 = "SPEC_2";
    public static final String SPEC_3 = "SPEC_3";
    public static final List<SpecDefinition> DEFINITIONS = List.of(
            new SpecDefinition(SPEC_1, "规格一", 10),
            new SpecDefinition(SPEC_2, "规格二", 20),
            new SpecDefinition(SPEC_3, "规格三", 30)
    );

    private MaterialPriceSpecSupport() {
    }

    public static List<MaterialPriceSpecDTO> normalizeDtos(Collection<MaterialPriceSpecDTO> input) {
        Map<String, MaterialPriceSpecDTO> byCode = new LinkedHashMap<>();
        if (input != null) {
            for (MaterialPriceSpecDTO row : input) {
                if (row == null || isBlank(row.getSpecCode())) {
                    continue;
                }
                String specCode = normalizeSpecCode(row.getSpecCode());
                if (byCode.containsKey(specCode)) {
                    throw new IllegalArgumentException("材料价格规格重复：" + specCode);
                }
                byCode.put(specCode, normalizeInput(row, definition(specCode)));
            }
        }
        return DEFINITIONS.stream()
                .map(definition -> toDto(byCode.getOrDefault(definition.code(), defaultDto(definition)), definition))
                .toList();
    }

    public static List<MaterialPriceSpecDTO> normalizeEntityDtos(Collection<DictMaterialPriceSpec> input) {
        Map<String, MaterialPriceSpecDTO> byCode = new LinkedHashMap<>();
        if (input != null) {
            for (DictMaterialPriceSpec row : input) {
                if (row == null || isBlank(row.getSpecCode())) {
                    continue;
                }
                String specCode = normalizeSpecCode(row.getSpecCode());
                byCode.putIfAbsent(specCode, toDto(row));
            }
        }
        return normalizeDtos(byCode.values());
    }

    public static Map<Long, List<MaterialPriceSpecDTO>> groupDtosByMaterialId(
            Collection<Long> materialIds,
            Collection<DictMaterialPriceSpec> specs) {
        Map<Long, List<DictMaterialPriceSpec>> byMaterialId = specs == null ? Map.of() : specs.stream()
                .filter(spec -> spec.getMaterialId() != null)
                .collect(Collectors.groupingBy(
                        DictMaterialPriceSpec::getMaterialId,
                        LinkedHashMap::new,
                        Collectors.toList()));
        Map<Long, List<MaterialPriceSpecDTO>> result = new LinkedHashMap<>();
        if (materialIds != null) {
            for (Long materialId : materialIds) {
                result.put(materialId, normalizeEntityDtos(byMaterialId.get(materialId)));
            }
        }
        return result;
    }

    public static List<DictMaterialPriceSpec> toEntities(
            Long materialId,
            Collection<MaterialPriceSpecDTO> specs,
            LocalDateTime now) {
        return normalizeDtos(specs).stream()
                .map(dto -> toEntity(materialId, dto, now))
                .toList();
    }

    public static List<DictMaterialPriceSpec> defaultEntities(Long materialId, LocalDateTime now) {
        return DEFINITIONS.stream()
                .map(definition -> toEntity(materialId, defaultDto(definition), now))
                .toList();
    }

    public static String normalizeSpecCode(String specCode) {
        String normalized = specCode == null ? "" : specCode.trim().toUpperCase(Locale.ROOT);
        if (DEFINITIONS.stream().noneMatch(definition -> definition.code().equals(normalized))) {
            throw new IllegalArgumentException("材料价格规格只能是 SPEC_1 / SPEC_2 / SPEC_3");
        }
        return normalized;
    }

    public static MaterialPriceSpecDTO findRequired(List<MaterialPriceSpecDTO> specs, String specCode) {
        String normalized = normalizeSpecCode(specCode);
        return normalizeDtos(specs).stream()
                .filter(spec -> normalized.equals(spec.getSpecCode()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("材料价格规格不存在：" + normalized));
    }

    public static PriceResolution effectivePrice(MaterialPriceSpecDTO spec, String shape) {
        if (spec == null) {
            return new PriceResolution(null, null);
        }
        Double shapePrice;
        String source;
        if (isBarShape(shape)) {
            shapePrice = spec.getBarPrice();
            source = "BAR_PRICE";
        } else if (isTubeShape(shape)) {
            shapePrice = spec.getTubePrice();
            source = "TUBE_PRICE";
        } else {
            shapePrice = spec.getPlatePrice();
            source = "PLATE_PRICE";
        }
        if (isPositive(shapePrice)) {
            return new PriceResolution(shapePrice, source);
        }
        if (isPositive(spec.getUnifiedPrice())) {
            return new PriceResolution(spec.getUnifiedPrice(), "UNIFIED_PRICE");
        }
        return new PriceResolution(null, null);
    }

    public static MaterialPriceSpecDTO toDto(DictMaterialPriceSpec entity) {
        if (entity == null) {
            return null;
        }
        MaterialPriceSpecDTO dto = new MaterialPriceSpecDTO();
        dto.setId(entity.getId());
        dto.setMaterialId(entity.getMaterialId());
        dto.setSpecCode(normalizeSpecCode(entity.getSpecCode()));
        dto.setSpecName(firstText(entity.getSpecName(), definition(dto.getSpecCode()).name()));
        dto.setUnifiedPrice(nonNegative(entity.getUnifiedPrice(), dto.getSpecName() + "统一价格"));
        dto.setPlatePrice(nonNegative(entity.getPlatePrice(), dto.getSpecName() + "板材价格"));
        dto.setBarPrice(nonNegative(entity.getBarPrice(), dto.getSpecName() + "棒材价格"));
        dto.setTubePrice(nonNegative(entity.getTubePrice(), dto.getSpecName() + "管材价格"));
        dto.setSortNo(entity.getSortNo());
        return toDto(dto, definition(dto.getSpecCode()));
    }

    public static boolean pricesEqual(MaterialPriceSpecDTO left, MaterialPriceSpecDTO right) {
        return Objects.equals(normalizePrice(left == null ? null : left.getUnifiedPrice()), normalizePrice(right == null ? null : right.getUnifiedPrice()))
                && Objects.equals(normalizePrice(left == null ? null : left.getPlatePrice()), normalizePrice(right == null ? null : right.getPlatePrice()))
                && Objects.equals(normalizePrice(left == null ? null : left.getBarPrice()), normalizePrice(right == null ? null : right.getBarPrice()))
                && Objects.equals(normalizePrice(left == null ? null : left.getTubePrice()), normalizePrice(right == null ? null : right.getTubePrice()));
    }

    private static MaterialPriceSpecDTO normalizeInput(MaterialPriceSpecDTO source, SpecDefinition definition) {
        MaterialPriceSpecDTO dto = new MaterialPriceSpecDTO();
        dto.setId(source.getId());
        dto.setMaterialId(source.getMaterialId());
        dto.setSpecCode(definition.code());
        dto.setSpecName(definition.name());
        dto.setUnifiedPrice(nonNegative(source.getUnifiedPrice(), definition.name() + "统一价格"));
        dto.setPlatePrice(nonNegative(source.getPlatePrice(), definition.name() + "板材价格"));
        dto.setBarPrice(nonNegative(source.getBarPrice(), definition.name() + "棒材价格"));
        dto.setTubePrice(nonNegative(source.getTubePrice(), definition.name() + "管材价格"));
        dto.setSortNo(definition.sortNo());
        return dto;
    }

    private static MaterialPriceSpecDTO toDto(MaterialPriceSpecDTO source, SpecDefinition definition) {
        MaterialPriceSpecDTO dto = new MaterialPriceSpecDTO();
        dto.setId(source.getId());
        dto.setMaterialId(source.getMaterialId());
        dto.setSpecCode(definition.code());
        dto.setSpecName(definition.name());
        dto.setUnifiedPrice(nonNegative(source.getUnifiedPrice(), definition.name() + "统一价格"));
        dto.setPlatePrice(nonNegative(source.getPlatePrice(), definition.name() + "板材价格"));
        dto.setBarPrice(nonNegative(source.getBarPrice(), definition.name() + "棒材价格"));
        dto.setTubePrice(nonNegative(source.getTubePrice(), definition.name() + "管材价格"));
        dto.setSortNo(definition.sortNo());
        dto.setEffectivePlatePrice(effectiveShapePrice(dto.getPlatePrice(), dto.getUnifiedPrice()));
        dto.setEffectiveBarPrice(effectiveShapePrice(dto.getBarPrice(), dto.getUnifiedPrice()));
        dto.setEffectiveTubePrice(effectiveShapePrice(dto.getTubePrice(), dto.getUnifiedPrice()));
        return dto;
    }

    private static DictMaterialPriceSpec toEntity(Long materialId, MaterialPriceSpecDTO dto, LocalDateTime now) {
        DictMaterialPriceSpec entity = new DictMaterialPriceSpec();
        entity.setMaterialId(materialId);
        entity.setSpecCode(dto.getSpecCode());
        entity.setSpecName(dto.getSpecName());
        entity.setUnifiedPrice(dto.getUnifiedPrice());
        entity.setPlatePrice(dto.getPlatePrice());
        entity.setBarPrice(dto.getBarPrice());
        entity.setTubePrice(dto.getTubePrice());
        entity.setSortNo(dto.getSortNo());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }

    private static MaterialPriceSpecDTO defaultDto(SpecDefinition definition) {
        MaterialPriceSpecDTO dto = new MaterialPriceSpecDTO();
        dto.setSpecCode(definition.code());
        dto.setSpecName(definition.name());
        dto.setSortNo(definition.sortNo());
        return dto;
    }

    private static SpecDefinition definition(String specCode) {
        String normalized = normalizeSpecCode(specCode);
        return DEFINITIONS.stream()
                .filter(definition -> definition.code().equals(normalized))
                .findFirst()
                .orElseThrow();
    }

    private static Double effectiveShapePrice(Double shapePrice, Double unifiedPrice) {
        return isPositive(shapePrice) ? shapePrice : isPositive(unifiedPrice) ? unifiedPrice : null;
    }

    private static boolean isBarShape(String shape) {
        return "棒材".equals(shape) || "妫掓潗".equals(shape) || "BAR".equalsIgnoreCase(firstText(shape, ""));
    }

    private static boolean isTubeShape(String shape) {
        return "管材".equals(shape) || "绠℃潗".equals(shape) || "TUBE".equalsIgnoreCase(firstText(shape, ""));
    }

    private static boolean isPositive(Double value) {
        return value != null && value > 0;
    }

    private static Double nonNegative(Double value, String label) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(label + "不能小于 0");
        }
        return value;
    }

    private static Double normalizePrice(Double value) {
        return value == null || value == 0 ? null : value;
    }

    private static String firstText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public record SpecDefinition(String code, String name, int sortNo) {
    }

    public record PriceResolution(Double unitPrice, String source) {
    }
}
