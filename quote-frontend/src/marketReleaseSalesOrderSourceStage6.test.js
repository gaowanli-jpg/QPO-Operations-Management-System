import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

describe('market release RFQ-only source stage 6', () => {
  test('ticket generation submits one RFQ batch with source identity per selected row', () => {
    const source = readSource('components', 'MarketRelease.vue')

    expect(source).toContain('rfqIds: selectedRfqIds.value')
    expect(source).toContain('releaseBatchKey')
    expect(source).toContain("sourceType: 'RFQ'")
    expect(source).toContain('sourceOrderId: Number(row?.sourceOrderId ?? row?.rfqId ?? selectedRfqId.value)')
    expect(source).toContain('sourceItemId: Number(row?.sourceItemId ?? row?.id)')
    expect(source).toContain('itemId: row.id')
    expect(source).toContain('const response = await generateTicket(payload)')
    expect(source).toContain('releaseResult.value = response')
    expect(source).toContain("router.push({ path: '/u9-sync', query: { salesOrderId: String(salesOrderId) } })")
    expect(source).toContain("v-permission=\"'market:u9-sync:view'\"")
    expect(source).not.toContain('rfqId: source.sourceOrderId')
    expect(source).not.toContain('successfulResults.push({ source, rows, response })')
    expect(source).not.toContain('SALES_ORDER')
    expect(source).not.toContain('const groupSelectedRowsBySource = (rows) => {')
  })
})
