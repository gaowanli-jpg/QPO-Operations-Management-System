import { describe, expect, test, vi } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { usePendingAction } from './composables/usePendingAction'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

const blockBetween = (source, start, end) => {
  const startIndex = source.indexOf(start)
  const endIndex = source.indexOf(end, startIndex)
  expect(startIndex, `Expected source to include start marker ${start}`).toBeGreaterThanOrEqual(0)
  expect(endIndex, `Expected source to include end marker ${end}`).toBeGreaterThan(startIndex)
  return source.slice(startIndex, endIndex)
}

const deferred = () => {
  let resolve
  const promise = new Promise((done) => {
    resolve = done
  })
  return { promise, resolve }
}

describe('engineering duplicate click guards', () => {
  test('global setup saves order defaults with an order-scoped pending key', () => {
    const source = readSource('components', 'GlobalSetup.vue')
    const saveBlock = blockBetween(source, 'const handleNext = async () =>', 'const openOrderPicker')

    expect(source).toContain("import { usePendingAction } from '../composables/usePendingAction'")
    expect(source).toContain('saveSetup: () => `engineer:setup:save-config:${orderInfo.value.id || \'none\'}`')
    expect(source).toContain('const setupSavePending = computed(() => isPending(engineeringSetupActionKeys.saveSetup()))')
    expect(source).toContain(':loading="setupSavePending"')
    expect(source).toContain(':disabled="!orderInfo.id || setupSavePending || isLoading"')
    expect(saveBlock).toContain('return runAction(engineeringSetupActionKeys.saveSetup(), async () =>')
    expect(saveBlock).toContain('await saveOrderConfig(orderInfo.value.id, nextConfig)')
  })

  test('item pricing registers pending keys for repeat reuse, item save, refresh, complete and export', () => {
    const source = readSource('components', 'ItemPricing.vue')

    expect(source).toContain("import { usePendingAction } from '../composables/usePendingAction'")
    expect(source).toContain('const { isPending, runAction } = usePendingAction()')
    expect(source).toContain('repeatApply: () => `engineer:pricing:repeat-quote:${rfqId.value || \'none\'}`')
    expect(source).toContain('refreshOrder: () => `engineer:pricing:refresh:${rfqId.value || \'none\'}`')
    expect(source).toContain('completeOrder: () => `engineer:pricing:complete-order:${rfqId.value || \'none\'}`')
    expect(source).toContain('exportSheet: () => `engineer:pricing:export-sheet:${rfqId.value || \'none\'}`')
    expect(source).toContain('saveItem: (itemId) => `engineer:pricing:save-item:${itemId || \'none\'}`')
  })

  test('item pricing buttons expose pending loading and same-order conflict disabled states', () => {
    const source = readSource('components', 'ItemPricing.vue')

    expect(source).toContain(':loading="exportPricingSheetLoading"')
    expect(source).toContain(':disabled="!canExportPricingSheet || exportPricingSheetLoading"')
    expect(source).toContain(':disabled="!canRefreshQuotes || refreshLoading || completeLoading || repeatApplyLoading"')
    expect(source).toContain(':disabled="!canCompleteQuote || completeLoading || refreshLoading || repeatApplyLoading"')
    expect(source).toContain(':disabled="orderPricingActionPending"')
    expect(source).toContain(':loading="isItemQuoteSaving(scope.row)"')
    expect(source).toContain(':disabled="submitLoading || completeLoading || refreshLoading"')
  })

  test('item pricing wraps long-running pricing actions with runAction guards', () => {
    const source = readSource('components', 'ItemPricing.vue')

    const repeatBlock = blockBetween(source, 'const applyRepeatQuotesOnPricingLoad = async () =>', 'const loadRepeatAnalysis')
    expect(repeatBlock).toContain('return runAction(pricingActionKeys.repeatApply(), async () =>')
    expect(repeatBlock).toContain('await applyRepeatQuotes(rfqId.value)')

    const exportBlock = blockBetween(source, 'const exportPricingSheet = async () =>', 'const completeOrderQuote')
    expect(exportBlock).toContain('return runAction(pricingActionKeys.exportSheet(), async () =>')
    expect(exportBlock).toContain('await downloadPricingSheet(rfqId.value, fallbackName)')

    const completeBlock = blockBetween(source, 'const completeOrderQuote = async () =>', 'const refreshOrderQuotes')
    expect(completeBlock).toContain('return runAction(pricingActionKeys.completeOrder(), async () =>')
    expect(completeBlock).toContain('await completeOrderQuoteRequest(rfqId.value)')

    const refreshBlock = blockBetween(source, 'const refreshOrderQuotes = async () =>', 'const pdfVisible')
    expect(refreshBlock).toContain('return runAction(pricingActionKeys.refreshOrder(), async () =>')
    expect(refreshBlock).toContain('await refreshEngineerQuotes(rfqId.value)')
    expect(refreshBlock).not.toContain('await waitSystemTask')

    const submitBlock = blockBetween(source, 'const submitCurrentQuote = async () =>', '</script>')
    expect(submitBlock).toContain('return runAction(pricingActionKeys.saveItem(currentRow.value.id), async () =>')
    expect(submitBlock).toContain('await submitQuote(payload)')
  })

  test('item save key suppresses duplicate submitQuote calls while pending', async () => {
    const { runAction } = usePendingAction()
    const pendingRequest = deferred()
    const submitQuote = vi.fn(() => pendingRequest.promise)

    const firstClick = runAction('engineer:pricing:save-item:201', submitQuote)
    const secondClick = runAction('engineer:pricing:save-item:201', submitQuote)

    await expect(secondClick).resolves.toBeUndefined()
    expect(submitQuote).toHaveBeenCalledTimes(1)

    pendingRequest.resolve({ success: true })

    await expect(firstClick).resolves.toEqual({ success: true })
    expect(submitQuote).toHaveBeenCalledTimes(1)
  })
})
