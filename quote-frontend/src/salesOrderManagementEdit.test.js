import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const componentSource = () => readFileSync(resolve(currentDir, 'components', 'SalesOrderManage.vue'), 'utf8').replace(/\r\n/g, '\n')
const replacementDialogSource = () => readFileSync(resolve(currentDir, 'components', 'sales-order', 'SalesOrderItemDataReplacementDialog.vue'), 'utf8').replace(/\r\n/g, '\n')
const apiSource = () => readFileSync(resolve(currentDir, 'services', 'orderApi.js'), 'utf8')

describe('sales order management editable detail', () => {
  test('order detail exposes header editing under sales order update permission', () => {
    const component = componentSource()

    expect(component).toContain("const SALES_ORDER_UPDATE_PERMISSION = 'market:sales-order:update'")
    expect(component).toContain('订单信息维护')
    expect(component).toContain('单据类型')
    expect(component).toContain('下单日期')
    expect(component).toContain('客户订单号')
    expect(component).toContain('客户项目号')
    expect(component).toContain('客户下单抬头')
    expect(component).toContain('订单类型')
    expect(component).toContain('内部项目号')
    expect(component).toContain('orderEditDraft.documentTypeCode')
    expect(component).toContain('orderEditDraft.salesOrderDate')
    expect(component).toContain('orderEditDraft.customerOrderNo')
    expect(component).toContain('orderEditDraft.customerProjectNo')
    expect(component).toContain('orderEditDraft.customerOrderTitle')
    expect(component).toContain('orderEditDraft.orderType')
    expect(component).toContain('orderEditDraft.internalProjectNo')
    expect(component).toContain('v-permission="SALES_ORDER_UPDATE_PERMISSION"')
    expect(component).toContain('saveSalesOrderHeaderChanges')
    expect(component).toContain('salesOrderEditLocked')
  })

  test('item detail table supports inline save and data replacement for the requested item fields', () => {
    const component = componentSource()
    const itemPane = component.slice(
      component.indexOf('<el-tab-pane label="零件明细" name="items">'),
      component.indexOf('<el-tab-pane label="提单行" name="tickets">')
    )

    expect(itemPane).toContain('表格数据替换')
    expect(itemPane).toContain('保存修改')
    expect(itemPane).toContain('SalesOrderItemDataReplacementDialog')
    expect(itemPane).toContain('itemDrafts[row.id]')
    expect(itemPane).toContain('itemDrafts[row.id].version')
    expect(itemPane).toContain('itemDrafts[row.id].qty')
    expect(itemPane).toContain('itemDrafts[row.id].untaxedUnitPrice')
    expect(itemPane).toContain('itemDrafts[row.id].taxIncludedUnitPrice')
    expect(itemPane).toContain('itemDrafts[row.id].customerDeliveryDate')
    expect(itemPane).toContain('itemDrafts[row.id].internalDeliveryDate')
    expect(itemPane).toContain('itemDrafts[row.id].customerOrderNo')
    expect(itemPane).toContain('itemDrafts[row.id].customerProjectNo')
    expect(itemPane).toContain('itemDrafts[row.id].internalProjectNo')
    expect(itemPane).toContain('itemDrafts[row.id].materialName')
    expect(itemPane).toContain('itemDrafts[row.id].surfaceTreatment')
    expect(itemPane).toContain('itemDrafts[row.id].remark')
    expect(component).toContain("'version'")
    expect(component).toContain("'qty'")
    expect(component).toContain("'untaxedUnitPrice'")
    expect(component).toContain("'taxIncludedUnitPrice'")
    expect(component).toContain("'customerDeliveryDate'")
    expect(component).toContain("'internalDeliveryDate'")
    expect(component).toContain("'customerOrderNo'")
    expect(component).toContain("'customerProjectNo'")
    expect(component).toContain("'internalProjectNo'")
    expect(component).toContain("'materialName'")
    expect(component).toContain("'surfaceTreatment'")
    expect(component).toContain("'remark'")
    expect(component).toContain('saveSalesOrderItemChanges')
    expect(component).toContain('openSalesOrderItemDataReplacementDialog')

    const replacementDialog = replacementDialogSource()
    expect(replacementDialog).toContain('未税单价、含税单价、交期')
  })

  test('editing is locked while U9 is syncing pending confirmation or already synced', () => {
    const component = componentSource()

    expect(component).toContain("const SALES_ORDER_EDIT_LOCKED_U9_STATUSES = ['同步中', '结果待确认', '已同步']")
    expect(component).toContain('当前 U9 状态不允许修改销售订单')
    expect(component).toContain(':disabled="salesOrderEditLocked"')
  })

  test('sales order item data replacement advertises material and surface treatment without replacing version', () => {
    const replacementDialog = replacementDialogSource()

    expect(replacementDialog).toContain('材料')
    expect(replacementDialog).toContain('表面处理')
    expect(replacementDialog).not.toContain('版本可替换')
  })

  test('order api exposes sales order management maintenance endpoints without using u9 sync paths', () => {
    const api = apiSource()

    expect(api).toContain('saveSalesOrderHeader')
    expect(api).toContain('saveSalesOrderItems')
    expect(api).toContain('previewSalesOrderItemDataReplacement')
    expect(api).toContain('confirmSalesOrderItemDataReplacement')
    expect(api).toContain("/api/market/sales-order-management/orders/${salesOrderId}")
    expect(api).toContain("/api/market/sales-order-management/orders/${salesOrderId}/items/batch")
    expect(api).toContain("/api/market/sales-order-management/orders/${salesOrderId}/items/data-replacement/preview")
    expect(api).toContain("/api/market/sales-order-management/orders/${salesOrderId}/items/data-replacement/confirm")
    expect(api).not.toContain('saveSalesOrderManagementU9Items')
  })
})
