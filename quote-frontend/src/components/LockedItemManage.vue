<template>
  <div class="erp-page locked-item-page" v-permission="LOCKED_ITEM_VIEW_PERMISSION">
    <header class="erp-page-header">
      <div>
        <h1 class="erp-page-title">锁库零件</h1>
        <p class="erp-page-description">查看 WMS 返回后的库存锁定、在产锁定和待生产拆分结果。</p>
      </div>
    </header>

    <section class="erp-toolbar locked-filter-toolbar">
      <div class="locked-toolbar-main">
        <div class="erp-toolbar-group locked-primary-filters">
          <el-tag v-if="route.query.ticketId" type="info" effect="plain">提单ID：{{ route.query.ticketId }}</el-tag>
          <el-button v-permission="'market:locked-item:customer-select'" class="customer-picker-trigger" @click="customerPickerVisible = true">
            <span
              :class="filters.customerName ? 'customer-picker-text' : 'customer-picker-placeholder'"
              :title="filters.customerName || '选择客户'"
            >
              {{ filters.customerName || '选择客户' }}
            </span>
            <span class="customer-picker-action">选择客户</span>
          </el-button>
          <el-button v-if="filters.customerName" plain @click="handleCustomerClear">清除客户</el-button>
          <el-input v-model="filters.partNo" clearable placeholder="图号" class="filter-input" @keyup.enter="handleSearch" @clear="handleSearch" />
          <el-input v-model="filters.version" clearable placeholder="版本" class="filter-input small" @keyup.enter="handleSearch" @clear="handleSearch" />
          <el-select v-model="filters.allocationType" clearable placeholder="分配类型" class="filter-select allocation-type-select" @change="handleSearch" @clear="handleSearch">
            <el-option v-for="option in allocationTypeOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
          <el-select v-model="filters.mesProductionStatus" clearable placeholder="生产下发状态" class="filter-select production-status-select" @change="handleSearch" @clear="handleSearch">
            <el-option v-for="option in mesProductionStatusOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
          <el-button @click="handleSearch">搜索</el-button>
          <el-button @click="handleResetFilters">重置</el-button>
          <el-button plain @click="advancedFiltersVisible = !advancedFiltersVisible">
            {{ advancedFiltersVisible ? '收起筛选' : '高级筛选' }}
          </el-button>
          <el-button v-if="route.query.ticketId" @click="showAllItems">查看全部</el-button>
        </div>

        <div class="erp-toolbar-group locked-batch-actions">
          <el-button
            v-permission="LOCKED_ITEM_MES_PRODUCTION_SUBMIT_PERMISSION"
            type="primary"
            plain
            :disabled="selectedAllocationIds.length === 0 || isMesProductionDispatchPending"
            :loading="isMesProductionDispatchPending"
            @click="handleMesProductionDispatch"
          >
            下发MES生产
          </el-button>
        </div>
      </div>

      <div v-if="advancedFiltersVisible" class="erp-toolbar-group locked-advanced-filters">
        <el-input v-model="filters.rfqNo" clearable placeholder="来源订单号" class="filter-input" @keyup.enter="handleSearch" @clear="handleSearch" />
        <el-input v-model="filters.materialNo" clearable placeholder="物料号" class="filter-input" @keyup.enter="handleSearch" @clear="handleSearch" />
        <el-input v-model="filters.internalProjectNo" clearable placeholder="内部项目号" class="filter-input" @keyup.enter="handleSearch" @clear="handleSearch" />
        <el-input v-model="filters.customerOrderNo" clearable placeholder="客户订单号" class="filter-input" @keyup.enter="handleSearch" @clear="handleSearch" />
        <el-input v-model="filters.customerProjectNo" clearable placeholder="客户项目号" class="filter-input" @keyup.enter="handleSearch" @clear="handleSearch" />
        <el-date-picker
          v-model="filters.dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="至"
          start-placeholder="交货日期起"
          end-placeholder="交货日期止"
          class="filter-date-range"
          @change="handleSearch"
          @clear="handleSearch"
        />
      </div>
    </section>

    <section v-if="mesProductionResult" class="erp-table-surface mes-production-result-surface">
      <div class="erp-table-header mes-production-result-header">
        <div>
          <h3>MES生产下发结果</h3>
          <p>只下发待生产数量行，非待生产行由后端自动跳过。</p>
        </div>
        <div class="mes-production-result-tags">
          <el-tag type="success">成功：{{ formatMesProductionCount(mesProductionResult, 'successCount') }}</el-tag>
          <el-tag type="danger">失败：{{ formatMesProductionCount(mesProductionResult, 'failedCount') }}</el-tag>
          <el-tag type="info">跳过：{{ formatMesProductionCount(mesProductionResult, 'skippedCount') }}</el-tag>
        </div>
      </div>
      <div v-if="Array.isArray(mesProductionResult.rows) && mesProductionResult.rows.length" class="mes-production-result-rows">
        <div
          v-for="row in mesProductionResult.rows.slice(0, 5)"
          :key="row.allocationId"
          class="mes-production-result-row"
        >
          <span>分配ID：{{ row.allocationId || '-' }}</span>
          <el-tag :type="mesProductionRowTagType(row.status)" size="small">{{ mesProductionRowStatusText(row.status) }}</el-tag>
          <span class="mes-production-result-message">{{ row.message || '-' }}</span>
        </div>
      </div>
    </section>

    <section class="erp-table-surface locked-table-surface">
      <div class="erp-table-body">
        <el-table
          :data="items"
          border
          stripe
          height="600"
          v-loading="loading"
          @selection-change="handleSelectionChange"
        >
          <el-table-column type="selection" width="46" fixed align="center" />
          <el-table-column prop="id" label="分配ID" width="96" fixed align="center" show-overflow-tooltip />
          <el-table-column prop="ticketId" label="提单ID" width="96" fixed align="center" show-overflow-tooltip />
          <el-table-column prop="partNo" label="图号" min-width="150" fixed show-overflow-tooltip />
          <el-table-column prop="version" label="版本" width="80" align="center">
            <template #default="{ row }">{{ formatDash(row.version) }}</template>
          </el-table-column>
          <el-table-column prop="materialNo" label="物料号" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ formatDash(row.materialNo) }}</template>
          </el-table-column>
          <el-table-column prop="itemType" label="零件类型" width="100" align="center" show-overflow-tooltip>
            <template #default="{ row }">{{ formatDash(row.itemType) }}</template>
          </el-table-column>
          <el-table-column label="分配类型" width="150" align="center">
            <template #default="{ row }">
              <el-tag :type="allocationTypeMeta(row.allocationType).type" size="small">
                {{ allocationTypeMeta(row.allocationType).label }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="wmsProductionOrderNo" label="在产订单号" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ formatDash(row.wmsProductionOrderNo) }}</template>
          </el-table-column>
          <el-table-column prop="orderType" label="订单类型" width="110" align="center" show-overflow-tooltip>
            <template #default="{ row }">{{ formatDash(row.orderType) }}</template>
          </el-table-column>
          <el-table-column prop="qty" label="数量" width="96" align="right" />
          <el-table-column prop="customerName" label="客户" min-width="150" show-overflow-tooltip />
          <el-table-column prop="rfqNo" label="来源订单号" min-width="150" show-overflow-tooltip />
          <el-table-column prop="internalProjectNo" label="内部项目号" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ formatDash(row.internalProjectNo) }}</template>
          </el-table-column>
          <el-table-column prop="customerOrderNo" label="客户订单号" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ formatDash(row.customerOrderNo) }}</template>
          </el-table-column>
          <el-table-column prop="customerProjectNo" label="客户项目号" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ formatDash(row.customerProjectNo) }}</template>
          </el-table-column>
          <el-table-column label="材料" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ formatMaterialName(row) }}</template>
          </el-table-column>
          <el-table-column label="材料类型" width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ formatMaterialShape(row.materialData) }}</template>
          </el-table-column>
          <el-table-column prop="materialGrossWeight" label="毛重" width="100" align="right">
            <template #default="{ row }">{{ formatWeight(row.materialGrossWeight) }}</template>
          </el-table-column>
          <el-table-column prop="partNetWeight" label="净重" width="100" align="right">
            <template #default="{ row }">{{ formatWeight(row.partNetWeight) }}</template>
          </el-table-column>
          <el-table-column label="工艺路线" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">{{ formatProcessRoute(row.processRoute) }}</template>
          </el-table-column>
          <el-table-column prop="deliveryDate" label="交货日期" width="120">
            <template #default="{ row }">{{ formatDash(row.deliveryDate) }}</template>
          </el-table-column>
          <el-table-column prop="description" label="材料描述" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ formatDash(row.description) }}</template>
          </el-table-column>
          <el-table-column prop="surfaceTreatment" label="表面处理" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ formatDash(row.surfaceTreatment) }}</template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ formatDash(row.remark) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="120" align="center" fixed="right">
            <template #default="{ row }">
              <el-button
                v-permission="DELIVERED_ITEM_VIEW_PERMISSION"
                size="small"
                type="primary"
                plain
                @click="openTicketDetail(row)"
              >
                查看提单行
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <div class="erp-pagination">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          :pager-count="7"
          :total="total"
          background
          @size-change="page = 1"
        />
      </div>
    </section>

    <el-drawer v-model="ticketDetailVisible" title="原始提单行详情" size="62%">
      <div v-if="selectedTicketDetail" class="detail-wrap">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="提单ID">{{ selectedTicketDetail.id }}</el-descriptions-item>
          <el-descriptions-item label="来源类型">{{ formatDash(selectedTicketDetail.sourceType) }}</el-descriptions-item>
          <el-descriptions-item label="来源订单ID">{{ formatDash(selectedTicketDetail.sourceOrderId) }}</el-descriptions-item>
          <el-descriptions-item label="来源零件ID">{{ formatDash(selectedTicketDetail.sourceItemId) }}</el-descriptions-item>
          <el-descriptions-item label="图号">{{ formatDash(selectedTicketDetail.partNo) }}</el-descriptions-item>
          <el-descriptions-item label="版本">{{ formatDash(selectedTicketDetail.version) }}</el-descriptions-item>
          <el-descriptions-item label="物料号">{{ formatDash(selectedTicketDetail.materialNo) }}</el-descriptions-item>
          <el-descriptions-item label="MES状态">{{ statusText(selectedTicketDetail.mesSyncStatus) }}</el-descriptions-item>
          <el-descriptions-item label="WMS状态">{{ statusText(selectedTicketDetail.wmsAllocateStatus) }}</el-descriptions-item>
          <el-descriptions-item label="客户">{{ formatDash(selectedTicketDetail.customerName) }}</el-descriptions-item>
          <el-descriptions-item label="来源订单号">{{ formatDash(selectedTicketDetail.rfqNo) }}</el-descriptions-item>
          <el-descriptions-item label="内部项目号">{{ formatDash(selectedTicketDetail.internalProjectNo) }}</el-descriptions-item>
          <el-descriptions-item label="客户订单号">{{ formatDash(selectedTicketDetail.customerOrderNo) }}</el-descriptions-item>
          <el-descriptions-item label="客户项目号">{{ formatDash(selectedTicketDetail.customerProjectNo) }}</el-descriptions-item>
          <el-descriptions-item label="材料">{{ formatMaterialName(selectedTicketDetail) }}</el-descriptions-item>
          <el-descriptions-item label="材料类型">{{ formatMaterialShape(selectedTicketDetail.materialData) }}</el-descriptions-item>
          <el-descriptions-item label="毛重">{{ formatWeight(selectedTicketDetail.materialGrossWeight) }}</el-descriptions-item>
          <el-descriptions-item label="净重">{{ formatWeight(selectedTicketDetail.partNetWeight) }}</el-descriptions-item>
        </el-descriptions>

        <section class="detail-section">
          <h3>工艺快照</h3>
          <div class="route-preview">{{ formatProcessRoute(selectedTicketDetail.processRoute) }}</div>
        </section>
      </div>
    </el-drawer>

    <CustomerPickerDialog
      v-model="customerPickerVisible"
      :current-customer="filters.customerName"
      customer-scope="locked-item"
      @select="handleCustomerSelected"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import CustomerPickerDialog from './CustomerPickerDialog.vue'
