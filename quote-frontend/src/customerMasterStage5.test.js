import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')
const readDoc = (...segments) => readFileSync(resolve(currentDir, '..', '..', 'docs', ...segments), 'utf8')

describe('QPO customer master stage 5 closure', () => {
  test('documents UI compatibility and legacy customer-name behavior', () => {
    const customerManage = readSource('components', 'CustomerManage.vue')
    const customerPicker = readSource('components', 'CustomerPickerDialog.vue')
    const discussionDoc = readDoc('QPO报价状态流转', '10-销售订单直提流程方案讨论文档.md')

    expect(customerManage).toContain('erp-page customer-manage-page')
    expect(customerManage).toContain('erp-toolbar')
    expect(customerPicker).toContain('prop="customerCode"')
    expect(customerPicker).toContain('prop="customerShortName"')
    expect(customerPicker).toContain('prop="customerName"')
    expect(discussionDoc).toContain('客户主数据模块已经落地')
    expect(discussionDoc).toContain('`customerName` 仍作为 QPO 订单筛选和数据权限兼容快照')
  })
})
