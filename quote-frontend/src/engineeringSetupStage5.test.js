import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const currentDir = dirname(fileURLToPath(import.meta.url))

const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

const countOccurrences = (source, text) => source.split(text).length - 1

describe('QPO Stage 5 engineering setup redesign', () => {
  test('moves GlobalSetup onto the shared QPO page shell', () => {
    const source = readSource('components', 'GlobalSetup.vue')

    for (const className of [
      'erp-page engineering-setup-page',
      'erp-page-header',
      'erp-page-actions engineering-header-actions',
      'engineering-context-card',
      'engineering-metric-grid',
      'engineering-workspace',
      'engineering-overview-panel',
      'engineering-config-panel'
    ]) {
      expect(source, `Expected GlobalSetup.vue to include ${className}`).toContain(className)
    }

    expect(source).not.toContain('class="setup-container"')
    expect(source).not.toContain('class="test-header"')
    expect(source).not.toContain('class="overview-section"')
    expect(source).not.toContain('class="config-section"')
  })

  test('groups configuration and drawing actions with shared Stage 4 patterns', () => {
    const source = readSource('components', 'GlobalSetup.vue')

    expect(countOccurrences(source, 'erp-dialog-section engineering-config-section')).toBeGreaterThanOrEqual(2)
    expect(source).toContain('erp-table-actions drawing-actions')
    expect(source).toContain('engineering-save-panel')
    expect(source).toContain('erp-dialog-footer preview-dialog-footer')
  })

  test('keeps ItemPricing deferred for Stage 6', () => {
    const source = readSource('components', 'ItemPricing.vue')

    expect(source).not.toContain('engineering-save-panel')
    expect(source).not.toContain('engineering-workspace')
  })
})
