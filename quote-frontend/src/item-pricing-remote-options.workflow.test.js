import { describe, expect, test } from 'vitest'
import { readFileSync } from 'fs'
import { dirname, resolve } from 'path'
import { fileURLToPath } from 'url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

describe('item pricing remote material and process options', () => {
  test('item pricing page wires material and process pickers to remote option APIs', () => {
    const source = readSource('components', 'ItemPricing.vue')

    expect(source).toContain('pagePricingMaterialOptions')
    expect(source).toContain('pagePricingProcessOptions')
    expect(source).toContain(':remote-loader="pagePricingMaterialOptions"')
    expect(source).toContain(':remote-loader="pagePricingProcessOptions"')
    expect(source).not.toContain(':items="materialOptions"')
    expect(source).not.toContain(':items="combinedProcessOptions"')
  })

  test('item pricing startup does not load full material or process dictionaries', () => {
    const source = readSource('components', 'ItemPricing.vue')

    expect(source).not.toContain('dictionaryStore.loadBaseDicts')
    expect(source).toContain('dictionaryStore.loadPricingFeeDicts')
    expect(source).not.toContain('dictionaryStore.materials')
    expect(source).not.toContain('dictionaryStore.processes')
    expect(source).not.toContain('dictionaryStore.combinedProcessOptions')
  })

  test('price preview uses selected snapshots instead of global option caches', () => {
    const source = readSource('components', 'ItemPricing.vue')

    expect(source).toContain('const currentMatInfo = computed(() => ({')
    expect(source).toContain('id: matForm.materialId')
    expect(source).toContain('platePrice: numberOrZero(matForm.platePrice)')
    expect(source).toContain('const findProcessOption = (proc) => {')
    expect(source).toContain('return buildProcessSnapshotOption(proc)')
    expect(source).not.toContain('combinedProcessOptions.value.find')
    expect(source).toContain('const normalProcessCostPreview = computed(() => processRoute.value.reduce')
    expect(source).toContain('inputVal * hourlyRate')
    expect(source).toContain('await submitQuote(payload)')
  })
})
