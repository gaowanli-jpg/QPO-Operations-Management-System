import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = () => readFileSync(resolve(currentDir, 'components', 'OrderManage.vue'), 'utf8')
const pickerSource = () => readFileSync(resolve(currentDir, 'components', 'OrderPickerDialog.vue'), 'utf8')
const appendDialogSource = () => readFileSync(resolve(currentDir, 'components', 'order', 'OrderItemAppendDialog.vue'), 'utf8').replace(/\r\n/g, '\n')

describe('order item maintenance detail table', () => {
  test('adds editable maintenance actions with dedicated order update permission', () => {
    const component = source()

    expect(component).toContain("const ORDER_ITEM_UPDATE_PERMISSION = 'market:order:update-item'")
    expect(component).toContain('表格数据替换')
    expect(component).toContain('保存修改')
    expect(component).toContain('导入替换图纸')
    expect(component).toContain('新增零件')
    expect(component).toContain('v-permission="ORDER_ITEM_UPDATE_PERMISSION"')
    expect(component).toContain('saveOrderItemChanges')
    expect(component).toContain('OrderItemDataReplacementDialog')
    expect(component).toContain('OrderItemAppendDialog')
    expect(component).toContain('openOrderItemAppendDialog')
    expect(component).toContain('submitOrderItemAppend')
  })

  test('order detail items mirror release table while excluding release-only columns', () => {
    const component = source()
    const itemPane = component.slice(
      component.indexOf('<el-tab-pane label="零件明细" name="items">'),
      component.indexOf('<el-tab-pane label="提单行" name="tickets">')
    )

    expect(itemPane).toContain('label="图号"')
    expect(itemPane).toContain('label="版本"')
    expect(itemPane).toContain('label="询价数量"')
    expect(itemPane).toContain('label="客户询价交期"')
    expect(itemPane).toContain('label="报价承诺交期"')
    expect(itemPane).toContain('label="内部目标交期"')
    expect(itemPane).toContain('label="状态"')
    expect(itemPane).toContain('row.status === \'废弃\' ? \'恢复\' : \'废弃\'')
    expect(itemPane).toContain('changeOrderItemDiscardState(row)')

    expect(itemPane).toContain('updateItemDraft(row, \'partNo\'')
    expect(itemPane).toContain('updateItemDraft(row, \'qty\'')
    expect(itemPane).toContain('updateItemDraft(row, \'leadTime\'')
    expect(itemPane).toContain('updateItemDraft(row, \'quoteLeadTime\'')
    expect(itemPane).toContain('label="材料名称"')
    expect(itemPane).toContain("itemFieldValue(row, 'materialName')")
    expect(itemPane).toContain("itemFieldChanged(row, 'materialName')")
    expect(itemPane).toContain("updateItemDraft(row, 'materialName'")
    expect(component).toContain("'materialName'")
    expect(itemPane.indexOf("itemFieldValue(row, 'description')"))
      .toBeLessThan(itemPane.indexOf("itemFieldValue(row, 'materialName')"))
    expect(itemPane.indexOf("itemFieldValue(row, 'materialName')"))
      .toBeLessThan(itemPane.indexOf("itemFieldValue(row, 'surfaceTreatment')"))
    expect(itemPane).not.toContain('row.engineeringMaterialName || row.materialName')

    expect(itemPane).not.toContain('来源单号')
    expect(itemPane).not.toContain('通过数量')
    expect(itemPane).not.toContain('可选状态')
    expect(itemPane).not.toContain('提单交期')
  })

  test('append dialog reuses rfq import excel and drawing upload steps inside order detail', () => {
    const component = source()

    expect(component).toContain('v-model="orderItemAppendVisible"')
    expect(component).toContain('v-model:excel-file="orderItemAppendExcelFile"')
    expect(component).toContain('v-model:files="orderItemAppendDrawingFiles"')
    expect(component).toContain('v-model:version-files="orderItemAppendVersionDrawingFiles"')
    expect(component).toContain('v-model:folder-files="orderItemAppendDrawingFolderFiles"')
    expect(component).toContain('v-model:folder-name="orderItemAppendDrawingFolderName"')
    expect(component).toContain('appendOrderItems(')
    expect(component).toContain("formData.append('excelFile', orderItemAppendExcelFile.value)")
    expect(component).toContain("formData.append('drawings', file)")
    expect(component).toContain("formData.append('versionDrawings', file)")
    expect(component).toContain("formData.append('drawingFolderFiles', file)")
    expect(component).toContain("formData.append('drawingFolderName'")
  })

  test('append dialog mirrors the clean supplemental drawing upload layout', () => {
    const component = source()
    const dialog = appendDialogSource()

    expect(component).toContain(':current-items="detailItemList"')
    expect(dialog).toContain('currentItems: { type: Array, default: () => [] }')
    expect(dialog).toContain('folderFiles: { type: Array, default: () => [] }')
    expect(dialog).toContain('folderName: { type: String, default: \'\' }')
    expect(dialog).toContain("'update:folderFiles'")
    expect(dialog).toContain("'update:folderName'")
    expect(dialog).toContain('<section class="erp-dialog-section append-current-items">')
    expect(dialog).toContain('当前订单零件')
    expect(dialog).toContain('上传订单图纸文件夹')
    expect(dialog).toContain('class="erp-upload-surface append-upload-surface"')
    expect(dialog).toContain('class="append-upload-dual-grid"')
    expect(dialog).toContain('<el-icon class="el-icon--upload append-upload-icon"><upload-filled /></el-icon>')
    expect(dialog).toContain('class="append-upload-actions import-upload-button-stack"')
    expect(dialog).toContain('<input ref="partFileInput" type="file" accept=".pdf" multiple class="hidden-input"')
    expect(dialog).toContain('<input ref="partFolderInput" type="file" webkitdirectory directory multiple class="hidden-input"')
    expect(dialog).toContain('<input ref="versionFileInput" type="file" accept=".pdf" multiple class="hidden-input"')
    expect(dialog).toContain('<input ref="versionFolderInput" type="file" webkitdirectory directory multiple class="hidden-input"')
    expect(dialog).toContain('.append-dialog :deep(.hidden-input)')
    expect(dialog).not.toContain("h('button'")
    expect(dialog).not.toContain('h(UploadFilled')
  })

  test('supplemental picker scopes include appended part status ranges', () => {
    const picker = pickerSource()

    expect(picker).toContain("'engineer-supplemental-pricing': ['已核价', '已报价', '部分已提单', '已生成提单']")
    expect(picker).toContain("'quote-process-supplemental': ['已报价', '部分已提单', '已生成提单']")
  })
})
