const rawApiBaseUrl = import.meta.env.VITE_API_BASE_URL
export const API_BASE_URL = rawApiBaseUrl === undefined ? 'http://localhost:8080' : rawApiBaseUrl

export const apiUrl = (path) => {
  if (!path) return ''
  if (/^https?:\/\//i.test(path)) return path
  return `${API_BASE_URL}${path.startsWith('/') ? path : `/${path}`}`
}
