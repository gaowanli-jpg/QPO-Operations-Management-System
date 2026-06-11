import axios from 'axios'
import { apiUrl } from './config'

export const revokeObjectUrl = (url) => {
  if (url?.startsWith('blob:')) {
    window.URL.revokeObjectURL(url)
  }
}

export const readFileErrorMessage = async (error) => {
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

export const createAuthenticatedObjectUrl = async (url) => {
  const response = await axios.get(apiUrl(url), { responseType: 'blob' })
  return window.URL.createObjectURL(response.data)
}

export const openAuthenticatedFileInNewWindow = async (url) => {
  const targetWindow = window.open('', '_blank')
  if (!targetWindow) {
    return { success: false, blocked: true }
  }
  targetWindow.opener = null

  try {
    const objectUrl = await createAuthenticatedObjectUrl(url)
    targetWindow.location.href = objectUrl
    window.setTimeout(() => revokeObjectUrl(objectUrl), 60_000)
    return { success: true }
  } catch (error) {
    targetWindow.close()
    return {
      success: false,
      message: await readFileErrorMessage(error)
    }
  }
}
