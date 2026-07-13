import { request } from '../api'

export const login = (credentials) => request('/api/auth/login', {
  method: 'POST',
  data: credentials
})

export const getCurrentUser = () => request('/api/auth/me')

export const changePassword = (payload) => request('/api/auth/change-password', {
  method: 'POST',
  data: payload
})

export const logout = () => request('/api/auth/logout', {
  method: 'POST'
})
