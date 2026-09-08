import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8').replace(/\r\n/g, '\n')

describe('release drawing replacement frontend retirement', () => {
  test('market api no longer exposes the release drawing replacement helper', () => {
    const apiSource = readSource('services', 'marketApi.js')

    expect(apiSource).not.toContain('replaceReleaseDrawings')
    expect(apiSource).not.toContain('/api/market/release/drawings/replace')
  })

  test('market release hides the drawing replacement button and dialog', () => {
    const source = readSource('components', 'MarketRelease.vue')

    expect(source).not.toContain('导入替换图纸')
    expect(source).not.toContain('openDrawingReplaceDialog')
    expect(source).not.toContain('submitReplacementDrawings')
    expect(source).not.toContain('drawingReplaceVisible')
    expect(source).not.toContain('replacementDrawingFiles')
    expect(source).not.toContain('replacementVersionDrawingFiles')
    expect(source).not.toContain('replaceReleaseDrawings')
    expect(source).not.toContain('drawing-replace-dialog')
    expect(source).not.toContain('drawing-replace-dual-grid')
  })

  test('market release keeps table data replacement and selection import actions', () => {
    const source = readSource('components', 'MarketRelease.vue')
    const actionStart = source.indexOf('class="release-table-actions import-action-row"')
    const actionEnd = source.indexOf('<el-table', actionStart)
    expect(actionStart).toBeGreaterThanOrEqual(0)
    expect(actionEnd).toBeGreaterThan(actionStart)
    const actionRow = source.slice(actionStart, actionEnd)

    expect(actionRow).toContain('表格数据替换')
    expect(actionRow).toContain('保存修改')
    expect(actionRow).toContain('导入表格选定')
    expect(actionRow).not.toContain('导入替换图纸')
  })

  test('market release keeps the compact selected-order summary unchanged', () => {
    const source = readSource('components', 'MarketRelease.vue')
    const orderSummaryPanel = source.slice(
      source.indexOf('<el-table\n              class="selected-orders-mini-table"'),
      source.indexOf('<el-form label-width="96px" class="ticket-form release-order-default-form">')
    )

    expect(source).not.toContain('<div class="panel-title">询价单</div>')
    expect(orderSummaryPanel).toContain('selected-orders-mini-table')
    expect(orderSummaryPanel).toContain('prop="customerName"')
    expect(orderSummaryPanel).toContain('prop="sourceOrderNo"')
    expect(orderSummaryPanel).toContain('prop="status"')
    expect(orderSummaryPanel).toContain('height="132"')
    expect(orderSummaryPanel).not.toContain('order-picker-row')
    expect(orderSummaryPanel).not.toContain('selected-order-tags')
    expect(orderSummaryPanel).not.toContain('@click="openOrderPicker"')
  })

  test('market release still displays drawing status on item rows', () => {
    const source = readSource('components', 'MarketRelease.vue')

    expect(source).toContain('label="图纸"')
    expect(source).toContain('row.hasDrawing || row.drawingUrl')
    expect(source).toContain("{{ row.hasDrawing || row.drawingUrl ? '已绑定' : '缺图' }}")
  })
})
