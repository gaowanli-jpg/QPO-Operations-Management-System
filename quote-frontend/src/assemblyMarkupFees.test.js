import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

describe('assembly markup fee dictionary', () => {
  test('material process page exposes a dedicated assembly markup fee tab', () => {
    const source = readSource('components', 'MaterialProcessManage.vue')

    expect(source).toContain('label="组装件百分比费用类"')
    expect(source).toContain('name="assemblyMarkupFees"')
    expect(source).toContain(':data="assemblyMarkupFees"')
    expect(source).toContain('openAssemblyMarkupFeeDialog')
    expect(source).toContain('saveAssemblyMarkupFee')
    expect(source).toContain('deleteAssemblyMarkupFee')
    expect(source).toContain("v-permission=\"'engineer:dict:fee-manage'\"")
  })

  test('item pricing preview uses assembly markup fees without transport-name filtering', () => {
    const source = readSource('components', 'ItemPricing.vue')

    expect(source).toContain('const assemblyMarkupFeeOptions = computed(() => dictionaryStore.assemblyMarkupFees)')
    expect(source).toContain('const assemblyFeeMarkupRows = computed(() => assemblyMarkupFeeOptions.value')
    expect(source).not.toContain('isTransportMarkupFee')
    expect(source).not.toContain("title=\"组合件父件价格 = 子件含税单价汇总 + 组装费 + 组装费对应的管理费/利润/税费；组装费不收运输费。\"")
  })
})
