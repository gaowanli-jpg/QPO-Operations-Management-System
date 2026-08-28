import { defineStore } from 'pinia'
import {
  listExtraFeeItems,
  listMaterialTypes,
  listMaterials,
  listPricingMarkupFees,
  listProcessCategories,
  listProcessDetails
} from '../services/dictApi'

const PRICING_MARKUP_SCOPES = ['NORMAL', 'ASSEMBLY']
const PRICING_MARKUP_FEE_CATEGORIES = ['BASIC_PERCENT', 'TAX', 'CUSTOMER_WEIGHT']

const toProcessCompatibilityRow = (item = {}) => ({
  ...item,
  id: item.id,
  processDetailId: item.id,
  processCategoryId: item.categoryId,
  categoryId: item.categoryId,
  detailHourlyRate: item.detailHourlyRate,
  categoryHourlyRate: item.categoryHourlyRate,
  effectiveHourlyRate: item.effectiveHourlyRate,
  hourlyRate: item.hourlyRate ?? item.effectiveHourlyRate,
  detailEnabled: item.detailEnabled ?? item.enabled,
  categoryEnabled: item.categoryEnabled,
  effectiveEnabled: item.effectiveEnabled ?? ((item.detailEnabled ?? item.enabled) !== false && item.categoryEnabled !== false),
  processName: item.detailName,
  calcType: '工时',
  sourceType: 'process'
})

const isProcessDetailEffectivelyEnabled = (item = {}) => {
  if (item.effectiveEnabled != null) return item.effectiveEnabled !== false
  const detailEnabled = item.detailEnabled ?? item.enabled
  return detailEnabled !== false && item.categoryEnabled !== false
}

const formatHourlyRate = (value) => Number(value ?? 0).toFixed(2)
const processRateSourceText = (item = {}) => item.detailHourlyRate == null ? '继承大类' : '具体价'

const normalizePricingMarkupFee = (item = {}, pricingScope, feeCategory) => ({
  ...item,
  pricingScope: item.pricingScope || pricingScope,
  feeCategory: item.feeCategory || feeCategory,
  enabled: item.enabled !== false,
  systemRequired: item.systemRequired === true
})

const loadPricingMarkupFeeRows = async () => {
  const groups = await Promise.all(PRICING_MARKUP_SCOPES.flatMap((pricingScope) => (
    PRICING_MARKUP_FEE_CATEGORIES.map((feeCategory) => (
      listPricingMarkupFees(pricingScope, feeCategory)
        .then((rows) => (rows || []).map((item) => normalizePricingMarkupFee(item, pricingScope, feeCategory)))
    ))
  )))
  return groups.flat()
}

