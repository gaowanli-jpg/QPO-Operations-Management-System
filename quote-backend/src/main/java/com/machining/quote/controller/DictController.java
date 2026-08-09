package com.machining.quote.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.config.RequirePermission;
import com.machining.quote.dto.MaterialMasterDTO;
import com.machining.quote.dto.MaterialMasterSaveReq;
import com.machining.quote.dto.MaterialTypeDTO;
import com.machining.quote.dto.MaterialTypeSaveReq;
import com.machining.quote.entity.DictAssemblyMarkupFee;
import com.machining.quote.entity.DictExtraFeeItem;
import com.machining.quote.entity.DictMarkupFee;
import com.machining.quote.mapper.DictAssemblyMarkupFeeMapper;
import com.machining.quote.mapper.DictExtraFeeItemMapper;
import com.machining.quote.mapper.DictMarkupFeeMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.MaterialMasterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dict")
public class DictController {
    @Autowired
    private MaterialMasterService materialMasterService;
    @Autowired
    private DictMarkupFeeMapper markupFeeMapper;
    @Autowired
    private DictAssemblyMarkupFeeMapper assemblyMarkupFeeMapper;
    @Autowired
    private DictExtraFeeItemMapper extraFeeItemMapper;
    @Autowired
    private AuditService auditService;

    @GetMapping("/materials")
    @RequirePermission("engineer:dict:view")
    public List<MaterialMasterDTO> listMaterials(
            @RequestParam(value = "enabledOnly", required = false) Boolean enabledOnly) {
        return materialMasterService.listMaterials(enabledOnly);
    }

    @PostMapping("/materials")
    @RequirePermission("engineer:dict:material-manage")
    public Map<String, Object> createMaterial(@RequestBody MaterialMasterSaveReq material) {
        MaterialMasterDTO data = materialMasterService.saveMaterial(null, material);
        return Map.of("success", true, "message", "材料已新增", "data", data);
    }

    @PutMapping("/materials/{id}")
    @RequirePermission("engineer:dict:material-manage")
    public Map<String, Object> updateMaterial(@PathVariable Long id, @RequestBody MaterialMasterSaveReq material) {
        MaterialMasterDTO data = materialMasterService.saveMaterial(id, material);
        return Map.of("success", true, "message", "材料已更新", "data", data);
    }

    @DeleteMapping("/materials/{id}")
    @RequirePermission("engineer:dict:material-manage")
    public Map<String, Object> deleteMaterial(@PathVariable Long id) {
        materialMasterService.deleteMaterial(id);
        return Map.of("success", true, "message", "材料已停用");
    }

    @GetMapping("/material-types")
    @RequirePermission("engineer:dict:view")
    public List<MaterialTypeDTO> listMaterialTypes(
            @RequestParam(value = "enabledOnly", required = false) Boolean enabledOnly) {
        return materialMasterService.listMaterialTypes(enabledOnly);
    }

    @PostMapping("/material-types")
    @RequirePermission("engineer:dict:material-manage")
    public Map<String, Object> createMaterialType(@RequestBody MaterialTypeSaveReq req) {
        MaterialTypeDTO data = materialMasterService.saveMaterialType(null, req);
        return Map.of("success", true, "message", "材料类型已新增", "data", data);
    }

    @PutMapping("/material-types/{id}")
    @RequirePermission("engineer:dict:material-manage")
    public Map<String, Object> updateMaterialType(@PathVariable Long id, @RequestBody MaterialTypeSaveReq req) {
        MaterialTypeDTO data = materialMasterService.saveMaterialType(id, req);
        return Map.of("success", true, "message", "材料类型已更新", "data", data);
    }

    @DeleteMapping("/material-types/{id}")
    @RequirePermission("engineer:dict:material-manage")
    public Map<String, Object> deleteMaterialType(@PathVariable Long id) {
        materialMasterService.deleteMaterialType(id);
        return Map.of("success", true, "message", "材料类型已删除或停用");
    }

