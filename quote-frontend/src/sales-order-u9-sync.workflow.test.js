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

    expect(app).toContain("paths: ['/market', '/quote-process', '/release', '/release-append', '/u9-sync', '/delivery-documents'")

    const marketGroupIndex = app.indexOf("{ key: 'market'")
    const releaseIndex = app.indexOf("'/release'", marketGroupIndex)
    const releaseAppendIndex = app.indexOf("'/release-append'", marketGroupIndex)
    const u9SyncIndex = app.indexOf("'/u9-sync'", marketGroupIndex)
    const deliveryDocumentIndex = app.indexOf("'/delivery-documents'", marketGroupIndex)

    expect(releaseIndex).toBeGreaterThan(-1)
    expect(releaseAppendIndex).toBeGreaterThan(releaseIndex)
    expect(u9SyncIndex).toBeGreaterThan(releaseAppendIndex)
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
    expect(component).toContain("const U9_SYNC_ALLOWED_STATUSES = ['部分已提单', '已生成提单']")
    expect(component).not.toContain("const U9_SYNC_ALLOWED_STATUSES = ['已生成提单']")
    expect(component).toContain('class="erp-surface workflow-workspace sales-order-u9-sync-workspace"')
    expect(component).toContain('class="erp-surface sales-order-u9-sync-side-panel"')
    expect(component).toContain('class="erp-table-surface sales-order-u9-sync-main-panel"')
    expect(component).toContain('class="selected-order-text"')
    expect(component).toContain('class="u9-order-date-panel"')
    expect(component).toContain('class="sync-condition-panel"')
    expect(component).toContain('class="sync-result-panel"')
    const assignmentIndex = component.indexOf('class="u9-assignment-panel"')
    const orderDateIndex = component.indexOf('class="u9-order-date-panel"')
    const conditionIndex = component.indexOf('class="sync-condition-panel"')
    expect(orderDateIndex).toBeGreaterThan(assignmentIndex)
    expect(conditionIndex).toBeGreaterThan(orderDateIndex)
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

  test('keeps U9 preview warnings compact so they do not crowd the item table', () => {
    const component = readSource('components', 'SalesOrderU9Sync.vue')

    expect(component).toContain('class="preview-message-summary"')
    expect(component).toContain('previewMessageSummary')
    expect(component).toContain('previewMessageDetailVisible')
    expect(component).toContain('class="preview-message-detail"')
    expect(component).toContain('class="line-issue-tag"')
    expect(component).toContain('lineIssueSummary(row)')
    expect(component).not.toContain('class="message-stack"')
    expect(component).not.toContain('class="sales-order-u9-sync-alert inline"')
  })

  test('uses U9 sync API helpers through the shared backend-paged order picker', () => {
    const component = readSource('components', 'SalesOrderU9Sync.vue')
    const picker = readSource('components', 'OrderPickerDialog.vue')

    expect(component).toContain('previewSalesOrderU9Sync')
    expect(component).toContain('syncSalesOrderU9')
    expect(component).not.toContain('v-model:current-page="orderPagination.page"')
    expect(component).not.toContain('@row-dblclick="confirmPickedOrder"')
    expect(picker).toContain("'u9-sync': 'market:u9-sync:customer-select'")
    expect(picker).toContain("'u9-sync': ['部分已提单', '已生成提单']")
    expect(picker).not.toContain("'u9-sync': ['已生成提单']")
    expect(picker).toContain("'u9-sync': '销售订单'")
    expect(picker).toContain('orderNoText')
    expect(picker).toContain('salesOrderNo')
    expect(picker).toContain('salesOrderId')
    expect(picker).toContain('v-if="showOrderSource"')
    expect(picker).toContain('prop="sourceLabel" label="订单来源"')
    expect(picker).toContain('SALES_ORDER_SCOPES.includes(props.orderScope)')
  })

  test('uses backend pagination for U9 preview item records', () => {
    const component = readSource('components', 'SalesOrderU9Sync.vue')
    const api = readSource('services', 'marketApi.js')

    expect(component).toContain("import { CLIENT_PAGE_SIZES, DEFAULT_CLIENT_PAGE_SIZE } from '../utils/clientPagination'")
    expect(component).toContain('const itemPagination = reactive({ page: 1, pageSize: DEFAULT_CLIENT_PAGE_SIZE })')
    expect(component).toContain('const previewLineTotal = computed(() => Number(previewData.value?.total ?? previewData.value?.itemCount ?? 0))')
    expect(component).toContain('const previewLines = computed(() => previewData.value?.records || previewData.value?.lines || [])')
    expect(component).toContain('class="erp-pagination sales-order-u9-sync-pagination"')
    expect(component).toContain(':current-page="itemPagination.page"')
    expect(component).toContain(':page-size="itemPagination.pageSize"')
    expect(component).toContain(':page-sizes="CLIENT_PAGE_SIZES"')
    expect(component).toContain('@current-change="handleItemPageChange"')
    expect(component).toContain('@size-change="handleItemPageSizeChange"')
    expect(component).toContain('previewSalesOrderU9Sync(selectedOrderId.value, {')
    expect(component).toContain('page: itemPagination.page')
    expect(component).toContain('pageSize: itemPagination.pageSize')
    expect(component).toContain('请先保存零件行修改后再翻页')
    expect(api).toContain('previewSalesOrderU9Sync = (salesOrderId, params = {})')
    expect(api).toContain('request(`/api/market/u9-sync/orders/${salesOrderId}/preview`, { params })')
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
    expect(component).toContain('prop="untaxedUnitPrice" label="未税单价"')
    expect(component).toContain('prop="taxIncludedUnitPrice" label="含税单价"')
    expect(component).toContain('按未税价同步')
    expect(component).toContain('按含税价同步')
    expect(component).toContain('未税单价缺失')
    expect(component).toContain('含税单价缺失')
    expect(component).toContain('label="U9私有字段"')
    expect(component).toContain('PrivateDescSeg9 同步价格')
    expect(component).toContain('prop="errorMessage"')
    expect(component).not.toContain('prop="salesMaterialNo"')
    expect(component).not.toContain('priceMissingTagType')
  })

  test('lets operators choose whether U9 sync uses untaxed or tax included price', () => {
    const component = readSource('components', 'SalesOrderU9Sync.vue')
    const api = readSource('services', 'marketApi.js')

    expect(component).toContain('const U9_PRICE_MODE_OPTIONS = [')
    expect(component).toContain("value: 'UNTAXED'")
    expect(component).toContain("value: 'TAX_INCLUDED'")
    expect(component).toContain('const u9PriceMode = ref(\'TAX_INCLUDED\')')
    expect(component).toContain('U9价格口径')
    expect(component).toContain('按未税价同步')
    expect(component).toContain('按含税价同步')
    expect(component).toContain('U9价格=未税单价')
    expect(component).toContain('U9价格=含税单价')
    expect(component).toContain('priceMode: u9PriceMode.value')
    expect(component).toContain('syncSalesOrderU9(selectedOrderId.value, { priceMode: u9PriceMode.value })')
    expect(component).toContain('selectedU9PriceField')
    expect(component).toContain('selectedU9PriceMissingText')
    expect(component).toContain('previewMatchesCurrentView')
    expect(component).toContain('source.priceMode === u9PriceMode.value')
    expect(component).toContain('PrivateDescSeg9 同步价格')
    expect(api).toContain('syncSalesOrderU9 = (salesOrderId, payload = {})')
    expect(api).toContain('syncSalesOrdersU9 = (salesOrderIds = [], payload = {})')
  })

  test('keeps order-level sales order metadata in order info while item edits stay line scoped', () => {
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
    expect(component).toContain('v-model="itemDrafts[row.salesOrderItemId].customerOrderNo"')
    expect(component).toContain('v-model="itemDrafts[row.salesOrderItemId].customerProjectNo"')
    expect(component).toContain('v-model="itemDrafts[row.salesOrderItemId].internalProjectNo"')
  })

  test('orders preview item columns as fixed material fields followed by remaining line-level import-template fields', () => {
    const component = readSource('components', 'SalesOrderU9Sync.vue')
    const expectedColumns = [
      'label="图号"',
      'label="行号"',
      'label="版本"',
      'label="物料号"',
      'label="物料号状态"',
      'label="数量(PCS)"',
      'label="就绪"',
      'label="错误/警告"',
      'prop="untaxedUnitPrice" label="未税单价"',
      'prop="taxIncludedUnitPrice" label="含税单价"',
      'label="未税金额"',
      'label="含税金额"',
      'label="下单日期"',
      'label="客户下单抬头"',
      'label="客户订单号"',
      'label="客户项目号"',
      'label="内部项目号"',
      'label="客户交期"',
      'label="内部交期(给生产的交期)"',
      'label="描述"',
      'label="材料"',
      'label="表面处理"',
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
    expect(component).not.toContain('label="已出数量"')
    expect(component).not.toContain('label="未出数量"')
    expect(component).not.toContain('prop="shippedQty"')
    expect(component).not.toContain('prop="unshippedQty"')
    expect(component).not.toContain('label="订单类型"')
    expect(component).not.toContain('ItemCode 来自销售订单零件物料号')
  })

  test('supports inline editing and batch saving for U9 item fields only', () => {
    const component = readSource('components', 'SalesOrderU9Sync.vue')

    expect(component).toContain('saveSalesOrderU9Items')
    expect(component).toContain('saveSalesOrderU9OrderDate')
    expect(component).toContain('v-model="orderDateDraft"')
    expect(component).toContain('handleSaveOrderDate')
    expect(component).toContain('orderDateReadOnly')
    expect(component).toContain(':disabled="orderDateSaveDisabled"')
    expect(component).toContain('保存下单日期')
    expect(component).toContain('const U9_ITEM_EDITABLE_FIELDS = [')
    ;[
      'qty',
      'customerDeliveryDate',
      'internalDeliveryDate',
      'untaxedUnitPrice',
      'taxIncludedUnitPrice',
      'customerOrderNo',
      'customerProjectNo',
      'internalProjectNo',
      'remark'
    ].forEach(field => expect(component).toContain(`'${field}'`))
    expect(component).toContain('itemDrafts')
    expect(component).toContain('dirtyItemIds')
    expect(component).toContain('handleItemDraftChange')
    expect(component).toContain('handleSaveItemEdits')
    expect(component).toContain('itemEditReadOnly')
    expect(component).toContain(':loading="itemEditSaving"')
    expect(component).toContain(':disabled="itemEditSaveDisabled"')
    expect(component).toContain('保存修改')
    expect(component).toContain('hasItemEditPermission')
    expect(component).toContain("authStore.hasPermission(U9_SYNC_SUBMIT_PERMISSION)")
    expect(component).toContain("['已同步', '同步中', '结果待确认'].includes(currentU9Status.value)")
  })

  test('keeps identity and material fields read-only while editable fields use explicit controls', () => {
    const component = readSource('components', 'SalesOrderU9Sync.vue')

    const readOnlyColumns = [
      'prop="partNo" label="图号"',
      'prop="lineNo" label="行号"',
      'prop="version" label="版本"',
      'prop="materialNo" label="物料号"'
    ]
    readOnlyColumns.forEach(column => expect(component).toContain(column))

    expect(component).toContain('v-model="itemDrafts[row.salesOrderItemId].qty"')
    expect(component).toContain('v-model="itemDrafts[row.salesOrderItemId].untaxedUnitPrice"')
    expect(component).toContain('v-model="itemDrafts[row.salesOrderItemId].taxIncludedUnitPrice"')
    expect(component).toContain('v-model="itemDrafts[row.salesOrderItemId].customerDeliveryDate"')
    expect(component).toContain('v-model="itemDrafts[row.salesOrderItemId].internalDeliveryDate"')
    expect(component).toContain('v-model="itemDrafts[row.salesOrderItemId].customerOrderNo"')
    expect(component).toContain('v-model="itemDrafts[row.salesOrderItemId].customerProjectNo"')
    expect(component).toContain('v-model="itemDrafts[row.salesOrderItemId].internalProjectNo"')
    expect(component).toContain('v-model="itemDrafts[row.salesOrderItemId].remark"')
    expect(component).toContain(':disabled="itemEditReadOnly"')
    expect(component).not.toContain('v-model="itemDrafts[row.salesOrderItemId].materialNo"')
    expect(component).not.toContain('v-model="itemDrafts[row.salesOrderItemId].partNo"')
    expect(component).not.toContain('v-model="itemDrafts[row.salesOrderItemId].version"')
    expect(component).not.toContain('v-model="itemDrafts[row.salesOrderItemId].lineNo"')
  })

  test('mounts U9 item data replacement dialog beside item edit actions', () => {
    const component = readSource('components', 'SalesOrderU9Sync.vue')

    expect(component).toContain("import SalesOrderU9ItemDataReplacementDialog from './u9-sync/SalesOrderU9ItemDataReplacementDialog.vue'")
    expect(component).toContain('<SalesOrderU9ItemDataReplacementDialog')
    expect(component).toContain('v-model="itemDataReplacementVisible"')
    expect(component).toContain(':sales-order-id="selectedOrderId"')
    expect(component).toContain(':disabled="itemDataReplacementDisabled"')
    expect(component).toContain('@confirmed="handleItemDataReplacementConfirmed"')
    expect(component).toContain('表格数据替换')
    expect(component).toContain('itemDataReplacementDisabled')
    expect(component).toContain('itemDataReplacementDisabledReason')
    expect(component).toContain('openItemDataReplacementDialog')
    expect(component).toContain('handleItemDataReplacementConfirmed')
    expect(component).toContain('v-permission="U9_SYNC_SUBMIT_PERMISSION"')
    expect(component).toContain('authStore.hasPermission(U9_SYNC_SUBMIT_PERMISSION)')
    expect(component).toContain("['已同步', '同步中', '结果待确认'].includes(currentU9Status.value)")
    expect(component).toContain('applyPreviewData(response.preview, { preserveResult: true })')
    expect(component).toContain('previewSelectedOrder({ silent: true, preserveResult: true })')
    expect(component).not.toContain('导入替换图纸')
  })
})
