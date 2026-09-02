import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = () => readFileSync(resolve(currentDir, 'components', 'OrderManage.vue'), 'utf8')

describe('order item maintenance detail table', () => {
  test('adds editable maintenance actions with dedicated order update permission', () => {
    const component = source()

    expect(component).toContain("const ORDER_ITEM_UPDATE_PERMISSION = 'market:order:update-item'")
    expect(component).toContain('表格数据替换')
    expect(component).toContain('保存修改')
    expect(component).toContain('导入替换图纸')
    expect(component).toContain('v-permission="ORDER_ITEM_UPDATE_PERMISSION"')
    expect(component).toContain('saveOrderItemChanges')
    expect(component).toContain('OrderItemDataReplacementDialog')
  })

  test('order detail items mirror release table while excluding release-only columns', () => {
    const component = source()
    const itemPane = component.slice(
      component.indexOf('<el-tab-pane label="零件明细" name="items">'),
      component.indexOf('<el-tab-pane label="提单行" name="tickets">')
    )

    expect(itemPane).toContain('label="图号"')
    expect(itemPane).toContain('label="版本"')
    expect(itemPane).toContain('label="询价数量"')
    expect(itemPane).toContain('label="客户询价交期"')
    expect(itemPane).toContain('label="报价承诺交期"')
    expect(itemPane).toContain('label="内部目标交期"')
    expect(itemPane).toContain('label="状态"')

    expect(itemPane).toContain('updateItemDraft(row, \'partNo\'')
    expect(itemPane).toContain('updateItemDraft(row, \'qty\'')
    expect(itemPane).toContain('updateItemDraft(row, \'leadTime\'')
    expect(itemPane).toContain('updateItemDraft(row, \'quoteLeadTime\'')

    expect(itemPane).not.toContain('来源单号')
    expect(itemPane).not.toContain('通过数量')
    expect(itemPane).not.toContain('可选状态')
    expect(itemPane).not.toContain('提单交期')
  })
})
