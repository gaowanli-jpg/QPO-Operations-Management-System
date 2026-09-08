import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8').replace(/\r\n/g, '\n')

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
    expect(dialog).toContain('extractDrawingFolderName')
    expect(dialog).toContain('webkitdirectory')
    expect(dialog).toContain('选择多个 PDF')
    expect(dialog).toContain('选择文件夹')
    expect(dialog).toContain('上传订单图纸文件夹')
    expect(dialog).toContain('@drop.prevent="handleDrop"')
  })

  test('exposes order-folder and part-version files and disables submit only when all entrances are empty', () => {
    expect(dialog).toContain('folderFiles: { type: Array, default: () => [] }')
    expect(dialog).toContain('folderName: { type: String, default: \'\' }')
    expect(dialog).toContain("'update:folderFiles'")
    expect(dialog).toContain("'update:folderName'")
    expect(dialog).toContain('versionFiles: { type: Array, default: () => [] }')
    expect(dialog).toContain("'update:versionFiles'")
    expect(dialog).toContain('props.folderFiles')
    expect(dialog).toContain('props.versionFiles')
    expect(dialog).toContain("emit('update:folderFiles'")
    expect(dialog).toContain("emit('update:versionFiles'")
    expect(dialog).toContain('folderFiles.length === 0 && files.length === 0 && versionFiles.length === 0')
  })

  test('parents submit order-folder and part-version supplement drawings without changing old drawings field', () => {
    for (const source of [orderManage, salesOrderManage]) {
      expect(source).toContain('v-model:files="supplementFiles"')
      expect(source).toContain('v-model:version-files="supplementVersionFiles"')
      expect(source).toContain('v-model:folder-files="supplementDrawingFolderFiles"')
      expect(source).toContain('v-model:folder-name="supplementDrawingFolderName"')
      expect(source).toContain('const supplementVersionFiles = ref([])')
      expect(source).toContain('const supplementDrawingFolderFiles = ref([])')
      expect(source).toContain('supplementVersionFiles.value.forEach')
      expect(source).toContain('supplementDrawingFolderFiles.value.forEach')
      expect(source).toContain("formData.append('drawings', file)")
      expect(source).toContain("formData.append('versionDrawings', file)")
      expect(source).toContain("formData.append('drawingFolderFiles', file)")
      expect(source).toContain("formData.append('drawingFolderName'")
    }
  })

  test('shows missing items and the four supplement result buckets', () => {
    expect(dialog).toContain(':data="missingItems"')
    expect(dialog).toContain('result.boundItems')
    expect(dialog).toContain('result.alreadyBoundItems')
    expect(dialog).toContain('result.unmatchedDrawings')
    expect(dialog).toContain('result.skippedFiles')
  })

  test('keeps paired supplement drawing entrances equal height with aligned buttons', () => {
    expect(dialog).toContain('.supplement-upload-dual-grid {\n  display: grid')
    expect(dialog).toContain('align-items: stretch')
    expect(dialog).toContain('.supplement-upload-entry {\n  display: flex')
    expect(dialog).toContain('.supplement-upload-title {\n  min-height: 24px')
    expect(dialog).toContain('.supplement-dropzone {\n  display: flex')
    expect(dialog).toContain('min-height: 280px')
    expect(dialog).toContain('.supplement-dropzone :deep(.el-upload__tip) {\n  min-height: 48px')
    expect(dialog).toContain('.supplement-upload-actions {\n  display: flex')
    expect(dialog).toContain('margin-top: auto')
    expect(dialog).toContain('.folder-select-button {\n  width: 100%')
  })
})
