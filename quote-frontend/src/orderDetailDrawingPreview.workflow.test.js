import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = fileURLToPath(new URL('.', import.meta.url))

const readSource = (file) => readFileSync(resolve(currentDir, 'components', file), 'utf8')

describe('order detail drawing preview', () => {
  test('adds a drawing preview column after drawing status and uses authenticated PDF dialog', () => {
    const source = readSource('OrderManage.vue')
    const itemTabStart = source.indexOf('<el-tab-pane label="零件明细" name="items">')
    const ticketTabStart = source.indexOf('<el-tab-pane label="提单行" name="tickets">')
    const itemTable = source.slice(itemTabStart, ticketTabStart)
    const drawingStatusIndex = itemTable.indexOf('label="图纸"')
    const drawingPreviewIndex = itemTable.indexOf('label="图纸预览"')
    const qtyIndex = itemTable.indexOf('label="询价数量"')

    expect(itemTabStart).toBeGreaterThan(-1)
    expect(ticketTabStart).toBeGreaterThan(itemTabStart)
    expect(drawingStatusIndex).toBeGreaterThan(-1)
    expect(drawingPreviewIndex).toBeGreaterThan(drawingStatusIndex)
    expect(drawingPreviewIndex).toBeLessThan(qtyIndex)
    expect(itemTable).toContain('@click.stop="openPdfPreview(row.drawingUrl, row.partNo)"')
    expect(source).toContain(':title="`图纸预览：${pdfTitle}`"')
    expect(source).toContain('createAuthenticatedObjectUrl')
    expect(source).toContain('readFileErrorMessage')
    expect(source).toContain('revokeObjectUrl')
  })
})
