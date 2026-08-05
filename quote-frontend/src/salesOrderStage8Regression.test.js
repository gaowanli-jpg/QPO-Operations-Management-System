import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

describe('sales order independent stage 8 regression', () => {
  test('keeps sales order import inside new-order import without RFQ result leakage', () => {
    const marketDashboard = readSource('components', 'MarketDashboard.vue')
    const salesOrderPane = marketDashboard.slice(
      marketDashboard.indexOf('name="sales-order"'),
      marketDashboard.indexOf('</el-tab-pane>', marketDashboard.indexOf('name="sales-order"'))
    )

    expect(marketDashboard).toContain("activeImportMode = ref('rfq')")
    expect(marketDashboard).toContain('name="rfq"')
    expect(marketDashboard).toContain('name="sales-order"')
    expect(marketDashboard).toContain("v-permission=\"'market:rfq:import-sales-order'\"")
    expect(marketDashboard).toContain('importSalesOrderToQpo(formData)')
    expect(salesOrderPane).toContain('prop="salesOrderNo"')
    expect(salesOrderPane).toContain('prop="customerOrderNo"')
    expect(salesOrderPane).toContain('prop="matchedItemCount"')
    expect(salesOrderPane).toContain('label="已补全数"')
    expect(salesOrderPane).toContain('prop="unmatchedItemCount"')
    expect(salesOrderPane).toContain('label="待销售匹配"')
    expect(salesOrderPane).not.toContain('label="历史匹配数"')
    expect(salesOrderPane).not.toContain('prop="rfqNo"')
    expect(salesOrderPane).not.toContain('label="RFQ')
  })

  test('keeps RFQ and sales order release paging source-aware while downstream pages stay server paged', () => {
    const marketRelease = readSource('components', 'MarketRelease.vue')
    const deliveryDocument = readSource('components', 'DeliveryDocumentManage.vue')
    const deliveredItem = readSource('components', 'DeliveredItemManage.vue')
    const lockedItem = readSource('components', 'LockedItemManage.vue')
    const marketApi = readSource('services', 'marketApi.js')
    const deliveredItemApi = readSource('services', 'deliveredItemApi.js')

    expect(marketRelease).toContain('selectedOrderSources')
    expect(marketRelease).toContain("sourceType: row?.sourceType || 'RFQ'")
    expect(marketRelease).toContain('sourceOrderId: Number(row?.sourceOrderId ?? row?.rfqId ?? selectedRfqId.value)')
    expect(marketRelease).toContain('sourceItemId: Number(row?.sourceItemId ?? row?.id)')
    expect(marketRelease).toContain('sources: selectedOrderSources.value')
    expect(marketApi).toContain("listReleaseItemsBatch = (payload) => postJson('/api/market/release/items/batch', payload)")
    expect(marketRelease).not.toContain('fetchAllReleaseItems')

    expect(deliveryDocument).toContain('listDeliveryDocumentTickets(source.sourceOrderId, {')
    expect(deliveryDocument).toContain('listDeliveryDocumentProcessSheets(source.sourceOrderId, {')
    expect(deliveryDocument).toContain('...sourceParams(source),')
    expect(deliveredItem).toContain('listDeliveredItems(buildParams())')
    expect(lockedItem).toContain('listLockedItems(buildParams())')
    expect(deliveredItemApi).toContain("request('/api/order-delivery/tickets', { params })")
    expect(deliveredItemApi).toContain("request('/api/order-delivery/locked-items', { params })")
  })
})
