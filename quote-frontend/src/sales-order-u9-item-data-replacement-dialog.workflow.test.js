import { describe, expect, test } from 'vitest'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

describe('sales order U9 item data replacement dialog', () => {
  test('provides a preview then confirm Excel replacement workflow for U9 item fields', () => {
    const componentPath = resolve(currentDir, 'components', 'u9-sync', 'SalesOrderU9ItemDataReplacementDialog.vue')
    expect(existsSync(componentPath)).toBe(true)

    const component = readSource('components', 'u9-sync', 'SalesOrderU9ItemDataReplacementDialog.vue')

    expect(component).toContain('previewSalesOrderU9ItemDataReplacement')
    expect(component).toContain('confirmSalesOrderU9ItemDataReplacement')
    expect(component).toContain('accept=".xlsx,.xls"')
    expect(component).toContain("formData.append('excelFile', selectedFile.value)")
    expect(component).toContain("formData.append('previewFingerprint', previewFingerprint.value)")
    expect(component).toContain('recognizedFields')
    expect(component).toContain('summary.errorCount')
    expect(component).toContain('canConfirmPreview')
    expect(component).toContain('确认替换')
    ;['CHANGED', 'UNCHANGED', 'UNMATCHED', 'SKIPPED', 'ERROR'].forEach(status => {
      expect(component).toContain(status)
    })
  })

  test('stays scoped to sales order item data and excludes drawing replacement concepts', () => {
    const component = readSource('components', 'u9-sync', 'SalesOrderU9ItemDataReplacementDialog.vue')

    expect(component).toContain('salesOrderId')
    expect(component).not.toContain('导入替换图纸')
    expect(component).not.toContain('replaceReleaseDrawings')
    expect(component).not.toContain('RFQ')
    expect(component).not.toContain('acceptedQty')
    expect(component).not.toContain('通过数量')
    expect(component).not.toContain('QUANTITY_ONLY')
  })
})
