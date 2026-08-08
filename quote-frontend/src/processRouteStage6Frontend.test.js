import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

describe('Stage 38 phase 6 item pricing process route payload', () => {
  test('ItemPricing submits processDetailId and unitWorkHours instead of price snapshots', () => {
    const source = readSource('components', 'ItemPricing.vue')
    const buildQuoteBlock = source.slice(
      source.indexOf('const buildQuoteProcessRoute'),
      source.indexOf('const buildGlobalProcessRoute')
    )

    expect(buildQuoteBlock).toContain('processDetailId: p.processDetailId')
    expect(buildQuoteBlock).toContain('unitWorkHours: p.inputVal')
    expect(buildQuoteBlock).toContain('sortNo: p.sortNo')
    expect(buildQuoteBlock).not.toContain('unitRate')
    expect(buildQuoteBlock).not.toContain('lineCost')
  })

  test('ItemPricing keeps processDetailId as route identity while preserving extra fee names', () => {
    const source = readSource('components', 'ItemPricing.vue')

    expect(source).toContain('processDetailId: source.processDetailId ?? null')
    expect(source).toContain('proc.processDetailId = selected.processDetailId')
    expect(source).toContain('return `process::${route.processDetailId || \'\'}`')
    expect(source).toContain('return `extra::${route.processName || \'\'}`')
    expect(source).not.toContain('return `${sourceType}::${route.processName || \'\'}`')
  })
})
