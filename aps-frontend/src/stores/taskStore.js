import { defineStore } from 'pinia'
import { field } from '../helpers'
import {
  assignDepartment as assignDepartmentRequest,
  deleteTasks as deleteTasksRequest,
  getPmcTask,
  listDepartmentCenterTasks,
  listPmcTasks,
  releaseAssignment as releaseAssignmentRequest,
  syncErpDeliveryItems as syncErpDeliveryItemsRequest
} from '../services/taskApi'

const DEFAULT_PAGE = 1
const DEFAULT_PAGE_SIZE = 20

export const useTaskStore = defineStore('tasks', {
  state: () => ({
    tasks: [],
    selectedTaskIds: [],
    selectedTaskId: null,
    taskDetail: null,
    page: DEFAULT_PAGE,
    pageSize: DEFAULT_PAGE_SIZE,
    total: 0,
    syncResult: null,

    departmentTasks: [],
    departmentPage: DEFAULT_PAGE,
    departmentPageSize: DEFAULT_PAGE_SIZE,
    departmentTotal: 0
  }),
  getters: {
    totalPages: (state) => Math.max(1, Math.ceil(state.total / state.pageSize)),
    departmentTotalPages: (state) => Math.max(1, Math.ceil(state.departmentTotal / state.departmentPageSize))
  },
  actions: {
    async loadPmcTasks(filters = {}) {
      const query = buildQuery({
        ...filters,
        page: this.page,
        pageSize: this.pageSize
      })
      const result = await listPmcTasks(query)
      this.tasks = result.records || []
      this.total = result.total || 0

      if (this.tasks.length === 0 && this.total > 0 && this.page > this.totalPages) {
        this.page = this.totalPages
        await this.loadPmcTasks(filters)
        return
      }

      this.retainVisibleSelection()
    },

    async loadDepartmentCenterTasks(filters = {}) {
      const query = buildQuery({
        ...filters,
        page: this.departmentPage,
        pageSize: this.departmentPageSize
      })
      const result = await listDepartmentCenterTasks(query)
      this.departmentTasks = result.records || []
      this.departmentTotal = result.total || 0

      if (
        this.departmentTasks.length === 0 &&
        this.departmentTotal > 0 &&
        this.departmentPage > this.departmentTotalPages
      ) {
        this.departmentPage = this.departmentTotalPages
        await this.loadDepartmentCenterTasks(filters)
      }
    },

    async loadPmcTask(id) {
      this.selectedTaskId = id
      this.taskDetail = await getPmcTask(id)
      return this.taskDetail
    },

    resetDetail() {
      this.selectedTaskId = null
      this.taskDetail = null
    },

    toggleTaskSelect(id, checked) {
      if (!id) return
      if (checked) {
        if (!this.selectedTaskIds.includes(id)) {
          this.selectedTaskIds = [...this.selectedTaskIds, id]
        }
        return
      }
      this.selectedTaskIds = this.selectedTaskIds.filter((selectedId) => selectedId !== id)
    },

    toggleAllTasks(checked) {
      this.selectedTaskIds = checked ? this.tasks.map((task) => field(task, 'id')).filter(Boolean) : []
    },

    clearSelection() {
      this.selectedTaskIds = []
    },

    retainVisibleSelection() {
      const visibleIds = new Set(this.tasks.map((task) => field(task, 'id')))
      this.selectedTaskIds = this.selectedTaskIds.filter((id) => visibleIds.has(id))
    },

    async syncErpDeliveryItems(filters = {}) {
      this.syncResult = await syncErpDeliveryItemsRequest()
      await this.loadPmcTasks(filters)
      return this.syncResult
    },

    async deleteSelectedTasks(filters = {}) {
      if (this.selectedTaskIds.length === 0) return null
      const ids = [...this.selectedTaskIds]
      await deleteTasksRequest(ids)
      this.clearSelection()
      await this.loadPmcTasks(filters)
      return ids
    },

    async assignTasks(ids, payload, filters = {}) {
      const nextIds = [...ids]
      await assignDepartmentRequest({
        ids: nextIds,
        ...payload
      })
      this.selectedTaskIds = this.selectedTaskIds.filter((id) => !nextIds.includes(id))
      await this.loadPmcTasks(filters)
      return nextIds
    },

    async releaseSelectedAssignment(filters = { assignmentStatus: 'ASSIGNED' }) {
      if (!this.selectedTaskId) return null
      const id = this.selectedTaskId
      await releaseAssignmentRequest(id)
      this.resetDetail()
      await this.loadPmcTasks(filters)
      return id
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
