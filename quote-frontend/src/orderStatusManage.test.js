import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

const orderManage = readSource('components', 'OrderManage.vue')
const orderApi = readSource('services', 'orderApi.js')

describe('order status management stage 0 guardrails', () => {
  test('freezes the six order management status filters', () => {
    expect(orderManage).toContain('ORDER_MANAGEMENT_STATUSES')
    for (const status of ['未核价', '已核价', '已报价', '部分已提单', '已生成提单', '废弃']) {
      expect(orderManage).toContain(status)
    }
  })

  test('freezes status action permissions under order management', () => {
    for (const permission of [
      'market:order:rollback-quoted',
      'market:order:rollback-priced',
      'market:order:discard',
      'market:order:restore'
    ]) {
      expect(orderManage).toContain(permission)
    }
  })

  test('freezes action labels and immutable released-order boundary', () => {
    expect(orderManage).toContain('退回已核价')
    expect(orderManage).toContain('退回未核价')
    expect(orderManage).toContain('废弃')
    expect(orderManage).toContain('恢复为已核价')
    expect(orderManage).toContain('部分已提单')
    expect(orderManage).toContain('已生成提单')
    expect(orderManage).toContain('订单已生成提单，不能废弃或回退')
  })

  test('freezes order status action APIs', () => {
    expect(orderApi).toContain('rollbackQuotedOrder')
    expect(orderApi).toContain('/api/order-management/orders/${rfqId}/rollback-quoted')
    expect(orderApi).toContain('rollbackPricedOrder')
    expect(orderApi).toContain('/api/order-management/orders/${rfqId}/rollback-priced')
    expect(orderApi).toContain('discardOrder')
    expect(orderApi).toContain('/api/order-management/orders/${rfqId}/discard')
    expect(orderApi).toContain('restoreOrder')
    expect(orderApi).toContain('/api/order-management/orders/${rfqId}/restore')
  })
})
