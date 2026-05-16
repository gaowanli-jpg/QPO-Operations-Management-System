export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

export const apiUrl = (path) => {
  if (!path) return ''
  if (/^https?:\/\//i.test(path)) return path
  return `${API_BASE_URL}${path.startsWith('/') ? path : `/${path}`}`
}
