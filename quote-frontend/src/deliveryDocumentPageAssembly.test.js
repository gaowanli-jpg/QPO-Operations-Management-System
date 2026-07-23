import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

const source = readSource('components', 'DeliveryDocumentManage.vue')
const ticketTableSource = readSource('components', 'delivery-document', 'DeliveryTicketRowsTable.vue')
const processSheetTableSource = readSource('components', 'delivery-document', 'ProcessSheetRowsTable.vue')

const expectIncludes = (tokens) => {
  for (const token of tokens) {
    expect(source, `Expected DeliveryDocumentManage.vue to include ${token}`).toContain(token)
  }
}

const expectExcludes = (tokens) => {
  for (const token of tokens) {
    expect(source, `Expected DeliveryDocumentManage.vue to exclude ${token}`).not.toContain(token)
  }
}

describe('delivery document page assembly', () => {
  test('loads summary and both paged result tables after selecting an allowed order', () => {
    expectIncludes([
      'getDeliveryDocumentSummary',
      'listDeliveryDocumentTickets',
      'listDeliveryDocumentProcessSheets',
      'const selectedRfqId = ref(null)',
      'const ticketPagination = reactive({ page: 1, pageSize: DEFAULT_CLIENT_PAGE_SIZE })',
      'const processSheetPagination = reactive({ page: 1, pageSize: DEFAULT_CLIENT_PAGE_SIZE })',
      'const handleOrderSelected = async (order) =>',
      'selectedRfqId.value = order.id',
      'resetDocumentPagination()',
      'await loadDeliveryDocumentWorkspace(order.id)',
      'const loadDeliveryDocumentWorkspace = async (rfqId) =>',
      'await Promise.all([',
      'loadDocumentSummary(rfqId)',
      'loadTicketRows(rfqId)',
      'loadProcessSheetRows(rfqId)',
      'const response = await getDeliveryDocumentSummary(rfqId)',
      'orderInfo.value = response.orderInfo || selectedOrder.value || {}',
      'ticketRows.value = response.records || []',
      'ticketTotal.value = Number(response.total ?? 0)',
      'processSheetRows.value = response.records || []',
      'processSheetTotal.value = Number(response.total ?? 0)'
    ])
  })

  test('renders empty state before selection and table components after selection', () => {
    expectIncludes([
      'v-if="!selectedRfqId"',
      '请先选择部分已提单或已生成提单订单',
      'v-else class="delivery-document-results"',
      '<DeliveryTicketRowsTable',
      ':rows="ticketRows"',
      ':total="ticketTotal"',
      ':page="ticketPagination.page"',
      ':page-size="ticketPagination.pageSize"',
      ':loading="loadingTickets"',
      ':exportable="ticketExportable"',
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
      '@page-change="handleProcessSheetPageChange"',
      '@page-size-change="handleProcessSheetPageSizeChange"',
      '@export="exportDeliveryDocumentExcel(\'process\')"'
    ])
  })

  test('uses the new delivery document export endpoints only', () => {
    expectIncludes([
      'downloadDeliveryDocumentExport',
      'const exportPathMap = {',
      "tickets: 'tickets'",
      "process: 'process-sheets'",
      'const result = await downloadDeliveryDocumentExport(',
      'path,',
      'selectedRfqId.value,',
      '`${path}-${selectedRfqId.value}.xlsx`'
    ])
    expect(source).not.toContain('downloadMarketExport')
  })

  test('guards both export buttons with the delivery document export permission', () => {
    for (const tableSource of [ticketTableSource, processSheetTableSource]) {
      expect(tableSource).toContain(`v-permission="'market:delivery-document:export'"`)
    }
  })

  test('does not bring the release generation left-side parameter panel into the new page', () => {
    expectExcludes([
      'release-workspace',
      'release-panel',
      'ticket-form',
      'releaseForm',
      'internal-lead-panel',
      'internal-project-panel',
      '通过零件选择',
      '生成正式提单',
      'release-order-type-select'
    ])
  })
})
