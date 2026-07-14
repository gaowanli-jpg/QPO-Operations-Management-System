import { beforeEach, describe, expect, test, vi } from 'vitest'
import {
  apiClient,
  buildRequestConfig,
  createAuthenticatedObjectUrl,
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
})
