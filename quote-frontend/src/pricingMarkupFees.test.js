import { describe, expect, test } from 'vitest'
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')
const oldTokens = [
  ['markup', 'Fees'].join(''),
  ['assembly', 'Markup', 'Fees'].join(''),
  ['list', 'Markup', 'Fees'].join(''),
  ['list', 'Assembly', 'Markup', 'Fees'].join('')
]

const collectProductionSources = (dir) => readdirSync(dir)
  .flatMap((name) => {
    const fullPath = resolve(dir, name)
    if (statSync(fullPath).isDirectory()) return collectProductionSources(fullPath)
    if (!/\.(js|vue)$/.test(name) || name.endsWith('.test.js')) return []
    return [fullPath]
  })

describe('pricing markup fees frontend contract', () => {
  test('dict api uses unified pricing markup fee endpoints', () => {
    const source = readSource('services', 'dictApi.js')

    expect(source).toContain('listPricingMarkupFees')
    expect(source).toContain('createPricingMarkupFee')
    expect(source).toContain('updatePricingMarkupFee')
    expect(source).toContain('deletePricingMarkupFee')
    expect(source).toContain('/api/dict/pricing-markup-fees')
    oldTokens.forEach((token) => expect(source).not.toContain(token))
  })

  test('material process page exposes categorized pricing markup fee maintenance', () => {
    const source = readSource('components', 'MaterialProcessManage.vue')

    expect(source).toContain('基础百分比费用')
    expect(source).toContain('税率')
    expect(source).toContain('客户权重')
    expect(source).toContain('pricingScope')
    expect(source).toContain('feeCategory')
    expect(source).toContain('systemRequired')
    expect(source).toContain('pricingMarkupScope')
    expect(source).toContain('pricingMarkupFeeCategory')
    expect(source).toContain('pagedPricingMarkupFees')
    expect(source).toContain('openPricingMarkupFeeDialog')
    expect(source).toContain('savePricingMarkupFee')
    expect(source).toContain('deletePricingMarkupFee')
    expect(source).toContain("pricingMarkupFeeCategory === 'BASIC_PERCENT'")
    expect(source).toContain('row.systemRequired')
  })

  test('frontend production code no longer depends on retired fee model names', () => {
    for (const fullPath of collectProductionSources(currentDir)) {
      const source = readFileSync(fullPath, 'utf8')
      oldTokens.forEach((token) => expect(source, fullPath).not.toContain(token))
    }
  })

  test('item pricing preview uses fee categories instead of fee names for tax handling', () => {
    const source = readSource('components', 'ItemPricing.vue')

    expect(source).toContain("const CUSTOMER_WEIGHT = 'CUSTOMER_WEIGHT'")
    expect(source).toContain("const BASIC_PERCENT = 'BASIC_PERCENT'")
    expect(source).toContain("const TAX = 'TAX'")
    expect(source).toContain('TAX_INCLUDED_PRICING_FEE_SEQUENCE = [CUSTOMER_WEIGHT, BASIC_PERCENT, TAX]')
    expect(source).toContain('UNTAXED_PRICING_FEE_SEQUENCE = [CUSTOMER_WEIGHT, BASIC_PERCENT]')
    expect(source).toContain('calculatePreviewPriceByFeeCategory')
    expect(source).toContain('fee.feeCategory === feeCategory')
    expect(source).toContain('sumPricingFeeRatePercent')
    expect(source).toContain('categoryBaseAmount')
    expect(source).toContain('categoryBaseAmount * (1 + categoryRatePercent / 100)')
    expect(source).not.toContain('isTaxFeeName')
    expect(source).not.toContain("includes('税')")
    expect(source).not.toContain("includes('tax')")
    expect(source).not.toContain("includes('vat')")
  })
})
