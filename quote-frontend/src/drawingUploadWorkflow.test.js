import { describe, expect, test } from 'vitest'
import { existsSync, readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'
import { collectPdfFilesFromFileList, extractDrawingFolderName, mergePdfFiles } from './utils/drawingUpload'

const currentDir = dirname(fileURLToPath(import.meta.url))

const normalizeSource = (source) => source.replace(/\r\n/g, '\n')
const readSource = (...segments) => normalizeSource(readFileSync(resolve(currentDir, ...segments), 'utf8'))
const readComponent = (fileName) => readSource('components', fileName)
const importDrawingPanelPath = resolve(currentDir, 'components', 'import', 'ImportDrawingUploadPanel.vue')
const supplementDrawingDialogPath = resolve(currentDir, 'components', 'order', 'SupplementDrawingDialog.vue')

describe('drawing upload workflow parity', () => {
  test('market RFQ and sales order imports render paired part and part-version drawing panels', () => {
    const source = readComponent('MarketDashboard.vue')

    expect(existsSync(importDrawingPanelPath)).toBe(true)
    expect(source).toContain("import ImportDrawingUploadPanel from './import/ImportDrawingUploadPanel.vue'")
    expect(source.match(/<ImportDrawingUploadPanel/g) || []).toHaveLength(6)
    expect(source.match(/class="drawing-upload-dual-grid"/g) || []).toHaveLength(2)
    expect(source.match(/upload-mode="folder"/g) || []).toHaveLength(2)
    expect(source).toContain('v-model="drawingFiles"')
    expect(source).toContain('v-model="versionDrawingFiles"')
    expect(source).toContain('v-model="drawingFolderFiles"')
    expect(source).toContain('v-model:folder-name="drawingFolderName"')
    expect(source).toContain('v-model="salesOrderDrawingFiles"')
    expect(source).toContain('v-model="salesOrderVersionDrawingFiles"')
    expect(source).toContain('v-model="salesOrderDrawingFolderFiles"')
    expect(source).toContain('v-model:folder-name="salesOrderDrawingFolderName"')
    expect(source).toContain('按图号+版本绑定图纸')
  })

  test('shared drawing upload panel exposes drop, file, folder, and remove controls', () => {
    expect(existsSync(importDrawingPanelPath)).toBe(true)
    const source = normalizeSource(readFileSync(importDrawingPanelPath, 'utf8'))

    expect(source).toContain('@drop.prevent="handleDrop"')
    expect(source).toContain('ref="fileInput"')
    expect(source).toContain('ref="folderInput"')
    expect(source).toContain('accept=".pdf"')
    expect(source).toContain('webkitdirectory directory multiple')
    expect(source).toContain('@change="handleFileSelect"')
    expect(source).toContain('@change="handleFolderSelect"')
    expect(source).toContain('@click="triggerFileSelect"')
    expect(source).toContain('@click="triggerFolderSelect"')
    expect(source).toContain('class="import-upload-button-stack"')
    expect(source).toContain('flex-direction: column')
    expect(source).toContain('collectPdfFilesFromDataTransfer')
    expect(source).toContain('mergePdfFiles')
    expect(source).toContain('extractDrawingFolderName')
    expect(source).toContain("uploadMode: { type: String, default: 'matching' }")
    expect(source).toContain("emit('update:folderName'")
    expect(source).toContain('选择订单图纸文件夹')
    expect(source).toContain('emit(\'update:modelValue\'')
  })

  test('shared drawing upload panel keeps paired entrances equal height and action buttons aligned', () => {
    const panel = normalizeSource(readFileSync(importDrawingPanelPath, 'utf8'))
    const marketDashboard = readComponent('MarketDashboard.vue')

    expect(panel).toContain('.import-drawing-upload-panel {\n  display: flex')
    expect(panel).toContain('.upload-alert {\n  display: flex')
    expect(panel).toContain('min-height: 56px')
    expect(panel).toContain('.custom-dropzone {\n  display: flex')
    expect(panel).toContain('min-height: 260px')
    expect(panel).toContain('.import-upload-button-stack {\n  display: flex')
    expect(panel).toContain('margin-top: auto')
    expect(panel).toContain('.import-upload-button-stack :deep(.el-button) {\n  margin-left: 0;')
    expect(panel).toContain('.import-axis-button {\n  width: 100%')
    expect(marketDashboard).toContain('align-items: stretch')
    expect(marketDashboard).toContain('height: 100%')
  })

  test('market import supports dropping folders, choosing folders, and choosing multiple PDFs', () => {
    const source = readComponent('MarketDashboard.vue')

    expect(source).toContain('<ImportDrawingUploadPanel')
    expect(source).toContain('v-model="drawingFiles"')
    expect(source).toContain('v-model="versionDrawingFiles"')
    expect(source).toContain('permission="market:rfq:upload-drawing"')
  })

  test('folder mode submits order drawing folder files without replacing legacy matching entrances', () => {
    const marketDashboard = readComponent('MarketDashboard.vue')
    const releaseAppend = readComponent('ReleaseAppendManage.vue')

    for (const source of [marketDashboard, releaseAppend]) {
      expect(source).toContain("formData.append('drawingFolderFiles', file)")
      expect(source).toContain("formData.append('drawingFolderName'")
      expect(source).toContain("formData.append('drawings', file)")
      expect(source).toContain("formData.append('versionDrawings', file)")
    }
  })

  test('order supplement supports paired part and part-version drawing entrances', () => {
    const orderSource = readComponent('OrderManage.vue')
    const source = normalizeSource(readFileSync(supplementDrawingDialogPath, 'utf8'))

    expect(orderSource).toContain('<SupplementDrawingDialog')
    expect(orderSource).toContain('v-model:files="supplementFiles"')
    expect(orderSource).toContain('v-model:version-files="supplementVersionFiles"')
    expect(orderSource).toContain('v-model:folder-files="supplementDrawingFolderFiles"')
    expect(orderSource).toContain('v-model:folder-name="supplementDrawingFolderName"')
    expect(source).toContain('versionFiles')
    expect(source).toContain('update:versionFiles')
    expect(source).toContain('folderFiles')
    expect(source).toContain('update:folderFiles')
    expect(source).toContain('update:folderName')
    expect(source).toContain('按图号补传')
    expect(source).toContain('按图号+版本补传')
    expect(source).toContain('上传订单图纸文件夹')
    expect(source).toContain('class="supplement-upload-dual-grid"')
    expect(source).toContain('@drop.prevent="handleDrop"')
    expect(source).toContain('ref="fileInput"')
    expect(source).toContain('ref="folderInput"')
    expect(source).toContain('ref="versionFileInput"')
    expect(source).toContain('ref="versionFolderInput"')
    expect(source).toContain('accept=".pdf"')
    expect(source).toContain('webkitdirectory directory multiple')
    expect(source).toContain('@change="handleFileSelect"')
    expect(source).toContain('@change="handleFolderSelect"')
    expect(source).toContain('@change="handleVersionFileSelect"')
    expect(source).toContain('@change="handleVersionFolderSelect"')
    expect(source).toContain('@click="triggerFileSelect"')
    expect(source).toContain('@click="triggerFolderSelect"')
    expect(source).toContain('@click="triggerVersionFileSelect"')
    expect(source).toContain('@click="triggerVersionFolderSelect"')
    expect(source).toContain('选择多个 PDF')
    expect(source).toContain('选择文件夹')
    expect(source).toContain('collectPdfFilesFromDataTransfer')
    expect(source).toContain('mergePdfFiles')
  })

  test('shared PDF helper filters non-PDF files and deduplicates selections', () => {
    const pdf = { name: 'A-100.PDF', size: 12, lastModified: 1 }
    const duplicate = { name: 'A-100.PDF', size: 12, lastModified: 1 }
    const otherPdf = { name: 'B-200.pdf', size: 20, lastModified: 2 }
    const word = { name: 'notes.docx', size: 4, lastModified: 3 }

    expect(collectPdfFilesFromFileList([pdf, word, otherPdf])).toEqual([pdf, otherPdf])
    expect(mergePdfFiles([pdf], [duplicate, otherPdf, word])).toEqual([pdf, otherPdf])
  })

  test('extracts the selected folder name from browser directory selections', () => {
    expect(extractDrawingFolderName([
      { name: 'A.pdf', webkitRelativePath: '订单图纸/A.pdf' }
    ])).toBe('订单图纸')
    expect(extractDrawingFolderName([{ name: 'A.pdf' }])).toBe('')
  })
})
