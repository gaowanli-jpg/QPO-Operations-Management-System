import { request } from '../api'

const multipartPost = (path, formData) => request(path, {
  method: 'POST',
  data: formData,
  headers: { 'Content-Type': 'multipart/form-data' }
})

export const listSalesOrderChangeOrders = (params = {}) => request('/api/change/sales-orders', { params })

export const previewSalesOrderChange = (salesOrderId) => request(`/api/change/sales-orders/${salesOrderId}/preview`)

export const saveSalesOrderChangeItems = (salesOrderId, payload) => request(`/api/change/sales-orders/${salesOrderId}/items`, {
  method: 'PUT',
  data: payload
})

export const previewSalesOrderChangeItemDataReplacement = (salesOrderId, formData) => multipartPost(
  `/api/change/sales-orders/${salesOrderId}/item-data-replacement/preview`,
  formData
)

export const confirmSalesOrderChangeItemDataReplacement = (salesOrderId, formData) => multipartPost(
  `/api/change/sales-orders/${salesOrderId}/item-data-replacement/confirm`,
  formData
)
