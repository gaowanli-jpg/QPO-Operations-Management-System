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
    expect(component).toContain("const SALES_ORDER_CHANGE_VIEW_PERMISSION = 'change:sales-order:view'")
    expect(component).toContain("const SALES_ORDER_CHANGE_UPDATE_PERMISSION = 'change:sales-order:update'")
    expect(component).toContain("const SALES_ORDER_CHANGE_ALLOWED_STATUSES = ['已生成提单']")
    expect(component).toContain('authStore.hasPermission(SALES_ORDER_CHANGE_UPDATE_PERMISSION)')
    expect(component).toContain('hasChangeUpdatePermission')
    expect(component).toContain('只读查看')
    expect(component).toContain('不调用 U9，只做本地校正')
    expect(component).toContain('已同步 U9 销售订单')
    expect(component).toContain('class="erp-table-surface sales-order-change-main-panel"')
    expect(component).toContain(':allowed-statuses="SALES_ORDER_CHANGE_ALLOWED_STATUSES"')
    expect(component).not.toContain('void listSalesOrderChangeOrders')
    expect(component).not.toContain('同步U9')
    expect(component).not.toContain('核对U9结果')
    expect(component).not.toContain('刷新U9选项')
    expect(component).not.toContain('同步条件')
    expect(component).not.toContain('Seller')
    expect(component).not.toContain('SaleDepartment')
    expect(component).not.toContain('syncSalesOrderU9')
    expect(component).not.toContain('reconcileSalesOrderU9')
    expect(component).not.toContain('refreshSalesOrderU9SyncOptions')
    expect(component).not.toContain('saveSalesOrderU9Assignment')
  })

  test('adds sales order change API helpers and order picker scope', () => {
    const api = readSource('services', 'salesOrderChangeApi.js')
    const picker = readSource('components', 'OrderPickerDialog.vue')

    expect(api).toContain("request('/api/change/sales-orders'")
    expect(api).toContain('previewSalesOrderChange')
    expect(api).toContain('/api/change/sales-orders/${salesOrderId}/preview')
    expect(api).toContain('saveSalesOrderChangeItems')
    expect(api).toContain('/api/change/sales-orders/${salesOrderId}/items')
    expect(api).toContain("method: 'PUT'")
    expect(picker).toContain("import { listSalesOrderChangeOrders } from '../services/salesOrderChangeApi'")
    expect(picker).toContain("props.orderScope === 'sales-order-change'")
    expect(picker).toContain('await listSalesOrderChangeOrders(params)')
    expect(picker).toContain("'sales-order-change': 'change:sales-order:customer-select'")
    expect(picker).toContain("'sales-order-change': ['已生成提单']")
    expect(picker).toContain("'sales-order-change': '销售订单'")
  })

  test('supports stage 6 line editing with required change reason', () => {
    const component = readSource('components', 'SalesOrderChangeManage.vue')

    expect(component).toContain('saveSalesOrderChangeItems')
    expect(component).toContain('const SALES_ORDER_CHANGE_ITEM_EDITABLE_FIELDS = [')
    ;[
      'qty',
      'customerDeliveryDate',
      'internalDeliveryDate',
      'untaxedUnitPrice',
      'customerOrderNo',
      'customerProjectNo',
      'internalProjectNo',
      'remark'
    ].forEach(field => expect(component).toContain(`'${field}'`))

    expect(component).toContain('itemDrafts')
    expect(component).toContain('savedItemDrafts')
    expect(component).toContain('dirtyItemIds')
    expect(component).toContain('dirtyItemCount')
    expect(component).toContain('handleItemDraftChange')
    expect(component).toContain('buildDirtyItemPayload')
    expect(component).toContain('itemEditReadOnly')
    expect(component).toContain(':disabled="itemEditReadOnly"')
    expect(component).toContain('保存修改')
    expect(component).toContain('itemEditSaving')
    expect(component).toContain('reasonDialogVisible')
    expect(component).toContain('changeReason')
    expect(component).toContain('变更原因')
    expect(component).toContain('handleOpenSaveReasonDialog')
    expect(component).toContain('handleConfirmSaveItemEdits')
    expect(component).toContain('变更原因不能为空')
    expect(component).toContain('reason: changeReason.value.trim()')
    expect(component).toContain('applyPreviewData(response.preview)')
    expect(component).toContain('dirtyItemIds.value = new Set()')
    expect(component).toContain('changeReason.value =')
    expect(component).toContain('v-permission="SALES_ORDER_CHANGE_UPDATE_PERMISSION"')

    expect(component).toContain('v-model="itemDrafts[row.salesOrderItemId].qty"')
    expect(component).toContain('v-model="itemDrafts[row.salesOrderItemId].untaxedUnitPrice"')
    expect(component).toContain('v-model="itemDrafts[row.salesOrderItemId].customerDeliveryDate"')
    expect(component).toContain('v-model="itemDrafts[row.salesOrderItemId].internalDeliveryDate"')
    expect(component).toContain('v-model="itemDrafts[row.salesOrderItemId].customerOrderNo"')
    expect(component).toContain('v-model="itemDrafts[row.salesOrderItemId].customerProjectNo"')
    expect(component).toContain('v-model="itemDrafts[row.salesOrderItemId].internalProjectNo"')
    expect(component).toContain('v-model="itemDrafts[row.salesOrderItemId].remark"')
    expect(component).not.toContain('v-model="itemDrafts[row.salesOrderItemId].materialNo"')
    expect(component).not.toContain('v-model="itemDrafts[row.salesOrderItemId].partNo"')
    expect(component).not.toContain('v-model="itemDrafts[row.salesOrderItemId].version"')
    expect(component).not.toContain('v-model="itemDrafts[row.salesOrderItemId].lineNo"')
  })
})
