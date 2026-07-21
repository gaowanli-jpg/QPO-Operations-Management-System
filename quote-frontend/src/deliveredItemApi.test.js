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
})
