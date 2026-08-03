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

  test('manages organization department and post dictionaries', async () => {
    await adminApi.listDepartmentsTree()
    await adminApi.listDepartmentOptions()
    await adminApi.saveDepartment({ deptName: '市场部' })
    await adminApi.saveDepartment({ id: 3, deptName: '工程部' })
    await adminApi.updateDepartmentStatus(3, false)
    await adminApi.deleteDepartment(3)
    await adminApi.listPosts()
    await adminApi.listPostOptions()
    await adminApi.savePost({ postName: '核价员' })
    await adminApi.savePost({ id: 5, postName: '主管' })
    await adminApi.updatePostStatus(5, true)
    await adminApi.deletePost(5)

    expect(request).toHaveBeenNthCalledWith(1, '/api/system/departments/tree')
    expect(request).toHaveBeenNthCalledWith(2, '/api/system/departments/options')
    expect(request).toHaveBeenNthCalledWith(3, '/api/system/departments', {
      method: 'POST',
      data: { deptName: '市场部' }
    })
    expect(request).toHaveBeenNthCalledWith(4, '/api/system/departments/3', {
      method: 'PUT',
      data: { id: 3, deptName: '工程部' }
    })
    expect(request).toHaveBeenNthCalledWith(5, '/api/system/departments/3/status', {
      method: 'PUT',
      data: { enabled: false }
    })
    expect(request).toHaveBeenNthCalledWith(6, '/api/system/departments/3', {
      method: 'DELETE'
    })
    expect(request).toHaveBeenNthCalledWith(7, '/api/system/posts')
    expect(request).toHaveBeenNthCalledWith(8, '/api/system/posts/options')
    expect(request).toHaveBeenNthCalledWith(9, '/api/system/posts', {
      method: 'POST',
      data: { postName: '核价员' }
    })
    expect(request).toHaveBeenNthCalledWith(10, '/api/system/posts/5', {
      method: 'PUT',
      data: { id: 5, postName: '主管' }
    })
    expect(request).toHaveBeenNthCalledWith(11, '/api/system/posts/5/status', {
      method: 'PUT',
      data: { enabled: true }
    })
    expect(request).toHaveBeenNthCalledWith(12, '/api/system/posts/5', {
      method: 'DELETE'
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

  test('updateQuoteTemplateConfig saves configurable header row number', async () => {
    await adminApi.updateQuoteTemplateConfig({ headerRowNumber: 10 })

    expect(request).toHaveBeenCalledWith('/api/admin/quote-template/config', {
      method: 'PUT',
      data: { headerRowNumber: 10 }
    })
  })
})