    @GetMapping("/markup-fees")
    @RequirePermission("engineer:dict:view")
    public List<DictMarkupFee> listMarkupFees() {
        QueryWrapper<DictMarkupFee> qw = new QueryWrapper<>();
        qw.orderByAsc("sort_no").orderByAsc("id");
        return markupFeeMapper.selectList(qw);
    }

    @PostMapping("/markup-fees")
    @RequirePermission("engineer:dict:fee-manage")
    public Map<String, Object> createMarkupFee(@RequestBody DictMarkupFee fee) {
        normalizeMarkupFee(fee);
        markupFeeMapper.insert(fee);
        auditService.record("DICT_MARKUP_FEE_CREATE", "dict_markup_fee", fee.getId(), null, fee);
        return Map.of("success", true, "message", "费用类已新增", "data", fee);
    }

    @PutMapping("/markup-fees/{id}")
    @RequirePermission("engineer:dict:fee-manage")
    public Map<String, Object> updateMarkupFee(@PathVariable Integer id, @RequestBody DictMarkupFee fee) {
        DictMarkupFee before = markupFeeMapper.selectById(id);
        fee.setId(id);
        normalizeMarkupFee(fee);
        markupFeeMapper.updateById(fee);
        auditService.record("DICT_MARKUP_FEE_UPDATE", "dict_markup_fee", id, before, fee);
        return Map.of("success", true, "message", "费用类已更新", "data", fee);
    }

    @DeleteMapping("/markup-fees/{id}")
    @RequirePermission("engineer:dict:fee-manage")
    public Map<String, Object> deleteMarkupFee(@PathVariable Integer id) {
        DictMarkupFee before = markupFeeMapper.selectById(id);
        markupFeeMapper.deleteById(id);
        auditService.record("DICT_MARKUP_FEE_DELETE", "dict_markup_fee", id, before, null);
        return Map.of("success", true, "message", "费用类已删除");
    }

    @GetMapping("/assembly-markup-fees")
    @RequirePermission("engineer:dict:view")
    public List<DictAssemblyMarkupFee> listAssemblyMarkupFees() {
        QueryWrapper<DictAssemblyMarkupFee> qw = new QueryWrapper<>();
        qw.orderByAsc("sort_no").orderByAsc("id");
        return assemblyMarkupFeeMapper.selectList(qw);
    }

    @PostMapping("/assembly-markup-fees")
    @RequirePermission("engineer:dict:fee-manage")
    public Map<String, Object> createAssemblyMarkupFee(@RequestBody DictAssemblyMarkupFee fee) {
        normalizeAssemblyMarkupFee(fee);
        assemblyMarkupFeeMapper.insert(fee);
        auditService.record("DICT_ASSEMBLY_MARKUP_FEE_CREATE", "dict_assembly_markup_fee", fee.getId(), null, fee);
        return Map.of("success", true, "message", "组装件费用类已新增", "data", fee);
    }

    @PutMapping("/assembly-markup-fees/{id}")
    @RequirePermission("engineer:dict:fee-manage")
    public Map<String, Object> updateAssemblyMarkupFee(@PathVariable Integer id, @RequestBody DictAssemblyMarkupFee fee) {
        DictAssemblyMarkupFee before = assemblyMarkupFeeMapper.selectById(id);
        fee.setId(id);
        normalizeAssemblyMarkupFee(fee);
        assemblyMarkupFeeMapper.updateById(fee);
        auditService.record("DICT_ASSEMBLY_MARKUP_FEE_UPDATE", "dict_assembly_markup_fee", id, before, fee);
        return Map.of("success", true, "message", "组装件费用类已更新", "data", fee);
    }

    @DeleteMapping("/assembly-markup-fees/{id}")
    @RequirePermission("engineer:dict:fee-manage")
    public Map<String, Object> deleteAssemblyMarkupFee(@PathVariable Integer id) {
        DictAssemblyMarkupFee before = assemblyMarkupFeeMapper.selectById(id);
        assemblyMarkupFeeMapper.deleteById(id);
        auditService.record("DICT_ASSEMBLY_MARKUP_FEE_DELETE", "dict_assembly_markup_fee", id, before, null);
        return Map.of("success", true, "message", "组装件费用类已删除");
    }

