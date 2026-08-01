import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

describe('market release sales order source stage 5', () => {
  test('release selection stores source objects instead of RFQ ids only', () => {
    const source = readSource('components', 'MarketRelease.vue')

    expect(source).toContain('const selectedOrderSources = computed(() => selectedOrders.value.map(toReleaseOrderSource))')
    expect(source).toContain('const selectedOrderKeys = computed(() => selectedOrderSources.value.map(sourceKey))')
    expect(source).toContain(':selected-order-ids="selectedOrderKeys"')
    expect(source).toContain('const toReleaseOrderSource = (order) => ({')
    expect(source).toContain("sourceType: order?.sourceType || 'RFQ'")
    expect(source).toContain('sourceOrderId: Number(order?.sourceOrderId ?? order?.id)')
    expect(source).toContain('const sourceKey = (source) => `${source?.sourceType || \'RFQ\'}:${source?.sourceOrderId ?? source?.id ?? \'\'}')
    expect(source).toContain('const isSalesOrderSource = (source) => source?.sourceType === \'SALES_ORDER\'')
    expect(source).not.toContain('const selectedOrderIds = computed(() => selectedOrders.value.map(order => order.id))')
  })

  test('release item paging and excel selection submit mixed source payloads', () => {
    const source = readSource('components', 'MarketRelease.vue')
    const apiSource = readSource('services', 'marketApi.js')

    expect(apiSource).toContain('listReleaseItemsBatch')
    expect(apiSource).toContain('/api/market/release/items/batch')
    expect(source).toContain('sources: selectedOrderSources.value')
    expect(source).toContain("formData.append('sources', JSON.stringify(selectedOrderSources.value))")
    expect(source).toContain('parsePartSelectionBatch(formData)')
    expect(source).toContain('rowKey(row)')
    expect(source).toContain('selectedItemIdMap[rowKey(row)] = row')
    expect(source).toContain('acceptedQtyMap[rowKey(row)]')
  })

  test('release page shows source order number and defaults release parameters from sales order headers', () => {
    const source = readSource('components', 'MarketRelease.vue')

    expect(source).toContain('prop="sourceOrderNo" label="来源单号"')
    expect(source).toContain('prop="sourceType" label="来源"')
    expect(source).toContain('applyReleaseDefaultsFromOrder(nextOrders[0])')
    expect(source).toContain("if (!wasEmpty || !isSalesOrderSource(order)) return")
    expect(source).toContain('releaseForm.orderType = order.orderType || releaseForm.orderType')
    expect(source).toContain('releaseForm.internalProjectNo = order.internalProjectNo || releaseForm.internalProjectNo')
    expect(source).toContain('releaseForm.customerOrderNo = order.customerOrderNo || releaseForm.customerOrderNo')
    expect(source).toContain('releaseForm.customerProjectNo = order.customerProjectNo || releaseForm.customerProjectNo')
    expect(source).toContain("const orderSourceNo = (order, source) => {")
    expect(source).toContain("return order?.sourceOrderNo || order?.salesOrderNo || source?.sourceOrderId || sourceKey(source)")
    expect(source).toContain("return order?.sourceOrderNo || order?.rfqNo || source?.sourceOrderId || sourceKey(source)")
    expect(source).not.toContain("return order?.sourceOrderNo || order?.rfqNo || source?.sourceOrderId || key")
  })

  test('order picker supports source-aware release rows while keeping reusable scopes', () => {
    const source = readSource('components', 'OrderPickerDialog.vue')
    const marketApi = readSource('services', 'marketApi.js')

    expect(marketApi).toContain("release: '/api/market/release/orders'")
    expect(source).toContain('const resolveOrderSourceType = (order) => (')
    expect(source).toContain("order?.sourceType || 'RFQ'")
    expect(source).toContain('const resolveOrderId = (order) => order?.sourceOrderId ?? order?.salesOrderId ?? order?.id ?? \'\'')
    expect(source).toContain('const orderSourceKey = (order) => `${resolveOrderSourceType(order)}:${resolveOrderId(order)}`')
    expect(source).toContain('pendingSelectedOrders[orderSourceKey(order)] = order')
    expect(source).toContain('isOrderAlreadySelected(row)')
    expect(source).toContain('isOrderSelected(row)')
  })
})
