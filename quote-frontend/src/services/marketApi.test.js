import { beforeEach, describe, expect, test, vi } from 'vitest'
import { request } from '../api'
import * as marketApi from './marketApi'

vi.mock('../api', () => ({
  request: vi.fn()
}))

beforeEach(() => {
  request.mockReset()
})

describe('marketApi', () => {
  test('getMarketSummary loads market summary by rfq id', async () => {
    await marketApi.getMarketSummary(18)

    expect(request).toHaveBeenCalledWith('/api/market/summary/18')
  })
})
