import { beforeAll, describe, expect, test, vi } from 'vitest'

let api

beforeAll(async () => {
  const store = new Map()
  vi.stubGlobal('localStorage', {
    getItem: (key) => store.get(key) || null,
    setItem: (key, value) => store.set(key, String(value)),
    removeItem: (key) => store.delete(key)
  })
  vi.stubGlobal('window', {
    location: {
      pathname: '/',
      search: '',
      hash: '',
      assign: vi.fn()
    }
  })
  api = await import('./api')
})

describe('buildRequestConfig', () => {
  test('converts legacy JSON body options into axios data', () => {
    const config = api.buildRequestConfig('/api/aps/pmc/tasks', {
      method: 'POST',
      body: JSON.stringify({ ids: [1, 2] })
    })

    expect(config).toEqual({
      url: '/api/aps/pmc/tasks',
      method: 'POST',
      data: { ids: [1, 2] },
      headers: { 'Content-Type': 'application/json' }
    })
  })

  test('prefers explicit data over legacy body', () => {
    const config = api.buildRequestConfig('/api/aps/admin/users', {
      method: 'PUT',
      body: JSON.stringify({ ignored: true }),
      data: { enabled: false }
    })

    expect(config.data).toEqual({ enabled: false })
  })
})

describe('unwrapResponsePayload', () => {
  test('returns successful response payload unchanged', () => {
    const payload = { records: [{ id: 1 }], total: 1 }

    expect(api.unwrapResponsePayload(payload, 200)).toBe(payload)
  })

  test('throws backend message when success is false', () => {
    expect(() => api.unwrapResponsePayload({ success: false, message: '保存失败' }, 200)).toThrow('保存失败')
  })
})

describe('extractFilename', () => {
  test('prefers RFC 5987 encoded filenames', () => {
    const header = "attachment; filename*=UTF-8''%E9%9B%B6%E4%BB%B6.xlsx"

    expect(api.extractFilename(header, 'fallback.xlsx')).toBe('零件.xlsx')
  })

  test('falls back when content disposition is absent', () => {
    expect(api.extractFilename('', 'fallback.xlsx')).toBe('fallback.xlsx')
  })
})

describe('readBlobErrorMessage', () => {
  test('reads JSON message from blob response data', async () => {
    const blob = new Blob([JSON.stringify({ message: '导出失败' })], { type: 'application/json' })

    await expect(api.readBlobErrorMessage({ response: { data: blob } })).resolves.toBe('导出失败')
  })
})
