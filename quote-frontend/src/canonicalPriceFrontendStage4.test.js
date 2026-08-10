import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')
const legacyUnitPrice = ['final', 'Unit', 'Price'].join('')
const legacyTotalPrice = ['final', 'Total', 'Price'].join('')

describe('canonical frontend price fields stage 4', () => {
  test('business price surfaces use canonical tax included fields', () => {
    const files = [
      'components/ItemPricing.vue',
      'components/MarketRelease.vue',
      'components/OrderManage.vue',
      'components/QuoteProcess.vue',
      'components/SalesOrderU9Sync.vue'
    ]

    files.forEach((file) => {
      const source = readSource(file)
      expect(source).not.toContain(legacyUnitPrice)
      expect(source).not.toContain(legacyTotalPrice)
    })

    expect(readSource('components/ItemPricing.vue')).toContain('taxIncludedTotalPrice')
    expect(readSource('components/SalesOrderU9Sync.vue'))
      .toContain('prop="taxIncludedUnitPrice"')
      .toContain('含税单价缺失')
  })

  test('audit formatting exposes only canonical price labels', () => {
    const source = readSource('utils', 'auditLogFormat.js')

    expect(source)
      .toContain("taxIncludedUnitPrice: '含税单价'")
      .toContain("taxIncludedTotalPrice: '含税金额'")
      .toContain("untaxedUnitPrice: '未税单价'")
      .toContain("untaxedTotalPrice: '未税金额'")
      .not.toContain(legacyUnitPrice)
      .not.toContain(legacyTotalPrice)
  })
})
