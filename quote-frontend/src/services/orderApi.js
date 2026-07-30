import { request } from '../api'

const CUSTOMER_ENDPOINTS = {
  'market-rfq': '/api/market/rfq/customers',
  'quote-process': '/api/market/quote-process/customers',
  release: '/api/market/release/customers',
  'delivery-document': '/api/market/delivery-documents/customers',
  'engineer-setup': '/api/engineer/setup/customers',
  'market-order': '/api/order-management/customers',
  'history-order': '/api/order-management/history-orders/customers',
  'delivered-item': '/api/order-delivery/tickets/customers',
  'locked-item': '/api/order-delivery/allocations/customers'
}

export const listCustomers = (scope = 'market-order', params = undefined) => {
  const endpoint = CUSTOMER_ENDPOINTS[scope] || CUSTOMER_ENDPOINTS['market-order']
  const hasParams = params && Object.keys(params).some(key => params[key] !== undefined && params[key] !== '')
  return hasParams ? request(endpoint, { params }) : request(endpoint)
}

export const listOrders = (params = {}) => request('/api/order-management/orders', { params })

export const getOrder = (orderId) => request(`/api/order-management/orders/${orderId}`)

export const listOrderItems = (rfqId, params = {}) => request(`/api/order-management/orders/${rfqId}/items`, { params })

export const listOrderTickets = (rfqId, params = {}) => request(`/api/order-management/orders/${rfqId}/tickets`, { params })

export const listOrderProcessSheets = (rfqId, params = {}) => request(`/api/order-management/orders/${rfqId}/process-sheets`, { params })

export const getOrderDrawingSummary = (rfqId) => request(`/api/order-management/orders/${rfqId}/drawing-summary`)

export const deleteOrder = (orderId, { deleteFiles = false } = {}) => request(`/api/order-management/orders/${orderId}`, {
  method: 'DELETE',
  params: { deleteFiles }
})

export const rollbackQuotedOrder = (rfqId, payload = {}) => request(`/api/order-management/orders/${rfqId}/rollback-quoted`, {
  method: 'POST',
  data: payload
})

export const rollbackPricedOrder = (rfqId, payload = {}) => request(`/api/order-management/orders/${rfqId}/rollback-priced`, {
  method: 'POST',
  data: payload
})

export const discardOrder = (rfqId, payload = {}) => request(`/api/order-management/orders/${rfqId}/discard`, {
  method: 'POST',
  data: payload
})

export const restoreOrder = (rfqId, payload = {}) => request(`/api/order-management/orders/${rfqId}/restore`, {
  method: 'POST',
  data: payload
})

export const supplementDrawings = (rfqId, formData) => request(`/api/order-management/orders/${rfqId}/supplement-drawings`, {
  method: 'POST',
  data: formData,
  headers: { 'Content-Type': 'multipart/form-data' }
})
