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
  test('getMarketSummary loads market summary by rfq id', async () => {
    await marketApi.getMarketSummary(18)

    expect(request).toHaveBeenCalledWith('/api/market/summary/18')
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

  test('listMarketOrders forwards picker query params', async () => {
    const params = { customerId: 3, keyword: 'RFQ' }

    await marketApi.listMarketOrders(params)

    expect(request).toHaveBeenCalledWith('/api/market/orders', { params })
  })

  test('loadReleaseWorkspace requests all release data by rfq id', async () => {
    request
      .mockResolvedValueOnce({ orderInfo: { id: 18 } })
      .mockResolvedValueOnce([{ id: 1 }])
      .mockResolvedValueOnce([{ id: 2 }])
      .mockResolvedValueOnce([{ id: 3 }])

    const result = await marketApi.loadReleaseWorkspace(18)

    expect(request).toHaveBeenNthCalledWith(1, '/api/market/summary/18')
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

  test('deleteTicket deletes ticket by id', async () => {
    await marketApi.deleteTicket(3)

    expect(request).toHaveBeenCalledWith('/api/market/tickets/3', {
      method: 'DELETE'
    })
  })

  test('deleteProcessSheet deletes process sheet by id', async () => {
    await marketApi.deleteProcessSheet(4)

    expect(request).toHaveBeenCalledWith('/api/market/process-sheets/4', {
      method: 'DELETE'
    })
  })

  test('getQuoteProcess loads quote process data', async () => {
    await marketApi.getQuoteProcess(18)

    expect(request).toHaveBeenCalledWith('/api/market/quote-process/18')
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

  test('download helpers preserve export paths and fallback names', async () => {
    await marketApi.downloadCustomerLeadTimeQuote(18)
    await marketApi.downloadMarketExport('ticket', 18, 'ticket-18.xlsx')
    await marketApi.downloadQuote(18)
    await marketApi.downloadSimpleQuote(18)
    await marketApi.downloadQuoteTemplateDetail(18)
    await marketApi.downloadQuoteTemplateSimple(18)

    expect(downloadFile).toHaveBeenNthCalledWith(1, '/api/market/export/quote/customer-lead-time/18', 'quote-18.xlsx')
    expect(downloadFile).toHaveBeenNthCalledWith(2, '/api/market/export/ticket/18', 'ticket-18.xlsx')
    expect(downloadFile).toHaveBeenNthCalledWith(3, '/api/market/export/quote/18', 'quote-18.xlsx')
    expect(downloadFile).toHaveBeenNthCalledWith(4, '/api/market/export/quote/simple/18', 'quote-simple-18.xlsx')
    expect(downloadFile).toHaveBeenNthCalledWith(5, '/api/market/export/quote/template/detail/18', 'quote-template-detail-18.xlsx')
    expect(downloadFile).toHaveBeenNthCalledWith(6, '/api/market/export/quote/template/simple/18', 'quote-template-simple-18.xlsx')
  })
})
