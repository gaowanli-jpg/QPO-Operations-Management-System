import { describe, expect, test } from 'vitest'
import { existsSync, readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

describe('sales match stage 1 shell', () => {
  test('adds sales match route guarded by its own menu permission', () => {
    const router = readSource('router', 'index.js')

    expect(router).toContain("const SalesMatch = () => import('../components/SalesMatch.vue')")
    expect(router).toContain("{ path: '/sales-match', component: SalesMatch, meta: { permission: 'market:sales-match:view' } }")
  })

  test('places sales match between new import and quote process in the market sidebar', () => {
    const app = readSource('App.vue')

    expect(app).toContain("paths: ['/market', '/sales-match', '/quote-process'")
    expect(app).toContain("'/sales-match': { title: '销售匹配', permission: 'market:sales-match:view'")

    const importIndex = app.indexOf("'/market': { title: '新单导入'")
    const salesMatchIndex = app.indexOf("'/sales-match': { title: '销售匹配'")
    const quoteProcessIndex = app.indexOf("'/quote-process': { title: '报价处理'")

    expect(importIndex).toBeGreaterThanOrEqual(0)
    expect(salesMatchIndex).toBeGreaterThan(importIndex)
    expect(quoteProcessIndex).toBeGreaterThan(salesMatchIndex)
  })

  test('creates a standard empty sales match page shell', () => {
    const componentPath = resolve(currentDir, 'components', 'SalesMatch.vue')
    expect(existsSync(componentPath)).toBe(true)

    const component = readSource('components', 'SalesMatch.vue')
    expect(component).toContain('class="erp-page"')
    expect(component).toContain('class="erp-page-header"')
    expect(component).toContain('<h1>销售匹配</h1>')
    expect(component).toContain('class="erp-toolbar"')
    expect(component).toContain('class="erp-surface"')
  })
})
