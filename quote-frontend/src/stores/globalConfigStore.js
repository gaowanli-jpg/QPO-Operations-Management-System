import { defineStore } from 'pinia'

const FALLBACK_KEY = 'erp_global_config'
const rfqConfigKey = (rfqId) => `erp_global_config_${rfqId}`
const PROCESS_SORT_STEP = 10

export const defaultGlobalConfig = () => ({
  materialName: '45#',
  shape: '板材',
  processRoute: []
})

const numericSortNo = (value) => {
  const numericValue = Number(value)
  return Number.isFinite(numericValue) ? numericValue : null
}

export const sortRouteList = (routes = []) => {
  const withIndex = (routes || []).map((route, index) => ({ route, index }))
  const hasSortNo = withIndex.some(item => numericSortNo(item.route?.sortNo) != null)
  if (!hasSortNo) return routes || []
  return withIndex
    .sort((a, b) => {
      const aSort = numericSortNo(a.route?.sortNo)
      const bSort = numericSortNo(b.route?.sortNo)
      if (aSort != null && bSort != null && aSort !== bSort) return aSort - bSort
      if (aSort != null && bSort == null) return -1
      if (aSort == null && bSort != null) return 1
      return a.index - b.index
    })
    .map(item => item.route)
}

export const normalizeRouteList = (route) => {
  let list = []
  if (Array.isArray(route)) {
    list = JSON.parse(JSON.stringify(route))
  } else if (typeof route === 'string') {
    try {
      list = JSON.parse(route)
    } catch {
      return list
    }
  }
  return sortRouteList(Array.isArray(list) ? list : []).map((item, index) => ({
    sortNo: numericSortNo(item.sortNo) ?? ((index + 1) * PROCESS_SORT_STEP),
    optionKey: item.optionKey || '',
    processName: item.processName || '',
    calcType: item.calcType || '工时',
    sourceType: item.sourceType || (item.calcType === '非工时费用' ? 'extra' : 'process'),
    inputVal: item.inputVal
  }))
}

export const normalizeGlobalConfig = (config) => {
  const defaults = defaultGlobalConfig()
  if (!config || typeof config !== 'object') return defaults
  return {
    materialName: config.materialName || defaults.materialName,
    shape: config.shape || defaults.shape,
    processRoute: normalizeRouteList(config.processRoute)
  }
}

const readLocalConfig = (key) => {
  try {
    const raw = localStorage.getItem(key)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

export const useGlobalConfigStore = defineStore('globalConfig', {
  state: () => ({
    currentRfqId: '',
    configByRfqId: {},
    fallbackConfig: normalizeGlobalConfig(readLocalConfig(FALLBACK_KEY))
  }),
  actions: {
    getConfig(rfqId) {
      const key = String(rfqId || '')
      return this.configByRfqId[key] || this.loadLocalConfig(key)
    },

    loadLocalConfig(rfqId) {
      const key = String(rfqId || '')
      this.currentRfqId = key
      const rawConfig = (key ? readLocalConfig(rfqConfigKey(key)) : null) || readLocalConfig(FALLBACK_KEY)
      const config = normalizeGlobalConfig(rawConfig)
      if (key) {
        this.configByRfqId[key] = config
      }
      this.fallbackConfig = config
      return config
    },

    persistLocalConfig(rfqId, config) {
      const normalized = normalizeGlobalConfig(config)
      const key = String(rfqId || '')
      if (key) {
        localStorage.setItem(rfqConfigKey(key), JSON.stringify(normalized))
      }
      localStorage.setItem(FALLBACK_KEY, JSON.stringify(normalized))
      return normalized
    },

    setConfig(rfqId, config) {
      const key = String(rfqId || '')
      const normalized = this.persistLocalConfig(key, config)
      if (key) {
        this.currentRfqId = key
        this.configByRfqId[key] = normalized
      }
      this.fallbackConfig = normalized
      return normalized
    },

    applyEngineerConfig(rfqId, config) {
      return this.setConfig(rfqId, config)
    }
  }
})
