import { beforeEach, describe, expect, test, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { defaultGlobalConfig, hasLegacyProcessIdentity, hasSavedEngineerConfig, normalizeRouteList, useGlobalConfigStore } from './globalConfigStore'

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

describe('globalConfigStore', () => {
  test('defaults blank global setup to spec one, plate shape, and three empty process rows', () => {
    const config = defaultGlobalConfig()

    expect(config.materialCode).toBe('')
    expect(config.materialName).toBe('')
    expect(config.shape).toBe('板材')
    expect(config.priceSpecCode).toBe('SPEC_1')
    expect(config.priceSpecName).toBe('规格一')
    expect(config.priceSpec).toEqual(expect.objectContaining({ specCode: 'SPEC_1', specName: '规格一' }))
    expect(config.processRoute).toHaveLength(3)
    expect(config.processRoute).toEqual(config.processRoute.map((row, index) => expect.objectContaining({
      sortNo: (index + 1) * 10,
      processName: '',
      processDetailId: null,
      sourceType: 'process'
    })))
    expect(hasSavedEngineerConfig(config)).toBe(false)
  })

  test('loadLocalConfig prefers rfq-specific config and normalizes route', () => {
    localStorage.setItem('erp_global_config', JSON.stringify({ materialName: 'fallback', shape: 'bar', processRoute: [] }))
    localStorage.setItem('erp_global_config_12', JSON.stringify({
      materialName: '45#',
      processRoute: [
        { sortNo: '20', optionKey: 'process-detail-2', processName: 'CNC1' },
        { sortNo: '10', processDetailId: 1, processCode: 'MILLING', categoryName: '铣削', detailName: 'MILL' }
      ]
    }))
    const store = useGlobalConfigStore()

    const config = store.loadLocalConfig(12)

    expect(config.materialName).toBe('45#')
    expect(config.shape).toBe('板材')
    expect(config.processRoute.map(item => item.processName)).toEqual(['MILL', 'CNC1'])
    expect(config.processRoute.map(item => item.processDetailId)).toEqual([1, 2])
    expect(config.processRoute.every(item => item.legacyProcessIdentity === false)).toBe(true)
  })

  test('marks processName-only local routes as legacy reselection candidates', () => {
    const normalized = normalizeRouteList([{ sortNo: 10, processName: 'CNC1', calcType: '工时', sourceType: 'process' }])

    expect(normalized[0]).toEqual(expect.objectContaining({
      processDetailId: null,
      legacyProcessIdentity: true
    }))
    expect(hasLegacyProcessIdentity({ processRoute: normalized })).toBe(true)
  })

  test('setConfig persists rfq-specific and fallback keys', () => {
    const store = useGlobalConfigStore()

    store.setConfig(15, { materialName: '40Cr', shape: '棒材', processRoute: [] })

    expect(localStorage.setItem).toHaveBeenCalledWith('erp_global_config_15', expect.stringContaining('40Cr'))
    expect(localStorage.setItem).not.toHaveBeenCalledWith('erp_global_config', expect.stringContaining('40Cr'))
  })

  test('applyEngineerConfig overwrites cached config with backend config', () => {
    const store = useGlobalConfigStore()
    store.setConfig(16, { materialName: 'local', shape: '板材', processRoute: [] })

    const config = store.applyEngineerConfig(16, { materialName: 'server', shape: '管材', processRoute: [] })

    expect(config.materialName).toBe('server')
    expect(store.getConfig(16).shape).toBe('管材')
  })

  test('normalizes assembly fee snapshots out of global process templates', () => {
    const config = normalizeRouteList([
      { sortNo: 10, processName: '组装费', calcType: '非工时费用', sourceType: 'extra' },
      { sortNo: 20, processDetailId: 7, processName: 'CNC1', calcType: '工时', sourceType: 'process' }
    ])

    expect(config.map(item => item.processName)).toEqual(['CNC1'])
  })

  test('treats empty engineer config as unsaved', () => {
    expect(hasSavedEngineerConfig(null)).toBe(false)
    expect(hasSavedEngineerConfig({})).toBe(false)
    expect(hasSavedEngineerConfig({ processRoute: [] })).toBe(false)
    expect(hasSavedEngineerConfig({ materialCode: '' })).toBe(false)
    expect(hasSavedEngineerConfig({ materialCode: 'MAT-001' })).toBe(true)
    expect(hasSavedEngineerConfig({ processRoute: [{ processDetailId: 7, processName: 'CNC1' }] })).toBe(true)
  })
})
