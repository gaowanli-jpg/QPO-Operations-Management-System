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
