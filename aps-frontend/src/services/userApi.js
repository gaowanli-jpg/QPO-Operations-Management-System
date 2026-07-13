import { request } from '../api'

export const listUsers = () => request('/api/aps/admin/users')

export const createUser = (payload) => request('/api/aps/admin/users', {
  method: 'POST',
  data: payload
})

export const updateUser = (userId, payload) => request(`/api/aps/admin/users/${userId}`, {
  method: 'PUT',
  data: payload
})

export const deleteUser = (userId) => request(`/api/aps/admin/users/${userId}`, {
  method: 'DELETE'
})
