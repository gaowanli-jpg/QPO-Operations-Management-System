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

export const downloadRfqImportTemplate = () => downloadFile('/api/market/import-rfq/template', '客户询价表导入模板.xlsx')

export const importSalesOrderToQpo = (formData) => multipartPost('/api/market/sales-orders/import-qpo', formData)

export const downloadSalesOrderImportTemplate = () => downloadFile('/api/market/sales-orders/import/template', '销售订单导入模板.xlsx')

const MARKET_ORDER_ENDPOINTS = {
  'quote-process': '/api/market/quote-process/orders',
  release: '/api/market/release/orders',
  'delivery-document': '/api/market/delivery-documents/orders',
  'u9-sync': '/api/market/u9-sync/orders'
}

export const listMarketOrders = (params = {}, scope) => {
  const endpoint = MARKET_ORDER_ENDPOINTS[scope]
  if (!endpoint) {
    throw new Error('module-scoped order selection scope is required')
  }
  return request(endpoint, { params })
}

export const listReleaseItems = (rfqId, params = {}) => request(`/api/market/release/items/${rfqId}`, { params })

export const listReleaseItemsBatch = (payload) => postJson('/api/market/release/items/batch', payload)

export const listTickets = (rfqId, params = {}) => request(`/api/market/tickets/${rfqId}`, { params })

export const listProcessSheets = (rfqId, params = {}) => request(`/api/market/process-sheets/${rfqId}`, { params })

export const getDeliveryDocumentSummary = (sourceOrderId, params = {}) => request(
  `/api/market/delivery-documents/summary/${sourceOrderId}`,
  { params }
)

export const listDeliveryDocumentTickets = (sourceOrderId, params = {}) => request(
  `/api/market/delivery-documents/tickets/${sourceOrderId}`,
  { params }
)

export const listDeliveryDocumentProcessSheets = (sourceOrderId, params = {}) => request(
  `/api/market/delivery-documents/process-sheets/${sourceOrderId}`,
  { params }
)

export const listSalesMatchCustomers = (params = {}) => request('/api/market/sales-match/customers', { params })

export const listSalesMatchOrders = (params = {}) => request('/api/market/sales-match/orders', { params })

export const listSalesMatchItems = (salesOrderId, params = {}) => request(
  `/api/market/sales-match/orders/${salesOrderId}/items`,
  { params }
)

export const previewSalesOrderMatch = (payload) => postJson('/api/market/sales-match/preview', payload)

export const applySalesOrderMatch = (payload) => postJson('/api/market/sales-match/apply', payload)

export const listSalesOrderU9SyncOrders = (params = {}) => request('/api/market/u9-sync/orders', { params })

export const previewSalesOrderU9Sync = (salesOrderId) => request(`/api/market/u9-sync/orders/${salesOrderId}/preview`)

export const syncSalesOrderU9 = (salesOrderId) => postJson(`/api/market/u9-sync/orders/${salesOrderId}/sync`)

export const syncSalesOrdersU9 = (salesOrderIds = []) => postJson('/api/market/u9-sync/sync', { salesOrderIds })

export const parsePartSelection = (rfqId, formData) => multipartPost(`/api/market/release/parse-part-selection/${rfqId}`, formData)

export const parsePartSelectionBatch = (formData) => multipartPost('/api/market/release/parse-part-selection/batch', formData)

export const replaceReleaseDrawings = (formData) => multipartPost('/api/market/release/drawings/replace', formData)

export const shiftInternalTargetLeadTime = (payload) => postJson('/api/market/internal-target-lead-time/shift', payload)

export const setInternalTargetLeadTime = (payload) => postJson('/api/market/internal-target-lead-time/set', payload)

export const updateInternalTargetLeadTime = (payload) => postJson('/api/market/internal-target-lead-time/update', payload)

export const updateReleaseItemVersion = (itemId, payload) => postJson(`/api/market/release/items/${itemId}/version`, payload)

export const generateTicket = (payload) => postJson('/api/market/generate-ticket', payload)

export const getQuoteProcessSummary = (rfqId) => request(`/api/market/quote-process/${rfqId}/summary`)

export const listQuoteProcessItems = (rfqId, params = {}) => request(`/api/market/quote-process/${rfqId}/items`, { params })

export const parseQuoteProcessPartSelection = (rfqId, formData) => multipartPost(
  `/api/market/quote-process/${rfqId}/parse-part-selection`,
  formData
)

export const shiftQuoteProcessLeadTime = (payload) => postJson('/api/market/quote-process/lead-time/shift', payload)

export const setQuoteProcessLeadTime = (payload) => postJson('/api/market/quote-process/lead-time/set', payload)

export const updateQuoteProcessLeadTime = (payload) => postJson('/api/market/quote-process/lead-time/update', payload)

export const saveQuoteSheet = (rfqId) => postJson(`/api/market/quote-process/${rfqId}/save-quote`)

export const downloadMarketExport = (path, rfqId, fallbackName = `${path}-${rfqId}.xlsx`) => downloadFile(
  `/api/market/export/${path}/${rfqId}`,
  fallbackName
)

export const downloadDeliveryDocumentExport = (path, sourceOrderId, fallbackName = `${path}-${sourceOrderId}.xlsx`, params = {}) => {
  const query = params.sourceType ? `?sourceType=${encodeURIComponent(params.sourceType)}` : ''
  return downloadFile(
    `/api/market/delivery-documents/export/${path}/${sourceOrderId}${query}`,
    fallbackName
  )
}

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
