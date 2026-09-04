import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

describe('quote process optimized exports', () => {
  test('quote process exposes optimized detail export actions without replacing existing exports', () => {
    const source = readSource('components', 'QuoteProcess.vue')

    expect(source).toContain('生成详细报价单')
    expect(source).toContain('生成客户模板详细报价单')
    expect(source).toContain('优化详细报价单')
    expect(source).toContain('优化客户模板详细报价单')
    expect(source).toContain('exportOptimizedDetail: () => `market:quote-process:export-optimized-detail:${selectedRfqId.value || \'none\'}`')
    expect(source).toContain('exportOptimizedTemplateDetail: () => `market:quote-process:export-optimized-template-detail:${selectedRfqId.value || \'none\'}`')
    expect(source).toContain(':loading="isQuoteExporting(\'exportOptimizedDetail\')"')
    expect(source).toContain(':loading="isQuoteExporting(\'exportOptimizedTemplateDetail\')"')
    expect(source).toContain('@click="exportOptimizedQuote"')
    expect(source).toContain('@click="exportOptimizedTemplateQuote"')
  })

  test('optimized export buttons reuse the quote process export permission', () => {
    const source = readSource('components', 'QuoteProcess.vue')
    const optimizedDetailIndex = source.indexOf('优化详细报价单')
    const optimizedTemplateIndex = source.indexOf('优化客户模板详细报价单')

    expect(optimizedDetailIndex).toBeGreaterThanOrEqual(0)
    expect(optimizedTemplateIndex).toBeGreaterThanOrEqual(0)
    expect(source.slice(Math.max(0, optimizedDetailIndex - 500), optimizedDetailIndex)).toContain("v-permission=\"'market:quote-process:export'\"")
    expect(source.slice(Math.max(0, optimizedTemplateIndex - 500), optimizedTemplateIndex)).toContain("v-permission=\"'market:quote-process:export'\"")
  })
})
