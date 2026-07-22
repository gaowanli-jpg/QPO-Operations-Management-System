import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'
import { collectPdfFilesFromFileList, mergePdfFiles } from './utils/drawingUpload'

const currentDir = dirname(fileURLToPath(import.meta.url))

const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')
const readComponent = (fileName) => readSource('components', fileName)

describe('drawing upload workflow parity', () => {
  test('market import supports dropping folders, choosing folders, and choosing multiple PDFs', () => {
    const source = readComponent('MarketDashboard.vue')

    expect(source).toContain('@drop.prevent="handleDrawingDrop"')
    expect(source).toContain('ref="drawingFileInput"')
    expect(source).toContain('ref="drawingFolderInput"')
    expect(source).toContain('accept=".pdf"')
    expect(source).toContain('webkitdirectory directory multiple')
    expect(source).toContain('@change="handleDrawingFileSelect"')
    expect(source).toContain('@change="handleDrawingFolderSelect"')
    expect(source).toContain('@click="triggerDrawingFileSelect"')
    expect(source).toContain('@click="triggerDrawingFolderSelect"')
    expect(source).toContain('选择多个 PDF')
    expect(source).toContain('选择文件夹')
    expect(source).toContain('collectPdfFilesFromDataTransfer')
    expect(source).toContain('mergePdfFiles')
  })

  test('order supplement supports dropping folders, choosing folders, and choosing multiple PDFs', () => {
    const source = readComponent('OrderManage.vue')

    expect(source).toContain('@drop.prevent="handleSupplementDrop"')
    expect(source).toContain('ref="supplementFileInput"')
    expect(source).toContain('ref="supplementFolderInput"')
    expect(source).toContain('accept=".pdf"')
    expect(source).toContain('webkitdirectory directory multiple')
    expect(source).toContain('@change="handleSupplementFileSelect"')
    expect(source).toContain('@change="handleSupplementFolderSelect"')
    expect(source).toContain('@click="triggerSupplementFileSelect"')
    expect(source).toContain('@click="triggerSupplementFolderSelect"')
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
})
