import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const currentDir = dirname(fileURLToPath(import.meta.url))

const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

const countOccurrences = (source, text) => source.split(text).length - 1

describe('QPO Stage 6 item pricing workbench redesign', () => {
  test('moves ItemPricing onto the shared workbench page shell', () => {
    const source = readSource('components', 'ItemPricing.vue')

    for (const className of [
      'erp-page pricing-workbench-page',
      'erp-page-header pricing-page-header',
      'erp-page-actions pricing-page-actions',
      'erp-surface pricing-context-card',
      'pricing-metric-grid',
      'erp-toolbar pricing-toolbar',
      'erp-table-surface pricing-table-surface',
      'erp-empty-state pricing-empty-state'
    ]) {
      expect(source, `Expected ItemPricing.vue to include ${className}`).toContain(className)
    }

    for (const oldClass of [
      'class="pricing-container"',
      'class="main-card"',
      'class="card-header"',
      'class="toolbar-row"',
      'class="table-shell"',
      'class="empty-state"'
    ]) {
      expect(source, `Expected ItemPricing.vue to stop using ${oldClass}`).not.toContain(oldClass)
    }
  })

  test('separates pricing dialog sections with shared dialog and action patterns', () => {
    const source = readSource('components', 'ItemPricing.vue')

    for (const className of [
      'pricing-workbench-section pricing-mode-section',
      'pricing-workbench-section pricing-material-section',
      'pricing-workbench-section pricing-process-section',
      'pricing-workbench-section pricing-assembly-section',
      'pricing-workbench-summary',
      'erp-dialog-footer pricing-dialog-footer',
      'erp-table-actions pricing-row-actions'
    ]) {
      expect(source, `Expected ItemPricing.vue to include ${className}`).toContain(className)
    }

    expect(countOccurrences(source, 'erp-dialog-section pricing-workbench-section')).toBeGreaterThanOrEqual(4)
    expect(countOccurrences(source, 'erp-dialog-footer')).toBeGreaterThanOrEqual(2)
  })

  test('keeps Stage 6 visual-only without splitting the pricing workflow', () => {
    const source = readSource('components', 'ItemPricing.vue')

    expect(source).not.toContain("from '../components/pricing/")
    expect(source).not.toContain("from './pricing/")
    expect(source).toContain('submitCurrentQuote')
    expect(source).toContain('fetchQuoteData')
    expect(source).toContain('globalConfigStore')
  })

  test('places editable item remark and read-only item info before module A', () => {
    const source = readSource('components', 'ItemPricing.vue')

    expect(source).toContain('pricing-item-note-section')
    expect(source).toContain('pricing-item-info-inline-button')
    expect(source).toContain('itemRemarkDraft')
    expect(source).toContain('savePricingItemRemark')
    expect(source).not.toContain('saveCurrentItemRemark')
    expect(source).not.toContain('saveRemark:')
    expect(source).not.toContain('保存备注')
    expect(source).toContain('itemInfoDialogVisible')
    expect(source).toContain('currentItemInfoRows')
    expect(source).toContain('零件备注')
    expect(source).toContain('查看零件信息')
    expect(source).toContain('材料名称')
    expect(source).toContain('物料描述')
    expect(source).toContain('表面处理')

    const modeIndex = source.indexOf('pricing-mode-section')
    const itemNoteIndex = source.indexOf('pricing-item-note-section')
    const moduleAIndex = source.indexOf('pricing-material-section')

    expect(modeIndex).toBeGreaterThanOrEqual(0)
    expect(itemNoteIndex).toBeGreaterThan(modeIndex)
    expect(moduleAIndex).toBeGreaterThan(itemNoteIndex)
  })

  test('saves item remark through the pricing submit action', () => {
    const source = readSource('components', 'ItemPricing.vue')
    const submitStart = source.indexOf('const submitCurrentQuote = async () =>')
    const submitEnd = source.indexOf('</script>', submitStart)
    expect(submitStart).toBeGreaterThanOrEqual(0)
    expect(submitEnd).toBeGreaterThan(submitStart)
    const submitBlock = source.slice(submitStart, submitEnd)

    expect(submitBlock).toContain('await savePricingItemRemark(currentRow.value.id, itemRemarkDraft.value)')
    expect(submitBlock).toContain('updatePricingItemRemarkInMemory(currentRow.value.id, savedRemark || null)')
    expect(submitBlock.indexOf('await savePricingItemRemark')).toBeLessThan(submitBlock.indexOf('await submitQuote(payload)'))
  })

  test('returns to the current order global setup deterministically', () => {
    const source = readSource('components', 'ItemPricing.vue')

    expect(source).toContain('@click="returnToGlobalSetup"')
    expect(source).toContain("path: '/engineering'")
    expect(source).toContain('query: { rfqId: rfqId.value }')
    expect(source).not.toContain('$router.back()')
  })

  test('allows moving a process row upward while keeping the first row locked', () => {
    const source = readSource('components', 'ItemPricing.vue')

    expect(source).toContain('与上一道工艺交换顺序')
    expect(source).toContain('第一道工艺无法上移')
    expect(source).toContain(':disabled="index === 0"')
    expect(source).toContain('@click="moveProcessUp(index)"')
    expect(source).toContain('const moveProcessUp = (index) =>')
    expect(source).toContain('[processRoute.value[index - 1], processRoute.value[index]] = [processRoute.value[index], processRoute.value[index - 1]]')
    expect(source).toContain('assignProcessSortNos()')
  })

  test('shows drawing folders inline in the pricing pane before selecting a PDF', () => {
    const source = readSource('components', 'ItemPricing.vue')

    expect(source).toContain("import DrawingFolderFileBrowser from './common/DrawingFolderFileBrowser.vue'")
    expect(source).toContain('<DrawingFolderFileBrowser')
    expect(source).toContain('class="drawing-folder-inline-shell"')
    expect(source).toContain(':compact="true"')
    expect(source).toContain('preview-behavior="emit"')
    expect(source).toContain('@preview="previewFolderDrawingInPane"')
    expect(source).toContain('selectedFolderDrawingSrc')
    expect(source).toContain('返回文件列表')
    expect(source).toContain('backToFolderFileList')
  })
})
