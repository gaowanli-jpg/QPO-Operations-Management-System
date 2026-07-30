import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

describe('release drawing replacement stage 4', () => {
  test('market api exposes release drawing replacement endpoint', () => {
    const apiSource = readSource('services', 'marketApi.js')

    expect(apiSource).toContain('replaceReleaseDrawings')
    expect(apiSource).toContain('/api/market/release/drawings/replace')
  })

  test('market release reuses PDF collection helpers and submits all selected orders', () => {
    const source = readSource('components', 'MarketRelease.vue')
    const submitBlock = source.slice(
      source.indexOf('const submitReplacementDrawings = async () =>'),
      source.indexOf('const adjustInternalLeadTime')
    )

    expect(source).toContain('collectPdfFilesFromDataTransfer')
    expect(source).toContain('mergePdfFiles')
    expect(source).toContain('isPdfFile')
    expect(source).toContain('导入替换图纸')
    expect(source).toContain('replacementDrawingFiles')
    expect(source).toContain('clientSkippedReplacementFiles')
    expect(source).toContain('仅支持 PDF 图纸')

    expect(submitBlock).toContain("formData.append('rfqIds', JSON.stringify(selectedRfqIds.value))")
    expect(submitBlock).toContain("formData.append('drawings', file)")
    expect(submitBlock).toContain('const response = await replaceReleaseDrawings(formData)')
    expect(submitBlock).toContain('await fetchReleaseItemsBatch()')
    expect(submitBlock).not.toContain('clearSelectedRows()')
  })

  test('market release displays drawing replacement result and refreshed drawing status', () => {
    const source = readSource('components', 'MarketRelease.vue')

    expect(source).toContain('replacementDrawingResult')
    expect(source).toContain('replacedCount')
    expect(source).toContain('unmatchedDrawings')
    expect(source).toContain('skippedFiles')
    expect(source).toContain('非PDF')
    expect(source).toContain('label="图纸"')
    expect(source).toContain('row.hasDrawing || row.drawingUrl')
    expect(source).toContain("{{ row.hasDrawing || row.drawingUrl ? '已绑定' : '缺图' }}")
  })

  test('market release keeps order picking in the header and summarizes selected orders as a compact table', () => {
    const source = readSource('components', 'MarketRelease.vue')
    const inquiryPanel = source.slice(
      source.indexOf('<div class="panel-title">询价单</div>'),
      source.indexOf('<el-form label-width="80px" class="ticket-form">')
    )

    expect(inquiryPanel).toContain('selected-orders-mini-table')
    expect(inquiryPanel).toContain('prop="customerName"')
    expect(inquiryPanel).toContain('prop="sourceOrderNo"')
    expect(inquiryPanel).toContain('prop="status"')
    expect(inquiryPanel).toContain('height="132"')
    expect(inquiryPanel).not.toContain('order-picker-row')
    expect(inquiryPanel).not.toContain('selected-order-tags')
    expect(inquiryPanel).not.toContain('@click="openOrderPicker"')
  })

  test('market release uses horizontal import actions and shared drawing upload wording', () => {
    const source = readSource('components', 'MarketRelease.vue')

    expect(source).toContain('class="release-table-actions import-action-row"')
    expect(source).toContain('支持 图号.pdf、图号 空格说明.pdf、图号-说明.pdf，例如 L20100000733.pdf 或 L20100000733-定位块.pdf。')
    expect(source).toContain('class="custom-dropzone drawing-replace-dropzone supplement-dropzone"')
    expect(source).toContain('class="drawing-replace-upload-actions import-action-row"')
    expect(source).toContain('将 <em>图纸文件夹</em> 或多个 PDF 图纸直接拖拽至此处')
  })
})
