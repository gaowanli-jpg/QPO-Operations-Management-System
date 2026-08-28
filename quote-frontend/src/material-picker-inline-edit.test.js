import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

describe('material picker inline density and price editing', () => {
  test('material picker exposes specific, MES, and effective density with editable material pricing fields', () => {
    const picker = readSource('components', 'EngineeringOptionPickerDialog.vue')

    expect(picker).toContain("import { saveMaterial, saveProcessDetail } from '../services/dictApi'")
    expect(picker).toContain('enableMaterialPriceEdit')
    expect(picker).toContain("'material-price-updated'")
    expect(picker).toContain('label="具体密度"')
    expect(picker).toContain('label="MES密度"')
    expect(picker).toContain('label="实际使用密度"')
    expect(picker).toContain("materialPriceDraft(row, 'density')")
    expect(picker).toContain("setMaterialPriceDraft(row, 'density', value)")
    expect(picker).toContain("materialPriceDraft(row, 'platePrice')")
    expect(picker).toContain("materialPriceDraft(row, 'barPrice')")
    expect(picker).toContain("materialPriceDraft(row, 'tubePrice')")
    expect(picker).toContain('saveInlineMaterialPrice(row)')
    expect(picker).toContain("v-permission=\"'engineer:dict:material-manage'\"")
    expect(picker).toContain('formatMesDensity(row.mesDensityRaw)')
    expect(picker).toContain('formatEffectiveMaterialDensity(row)')
    expect(picker).toContain("positiveNumberOrNull(materialPriceDraft(row, 'density')) ?? positiveNumberOrNull(row.mesDensityRaw)")
    expect(picker).toContain('formatDensityNumber')
    expect(picker).not.toContain("materialPriceDraft(row, 'mesDensityRaw')")
    expect(picker).not.toContain("setMaterialPriceDraft(row, 'mesDensityRaw'")
  })

  test('material picker keeps editable cells compact and moves type columns after save', () => {
    const picker = readSource('components', 'EngineeringOptionPickerDialog.vue')
    const materialBlock = picker.slice(
      picker.indexOf('<template v-if="type === \'material\'">'),
      picker.indexOf('<template v-else>')
    )

    expect(materialBlock.indexOf('label="具体密度"')).toBeGreaterThan(materialBlock.indexOf('label="材料名称"'))
    expect(materialBlock.indexOf('label="保存"')).toBeGreaterThan(materialBlock.indexOf('label="管材价"'))
    expect(materialBlock.indexOf('prop="materialTypeName" label="材料类型"')).toBeGreaterThan(materialBlock.indexOf('label="保存"'))
    expect(materialBlock.indexOf('prop="materialCategory" label="材质分类"')).toBeGreaterThan(materialBlock.indexOf('prop="materialTypeName" label="材料类型"'))
    expect(materialBlock.indexOf('prop="remarks" label="备注"')).toBeGreaterThan(materialBlock.indexOf('prop="materialCategory" label="材质分类"'))

    expect(picker).toContain('class-name="material-edit-cell"')
    expect(picker).toContain('.option-picker-dialog :deep(.material-edit-cell .cell)')
    expect(picker).toContain('text-overflow: clip;')
    expect(picker).toContain('width: 100%;')
    expect(picker).toContain('max-width: 104px;')
    expect(picker).toContain('max-width: 116px;')
  })

  test('item pricing page enables material inline save and refreshes selected material preview', () => {
    const itemPricing = readSource('components', 'ItemPricing.vue')

    expect(itemPricing).toContain(':enable-material-price-edit="true"')
    expect(itemPricing).toContain('@material-price-updated="handleMaterialPriceUpdated"')
    expect(itemPricing).toContain('const handleMaterialPriceUpdated = (updatedMaterial) =>')
    expect(itemPricing).toContain('cachePricingMaterialOptions(updatedMaterial ? [updatedMaterial] : [])')
    expect(itemPricing).toContain('materialCodeKey(updatedMaterial?.materialCode) !== materialCodeKey(matForm.materialCode)')
    expect(itemPricing).toContain('applyMaterialIdentityToMatForm(updatedMaterial)')
    expect(itemPricing).not.toContain('applyMaterialSnapshotToMatForm(updatedMaterial)')
  })
})
