import { beforeEach, describe, expect, test, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'
import { normalizeRouteList, useGlobalConfigStore } from './stores/globalConfigStore'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')
const readRepo = (...segments) => readFileSync(resolve(currentDir, '..', '..', ...segments), 'utf8')

const createLocalStorage = () => {
  const values = new Map()
  return {
    getItem: vi.fn((key) => values.get(key) ?? null),
    setItem: vi.fn((key, value) => values.set(key, String(value))),
    removeItem: vi.fn((key) => values.delete(key)),
    clear: vi.fn(() => values.clear())
  }
}

beforeEach(() => {
  Object.defineProperty(globalThis, 'localStorage', {
    value: createLocalStorage(),
    configurable: true
  })
  setActivePinia(createPinia())
})

describe('Stage 38 process route frontend contract', () => {
  test('global setup persists process routes with processDetailId identity when available', () => {
    const store = useGlobalConfigStore()

    const config = store.setConfig(38, {
      materialName: '40Cr',
      shape: '棒材',
      processRoute: [
        { sortNo: 20, processDetailId: 2, processCategoryId: 1, processCode: 'CNC', categoryName: '数控', detailName: 'CNC1', hourlyRate: 80, calcType: '工时', sourceType: 'process' },
        { sortNo: 10, processDetailId: 1, processCategoryId: 1, processCode: 'MILLING', categoryName: '铣削', detailName: '精铣', hourlyRate: 60, calcType: '工时', sourceType: 'process' }
      ]
    })

    expect(config.processRoute).toEqual([
      expect.objectContaining({ sortNo: 10, optionKey: 'process:1', processDetailId: 1, processCode: 'MILLING', categoryName: '铣削', detailName: '精铣', processName: '精铣', legacyProcessIdentity: false }),
      expect.objectContaining({ sortNo: 20, optionKey: 'process:2', processDetailId: 2, processCode: 'CNC', categoryName: '数控', detailName: 'CNC1', processName: 'CNC1', legacyProcessIdentity: false })
    ])
    expect(localStorage.setItem).toHaveBeenCalledWith('erp_global_config_38', expect.stringContaining('processDetailId'))
    expect(localStorage.setItem).not.toHaveBeenCalledWith('erp_global_config', expect.stringContaining('process:1'))
  })

  test('normalizeRouteList sorts routes and marks old processName-only identities', () => {
    const normalized = normalizeRouteList([
      { sortNo: '30', processName: 'CNC-5', calcType: '工时', sourceType: 'process', inputVal: 1.5 },
      { sortNo: '10', processName: '表面处理', calcType: '非工时费用', sourceType: 'extra', inputVal: 200 }
    ])

    expect(normalized).toEqual([
      expect.objectContaining({ sortNo: 10, optionKey: '', processName: '表面处理', calcType: '非工时费用', sourceType: 'extra', inputVal: 200, legacyProcessIdentity: false }),
      expect.objectContaining({ sortNo: 30, optionKey: '', processDetailId: null, processName: 'CNC-5', calcType: '工时', sourceType: 'process', inputVal: 1.5, legacyProcessIdentity: true })
    ])
  })

  test('global setup and item pricing use processDetailId as the process identity', () => {
    const itemPricing = readSource('components', 'ItemPricing.vue')
    const globalSetup = readSource('components', 'GlobalSetup.vue')
    const comparableBlock = itemPricing.slice(
      itemPricing.indexOf('const comparableProcessRoute'),
      itemPricing.indexOf('const syncCurrentRouteToGlobalConfig')
    )
    const openDialogBlock = itemPricing.slice(
      itemPricing.indexOf('const openQuoteDialog'),
      itemPricing.indexOf('// 智能计算材料费')
    )

    expect(itemPricing).toContain('processDetailId: p.processDetailId')
    expect(itemPricing).toContain('unitWorkHours: p.inputVal')
    expect(comparableBlock).toContain('processDetailId: sourceType === \'process\' ? (p.processDetailId ?? null) : null')
    expect(comparableBlock).toContain('processName: p.processName || \'\'')
    expect(openDialogBlock).toContain("const shouldUseSavedProcessRoutes = quoteMode.value === 'NORMAL' && savedProcessRoutes.length > 0")
    expect(openDialogBlock).toContain('if (shouldUseSavedProcessRoutes)')
    expect(itemPricing).not.toContain('return `${sourceType}::${route.processName || \'\'}`')
    expect(itemPricing).not.toContain('mergeRoutesByGlobalOrder')

    expect(globalSetup).toContain('processDetailId: item.processDetailId ?? null')
    expect(globalSetup).toContain('const processDetailId = sourceType === \'process\' ? (selected.processDetailId ?? selected.id ?? null) : null')
    expect(globalSetup).toContain('proc.processDetailId = processDetailId')
    expect(globalSetup).toContain('hasUnresolvedProcessRoute')
    expect(globalSetup).not.toContain('item.processName === proc.processName && item.sourceType')
  })

  test('stage 38 plan documents that these current frontend identities are replacement targets', () => {
    const plan = readRepo('docs', 'QPO报价状态流转', '38-数据库基线重建与两级工艺主数据全链路重构开发文档.md')

    expect(plan).toContain('全局工艺模板改用 `processDetailId`')
    expect(plan).toContain('前端源码不再把旧 `processName` 作为新工艺身份字段')
    expect(plan).toContain('旧本地模板读取时提示重新选择并保存')
    expect(plan).not.toContain('前端必须长期保留 `processName` 作为唯一身份')
  })
})
