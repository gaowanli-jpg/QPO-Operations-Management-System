import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))

const readComponent = (fileName) => readFileSync(resolve(currentDir, 'components', fileName), 'utf8')

describe('order manage filter layout', () => {
  test('places status filter before keyword search like sales order management', () => {
    const source = readComponent('OrderManage.vue')
    const toolbar = source.slice(
      source.indexOf('class="erp-toolbar-group order-filter-group"'),
      source.indexOf('<el-button @click="handleSearch">搜索</el-button>')
    )

    expect(toolbar.indexOf('v-model="statusFilter"')).toBeGreaterThan(-1)
    expect(toolbar.indexOf('v-model="keyword"')).toBeGreaterThan(-1)
    expect(toolbar.indexOf('v-model="statusFilter"')).toBeLessThan(toolbar.indexOf('v-model="keyword"'))
  })
})
