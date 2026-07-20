import { beforeEach, describe, expect, test, vi } from 'vitest'
import { request } from '../api'
import * as adminApi from './adminApi'

vi.mock('../api', () => ({
  request: vi.fn()
}))

beforeEach(() => {
  request.mockReset()
})

describe('adminApi', () => {
  test('listUsers loads admin users', async () => {
    await adminApi.listUsers()

    expect(request).toHaveBeenCalledWith('/api/system/users')
  })

  test('saveUser creates a user without id', async () => {
    const payload = { username: 'new-user' }

    await adminApi.saveUser(payload)

    expect(request).toHaveBeenCalledWith('/api/system/users', {
      method: 'POST',
      data: payload
    })
  })

  test('saveUser updates a user with id', async () => {
    const payload = { id: 7, username: 'existing-user' }

    await adminApi.saveUser(payload)

    expect(request).toHaveBeenCalledWith('/api/system/users/7', {
      method: 'PUT',
      data: payload
    })
  })

  test('changeUserPassword puts password payload by user id', async () => {
    const payload = { password: 'secret' }

    await adminApi.changeUserPassword(7, payload)

    expect(request).toHaveBeenCalledWith('/api/system/users/7/reset-password', {
      method: 'PUT',
      data: payload
    })
  })

  test('updateUserStatus puts enabled status by user id', async () => {
    await adminApi.updateUserStatus(7, false)

    expect(request).toHaveBeenCalledWith('/api/system/users/7/status', {
      method: 'PUT',
      data: { enabled: false }
    })
  })

  test('deleteUser deletes by user id', async () => {
    await adminApi.deleteUser(7)

    expect(request).toHaveBeenCalledWith('/api/system/users/7', {
      method: 'DELETE'
    })
  })

  test('loads roles and permissions for role management', async () => {
    await adminApi.listRoles()
    await adminApi.listPermissions()
    await adminApi.listPermissionTree()

    expect(request).toHaveBeenNthCalledWith(1, '/api/system/roles')
    expect(request).toHaveBeenNthCalledWith(2, '/api/system/permissions')
    expect(request).toHaveBeenNthCalledWith(3, '/api/system/permissions/tree')
  })

  test('saves role and role authorization settings', async () => {
    await adminApi.saveRole({ id: 8, roleName: 'Manager' })
    await adminApi.assignRolePermissions(8, ['market:rfq:view'])
    await adminApi.assignRoleDataScope(8, { dataScope: 'CUSTOMER', customerNames: ['ACME'] })

    expect(request).toHaveBeenNthCalledWith(1, '/api/system/roles/8', {
      method: 'PUT',
      data: { id: 8, roleName: 'Manager' }
    })
    expect(request).toHaveBeenNthCalledWith(2, '/api/system/roles/8/permissions', {
      method: 'POST',
      data: { permissionCodes: ['market:rfq:view'] }
    })
    expect(request).toHaveBeenNthCalledWith(3, '/api/system/roles/8/data-scope', {
      method: 'POST',
      data: { dataScope: 'CUSTOMER', customerNames: ['ACME'] }
    })
  })

  test('getQuoteTemplate loads template config', async () => {
    await adminApi.getQuoteTemplate()

    expect(request).toHaveBeenCalledWith('/api/admin/quote-template')
  })

  test('saveQuoteTemplate uploads template form data', async () => {
    const formData = new FormData()

    await adminApi.saveQuoteTemplate(formData)

    expect(request).toHaveBeenCalledWith('/api/admin/quote-template', {
      method: 'POST',
      data: formData,
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  })
})
