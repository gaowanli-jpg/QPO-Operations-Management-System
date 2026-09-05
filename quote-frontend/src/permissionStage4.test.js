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
const appVue = readSource('App.vue')
const mainSource = readSource('main.js')

const protectedRoutePermissions = {
  '/market': 'market:rfq:view',
  '/quote-process': 'market:quote-process:view',
  '/release': 'market:release:view',
  '/u9-sync': 'market:u9-sync:view',
  '/delivery-documents': 'market:delivery-document:view',
  '/engineering': 'engineer:setup:view',
  '/material-process': 'engineer:dict:view',
  '/orders': 'market:order:view',
  '/sales-orders': 'market:sales-order:view',
  '/part-search': 'market:part-search:view',
  '/delivered-items': 'market:delivered-item:view',
  '/production-items': 'market:production-item:view',
  '/locked-items': 'market:locked-item:view',
  '/sales-order-change': 'change:sales-order:view',
  '/users': 'system:user:view',
  '/roles': 'system:role:view',
  '/organization': 'system:org:view',
  '/customers': 'system:customer:view',
  '/audit-logs': 'system:audit:view',
  '/system-config': 'system:config:view',
  '/quote-template': 'market:template:view',
  '/pricing': 'engineer:pricing:view'
}

const routePermissionEntries = () => {
  const routeRegex = /\{ path: '([^']+)', component: (\w+), meta: \{ permission: '([^']+)' } }/g
  return Object.fromEntries([...routerSource.matchAll(routeRegex)].map(match => [match[1], match[3]]))
}

const componentSource = (name) => readSource('components', `${name}.vue`)
const systemConfigSources = () => [
  componentSource('SystemConfigManage'),
  readOptionalSource('components', 'system-config', 'SystemStorageConfigPanel.vue'),
  readOptionalSource('components', 'system-config', 'SystemIntegrationConfigPanel.vue')
].join('\n')

describe('QPO permission stage 4 menu and button permissions', () => {
  test('protects frontend routes with permission codes instead of legacy roles', () => {
    expect(routerSource).not.toContain('meta: { roles:')
    expect(routerSource).not.toContain('meta?.roles')
    expect(routerSource).not.toContain('authStore.hasRole(roles)')
    expect(routerSource).not.toContain('anyPermissions')

    expect(routePermissionEntries()).toEqual(protectedRoutePermissions)
    expect(routePermissionEntries()['/delivered-items']).toBe('market:delivered-item:view')
    expect(routePermissionEntries()['/locked-items']).toBe('market:locked-item:view')
    expect(routePermissionEntries()['/delivered-items']).not.toBe(routePermissionEntries()['/locked-items'])
    expect(routePermissionEntries()['/delivered-items']).not.toBe('market:order:view')
    expect(routePermissionEntries()['/locked-items']).not.toBe('market:order:view')
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
      ['DeliveryDocumentManage', ['market:delivery-document:order-select']],
      ['OrderManage', ['market:order:supplement-drawing', 'market:order:rollback-quoted', 'market:order:rollback-priced', 'market:order:discard', 'market:order:restore']],
      ['SalesOrderManage', ['market:sales-order:customer-select']],
      ['DeliveredItemManage', ['market:delivered-item:view', 'market:delivered-item:file-preview', 'market:delivered-item:wms-allocate', 'market:locked-item:view']],
      ['ProductionItemManage', ['market:production-item:view', 'market:production-item:customer-select', 'market:production-item:mes-production-submit', 'market:delivered-item:view']],
      ['LockedItemManage', ['market:locked-item:view']],
      ['QuoteTemplateManage', ['market:template:upload']],
      ['GlobalSetup', ['engineer:setup:save-config']],
      ['ItemPricing', ['engineer:pricing:save-item', 'engineer:pricing:refresh', 'engineer:pricing:export-sheet', 'engineer:pricing:complete-order']],
      ['MaterialProcessManage', ['engineer:dict:material-manage', 'engineer:dict:process-manage', 'engineer:dict:fee-manage']],
      ['UserManage', ['system:user:create', 'system:user:update', 'system:user:disable', 'system:user:reset-password', 'system:user:delete']],
      ['SystemRoleManage', ['system:role:create', 'system:role:update', 'system:role:delete', 'system:role:assign-permission', 'system:role:assign-data-scope']],
      ['SystemOrgManage', ['system:dept:manage', 'system:post:manage']],
      ['CustomerManage', ['system:customer:sync', 'system:customer:supplier-code-update']],
      ['AuditLogManage', ['system:audit:export', 'system:audit:retention']],
      ['SystemConfigManage', [
        'system:config:storage:update',
        'system:config:storage:test',
        'system:config:storage:activate',
        'system:config:integration:update',
        'system:config:integration:test',
        'system:config:integration:activate'
      ]]
    ]

    for (const [component, permissions] of expectations) {
      const source = component === 'SystemConfigManage' ? systemConfigSources() : componentSource(component)
      for (const permission of permissions) {
        expect(source, `${component} should reference ${permission}`).toContain(permission)
      }
    }

    expect(componentSource('MarketRelease')).not.toContain('market:release:reset')
    expect(componentSource('MarketDashboard')).not.toContain('market:rfq:order-type-select')
    expect(componentSource('CustomerManage')).not.toContain('system:customer:create')
    expect(componentSource('CustomerManage')).not.toContain('system:customer:update')
    expect(componentSource('CustomerManage')).not.toContain('system:customer:delete')
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
