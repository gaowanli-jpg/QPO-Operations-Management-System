import { describe, expect, test } from 'vitest'
import { existsSync, readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

describe('retired manual sales match module', () => {
  test('removes the manual sales-match route and sidebar entry', () => {
    const router = readSource('router', 'index.js')
    const app = readSource('App.vue')

    expect(router).not.toContain('SalesMatch')
    expect(router).not.toContain('/sales-match')
    expect(router).not.toContain('market:sales-match:view')
    expect(app).not.toContain('/sales-match')
    expect(app).not.toContain('market:sales-match:view')
  })

  test('deletes the standalone manual anomaly governance page', () => {
    expect(existsSync(resolve(currentDir, 'components', 'SalesMatch.vue'))).toBe(false)
  })

  test('keeps sales-order picker scopes explicit without restoring sales-match', () => {
    const picker = readSource('components', 'OrderPickerDialog.vue')

    expect(picker).not.toContain('sales-match')
    expect(picker).not.toContain('market:sales-match')
    expect(picker).toContain("const SALES_ORDER_SCOPES = ['release-append-sales-order', 'release-append', 'u9-sync', 'sales-order-change']")
    expect(picker).toContain("'release-append-sales-order': '销售订单'")
    expect(picker).toContain("'u9-sync': 'market:u9-sync:customer-select'")
    expect(picker).toContain("'sales-order-change': 'change:sales-order:customer-select'")
  })
})
