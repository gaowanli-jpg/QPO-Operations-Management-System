import { beforeEach, describe, expect, test, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { changePassword, getCurrentUser, getPermissionInfo, login, logout, updateMyProfile } from '../services/authApi'
import { useAuthStore } from './authStore'

vi.mock('../services/authApi', () => ({
  login: vi.fn(),
  getCurrentUser: vi.fn(),
  getPermissionInfo: vi.fn(),
  changePassword: vi.fn(),
  updateMyProfile: vi.fn(),
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
      user: { username: 'admin', role: 'ADMIN' },
      roles: ['SUPER_ADMIN'],
      permissions: ['system:user:view', 'system:audit:view'],
      menus: [{ path: '/users', title: '用户管理', permission: 'system:user:view' }]
    })
    const authStore = useAuthStore()

    const result = await authStore.login({ username: 'admin', password: 'secret' })

    expect(result.user.role).toBe('ADMIN')
    expect(authStore.token).toBe('token-1')
    expect(authStore.user).toEqual({ username: 'admin', role: 'ADMIN' })
    expect(authStore.roles).toEqual(['SUPER_ADMIN'])
    expect(authStore.permissions).toEqual(['system:user:view', 'system:audit:view'])
    expect(authStore.menus).toEqual([{ path: '/users', title: '用户管理', permission: 'system:user:view' }])
    expect(authStore.hasPermission('system:user:view')).toBe(true)
    expect(authStore.hasAnyPermission(['missing:permission', 'system:audit:view'])).toBe(true)
    expect(localStorage.setItem).toHaveBeenCalledWith('erp_token', 'token-1')
    expect(localStorage.setItem).toHaveBeenCalledWith('erp_user', JSON.stringify({ username: 'admin', role: 'ADMIN' }))
    expect(localStorage.setItem).toHaveBeenCalledWith('erp_roles', JSON.stringify(['SUPER_ADMIN']))
    expect(localStorage.setItem).toHaveBeenCalledWith('erp_permissions', JSON.stringify(['system:user:view', 'system:audit:view']))
    expect(localStorage.setItem).toHaveBeenCalledWith(
      'erp_menus',
      JSON.stringify([{ path: '/users', title: '用户管理', permission: 'system:user:view' }])
    )
  })

  test('verifyAuth refreshes current user and permission info through authApi', async () => {
    getCurrentUser.mockResolvedValue({
      success: true,
      data: { username: 'market', role: 'MARKET' }
    })
    getPermissionInfo.mockResolvedValue({
      success: true,
      data: {
        roles: ['MARKET_USER'],
        permissions: ['market:rfq:view', 'market:quote-process:order-select'],
        menus: [{ path: '/market', title: '新单导入', permission: 'market:rfq:view' }]
      }
    })
    const authStore = useAuthStore()
    authStore.setAuth('token-2', { username: 'market', role: 'MARKET' })
    authStore.verified = false

    await expect(authStore.verifyAuth()).resolves.toBe(true)

    expect(getCurrentUser).toHaveBeenCalledTimes(1)
    expect(getPermissionInfo).toHaveBeenCalledTimes(1)
    expect(authStore.verified).toBe(true)
    expect(authStore.user).toEqual({ username: 'market', role: 'MARKET' })
    expect(authStore.roles).toEqual(['MARKET_USER'])
    expect(authStore.permissions).toEqual(['market:rfq:view', 'market:quote-process:order-select'])
  })

  test('verifyAuth clears auth when current user response is invalid', async () => {
    getCurrentUser.mockResolvedValue({ success: false })
    const authStore = useAuthStore()
    authStore.setAuth('expired-token', { username: 'engineer', role: 'ENGINEER' })
    authStore.verified = false

    await expect(authStore.verifyAuth()).resolves.toBe(false)

    expect(authStore.token).toBe('')
    expect(authStore.user).toBeNull()
    expect(authStore.roles).toEqual([])
    expect(authStore.permissions).toEqual([])
    expect(authStore.menus).toEqual([])
    expect(localStorage.removeItem).toHaveBeenCalledWith('erp_token')
    expect(localStorage.removeItem).toHaveBeenCalledWith('erp_user')
    expect(localStorage.removeItem).toHaveBeenCalledWith('erp_roles')
    expect(localStorage.removeItem).toHaveBeenCalledWith('erp_permissions')
    expect(localStorage.removeItem).toHaveBeenCalledWith('erp_menus')
  })

  test('hasRole lets ADMIN access every protected role', () => {
    const authStore = useAuthStore()
    authStore.setAuth('token-3', { username: 'admin', role: 'ADMIN' })

    expect(authStore.hasRole(['ENGINEER'])).toBe(true)
  })

  test('changePassword keeps auth usable without forced password state', async () => {
    changePassword.mockResolvedValue({ success: true })
    const authStore = useAuthStore()
    authStore.setAuth('token-4', { username: 'admin', role: 'ADMIN' })

    await authStore.changePassword({
      oldPassword: 'old-secret',
      newPassword: 'new-secret',
      confirmPassword: 'new-secret'
    })

    expect('mustChangePassword' in authStore).toBe(false)
    expect(authStore.user).toEqual({ username: 'admin', role: 'ADMIN' })
  })

  test('updateMyProfile updates cached user contact fields without touching permissions', async () => {
    updateMyProfile.mockResolvedValue({
      success: true,
      data: {
        username: 'admin',
        displayName: '管理员',
        phone: '13800000000',
        email: 'admin@example.com',
        deptName: '系统管理',
        postName: '管理员'
      }
    })
    const authStore = useAuthStore()
    authStore.setAuth('token-6', {
      username: 'admin',
      displayName: '管理员',
      phone: '',
      email: '',
      deptName: '系统管理',
      postName: '管理员'
    }, {
      roles: ['SUPER_ADMIN'],
      permissions: ['system:user:view'],
      menus: [{ path: '/users', title: '用户管理' }]
    })

    await authStore.updateMyProfile({
      phone: '13800000000',
      email: 'admin@example.com'
    })

    expect(updateMyProfile).toHaveBeenCalledWith({
      phone: '13800000000',
      email: 'admin@example.com'
    })
    expect(authStore.user.phone).toBe('13800000000')
    expect(authStore.user.email).toBe('admin@example.com')
    expect(authStore.roles).toEqual(['SUPER_ADMIN'])
    expect(authStore.permissions).toEqual(['system:user:view'])
    expect(JSON.parse(localStorage.getItem('erp_user'))).toEqual(expect.objectContaining({
      username: 'admin',
      phone: '13800000000',
      email: 'admin@example.com'
    }))
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
