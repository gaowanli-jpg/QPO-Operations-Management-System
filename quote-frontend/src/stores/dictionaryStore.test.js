import { beforeEach, describe, expect, test, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { listExtraFeeItems, listMarkupFees, listMaterials, listProcesses } from '../services/dictApi'
import { useDictionaryStore } from './dictionaryStore'

vi.mock('../services/dictApi', () => ({
  listMaterials: vi.fn(),
  listProcesses: vi.fn(),
  listExtraFeeItems: vi.fn(),
  listMarkupFees: vi.fn()
}))

beforeEach(() => {
  vi.clearAllMocks()
  setActivePinia(createPinia())
})

describe('dictionaryStore', () => {
  test('loadBaseDicts loads materials, processes, extra fees, and markup fees when requested', async () => {
    listMaterials.mockResolvedValue([{ id: 1, materialName: '45#' }])
    listProcesses.mockResolvedValue([{ id: 2, processName: 'CNC1', calcType: '工时' }])
    listExtraFeeItems.mockResolvedValue([{ id: 3, feeName: 'Heat Treat' }])
    listMarkupFees.mockResolvedValue([{ id: 4, feeName: '利润', ratePercent: 15 }])
    const store = useDictionaryStore()

    await store.loadBaseDicts({ includeMarkupFees: true, force: true })

    expect(store.materials).toHaveLength(1)
    expect(store.processes).toHaveLength(1)
    expect(store.extraFeeItems).toHaveLength(1)
    expect(store.markupFees).toHaveLength(1)
    expect(listExtraFeeItems).toHaveBeenCalledWith({ includeDisabled: false })
  })

  test('combinedProcessOptions combines process and extra fee records', async () => {
    listMaterials.mockResolvedValue([])
    listProcesses.mockResolvedValue([{ id: 2, processName: 'CNC1', calcType: '工时' }])
    listExtraFeeItems.mockResolvedValue([{ id: 3, feeName: 'Heat Treat' }])
    listMarkupFees.mockResolvedValue([])
    const store = useDictionaryStore()

    await store.loadBaseDicts({ force: true })

    expect(store.combinedProcessOptions).toEqual([
      expect.objectContaining({ optionKey: 'process-2', processName: 'CNC1', sourceType: 'process' }),
      expect.objectContaining({ optionKey: 'extra-3', processName: 'Heat Treat', sourceType: 'extra' })
    ])
  })

  test('applyPricingFallbackData preserves ItemPricing fallback strategy', () => {
    const store = useDictionaryStore()

    store.applyPricingFallbackData()

    expect(store.materials[0].materialName).toBe('45#')
    expect(store.markupFees.map(item => item.feeName)).toEqual(['运输费', '管理费', '利润', '税费'])
  })
})
