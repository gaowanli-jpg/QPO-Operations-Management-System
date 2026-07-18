import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

const routerSource = readSource('router', 'index.js')
const appVue = readSource('App.vue')
const mainSource = readSource('main.js')

const protectedRoutePermissions = {
  '/market': 'market:rfq:view',
  '/quote-process': 'market:quote-process:view',
  '/release': 'market:release:view',
  '/engineering': 'engineer:setup:view',
  '/material-process': 'engineer:dict:view',
  '/orders': 'market:order:view',
  '/history-orders': 'market:order:view',
  '/users': 'system:user:view',
  '/roles': 'system:role:view',
  '/audit-logs': 'system:audit:view',
  '/quote-template': 'market:template:view',
  '/pricing': 'engineer:pricing:view'
}

const routePermissionEntries = () => {
  const routeRegex = /\{ path: '([^']+)', component: (\w+), meta: \{ permission: '([^']+)' } }/g
  return Object.fromEntries([...routerSource.matchAll(routeRegex)].map(match => [match[1], match[3]]))
}

const componentSource = (name) => readSource('components', `${name}.vue`)

describe('QPO permission stage 4 menu and button permissions', () => {
  test('protects frontend routes with permission codes instead of legacy roles', () => {
    expect(routerSource).not.toContain('meta: { roles:')
    expect(routerSource).not.toContain('meta?.roles')
    expect(routerSource).not.toContain('authStore.hasRole(roles)')

    expect(routePermissionEntries()).toEqual(protectedRoutePermissions)
    expect(routerSource).toContain('authStore.hasPermission(permission)')
    expect(routerSource).toContain('authStore.hasAnyPermission(anyPermissions)')
  })

  test('renders the shell menu from permission menus instead of hard-coded role checks', () => {
    expect(appVue).not.toContain('canSee([')
    expect(appVue).not.toContain('authStore.hasRole')
    expect(appVue).toContain('visibleMenuGroups')
    expect(appVue).toContain('authStore.menus')
    expect(appVue).toContain('v-for="group in visibleMenuGroups"')
    expect(appVue).toContain('v-for="item in group.items"')
  })

  test('registers a reusable v-permission directive for button-level permission control', () => {
    expect(mainSource).toContain("app.directive('permission'")
    expect(mainSource).toContain('authStore.hasPermission')
    expect(mainSource).toContain('authStore.hasAnyPermission')
  })

  test('wires critical business actions to permission points', () => {
    const expectations = [
      ['MarketDashboard', ['market:rfq:import', 'market:rfq:upload-drawing']],
      ['QuoteProcess', ['market:quote-process:update-lead-time', 'market:quote-process:export']],
      ['MarketRelease', ['market:release:create', 'market:release:reset']],
      ['OrderManage', ['market:order:supplement-drawing', 'market:order:delete']],
      ['HistoryOrderManage', ['market:order:delete']],
      ['QuoteTemplateManage', ['market:template:upload']],
      ['GlobalSetup', ['engineer:setup:save-config']],
      ['ItemPricing', ['engineer:pricing:save-item', 'engineer:pricing:refresh', 'engineer:pricing:complete-order']],
      ['MaterialProcessManage', ['engineer:dict:material-manage', 'engineer:dict:process-manage', 'engineer:dict:fee-manage']],
      ['UserManage', ['system:user:create', 'system:user:update', 'system:user:disable', 'system:user:reset-password', 'system:user:delete']],
      ['SystemRoleManage', ['system:role:create', 'system:role:update', 'system:role:delete', 'system:role:assign-permission', 'system:role:assign-data-scope']],
      ['AuditLogManage', ['system:audit:export', 'system:audit:retention']]
    ]

    for (const [component, permissions] of expectations) {
      const source = componentSource(component)
      for (const permission of permissions) {
        expect(source, `${component} should reference ${permission}`).toContain(permission)
      }
    }
  })
})
