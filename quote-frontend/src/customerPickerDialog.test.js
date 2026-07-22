import { describe, expect, test } from 'vitest'
import { readFileSync, existsSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')
const componentPath = (...segments) => resolve(currentDir, 'components', ...segments)
const readComponent = fileName => readSource('components', fileName)

describe('QPO customer picker dialog', () => {
  test('provides a reusable customer table picker with live local search', () => {
    const pickerFile = componentPath('CustomerPickerDialog.vue')

    expect(existsSync(pickerFile)).toBe(true)

    const source = readComponent('CustomerPickerDialog.vue')

    expect(source).toContain("import { listCustomers } from '../services/orderApi'")
    expect(source).toContain('customerScope')
    expect(source).toContain('listCustomers(props.customerScope)')
    expect(source).toContain('v-model="keyword"')
    expect(source).toContain('filteredCustomers')
    expect(source).toContain(':data="filteredCustomers"')
    expect(source).toContain('@row-click="selectCustomer"')
    expect(source).toContain("emit('select', customer)")
    expect(source).not.toContain('搜索按钮')
  })

  test('replaces customer dropdowns in existing order selection flows', () => {
    const orderPicker = readComponent('OrderPickerDialog.vue')
    const orderManage = readComponent('OrderManage.vue')
    const historyManage = readComponent('HistoryOrderManage.vue')

    for (const source of [orderPicker, orderManage, historyManage]) {
      expect(source).toContain("import CustomerPickerDialog from './CustomerPickerDialog.vue'")
      expect(source).toContain('<CustomerPickerDialog')
      expect(source).toContain('@select="handleCustomerSelected"')
      expect(source).not.toContain('class="customer-select"')
    }

    expect(orderPicker).not.toContain('import { listCustomers }')
    expect(orderManage).not.toContain('listCustomers,')
    expect(historyManage).not.toContain('listCustomers,')
    expect(orderPicker).toContain('customerScope')
    expect(orderPicker).toContain('orderScope')
    expect(orderPicker).toContain(':customer-scope="customerScope"')
    expect(orderPicker).toContain('listMarketOrders(params, props.orderScope)')
    expect(orderPicker).toContain('props.orderScope')
  })

  test('keeps manual customer entry on RFQ import while adding existing customer selection', () => {
    const source = readComponent('MarketDashboard.vue')

    expect(source).toContain("import CustomerPickerDialog from './CustomerPickerDialog.vue'")
    expect(source).toContain('v-model="rfqForm.customerName"')
    expect(source).toContain('选择已有客户')
    expect(source).toContain('@select="selectExistingCustomer"')
    expect(source).toContain('rfqForm.customerName = customer')
    expect(source).toContain('customer-scope="market-rfq"')
    expect(source).toContain('v-permission="\'market:rfq:customer-select\'"')
  })

  test('order picker consumers declare module selection scopes', () => {
    const globalSetup = readComponent('GlobalSetup.vue')
    const quoteProcess = readComponent('QuoteProcess.vue')
    const marketRelease = readComponent('MarketRelease.vue')

    expect(globalSetup).toContain('customer-scope="engineer-setup"')
    expect(globalSetup).toContain('order-scope="engineer-setup"')
    expect(globalSetup).toContain('v-permission="\'engineer:setup:order-select\'"')
    expect(quoteProcess).toContain('customer-scope="quote-process"')
    expect(quoteProcess).toContain('order-scope="quote-process"')
    expect(quoteProcess).toContain('v-permission="\'market:quote-process:order-select\'"')
    expect(marketRelease).toContain('customer-scope="release"')
    expect(marketRelease).toContain('order-scope="release"')
    expect(marketRelease).toContain('v-permission="\'market:release:order-select\'"')
  })

  test('customer selection buttons are gated by module-owned permissions', () => {
    const orderPicker = readComponent('OrderPickerDialog.vue')
    const orderManage = readComponent('OrderManage.vue')
    const historyManage = readComponent('HistoryOrderManage.vue')

    expect(orderPicker).toContain('customerSelectPermission')
    expect(orderPicker).toContain('v-permission="customerSelectPermission"')
    expect(orderManage).toContain('v-permission="\'market:order:customer-select\'"')
    expect(historyManage).toContain('v-permission="\'market:history-order:customer-select\'"')
  })

  test('delivered and locked item modules use their own customer selection scopes', () => {
    const deliveredManage = readComponent('DeliveredItemManage.vue')
    const lockedManage = readComponent('LockedItemManage.vue')

    expect(deliveredManage).toContain("import CustomerPickerDialog from './CustomerPickerDialog.vue'")
    expect(deliveredManage).toContain('customer-scope="delivered-item"')
    expect(deliveredManage).toContain('v-permission="\'market:delivered-item:customer-select\'"')
    expect(deliveredManage).toContain('@select="handleCustomerSelected"')

    expect(lockedManage).toContain("import CustomerPickerDialog from './CustomerPickerDialog.vue'")
    expect(lockedManage).toContain('customer-scope="locked-item"')
    expect(lockedManage).toContain('v-permission="\'market:locked-item:customer-select\'"')
    expect(lockedManage).toContain('@select="handleCustomerSelected"')
  })

  test('order workspaces use module-scoped summary APIs instead of common market summary', () => {
    const globalSetup = readComponent('GlobalSetup.vue')
    const itemPricing = readComponent('ItemPricing.vue')
    const marketRelease = readComponent('MarketRelease.vue')

    expect(globalSetup).toContain('getSetupSummary')
    expect(itemPricing).toContain('getPricingSummary')
    expect(marketRelease).toContain('getReleaseSummary')
    expect(globalSetup).not.toContain('getMarketSummary')
    expect(itemPricing).not.toContain('getMarketSummary')
    expect(marketRelease).not.toContain('getMarketSummary')
  })
})
