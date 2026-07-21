import { beforeEach, describe, expect, test, vi } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { request } from './api'
import * as deliveredItemApi from './services/deliveredItemApi'

const currentDir = dirname(fileURLToPath(import.meta.url))
const wmsPlanSource = readFileSync(resolve(currentDir, '..', '..', 'docs', 'QPO-MES-WMS对接', '04-WMS锁库分配与锁库零件模块开发文档.md'), 'utf8')

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

  test('syncMesMaterial posts MES material sync task by ticket id', async () => {
    await deliveredItemApi.syncMesMaterial(123)

    expect(request).toHaveBeenCalledWith('/api/order-delivery/tickets/123/mes-material', {
      method: 'POST'
    })
  })

  test('freezes planned WMS allocation and locked item API endpoints', () => {
    expect(wmsPlanSource).toContain('submitWmsAllocation')
    expect(wmsPlanSource).toContain('/api/order-delivery/tickets/${ticketId}/wms-allocation')
    expect(wmsPlanSource).toContain('listLockedItems')
    expect(wmsPlanSource).toContain('/api/order-delivery/allocations')
    expect(wmsPlanSource).toContain('listTicketAllocations')
    expect(wmsPlanSource).toContain('/api/order-delivery/tickets/${ticketId}/allocations')
  })
})
