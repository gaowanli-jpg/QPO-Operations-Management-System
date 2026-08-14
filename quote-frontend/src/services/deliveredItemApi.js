import { request } from '../api'

export const listDeliveredItems = (params = {}) => request('/api/order-delivery/tickets', { params })

export const getDeliveredItem = (ticketId) => request(`/api/order-delivery/tickets/${ticketId}`)

export const syncMesMaterialBatch = (ticketIds = []) => request('/api/order-delivery/tickets/mes-material/batch', {
  method: 'POST',
  data: { ticketIds }
})

export const submitWmsAllocationBatch = (ticketIds = []) => request('/api/order-delivery/tickets/wms-allocation/batch', {
  method: 'POST',
  data: { ticketIds }
})

export const listLockedItems = (params = {}) => request('/api/order-delivery/locked-items', { params })

export const listProductionItems = (params = {}) => request('/api/order-delivery/production-items', { params })

export const listTicketAllocations = (ticketId) => request(`/api/order-delivery/tickets/${ticketId}/allocations`)

export const submitMesProductionDispatchBatch = (allocationIds = []) => request('/api/order-delivery/production-items/mes-production', {
  method: 'POST',
  data: { allocationIds }
})

export const getDeliveryIntegrationTask = (taskId) => request(`/api/tasks/${taskId}`)
