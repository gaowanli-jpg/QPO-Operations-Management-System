import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

describe('Stage 38 phase 6 item pricing process route payload', () => {
  test('ItemPricing submits processDetailId and unitWorkHours instead of price snapshots', () => {
    const source = readSource('components', 'ItemPricing.vue')
    const buildQuoteBlock = source.slice(
      source.indexOf('const buildQuoteProcessRoute'),
      source.indexOf('const buildGlobalProcessRoute')
    )

    expect(buildQuoteBlock).toContain('processDetailId: p.processDetailId')
    expect(buildQuoteBlock).toContain('unitWorkHours: p.inputVal')
    expect(buildQuoteBlock).toContain('sortNo: p.sortNo')
    expect(buildQuoteBlock).not.toContain('unitRate')
    expect(buildQuoteBlock).not.toContain('lineCost')
  })

  test('ItemPricing keeps processDetailId as route identity while preserving extra fee names', () => {
    const source = readSource('components', 'ItemPricing.vue')
    const buildQuoteBlock = source.slice(
      source.indexOf('const buildQuoteProcessRoute'),
      source.indexOf('const buildGlobalProcessRoute')
    )
    const comparableBlock = source.slice(
      source.indexOf('const comparableProcessRoute'),
      source.indexOf('const syncCurrentRouteToGlobalConfig')
    )

    expect(source).toContain('processDetailId: source.processDetailId ?? null')
    expect(source).toContain('proc.processDetailId = selected.processDetailId')
    expect(buildQuoteBlock).toContain('processDetailId: p.processDetailId')
    expect(buildQuoteBlock).toContain('processName: p.processName')
    expect(comparableBlock).toContain('processDetailId: sourceType === \'process\' ? (p.processDetailId ?? null) : null')
    expect(comparableBlock).toContain('processName: p.processName || \'\'')
    expect(source).not.toContain('return `${sourceType}::${route.processName || \'\'}`')
    expect(source).not.toContain('mergeRoutesByGlobalOrder')
  })

  test('ItemPricing opens existing normal quoted items with their saved process route before global defaults', () => {
    const source = readSource('components', 'ItemPricing.vue')
    const openDialogBlock = source.slice(
      source.indexOf('const openQuoteDialog'),
      source.indexOf('// 智能计算材料费')
    )

    expect(openDialogBlock).toContain('const savedProcessRoutes = normalizeRouteList(row.processRoute)')
    expect(openDialogBlock).toContain("const shouldUseSavedProcessRoutes = quoteMode.value === 'NORMAL' && savedProcessRoutes.length > 0")
    expect(openDialogBlock).toContain('if (shouldUseSavedProcessRoutes)')
    expect(openDialogBlock).toContain('processRoute.value = assignProcessSortNos(savedProcessRoutes.map(route => hydrateRouteItem(createProcessRow(route))))')
    expect(openDialogBlock.indexOf('if (shouldUseSavedProcessRoutes)')).toBeLessThan(
      openDialogBlock.indexOf('loadGlobalProcessRouteDefaults()')
    )
    expect(openDialogBlock).not.toContain('mergeRoutesByGlobalOrder(row.processRoute, globalConfig.value.processRoute)')
  })

  test('ItemPricing does not load assembly fee snapshots into the normal process route state', () => {
    const source = readSource('components', 'ItemPricing.vue')
    const openDialogBlock = source.slice(
      source.indexOf('const openQuoteDialog'),
      source.indexOf('// 智能计算材料费')
    )

    expect(openDialogBlock).toContain("const shouldUseSavedProcessRoutes = quoteMode.value === 'NORMAL' && savedProcessRoutes.length > 0")
    expect(openDialogBlock).toContain('if (shouldUseSavedProcessRoutes)')
    expect(openDialogBlock).toContain('loadGlobalProcessRouteDefaults()')
    expect(openDialogBlock).toContain("if (quoteMode.value === 'ASSEMBLY')")
    expect(openDialogBlock.indexOf('if (shouldUseSavedProcessRoutes)')).toBeLessThan(
      openDialogBlock.indexOf('loadGlobalProcessRouteDefaults()')
    )
  })
})
