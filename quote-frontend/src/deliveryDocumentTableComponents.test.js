import { describe, expect, test } from 'vitest'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')
const readIfExists = (...segments) => {
  const path = resolve(currentDir, ...segments)
  return existsSync(path) ? readFileSync(path, 'utf8') : ''
}

const ticketTablePath = resolve(currentDir, 'components', 'delivery-document', 'DeliveryTicketRowsTable.vue')
const processTablePath = resolve(currentDir, 'components', 'delivery-document', 'ProcessSheetRowsTable.vue')
const ticketTable = () => readIfExists('components', 'delivery-document', 'DeliveryTicketRowsTable.vue')
const processTable = () => readIfExists('components', 'delivery-document', 'ProcessSheetRowsTable.vue')
const deliveryDocumentManage = () => readSource('components', 'DeliveryDocumentManage.vue')

const expectIncludes = (source, fileName, tokens) => {
  for (const token of tokens) {
    expect(source, `Expected ${fileName} to include ${token}`).toContain(token)
  }
}

describe('delivery document table components', () => {
  test('exposes parent-controlled table state and pagination events', () => {
    expect(existsSync(ticketTablePath)).toBe(true)
    expect(existsSync(processTablePath)).toBe(true)

    for (const [fileName, source] of [
      ['DeliveryTicketRowsTable.vue', ticketTable()],
      ['ProcessSheetRowsTable.vue', processTable()]
    ]) {
      expectIncludes(source, fileName, [
        'rows: {',
        'total: {',
        'page: {',
        'pageSize: {',
        'loading: {',
        'exportable: {',
        'exportLoading: {',
        'emptyText: {',
        "const emit = defineEmits(['page-change', 'page-size-change', 'export'])",
        ':data="rows"',
        'v-loading="loading"',
        ':empty-text="emptyText"',
        ':loading="exportLoading"',
        ':disabled="!exportable || exportLoading"',
        '@click="$emit(\'export\')"',
        ':current-page="page"',
        ':page-size="pageSize"',
        ':total="total"',
        '@current-change="handlePageChange"',
        '@size-change="handlePageSizeChange"',
        "emit('page-size-change', value)",
        "emit('page-change', 1)"
      ])
    }
  })

  test('keeps extracted delivery ticket columns aligned with the existing release table', () => {
    expectIncludes(ticketTable(), 'DeliveryTicketRowsTable.vue', [
      '<div class="panel-title">已生成提单行</div>',
      '导出提单 Excel',
      'prop="internalProjectNo" label="内部项目号" width="130"',
      'prop="orderType" label="订单类型" width="100" align="center"',
      'prop="itemType" label="类型" width="90" align="center"',
      'prop="partNo" label="图号" width="130"',
      'prop="version" label="版本" width="80" align="center"',
      'prop="qty" label="数量" width="70" align="center"',
      'prop="engineeringMaterialName" label="材料" width="110"',
      'prop="surfaceTreatment" label="表面处理" width="110"',
      'prop="startDate" label="开工日期" width="110"',
      'prop="deliveryDate" label="交货日期" width="110"',
      'label="安排类型" width="100"',
      'displayTicketText(row.arrangeType)',
      'label="备注" min-width="180"',
      'displayTicketText(row.remark)',
      "return String(value || '').replaceAll('待产', '在产')"
    ])
  })

  test('keeps extracted process sheet columns and formatting aligned with the existing release table', () => {
    expectIncludes(processTable(), 'ProcessSheetRowsTable.vue', [
      '<div class="panel-title">已生成工艺表行</div>',
      '导出工艺表 Excel',
      'prop="internalProjectNo" label="内部项目号" width="130"',
      'prop="itemType" label="类型" width="90" align="center"',
      'prop="partNo" label="图号" width="130"',
      'prop="version" label="版本" width="80" align="center"',
      'prop="description" label="材料描述" min-width="160"',
      'prop="qty" label="数量" width="70" align="center"',
      'prop="engineeringMaterialName" label="材料" width="120"',
      'label="毛重(kg)" width="95" align="right"',
      'formatWeight(row.materialGrossWeight)',
      'label="净重(kg)" width="95" align="right"',
      'formatWeight(row.partNetWeight)',
      'prop="surfaceTreatment" label="表面处理" width="130"',
      'prop="deliveryDate" label="交期" width="110"',
      'label="工艺路线" min-width="220"',
      'formatRoute(scope.row.processRoute)',
      '.map(item => `${item.processName || \'\'}${item.inputVal ? `(${item.inputVal})` : \'\'}`)',
      'return number.toFixed(3).replace(/\\.?0+$/, \'\')'
    ])
  })

  test('uses the extracted components from DeliveryDocumentManage without keeping table formatting logic inline', () => {
    const source = deliveryDocumentManage()

    expectIncludes(source, 'DeliveryDocumentManage.vue', [
      "import DeliveryTicketRowsTable from './delivery-document/DeliveryTicketRowsTable.vue'",
      "import ProcessSheetRowsTable from './delivery-document/ProcessSheetRowsTable.vue'",
      '<DeliveryTicketRowsTable',
      ':rows="ticketRows"',
      ':total="ticketTotal"',
      ':page="ticketPagination.page"',
      ':page-size="ticketPagination.pageSize"',
      ':loading="loadingTickets"',
      ':exportable="ticketExportable"',
      ':export-loading="ticketExportLoading"',
      '@page-change="handleTicketPageChange"',
      '@page-size-change="handleTicketPageSizeChange"',
      '@export="exportDeliveryDocumentExcel(\'tickets\')"',
      '<ProcessSheetRowsTable',
      ':rows="processSheetRows"',
      ':total="processSheetTotal"',
      ':page="processSheetPagination.page"',
      ':page-size="processSheetPagination.pageSize"',
      ':loading="loadingProcessSheets"',
      ':exportable="processSheetExportable"',
      ':export-loading="processSheetExportLoading"',
      '@page-change="handleProcessSheetPageChange"',
      '@page-size-change="handleProcessSheetPageSizeChange"',
      '@export="exportDeliveryDocumentExcel(\'process\')"'
    ])

    expect(source).not.toContain('const formatRoute = (route) =>')
    expect(source).not.toContain('const formatWeight = (value) =>')
    expect(source).not.toContain('const displayTicketText = (value) =>')
  })
})
