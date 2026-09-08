import { beforeEach, describe, expect, test, vi } from 'vitest'
import {
  apiClient,
  buildRequestConfig,
  createAuthenticatedObjectUrl,
  isDrawingFolderUrl,
  listDrawingFolderFiles,
  openAuthenticatedFileInNewWindow,
  revokeObjectUrl,
  unwrapResponsePayload
} from './api'

beforeEach(() => {
  vi.restoreAllMocks()
  vi.stubGlobal('window', {
    URL: {
      createObjectURL: vi.fn(() => 'blob:preview'),
      revokeObjectURL: vi.fn()
    },
    open: vi.fn()
  })
})

describe('api client helpers', () => {
  test('buildRequestConfig uses axios data instead of legacy body options', () => {
    expect(buildRequestConfig('/api/auth/login', {
      method: 'POST',
      data: { username: 'admin' }
    })).toEqual({
      url: '/api/auth/login',
      method: 'POST',
      data: { username: 'admin' },
    })
  })

  test('unwrapResponsePayload throws backend failure messages', () => {
    expect(() => unwrapResponsePayload({ success: false, message: 'denied' }, 403))
      .toThrow('denied')
  })

  test('unwrapResponsePayload preserves response shape for component error handlers', () => {
    try {
      unwrapResponsePayload({ success: false, message: 'denied' }, 403)
    } catch (error) {
      expect(error.response).toEqual({
        status: 403,
        data: { success: false, message: 'denied' }
      })
    }
  })

  test('createAuthenticatedObjectUrl downloads protected files through apiClient', async () => {
    const blob = new Blob(['pdf'])
    vi.spyOn(apiClient, 'get').mockResolvedValue({ data: blob })

    await expect(createAuthenticatedObjectUrl('/uploads/orders/1/drawing.pdf')).resolves.toBe('blob:preview')

    expect(apiClient.get).toHaveBeenCalledWith('/uploads/orders/1/drawing.pdf', { responseType: 'blob' })
    expect(window.URL.createObjectURL).toHaveBeenCalledWith(blob)
  })

  test('openAuthenticatedFileInNewWindow reports popup blockers without requesting the file', async () => {
    window.open.mockReturnValue(null)
    const getSpy = vi.spyOn(apiClient, 'get')

    await expect(openAuthenticatedFileInNewWindow('/uploads/orders/1/drawing.pdf')).resolves.toEqual({
      success: false,
      blocked: true
    })

    expect(getSpy).not.toHaveBeenCalled()
  })

  test('revokeObjectUrl only revokes blob URLs', () => {
    revokeObjectUrl('blob:preview')
    revokeObjectUrl('/uploads/orders/1/drawing.pdf')

    expect(window.URL.revokeObjectURL).toHaveBeenCalledTimes(1)
    expect(window.URL.revokeObjectURL).toHaveBeenCalledWith('blob:preview')
  })

  test('isDrawingFolderUrl recognizes drawing folder public paths only', () => {
    expect(isDrawingFolderUrl('/uploads/drawing-folders/RFQ/12/01ABC/')).toBe(true)
    expect(isDrawingFolderUrl('http://qpo.local/uploads/drawing-folders/SALES_ORDER/7/01ABC/')).toBe(true)
    expect(isDrawingFolderUrl('/uploads/drawings/RFQ-1.pdf')).toBe(false)
    expect(isDrawingFolderUrl('/uploads/drawing-folders/RFQ/12/01ABC/a.pdf')).toBe(false)
    expect(isDrawingFolderUrl('')).toBe(false)
  })

  test('listDrawingFolderFiles queries the secured drawing folder endpoint with paging and keyword', async () => {
    const files = [{ fileName: 'A.pdf', publicPath: '/uploads/drawing-folders/RFQ/12/01ABC/A.pdf' }]
    vi.spyOn(apiClient, 'request').mockResolvedValue({
      status: 200,
      data: { success: true, records: files, total: 1, page: 2, pageSize: 50 }
    })

    await expect(listDrawingFolderFiles('/uploads/drawing-folders/RFQ/12/01ABC/', {
      keyword: 'A-100',
      page: 2,
      pageSize: 50
    })).resolves.toEqual({
      success: true,
      records: files,
      total: 1,
      page: 2,
      pageSize: 50
    })

    expect(apiClient.request).toHaveBeenCalledWith({
      url: '/api/business-files/drawing-folder',
      method: 'GET',
      params: {
        folderUrl: '/uploads/drawing-folders/RFQ/12/01ABC/',
        keyword: 'A-100',
        page: 2,
        pageSize: 50
      }
    })
  })
})
