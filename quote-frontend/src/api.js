import axios from 'axios'
import { API_BASE_URL } from './config'

export const apiUrl = (path) => `${API_BASE_URL}${path}`

export const apiClient = axios.create({
  baseURL: API_BASE_URL
})

let getAuthToken = () => ''
let onUnauthorized = () => {}
let onPasswordChangeRequired = () => {}

export const configureApiAuth = (handlers = {}) => {
  getAuthToken = handlers.getToken || getAuthToken
  onUnauthorized = handlers.onUnauthorized || onUnauthorized
  onPasswordChangeRequired = handlers.onPasswordChangeRequired || onPasswordChangeRequired
}

apiClient.interceptors.request.use((config) => {
  const token = getAuthToken()
  if (token) {
    config.headers = config.headers || {}
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

apiClient.interceptors.response.use(
  (response) => {
    handleApiStatus(response.status)
    return response
  },
  (error) => {
    handleApiStatus(error.response?.status)
    return Promise.reject(error)
  }
)

export const buildRequestConfig = (path, options = {}) => {
  const {
    method = 'GET',
    body,
    data,
    headers,
    ...rest
  } = options
  const nextHeaders = headers ? { ...headers } : undefined
  const config = {
    url: path,
    method,
    ...rest
  }

  if (data !== undefined) {
    config.data = data
  } else if (body !== undefined) {
    config.data = parseBody(body, nextHeaders)
    if (!hasHeader(nextHeaders, 'Content-Type') && !(body instanceof FormData)) {
      config.headers = {
        ...(nextHeaders || {}),
        'Content-Type': 'application/json'
      }
    }
  }

  if (!config.headers && nextHeaders) {
    config.headers = nextHeaders
  }

  return config
}

export const unwrapResponsePayload = (payload, status) => {
  if (payload?.success === false) {
    const error = new Error(payload.message || `Request failed: ${status}`)
    error.status = status
    error.payload = payload
    error.response = { status, data: payload }
    throw error
  }
  return payload
}

export const request = async (path, options = {}) => {
  try {
    const response = await apiClient.request(buildRequestConfig(path, options))
    return unwrapResponsePayload(response.data, response.status)
  } catch (error) {
    throw await normalizeRequestError(error)
  }
}

export const downloadFile = async (path, fallbackName = 'download.xlsx') => {
  try {
    const response = await apiClient.get(path, { responseType: 'blob' })
    const blobUrl = window.URL.createObjectURL(response.data)
    const link = document.createElement('a')

    link.href = blobUrl
    link.download = extractFilename(response.headers['content-disposition'], fallbackName)
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.URL.revokeObjectURL(blobUrl)

    return { success: true }
  } catch (error) {
    return {
      success: false,
      message: await readBlobErrorMessage(error)
    }
  }
}

export const extractFilename = (contentDisposition, fallbackName) => {
  if (!contentDisposition) return fallbackName

  const encodedMatch = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i)
  if (encodedMatch?.[1]) {
    try {
      return decodeURIComponent(encodedMatch[1])
    } catch {
      return encodedMatch[1]
    }
  }

  const plainMatch = contentDisposition.match(/filename="?([^";]+)"?/i)
  return plainMatch?.[1] || fallbackName
}

export const readBlobErrorMessage = async (error) => {
  const data = error.response?.data
  if (data instanceof Blob) {
    try {
      const text = await data.text()
      if (!text) return ''
      return JSON.parse(text).message || text
    } catch {
      return ''
    }
  }
  return data?.message || error.message || ''
}

const normalizeRequestError = async (error) => {
  if (error.payload) {
    return error
  }

  const payload = error.response?.data
  const status = error.response?.status
  const message = payload?.message || error.message || `Request failed: ${status}`
  const nextError = new Error(message)
  nextError.status = status
  nextError.payload = payload
  return nextError
}

const handleApiStatus = (status) => {
  if (status === 401) {
    onUnauthorized()
  }
  if (status === 428) {
    onPasswordChangeRequired()
  }
}

const parseBody = (body, headers) => {
  if (typeof body !== 'string') return body
  if (!isJsonBody(headers)) return body
  try {
    return JSON.parse(body)
  } catch {
    return body
  }
}

const isJsonBody = (headers) => {
  const contentType = getHeader(headers, 'Content-Type')
  return !contentType || contentType.includes('application/json')
}

const hasHeader = (headers, name) => getHeader(headers, name) !== undefined

const getHeader = (headers, name) => {
  if (!headers) return undefined
  const match = Object.keys(headers).find((key) => key.toLowerCase() === name.toLowerCase())
  return match ? headers[match] : undefined
}
