import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8').replace(/\r\n/g, '\n')

describe('market release supplemental drawing workflow', () => {
  test('shows backend release blocked reason for supplemental quote items that cannot be selected', () => {
    const source = readSource('components', 'MarketRelease.vue')

    expect(source).toContain('<el-table-column label="可选状态"')
    expect(source).toContain('releaseBlockedReasonText')
    expect(source).toContain('row.releaseBlockedReason')
    expect(source).toContain('补充报价尚未确认')
    expect(source).toContain('canSelectItem(row)')
  })

  test('keeps accepted quantity editable without capping it to remaining quantity', () => {
    const source = readSource('components', 'MarketRelease.vue')

    expect(source).toContain('const defaultAcceptedQtyFor = (row) => {')
    expect(source).toContain('const remainingQty = remainingQtyFor(row)')
    expect(source).toContain('if (remainingQty > 0) return remainingQty')
    expect(source).toContain('const sourceQty = Number(row?.qty ?? 0)')
    expect(source).not.toContain(':max="remainingQtyFor(scope.row)"')
    expect(source).not.toContain('acceptedQty > remainingQty')
  })
})
