import { beforeEach, describe, expect, test, vi } from 'vitest'
import { request } from '../api'
import * as authApi from './authApi'

vi.mock('../api', () => ({
  request: vi.fn()
}))

beforeEach(() => {
  request.mockReset()
})

describe('authApi', () => {
  test('login posts credentials to ERP auth endpoint', async () => {
    await authApi.login({ username: 'admin', password: 'secret' })

    expect(request).toHaveBeenCalledWith('/api/auth/login', {
      method: 'POST',
      data: { username: 'admin', password: 'secret' }
    })
  })

  test('changePassword posts password payload', async () => {
    const payload = {
      oldPassword: 'old-secret',
      newPassword: 'new-secret',
      confirmPassword: 'new-secret'
    }

    await authApi.changePassword(payload)

    expect(request).toHaveBeenCalledWith('/api/auth/change-password', {
      method: 'POST',
      data: payload
    })
  })
})
