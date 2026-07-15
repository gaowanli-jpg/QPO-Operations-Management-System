import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const currentDir = dirname(fileURLToPath(import.meta.url))

const readComponent = (fileName) => readFileSync(resolve(currentDir, 'components', fileName), 'utf8')

const expectClasses = (source, classes) => {
  for (const className of classes) {
    expect(source, `Expected component to include ${className}`).toContain(className)
  }
}

describe('ERP Stage 3 shared page layout migration', () => {
  test('migrates the market import page to shared page and surface classes', () => {
    const source = readComponent('MarketDashboard.vue')

    expectClasses(source, [
      'erp-page',
      'erp-page-header',
      'erp-page-title',
      'erp-page-description',
      'erp-surface',
      'erp-toolbar'
    ])
    expect(source).not.toContain('main-card')
  })

  test('migrates order management to shared toolbar and table surface classes', () => {
    const source = readComponent('OrderManage.vue')

    expectClasses(source, [
      'erp-page',
      'erp-page-header',
      'erp-page-title',
      'erp-page-description',
      'erp-toolbar',
      'erp-toolbar-group',
      'erp-table-surface',
      'erp-table-body',
      'erp-pagination'
    ])
    expect(source).not.toContain('manage-card')
  })

  test('migrates material/process management to shared toolbar and table surface classes', () => {
    const source = readComponent('MaterialProcessManage.vue')

    expectClasses(source, [
      'erp-page',
      'erp-page-header',
      'erp-page-title',
      'erp-page-description',
      'erp-toolbar',
      'erp-table-surface',
      'erp-table-body'
    ])
    expect(source).not.toContain('manage-card')
  })
})
