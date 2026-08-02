import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const pageSource = readFileSync(resolve(currentDir, 'components', 'DeliveredItemManage.vue'), 'utf8')
const apiSource = readFileSync(resolve(currentDir, 'services', 'deliveredItemApi.js'), 'utf8')

describe('DeliveredItemManage WMS allocation guardrails', () => {
  test('wires module-owned WMS allocation permission to the batch toolbar action', () => {
    expect(pageSource).toContain('DELIVERED_ITEM_WMS_ALLOCATE_PERMISSION')
    expect(pageSource).toContain('market:delivered-item:wms-allocate')
    expect(pageSource).toContain('v-permission="DELIVERED_ITEM_WMS_ALLOCATE_PERMISSION"')
    expect(pageSource).toContain('submitWmsAllocationBatch')
    expect(pageSource).toContain('handleBatchWmsAllocation')
    expect(pageSource).toContain('批量获取库存数据')
    expect(pageSource).not.toContain('wmsAllocationAction')
    expect(pageSource).not.toContain('@click="handleWmsAllocation(row)"')
    expect(pageSource).toContain('查看锁库')
  })

  test('keeps WMS status display but removes row-level WMS mutation rules', () => {
    expect(pageSource).toContain('wmsAllocateStatus')
    expect(pageSource).toContain('statusText(row.wmsAllocateStatus)')
    expect(pageSource).not.toContain('wmsAllocationAction')
    expect(pageSource).not.toContain('!row.materialNo')
    expect(pageSource).not.toContain("row.wmsAllocateStatus === 'PENDING'")
    expect(pageSource).not.toContain("row.wmsAllocateStatus === 'FAILED'")
    expect(pageSource).not.toContain("row.wmsAllocateStatus === 'PROCESSING'")
    expect(pageSource).toContain("row.wmsAllocateStatus === 'SUCCESS'")
  })

  test('posts batch WMS allocation then refreshes list with Element Plus feedback', () => {
    expect(apiSource).toContain('submitWmsAllocation')
    expect(apiSource).toContain('/api/order-delivery/tickets/${ticketId}/wms-allocation')
    expect(pageSource).toContain('ElMessageBox.confirm')
    expect(pageSource).toContain('确认批量向 WMS 获取')
    expect(pageSource).toContain('submitWmsAllocationBatch(selectedTicketIds.value)')
    expect(pageSource).not.toContain('submitWmsAllocation(row.id)')
    expect(pageSource).toContain('锁库完成，结果已进入锁库零件模块')
    expect(pageSource).not.toContain('WMS 锁库任务已提交')
    expect(pageSource).not.toContain('批量 WMS 库存任务已提交')
    expect(pageSource).toContain('任务ID')
    expect(pageSource).toContain('await loadItems()')
    expect(pageSource).toContain('批量 WMS 库存任务提交失败。')
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
