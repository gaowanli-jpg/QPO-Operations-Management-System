import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

describe('market release released item row visual state', () => {
  test('fully released rows get a dedicated visual row class', () => {
    const source = readSource('components', 'MarketRelease.vue')

    expect(source).toContain(':row-class-name="releaseItemRowClassName"')
    expect(source).toContain('const isFullyReleasedReleaseItem = (row) =>')
    expect(source).toContain('const releaseItemRowClassName = ({ row }) =>')
    expect(source).toContain('release-item-row--released')
    expect(source).toContain('.release-selection-table :deep(.release-item-row--released > td.el-table__cell)')
    expect(source).toContain('.release-selection-table :deep(.release-item-row--released > td[class*="el-table-fixed-column--"])')
    expect(source).toContain('box-shadow: inset 3px 0 0 #f59e0b;')
  })
})
