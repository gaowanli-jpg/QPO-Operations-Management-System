import { describe, expect, test } from 'vitest'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const sourcePath = (...segments) => resolve(currentDir, ...segments)
const readSource = (...segments) => readFileSync(sourcePath(...segments), 'utf8')

const routerSource = readSource('router', 'index.js')
const appVue = readSource('App.vue')
const customerApi = existsSync(sourcePath('services', 'customerApi.js'))
  ? readSource('services', 'customerApi.js')
  : ''
const customerManage = existsSync(sourcePath('components', 'CustomerManage.vue'))
  ? readSource('components', 'CustomerManage.vue')
  : ''

describe('QPO customer master frontend module', () => {
  test('wires customer master route to its own system permission', () => {
    expect(routerSource).toContain("const CustomerManage = () => import('../components/CustomerManage.vue')")
    expect(routerSource).toContain("{ path: '/customers', component: CustomerManage, meta: { permission: 'system:customer:view' } }")
    expect(routerSource).not.toContain("import CustomerManage from '../components/CustomerManage.vue'")
  })

  test('adds customer master menu entry under system group', () => {
    expect(appVue).toContain("'/customers'")
    expect(appVue).toContain("title: '客户资料'")
    expect(appVue).toContain("permission: 'system:customer:view'")

    const organizationIndex = appVue.indexOf("'/organization'")
    const customersIndex = appVue.indexOf("'/customers'")
    const auditIndex = appVue.indexOf("'/audit-logs'")
    expect(customersIndex).toBeGreaterThan(organizationIndex)
    expect(customersIndex).toBeLessThan(auditIndex)
  })

  test('adds customer API helpers for page and mutation endpoints', () => {
    expect(customerApi).toContain('listCustomers')
    expect(customerApi).toContain('listCustomerOptions')
    expect(customerApi).toContain('createCustomer')
    expect(customerApi).toContain('updateCustomer')
    expect(customerApi).toContain('deleteCustomer')
    expect(customerApi).toContain("request('/api/system/customers', { params })")
    expect(customerApi).toContain("request('/api/system/customers/options', { params })")
    expect(customerApi).toContain("request('/api/system/customers', {")
    expect(customerApi).toContain("method: 'POST'")
    expect(customerApi).toContain("method: 'PUT'")
    expect(customerApi).toContain("method: 'DELETE'")
  })

  test('builds customer master page with QPO surfaces and guarded actions', () => {
    expect(existsSync(sourcePath('components', 'CustomerManage.vue'))).toBe(true)
    expect(customerManage).toContain('erp-page customer-manage-page')
    expect(customerManage).toContain('erp-page-header')
    expect(customerManage).toContain('erp-toolbar')
    expect(customerManage).toContain('erp-table-surface')
    expect(customerManage).toContain('erp-table-actions')
    expect(customerManage).toContain('客户资料')
    expect(customerManage).toContain('客户编码')
    expect(customerManage).toContain('客户简称')
    expect(customerManage).toContain('客户名称')
    expect(customerManage).toContain("v-permission=\"'system:customer:create'\"")
    expect(customerManage).toContain("v-permission=\"'system:customer:update'\"")
    expect(customerManage).toContain("v-permission=\"'system:customer:delete'\"")
    expect(customerManage).toContain('ElMessageBox.confirm')
    expect(customerManage).toContain('loadCustomers')
    expect(customerManage).toContain('saveCustomer')
    expect(customerManage).toContain('deleteCustomer')
  })

  test('supports server paging and field-specific filtering', () => {
    expect(customerManage).toContain("field: 'all'")
    expect(customerManage).toContain("value=\"all\"")
    expect(customerManage).toContain("value=\"code\"")
    expect(customerManage).toContain("value=\"shortName\"")
    expect(customerManage).toContain("value=\"name\"")
    expect(customerManage).toContain('v-model:current-page="pagination.page"')
    expect(customerManage).toContain('v-model:page-size="pagination.pageSize"')
    expect(customerManage).toContain(':total="pagination.total"')
    expect(customerManage).toContain('@size-change="handlePageSizeChange"')
    expect(customerManage).toContain('@current-change="loadCustomers"')
  })
})
