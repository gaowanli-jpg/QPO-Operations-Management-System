<template>
  <section class="erp-page sales-order-u9-sync-page">
    <header class="erp-page-header sales-order-u9-sync-header">
      <div>
        <h1 class="erp-page-title">U9同步</h1>
        <p class="erp-page-description">选择已完成提单并确认物料号的销售订单，按整单同步到 U9。</p>
      </div>
      <div class="erp-page-actions sales-order-u9-sync-header-actions">
        <el-button :loading="orderLoading" @click="refreshCurrentState">刷新</el-button>
        <el-button type="primary" :loading="previewLoading" :disabled="!selectedOrder" @click="previewSelectedOrder()">
          预览U9同步
        </el-button>
        <el-tooltip :disabled="!syncDisabledReason" :content="syncDisabledReason" placement="top">
          <span class="sales-order-u9-sync-action-wrap">
            <el-button
              v-permission="U9_SYNC_SUBMIT_PERMISSION"
              type="success"
              :loading="isPending(u9SyncActionKeys.sync)"
              :disabled="!canSyncSelected"
              @click="syncSelectedOrder"
            >
              同步U9
            </el-button>
          </span>
        </el-tooltip>
      </div>
    </header>

    <section class="erp-toolbar">
      <div class="sales-order-u9-sync-toolbar">
        <label v-permission="U9_SYNC_CUSTOMER_SELECT_PERMISSION" class="sales-order-u9-sync-filter">
          <span>客户</span>
          <el-input v-model.trim="filters.customerName" clearable placeholder="输入客户名称" @keyup.enter="searchOrders" />
        </label>
        <label class="sales-order-u9-sync-filter sales-order-u9-sync-keyword-filter">
          <span>销售订单筛选</span>
          <el-input v-model.trim="filters.keyword" clearable placeholder="销售订单号 / 客户订单号 / 内部项目号" @keyup.enter="searchOrders" />
        </label>
        <label class="sales-order-u9-sync-filter">
          <span>订单状态</span>
          <el-select v-model="filters.status" clearable placeholder="全部状态">
            <el-option label="已报价" value="已报价" />
            <el-option label="部分已提单" value="部分已提单" />
            <el-option label="已生成提单" value="已生成提单" />
          </el-select>
        </label>
        <label class="sales-order-u9-sync-filter">
          <span>U9 状态</span>
          <el-select v-model="filters.u9SyncStatus" clearable placeholder="全部状态">
            <el-option label="未同步" value="未同步" />
            <el-option label="同步中" value="同步中" />
            <el-option label="已同步" value="已同步" />
            <el-option label="同步失败" value="同步失败" />
          </el-select>
        </label>
        <label class="sales-order-u9-sync-filter">
          <span>物料号就绪</span>
          <el-select v-model="filters.materialReadyStatus" clearable placeholder="全部">
            <el-option label="已就绪" value="READY" />
            <el-option label="未就绪" value="BLOCKED" />
          </el-select>
        </label>
        <div class="sales-order-u9-sync-toolbar-actions">
          <el-button type="primary" :loading="orderLoading" @click="searchOrders">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </div>
      </div>
    </section>

    <section class="erp-surface">
      <div class="sales-order-u9-sync-summary-grid">
        <div class="sales-order-u9-sync-summary-card">
          <strong>{{ orderPagination.total }}</strong>
          <span>销售订单</span>
        </div>
        <div class="sales-order-u9-sync-summary-card">
          <strong>{{ selectedOrder ? selectedOrder.salesOrderNo || '-' : '-' }}</strong>
          <span>当前选择</span>
        </div>
        <div class="sales-order-u9-sync-summary-card">
          <strong>{{ readySummary.itemCount }}</strong>
          <span>零件数</span>
        </div>
        <div class="sales-order-u9-sync-summary-card success">
          <strong>{{ readySummary.materialConfirmedItemCount }}</strong>
          <span>已确认物料号</span>
        </div>
        <div class="sales-order-u9-sync-summary-card warning">
          <strong>{{ readySummary.missingMaterialItemCount }}</strong>
          <span>缺失/未确认</span>
        </div>
        <div class="sales-order-u9-sync-summary-card" :class="readySummary.syncReady ? 'success' : 'warning'">
          <strong>{{ readySummary.syncReady ? '已就绪' : '不可同步' }}</strong>
          <span>同步就绪摘要</span>
        </div>
      </div>
      <el-alert
        v-if="syncDisabledReason"
        class="sales-order-u9-sync-alert"
        :title="syncDisabledReason"
        type="warning"
        :closable="false"
        show-icon
      />
    </section>

    <section class="erp-table-surface">
      <div class="sales-order-u9-sync-section-header">
        <div>
          <h2>销售订单列表</h2>
          <p>只读取独立销售订单主表，列表筛选和翻页均由后端处理。</p>
        </div>
        <el-tag :type="selectedOrder ? 'success' : 'info'">{{ selectedOrderTitle }}</el-tag>
      </div>
      <div class="erp-table-body">
        <el-table
          :data="orders"
          :row-key="orderRowKey"
          border
          stripe
          height="360"
          v-loading="orderLoading"
          empty-text="暂无销售订单"
          highlight-current-row
          @row-click="selectOrder"
        >
          <el-table-column width="52" align="center">
            <template #default="{ row }">
              <el-radio :model-value="selectedOrderId" :label="row.salesOrderId" @change="selectOrder(row)">
                <span class="sales-order-u9-sync-radio-label">选择</span>
              </el-radio>
            </template>
          </el-table-column>
          <el-table-column prop="salesOrderNo" label="销售订单号" min-width="150" fixed show-overflow-tooltip />
          <el-table-column prop="customerName" label="客户" min-width="140" show-overflow-tooltip />
          <el-table-column prop="customerOrderNo" label="客户订单号" min-width="140" show-overflow-tooltip />
          <el-table-column prop="internalProjectNo" label="内部项目号" min-width="140" show-overflow-tooltip />
          <el-table-column prop="documentTypeCode" label="单据类型" width="96" align="center" />
          <el-table-column prop="status" label="状态" width="112" align="center">
            <template #default="{ row }">
              <el-tag :type="orderStatusTagType(row.status)" size="small">{{ row.status || '-' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="零件数" width="88" align="right">
            <template #default="{ row }">{{ numberOrZero(row.itemCount) }}</template>
          </el-table-column>
          <el-table-column label="已确认物料号" width="122" align="right">
            <template #default="{ row }">
              {{ numberOrZero(row.materialConfirmedItemCount) }} / {{ numberOrZero(row.itemCount) }}
            </template>
          </el-table-column>
          <el-table-column prop="u9SyncStatus" label="U9状态" width="112" align="center">
            <template #default="{ row }">
              <el-tag :type="u9StatusTagType(row.u9SyncStatus)" size="small">{{ row.u9SyncStatus || '未同步' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="u9OrderCode" label="U9编码" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ row.u9OrderCode || '-' }}</template>
          </el-table-column>
          <el-table-column prop="blockedReason" label="阻断原因" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">{{ row.blockedReason || row.u9SyncMessage || '-' }}</template>
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

    <section class="erp-table-surface">
      <div class="sales-order-u9-sync-section-header">
        <div>
          <h2>U9同步预览</h2>
          <p>预览只展示销售订单头和销售订单零件中的同步字段，不调用 U9。</p>
        </div>
        <el-tag :type="previewData?.syncReady ? 'success' : 'warning'">
          {{ previewData ? (previewData.syncReady ? '可同步' : '不可同步') : '待预览' }}
        </el-tag>
      </div>
      <el-alert
        v-if="previewData?.blockedReason"
        class="sales-order-u9-sync-alert"
        :title="previewData.blockedReason"
        type="warning"
        :closable="false"
        show-icon
      />
      <div v-if="previewData" class="sales-order-u9-sync-preview-meta">
        <span>销售订单号：{{ previewData.salesOrderNo || '-' }}</span>
        <span>OrderBy：{{ previewData.orderBy || previewData.customerCode || '-' }}</span>
        <span>DocumentType：{{ previewData.documentTypeCode || '-' }}</span>
        <span>客户订单号：{{ previewData.customerOrderNo || '-' }}</span>
        <span>内部项目号：{{ previewData.internalProjectNo || '-' }}</span>
        <span>U9状态：{{ previewData.u9SyncStatus || '未同步' }}</span>
      </div>
      <div class="erp-table-body">
        <el-table
          :data="previewLines"
          border
          stripe
          height="360"
          v-loading="previewLoading"
          empty-text="请选择销售订单并点击预览U9同步"
        >
          <el-table-column prop="lineNo" label="行号" width="72" align="center" />
          <el-table-column prop="partNo" label="图号" min-width="130" fixed show-overflow-tooltip />
          <el-table-column prop="version" label="版本" width="88" align="center" show-overflow-tooltip />
          <el-table-column prop="salesMaterialNo" label="销售料号" min-width="130" show-overflow-tooltip />
          <el-table-column prop="description" label="描述" min-width="160" show-overflow-tooltip />
          <el-table-column prop="qty" label="数量" width="82" align="right" />
          <el-table-column prop="finalUnitPrice" label="单价" width="104" align="right">
            <template #default="{ row }">{{ formatMoney(row.finalUnitPrice) }}</template>
          </el-table-column>
          <el-table-column prop="deliveryDate" label="交期" width="118">
            <template #default="{ row }">{{ formatDate(row.deliveryDate) }}</template>
          </el-table-column>
          <el-table-column prop="itemCode" label="ItemCode" min-width="130" show-overflow-tooltip>
            <template #default="{ row }">{{ row.itemCode || row.materialNo || '-' }}</template>
          </el-table-column>
          <el-table-column prop="materialNoStatus" label="物料号状态" width="116" align="center">
            <template #default="{ row }">
              <el-tag :type="materialStatusTagType(row.materialNoStatus)" size="small">{{ row.materialNoStatus || '未确认' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="materialNoSource" label="物料号来源" width="118" show-overflow-tooltip>
            <template #default="{ row }">{{ row.materialNoSource || '-' }}</template>
          </el-table-column>
          <el-table-column label="来源提单行" width="110" align="right">
            <template #default="{ row }">{{ row.materialNoSourceTicketId || '-' }}</template>
          </el-table-column>
          <el-table-column label="就绪" width="88" align="center">
            <template #default="{ row }">
              <el-tag :type="row.ready ? 'success' : 'danger'" size="small">{{ row.ready ? '已就绪' : '阻断' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="blockedReason" label="阻断原因" min-width="190" show-overflow-tooltip>
            <template #default="{ row }">{{ row.blockedReason || '-' }}</template>
          </el-table-column>
        </el-table>
      </div>
    </section>

    <section class="erp-table-surface">
      <div class="sales-order-u9-sync-section-header">
        <div>
          <h2>U9同步结果</h2>
          <p>展示 U9 返回摘要、成功编码和失败明细，失败后可在修正后重试。</p>
        </div>
        <div v-if="syncResult" class="sales-order-u9-sync-result-tags">
          <el-tag :type="syncResult.success ? 'success' : 'danger'">{{ syncResult.status || '-' }}</el-tag>
          <el-tag type="success">成功 {{ numberOrZero(syncResult.successOrderCount) }}</el-tag>
          <el-tag type="danger">失败 {{ numberOrZero(syncResult.failedOrderCount) }}</el-tag>
          <el-tag type="info">合计 {{ numberOrZero(syncResult.totalOrderCount) }}</el-tag>
        </div>
      </div>
      <el-alert
        v-if="syncResult?.message"
        class="sales-order-u9-sync-alert"
        :title="syncResult.message"
        :type="syncResult.success ? 'success' : 'warning'"
        :closable="false"
        show-icon
      />
      <div class="erp-table-body">
        <el-table :data="syncRows" border stripe max-height="320" empty-text="暂无U9同步结果">
          <el-table-column prop="salesOrderNo" label="销售订单号" min-width="150" fixed show-overflow-tooltip />
          <el-table-column prop="status" label="结果状态" width="112" align="center">
            <template #default="{ row }">
              <el-tag :type="row.success ? 'success' : 'danger'" size="small">{{ row.status || (row.success ? '成功' : '失败') }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="u9OrderCode" label="U9编码" min-width="130" show-overflow-tooltip>
            <template #default="{ row }">{{ row.u9OrderCode || '-' }}</template>
          </el-table-column>
          <el-table-column prop="u9OrderId" label="U9订单ID" min-width="130" show-overflow-tooltip>
            <template #default="{ row }">{{ row.u9OrderId || '-' }}</template>
          </el-table-column>
          <el-table-column prop="errorMessage" label="失败明细" min-width="260" show-overflow-tooltip>
            <template #default="{ row }">{{ row.errorMessage || '-' }}</template>
          </el-table-column>
        </el-table>
      </div>
    </section>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { usePendingAction } from '../composables/usePendingAction'
import {
  listSalesOrderU9SyncOrders,
  previewSalesOrderU9Sync,
  syncSalesOrderU9
} from '../services/marketApi'

const U9_SYNC_SUBMIT_PERMISSION = 'market:u9-sync:submit'
const U9_SYNC_CUSTOMER_SELECT_PERMISSION = 'market:u9-sync:customer-select'
const PAGE_SIZES = [20, 50, 100]

const u9SyncActionKeys = {
  sync: 'market:u9-sync:submit'
}

const { isPending, runAction } = usePendingAction()

const orders = ref([])
const selectedOrderId = ref(null)
const previewData = ref(null)
const syncResult = ref(null)
const orderLoading = ref(false)
const previewLoading = ref(false)

const filters = reactive({
  customerName: '',
  keyword: '',
  status: '',
  u9SyncStatus: '',
  materialReadyStatus: ''
})

const orderPagination = reactive({
  page: 1,
  pageSize: 20,
  total: 0
})

const selectedOrder = computed(() => orders.value.find(row => row.salesOrderId === selectedOrderId.value) || null)

const selectedOrderTitle = computed(() => {
  if (!selectedOrder.value) return '未选择'
  return selectedOrder.value.salesOrderNo || '未编号销售订单'
})

const previewLines = computed(() => previewData.value?.lines || [])
const syncRows = computed(() => syncResult.value?.rows || [])

const readySummary = computed(() => {
  const source = previewData.value || selectedOrder.value || {}
  return {
    itemCount: numberOrZero(source.itemCount),
    materialConfirmedItemCount: numberOrZero(source.materialConfirmedItemCount),
    missingMaterialItemCount: numberOrZero(source.missingMaterialItemCount),
    syncReady: source.syncReady === true
  }
})

const syncDisabledReason = computed(() => {
  if (isPending(u9SyncActionKeys.sync)) return 'U9同步正在提交，请稍候'
  if (!selectedOrder.value) return '请先选择销售订单'
  if (!previewData.value || previewData.value.salesOrderId !== selectedOrder.value.salesOrderId) {
    return '请先预览U9同步'
  }
  if (previewData.value.u9SyncStatus === '已同步') return '该销售订单已同步 U9'
  if (previewData.value.syncReady !== true) {
    return previewData.value.blockedReason || selectedOrder.value.blockedReason || '未满足整单同步条件'
  }
  return ''
})

const canSyncSelected = computed(() => !syncDisabledReason.value)

const requestErrorMessage = (error, fallback) => error?.response?.data?.message || error?.payload?.message || error?.message || fallback

const buildOrderParams = () => ({
  customerName: filters.customerName || undefined,
  keyword: filters.keyword || undefined,
  status: filters.status || undefined,
  u9SyncStatus: filters.u9SyncStatus || undefined,
  materialReadyStatus: filters.materialReadyStatus || undefined,
  page: orderPagination.page,
  pageSize: orderPagination.pageSize
})

const loadOrders = async () => {
  orderLoading.value = true
  try {
    const response = await listSalesOrderU9SyncOrders(buildOrderParams())
    orders.value = response?.records || []
    orderPagination.total = Number(response?.total || 0)
    orderPagination.page = Number(response?.page || orderPagination.page)
    orderPagination.pageSize = Number(response?.pageSize || orderPagination.pageSize)
    if (!orders.value.some(row => row.salesOrderId === selectedOrderId.value)) {
      selectedOrderId.value = orders.value[0]?.salesOrderId || null
      clearPreviewState()
    }
  } catch (error) {
    orders.value = []
    orderPagination.total = 0
    selectedOrderId.value = null
    clearPreviewState()
    ElMessage.error(requestErrorMessage(error, '销售订单U9同步列表加载失败。'))
  } finally {
    orderLoading.value = false
  }
}

const searchOrders = async () => {
  orderPagination.page = 1
  clearPreviewState()
  await loadOrders()
}

const resetFilters = async () => {
  filters.customerName = ''
  filters.keyword = ''
  filters.status = ''
  filters.u9SyncStatus = ''
  filters.materialReadyStatus = ''
  await searchOrders()
}

const refreshCurrentState = async () => {
  await loadOrders()
  if (selectedOrder.value && previewData.value?.salesOrderId === selectedOrder.value.salesOrderId) {
    await previewSelectedOrder({ silent: true, preserveResult: true })
  }
}

const handleOrderPageSizeChange = async () => {
  orderPagination.page = 1
  clearPreviewState()
  await loadOrders()
}

const handleOrderPageChange = async () => {
  clearPreviewState()
  await loadOrders()
}

const selectOrder = (row) => {
  if (!row?.salesOrderId || selectedOrderId.value === row.salesOrderId) return
  selectedOrderId.value = row.salesOrderId
  clearPreviewState()
}

const previewSelectedOrder = async ({ silent = false, preserveResult = false } = {}) => {
  if (!selectedOrder.value) {
    if (!silent) ElMessage.warning('请先选择销售订单。')
    return
  }
  previewLoading.value = true
  try {
    const response = await previewSalesOrderU9Sync(selectedOrder.value.salesOrderId)
    previewData.value = response || {}
    if (!preserveResult) {
      syncResult.value = null
    }
    if (!silent) {
      if (response?.syncReady) {
        ElMessage.success('U9同步预览已就绪，可以提交整单同步。')
      } else {
        ElMessage.warning(response?.blockedReason || '该销售订单暂不满足整单同步条件。')
      }
    }
  } catch (error) {
    previewData.value = null
    if (!preserveResult) {
      syncResult.value = null
    }
    ElMessage.error(requestErrorMessage(error, 'U9同步预览失败。'))
  } finally {
    previewLoading.value = false
  }
}

const syncSelectedOrder = async () => {
  if (!canSyncSelected.value) {
    ElMessage.warning(syncDisabledReason.value || '该销售订单暂不可同步。')
    return
  }
  return runAction(u9SyncActionKeys.sync, async () => {
    try {
      await ElMessageBox.confirm(
        `确认将销售订单 ${selectedOrder.value.salesOrderNo || ''} 整单同步到 U9？`,
        '确认同步U9',
        {
          type: 'warning',
          confirmButtonText: '同步U9',
          cancelButtonText: '取消'
        }
      )
    } catch {
      return
    }
    try {
      const response = await syncSalesOrderU9(selectedOrder.value.salesOrderId)
      syncResult.value = response || {}
      if (response?.success) {
        ElMessage.success(response?.message || 'U9同步成功。')
      } else {
        ElMessage.warning(response?.message || 'U9同步返回失败，请查看失败明细。')
      }
      await loadOrders()
      await previewSelectedOrder({ silent: true, preserveResult: true })
    } catch (error) {
      ElMessage.error(requestErrorMessage(error, 'U9同步提交失败。'))
    }
  })
}

const clearPreviewState = () => {
  previewData.value = null
  syncResult.value = null
}

const orderRowKey = (row) => row.salesOrderId

function numberOrZero(value) {
  const number = Number(value)
  return Number.isFinite(number) ? number : 0
}

const orderStatusTagType = (status) => {
  if (status === '已生成提单') return 'success'
  if (status === '部分已提单') return 'warning'
  if (status === '已报价') return 'primary'
  if (status === '已废弃') return 'info'
  return 'info'
}

const u9StatusTagType = (status) => {
  if (status === '已同步') return 'success'
  if (status === '同步失败') return 'danger'
  if (status === '同步中') return 'warning'
  return 'info'
}

const materialStatusTagType = (status) => {
  if (status === '已确认') return 'success'
  if (status === '获取失败') return 'danger'
  if (status === '未确认') return 'warning'
  return 'info'
}

const formatDateTime = (value) => {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

const formatDate = (value) => {
  if (!value) return '-'
  return String(value).slice(0, 10)
}

const formatMoney = (value) => {
  if (value === null || value === undefined || value === '') return '-'
  const amount = Number(value)
  if (Number.isNaN(amount)) return String(value)
  return amount.toFixed(2)
}

onMounted(() => {
  loadOrders()
})
</script>

<style scoped>
.sales-order-u9-sync-page {
  padding: 0;
}

.sales-order-u9-sync-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

.sales-order-u9-sync-header-actions {
  flex-wrap: wrap;
  justify-content: flex-end;
}

.sales-order-u9-sync-action-wrap {
  display: inline-flex;
}

.sales-order-u9-sync-toolbar {
  display: grid;
  grid-template-columns: minmax(180px, 220px) minmax(260px, 1fr) 140px 140px 140px auto;
  gap: 12px;
  align-items: end;
  width: 100%;
}

.sales-order-u9-sync-filter {
  display: grid;
  gap: 6px;
  min-width: 0;
  color: var(--erp-text-secondary);
  font-size: 12px;
  font-weight: 600;
}

.sales-order-u9-sync-filter :deep(.el-select),
.sales-order-u9-sync-filter :deep(.el-input) {
  width: 100%;
}

.sales-order-u9-sync-keyword-filter {
  min-width: 0;
}

.sales-order-u9-sync-toolbar-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

.sales-order-u9-sync-summary-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(120px, 1fr));
  gap: 10px;
}

.sales-order-u9-sync-summary-card {
  display: grid;
  gap: 4px;
  min-height: 64px;
  padding: 12px;
  border: 1px solid var(--erp-border-soft);
  border-radius: 8px;
  background: var(--erp-surface-soft);
}

.sales-order-u9-sync-summary-card strong {
  overflow: hidden;
  color: var(--erp-text);
  font-size: 22px;
  line-height: 1.1;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sales-order-u9-sync-summary-card span {
  color: var(--erp-text-secondary);
  font-size: 12px;
}

.sales-order-u9-sync-summary-card.success strong {
  color: var(--erp-success);
}

.sales-order-u9-sync-summary-card.warning strong {
  color: var(--erp-warning);
}

.sales-order-u9-sync-alert {
  margin: 12px 14px 0;
}

.sales-order-u9-sync-section-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 14px 0;
}

.sales-order-u9-sync-section-header h2 {
  margin: 0;
  color: var(--erp-text);
  font-size: 16px;
  line-height: 1.3;
}

.sales-order-u9-sync-section-header p {
  margin: 4px 0 0;
  color: var(--erp-text-secondary);
  font-size: 12px;
}

.erp-table-body {
  padding: 12px 14px;
}

.sales-order-u9-sync-radio-label {
  display: none;
}

.sales-order-u9-sync-preview-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  padding: 12px 14px 0;
  color: var(--erp-text-secondary);
  font-size: 12px;
}

.sales-order-u9-sync-result-tags {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

@media (max-width: 1200px) {
  .sales-order-u9-sync-toolbar {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .sales-order-u9-sync-summary-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .sales-order-u9-sync-header,
  .sales-order-u9-sync-section-header {
    flex-direction: column;
    align-items: stretch;
  }

  .sales-order-u9-sync-header-actions,
  .sales-order-u9-sync-toolbar-actions,
  .sales-order-u9-sync-result-tags {
    justify-content: stretch;
  }

  .sales-order-u9-sync-header-actions :deep(.el-button),
  .sales-order-u9-sync-toolbar-actions :deep(.el-button) {
    flex: 1;
    margin-left: 0;
  }

  .sales-order-u9-sync-toolbar,
  .sales-order-u9-sync-summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