import {
  getDeliveredItem,
  listLockedItems,
  listTicketAllocations,
  submitMesProductionDispatchBatch
} from '../services/deliveredItemApi'
import { getTask } from '../services/taskApi'
import { usePendingAction } from '../composables/usePendingAction'

const LOCKED_ITEM_VIEW_PERMISSION = 'market:locked-item:view'
const LOCKED_ITEM_MES_PRODUCTION_SUBMIT_PERMISSION = 'market:locked-item:mes-production-submit'
const DELIVERED_ITEM_VIEW_PERMISSION = 'market:delivered-item:view'

const route = useRoute()
const router = useRouter()
const { isPending, runAction } = usePendingAction()

const lockedActionKeys = {
  mesProductionDispatch: () => 'locked-item:mes-production:batch'
}

const filters = reactive({
  customerName: '',
  rfqNo: '',
  partNo: '',
  version: '',
  materialNo: '',
  internalProjectNo: '',
  customerOrderNo: '',
  customerProjectNo: '',
  allocationType: '',
  mesProductionStatus: '',
  dateRange: []
})

const allocationTypeOptions = [
  { value: 'IN_STOCK_LOCK', label: '已入库库存锁定', type: 'success' },
  { value: 'IN_PRODUCTION_LOCK', label: '在产库存锁定', type: 'warning' },
  { value: 'PRODUCTION_REQUIRED', label: '待生产数量', type: 'danger' }
]
const mesProductionStatusOptions = [
  { value: 'PENDING', label: '未处理' },
  { value: 'PROCESSING', label: '处理中' },
  { value: 'SUCCESS', label: '成功' },
  { value: 'FAILED', label: '失败' }
]

