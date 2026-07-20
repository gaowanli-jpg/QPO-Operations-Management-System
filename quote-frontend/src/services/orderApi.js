import { request } from '../api'

export const listCustomers = () => request('/api/order-management/customers')

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
