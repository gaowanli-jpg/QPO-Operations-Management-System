package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.dto.MaterialMasterDTO;
import com.machining.quote.dto.MaterialMasterSaveReq;
import com.machining.quote.dto.MaterialTypeDTO;
import com.machining.quote.dto.MaterialTypeSaveReq;
import com.machining.quote.entity.DictMaterial;
import com.machining.quote.entity.DictMaterialType;
import com.machining.quote.mapper.DictMaterialMapper;
import com.machining.quote.mapper.DictMaterialTypeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MaterialMasterService {
    private static final String SOURCE_LOCAL = "LOCAL";
    private static final String SOURCE_MES = "MES";

    @Autowired
    private DictMaterialMapper materialMapper;
    @Autowired
    private DictMaterialTypeMapper materialTypeMapper;
    @Autowired
    private AuditService auditService;

    public List<MaterialTypeDTO> listMaterialTypes(Boolean enabledOnly) {
        QueryWrapper<DictMaterialType> query = new QueryWrapper<>();
        if (Boolean.TRUE.equals(enabledOnly)) {
            query.eq("enabled", true);
        }
        query.orderByAsc("sort_no").orderByAsc("id");
        return materialTypeMapper.selectList(query).stream().map(this::toTypeDTO).toList();
    }

    public List<MaterialMasterDTO> listMaterials(Boolean enabledOnly) {
        QueryWrapper<DictMaterial> query = new QueryWrapper<>();
        if (Boolean.TRUE.equals(enabledOnly)) {
            query.eq("enabled", true);
        }
        query.orderByAsc("material_type_id").orderByAsc("sort_no").orderByAsc("id");
        List<DictMaterial> materials = materialMapper.selectList(query);
        if (materials.isEmpty()) {
            return List.of();
        }
        List<Long> typeIds = materials.stream()
                .map(DictMaterial::getMaterialTypeId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, DictMaterialType> typeById = typeIds.isEmpty()
                ? Map.of()
                : materialTypeMapper.selectBatchIds(typeIds).stream()
                .collect(Collectors.toMap(DictMaterialType::getId, Function.identity(), (left, right) -> left));
        return materials.stream()
                .map(material -> toMaterialDTO(material, typeById.get(material.getMaterialTypeId())))
                .toList();
    }

    @Transactional
    public MaterialTypeDTO saveMaterialType(Long id, MaterialTypeSaveReq req) {
        DictMaterialType before = id == null ? null : requireMaterialType(id);
        DictMaterialType entity = normalizeMaterialType(req, before);
        entity.setId(id);
        ensureMaterialTypeCodeUnique(entity.getTypeCode(), id);
        ensureMaterialTypeNameUnique(entity.getTypeName(), id);
        LocalDateTime now = LocalDateTime.now();
        entity.setUpdatedAt(now);
        if (id == null) {
            entity.setCreatedAt(now);
            materialTypeMapper.insert(entity);
            auditService.record(
                    "DICT_MATERIAL_TYPE_CREATE",
                    "dict_material_type",
                    entity.getId(),
                    null,
                    materialTypeAuditSummary(entity)
            );
        } else {
            materialTypeMapper.updateById(entity);
            auditService.record(
                    "DICT_MATERIAL_TYPE_UPDATE",
                    "dict_material_type",
                    id,
                    materialTypeAuditSummary(before),
                    materialTypeAuditSummary(entity)
            );
        }
        return toTypeDTO(entity);
    }

    @Transactional
    public void deleteMaterialType(Long id) {
        DictMaterialType before = requireMaterialType(id);
        QueryWrapper<DictMaterial> query = new QueryWrapper<>();
        query.eq("material_type_id", id);
        Long references = materialMapper.selectCount(query);
        if (references != null && references > 0) {
            DictMaterialType disabled = copyType(before);
            disabled.setEnabled(false);
            disabled.setUpdatedAt(LocalDateTime.now());
            materialTypeMapper.updateById(disabled);
            auditService.record(
                    "DICT_MATERIAL_TYPE_DISABLE",
                    "dict_material_type",
                    id,
                    materialTypeAuditSummary(before),
                    materialTypeAuditSummary(disabled)
            );
            return;
        }
        materialTypeMapper.deleteById(id);
        auditService.record(
                "DICT_MATERIAL_TYPE_DELETE",
                "dict_material_type",
                id,
                materialTypeAuditSummary(before),
                null
        );
    }

    @Transactional
    public MaterialMasterDTO saveMaterial(Long id, MaterialMasterSaveReq req) {
        DictMaterial before = id == null ? null : requireMaterial(id);
        validateMesMaterialIdentityUnchanged(before, req);
        DictMaterial entity = normalizeMaterial(req, before);
        entity.setId(id);
        DictMaterialType type = requireEnabledMaterialType(entity.getMaterialTypeId());
        ensureMaterialCodeUnique(entity.getMaterialCode(), id);
        LocalDateTime now = LocalDateTime.now();
        entity.setUpdatedAt(now);
        if (id == null) {
            entity.setCreatedAt(now);
            materialMapper.insert(entity);
            auditService.record(
                    "DICT_MATERIAL_CREATE",
                    "dict_material",
                    entity.getId(),
                    null,
                    materialAuditSummary(entity, type)
            );
        } else {
            DictMaterialType beforeType = before.getMaterialTypeId() == null ? null : materialTypeMapper.selectById(before.getMaterialTypeId());
            materialMapper.updateById(entity);
            auditService.record(
                    "DICT_MATERIAL_UPDATE",
                    "dict_material",
                    id,
                    materialAuditSummary(before, beforeType),
                    materialAuditSummary(entity, type)
            );
        }
        return toMaterialDTO(entity, type);
    }

    @Transactional
    public void deleteMaterial(Long id) {
        DictMaterial before = requireMaterial(id);
        if (isMesSource(before.getSourceType())) {
            throw new IllegalArgumentException("MES material cannot be deleted manually; sync MES master data instead");
        }
        DictMaterialType type = before.getMaterialTypeId() == null ? null : materialTypeMapper.selectById(before.getMaterialTypeId());
        DictMaterial disabled = copyMaterial(before);
        disabled.setEnabled(false);
        disabled.setUpdatedAt(LocalDateTime.now());
        materialMapper.updateById(disabled);
        auditService.record(
                "DICT_MATERIAL_DISABLE",
                "dict_material",
                id,
                materialAuditSummary(before, type),
                materialAuditSummary(disabled, type)
        );
    }

    private DictMaterialType normalizeMaterialType(MaterialTypeSaveReq req, DictMaterialType before) {
        if (req == null) {
            throw new IllegalArgumentException("材料类型请求不能为空");
        }
        DictMaterialType entity = new DictMaterialType();
        entity.setTypeCode(upper(required(req.getTypeCode(), "材料类型编码"), "材料类型编码", 64));
        entity.setTypeName(limit(required(req.getTypeName(), "材料类型名称"), "材料类型名称", 120));
        entity.setRemarks(limit(optional(req.getRemarks()), "备注", 500));
        entity.setEnabled(req.getEnabled() == null ? before == null || Boolean.TRUE.equals(before.getEnabled()) : req.getEnabled());
        entity.setSortNo(req.getSortNo() == null ? before == null ? 0 : Objects.requireNonNullElse(before.getSortNo(), 0) : req.getSortNo());
        return entity;
    }

    private DictMaterial normalizeMaterial(MaterialMasterSaveReq req, DictMaterial before) {
        if (req == null) {
            throw new IllegalArgumentException("材料请求不能为空");
        }
        DictMaterial entity = new DictMaterial();
        entity.setMaterialCode(limit(required(req.getMaterialCode(), "材料编码"), "材料编码", 80));
        entity.setMaterialName(limit(required(req.getMaterialName(), "材料名称"), "材料名称", 100));
        if (req.getMaterialTypeId() == null) {
            throw new IllegalArgumentException("材料类型不能为空");
        }
        entity.setMaterialTypeId(req.getMaterialTypeId());
        entity.setMaterialCategory(limit(optional(req.getMaterialCategory()), "材质分类", 80));
        entity.setDensity(nonNegative(req.getDensity(), "默认密度"));
        entity.setPlateDensity(nonNegative(req.getPlateDensity(), "板材密度"));
        entity.setPlatePrice(nonNegative(req.getPlatePrice(), "板材价格"));
        entity.setBarDensity(nonNegative(req.getBarDensity(), "棒材密度"));
        entity.setBarPrice(nonNegative(req.getBarPrice(), "棒材价格"));
        entity.setTubeDensity(nonNegative(req.getTubeDensity(), "管材密度"));
        entity.setTubePrice(nonNegative(req.getTubePrice(), "管材价格"));
        if (entity.getPlateDensity() == null) {
            entity.setPlateDensity(entity.getDensity());
        }
        if (entity.getBarDensity() == null) {
            entity.setBarDensity(entity.getDensity());
        }
        if (entity.getTubeDensity() == null) {
            entity.setTubeDensity(entity.getDensity());
        }
        if (entity.getDensity() == null) {
            entity.setDensity(firstNonNull(entity.getPlateDensity(), entity.getBarDensity(), entity.getTubeDensity()));
        }
        entity.setRemarks(optional(req.getRemarks()));
        copyMesSourceFields(entity, before);
        entity.setEnabled(req.getEnabled() == null ? before == null || Boolean.TRUE.equals(before.getEnabled()) : req.getEnabled());
        entity.setSortNo(req.getSortNo() == null ? before == null ? 0 : Objects.requireNonNullElse(before.getSortNo(), 0) : req.getSortNo());
        return entity;
    }

    private void validateMesMaterialIdentityUnchanged(DictMaterial before, MaterialMasterSaveReq req) {
        if (before == null || !isMesSource(before.getSourceType()) || req == null) {
            return;
        }
        if (!Objects.equals(optional(before.getMaterialCode()), optional(req.getMaterialCode()))
                || !Objects.equals(optional(before.getMaterialName()), optional(req.getMaterialName()))
                || !Objects.equals(before.getMaterialTypeId(), req.getMaterialTypeId())
                || !Objects.equals(optional(before.getMaterialCategory()), optional(req.getMaterialCategory()))) {
            throw new IllegalArgumentException("MES material identity fields are read-only in QPO; update MES and sync again");
        }
    }

    private void copyMesSourceFields(DictMaterial target, DictMaterial before) {
        if (before == null) {
            target.setSourceType(SOURCE_LOCAL);
            return;
        }
        target.setSourceType(isBlank(before.getSourceType()) ? SOURCE_LOCAL : before.getSourceType());
        target.setMesExternalId(before.getMesExternalId());
        target.setMesMaterialType(before.getMesMaterialType());
        target.setMesPurchaseType(before.getMesPurchaseType());
        target.setMesProductionType(before.getMesProductionType());
        target.setMesDocNum(before.getMesDocNum());
        target.setMesStandard(before.getMesStandard());
        target.setMesWeight(before.getMesWeight());
        target.setMesDensityRaw(before.getMesDensityRaw());
        target.setMesSyncedAt(before.getMesSyncedAt());
    }

    private DictMaterialType requireMaterialType(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("材料类型 ID 不能为空");
        }
        DictMaterialType type = materialTypeMapper.selectById(id);
        if (type == null) {
            throw new IllegalArgumentException("材料类型不存在");
        }
        return type;
    }

    private DictMaterialType requireEnabledMaterialType(Long id) {
        DictMaterialType type = requireMaterialType(id);
        if (!Boolean.TRUE.equals(type.getEnabled())) {
            throw new IllegalArgumentException("材料类型已停用，不能用于新材料");
        }
        return type;
    }

    private DictMaterial requireMaterial(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("材料 ID 不能为空");
        }
        DictMaterial material = materialMapper.selectById(id);
        if (material == null) {
            throw new IllegalArgumentException("材料不存在");
        }
        return material;
    }

    private void ensureMaterialTypeCodeUnique(String typeCode, Long id) {
        QueryWrapper<DictMaterialType> query = new QueryWrapper<>();
        query.eq("type_code", typeCode);
        if (id != null) {
            query.ne("id", id);
        }
        Long count = materialTypeMapper.selectCount(query);
        if (count != null && count > 0) {
            throw new IllegalArgumentException("材料类型编码已存在");
        }
    }

    private void ensureMaterialTypeNameUnique(String typeName, Long id) {
        QueryWrapper<DictMaterialType> query = new QueryWrapper<>();
        query.eq("type_name", typeName);
        if (id != null) {
            query.ne("id", id);
        }
        Long count = materialTypeMapper.selectCount(query);
        if (count != null && count > 0) {
            throw new IllegalArgumentException("材料类型名称已存在");
        }
    }

    private void ensureMaterialCodeUnique(String materialCode, Long id) {
        QueryWrapper<DictMaterial> query = new QueryWrapper<>();
        query.apply("UPPER(material_code) = {0}", materialCode.toUpperCase(Locale.ROOT));
        if (id != null) {
            query.ne("id", id);
        }
        Long count = materialMapper.selectCount(query);
        if (count != null && count > 0) {
            throw new IllegalArgumentException("材料编码已存在");
        }
    }

    private MaterialTypeDTO toTypeDTO(DictMaterialType entity) {
        MaterialTypeDTO dto = new MaterialTypeDTO();
        dto.setId(entity.getId());
        dto.setTypeCode(entity.getTypeCode());
        dto.setTypeName(entity.getTypeName());
        dto.setRemarks(entity.getRemarks());
        dto.setEnabled(entity.getEnabled());
        dto.setSortNo(entity.getSortNo());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    private MaterialMasterDTO toMaterialDTO(DictMaterial material, DictMaterialType type) {
        MaterialMasterDTO dto = new MaterialMasterDTO();
        dto.setId(material.getId());
        dto.setMaterialCode(material.getMaterialCode());
        dto.setMaterialName(material.getMaterialName());
        dto.setMaterialTypeId(material.getMaterialTypeId());
        dto.setMaterialTypeCode(type == null ? null : type.getTypeCode());
        dto.setMaterialTypeName(type == null ? null : type.getTypeName());
        dto.setMaterialCategory(material.getMaterialCategory());
        dto.setDensity(material.getDensity());
        dto.setPlateDensity(material.getPlateDensity());
        dto.setPlatePrice(material.getPlatePrice());
        dto.setBarDensity(material.getBarDensity());
        dto.setBarPrice(material.getBarPrice());
        dto.setTubeDensity(material.getTubeDensity());
        dto.setTubePrice(material.getTubePrice());
        dto.setRemarks(material.getRemarks());
        dto.setSourceType(material.getSourceType() == null || material.getSourceType().isBlank() ? SOURCE_LOCAL : material.getSourceType());
        dto.setMesExternalId(material.getMesExternalId());
        dto.setMesMaterialType(material.getMesMaterialType());
        dto.setMesPurchaseType(material.getMesPurchaseType());
        dto.setMesProductionType(material.getMesProductionType());
        dto.setMesDocNum(material.getMesDocNum());
        dto.setMesStandard(material.getMesStandard());
        dto.setMesWeight(material.getMesWeight());
        dto.setMesDensityRaw(material.getMesDensityRaw());
        dto.setMesSyncedAt(material.getMesSyncedAt());
        dto.setEnabled(material.getEnabled());
        dto.setSortNo(material.getSortNo());
        dto.setCreatedAt(material.getCreatedAt());
        dto.setUpdatedAt(material.getUpdatedAt());
        return dto;
    }

    private Map<String, Object> materialTypeAuditSummary(DictMaterialType type) {
        if (type == null) {
            return null;
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", type.getId());
        summary.put("typeCode", type.getTypeCode());
        summary.put("typeName", type.getTypeName());
        summary.put("enabled", type.getEnabled());
        return summary;
    }

    private Map<String, Object> materialAuditSummary(DictMaterial material, DictMaterialType type) {
        if (material == null) {
            return null;
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", material.getId());
        summary.put("materialCode", material.getMaterialCode());
        summary.put("materialName", material.getMaterialName());
        summary.put("materialTypeId", material.getMaterialTypeId());
        summary.put("materialTypeCode", type == null ? null : type.getTypeCode());
        summary.put("materialTypeName", type == null ? null : type.getTypeName());
        summary.put("materialCategory", material.getMaterialCategory());
        summary.put("enabled", material.getEnabled());
        return summary;
    }

    private DictMaterialType copyType(DictMaterialType source) {
        DictMaterialType target = new DictMaterialType();
        target.setId(source.getId());
        target.setTypeCode(source.getTypeCode());
        target.setTypeName(source.getTypeName());
        target.setRemarks(source.getRemarks());
        target.setEnabled(source.getEnabled());
        target.setSortNo(source.getSortNo());
        target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedAt(source.getUpdatedAt());
        return target;
    }

    private DictMaterial copyMaterial(DictMaterial source) {
        DictMaterial target = new DictMaterial();
        target.setId(source.getId());
        target.setMaterialCode(source.getMaterialCode());
        target.setMaterialName(source.getMaterialName());
        target.setMaterialTypeId(source.getMaterialTypeId());
        target.setMaterialCategory(source.getMaterialCategory());
        target.setDensity(source.getDensity());
        target.setPlateDensity(source.getPlateDensity());
        target.setPlatePrice(source.getPlatePrice());
        target.setBarDensity(source.getBarDensity());
        target.setBarPrice(source.getBarPrice());
        target.setTubeDensity(source.getTubeDensity());
        target.setTubePrice(source.getTubePrice());
        target.setRemarks(source.getRemarks());
        target.setSourceType(source.getSourceType());
        target.setMesExternalId(source.getMesExternalId());
        target.setMesMaterialType(source.getMesMaterialType());
        target.setMesPurchaseType(source.getMesPurchaseType());
        target.setMesProductionType(source.getMesProductionType());
        target.setMesDocNum(source.getMesDocNum());
        target.setMesStandard(source.getMesStandard());
        target.setMesWeight(source.getMesWeight());
        target.setMesDensityRaw(source.getMesDensityRaw());
        target.setMesSyncedAt(source.getMesSyncedAt());
        target.setEnabled(source.getEnabled());
        target.setSortNo(source.getSortNo());
        target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedAt(source.getUpdatedAt());
        return target;
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isMesSource(String value) {
        return SOURCE_MES.equalsIgnoreCase(optional(value));
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

    private Double nonNegative(Double value, String label) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(label + "不能小于 0");
        }
        return value;
    }

    private Double firstNonNull(Double... values) {
        for (Double value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
