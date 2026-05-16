import { reactive } from 'vue'

const TOKEN_KEY = 'erp_token'
const USER_KEY = 'erp_user'

const loadUser = () => {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  } catch {
    return null
  }
}

export const authState = reactive({
  token: localStorage.getItem(TOKEN_KEY) || '',
  user: loadUser()
})

export const setAuth = (token, user) => {
  authState.token = token || ''
  authState.user = user || null
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

export const hasRole = (roles) => {
  const role = authState.user?.role
  if (!role) return false
  if (role === 'ADMIN') return true
  return roles.includes(role)
}
