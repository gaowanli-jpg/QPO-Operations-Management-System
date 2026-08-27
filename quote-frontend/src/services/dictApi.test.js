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

  test('manages material types with dedicated endpoints', async () => {
    const payload = { id: 3, typeCode: 'AUXILIARY', typeName: '辅料', enabled: true }

    await dictApi.listMaterialTypes({ enabledOnly: true })
    await dictApi.saveMaterialType(payload)
    await dictApi.deleteMaterialType(3)

    expect(request).toHaveBeenNthCalledWith(1, '/api/dict/material-types?enabledOnly=true')
    expect(request).toHaveBeenNthCalledWith(2, '/api/dict/material-types/3', {
      method: 'PUT',
      data: payload
    })
    expect(request).toHaveBeenNthCalledWith(3, '/api/dict/material-types/3', {
      method: 'DELETE'
    })
  })

  test('loads materials with enabled filter query', async () => {
    await dictApi.listMaterials({ enabledOnly: false })

    expect(request).toHaveBeenCalledWith('/api/dict/materials?enabledOnly=false')
  })

  test('loads material and process management dictionaries with backend pagination', async () => {
    await dictApi.pageMaterials({ enabledOnly: true, keyword: 'raw', page: 2, pageSize: 20 })
    await dictApi.pageProcessCategories({ enabledOnly: false, keyword: 'pack', page: 3, pageSize: 50 })
    await dictApi.pageProcessDetails({ categoryId: 9, enabledOnly: true, keyword: 'cnc', page: 4, pageSize: 100 })

    expect(request).toHaveBeenNthCalledWith(1, '/api/dict/materials/page?enabledOnly=true&keyword=raw&page=2&pageSize=20')
    expect(request).toHaveBeenNthCalledWith(2, '/api/dict/process-categories/page?enabledOnly=false&keyword=pack&page=3&pageSize=50')
    expect(request).toHaveBeenNthCalledWith(3, '/api/dict/process-details/page?categoryId=9&enabledOnly=true&keyword=cnc&page=4&pageSize=100')
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
