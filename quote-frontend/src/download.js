import axios from 'axios'

const parseFilename = (contentDisposition, fallbackName) => {
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

const readErrorMessage = async (error) => {
  const data = error.response?.data
  if (data instanceof Blob) {
    try {
      const text = await data.text()
      return JSON.parse(text).message || text
    } catch {
      return ''
    }
  }
  return data?.message || error.message || ''
}

export const downloadByAxios = async (url, fallbackName = 'download.xlsx') => {
  try {
    const response = await axios.get(url, { responseType: 'blob' })
    const filename = parseFilename(response.headers['content-disposition'], fallbackName)
    const blobUrl = window.URL.createObjectURL(response.data)
    const link = document.createElement('a')

    link.href = blobUrl
    link.download = filename
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.URL.revokeObjectURL(blobUrl)

    return { success: true }
  } catch (error) {
    return {
      success: false,
      message: await readErrorMessage(error)
    }
  }
}
