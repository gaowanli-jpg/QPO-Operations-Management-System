import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { hasLegacyProcessIdentity, normalizeRouteList } from './stores/globalConfigStore'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

describe('Stage 38 phase 5 process picker and global template refactor', () => {
  test('assembly preview applies ASSEMBLY scoped percentage factors only to assembly fee', () => {
    const itemPricing = readSource('components', 'ItemPricing.vue')

    expect(itemPricing).toContain("const assemblyMarkupFeeOptions = computed(() => dictionaryStore.getPricingMarkupFees('ASSEMBLY'))")
    expect(itemPricing).toContain('const assemblyPreviewPricingResult = computed(() => calculatePreviewPriceByFeeCategory(')
    expect(itemPricing).toContain('numberOrZero(assemblyFee.value),')
    expect(itemPricing).toContain('assemblyMarkupFeeOptions.value,')
    expect(itemPricing).toContain("'ASSEMBLY'")
    expect(itemPricing).toContain('const assemblyFeeMarkupRows = computed(() => assemblyPreviewPricingResult.value.feeRows)')
    expect(itemPricing).toContain('const assemblyFeeMarkupTotal = computed(() => assemblyFeeMarkupRows.value.reduce((sum, row) => sum + numberOrZero(row.cost), 0))')
    expect(itemPricing).toContain('const assemblyTaxIncludedUnitPrice = computed(() => assemblyChildrenTotal.value + assemblyPreviewPricingResult.value.taxIncludedUnitPrice)')
    expect(itemPricing).toContain("fee.feeCategory === feeCategory")
    expect(itemPricing).toContain("fee.pricingScope === pricingScope")
    expect(itemPricing).not.toContain('assemblyChildrenTotal.value + numberOrZero(assemblyFee.value) + assemblyFeeMarkupTotal.value')
    expect(itemPricing).not.toContain('numberOrZero(assemblyFee.value) * ratePercent / 100')
  })

  test('process picker shows detail process with category identity and hourly rate', () => {
    const source = readSource('components', 'EngineeringOptionPickerDialog.vue')

    expect(source).toContain('prop="detailName"')
    expect(source).toContain('label="具体工艺"')
    expect(source).toContain('prop="processCode"')
    expect(source).toContain('label="大类编码"')
    expect(source).toContain('prop="categoryName"')
    expect(source).toContain('label="大类名称"')
    expect(source).toContain('label="具体小时价格"')
    expect(source).toContain('label="大类小时价格"')
    expect(source).toContain('label="核价使用价格"')
    expect(source).toContain('formatHourlyRate')
    expect(source).toContain('item?.detailName')
    expect(source).toContain('item?.processCode')
    expect(source).toContain('item?.categoryName')
    expect(source).toContain('item?.hourlyRate')
  })

  test('process picker uses local ten-row pagination without the shared pagination component', () => {
    const source = readSource('components', 'EngineeringOptionPickerDialog.vue')

    expect(source).toContain('const PROCESS_PICKER_PAGE_SIZE = 10')
    expect(source).toContain(':data="pagedItems"')
    expect(source).toContain('const pagedItems = computed(() =>')
    expect(source).toContain('filteredItems.value.slice(start, start + PROCESS_PICKER_PAGE_SIZE)')
    expect(source).toContain('picker-pagination')
    expect(source).toContain('processPickerPage')
    expect(source).not.toContain('<el-pagination')
  })

  test('process picker can update concrete detail hourly rate inline and notify pricing page', () => {
    const picker = readSource('components', 'EngineeringOptionPickerDialog.vue')
    const itemPricing = readSource('components', 'ItemPricing.vue')

    expect(picker).toContain("import { saveProcessDetail } from '../services/dictApi'")
    expect(picker).toContain('enableProcessRateEdit')
    expect(picker).toContain('label="具体小时价格"')
    expect(picker).toContain('label="大类小时价格"')
    expect(picker).toContain('label="核价使用价格"')
    expect(picker).toContain("v-permission=\"'engineer:dict:process-manage'\"")
    expect(picker).toContain('saveInlineProcessRate(row)')
    expect(picker).toContain("emit('process-rate-updated', updatedDetail)")
    expect(picker).toContain(':value-on-clear="null"')

    expect(itemPricing).toContain(':enable-process-rate-edit="true"')
    expect(itemPricing).toContain('@process-rate-updated="handleProcessRateUpdated"')
    expect(itemPricing).toContain('await dictionaryStore.reloadAfterMutation()')
    expect(itemPricing).toContain('syncCurrentSelectedProcessRate(updatedDetail)')
  })

  test('global config normalization preserves processDetailId as stable identity', () => {
    const normalized = normalizeRouteList([
      {
        sortNo: '20',
        processDetailId: 7,
        processCategoryId: 3,
        processCode: 'PACKAGING',
        categoryName: '包装',
        detailName: '精细包装',
        hourlyRate: 10,
        unitWorkHours: 1.5
      }
    ])

    expect(normalized).toEqual([
      expect.objectContaining({
        sortNo: 20,
        optionKey: 'process-detail-7',
        processDetailId: 7,
        processCategoryId: 3,
        processCode: 'PACKAGING',
        categoryName: '包装',
        detailName: '精细包装',
        hourlyRate: 10,
        processName: '精细包装',
        sourceType: 'process',
        legacyProcessIdentity: false
      })
    ])
  })

  test('old local processName templates are marked for reselection', () => {
    const normalized = normalizeRouteList([
      { sortNo: 10, optionKey: 'process-1', processName: 'CNC1', calcType: '工时', sourceType: 'process' },
      { sortNo: 20, optionKey: 'extra-2', processName: 'Heat Treat', calcType: '非工时费用', sourceType: 'extra' }
    ])

    expect(normalized[0]).toEqual(expect.objectContaining({
      processDetailId: null,
      legacyProcessIdentity: true
    }))
    expect(normalized[1]).toEqual(expect.objectContaining({
      sourceType: 'extra',
      legacyProcessIdentity: false
    }))
    expect(hasLegacyProcessIdentity({ processRoute: normalized })).toBe(true)
  })

  test('GlobalSetup saves and validates templates by processDetailId instead of processName', () => {
    const source = readSource('components', 'GlobalSetup.vue')

    expect(source).toContain('proc.processDetailId = selected.processDetailId')
    expect(source).toContain('proc.processCode = selected.processCode')
    expect(source).toContain('proc.categoryName = selected.categoryName')
    expect(source).toContain('proc.detailName = selected.detailName')
    expect(source).toContain('proc.hourlyRate = selected.hourlyRate')
    expect(source).toContain('hasUnresolvedProcessRoute')
    expect(source).toContain('hasLegacyProcessIdentity')
    expect(source).toContain('hasSavedEngineerConfig')
    expect(source).toContain('请重新选择具体工艺后再保存')
    expect(source).not.toContain('item.processName === proc.processName && item.sourceType')
    expect(source).not.toContain('!!orderInfo.value.engineerConfig')
  })
})
