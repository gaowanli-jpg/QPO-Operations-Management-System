import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

const appVue = readSource('App.vue')
const routerSource = readSource('router', 'index.js')
const salesOrderManage = readSource('components', 'SalesOrderManage.vue')
const orderApi = readSource('services', 'orderApi.js')

describe('sales order management stage 6 regression coverage', () => {
  test('keeps inquiry and sales order sidebar entries independent from legacy history wording', () => {
    expect(routerSource).toContain("{ path: '/orders', component: OrderManage, meta: { permission: 'market:order:view' } }")
    expect(routerSource).toContain("{ path: '/sales-orders', component: SalesOrderManage, meta: { permission: 'market:sales-order:view' } }")
    expect(routerSource).toContain("{ path: '/history-orders', redirect: '/sales-orders' }")
    expect(appVue).toContain("'/orders': { title: '询价订单', permission: 'market:order:view'")
    expect(appVue).toContain("'/sales-orders': { title: '销售订单', permission: 'market:sales-order:view'")
    expect(appVue).toContain("{ key: 'orders', title: '订单', paths: ['/orders', '/sales-orders', '/delivered-items', '/production-items', '/locked-items'] }")
    expect(appVue).not.toContain("title: '订单管理'")
    expect(appVue).not.toContain("title: '历史订单'")
    expect(appVue).not.toContain("'/sales-orders': { title: '销售订单', permission: 'market:history-order:view'")
  })

  test('hides sales order entry without its own permission', () => {
    expect(appVue).toContain("return authStore.hasPermission(menuMetaByPath[path]?.permission)")
    expect(appVue).toContain("return backendPaths.has(path)")
    expect(appVue).toContain("'/sales-orders': { title: '销售订单', permission: 'market:sales-order:view'")
    expect(appVue).not.toContain("market:history-order:view', icon: Files")
  })

  test('loads sales order rows from selected customer and backend paging parameters', () => {
    expect(salesOrderManage).toContain('customer-scope="sales-order"')
    expect(salesOrderManage).toContain('@select="handleCustomerSelected"')
    expect(salesOrderManage).toContain('selectedCustomer.value = resolveCustomerName(customer)')
    expect(salesOrderManage).toContain('await handleCustomerChange()')
    expect(salesOrderManage).toContain('const loadOrders = async () =>')
    expect(salesOrderManage).toContain('listSalesOrders({')
    expect(salesOrderManage).toContain('customerName: selectedCustomer.value')
    expect(salesOrderManage).toContain('page: page.value')
    expect(salesOrderManage).toContain('pageSize: pageSize.value')
    expect(orderApi).toContain("export const listSalesOrders = (params = {}) => request('/api/market/sales-order-management/orders', { params })")
  })

  test('exposes only discard for quoted sales orders and restore for discarded sales orders', () => {
    expect(salesOrderManage).toContain('SALES_ORDER_STATUS_ACTIONS')
    expect(salesOrderManage).toContain("permission: 'market:sales-order:discard'")
    expect(salesOrderManage).toContain('handler: discardSalesOrder')
    expect(salesOrderManage).toContain("permission: 'market:sales-order:restore'")
    expect(salesOrderManage).toContain('handler: restoreSalesOrder')
    expect(salesOrderManage).toContain('SALES_ORDER_STATUS_ACTIONS.filter(action => action.status === row.status)')
    expect(salesOrderManage).toContain('RELEASED_SALES_ORDER_STATUSES.includes(row.status)')
    expect(salesOrderManage).toContain('Number(row.ticketCount || 0) > 0')
  })

  test('keeps physical delete out of the sales order management page and API surface', () => {
    expect(salesOrderManage).not.toContain('deleteOrder')
    expect(salesOrderManage).not.toContain('market:order:delete')
    expect(salesOrderManage).not.toContain('market:history-order')
    expect(orderApi).not.toContain('export const deleteOrder')
    expect(orderApi).not.toContain('/api/order-management/history-orders/customers')
  })

  test('detail drawer includes item, ticket, and process sheet tabs backed by paged APIs', () => {
    expect(salesOrderManage).toContain('name="items"')
    expect(salesOrderManage).toContain('name="tickets"')
    expect(salesOrderManage).toContain('name="processSheets"')
    expect(salesOrderManage).toContain('detailItemPagination')
    expect(salesOrderManage).toContain('detailTicketPagination')
    expect(salesOrderManage).toContain('detailProcessSheetPagination')
    expect(salesOrderManage).toContain('const fetchDetailItems = async () =>')
    expect(salesOrderManage).toContain('const fetchDetailTickets = async () =>')
    expect(salesOrderManage).toContain('const fetchDetailProcessSheets = async () =>')
    expect(salesOrderManage).toContain('listSalesOrderItems(salesOrderId, {')
    expect(salesOrderManage).toContain('listSalesOrderTickets(salesOrderId, {')
    expect(salesOrderManage).toContain('listSalesOrderProcessSheets(salesOrderId, {')
    expect(salesOrderManage).toContain('page: detailItemPagination.page')
    expect(salesOrderManage).toContain('pageSize: detailItemPagination.pageSize')
  })
})
