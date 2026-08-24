import { describe, expect, test } from 'vitest'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

describe('sales order change module stage 1', () => {
  test('registers a protected sales order change route', () => {
    const router = readSource('router', 'index.js')

    expect(router).toContain("const SalesOrderChangeManage = () => import('../components/SalesOrderChangeManage.vue')")
    expect(router).toContain("{ path: '/sales-order-change', component: SalesOrderChangeManage, meta: { permission: 'change:sales-order:view' } }")
  })

  test('places the change group between orders and system in the sidebar', () => {
    const app = readSource('App.vue')

    expect(app).toContain("{ key: 'change', title: '变更', paths: ['/sales-order-change'] }")
    expect(app).toContain("'/sales-order-change': { title: '销售变更', permission: 'change:sales-order:view'")
    expect(app).toContain("'/sales-order-change':")
    expect(app).toContain('校正已同步 U9 销售订单的本地零件数据')

    const ordersGroupIndex = app.indexOf("{ key: 'orders'")
    const changeGroupIndex = app.indexOf("{ key: 'change'")
    const systemGroupIndex = app.indexOf("{ key: 'system'")

    expect(ordersGroupIndex).toBeGreaterThan(-1)
    expect(changeGroupIndex).toBeGreaterThan(ordersGroupIndex)
    expect(systemGroupIndex).toBeGreaterThan(changeGroupIndex)
  })

  test('adds a read-only sales order change page shell without U9 sync actions', () => {
    const componentPath = resolve(currentDir, 'components', 'SalesOrderChangeManage.vue')
    expect(existsSync(componentPath)).toBe(true)

    const component = readSource('components', 'SalesOrderChangeManage.vue')
    expect(component).toContain('class="erp-page sales-order-change-page"')
    expect(component).toContain('销售变更')
    expect(component).toContain('本功能仅修改 QPO 本地销售订单零件数据，不会同步到 U9')
    expect(component).toContain('customer-scope="sales-order-change"')
    expect(component).toContain('order-scope="sales-order-change"')
    expect(component).toContain('previewSalesOrderChange')
    expect(component).toContain('listSalesOrderChangeOrders')
    expect(component).toContain("const SALES_ORDER_CHANGE_VIEW_PERMISSION = 'change:sales-order:view'")
    expect(component).not.toContain('同步U9')
    expect(component).not.toContain('核对U9结果')
    expect(component).not.toContain('刷新U9选项')
    expect(component).not.toContain('Seller')
    expect(component).not.toContain('SaleDepartment')
  })

  test('adds sales order change API helpers and order picker scope', () => {
    const api = readSource('services', 'salesOrderChangeApi.js')
    const picker = readSource('components', 'OrderPickerDialog.vue')

    expect(api).toContain("request('/api/change/sales-orders'")
    expect(api).toContain('previewSalesOrderChange')
    expect(api).toContain('/api/change/sales-orders/${salesOrderId}/preview')
    expect(picker).toContain("'sales-order-change': 'change:sales-order:customer-select'")
    expect(picker).toContain("'sales-order-change': '销售订单'")
  })
})
