import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

describe('market release RFQ-only source governance stage 5', () => {
  test('release selection stores RFQ source objects instead of sales order candidates', () => {
    const source = readSource('components', 'MarketRelease.vue')

    expect(source).toContain('const selectedOrderSources = computed(() => selectedOrders.value.map(toReleaseOrderSource))')
    expect(source).toContain('const selectedOrderKeys = computed(() => selectedOrderSources.value.map(sourceKey))')
    expect(source).toContain(':selected-order-ids="selectedOrderKeys"')
    expect(source).toContain('const toReleaseOrderSource = (order) => ({')
    expect(source).toContain("sourceType: 'RFQ'")
    expect(source).toContain('sourceOrderId: Number(order?.sourceOrderId ?? order?.rfqId ?? order?.id)')
    expect(source).toContain('const sourceKey = (source) => `${source?.sourceType || \'RFQ\'}:${source?.sourceOrderId ?? source?.id ?? \'\'}')
    expect(source).toContain(".filter(order => (order?.sourceType || 'RFQ') === 'RFQ')")
    expect(source).not.toContain('const isSalesOrderSource')
    expect(source).not.toContain('selectedSalesOrderIds')
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

  test('release action empty-selection warnings use generic order copy', () => {
    const source = readSource('components', 'MarketRelease.vue')

    expect(source).toContain("ElMessage.warning('请先选择订单。')")
    expect(source).not.toContain("ElMessage.warning('请先选择询价单。')")
  })

  test('release page shows RFQ source order number without sales order default injection', () => {
    const source = readSource('components', 'MarketRelease.vue')

    expect(source).toContain('prop="sourceOrderNo" label="来源单号"')
    expect(source).toContain('class="source-order-cell"')
    expect(source).toContain('sourceTypeLabel(row.sourceType)')
    expect(source).toContain('const sourceTypeLabel = () => {')
    expect(source).not.toContain('applyReleaseDefaultsFromOrder')
    expect(source).not.toContain('releaseForm.orderType = order.orderType || releaseForm.orderType')
    expect(source).not.toContain('releaseForm.internalProjectNo = order.internalProjectNo || releaseForm.internalProjectNo')
    expect(source).not.toContain('releaseForm.customerOrderNo = order.customerOrderNo || releaseForm.customerOrderNo')
    expect(source).not.toContain('releaseForm.customerProjectNo = order.customerProjectNo || releaseForm.customerProjectNo')
    expect(source).toContain("const orderSourceNo = (order, source) => {")
    expect(source).toContain("return order?.sourceOrderNo || order?.rfqNo || source?.sourceOrderId || sourceKey(source)")
    expect(source).not.toContain('salesOrderNo')
    expect(source).not.toContain("return order?.sourceOrderNo || order?.rfqNo || source?.sourceOrderId || key")
  })

  test('order picker supports source-aware release rows while keeping reusable scopes', () => {
    const source = readSource('components', 'OrderPickerDialog.vue')
    const marketApi = readSource('services', 'marketApi.js')

    expect(marketApi).toContain("release: '/api/market/release/orders'")
    expect(source).toContain("const SALES_ORDER_SCOPES = ['sales-match', 'u9-sync']")
    expect(source).not.toContain("const SALES_ORDER_SCOPES = ['release'")
    expect(source).toContain('const resolveOrderSourceType = (order) => (')
    expect(source).toContain("order?.sourceType || 'RFQ'")
    expect(source).toContain('const resolveOrderId = (order) => order?.sourceOrderId ?? order?.salesOrderId ?? order?.id ?? \'\'')
    expect(source).toContain('const orderSourceKey = (order) => `${resolveOrderSourceType(order)}:${resolveOrderId(order)}`')
    expect(source).toContain('pendingSelectedOrders[orderSourceKey(order)] = order')
    expect(source).toContain('isOrderAlreadySelected(row)')
    expect(source).toContain('isOrderSelected(row)')
  })
})
