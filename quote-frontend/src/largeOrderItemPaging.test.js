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
    expect(marketRelease).toContain('releaseTicketTotal')
    expect(marketRelease).toContain('releaseProcessSheetTotal')
    expect(marketRelease).toContain(':total="releaseItemTotal"')
    expect(marketRelease).toContain(':total="releaseTicketTotal"')
    expect(marketRelease).toContain(':total="releaseProcessSheetTotal"')
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
})
