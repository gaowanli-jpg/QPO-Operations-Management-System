<template>
  <div class="erp-page delivered-item-page" v-permission="DELIVERED_ITEM_VIEW_PERMISSION">
    <header class="erp-page-header">
      <div>
        <h1 class="erp-page-title">已提零件</h1>
        <p class="erp-page-description">仅展示尚未完成物料号获取和锁库流程的提单零件；完成锁库后的结果请在锁库零件模块查看。</p>
      </div>
    </header>

    <section class="erp-toolbar delivered-filter-toolbar">
      <div class="erp-toolbar-group delivered-filter-group">
        <el-button v-permission="'market:delivered-item:customer-select'" class="customer-picker-trigger" @click="customerPickerVisible = true">
          <span
            :class="filters.customerName ? 'customer-picker-text' : 'customer-picker-placeholder'"
            :title="filters.customerName || '选择客户'"
          >
            {{ filters.customerName || '选择客户' }}
          </span>
          <span class="customer-picker-action">选择客户</span>
        </el-button>
        <el-button v-if="filters.customerName" plain @click="handleCustomerClear">清除客户</el-button>
        <el-input v-model="filters.rfqNo" clearable placeholder="RFQ号" class="filter-input" @keyup.enter="handleSearch" @clear="handleSearch" />
        <el-input v-model="filters.partNo" clearable placeholder="图号" class="filter-input" @keyup.enter="handleSearch" @clear="handleSearch" />
        <el-input v-model="filters.version" clearable placeholder="版本" class="filter-input small" @keyup.enter="handleSearch" @clear="handleSearch" />
        <el-input v-model="filters.internalProjectNo" clearable placeholder="内部项目号" class="filter-input" @keyup.enter="handleSearch" @clear="handleSearch" />
        <el-input v-model="filters.customerOrderNo" clearable placeholder="客户订单号" class="filter-input" @keyup.enter="handleSearch" @clear="handleSearch" />
        <el-input v-model="filters.customerProjectNo" clearable placeholder="客户项目号" class="filter-input" @keyup.enter="handleSearch" @clear="handleSearch" />
        <el-select v-model="filters.mesSyncStatus" clearable placeholder="MES状态" class="filter-select" @change="handleSearch" @clear="handleSearch">
          <el-option v-for="option in syncStatusOptions" :key="option.value" :label="option.label" :value="option.value" />
        </el-select>
        <el-select v-model="filters.wmsAllocateStatus" clearable placeholder="WMS状态" class="filter-select" @change="handleSearch" @clear="handleSearch">
          <el-option v-for="option in syncStatusOptions" :key="option.value" :label="option.label" :value="option.value" />
        </el-select>
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
        <el-button @click="handleSearch">搜索</el-button>
        <el-button type="primary" :loading="loading" @click="loadItems">刷新</el-button>
        <el-button
          v-permission="DELIVERED_ITEM_MES_SYNC_PERMISSION"
          type="success"
          plain
          :disabled="selectedTicketIds.length === 0 || isBatchMesSyncPending"
          :loading="isBatchMesSyncPending"
          @click="handleBatchMesSync"
        >
          批量获取物料号
        </el-button>
        <el-button
          v-permission="DELIVERED_ITEM_WMS_ALLOCATE_PERMISSION"
          type="primary"
          plain
          :disabled="selectedTicketIds.length === 0 || isBatchWmsAllocationPending"
          :loading="isBatchWmsAllocationPending"
          @click="handleBatchWmsAllocation"
        >
          批量获取库存数据
        </el-button>
      </div>
    </section>

    <section v-if="mesBatchResult" class="erp-table-surface mes-batch-result-surface">
      <div class="erp-table-header mes-batch-result-header">
        <div>
          <h3>MES批量获取结果</h3>
          <p>已按同图号版本去重，未重复发送 MES 的行会继续等待主请求回调。</p>
        </div>
        <div class="mes-batch-result-tags">
          <el-tag type="warning">关联待回调：{{ formatMesBatchCount(mesBatchResult, 'dedupFollowerCount') }}</el-tag>
          <el-tag type="success">复用已确认物料号：{{ formatMesBatchCount(mesBatchResult, 'reusedMaterialCount') }}</el-tag>
          <el-tag type="primary">实际提交MES：{{ formatMesBatchCount(mesBatchResult, 'submittedToMesCount') }}</el-tag>
        </div>
      </div>
    </section>

    <section class="erp-table-surface delivered-table-surface">
      <div class="erp-table-body">
        <el-table
          :data="items"
          border
          stripe
          height="600"
          v-loading="loading"
          empty-text="暂无未完成流程的已提零件，完成锁库后的结果请在锁库零件模块查看。"
          @selection-change="handleSelectionChange"
        >
          <el-table-column type="selection" width="46" fixed align="center" />
          <el-table-column prop="id" label="提单ID" width="96" fixed align="center" show-overflow-tooltip />
          <el-table-column prop="partNo" label="图号" min-width="150" fixed show-overflow-tooltip />
          <el-table-column prop="version" label="版本" width="80" align="center">
            <template #default="{ row }">{{ row.version || '-' }}</template>
          </el-table-column>
          <el-table-column label="物料号" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ formatMaterialNo(row.materialNo) }}</template>
          </el-table-column>
          <el-table-column prop="itemType" label="零件类型" width="100" align="center" show-overflow-tooltip>
            <template #default="{ row }">{{ row.itemType || '-' }}</template>
          </el-table-column>
          <el-table-column label="MES状态" width="110" align="center">
            <template #default="{ row }">
              <div class="mes-status-cell">
                <el-tag :type="statusTagType(row.mesSyncStatus)" size="small">{{ statusText(row.mesSyncStatus) }}</el-tag>
                <el-tooltip v-if="dedupFollowerNote(row)" :content="dedupFollowerNote(row)" placement="top">
                  <el-tag type="warning" effect="plain" size="small">关联待回调</el-tag>
                </el-tooltip>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="WMS状态" width="110" align="center">
            <template #default="{ row }"><el-tag :type="statusTagType(row.wmsAllocateStatus)" size="small">{{ statusText(row.wmsAllocateStatus) }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="orderType" label="订单类型" width="110" align="center" show-overflow-tooltip>
            <template #default="{ row }">{{ row.orderType || '-' }}</template>
          </el-table-column>
          <el-table-column prop="qty" label="提单数量" width="100" align="right" />
          <el-table-column prop="customerName" label="客户" min-width="150" show-overflow-tooltip />
          <el-table-column prop="rfqNo" label="RFQ号" min-width="150" show-overflow-tooltip />
          <el-table-column prop="internalProjectNo" label="内部项目号" min-width="140" show-overflow-tooltip />
          <el-table-column prop="customerOrderNo" label="客户订单号" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ row.customerOrderNo || '-' }}</template>
          </el-table-column>
          <el-table-column prop="customerProjectNo" label="客户项目号" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ row.customerProjectNo || '-' }}</template>
          </el-table-column>
          <el-table-column label="材料" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ formatMaterialName(row) }}</template>
          </el-table-column>
          <el-table-column label="材料类型" width="110" align="center">
            <template #default="{ row }">{{ formatMaterialShape(row.materialData) }}</template>
          </el-table-column>
          <el-table-column label="毛重" width="100" align="right">
            <template #default="{ row }">{{ formatWeight(row.materialGrossWeight) }}</template>
          </el-table-column>
          <el-table-column label="净重" width="100" align="right">
            <template #default="{ row }">{{ formatWeight(row.partNetWeight) }}</template>
          </el-table-column>
          <el-table-column label="工艺路线" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">{{ formatProcessRoute(row.processRoute) }}</template>
          </el-table-column>
          <el-table-column prop="deliveryDate" label="交货日期" width="120" />
          <el-table-column prop="description" label="材料描述" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ row.description || '-' }}</template>
          </el-table-column>
          <el-table-column prop="surfaceTreatment" label="表面处理" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ row.surfaceTreatment || '-' }}</template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ row.remark || '-' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="220" align="center" fixed="right">
            <template #default="{ row }">
              <div class="erp-table-actions delivered-row-actions">
                <el-button size="small" @click="openDetail(row)">详情</el-button>
                <el-button
                  v-if="row.wmsAllocateStatus === 'SUCCESS'"
                  v-permission="LOCKED_ITEM_VIEW_PERMISSION"
                  size="small"
                  type="primary"
                  plain
                  @click="openLockedItems(row)"
                >
                  查看锁库
                </el-button>
                <el-button
                  v-if="row.drawingUrl"
                  v-permission="DELIVERED_ITEM_FILE_PREVIEW_PERMISSION"
                  size="small"
                  type="primary"
                  plain
                  @click="openDrawingPreview(row)"
                >
                  预览图纸
                </el-button>
                <el-tag v-else type="info" size="small">无图纸</el-tag>
              </div>
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

    <el-drawer v-model="detailVisible" title="已提零件详情" size="62%">
      <div v-if="selectedItem" class="detail-wrap">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="提单ID">{{ selectedItem.id }}</el-descriptions-item>
          <el-descriptions-item label="图号">{{ selectedItem.partNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="版本">{{ selectedItem.version || '-' }}</el-descriptions-item>
          <el-descriptions-item label="物料号">{{ formatMaterialNo(selectedItem.materialNo) }}</el-descriptions-item>
          <el-descriptions-item label="零件类型">{{ selectedItem.itemType || '-' }}</el-descriptions-item>
          <el-descriptions-item label="MES状态">{{ statusText(selectedItem.mesSyncStatus) }}</el-descriptions-item>
          <el-descriptions-item label="MES请求ID">{{ selectedItem.mesRequestId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="MES任务号">{{ selectedItem.mesExternalTaskId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="回调时间">{{ formatDateTime(selectedItem.mesCallbackReceivedAt) }}</el-descriptions-item>
          <el-descriptions-item label="状态摘要">{{ callbackStatusSummary(selectedItem) }}</el-descriptions-item>
          <el-descriptions-item label="MES请求角色">{{ mesRequestRoleText(selectedItem.mesRequestRole) }}</el-descriptions-item>
          <el-descriptions-item label="主提单ID">{{ selectedItem.mesMasterTicketId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="主请求ID">{{ selectedItem.mesMasterRequestId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="去重键">{{ selectedItem.mesDedupKey || '-' }}</el-descriptions-item>
          <el-descriptions-item label="WMS状态">{{ statusText(selectedItem.wmsAllocateStatus) }}</el-descriptions-item>
          <el-descriptions-item label="提单数量">{{ selectedItem.qty ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="客户">{{ selectedItem.customerName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="RFQ号">{{ selectedItem.rfqNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="内部项目号">{{ selectedItem.internalProjectNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="客户订单号">{{ selectedItem.customerOrderNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="客户项目号">{{ selectedItem.customerProjectNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="材料">{{ formatMaterialName(selectedItem) }}</el-descriptions-item>
          <el-descriptions-item label="材料类型">{{ formatMaterialShape(selectedItem.materialData) }}</el-descriptions-item>
          <el-descriptions-item label="毛重">{{ formatWeight(selectedItem.materialGrossWeight) }}</el-descriptions-item>
          <el-descriptions-item label="净重">{{ formatWeight(selectedItem.partNetWeight) }}</el-descriptions-item>
          <el-descriptions-item label="交货日期">{{ selectedItem.deliveryDate || '-' }}</el-descriptions-item>
          <el-descriptions-item label="材料描述">{{ selectedItem.description || '-' }}</el-descriptions-item>
          <el-descriptions-item label="表面处理">{{ selectedItem.surfaceTreatment || '-' }}</el-descriptions-item>
          <el-descriptions-item label="备注">{{ selectedItem.remark || '-' }}</el-descriptions-item>
        </el-descriptions>

        <el-alert
          v-if="dedupFollowerNote(selectedItem)"
          class="mes-dedup-alert"
          type="warning"
          :closable="false"
          show-icon
          :title="dedupFollowerNote(selectedItem)"
        />

        <section class="detail-section">
          <h3>工艺快照</h3>
          <div class="route-preview">{{ formatProcessRoute(selectedItem.processRoute) }}</div>
        </section>
      </div>
    </el-drawer>

    <CustomerPickerDialog
      v-model="customerPickerVisible"
      :current-customer="filters.customerName"
      customer-scope="delivered-item"
      @select="handleCustomerSelected"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { openAuthenticatedFileInNewWindow } from '../api'
import CustomerPickerDialog from './CustomerPickerDialog.vue'
import {
  getDeliveredItem,
  listDeliveredItems,
  submitWmsAllocationBatch,
  syncMesMaterialBatch
} from '../services/deliveredItemApi'
import { getTask } from '../services/taskApi'
import { usePendingAction } from '../composables/usePendingAction'

const router = useRouter()
const { isPending, runAction } = usePendingAction()

const DELIVERED_ITEM_VIEW_PERMISSION = 'market:delivered-item:view'
const DELIVERED_ITEM_FILE_PREVIEW_PERMISSION = 'market:delivered-item:file-preview'
const DELIVERED_ITEM_MES_SYNC_PERMISSION = 'market:delivered-item:mes-sync'
const DELIVERED_ITEM_WMS_ALLOCATE_PERMISSION = 'market:delivered-item:wms-allocate'
const LOCKED_ITEM_VIEW_PERMISSION = 'market:locked-item:view'

const deliveredActionKeys = {
  batchMesSync: () => 'delivery:mes-sync:batch',
  batchWmsAllocation: () => 'delivery:wms-allocation:batch'
}

const filters = reactive({
  customerName: '',
  rfqNo: '',
  partNo: '',
  version: '',
  internalProjectNo: '',
  customerOrderNo: '',
  customerProjectNo: '',
  mesSyncStatus: '',
  wmsAllocateStatus: '',
  dateRange: []
})
const syncStatusOptions = [
  { value: 'PENDING', label: '未处理' },
  { value: 'PROCESSING', label: '处理中' },
  { value: 'CALLBACK_PENDING', label: '待MES回调' },
  { value: 'SUCCESS', label: '成功' },
  { value: 'FAILED', label: '失败' }
]
const items = ref([])
const selectedRows = ref([])
const selectedTicketIds = computed(() => selectedRows.value.map(row => row.id).filter(Boolean))
const loading = ref(false)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const detailVisible = ref(false)
const selectedItem = ref(null)
const customerPickerVisible = ref(false)
const mesBatchResult = ref(null)

watch([page, pageSize], () => loadItems())

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
    const response = await listDeliveredItems(buildParams())
    items.value = response.records || []
    total.value = response.total || 0
  } catch (error) {
    console.error(error)
    ElMessage.error(error.response?.data?.message || error.message || '已提零件列表加载失败。')
  } finally {
    loading.value = false
  }
}

const handleSearch = async () => {
  page.value = 1
  await loadItems()
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

const selectedCountText = () => `${selectedTicketIds.value.length} 条已提零件`

const taskIdOf = (task) => task?.id || task?.taskId || task?.task?.id

const openDetail = async (row) => {
  try {
    selectedItem.value = await getDeliveredItem(row.id)
    detailVisible.value = true
  } catch (error) {
    console.error(error)
    ElMessage.error(error.response?.data?.message || error.message || '已提零件详情加载失败。')
  }
}

const openDrawingPreview = async (row) => {
  if (!row?.drawingUrl) {
    return ElMessage.warning('当前零件没有绑定图纸。')
  }
  const result = await openAuthenticatedFileInNewWindow(row.drawingUrl)
  if (!result.success) {
    ElMessage.warning(result.blocked ? '浏览器拦截了新窗口，请允许弹窗后重试。' : (result.message || '图纸打开失败。'))
  }
}

const isBatchMesSyncPending = computed(() => isPending(deliveredActionKeys.batchMesSync()))

const isBatchWmsAllocationPending = computed(() => isPending(deliveredActionKeys.batchWmsAllocation()))

const handleBatchMesSync = async () => {
  if (selectedTicketIds.value.length === 0) {
    return ElMessage.warning('请先选择需要获取物料号的已提零件。')
  }
  mesBatchResult.value = null
  try {
    await ElMessageBox.confirm(`确认批量向 MES 获取 ${selectedCountText()} 的物料号？已成功、处理中、待MES回调的行会自动跳过。`, '批量获取MES物料号', {
      confirmButtonText: '确认提交',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }

  return runAction(deliveredActionKeys.batchMesSync(), async () => {
    try {
      const task = await syncMesMaterialBatch(selectedTicketIds.value)
      const taskId = taskIdOf(task)
      ElMessage.success(taskId ? `批量 MES 物料号任务已提交，任务ID：${taskId}` : '批量 MES 物料号任务已提交')
      mesBatchResult.value = mesBatchResultOf(task) || await waitMesBatchTaskResult(taskId)
      selectedRows.value = []
      await loadItems()
    } catch (error) {
      console.error(error)
      ElMessage.error(error.response?.data?.message || error.message || '批量 MES 物料号任务提交失败。')
    }
  })
}

const handleBatchWmsAllocation = async () => {
  if (selectedTicketIds.value.length === 0) {
    return ElMessage.warning('请先选择需要获取库存数据的已提零件。')
  }
  try {
    await ElMessageBox.confirm(`确认批量向 WMS 获取 ${selectedCountText()} 的库存数据？缺少物料号、已成功或处理中的行会自动跳过。`, '批量获取WMS库存数据', {
      confirmButtonText: '确认提交',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }

  return runAction(deliveredActionKeys.batchWmsAllocation(), async () => {
    try {
      const task = await submitWmsAllocationBatch(selectedTicketIds.value)
      const taskId = taskIdOf(task)
      ElMessage.success(taskId ? `锁库完成，结果已进入锁库零件模块，任务ID：${taskId}` : '锁库完成，结果已进入锁库零件模块')
      selectedRows.value = []
      await loadItems()
    } catch (error) {
      console.error(error)
      ElMessage.error(error.response?.data?.message || error.message || '批量 WMS 库存任务提交失败。')
    }
  })
}

const openLockedItems = (row) => {
  router.push({ path: '/locked-items', query: { ticketId: row.id } })
}


const formatMaterialNo = (value) => value || '未获取'

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

const statusText = (value) => syncStatusOptions.find(option => option.value === value)?.label || '未处理'

const statusTagType = (value) => ({
  SUCCESS: 'success',
  FAILED: 'danger',
  PROCESSING: 'warning',
  CALLBACK_PENDING: 'warning',
  PENDING: 'info'
}[value] || 'info')

const formatDateTime = (value) => {
  if (!value) return '-'
  return String(value).replace('T', ' ').replace(/\.\d+$/, '').replace(/Z$/, '')
}

const callbackStatusSummary = (selectedItem) => {
  if (!selectedItem) return '-'
  const message = selectedItem.mesCallbackMessage || selectedItem.mesSyncMessage
  return message ? `${statusText(selectedItem.mesSyncStatus)}：${message}` : statusText(selectedItem.mesSyncStatus)
}

const mesRequestRoleText = (role) => ({
  MASTER: '主请求',
  FOLLOWER: '关联等待',
  REUSED: '复用成功'
}[role] || '-')

const dedupFollowerNote = (item) => {
  if (!item || item.mesRequestRole !== 'FOLLOWER') return ''
  return '已关联同图号版本主请求，未重复发送MES'
}

const mesBatchResultOf = (task) => {
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

const waitMesBatchTaskResult = async (taskId) => {
  if (!taskId) return null
  const delays = [300, 700, 1000, 1200, 1500]
  for (const delayMs of delays) {
    await waitFor(delayMs)
    try {
      const latestTask = await getTask(taskId)
      const result = mesBatchResultOf(latestTask)
      if (result) return result
      if (['SUCCESS', 'FAILED'].includes(String(latestTask?.status || '').toUpperCase())) {
        return null
      }
    } catch (error) {
      console.warn('MES batch task result polling failed', error)
      return null
    }
  }
  return null
}

const formatMesBatchCount = (result, key) => {
  const value = result?.[key]
  return Number.isFinite(Number(value)) ? Number(value) : 0
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
.delivered-filter-group {
  width: 100%;
  justify-content: flex-end;
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
  width: 130px;
  flex: 0 0 130px;
}

.filter-date-range {
  width: 250px;
  flex: 0 0 250px;
}

.delivered-table-surface .erp-table-body {
  padding: var(--erp-space-3);
}

.delivered-row-actions {
  justify-content: center;
}

.delivered-row-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.mes-batch-result-surface {
  margin-bottom: 12px;
}

.mes-batch-result-header {
  align-items: center;
  gap: 12px;
}

.mes-batch-result-header h3 {
  margin: 0 0 4px;
  font-size: 15px;
  color: var(--erp-text);
}

.mes-batch-result-header p {
  margin: 0;
  color: var(--erp-text-secondary);
  font-size: 13px;
}

.mes-batch-result-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

.mes-status-cell {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.detail-wrap {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-section h3 {
  margin: 0 0 8px;
  color: var(--erp-text);
  font-size: 15px;
}

.mes-dedup-alert {
  margin-top: -4px;
}

.route-preview {
  min-height: 40px;
  padding: 10px 12px;
  border: 1px solid var(--erp-border-soft);
  border-radius: 6px;
  background: var(--erp-surface-soft);
  color: var(--erp-text-secondary);
}

@media (max-width: 1200px) {
  .delivered-filter-group {
    justify-content: flex-start;
  }

  .mes-batch-result-header,
  .mes-batch-result-tags {
    justify-content: flex-start;
  }
}
</style>
