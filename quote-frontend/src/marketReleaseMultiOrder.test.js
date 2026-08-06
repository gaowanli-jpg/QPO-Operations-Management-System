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
    expect(source).toContain('isOrderAlreadySelected(row)')
    expect(source).toContain("multiple ? '添加订单' : '确定订单'")
    expect(source).toContain('emit(\'select\', Object.values(pendingSelectedOrders))')
  })

  test('market release uses selectedOrders and backend batch paging', () => {
    const source = readSource('components', 'MarketRelease.vue')
    const apiSource = readSource('services', 'marketApi.js')

    expect(apiSource).toContain('listReleaseItemsBatch')
    expect(apiSource).toContain('/api/market/release/items/batch')
    expect(source).toContain('const selectedOrders = ref([])')
    expect(source).toContain('const selectedOrderSources = computed(() => selectedOrders.value.map(toReleaseOrderSource))')
    expect(source).toContain('listReleaseItemsBatch')
    expect(source).toContain('fetchReleaseItemsBatch')
    expect(source).toContain('sources: selectedOrderSources.value')
    expect(source).toContain('watch(\n  () => [releaseItemPagination.page, releaseItemPagination.pageSize]')
    expect(source).toContain('fetchReleaseItemsBatch()')
    expect(source).not.toContain('fetchReleaseItems(selectedRfqId.value)')
  })

  test('market release displays order source columns and selected order summary table', () => {
    const source = readSource('components', 'MarketRelease.vue')

    expect(source).toContain('prop="sourceOrderNo" label="来源单号"')
    expect(source).toContain('prop="customerName" label="客户"')
    expect(source).toContain('class="selected-orders-mini-table"')
    expect(source).toContain('prop="status" label="订单状态"')
    expect(source).toContain('height="132"')
    expect(source).toContain(':multiple="true"')
    expect(source).toContain(':locked-customer-name="selectedCustomerName"')
    expect(source).toContain(':selected-order-ids="selectedOrderKeys"')
  })

  test('market release lets users remove selected orders without obsolete RFQ form field', () => {
    const source = readSource('components', 'MarketRelease.vue')

    expect(source).toContain('@click.stop="removeSelectedOrder(row)"')
    expect(source).toContain('const removeSelectedOrder = async (order) =>')
    expect(source).not.toContain('v-model="releaseForm.projectNo"')
    expect(source).not.toContain('projectNo: releaseForm.projectNo')
    expect(source).not.toContain("projectNo: ''")
  })

  test('market release imports selection across selected orders and preserves cross-page map', () => {
    const source = readSource('components', 'MarketRelease.vue')
    const apiSource = readSource('services', 'marketApi.js')

    expect(apiSource).toContain('parsePartSelectionBatch')
    expect(apiSource).toContain('/api/market/release/parse-part-selection/batch')
    expect(source).toContain('parsePartSelectionBatch')
    expect(source).toContain("formData.append('sources', JSON.stringify(selectedOrderSources.value))")
    expect(source).toContain('const response = await parsePartSelectionBatch(formData)')
    expect(source).toContain('selectedItemIdMap[rowKey(row)] = row')
    expect(source).toContain('await syncReleaseItemTableSelection()')
    expect(source).not.toContain('selectedOrders.value.length !== 1')
    expect(source).not.toContain('parsePartSelection(selectedRfqId.value, formData)')
  })

  test('market release groups generated tickets by RFQ source and refreshes selected RFQ orders', () => {
    const source = readSource('components', 'MarketRelease.vue')
    const submitBlock = source.slice(
      source.indexOf('const submitTicket = async () =>'),
      source.indexOf('const resolveDeliveryLeadTime')
    )

    expect(source).toContain('const groupSelectedRowsBySource = (rows) =>')
    expect(source).toContain('const refreshSelectedOrdersAfterGeneration = async (successItemIds = new Set()) =>')
    expect(source).toContain('const refreshedOrders = refreshedRfqOrders')
    expect(source).not.toContain('getSalesOrder')
    expect(source).not.toContain('selectedSalesOrderIds')
    expect(source).not.toContain("sourceType: 'SALES_ORDER'")
    expect(source).toContain('response.orderInfo || {}')
    expect(submitBlock).toContain('const releaseGroups = groupSelectedRowsBySource(orderedSelectedRows)')
    expect(submitBlock).toContain('for (const { source, rows } of releaseGroups.values())')
    expect(submitBlock).toContain('const response = await generateTicket(payload)')
    expect(submitBlock).toContain('successfulResults')
    expect(submitBlock).toContain('failedResults')
    expect(submitBlock).toContain('await refreshSelectedOrdersAfterGeneration(successItemIds)')
    expect(submitBlock).not.toContain('requireSingleSelectedRowsRfqId')
  })
})
