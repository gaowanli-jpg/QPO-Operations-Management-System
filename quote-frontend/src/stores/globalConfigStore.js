import { defineStore } from 'pinia'

const FALLBACK_KEY = 'erp_global_config'
const rfqConfigKey = (rfqId) => `erp_global_config_${rfqId}`
const PROCESS_SORT_STEP = 10
const ASSEMBLY_FEE_PROCESS_NAME = '组装费'

export const defaultGlobalConfig = () => ({
  materialId: null,
  materialCode: '',
  materialName: '',
  materialTypeId: null,
  materialTypeCode: '',
  materialTypeName: '',
  materialCategory: '',
  density: null,
  plateDensity: null,
  platePrice: null,
  barDensity: null,
  barPrice: null,
  tubeDensity: null,
  tubePrice: null,
  remarks: '',
  shape: '板材',
  processRoute: [],
  legacyMaterialIdentity: false
})

const numericSortNo = (value) => {
  const numericValue = Number(value)
  return Number.isFinite(numericValue) ? numericValue : null
}

const hasValue = (value) => value !== undefined && value !== null && value !== ''

const numericOrOriginal = (value) => {
  if (!hasValue(value)) return null
  const numericValue = Number(value)
  return Number.isFinite(numericValue) ? numericValue : value
}

const parseProcessDetailIdFromOptionKey = (optionKey) => {
  const match = String(optionKey || '').match(/^process(?::|-detail-)(.+)$/)
  return match?.[1] ? numericOrOriginal(match[1]) : null
}

const resolveProcessDetailId = (item = {}) => numericOrOriginal(
  item.processDetailId ?? item.detailId ?? parseProcessDetailIdFromOptionKey(item.optionKey)
)

const resolveSourceType = (item = {}) => item.sourceType || (item.calcType === '非工时费用' ? 'extra' : 'process')

const isAssemblyFeeRoute = (item = {}) => {
  const sourceType = resolveSourceType(item)
  const processName = String(item.processName || item.detailName || item.feeName || '').trim()
  return sourceType === 'extra' && processName === ASSEMBLY_FEE_PROCESS_NAME
}

const normalizeProcessOptionKey = (item = {}, sourceType = resolveSourceType(item)) => {
  if (sourceType === 'process') {
    const processDetailId = resolveProcessDetailId(item)
    return hasValue(processDetailId) ? `process:${processDetailId}` : ''
  }
  return item.optionKey || ''
}

const normalizeRouteItem = (item = {}, index = 0) => {
  const sourceType = resolveSourceType(item)
  const processDetailId = sourceType === 'process' ? resolveProcessDetailId(item) : null
  const detailName = item.detailName || item.processName || item.feeName || ''
  const processName = sourceType === 'process' ? detailName : (item.processName || item.feeName || detailName)
  const legacyProcessIdentity = sourceType === 'process'
    && !hasValue(processDetailId)
    && Boolean(item.processName || item.optionKey)

  return {
    sortNo: numericSortNo(item.sortNo) ?? ((index + 1) * PROCESS_SORT_STEP),
    optionKey: normalizeProcessOptionKey(item, sourceType),
    processDetailId,
    processCategoryId: sourceType === 'process' ? numericOrOriginal(item.processCategoryId ?? item.categoryId) : null,
    categoryId: sourceType === 'process' ? numericOrOriginal(item.categoryId ?? item.processCategoryId) : null,
    processCode: sourceType === 'process' ? (item.processCode || '') : '',
    categoryName: sourceType === 'process' ? (item.categoryName || '') : '',
    detailName: sourceType === 'process' ? detailName : '',
    detailHourlyRate: sourceType === 'process' ? numericOrOriginal(item.detailHourlyRate) : null,
    categoryHourlyRate: sourceType === 'process' ? numericOrOriginal(item.categoryHourlyRate) : null,
    effectiveHourlyRate: sourceType === 'process' ? numericOrOriginal(item.effectiveHourlyRate ?? item.hourlyRate ?? item.unitRate) : null,
    hourlyRate: sourceType === 'process' ? numericOrOriginal(item.hourlyRate ?? item.effectiveHourlyRate ?? item.unitRate) : null,
    unitRate: sourceType === 'process' ? numericOrOriginal(item.unitRate ?? item.hourlyRate ?? item.effectiveHourlyRate) : null,
    feeItemId: sourceType === 'extra' ? numericOrOriginal(item.feeItemId ?? item.id) : null,
    feeName: sourceType === 'extra' ? (item.feeName || processName) : '',
    label: item.label || '',
    processName,
    calcType: sourceType === 'extra' ? (item.calcType || '非工时费用') : '工时',
    sourceType,
    inputVal: item.inputVal,
    unitWorkHours: item.unitWorkHours ?? item.inputVal,
    legacyProcessIdentity
  }
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
  return sortRouteList(Array.isArray(list) ? list : [])
    .filter(item => !isAssemblyFeeRoute(item))
    .map(normalizeRouteItem)
}

