import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const currentDir = dirname(fileURLToPath(import.meta.url))
const component = readFileSync(resolve(currentDir, 'components', 'PartSearchManage.vue'), 'utf8')
const router = readFileSync(resolve(currentDir, 'router', 'index.js'), 'utf8')
const appVue = readFileSync(resolve(currentDir, 'App.vue'), 'utf8')

describe('PartSearchManage', () => {
  test('registers an independent read-only part search menu route', () => {
    expect(router).toContain("const PartSearchManage = () => import('../components/PartSearchManage.vue')")
    expect(router).toContain("{ path: '/part-search', component: PartSearchManage, meta: { permission: 'market:part-search:view' } }")
    expect(appVue).toContain("'/part-search': { title: '零件查询', permission: 'market:part-search:view'")
    expect(appVue).toContain("paths: ['/orders', '/sales-orders', '/part-search', '/delivered-items', '/production-items', '/locked-items']")
  })

  test('shows rfq and sales part tabs with exact part number search and optional version', () => {
    expect(component).toContain('<h1 class="erp-page-title">零件查询</h1>')
    expect(component).toContain('<el-tab-pane label="询价零件" name="rfq"')
    expect(component).toContain('<el-tab-pane label="销售零件" name="sales"')
    expect(component).toContain('v-model="filters.partNo"')
    expect(component).toContain('placeholder="图号（必填，精确查询）"')
    expect(component).toContain('v-model="filters.version"')
    expect(component).toContain('placeholder="版本（可选，精确查询）"')
  })

  test('renders the required base columns and read-only detail drawer', () => {
    for (const column of [
      'prop="partNo" label="图号"',
      'prop="version" label="版本"',
      'label="未税单价"',
      'label="含税单价"',
      'prop="orderNo" label="所属订单号"',
      'prop="itemStatus" label="状态"'
    ]) {
      expect(component).toContain(column)
    }
    expect(component).toContain('@click="openDetail(scope.row)"')
    expect(component).toContain('<el-drawer v-model="detailVisible" title="零件详情"')
    expect(component).not.toContain('@click="save')
    expect(component).not.toContain('v-permission="\'market:order:update-item\'"')
  })

  test('places part preview after tax included price in both query tables', () => {
    const rfqPane = component.slice(component.indexOf('<el-tab-pane label="询价零件"'), component.indexOf('<el-tab-pane label="销售零件"'))
    const salesPane = component.slice(component.indexOf('<el-tab-pane label="销售零件"'), component.indexOf('</el-tabs>'))

    for (const pane of [rfqPane, salesPane]) {
      const taxColumnIndex = pane.indexOf('label="含税单价"')
      const previewColumnIndex = pane.indexOf('label="零件预览"')
      const orderColumnIndex = pane.indexOf('prop="orderNo" label="所属订单号"')

      expect(taxColumnIndex).toBeGreaterThan(-1)
      expect(previewColumnIndex).toBeGreaterThan(taxColumnIndex)
      expect(orderColumnIndex).toBeGreaterThan(previewColumnIndex)
      expect(pane).toContain('@click.stop="openPdfPreview(scope.row.drawingUrl, scope.row.partNo)"')
      expect(pane).toContain('<el-tag v-else type="info" size="small">无图</el-tag>')
    }
  })

  test('translates backend detail keys instead of showing raw english field names', () => {
    for (const label of [
      "salesOrderDate: '下单日期'",
      "customerDeliveryDate: '客户交期'",
      "internalDeliveryDate: '内部交期'",
      "quoteLeadTime: '报价承诺交期'",
      "customerOrderTitle: '客户下单抬头'",
      "engineeringMaterialCode: '核价材料编码'",
      "engineeringMaterialTypeCode: '核价材料类型编码'",
      "repeatMatchEligible: '可作为重复件匹配来源'",
      "materialData: '材料核价明细'",
      "processRoute: '工艺路线'",
      "feeData: '费用明细'"
    ]) {
      expect(component).toContain(label)
    }

    expect(component).toContain('label: formatDetailLabel(key, labels)')
    expect(component).toContain('formatStructuredValue(value)')
    expect(component).not.toContain('label: labels[key] || key')
  })
})
