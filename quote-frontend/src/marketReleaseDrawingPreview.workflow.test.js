import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = fileURLToPath(new URL('.', import.meta.url))

const readSource = (file) => readFileSync(resolve(currentDir, 'components', file), 'utf8')

describe('market release drawing preview', () => {
  test('adds a drawing preview column after drawing status and uses authenticated PDF dialog', () => {
    const source = readSource('MarketRelease.vue')
    const drawingStatusIndex = source.indexOf('label="图纸"')
    const drawingPreviewIndex = source.indexOf('label="图纸预览"')
    const selectableStatusIndex = source.indexOf('label="可选状态"')

    expect(drawingStatusIndex).toBeGreaterThan(-1)
    expect(drawingPreviewIndex).toBeGreaterThan(drawingStatusIndex)
    expect(drawingPreviewIndex).toBeLessThan(selectableStatusIndex)
    expect(source).toContain('@click.stop="openPdfPreview(row.drawingUrl, row.partNo)"')
    expect(source).toContain(':title="`图纸预览：${pdfTitle}`"')
    expect(source).toContain('createAuthenticatedObjectUrl')
    expect(source).toContain('readFileErrorMessage')
    expect(source).toContain('revokeObjectUrl')
  })
})
