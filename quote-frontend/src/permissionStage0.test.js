import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')
const readDoc = (...segments) => readFileSync(resolve(currentDir, '..', '..', 'docs', ...segments), 'utf8')

const routerSource = readSource('router', 'index.js')
const appVue = readSource('App.vue')
const mappingDoc = () => readDoc('QPO\u6743\u9650\u7528\u6237\u4f53\u7cfb', '02-\u6743\u9650\u70b9\u6e05\u5355\u4e0e\u73b0\u6709\u89d2\u8272\u6620\u5c04.md')

const protectedRoutePermissions = {
  '/market': 'market:rfq:view',
  '/sales-match': 'market:sales-match:view',
  '/quote-process': 'market:quote-process:view',
  '/release': 'market:release:view',
  '/u9-sync': 'market:u9-sync:view',
  '/delivery-documents': 'market:delivery-document:view',
  '/engineering': 'engineer:setup:view',
  '/material-process': 'engineer:dict:view',
  '/orders': 'market:order:view',
  '/sales-orders': 'market:sales-order:view',
  '/delivered-items': 'market:delivered-item:view',
  '/production-items': 'market:production-item:view',
  '/locked-items': 'market:locked-item:view',
  '/users': 'system:user:view',
  '/roles': 'system:role:view',
  '/organization': 'system:org:view',
  '/customers': 'system:customer:view',
  '/audit-logs': 'system:audit:view',
  '/system-config': 'system:config:view',
  '/quote-template': 'market:template:view',
  '/pricing': 'engineer:pricing:view'
}

const visibleMenuPermissions = {
  '/market': 'market:rfq:view',
  '/sales-match': 'market:sales-match:view',
  '/quote-process': 'market:quote-process:view',
  '/release': 'market:release:view',
  '/u9-sync': 'market:u9-sync:view',
  '/delivery-documents': 'market:delivery-document:view',
  '/quote-template': 'market:template:view',
  '/engineering': 'engineer:setup:view',
  '/material-process': 'engineer:dict:view',
  '/orders': 'market:order:view',
  '/sales-orders': 'market:sales-order:view',
  '/delivered-items': 'market:delivered-item:view',
  '/production-items': 'market:production-item:view',
  '/locked-items': 'market:locked-item:view',
  '/users': 'system:user:view',
  '/roles': 'system:role:view',
  '/organization': 'system:org:view',
  '/customers': 'system:customer:view',
  '/audit-logs': 'system:audit:view',
  '/system-config': 'system:config:view'
}

const routePermissionEntries = () => {
  const routeRegex = /\{ path: '([^']+)', component: (\w+), meta: \{ permission: '([^']+)' } }/g
  return Object.fromEntries([...routerSource.matchAll(routeRegex)].map(match => [match[1], match[3]]))
}

const menuPermissionEntries = () => {
  const menuRegex = /'([^']+)': \{ title: '[^']+', permission: '([^']+)'/g
  return Object.fromEntries([...appVue.matchAll(menuRegex)].map(match => [match[1], match[2]]))
}

describe('QPO permission stage 0 guardrails', () => {
  test('freezes protected route permission configuration', () => {
    expect(routerSource).not.toContain('meta: { roles:')
    expect(routePermissionEntries()).toEqual(protectedRoutePermissions)
  })

  test('freezes sidebar menu permission snapshot', () => {
    expect(appVue).not.toContain('canSee([')
    expect(appVue).toContain('visibleMenuGroups')
    expect(appVue).not.toContain('backendVisiblePermissions')
    expect(menuPermissionEntries()).toEqual(visibleMenuPermissions)
  })

  test('uses current system module display names in sidebar and page headers', () => {
    const userManage = readSource('components', 'UserManage.vue')
    const roleManage = readSource('components', 'SystemRoleManage.vue')

    expect(appVue).toContain("'/users': { title: '用户管理', permission: 'system:user:view'")
    expect(appVue).toContain("'/roles': { title: '角色权限', permission: 'system:role:view'")
    expect(appVue).not.toContain("title: '用户权限'")
    expect(appVue).not.toContain("title: '角色管理'")
    expect(userManage).toContain('<h1 class="erp-page-title">用户管理</h1>')
    expect(roleManage).toContain('<h1 class="erp-page-title">角色权限</h1>')
  })

  test('maps every protected frontend route to a target permission code', () => {
    const doc = mappingDoc()

    for (const [path, permission] of Object.entries(protectedRoutePermissions)) {
      expect(doc, path).toContain('| `' + path + '` |')
      expect(doc, permission).toContain('`' + permission + '`')
    }

    expect(doc).toContain('\u524d\u7aef\u8def\u7531\u6743\u9650\u8868')
    expect(doc).toContain('`market:rfq:view`')
    expect(doc).toContain('`engineer:pricing:view`')
    expect(doc).toContain('`system:audit:view`')
  })
})
