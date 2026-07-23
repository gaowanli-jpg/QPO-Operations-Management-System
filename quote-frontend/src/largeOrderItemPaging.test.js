import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

describe('large order item paging contracts', () => {
  test('keeps OrderPickerDialog on server-side order pagination', () => {
    const source = readSource('components', 'OrderPickerDialog.vue')

    expect(source).toContain('listMarketOrders')
    expect(source).toContain('listSetupOrders')
    expect(source).toContain('page: page.value')
    expect(source).toContain('pageSize: pageSize.value')
    expect(source).toContain('total.value = response.total')
    expect(source).toContain(':total="total"')
  })

  test('keeps GlobalSetup as a preview-only page', () => {
    const source = readSource('components', 'GlobalSetup.vue')

    expect(source).toContain('previewLimit')
    expect(source).toContain('itemCount')
    expect(source).toContain('仅预览')
    expect(source).toContain('完整零件清单')
    expect(source).not.toContain('applyRepeatQuotes')
  })

  test('does not use full item arrays as server totals in item operation pages', () => {
    const itemPricing = readSource('components', 'ItemPricing.vue')
    const quoteProcess = readSource('components', 'QuoteProcess.vue')
    const marketRelease = readSource('components', 'MarketRelease.vue')

    expect(itemPricing).toContain('pricingItemTotal')
    expect(itemPricing).toContain(':total="pricingItemTotal"')
    expect(itemPricing).not.toContain(':total="quoteData.itemList.length"')

    expect(quoteProcess).toContain('quoteProcessItemTotal')
    expect(quoteProcess).toContain(':total="quoteProcessItemTotal"')
    expect(quoteProcess).not.toContain(':total="itemList.length"')

    expect(marketRelease).toContain('releaseItemTotal')
    expect(marketRelease).toContain(':total="releaseItemTotal"')
    expect(marketRelease).not.toContain(':total="itemList.length"')
    expect(marketRelease).not.toContain(':total="ticketList.length"')
    expect(marketRelease).not.toContain(':total="processSheetList.length"')
  })

  test('item pricing page uses backend paging for the main pricing table', () => {
    const source = readSource('components', 'ItemPricing.vue')

    expect(source).toContain('listPricingItems')
    expect(source).toContain('fetchPricingItems')
    expect(source).toContain('quoteData.value.itemList = response.records || []')
    expect(source).toContain('pricingItemTotal.value = Number(response.total ?? 0)')
    expect(source).toContain('watch(\n  () => [pricingListPagination.page, pricingListPagination.pageSize]')
    expect(source).not.toContain('const pagedPricingItems = computed(() => paginateRows(quoteData.value?.itemList || [], pricingListPagination))')
  })

  test('item pricing assembly child picker uses backend paging and search', () => {
    const source = readSource('components', 'ItemPricing.vue')

    expect(source).toContain('listAssemblyChildCandidates')
    expect(source).toContain('fetchAssemblyChildCandidates')
    expect(source).toContain('assemblyChildKeyword')
    expect(source).toContain(':total="assemblyChildTotal"')
    expect(source).toContain('keyword: assemblyChildKeyword.value')
    expect(source).not.toContain('const pagedAssemblyChildOptions = computed(() => paginateRows(assemblyChildOptions.value, assemblyPickerPagination))')
  })

  test('quote process page uses backend paging and split summary', () => {
    const source = readSource('components', 'QuoteProcess.vue')
    const apiSource = readSource('services', 'marketApi.js')

    expect(apiSource).toContain('getQuoteProcessSummary')
    expect(apiSource).toContain('/api/market/quote-process/${rfqId}/summary')
    expect(apiSource).toContain('listQuoteProcessItems')
    expect(apiSource).toContain('/api/market/quote-process/${rfqId}/items')
    expect(apiSource).not.toContain('export const getQuoteProcess =')

    expect(source).toContain('getQuoteProcessSummary')
    expect(source).toContain('listQuoteProcessItems')
    expect(source).toContain('fetchQuoteProcessSummary')
    expect(source).toContain('fetchQuoteProcessItems')
    expect(source).toContain('itemList.value = response.records || []')
    expect(source).toContain('quoteProcessItemTotal.value = Number(response.total ?? 0)')
    expect(source).toContain('watch(\n  () => [quoteProcessPagination.page, quoteProcessPagination.pageSize]')
    expect(source).not.toContain('const pagedQuoteProcessItems = computed(() => paginateRows(itemList.value, quoteProcessPagination))')
    expect(source).not.toContain('paginateRows')
  })

  test('quote process batch lead time uses cross-page selected id set', () => {
    const source = readSource('components', 'QuoteProcess.vue')

    expect(source).toContain('selectedQuoteProcessItemIds')
    expect(source).toContain('selectedQuoteProcessItemIds.value.size')
    expect(source).toContain('itemIds: selectedQuoteProcessIdsForSubmit()')
    expect(source).toContain('currentPageIds.forEach(id => selectedQuoteProcessItemIds.value.delete(id))')
    expect(source).toContain('rows.forEach(row => selectedQuoteProcessItemIds.value.add(row.id))')
    expect(source).not.toContain('selectedRows.value.map(row => row.id)')
  })

  test('quote process imports an excel file to lock cross-page selected item ids', () => {
    const source = readSource('components', 'QuoteProcess.vue')
    const apiSource = readSource('services', 'marketApi.js')

    expect(apiSource).toContain('parseQuoteProcessPartSelection')
    expect(apiSource).toContain('/api/market/quote-process/${rfqId}/parse-part-selection')
    expect(source).toContain('parseQuoteProcessPartSelection')
    expect(source).toContain('selectionUploadRef')
    expect(source).toContain('importSelecting')
    expect(source).toContain('handleQuoteProcessSelectionExcelChange')
    expect(source).toContain(':on-change="handleQuoteProcessSelectionExcelChange"')
    expect(source).toContain('selectedQuoteProcessItemIds.value = new Set(importedIds)')
    expect(source).toContain('await syncQuoteProcessPageSelection()')
  })

  test('market release page uses backend paging and cross-page selection state', () => {
    const source = readSource('components', 'MarketRelease.vue')
    const apiSource = readSource('services', 'marketApi.js')

    expect(apiSource).toContain('listReleaseItems')
    expect(apiSource).toContain('/api/market/release/items/${rfqId}')
    expect(apiSource).toContain('listReleaseItemsBatch')
    expect(apiSource).toContain('/api/market/release/items/batch')
    expect(apiSource).toContain('listTickets')
    expect(apiSource).toContain('/api/market/tickets/${rfqId}')
    expect(apiSource).toContain('listProcessSheets')
    expect(apiSource).toContain('/api/market/process-sheets/${rfqId}')
    expect(apiSource).toContain('/api/market/release/parse-part-selection/${rfqId}')
    expect(apiSource).not.toContain('loadReleaseWorkspace')

    expect(source).toContain('selectedOrders')
    expect(source).toContain('selectedOrderIds')
    expect(source).toContain('listReleaseItemsBatch')
    expect(source).toContain('fetchReleaseItemsBatch')
    expect(source).toContain('rfqIds: selectedOrderIds.value')
    expect(source).toContain('setItemList(response.records || [])')
    expect(source).toContain('releaseItemTotal.value = Number(response.total ?? 0)')
    expect(source).toContain('selectedItemIdMap')
    expect(source).toContain('currentPageIds.forEach(id => delete selectedItemIdMap[id])')
    expect(source).toContain("formData.append('rfqIds', JSON.stringify(selectedOrderIds.value))")
    expect(source).toContain('parsePartSelectionBatch(formData)')
    expect(source).toContain('selected-orders-mini-table')
    expect(source).not.toContain('paginateRows')
    expect(source).not.toContain('listInternalTargetLeadTimeItems')
  })

  test('order detail API exposes independent paged tabs and drawing summary', () => {
    const apiSource = readSource('services', 'orderApi.js')

    expect(apiSource).toContain('listOrderItems')
    expect(apiSource).toContain('/api/order-management/orders/${rfqId}/items')
    expect(apiSource).toContain('listOrderTickets')
    expect(apiSource).toContain('/api/order-management/orders/${rfqId}/tickets')
    expect(apiSource).toContain('listOrderProcessSheets')
    expect(apiSource).toContain('/api/order-management/orders/${rfqId}/process-sheets')
    expect(apiSource).toContain('getOrderDrawingSummary')
    expect(apiSource).toContain('/api/order-management/orders/${rfqId}/drawing-summary')
  })

  test('order management detail drawer lazy-loads paged tab data', () => {
    const source = readSource('components', 'OrderManage.vue')

    expect(source).toContain('activeDetailTab')
    expect(source).toContain('@tab-change="handleDetailTabChange"')
    expect(source).toContain('fetchDetailItems')
    expect(source).toContain('fetchDetailTickets')
    expect(source).toContain('fetchDetailProcessSheets')
    expect(source).toContain('fetchDrawingSummary')
    expect(source).toContain('detailItemTotal')
    expect(source).toContain('detailTicketTotal')
    expect(source).toContain('detailProcessSheetTotal')
    expect(source).toContain(':total="detailItemTotal"')
    expect(source).toContain(':total="detailTicketTotal"')
    expect(source).toContain(':total="detailProcessSheetTotal"')
    expect(source).toContain('await fetchDrawingSummary(currentId)')
    expect(source).toContain('await fetchCurrentDetailTab()')
    expect(source).not.toContain('orderDetail.itemList || []')
    expect(source).not.toContain('orderDetail.ticketList || []')
    expect(source).not.toContain('orderDetail.processSheetList || []')
    expect(source).not.toContain('orderDetail.value = await getOrder(currentId)')
  })

  test('history order detail drawer lazy-loads read-only paged tab data', () => {
    const source = readSource('components', 'HistoryOrderManage.vue')

    expect(source).toContain('activeDetailTab')
    expect(source).toContain('@tab-change="handleDetailTabChange"')
    expect(source).toContain('fetchDetailItems')
    expect(source).toContain('fetchDetailTickets')
    expect(source).toContain('fetchDetailProcessSheets')
    expect(source).toContain('detailItemTotal')
    expect(source).toContain('detailTicketTotal')
    expect(source).toContain('detailProcessSheetTotal')
    expect(source).toContain(':total="detailItemTotal"')
    expect(source).toContain(':total="detailTicketTotal"')
    expect(source).toContain(':total="detailProcessSheetTotal"')
    expect(source).not.toContain('orderDetail.itemList || []')
    expect(source).not.toContain('orderDetail.ticketList || []')
    expect(source).not.toContain('orderDetail.processSheetList || []')
  })
})
