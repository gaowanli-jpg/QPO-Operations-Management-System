import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

describe('sales order U9 sync options frontend', () => {
  test('exposes U9 options assignment and reconcile API helpers', () => {
    const api = readSource('services', 'marketApi.js')

    expect(api).toContain("export const getSalesOrderU9SyncOptions = () => request('/api/market/u9-sync/options')")
    expect(api).toContain("export const refreshSalesOrderU9SyncOptions = () => postJson('/api/market/u9-sync/options/refresh')")
    expect(api).toContain("export const saveSalesOrderU9Assignment = (salesOrderId, payload) => request(`/api/market/u9-sync/orders/${salesOrderId}/assignment`")
    expect(api).toContain("method: 'PUT'")
    expect(api).toContain("export const reconcileSalesOrderU9 = (salesOrderId) => postJson(`/api/market/u9-sync/orders/${salesOrderId}/reconcile`)")
  })

  test('renders editable Seller and SaleDepartment selectors with cache warning and save guard', () => {
    const component = readSource('components', 'SalesOrderU9Sync.vue')

    expect(component).toContain('getSalesOrderU9SyncOptions')
    expect(component).toContain('saveSalesOrderU9Assignment')
    expect(component).toContain('const assignmentDraft = ref')
    expect(component).toContain('const optionsLoading = ref(false)')
    expect(component).toContain('const optionsRefreshing = ref(false)')
    expect(component).toContain('const assignmentSaving = ref(false)')
    expect(component).toContain('u9Options.stale')
    expect(component).toContain('u9Options.warning')
    expect(component).toContain('refreshSalesOrderU9SyncOptions')
    expect(component).toContain('handleRefreshU9Options')
    expect(component).toContain(':loading="optionsRefreshing"')
    expect(component).toContain(':disabled="optionsLoading || optionsRefreshing"')
    expect(component).toContain('label="Seller"')
    expect(component).toContain('label="SaleDepartment"')
    expect(component).toContain('@change="handleSellerChange"')
    expect(component).toContain('@change="handleDepartmentChange"')
    expect(component).toContain('handleSaveAssignment')
    expect(component).toContain(':loading="assignmentSaving"')
    expect(component).toContain(':disabled="assignmentSaveDisabled"')
    expect(component).toContain('assignmentReadOnly')
  })

  test('does not introduce an independent U9 sales master menu or CRUD page', () => {
    const component = readSource('components', 'SalesOrderU9Sync.vue')

    expect(component).not.toContain('U9SalesMasterManage')
    expect(component).not.toContain('销售部门管理')
    expect(component).not.toContain('销售员管理')
    expect(component).not.toContain('/u9-sales-master')
  })

  test('shows selected U9 price mode and private field preview without customer material number', () => {
    const component = readSource('components', 'SalesOrderU9Sync.vue')

    expect(component).toContain('prop="untaxedUnitPrice" label="未税单价"')
    expect(component).toContain('prop="taxIncludedUnitPrice" label="含税单价"')
    expect(component).toContain('按未税价同步')
    expect(component).toContain('按含税价同步')
    expect(component).toContain('selectedU9PriceField')
    expect(component).toContain('label="U9私有字段"')
    expect(component).toContain('PrivateDescSeg1')
    expect(component).toContain('PrivateDescSeg2')
    expect(component).toContain('PrivateDescSeg3')
    expect(component).toContain('PrivateDescSeg4')
    expect(component).toContain('PrivateDescSeg5')
    expect(component).toContain('PrivateDescSeg9')
    expect(component).toContain('PrivateDescSeg9 同步价格')
    expect(component).toContain('PubDescSeg2')
    expect(component).toContain('u9PrivateFieldValue')
    expect(component).not.toContain('客户料号')
    expect(component).not.toContain('prop="salesMaterialNo"')
  })

  test('uses pending confirmation reconcile action instead of direct sync', () => {
    const component = readSource('components', 'SalesOrderU9Sync.vue')

    expect(component).toContain('reconcileSalesOrderU9')
    expect(component).toContain('const isPendingConfirmation = computed')
    expect(component).toContain('v-if="isPendingConfirmation"')
    expect(component).toContain('核对U9结果')
    expect(component).toContain('handleReconcileOrder')
    expect(component).toContain(':loading="isPending(u9SyncActionKeys.reconcile)"')
    expect(component).toContain('v-else')
    expect(component).toContain('结果待确认，请先核对 U9 结果')
  })

  test('keeps small-screen layout within the page and avoids control overlap', () => {
    const component = readSource('components', 'SalesOrderU9Sync.vue')

    expect(component).toContain('max-width: 100%;')
    expect(component).toContain('min-width: 0;')
    expect(component).toContain('grid-template-columns: minmax(0, 1fr);')
    expect(component).toContain('width: 100%;')
    expect(component).toContain('overflow-x: hidden;')
    expect(component).toContain('.assignment-form-grid')
    expect(component).toContain('.private-field-grid')
  })
})
