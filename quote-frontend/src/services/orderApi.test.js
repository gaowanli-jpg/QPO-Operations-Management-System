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

  test('listCustomers forwards customer master field filters', async () => {
    const params = { field: 'shortName', keyword: 'ACME' }

    await orderApi.listCustomers('market-order', params)

    expect(request).toHaveBeenCalledWith('/api/order-management/customers', { params })
  })

  test('listCustomers uses module-scoped customer selection endpoints', async () => {
    await orderApi.listCustomers('market-rfq')
    await orderApi.listCustomers('quote-process')
    await orderApi.listCustomers('release')
    await orderApi.listCustomers('delivery-document')
    await orderApi.listCustomers('engineer-setup')
    await orderApi.listCustomers('engineer-pricing')
    await orderApi.listCustomers('market-order')
    await orderApi.listCustomers('sales-order')
    await orderApi.listCustomers('delivered-item')
    await orderApi.listCustomers('locked-item')
    await orderApi.listCustomers('production-item')

    expect(request).toHaveBeenNthCalledWith(1, '/api/market/rfq/customers')
    expect(request).toHaveBeenNthCalledWith(2, '/api/market/quote-process/customers')
    expect(request).toHaveBeenNthCalledWith(3, '/api/market/release/customers')
    expect(request).toHaveBeenNthCalledWith(4, '/api/market/delivery-documents/customers')
    expect(request).toHaveBeenNthCalledWith(5, '/api/engineer/setup/customers')
    expect(request).toHaveBeenNthCalledWith(6, '/api/engineer/pricing/customers')
    expect(request).toHaveBeenNthCalledWith(7, '/api/order-management/customers')
    expect(request).toHaveBeenNthCalledWith(8, '/api/market/sales-order-management/customers')
    expect(request).toHaveBeenNthCalledWith(9, '/api/order-delivery/tickets/customers')
    expect(request).toHaveBeenNthCalledWith(10, '/api/order-delivery/locked-items/customers')
    expect(request).toHaveBeenNthCalledWith(11, '/api/order-delivery/production-items/customers')
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

  test('order detail tabs use independent paged endpoints', async () => {
    const params = { page: 2, pageSize: 50 }

    await orderApi.listOrderItems(12, params)
    await orderApi.listOrderTickets(12, params)
    await orderApi.listOrderProcessSheets(12, params)
    await orderApi.getOrderDrawingSummary(12)

    expect(request).toHaveBeenNthCalledWith(1, '/api/order-management/orders/12/items', { params })
    expect(request).toHaveBeenNthCalledWith(2, '/api/order-management/orders/12/tickets', { params })
    expect(request).toHaveBeenNthCalledWith(3, '/api/order-management/orders/12/process-sheets', { params })
    expect(request).toHaveBeenNthCalledWith(4, '/api/order-management/orders/12/drawing-summary')
  })

  test('does not expose physical delete helper after history orders are retired', () => {
    expect('deleteOrder' in orderApi).toBe(false)
  })

  test('exposes order status management actions with dedicated endpoints', async () => {
    await orderApi.rollbackQuotedOrder(12, { reason: '閲嶆柊璋冩暣鎶ヤ环浜ゆ湡' })
    await orderApi.rollbackPricedOrder(12, { reason: '宸ョ▼閲嶆柊鏍镐环' })
    await orderApi.discardOrder(12, { reason: '瀹㈡埛鍙栨秷' })
    await orderApi.restoreOrder(12, { reason: '瀹㈡埛鎭㈠璇环' })

    expect(request).toHaveBeenNthCalledWith(1, '/api/order-management/orders/12/rollback-quoted', {
      method: 'POST',
      data: { reason: '閲嶆柊璋冩暣鎶ヤ环浜ゆ湡' }
    })
    expect(request).toHaveBeenNthCalledWith(2, '/api/order-management/orders/12/rollback-priced', {
      method: 'POST',
      data: { reason: '宸ョ▼閲嶆柊鏍镐环' }
    })
    expect(request).toHaveBeenNthCalledWith(3, '/api/order-management/orders/12/discard', {
      method: 'POST',
      data: { reason: '瀹㈡埛鍙栨秷' }
    })
    expect(request).toHaveBeenNthCalledWith(4, '/api/order-management/orders/12/restore', {
      method: 'POST',
      data: { reason: '瀹㈡埛鎭㈠璇环' }
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

  test('exposes order item maintenance endpoints', async () => {
    const payload = {
      items: [{ itemId: 21, partNo: 'P-100', version: 'B', qty: 12, leadTime: '2026-10-01', quoteLeadTime: '2026-10-05' }]
    }
    const formData = new FormData()

    await orderApi.saveOrderItems(12, payload)
    await orderApi.replaceOrderDrawings(12, formData)
    await orderApi.previewOrderItemDataReplacement(12, formData)
    await orderApi.confirmOrderItemDataReplacement(12, formData)

    expect(request).toHaveBeenNthCalledWith(1, '/api/order-management/orders/12/items/batch', {
      method: 'POST',
      data: payload
    })
    expect(request).toHaveBeenNthCalledWith(2, '/api/order-management/orders/12/drawings/replace', {
      method: 'POST',
      data: formData,
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    expect(request).toHaveBeenNthCalledWith(3, '/api/order-management/orders/12/item-data-replacement/preview', {
      method: 'POST',
      data: formData,
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    expect(request).toHaveBeenNthCalledWith(4, '/api/order-management/orders/12/item-data-replacement/confirm', {
      method: 'POST',
      data: formData,
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  })
})
