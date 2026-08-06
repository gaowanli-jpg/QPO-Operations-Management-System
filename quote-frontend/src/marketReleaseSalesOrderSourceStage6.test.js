import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

describe('market release RFQ-only source stage 6', () => {
  test('ticket generation submits RFQ source identity per selected row', () => {
    const source = readSource('components', 'MarketRelease.vue')

    expect(source).toContain('const releaseGroups = groupSelectedRowsBySource(orderedSelectedRows)')
    expect(source).toContain('const groupSelectedRowsBySource = (rows) => {')
    expect(source).toContain('sourceType: row?.sourceType || \'RFQ\'')
    expect(source).toContain('sourceOrderId: Number(row?.sourceOrderId ?? row?.rfqId ?? selectedRfqId.value)')
    expect(source).toContain('sourceItemId: Number(row?.sourceItemId ?? row?.id)')
    expect(source).toContain('itemId: row.id')
    expect(source).toContain('rfqId: source.sourceOrderId')
    expect(source).toContain('successfulResults.push({ source, rows, response })')
    expect(source).not.toContain('SALES_ORDER')
    expect(source).not.toContain('const releaseGroups = groupSelectedRowsByRfqId(orderedSelectedRows)')
  })
})
