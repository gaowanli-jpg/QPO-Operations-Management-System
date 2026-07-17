import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const currentDir = dirname(fileURLToPath(import.meta.url))
const css = readFileSync(resolve(currentDir, 'style.css'), 'utf8')

const expectCssIncludes = (values) => {
  for (const value of values) {
    expect(css, `Expected style.css to include ${value}`).toContain(value)
  }
}

describe('QPO style foundation', () => {
  test('defines visual design tokens for the QPO foundation', () => {
    expectCssIncludes([
      '--erp-font-sans',
      '--erp-space-4',
      '--erp-radius-lg',
      '--erp-shadow-sm',
      '--erp-z-dropdown',
      '--erp-duration-fast',
      '--erp-ease-standard'
    ])
  })

  test('maps Element Plus basics to QPO-compatible tokens', () => {
    expectCssIncludes([
      '--el-color-primary: var(--erp-primary)',
      '--el-border-color: var(--erp-border)',
      '--el-border-radius-base: var(--erp-radius-md)',
      '--el-table-header-bg-color: var(--erp-surface-soft)'
    ])
  })

  test('provides shared layout and component classes for later stages', () => {
    expectCssIncludes([
      '.erp-page',
      '.erp-page-header',
      '.erp-page-actions',
      '.erp-toolbar',
      '.erp-filter-bar',
      '.erp-surface',
      '.erp-section',
      '.erp-table-surface',
      '.erp-empty-state',
      '.erp-dialog-footer'
    ])
  })
})
