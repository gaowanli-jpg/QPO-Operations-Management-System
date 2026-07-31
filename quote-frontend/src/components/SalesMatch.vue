<template>
  <section class="erp-page sales-match-page">
    <header class="erp-page-header sales-match-header">
      <div>
        <h1 class="erp-page-title">销售匹配</h1>
        <p class="erp-page-description">选择销售订单，预览历史已提单零件快照，并确认补全生产信息。</p>
      </div>
      <div class="erp-page-actions sales-match-header-actions">
        <el-button :loading="orderLoading || itemLoading" @click="refreshCurrentState">刷新</el-button>
        <el-button type="primary" :loading="previewLoading" :disabled="!hasMatchTarget || previewLoading" @click="previewMatch()">
          预览匹配
        </el-button>
        <el-button
          v-permission="SALES_MATCH_APPLY_PERMISSION"
          type="success"
          :loading="isPending(salesMatchActionKeys.apply)"
          :disabled="!canApply"
          @click="applyMatch"
        >
          确认补全
        </el-button>
      </div>
    </header>

    <section class="erp-toolbar">
      <div class="sales-match-toolbar">
        <label v-permission="SALES_MATCH_CUSTOMER_SELECT_PERMISSION" class="sales-match-filter">
          <span>客户</span>
          <el-select
            v-model="filters.customerName"
            class="sales-match-customer-select"
            filterable
            remote
            clearable
            reserve-keyword
            :remote-method="searchCustomers"
            :loading="customerLoading"
            placeholder="选择客户"
            @visible-change="handleCustomerVisible"
          >
            <el-option
              v-for="customer in customerOptions"
              :key="customer.id || customer.customerName"
              :label="customerLabel(customer)"
              :value="customer.customerName"
            />
          </el-select>
        </label>
        <label class="sales-match-filter sales-match-keyword-filter">
          <span>销售订单筛选</span>
          <el-input
            v-model.trim="filters.keyword"
            clearable
            placeholder="销售订单号 / 客户订单号 / 内部项目号"
            @keyup.enter="searchOrders"
          />
        </label>
        <label class="sales-match-filter">
          <span>订单状态</span>
          <el-select v-model="filters.status" clearable placeholder="全部状态">
            <el-option label="已报价" value="已报价" />
            <el-option label="部分已提单" value="部分已提单" />
            <el-option label="已生成提单" value="已生成提单" />
          </el-select>
        </label>
        <label class="sales-match-filter">
          <span>匹配状态</span>
          <el-select v-model="filters.matchStatus" clearable placeholder="全部匹配状态">
            <el-option label="待匹配" value="待匹配" />
            <el-option label="已匹配" value="已匹配" />
            <el-option label="未匹配" value="未匹配" />
            <el-option label="跳过" value="跳过" />
          </el-select>
        </label>
        <label class="sales-match-checkbox">
          <el-checkbox v-model="matchOptions.onlyUnmatched">仅处理未补全零件</el-checkbox>
        </label>
        <div class="sales-match-toolbar-actions">
          <el-button type="primary" :loading="orderLoading" @click="searchOrders">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </div>
      </div>
    </section>

    <section class="erp-surface">
      <div class="sales-match-summary-grid">
        <div class="sales-match-summary-card">
          <strong>{{ orderPagination.total }}</strong>
          <span>销售订单</span>
        </div>
        <div class="sales-match-summary-card">
          <strong>{{ selectedOrderIds.length }}</strong>
          <span>已选订单</span>
        </div>
        <div class="sales-match-summary-card">
          <strong>{{ previewSummary.totalItemCount || 0 }}</strong>
          <span>预览零件</span>
        </div>
        <div class="sales-match-summary-card success">
          <strong>{{ previewSummary.matchedItemCount || 0 }}</strong>
          <span>命中快照</span>
        </div>
        <div class="sales-match-summary-card warning">
          <strong>{{ previewSummary.unmatchedItemCount || 0 }}</strong>
          <span>未匹配</span>
        </div>
      </div>
      <el-alert
        v-if="previewSummary.warningMessage"
        class="sales-match-warning"
        :title="previewSummary.warningMessage"
        type="warning"
        :closable="false"
        show-icon
      />
    </section>

    <section class="sales-match-workspace">
      <section class="erp-table-surface sales-match-orders">
        <div class="sales-match-section-header">
          <div>
            <h2>销售订单列表</h2>
            <p>只从独立销售订单主表读取，支持后端分页。</p>
          </div>
          <el-tag type="info">已选 {{ selectedOrderIds.length }}</el-tag>
        </div>
        <div class="erp-table-body">
          <el-table
            ref="orderTableRef"
            :data="orders"
            :row-key="orderRowKey"
            border
            stripe
            height="360"
            v-loading="orderLoading"
            empty-text="暂无销售订单"
            @selection-change="handleOrderSelectionChange"
            @row-click="selectOrder"
          >
            <el-table-column type="selection" width="46" :reserve-selection="true" />
            <el-table-column prop="salesOrderNo" label="销售订单号" min-width="150" fixed show-overflow-tooltip />
            <el-table-column prop="customerName" label="客户" min-width="140" show-overflow-tooltip />
            <el-table-column prop="documentTypeCode" label="单据类型" width="96" align="center" />
            <el-table-column prop="customerOrderNo" label="客户订单号" min-width="140" show-overflow-tooltip />
            <el-table-column prop="internalProjectNo" label="内部项目号" min-width="140" show-overflow-tooltip />
            <el-table-column label="零件数" width="88" align="right">
              <template #default="{ row }">{{ row.totalItemCount || 0 }}</template>
            </el-table-column>
            <el-table-column label="已匹配" width="88" align="right">
              <template #default="{ row }">{{ row.matchedItemCount || 0 }}</template>
            </el-table-column>
            <el-table-column label="未匹配" width="88" align="right">
              <template #default="{ row }">{{ row.unmatchedItemCount || 0 }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="112" align="center">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.status)" size="small">{{ row.status || '-' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="warningMessage" label="警告" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ row.warningMessage || '-' }}</template>
            </el-table-column>
            <el-table-column prop="updatedAt" label="更新时间" min-width="160">
              <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
            </el-table-column>
          </el-table>
        </div>
        <div class="erp-pagination">
          <el-pagination
            v-model:current-page="orderPagination.page"
            v-model:page-size="orderPagination.pageSize"
            :total="orderPagination.total"
            :page-sizes="PAGE_SIZES"
            layout="total, sizes, prev, pager, next, jumper"
            :pager-count="7"
            background
            @size-change="handleOrderPageSizeChange"
            @current-change="handleOrderPageChange"
          />
        </div>
      </section>

      <section class="erp-table-surface sales-match-items">
        <div class="sales-match-section-header">
          <div>
            <h2>销售订单零件</h2>
            <p>{{ activeOrderTitle }}</p>
          </div>
          <el-tag :type="selectedItemIds.length > 0 ? 'success' : 'info'">已选 {{ selectedItemIds.length }}</el-tag>
        </div>
        <div class="erp-table-body">
          <el-table
            ref="itemTableRef"
            :data="items"
            :row-key="itemRowKey"
            border
            stripe
            height="360"
            v-loading="itemLoading"
            empty-text="请选择销售订单查看零件"
            @selection-change="handleItemSelectionChange"
          >
            <el-table-column type="selection" width="46" :reserve-selection="true" />
            <el-table-column prop="lineNo" label="行号" width="70" align="center" />
            <el-table-column prop="partNo" label="图号" min-width="140" fixed show-overflow-tooltip />
            <el-table-column prop="version" label="版本" width="90" align="center" show-overflow-tooltip />
            <el-table-column prop="description" label="物料描述" min-width="180" show-overflow-tooltip />
            <el-table-column prop="qty" label="数量" width="80" align="right" />
            <el-table-column prop="materialName" label="材料" min-width="120" show-overflow-tooltip />
            <el-table-column prop="surfaceTreatment" label="表面处理" min-width="120" show-overflow-tooltip />
            <el-table-column prop="materialNo" label="物料号" min-width="130" show-overflow-tooltip>
              <template #default="{ row }">{{ row.materialNo || '待回写' }}</template>
            </el-table-column>
            <el-table-column label="含税单价" width="110" align="right">
              <template #default="{ row }">{{ formatMoney(row.taxIncludedUnitPrice) }}</template>
            </el-table-column>
            <el-table-column prop="currentMatchStatus" label="匹配状态" width="112" align="center">
              <template #default="{ row }">
                <el-tag :type="matchTagType(row.currentMatchStatus)" size="small">{{ row.currentMatchStatus || '待匹配' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="assemblyRelationStatus" label="父子关系" min-width="140" show-overflow-tooltip />
            <el-table-column prop="matchMessage" label="匹配说明" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">{{ row.matchMessage || '-' }}</template>
            </el-table-column>
          </el-table>
        </div>
        <div class="erp-pagination">
          <el-pagination
            v-model:current-page="itemPagination.page"
            v-model:page-size="itemPagination.pageSize"
            :total="itemPagination.total"
            :page-sizes="PAGE_SIZES"
            layout="total, sizes, prev, pager, next, jumper"
            :pager-count="7"
            background
            @size-change="handleItemPageSizeChange"
            @current-change="loadItems"
          />
        </div>
      </section>
    </section>

    <section class="erp-table-surface sales-match-preview">
      <div class="sales-match-section-header">
        <div>
          <h2>匹配预览结果</h2>
          <p>预览不落库，只展示历史已提单生产快照摘要和保护字段。</p>
        </div>
        <div class="sales-match-options">
          <el-checkbox v-model="applyOptions.overwriteExistingProductionSnapshot">覆盖已有生产快照</el-checkbox>
          <el-checkbox v-model="applyOptions.overwriteTemplateMaterial">覆盖模板材料/表面处理</el-checkbox>
        </div>
      </div>
      <div class="erp-table-body">
        <el-table :data="previewRows" border stripe height="360" v-loading="previewLoading" empty-text="暂无预览结果">
          <el-table-column prop="salesOrderNo" label="销售订单号" min-width="150" fixed show-overflow-tooltip />
          <el-table-column prop="customerOrderNo" label="客户订单号" min-width="130" show-overflow-tooltip />
          <el-table-column prop="partNo" label="图号" min-width="140" fixed show-overflow-tooltip />
          <el-table-column prop="version" label="版本" width="90" align="center" show-overflow-tooltip />
          <el-table-column prop="qty" label="数量" width="80" align="right" />
          <el-table-column prop="currentMatchStatus" label="当前状态" width="110" align="center" />
          <el-table-column label="历史来源" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.matched">RFQ ID {{ row.sourceRfqId }} / 零件 {{ row.sourceItemId }}</span>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="命中" width="86" align="center">
            <template #default="{ row }">
              <el-tag :type="row.matched ? 'success' : 'warning'" size="small">{{ row.matched ? '已命中' : '未匹配' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="materialName" label="历史材料" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ row.materialName || '-' }}</template>
          </el-table-column>
          <el-table-column prop="surfaceTreatment" label="历史表面处理" min-width="130" show-overflow-tooltip>
            <template #default="{ row }">{{ row.surfaceTreatment || '-' }}</template>
          </el-table-column>
          <el-table-column label="可补全摘要" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ formatFieldSummary(row.changedFields) }}</template>
          </el-table-column>
          <el-table-column label="保护字段" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ formatFieldSummary(row.protectedFields) }}</template>
          </el-table-column>
          <el-table-column prop="assemblyRelationStatus" label="父子关系" min-width="140" show-overflow-tooltip />
          <el-table-column prop="warningMessage" label="警告" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ row.warningMessage || '-' }}</template>
          </el-table-column>
        </el-table>
      </div>
    </section>

    <section v-if="applyResult" class="erp-table-surface sales-match-apply-result">
      <div class="sales-match-section-header">
        <div>
          <h2>补全结果</h2>
          <p>{{ applyResult.message || '销售匹配补全已返回结果。' }}</p>
        </div>
        <div class="sales-match-result-tags">
          <el-tag type="success">已更新 {{ applyResult.updatedItemCount || 0 }}</el-tag>
          <el-tag type="warning">未匹配 {{ applyResult.unmatchedItemCount || 0 }}</el-tag>
          <el-tag type="info">跳过 {{ applyResult.skippedItemCount || 0 }}</el-tag>
        </div>
      </div>
      <el-alert
        v-if="applyResult.warningMessage"
        class="sales-match-warning"
        :title="applyResult.warningMessage"
        type="warning"
        :closable="false"
        show-icon
      />
      <div class="erp-table-body">
        <el-table :data="applyResult.rows || []" border stripe max-height="320" empty-text="暂无补全明细">
          <el-table-column prop="salesOrderNo" label="销售订单号" min-width="150" fixed show-overflow-tooltip />
          <el-table-column prop="partNo" label="图号" min-width="140" fixed show-overflow-tooltip />
          <el-table-column prop="version" label="版本" width="90" align="center" show-overflow-tooltip />
          <el-table-column label="结果" width="110" align="center">
            <template #default="{ row }">
              <el-tag :type="applyRowTagType(row)" size="small">{{ applyRowStatus(row) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="历史来源" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.sourceRfqId">RFQ ID {{ row.sourceRfqId }} / 零件 {{ row.sourceItemId }}</span>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column prop="matchStatus" label="匹配状态" width="110" align="center" />
          <el-table-column prop="assemblyRelationStatus" label="父子关系" min-width="140" show-overflow-tooltip />
          <el-table-column prop="matchMessage" label="说明" min-width="220" show-overflow-tooltip />
        </el-table>
      </div>
    </section>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '../stores/authStore'
import { usePendingAction } from '../composables/usePendingAction'
import {
  applySalesOrderMatch,
  listSalesMatchCustomers,
  listSalesMatchItems,
  listSalesMatchOrders,
  previewSalesOrderMatch
} from '../services/marketApi'

const SALES_MATCH_APPLY_PERMISSION = 'market:sales-match:apply'
const SALES_MATCH_CUSTOMER_SELECT_PERMISSION = 'market:sales-match:customer-select'
const SALES_MATCH_ORDER_SELECT_PERMISSION = 'market:sales-match:order-select'
const PAGE_SIZES = [20, 50, 100]

const FIELD_LABELS = {
  materialName: '材料',
  surfaceTreatment: '表面处理',
  engineeringMaterialName: '工程材料',
  materialGrossWeight: '毛重',
  partNetWeight: '净重',
  quoteMode: '核价模式',
  itemType: '零件类型',
  assemblyFee: '组装费',
  baseCost: '基础成本',
  drawingUrl: '图纸',
  materialSnapshotAvailable: '材料快照',
  processSnapshotAvailable: '工艺路线',
  feeSnapshotAvailable: '费用快照',
  qty: '数量',
  materialNo: '物料号',
  taxIncludedUnitPrice: '含税单价',
  finalUnitPrice: '最终单价',
  customerOrderNo: '客户订单号',
  internalProjectNo: '内部项目号'
}

const authStore = useAuthStore()
const { isPending, runAction } = usePendingAction()

const customerOptions = ref([])
const orders = ref([])
const items = ref([])
const selectedOrders = ref([])
const selectedItems = ref([])
const selectedOrderId = ref(null)
const previewRows = ref([])
const previewSummary = ref({})
const applyResult = ref(null)
const previewCompleted = ref(false)
const customerLoading = ref(false)
const orderLoading = ref(false)
const itemLoading = ref(false)
const previewLoading = ref(false)

const filters = reactive({
  customerName: '',
  keyword: '',
  status: '',
  matchStatus: ''
})

const itemFilters = reactive({
  matchStatus: ''
})

const matchOptions = reactive({
  onlyUnmatched: true
})

const applyOptions = reactive({
  overwriteExistingProductionSnapshot: false,
  overwriteTemplateMaterial: false
})

const orderPagination = reactive({
  page: 1,
  pageSize: 20,
  total: 0
})

const itemPagination = reactive({
  page: 1,
  pageSize: 20,
  total: 0
})

const salesMatchActionKeys = {
  apply: 'market:sales-match:apply'
}

const canSelectCustomer = computed(() => authStore.hasPermission(SALES_MATCH_CUSTOMER_SELECT_PERMISSION))
const canSelectOrder = computed(() => authStore.hasPermission(SALES_MATCH_ORDER_SELECT_PERMISSION))
const selectedOrderIds = computed(() => selectedOrders.value.map(row => Number(row.salesOrderId)).filter(Boolean))
const selectedItemIds = computed(() => selectedItems.value.map(row => Number(row.salesOrderItemId)).filter(Boolean))
const hasMatchTarget = computed(() => selectedOrderIds.value.length > 0 || selectedItemIds.value.length > 0)
const canApply = computed(() => previewCompleted.value && hasMatchTarget.value && !previewLoading.value && !isPending(salesMatchActionKeys.apply))

const activeOrderTitle = computed(() => {
  const active = orders.value.find(row => row.salesOrderId === selectedOrderId.value)
  if (!active) return '从左侧选择一张销售订单后分页查看零件。'
  return `${active.salesOrderNo || '未编号销售订单'} / ${active.customerName || '未指定客户'}`
})

const requestErrorMessage = (error, fallback) => error?.response?.data?.message || error?.payload?.message || error?.message || fallback

const buildOrderParams = () => ({
  customerName: filters.customerName || undefined,
  keyword: filters.keyword || undefined,
  status: filters.status || undefined,
  matchStatus: filters.matchStatus || undefined,
  page: orderPagination.page,
  pageSize: orderPagination.pageSize
})

const buildItemParams = () => ({
  matchStatus: itemFilters.matchStatus || filters.matchStatus || undefined,
  page: itemPagination.page,
  pageSize: itemPagination.pageSize
})

const buildMatchPayload = () => {
  const itemIds = selectedItemIds.value
  return {
    salesOrderIds: itemIds.length > 0 ? [] : selectedOrderIds.value,
    salesOrderItemIds: itemIds,
    onlyUnmatched: matchOptions.onlyUnmatched
  }
}

const loadCustomers = async (keyword = '') => {
  if (!canSelectCustomer.value) return
  customerLoading.value = true
  try {
    customerOptions.value = await listSalesMatchCustomers({
      field: 'all',
      keyword: keyword || undefined
    }) || []
  } catch (error) {
    customerOptions.value = []
    ElMessage.warning(requestErrorMessage(error, '客户筛选项加载失败。'))
  } finally {
    customerLoading.value = false
  }
}

const searchCustomers = (keyword) => {
  loadCustomers(keyword)
}

const handleCustomerVisible = (visible) => {
  if (visible && customerOptions.value.length === 0) {
    loadCustomers()
  }
}

const loadOrders = async () => {
  if (!canSelectOrder.value) {
    orders.value = []
    orderPagination.total = 0
    return
  }
  orderLoading.value = true
  try {
    const response = await listSalesMatchOrders(buildOrderParams())
    orders.value = response?.records || []
    orderPagination.total = Number(response?.total || 0)
    orderPagination.page = Number(response?.page || orderPagination.page)
    orderPagination.pageSize = Number(response?.pageSize || orderPagination.pageSize)
    if (!orders.value.some(row => row.salesOrderId === selectedOrderId.value)) {
      selectedOrderId.value = orders.value[0]?.salesOrderId || null
      selectedItems.value = []
    }
    if (!selectedOrderId.value) {
      resetItems()
    }
  } catch (error) {
    orders.value = []
    orderPagination.total = 0
    selectedOrderId.value = null
    resetItems()
    ElMessage.error(requestErrorMessage(error, '销售订单加载失败。'))
  } finally {
    orderLoading.value = false
  }
}

const loadItems = async () => {
  if (!selectedOrderId.value) {
    resetItems()
    return
  }
  itemLoading.value = true
  try {
    const response = await listSalesMatchItems(selectedOrderId.value, buildItemParams())
    items.value = response?.records || []
    itemPagination.total = Number(response?.total || 0)
    itemPagination.page = Number(response?.page || itemPagination.page)
    itemPagination.pageSize = Number(response?.pageSize || itemPagination.pageSize)
  } catch (error) {
    resetItems()
    ElMessage.error(requestErrorMessage(error, '销售订单零件加载失败。'))
  } finally {
    itemLoading.value = false
  }
}

const resetItems = () => {
  items.value = []
  selectedItems.value = []
  itemPagination.total = 0
}

const searchOrders = async () => {
  orderPagination.page = 1
  itemPagination.page = 1
  clearPreviewState()
  await loadOrders()
  await loadItems()
}

const resetFilters = async () => {
  filters.customerName = ''
  filters.keyword = ''
  filters.status = ''
  filters.matchStatus = ''
  itemFilters.matchStatus = ''
  await searchOrders()
}

const refreshCurrentState = async () => {
  await loadOrders()
  await loadItems()
}

const handleOrderPageSizeChange = async () => {
  orderPagination.page = 1
  await loadOrders()
  await loadItems()
}

const handleOrderPageChange = async () => {
  await loadOrders()
  await loadItems()
}

const handleItemPageSizeChange = async () => {
  itemPagination.page = 1
  await loadItems()
}

const selectOrder = async (row) => {
  if (!row?.salesOrderId || selectedOrderId.value === row.salesOrderId) return
  selectedOrderId.value = row.salesOrderId
  itemPagination.page = 1
  selectedItems.value = []
  clearPreviewState()
  await loadItems()
}

const handleOrderSelectionChange = (rows) => {
  selectedOrders.value = rows || []
  if (!selectedOrderId.value && selectedOrders.value[0]?.salesOrderId) {
    selectedOrderId.value = selectedOrders.value[0].salesOrderId
    itemPagination.page = 1
    loadItems()
  }
  clearPreviewState()
}

const handleItemSelectionChange = (rows) => {
  selectedItems.value = rows || []
  clearPreviewState()
}

const previewMatch = async ({ silent = false } = {}) => {
  if (!hasMatchTarget.value) {
    if (!silent) ElMessage.warning('请先选择销售订单或销售订单零件。')
    return
  }
  previewLoading.value = true
  try {
    const response = await previewSalesOrderMatch(buildMatchPayload())
    previewSummary.value = {
      totalItemCount: response?.totalItemCount || 0,
      matchedItemCount: response?.matchedItemCount || 0,
      unmatchedItemCount: response?.unmatchedItemCount || 0,
      warningMessage: response?.warningMessage || ''
    }
    previewRows.value = response?.rows || []
    previewCompleted.value = true
    applyResult.value = null
    if (!silent) {
      ElMessage.success(`预览完成：命中 ${previewSummary.value.matchedItemCount} 个，未匹配 ${previewSummary.value.unmatchedItemCount} 个。`)
    }
  } catch (error) {
    previewCompleted.value = false
    previewRows.value = []
    previewSummary.value = {}
    ElMessage.error(requestErrorMessage(error, '销售匹配预览失败。'))
  } finally {
    previewLoading.value = false
  }
}

const applyMatch = async () => {
  if (!previewCompleted.value) {
    ElMessage.warning('请先完成一次预览匹配，再确认补全。')
    return
  }
  if (!hasMatchTarget.value) {
    ElMessage.warning('请先选择销售订单或销售订单零件。')
    return
  }
  return runAction(salesMatchActionKeys.apply, async () => {
    await ElMessageBox.confirm(
      '确认将预览命中的历史生产快照补全到销售订单零件？价格、数量、交期、客户字段和物料号不会被覆盖。',
      '确认销售匹配补全',
      {
        type: 'warning',
        confirmButtonText: '确认补全',
        cancelButtonText: '取消'
      }
    )
    try {
      const response = await applySalesOrderMatch({
        ...buildMatchPayload(),
        applyAllMatchedInSelectedOrders: matchOptions.onlyUnmatched,
        overwriteExistingProductionSnapshot: applyOptions.overwriteExistingProductionSnapshot,
        overwriteTemplateMaterial: applyOptions.overwriteTemplateMaterial
      })
      applyResult.value = response || {}
      ElMessage.success(response?.message || '销售匹配补全完成。')
      await refreshAfterApply()
    } catch (error) {
      ElMessage.error(requestErrorMessage(error, '销售匹配补全失败。'))
    }
  })
}

const refreshAfterApply = async () => {
  await loadOrders()
  await loadItems()
  if (hasMatchTarget.value) {
    await previewMatch({ silent: true })
  }
}

const clearPreviewState = () => {
  previewCompleted.value = false
  previewRows.value = []
  previewSummary.value = {}
  applyResult.value = null
}

const customerLabel = (customer) => {
  const code = customer.customerCode ? `${customer.customerCode} / ` : ''
  const shortName = customer.customerShortName ? `${customer.customerShortName} / ` : ''
  return `${code}${shortName}${customer.customerName || '-'}`
}

const orderRowKey = (row) => row.salesOrderId
const itemRowKey = (row) => row.salesOrderItemId

const statusTagType = (status) => {
  if (status === '已报价') return 'primary'
  if (status === '部分已提单') return 'warning'
  if (status === '已生成提单') return 'success'
  if (status === '废弃') return 'info'
  return 'info'
}

const matchTagType = (status) => {
  if (status === '已匹配') return 'success'
  if (status === '未匹配') return 'warning'
  if (status === '跳过') return 'info'
  return 'primary'
}

const applyRowStatus = (row) => {
  if (row?.skipped) return '跳过'
  if (row?.updated && row?.matched) return '已补全'
  if (row?.updated) return '已更新'
  return row?.matched ? '已匹配' : '未匹配'
}

const applyRowTagType = (row) => {
  if (row?.skipped) return 'info'
  if (row?.updated && row?.matched) return 'success'
  if (row?.matched) return 'primary'
  return 'warning'
}

const formatDateTime = (value) => {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

const formatMoney = (value) => {
  if (value === null || value === undefined || value === '') return '-'
  const amount = Number(value)
  if (Number.isNaN(amount)) return String(value)
  return `¥ ${amount.toFixed(2)}`
}

const formatFieldSummary = (fields) => {
  if (!fields || Object.keys(fields).length === 0) return '-'
  const labels = Object.entries(fields)
    .filter(([, value]) => value !== false && value !== null && value !== undefined && value !== '')
    .map(([key]) => FIELD_LABELS[key] || key)
  if (labels.length === 0) return '-'
  return labels.length > 5 ? `${labels.slice(0, 5).join('、')}等 ${labels.length} 项` : labels.join('、')
}

onMounted(async () => {
  await loadCustomers()
  await loadOrders()
  await loadItems()
})
</script>

<style scoped>
.sales-match-page {
  padding: 0;
}

.sales-match-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

.sales-match-header-actions {
  flex-wrap: wrap;
  justify-content: flex-end;
}

.sales-match-toolbar {
  display: grid;
  grid-template-columns: minmax(180px, 240px) minmax(260px, 1fr) 150px 150px auto auto;
  gap: 12px;
  align-items: end;
  width: 100%;
}

.sales-match-filter {
  display: grid;
  gap: 6px;
  min-width: 0;
  color: var(--erp-text-secondary);
  font-size: 12px;
  font-weight: 600;
}

.sales-match-filter :deep(.el-select),
.sales-match-filter :deep(.el-input) {
  width: 100%;
}

.sales-match-customer-select,
.sales-match-keyword-filter {
  min-width: 0;
}

.sales-match-checkbox {
  min-height: 32px;
  display: flex;
  align-items: center;
  white-space: nowrap;
}

.sales-match-toolbar-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

.sales-match-summary-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(120px, 1fr));
  gap: 10px;
}

.sales-match-summary-card {
  display: grid;
  gap: 4px;
  min-height: 64px;
  padding: 12px;
  border: 1px solid var(--erp-border-soft);
  border-radius: 8px;
  background: var(--erp-surface-soft);
}

.sales-match-summary-card strong {
  color: var(--erp-text);
  font-size: 24px;
  line-height: 1;
}

.sales-match-summary-card span {
  color: var(--erp-text-secondary);
  font-size: 12px;
}

.sales-match-summary-card.success strong {
  color: var(--erp-success);
}

.sales-match-summary-card.warning strong {
  color: var(--erp-warning);
}

.sales-match-warning {
  margin-top: 10px;
}

.sales-match-workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 14px;
}

.sales-match-section-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 14px 0;
}

.sales-match-section-header h2 {
  margin: 0;
  color: var(--erp-text);
  font-size: 16px;
  line-height: 1.3;
}

.sales-match-section-header p {
  margin: 4px 0 0;
  color: var(--erp-text-secondary);
  font-size: 12px;
}

.sales-match-orders .erp-table-body,
.sales-match-items .erp-table-body,
.sales-match-preview .erp-table-body,
.sales-match-apply-result .erp-table-body {
  padding: 12px 14px;
}

.sales-match-options,
.sales-match-result-tags {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.sales-match-options :deep(.el-checkbox) {
  margin-right: 0;
}

@media (max-width: 1200px) {
  .sales-match-toolbar {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .sales-match-workspace {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .sales-match-header,
  .sales-match-section-header {
    flex-direction: column;
    align-items: stretch;
  }

  .sales-match-header-actions,
  .sales-match-toolbar-actions,
  .sales-match-options,
  .sales-match-result-tags {
    justify-content: stretch;
  }

  .sales-match-header-actions :deep(.el-button),
  .sales-match-toolbar-actions :deep(.el-button) {
    flex: 1;
    margin-left: 0;
  }

  .sales-match-toolbar,
  .sales-match-summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
