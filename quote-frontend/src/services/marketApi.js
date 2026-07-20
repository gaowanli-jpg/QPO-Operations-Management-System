import { downloadFile, request } from '../api'

export const getReleaseSummary = (rfqId) => request(`/api/market/release/summary/${rfqId}`)

const multipartPost = (path, formData) => request(path, {
  method: 'POST',
  data: formData,
  headers: { 'Content-Type': 'multipart/form-data' }
})

const postJson = (path, payload) => request(path, {
  method: 'POST',
  data: payload
})

export const importRfq = (formData) => multipartPost('/api/market/import-rfq', formData)

const MARKET_ORDER_ENDPOINTS = {
  'quote-process': '/api/market/quote-process/orders',
  release: '/api/market/release/orders'
}

export const listMarketOrders = (params = {}, scope) => {
  const endpoint = MARKET_ORDER_ENDPOINTS[scope]
  if (!endpoint) {
    throw new Error('module-scoped order selection scope is required')
  }
  return request(endpoint, { params })
}

export const listInternalTargetLeadTimeItems = (rfqId) => request(`/api/market/internal-target-lead-time/${rfqId}/items`)

export const listTickets = (rfqId) => request(`/api/market/tickets/${rfqId}`)

export const listProcessSheets = (rfqId) => request(`/api/market/process-sheets/${rfqId}`)

export const loadReleaseWorkspace = async (rfqId) => {
  const [summary, internalTargetItems, tickets, processSheets] = await Promise.all([
    getReleaseSummary(rfqId),
    listInternalTargetLeadTimeItems(rfqId),
    listTickets(rfqId),
    listProcessSheets(rfqId)
  ])
  return { summary, internalTargetItems, tickets, processSheets }
}

export const parsePartSelection = (formData) => multipartPost('/api/market/parse-part-selection', formData)

export const shiftInternalTargetLeadTime = (payload) => postJson('/api/market/internal-target-lead-time/shift', payload)

export const setInternalTargetLeadTime = (payload) => postJson('/api/market/internal-target-lead-time/set', payload)

export const updateInternalTargetLeadTime = (payload) => postJson('/api/market/internal-target-lead-time/update', payload)

export const generateTicket = (payload) => postJson('/api/market/generate-ticket', payload)

export const getQuoteProcess = (rfqId) => request(`/api/market/quote-process/${rfqId}`)

export const shiftQuoteProcessLeadTime = (payload) => postJson('/api/market/quote-process/lead-time/shift', payload)

export const setQuoteProcessLeadTime = (payload) => postJson('/api/market/quote-process/lead-time/set', payload)

export const updateQuoteProcessLeadTime = (payload) => postJson('/api/market/quote-process/lead-time/update', payload)

export const saveQuoteSheet = (rfqId) => postJson(`/api/market/quote-process/${rfqId}/save-quote`)

export const downloadCustomerLeadTimeQuote = (rfqId, fallbackName = `quote-${rfqId}.xlsx`) => downloadFile(
  `/api/market/export/quote/customer-lead-time/${rfqId}`,
  fallbackName
)

export const downloadMarketExport = (path, rfqId, fallbackName = `${path}-${rfqId}.xlsx`) => downloadFile(
  `/api/market/export/${path}/${rfqId}`,
  fallbackName
)

export const downloadQuote = (rfqId, fallbackName = `quote-${rfqId}.xlsx`) => downloadFile(
  `/api/market/export/quote/${rfqId}`,
  fallbackName
)

export const downloadSimpleQuote = (rfqId, fallbackName = `quote-simple-${rfqId}.xlsx`) => downloadFile(
  `/api/market/export/quote/simple/${rfqId}`,
  fallbackName
)

export const downloadQuoteTemplateDetail = (rfqId, fallbackName = `quote-template-detail-${rfqId}.xlsx`) => downloadFile(
  `/api/market/export/quote/template/detail/${rfqId}`,
  fallbackName
)

export const downloadQuoteTemplateSimple = (rfqId, fallbackName = `quote-template-simple-${rfqId}.xlsx`) => downloadFile(
  `/api/market/export/quote/template/simple/${rfqId}`,
  fallbackName
)
