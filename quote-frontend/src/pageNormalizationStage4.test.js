import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const currentDir = dirname(fileURLToPath(import.meta.url))

const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

const countOccurrences = (source, text) => source.split(text).length - 1

describe('QPO Stage 4 dialog, form, and table action normalization', () => {
  test('defines shared normalization classes in the QPO style foundation', () => {
    const source = readSource('style.css')

    for (const className of [
      '.erp-dialog-form',
      '.erp-dialog-section',
      '.erp-dialog-footer',
      '.erp-table-actions',
      '.erp-danger-action',
      '.erp-upload-surface'
    ]) {
      expect(source, `Expected style.css to define ${className}`).toContain(className)
    }
  })

  test('normalizes order management dialog, upload, and action affordances', () => {
    const source = readSource('components', 'OrderManage.vue')

    expect(source).toContain('erp-dialog-section')
    expect(source).toContain('erp-dialog-footer')
    expect(source).toContain('erp-table-actions')
    expect(source).toContain('erp-upload-surface')
  })

  test('keeps sales order shell on shared page affordances without legacy delete actions', () => {
    const source = readSource('components', 'SalesOrderManage.vue')

    expect(source).toContain('erp-page sales-order-manage-page')
    expect(source).toContain('erp-toolbar')
    expect(source).toContain('erp-table-surface')
    expect(source).not.toContain('market:order:delete')
    expect(source).not.toContain('历史订单')
  })
  test('normalizes material/process dialogs, forms, footers, and destructive table actions', () => {
    const source = readSource('components', 'MaterialProcessManage.vue')

    expect(countOccurrences(source, 'class="erp-dialog-form"')).toBeGreaterThanOrEqual(4)
    expect(countOccurrences(source, 'class="erp-dialog-footer"')).toBeGreaterThanOrEqual(4)
    expect(countOccurrences(source, 'class="erp-table-actions"')).toBeGreaterThanOrEqual(4)
    expect(countOccurrences(source, 'erp-danger-action')).toBeGreaterThanOrEqual(4)
  })
})
