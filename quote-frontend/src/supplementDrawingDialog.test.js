import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

describe('shared supplement drawing dialog', () => {
  const dialog = readSource('components', 'order', 'SupplementDrawingDialog.vue')
  const orderManage = readSource('components', 'OrderManage.vue')
  const salesOrderManage = readSource('components', 'SalesOrderManage.vue')

  test('is reused by RFQ and sales order detail pages', () => {
    expect(orderManage).toContain('SupplementDrawingDialog')
    expect(salesOrderManage).toContain('SupplementDrawingDialog')
    expect(orderManage).toContain(':permission="\'market:order:supplement-drawing\'"')
    expect(salesOrderManage).toContain(':permission="\'market:sales-order:supplement-drawing\'"')
  })

  test('supports multiple PDF and folder selection with the existing upload helpers', () => {
    expect(dialog).toContain('collectPdfFilesFromDataTransfer')
    expect(dialog).toContain('mergePdfFiles')
    expect(dialog).toContain('webkitdirectory')
    expect(dialog).toContain('选择多个 PDF')
    expect(dialog).toContain('选择文件夹')
    expect(dialog).toContain('@drop.prevent="handleDrop"')
  })

  test('shows missing items and the four supplement result buckets', () => {
    expect(dialog).toContain(':data="missingItems"')
    expect(dialog).toContain('result.boundItems')
    expect(dialog).toContain('result.alreadyBoundItems')
    expect(dialog).toContain('result.unmatchedDrawings')
    expect(dialog).toContain('result.skippedFiles')
  })
})
