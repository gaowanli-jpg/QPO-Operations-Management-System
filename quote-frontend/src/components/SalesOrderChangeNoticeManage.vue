<template>
  <section class="erp-page sales-change-page">
    <header class="erp-page-header">
      <div>
        <h1 class="erp-page-title">销售变更</h1>
        <p class="erp-page-description">查看销售订单变更并通知已经形成下游工单的生产和提库存流程。</p>
      </div>
      <div class="erp-page-actions">
        <el-tag :type="dispatchSetting.dispatchMode === 'AUTO' ? 'success' : 'info'" effect="plain">
          {{ dispatchSetting.dispatchMode === 'AUTO' ? '自动发送' : '手动发送' }}
        </el-tag>
        <span v-if="dispatchSetting.dispatchMode === 'AUTO' && dispatchSetting.autoEnabledAt" class="sales-change-mode-time">
          自动生效：{{ formatDateTime(dispatchSetting.autoEnabledAt) }}
        </span>
        <el-button :icon="Refresh" :loading="loading" @click="loadRows">刷新</el-button>
        <el-button v-if="view === 'ACTIVE'" v-permission="HIDE_PERMISSION" plain type="warning" :disabled="!hideableRows.length" :loading="hiding" @click="hideSelected">
          批量隐藏{{ hideableRows.length ? `（${hideableRows.length}）` : '' }}
        </el-button>
        <el-button v-if="view === 'HIDDEN'" v-permission="RESTORE_PERMISSION" plain type="success" :disabled="!restorableRows.length" :loading="restoring" @click="restoreSelected">
          批量恢复{{ restorableRows.length ? `（${restorableRows.length}）` : '' }}
        </el-button>
        <el-button v-permission="SEND_PERMISSION" type="primary" :icon="Promotion" :disabled="!sendableRows.length" :loading="sending" @click="sendSelected">
          批量发送{{ sendableRows.length ? `（${sendableRows.length}）` : '' }}
        </el-button>
        <el-button v-permission="MODE_PERMISSION" plain @click="toggleMode">
          切换为{{ dispatchSetting.dispatchMode === 'AUTO' ? '手动' : '自动' }}
        </el-button>
      </div>
    </header>

    <div class="sales-change-view-switch">
      <el-radio-group v-model="view" aria-label="销售变更通知视图" @change="switchView">
        <el-radio-button label="ACTIVE">有效通知</el-radio-button>
        <el-radio-button label="HIDDEN">已隐藏</el-radio-button>
        <el-radio-button label="SENT">已发送</el-radio-button>
      </el-radio-group>
      <span class="sales-change-view-hint" v-if="view === 'HIDDEN'">恢复只重新显示；已有积压需手动发送。</span>
    </div>

    <section class="erp-toolbar sales-change-filter-toolbar">
      <CustomerAutocomplete
        v-model="filters.customerName"
        :customer-code="filters.customerCode"
        customer-scope="sales-change"
        permission="change:sales-change:customer-select"
        @select="handleCustomerSelected"
        @clear="clearCustomer"
        @open-picker="customerPickerVisible = true"
      />
      <el-input v-model="filters.salesOrderNo" clearable placeholder="销售订单号" class="filter-input" @keyup.enter="search" @clear="search" />
      <el-input v-model="filters.lineNo" clearable placeholder="零件行号" class="filter-input small" @keyup.enter="search" @clear="search" />
      <el-select v-model="filters.changeType" clearable placeholder="变更类型" class="filter-select" @change="search">
        <el-option label="修改" value="MODIFY" />
        <el-option label="关联废弃" value="DISCARD" />
        <el-option label="关联恢复" value="RESTORE" />
      </el-select>
      <el-select v-model="filters.targetType" clearable placeholder="下游类型" class="filter-select" @change="search">
        <el-option label="生产工单" value="PRODUCTION" />
        <el-option label="提库存工单" value="STOCK_PICKING" />
      </el-select>
      <el-select v-model="filters.status" clearable placeholder="发送状态" class="filter-select" @change="search">
        <el-option label="待发送" value="PENDING" />
        <el-option label="发送中" value="SENDING" />
        <el-option label="已发送" value="SENT" />
        <el-option label="失败" value="FAILED" />
        <el-option label="结果未知" value="RESULT_UNKNOWN" />
      </el-select>
      <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" range-separator="至" clearable class="filter-date-range" @change="search" @clear="search" />
      <el-button type="primary" :icon="Search" @click="search">查询</el-button>
      <el-button @click="resetFilters">重置</el-button>
    </section>

    <section class="erp-table-surface sales-change-table-wrap" v-loading="loading">
      <el-table :data="rows" border stripe height="min(68vh, 720px)" empty-text="暂无销售变更通知" @selection-change="selectedRows = $event">
        <el-table-column type="selection" width="48" align="center" :selectable="isSelectable" />
        <el-table-column prop="noticeId" label="通知编号" min-width="190" show-overflow-tooltip />
        <el-table-column prop="customerName" label="客户" min-width="130" show-overflow-tooltip />
        <el-table-column prop="salesOrderNo" label="销售订单号" min-width="150" show-overflow-tooltip />
        <el-table-column prop="lineNo" label="行号" width="68" align="center" />
        <el-table-column prop="partNo" label="图号" min-width="140" show-overflow-tooltip />
        <el-table-column prop="version" label="版本" width="82" show-overflow-tooltip />
        <el-table-column label="下游类型" width="118" align="center">
          <template #default="{ row }">{{ targetTypeLabel(row.targetType) }}</template>
        </el-table-column>
        <el-table-column label="变更类型" width="98" align="center">
          <template #default="{ row }"><el-tag size="small" :type="changeTypeTag(row.changeType)">{{ changeTypeLabel(row.changeType) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="targetExternalId" label="下游工单号" min-width="150" show-overflow-tooltip />
        <el-table-column prop="reason" label="变更理由" min-width="170" show-overflow-tooltip />
        <el-table-column v-if="view === 'HIDDEN'" prop="hiddenReason" label="隐藏理由" min-width="170" show-overflow-tooltip />
        <el-table-column v-if="view === 'HIDDEN'" label="隐藏时间" width="168"><template #default="{ row }">{{ formatDateTime(row.hiddenAt) }}</template></el-table-column>
        <el-table-column v-if="view === 'HIDDEN'" prop="hiddenByName" label="隐藏操作人" width="120" show-overflow-tooltip />
        <el-table-column label="发送状态" width="104" align="center">
          <template #default="{ row }"><el-tag size="small" :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="创建时间" width="168"><template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template></el-table-column>
        <el-table-column prop="lastError" label="最后错误" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="90" fixed="right" align="center">
          <template #default="{ row }"><el-button link type="primary" @click="showDetail(row)">详情</el-button></template>
        </el-table-column>
      </el-table>
      <div class="erp-pagination">
        <el-pagination v-model:current-page="pagination.page" v-model:page-size="pagination.pageSize" :page-sizes="[20, 50, 100]" :total="pagination.total" layout="total, sizes, prev, pager, next, jumper" background :pager-count="7" @size-change="pagination.page = 1" @change="loadRows" />
      </div>
    </section>

    <el-drawer v-model="detailVisible" title="销售变更详情" size="min(680px, 94vw)">
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="通知编号">{{ detail.noticeId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="发送状态">{{ statusLabel(detail.status) }}</el-descriptions-item>
          <el-descriptions-item label="销售订单号">{{ detail.salesOrderNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="零件行号">{{ detail.lineNo ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="客户">{{ detail.customerName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="图号 / 版本">{{ [detail.partNo, detail.version].filter(Boolean).join(' / ') || '-' }}</el-descriptions-item>
          <el-descriptions-item label="下游类型">{{ targetTypeLabel(detail.targetType) }}</el-descriptions-item>
          <el-descriptions-item label="下游工单号">{{ detail.targetExternalId || detail.targetKey || '-' }}</el-descriptions-item>
          <el-descriptions-item label="来源批次">{{ detail.sourceBizId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="Allocation">{{ detail.allocationId ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="变更类型">{{ changeTypeLabel(detail.changeType) }}</el-descriptions-item>
          <el-descriptions-item label="变更理由" :span="2">{{ detail.reason || '-' }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.hidden" label="隐藏理由" :span="2">{{ detail.hiddenReason || '-' }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.hiddenAt" label="隐藏时间">{{ formatDateTime(detail.hiddenAt) }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.hiddenByName" label="隐藏操作人">{{ detail.hiddenByName }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.restoredAt" label="最近恢复时间">{{ formatDateTime(detail.restoredAt) }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.restoredBy" label="恢复操作人 ID">{{ detail.restoredBy }}</el-descriptions-item>
          <el-descriptions-item label="最后错误" :span="2">{{ detail.lastError || '-' }}</el-descriptions-item>
        </el-descriptions>
        <h2 class="detail-heading">变更字段</h2>
        <el-tag v-for="field in detail.changedFields || []" :key="field" class="field-tag" effect="plain">{{ field }}</el-tag>
        <h2 class="detail-heading">发送快照</h2>
        <pre class="snapshot-view">{{ formatSnapshot(detail.snapshot) }}</pre>
      </template>
    </el-drawer>

    <CustomerPickerDialog v-model="customerPickerVisible" :current-customer="filters.customerName" :current-customer-code="filters.customerCode" customer-scope="sales-change" @select="handleCustomerSelected" />
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Promotion, Refresh, Search } from '@element-plus/icons-vue'
import CustomerAutocomplete from './CustomerAutocomplete.vue'
import CustomerPickerDialog from './CustomerPickerDialog.vue'
import { getSalesOrderChangeDispatchSetting, getSalesOrderChangeNotice, hideSalesOrderChangeNotices, listSalesOrderChangeNotices, restoreSalesOrderChangeNotices, sendSalesOrderChangeNotices, updateSalesOrderChangeDispatchMode } from '../services/salesOrderChangeApi'
import { canHideSalesChangeNotice, canRestoreSalesChangeNotice, canSelectSalesChangeNotice } from '../services/salesOrderChangePolicy'

const SEND_PERMISSION = 'change:sales-change:send'
const MODE_PERMISSION = 'change:sales-change:mode-update'
const HIDE_PERMISSION = 'change:sales-change:hide'
const RESTORE_PERMISSION = 'change:sales-change:restore'
const loading = ref(false)
const sending = ref(false)
const hiding = ref(false)
const restoring = ref(false)
const rows = ref([])
const selectedRows = ref([])
const detail = ref(null)
const detailVisible = ref(false)
const customerPickerVisible = ref(false)
const dateRange = ref([])
const view = ref('ACTIVE')
const dispatchSetting = reactive({ dispatchMode: 'MANUAL', autoDispatchEpoch: 0, autoEnabledAt: null })
const filters = reactive({ customerId: undefined, customerCode: '', customerName: '', salesOrderNo: '', lineNo: '', changeType: '', targetType: '', status: '' })
const pagination = reactive({ page: 1, pageSize: 20, total: 0 })

const queryParams = () => ({ customerId: filters.customerId, salesOrderNo: filters.salesOrderNo.trim() || undefined, lineNo: filters.lineNo ? Number(filters.lineNo) : undefined, changeType: filters.changeType || undefined, targetType: filters.targetType || undefined, status: filters.status || undefined, dateFrom: dateRange.value?.[0] || undefined, dateTo: dateRange.value?.[1] || undefined, view: view.value, page: pagination.page, pageSize: pagination.pageSize })
const loadRows = async () => {
  if (loading.value) return
  loading.value = true
  try {
    const result = await listSalesOrderChangeNotices(queryParams())
    rows.value = result?.records || []
    pagination.total = Number(result?.total || 0)
  } catch (error) {
    ElMessage.error(error?.message || '销售变更加载失败')
  } finally { loading.value = false }
}
const loadSetting = async () => {
  try { Object.assign(dispatchSetting, await getSalesOrderChangeDispatchSetting()) } catch (error) { ElMessage.error(error?.message || '发送模式加载失败') }
}
const search = () => { pagination.page = 1; loadRows() }
const switchView = () => { selectedRows.value = []; pagination.page = 1; loadRows() }
const resetFilters = () => { Object.assign(filters, { customerId: undefined, customerCode: '', customerName: '', salesOrderNo: '', lineNo: '', changeType: '', targetType: '', status: '' }); dateRange.value = []; search() }
const handleCustomerSelected = customer => { filters.customerId = customer?.id || undefined; filters.customerCode = customer?.customerCode || ''; filters.customerName = customer?.customerName || customer?.customerShortName || filters.customerCode; search() }
const clearCustomer = () => { filters.customerId = undefined; filters.customerCode = ''; filters.customerName = ''; search() }
const isSelectable = row => {
  if (view.value === 'HIDDEN') return canRestoreSalesChangeNotice(row)
  if (view.value === 'SENT') return false
  return canHideSalesChangeNotice(row) || canSelectSalesChangeNotice(row, dispatchSetting.dispatchMode, dispatchSetting.autoDispatchEpoch)
}
const sendableRows = computed(() => view.value === 'ACTIVE' ? selectedRows.value.filter(row => canSelectSalesChangeNotice(row, dispatchSetting.dispatchMode, dispatchSetting.autoDispatchEpoch)) : [])
const hideableRows = computed(() => view.value === 'ACTIVE' ? selectedRows.value.filter(canHideSalesChangeNotice) : [])
const restorableRows = computed(() => view.value === 'HIDDEN' ? selectedRows.value.filter(canRestoreSalesChangeNotice) : [])
const sendSelected = async () => {
  const ids = sendableRows.value.map(row => row.noticeId)
  if (!ids.length || sending.value) return
  try {
    await ElMessageBox.confirm(`确认发送选中的 ${ids.length} 条销售变更通知？`, '批量发送确认', { type: 'warning' })
    sending.value = true
    await sendSalesOrderChangeNotices(ids)
    ElMessage.success('已提交异步发送任务')
    selectedRows.value = []
    await loadRows()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.message || '销售变更发送失败')
  } finally { sending.value = false }
}
const hideSelected = async () => {
  const ids = hideableRows.value.map(row => row.noticeId)
  if (!ids.length || hiding.value) return
  try {
    const { value } = await ElMessageBox.prompt(`将隐藏选中的 ${ids.length} 条通知，不会修改销售订单或下游工单。请输入隐藏理由。`, '批量隐藏销售变更通知', {
      inputType: 'textarea',
      inputAttrs: { maxlength: 500, rows: 3 },
      inputValidator: value => {
        const length = String(value || '').trim().length
        return length > 0 && length <= 500
      },
      inputErrorMessage: '请输入 1 至 500 个字符的理由',
      confirmButtonText: '隐藏通知',
      cancelButtonText: '取消',
      type: 'warning'
    })
    hiding.value = true
    await hideSalesOrderChangeNotices(ids, value.trim())
    ElMessage.success(`已隐藏 ${ids.length} 条通知`)
    selectedRows.value = []
    await loadRows()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.message || '隐藏销售变更通知失败')
  } finally { hiding.value = false }
}
const restoreSelected = async () => {
  const ids = restorableRows.value.map(row => row.noticeId)
  if (!ids.length || restoring.value) return
  try {
    await ElMessageBox.confirm(`确认恢复选中的 ${ids.length} 条通知？恢复只重新显示，不会自动发送。`, '批量恢复销售变更通知', { type: 'warning', confirmButtonText: '恢复通知', cancelButtonText: '取消' })
    restoring.value = true
    await restoreSalesOrderChangeNotices(ids)
    ElMessage.success(`已恢复 ${ids.length} 条通知；需要发送时请手动提交`)
    selectedRows.value = []
    await loadRows()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.message || '恢复销售变更通知失败')
  } finally { restoring.value = false }
}
const toggleMode = async () => {
  const nextMode = dispatchSetting.dispatchMode === 'AUTO' ? 'MANUAL' : 'AUTO'
  try {
    await ElMessageBox.confirm(nextMode === 'AUTO' ? '切换后只自动发送切换之后新产生的通知；已有待发送积压不会自动发送，但仍可在列表中手动勾选发送。继续吗？' : '切换为手动发送后，新通知需要人工批量发送。继续吗？', '发送模式确认', { type: 'warning' })
    Object.assign(dispatchSetting, await updateSalesOrderChangeDispatchMode(nextMode))
    selectedRows.value = []
    await loadRows()
    ElMessage.success('发送模式已更新')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.message || '发送模式更新失败')
  }
}
const showDetail = async row => { try { detail.value = await getSalesOrderChangeNotice(row.noticeId); detailVisible.value = true } catch (error) { ElMessage.error(error?.message || '详情加载失败') } }
const formatSnapshot = snapshot => snapshot ? JSON.stringify(snapshot, null, 2) : '-'
const formatDateTime = value => value ? String(value).replace('T', ' ').slice(0, 19) : '-'
const targetTypeLabel = value => value === 'PRODUCTION' ? '生产工单' : value === 'STOCK_PICKING' ? '提库存工单' : value || '-'
const changeTypeLabel = value => value === 'MODIFY' ? '修改' : value === 'DISCARD' ? '关联废弃' : value === 'RESTORE' ? '关联恢复' : value || '-'
const changeTypeTag = value => value === 'DISCARD' ? 'danger' : value === 'RESTORE' ? 'success' : 'warning'
const statusLabel = value => ({ PENDING: '待发送', SENDING: '发送中', SENT: '已发送', FAILED: '失败', RESULT_UNKNOWN: '结果未知' })[value] || value || '-'
const statusTag = value => value === 'SENT' ? 'success' : value === 'FAILED' ? 'danger' : value === 'RESULT_UNKNOWN' ? 'warning' : value === 'SENDING' ? 'primary' : 'info'

onMounted(async () => { await Promise.all([loadSetting(), loadRows()]) })
</script>

<style scoped>
.sales-change-page { gap: var(--erp-space-4); }
.sales-change-view-switch { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; }
.sales-change-view-hint { color: var(--el-text-color-secondary); font-size: 12px; }
.sales-change-mode-time { color: var(--el-text-color-secondary); font-size: 12px; }
.sales-change-filter-toolbar { align-items: center; flex-wrap: wrap; gap: 8px; padding: 12px; }
.filter-input { width: 160px; }
.filter-input.small { width: 100px; }
.filter-select { width: 130px; }
.filter-date-range { width: 250px; }
.sales-change-table-wrap { padding: var(--erp-space-4); }
.erp-pagination { display: flex; justify-content: flex-end; margin-top: var(--erp-space-3); }
.detail-heading { margin: var(--erp-space-5) 0 var(--erp-space-3); font-size: var(--erp-font-size-md); }
.field-tag { margin: 0 8px 8px 0; }
.snapshot-view { max-height: 420px; overflow: auto; padding: 12px; border-radius: 6px; background: var(--erp-surface-soft); color: var(--erp-text-primary); font-size: 12px; white-space: pre-wrap; word-break: break-word; }
@media (max-width: 900px) { .sales-change-filter-toolbar > * { flex: 1 1 150px; width: auto; } .filter-date-range { min-width: 260px; } }
</style>
