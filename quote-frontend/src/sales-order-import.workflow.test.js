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

  test('sales order mode requires customer master selection and defaults document type to SO06', () => {
    expect(marketDashboard).toContain('salesOrderForm')
    expect(marketDashboard).toContain("documentTypeCode: 'SO06'")
    expect(salesOrderPane).toContain('placeholder="SO06"')
    expect(marketDashboard).toContain('openSalesOrderCustomerPicker')
    expect(marketDashboard).toContain('selectSalesOrderCustomer')
    expect(marketDashboard).toContain('customer-scope="market-rfq"')
    expect(salesOrderPane).toContain(':model-value="salesOrderCustomerLabel"')
    expect(salesOrderPane).toContain('readonly')
    expect(salesOrderPane).toContain('placeholder="请选择客户主数据"')
    expect(marketDashboard).toContain("formData.append('customerId'")
    expect(marketDashboard).toContain("formData.append('documentTypeCode'")
  })

  test('sales order mode follows RFQ step rhythm with document type and order defaults separated', () => {
    expect(salesOrderPane).toContain('步骤 1：客户基础信息')
    expect(salesOrderPane).toContain('步骤 2：单据类型与订单信息')
    expect(salesOrderPane).toContain('步骤 3：上传销售订单（Excel）')
    expect(salesOrderPane).toContain('步骤 4：上传关联图纸（PDF）')

    const customerStep = salesOrderPane.slice(
      salesOrderPane.indexOf('步骤 1：客户基础信息'),
      salesOrderPane.indexOf('步骤 2：单据类型与订单信息')
    )
    expect(customerStep).toContain(':model-value="salesOrderCustomerLabel"')
    expect(customerStep).not.toContain('documentTypeCode')

    expect(salesOrderPane.indexOf('步骤 1：客户基础信息')).toBeLessThan(salesOrderPane.indexOf('步骤 2：单据类型与订单信息'))
    expect(salesOrderPane.indexOf('步骤 2：单据类型与订单信息')).toBeLessThan(salesOrderPane.indexOf('步骤 3：上传销售订单（Excel）'))
    expect(salesOrderPane.indexOf('步骤 3：上传销售订单（Excel）')).toBeLessThan(salesOrderPane.indexOf('步骤 4：上传关联图纸（PDF）'))
  })

  test('sales order step 2 renders the order default information matrix', () => {
    const documentStep = salesOrderPane.slice(
      salesOrderPane.indexOf('步骤 2：单据类型与订单信息'),
      salesOrderPane.indexOf('步骤 3：上传销售订单（Excel）')
    )

    expect(documentStep).toContain('class="sales-order-default-grid"')
    expect(documentStep).toContain('label="单据类型编码"')
    expect(documentStep).toContain('label="下单日期"')
    expect(documentStep).toContain('v-model="salesOrderForm.salesOrderDate"')
    expect(documentStep).toContain('label="客户订单号"')
    expect(documentStep).toContain('v-model.trim="salesOrderForm.customerOrderNo"')
    expect(documentStep).toContain('label="客户项目号"')
    expect(documentStep).toContain('v-model.trim="salesOrderForm.customerProjectNo"')
    expect(documentStep).toContain('label="客户下单抬头"')
    expect(documentStep).toContain('v-model.trim="salesOrderForm.customerOrderTitle"')
    expect(documentStep).toContain('label="订单类型"')
    expect(documentStep).toContain('v-model="salesOrderForm.orderType"')
    expect(documentStep).toContain('label="内部项目号"')
    expect(documentStep).toContain('v-model.trim="salesOrderForm.internalProjectNo"')
    expect(documentStep).toContain('<el-option label="预测订单" value="预测订单" />')
    expect(documentStep).toContain('<el-option label="备货订单" value="备货订单" />')
    expect(documentStep).toContain('<el-option label="正式订单" value="正式订单" />')
  })

  test('sales order import validates required order defaults before submit', () => {
    const submitBlock = marketDashboard.slice(
      marketDashboard.indexOf('const submitSalesOrderImport'),
      marketDashboard.indexOf('// 提交询价单和图纸')
    )

    expect(submitBlock).toContain("salesOrderForm.documentTypeCode.trim()")
    expect(submitBlock).toContain("ElMessage.warning('请填写单据类型编码！')")
    expect(submitBlock).toContain("ElMessage.warning('请选择销售订单下单日期！')")
    expect(submitBlock).toContain("ElMessage.warning('请填写客户订单号！')")
    expect(submitBlock).toContain("ElMessage.warning('请选择订单类型！')")
    expect(submitBlock).toContain("ElMessage.warning('请填写内部项目号！')")
    expect(submitBlock.indexOf("请填写内部项目号")).toBeLessThan(submitBlock.indexOf("请上传销售订单 Excel"))
  })

  test('sales order import submits all order defaults in multipart form data', () => {
    const submitBlock = marketDashboard.slice(
      marketDashboard.indexOf('const submitSalesOrderImport'),
      marketDashboard.indexOf('// 提交询价单和图纸')
    )

    expect(submitBlock).toContain("formData.append('salesOrderDate', salesOrderForm.salesOrderDate)")
    expect(submitBlock).toContain("formData.append('customerOrderNo', customerOrderNo)")
    expect(submitBlock).toContain("formData.append('customerProjectNo', salesOrderForm.customerProjectNo.trim())")
    expect(submitBlock).toContain("formData.append('customerOrderTitle', salesOrderForm.customerOrderTitle.trim())")
    expect(submitBlock).toContain("formData.append('orderType', salesOrderForm.orderType)")
    expect(submitBlock).toContain("formData.append('internalProjectNo', internalProjectNo)")
  })

  test('sales order default matrix is responsive and does not overflow on small screens', () => {
    expect(marketDashboard).toContain('.sales-order-default-grid')
    expect(marketDashboard).toContain('grid-template-columns: repeat(3, minmax(0, 1fr));')
    expect(marketDashboard).toContain('.sales-order-default-grid :deep(.el-form-item__content)')
    expect(marketDashboard).toContain('min-width: 0;')
    expect(marketDashboard).toContain('@media (max-width: 900px)')
    expect(marketDashboard).toContain('.sales-order-default-grid {')
    expect(marketDashboard).toContain('grid-template-columns: 1fr;')
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
    expect(marketDashboard).toContain('salesOrderVersionDrawingFiles')
    expect(submitBlock).toContain('salesOrderDrawingFiles.value.forEach')
    expect(submitBlock).toContain("formData.append('drawings', file)")
    expect(submitBlock).toContain('salesOrderVersionDrawingFiles.value.forEach')
    expect(submitBlock).toContain("formData.append('versionDrawings', file)")
    expect(submitBlock).toContain('buildDrawingReportHtml(response.drawingReport)')
    expect(submitBlock).toContain('ElMessageBox.alert')
    expect(submitBlock).toContain('销售订单导入成功')
  })

  test('RFQ import submits old and part-version drawing entrances independently', () => {
    const submitBlock = marketDashboard.slice(
      marketDashboard.indexOf('const submitRfqPackage'),
      marketDashboard.indexOf('</script>')
    )

    expect(marketDashboard).toContain('const drawingFiles = ref([])')
    expect(marketDashboard).toContain('const versionDrawingFiles = ref([])')
    expect(submitBlock).toContain('drawingFiles.value.forEach')
    expect(submitBlock).toContain("formData.append('drawings', file)")
    expect(submitBlock).toContain('versionDrawingFiles.value.forEach')
    expect(submitBlock).toContain("formData.append('versionDrawings', file)")
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
    expect(marketDashboard).toContain('label="未补全快照"')
    expect(marketDashboard).not.toContain('label="待销售匹配"')
    expect(marketDashboard).toContain('prop="missingPriceCount"')
    expect(marketDashboard).toContain('label="缺价格"')
    expect(marketDashboard).toContain('missingPriceCount')
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

  test('sales order QPO import copy does not imply immediate U9 sync', () => {
    expect(salesOrderPane).not.toContain('导入后同步 U9')
    expect(salesOrderPane).not.toContain('直接同步 U9')
    expect(salesOrderPane).not.toContain('创建 RFQ')
    expect(salesOrderPane).not.toContain('QPO 主流程')
    expect(marketDashboard).toContain('importSalesOrderToQpo(formData)')
  })

  test('sales order upload instructions follow the new market template', () => {
    expect(salesOrderPane).toContain('销售订单 Excel 需包含表头')
    expect(salesOrderPane).toContain('图号、订单数量、版本')
    expect(salesOrderPane).toContain('可选表头：序号、下单日期、客户订单号、客户项目号、客户下单抬头、订单类型、内部项目号')
    expect(salesOrderPane).toContain('订单级字段以下方“订单默认信息”为准')
    expect(salesOrderPane).toContain('Excel 行内同名字段仅作为零件级覆盖值')
    expect(salesOrderPane).toContain('价格允许为空，但 U9 同步前必须补齐')
    expect(salesOrderPane).toContain('日期字段（下单日期、客户交期、内部交期）支持具体日期')
    for (const dateExample of ['2026-03-08', '2026-3-8', '2026.03.08', '2026.3.8', '2026/3/8', '2026年3月8日', '46089']) {
      expect(salesOrderPane).toContain(dateExample)
    }
    expect(salesOrderPane).toContain('订单数量必须为正整数')
    expect(salesOrderPane).toContain('（未税）金额为空且未税单价存在时按未税单价×订单数量计算')
    expect(salesOrderPane).not.toContain('同一客户订单号+内部项目号')
    expect(salesOrderPane).not.toContain('已出数量')
    expect(salesOrderPane).not.toContain('未出数量')
    expect(salesOrderPane).not.toContain('未出数量为空时按订单数量-已出数量计算')
    expect(salesOrderPane).not.toContain('实际出货日期')
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
