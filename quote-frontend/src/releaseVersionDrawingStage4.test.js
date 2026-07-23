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

    expect(submitBlock).toContain("formData.append('rfqIds', JSON.stringify(selectedOrderIds.value))")
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
})
