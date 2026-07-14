import { beforeEach, describe, expect, test, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  configureApiAuth: vi.fn(),
  getCurrentUser: vi.fn()
}))

vi.mock('./api', () => ({
  configureApiAuth: mocks.configureApiAuth
}))

vi.mock('./services/authApi', () => ({
  getCurrentUser: mocks.getCurrentUser
}))

const createStorage = () => {
  const store = new Map()
  return {
    getItem: vi.fn((key) => store.get(key) || null),
    setItem: vi.fn((key, value) => store.set(key, String(value))),
    removeItem: vi.fn((key) => store.delete(key)),
    dump: () => Object.fromEntries(store.entries())
  }
}

const loadAuth = async () => {
  vi.resetModules()
  return import('./auth')
}

beforeEach(() => {
  const storage = createStorage()
  vi.stubGlobal('localStorage', storage)
  vi.stubGlobal('window', {
    location: {
      pathname: '/tasks',
      search: '?page=1',
      hash: '#pending',
      assign: vi.fn()
    }
  })
  mocks.configureApiAuth.mockReset()
  mocks.getCurrentUser.mockReset()
})

describe('APS auth state', () => {
  test('setAuth persists token and user in localStorage', async () => {
    const auth = await loadAuth()

    auth.setAuth('token-1', { username: 'planner', role: 'ADMIN' })

    expect(auth.authState.token).toBe('token-1')
    expect(auth.authState.user).toEqual({ username: 'planner', role: 'ADMIN' })
    expect(auth.authState.verified).toBe(true)
    expect(localStorage.dump()).toEqual({
      aps_token: 'token-1',
      aps_user: JSON.stringify({ username: 'planner', role: 'ADMIN' })
    })
  })

  test('clearAuth removes persisted auth state', async () => {
    const auth = await loadAuth()
    auth.setAuth('token-1', { username: 'planner', role: 'ADMIN' })

    auth.clearAuth()

    expect(auth.authState.token).toBe('')
    expect(auth.authState.user).toBe(null)
    expect(localStorage.dump()).toEqual({})
  })

  test('verifyAuth refreshes current user once token is present', async () => {
    const auth = await loadAuth()
    auth.setAuth('token-1', { username: 'planner', role: 'ADMIN' })
    auth.authState.verified = false
    mocks.getCurrentUser.mockResolvedValue({
      success: true,
      data: { username: 'planner', role: 'ADMIN', displayName: 'PMC Planner' }
    })

    await expect(auth.verifyAuth()).resolves.toBe(true)

    expect(auth.authState.verified).toBe(true)
    expect(auth.authState.user.displayName).toBe('PMC Planner')
    expect(localStorage.dump().aps_user).toBe(JSON.stringify({
      username: 'planner',
      role: 'ADMIN',
      displayName: 'PMC Planner'
    }))
  })

  test('verifyAuth clears auth when current user request fails', async () => {
    const auth = await loadAuth()
    auth.setAuth('token-1', { username: 'planner', role: 'ADMIN' })
    auth.authState.verified = false
    mocks.getCurrentUser.mockRejectedValue(new Error('unauthorized'))

    await expect(auth.verifyAuth()).resolves.toBe(false)

    expect(auth.authState.token).toBe('')
    expect(auth.authState.user).toBe(null)
  })

  test('hasRole allows ADMIN to access role guarded pages', async () => {
    const auth = await loadAuth()
    auth.setAuth('token-1', { username: 'admin', role: 'ADMIN' })

    expect(auth.hasRole(['PMC'])).toBe(true)
  })

  test('configured unauthorized handler clears auth and redirects to login', async () => {
    const auth = await loadAuth()
    auth.setAuth('token-1', { username: 'planner', role: 'ADMIN' })

    const handlers = mocks.configureApiAuth.mock.calls[0][0]
    handlers.onUnauthorized()

    expect(auth.authState.token).toBe('')
    expect(window.location.assign).toHaveBeenCalledWith('/login?redirect=%2Ftasks%3Fpage%3D1%23pending')
  })

  test('configured password change handler marks current user', async () => {
    const auth = await loadAuth()
    auth.setAuth('token-1', { username: 'planner', role: 'ADMIN' })

    const handlers = mocks.configureApiAuth.mock.calls[0][0]
    handlers.onPasswordChangeRequired()

    expect(auth.authState.user.forcePasswordChange).toBe(true)
  })
})
