import { request } from '../api'

export const listDeliveredItems = (params = {}) => request('/api/order-delivery/tickets', { params })

export const getDeliveredItem = (ticketId) => request(`/api/order-delivery/tickets/${ticketId}`)

export const discardDeliveredItem = (ticketId, payload = {}) => request(`/api/order-delivery/tickets/${ticketId}/discard`, {
  method: 'POST',
  data: payload
})

export const restoreDeliveredItem = (ticketId, payload = {}) => request(`/api/order-delivery/tickets/${ticketId}/restore`, {
  method: 'POST',
  data: payload
})

export const batchDiscardDeliveredItems = (ticketIds = [], payload = {}) => request('/api/order-delivery/tickets/batch-discard', {
  method: 'POST',
  data: { ...payload, ticketIds }
})

export const batchRestoreDeliveredItems = (ticketIds = [], payload = {}) => request('/api/order-delivery/tickets/batch-restore', {
  method: 'POST',
  data: { ...payload, ticketIds }
})

export const syncMesMaterialBatch = (ticketIds = []) => request('/api/order-delivery/tickets/mes-material/batch', {
  method: 'POST',
  data: { ticketIds }
})

export const submitWmsAllocationBatch = (ticketIds = []) => request('/api/order-delivery/tickets/wms-allocation/batch', {
  method: 'POST',
  data: { ticketIds }
})

export const listLockedItems = (params = {}) => request('/api/order-delivery/locked-items', { params })

export const submitLockedStockPickingOrderBatch = (allocationIds = []) => request('/api/order-delivery/locked-items/stock-picking-orders', {
  method: 'POST',
  data: { allocationIds }
})

export const discardLockedItem = (allocationId, payload = {}) => request(`/api/order-delivery/locked-items/${allocationId}/discard`, {
  method: 'POST',
  data: payload
})

export const restoreLockedItem = (allocationId, payload = {}) => request(`/api/order-delivery/locked-items/${allocationId}/restore`, {
  method: 'POST',
  data: payload
})

export const batchDiscardLockedItems = (allocationIds = [], payload = {}) => request('/api/order-delivery/locked-items/batch-discard', {
  method: 'POST',
  data: { ...payload, allocationIds }
})

export const batchRestoreLockedItems = (allocationIds = [], payload = {}) => request('/api/order-delivery/locked-items/batch-restore', {
  method: 'POST',
  data: { ...payload, allocationIds }
})

export const listProductionItems = (params = {}) => request('/api/order-delivery/production-items', { params })

export const discardProductionItem = (allocationId, payload = {}) => request(`/api/order-delivery/production-items/${allocationId}/discard`, {
  method: 'POST',
  data: payload
})

export const restoreProductionItem = (allocationId, payload = {}) => request(`/api/order-delivery/production-items/${allocationId}/restore`, {
  method: 'POST',
  data: payload
})

export const batchDiscardProductionItems = (allocationIds = [], payload = {}) => request('/api/order-delivery/production-items/batch-discard', {
  method: 'POST',
  data: { ...payload, allocationIds }
})

export const batchRestoreProductionItems = (allocationIds = [], payload = {}) => request('/api/order-delivery/production-items/batch-restore', {
  method: 'POST',
  data: { ...payload, allocationIds }
})

export const listTicketAllocations = (ticketId) => request(`/api/order-delivery/tickets/${ticketId}/allocations`)

export const submitMesProductionDispatchBatch = (allocationIds = []) => request('/api/order-delivery/production-items/mes-production', {
  method: 'POST',
  data: { allocationIds }
})

export const getDeliveryIntegrationTask = (taskId) => request(`/api/tasks/${taskId}`)
