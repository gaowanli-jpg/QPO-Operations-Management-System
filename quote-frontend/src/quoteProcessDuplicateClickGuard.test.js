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

describe('quote process duplicate click guards', () => {
  test('quote process registers pending keys for save, export, batch lead-time and import actions', () => {
    const source = readSource('components', 'QuoteProcess.vue')

    expect(source).toContain("import { usePendingAction } from '../composables/usePendingAction'")
    expect(source).toContain('const { isPending, runAction } = usePendingAction()')
    expect(source).toContain('saveQuote: () => `market:quote-process:save-quote:${selectedRfqId.value || \'none\'}`')
    expect(source).toContain('leadTimeBatch: () => `market:quote-process:lead-time-batch:${selectedRfqId.value || \'none\'}`')
    expect(source).toContain('importSelection: () => `market:quote-process:import-selection:${selectedRfqId.value || \'none\'}`')
    expect(source).toContain('exportDetail: () => `market:quote-process:export-detail:${selectedRfqId.value || \'none\'}`')
    expect(source).toContain('exportSimple: () => `market:quote-process:export-simple:${selectedRfqId.value || \'none\'}`')
    expect(source).toContain('exportTemplateDetail: () => `market:quote-process:export-template-detail:${selectedRfqId.value || \'none\'}`')
    expect(source).toContain('exportTemplateSimple: () => `market:quote-process:export-template-simple:${selectedRfqId.value || \'none\'}`')
  })

  test('quote process buttons expose loading and disabled states for pending actions', () => {
    const source = readSource('components', 'QuoteProcess.vue')

    expect(source).toContain(':loading="savingQuoteSheet"')
    expect(source).toContain(':disabled="!canSaveQuoteSheet || savingQuoteSheet || quoteLeadTimeBatchPending || importSelecting"')
    expect(source).toContain(':loading="isQuoteExporting(\'exportDetail\')"')
    expect(source).toContain(':disabled="!canExportQuoteSheet || isQuoteExporting(\'exportDetail\')"')
    expect(source).toContain(':loading="isQuoteExporting(\'exportSimple\')"')
    expect(source).toContain(':loading="isQuoteExporting(\'exportTemplateDetail\')"')
    expect(source).toContain(':loading="isQuoteExporting(\'exportTemplateSimple\')"')
    expect(source).toContain(':disabled="!canEditQuoteLeadTime || selectedQuoteProcessItemIds.size === 0 || quoteLeadTimeBatchPending || savingQuoteSheet || importSelecting"')
    expect(source).toContain(':disabled="!canEditQuoteLeadTime || quoteProcessItemTotal === 0 || importSelecting || quoteLeadTimeBatchPending || savingQuoteSheet"')
  })

  test('quote process wraps write and export actions with runAction duplicate guards', () => {
    const source = readSource('components', 'QuoteProcess.vue')

    const importBlock = blockBetween(source, 'const handleQuoteProcessSelectionExcelChange = async (uploadFile) =>', 'const applyShift')
    expect(importBlock).toContain('return runAction(quoteProcessActionKeys.importSelection(), async () =>')
    expect(importBlock).toContain('await parseQuoteProcessPartSelection(selectedRfqId.value, formData)')

    const shiftBlock = blockBetween(source, 'const applyShift = async (direction) =>', 'const setQuoteLeadTime')
    expect(shiftBlock).toContain('return runAction(quoteProcessActionKeys.leadTimeBatch(), async () =>')
    expect(shiftBlock).toContain('await shiftQuoteProcessLeadTime')

    const setBlock = blockBetween(source, 'const setQuoteLeadTime = async () =>', 'const saveLeadTime')
    expect(setBlock).toContain('return runAction(quoteProcessActionKeys.leadTimeBatch(), async () =>')
    expect(setBlock).toContain('await setQuoteProcessLeadTime')

    const saveQuoteBlock = blockBetween(source, 'const saveMarketQuoteSheet = async () =>', 'const runQuoteExport')
    expect(saveQuoteBlock).toContain('return runAction(quoteProcessActionKeys.saveQuote(), async () =>')
    expect(saveQuoteBlock).toContain('await saveQuoteSheetApi(selectedRfqId.value)')

    const exportBlock = blockBetween(source, 'const runQuoteExport = async (actionKey, fallbackName, downloadAction, errorMessage) =>', 'const exportQuote')
    expect(exportBlock).toContain('return runAction(actionKey(), async () =>')
    expect(exportBlock).toContain('await downloadAction(selectedRfqId.value, fallbackName)')
  })

  test('quote process save quote key suppresses duplicate API calls while pending', async () => {
    const { runAction } = usePendingAction()
    const pendingRequest = deferred()
    const saveQuoteSheetApi = vi.fn(() => pendingRequest.promise)

    const firstClick = runAction('market:quote-process:save-quote:18', saveQuoteSheetApi)
    const secondClick = runAction('market:quote-process:save-quote:18', saveQuoteSheetApi)

    await expect(secondClick).resolves.toBeUndefined()
    expect(saveQuoteSheetApi).toHaveBeenCalledTimes(1)

    pendingRequest.resolve({ success: true })

    await expect(firstClick).resolves.toEqual({ success: true })
    expect(saveQuoteSheetApi).toHaveBeenCalledTimes(1)
  })
})
