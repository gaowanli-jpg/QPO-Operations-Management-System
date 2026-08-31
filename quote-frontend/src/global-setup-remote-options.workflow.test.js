import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { normalizeGlobalConfig, normalizeRouteList } from './stores/globalConfigStore'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

describe('global setup remote material and process options', () => {
  test('global setup wires material and process pickers to remote option APIs', () => {
    const source = readSource('components', 'GlobalSetup.vue')

    expect(source).toContain('pagePricingMaterialOptions')
    expect(source).toContain('pagePricingProcessOptions')
    expect(source).toContain(':remote-loader="pagePricingMaterialOptions"')
    expect(source).toContain(':remote-loader="pagePricingProcessOptions"')
    expect(source).not.toContain(':items="materialOptions"')
    expect(source).not.toContain(':items="combinedProcessOptions"')
  })

  test('global setup uses the same enhanced option picker mode as item pricing', () => {
    const source = readSource('components', 'GlobalSetup.vue')

    expect(source).toContain(':page-size-options="[10, 20, 50, 100]"')
    expect(source).toContain(':default-page-size="10"')
    expect(source).toContain(':enable-material-price-edit="true"')
    expect(source).toContain('@material-price-updated="handleMaterialPriceUpdated"')
    expect(source).toContain(':enable-process-rate-edit="true"')
    expect(source).toContain('@process-rate-updated="handleProcessRateUpdated"')
    expect(source).toContain('const handleMaterialPriceUpdated = (updatedMaterial) => {')
    expect(source).toContain("applyMaterialSelectionToConfig(buildMaterialSpecSelection(updatedMaterial, config.priceSpecCode || 'SPEC_1'))")
    expect(source).toContain('const handleProcessRateUpdated = (updatedDetail) => {')
    expect(source).toContain('syncCurrentSelectedProcessRate(updatedDetail)')
  })

  test('global setup startup does not load full material or process dictionaries', () => {
    const source = readSource('components', 'GlobalSetup.vue')

    expect(source).not.toContain('dictionaryStore.loadBaseDicts')
    expect(source).not.toContain('dictionaryStore.materials')
    expect(source).not.toContain('dictionaryStore.processes')
    expect(source).not.toContain('dictionaryStore.combinedProcessOptions')
  })

  test('global setup allows blank material and blank process placeholders before entering item pricing', () => {
    const source = readSource('components', 'GlobalSetup.vue')

    expect(source).toContain('const hasUnresolvedMaterialIdentity = computed(() => hasLegacyMaterialIdentity(config))')
    expect(source).not.toContain('const hasUnresolvedMaterialIdentity = computed(() => !config.materialCode || config.legacyMaterialIdentity)')
    expect(source).toContain('isBlankProcessRoute(proc)')
    expect(source).toContain('persistableProcessRouteList(config.processRoute)')
  })

  test('global config normalization preserves selected material and process snapshots', () => {
    const normalized = normalizeGlobalConfig({
      materialId: 12,
      materialCode: 'MAT-45',
      materialName: '45#',
      materialTypeId: 3,
      materialTypeCode: 'RAW',
      materialTypeName: '原材料',
      materialCategory: '钢材',
      density: '7850',
      priceSpecCode: 'SPEC_2',
      priceSpecs: [
        { specCode: 'SPEC_1', unifiedPrice: '7.5' },
        { specCode: 'SPEC_2', unifiedPrice: '8.5', barPrice: '9.5' }
      ],
      remarks: '远程材料快照',
      processRoute: [
        {
          sortNo: '10',
          optionKey: 'process:77',
          processDetailId: 77,
          processCategoryId: 8,
          processCode: 'CNC',
          categoryName: '数控加工',
          detailName: 'CNC 精加工',
          processName: 'CNC 精加工',
          detailHourlyRate: '120',
          categoryHourlyRate: '100',
          effectiveHourlyRate: '120',
          hourlyRate: '120'
        }
      ]
    })

    expect(normalized).toEqual(expect.objectContaining({
      materialId: 12,
      materialCode: 'MAT-45',
      density: 7850,
      priceSpecCode: 'SPEC_2',
      priceSpecName: '规格二',
      remarks: '远程材料快照'
    }))
    expect(normalized.priceSpec).toEqual(expect.objectContaining({
      specCode: 'SPEC_2',
      unifiedPrice: 8.5,
      barPrice: 9.5
    }))
    expect(normalized.priceSpecs).toHaveLength(3)
    expect(normalized).not.toHaveProperty('platePrice')
    expect(normalized).not.toHaveProperty('barPrice')
    expect(normalized).not.toHaveProperty('tubePrice')
    expect(normalized.processRoute[0]).toEqual(expect.objectContaining({
      optionKey: 'process:77',
      processDetailId: 77,
      processCategoryId: 8,
      detailHourlyRate: 120,
      categoryHourlyRate: 100,
      effectiveHourlyRate: 120,
      hourlyRate: 120
    }))
  })

  test('legacy process-detail option keys remain compatible with saved history', () => {
    const normalized = normalizeRouteList([{ optionKey: 'process-detail-7', processName: '旧 CNC' }])

    expect(normalized[0]).toEqual(expect.objectContaining({
      optionKey: 'process:7',
      processDetailId: 7,
      legacyProcessIdentity: false
    }))
  })
})
