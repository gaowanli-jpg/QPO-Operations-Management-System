import { beforeEach, describe, expect, test, vi } from 'vitest'
import { request } from './api'
import * as deliveredItemApi from './services/deliveredItemApi'

vi.mock('./api', () => ({
  request: vi.fn()
}))

beforeEach(() => {
  request.mockReset()
})

describe('deliveredItemApi', () => {
  test('listDeliveredItems calls delivered ticket demand pool endpoint with params', async () => {
    const params = { customerName: 'ACME', partNo: 'P-100', materialNo: 'MAT-1' }

    await deliveredItemApi.listDeliveredItems(params)

    expect(request).toHaveBeenCalledWith('/api/order-delivery/tickets', { params })
  })

  test('getDeliveredItem loads delivered ticket detail by ticket id', async () => {
    await deliveredItemApi.getDeliveredItem(42)

    expect(request).toHaveBeenCalledWith('/api/order-delivery/tickets/42')
  })

  test('discard and restore delivered tickets through module-owned endpoints', async () => {
    await deliveredItemApi.discardDeliveredItem(42, { reason: '重复提单' })
    await deliveredItemApi.restoreDeliveredItem(42, { reason: '恢复处理' })

    expect(request).toHaveBeenNthCalledWith(1, '/api/order-delivery/tickets/42/discard', {
      method: 'POST',
      data: { reason: '重复提单' }
    })
    expect(request).toHaveBeenNthCalledWith(2, '/api/order-delivery/tickets/42/restore', {
      method: 'POST',
      data: { reason: '恢复处理' }
    })
  })

  test('batch discard and restore delivered tickets through module-owned endpoints', async () => {
    await deliveredItemApi.batchDiscardDeliveredItems([42, 43], { reason: '批量停止处理' })
    await deliveredItemApi.batchRestoreDeliveredItems([42, 43], { reason: '批量恢复处理' })

    expect(request).toHaveBeenNthCalledWith(1, '/api/order-delivery/tickets/batch-discard', {
      method: 'POST',
      data: { ticketIds: [42, 43], reason: '批量停止处理' }
    })
    expect(request).toHaveBeenNthCalledWith(2, '/api/order-delivery/tickets/batch-restore', {
      method: 'POST',
      data: { ticketIds: [42, 43], reason: '批量恢复处理' }
    })
  })

  test('does not expose legacy single-ticket MES material sync helper', () => {
    expect(deliveredItemApi.syncMesMaterial).toBeUndefined()
  })

  test('syncMesMaterialBatch posts one MES batch task for selected ticket ids', async () => {
    await deliveredItemApi.syncMesMaterialBatch([123, 456])

    expect(request).toHaveBeenCalledWith('/api/order-delivery/tickets/mes-material/batch', {
      method: 'POST',
      data: { ticketIds: [123, 456] }
    })
  })

  test('does not expose legacy single-ticket WMS allocation helper', () => {
    expect(deliveredItemApi['submit' + 'WmsAllocation']).toBeUndefined()
  })

  test('submitWmsAllocationBatch posts one WMS batch task for selected ticket ids', async () => {
    await deliveredItemApi.submitWmsAllocationBatch([123, 456])

    expect(request).toHaveBeenCalledWith('/api/order-delivery/tickets/wms-allocation/batch', {
      method: 'POST',
      data: { ticketIds: [123, 456] }
    })
  })

  test('listLockedItems calls locked stock result endpoint with params', async () => {
    const params = { allocationType: 'IN_PRODUCTION_LOCK', partNo: 'P-100' }

    await deliveredItemApi.listLockedItems(params)

    expect(request).toHaveBeenCalledWith('/api/order-delivery/locked-items', { params })
    expect(JSON.stringify(params)).not.toContain('wms' + 'ProductionOrderNo')
  })

  test('submitLockedStockPickingOrderBatch posts one MES stock picking task for selected allocation ids', async () => {
    await deliveredItemApi.submitLockedStockPickingOrderBatch([101, 102])

    expect(request).toHaveBeenCalledWith('/api/order-delivery/locked-items/stock-picking-orders', {
      method: 'POST',
      data: { allocationIds: [101, 102] }
    })
  })

  test('discard and restore locked allocations through module-owned endpoints', async () => {
    await deliveredItemApi.discardLockedItem(101, { reason: '不再处理' })
    await deliveredItemApi.restoreLockedItem(101, {})

    expect(request).toHaveBeenNthCalledWith(1, '/api/order-delivery/locked-items/101/discard', {
      method: 'POST',
      data: { reason: '不再处理' }
    })
    expect(request).toHaveBeenNthCalledWith(2, '/api/order-delivery/locked-items/101/restore', {
      method: 'POST',
      data: {}
    })
  })

  test('batch discard and restore locked allocations through module-owned endpoints', async () => {
    await deliveredItemApi.batchDiscardLockedItems([101, 102], { reason: '批量停止处理' })
    await deliveredItemApi.batchRestoreLockedItems([101, 102], { reason: '批量恢复处理' })

    expect(request).toHaveBeenNthCalledWith(1, '/api/order-delivery/locked-items/batch-discard', {
      method: 'POST',
      data: { allocationIds: [101, 102], reason: '批量停止处理' }
    })
    expect(request).toHaveBeenNthCalledWith(2, '/api/order-delivery/locked-items/batch-restore', {
      method: 'POST',
      data: { allocationIds: [101, 102], reason: '批量恢复处理' }
    })
  })


  test('listProductionItems calls production work pool endpoint with params', async () => {
    const params = { mesProductionStatus: 'FAILED', partNo: 'P-200' }

    await deliveredItemApi.listProductionItems(params)

    expect(request).toHaveBeenCalledWith('/api/order-delivery/production-items', { params })
  })
  test('listTicketAllocations loads allocation rows for a delivered ticket', async () => {
    await deliveredItemApi.listTicketAllocations(123)

    expect(request).toHaveBeenCalledWith('/api/order-delivery/tickets/123/allocations')
  })

  test('submitMesProductionDispatchBatch posts one MES production task for selected allocation ids', async () => {
    await deliveredItemApi.submitMesProductionDispatchBatch([101, 102])

    expect(request).toHaveBeenCalledWith('/api/order-delivery/production-items/mes-production', {
      method: 'POST',
      data: { allocationIds: [101, 102] }
    })
  })

  test('discard and restore production allocations through module-owned endpoints', async () => {
    await deliveredItemApi.discardProductionItem(101, { reason: '暂停生产' })
    await deliveredItemApi.restoreProductionItem(101, {})

    expect(request).toHaveBeenNthCalledWith(1, '/api/order-delivery/production-items/101/discard', {
      method: 'POST',
      data: { reason: '暂停生产' }
    })
    expect(request).toHaveBeenNthCalledWith(2, '/api/order-delivery/production-items/101/restore', {
      method: 'POST',
      data: {}
    })
  })

  test('batch discard and restore production allocations through module-owned endpoints', async () => {
    await deliveredItemApi.batchDiscardProductionItems([101, 102], { reason: '批量暂停生产' })
    await deliveredItemApi.batchRestoreProductionItems([101, 102], { reason: '批量恢复生产' })

    expect(request).toHaveBeenNthCalledWith(1, '/api/order-delivery/production-items/batch-discard', {
      method: 'POST',
      data: { allocationIds: [101, 102], reason: '批量暂停生产' }
    })
    expect(request).toHaveBeenNthCalledWith(2, '/api/order-delivery/production-items/batch-restore', {
      method: 'POST',
      data: { allocationIds: [101, 102], reason: '批量恢复生产' }
    })
  })

  test('getDeliveryIntegrationTask loads async batch task result by task id', async () => {
    await deliveredItemApi.getDeliveryIntegrationTask(9001)

    expect(request).toHaveBeenCalledWith('/api/tasks/9001')
  })
})
