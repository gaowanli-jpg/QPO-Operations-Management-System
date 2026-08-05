import { describe, expect, test } from 'vitest'
import { readdirSync, readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const currentDir = dirname(fileURLToPath(import.meta.url))

const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')
const readComponent = (fileName) => readSource('components', fileName)

const componentFiles = () => readdirSync(resolve(currentDir, 'components')).filter((fileName) => fileName.endsWith('.vue'))

const tableColumnTags = (source) => source.match(/<el-table-column\b[\s\S]*?(?:\/>|>[\s\S]*?<\/el-table-column>)/g) ?? []
const uploadAlertTags = (source) => source.match(/<el-alert\b[\s\S]*?class="upload-alert"[\s\S]*?\/>/g) ?? []

describe('QPO final UI punch-list polish', () => {
  test('keeps dense table and workflow action buttons readable', () => {
    const css = readSource('style.css')

    expect(css).toContain('.erp-table-actions .el-button--small')
    expect(css).toContain('.workflow-soft-button.el-button--small')
    expect(css).toContain('min-height: 30px')
  })

  test('uses quiet shared styling for the market folder-select fallback action', () => {
    const marketDashboard = readSource('components', 'MarketDashboard.vue')
    const drawingPanel = readSource('components', 'import', 'ImportDrawingUploadPanel.vue')

    expect(marketDashboard).toContain('<ImportDrawingUploadPanel')
    expect(drawingPanel).toContain('workflow-soft-button')
    expect(drawingPanel).toContain('folder-select-button')
    expect(drawingPanel).toContain('@click="triggerFileSelect"')
    expect(drawingPanel).toContain('@click="triggerFolderSelect"')
    expect(drawingPanel).not.toContain('<el-button size="small" @click="triggerFolderSelect"')
    expect(drawingPanel).toContain('.folder-select-button')
  })

  test('keeps market import workflow visually unified with centered action buttons', () => {
    const marketDashboard = readSource('components', 'MarketDashboard.vue')
    const drawingPanel = readSource('components', 'import', 'ImportDrawingUploadPanel.vue')

    expect(marketDashboard).toContain('class="import-step import-step-customer"')
    expect(marketDashboard).toContain('class="import-step import-step-excel"')
    expect(marketDashboard).toContain('class="import-step import-step-drawings"')
    expect(marketDashboard).toContain('class="import-action-row"')
    expect(marketDashboard).toContain('class="workflow-primary-action import-axis-button"')
    expect(drawingPanel).toContain('class="workflow-soft-button folder-select-button import-axis-button"')
    expect(marketDashboard).toContain('class="submit-btn import-axis-button"')
    expect(marketDashboard + drawingPanel).toContain('.import-axis-button')
    expect(marketDashboard + drawingPanel).toContain('justify-content: center')
  })

  test('keeps RFQ import free of order type and moves order type selection to release generation', () => {
    const marketDashboard = readSource('components', 'MarketDashboard.vue')
    const marketRelease = readSource('components', 'MarketRelease.vue')

    expect(marketDashboard).not.toContain('import-step-order-type')
    expect(marketDashboard).not.toContain("rfqForm = reactive({ customerName: '', orderType: '' })")
    expect(marketDashboard).not.toContain('market:rfq:order-type-select')
    expect(marketDashboard).not.toContain("formData.append('orderType'")
    expect(marketDashboard).toContain("const rfqForm = reactive({")
    expect(marketDashboard).toContain("customerId: null")
    expect(marketDashboard).toContain("customerCode: ''")
    expect(marketDashboard).toContain("customerShortName: ''")
    expect(marketDashboard).toContain("customerName: ''")

    expect(marketRelease).toContain('v-model="releaseForm.orderType"')
    expect(marketRelease).toContain('release-order-type-select')
    expect(marketRelease).toContain('预测订单')
    expect(marketRelease).toContain('备货订单')
    expect(marketRelease).toContain('正式订单')
    expect(marketRelease).toContain("if (!releaseForm.orderType) return ElMessage.warning('请选择订单类型后再生成正式提单。')")
    expect(marketRelease).toContain('orderType: releaseForm.orderType')
  })

  test('explains customer inquiry spreadsheet headers and lead time formats before upload', () => {
    const marketDashboard = readSource('components', 'MarketDashboard.vue')

    expect(marketDashboard).toContain('客户询价表需包含表头')
    expect(marketDashboard).toContain('图号、物料描述、材料名称、表面处理、版本、数量、单位、交货周期、备注')
    expect(marketDashboard).toContain('交货周期支持具体日期')
    expect(marketDashboard).toContain('2026-3-8')
    expect(marketDashboard).toContain('2026.3.8')
    expect(marketDashboard).toContain('天数周期')
    expect(marketDashboard).toContain('Excel 日期序列号')

    const alerts = uploadAlertTags(marketDashboard)
    expect(alerts.length).toBeGreaterThanOrEqual(2)
    for (const alert of alerts) {
      expect(alert).toContain('type="info"')
      expect(alert).toContain(':closable="false"')
    }
  })

  test('keeps long text surfaces truncated with full hover text available', () => {
    const highRiskTextProps = new Set([
      'customerName',
      'description',
      'displayName',
      'engineeringMaterialName',
      'feeName',
      'internalProjectNo',
      'label',
      'materialName',
      'materialType',
      'partNo',
      'processName',
      'remark',
      'remarks',
      'rfqNo',
      'surfaceTreatment',
      'username'
    ])

    for (const fileName of componentFiles()) {
      const source = readComponent(fileName)
      for (const tag of tableColumnTags(source)) {
        const prop = tag.match(/prop="([^"]+)"/)?.[1]
        if (!prop || !highRiskTextProps.has(prop)) continue

        expect(tag, `${fileName} ${prop} column should expose full text on hover`).toContain('show-overflow-tooltip')
      }
    }

    const engineeringPicker = readComponent('EngineeringOptionPickerDialog.vue')
    const appShell = readSource('App.vue')
    const userProfilePopover = readComponent('UserProfilePopover.vue')
    const globalSetup = readComponent('GlobalSetup.vue')
    const itemPricing = readComponent('ItemPricing.vue')
    const marketRelease = readComponent('MarketRelease.vue')
    const quoteProcess = readComponent('QuoteProcess.vue')

    expect(engineeringPicker).toContain('<span class="selected-hint" :title="selectedLabel">')
    expect(appShell).toContain('<div class="shell-title" :title="shellMeta.title">')
    expect(appShell).toContain('<div class="shell-subtitle" :title="shellMeta.description">')
    expect(userProfilePopover).toContain('<button type="button" class="profile-trigger" :title="displayName"')
    expect(userProfilePopover).toContain('<div class="profile-name" :title="displayName">')
    expect(userProfilePopover).toContain('<span class="profile-value" :title="roleNames">')
    expect(globalSetup).toContain('<strong class="selected-order-value" :title="selectedOrderLabel">')
    expect(itemPricing).toContain('<div class="assembly-child-title" :title="formatAssemblyChildTitle(child)">')
    expect(itemPricing).toContain('<div class="assembly-child-desc" :title="child.description || \'-\'">')
    expect(itemPricing).toContain('<th scope="row" title="毛坯体积">毛坯体积</th>')
    expect(itemPricing).toContain('<td :title="`${calculatedVolume.toFixed(6)} m³`">')
    expect(itemPricing).toContain('<th scope="row" title="毛坯重量">毛坯重量</th>')
    expect(itemPricing).toContain('<td :title="`${calculatedWeight.toFixed(3)} kg`">')
    expect(itemPricing).toContain('<th scope="row" title="材料单价">材料单价</th>')
    expect(itemPricing).toContain('<td :title="`${currentUnitPrice.toFixed(2)} 元/kg`">')
    expect(itemPricing).toContain('<th scope="row" title="预测本件材料费">预测本件材料费</th>')
    expect(itemPricing).toContain('<td :title="`¥ ${calculatedMaterialCost.toFixed(2)}`">')
    expect(itemPricing).toContain('.material-summary-table th,\n.material-summary-table td {\n  min-width: 0;\n  max-width: 0;\n  overflow: hidden;')
    expect(marketRelease).toContain('class="selected-orders-mini-table"')
    expect(quoteProcess).toContain('<div class="selected-order-text" :title="selectedOrderLabel">')
  })
})
