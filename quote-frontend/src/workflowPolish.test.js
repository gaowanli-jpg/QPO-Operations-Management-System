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

const expectExcludes = (source, fileName, tokens) => {
  for (const token of tokens) {
    expect(source, `Expected ${fileName} to exclude ${token}`).not.toContain(token)
  }
}

const countNoisySemanticButtons = (source) => {
  return [...source.matchAll(/<el-button\b[^>]*\btype="(?:success|warning)"/g)].length
}

describe('QPO workflow page polish', () => {
  test('keeps quote process as a focused workflow workbench', () => {
    const source = readComponent('QuoteProcess.vue')

    expectIncludes(source, 'QuoteProcess.vue', [
      'workflow-page',
      'quote-process-page',
      'workflow-workspace',
      'workflow-side-panel',
      'workflow-main-panel',
      'workflow-batch-panel',
      'workflow-action-stack',
      'workflow-muted-actions',
      'workflow-soft-button',
      'workflow-primary-action',
      '维护已核价订单的报价交期，生成并导出客户报价单。'
    ])
  })

  test('lays out manual quote date as one compact control row', () => {
    const source = readComponent('QuoteProcess.vue')

    expectIncludes(source, 'QuoteProcess.vue', [
      'quote-manual-date-row',
      'quote-manual-date-input',
      'quote-manual-date-button',
      '.quote-manual-date-input.el-date-editor.el-input',
      'max-width: 100%;'
    ])
    expect(source).toContain('手动指定报价交期')
  })

  test('keeps market release compact instead of using wide workflow spacing', () => {
    const source = readComponent('MarketRelease.vue')

    expectIncludes(source, 'MarketRelease.vue', [
      'erp-page release-page workflow-page',
      'erp-surface workflow-workspace release-workspace',
      'erp-surface workflow-side-panel release-panel',
      'erp-table-surface workflow-main-panel release-table-panel',
      'internal-lead-panel',
      'internal-project-panel'
    ])
    expectExcludes(source, 'MarketRelease.vue', [
      'workflow-batch-panel',
      'workflow-action-stack'
    ])
  })

  test('aligns market release workspace proportions with quote process page', () => {
    const source = readComponent('MarketRelease.vue')

    expectIncludes(source, 'MarketRelease.vue', [
      '<section class="erp-surface workflow-workspace release-workspace">',
      '<el-row :gutter="16">',
      '<el-col :span="6">',
      '<el-col :span="18">',
      'class="erp-surface workflow-side-panel release-panel"',
      'class="erp-table-surface workflow-main-panel release-table-panel"',
      'height="640"'
    ])
    expectExcludes(source, 'MarketRelease.vue', [
      '.release-workspace {\n  margin-top:'
    ])
  })

  test('allows released orders in release picker while keeping item selection guarded', () => {
    const source = readComponent('MarketRelease.vue')

    expectIncludes(source, 'MarketRelease.vue', [
      ':allowed-statuses="RELEASE_ALLOWED_STATUSES"',
      "row.status !== '已生成提单'",
      "remainingQtyFor(row) > 0"
    ])
    expect(source).toContain("const RELEASE_ALLOWED_STATUSES = ['已报价', '部分已提单', '已生成提单']")
  })

  test('lays out manual internal target date as one compact control row', () => {
    const source = readComponent('MarketRelease.vue')

    expectIncludes(source, 'MarketRelease.vue', [
      'market-manual-date-row',
      'market-manual-date-input',
      'market-manual-date-button',
      '.market-manual-date-row :deep(.market-manual-date-input.el-date-editor.el-input)'
    ])
    expect(source).toMatch(
      /\.market-manual-date-row :deep\(\.market-manual-date-input\.el-date-editor\.el-input\)\s*\{[\s\S]*?width: 100%;[\s\S]*?max-width: 100%;[\s\S]*?min-width: 0;[\s\S]*?\}/
    )
  })

  test('keeps market release selection table free from right-edge scrollbar drift', () => {
    const source = readComponent('MarketRelease.vue')

    expectIncludes(source, 'MarketRelease.vue', [
      'class="release-selection-table"',
      'scrollbar-always-on',
      '<el-table-column type="selection" width="45" :selectable="canSelectItem" :reserve-selection="true" />',
      '<el-table-column prop="partNo" label="图号" width="130" fixed show-overflow-tooltip />'
    ])
    expectExcludes(source, 'MarketRelease.vue', [
      'native-scrollbar',
      '--el-table-fixed-left-column',
      '.el-table-fixed-column--left',
      '<el-table-column type="selection" width="45" fixed :selectable="canSelectItem" />'
    ])
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

  test('uses a consistent order selection icon across workflow pages', () => {
    const pages = ['QuoteProcess.vue', 'MarketRelease.vue', 'GlobalSetup.vue', 'DeliveryDocumentManage.vue']

    for (const fileName of pages) {
      const source = readComponent(fileName)

      expect(source, `${fileName} should use the shared order selection icon`).toContain(':icon="DocumentChecked"')
      expect(source, `${fileName} should import the shared order selection icon`).toContain('DocumentChecked')
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
      '@click="submitTicket"'
    ])
    expectExcludes(releaseSource, 'MarketRelease.vue', [
      '@click="deleteTicket(scope.row)"',
      '@click="deleteProcessSheet(scope.row)"',
      'deleteTicketRequest',
      'deleteProcessSheetRequest'
    ])
  })

  test('freezes batched release quantity columns for partial delivery', () => {
    const source = readComponent('MarketRelease.vue')

    expectIncludes(source, 'MarketRelease.vue', [
      '已提单数量',
      '剩余可提数量',
      ':max="remainingQtyFor(scope.row)"',
      'const remainingQtyFor = (row) =>'
    ])
  })

  test('submits market release tickets in table item order instead of click order', () => {
    const source = readComponent('MarketRelease.vue')

    expectIncludes(source, 'MarketRelease.vue', [
      'const selectedRowsInItemOrder = () =>',
      'selectedRows.value.slice().sort',
      'Number(left.id || 0) - Number(right.id || 0)',
      'const orderedSelectedRows = selectedRowsInItemOrder()',
      'const releaseGroups = groupSelectedRowsBySource(orderedSelectedRows)',
      'items: rows.map(row => ({'
    ])
    expect(source).not.toContain('items: selectedRows.value.map(row => ({')
  })
})
