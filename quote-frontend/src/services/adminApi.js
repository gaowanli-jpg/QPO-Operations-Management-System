import { request } from '../api'

export const listUsers = () => request('/api/admin/users')

export const saveUser = (payload) => {
  const id = payload?.id
  return request(id ? `/api/admin/users/${id}` : '/api/admin/users', {
    method: id ? 'PUT' : 'POST',
    data: payload
  })
}

export const changeUserPassword = (userId, payload) => request(`/api/admin/users/${userId}`, {
  method: 'PUT',
  data: payload
})

export const updateUserStatus = (userId, enabled) => request(`/api/admin/users/${userId}`, {
  method: 'PUT',
  data: { enabled }
})

export const deleteUser = (userId) => request(`/api/admin/users/${userId}`, {
  method: 'DELETE'
})

export const getQuoteTemplate = () => request('/api/admin/quote-template')

export const saveQuoteTemplate = (formData) => request('/api/admin/quote-template', {
  method: 'POST',
  data: formData,
  headers: { 'Content-Type': 'multipart/form-data' }
})
