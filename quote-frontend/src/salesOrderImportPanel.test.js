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
    expect(marketDashboard).toContain('importSalesOrder(formData)')
    expect(marketDashboard).toContain('downloadSalesOrderImportTemplate')
    expect(marketDashboard).toContain('下载销售订单模板')
    expect(marketDashboard).toContain("v-permission=\"'market:rfq:import-sales-order'\"")
    expect(marketDashboard).toContain("v-permission=\"'market:rfq:import'\"")
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
    expect(marketDashboard).toContain('prop="customerOrderNo"')
    expect(marketDashboard).toContain('prop="u9Code"')
    expect(marketDashboard).toContain('prop="status"')
    expect(marketDashboard).toContain('prop="errorMessage"')
    expect(marketDashboard).toContain('successOrderCount')
    expect(marketDashboard).toContain('failedOrderCount')
  })

  test('market API exposes sales order import endpoint under sales-orders namespace', () => {
    expect(marketApi).toContain('export const importSalesOrder')
    expect(marketApi).toContain("multipartPost('/api/market/sales-orders/import', formData)")
    expect(marketApi).toContain('export const downloadRfqImportTemplate')
    expect(marketApi).toContain("downloadFile('/api/market/import-rfq/template', '客户询价表导入模板.xlsx')")
    expect(marketApi).toContain('export const downloadSalesOrderImportTemplate')
    expect(marketApi).toContain("downloadFile('/api/market/sales-orders/import/template', '销售订单导入模板.xlsx')")
  })
})
