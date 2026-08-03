import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

describe('sales match order picker stage 2', () => {
  test('shared order picker treats sales match as sales-order selection', () => {
    const picker = readSource('components', 'OrderPickerDialog.vue')

    expect(picker).toContain("'sales-match': 'market:sales-match:customer-select'")
    expect(picker).toContain("'sales-match': ['已报价']")
    expect(picker).toContain("'sales-match': '销售订单'")
    expect(picker).toContain("const SALES_ORDER_SCOPES = ['sales-match', 'u9-sync']")
    expect(picker).toContain("SALES_ORDER_SCOPES.includes(props.orderScope) ? '销售订单号' : 'RFQ号'")
    expect(picker).toContain("SALES_ORDER_SCOPES.includes(props.orderScope) ? '搜索销售订单号' : '搜索 RFQ 号'")
    expect(picker).toContain("SALES_ORDER_SCOPES.includes(props.orderScope) ? 'SALES_ORDER'")
  })
})