const items = ref([])
const selectedRows = ref([])
const selectedAllocationIds = computed(() => selectedRows.value.map(row => row.id).filter(Boolean))
const loading = ref(false)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const customerPickerVisible = ref(false)
const ticketDetailVisible = ref(false)
const selectedTicketDetail = ref(null)
const advancedFiltersVisible = ref(false)
const mesProductionResult = ref(null)

watch([page, pageSize], () => loadItems())
watch(() => route.query.ticketId, () => loadItems())

const buildParams = () => {
  const params = Object.fromEntries(Object.entries(filters)
    .filter(([key, value]) => key !== 'dateRange' && String(value || '').trim()))
  const [dateFrom, dateTo] = filters.dateRange || []
  if (dateFrom) params.dateFrom = dateFrom
  if (dateTo) params.dateTo = dateTo
  return {
    ...params,
    page: page.value,
    pageSize: pageSize.value
  }
}

const loadItems = async () => {
  loading.value = true
  try {
    if (route.query.ticketId) {
      const records = await listTicketAllocations(route.query.ticketId)
      items.value = Array.isArray(records) ? records : []
      total.value = items.value.length
      return
    }
    const response = await listLockedItems(buildParams())
    items.value = response.records || []
    total.value = response.total || 0
  } catch (error) {
    console.error(error)
    ElMessage.error(error.response?.data?.message || error.message || '锁库零件列表加载失败。')
  } finally {
    loading.value = false
  }
}

