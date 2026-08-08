import { defineStore } from 'pinia'
import {
  listAssemblyMarkupFees,
  listExtraFeeItems,
  listMarkupFees,
  listMaterials,
  listProcessCategories,
  listProcessDetails
} from '../services/dictApi'

const toProcessCompatibilityRow = (item = {}) => ({
  ...item,
  id: item.id,
  processDetailId: item.id,
  processCategoryId: item.categoryId,
  processName: item.detailName,
  calcType: '工时',
  sourceType: 'process'
})

export const useDictionaryStore = defineStore('dictionary', {
  state: () => ({
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
        processCode: item.processCode,
        categoryName: item.categoryName,
        detailName: item.detailName,
        hourlyRate: item.hourlyRate,
        processName: item.detailName,
        label: `${item.detailName}（${item.processCode || '-'} / ${item.categoryName || '-'} / ${Number(item.hourlyRate || 0).toFixed(2)}元/小时）`,
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
          listMaterials(),
          listProcessCategories(),
          listProcessDetails(),
          listExtraFeeItems({ includeDisabled: includeAllExtraFees })
        ]
        if (includeMarkupFees) {
          requests.push(listMarkupFees())
          requests.push(listAssemblyMarkupFees())
        }
        const [materials, processCategories, processDetails, extraFeeItems, markupFees, assemblyMarkupFees] = await Promise.all(requests)
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

    applyPricingFallbackData() {
      this.materials = [
        { id: 'fallback-45', materialName: '45#', density: 7850, plateDensity: 7850, platePrice: 7.1, barDensity: 7850, barPrice: 10, tubeDensity: 7850, tubePrice: 10 }
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
