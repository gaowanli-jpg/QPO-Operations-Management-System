import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

describe('market release multi-order stage 2', () => {
  test('order picker exposes reusable multiple append mode', () => {
    const source = readSource('components', 'OrderPickerDialog.vue')

    expect(source).toContain('multiple: {')
    expect(source).toContain('lockedCustomerName: {')
    expect(source).toContain('selectedOrderIds: {')
    expect(source).toContain('pendingSelectedOrders')
    expect(source).toContain('isOrderAlreadySelected(row.id)')
    expect(source).toContain("multiple ? '添加订单' : '确定订单'")
    expect(source).toContain('emit(\'select\', Object.values(pendingSelectedOrders))')
  })

  test('market release uses selectedOrders and backend batch paging', () => {
    const source = readSource('components', 'MarketRelease.vue')
    const apiSource = readSource('services', 'marketApi.js')

    expect(apiSource).toContain('listReleaseItemsBatch')
    expect(apiSource).toContain('/api/market/release/items/batch')
    expect(source).toContain('const selectedOrders = ref([])')
    expect(source).toContain('const selectedOrderIds = computed(() => selectedOrders.value.map(order => order.id))')
    expect(source).toContain('listReleaseItemsBatch')
    expect(source).toContain('fetchReleaseItemsBatch')
    expect(source).toContain('rfqIds: selectedOrderIds.value')
    expect(source).toContain('watch(\n  () => [releaseItemPagination.page, releaseItemPagination.pageSize]')
    expect(source).toContain('fetchReleaseItemsBatch()')
    expect(source).not.toContain('fetchReleaseItems(selectedRfqId.value)')
  })

  test('market release displays order source columns and removes selected order rows', () => {
    const source = readSource('components', 'MarketRelease.vue')

    expect(source).toContain('prop="rfqNo" label="RFQ号"')
    expect(source).toContain('prop="customerName" label="客户"')
    expect(source).toContain('prop="orderStatus" label="订单状态"')
    expect(source).toContain('removeSelectedOrder')
    expect(source).toContain('row.rfqId === orderId')
    expect(source).toContain('delete selectedItemIdMap[id]')
    expect(source).toContain('selectedOrders.value = selectedOrders.value.filter(order => order.id !== orderId)')
    expect(source).toContain(':multiple="true"')
    expect(source).toContain(':locked-customer-name="selectedCustomerName"')
    expect(source).toContain(':selected-order-ids="selectedOrderIds"')
  })

  test('market release imports selection across selected orders and preserves cross-page map', () => {
    const source = readSource('components', 'MarketRelease.vue')
    const apiSource = readSource('services', 'marketApi.js')

    expect(apiSource).toContain('parsePartSelectionBatch')
    expect(apiSource).toContain('/api/market/release/parse-part-selection/batch')
    expect(source).toContain('parsePartSelectionBatch')
    expect(source).toContain("formData.append('rfqIds', JSON.stringify(selectedOrderIds.value))")
    expect(source).toContain('const response = await parsePartSelectionBatch(formData)')
    expect(source).toContain('selectedItemIdMap[row.id] = row')
    expect(source).toContain('await syncReleaseItemTableSelection()')
    expect(source).not.toContain('selectedOrders.value.length !== 1')
    expect(source).not.toContain('parsePartSelection(selectedRfqId.value, formData)')
  })
})
