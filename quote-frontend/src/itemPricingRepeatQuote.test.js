import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const currentDir = dirname(fileURLToPath(import.meta.url))

const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

const countOccurrences = (source, text) => source.split(text).length - 1

describe('item pricing repeat quote reuse ownership', () => {
  test('runs repeat quote reuse from ItemPricing after full pricing summary is loaded', () => {
    const source = readSource('components', 'ItemPricing.vue')

    expect(source).toContain('applyRepeatQuotes')
    expect(source).toContain('repeatApplyAttempted')
    expect(source).toContain('repeatApplyLoading')
    expect(source).toContain('await getPricingSummary(rfqId.value)')
    expect(source).toContain('await applyRepeatQuotes(rfqId.value)')
    expect(source).toContain('await fetchQuoteData')
  })

  test('does not surface missing assembly child reasons in the frontend', () => {
    const globalSetup = readSource('components', 'GlobalSetup.vue')
    const itemPricing = readSource('components', 'ItemPricing.vue')
    const combined = `${globalSetup}\n${itemPricing}`

    expect(combined).not.toContain('缺少子件')
    expect(combined).not.toContain('需手动补齐')
    expect(combined).not.toContain('skippedItems')
    expect(combined).not.toContain('manualRepeatSkipReasons')
    expect(combined).not.toContain('notifyManualRepeatQuoteSkips')
  })

  test('keeps repeat quote application to a single automatic attempt per pricing page load', () => {
    const source = readSource('components', 'ItemPricing.vue')

    expect(countOccurrences(source, 'applyRepeatQuotes(')).toBe(1)
    expect(source).toContain('if (!rfqId.value || repeatApplyAttempted.value) return')
    expect(source).toContain('repeatApplyAttempted.value = true')
  })

  test('requires item save permission before automatic repeat quote reuse', () => {
    const source = readSource('components', 'ItemPricing.vue')

    expect(source).toContain("const ENGINEER_PRICING_SAVE_ITEM_PERMISSION = 'engineer:pricing:save-item'")
    expect(source).toContain('const authStore = useAuthStore()')
    expect(source).toContain('authStore.hasPermission(ENGINEER_PRICING_SAVE_ITEM_PERMISSION)')
    expect(source).toContain('if (!canApplyRepeatQuotes.value) return')
  })
})
