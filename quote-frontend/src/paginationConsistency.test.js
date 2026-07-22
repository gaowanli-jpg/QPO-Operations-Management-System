import { describe, expect, test } from 'vitest'
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const srcDir = dirname(fileURLToPath(import.meta.url))

function collectVueFiles(dir) {
  return readdirSync(dir).flatMap((entry) => {
    const fullPath = join(dir, entry)
    if (statSync(fullPath).isDirectory()) {
      return collectVueFiles(fullPath)
    }
    return fullPath.endsWith('.vue') ? [fullPath] : []
  })
}

describe('pagination consistency', () => {
  test('all QPO pagination controls use the shared full layout', () => {
    const vueFiles = collectVueFiles(srcDir)
    const paginationBlocks = vueFiles.flatMap((file) => {
      const content = readFileSync(file, 'utf8')
      return [...content.matchAll(/<el-pagination[\s\S]*?\/>/g)].map((match) => ({
        file,
        block: match[0]
      }))
    })

    expect(paginationBlocks.length).toBeGreaterThan(0)
    for (const { file, block } of paginationBlocks) {
      expect(block, file).toContain('layout="total, sizes, prev, pager, next, jumper"')
      expect(block, file).toContain('background')
      expect(block, file).toContain(':pager-count="7"')
    }
  })

  test('large workflow tables use client-side paged data', () => {
    const quoteProcess = readFileSync(join(srcDir, 'components', 'QuoteProcess.vue'), 'utf8')
    const marketRelease = readFileSync(join(srcDir, 'components', 'MarketRelease.vue'), 'utf8')
    const itemPricing = readFileSync(join(srcDir, 'components', 'ItemPricing.vue'), 'utf8')

    expect(quoteProcess).toContain(':data="pagedQuoteProcessItems"')
    expect(quoteProcess).toContain('quoteProcessPagination')

    expect(marketRelease).toContain(':data="pagedReleaseItems"')
    expect(marketRelease).toContain(':data="pagedTicketList"')
    expect(marketRelease).toContain(':data="pagedProcessSheetList"')
    expect(marketRelease).toContain('releaseItemPagination')
    expect(marketRelease).toContain('ticketPagination')
    expect(marketRelease).toContain('processSheetPagination')

    expect(itemPricing).toContain(':data="pagedPricingItems"')
    expect(itemPricing).toContain(':data="pagedAssemblyChildOptions"')
    expect(itemPricing).toContain('pricingListPagination')
    expect(itemPricing).toContain('assemblyPickerPagination')
  })

  test('all pageable controls reset to first page when page size changes', () => {
    const vueFiles = collectVueFiles(srcDir)
    const paginationBlocks = vueFiles.flatMap((file) => {
      const content = readFileSync(file, 'utf8')
      return [...content.matchAll(/<el-pagination[\s\S]*?\/>/g)].map((match) => ({
        file,
        block: match[0]
      }))
    })

    expect(paginationBlocks.length).toBeGreaterThan(0)
    for (const { file, block } of paginationBlocks) {
      expect(block, file).toContain('@size-change=')
    }
  })
})
