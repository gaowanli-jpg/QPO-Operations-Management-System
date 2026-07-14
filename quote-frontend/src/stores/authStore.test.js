import { beforeEach, describe, expect, test, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { changePassword, getCurrentUser, login, logout } from '../services/authApi'
import { useAuthStore } from './authStore'

vi.mock('../services/authApi', () => ({
  login: vi.fn(),
  getCurrentUser: vi.fn(),
  changePassword: vi.fn(),
  logout: vi.fn()
}))

const createLocalStorage = () => {
  const values = new Map()
  return {
    getItem: vi.fn((key) => values.get(key) ?? null),
    setItem: vi.fn((key, value) => values.set(key, String(value))),
    removeItem: vi.fn((key) => values.delete(key)),
    clear: vi.fn(() => values.clear())
  }
}

beforeEach(() => {
  vi.clearAllMocks()
  Object.defineProperty(globalThis, 'localStorage', {
    value: createLocalStorage(),
    configurable: true
  })
  setActivePinia(createPinia())
})

describe('authStore', () => {
  test('login persists token and user', async () => {
    login.mockResolvedValue({
      success: true,
      token: 'token-1',
      user: { username: 'admin', role: 'ADMIN' }
    })
    const authStore = useAuthStore()

    const result = await authStore.login({ username: 'admin', password: 'secret' })

    expect(result.user.role).toBe('ADMIN')
    expect(authStore.token).toBe('token-1')
    expect(authStore.user).toEqual({ username: 'admin', role: 'ADMIN' })
    expect(localStorage.setItem).toHaveBeenCalledWith('erp_token', 'token-1')
    expect(localStorage.setItem).toHaveBeenCalledWith('erp_user', JSON.stringify({ username: 'admin', role: 'ADMIN' }))
  })

  test('verifyAuth refreshes current user through authApi', async () => {
    getCurrentUser.mockResolvedValue({
      success: true,
      data: { username: 'market', role: 'MARKET' }
    })
    const authStore = useAuthStore()
    authStore.setAuth('token-2', { username: 'market', role: 'MARKET' })
    authStore.verified = false

    await expect(authStore.verifyAuth()).resolves.toBe(true)

    expect(getCurrentUser).toHaveBeenCalledTimes(1)
    expect(authStore.verified).toBe(true)
    expect(authStore.user).toEqual({ username: 'market', role: 'MARKET' })
  })

  test('verifyAuth clears auth when current user response is invalid', async () => {
    getCurrentUser.mockResolvedValue({ success: false })
    const authStore = useAuthStore()
    authStore.setAuth('expired-token', { username: 'engineer', role: 'ENGINEER' })
    authStore.verified = false

    await expect(authStore.verifyAuth()).resolves.toBe(false)

    expect(authStore.token).toBe('')
    expect(authStore.user).toBeNull()
    expect(localStorage.removeItem).toHaveBeenCalledWith('erp_token')
    expect(localStorage.removeItem).toHaveBeenCalledWith('erp_user')
  })

  test('hasRole lets ADMIN access every protected role', () => {
    const authStore = useAuthStore()
    authStore.setAuth('token-3', { username: 'admin', role: 'ADMIN' })

    expect(authStore.hasRole(['ENGINEER'])).toBe(true)
  })

  test('changePassword clears forced password flag', async () => {
    changePassword.mockResolvedValue({ success: true })
    const authStore = useAuthStore()
    authStore.setAuth('token-4', { username: 'admin', role: 'ADMIN', forcePasswordChange: true })

    await authStore.changePassword({
      oldPassword: 'old-secret',
      newPassword: 'new-secret',
      confirmPassword: 'new-secret'
    })

    expect(authStore.mustChangePassword).toBe(false)
    expect(authStore.user.forcePasswordChange).toBe(false)
  })

  test('logout clears local auth even when server logout fails', async () => {
    logout.mockRejectedValue(new Error('offline'))
    const authStore = useAuthStore()
    authStore.setAuth('token-5', { username: 'admin', role: 'ADMIN' })

    await authStore.logout()

    expect(authStore.token).toBe('')
    expect(authStore.user).toBeNull()
  })
})
