import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const currentDir = dirname(fileURLToPath(import.meta.url))
const routerSource = readFileSync(resolve(currentDir, 'router', 'index.js'), 'utf8')

const businessRouteComponents = [
  ['DashboardHome', '../components/dashboard/DashboardHome.vue'],
  'MarketDashboard',
  'MarketRelease',
  'DeliveryDocumentManage',
  'QuoteProcess',
  'GlobalSetup',
  'ItemPricing',
  'MaterialProcessManage',
  'OrderManage',
  'SalesOrderManage',
  'LinkedChangeManage',
  'UserManage',
  'SystemRoleManage',
  'CustomerManage',
  'QuoteTemplateManage',
  'AuditLogManage',
  'SystemConfigManage'
]

describe('QPO router code splitting', () => {
  test('lazy-loads business route components instead of pulling them into the main bundle', () => {
    for (const componentEntry of businessRouteComponents) {
      const [componentName, importPath] = Array.isArray(componentEntry)
        ? componentEntry
        : [componentEntry, `../components/${componentEntry}.vue`]
      expect(routerSource).toContain(`const ${componentName} = () => import('${importPath}')`)
      expect(routerSource).not.toContain(`import ${componentName} from '${importPath}'`)
    }
  })

  test('keeps the login view eager for the default entry path', () => {
    expect(routerSource).toContain("import LoginView from '../components/LoginView.vue'")
  })
})
