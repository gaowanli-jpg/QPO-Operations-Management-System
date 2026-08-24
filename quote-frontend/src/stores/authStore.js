import { defineStore } from 'pinia'
import {
  changePassword as changePasswordRequest,
  getCurrentUser,
  getPermissionInfo,
  login as loginRequest,
  logout as logoutRequest,
  updateMyProfile as updateMyProfileRequest
} from '../services/authApi'

const TOKEN_KEY = 'erp_token'
const USER_KEY = 'erp_user'
const ROLES_KEY = 'erp_roles'
const PERMISSIONS_KEY = 'erp_permissions'
const MENUS_KEY = 'erp_menus'

const loadJson = (key, fallback) => {
  try {
    const value = JSON.parse(localStorage.getItem(key) || 'null')
    return value ?? fallback
  } catch {
    return fallback
  }
}

const normalizeArray = (value) => Array.isArray(value) ? value : []

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    user: loadJson(USER_KEY, null),
    roles: normalizeArray(loadJson(ROLES_KEY, [])),
    permissions: normalizeArray(loadJson(PERMISSIONS_KEY, [])),
    menus: normalizeArray(loadJson(MENUS_KEY, [])),
    verified: false
  }),
  getters: {
    isAuthenticated: (state) => !!state.token,
    displayName: (state) => state.user?.displayName || state.user?.username || '未登录',
    isSuperAdmin: (state) => state.user?.role === 'ADMIN' || state.roles.includes('SUPER_ADMIN'),
    roleName: (state) => ({
      ADMIN: '管理员',
      MARKET: '市场部',
      ENGINEER: '工程部',
      SUPER_ADMIN: '超级管理员',
      MARKET_USER: '市场专员',
      MARKET_MANAGER: '市场主管',
      ENGINEER_USER: '工程核价员',
      ENGINEER_MANAGER: '工程主管',
      AUDITOR: '审计员',
      READONLY: '只读查看员'
    }[state.user?.role] || state.roles.map((role) => ({
      SUPER_ADMIN: '超级管理员',
      MARKET_USER: '市场专员',
      MARKET_MANAGER: '市场主管',
      ENGINEER_USER: '工程核价员',
      ENGINEER_MANAGER: '工程主管',
      AUDITOR: '审计员',
      READONLY: '只读查看员'
    }[role] || role)).filter(Boolean).join(' / ') || '未知角色')
  },
  actions: {
    setAuth(token, user, permissionPayload = {}) {
      this.token = token || ''
      this.user = user || null
      this.verified = !!this.token
      this.applyPermissionInfo(permissionPayload)

      if (this.token) {
        localStorage.setItem(TOKEN_KEY, this.token)
      } else {
        localStorage.removeItem(TOKEN_KEY)
      }

      if (this.user) {
        localStorage.setItem(USER_KEY, JSON.stringify(this.user))
      } else {
        localStorage.removeItem(USER_KEY)
      }
    },

    applyPermissionInfo(payload = {}) {
      this.roles = normalizeArray(payload.roles)
      this.permissions = normalizeArray(payload.permissions)
      this.menus = normalizeArray(payload.menus)

      if (this.token) {
        localStorage.setItem(ROLES_KEY, JSON.stringify(this.roles))
        localStorage.setItem(PERMISSIONS_KEY, JSON.stringify(this.permissions))
        localStorage.setItem(MENUS_KEY, JSON.stringify(this.menus))
      } else {
        localStorage.removeItem(ROLES_KEY)
        localStorage.removeItem(PERMISSIONS_KEY)
        localStorage.removeItem(MENUS_KEY)
      }
    },

    clearAuth() {
      this.token = ''
      this.user = null
      this.roles = []
      this.permissions = []
      this.menus = []
      this.verified = false
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
      localStorage.removeItem(ROLES_KEY)
      localStorage.removeItem(PERMISSIONS_KEY)
      localStorage.removeItem(MENUS_KEY)
    },

    updateUser(patch) {
      this.user = {
        ...(this.user || {}),
        ...(patch || {})
      }
      localStorage.setItem(USER_KEY, JSON.stringify(this.user))
    },

    async login(credentials) {
      const response = await loginRequest(credentials)
      if (!response?.success) {
        throw new Error(response?.message || '登录失败')
      }
      this.setAuth(response.token, response.user, response)
      return response
    },

    async loadPermissionInfo() {
      const response = await getPermissionInfo()
      if (!response?.success || !response?.data) {
        this.applyPermissionInfo({})
        return false
      }
      this.applyPermissionInfo(response.data)
      return true
    },

    async verifyAuth() {
      if (!this.token) {
        this.clearAuth()
        return false
      }
      if (this.verified) {
        return true
      }
      try {
        const response = await getCurrentUser()
        if (!response?.success || !response?.data) {
          this.clearAuth()
          return false
        }
        this.updateUser(response.data)
        await this.loadPermissionInfo()
        this.verified = true
        return true
      } catch {
        this.clearAuth()
        return false
      }
    },

    hasRole(roles = []) {
      const expectedRoles = Array.isArray(roles) ? roles : [roles]
      const role = this.user?.role
      if (!role && this.roles.length === 0) return false
      if (role === 'ADMIN' || this.roles.includes('SUPER_ADMIN')) return true
      return expectedRoles.includes(role) || expectedRoles.some((item) => this.roles.includes(item))
    },

    hasPermission(permission) {
      if (!permission) return false
      if (this.isSuperAdmin) return true
      return this.permissions.includes(permission)
    },

    hasAnyPermission(permissions = []) {
      const expectedPermissions = Array.isArray(permissions) ? permissions : [permissions]
      if (this.isSuperAdmin) return true
      return expectedPermissions.some((permission) => this.permissions.includes(permission))
    },

    async changePassword(payload) {
      const response = await changePasswordRequest(payload)
      return response
    },

    async updateMyProfile(payload) {
      const response = await updateMyProfileRequest(payload)
      if (response?.success && response?.data) {
        this.updateUser(response.data)
      }
      return response
    },

    async logout() {
      try {
        await logoutRequest()
      } catch {
        // Local logout should not depend on server availability.
      } finally {
        this.clearAuth()
      }
    }
  }
})
