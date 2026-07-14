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
})
