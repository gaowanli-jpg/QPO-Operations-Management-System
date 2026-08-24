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
  test('login posts credentials to QPO auth endpoint', async () => {
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

  test('updateMyProfile puts self-service contact fields only', async () => {
    const payload = {
      phone: '13800000000',
      email: 'user@example.com'
    }

    await authApi.updateMyProfile(payload)

    expect(request).toHaveBeenCalledWith('/api/auth/me/profile', {
      method: 'PUT',
      data: payload
    })
  })

  test('exchangeDataCenterSso posts signed data center login payload', async () => {
    const payload = {
      appCode: 'QPO',
      username: 'market',
      displayName: '市场用户',
      timestamp: 1780000000000,
      nonce: 'dc-sso-nonce-1',
      redirect: '/dashboard',
      signature: 'signed-ticket'
    }

    await authApi.exchangeDataCenterSso(payload)

    expect(request).toHaveBeenCalledWith('/api/auth/datacenter-sso/exchange', {
      method: 'POST',
      data: payload
    })
  })
})
