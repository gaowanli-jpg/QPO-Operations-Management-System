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

describe('Stage 38 phase 1 process route baseline', () => {
  test('global setup currently persists process routes by option key and process name', () => {
    const store = useGlobalConfigStore()

    const config = store.setConfig(38, {
      materialName: '40Cr',
      shape: '棒材',
      processRoute: [
        { sortNo: 20, optionKey: 'process-2', processName: 'CNC1', calcType: '工时', sourceType: 'process' },
        { sortNo: 10, optionKey: 'process-1', processName: '铣', calcType: '工时', sourceType: 'process' }
      ]
    })

    expect(config.processRoute).toEqual([
      { sortNo: 10, optionKey: 'process-1', processName: '铣', calcType: '工时', sourceType: 'process', inputVal: undefined },
      { sortNo: 20, optionKey: 'process-2', processName: 'CNC1', calcType: '工时', sourceType: 'process', inputVal: undefined }
    ])
    expect(localStorage.setItem).toHaveBeenCalledWith('erp_global_config_38', expect.stringContaining('processName'))
    expect(localStorage.setItem).toHaveBeenCalledWith('erp_global_config', expect.stringContaining('optionKey'))
  })

  test('normalizeRouteList keeps current legacy process fields sorted by sort number', () => {
    const normalized = normalizeRouteList([
      { sortNo: '30', processName: 'CNC-5', calcType: '工时', sourceType: 'process', inputVal: 1.5 },
      { sortNo: '10', processName: '表面处理', calcType: '非工时费用', sourceType: 'extra', inputVal: 200 }
    ])

    expect(normalized).toEqual([
      { sortNo: 10, optionKey: '', processName: '表面处理', calcType: '非工时费用', sourceType: 'extra', inputVal: 200 },
      { sortNo: 30, optionKey: '', processName: 'CNC-5', calcType: '工时', sourceType: 'process', inputVal: 1.5 }
    ])
  })

  test('pricing and global setup pages currently use processName as the route identity', () => {
    const itemPricing = readSource('components', 'ItemPricing.vue')
    const globalSetup = readSource('components', 'GlobalSetup.vue')

    expect(itemPricing).toContain('return `${sourceType}::${route.processName || \'\'}`')
    expect(itemPricing).toContain('processName: p.processName')
    expect(itemPricing).toContain('inputVal: p.inputVal')
    expect(itemPricing).toContain('sourceType: p.sourceType || (p.calcType === \'非工时费用\' ? \'extra\' : \'process\')')
    expect(itemPricing).not.toContain('processDetailId: p.processDetailId')

    expect(globalSetup).toContain('processName: item.processName || \'\'')
    expect(globalSetup).toContain('optionKey: item.optionKey || \'\'')
    expect(globalSetup).toContain('proc.processName = selected.processName')
    expect(globalSetup).not.toContain('proc.processDetailId = selected.processDetailId')
  })

  test('stage 38 plan documents that these current frontend identities are replacement targets', () => {
    const plan = readRepo('docs', 'QPO报价状态流转', '38-数据库基线重建与两级工艺主数据全链路重构开发文档.md')

    expect(plan).toContain('全局工艺模板改用 `processDetailId`')
    expect(plan).toContain('前端源码不再把旧 `processName` 作为新工艺身份字段')
    expect(plan).toContain('旧本地模板读取时提示重新选择并保存')
    expect(plan).not.toContain('前端必须长期保留 `processName` 作为唯一身份')
  })
})
