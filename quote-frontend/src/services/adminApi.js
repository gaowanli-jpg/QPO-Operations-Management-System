import { request } from '../api'

export const listUsers = () => request('/api/system/users')

export const saveUser = (payload) => {
  const id = payload?.id
  return request(id ? `/api/system/users/${id}` : '/api/system/users', {
    method: id ? 'PUT' : 'POST',
    data: payload
  })
}

export const changeUserPassword = (userId, payload) => request(`/api/system/users/${userId}/reset-password`, {
  method: 'PUT',
  data: payload
})

export const updateUserStatus = (userId, enabled) => request(`/api/system/users/${userId}/status`, {
  method: 'PUT',
  data: { enabled }
})

export const deleteUser = (userId) => request(`/api/system/users/${userId}`, {
  method: 'DELETE'
})

export const listRoles = () => request('/api/system/roles')

export const saveRole = (payload) => {
  const id = payload?.id
  return request(id ? `/api/system/roles/${id}` : '/api/system/roles', {
    method: id ? 'PUT' : 'POST',
    data: payload
  })
}

export const deleteRole = (roleId) => request(`/api/system/roles/${roleId}`, {
  method: 'DELETE'
})

export const listPermissions = () => request('/api/system/permissions')

export const getRolePermissions = (roleId) => request(`/api/system/roles/${roleId}/permissions`)

export const assignRolePermissions = (roleId, permissionCodes = []) => request(`/api/system/roles/${roleId}/permissions`, {
  method: 'POST',
  data: { permissionCodes }
})

export const getRoleDataScope = (roleId) => request(`/api/system/roles/${roleId}/data-scope`)

export const assignRoleDataScope = (roleId, payload) => request(`/api/system/roles/${roleId}/data-scope`, {
  method: 'POST',
  data: payload
})

export const listDataScopeDepartments = () => request('/api/system/data-scope/departments')

export const listDataScopeCustomers = () => request('/api/system/data-scope/customers')

export const getQuoteTemplate = () => request('/api/admin/quote-template')

export const saveQuoteTemplate = (formData) => request('/api/admin/quote-template', {
  method: 'POST',
  data: formData,
  headers: { 'Content-Type': 'multipart/form-data' }
})
