import { beforeEach, describe, expect, test, vi } from 'vitest'
import { downloadFile, request } from '../api'
import * as marketApi from './marketApi'

vi.mock('../api', () => ({
  downloadFile: vi.fn(),
  request: vi.fn()
}))

beforeEach(() => {
  downloadFile.mockReset()
  request.mockReset()
})

describe('marketApi', () => {
  test('getReleaseSummary loads release summary by rfq id', async () => {
    await marketApi.getReleaseSummary(18)

    expect(request).toHaveBeenCalledWith('/api/market/release/summary/18')
  })

  test('importRfq uploads RFQ form data', async () => {
    const formData = new FormData()

    await marketApi.importRfq(formData)

    expect(request).toHaveBeenCalledWith('/api/market/import-rfq', {
      method: 'POST',
      data: formData,
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  })

  test('does not expose legacy sales order U9 import helper', () => {
    expect('importSalesOrder' in marketApi).toBe(false)
  })

  test('importSalesOrderToQpo uploads sales order form data to QPO flow endpoint', async () => {
    const formData = new FormData()

    await marketApi.importSalesOrderToQpo(formData)

    expect(request).toHaveBeenCalledWith('/api/market/sales-orders/import-qpo', {
      method: 'POST',
      data: formData,
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  })

  test('downloads RFQ and sales order import templates', async () => {
    await marketApi.downloadRfqImportTemplate()
    await marketApi.downloadSalesOrderImportTemplate()

    expect(downloadFile).toHaveBeenNthCalledWith(
      1,
      '/api/market/import-rfq/template',
      '客户询价表导入模板.xlsx'
    )
    expect(downloadFile).toHaveBeenNthCalledWith(
      2,
      '/api/market/sales-orders/import/template',
      '销售订单导入模板.xlsx'
    )
  })

  test('listMarketOrders requires a module-scoped order selection endpoint', () => {
    const params = { customerId: 3, keyword: 'RFQ' }

    expect(() => marketApi.listMarketOrders(params)).toThrow('module-scoped order selection scope')
    expect(request).not.toHaveBeenCalled()
  })

  test('does not expose old common market summary helper', () => {
    expect('getMarketSummary' in marketApi).toBe(false)
  })

  test('does not expose customer-lead-time quote export helper', () => {
    expect('downloadCustomerLeadTimeQuote' in marketApi).toBe(false)
  })

  test('listMarketOrders uses module-scoped order selection endpoints', async () => {
    const params = { customerName: 'ACME', status: 'PRICED' }

    await marketApi.listMarketOrders(params, 'quote-process')
    await marketApi.listMarketOrders(params, 'release')
    await marketApi.listMarketOrders(params, 'delivery-document')
    await marketApi.listMarketOrders(params, 'u9-sync')
    await marketApi.listMarketOrders(params, 'sales-match')

    expect(request).toHaveBeenNthCalledWith(1, '/api/market/quote-process/orders', { params })
    expect(request).toHaveBeenNthCalledWith(2, '/api/market/release/orders', { params })
    expect(request).toHaveBeenNthCalledWith(3, '/api/market/delivery-documents/orders', { params })
    expect(request).toHaveBeenNthCalledWith(4, '/api/market/u9-sync/orders', { params })
    expect(request).toHaveBeenNthCalledWith(5, '/api/market/sales-match/orders', { params })
  })

  test('delivery document helpers use the independent backend namespace', async () => {
    const params = { page: 2, pageSize: 50 }

    await marketApi.getDeliveryDocumentSummary(18, { sourceType: 'SALES_ORDER' })
    await marketApi.listDeliveryDocumentTickets(18, params)
    await marketApi.listDeliveryDocumentProcessSheets(18, params)
    await marketApi.downloadDeliveryDocumentExport('tickets', 18, 'delivery-tickets-18.xlsx', { sourceType: 'SALES_ORDER' })

    expect(request).toHaveBeenNthCalledWith(1, '/api/market/delivery-documents/summary/18', { params: { sourceType: 'SALES_ORDER' } })
    expect(request).toHaveBeenNthCalledWith(2, '/api/market/delivery-documents/tickets/18', { params })
    expect(request).toHaveBeenNthCalledWith(3, '/api/market/delivery-documents/process-sheets/18', { params })
    expect(downloadFile).toHaveBeenCalledWith(
      '/api/market/delivery-documents/export/tickets/18?sourceType=SALES_ORDER',
      'delivery-tickets-18.xlsx'
    )
  })

  test('sales match helpers use independent sales-order endpoints with backend paging', async () => {
    const params = { customerName: 'ACME', keyword: 'SO-2026', page: 2, pageSize: 50 }
    const payload = { salesOrderIds: [101], salesOrderItemIds: [9001], onlyUnmatched: true }

    await marketApi.listSalesMatchCustomers({ keyword: 'ACME' })
    await marketApi.listSalesMatchOrders(params)
    await marketApi.listSalesMatchItems(101, params)
    await marketApi.previewSalesOrderMatch(payload)
    await marketApi.applySalesOrderMatch({ ...payload, applyAllMatchedInSelectedOrders: false })
    await marketApi.previewSalesOrderMatchByOrder(101)
    await marketApi.applySalesOrderMatchByOrder(101)

    expect(request).toHaveBeenNthCalledWith(1, '/api/market/sales-match/customers', { params: { keyword: 'ACME' } })
    expect(request).toHaveBeenNthCalledWith(2, '/api/market/sales-match/orders', { params })
    expect(request).toHaveBeenNthCalledWith(3, '/api/market/sales-match/orders/101/items', { params })
    expect(request).toHaveBeenNthCalledWith(4, '/api/market/sales-match/preview', {
      method: 'POST',
      data: payload
    })
    expect(request).toHaveBeenNthCalledWith(5, '/api/market/sales-match/apply', {
      method: 'POST',
      data: { ...payload, applyAllMatchedInSelectedOrders: false }
    })
    expect(request).toHaveBeenNthCalledWith(6, '/api/market/sales-match/orders/101/preview')
    expect(request).toHaveBeenNthCalledWith(7, '/api/market/sales-match/orders/101/apply', {
      method: 'POST',
      data: undefined
    })
  })

  test('sales order U9 sync helpers use the independent whole-order sync namespace', async () => {
    const params = {
      customerName: 'ACME',
      keyword: 'SO-2026',
      status: '已生成提单',
      u9SyncStatus: '未同步',
      materialReadyStatus: 'READY',
      page: 2,
      pageSize: 50
    }

    await marketApi.listSalesOrderU9SyncOrders(params)
    await marketApi.previewSalesOrderU9Sync(101)
    await marketApi.syncSalesOrderU9(101)
    await marketApi.syncSalesOrdersU9([101, 102])

    expect(request).toHaveBeenNthCalledWith(1, '/api/market/u9-sync/orders', { params })
    expect(request).toHaveBeenNthCalledWith(2, '/api/market/u9-sync/orders/101/preview')
    expect(request).toHaveBeenNthCalledWith(3, '/api/market/u9-sync/orders/101/sync', {
      method: 'POST',
      data: undefined
    })
    expect(request).toHaveBeenNthCalledWith(4, '/api/market/u9-sync/sync', {
      method: 'POST',
      data: { salesOrderIds: [101, 102] }
    })
  })

  test('release workspace helpers use split paging endpoints', async () => {
    const params = { page: 2, pageSize: 50, keyword: 'PN' }

    await marketApi.listReleaseItems(18, params)
    await marketApi.listReleaseItemsBatch({ rfqIds: [18, 19], ...params })
    await marketApi.listTickets(18, params)
    await marketApi.listProcessSheets(18, params)

    expect(request).toHaveBeenNthCalledWith(1, '/api/market/release/items/18', { params })
    expect(request).toHaveBeenNthCalledWith(2, '/api/market/release/items/batch', {
      method: 'POST',
      data: { rfqIds: [18, 19], ...params }
    })
    expect(request).toHaveBeenNthCalledWith(3, '/api/market/tickets/18', { params })
    expect(request).toHaveBeenNthCalledWith(4, '/api/market/process-sheets/18', { params })
    expect('loadReleaseWorkspace' in marketApi).toBe(false)
  })

  test('parsePartSelection uploads selection form data by rfq id', async () => {
    const formData = new FormData()

    await marketApi.parsePartSelection(18, formData)

    expect(request).toHaveBeenCalledWith('/api/market/release/parse-part-selection/18', {
      method: 'POST',
      data: formData,
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  })

  test('parsePartSelectionBatch uploads selection form data with rfq ids payload', async () => {
    const formData = new FormData()
    formData.append('rfqIds', JSON.stringify([18, 19]))

    await marketApi.parsePartSelectionBatch(formData)

    expect(request).toHaveBeenCalledWith('/api/market/release/parse-part-selection/batch', {
      method: 'POST',
      data: formData,
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  })

  test('replaceReleaseDrawings uploads release drawing replacement form data', async () => {
    const formData = new FormData()
    formData.append('rfqIds', JSON.stringify([18, 19]))

    await marketApi.replaceReleaseDrawings(formData)

    expect(request).toHaveBeenCalledWith('/api/market/release/drawings/replace', {
      method: 'POST',
      data: formData,
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  })

  test('shiftInternalTargetLeadTime posts shift payload', async () => {
    const payload = { rfqId: 18, itemIds: [1], deltaDays: 2 }

    await marketApi.shiftInternalTargetLeadTime(payload)

    expect(request).toHaveBeenCalledWith('/api/market/internal-target-lead-time/shift', {
      method: 'POST',
      data: payload
    })
  })

  test('setInternalTargetLeadTime posts set payload', async () => {
    const payload = { rfqId: 18, itemIds: [1], targetLeadTime: 5 }

    await marketApi.setInternalTargetLeadTime(payload)

    expect(request).toHaveBeenCalledWith('/api/market/internal-target-lead-time/set', {
      method: 'POST',
      data: payload
    })
  })

  test('updateInternalTargetLeadTime posts update payload', async () => {
    const payload = { itemId: 1, internalTargetLeadTime: 5 }

    await marketApi.updateInternalTargetLeadTime(payload)

    expect(request).toHaveBeenCalledWith('/api/market/internal-target-lead-time/update', {
      method: 'POST',
      data: payload
    })
  })

  test('generateTicket posts ticket payload', async () => {
    const payload = { rfqId: 18, itemIds: [1] }

    await marketApi.generateTicket(payload)

    expect(request).toHaveBeenCalledWith('/api/market/generate-ticket', {
      method: 'POST',
      data: payload
    })
  })

  test('updateReleaseItemVersion posts item-scoped version payload', async () => {
    const payload = { rfqId: 18, version: 'B' }

    await marketApi.updateReleaseItemVersion(201, payload)

    expect(request).toHaveBeenCalledWith('/api/market/release/items/201/version', {
      method: 'POST',
      data: payload
    })
  })

  test('updateReleaseItemFields posts item-scoped editable field payload', async () => {
    const payload = {
      sourceType: 'SALES_ORDER',
      sourceOrderId: 2001,
      description: 'desc',
      surfaceTreatment: 'anodized',
      remark: 'note',
      customerRequirement: 'need'
    }

    await marketApi.updateReleaseItemFields(3001, payload)

    expect(request).toHaveBeenCalledWith('/api/market/release/items/3001/fields', {
      method: 'POST',
      data: payload
    })
  })

  test('does not expose destructive release result delete helpers', () => {
    expect('deleteTicket' in marketApi).toBe(false)
    expect('deleteProcessSheet' in marketApi).toBe(false)
  })

  test('quote process summary and item paging helpers use split endpoints', async () => {
    const params = { page: 2, pageSize: 50, keyword: 'PN' }

    await marketApi.getQuoteProcessSummary(18)
    await marketApi.listQuoteProcessItems(18, params)

    expect(request).toHaveBeenNthCalledWith(1, '/api/market/quote-process/18/summary')
    expect(request).toHaveBeenNthCalledWith(2, '/api/market/quote-process/18/items', { params })
  })

  test('parseQuoteProcessPartSelection uploads quote process lock form data by rfq id', async () => {
    const formData = new FormData()

    await marketApi.parseQuoteProcessPartSelection(18, formData)

    expect(request).toHaveBeenCalledWith('/api/market/quote-process/18/parse-part-selection', {
      method: 'POST',
      data: formData,
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  })

  test('does not expose legacy full quote process workspace loader', () => {
    expect('getQuoteProcess' in marketApi).toBe(false)
  })

  test('quote process lead time helpers preserve endpoints', async () => {
    const payload = { rfqId: 18, itemIds: [1], deltaDays: 2 }

    await marketApi.shiftQuoteProcessLeadTime(payload)
    await marketApi.setQuoteProcessLeadTime(payload)
    await marketApi.updateQuoteProcessLeadTime(payload)

    expect(request).toHaveBeenNthCalledWith(1, '/api/market/quote-process/lead-time/shift', {
      method: 'POST',
      data: payload
    })
    expect(request).toHaveBeenNthCalledWith(2, '/api/market/quote-process/lead-time/set', {
      method: 'POST',
      data: payload
    })
    expect(request).toHaveBeenNthCalledWith(3, '/api/market/quote-process/lead-time/update', {
      method: 'POST',
      data: payload
    })
  })

  test('saveQuoteSheet posts quote confirmation by rfq id', async () => {
    await marketApi.saveQuoteSheet(18)

    expect(request).toHaveBeenCalledWith('/api/market/quote-process/18/save-quote', {
      method: 'POST',
      data: undefined
    })
  })

  test('download helpers preserve export paths and fallback names', async () => {
    await marketApi.downloadMarketExport('ticket', 18, 'ticket-18.xlsx')
    await marketApi.downloadQuote(18)
    await marketApi.downloadSimpleQuote(18)
    await marketApi.downloadQuoteTemplateDetail(18)
    await marketApi.downloadQuoteTemplateSimple(18)

    expect(downloadFile).toHaveBeenNthCalledWith(1, '/api/market/export/ticket/18', 'ticket-18.xlsx')
    expect(downloadFile).toHaveBeenNthCalledWith(2, '/api/market/export/quote/18', 'quote-18.xlsx')
    expect(downloadFile).toHaveBeenNthCalledWith(3, '/api/market/export/quote/simple/18', 'quote-simple-18.xlsx')
    expect(downloadFile).toHaveBeenNthCalledWith(4, '/api/market/export/quote/template/detail/18', 'quote-template-detail-18.xlsx')
    expect(downloadFile).toHaveBeenNthCalledWith(5, '/api/market/export/quote/template/simple/18', 'quote-template-simple-18.xlsx')
  })
})
