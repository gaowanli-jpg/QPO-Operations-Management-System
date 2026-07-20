import { beforeEach, describe, expect, test, vi } from 'vitest'
import { request } from '../api'
import * as engineerApi from './engineerApi'

vi.mock('../api', () => ({
  request: vi.fn()
}))

beforeEach(() => {
  request.mockReset()
})

describe('engineerApi', () => {
  test('saveOrderConfig posts normalized config by order id', async () => {
    const config = { materialName: '45#', processRoute: [{ processName: 'CNC1' }] }

    await engineerApi.saveOrderConfig(12, config)

    expect(request).toHaveBeenCalledWith('/api/engineer/order-config/12', {
      method: 'POST',
      data: config
    })
  })

  test('getRepeatAnalysis loads repeat analysis by rfq id', async () => {
    await engineerApi.getRepeatAnalysis(12)

    expect(request).toHaveBeenCalledWith('/api/engineer/repeat-analysis/12')
  })

  test('listSetupOrders forwards engineer setup order picker params', async () => {
    const params = { customerName: 'ACME', status: 'UNPRICED' }

    await engineerApi.listSetupOrders(params)

    expect(request).toHaveBeenCalledWith('/api/engineer/setup/orders', { params })
  })

  test('getSetupSummary loads engineer setup order summary by rfq id', async () => {
    await engineerApi.getSetupSummary(12)

    expect(request).toHaveBeenCalledWith('/api/engineer/setup/summary/12')
  })

  test('getPricingSummary loads pricing workspace summary by rfq id', async () => {
    await engineerApi.getPricingSummary(12)

    expect(request).toHaveBeenCalledWith('/api/engineer/pricing/summary/12')
  })

  test('applyRepeatQuotes posts repeat quote application by order id', async () => {
    await engineerApi.applyRepeatQuotes(12)

    expect(request).toHaveBeenCalledWith('/api/engineer/apply-repeat-quotes/12', {
      method: 'POST'
    })
  })

  test('completeOrderQuote posts completion by rfq id', async () => {
    await engineerApi.completeOrderQuote(12)

    expect(request).toHaveBeenCalledWith('/api/engineer/complete-order/12', {
      method: 'POST'
    })
  })

  test('listAssemblyRelations loads assembly children by item id', async () => {
    await engineerApi.listAssemblyRelations(34)

    expect(request).toHaveBeenCalledWith('/api/engineer/assembly-relations/34')
  })

  test('submitQuote posts quote payload', async () => {
    const payload = { itemId: 34, quoteMode: 'NORMAL' }

    await engineerApi.submitQuote(payload)

    expect(request).toHaveBeenCalledWith('/api/engineer/submit-quote', {
      method: 'POST',
      data: payload
    })
  })
})
