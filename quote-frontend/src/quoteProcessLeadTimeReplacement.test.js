import { beforeEach, describe, expect, test, vi } from 'vitest'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { request } from './api'
import * as marketApi from './services/marketApi'

vi.mock('./api', () => ({
  downloadFile: vi.fn(),
  request: vi.fn()
}))

const currentDir = dirname(fileURLToPath(import.meta.url))
const readOptionalSource = (...segments) => {
  const path = resolve(currentDir, ...segments)
  return existsSync(path) ? readFileSync(path, 'utf8').replace(/\r\n/g, '\n') : ''
}

const quoteProcessSource = readOptionalSource('components', 'QuoteProcess.vue')
const dialogSource = readOptionalSource('components', 'quote-process', 'QuoteLeadTimeReplacementDialog.vue')
const marketApiSource = readOptionalSource('services', 'marketApi.js')

const blockBetween = (source, start, end) => {
  const startIndex = source.indexOf(start)
  const endIndex = source.indexOf(end, startIndex)
  expect(startIndex, `Expected source to include start marker ${start}`).toBeGreaterThanOrEqual(0)
  expect(endIndex, `Expected source to include end marker ${end}`).toBeGreaterThan(startIndex)
  return source.slice(startIndex, endIndex)
}

beforeEach(() => {
  request.mockReset()
})

describe('quote process lead-time replacement', () => {
  test('market api exposes batch save and preview/confirm multipart endpoints', async () => {
    const payload = { rfqId: 18, items: [{ itemId: 1, quoteLeadTime: '2026-04-05' }] }
    const previewFormData = new FormData()
    previewFormData.append('excelFile', new File(['x'], 'lead-time.xlsx'))
    const confirmFormData = new FormData()
    confirmFormData.append('previewFingerprint', 'fp-001')
    confirmFormData.append('excelFile', new File(['x'], 'lead-time.xlsx'))

    expect(marketApiSource).toContain('batchUpdateQuoteProcessLeadTime')
    expect(marketApiSource).toContain('previewQuoteProcessLeadTimeReplacement')
    expect(marketApiSource).toContain('confirmQuoteProcessLeadTimeReplacement')

    await marketApi.batchUpdateQuoteProcessLeadTime(payload)
    await marketApi.previewQuoteProcessLeadTimeReplacement(18, previewFormData)
    await marketApi.confirmQuoteProcessLeadTimeReplacement(18, confirmFormData)

    expect(request).toHaveBeenNthCalledWith(1, '/api/market/quote-process/lead-time/batch-update', {
      method: 'POST',
      data: payload
    })
    expect(request).toHaveBeenNthCalledWith(2, '/api/market/quote-process/18/lead-time-replacement/preview', {
      method: 'POST',
      data: previewFormData,
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    expect(request).toHaveBeenNthCalledWith(3, '/api/market/quote-process/18/lead-time-replacement/confirm', {
      method: 'POST',
      data: confirmFormData,
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  })

  test('quote process places data replacement and bulk save beside existing table import action', () => {
    const actionRow = blockBetween(
      quoteProcessSource,
      'class="quote-process-table-actions import-action-row"',
      '</div>\n            </div>\n            <el-empty'
    )
    const replacementIndex = actionRow.indexOf('表格数据替换')
    const bulkSaveIndex = actionRow.indexOf('保存修改')
    const importSelectionIndex = actionRow.indexOf('导入表格选定')

    expect(replacementIndex).toBeGreaterThanOrEqual(0)
    expect(bulkSaveIndex).toBeGreaterThan(replacementIndex)
    expect(importSelectionIndex).toBeGreaterThan(bulkSaveIndex)
    expect(actionRow).toContain('v-permission="\'market:quote-process:update-lead-time\'"')
    expect(actionRow).toContain('@click="openLeadTimeReplacementDialog"')
    expect(actionRow).toContain('@click="saveAllQuoteLeadTimeDrafts"')
    expect(actionRow).toContain(':disabled="leadTimeReplacementDisabled"')
    expect(actionRow).toContain(':disabled="!hasQuoteLeadTimeDraftChanges')
  })

  test('quote process tracks quote lead-time drafts across paging and submits only changed rows', () => {
    expect(quoteProcessSource).toContain('const quoteLeadTimeDraftCache = ref(new Map())')
    expect(quoteProcessSource).toContain('quoteLeadTimeOriginal')
    expect(quoteProcessSource).toContain('@change="rememberQuoteLeadTimeDraft(row)"')
    expect(quoteProcessSource).toContain('const changedQuoteLeadTimeDrafts = computed(() =>')
    expect(quoteProcessSource).toContain('await batchUpdateQuoteProcessLeadTime({')
    expect(quoteProcessSource).toContain('items: changedQuoteLeadTimeDrafts.value')
  })

  test('quote process mounts a preview-confirm dialog and refreshes after confirmation', () => {
    expect(quoteProcessSource).toContain("import QuoteLeadTimeReplacementDialog from './quote-process/QuoteLeadTimeReplacementDialog.vue'")
    expect(quoteProcessSource).toContain('<QuoteLeadTimeReplacementDialog')
    expect(quoteProcessSource).toContain('v-model="leadTimeReplacementVisible"')
    expect(quoteProcessSource).toContain(':rfq-id="selectedRfqId"')
    expect(quoteProcessSource).toContain('@confirmed="handleLeadTimeReplacementConfirmed"')
  })

  test('lead-time replacement dialog previews summary, row status and blocks errored confirmation', () => {
    expect(dialogSource).toContain('报价交期表格替换预览')
    expect(dialogSource).toContain('recognizedFields')
    expect(dialogSource).toContain('summaryItems')
    expect(dialogSource).toContain('excelRowNumber')
    expect(dialogSource).toContain('currentQuoteLeadTime')
    expect(dialogSource).toContain('importedQuoteLeadTime')
    expect(dialogSource).toContain('statusLabel')
    expect(dialogSource).toContain('row.status === \'ERROR\'')
    expect(dialogSource).toContain(':disabled="!canConfirmPreview"')
    expect(dialogSource).toContain('return runAction(actionKeys.preview, async () =>')
    expect(dialogSource).toContain('return runAction(actionKeys.confirm, async () =>')
  })
})
