import { describe, expect, test } from 'vitest'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

describe('sales order U9 sync module stage 1 wiring', () => {
  test('registers an independent protected U9 sync route', () => {
    const router = readSource('router', 'index.js')

    expect(router).toContain("const SalesOrderU9Sync = () => import('../components/SalesOrderU9Sync.vue')")
    expect(router).toContain("{ path: '/u9-sync', component: SalesOrderU9Sync, meta: { permission: 'market:u9-sync:view' } }")
  })

  test('places U9 sync between release and delivery documents in the market sidebar', () => {
    const app = readSource('App.vue')

    expect(app).toContain("paths: ['/market', '/sales-match', '/quote-process', '/release', '/u9-sync', '/delivery-documents'")
    expect(app).toContain("'/u9-sync': { title: 'U9同步', permission: 'market:u9-sync:view'")

    const marketGroupIndex = app.indexOf("{ key: 'market'")
    const releaseIndex = app.indexOf("'/release'", marketGroupIndex)
    const u9SyncIndex = app.indexOf("'/u9-sync'", marketGroupIndex)
    const deliveryDocumentIndex = app.indexOf("'/delivery-documents'", marketGroupIndex)

    expect(releaseIndex).toBeGreaterThan(-1)
    expect(u9SyncIndex).toBeGreaterThan(releaseIndex)
    expect(deliveryDocumentIndex).toBeGreaterThan(u9SyncIndex)
  })

  test('creates a standard U9 sync shell without submit action in stage 1', () => {
    const componentPath = resolve(currentDir, 'components', 'SalesOrderU9Sync.vue')
    expect(existsSync(componentPath)).toBe(true)

    const component = readSource('components', 'SalesOrderU9Sync.vue')
    expect(component).toContain('class="erp-page sales-order-u9-sync-page"')
    expect(component).toContain('class="erp-page-header sales-order-u9-sync-header"')
    expect(component).toContain('<h1 class="erp-page-title">U9同步</h1>')
    expect(component).toContain('class="erp-toolbar"')
    expect(component).toContain('class="erp-surface sales-order-u9-sync-placeholder"')
    expect(component).toContain('销售订单')
    expect(component).not.toContain('RFQ')
    expect(component).not.toContain('同步U9')
    expect(component).not.toContain('market:u9-sync:submit')
  })
})
