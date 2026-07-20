import { request } from '../api'

const CUSTOMER_ENDPOINTS = {
  'market-rfq': '/api/market/rfq/customers',
  'quote-process': '/api/market/quote-process/customers',
  release: '/api/market/release/customers',
  'engineer-setup': '/api/engineer/setup/customers',
  'market-order': '/api/order-management/customers',
  'history-order': '/api/order-management/history-orders/customers'
}

export const listCustomers = (scope = 'market-order') => request(
  CUSTOMER_ENDPOINTS[scope] || CUSTOMER_ENDPOINTS['market-order']
)

export const listOrders = (params = {}) => request('/api/order-management/orders', { params })

export const getOrder = (orderId) => request(`/api/order-management/orders/${orderId}`)

export const deleteOrder = (orderId, { deleteFiles = false } = {}) => request(`/api/order-management/orders/${orderId}`, {
  method: 'DELETE',
  params: { deleteFiles }
})

export const supplementDrawings = (rfqId, formData) => request(`/api/order-management/orders/${rfqId}/supplement-drawings`, {
  method: 'POST',
  data: formData,
  headers: { 'Content-Type': 'multipart/form-data' }
})
