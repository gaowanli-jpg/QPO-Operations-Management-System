import { API_BASE_URL } from './config'
import { authState, clearAuth, updateAuthUser } from './auth'

export const apiUrl = (path) => `${API_BASE_URL}${path}`

export const request = async (path, options = {}) => {
  const config = {
    ...options,
    headers: {
      ...(options.body ? { 'Content-Type': 'application/json' } : {}),
      ...(authState.token ? { Authorization: `Bearer ${authState.token}` } : {}),
      ...(options.headers || {})
    }
  }
  const response = await fetch(apiUrl(path), config)
  const text = await response.text()
  const payload = text ? JSON.parse(text) : null
  if (response.status === 401) {
    clearAuth()
    redirectToLogin()
  }
  if (response.status === 428) {
    updateAuthUser({ forcePasswordChange: true })
  }
  if (!response.ok || payload?.success === false) {
    const error = new Error(payload?.message || `Request failed: ${response.status}`)
    error.status = response.status
    error.payload = payload
    throw error
  }
  return payload
}

const redirectToLogin = () => {
  if (window.location.pathname === '/login') {
    return
  }
  const current = `${window.location.pathname}${window.location.search}${window.location.hash}`
  window.location.assign(`/login?redirect=${encodeURIComponent(current)}`)
}
