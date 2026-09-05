import { describe, expect, test } from 'vitest'
import {
  buildPricingOptionIndex,
  hydrateMaterialPricingSnapshot,
  hydrateProcessPricingSnapshot,
  hydrateProcessRoutePricingSnapshots
} from './pricingSnapshotHydrator'

describe('pricingSnapshotHydrator', () => {
  test('hydrates process snapshots from latest options by processDetailId', () => {
    const optionIndex = buildPricingOptionIndex({
      processOptions: [{
        processDetailId: 3,
        processCategoryId: 9,
        processCode: 'MTA_PICK',
        categoryName: '下料',
        detailName: '激光下料',
        detailHourlyRate: 200,
        categoryHourlyRate: 80,
        effectiveHourlyRate: 200,
        hourlyRate: 200,
        label: '激光下料（MTA_PICK / 下料 / 200.00元/小时）'
      }]
    })

    const hydrated = hydrateProcessPricingSnapshot({
      processDetailId: 3,
      processCategoryId: 1,
      processCode: 'OLD',
      categoryName: '旧下料',
      detailName: '下料',
      hourlyRate: 0,
      effectiveHourlyRate: 0,
      unitRate: 0,
      inputVal: 0.5,
      sortNo: 20
    }, optionIndex)

    expect(hydrated).toEqual(expect.objectContaining({
      processDetailId: 3,
      processCategoryId: 9,
      processCode: 'MTA_PICK',
      categoryName: '下料',
      detailName: '激光下料',
      processName: '激光下料',
      detailHourlyRate: 200,
      categoryHourlyRate: 80,
      effectiveHourlyRate: 200,
      hourlyRate: 200,
      unitRate: 200,
      inputVal: 0.5,
      sortNo: 20
    }))
  })

  test('hydrates material snapshots from latest options by materialCode without losing dimensions', () => {
    const optionIndex = buildPricingOptionIndex({
      materialOptions: [{
        id: 7,
        materialCode: 'MAT-45',
        materialName: '45# 钢',
        materialTypeId: 2,
        materialTypeCode: 'RAW_MATERIAL',
        materialTypeName: '原材料',
        materialCategory: '碳钢',
        density: null,
        mesDensityRaw: '7850',
        priceSpecs: [
          { specCode: 'SPEC_1', unifiedPrice: 6.5 },
          { specCode: 'SPEC_2', unifiedPrice: 7.0, barPrice: 7.5, tubePrice: 8.5 }
        ],
        remarks: 'MES 同步'
      }]
    })

    const hydrated = hydrateMaterialPricingSnapshot({
      materialCode: 'mat-45',
      materialName: '旧45#',
      density: 0,
      platePrice: 0,
      barPrice: 0,
      tubePrice: 0,
      priceSpecCode: 'SPEC_2',
      shape: '棒材',
      dimensions: { L: 0.2, outR: 0.03 }
    }, optionIndex)

    expect(hydrated).toEqual(expect.objectContaining({
      materialId: 7,
      materialCode: 'MAT-45',
      materialName: '45# 钢',
      materialTypeId: 2,
      materialTypeCode: 'RAW_MATERIAL',
      materialTypeName: '原材料',
      materialCategory: '碳钢',
      density: 7850,
      priceSpecCode: 'SPEC_2',
      priceSpecName: '规格二',
      remarks: 'MES 同步',
      shape: '棒材',
      dimensions: { L: 0.2, outR: 0.03 }
    }))
    expect(hydrated.priceSpec).toEqual(expect.objectContaining({
      specCode: 'SPEC_2',
      unifiedPrice: 7,
      barPrice: 7.5,
      tubePrice: 8.5
    }))
    expect(hydrated.priceSpecs).toHaveLength(3)
    expect(hydrated).not.toHaveProperty('platePrice')
    expect(hydrated).not.toHaveProperty('barPrice')
    expect(hydrated).not.toHaveProperty('tubePrice')
  })

  test('treats zero material density as maintained instead of falling back to MES density', () => {
    const optionIndex = buildPricingOptionIndex({
      materialOptions: [{
        id: 8,
        materialCode: 'MAT-AL',
        materialName: '铝材',
        density: 0,
        mesDensityRaw: '2700',
        priceSpecs: [{ specCode: 'SPEC_1', unifiedPrice: 16 }]
      }]
    })

    expect(hydrateMaterialPricingSnapshot({ materialCode: 'MAT-AL' }, optionIndex).density).toBe(0)
  })

  test('leaves unresolved or extra-fee process rows unchanged', () => {
    const rows = hydrateProcessRoutePricingSnapshots([
      { sourceType: 'extra', processName: '喷砂', inputVal: 12 },
      { sourceType: 'process', processName: '旧模板工艺', inputVal: 1 }
    ], buildPricingOptionIndex())

    expect(rows).toEqual([
      { sourceType: 'extra', processName: '喷砂', inputVal: 12 },
      { sourceType: 'process', processName: '旧模板工艺', inputVal: 1 }
    ])
  })
})