    @GetMapping("/extra-fee-items")
    @RequirePermission("engineer:dict:view")
    public List<DictExtraFeeItem> listExtraFeeItems() {
        QueryWrapper<DictExtraFeeItem> qw = new QueryWrapper<>();
        qw.eq("enabled", true).orderByAsc("sort_no").orderByAsc("id");
        return extraFeeItemMapper.selectList(qw);
    }

    @GetMapping("/extra-fee-items/all")
    @RequirePermission("engineer:dict:view")
    public List<DictExtraFeeItem> listAllExtraFeeItems() {
        QueryWrapper<DictExtraFeeItem> qw = new QueryWrapper<>();
        qw.orderByAsc("sort_no").orderByAsc("id");
        return extraFeeItemMapper.selectList(qw);
    }

    @PostMapping("/extra-fee-items")
    @RequirePermission("engineer:dict:fee-manage")
    public Map<String, Object> createExtraFeeItem(@RequestBody DictExtraFeeItem item) {
        normalizeExtraFeeItem(item);
        extraFeeItemMapper.insert(item);
        auditService.record("DICT_EXTRA_FEE_CREATE", "dict_extra_fee_item", item.getId(), null, item);
        return Map.of("success", true, "message", "非工时费用工艺已新增", "data", item);
    }

    @PutMapping("/extra-fee-items/{id}")
    @RequirePermission("engineer:dict:fee-manage")
    public Map<String, Object> updateExtraFeeItem(@PathVariable Integer id, @RequestBody DictExtraFeeItem item) {
        DictExtraFeeItem before = extraFeeItemMapper.selectById(id);
        item.setId(id);
        normalizeExtraFeeItem(item);
        extraFeeItemMapper.updateById(item);
        auditService.record("DICT_EXTRA_FEE_UPDATE", "dict_extra_fee_item", id, before, item);
        return Map.of("success", true, "message", "非工时费用工艺已更新", "data", item);
    }

    @DeleteMapping("/extra-fee-items/{id}")
    @RequirePermission("engineer:dict:fee-manage")
    public Map<String, Object> deleteExtraFeeItem(@PathVariable Integer id) {
        DictExtraFeeItem before = extraFeeItemMapper.selectById(id);
        extraFeeItemMapper.deleteById(id);
        auditService.record("DICT_EXTRA_FEE_DELETE", "dict_extra_fee_item", id, before, null);
        return Map.of("success", true, "message", "非工时费用工艺已删除");
    }

    private void normalizeMarkupFee(DictMarkupFee fee) {
        if (isBlank(fee.getFeeName())) {
            throw new RuntimeException("费用名称不能为空");
        }
        fee.setFeeName(fee.getFeeName().trim());
        if (fee.getRatePercent() == null) {
            fee.setRatePercent(0.0);
        }
        if (fee.getSortNo() == null) {
            fee.setSortNo(0);
        }
        if (fee.getEnabled() == null) {
            fee.setEnabled(true);
        }
    }

    private void normalizeAssemblyMarkupFee(DictAssemblyMarkupFee fee) {
        if (isBlank(fee.getFeeName())) {
            throw new RuntimeException("费用名称不能为空");
        }
        fee.setFeeName(fee.getFeeName().trim());
        if (fee.getRatePercent() == null) {
            fee.setRatePercent(0.0);
        }
        if (fee.getSortNo() == null) {
            fee.setSortNo(0);
        }
        if (fee.getEnabled() == null) {
            fee.setEnabled(true);
        }
    }

    private void normalizeExtraFeeItem(DictExtraFeeItem item) {
        if (isBlank(item.getFeeName())) {
            throw new RuntimeException("工艺名称不能为空");
        }
        item.setFeeName(item.getFeeName().trim());
        if (item.getSortNo() == null) {
            item.setSortNo(0);
        }
        if (item.getEnabled() == null) {
            item.setEnabled(true);
        }
        if (item.getRemarks() == null) {
            item.setRemarks("");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
