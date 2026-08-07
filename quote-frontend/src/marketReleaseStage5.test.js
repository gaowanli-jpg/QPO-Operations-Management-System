import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(resolve(currentDir, 'components', 'MarketRelease.vue'), 'utf8')

const expectIncludes = (tokens) => {
  for (const token of tokens) {
    expect(source, `Expected MarketRelease.vue to include ${token}`).toContain(token)
  }
}

const expectExcludes = (tokens) => {
  for (const token of tokens) {
    expect(source, `Expected MarketRelease.vue to exclude ${token}`).not.toContain(token)
  }
}

describe('market release stage 5 cleanup', () => {
  test('removes read-only delivery document tables and export controls from release page', () => {
    expectExcludes([
      '<DeliveryTicketRowsTable',
      '<ProcessSheetRowsTable',
      "import DeliveryTicketRowsTable from './delivery-document/DeliveryTicketRowsTable.vue'",
      "import ProcessSheetRowsTable from './delivery-document/ProcessSheetRowsTable.vue'",
      'releaseTicketTotal',
      'releaseProcessSheetTotal',
      'ticketPagination',
      'processSheetPagination',
      'loadingTickets',
      'loadingProcessSheets',
      'ticketList',
      'processSheetList',
      'pagedTicketList',
      'pagedProcessSheetList',
      'loadTickets',
      'loadProcessSheets',
      'exportExcel',
      'downloadMarketExport',
      'listTickets',
      'listProcessSheets',
      '已生成提单行',
      '已生成工艺表行',
      '导出提单 Excel',
      '导出工艺表 Excel'
    ])
  })

  test('keeps the release generation workflow focused on order selection, item selection, parameters and submit', () => {
    expectIncludes([
      '<OrderPickerDialog',
      'customer-scope="release"',
      'order-scope="release"',
      ':allowed-statuses="RELEASE_ALLOWED_STATUSES"',
      ':multiple="true"',
      ':selected-order-ids="selectedOrderKeys"',
      ':locked-customer-name="selectedCustomerName"',
      ':data="pagedReleaseItems"',
      'releaseItemPagination',
      'releaseForm.orderType',
      'releaseForm.internalProjectNo',
      'releaseForm.customerOrderNo',
      'releaseForm.salesOrderDate',
      'releaseForm.documentTypeCode',
      'releaseForm.customerOrderTitle',
      'releaseForm.arrangeType',
      '@click="submitTicket"',
      'generateTicket(payload)',
      'const batchRfqIds = selectedRowsRfqIds()',
      'rfqIds: batchRfqIds',
      'await refreshSelectedOrdersAfterGeneration(new Set(orderedSelectedRows.map(rowKey)))'
    ])
  })

  test('guides users to delivery documents after successful ticket generation', () => {
    expectIncludes([
      'response.message',
      'response.ticketCount',
      '提单生成请求失败'
    ])
  })
})
