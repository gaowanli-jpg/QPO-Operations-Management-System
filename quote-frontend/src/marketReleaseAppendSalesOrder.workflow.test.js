import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

describe('market release append sales order workflow', () => {
  test('adds append outlet without requiring left-side new-order defaults', () => {
    const source = readSource('components', 'MarketRelease.vue')

    expect(source).toContain('追加销售提单')
    expect(source).toContain('openAppendSalesOrderPicker')
    expect(source).toContain('appendSalesOrderDisabled')
    expect(source).toContain('appendReleaseToSalesOrder(payload)')
    expect(source).toContain('order-scope="release-append-sales-order"')
    expect(source).toContain('title="选择要追加的销售订单"')
    expect(source).toContain('仅用于生成正式提单：一批生成一张销售订单，零件值优先，空值继承订单默认信息；追加销售提单以目标销售订单头为准，左侧必填项不参与。')
    expect(source).toContain('请先保存已选零件的行级订单字段草稿，再追加销售提单。')
    expect(source).toContain('本次追加以目标销售订单头为准，不使用左侧订单默认信息。')

    const disabledDefinition = source.match(/const appendSalesOrderDisabled = computed\(\(\) => \(([^)]*)\)\)/s)?.[1] || ''
    expect(disabledDefinition).toContain('selectedRows.value.length === 0')
    expect(disabledDefinition).toContain('selectedRowsWithUnsavedOrderFields.value.length > 0')
    expect(disabledDefinition).toContain('submitting')
    expect(disabledDefinition).toContain('dataReplacementBusy')
    expect(disabledDefinition).not.toContain('releaseForm.documentTypeCode')
    expect(disabledDefinition).not.toContain('releaseForm.salesOrderDate')
    expect(disabledDefinition).not.toContain('releaseForm.customerOrderNo')
    expect(disabledDefinition).not.toContain('releaseForm.customerProjectNo')
    expect(disabledDefinition).not.toContain('releaseForm.customerOrderTitle')
    expect(disabledDefinition).not.toContain('releaseForm.orderType')
    expect(disabledDefinition).not.toContain('releaseForm.internalProjectNo')
  })

  test('append payload contains target order and selected RFQ identities only', () => {
    const source = readSource('components', 'MarketRelease.vue')

    const appendStart = source.indexOf('const appendToSalesOrder = async (targetOrder) => {')
    const appendEnd = source.indexOf('\nconst submitTicket = async () => {', appendStart)
    const appendHandler = appendStart >= 0 && appendEnd > appendStart ? source.slice(appendStart, appendEnd) : ''
    expect(appendHandler).toContain('targetSalesOrderId')
    expect(appendHandler).toContain('appendBatchKey')
    expect(appendHandler).toContain('rfqIds: batchRfqIds')
    expect(appendHandler).toContain('items: orderedSelectedRows.map')
    expect(appendHandler).not.toContain('documentTypeCode')
    expect(appendHandler).not.toContain('salesOrderDate')
    expect(appendHandler).not.toContain('customerOrderNo')
    expect(appendHandler).not.toContain('customerProjectNo')
    expect(appendHandler).not.toContain('customerOrderTitle')
    expect(appendHandler).not.toContain('orderType')
    expect(appendHandler).not.toContain('internalProjectNo')
  })
})
