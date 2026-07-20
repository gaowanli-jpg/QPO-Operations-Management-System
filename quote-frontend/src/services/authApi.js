import { request } from '../api'

export const login = (credentials) => request('/api/auth/login', {
  method: 'POST',
  data: credentials
})

export const getCurrentUser = () => request('/api/auth/me')

export const getPermissionInfo = () => request('/api/auth/permission-info')

export const changePassword = (payload) => request('/api/auth/change-password', {
  method: 'POST',
  data: payload
})

export const updateMyProfile = (payload) => request('/api/auth/me/profile', {
  method: 'PUT',
  data: payload
})

export const logout = () => request('/api/auth/logout', {
  method: 'POST'
})