export const useDictionaryStore = defineStore('dictionary', {
  state: () => ({
    materialTypes: [],
    materials: [],
    processes: [],
    processCategories: [],
    processDetails: [],
    extraFeeItems: [],
    pricingMarkupFees: [],
    loadedAt: null,
    baseLoadedAt: null,
    pricingMarkupFeesLoadedAt: null,
    loading: false,
    lastIncludePricingMarkupFees: false,
    lastIncludeAllExtraFees: false
  }),
  getters: {
    getPricingMarkupFees: (state) => (pricingScope, feeCategory) => state.pricingMarkupFees.filter((item) => {
      const scopeMatches = !pricingScope || item.pricingScope === pricingScope
      const categoryMatches = !feeCategory || item.feeCategory === feeCategory
      return scopeMatches && categoryMatches
    }),
    combinedProcessOptions: (state) => [
      ...state.processDetails.filter(isProcessDetailEffectivelyEnabled).map(item => ({
        optionKey: `process:${item.id}`,
        processDetailId: item.id,
        processCategoryId: item.categoryId,
        categoryId: item.categoryId,
        processCode: item.processCode,
        categoryName: item.categoryName,
        detailName: item.detailName,
        detailRemarks: item.detailRemarks,
        enabled: item.enabled,
        detailEnabled: item.detailEnabled ?? item.enabled,
        categoryEnabled: item.categoryEnabled,
        effectiveEnabled: item.effectiveEnabled ?? ((item.detailEnabled ?? item.enabled) !== false && item.categoryEnabled !== false),
        sortNo: item.sortNo,
        detailHourlyRate: item.detailHourlyRate,
        categoryHourlyRate: item.categoryHourlyRate,
        effectiveHourlyRate: item.effectiveHourlyRate,
        hourlyRate: item.hourlyRate ?? item.effectiveHourlyRate,
        processName: item.detailName,
        label: `${item.detailName}（${item.processCode || '-'} / ${item.categoryName || '-'} / ${formatHourlyRate(item.hourlyRate ?? item.effectiveHourlyRate)}元/小时 / ${processRateSourceText(item)}）`,
        calcType: '工时',
        sourceType: 'process'
      })),
      ...state.extraFeeItems.map(item => ({
        optionKey: `extra-${item.id}`,
        processName: item.feeName,
        label: `${item.feeName}（非工时费用）`,
        calcType: '非工时费用',
        sourceType: 'extra'
      }))
    ]
  },
  actions: {
    async loadBaseDicts({
      includePricingMarkupFees = false,
      includeAllExtraFees = false,
      force = false
    } = {}) {
      const hasRequiredCache = this.loadedAt
        && this.baseLoadedAt
        && (!includePricingMarkupFees || this.lastIncludePricingMarkupFees)
        && (!includeAllExtraFees || this.lastIncludeAllExtraFees)
      if (hasRequiredCache && !force) {
        return {
          materialTypes: this.materialTypes,
          materials: this.materials,
          processes: this.processes,
          processCategories: this.processCategories,
          processDetails: this.processDetails,
          extraFeeItems: this.extraFeeItems,
          pricingMarkupFees: this.pricingMarkupFees
        }
      }

      this.loading = true
      try {
        const requests = [
          listMaterialTypes(),
          listMaterials(),
          listProcessCategories(),
          listProcessDetails(),
          listExtraFeeItems({ includeDisabled: includeAllExtraFees })
        ]
        if (includePricingMarkupFees) {
          requests.push(loadPricingMarkupFeeRows())
        }
        const [materialTypes, materials, processCategories, processDetails, extraFeeItems, pricingMarkupFees] = await Promise.all(requests)
        this.materialTypes = materialTypes || []
        this.materials = materials || []
        this.processCategories = processCategories || []
        this.processDetails = processDetails || []
        this.processes = this.processDetails.filter(isProcessDetailEffectivelyEnabled).map(toProcessCompatibilityRow)
        this.extraFeeItems = extraFeeItems || []
        if (includePricingMarkupFees) {
          this.pricingMarkupFees = pricingMarkupFees || []
          this.pricingMarkupFeesLoadedAt = Date.now()
        }
        this.loadedAt = Date.now()
        this.baseLoadedAt = this.loadedAt
        this.lastIncludePricingMarkupFees = this.lastIncludePricingMarkupFees || includePricingMarkupFees
        this.lastIncludeAllExtraFees = this.lastIncludeAllExtraFees || includeAllExtraFees
        return {
          materialTypes: this.materialTypes,
          materials: this.materials,
          processes: this.processes,
          processCategories: this.processCategories,
          processDetails: this.processDetails,
          extraFeeItems: this.extraFeeItems,
          pricingMarkupFees: this.pricingMarkupFees
        }
      } finally {
        this.loading = false
      }
    },

    async loadPricingFeeDicts({ force = false } = {}) {
      if (this.pricingMarkupFeesLoadedAt && this.lastIncludePricingMarkupFees && !force) {
        return { pricingMarkupFees: this.pricingMarkupFees }
      }

      this.loading = true
      try {
        const pricingMarkupFees = await loadPricingMarkupFeeRows()
        this.pricingMarkupFees = pricingMarkupFees || []
        this.loadedAt = Date.now()
        this.pricingMarkupFeesLoadedAt = this.loadedAt
        this.lastIncludePricingMarkupFees = true
        return { pricingMarkupFees: this.pricingMarkupFees }
      } finally {
        this.loading = false
      }
    },

    async reloadAfterMutation() {
      return this.loadBaseDicts({
        includePricingMarkupFees: true,
        includeAllExtraFees: true,
        force: true
      })
    },

    upsertProcessDetail(detail = {}) {
      const id = detail.id ?? detail.processDetailId
      if (id == null) return
      const normalizedDetail = {
        ...detail,
        id,
        categoryId: detail.categoryId ?? detail.processCategoryId
      }
      const index = this.processDetails.findIndex(item => String(item.id) === String(id))
      if (index >= 0) {
        this.processDetails.splice(index, 1, {
          ...this.processDetails[index],
          ...normalizedDetail
        })
      } else {
        this.processDetails.push(normalizedDetail)
      }
      this.processes = this.processDetails.filter(isProcessDetailEffectivelyEnabled).map(toProcessCompatibilityRow)
    },

    applyPricingFallbackData() {
      this.materialTypes = [
        { id: 'fallback-raw', typeCode: 'RAW_MATERIAL', typeName: '原材料', enabled: true, sortNo: 0 }
      ]
      this.materials = [
        { id: 'fallback-45', materialCode: 'MAT-FALLBACK-45', materialName: '45#', materialTypeId: 'fallback-raw', materialTypeCode: 'RAW_MATERIAL', materialTypeName: '原材料', materialCategory: '钢铁', density: 7850, platePrice: 7.1, barPrice: 10, tubePrice: 10, enabled: true, sortNo: 0 }
      ]
      this.processes = [
        { id: 'fallback-mill', processDetailId: 'fallback-mill', processName: '铣', detailName: '铣', processCode: 'MILLING', categoryName: '铣削', hourlyRate: 0, calcType: '工时', sourceType: 'process' },
        { id: 'fallback-cnc1', processDetailId: 'fallback-cnc1', processName: 'CNC1', detailName: 'CNC1', processCode: 'CNC', categoryName: '数控', hourlyRate: 0, calcType: '工时', sourceType: 'process' }
      ]
      this.processCategories = [
        { id: 'fallback-milling', processCode: 'MILLING', categoryName: '铣削', hourlyRate: 0, enabled: true },
        { id: 'fallback-cnc', processCode: 'CNC', categoryName: '数控', hourlyRate: 0, enabled: true }
      ]
      this.processDetails = [
        { id: 'fallback-mill', categoryId: 'fallback-milling', processCode: 'MILLING', categoryName: '铣削', detailName: '铣', hourlyRate: 0, enabled: true },
        { id: 'fallback-cnc1', categoryId: 'fallback-cnc', processCode: 'CNC', categoryName: '数控', detailName: 'CNC1', hourlyRate: 0, enabled: true }
      ]
      this.extraFeeItems = []
      this.pricingMarkupFees = [
        { id: 'fallback-freight', pricingScope: 'NORMAL', feeCategory: 'BASIC_PERCENT', feeName: '运输费', ratePercent: 5, sortNo: 10, enabled: true, systemRequired: false },
        { id: 'fallback-management', pricingScope: 'NORMAL', feeCategory: 'BASIC_PERCENT', feeName: '管理费', ratePercent: 5, sortNo: 20, enabled: true, systemRequired: false },
        { id: 'fallback-profit', pricingScope: 'NORMAL', feeCategory: 'BASIC_PERCENT', feeName: '利润', ratePercent: 15, sortNo: 30, enabled: true, systemRequired: false },
        { id: 'fallback-tax', pricingScope: 'NORMAL', feeCategory: 'TAX', feeName: '税率', ratePercent: 13, sortNo: 40, enabled: true, systemRequired: true },
        { id: 'fallback-customer-weight', pricingScope: 'NORMAL', feeCategory: 'CUSTOMER_WEIGHT', feeName: '客户权重', ratePercent: 0, sortNo: 50, enabled: false, systemRequired: true },
        { id: 'fallback-assembly-management', pricingScope: 'ASSEMBLY', feeCategory: 'BASIC_PERCENT', feeName: '组装管理费', ratePercent: 5, sortNo: 10, enabled: true, systemRequired: false },
        { id: 'fallback-assembly-profit', pricingScope: 'ASSEMBLY', feeCategory: 'BASIC_PERCENT', feeName: '组装利润', ratePercent: 15, sortNo: 20, enabled: true, systemRequired: false },
        { id: 'fallback-assembly-tax', pricingScope: 'ASSEMBLY', feeCategory: 'TAX', feeName: '税率', ratePercent: 13, sortNo: 30, enabled: true, systemRequired: true },
        { id: 'fallback-assembly-customer-weight', pricingScope: 'ASSEMBLY', feeCategory: 'CUSTOMER_WEIGHT', feeName: '客户权重', ratePercent: 0, sortNo: 40, enabled: false, systemRequired: true }
      ]
      this.loadedAt = Date.now()
      this.baseLoadedAt = this.loadedAt
      this.pricingMarkupFeesLoadedAt = this.loadedAt
      this.lastIncludePricingMarkupFees = true
    }
  }
})
