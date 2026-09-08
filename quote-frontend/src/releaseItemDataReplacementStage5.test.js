import { describe, expect, test, vi, beforeEach } from 'vitest'
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

const marketReleaseSource = readOptionalSource('components', 'MarketRelease.vue')
const dialogSource = readOptionalSource('components', 'market-release', 'ReleaseItemDataReplacementDialog.vue')
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

describe('release item data replacement stage 5 frontend', () => {
  test('market api exposes preview and confirm multipart endpoints', async () => {
    const previewFormData = new FormData()
    previewFormData.append('sources', JSON.stringify([{ sourceType: 'RFQ', sourceOrderId: 18 }]))
    previewFormData.append('excelFile', new File(['x'], 'replace.xlsx'))
    const confirmFormData = new FormData()
    confirmFormData.append('sources', JSON.stringify([{ sourceType: 'RFQ', sourceOrderId: 18 }]))
    confirmFormData.append('previewFingerprint', 'fp-001')
    confirmFormData.append('excelFile', new File(['x'], 'replace.xlsx'))

    expect(marketApiSource).toContain('previewReleaseItemDataReplacement')
    expect(marketApiSource).toContain('confirmReleaseItemDataReplacement')

    await marketApi.previewReleaseItemDataReplacement(previewFormData)
    await marketApi.confirmReleaseItemDataReplacement(confirmFormData)

    expect(request).toHaveBeenNthCalledWith(1, '/api/market/release/item-data-replacement/preview', {
      method: 'POST',
      data: previewFormData,
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    expect(request).toHaveBeenNthCalledWith(2, '/api/market/release/item-data-replacement/confirm', {
      method: 'POST',
      data: confirmFormData,
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  })

  test('market release exposes data replacement but hides drawing replacement', () => {
    const actionRow = blockBetween(
      marketReleaseSource,
      'class="release-table-actions import-action-row"',
      '<el-table'
    )
    const dataReplacementIndex = actionRow.indexOf('表格数据替换')
    const selectionImportIndex = actionRow.indexOf('导入表格选定')

    expect(dataReplacementIndex).toBeGreaterThanOrEqual(0)
    expect(selectionImportIndex).toBeGreaterThan(dataReplacementIndex)
    expect(actionRow).toContain('v-permission="\'market:release:create\'"')
    expect(actionRow).toContain('@click="openDataReplacementDialog"')
    expect(actionRow).toContain(':disabled="dataReplacementDisabled"')
    expect(actionRow).toContain(':loading="dataReplacementBusy"')
    expect(actionRow).not.toContain('导入替换图纸')
    expect(actionRow).not.toContain('openDrawingReplaceDialog')
  })

  test('market release places the bulk draft save button beside data replacement', () => {
    const actionRow = blockBetween(
      marketReleaseSource,
      'class="release-table-actions import-action-row"',
      '<el-table'
    )
    const dataReplacementIndex = actionRow.indexOf('表格数据替换')
    const bulkSaveIndex = actionRow.indexOf('保存修改')
    const selectionImportIndex = actionRow.indexOf('导入表格选定')

    expect(dataReplacementIndex).toBeGreaterThanOrEqual(0)
    expect(bulkSaveIndex).toBeGreaterThan(dataReplacementIndex)
    expect(selectionImportIndex).toBeGreaterThan(bulkSaveIndex)
    expect(actionRow).toContain('v-permission="\'market:release:create\'"')
    expect(actionRow).toContain('@click="saveAllReleaseDraftChanges"')
    expect(actionRow).toContain(':disabled="bulkSaveDisabled"')
    expect(actionRow).toContain(':loading="savingReleaseDrafts"')
  })

  test('market release mounts an independent dialog with selected RFQ sources', () => {
    expect(marketReleaseSource).toContain("import ReleaseItemDataReplacementDialog from './market-release/ReleaseItemDataReplacementDialog.vue'")
    expect(marketReleaseSource).toContain('<ReleaseItemDataReplacementDialog')
    expect(marketReleaseSource).toContain('v-model="dataReplacementVisible"')
    expect(marketReleaseSource).toContain(':sources="selectedOrderSources"')
    expect(marketReleaseSource).toContain(':disabled="submitting || selectedOrders.length === 0"')
    expect(marketReleaseSource).toContain('@busy-change="dataReplacementBusy = $event"')
  })

  test('dialog previews recognized fields, summary, row differences, skips and errors', () => {
    expect(dialogSource).toContain('recognizedFields')
    expect(dialogSource).toContain('summaryItems')
    expect(dialogSource).toContain('excelRowNumber')
    expect(dialogSource).toContain('partNo')
    expect(dialogSource).toContain('version')
    expect(dialogSource).toContain('changes')
    expect(dialogSource).toContain('statusLabel')
    expect(dialogSource).toContain('acceptedQty')
    expect(dialogSource).toContain('reason')
  })

  test('dialog retains file, tracks fingerprint state, blocks errored confirmation and guards duplicate clicks', () => {
    expect(dialogSource).toContain('selectedFile')
    expect(dialogSource).toContain('重新选择文件')
    expect(dialogSource).toContain('previewFingerprint')
    expect(dialogSource).toContain('fingerprintStatusText')
    expect(dialogSource).toContain('canConfirmPreview')
    expect(dialogSource).toContain('summary.errorCount')
    expect(dialogSource).toContain("row.status === 'ERROR'")
    expect(dialogSource).toContain(':disabled="!canConfirmPreview"')
    expect(dialogSource).toContain("preview: 'market:release:item-data-replacement-preview'")
    expect(dialogSource).toContain("confirm: 'market:release:item-data-replacement-confirm'")
    expect(dialogSource).toContain('return runAction(dataReplacementActionKeys.preview, async () =>')
    expect(dialogSource).toContain('return runAction(dataReplacementActionKeys.confirm, async () =>')
  })

  test('preview request keeps page data untouched until an explicit confirm flow', () => {
    const previewBlock = blockBetween(
      dialogSource,
      'const submitPreview = async () =>',
      'const submitConfirm = async () =>'
    )

    expect(previewBlock).toContain('await previewReleaseItemDataReplacement(formData)')
    expect(previewBlock).not.toContain('acceptedQtyMap')
    expect(previewBlock).not.toContain("emit('confirmed'")
  })

  test('dialog uses internal table scrolling and small-screen wrapping instead of page overflow', () => {
    expect(dialogSource).toContain('width="min(1080px, calc(100vw - 32px))"')
    expect(dialogSource).toContain('class="replacement-preview-table-wrap"')
    expect(dialogSource).toContain('.replacement-preview-table-wrap {\n  max-width: 100%;\n  overflow-x: auto;')
    expect(dialogSource).toContain('.replacement-dialog-actions {\n  display: flex;\n  flex-wrap: wrap;')
    expect(dialogSource).toContain('@media (max-width: 640px)')
  })
})
