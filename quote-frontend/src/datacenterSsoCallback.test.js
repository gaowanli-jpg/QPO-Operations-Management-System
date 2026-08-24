import { describe, expect, test } from 'vitest'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')
const readOptionalSource = (...segments) => {
  const file = resolve(currentDir, ...segments)
  return existsSync(file) ? readFileSync(file, 'utf8') : ''
}

const routerSource = readSource('router', 'index.js')
const loginSource = readSource('components', 'LoginView.vue')
const callbackSource = readOptionalSource('views', 'DataCenterSsoCallback.vue')

describe('data center SSO callback frontend', () => {
  test('keeps the data center callback route public for users without a QPO token', () => {
    expect(routerSource).toContain("const DataCenterSsoCallback = () => import('../views/DataCenterSsoCallback.vue')")
    expect(routerSource).toContain("{ path: '/sso/datacenter/callback', component: DataCenterSsoCallback }")
    expect(routerSource).toContain("const PUBLIC_ROUTES = ['/login', '/sso/datacenter/callback']")
    expect(routerSource).toContain('if (PUBLIC_ROUTES.includes(to.path))')
    expect(routerSource).not.toContain("if (to.path === '/login')")
  })

  test('callback page exchanges signed query parameters and redirects to the requested internal target', () => {
    expect(callbackSource).toContain('useRoute')
    expect(callbackSource).toContain('useRouter')
    expect(callbackSource).toContain('loginWithDataCenterSso')
    expect(callbackSource).toContain('appCode: queryString(route.query.appCode)')
    expect(callbackSource).toContain('username: queryString(route.query.username)')
    expect(callbackSource).toContain('displayName: queryString(route.query.displayName)')
    expect(callbackSource).toContain('timestamp: Number(queryString(route.query.timestamp))')
    expect(callbackSource).toContain('nonce: queryString(route.query.nonce)')
    expect(callbackSource).toContain('redirect: queryString(route.query.redirect) || \'/dashboard\'')
    expect(callbackSource).toContain('signature: queryString(route.query.signature)')
    expect(callbackSource).toContain("const target = safeRedirectPath(response?.redirect || payload.redirect || '/dashboard')")
    expect(callbackSource).toContain('router.replace(target)')
  })

  test('callback page fails closed to login without exposing the full signed URL', () => {
    expect(callbackSource).toContain('authStore.clearAuth()')
    expect(callbackSource).toContain("router.replace({ path: '/login', query: { error: message } })")
    expect(callbackSource).toContain('const safeRedirectPath = (candidate) =>')
    expect(callbackSource).toContain("value.startsWith('http://')")
    expect(callbackSource).toContain("value.startsWith('https://')")
    expect(callbackSource).toContain("value.startsWith('//')")
    expect(callbackSource).toContain("return '/dashboard'")
    expect(callbackSource).not.toContain('console.log(route.query)')
    expect(callbackSource).not.toContain('console.error(route.query)')
    expect(callbackSource).not.toContain('JSON.stringify(route.query)')
    expect(loginSource).toContain('route.query.error')
    expect(loginSource).toContain('ElMessage.error(queryError)')
  })
})
