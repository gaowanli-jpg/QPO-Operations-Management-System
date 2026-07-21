import { request } from '../api'

export const listDeliveredItems = (params = {}) => request('/api/order-delivery/tickets', { params })

export const getDeliveredItem = (ticketId) => request(`/api/order-delivery/tickets/${ticketId}`)

export const syncMesMaterial = (ticketId) => request(`/api/order-delivery/tickets/${ticketId}/mes-material`, {
  method: 'POST'
})
