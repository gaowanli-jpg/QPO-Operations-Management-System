import { beforeEach, describe, expect, test, vi } from 'vitest'
import { request } from '../api'
import * as orderApi from './orderApi'

vi.mock('../api', () => ({
  request: vi.fn()
}))

beforeEach(() => {
  request.mockReset()
})

describe('orderApi', () => {
  test('listCustomers loads order management customers', async () => {
    await orderApi.listCustomers()

    expect(request).toHaveBeenCalledWith('/api/order-management/customers')
  })

  test('listCustomers uses module-scoped customer selection endpoints', async () => {
    await orderApi.listCustomers('market-rfq')
    await orderApi.listCustomers('quote-process')
    await orderApi.listCustomers('release')
    await orderApi.listCustomers('engineer-setup')
    await orderApi.listCustomers('market-order')
    await orderApi.listCustomers('history-order')

    expect(request).toHaveBeenNthCalledWith(1, '/api/market/rfq/customers')
    expect(request).toHaveBeenNthCalledWith(2, '/api/market/quote-process/customers')
    expect(request).toHaveBeenNthCalledWith(3, '/api/market/release/customers')
    expect(request).toHaveBeenNthCalledWith(4, '/api/engineer/setup/customers')
    expect(request).toHaveBeenNthCalledWith(5, '/api/order-management/customers')
    expect(request).toHaveBeenNthCalledWith(6, '/api/order-management/history-orders/customers')
  })

  test('listOrders forwards query params', async () => {
    const params = { customerId: 3, keyword: 'RFQ' }

    await orderApi.listOrders(params)

    expect(request).toHaveBeenCalledWith('/api/order-management/orders', { params })
  })

  test('getOrder loads order detail by id', async () => {
    await orderApi.getOrder(12)

    expect(request).toHaveBeenCalledWith('/api/order-management/orders/12')
  })

  test('deleteOrder preserves delete files query option', async () => {
    await orderApi.deleteOrder(12, { deleteFiles: true })

    expect(request).toHaveBeenCalledWith('/api/order-management/orders/12', {
      method: 'DELETE',
      params: { deleteFiles: true }
    })
  })

  test('does not expose production reset helper for immutable released records', () => {
    expect('resetProduction' in orderApi).toBe(false)
  })

  test('supplementDrawings uploads multipart form data', async () => {
    const formData = new FormData()

    await orderApi.supplementDrawings(12, formData)

    expect(request).toHaveBeenCalledWith('/api/order-management/orders/12/supplement-drawings', {
      method: 'POST',
      data: formData,
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  })
})
