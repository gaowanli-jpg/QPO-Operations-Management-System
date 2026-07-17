import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const currentDir = dirname(fileURLToPath(import.meta.url))

const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

const readComponent = (fileName) => readSource('components', fileName)

const expectIncludes = (source, fileName, classNames) => {
  for (const className of classNames) {
    expect(source, `Expected ${fileName} to include ${className}`).toContain(className)
  }
}

const expectExcludes = (source, fileName, classNames) => {
  for (const className of classNames) {
    expect(source, `Expected ${fileName} to stop using ${className}`).not.toContain(className)
  }
}

describe('QPO Stage 7 final consistency pass', () => {
  test('moves remaining market workflow pages off local card shells', () => {
    const pages = [
      ['MarketRelease.vue', 'release-page'],
      ['QuoteProcess.vue', 'quote-process-page']
    ]

    for (const [fileName, pageClass] of pages) {
      const source = readComponent(fileName)

      expectIncludes(source, fileName, [
        `erp-page ${pageClass}`,
        'erp-page-header',
        'erp-page-actions',
        'erp-surface',
        'erp-table-surface',
        'erp-table-actions'
      ])
      expectExcludes(source, fileName, [
        '<el-card',
        'template #header',
        'class="release-container"',
        'class="quote-process-container"',
        'class="release-card"',
        'class="quote-card"',
        'class="card-header"'
      ])
    }
  })

  test('normalizes remaining admin and history pages', () => {
    const expectations = [
      ['HistoryOrderManage.vue', 'history-page'],
      ['QuoteTemplateManage.vue', 'template-page'],
      ['UserManage.vue', 'user-manage-page']
    ]

    for (const [fileName, pageClass] of expectations) {
      const source = readComponent(fileName)

      expectIncludes(source, fileName, [
        `erp-page ${pageClass}`,
        'erp-page-header',
        'erp-toolbar',
        'erp-table-surface'
      ])
      expectExcludes(source, fileName, [
        '<el-card',
        'template #header',
        'class="page"',
        'class="history-container"',
        'class="template-card"',
        'class="history-card"',
        'class="toolbar"',
        'class="card-header"'
      ])
    }
  })

  test('keeps dialogs and destructive table operations on shared affordances', () => {
    const userSource = readComponent('UserManage.vue')
    const historySource = readComponent('HistoryOrderManage.vue')

    expect(userSource).toContain('erp-dialog-form')
    expect(userSource).toContain('erp-dialog-footer')
    expect(userSource).toContain('erp-table-actions')
    expect(historySource).toContain('erp-table-actions')
    expect(historySource).toContain('erp-empty-state')
  })

  test('aligns order and history order list chrome while keeping history functions distinct', () => {
    const orderSource = readComponent('OrderManage.vue')
    const historySource = readComponent('HistoryOrderManage.vue')
    const disabledRefreshButton = ':loading="loading" :disabled="!selectedCustomer" @click="refreshOrders"'

    expect(historySource).toContain('<header class="erp-page-header">')
    expect(historySource).not.toContain('history-page-actions')

    const historyToolbarIndex = historySource.indexOf('class="erp-toolbar-group history-filter-group"')
    const historyRefreshIndex = historySource.indexOf(disabledRefreshButton)
    expect(historyToolbarIndex).toBeGreaterThan(-1)
    expect(historyRefreshIndex).toBeGreaterThan(historyToolbarIndex)
    expect(orderSource).toContain(disabledRefreshButton)

    expect(historySource).toContain('彻底删除')
    expect(historySource).toContain('查看订单')
    expect(historySource).not.toContain('重置提单/工艺')

    expect(orderSource).toContain('.order-table-surface .erp-table-body')
    expect(historySource).toContain('.history-table-surface .erp-table-body')
    expect(orderSource).toContain('padding: var(--erp-space-3);')
    expect(historySource).toContain('padding: var(--erp-space-3);')
  })
})
