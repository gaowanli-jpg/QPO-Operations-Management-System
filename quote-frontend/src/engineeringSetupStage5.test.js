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

  test('can preload the current order from the route query', () => {
    const source = readSource('components', 'GlobalSetup.vue')

    expect(source).toContain("import { useRouter, useRoute } from 'vue-router'")
    expect(source).toContain('const route = useRoute()')
    expect(source).toContain('const initialRfqId = resolveRouteRfqId()')
    expect(source).toContain('await fetchOrderData()')
  })

  test('keeps GlobalSetup as preview-only without repeat quote application', () => {
    const source = readSource('components', 'GlobalSetup.vue')

    expect(source).not.toContain('applyRepeatQuotes')
    expect(source).not.toContain('manualRepeatSkipReasons')
    expect(source).not.toContain('notifyManualRepeatQuoteSkips')
    expect(source).not.toContain('skippedItems')
    expect(source).not.toContain('需手动补齐')
    expect(source).not.toContain('showClose: true')
    expect(source).not.toContain('duration: 0')
  })

  test('shows setup summary as a limited item preview with full order count', () => {
    const source = readSource('components', 'GlobalSetup.vue')

    expect(source).toContain('itemCount')
    expect(source).toContain('previewLimit')
    expect(source).toContain('仅预览')
    expect(source).toContain('完整零件清单')
  })

  test('describes repeat recommendations by part number and version instead of same customer', () => {
    const source = readSource('components', 'GlobalSetup.vue')

    expect(source).not.toContain('同客户已提单历史重复件')
    expect(source).not.toContain('同客户')
    expect(source).toContain('图号和版本')
    expect(source).toContain('历史重复件')
    expect(source).toContain('可自动复用')
  })
})
