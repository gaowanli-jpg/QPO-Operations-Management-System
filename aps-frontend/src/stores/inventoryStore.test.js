import { beforeEach, describe, expect, test, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import {
  addPartStock,
  confirmMaterialLock,
  confirmPartLock,
  createMaterial,
  createPartInventory,
  deleteMaterial,
  deletePartInventory,
  listMaterialLockCustomers,
  listMaterialLocks,
  listMaterialShortages,
  listMaterials,
  listPartCustomers,
  listPartInventoryPage,
  listPartLocks,
  listPartPending,
  listPartProductionRequirements,
  modifyPartInventory,
  releaseMaterialLock,
  releasePartLock,
  transferPartPending
} from '../services/inventoryApi'
import { useInventoryStore } from './inventoryStore'

vi.mock('../services/inventoryApi', () => ({
  addPartStock: vi.fn(),
  confirmMaterialLock: vi.fn(),
  confirmPartLock: vi.fn(),
  createMaterial: vi.fn(),
  createPartInventory: vi.fn(),
  deleteMaterial: vi.fn(),
  deletePartInventory: vi.fn(),
  listMaterialLockCustomers: vi.fn(),
  listMaterialLocks: vi.fn(),
  listMaterialShortages: vi.fn(),
  listMaterials: vi.fn(),
  listPartCustomers: vi.fn(),
  listPartInventoryPage: vi.fn(),
  listPartLocks: vi.fn(),
  listPartPending: vi.fn(),
  listPartProductionRequirements: vi.fn(),
  modifyPartInventory: vi.fn(),
  releaseMaterialLock: vi.fn(),
  releasePartLock: vi.fn(),
  transferPartPending: vi.fn()
}))

const queryObject = (call) => Object.fromEntries(call[0])

beforeEach(() => {
  vi.clearAllMocks()
  setActivePinia(createPinia())
})

describe('inventoryStore', () => {
  test('loads part customers and paged part inventory with filters', async () => {
    listPartCustomers.mockResolvedValue(['ACME'])
    listPartInventoryPage.mockResolvedValue({
      records: [{ id: 1, part_no: 'P-100' }],
      total: 1
    })
    const inventoryStore = useInventoryStore()

    await inventoryStore.loadPartCustomers()
    await inventoryStore.loadPartInventoryPage({ customerName: 'ACME', keyword: 'P-100' })

    expect(inventoryStore.partCustomers).toEqual(['ACME'])
    expect(queryObject(listPartInventoryPage.mock.calls[0])).toEqual({
      customerName: 'ACME',
      keyword: 'P-100',
      page: '1',
      pageSize: '20'
    })
    expect(inventoryStore.partInventory).toEqual([{ id: 1, part_no: 'P-100' }])
    expect(inventoryStore.partTotal).toBe(1)
  })

  test('part inventory create and delete refresh customers and current page', async () => {
    createPartInventory.mockResolvedValue({ success: true })
    deletePartInventory.mockResolvedValue({ success: true })
    listPartCustomers.mockResolvedValue(['ACME'])
    listPartInventoryPage.mockResolvedValue({ records: [], total: 0 })
    const inventoryStore = useInventoryStore()
    inventoryStore.partInventoryFilters = { customerName: 'ACME' }

    await inventoryStore.createPartInventory({ customerName: 'ACME', partNo: 'P-100' })
    await inventoryStore.deletePartInventory(1)

    expect(createPartInventory).toHaveBeenCalledWith({ customerName: 'ACME', partNo: 'P-100' })
    expect(deletePartInventory).toHaveBeenCalledWith(1)
    expect(listPartInventoryPage).toHaveBeenCalledTimes(2)
    expect(listPartCustomers).toHaveBeenCalledTimes(2)
  })

  test('part stock mutations refresh active inventory and pending rows', async () => {
    addPartStock.mockResolvedValue({ success: true })
    transferPartPending.mockResolvedValue({ success: true })
    modifyPartInventory.mockResolvedValue({ success: true })
    listPartInventoryPage.mockResolvedValue({
      records: [{ id: 7, part_no: 'P-700', in_stock_qty: 4 }],
      total: 1
    })
    listPartPending.mockResolvedValue([{ pending_ref_no: 'WO-1', pending_qty: 2 }])
    const inventoryStore = useInventoryStore()
    inventoryStore.partInventoryFilters = { customerName: 'ACME' }
    inventoryStore.setActivePartRow({ id: 7, part_no: 'P-700' })

    await inventoryStore.addPartStock({ inventoryId: 7, qty: 1 })
    await inventoryStore.transferPartPending({ inventoryId: 7, pendingRefNo: 'WO-1', qty: 1 })
    await inventoryStore.modifyPartInventory({ inventoryId: 7, inStockQty: 5 })

    expect(addPartStock).toHaveBeenCalledWith({ inventoryId: 7, qty: 1 })
    expect(transferPartPending).toHaveBeenCalledWith({ inventoryId: 7, pendingRefNo: 'WO-1', qty: 1 })
    expect(modifyPartInventory).toHaveBeenCalledWith({ inventoryId: 7, inStockQty: 5 })
    expect(inventoryStore.activePartRow).toEqual({ id: 7, part_no: 'P-700', in_stock_qty: 4 })
    expect(inventoryStore.partPendingRows).toEqual([{ pending_ref_no: 'WO-1', pending_qty: 2 }])
  })

  test('part lock actions refresh the current lock list', async () => {
    listPartLocks.mockResolvedValue([{ id: 1 }])
    confirmPartLock.mockResolvedValue({ success: true })
    releasePartLock.mockResolvedValue({ success: true })
    const inventoryStore = useInventoryStore()

    await inventoryStore.loadPartLocks({ customerName: 'ACME' })
    await inventoryStore.confirmPartLock(1)
    await inventoryStore.releasePartLock(2)

    expect(queryObject(listPartLocks.mock.calls[0])).toEqual({ limit: '200', customerName: 'ACME' })
    expect(confirmPartLock).toHaveBeenCalledWith(1)
    expect(releasePartLock).toHaveBeenCalledWith(2)
    expect(listPartLocks).toHaveBeenCalledTimes(3)
  })

  test('loads part production requirements with filters', async () => {
    listPartProductionRequirements.mockResolvedValue([{ id: 9, status: 'PENDING_PRODUCTION' }])
    const inventoryStore = useInventoryStore()

    await inventoryStore.loadPartProductionRequirements({ status: 'PENDING_PRODUCTION' })

    expect(listPartProductionRequirements).toHaveBeenCalledWith({
      limit: '200',
      status: 'PENDING_PRODUCTION'
    })
    expect(inventoryStore.partProductionRequirements).toEqual([{ id: 9, status: 'PENDING_PRODUCTION' }])
  })

  test('material inventory create and delete refresh material list', async () => {
    listMaterials.mockResolvedValue([{ id: 1, material_name: '45#' }])
    createMaterial.mockResolvedValue({ success: true })
    deleteMaterial.mockResolvedValue({ success: true })
    const inventoryStore = useInventoryStore()

    await inventoryStore.loadMaterials()
    await inventoryStore.createMaterial({ materialName: '45#' })
    await inventoryStore.deleteMaterial(1)

    expect(listMaterials).toHaveBeenNthCalledWith(1, { limit: '200' })
    expect(createMaterial).toHaveBeenCalledWith({ materialName: '45#' })
    expect(deleteMaterial).toHaveBeenCalledWith(1)
    expect(listMaterials).toHaveBeenCalledTimes(3)
    expect(inventoryStore.materials).toEqual([{ id: 1, material_name: '45#' }])
  })

  test('material lock actions refresh the current lock list', async () => {
    listMaterialLockCustomers.mockResolvedValue(['ACME'])
    listMaterialLocks.mockResolvedValue([{ id: 4 }])
    confirmMaterialLock.mockResolvedValue({ success: true })
    releaseMaterialLock.mockResolvedValue({ success: true })
    const inventoryStore = useInventoryStore()

    await inventoryStore.loadMaterialLockCustomers()
    await inventoryStore.loadMaterialLocks({ customerName: 'ACME' })
    await inventoryStore.confirmMaterialLock(4)
    await inventoryStore.releaseMaterialLock(5)

    expect(inventoryStore.materialLockCustomers).toEqual(['ACME'])
    expect(queryObject(listMaterialLocks.mock.calls[0])).toEqual({ limit: '200', customerName: 'ACME' })
    expect(confirmMaterialLock).toHaveBeenCalledWith(4)
    expect(releaseMaterialLock).toHaveBeenCalledWith(5)
    expect(listMaterialLocks).toHaveBeenCalledTimes(3)
  })

  test('loads material shortages with the default limit', async () => {
    listMaterialShortages.mockResolvedValue([{ id: 6 }])
    const inventoryStore = useInventoryStore()

    await inventoryStore.loadMaterialShortages()

    expect(listMaterialShortages).toHaveBeenCalledWith({ limit: '200' })
    expect(inventoryStore.materialShortages).toEqual([{ id: 6 }])
  })
})
