import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8').replace(/\r\n/g, '\n')

const functionBlock = (source, declaration) => {
  const start = source.indexOf(declaration)
  expect(start).toBeGreaterThanOrEqual(0)
  const end = source.indexOf('\n}\n', start)
  expect(end).toBeGreaterThan(start)
  return source.slice(start, end)
}

describe('quote process supplemental confirmation workflow', () => {
  test('shows supplemental confirmation entry beside normal quote process order picker', () => {
    const source = readSource('components', 'QuoteProcess.vue')

    expect(source).toContain('补充报价待确认')
    expect(source).toContain('supplementalQuoteOrderPickerVisible')
    expect(source).toContain('openSupplementalQuoteOrderPicker')
    expect(source).toContain('handleSupplementalQuoteOrderSelected')
    expect(source).toContain('order-scope="quote-process-supplemental"')
    expect(source).toContain('customer-scope="quote-process"')
    expect(source).toContain('SUPPLEMENTAL_DRAWING_QUOTE_SCOPE')
  })

  test('supplemental entry loads summary items and lead-time operations with supplemental scope', () => {
    const source = readSource('components', 'QuoteProcess.vue')
    const summaryBlock = functionBlock(source, 'const fetchQuoteProcessSummary = async')
    const itemsBlock = functionBlock(source, 'const fetchQuoteProcessItems = async')
    const shiftBlock = functionBlock(source, 'const applyShift = async')
    const setBlock = functionBlock(source, 'const setQuoteLeadTime = async')
    const saveLeadTimeBlock = functionBlock(source, 'const saveLeadTime = async')
    const saveDraftsBlock = functionBlock(source, 'const saveAllQuoteLeadTimeDrafts = async')

    expect(summaryBlock).toContain('getQuoteProcessSummary(rfqId, currentQuoteProcessScope.value)')
    expect(itemsBlock).toContain('scope: currentQuoteProcessScope.value || undefined')
    expect(shiftBlock).toContain('scope: currentQuoteProcessScope.value || undefined')
    expect(setBlock).toContain('scope: currentQuoteProcessScope.value || undefined')
    expect(saveLeadTimeBlock).toContain('scope: currentQuoteProcessScope.value || undefined')
    expect(saveDraftsBlock).toContain('scope: currentQuoteProcessScope.value || undefined')
    expect(source).toContain(':scope="currentQuoteProcessScope"')
  })

  test('supplemental entry saves through supplemental endpoint while normal save stays unchanged', () => {
    const source = readSource('components', 'QuoteProcess.vue')
    const saveBlock = functionBlock(source, 'const saveMarketQuoteSheet = async')

    expect(source).toContain('saveSupplementalQuoteSheet as saveSupplementalQuoteSheetApi')
    expect(saveBlock).toContain('isSupplementalQuoteProcessScope.value')
    expect(saveBlock).toContain('await saveSupplementalQuoteSheetApi(selectedRfqId.value)')
    expect(saveBlock).toContain('await saveQuoteSheetApi(selectedRfqId.value)')
    expect(saveBlock.indexOf('await saveSupplementalQuoteSheetApi(selectedRfqId.value)'))
      .toBeLessThan(saveBlock.indexOf('await saveQuoteSheetApi(selectedRfqId.value)'))
  })

  test('order picker and market api expose supplemental quote process order source', () => {
    const orderPicker = readSource('components', 'OrderPickerDialog.vue')
    const marketApi = readSource('services', 'marketApi.js')

    expect(orderPicker).toContain("'quote-process-supplemental': ['已报价', '部分已提单', '已生成提单']")
    expect(orderPicker).toContain("'quote-process-supplemental': '补充报价待确认 RFQ 订单'")
    expect(marketApi).toContain("'quote-process-supplemental': '/api/market/quote-process/supplemental-drawing/orders'")
    expect(marketApi).toContain('saveSupplementalQuoteSheet')
  })
})
