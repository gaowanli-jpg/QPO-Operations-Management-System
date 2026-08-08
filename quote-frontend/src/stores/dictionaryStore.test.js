import { beforeEach, describe, expect, test, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import {
  listAssemblyMarkupFees,
  listExtraFeeItems,
  listMarkupFees,
  listMaterials,
  listProcessCategories,
  listProcessDetails
} from '../services/dictApi'
import { useDictionaryStore } from './dictionaryStore'

vi.mock('../services/dictApi', () => ({
  listMaterials: vi.fn(),
  listProcessCategories: vi.fn(),
  listProcessDetails: vi.fn(),
  listExtraFeeItems: vi.fn(),
  listMarkupFees: vi.fn(),
  listAssemblyMarkupFees: vi.fn()
}))

beforeEach(() => {
  vi.clearAllMocks()
  setActivePinia(createPinia())
})

describe('dictionaryStore', () => {
  test('loadBaseDicts loads materials, processes, extra fees, and markup fees when requested', async () => {
    listMaterials.mockResolvedValue([{ id: 1, materialName: '45#' }])
    listProcessCategories.mockResolvedValue([{ id: 10, processCode: 'PACKAGING', categoryName: '包装', hourlyRate: 10 }])
    listProcessDetails.mockResolvedValue([{ id: 2, categoryId: 10, processCode: 'PACKAGING', categoryName: '包装', detailName: '精细包装', hourlyRate: 10 }])
    listExtraFeeItems.mockResolvedValue([{ id: 3, feeName: 'Heat Treat' }])
    listMarkupFees.mockResolvedValue([{ id: 4, feeName: '利润', ratePercent: 15 }])
    listAssemblyMarkupFees.mockResolvedValue([{ id: 5, feeName: '组装管理费', ratePercent: 5 }])
    const store = useDictionaryStore()

    await store.loadBaseDicts({ includeMarkupFees: true, force: true })

    expect(store.materials).toHaveLength(1)
    expect(store.processes).toHaveLength(1)
    expect(store.processCategories).toHaveLength(1)
    expect(store.processDetails).toHaveLength(1)
    expect(store.processes[0]).toEqual(expect.objectContaining({ processName: '精细包装', processDetailId: 2, processCode: 'PACKAGING' }))
    expect(store.extraFeeItems).toHaveLength(1)
    expect(store.markupFees).toHaveLength(1)
    expect(store.assemblyMarkupFees).toHaveLength(1)
    expect(listExtraFeeItems).toHaveBeenCalledWith({ includeDisabled: false })
    expect(listAssemblyMarkupFees).toHaveBeenCalled()
  })

  test('combinedProcessOptions combines process and extra fee records', async () => {
    listMaterials.mockResolvedValue([])
    listProcessCategories.mockResolvedValue([{ id: 10, processCode: 'PACKAGING', categoryName: '包装', hourlyRate: 10 }])
    listProcessDetails.mockResolvedValue([{ id: 2, categoryId: 10, processCode: 'PACKAGING', categoryName: '包装', detailName: '精细包装', hourlyRate: 10 }])
    listExtraFeeItems.mockResolvedValue([{ id: 3, feeName: 'Heat Treat' }])
    listMarkupFees.mockResolvedValue([])
    listAssemblyMarkupFees.mockResolvedValue([])
    const store = useDictionaryStore()

    await store.loadBaseDicts({ force: true })

    expect(store.combinedProcessOptions).toEqual([
      expect.objectContaining({ optionKey: 'process-detail-2', processDetailId: 2, processCode: 'PACKAGING', categoryName: '包装', detailName: '精细包装', processName: '精细包装', hourlyRate: 10, sourceType: 'process' }),
      expect.objectContaining({ optionKey: 'extra-3', processName: 'Heat Treat', sourceType: 'extra' })
    ])
  })

  test('applyPricingFallbackData preserves ItemPricing fallback strategy', () => {
    const store = useDictionaryStore()

    store.applyPricingFallbackData()

    expect(store.materials[0].materialName).toBe('45#')
    expect(store.markupFees.map(item => item.feeName)).toEqual(['运输费', '管理费', '利润', '税费'])
    expect(store.assemblyMarkupFees.map(item => item.feeName)).toEqual(['组装管理费', '组装利润', '组装税费'])
  })
})
