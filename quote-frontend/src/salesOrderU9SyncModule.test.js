import { describe, expect, test } from 'vitest'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

describe('sales order U9 sync module', () => {
  test('registers an independent protected U9 sync route', () => {
    const router = readSource('router', 'index.js')

    expect(router).toContain("const SalesOrderU9Sync = () => import('../components/SalesOrderU9Sync.vue')")
    expect(router).toContain("{ path: '/u9-sync', component: SalesOrderU9Sync, meta: { permission: 'market:u9-sync:view' } }")
  })

  test('places U9 sync between release and delivery documents in the market sidebar', () => {
    const app = readSource('App.vue')

    expect(app).toContain("paths: ['/market', '/quote-process', '/release', '/u9-sync', '/delivery-documents'")

    const marketGroupIndex = app.indexOf("{ key: 'market'")
    const releaseIndex = app.indexOf("'/release'", marketGroupIndex)
    const u9SyncIndex = app.indexOf("'/u9-sync'", marketGroupIndex)
    const deliveryDocumentIndex = app.indexOf("'/delivery-documents'", marketGroupIndex)

    expect(releaseIndex).toBeGreaterThan(-1)
    expect(u9SyncIndex).toBeGreaterThan(releaseIndex)
    expect(deliveryDocumentIndex).toBeGreaterThan(u9SyncIndex)
  })

  test('renders a release-style single-order U9 sync workspace', () => {
    const componentPath = resolve(currentDir, 'components', 'SalesOrderU9Sync.vue')
    expect(existsSync(componentPath)).toBe(true)

    const component = readSource('components', 'SalesOrderU9Sync.vue')
    expect(component).toContain('class="erp-page sales-order-u9-sync-page"')
    expect(component).toContain('class="erp-page-header sales-order-u9-sync-header"')
    expect(component).toContain("import OrderPickerDialog from './OrderPickerDialog.vue'")
    expect(component).toContain('<OrderPickerDialog')
    expect(component).toContain('customer-scope="u9-sync"')
    expect(component).toContain('order-scope="u9-sync"')
    expect(component).toContain(':allowed-statuses="U9_SYNC_ALLOWED_STATUSES"')
    expect(component).toContain('@select="handleOrderSelected"')
    expect(component).toContain("const U9_SYNC_ALLOWED_STATUSES = ['已生成提单']")
    expect(component).not.toContain("const U9_SYNC_ALLOWED_STATUSES = ['部分已提单', '已生成提单']")
    expect(component).toContain('class="erp-surface workflow-workspace sales-order-u9-sync-workspace"')
    expect(component).toContain('class="erp-surface sales-order-u9-sync-side-panel"')
    expect(component).toContain('class="erp-table-surface sales-order-u9-sync-main-panel"')
    expect(component).toContain('class="selected-order-text"')
    expect(component).toContain('class="sync-condition-panel"')
    expect(component).toContain('class="sync-result-panel"')
    expect(component).toContain('orderPickerVisible')
    expect(component).toContain('openOrderPicker')
    expect(component).toContain('handleOrderSelected')
    expect(component).toContain('<dt>订单来源</dt>')
    expect(component).toContain('orderSourceLabel')
    expect(component).toContain("if (!source.sourceType && !source.sourceLabel) return '-'")
    expect(component).not.toContain('class="erp-toolbar"')
    expect(component).not.toContain('order-picker-toolbar')
    expect(component).not.toContain('sales-order-u9-sync-summary-grid')
    expect(component).not.toContain('<el-option label="已报价"')
    expect(component).toContain('RFQ 议价提单派生')
    expect(component).not.toContain('sales-order-u9-sync-placeholder')
    expect(component).not.toContain('background: transparent;')
    expect(component).not.toContain('box-shadow: none;')
  })

  test('uses U9 sync API helpers through the shared backend-paged order picker', () => {
    const component = readSource('components', 'SalesOrderU9Sync.vue')
    const picker = readSource('components', 'OrderPickerDialog.vue')

    expect(component).toContain('previewSalesOrderU9Sync')
    expect(component).toContain('syncSalesOrderU9')
    expect(component).not.toContain('v-model:current-page="orderPagination.page"')
    expect(component).not.toContain('@row-dblclick="confirmPickedOrder"')
    expect(picker).toContain("'u9-sync': 'market:u9-sync:customer-select'")
    expect(picker).toContain("'u9-sync': ['已生成提单']")
    expect(picker).not.toContain("'u9-sync': ['部分已提单', '已生成提单']")
    expect(picker).toContain("'u9-sync': '销售订单'")
    expect(picker).toContain('orderNoText')
    expect(picker).toContain('salesOrderNo')
    expect(picker).toContain('salesOrderId')
    expect(picker).toContain('v-if="showOrderSource"')
    expect(picker).toContain('prop="sourceLabel" label="订单来源"')
    expect(picker).toContain("props.orderScope === 'u9-sync'")
  })

  test('loads the sales order supplied by the release-result route query', () => {
    const component = readSource('components', 'SalesOrderU9Sync.vue')

    expect(component).toContain("import { useRoute } from 'vue-router'")
    expect(component).toContain('const route = useRoute()')
    expect(component).toContain('route.query.salesOrderId')
    expect(component).toContain('normalizeRouteSalesOrderId')
    expect(component).toContain("await handleOrderSelected({ salesOrderId })")
    expect(component).toContain('{ immediate: true }')
  })

  test('gates submit action and disables sync when the selected order is blocked', () => {
    const component = readSource('components', 'SalesOrderU9Sync.vue')

    expect(component).toContain("const U9_SYNC_SUBMIT_PERMISSION = 'market:u9-sync:submit'")
    expect(component).toContain('v-permission="U9_SYNC_SUBMIT_PERMISSION"')
    expect(component).toContain(':disabled="!canSyncSelected"')
    expect(component).toContain('syncDisabledReason')
    expect(component).toContain('blockedReason')
    expect(component).toContain('missingMaterialItemCount')
  })

  test('shows sales order U9 fields, material readiness, return result and failure details', () => {
    const component = readSource('components', 'SalesOrderU9Sync.vue')

    expect(component).toContain('prop="salesOrderNo"')
    expect(component).toContain('<dt>客户</dt>')
    expect(component).toContain('customerDisplay')
    expect(component).toContain('客户订单号')
    expect(component).toContain('内部项目号')
    expect(component).toContain('U9状态')
    expect(component).toContain('prop="u9OrderCode"')
    expect(component).toContain('prop="materialNo" label="物料号"')
    expect(component).toContain('prop="materialNoStatus"')
    expect(component).toContain('prop="taxIncludedUnitPrice"')
    expect(component).toContain('含税单价缺失')
    expect(component).toContain('priceMissingTagType')
    expect(component).toContain('prop="errorMessage"')
    expect(component).not.toContain('prop="salesMaterialNo"')
  })

  test('keeps order-level sales order fields in order info instead of repeated item columns', () => {
    const component = readSource('components', 'SalesOrderU9Sync.vue')
    const orderInfoIndex = component.indexOf('<div class="selected-order-meta">')
    const syncResultIndex = component.indexOf('<div class="sync-result-panel">')
    const orderInfo = component.slice(orderInfoIndex, syncResultIndex)

    const expectedOrderFields = [
      '<dt>客户</dt>',
      '<dt>单据类型编码</dt>',
      '<dt>客户订单号</dt>',
      '<dt>客户项目号</dt>',
      '<dt>订单类型</dt>',
      '<dt>内部项目号</dt>'
    ]

    let previousIndex = -1
    expectedOrderFields.forEach(field => {
      const currentIndex = orderInfo.indexOf(field)
      expect(currentIndex).toBeGreaterThan(previousIndex)
      previousIndex = currentIndex
    })

    expect(orderInfo).toContain('customerDisplay')
    expect(orderInfo).not.toContain('<dt>下单日期</dt>')
    expect(orderInfo).not.toContain('<dt>客户下单抬头</dt>')
    expect(orderInfo).not.toContain('<dt>OrderBy</dt>')
    expect(orderInfo).not.toContain('<dt>U9编码</dt>')
    expect(component).toContain('<div class="sync-result-code-row">')
    expect(component).toContain('当前U9编码')
  })

  test('orders preview item columns as fixed material fields followed by remaining line-level import-template fields', () => {
    const component = readSource('components', 'SalesOrderU9Sync.vue')
    const expectedColumns = [
      'label="图号"',
      'label="行号"',
      'label="版本"',
      'label="物料号"',
      'label="物料号状态"',
      'label="数量"',
      'label="就绪"',
      'label="阻断原因"',
      'prop="taxIncludedUnitPrice"',
      'label="含税金额"',
      'label="下单日期"',
      'label="客户下单抬头"',
      'label="客户交期"',
      'label="内部交期(给生产的交期)"',
      'label="已出数量"',
      'label="未出数量"',
      'label="描述"',
      'label="材料"',
      'label="表面处理"',
      'label="未税单价(CNY)"',
      'label="（未税）金额"',
      'label="备注"'
    ]

    let previousIndex = -1
    expectedColumns.forEach(column => {
      const currentIndex = component.indexOf(column)
      expect(currentIndex).toBeGreaterThan(previousIndex)
      previousIndex = currentIndex
    })

    expect(component).not.toContain('label="ItemCode"')
    expect(component).not.toContain('prop="salesMaterialNo"')
    expect(component).not.toContain('label="物料号来源"')
    expect(component).not.toContain('label="来源提单行"')
    expect(component).not.toContain('label="实际出货日期"')
    expect(component).not.toContain('label="客户订单号"')
    expect(component).not.toContain('label="客户项目号"')
    expect(component).not.toContain('label="订单类型"')
    expect(component).not.toContain('label="内部项目号"')
    expect(component).not.toContain('ItemCode 来自销售订单零件物料号')
  })
})
