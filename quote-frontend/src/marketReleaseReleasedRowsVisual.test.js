import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

describe('market release released item row visual state', () => {
  test('released rows no longer get a dedicated disabled visual row class', () => {
    const source = readSource('components', 'MarketRelease.vue')

    expect(source).not.toContain(':row-class-name="releaseItemRowClassName"')
    expect(source).not.toContain('const isFullyReleasedReleaseItem = (row) =>')
    expect(source).not.toContain('const releaseItemRowClassName = ({ row }) =>')
    expect(source).not.toContain('release-item-row--released')
    expect(source).not.toContain('box-shadow: inset 3px 0 0 #f59e0b;')
  })
})
