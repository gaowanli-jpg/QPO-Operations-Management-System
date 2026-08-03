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

describe('QPO Stage 3 shared page layout migration', () => {
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

    expect(source).toContain('<h1 class="erp-page-title">询价订单</h1>')
    expect(source).not.toContain('<h1 class="erp-page-title">订单管理</h1>')
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

  test('keeps major module page titles aligned with sidebar labels', () => {
    const marketDashboard = readComponent('MarketDashboard.vue')
    const quoteProcess = readComponent('QuoteProcess.vue')
    const marketRelease = readComponent('MarketRelease.vue')
    const materialProcess = readComponent('MaterialProcessManage.vue')

    expect(marketDashboard).toContain('<h1 class="erp-page-title">新单导入</h1>')
    expect(quoteProcess).toContain('<h2>报价处理</h2>')
    expect(marketRelease).toContain('<h2>议价提单</h2>')
    expect(materialProcess).toContain('<h1 class="erp-page-title">材料工艺</h1>')

    expect(marketDashboard).not.toContain('新单导入中心')
    expect(quoteProcess).not.toContain('报价单处理生成')
    expect(marketRelease).not.toContain('议价确认与提单生成')
    expect(materialProcess).not.toContain('材料与工艺管理')
  })
})
