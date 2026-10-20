import { describe, expect, test } from 'vitest'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const sourcePath = (...segments) => resolve(currentDir, ...segments)
const readSource = (...segments) => readFileSync(sourcePath(...segments), 'utf8')

describe('sales order management history cleanup characterization', () => {
  test('retains only the compatibility redirect for /history-orders', () => {
    const routerSource = readSource('router', 'index.js')

    expect(routerSource).toContain("{ path: '/history-orders', redirect: '/sales-orders' }")
    expect(routerSource).not.toContain('HistoryOrderManage')
    expect(existsSync(sourcePath('components', 'HistoryOrderManage.vue'))).toBe(false)
  })

  test('sales order page does not depend on history-order permissions or physical delete', () => {
    const source = readSource('components', 'SalesOrderManage.vue')

    expect(source).not.toContain('market:history-order')
    expect(source).not.toContain('market:order:delete')
    expect(source).not.toContain('deleteOrder')
    expect(source).not.toContain('HistoryOrder')
  })

  test('order API removes retired history-order customer and delete helpers', () => {
    const source = readSource('services', 'orderApi.js')

    expect(source).not.toContain("'history-order'")
    expect(source).not.toContain('/api/order-management/history-orders/customers')
    expect(source).not.toContain('export const deleteOrder')
  })
})
