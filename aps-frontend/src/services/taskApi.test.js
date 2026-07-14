import { beforeEach, describe, expect, test, vi } from 'vitest'
import { request } from '../api'
import * as taskApi from './taskApi'

vi.mock('../api', () => ({
  request: vi.fn()
}))

beforeEach(() => {
  request.mockReset()
})

describe('taskApi', () => {
  test('listPmcTasks forwards page filters as query string', async () => {
    await taskApi.listPmcTasks({ page: 2, size: 20, customerName: 'ACME' })

    expect(request).toHaveBeenCalledWith('/api/aps/pmc/tasks/page?page=2&size=20&customerName=ACME')
  })

  test('getPmcTask loads a task by id', async () => {
    await taskApi.getPmcTask(12)

    expect(request).toHaveBeenCalledWith('/api/aps/pmc/tasks/12')
  })

  test('releaseAssignment posts release action by task id', async () => {
    await taskApi.releaseAssignment(12)

    expect(request).toHaveBeenCalledWith('/api/aps/pmc/tasks/12/release-assignment', {
      method: 'POST'
    })
  })

  test('listDepartmentCenterTasks uses department center endpoint', async () => {
    await taskApi.listDepartmentCenterTasks({ departmentId: 5, status: 'IN_PROGRESS' })

    expect(request).toHaveBeenCalledWith('/api/aps/department-center/tasks/page?departmentId=5&status=IN_PROGRESS')
  })

  test('deleteTasks sends ids in DELETE body', async () => {
    await taskApi.deleteTasks([1, 2])

    expect(request).toHaveBeenCalledWith('/api/aps/pmc/tasks', {
      method: 'DELETE',
      data: { ids: [1, 2] }
    })
  })

  test('createTasks sends ids in POST body', async () => {
    await taskApi.createTasks([1, 2])

    expect(request).toHaveBeenCalledWith('/api/aps/pmc/tasks', {
      method: 'POST',
      data: { ids: [1, 2] }
    })
  })

  test('syncErpDeliveryItems posts ERP sync action', async () => {
    await taskApi.syncErpDeliveryItems()

    expect(request).toHaveBeenCalledWith('/api/aps/sync/erp-delivery-items', {
      method: 'POST'
    })
  })
})
