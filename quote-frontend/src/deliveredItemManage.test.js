import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(resolve(currentDir, 'components', 'DeliveredItemManage.vue'), 'utf8')

describe('DeliveredItemManage foundation page', () => {
  test('freezes delivered item page title, filters, and core columns', () => {
    expect(source).toContain('已提零件')
    for (const label of ['客户', 'RFQ号', '图号', '版本', '物料号', '内部项目号', 'MES状态', 'WMS状态', '交货日期']) {
      expect(source).toContain(label)
    }
    for (const label of ['提单ID', '图号', '物料号', '提单数量', 'MES状态', 'WMS状态', '客户订单号', '客户项目号', '材料类型', '毛重', '净重', '工艺路线', '材料描述', '表面处理']) {
      expect(source).toContain(label)
    }
  })

  test('keeps delivered item table columns in the MES WMS demand-pool order', () => {
    const tableSource = source.slice(source.indexOf('<el-table :data="items"'))
    const labels = [
      'label="提单ID"',
      'label="图号"',
      'label="版本"',
      'label="物料号"',
      'label="MES状态"',
      'label="WMS状态"',
      'label="订单类型"',
      'label="提单数量"',
      'label="客户"',
      'label="RFQ号"',
      'label="内部项目号"',
      'label="客户订单号"',
      'label="客户项目号"',
      'label="材料"',
      'label="材料类型"',
      'label="毛重"',
      'label="净重"',
      'label="工艺路线"',
      'label="交货日期"',
      'label="材料描述"',
      'label="表面处理"',
      'label="备注"',
      'label="操作"'
    ]
    const positions = labels.map(label => tableSource.indexOf(label))
    expect(positions.every(position => position >= 0)).toBe(true)
    expect(positions).toEqual([...positions].sort((a, b) => a - b))
  })

  test('keeps delivered ticket id tooltip enabled for long ids', () => {
    expect(source).toContain('prop="id" label="提单ID" width="96" fixed align="center" show-overflow-tooltip')
  })

  test('wires WMS allocation action and locked-result entry after the WMS allocation phase', () => {
    expect(source).toContain('DELIVERED_ITEM_WMS_ALLOCATE_PERMISSION')
    expect(source).toContain('market:delivered-item:wms-allocate')
    expect(source).toContain('submitWmsAllocation')
    expect(source).toContain('wmsAllocationAction(row)')
    expect(source).toContain('提交WMS锁库')
    expect(source).toContain('重试WMS')
    expect(source).toContain('查看锁库')
  })

  test('renders missing material numbers and malformed process routes safely', () => {
    expect(source).toContain('未获取')
    expect(source).toContain('formatMaterialNo')
    expect(source).toContain('formatMaterialName')
    expect(source).toContain('formatMaterialShape')
    expect(source).toContain('formatWeight')
    expect(source).toContain('formatProcessRoute')
    expect(source).toContain('JSON.parse')
    expect(source).not.toContain('工艺解析失败')
  })

  test('maps delivery date range filters to backend query params', () => {
    expect(source).toContain('dateRange')
    expect(source).toContain('dateFrom')
    expect(source).toContain('dateTo')
    expect(source).toContain('value-format="YYYY-MM-DD"')
  })

  test('uses module customer picker and freezes common delivered-item filters', () => {
    const toolbarStart = source.indexOf('delivered-filter-group')
    const toolbarSource = source.slice(toolbarStart, source.indexOf('</section>', toolbarStart))

    expect(source).toContain("import CustomerPickerDialog from './CustomerPickerDialog.vue'")
    expect(source).toContain('<CustomerPickerDialog')
    expect(source).toContain('customer-scope="delivered-item"')
    expect(source).toContain('v-permission="\'market:delivered-item:customer-select\'"')
    expect(source).toContain('@select="handleCustomerSelected"')
    expect(toolbarSource).not.toContain('v-model="filters.customerName"')

    for (const token of [
      'filters.rfqNo',
      'filters.partNo',
      'filters.version',
      'filters.internalProjectNo',
      'filters.customerOrderNo',
      'filters.customerProjectNo',
      'filters.mesSyncStatus',
      'filters.wmsAllocateStatus',
      'filters.dateRange'
    ]) {
      expect(toolbarSource).toContain(token)
    }

    for (const removedCommonFilter of [
      'filters.materialNo'
    ]) {
      expect(toolbarSource).not.toContain(removedCommonFilter)
    }
  })

  test('wires drawing preview to delivered item module permission', () => {
    expect(source).toContain('market:delivered-item:file-preview')
    expect(source).toContain('预览图纸')
    expect(source).toContain('openDrawingPreview')
    expect(source).toContain('当前零件没有绑定图纸。')
    expect(source).toContain('图纸打开失败。')
  })
})
