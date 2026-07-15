import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const currentDir = dirname(fileURLToPath(import.meta.url))

const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

const readComponent = (fileName) => readSource('components', fileName)

const expectIncludes = (source, fileName, tokens) => {
  for (const token of tokens) {
    expect(source, `Expected ${fileName} to include ${token}`).toContain(token)
  }
}

const countNoisySemanticButtons = (source) => {
  return [...source.matchAll(/<el-button\b[^>]*\btype="(?:success|warning)"/g)].length
}

describe('ERP workflow page polish', () => {
  test('uses shared workflow workbench classes on dense market pages', () => {
    const expectations = [
      ['QuoteProcess.vue', 'quote-process-page'],
      ['MarketRelease.vue', 'release-page']
    ]

    for (const [fileName, pageClass] of expectations) {
      const source = readComponent(fileName)

      expectIncludes(source, fileName, [
        'workflow-page',
        pageClass,
        'workflow-workspace',
        'workflow-side-panel',
        'workflow-main-panel',
        'workflow-batch-panel',
        'workflow-action-stack',
        'workflow-muted-actions',
        'workflow-soft-button',
        'workflow-primary-action'
      ])
    }
  })

  test('keeps batch and export actions visually quiet', () => {
    const pages = ['QuoteProcess.vue', 'MarketRelease.vue']

    for (const fileName of pages) {
      const source = readComponent(fileName)

      expect(
        countNoisySemanticButtons(source),
        `Expected ${fileName} to avoid success/warning button colors in workflow action groups`
      ).toBe(0)
    }
  })

  test('preserves the existing quote and release workflow handlers', () => {
    const quoteSource = readComponent('QuoteProcess.vue')
    const releaseSource = readComponent('MarketRelease.vue')

    expectIncludes(quoteSource, 'QuoteProcess.vue', [
      '@click="openOrderPicker"',
      '@click="applyShift(1)"',
      '@click="applyShift(-1)"',
      '@click="setQuoteLeadTime"',
      '@click="exportQuote"',
      '@click="exportSimpleQuote"',
      '@click="exportTemplateQuote"',
      '@click="exportTemplateSimpleQuote"',
      '@click="saveLeadTime(row)"'
    ])

    expectIncludes(releaseSource, 'MarketRelease.vue', [
      '@click="openOrderPicker"',
      ':on-change="handleSelectionExcelChange"',
      '@click="adjustInternalLeadTime(1)"',
      '@click="adjustInternalLeadTime(-1)"',
      '@click="setInternalTargetLeadTime"',
      '@click="submitTicket"',
      '@click="deleteTicket(scope.row)"',
      '@click="deleteProcessSheet(scope.row)"',
      '@click="exportExcel(\'tickets\')"',
      '@click="exportExcel(\'process\')"'
    ])
  })
})
