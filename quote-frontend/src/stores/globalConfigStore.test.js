import { beforeEach, describe, expect, test, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useGlobalConfigStore } from './globalConfigStore'

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
  test('loadLocalConfig prefers rfq-specific config and normalizes route', () => {
    localStorage.setItem('erp_global_config', JSON.stringify({ materialName: 'fallback', shape: 'bar', processRoute: [] }))
    localStorage.setItem('erp_global_config_12', JSON.stringify({
      materialName: '45#',
      processRoute: [{ sortNo: '20', processName: 'CNC1' }, { sortNo: '10', processName: 'MILL' }]
    }))
    const store = useGlobalConfigStore()

    const config = store.loadLocalConfig(12)

    expect(config.materialName).toBe('45#')
    expect(config.shape).toBe('板材')
    expect(config.processRoute.map(item => item.processName)).toEqual(['MILL', 'CNC1'])
  })

  test('setConfig persists rfq-specific and fallback keys', () => {
    const store = useGlobalConfigStore()

    store.setConfig(15, { materialName: '40Cr', shape: '棒材', processRoute: [] })

    expect(localStorage.setItem).toHaveBeenCalledWith('erp_global_config_15', expect.stringContaining('40Cr'))
    expect(localStorage.setItem).toHaveBeenCalledWith('erp_global_config', expect.stringContaining('40Cr'))
  })

  test('applyEngineerConfig overwrites cached config with backend config', () => {
    const store = useGlobalConfigStore()
    store.setConfig(16, { materialName: 'local', shape: '板材', processRoute: [] })

    const config = store.applyEngineerConfig(16, { materialName: 'server', shape: '管材', processRoute: [] })

    expect(config.materialName).toBe('server')
    expect(store.getConfig(16).shape).toBe('管材')
  })
})
