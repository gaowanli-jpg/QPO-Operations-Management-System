import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const currentDir = dirname(fileURLToPath(import.meta.url))

const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

describe('ERP final UI punch-list polish', () => {
  test('keeps dense table and workflow action buttons readable', () => {
    const css = readSource('style.css')

    expect(css).toContain('.erp-table-actions .el-button--small')
    expect(css).toContain('.workflow-soft-button.el-button--small')
    expect(css).toContain('min-height: 30px')
  })

  test('uses quiet shared styling for the market folder-select fallback action', () => {
    const marketDashboard = readSource('components', 'MarketDashboard.vue')

    expect(marketDashboard).toContain('class="workflow-soft-button folder-select-button"')
    expect(marketDashboard).toContain('@click="triggerFolderSelect"')
    expect(marketDashboard).not.toContain('<el-button size="small" @click="triggerFolderSelect"')
    expect(marketDashboard).toContain('.folder-select-row :deep(.workflow-soft-button)')
  })
})
