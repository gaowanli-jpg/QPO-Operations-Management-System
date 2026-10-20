import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

describe('sales order management governance and drawings', () => {
  const salesOrderManage = readSource('components', 'SalesOrderManage.vue')
  const orderApi = readSource('services', 'orderApi.js')

  test('uses sales order management APIs for list, detail, paged tabs, status actions, and drawings', () => {
    for (const apiName of [
      'listSalesOrders',
      'getSalesOrder',
      'listSalesOrderItems',
      'listSalesOrderTickets',
      'listSalesOrderProcessSheets',
      'getSalesOrderDrawingSummary',
      'discardSalesOrder',
      'restoreSalesOrder',
      'discardSalesOrderItemForU9Change',
      'restoreSalesOrderItemForU9Change',
      'replaceSalesOrderItemField',
      'supplementSalesOrderDrawings',
      'replaceSalesOrderDrawings',
      'downloadSalesOrderItemDetails',
      'downloadSalesOrderProductionTaskNotice'
    ]) {
      expect(orderApi).toContain(`export const ${apiName}`)
      expect(salesOrderManage).toContain(apiName)
    }
    expect(orderApi).toContain('/api/market/sales-order-management/orders')
    expect(orderApi).toContain('/api/market/sales-order-management/orders/${salesOrderId}/supplement-drawings')
    expect(orderApi).toContain('/api/market/sales-order-management/orders/${salesOrderId}/drawings/replace')
    expect(orderApi).toContain('/api/market/sales-order-management/orders/${salesOrderId}/items/export')
    expect(orderApi).toContain('/api/market/sales-order-management/orders/${salesOrderId}/production-task-notice/export')
    expect(orderApi).not.toContain('/api/market/sales-order-management/items/${itemId}/drawings')
  })

  test('shows only sales order governance buttons and never exposes delete behavior', () => {
    expect(salesOrderManage).toContain('SALES_ORDER_STATUS_ACTIONS')
    expect(salesOrderManage).toContain("permission: 'market:sales-order:discard'")
    expect(salesOrderManage).toContain("permission: 'market:sales-order:restore'")
    expect(salesOrderManage).toContain("status: '已生成提单'")
    expect(salesOrderManage).toContain('恢复为已生成提单')
    expect(salesOrderManage).toContain('SALES_ORDER_STATUS_ACTIONS.filter(action => action.status === row.status)')
    expect(salesOrderManage).toContain('v-permission="action.permission"')
    expect(salesOrderManage).not.toContain('deleteOrder')
    expect(salesOrderManage).not.toContain('market:order:delete')
  })

  test('supports order-level sales order drawing supplement with shared dialog', () => {
    expect(salesOrderManage).toContain("v-permission=\"'market:sales-order:supplement-drawing'\"")
    expect(salesOrderManage).toContain('<SupplementDrawingDialog')
    expect(salesOrderManage).toContain('v-model:files="supplementFiles"')
    expect(salesOrderManage).toContain('v-model:version-files="supplementVersionFiles"')
    expect(salesOrderManage).toContain(':missing-items="missingDrawingItems"')
    expect(salesOrderManage).toContain(':permission="\'market:sales-order:supplement-drawing\'"')
    expect(salesOrderManage).toContain('@click="openSupplementDialog"')
    expect(salesOrderManage).toContain('supplementSalesOrderDrawings(salesOrderId, formData)')
    expect(salesOrderManage).toContain("formData.append('drawings', file)")
    expect(salesOrderManage).toContain("formData.append('versionDrawings', file)")
    expect(salesOrderManage).toContain('drawingSummary.value?.missingItems')
    expect(salesOrderManage).not.toContain('openSupplementDialog(scope.row)')
    expect(salesOrderManage).not.toContain('supplementTargetItem')
    expect(salesOrderManage).not.toContain('supplementSalesOrderItemDrawings')
    expect(salesOrderManage).toContain('await fetchDetailItems()')
    expect(salesOrderManage).toContain('await loadOrders()')
  })

  test('detail view exposes secured production task notice export action', () => {
    expect(salesOrderManage).toContain('导出生产任务通知单')
    expect(salesOrderManage).toContain("v-permission=\"'market:sales-order:production-task-notice-export'\"")
    expect(salesOrderManage).toContain('productionTaskNoticeExport: (orderId) => `sales-order:production-task-notice-export:${orderId || \'none\'}`')
    expect(salesOrderManage).toContain('productionTaskNoticeExportPending')
    expect(salesOrderManage).toContain('downloadSalesOrderProductionTaskNotice')
    expect(salesOrderManage).toContain('handleProductionTaskNoticeExport')
    expect(salesOrderManage).toContain('runAction(salesOrderActionKeys.productionTaskNoticeExport(salesOrderId)')
  })

  test('detail item tab exposes secured full item detail export action', () => {
    expect(salesOrderManage).toContain('导出明细表')
    expect(salesOrderManage).toContain("v-permission=\"'market:sales-order:item-detail-export'\"")
    expect(salesOrderManage).toContain('itemDetailExport: (orderId) => `sales-order:item-detail-export:${orderId || \'none\'}`')
    expect(salesOrderManage).toContain('itemDetailExportPending')
    expect(salesOrderManage).toContain('downloadSalesOrderItemDetails')
    expect(salesOrderManage).toContain('handleSalesOrderItemDetailExport')
    expect(salesOrderManage).toContain('runAction(salesOrderActionKeys.itemDetailExport(salesOrderId)')
  })

  test('detail item table makes title description and item status editable and replaceable', () => {
    const itemTableSource = salesOrderManage.slice(
      salesOrderManage.indexOf('<el-table-column prop="lineNo"'),
      salesOrderManage.indexOf('<el-table-column label="未税单价"')
    )
    const editableFieldsSource = salesOrderManage.slice(
      salesOrderManage.indexOf('const SALES_ORDER_ITEM_EDITABLE_FIELDS = ['),
      salesOrderManage.indexOf('const SALES_ORDER_STATUS_ACTIONS = [')
    )
    const draftSource = salesOrderManage.slice(
      salesOrderManage.indexOf('const buildItemDraft = (row = {}) => ({'),
      salesOrderManage.indexOf('const buildOrderPayload = () =>')
    )
    const replacementDialog = readSource('components', 'sales-order', 'SalesOrderItemDataReplacementDialog.vue')

    expect(itemTableSource).toContain('prop="customerOrderTitle" label="客户下单抬头"')
    expect(itemTableSource.indexOf('prop="materialName" label="材料"')).toBeLessThan(
      itemTableSource.indexOf('prop="description" label="物料描述"')
    )
    expect(itemTableSource.indexOf('prop="description" label="物料描述"')).toBeLessThan(
      itemTableSource.indexOf('prop="surfaceTreatment" label="表面处理"')
    )
    expect(itemTableSource).toContain('v-model="itemDrafts[row.id].customerOrderTitle"')
    expect(itemTableSource).toContain('v-model="itemDrafts[row.id].description"')
    expect(itemTableSource).toContain('v-model="itemDrafts[row.id].status"')
    expect(itemTableSource).toContain('v-model="itemDrafts[row.id].orderType"')
    expect(itemTableSource).toContain('<el-option v-for="type in SALES_ORDER_TYPES"')

    for (const field of ['customerOrderTitle', 'description', 'status', 'orderType']) {
      expect(editableFieldsSource).toContain(`'${field}'`)
      expect(draftSource).toContain(`${field}: draftValue(row.${field})`)
    }
    expect(replacementDialog).toContain('客户下单抬头、订单类型、物料描述、订单状态')
  })

  test('detail item tab exposes whole-order field replacement row above the item table', () => {
    const replacementSource = salesOrderManage.slice(
      salesOrderManage.indexOf('class="item-field-replacement-row"'),
      salesOrderManage.indexOf('<el-table :data="detailItemList"')
    )

    expect(orderApi).toContain('export const replaceSalesOrderItemField')
    expect(orderApi).toContain('/api/market/sales-order-management/orders/${salesOrderId}/items/field-replacement')
    expect(replacementSource).toContain('整单零件字段替换')
    expect(replacementSource).toContain('SALES_ORDER_ITEM_FIELD_REPLACEMENTS')
    expect(replacementSource).toContain('itemFieldReplacementDrafts[field.key]')
    expect(replacementSource).toContain('saveSalesOrderItemFieldReplacement(field)')
    expect(replacementSource).toContain('isItemFieldReplacementPending(field.key)')
    expect(replacementSource).toContain('v-permission="SALES_ORDER_UPDATE_PERMISSION"')
    for (const label of ['客户下单抬头', '客户订单号', '客户项目号', '内部项目号', '订单类型']) {
      expect(salesOrderManage).toContain(`label: '${label}'`)
    }
    for (const field of ['customerOrderTitle', 'customerOrderNo', 'customerProjectNo', 'internalProjectNo', 'orderType']) {
      expect(salesOrderManage).toContain(`key: '${field}'`)
    }
    expect(salesOrderManage).toContain('replaceSalesOrderItemField(salesOrderId, {')
    expect(salesOrderManage).toContain('dirtyItemCount.value > 0')
  })

  test('detail item toolbar exposes whole-order price conversion before saving row edits', () => {
    const toolbarSource = salesOrderManage.slice(
      salesOrderManage.indexOf('<div class="item-maintenance-action-buttons">'),
      salesOrderManage.indexOf('<el-table :data="detailItemList"')
    )

    expect(orderApi).toContain('export const convertSalesOrderUntaxedToTaxIncluded')
    expect(orderApi).toContain('export const convertSalesOrderTaxIncludedToUntaxed')
    expect(toolbarSource).toContain('未税转含税')
    expect(toolbarSource).toContain('含税转未税')
    expect(toolbarSource).toContain('handleSalesOrderPriceConversion')
    expect(toolbarSource.indexOf('未税转含税')).toBeLessThan(toolbarSource.indexOf('保存修改'))
    expect(toolbarSource.indexOf('含税转未税')).toBeLessThan(toolbarSource.indexOf('保存修改'))
    expect(salesOrderManage).toContain('itemPriceConversionPending')
    expect(salesOrderManage).toContain('convertSalesOrderUntaxedToTaxIncluded')
    expect(salesOrderManage).toContain('convertSalesOrderTaxIncludedToUntaxed')
    expect(salesOrderManage).toContain('dirtyItemCount.value > 0')
  })

  test('detail view exposes sales order drawing replacement between supplement and production export', () => {
    expect(salesOrderManage).toContain('导入替换图纸')
    expect(salesOrderManage).toContain('replaceSalesOrderDrawings')
    expect(salesOrderManage).toContain('replaceSalesOrderDrawingVisible')
    expect(salesOrderManage).toContain('submitReplaceSalesOrderDrawings')
    expect(salesOrderManage).toContain('salesOrderDrawingReplace: (orderId) => `sales-order:drawing-replace:${orderId || \'none\'}`')
    expect(salesOrderManage).toContain('replaceSalesOrderDrawingUploadPending')
    expect(salesOrderManage).toContain(':permission="\'market:sales-order:supplement-drawing\'"')
    expect(salesOrderManage).toContain('submit-text="上传并替换"')
    expect(salesOrderManage).toContain('bound-result-label="已替换"')
    expect(salesOrderManage).toContain('replaceSalesOrderDrawingFiles.value.forEach(file => formData.append(\'drawings\', file))')
    expect(salesOrderManage).toContain('replaceSalesOrderVersionDrawingFiles.value.forEach(file => formData.append(\'versionDrawings\', file))')
    expect(salesOrderManage).toContain('replaceSalesOrderDrawingFolderFiles.value.forEach(file => formData.append(\'drawingFolderFiles\', file))')
    expect(salesOrderManage.indexOf('补传图纸')).toBeLessThan(salesOrderManage.indexOf('导入替换图纸'))
    expect(salesOrderManage.indexOf('导入替换图纸')).toBeLessThan(salesOrderManage.indexOf('导出生产任务通知单'))
  })

  test('detail view uses backend pagination for independent sales order tabs', () => {
    expect(salesOrderManage).toContain('detailItemPagination')
    expect(salesOrderManage).toContain('detailTicketPagination')
    expect(salesOrderManage).toContain('detailProcessSheetPagination')
    expect(salesOrderManage).toContain('listSalesOrderItems(salesOrderId')
    expect(salesOrderManage).toContain('listSalesOrderTickets(salesOrderId')
    expect(salesOrderManage).toContain('listSalesOrderProcessSheets(salesOrderId')
  })

  test('sales order detail exposes item-level U9/change exclusion without hiding excluded rows', () => {
    expect(orderApi).toContain('discardSalesOrderItemForU9Change')
    expect(orderApi).toContain('/api/market/sales-order-management/orders/${salesOrderId}/items/${itemId}/discard')
    expect(orderApi).toContain('restoreSalesOrderItemForU9Change')
    expect(orderApi).toContain('/api/market/sales-order-management/orders/${salesOrderId}/items/${itemId}/restore')

    expect(salesOrderManage).toContain('u9ChangeExcluded')
    expect(salesOrderManage).toContain('仅从 U9 同步和销售变更排除')
    expect(salesOrderManage).toContain('不影响 MES 物料号、WMS 库存、已提/待产处理和 MES 生产下发')
    expect(salesOrderManage).toContain('handleSalesOrderItemU9ChangeExclusion')
    expect(salesOrderManage).toContain('discardSalesOrderItemForU9Change')
    expect(salesOrderManage).toContain('restoreSalesOrderItemForU9Change')
    expect(salesOrderManage).toContain("v-permission=\"SALES_ORDER_UPDATE_PERMISSION\"")
    expect(salesOrderManage).toContain('salesOrderItemU9ChangeExclusionLocked')
    expect(salesOrderManage).toContain("['同步中', '结果待确认'].includes")
    expect(salesOrderManage).toContain('请输入废弃原因')
    expect(salesOrderManage).toContain('请输入恢复原因')
    expect(salesOrderManage).toContain('detailItemList.value = response.records || []')
    expect(salesOrderManage).not.toContain('detailItemList.value = (response.records || []).filter')
  })
})
