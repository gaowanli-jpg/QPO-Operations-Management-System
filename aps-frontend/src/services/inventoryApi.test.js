import { beforeEach, describe, expect, test, vi } from 'vitest'
import { downloadFile, request } from '../api'
import * as inventoryApi from './inventoryApi'

vi.mock('../api', () => ({
  downloadFile: vi.fn(),
  request: vi.fn()
}))

beforeEach(() => {
  downloadFile.mockReset()
  request.mockReset()
})

describe('inventoryApi', () => {
  test('listMaterials applies default limit', async () => {
    await inventoryApi.listMaterials()

    expect(request).toHaveBeenCalledWith('/api/aps/inventory/materials?limit=200')
  })

  test('createMaterial posts material payload', async () => {
    const payload = { materialName: '45#', qty: 10 }

    await inventoryApi.createMaterial(payload)

    expect(request).toHaveBeenCalledWith('/api/aps/inventory/materials', {
      method: 'POST',
      data: payload
    })
  })

  test('material lock actions preserve endpoints', async () => {
    await inventoryApi.confirmMaterialLock(7)
    await inventoryApi.releaseMaterialLock(8)

    expect(request).toHaveBeenNthCalledWith(1, '/api/aps/inventory/materials/locks/7/confirm', {
      method: 'POST'
    })
    expect(request).toHaveBeenNthCalledWith(2, '/api/aps/inventory/materials/locks/8/release', {
      method: 'POST'
    })
  })

  test('part inventory mutation endpoints post payloads', async () => {
    const addPayload = { partId: 1, qty: 3 }
    const transferPayload = { pendingId: 2, qty: 1 }
    const modifyPayload = { partId: 3, qty: 9 }

    await inventoryApi.addPartStock(addPayload)
    await inventoryApi.transferPartPending(transferPayload)
    await inventoryApi.modifyPartInventory(modifyPayload)

    expect(request).toHaveBeenNthCalledWith(1, '/api/aps/inventory/parts/add-stock', {
      method: 'POST',
      data: addPayload
    })
    expect(request).toHaveBeenNthCalledWith(2, '/api/aps/inventory/parts/transfer-pending', {
      method: 'POST',
      data: transferPayload
    })
    expect(request).toHaveBeenNthCalledWith(3, '/api/aps/inventory/parts/modify', {
      method: 'POST',
      data: modifyPayload
    })
  })

  test('part lock actions preserve endpoints', async () => {
    await inventoryApi.confirmPartLock(7)
    await inventoryApi.releasePartLock(8)

    expect(request).toHaveBeenNthCalledWith(1, '/api/aps/inventory/parts/locks/7/confirm', {
      method: 'POST'
    })
    expect(request).toHaveBeenNthCalledWith(2, '/api/aps/inventory/parts/locks/8/release', {
      method: 'POST'
    })
  })

  test('exportPartLocks delegates to download helper with generated query string', async () => {
    await inventoryApi.exportPartLocks({ limit: '200', customerName: 'ACME' })

    expect(downloadFile).toHaveBeenCalledWith(
      '/api/aps/inventory/parts/locks/export?limit=200&customerName=ACME',
      '零件锁定记录.xlsx'
    )
  })
})
