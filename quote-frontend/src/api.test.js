import { describe, expect, test } from 'vitest'
import { buildRequestConfig, unwrapResponsePayload } from './api'

describe('api client helpers', () => {
  test('buildRequestConfig converts JSON string bodies into request data', () => {
    expect(buildRequestConfig('/api/auth/login', {
      method: 'POST',
      body: '{"username":"admin"}'
    })).toEqual({
      url: '/api/auth/login',
      method: 'POST',
      data: { username: 'admin' },
      headers: { 'Content-Type': 'application/json' }
    })
  })

  test('unwrapResponsePayload throws backend failure messages', () => {
    expect(() => unwrapResponsePayload({ success: false, message: 'denied' }, 403))
      .toThrow('denied')
  })
})
