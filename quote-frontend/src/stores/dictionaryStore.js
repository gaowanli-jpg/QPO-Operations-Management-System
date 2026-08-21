import { defineStore } from 'pinia'
import {
  listAssemblyMarkupFees,
  listExtraFeeItems,
  listMarkupFees,
  listMaterialTypes,
  listMaterials,
  listProcessCategories,
  listProcessDetails
} from '../services/dictApi'

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
  processName: item.detailName,
  calcType: '工时',
  sourceType: 'process'
})

const formatHourlyRate = (value) => Number(value ?? 0).toFixed(2)
const processRateSourceText = (item = {}) => item.detailHourlyRate == null ? '继承大类' : '具体价'

export const useDictionaryStore = defineStore('dictionary', {
  state: () => ({
    materialTypes: [],
    materials: [],
    processes: [],
    processCategories: [],
    processDetails: [],
    extraFeeItems: [],
    markupFees: [],
    assemblyMarkupFees: [],
    loadedAt: null,
    loading: false,
    lastIncludeMarkupFees: false,
    lastIncludeAllExtraFees: false
  }),
  getters: {
    combinedProcessOptions: (state) => [
      ...state.processDetails.map(item => ({
        optionKey: `process-detail-${item.id}`,
        processDetailId: item.id,
        processCategoryId: item.categoryId,
        categoryId: item.categoryId,
        processCode: item.processCode,
        categoryName: item.categoryName,
        detailName: item.detailName,
        detailRemarks: item.detailRemarks,
        enabled: item.enabled,
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
      includeMarkupFees = false,
      includeAllExtraFees = false,
      force = false
    } = {}) {
      const hasRequiredCache = this.loadedAt
        && (!includeMarkupFees || this.lastIncludeMarkupFees)
        && (!includeAllExtraFees || this.lastIncludeAllExtraFees)
      if (hasRequiredCache && !force) {
        return {
          materialTypes: this.materialTypes,
          materials: this.materials,
          processes: this.processes,
          processCategories: this.processCategories,
          processDetails: this.processDetails,
          extraFeeItems: this.extraFeeItems,
          markupFees: this.markupFees,
          assemblyMarkupFees: this.assemblyMarkupFees
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
        if (includeMarkupFees) {
          requests.push(listMarkupFees())
          requests.push(listAssemblyMarkupFees())
        }
        const [materialTypes, materials, processCategories, processDetails, extraFeeItems, markupFees, assemblyMarkupFees] = await Promise.all(requests)
        this.materialTypes = materialTypes || []
        this.materials = materials || []
        this.processCategories = processCategories || []
        this.processDetails = processDetails || []
        this.processes = this.processDetails.map(toProcessCompatibilityRow)
        this.extraFeeItems = extraFeeItems || []
        if (includeMarkupFees) {
          this.markupFees = markupFees || []
          this.assemblyMarkupFees = assemblyMarkupFees || []
        }
        this.loadedAt = Date.now()
        this.lastIncludeMarkupFees = this.lastIncludeMarkupFees || includeMarkupFees
        this.lastIncludeAllExtraFees = this.lastIncludeAllExtraFees || includeAllExtraFees
        return {
          materialTypes: this.materialTypes,
          materials: this.materials,
          processes: this.processes,
          processCategories: this.processCategories,
          processDetails: this.processDetails,
          extraFeeItems: this.extraFeeItems,
          markupFees: this.markupFees,
          assemblyMarkupFees: this.assemblyMarkupFees
        }
      } finally {
        this.loading = false
      }
    },

    async reloadAfterMutation() {
      return this.loadBaseDicts({
        includeMarkupFees: true,
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
      this.processes = this.processDetails.map(toProcessCompatibilityRow)
    },

    applyPricingFallbackData() {
      this.materialTypes = [
        { id: 'fallback-raw', typeCode: 'RAW_MATERIAL', typeName: '原材料', enabled: true, sortNo: 0 }
      ]
      this.materials = [
        { id: 'fallback-45', materialCode: 'MAT-FALLBACK-45', materialName: '45#', materialTypeId: 'fallback-raw', materialTypeCode: 'RAW_MATERIAL', materialTypeName: '原材料', materialCategory: '钢铁', density: 7850, plateDensity: 7850, platePrice: 7.1, barDensity: 7850, barPrice: 10, tubeDensity: 7850, tubePrice: 10, enabled: true, sortNo: 0 }
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
      this.markupFees = [
        { id: 'fallback-freight', feeName: '运输费', ratePercent: 5, enabled: true },
        { id: 'fallback-management', feeName: '管理费', ratePercent: 5, enabled: true },
        { id: 'fallback-profit', feeName: '利润', ratePercent: 15, enabled: true },
        { id: 'fallback-tax', feeName: '税费', ratePercent: 13, enabled: true }
      ]
      this.assemblyMarkupFees = [
        { id: 'fallback-assembly-management', feeName: '组装管理费', ratePercent: 5, enabled: true },
        { id: 'fallback-assembly-profit', feeName: '组装利润', ratePercent: 15, enabled: true },
        { id: 'fallback-assembly-tax', feeName: '组装税费', ratePercent: 13, enabled: true }
      ]
      this.loadedAt = Date.now()
      this.lastIncludeMarkupFees = true
    }
  }
})
