import { reactive } from 'vue'
import { configureApiAuth } from './api'
import { getCurrentUser } from './services/authApi'

const TOKEN_KEY = 'aps_token'
const USER_KEY = 'aps_user'

const loadUser = () => {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  } catch {
    return null
  }
}

export const authState = reactive({
  token: localStorage.getItem(TOKEN_KEY) || '',
  user: loadUser(),
  verified: false
})

export const setAuth = (token, user) => {
  authState.token = token || ''
  authState.user = user || null
  authState.verified = !!authState.token
  if (authState.token) {
    localStorage.setItem(TOKEN_KEY, authState.token)
  } else {
    localStorage.removeItem(TOKEN_KEY)
  }
  if (authState.user) {
    localStorage.setItem(USER_KEY, JSON.stringify(authState.user))
  } else {
    localStorage.removeItem(USER_KEY)
  }
}

export const clearAuth = () => {
  setAuth('', null)
}

export const updateAuthUser = (patch) => {
  authState.user = {
    ...(authState.user || {}),
    ...(patch || {})
  }
  localStorage.setItem(USER_KEY, JSON.stringify(authState.user))
}

export const verifyAuth = async () => {
  if (!authState.token) {
    clearAuth()
    return false
  }
  if (authState.verified) {
    return true
  }
  try {
    const data = await getCurrentUser()
    if (!data?.success || !data?.data) {
      clearAuth()
      return false
    }
    updateAuthUser(data.data)
    authState.verified = true
    return true
  } catch {
    clearAuth()
    return false
  }
}

export const hasRole = (roles) => {
  const role = authState.user?.role
  if (!role) return false
  if (role === 'ADMIN') return true
  return roles.includes(role)
}

const redirectToLogin = () => {
  if (window.location.pathname === '/login') {
    return
  }
  const current = `${window.location.pathname}${window.location.search}${window.location.hash}`
  window.location.assign(`/login?redirect=${encodeURIComponent(current)}`)
}

configureApiAuth({
  getToken: () => authState.token,
  onUnauthorized: () => {
    clearAuth()
    redirectToLogin()
  },
  onPasswordChangeRequired: () => updateAuthUser({ forcePasswordChange: true })
})
