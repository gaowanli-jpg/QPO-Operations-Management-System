import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

const functionBlock = (source, declaration) => {
  const start = source.indexOf(declaration)
  expect(start).toBeGreaterThanOrEqual(0)
  const end = source.indexOf('\n}\n', start)
  expect(end).toBeGreaterThan(start)
  return source.slice(start, end)
}

describe('item pricing supplemental drawing workflow', () => {
  test('shows supplemental drawing pricing entry and reuses the order picker', () => {
    const source = readSource('components', 'ItemPricing.vue')

    expect(source).toContain('补图待核价')
    expect(source).toContain('openSupplementalDrawingOrderPicker')
    expect(source).toContain('supplementalDrawingOrderPickerVisible')
    expect(source).toContain('order-scope="engineer-supplemental-pricing"')
    expect(source).toContain('customer-scope="engineer-pricing"')
    expect(source).toContain('@select="handleSupplementalDrawingOrderSelected"')
  })

  test('loads only supplemental drawing items when opened from supplemental entry', () => {
    const source = readSource('components', 'ItemPricing.vue')
    const fetchItemsBlock = functionBlock(source, 'const fetchPricingItems = async')
    const selectedOrderBlock = functionBlock(source, 'const handleSupplementalDrawingOrderSelected = async')

    expect(source).toContain("const SUPPLEMENTAL_DRAWING_SCOPE = 'SUPPLEMENTAL_DRAWING'")
    expect(source).toContain('const isSupplementalDrawingScope = computed(() => pricingScope.value === SUPPLEMENTAL_DRAWING_SCOPE)')
    expect(fetchItemsBlock).toContain('scope: isSupplementalDrawingScope.value ? SUPPLEMENTAL_DRAWING_SCOPE : undefined')
    expect(selectedOrderBlock).toContain('scope: SUPPLEMENTAL_DRAWING_SCOPE')
    expect(selectedOrderBlock).toContain('await fetchQuoteData()')
  })

  test('supplemental completion calls supplemental API while normal completion stays unchanged', () => {
    const source = readSource('components', 'ItemPricing.vue')
    const completeBlock = functionBlock(source, 'const completeOrderQuote = async')

    expect(source).toContain('completeSupplementalDrawingQuote as completeSupplementalDrawingQuoteRequest')
    expect(completeBlock).toContain('if (isSupplementalDrawingScope.value)')
    expect(completeBlock).toContain('await completeSupplementalDrawingQuoteRequest(rfqId.value)')
    expect(completeBlock).toContain('补充报价待确认')
    expect(completeBlock).toContain('await completeOrderQuoteRequest(rfqId.value)')
    expect(completeBlock.indexOf('await completeSupplementalDrawingQuoteRequest(rfqId.value)'))
      .toBeLessThan(completeBlock.indexOf('await completeOrderQuoteRequest(rfqId.value)'))
  })

  test('order picker uses supplemental pricing API for supplemental scope', () => {
    const source = readSource('components', 'OrderPickerDialog.vue')

    expect(source).toContain('listSupplementalPricingOrders')
    expect(source).toContain("'engineer-supplemental-pricing': ['部分已提单', '已生成提单']")
    expect(source).toContain("props.orderScope === 'engineer-supplemental-pricing'")
    expect(source).toContain('await listSupplementalPricingOrders(params)')
  })
})
