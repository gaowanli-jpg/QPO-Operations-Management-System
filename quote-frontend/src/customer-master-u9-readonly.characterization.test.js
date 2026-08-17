import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')
const readDoc = (...segments) => readFileSync(resolve(currentDir, '..', '..', 'docs', ...segments), 'utf8')

describe('QPO customer master U9 read-only characterization', () => {
  test('keeps customer selectors compatible while making customer master read-only', () => {
    const customerManage = readSource('components', 'CustomerManage.vue')
    const customerPicker = readSource('components', 'CustomerPickerDialog.vue')
    const u9CustomerDoc = readDoc('QPO报价状态流转', '43-U9客户主数据与U9同步选项缓存开发文档.md')

    expect(customerManage).toContain('erp-page customer-manage-page')
    expect(customerManage).toContain('erp-toolbar')
    expect(customerManage).toContain('来自 U9')
    expect(customerManage).toContain('最近同步时间')
    expect(customerManage).toContain('syncU9Customers')
    expect(customerManage).not.toContain('openCreate')
    expect(customerManage).not.toContain('openEdit')
    expect(customerManage).not.toContain('deleteCustomer')
    expect(customerPicker).toContain('prop="customerCode"')
    expect(customerPicker).toContain('prop="customerShortName"')
    expect(customerPicker).toContain('prop="customerName"')
    expect(u9CustomerDoc).toContain('客户模块只读，所有客户来自 U9')
    expect(u9CustomerDoc).toContain('状态：已完成')
  })
})
