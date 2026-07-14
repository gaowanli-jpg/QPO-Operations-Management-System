import { defineStore } from 'pinia'
import {
  changePassword as changePasswordRequest,
  getCurrentUser,
  login as loginRequest,
  logout as logoutRequest
} from '../services/authApi'

const TOKEN_KEY = 'erp_token'
const USER_KEY = 'erp_user'

const loadUser = () => {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  } catch {
    return null
  }
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    user: loadUser(),
    verified: false
  }),
  getters: {
    isAuthenticated: (state) => !!state.token,
    mustChangePassword: (state) => !!state.user?.forcePasswordChange,
    displayName: (state) => state.user?.displayName || state.user?.username || '未登录',
    roleName: (state) => ({
      ADMIN: '管理员',
      MARKET: '市场部',
      ENGINEER: '工程部'
    }[state.user?.role] || '未知角色')
  },
  actions: {
    setAuth(token, user) {
      this.token = token || ''
      this.user = user || null
      this.verified = !!this.token

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

    clearAuth() {
      this.setAuth('', null)
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
      this.setAuth(response.token, response.user)
      return response
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
        this.verified = true
        return true
      } catch {
        this.clearAuth()
        return false
      }
    },

    hasRole(roles = []) {
      const role = this.user?.role
      if (!role) return false
      if (role === 'ADMIN') return true
      return roles.includes(role)
    },

    async changePassword(payload) {
      const response = await changePasswordRequest(payload)
      this.updateUser({ forcePasswordChange: false })
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
