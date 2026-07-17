import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const currentDir = dirname(fileURLToPath(import.meta.url))

const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

const countOccurrences = (source, text) => source.split(text).length - 1

describe('QPO Stage 6 item pricing workbench redesign', () => {
  test('moves ItemPricing onto the shared workbench page shell', () => {
    const source = readSource('components', 'ItemPricing.vue')

    for (const className of [
      'erp-page pricing-workbench-page',
      'erp-page-header pricing-page-header',
      'erp-page-actions pricing-page-actions',
      'erp-surface pricing-context-card',
      'pricing-metric-grid',
      'erp-toolbar pricing-toolbar',
      'erp-table-surface pricing-table-surface',
      'erp-empty-state pricing-empty-state'
    ]) {
      expect(source, `Expected ItemPricing.vue to include ${className}`).toContain(className)
    }

    for (const oldClass of [
      'class="pricing-container"',
      'class="main-card"',
      'class="card-header"',
      'class="toolbar-row"',
      'class="table-shell"',
      'class="empty-state"'
    ]) {
      expect(source, `Expected ItemPricing.vue to stop using ${oldClass}`).not.toContain(oldClass)
    }
  })

  test('separates pricing dialog sections with shared dialog and action patterns', () => {
    const source = readSource('components', 'ItemPricing.vue')

    for (const className of [
      'pricing-workbench-section pricing-mode-section',
      'pricing-workbench-section pricing-material-section',
      'pricing-workbench-section pricing-process-section',
      'pricing-workbench-section pricing-assembly-section',
      'pricing-workbench-summary',
      'erp-dialog-footer pricing-dialog-footer',
      'erp-table-actions pricing-row-actions'
    ]) {
      expect(source, `Expected ItemPricing.vue to include ${className}`).toContain(className)
    }

    expect(countOccurrences(source, 'erp-dialog-section pricing-workbench-section')).toBeGreaterThanOrEqual(4)
    expect(countOccurrences(source, 'erp-dialog-footer')).toBeGreaterThanOrEqual(2)
  })

  test('keeps Stage 6 visual-only without splitting the pricing workflow', () => {
    const source = readSource('components', 'ItemPricing.vue')

    expect(source).not.toContain("from '../components/pricing/")
    expect(source).not.toContain("from './pricing/")
    expect(source).toContain('submitCurrentQuote')
    expect(source).toContain('fetchQuoteData')
    expect(source).toContain('globalConfigStore')
  })
})
