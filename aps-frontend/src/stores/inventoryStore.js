import { defineStore } from 'pinia'
import {
  addPartStock as addPartStockRequest,
  confirmMaterialLock as confirmMaterialLockRequest,
  confirmPartLock as confirmPartLockRequest,
  createMaterial as createMaterialRequest,
  createPartInventory as createPartInventoryRequest,
  deleteMaterial as deleteMaterialRequest,
  deletePartInventory as deletePartInventoryRequest,
  listMaterialLockCustomers,
  listMaterialLocks,
  listMaterialShortages,
  listMaterials,
  listPartCustomers,
  listPartInventoryPage,
  listPartLocks,
  listPartPending,
  listPartProductionRequirements,
  modifyPartInventory as modifyPartInventoryRequest,
  releaseMaterialLock as releaseMaterialLockRequest,
  releasePartLock as releasePartLockRequest,
  transferPartPending as transferPartPendingRequest
} from '../services/inventoryApi'

const DEFAULT_PAGE = 1
const DEFAULT_PAGE_SIZE = 20
const DEFAULT_LIMIT = '200'

export const useInventoryStore = defineStore('inventory', {
  state: () => ({
    partCustomers: [],
    partInventory: [],
    partInventoryFilters: {},
    partPage: DEFAULT_PAGE,
    partPageSize: DEFAULT_PAGE_SIZE,
    partTotal: 0,
    activePartRow: null,
    partPendingRows: [],
    partLocks: [],
    partLockFilters: {},
    partProductionRequirements: [],

    materials: [],
    materialFilters: { limit: DEFAULT_LIMIT },
    materialLocks: [],
    materialLockFilters: {},
    materialLockCustomers: [],
    materialShortages: []
  }),
  getters: {
    partTotalPages: (state) => Math.max(1, Math.ceil(state.partTotal / state.partPageSize))
  },
  actions: {
    async loadPartCustomers() {
      this.partCustomers = await listPartCustomers()
      return this.partCustomers
    },

    async loadPartInventoryPage(filters = {}) {
      this.partInventoryFilters = cleanFilters(filters)
      const result = await listPartInventoryPage(buildQuery({
        ...this.partInventoryFilters,
        page: this.partPage,
        pageSize: this.partPageSize
      }))
      this.partInventory = result.records || []
      this.partTotal = result.total || 0

      if (this.partInventory.length === 0 && this.partTotal > 0 && this.partPage > this.partTotalPages) {
        this.partPage = this.partTotalPages
        await this.loadPartInventoryPage(this.partInventoryFilters)
      }

      return this.partInventory
    },

    async reloadPartInventory() {
      return this.loadPartInventoryPage(this.partInventoryFilters)
    },

    async createPartInventory(payload) {
      const result = await createPartInventoryRequest(payload)
      await Promise.all([this.reloadPartInventory(), this.loadPartCustomers()])
      return result
    },

    async deletePartInventory(id) {
      const result = await deletePartInventoryRequest(id)
      if (this.activePartRow?.id === id) {
        this.clearActivePartRow()
      }
      await Promise.all([this.reloadPartInventory(), this.loadPartCustomers()])
      return result
    },

    setActivePartRow(row) {
      this.activePartRow = row || null
    },

    clearActivePartRow() {
      this.activePartRow = null
      this.partPendingRows = []
    },

    async loadPartPending(partId) {
      if (!partId) {
        this.partPendingRows = []
        return this.partPendingRows
      }
      this.partPendingRows = await listPartPending(partId)
      return this.partPendingRows
    },

    async refreshActivePartRow() {
      const activeId = this.activePartRow?.id
      await this.reloadPartInventory()
      if (!activeId) return null

      const updated = this.partInventory.find((row) => row.id === activeId)
      if (updated) {
        this.activePartRow = updated
        await this.loadPartPending(activeId)
        return updated
      }

      this.clearActivePartRow()
      return null
    },

    async addPartStock(payload) {
      const result = await addPartStockRequest(payload)
      await this.refreshActivePartRow()
      return result
    },

    async transferPartPending(payload) {
      const result = await transferPartPendingRequest(payload)
      await this.refreshActivePartRow()
      return result
    },

    async modifyPartInventory(payload) {
      const result = await modifyPartInventoryRequest(payload)
      await this.refreshActivePartRow()
      return result
    },

    async loadPartLocks(filters = {}) {
      this.partLockFilters = cleanFilters(filters)
      this.partLocks = await listPartLocks(buildQuery({ limit: DEFAULT_LIMIT, ...this.partLockFilters }))
      return this.partLocks
    },

    async confirmPartLock(id) {
      const result = await confirmPartLockRequest(id)
      await this.loadPartLocks(this.partLockFilters)
      return result
    },

    async releasePartLock(id) {
      const result = await releasePartLockRequest(id)
      await this.loadPartLocks(this.partLockFilters)
      return result
    },

    async loadPartProductionRequirements(filters = {}) {
      this.partProductionRequirements = await listPartProductionRequirements({
        limit: DEFAULT_LIMIT,
        ...cleanFilters(filters)
      })
      return this.partProductionRequirements
    },

    async loadMaterials(filters = {}) {
      this.materialFilters = {
        limit: DEFAULT_LIMIT,
        ...cleanFilters(filters)
      }
      this.materials = await listMaterials(this.materialFilters)
      return this.materials
    },

    async createMaterial(payload) {
      const result = await createMaterialRequest(payload)
      await this.loadMaterials(this.materialFilters)
      return result
    },

    async deleteMaterial(id) {
      const result = await deleteMaterialRequest(id)
      await this.loadMaterials(this.materialFilters)
      return result
    },

    async loadMaterialLockCustomers() {
      this.materialLockCustomers = await listMaterialLockCustomers()
      return this.materialLockCustomers
    },

    async loadMaterialLocks(filters = {}) {
      this.materialLockFilters = cleanFilters(filters)
      this.materialLocks = await listMaterialLocks(buildQuery({ limit: DEFAULT_LIMIT, ...this.materialLockFilters }))
      return this.materialLocks
    },

    async confirmMaterialLock(id) {
      const result = await confirmMaterialLockRequest(id)
      await this.loadMaterialLocks(this.materialLockFilters)
      return result
    },

    async releaseMaterialLock(id) {
      const result = await releaseMaterialLockRequest(id)
      await this.loadMaterialLocks(this.materialLockFilters)
      return result
    },

    async loadMaterialShortages(filters = {}) {
      this.materialShortages = await listMaterialShortages({
        limit: DEFAULT_LIMIT,
        ...cleanFilters(filters)
      })
      return this.materialShortages
    }
  }
})

const buildQuery = (params) => {
  const query = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      query.set(key, String(value))
    }
  })
  return query
}

const cleanFilters = (params = {}) => Object.fromEntries(
  Object.entries(params).filter(([, value]) => value !== undefined && value !== null && value !== '')
)
