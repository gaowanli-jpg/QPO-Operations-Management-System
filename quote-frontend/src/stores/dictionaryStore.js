import { defineStore } from 'pinia'
import {
  listAssemblyMarkupFees,
  listExtraFeeItems,
  listMarkupFees,
  listMaterials,
  listProcesses
} from '../services/dictApi'

export const useDictionaryStore = defineStore('dictionary', {
  state: () => ({
    materials: [],
    processes: [],
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
      ...state.processes.map(item => ({
        optionKey: `process-${item.id}`,
        processName: item.processName,
        label: `${item.processName}（${item.calcType || '工时'}）`,
        calcType: item.calcType || '工时',
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
          extraFeeItems: this.extraFeeItems,
          markupFees: this.markupFees,
          assemblyMarkupFees: this.assemblyMarkupFees
        }
      }

      this.loading = true
      try {
        const requests = [
          listMaterials(),
          listProcesses(),
          listExtraFeeItems({ includeDisabled: includeAllExtraFees })
        ]
        if (includeMarkupFees) {
          requests.push(listMarkupFees())
          requests.push(listAssemblyMarkupFees())
        }
        const [materials, processes, extraFeeItems, markupFees, assemblyMarkupFees] = await Promise.all(requests)
        this.materials = materials || []
        this.processes = processes || []
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
        { id: 'fallback-mill', processName: '铣' },
        { id: 'fallback-cnc1', processName: 'CNC1' }
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
