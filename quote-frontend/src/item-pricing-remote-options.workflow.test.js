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
    expect(source).toContain(':page-size-options="[10, 20, 50, 100]"')
    expect(source).toContain(':default-page-size="10"')
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

  test('item pricing opens normal items with three blank process placeholders when no route template exists', () => {
    const source = readSource('components', 'ItemPricing.vue')

    expect(source).toContain('const createDefaultBlankProcessRows = () =>')
    expect(source).toContain('processRoute.value = assignProcessSortNos(createDefaultBlankProcessRows())')
  })

  test('price preview hydrates selected snapshots from targeted remote option caches', () => {
    const source = readSource('components', 'ItemPricing.vue')

    expect(source).toContain('const currentMatInfo = computed(() => ({')
    expect(source).toContain('id: matForm.materialId')
    expect(source).toContain('priceSpecCode: matForm.priceSpecCode')
    expect(source).toContain('priceSpec: matForm.priceSpec || findPriceSpec(matForm.priceSpecs, matForm.priceSpecCode)')
    expect(source).toContain('const pricingMaterialOptionCache = ref(new Map())')
    expect(source).toContain('const pricingProcessOptionCache = ref(new Map())')
    expect(source).toContain('const loadLatestPricingOptionsForQuote = async (materialSource = {}, processRoutes = []) =>')
    expect(source).toContain("materialCodes: missingMaterialCodes.join(',')")
    expect(source).toContain("processDetailIds: missingProcessDetailIds.join(',')")
    expect(source).toContain('hydrateMaterialPricingSnapshot(source, pricingOptionIndex.value)')
    expect(source).toContain('hydrateProcessPricingSnapshot(proc, pricingOptionIndex.value)')
    expect(source).toContain('const findProcessOption = (proc) => {')
    expect(source).not.toContain('return buildProcessSnapshotOption(proc)')
    expect(source).not.toContain('combinedProcessOptions.value.find')
    expect(source).toContain('const normalProcessCostPreview = computed(() => processRoute.value.reduce')
    expect(source).toContain('inputVal * hourlyRate')
    expect(source).toContain('effectiveSpecPrice(currentMatInfo.value.priceSpec, matForm.shape)')
    expect(source).toContain('await submitQuote(payload)')
  })

  test('normal item save syncs current material, spec, shape, and process route back to the order global config', () => {
    const source = readSource('components', 'ItemPricing.vue')

    expect(source).toContain('const buildGlobalMaterialConfig = () => ({')
    expect(source).toContain('materialCode: matForm.materialCode')
    expect(source).toContain('priceSpecCode: matForm.priceSpecCode')
    expect(source).toContain('priceSpec: matForm.priceSpec')
    expect(source).toContain('shape: matForm.shape')
    expect(source).toContain('const syncCurrentSelectionToGlobalConfig = async () =>')
    expect(source).toContain('...buildGlobalMaterialConfig(),')
    expect(source).toContain('processRoute: nextProcessRoute')
    expect(source).toContain('await syncCurrentSelectionToGlobalConfig()')
    expect(source).not.toContain('syncCurrentRouteToGlobalConfig')
  })

  test('normal item save includes stable extra fee identity for usage statistics', () => {
    const source = readSource('components', 'ItemPricing.vue')

    expect(source).toContain('proc.feeCode = sourceType === \'extra\' ? (selected.feeCode || \'\') : \'\'')
    expect(source).toContain('feeItemId: p.feeItemId ?? null')
    expect(source).toContain('feeCode: p.feeCode || \'\'')
  })
})
