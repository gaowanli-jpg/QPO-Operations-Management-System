import { request } from '../api'

export const listDeliveredItems = (params = {}) => request('/api/order-delivery/tickets', { params })

export const getDeliveredItem = (ticketId) => request(`/api/order-delivery/tickets/${ticketId}`)

export const syncMesMaterial = (ticketId) => request(`/api/order-delivery/tickets/${ticketId}/mes-material`, {
  method: 'POST'
})

export const submitWmsAllocation = (ticketId) => request(`/api/order-delivery/tickets/${ticketId}/wms-allocation`, {
  method: 'POST'
})

export const listLockedItems = (params = {}) => request('/api/order-delivery/allocations', { params })

export const listTicketAllocations = (ticketId) => request(`/api/order-delivery/tickets/${ticketId}/allocations`)
