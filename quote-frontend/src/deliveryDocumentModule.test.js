import { describe, expect, test } from 'vitest'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')
const componentPath = (...segments) => resolve(currentDir, 'components', ...segments)

const routerSource = readSource('router', 'index.js')
const appVue = readSource('App.vue')
const marketApi = readSource('services', 'marketApi.js')
const orderApi = readSource('services', 'orderApi.js')
const orderPicker = readSource('components', 'OrderPickerDialog.vue')

describe('delivery document module stage 2 wiring', () => {
  test('registers an independent protected route for delivery documents', () => {
    expect(routerSource).toContain("const DeliveryDocumentManage = () => import('../components/DeliveryDocumentManage.vue')")
    expect(routerSource).toContain("{ path: '/delivery-documents', component: DeliveryDocumentManage, meta: { permission: 'market:delivery-document:view' } }")
    expect(routerSource).not.toContain("{ path: '/delivery-documents', component: MarketRelease")
    expect(routerSource).not.toContain("'/delivery-documents', component: MarketRelease")
  })

  test('places delivery document immediately after release in the market menu', () => {
    expect(appVue).toContain("{ key: 'market', title: '市场', paths: ['/market', '/sales-match', '/quote-process', '/release', '/delivery-documents', '/quote-template'] }")
    expect(appVue).toContain("'/delivery-documents': { title: '提单表据', permission: 'market:delivery-document:view'")
    expect(appVue).toContain("'/delivery-documents': {\n    title: '提单表据'")
    expect(appVue).not.toContain("'/delivery-documents': { title: '提单表据', permission: 'market:release:view'")

    const marketGroupIndex = appVue.indexOf("{ key: 'market'")
    const releaseIndex = appVue.indexOf("'/release'", marketGroupIndex)
    const documentIndex = appVue.indexOf("'/delivery-documents'", marketGroupIndex)
    const templateIndex = appVue.indexOf("'/quote-template'", marketGroupIndex)

    expect(releaseIndex).toBeGreaterThan(-1)
    expect(documentIndex).toBeGreaterThan(releaseIndex)
    expect(templateIndex).toBeGreaterThan(documentIndex)
  })

  test('adds delivery document API helpers on the new backend namespace', () => {
    expect(marketApi).toContain("'delivery-document': '/api/market/delivery-documents/orders'")
    expect(orderApi).toContain("'delivery-document': '/api/market/delivery-documents/customers'")
    expect(marketApi).toContain('getDeliveryDocumentSummary')
    expect(marketApi).toContain('/api/market/delivery-documents/summary/${rfqId}')
    expect(marketApi).toContain('listDeliveryDocumentTickets')
    expect(marketApi).toContain('/api/market/delivery-documents/tickets/${rfqId}')
    expect(marketApi).toContain('listDeliveryDocumentProcessSheets')
    expect(marketApi).toContain('/api/market/delivery-documents/process-sheets/${rfqId}')
    expect(marketApi).toContain('downloadDeliveryDocumentExport')
    expect(marketApi).toContain('/api/market/delivery-documents/export/${path}/${rfqId}')
    expect(marketApi).not.toContain('/api/market/export/${path}/${rfqId}`,\n  fallbackName\n)\n\nexport const getDeliveryDocumentSummary')
  })

  test('teaches the reusable order picker the delivery document scope and statuses', () => {
    expect(orderPicker).toContain("'delivery-document': 'market:delivery-document:customer-select'")
    expect(orderPicker).toContain("const STATUS_OPTIONS_BY_SCOPE = {")
    expect(orderPicker).toContain("'delivery-document': ['部分已提单', '已生成提单']")
    expect(orderPicker).toContain('STATUS_OPTIONS_BY_SCOPE[props.orderScope]')
  })

  test('provides a route component that opens the scoped order picker only for delivery document orders', () => {
    const deliveryDocumentPath = componentPath('DeliveryDocumentManage.vue')
    expect(existsSync(deliveryDocumentPath)).toBe(true)

    const deliveryDocumentManage = readFileSync(deliveryDocumentPath, 'utf8')
    expect(deliveryDocumentManage).toContain('<OrderPickerDialog')
    expect(deliveryDocumentManage).toContain('customer-scope="delivery-document"')
    expect(deliveryDocumentManage).toContain('order-scope="delivery-document"')
    expect(deliveryDocumentManage).toContain(':allowed-statuses="DELIVERY_DOCUMENT_ALLOWED_STATUSES"')
    expect(deliveryDocumentManage).toContain("const DELIVERY_DOCUMENT_ALLOWED_STATUSES = ['部分已提单', '已生成提单']")
    expect(deliveryDocumentManage).toContain('market:delivery-document:order-select')
  })
})
