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
  })

  test('keeps manual customer entry on RFQ import while adding existing customer selection', () => {
    const source = readComponent('MarketDashboard.vue')

    expect(source).toContain("import CustomerPickerDialog from './CustomerPickerDialog.vue'")
    expect(source).toContain('v-model="rfqForm.customerName"')
    expect(source).toContain('选择已有客户')
    expect(source).toContain('@select="selectExistingCustomer"')
    expect(source).toContain('rfqForm.customerName = customer')
  })
})
