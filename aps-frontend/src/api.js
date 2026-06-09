import { API_BASE_URL } from './config'

export const apiUrl = (path) => `${API_BASE_URL}${path}`

export const request = async (path, options = {}) => {
  const config = {
    ...options,
    headers: {
      ...(options.body ? { 'Content-Type': 'application/json' } : {}),
      ...(options.headers || {})
    }
  }
  const response = await fetch(apiUrl(path), config)
  const text = await response.text()
  const payload = text ? JSON.parse(text) : null
  if (!response.ok || payload?.success === false) {
    const error = new Error(payload?.message || `Request failed: ${response.status}`)
    error.status = response.status
    error.payload = payload
    throw error
  }
  return payload
}
