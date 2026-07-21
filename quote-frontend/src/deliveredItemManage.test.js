import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(resolve(currentDir, 'components', 'DeliveredItemManage.vue'), 'utf8')

describe('DeliveredItemManage foundation page', () => {
  test('freezes delivered item page title, filters, and core columns', () => {
    expect(source).toContain('已提零件')
    for (const label of ['客户', 'RFQ号', '图号', '版本', '物料号', '内部项目号', 'MES状态', 'WMS状态', '提单日期']) {
      expect(source).toContain(label)
    }
    for (const label of ['提单ID', '图号', '物料号', '提单数量', 'MES状态', 'WMS状态']) {
      expect(source).toContain(label)
    }
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

  test('wires drawing preview to delivered item module permission', () => {
    expect(source).toContain('market:delivered-item:file-preview')
    expect(source).toContain('预览图纸')
    expect(source).toContain('openDrawingPreview')
    expect(source).toContain('当前零件没有绑定图纸。')
    expect(source).toContain('图纸打开失败。')
  })
})
