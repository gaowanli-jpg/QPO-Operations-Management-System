import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

describe('assembly pricing fee dictionary', () => {
  test('material process page exposes assembly scope inside unified pricing fee tab', () => {
    const source = readSource('components', 'MaterialProcessManage.vue')

    expect(source).toContain('label="百分比费用"')
    expect(source).toContain('name="pricingMarkupFees"')
    expect(source).toContain("{ value: 'ASSEMBLY', label: '组装件' }")
    expect(source).toContain('pricingMarkupScope')
    expect(source).toContain('pricingMarkupFeeCategory')
    expect(source).toContain('openPricingMarkupFeeDialog')
    expect(source).toContain('savePricingMarkupFee')
    expect(source).toContain('deletePricingMarkupFee')
    expect(source).toContain("v-permission=\"'engineer:dict:fee-manage'\"")
  })

  test('item pricing preview uses assembly-scoped pricing fees without transport-name filtering', () => {
    const source = readSource('components', 'ItemPricing.vue')

    expect(source).toContain("const assemblyMarkupFeeOptions = computed(() => dictionaryStore.getPricingMarkupFees('ASSEMBLY'))")
    expect(source).toContain('const assemblyPreviewPricingResult = computed(() => calculatePreviewPriceByFeeCategory(')
    expect(source).toContain('numberOrZero(assemblyFee.value),')
    expect(source).toContain('effectiveAssemblyMarkupFeeOptions.value,')
    expect(source).toContain("'ASSEMBLY'")
    expect(source).toContain('const assemblyFeeMarkupRows = computed(() => assemblyPreviewPricingResult.value.feeRows)')
    expect(source).not.toContain('isTransportMarkupFee')
    expect(source).not.toContain("title=\"组合件父件价格 = 子件含税单价汇总 + 组装费 + 组装费对应的管理费/利润/税费；组装费不收运输费。\"")
  })
})
