import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

describe('ItemPricing realtime normal price preview', () => {
  test('shows normal item untaxed/tax-included unit and total previews from cached inputs', () => {
    const source = readSource('components', 'ItemPricing.vue')
    const materialSummary = source.slice(
      source.indexOf('class="material-summary-table"'),
      source.indexOf('</table>', source.indexOf('class="material-summary-table"'))
    )

    expect(materialSummary).toContain('\u672a\u7a0e\u5355\u4ef7')
    expect(materialSummary).toContain('\u542b\u7a0e\u5355\u4ef7')
    expect(materialSummary).toContain('\u672a\u7a0e\u91d1\u989d')
    expect(materialSummary).toContain('\u542b\u7a0e\u91d1\u989d')
    expect(materialSummary).toContain('quoteMode === \'NORMAL\'')
    expect(materialSummary).toContain('normalUntaxedUnitPricePreview.toFixed(2)')
    expect(materialSummary).toContain('normalTaxIncludedUnitPricePreview.toFixed(2)')
    expect(materialSummary).toContain('normalUntaxedTotalPricePreview.toFixed(2)')
    expect(materialSummary).toContain('normalTaxIncludedTotalPricePreview.toFixed(2)')

    expect(source).toContain("const markupFeeOptions = computed(() => dictionaryStore.getPricingMarkupFees('NORMAL'))")
    expect(source).toContain('const currentItemQty = computed(() => numberOrZero(currentRow.value?.qty))')
    expect(source).toContain('const normalProcessCostPreview = computed(() =>')
    expect(source).toContain('processRoute.value.reduce')
    expect(source).toContain('option?.hourlyRate ?? proc.hourlyRate ?? proc.unitRate')
    expect(source).toContain('inputVal * hourlyRate')
    expect(source).toContain('const normalBaseCostPreview = computed(() => calculatedMaterialCost.value + normalProcessCostPreview.value)')
    expect(source).toContain("const CUSTOMER_WEIGHT = 'CUSTOMER_WEIGHT'")
    expect(source).toContain("const BASIC_PERCENT = 'BASIC_PERCENT'")
    expect(source).toContain("const TAX = 'TAX'")
    expect(source).toContain('const UNTAXED_PRICING_FEE_SEQUENCE = [CUSTOMER_WEIGHT, BASIC_PERCENT]')
    expect(source).toContain('const TAX_INCLUDED_PRICING_FEE_SEQUENCE = [CUSTOMER_WEIGHT, BASIC_PERCENT, TAX]')
    expect(source).toContain('const calculatePreviewPriceByFeeCategory = (baseAmount, fees, pricingScope) =>')
    expect(source).toContain('fee.feeCategory === feeCategory')
    expect(source).toContain('fee.pricingScope === pricingScope')
    expect(source).toContain('const normalPreviewPricingResult = computed(() => calculatePreviewPriceByFeeCategory(')
    expect(source).toContain('normalBaseCostPreview.value,')
    expect(source).toContain("markupFeeOptions.value,")
    expect(source).toContain("'NORMAL'")
    expect(source).toContain('const normalMarkupFeeRowsPreview = computed(() => normalPreviewPricingResult.value.feeRows)')
    expect(source).toContain('const normalTaxIncludedUnitPricePreview = computed(() => normalPreviewPricingResult.value.taxIncludedUnitPrice)')
    expect(source).toContain('const normalUntaxedUnitPricePreview = computed(() => normalPreviewPricingResult.value.untaxedUnitPrice)')
    expect(source).not.toContain('const isTaxFeeName = (feeName) =>')
    expect(source).not.toContain("text.includes('税')")
    expect(source).not.toContain("text.includes('tax')")
    expect(source).not.toContain("text.includes('vat')")
    expect(source).toContain('normalTaxIncludedUnitPricePreview.value * currentItemQty.value')
    expect(source).toContain('normalUntaxedUnitPricePreview.value * currentItemQty.value')
  })

  test('does not refresh dictionary cache after saving one item quote', () => {
    const source = readSource('components', 'ItemPricing.vue')
    const submitBlock = source.slice(
      source.indexOf('const submitCurrentQuote'),
      source.indexOf('</script>')
    )

    expect(submitBlock).toContain('await submitQuote(payload)')
    expect(submitBlock).toContain('await fetchQuoteData({ keepScroll: true })')
    expect(submitBlock).not.toContain('fetchDicts()')
    expect(submitBlock).not.toContain('loadBaseDicts')
  })
})
