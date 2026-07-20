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
  '/history-orders': 'market:history-order:view',
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
    expect(routerSource).not.toContain('anyPermissions')

    expect(routePermissionEntries()).toEqual(protectedRoutePermissions)
    expect(routerSource).toContain('authStore.hasPermission(permission)')
  })

  test('renders the shell menu from permission menus instead of hard-coded role checks', () => {
    expect(appVue).not.toContain('canSee([')
    expect(appVue).not.toContain('authStore.hasRole')
    expect(appVue).toContain('visibleMenuGroups')
    expect(appVue).toContain('authStore.menus')
    expect(appVue).toContain('v-for="group in visibleMenuGroups"')
    expect(appVue).toContain('v-for="item in group.items"')
    expect(appVue).not.toContain('backendVisiblePermissions')
    expect(appVue).toContain('return backendPaths.has(path)')
  })

  test('registers a reusable v-permission directive for button-level permission control', () => {
    expect(mainSource).toContain("app.directive('permission'")
    expect(mainSource).toContain('authStore.hasPermission')
    expect(mainSource).toContain('authStore.hasAnyPermission')
  })

  test('wires critical business actions to permission points', () => {
    const expectations = [
      ['MarketDashboard', ['market:rfq:import', 'market:rfq:upload-drawing']],
      ['QuoteProcess', ['market:quote-process:update-lead-time', 'market:quote-process:save-quote', 'market:quote-process:export']],
      ['MarketRelease', ['market:release:create']],
      ['OrderManage', ['market:order:supplement-drawing']],
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

    expect(componentSource('MarketRelease')).not.toContain('market:release:reset')
    expect(componentSource('OrderManage')).not.toContain('reset-production')
    expect(componentSource('OrderManage')).not.toContain('重置提单/工艺')
  })

  test('freezes quote process save quote API and allowed status source anchors', () => {
    const quoteProcess = componentSource('QuoteProcess')
    const marketApi = readSource('services', 'marketApi.js')

    expect(marketApi).toContain('saveQuoteSheet')
    expect(marketApi).toContain('/api/market/quote-process/${rfqId}/save-quote')
    expect(quoteProcess).toContain('QUOTE_PROCESS_ALLOWED_STATUSES')
    expect(quoteProcess).toContain("'已核价'")
    expect(quoteProcess).toContain("'已报价'")
    expect(quoteProcess).toContain('QUOTE_PROCESS_SAVE_QUOTE_PERMISSION')
    expect(quoteProcess).toContain('market:quote-process:save-quote')
  })
})
