import { request } from '../api'

export const listDeliveredItems = (params = {}) => request('/api/order-delivery/tickets', { params })

export const getDeliveredItem = (ticketId) => request(`/api/order-delivery/tickets/${ticketId}`)

export const syncMesMaterial = (ticketId) => request(`/api/order-delivery/tickets/${ticketId}/mes-material`, {
  method: 'POST'
})

export const submitWmsAllocation = (ticketId) => request(`/api/order-delivery/tickets/${ticketId}/wms-allocation`, {
  method: 'POST'
})

export const syncMesMaterialBatch = (ticketIds = []) => request('/api/order-delivery/tickets/mes-material/batch', {
  method: 'POST',
  data: { ticketIds }
})

export const submitWmsAllocationBatch = (ticketIds = []) => request('/api/order-delivery/tickets/wms-allocation/batch', {
  method: 'POST',
  data: { ticketIds }
})

export const listLockedItems = (params = {}) => request('/api/order-delivery/allocations', { params })

export const listTicketAllocations = (ticketId) => request(`/api/order-delivery/tickets/${ticketId}/allocations`)

export const submitMesProductionDispatchBatch = (allocationIds = []) => request('/api/order-delivery/allocations/mes-production', {
  method: 'POST',
  data: { allocationIds }
})
