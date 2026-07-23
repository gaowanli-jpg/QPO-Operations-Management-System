import { beforeEach, describe, expect, test, vi } from 'vitest'
import { request } from '../api'
import * as dictApi from './dictApi'

vi.mock('../api', () => ({
  request: vi.fn()
}))

beforeEach(() => {
  request.mockReset()
})

describe('dictApi', () => {
  test('loads extra fee items from all endpoint when disabled records are included', async () => {
    await dictApi.listExtraFeeItems({ includeDisabled: true })

    expect(request).toHaveBeenCalledWith('/api/dict/extra-fee-items/all')
  })

  test('saves existing material with PUT by id', async () => {
    const payload = { id: 7, materialName: '45#' }

    await dictApi.saveMaterial(payload)

    expect(request).toHaveBeenCalledWith('/api/dict/materials/7', {
      method: 'PUT',
      data: payload
    })
  })

  test('deletes markup fee by id', async () => {
    await dictApi.deleteMarkupFee(9)

    expect(request).toHaveBeenCalledWith('/api/dict/markup-fees/9', {
      method: 'DELETE'
    })
  })

  test('manages assembly markup fees with dedicated endpoints', async () => {
    const payload = { id: 11, feeName: '组装管理费', ratePercent: 5 }

    await dictApi.listAssemblyMarkupFees()
    await dictApi.saveAssemblyMarkupFee(payload)
    await dictApi.deleteAssemblyMarkupFee(11)

    expect(request).toHaveBeenNthCalledWith(1, '/api/dict/assembly-markup-fees')
    expect(request).toHaveBeenNthCalledWith(2, '/api/dict/assembly-markup-fees/11', {
      method: 'PUT',
      data: payload
    })
    expect(request).toHaveBeenNthCalledWith(3, '/api/dict/assembly-markup-fees/11', {
      method: 'DELETE'
    })
  })
})
