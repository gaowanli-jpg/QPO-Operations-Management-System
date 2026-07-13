import { beforeEach, describe, expect, test, vi } from 'vitest'
import { downloadFile, request } from '../api'
import * as authApi from './authApi'
import * as departmentApi from './departmentApi'
import * as inventoryApi from './inventoryApi'
import * as machineApi from './machineApi'
import * as processApi from './processApi'
import * as taskApi from './taskApi'
import * as userApi from './userApi'

vi.mock('../api', () => ({
  downloadFile: vi.fn(),
  request: vi.fn()
}))

beforeEach(() => {
  request.mockReset()
  downloadFile.mockReset()
})

describe('authApi', () => {
  test('login posts credentials', async () => {
    await authApi.login({ username: 'admin', password: 'secret' })

    expect(request).toHaveBeenCalledWith('/api/auth/login', {
      method: 'POST',
      data: { username: 'admin', password: 'secret' }
    })
  })
})

describe('departmentApi', () => {
  test('adjustDepartmentCapacity posts delta hours', async () => {
    await departmentApi.adjustDepartmentCapacity(8, 12)

    expect(request).toHaveBeenCalledWith('/api/aps/pmc/departments/8/adjust-capacity', {
      method: 'POST',
      data: { deltaHours: 12 }
    })
  })
})

describe('machineApi', () => {
  test('listMachineSetupData loads departments, machines, capabilities, and processes', async () => {
    request
      .mockResolvedValueOnce(['departments'])
      .mockResolvedValueOnce(['machines'])
      .mockResolvedValueOnce(['capabilities'])
      .mockResolvedValueOnce(['processes'])

    await expect(machineApi.listMachineSetupData()).resolves.toEqual({
      departments: ['departments'],
      machines: ['machines'],
      capabilities: ['capabilities'],
      processes: ['processes']
    })
    expect(request).toHaveBeenNthCalledWith(1, '/api/aps/pmc/departments')
    expect(request).toHaveBeenNthCalledWith(2, '/api/aps/pmc/machines')
    expect(request).toHaveBeenNthCalledWith(3, '/api/aps/pmc/machine-capabilities')
    expect(request).toHaveBeenNthCalledWith(4, '/api/aps/processes')
  })
})

describe('inventoryApi', () => {
  test('exportPartLocks delegates to download helper with generated query string', async () => {
    await inventoryApi.exportPartLocks({ limit: '200', customerName: 'ACME' })

    expect(downloadFile).toHaveBeenCalledWith(
      '/api/aps/inventory/parts/locks/export?limit=200&customerName=ACME',
      '零件锁定记录.xlsx'
    )
  })
})

describe('processApi', () => {
  test('syncProcessesFromErp appends importMissing query only when requested', async () => {
    await processApi.syncProcessesFromErp(true)

    expect(request).toHaveBeenCalledWith('/api/aps/processes/sync-from-erp?importMissing=true', {
      method: 'POST'
    })
  })
})

describe('taskApi', () => {
  test('assignDepartment posts assignment payload', async () => {
    const payload = { ids: [1, 2], departmentId: 4, assignedBy: 'PMC', remark: 'rush' }

    await taskApi.assignDepartment(payload)

    expect(request).toHaveBeenCalledWith('/api/aps/pmc/tasks/assign-department', {
      method: 'POST',
      data: payload
    })
  })
})

describe('userApi', () => {
  test('updateUser puts user payload by id', async () => {
    await userApi.updateUser(9, { enabled: false })

    expect(request).toHaveBeenCalledWith('/api/aps/admin/users/9', {
      method: 'PUT',
      data: { enabled: false }
    })
  })
})