export const hasLegacyProcessIdentity = (configOrRoutes) => {
  const routes = Array.isArray(configOrRoutes) ? configOrRoutes : (configOrRoutes?.processRoute || [])
  return routes.some(item => item?.legacyProcessIdentity || normalizeRouteItem(item).legacyProcessIdentity)
}

const normalizeMaterialIdentity = (config = {}, defaults = defaultGlobalConfig()) => {
  const materialCode = String(config.materialCode || '').trim()
  const materialName = config.materialName || config.name || defaults.materialName || ''
  const density = numericOrOriginal(config.density ?? defaults.density)
  const unitPrice = numericOrOriginal(config.unitPrice ?? defaults.unitPrice)
  return {
    materialId: config.materialId ?? config.id ?? defaults.materialId ?? null,
    materialCode,
    materialName,
    materialTypeId: config.materialTypeId ?? defaults.materialTypeId ?? null,
    materialTypeCode: config.materialTypeCode || defaults.materialTypeCode || '',
    materialTypeName: config.materialTypeName || defaults.materialTypeName || '',
    materialCategory: config.materialCategory || defaults.materialCategory || '',
    density,
    plateDensity: numericOrOriginal(config.plateDensity ?? density ?? defaults.plateDensity),
    platePrice: numericOrOriginal(config.platePrice ?? unitPrice ?? defaults.platePrice),
    barDensity: numericOrOriginal(config.barDensity ?? density ?? defaults.barDensity),
    barPrice: numericOrOriginal(config.barPrice ?? unitPrice ?? defaults.barPrice),
    tubeDensity: numericOrOriginal(config.tubeDensity ?? density ?? defaults.tubeDensity),
    tubePrice: numericOrOriginal(config.tubePrice ?? unitPrice ?? defaults.tubePrice),
    remarks: config.remarks || defaults.remarks || '',
    legacyMaterialIdentity: !materialCode && Boolean(materialName)
  }
}

export const hasLegacyMaterialIdentity = (config = {}) => Boolean(
  config?.legacyMaterialIdentity || (!config?.materialCode && (config?.materialName || config?.name))
)

export const normalizeGlobalConfig = (config) => {
  const defaults = defaultGlobalConfig()
  if (!config || typeof config !== 'object') return defaults
  return {
    ...normalizeMaterialIdentity(config, defaults),
    shape: config.shape || defaults.shape,
    processRoute: normalizeRouteList(config.processRoute)
  }
}

export const hasSavedEngineerConfig = (config) => {
  if (!config || typeof config !== 'object') return false
  const normalized = normalizeGlobalConfig(config)
  return Boolean(
    normalized.materialCode
    || normalized.materialName
    || normalized.processRoute.length > 0
  )
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
      const rawSpecificConfig = key ? readLocalConfig(rfqConfigKey(key)) : null
      const rawFallbackConfig = readLocalConfig(FALLBACK_KEY)
      const rawConfig = rawSpecificConfig || rawFallbackConfig
      const config = normalizeGlobalConfig(rawConfig)
      if (key) {
        this.configByRfqId[key] = config
      }
      if (!key || !rawSpecificConfig) {
        this.fallbackConfig = config
      }
      return config
    },

    persistLocalConfig(rfqId, config) {
      const normalized = normalizeGlobalConfig(config)
      const key = String(rfqId || '')
      if (key) {
        localStorage.setItem(rfqConfigKey(key), JSON.stringify(normalized))
        return normalized
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
      } else {
        this.fallbackConfig = normalized
      }
      return normalized
    },

    applyEngineerConfig(rfqId, config) {
      return this.setConfig(rfqId, config)
    }
  }
})
