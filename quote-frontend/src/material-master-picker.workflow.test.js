import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { hasLegacyMaterialIdentity, normalizeGlobalConfig } from './stores/globalConfigStore'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

const optionPicker = readSource('components', 'EngineeringOptionPickerDialog.vue')
const globalSetup = readSource('components', 'GlobalSetup.vue')
const itemPricing = readSource('components', 'ItemPricing.vue')

describe('material master picker workflow and global defaults', () => {
  test('global config keeps coded material snapshots and marks name-only local cache as legacy', () => {
    const coded = normalizeGlobalConfig({
      materialCode: 'MAT-000001',
      materialName: '45#',
      materialTypeId: 6,
      materialTypeCode: 'RAW_MATERIAL',
      materialTypeName: '原材料',
      materialCategory: '钢铁',
      priceSpecCode: 'SPEC_1',
      priceSpecs: [{ specCode: 'SPEC_1', unifiedPrice: 7.5 }],
      shape: '棒材',
      processRoute: []
    })

    expect(coded).toEqual(expect.objectContaining({
      materialCode: 'MAT-000001',
      materialName: '45#',
      materialTypeId: 6,
      materialTypeCode: 'RAW_MATERIAL',
      materialTypeName: '原材料',
      materialCategory: '钢铁',
      priceSpecCode: 'SPEC_1',
      priceSpecName: '规格一',
      legacyMaterialIdentity: false
    }))
    expect(coded.priceSpec).toEqual(expect.objectContaining({
      specCode: 'SPEC_1',
      unifiedPrice: 7.5
    }))

    const legacy = normalizeGlobalConfig({ materialName: '45#', shape: '板材', processRoute: [] })

    expect(legacy).toEqual(expect.objectContaining({
      materialCode: '',
      materialName: '45#',
      legacyMaterialIdentity: true
    }))
    expect(hasLegacyMaterialIdentity(legacy)).toBe(true)
  })

  test('material picker identifies materials by material code and renders remote material fields', () => {
    expect(optionPicker).toContain("props.type === 'material' && props.enableMaterialPriceEdit ? '1380px' : '1180px'")
    expect(optionPicker).toContain('prop="materialCode"')
    expect(optionPicker).toContain('label="材料编码"')
    expect(optionPicker).toContain('prop="materialName"')
    expect(optionPicker).toContain('label="材料名称"')
    expect(optionPicker).toContain('materialTypeName')
    expect(optionPicker).not.toContain('row.materialTypeCode')
    expect(optionPicker).toContain('label="具体密度"')
    expect(optionPicker).toContain('label="MES密度"')
    expect(optionPicker).toContain('label="实际使用密度"')
    expect(optionPicker).toContain('MATERIAL_PRICE_SPEC_DEFINITIONS')
    expect(optionPicker).toContain('选择{{ definition.specName }}')
    expect(optionPicker).toContain('formatEffectiveMaterialDensity(row)')
    expect(optionPicker).not.toContain('row.plateDensity')
    expect(optionPicker).toContain('materialCategory')
    expect(optionPicker).toContain('categoryName')
    expect(optionPicker).toContain('item?.materialCode')
    expect(optionPicker).toContain('item.materialName')
    expect(optionPicker).toContain('item.materialTypeName')
    expect(optionPicker).toContain('item.categoryName')
    expect(optionPicker).toContain('row.remarks')
    expect(optionPicker).toContain('const pagedItems = computed(() => remoteRecords.value)')
    expect(optionPicker).not.toContain('item?.materialName ?? item?.id')
    expect(optionPicker).not.toContain('searchTextOf')
    expect(optionPicker).not.toContain('filteredItems')
    expect(optionPicker).not.toContain('props.items')
  })

  test('GlobalSetup saves default material by material code and refuses legacy name-only defaults', () => {
    expect(globalSetup).toContain(':selected-key="config.materialCode"')
    expect(globalSetup).toContain('config.materialCode = selectedMaterial.materialCode')
    expect(globalSetup).toContain('config.priceSpecCode = selectedMaterial.priceSpecCode')
    expect(globalSetup).toContain('config.materialTypeCode = selectedMaterial.materialTypeCode')
    expect(globalSetup).toContain('config.materialTypeName = selectedMaterial.materialTypeName')
    expect(globalSetup).toContain('config.materialCategory = selectedMaterial.materialCategory')
    expect(globalSetup).not.toContain('config.plateDensity')
    expect(globalSetup).not.toContain('config.barDensity')
    expect(globalSetup).not.toContain('config.tubeDensity')
    expect(globalSetup).toContain('hasUnresolvedMaterialIdentity')
    expect(globalSetup).toContain('材料模板缺少材料编码或价格规格')
    expect(globalSetup).not.toContain('item.materialName === config.materialName')
  })

  test('ItemPricing opens saved material snapshots before global defaults and submits material code', () => {
    expect(itemPricing).toContain(':selected-key="matForm.materialCode"')
    expect(itemPricing).toContain('matForm.materialCode')
    expect(itemPricing).toContain('matForm.materialTypeCode')
    expect(itemPricing).toContain('matForm.materialTypeName')
    expect(itemPricing).toContain('matForm.materialCategory')
    expect(itemPricing).toContain('applyMaterialIdentityToMatForm(material)')
    expect(itemPricing).toContain('id: matForm.materialId')
    expect(itemPricing).toContain('materialCode: matForm.materialCode')
    expect(itemPricing).toContain('materialName: matForm.materialName')
    expect(itemPricing).toContain('materialTypeCode: matForm.materialTypeCode')
    expect(itemPricing).toContain('materialTypeName: matForm.materialTypeName')
    expect(itemPricing).toContain('materialCategory: matForm.materialCategory')
    expect(itemPricing).toContain('savedMaterialData.materialCode')
    expect(itemPricing).not.toContain('plateDensity')
    expect(itemPricing).not.toContain('barDensity')
    expect(itemPricing).not.toContain('tubeDensity')
    expect(itemPricing).not.toContain('item.materialCode === matForm.materialCode')
    expect(itemPricing).not.toContain('find(item => item.materialName === matForm.materialName)')
  })
})
