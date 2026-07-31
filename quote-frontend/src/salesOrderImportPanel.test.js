import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

const marketDashboard = readSource('components', 'MarketDashboard.vue')
const marketApi = readSource('services', 'marketApi.js')
const rfqPane = marketDashboard.slice(
  marketDashboard.indexOf('name="rfq"'),
  marketDashboard.indexOf('</el-tab-pane>', marketDashboard.indexOf('name="rfq"'))
)
const salesOrderPane = marketDashboard.slice(
  marketDashboard.indexOf('name="sales-order"'),
  marketDashboard.indexOf('</el-tab-pane>', marketDashboard.indexOf('name="sales-order"'))
)

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
    expect(salesOrderPane).toContain(':model-value="salesOrderCustomerLabel"')
    expect(salesOrderPane).toContain('readonly')
    expect(salesOrderPane).toContain('placeholder="请选择客户主数据"')
    expect(marketDashboard).toContain("formData.append('customerId'")
    expect(marketDashboard).toContain("formData.append('documentTypeCode'")
  })

  test('sales order mode follows RFQ step rhythm with document type and drawing upload separated', () => {
    expect(salesOrderPane).toContain('步骤 1：客户基础信息')
    expect(salesOrderPane).toContain('步骤 2：单据类型编码')
    expect(salesOrderPane).toContain('步骤 3：上传销售订单（Excel）')
    expect(salesOrderPane).toContain('步骤 4：上传关联图纸（PDF）')

    const customerStep = salesOrderPane.slice(
      salesOrderPane.indexOf('步骤 1：客户基础信息'),
      salesOrderPane.indexOf('步骤 2：单据类型编码')
    )
    expect(customerStep).toContain(':model-value="salesOrderCustomerLabel"')
    expect(customerStep).not.toContain('documentTypeCode')

    expect(salesOrderPane.indexOf('步骤 1：客户基础信息')).toBeLessThan(salesOrderPane.indexOf('步骤 2：单据类型编码'))
    expect(salesOrderPane.indexOf('步骤 2：单据类型编码')).toBeLessThan(salesOrderPane.indexOf('步骤 3：上传销售订单（Excel）'))
    expect(salesOrderPane.indexOf('步骤 3：上传销售订单（Excel）')).toBeLessThan(salesOrderPane.indexOf('步骤 4：上传关联图纸（PDF）'))
  })

  test('RFQ import uses the same clean step workflow surface as sales order import', () => {
    expect(rfqPane).toContain('<section class="import-workflow">')
    expect(rfqPane).not.toContain('<section class="erp-surface import-workflow">')
    expect(salesOrderPane).toContain('<section class="sales-order-workflow">')
  })

  test('sales order import submits drawing files and uses RFQ-style success interaction', () => {
    const submitBlock = marketDashboard.slice(
      marketDashboard.indexOf('const submitSalesOrderImport'),
      marketDashboard.indexOf('// 提交询价单和图纸')
    )

    expect(marketDashboard).toContain('salesOrderDrawingFiles')
    expect(submitBlock).toContain('salesOrderDrawingFiles.value.forEach')
    expect(submitBlock).toContain("formData.append('drawings', file)")
    expect(submitBlock).toContain('buildDrawingReportHtml(response.drawingReport)')
    expect(submitBlock).toContain('ElMessageBox.alert')
    expect(submitBlock).toContain('销售订单导入成功')
  })

  test('adds template download actions to both import modes', () => {
    expect(marketDashboard).toContain('downloadRfqTemplate')
    expect(marketDashboard).toContain('downloadSalesOrderTemplate')
    expect(marketDashboard).toContain('下载询价单模板')
    expect(marketDashboard).toContain('下载销售订单模板')
    expect(marketDashboard).toContain(':icon="Download"')
  })

  test('renders independent sales order import results without RFQ columns', () => {
    expect(marketDashboard).toContain('salesOrderImportResult')
    expect(marketDashboard).toContain('salesOrderResultRows')
    expect(salesOrderPane).toContain('prop="salesOrderNo"')
    expect(salesOrderPane).toContain('label="销售订单号"')
    expect(marketDashboard).toContain('prop="customerOrderNo"')
    expect(marketDashboard).toContain('prop="internalProjectNo"')
    expect(marketDashboard).toContain('prop="lineCount"')
    expect(marketDashboard).toContain('prop="matchedItemCount"')
    expect(marketDashboard).toContain('label="已补全数"')
    expect(marketDashboard).toContain('prop="unmatchedItemCount"')
    expect(marketDashboard).toContain('label="待销售匹配"')
    expect(salesOrderPane).not.toContain('label="历史匹配数"')
    expect(marketDashboard).toContain('prop="matchedDrawingCount"')
    expect(marketDashboard).toContain('prop="missingDrawingCount"')
    expect(marketDashboard).toContain('prop="status"')
    expect(marketDashboard).toContain('prop="warningMessage"')
    expect(marketDashboard).toContain('successOrderCount')
    expect(marketDashboard).toContain('failedOrderCount')
    expect(salesOrderPane).not.toContain('prop="rfqNo"')
    expect(salesOrderPane).not.toContain('label="RFQ号"')
  })

  test('sales order QPO import copy no longer implies U9 sync', () => {
    expect(salesOrderPane).not.toContain('U9')
    expect(salesOrderPane).not.toContain('同步')
    expect(salesOrderPane).not.toContain('创建 RFQ')
    expect(salesOrderPane).not.toContain('QPO 主流程')
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
