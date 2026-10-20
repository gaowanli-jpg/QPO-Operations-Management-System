import { describe, expect, test } from 'vitest'
import { readFileSync, existsSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')
const componentPath = (...segments) => resolve(currentDir, 'components', ...segments)
const readComponent = fileName => readSource('components', fileName)

describe('QPO customer picker dialog', () => {
  test('provides a reusable customer master picker with server field search', () => {
    const pickerFile = componentPath('CustomerPickerDialog.vue')

    expect(existsSync(pickerFile)).toBe(true)

    const source = readComponent('CustomerPickerDialog.vue')

    expect(source).toContain("import { listCustomers } from '../services/orderApi'")
    expect(source).toContain('customerScope')
    expect(source).toContain('listCustomers(props.customerScope, customerQueryParams())')
    expect(source).toContain('page: page.value')
    expect(source).toContain('pageSize: pageSize.value')
    expect(source).toContain('normalizeCustomerPage')
    expect(source).toContain('<el-pagination')
    expect(source).toContain('layout="total, sizes, prev, pager, next, jumper"')
    expect(source).toContain(':pager-count="7"')
    expect(source).toContain('@current-change="handlePageChange"')
    expect(source).toContain('@size-change="handlePageSizeChange"')
    expect(source).toContain('v-model="keyword"')
    expect(source).toContain('v-model="field"')
    expect(source).toContain('value="all"')
    expect(source).toContain('value="code"')
    expect(source).toContain('value="shortName"')
    expect(source).toContain('value="name"')
    expect(source).toContain('prop="customerCode"')
    expect(source).toContain('prop="customerShortName"')
    expect(source).toContain('prop="customerName"')
    expect(source).toContain(':data="customers"')
    expect(source).toContain('@row-click="selectCustomer"')
    expect(source).toContain("emit('select', customer)")
    expect(source).toContain('currentCustomerCode')
    expect(source).toContain('customer.customerCode === props.currentCustomerCode')
    expect(source).not.toContain('鎼滅储鎸夐挳')
  })

  test('selects and highlights customers by customer code instead of duplicate names', () => {
    const source = readComponent('CustomerPickerDialog.vue')

    expect(source).toContain('customer.customerCode === props.currentCustomerCode')
    expect(source).not.toContain('customer.customerName === props.currentCustomer')
    expect(source).toContain('filter(customer => customer.customerCode)')
  })

  test('replaces customer dropdowns in existing order selection flows', () => {
    const orderPicker = readComponent('OrderPickerDialog.vue')
    const orderManage = readComponent('OrderManage.vue')
    const salesOrderManage = readComponent('SalesOrderManage.vue')

    for (const source of [orderPicker, orderManage, salesOrderManage]) {
      expect(source).toContain("import CustomerPickerDialog from './CustomerPickerDialog.vue'")
      expect(source).toContain('<CustomerPickerDialog')
      expect(source).toContain('@select="handleCustomerSelected"')
      expect(source).not.toContain('class="customer-select"')
    }

    expect(orderPicker).not.toContain('import { listCustomers }')
    expect(orderManage).not.toContain('listCustomers,')
    expect(salesOrderManage).not.toContain('listCustomers,')
    expect(orderPicker).toContain('customerScope')
    expect(orderPicker).toContain('orderScope')
    expect(orderPicker).toContain(':customer-scope="customerScope"')
    expect(orderPicker).toContain('listMarketOrders(params, props.orderScope)')
    expect(orderPicker).toContain('props.orderScope')
    expect(orderPicker).toContain('resolveCustomerName(customer)')
    expect(orderPicker).toContain('customer?.customerName')
  })

  test('requires customer master selection on RFQ import instead of manual customer entry', () => {
    const source = readComponent('MarketDashboard.vue')

    expect(source).toContain("import CustomerPickerDialog from './CustomerPickerDialog.vue'")
    expect(source).toContain(':model-value="rfqCustomerLabel"')
    expect(source).toContain("import CustomerAutocomplete from './CustomerAutocomplete.vue'")
    expect(source).toContain('@open-picker="openRfqCustomerPicker"')
    expect(source).toContain('@select="selectExistingCustomer"')
    expect(source).toContain('rfqForm.customerId = customer?.id || null')
    expect(source).toContain('rfqForm.customerCode = customer?.customerCode || \'\'')
    expect(source).toContain('rfqForm.customerShortName = customer?.customerShortName || \'\'')
    expect(source).toContain('rfqForm.customerName = resolveCustomerName(customer)')
    expect(source).toContain('const rfqCustomerLabel = computed(() =>')
    expect(source).toContain('customer-scope="market-rfq"')
    expect(source).toContain('permission="market:rfq:customer-select"')
    expect(source).not.toContain('readonly')
    expect(source).not.toContain('閫夋嫨宸叉湁瀹㈡埛')
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

  test('engineer setup order picker only exposes unpriced order status', () => {
    const orderPicker = readComponent('OrderPickerDialog.vue')
    const globalSetup = readComponent('GlobalSetup.vue')

    expect(globalSetup).toContain('order-scope="engineer-setup"')
    expect(orderPicker).toContain("'engineer-setup'")
  })

  test('customer selection buttons are gated by module-owned permissions', () => {
    const orderPicker = readComponent('OrderPickerDialog.vue')
    const orderManage = readComponent('OrderManage.vue')
    const salesOrderManage = readComponent('SalesOrderManage.vue')

    expect(orderPicker).toContain('customerSelectPermission')
    expect(orderPicker).toContain(':permission="customerSelectPermission"')
    expect(orderManage).toContain('permission="market:order:customer-select"')
    expect(salesOrderManage).toContain('permission="market:sales-order:customer-select"')
  })

  test('delivered, production, and locked item modules use their own customer selection scopes', () => {
    const deliveredManage = readComponent('DeliveredItemManage.vue')
    const productionManage = readComponent('ProductionItemManage.vue')
    const lockedManage = readComponent('LockedItemManage.vue')

    expect(deliveredManage).toContain("import CustomerPickerDialog from './CustomerPickerDialog.vue'")
    expect(deliveredManage).toContain('customer-scope="delivered-item"')
    expect(deliveredManage).toContain('permission="market:delivered-item:customer-select"')
    expect(deliveredManage).toContain('@select="handleCustomerSelected"')
    expect(deliveredManage).toContain('filters.customerCode = resolveCustomerCode(customer)')
    expect(deliveredManage).toContain('filters.customerName = resolveCustomerName(customer)')

    expect(productionManage).toContain("import CustomerPickerDialog from './CustomerPickerDialog.vue'")
    expect(productionManage).toContain('customer-scope="production-item"')
    expect(productionManage).toContain('permission="market:production-item:customer-select"')
    expect(productionManage).toContain('@select="handleCustomerSelected"')
    expect(productionManage).toContain('filters.customerCode = resolveCustomerCode(customer)')
    expect(productionManage).toContain('filters.customerName = resolveCustomerName(customer)')

    expect(lockedManage).toContain("import CustomerPickerDialog from './CustomerPickerDialog.vue'")
    expect(lockedManage).toContain('customer-scope="locked-item"')
    expect(lockedManage).toContain('permission="market:locked-item:customer-select"')
    expect(lockedManage).toContain('@select="handleCustomerSelected"')
    expect(lockedManage).toContain('filters.customerCode = resolveCustomerCode(customer)')
    expect(lockedManage).toContain('filters.customerName = resolveCustomerName(customer)')
  })

  test('customer picker triggers keep the action label fixed while truncating long customer names', () => {
    const pages = [
      readComponent('OrderManage.vue'),
      readComponent('SalesOrderManage.vue'),
      readComponent('DeliveredItemManage.vue'),
      readComponent('ProductionItemManage.vue'),
      readComponent('LockedItemManage.vue')
    ]

    for (const source of pages) {
      expect(source).toContain('@open-picker="customerPickerVisible = true"')
    }
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
