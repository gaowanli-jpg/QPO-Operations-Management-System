import { beforeEach, describe, expect, test, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import {
  assignDepartment,
  deleteTasks,
  getPmcTask,
  listDepartmentCenterTasks,
  listPmcTasks,
  releaseAssignment,
  syncErpDeliveryItems as syncErpDeliveryItemsRequest
} from '../services/taskApi'
import { useTaskStore } from './taskStore'

vi.mock('../services/taskApi', () => ({
  assignDepartment: vi.fn(),
  deleteTasks: vi.fn(),
  getPmcTask: vi.fn(),
  listDepartmentCenterTasks: vi.fn(),
  listPmcTasks: vi.fn(),
  releaseAssignment: vi.fn(),
  syncErpDeliveryItems: vi.fn()
}))

const queryObject = (call) => Object.fromEntries(call[0])

beforeEach(() => {
  vi.clearAllMocks()
  setActivePinia(createPinia())
})

describe('taskStore', () => {
  test('loads PMC tasks with pagination and page filters', async () => {
    listPmcTasks.mockResolvedValue({
      records: [{ id: 1 }, { id: 2 }],
      total: 2
    })
    const taskStore = useTaskStore()
    taskStore.selectedTaskIds = [1, 99]

    await taskStore.loadPmcTasks({
      assignmentStatus: 'PENDING',
      customerName: 'ACME',
      sortMode: 'MATERIAL'
    })

    expect(queryObject(listPmcTasks.mock.calls[0])).toEqual({
      assignmentStatus: 'PENDING',
      page: '1',
      pageSize: '20',
      customerName: 'ACME',
      sortMode: 'MATERIAL'
    })
    expect(taskStore.tasks).toEqual([{ id: 1 }, { id: 2 }])
    expect(taskStore.total).toBe(2)
    expect(taskStore.selectedTaskIds).toEqual([1])
  })

  test('clamps PMC page after mutations leave the current page empty', async () => {
    listPmcTasks
      .mockResolvedValueOnce({ records: [], total: 30 })
      .mockResolvedValueOnce({ records: [{ id: 21 }], total: 30 })
    const taskStore = useTaskStore()
    taskStore.page = 3

    await taskStore.loadPmcTasks({ assignmentStatus: 'PENDING' })

    expect(taskStore.page).toBe(2)
    expect(listPmcTasks).toHaveBeenCalledTimes(2)
    expect(queryObject(listPmcTasks.mock.calls[1])).toMatchObject({
      assignmentStatus: 'PENDING',
      page: '2',
      pageSize: '20'
    })
  })

  test('tracks selected PMC tasks on the visible page', () => {
    const taskStore = useTaskStore()
    taskStore.tasks = [{ id: 1 }, { id: 2 }]

    taskStore.toggleTaskSelect(1, true)
    taskStore.toggleTaskSelect(2, true)
    taskStore.toggleTaskSelect(1, false)
    taskStore.toggleAllTasks(true)
    taskStore.toggleAllTasks(false)

    expect(taskStore.selectedTaskIds).toEqual([])
  })

  test('syncErpDeliveryItems stores sync result and refreshes the PMC list', async () => {
    syncErpDeliveryItemsRequest.mockResolvedValue({ syncRunId: 'sync-1', importedCount: 3 })
    listPmcTasks.mockResolvedValue({ records: [{ id: 1 }], total: 1 })
    const taskStore = useTaskStore()

    const result = await taskStore.syncErpDeliveryItems()

    expect(result).toEqual({ syncRunId: 'sync-1', importedCount: 3 })
    expect(taskStore.syncResult).toEqual({ syncRunId: 'sync-1', importedCount: 3 })
    expect(listPmcTasks).toHaveBeenCalledTimes(1)
  })

  test('deleteSelectedTasks deletes selected ids and refreshes the PMC list', async () => {
    deleteTasks.mockResolvedValue({ success: true })
    listPmcTasks.mockResolvedValue({ records: [], total: 0 })
    const taskStore = useTaskStore()
    taskStore.selectedTaskIds = [1, 2]

    await taskStore.deleteSelectedTasks({ assignmentStatus: 'PENDING' })

    expect(deleteTasks).toHaveBeenCalledWith([1, 2])
    expect(taskStore.selectedTaskIds).toEqual([])
    expect(queryObject(listPmcTasks.mock.calls[0])).toMatchObject({
      assignmentStatus: 'PENDING'
    })
  })

  test('assignTasks posts assignment payload, removes assigned ids, and refreshes', async () => {
    assignDepartment.mockResolvedValue({ success: true })
    listPmcTasks.mockResolvedValue({ records: [{ id: 3 }], total: 1 })
    const taskStore = useTaskStore()
    taskStore.selectedTaskIds = [1, 2, 3]

    await taskStore.assignTasks(
      [1, 2],
      { departmentId: 7, assignedBy: 'PMC', remark: 'rush' },
      { assignmentStatus: 'PENDING' }
    )

    expect(assignDepartment).toHaveBeenCalledWith({
      ids: [1, 2],
      departmentId: 7,
      assignedBy: 'PMC',
      remark: 'rush'
    })
    expect(taskStore.selectedTaskIds).toEqual([3])
    expect(taskStore.tasks).toEqual([{ id: 3 }])
  })

  test('loadPmcTask stores selected PMC task detail', async () => {
    getPmcTask.mockResolvedValue({ task: { id: 5 }, processRows: [] })
    const taskStore = useTaskStore()

    await taskStore.loadPmcTask(5)

    expect(taskStore.selectedTaskId).toBe(5)
    expect(taskStore.taskDetail).toEqual({ task: { id: 5 }, processRows: [] })
  })

  test('releaseSelectedAssignment releases detail task and refreshes assigned tasks', async () => {
    releaseAssignment.mockResolvedValue({ success: true })
    listPmcTasks.mockResolvedValue({ records: [], total: 0 })
    const taskStore = useTaskStore()
    taskStore.selectedTaskId = 5
    taskStore.taskDetail = { task: { id: 5 } }

    await taskStore.releaseSelectedAssignment()

    expect(releaseAssignment).toHaveBeenCalledWith(5)
    expect(taskStore.selectedTaskId).toBe(null)
    expect(taskStore.taskDetail).toBe(null)
    expect(queryObject(listPmcTasks.mock.calls[0])).toMatchObject({
      assignmentStatus: 'ASSIGNED'
    })
  })

  test('loads department center tasks with independent pagination', async () => {
    listDepartmentCenterTasks.mockResolvedValue({
      records: [{ id: 11 }],
      total: 1
    })
    const taskStore = useTaskStore()

    await taskStore.loadDepartmentCenterTasks({
      departmentId: 2,
      status: 'ACTIVE'
    })

    expect(queryObject(listDepartmentCenterTasks.mock.calls[0])).toEqual({
      status: 'ACTIVE',
      page: '1',
      pageSize: '20',
      departmentId: '2'
    })
    expect(taskStore.departmentTasks).toEqual([{ id: 11 }])
    expect(taskStore.departmentTotal).toBe(1)
  })
})
