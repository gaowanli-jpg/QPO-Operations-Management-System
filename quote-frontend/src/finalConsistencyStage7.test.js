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

  test('normalizes remaining admin and sales order pages', () => {
    const expectations = [
      ['SalesOrderManage.vue', 'sales-order-manage-page'],
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

  test('keeps dialogs and sales order shell operations on shared affordances', () => {
    const userSource = readComponent('UserManage.vue')
    const salesOrderSource = readComponent('SalesOrderManage.vue')

    expect(userSource).toContain('erp-dialog-form')
    expect(userSource).toContain('erp-dialog-footer')
    expect(userSource).toContain('erp-table-actions')
    expect(salesOrderSource).toContain('erp-table-actions')
    expect(salesOrderSource).toContain('sales-order-table-surface')
  })

  test('aligns inquiry and sales order list chrome while keeping sales order actions separate', () => {
    const orderSource = readComponent('OrderManage.vue')
    const salesOrderSource = readComponent('SalesOrderManage.vue')
    const refreshButton = ':loading="loading" @click="refreshOrders"'

    expect(salesOrderSource).toContain('<header class="erp-page-header">')
    expect(salesOrderSource).not.toContain('history-page-actions')

    const salesOrderToolbarIndex = salesOrderSource.indexOf('class="erp-toolbar-group sales-order-filter-group"')
    const salesOrderRefreshIndex = salesOrderSource.indexOf(refreshButton)
    expect(salesOrderToolbarIndex).toBeGreaterThan(-1)
    expect(salesOrderRefreshIndex).toBeGreaterThan(salesOrderToolbarIndex)
    expect(orderSource).toContain(refreshButton)
    expect(orderSource).toContain('全部客户')
    expect(salesOrderSource).toContain('全部客户')

    expect(salesOrderSource).not.toContain('彻底删除')
    expect(salesOrderSource).toContain('查看销售订单')
    expect(salesOrderSource).not.toContain('重置提单/工艺')

    expect(orderSource).toContain('.order-table-surface .erp-table-body')
    expect(salesOrderSource).toContain('.sales-order-table-surface .erp-table-body')
    expect(orderSource).toContain('padding: var(--erp-space-3);')
    expect(salesOrderSource).toContain('padding: var(--erp-space-3);')
  })
})
