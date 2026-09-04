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
    await dictApi.pageExtraFeeItems({ includeDisabled: true, keyword: 'edm', page: 5, pageSize: 50 })

    expect(request).toHaveBeenNthCalledWith(1, '/api/dict/materials/page?enabledOnly=true&keyword=raw&page=2&pageSize=20')
    expect(request).toHaveBeenNthCalledWith(2, '/api/dict/process-categories/page?enabledOnly=false&keyword=pack&page=3&pageSize=50')
    expect(request).toHaveBeenNthCalledWith(3, '/api/dict/process-details/page?categoryId=9&enabledOnly=true&keyword=cnc&page=4&pageSize=100')
    expect(request).toHaveBeenNthCalledWith(4, '/api/dict/extra-fee-items/page?includeDisabled=true&keyword=edm&page=5&pageSize=50')
  })

  test('manages process arrangement tree and placements', async () => {
    const nodePayload = { parentId: null, nodeName: '铣', sortNo: 1 }
    const itemPayload = { parentNodeId: 7, items: [{ sourceType: 'process', processDetailId: 9 }] }

    await dictApi.listProcessArrangementTree()
    await dictApi.listUnplacedProcessArrangementOptions({ keyword: 'cnc', sourceType: 'process' })
    await dictApi.createProcessArrangementNode(nodePayload)
    await dictApi.updateProcessArrangementNode(7, nodePayload)
    await dictApi.deleteProcessArrangementNode(7)
    await dictApi.placeProcessArrangementItems(itemPayload)
    await dictApi.moveProcessArrangementItem(11, { parentNodeId: null, sortNo: 3 })
    await dictApi.removeProcessArrangementItem(11)

    expect(request).toHaveBeenNthCalledWith(1, '/api/dict/process-arrangement/tree')
    expect(request).toHaveBeenNthCalledWith(2, '/api/dict/process-arrangement/unplaced-options?keyword=cnc&sourceType=process')
    expect(request).toHaveBeenNthCalledWith(3, '/api/dict/process-arrangement/nodes', {
      method: 'POST',
      data: nodePayload
    })
    expect(request).toHaveBeenNthCalledWith(4, '/api/dict/process-arrangement/nodes/7', {
      method: 'PUT',
      data: nodePayload
    })
    expect(request).toHaveBeenNthCalledWith(5, '/api/dict/process-arrangement/nodes/7', {
      method: 'DELETE'
    })
    expect(request).toHaveBeenNthCalledWith(6, '/api/dict/process-arrangement/items/batch', {
      method: 'POST',
      data: itemPayload
    })
    expect(request).toHaveBeenNthCalledWith(7, '/api/dict/process-arrangement/items/11/move', {
      method: 'PUT',
      data: { parentNodeId: null, sortNo: 3 }
    })
    expect(request).toHaveBeenNthCalledWith(8, '/api/dict/process-arrangement/items/11', {
      method: 'DELETE'
    })
  })

  test('manages unified pricing markup fees with categorized endpoints', async () => {
    const payload = {
      id: 11,
      pricingScope: 'ASSEMBLY',
      feeCategory: 'BASIC_PERCENT',
      feeName: '组装管理费',
      ratePercent: 5,
      enabled: true
    }

    await dictApi.listPricingMarkupFees('ASSEMBLY', 'BASIC_PERCENT')
    await dictApi.createPricingMarkupFee(payload)
    await dictApi.updatePricingMarkupFee(11, payload)
    await dictApi.deletePricingMarkupFee(11)

    expect(request).toHaveBeenNthCalledWith(1, '/api/dict/pricing-markup-fees?pricingScope=ASSEMBLY&feeCategory=BASIC_PERCENT')
    expect(request).toHaveBeenNthCalledWith(2, '/api/dict/pricing-markup-fees', {
      method: 'POST',
      data: payload
    })
    expect(request).toHaveBeenNthCalledWith(3, '/api/dict/pricing-markup-fees/11', {
      method: 'PUT',
      data: payload
    })
    expect(request).toHaveBeenNthCalledWith(4, '/api/dict/pricing-markup-fees/11', {
      method: 'DELETE'
    })
  })
})
