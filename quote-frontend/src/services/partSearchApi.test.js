import { beforeEach, describe, expect, test, vi } from 'vitest'
import { request } from '../api'
import * as partSearchApi from './partSearchApi'

vi.mock('../api', () => ({
  request: vi.fn()
}))

beforeEach(() => {
  request.mockReset()
})

describe('partSearchApi', () => {
  test('loads rfq and sales part search lists through independent read-only endpoints', async () => {
    const params = { partNo: 'P-100', version: 'A', page: 1, pageSize: 20 }

    await partSearchApi.listRfqPartSearchItems(params)
    await partSearchApi.listSalesPartSearchItems(params)

    expect(request).toHaveBeenNthCalledWith(1, '/api/market/part-search/rfq-items', { params })
    expect(request).toHaveBeenNthCalledWith(2, '/api/market/part-search/sales-items', { params })
  })

  test('loads rfq and sales item details without write endpoints', async () => {
    await partSearchApi.getRfqPartSearchItem(12)
    await partSearchApi.getSalesPartSearchItem(22)

    expect(request).toHaveBeenNthCalledWith(1, '/api/market/part-search/rfq-items/12')
    expect(request).toHaveBeenNthCalledWith(2, '/api/market/part-search/sales-items/22')
    expect('savePartSearchItem' in partSearchApi).toBe(false)
    expect('deletePartSearchItem' in partSearchApi).toBe(false)
  })
})