const handleSearch = async () => {
  page.value = 1
  await loadItems()
}

const handleResetFilters = async () => {
  Object.assign(filters, {
    customerName: '',
    rfqNo: '',
    partNo: '',
    version: '',
    materialNo: '',
    internalProjectNo: '',
    customerOrderNo: '',
    customerProjectNo: '',
    allocationType: '',
    mesProductionStatus: '',
    dateRange: []
  })
  await handleSearch()
}

const resolveCustomerName = (customer) => typeof customer === 'string' ? customer : (customer?.customerName || '')

const handleCustomerSelected = async (customer) => {
  filters.customerName = resolveCustomerName(customer)
  await handleSearch()
}

const handleCustomerClear = async () => {
  filters.customerName = ''
  await handleSearch()
}

const handleSelectionChange = (rows) => {
  selectedRows.value = rows || []
}

const selectedAllocationCountText = () => `${selectedAllocationIds.value.length} 条锁库零件`

const taskIdOf = (task) => task?.id || task?.taskId || task?.task?.id

const isMesProductionDispatchPending = computed(() => isPending(lockedActionKeys.mesProductionDispatch()))

const handleMesProductionDispatch = async () => {
  if (selectedAllocationIds.value.length === 0) {
    return ElMessage.warning('请先选择需要下发 MES 生产的锁库零件。')
  }
  mesProductionResult.value = null
  try {
    await ElMessageBox.confirm(`确认将选中的待生产锁库零件下发给 MES 生产流程？本次已选 ${selectedAllocationCountText()}，非待生产行会自动跳过。`, '下发MES生产', {
      confirmButtonText: '确认提交',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }

  return runAction(lockedActionKeys.mesProductionDispatch(), async () => {
    try {
      const task = await submitMesProductionDispatchBatch(selectedAllocationIds.value)
      const taskId = taskIdOf(task)
      ElMessage.success(taskId ? `MES生产下发任务已提交，任务ID：${taskId}` : 'MES生产下发任务已提交')
      mesProductionResult.value = mesProductionResultOf(task) || await waitMesProductionTaskResult(taskId)
      selectedRows.value = []
      await loadItems()
    } catch (error) {
      console.error(error)
      ElMessage.error(error.response?.data?.message || error.message || 'MES生产下发任务提交失败。')
    }
  })
}

const showAllItems = () => {
  router.push({ path: '/locked-items' })
}

const openTicketDetail = async (row) => {
  if (!row?.ticketId) {
    ElMessage.warning('当前锁库结果缺少提单ID，无法反查原始提单行。')
    return
  }
  try {
    selectedTicketDetail.value = await getDeliveredItem(row.ticketId)
    ticketDetailVisible.value = true
  } catch (error) {
    console.error(error)
    ElMessage.error(error.response?.data?.message || error.message || '原始提单行详情加载失败。')
  }
}

const allocationTypeMeta = (value) => allocationTypeOptions.find(option => option.value === value) || {
  label: value || '-',
  type: 'info'
}

const formatDash = (value) => (value === null || value === undefined || value === '') ? '-' : value

const statusText = (value) => ({
  PENDING: '未处理',
  PROCESSING: '处理中',
  CALLBACK_PENDING: '待MES回调',
  SUCCESS: '成功',
  FAILED: '失败'
}[value] || formatDash(value))

const mesProductionResultOf = (task) => {
  const result = task?.resultJson || task?.result || task?.task?.resultJson
  if (!result) return null
  if (typeof result === 'object') return result
  if (typeof result === 'string') {
    try {
      const parsed = JSON.parse(result)
      return parsed && typeof parsed === 'object' ? parsed : null
    } catch {
      return null
    }
  }
  return null
}

const waitFor = (milliseconds) => new Promise(resolve => globalThis.setTimeout(resolve, milliseconds))

const waitMesProductionTaskResult = async (taskId) => {
  if (!taskId) return null
  const delays = [300, 700, 1000, 1200, 1500]
  for (const delayMs of delays) {
    await waitFor(delayMs)
    try {
      const latestTask = await getTask(taskId)
      const result = mesProductionResultOf(latestTask)
      if (result) return result
      if (['SUCCESS', 'FAILED'].includes(String(latestTask?.status || '').toUpperCase())) {
        return null
      }
    } catch (error) {
      console.warn('MES production dispatch task result polling failed', error)
      return null
    }
  }
  return null
}

const formatMesProductionCount = (result, key) => {
  const value = result?.[key]
  return Number.isFinite(Number(value)) ? Number(value) : 0
}

const mesProductionRowStatusText = (value) => ({
  SUCCESS: '成功',
  FAILED: '失败',
  SKIPPED: '跳过'
}[value] || formatDash(value))

const mesProductionRowTagType = (value) => ({
  SUCCESS: 'success',
  FAILED: 'danger',
  SKIPPED: 'info'
}[value] || 'info')

const normalizeObject = (value) => {
  if (!value) return {}
  if (typeof value === 'object') return value
  if (typeof value === 'string') {
    try {
      const parsed = JSON.parse(value)
      return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {}
    } catch {
      return {}
    }
  }
  return {}
}

const formatMaterialName = (row) => row?.engineeringMaterialName || row?.materialName || '-'

const formatMaterialShape = (materialData) => {
  const data = normalizeObject(materialData)
  return data.shape || data.materialShape || data.materialType || '-'
}

const formatWeight = (value) => {
  if (value === null || value === undefined || value === '') return '-'
  const numeric = Number(value)
  if (Number.isNaN(numeric)) return String(value)
  return numeric.toFixed(3).replace(/\.?0+$/, '')
}

const formatProcessRoute = (route) => {
  if (!route) return '-'
  let rows = Array.isArray(route) ? route : []
  if (typeof route === 'string') {
    try {
      rows = JSON.parse(route)
    } catch {
      rows = []
    }
  }
  if (!Array.isArray(rows) || rows.length === 0) return '-'
  return rows
    .map(row => `${row.processName || row.name || ''}${row.inputVal ? `(${row.inputVal})` : ''}`)
    .filter(Boolean)
    .join(' / ') || '-'
}

onMounted(() => loadItems())
</script>

<style scoped>
.locked-filter-toolbar {
  align-items: stretch;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
  box-sizing: border-box;
}

.locked-toolbar-main {
  display: flex;
  width: 100%;
  gap: 12px;
  row-gap: 8px;
  column-gap: 10px;
  align-items: flex-start;
  justify-content: space-between;
  box-sizing: border-box;
}

.locked-primary-filters {
  flex: 1 1 680px;
  min-width: 0;
  justify-content: flex-start;
  row-gap: 8px;
  column-gap: 10px;
  box-sizing: border-box;
}

.locked-batch-actions {
  flex: 0 0 auto;
  align-self: stretch;
  justify-content: flex-end;
  align-content: flex-start;
  row-gap: 8px;
  column-gap: 8px;
  padding-left: 12px;
  border-left: 1px solid var(--erp-border-soft);
  box-sizing: border-box;
}

.locked-advanced-filters {
  width: 100%;
  padding: 12px;
  border: 1px solid var(--erp-border-soft);
  border-radius: 6px;
  background: var(--erp-surface-soft);
  justify-content: flex-start;
  row-gap: 8px;
  column-gap: 10px;
  box-sizing: border-box;
}

.filter-input {
  width: 150px;
  flex: 0 0 150px;
}

.filter-input.small {
  width: 92px;
  flex-basis: 92px;
}

.filter-select {
  width: 140px;
  flex: 0 0 140px;
}

.allocation-type-select {
  width: 150px;
  flex-basis: 150px;
}

.production-status-select {
  width: 150px;
  flex-basis: 150px;
}

.filter-date-range {
  width: 250px;
  flex: 0 0 250px;
}

.locked-table-surface .erp-table-body {
  padding: var(--erp-space-3);
}

.mes-production-result-surface {
  margin-bottom: 12px;
}

.mes-production-result-header {
  align-items: center;
  gap: 12px;
}

.mes-production-result-header h3 {
  margin: 0 0 4px;
  color: var(--erp-text-primary);
  font-size: var(--erp-font-size-base);
  font-weight: 600;
}

.mes-production-result-header p {
  margin: 0;
  color: var(--erp-text-secondary);
  font-size: var(--erp-font-size-sm);
}

.mes-production-result-tags,
.mes-production-result-rows {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.mes-production-result-tags {
  justify-content: flex-end;
}

.mes-production-result-rows {
  padding: 0 var(--erp-space-3) var(--erp-space-3);
}

.mes-production-result-row {
  display: inline-flex;
  min-height: 32px;
  align-items: center;
  gap: 8px;
  max-width: 100%;
  padding: 6px 10px;
  border: 1px solid var(--erp-border-light);
  border-radius: var(--erp-radius-sm);
  background: var(--erp-bg-soft);
  color: var(--erp-text-secondary);
  font-size: var(--erp-font-size-sm);
}

.mes-production-result-message {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.detail-wrap {
  display: grid;
  gap: var(--erp-space-4);
}

.detail-section h3 {
  margin: 0 0 var(--erp-space-2);
  color: var(--erp-text-primary);
  font-size: var(--erp-font-size-base);
  font-weight: 600;
}

.route-preview {
  min-height: 48px;
  padding: var(--erp-space-3);
  border: 1px solid var(--erp-border-light);
  border-radius: var(--erp-radius-sm);
  background: var(--erp-bg-soft);
  color: var(--erp-text-secondary);
  line-height: 1.6;
  white-space: pre-wrap;
}

@media (max-width: 1200px) {
  .locked-toolbar-main {
    flex-direction: column;
    align-items: stretch;
  }

  .locked-primary-filters,
  .locked-batch-actions {
    justify-content: flex-start;
  }

  .mes-production-result-header,
  .mes-production-result-tags {
    justify-content: flex-start;
  }

  .locked-batch-actions {
    width: 100%;
    padding-left: 0;
    border-left: 0;
  }
}

@media (max-width: 720px) {
  .locked-primary-filters .filter-input,
  .locked-primary-filters .filter-select,
  .locked-advanced-filters .filter-input,
  .locked-advanced-filters .filter-select {
    width: auto;
    min-width: 120px;
    flex: 1 1 140px;
  }

  .filter-date-range {
    width: 100%;
    flex: 1 1 100%;
  }
}
</style>
