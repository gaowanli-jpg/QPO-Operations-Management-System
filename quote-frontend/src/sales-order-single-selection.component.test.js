import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

describe('sales order single-selection display', () => {
  test.each([
    ['SalesOrderU9Sync.vue']
  ])('%s uses a quote-process style single order summary', (componentName) => {
    const source = readSource('components', componentName)

    expect(source).toContain('<div class="panel-title">销售订单</div>')
    expect(source).toContain('<div class="selected-order-text" :title="selectedOrderLabel">')
    expect(source).toContain('{{ selectedOrderLabel }}')
    expect(source).toContain('const selectedOrderLabel = computed(() =>')
    expect(source).not.toContain('selected-order-mini-table')
    expect(source).not.toContain('selectedOrderRows')
  })
})
