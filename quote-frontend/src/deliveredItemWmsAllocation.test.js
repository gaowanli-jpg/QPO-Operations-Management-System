import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const pageSource = readFileSync(resolve(currentDir, 'components', 'DeliveredItemManage.vue'), 'utf8')
const apiSource = readFileSync(resolve(currentDir, 'services', 'deliveredItemApi.js'), 'utf8')

describe('DeliveredItemManage WMS allocation guardrails', () => {
  test('wires module-owned WMS allocation permission and action labels in delivered item page', () => {
    expect(pageSource).toContain('DELIVERED_ITEM_WMS_ALLOCATE_PERMISSION')
    expect(pageSource).toContain('market:delivered-item:wms-allocate')
    expect(pageSource).toContain('v-permission="DELIVERED_ITEM_WMS_ALLOCATE_PERMISSION"')
    expect(pageSource).toContain('submitWmsAllocation')
    expect(pageSource).toContain('提交WMS锁库')
    expect(pageSource).toContain('重试WMS')
    expect(pageSource).toContain('处理中')
    expect(pageSource).toContain('查看锁库')
  })

  test('implements WMS button display rules by material number and allocation status', () => {
    expect(pageSource).toContain('wmsAllocateStatus')
    expect(pageSource).toContain('statusText(row.wmsAllocateStatus)')
    expect(pageSource).toContain('wmsAllocationAction')
    expect(pageSource).toContain('!row.materialNo')
    expect(pageSource).toContain("row.wmsAllocateStatus === 'PENDING'")
    expect(pageSource).toContain("row.wmsAllocateStatus === 'FAILED'")
    expect(pageSource).toContain("row.wmsAllocateStatus === 'PROCESSING'")
    expect(pageSource).toContain("row.wmsAllocateStatus === 'SUCCESS'")
  })

  test('posts WMS allocation then refreshes list with Element Plus feedback', () => {
    expect(apiSource).toContain('submitWmsAllocation')
    expect(apiSource).toContain('/api/order-delivery/tickets/${ticketId}/wms-allocation')
    expect(pageSource).toContain('ElMessageBox.confirm')
    expect(pageSource).toContain('确认向 WMS 提交该零件锁库分配')
    expect(pageSource).toContain('锁库完成，结果已进入锁库零件模块')
    expect(pageSource).not.toContain('WMS 锁库任务已提交')
    expect(pageSource).not.toContain('批量 WMS 库存任务已提交')
    expect(pageSource).toContain('任务ID')
    expect(pageSource).toContain('await loadItems()')
    expect(pageSource).toContain('WMS 锁库任务提交失败。')
  })

  test('links successful WMS allocations to locked item results with permission guard', () => {
    expect(pageSource).toContain('LOCKED_ITEM_VIEW_PERMISSION')
    expect(pageSource).toContain('market:locked-item:view')
    expect(pageSource).toContain('openLockedItems')
    expect(pageSource).toContain('router.push')
    expect(pageSource).toContain("path: '/locked-items'")
    expect(pageSource).toContain('ticketId')
  })
})
