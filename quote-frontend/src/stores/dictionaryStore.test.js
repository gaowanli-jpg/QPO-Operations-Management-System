import { beforeEach, describe, expect, test, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import {
  listExtraFeeItems,
  listMaterialTypes,
  listMaterials,
  listPricingMarkupFees,
  listProcessCategories,
  listProcessDetails
} from '../services/dictApi'
import { useDictionaryStore } from './dictionaryStore'

vi.mock('../services/dictApi', () => ({
  listMaterials: vi.fn(),
  listMaterialTypes: vi.fn(),
  listProcessCategories: vi.fn(),
  listProcessDetails: vi.fn(),
  listExtraFeeItems: vi.fn(),
  listPricingMarkupFees: vi.fn()
}))

beforeEach(() => {
  vi.clearAllMocks()
  setActivePinia(createPinia())
})

describe('dictionaryStore', () => {
  test('loadBaseDicts loads materials, processes, extra fees, and categorized pricing markup fees when requested', async () => {
    listMaterialTypes.mockResolvedValue([{ id: 6, typeCode: 'RAW_MATERIAL', typeName: '原材料' }])
    listMaterials.mockResolvedValue([{ id: 1, materialCode: 'MAT-000001', materialName: '45#', materialTypeId: 6, materialTypeCode: 'RAW_MATERIAL', materialTypeName: '原材料' }])
    listProcessCategories.mockResolvedValue([{ id: 10, processCode: 'PACKAGING', categoryName: '包装', hourlyRate: 10 }])
    listProcessDetails.mockResolvedValue([{ id: 2, categoryId: null, processCode: 'PACK_FINE', categoryName: null, detailName: '精细包装', hourlyRate: 10 }])
    listExtraFeeItems.mockResolvedValue([{ id: 3, feeName: 'Heat Treat' }])
    listPricingMarkupFees.mockImplementation((pricingScope, feeCategory) => Promise.resolve([
      {
        id: `${pricingScope}-${feeCategory}`,
        pricingScope,
        feeCategory,
        feeName: `${pricingScope}-${feeCategory}`,
        ratePercent: feeCategory === 'TAX' ? 13 : 0,
        systemRequired: feeCategory !== 'BASIC_PERCENT',
        enabled: true
      }
    ]))
    const store = useDictionaryStore()

    await store.loadBaseDicts({ includePricingMarkupFees: true, force: true })

    expect(store.materialTypes).toHaveLength(1)
    expect(store.materials).toHaveLength(1)
    expect(store.materials[0]).toEqual(expect.objectContaining({ materialCode: 'MAT-000001', materialTypeName: '原材料' }))
    expect(store.processes).toHaveLength(1)
    expect(store.processCategories).toHaveLength(1)
    expect(store.processDetails).toHaveLength(1)
    expect(store.processes[0]).toEqual(expect.objectContaining({ processName: '精细包装', processDetailId: 2, processCode: 'PACK_FINE' }))
    expect(store.extraFeeItems).toHaveLength(1)
    expect(store.pricingMarkupFees).toHaveLength(6)
    expect(store.getPricingMarkupFees('NORMAL', 'TAX')).toEqual([
      expect.objectContaining({ pricingScope: 'NORMAL', feeCategory: 'TAX', systemRequired: true })
    ])
    expect(store.getPricingMarkupFees('ASSEMBLY', 'BASIC_PERCENT')).toEqual([
      expect.objectContaining({ pricingScope: 'ASSEMBLY', feeCategory: 'BASIC_PERCENT' })
    ])
    expect(listExtraFeeItems).toHaveBeenCalledWith({ includeDisabled: false })
    expect(listMaterialTypes).toHaveBeenCalledWith()
    expect(listPricingMarkupFees).toHaveBeenCalledTimes(6)
    expect(listPricingMarkupFees).toHaveBeenCalledWith('NORMAL', 'BASIC_PERCENT')
    expect(listPricingMarkupFees).toHaveBeenCalledWith('ASSEMBLY', 'CUSTOMER_WEIGHT')
  })

  test('combinedProcessOptions combines process and extra fee records', async () => {
    listMaterialTypes.mockResolvedValue([])
    listMaterials.mockResolvedValue([])
    listProcessCategories.mockResolvedValue([{ id: 10, processCode: 'CNC', categoryName: '数控', hourlyRate: 80 }])
    listProcessDetails.mockResolvedValue([
      { id: 2, categoryId: null, processCode: 'CNC1', categoryName: null, detailName: 'CNC1', detailHourlyRate: 0, categoryHourlyRate: null, effectiveHourlyRate: 0, hourlyRate: 0 }
    ])
    listExtraFeeItems.mockResolvedValue([{ id: 3, feeName: 'Heat Treat' }])
    const store = useDictionaryStore()

    await store.loadBaseDicts({ force: true })

    expect(store.combinedProcessOptions).toEqual([
      expect.objectContaining({ optionKey: 'process:2', processDetailId: 2, processCode: 'CNC1', categoryName: null, detailName: 'CNC1', processName: 'CNC1', hourlyRate: 0, sourceType: 'process' }),
      expect.objectContaining({ optionKey: 'extra-3', processName: 'Heat Treat', sourceType: 'extra' })
    ])
    expect(store.combinedProcessOptions[0].label).toContain('0.00元/小时')
    expect(store.combinedProcessOptions[0].label).toContain('CNC1')
    expect(store.combinedProcessOptions[0].label).not.toContain('具体价')
    expect(store.combinedProcessOptions[0].label).not.toContain('继承大类')
    expect(store.combinedProcessOptions[0].label).not.toContain('80.00元/小时')
  })

  test('combinedProcessOptions keeps enabled process details even when old category flags are disabled', async () => {
    listMaterialTypes.mockResolvedValue([])
    listMaterials.mockResolvedValue([])
    listProcessCategories.mockResolvedValue([])
    listProcessDetails.mockResolvedValue([
      { id: 2, categoryId: 10, processCode: 'CNC1', categoryName: 'CNC', detailName: 'CNC1', enabled: true, detailEnabled: true, categoryEnabled: false, effectiveEnabled: false, hourlyRate: 80 },
      { id: 3, categoryId: 11, processCode: 'MILL1', categoryName: 'Milling', detailName: 'MILL1', enabled: false, detailEnabled: false, categoryEnabled: true, effectiveEnabled: true, hourlyRate: 60 }
    ])
    listExtraFeeItems.mockResolvedValue([])
    const store = useDictionaryStore()

    await store.loadBaseDicts({ force: true })

    expect(store.combinedProcessOptions.map(item => item.processDetailId)).toEqual([2])
    expect(store.combinedProcessOptions[0]).toEqual(expect.objectContaining({
      processDetailId: 2,
      detailEnabled: true,
      categoryEnabled: null,
      effectiveEnabled: true
    }))
  })

  test('loadPricingFeeDicts loads only pricing fee dictionaries', async () => {
    listPricingMarkupFees.mockImplementation((pricingScope, feeCategory) => Promise.resolve([{
      id: `${pricingScope}-${feeCategory}`,
      pricingScope,
      feeCategory,
      feeName: `${pricingScope}-${feeCategory}`,
      ratePercent: feeCategory === 'TAX' ? 13 : 0,
      enabled: true
    }]))
    const store = useDictionaryStore()

    await store.loadPricingFeeDicts({ force: true })

    expect(store.pricingMarkupFees).toHaveLength(6)
    expect(store.getPricingMarkupFees('NORMAL', 'TAX')).toEqual([
      expect.objectContaining({ pricingScope: 'NORMAL', feeCategory: 'TAX' })
    ])
    expect(listPricingMarkupFees).toHaveBeenCalledTimes(6)
    expect(listMaterialTypes).not.toHaveBeenCalled()
    expect(listMaterials).not.toHaveBeenCalled()
    expect(listProcessCategories).not.toHaveBeenCalled()
    expect(listProcessDetails).not.toHaveBeenCalled()
    expect(listExtraFeeItems).not.toHaveBeenCalled()
  })

  test('loadPricingFeeDicts does not make base dictionary cache look loaded', async () => {
    listPricingMarkupFees.mockImplementation((pricingScope, feeCategory) => Promise.resolve([{
      id: `${pricingScope}-${feeCategory}`,
      pricingScope,
      feeCategory,
      feeName: `${pricingScope}-${feeCategory}`,
      ratePercent: 0,
      enabled: true
    }]))
    listMaterialTypes.mockResolvedValue([{ id: 6, typeCode: 'RAW_MATERIAL', typeName: '原材料' }])
    listMaterials.mockResolvedValue([{ id: 1, materialCode: 'MAT-000001', materialName: '45#' }])
    listProcessCategories.mockResolvedValue([{ id: 10, processCode: 'CNC', categoryName: '数控', hourlyRate: 80 }])
    listProcessDetails.mockResolvedValue([{ id: 2, categoryId: 10, detailName: 'CNC1', hourlyRate: 80 }])
    listExtraFeeItems.mockResolvedValue([{ id: 3, feeName: '喷砂' }])
    const store = useDictionaryStore()

    await store.loadPricingFeeDicts({ force: true })
    vi.clearAllMocks()
    await store.loadBaseDicts()

    expect(listMaterialTypes).toHaveBeenCalledTimes(1)
    expect(listMaterials).toHaveBeenCalledTimes(1)
    expect(listProcessCategories).toHaveBeenCalledTimes(1)
    expect(listProcessDetails).toHaveBeenCalledTimes(1)
    expect(listExtraFeeItems).toHaveBeenCalledWith({ includeDisabled: false })
    expect(listPricingMarkupFees).not.toHaveBeenCalled()
    expect(store.materials).toEqual([expect.objectContaining({ materialCode: 'MAT-000001' })])
    expect(store.processDetails).toEqual([expect.objectContaining({ detailName: 'CNC1' })])
  })

  test('applyPricingFallbackData preserves ItemPricing fallback strategy', () => {
    const store = useDictionaryStore()

    store.applyPricingFallbackData()

    expect(store.materialTypes[0]).toEqual(expect.objectContaining({ typeCode: 'RAW_MATERIAL', typeName: '原材料' }))
    expect(store.materials[0]).toEqual(expect.objectContaining({ materialCode: 'MAT-FALLBACK-45', materialTypeCode: 'RAW_MATERIAL', materialTypeName: '原材料', materialCategory: '钢铁' }))
    expect(store.materials[0].materialName).toBe('45#')
    expect(store.getPricingMarkupFees('NORMAL').map(item => item.feeName)).toEqual(['运输费', '管理费', '利润', '税率', '客户权重'])
    expect(store.getPricingMarkupFees('ASSEMBLY').map(item => item.feeName)).toEqual(['组装管理费', '组装利润', '税率', '客户权重'])
  })

  test('upsertPricingMarkupFee replaces the saved row so active pricing dialogs recalculate immediately', () => {
    const store = useDictionaryStore()
    store.pricingMarkupFees = [
      { id: 7, pricingScope: 'NORMAL', feeCategory: 'CUSTOMER_WEIGHT', feeName: '客户权重', ratePercent: 0, enabled: false },
      { id: 8, pricingScope: 'NORMAL', feeCategory: 'BASIC_PERCENT', feeName: '利润', ratePercent: 10, enabled: true }
    ]

    store.upsertPricingMarkupFee({
      id: 7,
      pricingScope: 'NORMAL',
      feeCategory: 'CUSTOMER_WEIGHT',
      feeName: '客户权重',
      ratePercent: 20,
      enabled: true
    })

    expect(store.getPricingMarkupFees('NORMAL', 'CUSTOMER_WEIGHT')).toEqual([
      expect.objectContaining({ id: 7, ratePercent: 20, enabled: true, systemRequired: false })
    ])
    expect(store.getPricingMarkupFees('NORMAL')).toHaveLength(2)
  })
})
