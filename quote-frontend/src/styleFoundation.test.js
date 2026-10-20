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

const expectCssMatches = (pattern, message) => {
  expect(css, message).toMatch(pattern)
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

  test('uses an attached light module header pattern for page shells', () => {
    expectCssIncludes([
      '--erp-page-gap: var(--erp-space-4)',
      'gap: var(--erp-page-gap)',
      'margin-bottom: calc(-1 * var(--erp-page-gap))',
      'border-radius: var(--erp-radius-xl) var(--erp-radius-xl) 0 0',
      '--erp-page-gap: var(--erp-space-5)',
      '.erp-page-header + .erp-surface',
      'border-top-left-radius: 0',
      'border-top-right-radius: 0'
    ])
    expectCssMatches(
      /\.erp-page-header\s*\{[^}]*background: var\(--erp-surface-soft\);[^}]*color: var\(--erp-text\);[^}]*\}/,
      'Expected .erp-page-header to use the light step-section header background and text color'
    )
    expectCssMatches(
      /\.erp-page-header h1,[^{]*\.erp-page-header h2,[^{]*\.erp-page-title\s*\{[^}]*color: var\(--erp-text\);[^}]*\}/,
      'Expected page header titles to use normal dark text on the light header'
    )
    expectCssMatches(
      /\.erp-page-header p,[^{]*\.erp-page-description\s*\{[^}]*color: var\(--erp-text-secondary\);[^}]*\}/,
      'Expected page header descriptions to use secondary text on the light header'
    )
  })
})
