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
    expect(pageSource).not.toContain('wmsAllocationAction')
    expect(pageSource).not.toContain('@click="handleWmsAllocation(row)"')
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

  test('posts batch WMS allocation, announces task submission, and polls task result', () => {
    expect(apiSource).not.toContain('submit' + 'WmsAllocation =')
    expect(apiSource).not.toContain('/api/order-delivery/tickets/${ticketId}/' + 'wms-allocation')
    expect(pageSource).toContain('ElMessageBox.confirm')
    expect(pageSource).toContain('submitWmsAllocationBatch(selectedTicketIds.value)')
    expect(pageSource).not.toContain('submit' + 'WmsAllocation(row.id)')
    expect(pageSource).toContain('WMS 库存任务已提交')
    expect(pageSource).toContain('waitWmsBatchTaskResult(taskId)')
    expect(pageSource).toContain('getDeliveryIntegrationTask(taskId)')
    expect(pageSource).not.toContain('锁库完成')
    expect(pageSource).toContain('await loadItems()')
  })

  test('renders WMS batch result statuses without reusing MES result state', () => {
    expect(pageSource).toContain('wmsBatchResult')
    expect(pageSource).toContain('wmsBatchStats')
    expect(pageSource).toContain('wmsBatchRows')
    expect(pageSource).toContain('closeWmsBatchResult')
    expect(pageSource).toContain('WMS 批量库存结果')
    expect(pageSource).toContain('结果待核对')
    expect(pageSource).toContain('本地写入待修复')
    expect(pageSource).toContain('跳过')
    expect(pageSource).toContain('应用失败')
    expect(pageSource).toContain('wmsBatchResult.value = null')
    expect(pageSource).toContain('mesBatchResult.value = null')
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
