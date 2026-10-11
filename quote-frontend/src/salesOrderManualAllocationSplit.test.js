import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const pageSource = readFileSync(resolve(currentDir, 'components', 'SalesOrderManage.vue'), 'utf8')
const apiSource = readFileSync(resolve(currentDir, 'services', 'orderApi.js'), 'utf8')
const migrationSource = readFileSync(resolve(currentDir, '..', '..', 'quote-backend', 'src', 'main', 'resources', 'db', 'migration', 'V60__manual_allocation_split.sql'), 'utf8')

describe('sales order manual allocation split', () => {
  test('provides manual split controls and live remainder validation', () => {
    expect(pageSource).toContain('label="人工拆行"')
    expect(pageSource).toContain('title="人工拆行"')
    expect(pageSource).toContain('锁已入库库存数量')
    expect(pageSource).toContain('锁在产库存数量')
    expect(pageSource).toContain('manualSplitRemainder')
    expect(pageSource).toContain('manualSplitAvailable(row)')
    expect(pageSource).toContain('allocationSplitSourceText')
  })

  test('posts manual split quantities through the sales order update endpoint', () => {
    expect(apiSource).toContain('/allocation-splits/manual')
    expect(pageSource).toContain('manualSplitSalesOrderAllocation(salesOrderId')
    expect(pageSource).toContain('inStockQty')
    expect(pageSource).toContain('inProductionQty')
  })

  test('persists source and parent columns for WMS/manual mutual exclusion', () => {
    expect(migrationSource).toContain('allocation_split_source')
    expect(migrationSource).toContain('split_parent_allocation_id')
    expect(migrationSource).toContain("'WMS', 'MANUAL'")
  })
})
