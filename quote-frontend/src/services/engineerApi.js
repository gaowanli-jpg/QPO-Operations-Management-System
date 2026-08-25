import { downloadFile, request } from '../api'

export const listSetupOrders = (params = {}) => request('/api/engineer/setup/orders', { params })

export const listSupplementalPricingOrders = (params = {}) => request(
  '/api/engineer/pricing/supplemental-drawing/orders',
  { params }
)

export const getSetupSummary = (rfqId) => request(`/api/engineer/setup/summary/${rfqId}`)

export const getPricingSummary = (rfqId) => request(`/api/engineer/pricing/summary/${rfqId}`)

export const listPricingItems = (rfqId, params = {}) => request(`/api/engineer/pricing/items/${rfqId}`, { params })

export const listAssemblyChildCandidates = (rfqId, params = {}) => request(
  `/api/engineer/pricing/assembly-child-candidates/${rfqId}`,
  { params }
)

export const downloadPricingSheet = (rfqId, fallbackName = `pricing-sheet-${rfqId}.xlsx`) => downloadFile(
  `/api/engineer/pricing/export-sheet/${rfqId}`,
  fallbackName
)

export const saveOrderConfig = (orderId, config) => request(`/api/engineer/order-config/${orderId}`, {
  method: 'POST',
  data: config
})

export const applyRepeatQuotes = (orderId) => request(`/api/engineer/apply-repeat-quotes/${orderId}`, {
  method: 'POST'
})

export const getRepeatAnalysis = (rfqId) => request(`/api/engineer/repeat-analysis/${rfqId}`)

export const completeOrderQuote = (rfqId) => request(`/api/engineer/complete-order/${rfqId}`, {
  method: 'POST'
})

export const completeSupplementalDrawingQuote = (rfqId) => request(
  `/api/engineer/pricing/supplemental-drawing/complete-order/${rfqId}`,
  { method: 'POST' }
)

export const listAssemblyRelations = (itemId) => request(`/api/engineer/assembly-relations/${itemId}`)

export const submitQuote = (payload) => request('/api/engineer/submit-quote', {
  method: 'POST',
  data: payload
})

export const savePricingItemRemark = (itemId, remark) => request(`/api/engineer/pricing/items/${itemId}/remark`, {
  method: 'PUT',
  data: { remark }
})
