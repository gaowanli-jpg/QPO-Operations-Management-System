import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const currentDir = dirname(fileURLToPath(import.meta.url))
const routerSource = readFileSync(resolve(currentDir, 'router', 'index.js'), 'utf8')

const businessRouteComponents = [
  'MarketDashboard',
  'MarketRelease',
  'DeliveryDocumentManage',
  'QuoteProcess',
  'GlobalSetup',
  'ItemPricing',
  'MaterialProcessManage',
  'OrderManage',
  'HistoryOrderManage',
  'UserManage',
  'SystemRoleManage',
  'CustomerManage',
  'QuoteTemplateManage',
  'AuditLogManage'
]

describe('QPO router code splitting', () => {
  test('lazy-loads business route components instead of pulling them into the main bundle', () => {
    for (const componentName of businessRouteComponents) {
      expect(routerSource).toContain(`const ${componentName} = () => import('../components/${componentName}.vue')`)
      expect(routerSource).not.toContain(`import ${componentName} from '../components/${componentName}.vue'`)
    }
  })

  test('keeps the login view eager for the default entry path', () => {
    expect(routerSource).toContain("import LoginView from '../components/LoginView.vue'")
  })
})
