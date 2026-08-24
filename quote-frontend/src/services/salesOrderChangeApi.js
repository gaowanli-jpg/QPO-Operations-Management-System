import { request } from '../api'

export const listSalesOrderChangeOrders = (params = {}) => request('/api/change/sales-orders', { params })

export const previewSalesOrderChange = (salesOrderId) => request(`/api/change/sales-orders/${salesOrderId}/preview`)

export const saveSalesOrderChangeItems = (salesOrderId, payload) => request(`/api/change/sales-orders/${salesOrderId}/items`, {
  method: 'PUT',
  data: payload
})
