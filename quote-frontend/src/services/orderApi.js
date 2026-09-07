import { downloadFile, request } from '../api'

const CUSTOMER_ENDPOINTS = {
  'market-rfq': '/api/market/rfq/customers',
  'quote-process': '/api/market/quote-process/customers',
  release: '/api/market/release/customers',
  'release-append': '/api/market/release-append/customers',
  'delivery-document': '/api/market/delivery-documents/customers',
  'engineer-setup': '/api/engineer/setup/customers',
  'engineer-pricing': '/api/engineer/pricing/customers',
  'market-order': '/api/order-management/customers',
  'sales-order': '/api/market/sales-order-management/customers',
  'sales-order-change': '/api/change/sales-orders/customers',
  'delivered-item': '/api/order-delivery/tickets/customers',
  'locked-item': '/api/order-delivery/locked-items/customers',
  'production-item': '/api/order-delivery/production-items/customers'
}

export const listCustomers = (scope = 'market-order', params = undefined) => {
  const endpoint = CUSTOMER_ENDPOINTS[scope] || CUSTOMER_ENDPOINTS['market-order']
  const hasParams = params && Object.keys(params).some(key => params[key] !== undefined && params[key] !== '')
  return hasParams ? request(endpoint, { params }) : request(endpoint)
}

export const listOrders = (params = {}) => request('/api/order-management/orders', { params })

export const getOrder = (orderId) => request(`/api/order-management/orders/${orderId}`)

export const listOrderItems = (rfqId, params = {}) => request(`/api/order-management/orders/${rfqId}/items`, { params })

export const saveOrderItems = (rfqId, payload = {}) => request(`/api/order-management/orders/${rfqId}/items/batch`, {
  method: 'POST',
  data: payload
})

export const discardOrderItem = (rfqId, itemId) => request(`/api/order-management/orders/${rfqId}/items/${itemId}/discard`, {
  method: 'POST'
})

export const restoreOrderItem = (rfqId, itemId) => request(`/api/order-management/orders/${rfqId}/items/${itemId}/restore`, {
  method: 'POST'
})

export const appendOrderItems = (rfqId, formData) => request(`/api/order-management/orders/${rfqId}/items/import`, {
  method: 'POST',
  data: formData,
  headers: { 'Content-Type': 'multipart/form-data' }
})

export const replaceOrderDrawings = (rfqId, formData) => request(`/api/order-management/orders/${rfqId}/drawings/replace`, {
  method: 'POST',
  data: formData,
  headers: { 'Content-Type': 'multipart/form-data' }
})

export const previewOrderItemDataReplacement = (rfqId, formData) => request(`/api/order-management/orders/${rfqId}/item-data-replacement/preview`, {
  method: 'POST',
  data: formData,
  headers: { 'Content-Type': 'multipart/form-data' }
})

export const confirmOrderItemDataReplacement = (rfqId, formData) => request(`/api/order-management/orders/${rfqId}/item-data-replacement/confirm`, {
  method: 'POST',
  data: formData,
  headers: { 'Content-Type': 'multipart/form-data' }
})

export const listOrderTickets = (rfqId, params = {}) => request(`/api/order-management/orders/${rfqId}/tickets`, { params })

export const listOrderProcessSheets = (rfqId, params = {}) => request(`/api/order-management/orders/${rfqId}/process-sheets`, { params })

export const getOrderDrawingSummary = (rfqId) => request(`/api/order-management/orders/${rfqId}/drawing-summary`)

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

export const listSalesOrders = (params = {}) => request('/api/market/sales-order-management/orders', { params })

export const getSalesOrder = (salesOrderId) => request(`/api/market/sales-order-management/orders/${salesOrderId}`)

export const listSalesOrderItems = (salesOrderId, params = {}) => request(`/api/market/sales-order-management/orders/${salesOrderId}/items`, { params })

export const listSalesOrderTickets = (salesOrderId, params = {}) => request(`/api/market/sales-order-management/orders/${salesOrderId}/tickets`, { params })

export const listSalesOrderProcessSheets = (salesOrderId, params = {}) => request(`/api/market/sales-order-management/orders/${salesOrderId}/process-sheets`, { params })

export const getSalesOrderDrawingSummary = (salesOrderId) => request(`/api/market/sales-order-management/orders/${salesOrderId}/drawing-summary`)

export const saveSalesOrderHeader = (salesOrderId, payload = {}) => request(`/api/market/sales-order-management/orders/${salesOrderId}`, {
  method: 'PUT',
  data: payload
})

export const saveSalesOrderItems = (salesOrderId, payload = {}) => request(`/api/market/sales-order-management/orders/${salesOrderId}/items/batch`, {
  method: 'POST',
  data: payload
})

export const previewSalesOrderItemDataReplacement = (salesOrderId, formData) => request(`/api/market/sales-order-management/orders/${salesOrderId}/items/data-replacement/preview`, {
  method: 'POST',
  data: formData,
  headers: { 'Content-Type': 'multipart/form-data' }
})

export const confirmSalesOrderItemDataReplacement = (salesOrderId, formData) => request(`/api/market/sales-order-management/orders/${salesOrderId}/items/data-replacement/confirm`, {
  method: 'POST',
  data: formData,
  headers: { 'Content-Type': 'multipart/form-data' }
})

export const discardSalesOrder = (salesOrderId, payload = {}) => request(`/api/market/sales-order-management/orders/${salesOrderId}/discard`, {
  method: 'POST',
  data: payload
})

export const restoreSalesOrder = (salesOrderId, payload = {}) => request(`/api/market/sales-order-management/orders/${salesOrderId}/restore`, {
  method: 'POST',
  data: payload
})

export const supplementSalesOrderDrawings = (salesOrderId, formData) => request(`/api/market/sales-order-management/orders/${salesOrderId}/supplement-drawings`, {
  method: 'POST',
  data: formData,
  headers: { 'Content-Type': 'multipart/form-data' }
})

export const downloadSalesOrderProductionTaskNotice = (salesOrderId, fallbackName = `${salesOrderId}-生产任务通知单.xlsx`) => downloadFile(
  `/api/market/sales-order-management/orders/${salesOrderId}/production-task-notice/export`,
  fallbackName
)
