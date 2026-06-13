const rawApiBaseUrl = import.meta.env.VITE_API_BASE_URL
export const API_BASE_URL = rawApiBaseUrl === undefined ? 'http://localhost:8091' : rawApiBaseUrl
