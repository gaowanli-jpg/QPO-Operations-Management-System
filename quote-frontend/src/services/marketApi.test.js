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

    expect(request).toHaveBeenNthCalledWith(1, '/api/market/quote-process/orders', { params })
    expect(request).toHaveBeenNthCalledWith(2, '/api/market/release/orders', { params })
  })

  test('loadReleaseWorkspace requests all release data by rfq id', async () => {
    request
      .mockResolvedValueOnce({ orderInfo: { id: 18 } })
      .mockResolvedValueOnce([{ id: 1 }])
      .mockResolvedValueOnce([{ id: 2 }])
      .mockResolvedValueOnce([{ id: 3 }])

    const result = await marketApi.loadReleaseWorkspace(18)

    expect(request).toHaveBeenNthCalledWith(1, '/api/market/release/summary/18')
    expect(request).toHaveBeenNthCalledWith(2, '/api/market/internal-target-lead-time/18/items')
    expect(request).toHaveBeenNthCalledWith(3, '/api/market/tickets/18')
    expect(request).toHaveBeenNthCalledWith(4, '/api/market/process-sheets/18')
    expect(result).toEqual({
      summary: { orderInfo: { id: 18 } },
      internalTargetItems: [{ id: 1 }],
      tickets: [{ id: 2 }],
      processSheets: [{ id: 3 }]
    })
  })

  test('parsePartSelection uploads selection form data', async () => {
    const formData = new FormData()

    await marketApi.parsePartSelection(formData)

    expect(request).toHaveBeenCalledWith('/api/market/parse-part-selection', {
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
