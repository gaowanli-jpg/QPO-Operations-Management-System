import { beforeEach, describe, expect, test, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { configureApiAuth } from '../api'
import { changePassword, getCurrentUser, login, logout } from '../services/authApi'
import { configureApsApiAuth, useAuthStore } from './authStore'

vi.mock('../api', () => ({
  configureApiAuth: vi.fn()
}))

vi.mock('../services/authApi', () => ({
  login: vi.fn(),
  getCurrentUser: vi.fn(),
  changePassword: vi.fn(),
  logout: vi.fn()
}))

const createLocalStorage = (initialValues = {}) => {
  const values = new Map(Object.entries(initialValues))
  return {
    getItem: vi.fn((key) => values.get(key) ?? null),
    setItem: vi.fn((key, value) => values.set(key, String(value))),
    removeItem: vi.fn((key) => values.delete(key)),
    clear: vi.fn(() => values.clear()),
    dump: () => Object.fromEntries(values.entries())
  }
}

const activateStore = (storage = createLocalStorage()) => {
  vi.stubGlobal('localStorage', storage)
  setActivePinia(createPinia())
  return useAuthStore()
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.unstubAllGlobals()
})

describe('APS authStore', () => {
  test('initializes token and user from compatible localStorage keys', () => {
    const authStore = activateStore(createLocalStorage({
      aps_token: 'saved-token',
      aps_user: JSON.stringify({ username: 'planner', role: 'ADMIN', displayName: 'Planner' })
    }))

    expect(authStore.token).toBe('saved-token')
    expect(authStore.user).toEqual({ username: 'planner', role: 'ADMIN', displayName: 'Planner' })
    expect(authStore.verified).toBe(false)
    expect(authStore.isAuthenticated).toBe(true)
    expect(authStore.displayName).toBe('Planner')
    expect(authStore.roleName).toBe('管理员')
  })

  test('setAuth persists token and user in localStorage', () => {
    const storage = createLocalStorage()
    const authStore = activateStore(storage)

    authStore.setAuth('token-1', { username: 'planner', role: 'ADMIN' })

    expect(authStore.token).toBe('token-1')
    expect(authStore.user).toEqual({ username: 'planner', role: 'ADMIN' })
    expect(authStore.verified).toBe(true)
    expect(storage.dump()).toEqual({
      aps_token: 'token-1',
      aps_user: JSON.stringify({ username: 'planner', role: 'ADMIN' })
    })
  })

  test('clearAuth removes local auth state and persisted keys', () => {
    const storage = createLocalStorage()
    const authStore = activateStore(storage)
    authStore.setAuth('token-1', { username: 'planner', role: 'ADMIN' })

    authStore.clearAuth()

    expect(authStore.token).toBe('')
    expect(authStore.user).toBe(null)
    expect(authStore.verified).toBe(false)
    expect(storage.dump()).toEqual({})
  })

  test('updateUser patches and persists the current user', () => {
    const storage = createLocalStorage()
    const authStore = activateStore(storage)
    authStore.setAuth('token-1', { username: 'planner', role: 'ADMIN' })

    authStore.updateUser({ displayName: 'PMC Planner', forcePasswordChange: true })

    expect(authStore.user).toEqual({
      username: 'planner',
      role: 'ADMIN',
      displayName: 'PMC Planner',
      forcePasswordChange: true
    })
    expect(authStore.mustChangePassword).toBe(true)
    expect(storage.dump().aps_user).toBe(JSON.stringify(authStore.user))
  })

  test('verifyAuth refreshes current user once token is present', async () => {
    const authStore = activateStore()
    authStore.setAuth('token-1', { username: 'planner', role: 'ADMIN' })
    authStore.verified = false
    getCurrentUser.mockResolvedValue({
      success: true,
      data: { username: 'planner', role: 'ADMIN', displayName: 'PMC Planner' }
    })

    await expect(authStore.verifyAuth()).resolves.toBe(true)

    expect(getCurrentUser).toHaveBeenCalledTimes(1)
    expect(authStore.verified).toBe(true)
    expect(authStore.user.displayName).toBe('PMC Planner')
  })

  test('verifyAuth clears auth when current user request fails or payload is invalid', async () => {
    const authStore = activateStore()
    authStore.setAuth('token-1', { username: 'planner', role: 'ADMIN' })
    authStore.verified = false
    getCurrentUser.mockResolvedValue({ success: false })

    await expect(authStore.verifyAuth()).resolves.toBe(false)

    expect(authStore.token).toBe('')
    expect(authStore.user).toBe(null)
    expect(authStore.verified).toBe(false)
  })

  test('login persists successful auth response', async () => {
    const authStore = activateStore()
    login.mockResolvedValue({
      success: true,
      token: 'token-2',
      user: { username: 'planner', role: 'ADMIN' },
      message: '登录成功'
    })

    const result = await authStore.login({ username: 'planner', password: 'secret' })

    expect(result.message).toBe('登录成功')
    expect(login).toHaveBeenCalledWith({ username: 'planner', password: 'secret' })
    expect(authStore.token).toBe('token-2')
    expect(authStore.user).toEqual({ username: 'planner', role: 'ADMIN' })
  })

  test('hasRole allows ADMIN to access role guarded pages', () => {
    const authStore = activateStore()
    authStore.setAuth('token-1', { username: 'admin', role: 'ADMIN' })

    expect(authStore.hasRole(['PMC'])).toBe(true)
  })

  test('changePassword clears forced password change flag', async () => {
    const authStore = activateStore()
    authStore.setAuth('token-1', { username: 'planner', role: 'ADMIN', forcePasswordChange: true })
    changePassword.mockResolvedValue({ success: true })

    await authStore.changePassword({
      oldPassword: 'old-secret',
      newPassword: 'new-secret',
      confirmPassword: 'new-secret'
    })

    expect(changePassword).toHaveBeenCalledTimes(1)
    expect(authStore.mustChangePassword).toBe(false)
    expect(authStore.user.forcePasswordChange).toBe(false)
  })

  test('logout clears local auth even when server logout fails', async () => {
    const authStore = activateStore()
    authStore.setAuth('token-1', { username: 'planner', role: 'ADMIN' })
    logout.mockRejectedValue(new Error('offline'))

    await authStore.logout()

    expect(logout).toHaveBeenCalledTimes(1)
    expect(authStore.token).toBe('')
    expect(authStore.user).toBe(null)
  })

  test('configureApsApiAuth binds token, unauthorized redirect, and password-required handlers', async () => {
    const authStore = activateStore()
    authStore.setAuth('token-1', { username: 'planner', role: 'ADMIN' })
    const router = {
      currentRoute: {
        value: {
          path: '/assignment/pending',
          fullPath: '/assignment/pending?page=1'
        }
      },
      replace: vi.fn()
    }

    configureApsApiAuth(router)
    const handlers = configureApiAuth.mock.calls[0][0]

    expect(handlers.getToken()).toBe('token-1')

    handlers.onPasswordChangeRequired()
    expect(authStore.user.forcePasswordChange).toBe(true)

    handlers.onUnauthorized()
    expect(authStore.token).toBe('')
    expect(router.replace).toHaveBeenCalledWith({
      path: '/login',
      query: { redirect: '/assignment/pending?page=1' }
    })
  })
})
