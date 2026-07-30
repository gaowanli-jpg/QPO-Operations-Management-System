import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

const marketDashboard = readSource('components', 'MarketDashboard.vue')
const marketApi = readSource('services', 'marketApi.js')

describe('sales order import panel', () => {
  test('keeps RFQ import as the default mode and adds sales order mode inside MarketDashboard', () => {
    expect(marketDashboard).toContain("activeImportMode = ref('rfq')")
    expect(marketDashboard).toContain('name="rfq"')
    expect(marketDashboard).toContain('name="sales-order"')
    expect(marketDashboard).toContain('submitRfqPackage')
    expect(marketDashboard).toContain('importRfq(formData)')
  })

  test('wires sales order upload to independent permission and API without changing RFQ permissions', () => {
    expect(marketDashboard).toContain('market:rfq:import-sales-order')
    expect(marketDashboard).toContain('submitSalesOrderImport')
    expect(marketDashboard).toContain('importSalesOrderToQpo(formData)')
    expect(marketDashboard).toContain('downloadSalesOrderImportTemplate')
    expect(marketDashboard).toContain('下载销售订单模板')
    expect(marketDashboard).toContain("v-permission=\"'market:rfq:import-sales-order'\"")
    expect(marketDashboard).toContain("v-permission=\"'market:rfq:import'\"")
  })

  test('sales order mode requires customer master selection and defaults document type to SO01', () => {
    expect(marketDashboard).toContain('salesOrderForm')
    expect(marketDashboard).toContain("documentTypeCode: 'SO01'")
    expect(marketDashboard).toContain('openSalesOrderCustomerPicker')
    expect(marketDashboard).toContain('selectSalesOrderCustomer')
    expect(marketDashboard).toContain('customer-scope="market-rfq"')
    expect(marketDashboard).toContain("formData.append('customerId'")
    expect(marketDashboard).toContain("formData.append('documentTypeCode'")
  })

  test('adds template download actions to both import modes', () => {
    expect(marketDashboard).toContain('downloadRfqTemplate')
    expect(marketDashboard).toContain('downloadSalesOrderTemplate')
    expect(marketDashboard).toContain('下载询价单模板')
    expect(marketDashboard).toContain('下载销售订单模板')
    expect(marketDashboard).toContain(':icon="Download"')
  })

  test('renders clear sales order sync results for success and failure rows', () => {
    expect(marketDashboard).toContain('salesOrderImportResult')
    expect(marketDashboard).toContain('salesOrderResultRows')
    expect(marketDashboard).toContain('prop="rfqNo"')
    expect(marketDashboard).toContain('prop="customerOrderNo"')
    expect(marketDashboard).toContain('prop="internalProjectNo"')
    expect(marketDashboard).toContain('prop="lineCount"')
    expect(marketDashboard).toContain('prop="matchedItemCount"')
    expect(marketDashboard).toContain('prop="unmatchedItemCount"')
    expect(marketDashboard).toContain('prop="status"')
    expect(marketDashboard).toContain('prop="warningMessage"')
    expect(marketDashboard).toContain('successOrderCount')
    expect(marketDashboard).toContain('failedOrderCount')
  })

  test('sales order QPO import copy no longer implies U9 sync', () => {
    const salesOrderPane = marketDashboard.slice(
      marketDashboard.indexOf('name="sales-order"'),
      marketDashboard.indexOf('</el-tab-pane>', marketDashboard.indexOf('name="sales-order"'))
    )

    expect(salesOrderPane).not.toContain('U9')
    expect(marketDashboard).toContain('importSalesOrderToQpo(formData)')
  })

  test('market API exposes sales order QPO import endpoint under sales-orders namespace', () => {
    expect(marketApi).toContain('export const importSalesOrderToQpo')
    expect(marketApi).toContain("multipartPost('/api/market/sales-orders/import-qpo', formData)")
    expect(marketApi).toContain('export const downloadRfqImportTemplate')
    expect(marketApi).toContain("downloadFile('/api/market/import-rfq/template', '客户询价表导入模板.xlsx')")
    expect(marketApi).toContain('export const downloadSalesOrderImportTemplate')
    expect(marketApi).toContain("downloadFile('/api/market/sales-orders/import/template', '销售订单导入模板.xlsx')")
  })
})
