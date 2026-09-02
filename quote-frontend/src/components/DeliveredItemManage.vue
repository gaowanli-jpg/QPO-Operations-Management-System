<template>
  <div class="erp-page delivered-item-page" v-permission="DELIVERED_ITEM_VIEW_PERMISSION">
    <header class="erp-page-header">
      <div>
        <h1 class="erp-page-title">已提零件</h1>
        <p class="erp-page-description">仅展示尚未完成物料号获取和锁库流程的提单零件；完成锁库后的结果请在锁库零件模块查看。</p>
      </div>
    </header>

    <section class="erp-toolbar delivered-filter-toolbar">
      <div class="delivered-toolbar-main">
        <div class="erp-toolbar-group delivered-primary-filters">
          <el-radio-group v-model="viewMode" size="small">
            <el-radio-button v-for="option in viewModeOptions" :key="option.value" :label="option.value">{{ option.label }}</el-radio-button>
          </el-radio-group>
          <el-button v-permission="'market:delivered-item:customer-select'" class="customer-picker-trigger" @click="customerPickerVisible = true">
            <span class="customer-picker-content">
              <span
                :class="filters.customerName ? 'customer-picker-text' : 'customer-picker-placeholder'"
                :title="filters.customerName || '先选择客户'"
              >
                {{ filters.customerName || '先选择客户' }}
              </span>
              <span class="customer-picker-action">选择客户</span>
            </span>
          </el-button>
          <el-button v-if="filters.customerName" plain @click="handleCustomerClear">清除客户</el-button>
          <el-input v-model="filters.partNo" clearable placeholder="图号" class="filter-input" @keyup.enter="handleSearch" @clear="handleSearch" />
          <el-input v-model="filters.version" clearable placeholder="版本" class="filter-input small" @keyup.enter="handleSearch" @clear="handleSearch" />
          <el-select v-model="filters.flowStage" clearable placeholder="流程状态" class="filter-select flow-stage-select" @change="handleFlowStageChange" @clear="handleFlowStageChange">
            <el-option v-for="option in flowStageOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
          <el-button @click="handleSearch">搜索</el-button>
          <el-button @click="handleResetFilters">重置</el-button>
          <el-button plain @click="advancedFiltersVisible = !advancedFiltersVisible">
            {{ advancedFiltersVisible ? '收起筛选' : '高级筛选' }}
          </el-button>
        </div>

        <div v-if="viewMode === 'ACTIVE'" class="erp-toolbar-group delivered-batch-actions">
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
      </div>

      <div v-if="advancedFiltersVisible" class="erp-toolbar-group delivered-advanced-filters">
        <el-input v-model="filters.rfqNo" clearable placeholder="来源订单号" class="filter-input" @keyup.enter="handleSearch" @clear="handleSearch" />
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
        <el-select v-model="filters.mesSyncStatus" clearable placeholder="MES状态" class="filter-select" @change="handleTechnicalStatusChange" @clear="handleTechnicalStatusChange">
          <el-option v-for="option in syncStatusOptions" :key="option.value" :label="option.label" :value="option.value" />
        </el-select>
        <el-select v-model="filters.wmsAllocateStatus" clearable placeholder="WMS状态" class="filter-select" @change="handleTechnicalStatusChange" @clear="handleTechnicalStatusChange">
          <el-option v-for="option in syncStatusOptions" :key="option.value" :label="option.label" :value="option.value" />
        </el-select>
      </div>
    </section>

    <section v-if="mesBatchResult" class="erp-table-surface mes-batch-result-surface">
      <div class="erp-table-header mes-batch-result-header">
        <div>
          <h3>MES批量获取结果</h3>
          <p>已按真实 MES 批次返回展示，关闭结果区只清空页面展示，不影响后台批次和回调。</p>
        </div>
        <div class="mes-batch-result-actions">
          <el-button text @click="closeMesBatchResult">关闭结果</el-button>
        </div>
      </div>
      <div class="mes-batch-result-stats">
        <div v-for="stat in mesMaterialBatchStats" :key="stat.label" class="mes-batch-result-stat">
          <span>{{ stat.label }}</span>
          <strong>{{ stat.value }}</strong>
        </div>
      </div>
      <div v-if="mesMaterialBatchRows.length" class="mes-batch-result-details">
        <div v-for="row in mesMaterialBatchRows" :key="`mes-material-${row.ticketId || row.allocationId || row.result?.mesRequestId || row.result?.requestId || row.message}`" class="mes-batch-result-row">
          <span class="result-row-id">提单ID：{{ row.ticketId || row.result?.ticketId || '-' }}</span>
          <span class="result-row-code">bizId：{{ mesMaterialRowBizId(row) }}</span>
          <span class="result-row-code">taskNo：{{ mesMaterialRowTaskNo(row) }}</span>
          <el-tag :type="mesMaterialRowTagType(row.status)" size="small">{{ mesMaterialRowStatusText(row.status) }}</el-tag>
          <el-tooltip :content="row.message || row.result?.message || '-'" placement="top">
            <span class="result-row-message">{{ row.message || row.result?.message || '-' }}</span>
          </el-tooltip>
        </div>
      </div>
    </section>

    <section v-if="wmsBatchResult" class="erp-table-surface wms-batch-result-surface">
      <div class="erp-table-header wms-batch-result-header">
        <div>
          <h3>WMS 批量库存结果</h3>
          <p>任务已提交后按后台任务结果展示；结果待核对和本地写入待修复可再次勾选触发后端恢复原批次。</p>
        </div>
        <div class="wms-batch-result-actions">
          <el-button text @click="closeWmsBatchResult">关闭结果</el-button>
        </div>
      </div>
      <div class="wms-batch-result-stats">
        <div v-for="stat in wmsBatchStats" :key="stat.label" class="wms-batch-result-stat">
          <span>{{ stat.label }}</span>
          <strong>{{ stat.value }}</strong>
        </div>
      </div>
      <div v-if="wmsBatchRows.length" class="wms-batch-result-details">
        <div v-for="row in wmsBatchRows" :key="`wms-batch-${row.ticketId || row.batchId || row.externalBizId || row.message}`" class="wms-batch-result-row">
          <span class="result-row-id">提单ID：{{ row.ticketId || '-' }}</span>
          <span class="result-row-code">batchId：{{ row.batchId || '-' }}</span>
          <span class="result-row-code">bizId：{{ row.externalBizId || '-' }}</span>
          <el-tag :type="wmsBatchRowTagType(row.status)" size="small">{{ wmsBatchRowStatusText(row.status) }}</el-tag>
          <el-tooltip :content="row.message || '-'" placement="top">
            <span class="result-row-message">{{ row.message || '-' }}</span>
          </el-tooltip>
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
          <el-table-column v-if="viewMode === 'ACTIVE'" type="selection" width="46" fixed align="center" />
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
          <el-table-column prop="rfqNo" label="来源订单号" min-width="150" show-overflow-tooltip />
          <el-table-column prop="internalProjectNo" label="内部项目号" min-width="140" show-overflow-tooltip />
          <el-table-column prop="customerOrderNo" label="客户订单号" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ row.customerOrderNo || '-' }}</template>
          </el-table-column>
          <el-table-column prop="customerProjectNo" label="客户项目号" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ row.customerProjectNo || '-' }}</template>
          </el-table-column>
          <el-table-column label="材料编码" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ formatMaterialCode(row) }}</template>
          </el-table-column>
          <el-table-column label="材料类型" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ formatMaterialType(row) }}</template>
          </el-table-column>
          <el-table-column label="材料" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ formatMaterialName(row) }}</template>
          </el-table-column>
          <el-table-column label="材料形态" width="110" align="center">
            <template #default="{ row }">{{ formatMaterialShape(row) }}</template>
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
          <el-table-column v-if="viewMode === 'DISCARDED'" prop="discardReason" label="废弃原因" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ row.discardReason || '-' }}</template>
          </el-table-column>
          <el-table-column v-if="viewMode === 'DISCARDED'" prop="discardedAt" label="废弃时间" width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ formatDateTime(row.discardedAt) }}</template>
          </el-table-column>
          <el-table-column v-if="viewMode === 'DISCARDED'" prop="discardedBy" label="废弃人" width="110" align="center" show-overflow-tooltip>
            <template #default="{ row }">{{ row.discardedBy || '-' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="220" align="center" fixed="right">
            <template #default="{ row }">
              <div class="erp-table-actions delivered-row-actions">
                <el-button size="small" @click="openDetail(row)">详情</el-button>
                <el-button
                  v-if="viewMode === 'ACTIVE'"
                  v-permission="DELIVERED_ITEM_DISCARD_PERMISSION"
                  size="small"
                  type="danger"
                  plain
                  @click="handleDiscard(row)"
                >
                  废弃
                </el-button>
                <el-button
                  v-if="viewMode === 'DISCARDED'"
                  v-permission="DELIVERED_ITEM_RESTORE_PERMISSION"
                  size="small"
                  type="success"
                  plain
                  @click="handleRestore(row)"
                >
                  恢复
                </el-button>
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
          <el-descriptions-item label="来源订单号">{{ selectedItem.rfqNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="内部项目号">{{ selectedItem.internalProjectNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="客户订单号">{{ selectedItem.customerOrderNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="客户项目号">{{ selectedItem.customerProjectNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="材料编码">{{ formatMaterialCode(selectedItem) }}</el-descriptions-item>
          <el-descriptions-item label="材料类型">{{ formatMaterialType(selectedItem) }}</el-descriptions-item>
          <el-descriptions-item label="材料">{{ formatMaterialName(selectedItem) }}</el-descriptions-item>
          <el-descriptions-item label="材料形态">{{ formatMaterialShape(selectedItem) }}</el-descriptions-item>
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
      :current-customer-code="filters.customerCode"
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
  discardDeliveredItem,
  getDeliveredItem,
  getDeliveryIntegrationTask,
  listDeliveredItems,
  restoreDeliveredItem,
  submitWmsAllocationBatch,
  syncMesMaterialBatch
} from '../services/deliveredItemApi'
import { usePendingAction } from '../composables/usePendingAction'
import { formatProcessRouteSnapshot } from '../utils/processRouteFormatter'

const router = useRouter()
const { isPending, runAction } = usePendingAction()

const DELIVERED_ITEM_VIEW_PERMISSION = 'market:delivered-item:view'
const DELIVERED_ITEM_FILE_PREVIEW_PERMISSION = 'market:delivered-item:file-preview'
const DELIVERED_ITEM_MES_SYNC_PERMISSION = 'market:delivered-item:mes-sync'
const DELIVERED_ITEM_WMS_ALLOCATE_PERMISSION = 'market:delivered-item:wms-allocate'
const DELIVERED_ITEM_DISCARD_PERMISSION = 'market:delivered-item:discard'
const DELIVERED_ITEM_RESTORE_PERMISSION = 'market:delivered-item:restore'
const LOCKED_ITEM_VIEW_PERMISSION = 'market:locked-item:view'

const deliveredActionKeys = {
  batchMesSync: () => 'delivery:mes-sync:batch',
  batchWmsAllocation: () => 'delivery:wms-allocation:batch'
}

const filters = reactive({
  customerName: '',
  customerCode: '',
  rfqNo: '',
  partNo: '',
  version: '',
  flowStage: '',
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
  { value: 'RESULT_UNKNOWN', label: '结果待核对' },
  { value: 'APPLY_FAILED', label: '本地写入待修复' },
  { value: 'SUCCESS', label: '成功' },
  { value: 'FAILED', label: '失败' }
]
const flowStageOptions = [
  { value: '', label: '全部未完成' },
  { value: 'NEED_MATERIAL', label: '待获取物料号' },
  { value: 'MES_PROCESSING', label: '物料号处理中' },
  { value: 'MES_CALLBACK_PENDING', label: '待MES回调' },
  { value: 'MES_FAILED', label: '物料号失败' },
  { value: 'NEED_WMS', label: '待获取库存' },
  { value: 'WMS_PROCESSING', label: '库存处理中' },
  { value: 'WMS_FAILED', label: '库存失败' }
]
const viewModeOptions = [
  { label: '待处理', value: 'ACTIVE' },
  { label: '已废弃', value: 'DISCARDED' }
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
const wmsBatchResult = ref(null)
const advancedFiltersVisible = ref(false)
const viewMode = ref('ACTIVE')
const isActiveView = computed(() => viewMode.value === 'ACTIVE')
const isDiscardedView = computed(() => viewMode.value === 'DISCARDED')

watch([page, pageSize], () => loadItems())
watch(viewMode, async () => {
  selectedRows.value = []
  page.value = 1
  await loadItems()
})

const buildParams = () => {
  const params = Object.fromEntries(Object.entries(filters)
    .filter(([key, value]) => key !== 'dateRange' && String(value || '').trim()))
  if (filters.flowStage) {
    params.flowStage = filters.flowStage
  }
  const [dateFrom, dateTo] = filters.dateRange || []
  if (dateFrom) params.dateFrom = dateFrom
  if (dateTo) params.dateTo = dateTo
  params.discarded = viewMode.value === 'DISCARDED'
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

const handleResetFilters = async () => {
  Object.assign(filters, {
    customerName: '',
    customerCode: '',
    rfqNo: '',
    partNo: '',
    version: '',
    flowStage: '',
    internalProjectNo: '',
    customerOrderNo: '',
    customerProjectNo: '',
    mesSyncStatus: '',
    wmsAllocateStatus: '',
    dateRange: []
  })
  await handleSearch()
}

const handleFlowStageChange = async () => {
  if (filters.flowStage) {
    filters.mesSyncStatus = ''
    filters.wmsAllocateStatus = ''
  }
  await handleSearch()
}

const handleTechnicalStatusChange = async () => {
  if (filters.mesSyncStatus || filters.wmsAllocateStatus) {
    filters.flowStage = ''
  }
  await handleSearch()
}

const resolveCustomerName = (customer) => typeof customer === 'string' ? customer : (customer?.customerName || '')
const resolveCustomerCode = (customer) => typeof customer === 'string' ? customer : (customer?.customerCode || '')

const handleCustomerSelected = async (customer) => {
  filters.customerName = resolveCustomerName(customer)
  filters.customerCode = resolveCustomerCode(customer)
  await handleSearch()
}

const handleCustomerClear = async () => {
  filters.customerName = ''
  filters.customerCode = ''
  await handleSearch()
}

const handleSelectionChange = (rows) => {
  selectedRows.value = rows || []
}

const handleDiscard = async (row) => {
  try {
    const { value } = await ElMessageBox.prompt('请输入废弃原因', '废弃已提零件', {
      confirmButtonText: '确认废弃',
      cancelButtonText: '取消',
      inputPlaceholder: '废弃原因',
      inputValidator: (value) => String(value || '').trim() ? true : '废弃原因不能为空'
    })
    await discardDeliveredItem(row.id, { reason: String(value || '').trim() })
    ElMessage.success('已提零件已废弃')
    selectedRows.value = []
    await loadItems()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    console.error(error)
    ElMessage.error(error.response?.data?.message || error.message || '已提零件废弃失败。')
  }
}

const handleRestore = async (row) => {
  try {
    const { value } = await ElMessageBox.prompt('请输入恢复原因（可选）', '恢复已提零件', {
      confirmButtonText: '确认恢复',
      cancelButtonText: '取消',
      inputPlaceholder: '恢复原因（可选）'
    })
    await restoreDeliveredItem(row.id, { reason: String(value || '').trim() })
    ElMessage.success('已提零件已恢复')
    await loadItems()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    console.error(error)
    ElMessage.error(error.response?.data?.message || error.message || '已提零件恢复失败。')
  }
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
  wmsBatchResult.value = null
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
      ElMessage.success(taskId ? `WMS 库存任务已提交，任务ID：${taskId}` : 'WMS 库存任务已提交')
      wmsBatchResult.value = wmsBatchResultOf(task) || await waitWmsBatchTaskResult(taskId)
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

const formatDash = (value) => (value === null || value === undefined || value === '') ? '-' : value

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

const formatMaterialCode = (row) => {
  const data = normalizeObject(row?.materialData)
  return row?.engineeringMaterialCode || data.materialCode || '-'
}

const formatMaterialType = (row) => {
  const data = normalizeObject(row?.materialData)
  return row?.engineeringMaterialTypeName || row?.engineeringMaterialTypeCode || data.materialTypeName || data.materialTypeCode || '-'
}

const formatMaterialName = (row) => {
  const data = normalizeObject(row?.materialData)
  return row?.engineeringMaterialName || data.materialName || data.name || row?.materialName || '-'
}

const formatMaterialShape = (row) => {
  const data = normalizeObject(row?.materialData)
  return row?.materialShape || data.shape || data.materialShape || '-'
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
  RESULT_UNKNOWN: 'warning',
  APPLY_FAILED: 'danger',
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
  FOLLOWER: '关联等待'
}[role] || '-')

const dedupFollowerNote = (item) => {
  if (!item || item.mesRequestRole !== 'FOLLOWER') return ''
  return '已关联同图号版本 MES 主请求，等待主请求结果'
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

const wmsBatchResultOf = (task) => mesBatchResultOf(task)

const resultRowsOf = (result, primaryKey = 'results') => {
  const rows = result?.[primaryKey] || result?.rows || result?.results || []
  return Array.isArray(rows) ? rows : []
}

const mesMaterialBatchRows = computed(() => resultRowsOf(mesBatchResult.value, 'results'))

const wmsBatchRows = computed(() => resultRowsOf(wmsBatchResult.value, 'results'))

const numberCount = (value) => Number.isFinite(Number(value)) ? Number(value) : 0

const resultObjectOf = (row) => row?.result && typeof row.result === 'object' ? row.result : {}

const rowStatusOf = (row) => String(row?.status || resultObjectOf(row).status || '').toUpperCase()

const countRowsByStatus = (rows, statuses) => {
  const targets = new Set(statuses)
  return rows.filter(row => targets.has(rowStatusOf(row))).length
}

const distinctRowValueCount = (rows, resolver) => new Set(rows
  .map(resolver)
  .filter(value => value !== null && value !== undefined && String(value).trim()))
  .size

const mesMaterialRowBizId = (row) => {
  const result = resultObjectOf(row)
  return result.bizId || row?.bizId || result.batchBizId || result.mesRequestBizId || result.mesRequestId || '-'
}

const mesMaterialRowTaskNo = (row) => {
  const result = resultObjectOf(row)
  return result.taskNo || result.externalTraceId || row?.taskNo || row?.externalTraceId || '-'
}

const mesMaterialBatchStats = computed(() => {
  const result = mesBatchResult.value || {}
  const rows = mesMaterialBatchRows.value
  const batchCount = numberCount(result.batchCount) || distinctRowValueCount(rows, mesMaterialRowBizId)
  const pendingCreateCount = numberCount(result.pendingCreateCount)
    || Math.max(0, numberCount(result.callbackPendingCount) - numberCount(result.dedupFollowerCount))
    || countRowsByStatus(rows, ['CALLBACK_PENDING'])
  const unknownCount = numberCount(result.unknownCount)
    || numberCount(result.resultUnknownCount)
    || countRowsByStatus(rows, ['RESULT_UNKNOWN'])
  return [
    { label: 'MES 批次数', value: batchCount },
    { label: '实际发送', value: numberCount(result.submittedToMesCount) || rows.filter(row => row.submittedToMes === true || resultObjectOf(row).submittedToMes === true).length },
    { label: '已有物料成功', value: numberCount(result.successCount) || countRowsByStatus(rows, ['SUCCESS']) },
    { label: '待 MES 创建', value: pendingCreateCount },
    { label: 'Follower', value: numberCount(result.dedupFollowerCount) || countRowsByStatus(rows, ['DEDUP_FOLLOWING']) },
    { label: '失败', value: numberCount(result.failedCount) || countRowsByStatus(rows, ['FAILED']) },
    { label: '结果待核对', value: unknownCount }
  ]
})

const closeMesBatchResult = () => {
  mesBatchResult.value = null
}

const wmsBatchStats = computed(() => {
  const result = wmsBatchResult.value || {}
  const rows = wmsBatchRows.value
  return [
    { label: '批次数', value: numberCount(result.batchCount) || distinctRowValueCount(rows, row => row?.batchId || row?.externalBizId) },
    { label: '成功', value: numberCount(result.successCount) || countRowsByStatus(rows, ['SUCCESS']) },
    { label: '失败', value: numberCount(result.failedCount) || countRowsByStatus(rows, ['FAILED']) },
    { label: '待核对', value: numberCount(result.unknownCount) || numberCount(result.resultUnknownCount) || countRowsByStatus(rows, ['RESULT_UNKNOWN']) },
    { label: '应用失败', value: numberCount(result.applyFailedCount) || countRowsByStatus(rows, ['APPLY_FAILED']) },
    { label: '跳过', value: numberCount(result.skippedCount) || countRowsByStatus(rows, ['SKIPPED']) }
  ]
})

const closeWmsBatchResult = () => {
  wmsBatchResult.value = null
}

const mesMaterialRowStatusText = (value) => ({
  SUCCESS: '成功',
  FAILED: '失败',
  SKIPPED: '跳过',
  CALLBACK_PENDING: 'MES 已受理，等待物料创建结果',
  DEDUP_FOLLOWING: 'Follower 等待主请求结果',
  RESULT_UNKNOWN: '结果待核对'
}[String(value || '').toUpperCase()] || formatDash(value))

const mesMaterialRowTagType = (value) => ({
  SUCCESS: 'success',
  FAILED: 'danger',
  SKIPPED: 'info',
  CALLBACK_PENDING: 'warning',
  DEDUP_FOLLOWING: 'warning',
  RESULT_UNKNOWN: 'warning'
}[String(value || '').toUpperCase()] || 'info')

const wmsBatchRowStatusText = (value) => ({
  SUCCESS: '成功',
  FAILED: '失败',
  SKIPPED: '跳过',
  RESULT_UNKNOWN: '结果待核对',
  APPLY_FAILED: '本地写入待修复'
}[String(value || '').toUpperCase()] || formatDash(value))

const wmsBatchRowTagType = (value) => ({
  SUCCESS: 'success',
  FAILED: 'danger',
  SKIPPED: 'info',
  RESULT_UNKNOWN: 'warning',
  APPLY_FAILED: 'danger'
}[String(value || '').toUpperCase()] || 'info')

const waitFor = (milliseconds) => new Promise(resolve => globalThis.setTimeout(resolve, milliseconds))

const waitMesBatchTaskResult = async (taskId) => {
  if (!taskId) return null
  const delays = [300, 700, 1000, 1200, 1500]
  for (const delayMs of delays) {
    await waitFor(delayMs)
    try {
      const latestTask = await getDeliveryIntegrationTask(taskId)
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

const waitWmsBatchTaskResult = async (taskId) => {
  if (!taskId) return null
  const delays = [300, 700, 1000, 1200, 1500]
  for (const delayMs of delays) {
    await waitFor(delayMs)
    try {
      const latestTask = await getDeliveryIntegrationTask(taskId)
      const result = wmsBatchResultOf(latestTask)
      if (result) return result
      if (['SUCCESS', 'FAILED'].includes(String(latestTask?.status || '').toUpperCase())) {
        return null
      }
    } catch (error) {
      console.warn('WMS batch task result polling failed', error)
      return null
    }
  }
  return null
}

const formatMesBatchCount = (result, key) => {
  const value = result?.[key]
  return Number.isFinite(Number(value)) ? Number(value) : 0
}

const formatProcessRoute = (route) => formatProcessRouteSnapshot(route)

onMounted(() => loadItems())
</script>

<style scoped>
.delivered-filter-toolbar {
  align-items: stretch;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
  box-sizing: border-box;
}

.delivered-toolbar-main {
  display: flex;
  width: 100%;
  gap: 12px;
  row-gap: 8px;
  column-gap: 10px;
  align-items: flex-start;
  justify-content: space-between;
  box-sizing: border-box;
}

.delivered-primary-filters {
  flex: 1 1 680px;
  min-width: 0;
  justify-content: flex-start;
  row-gap: 8px;
  column-gap: 10px;
  box-sizing: border-box;
}

.delivered-batch-actions {
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

.delivered-advanced-filters {
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

.customer-picker-trigger {
  flex: 0 0 220px;
  min-height: 32px;
  padding: 0 12px;
  text-align: left;
  width: 220px;
}

.customer-picker-trigger :deep(> span) {
  display: block;
  width: 100%;
}

.customer-picker-content {
  align-items: center;
  display: flex;
  gap: 12px;
  min-width: 0;
  width: 100%;
}

.customer-picker-text,
.customer-picker-placeholder {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.customer-picker-placeholder {
  color: var(--erp-text-secondary);
}

.customer-picker-action {
  align-items: center;
  border-left: 1px solid var(--erp-border-soft);
  color: var(--erp-primary);
  display: inline-flex;
  flex: none;
  font-size: 12px;
  font-weight: 700;
  min-height: 24px;
  padding-left: 12px;
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

.flow-stage-select {
  width: 150px;
  flex-basis: 150px;
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

.mes-batch-result-surface,
.wms-batch-result-surface {
  margin-bottom: 12px;
}

.mes-batch-result-header,
.wms-batch-result-header {
  align-items: center;
  gap: 12px;
}

.mes-batch-result-header h3,
.wms-batch-result-header h3 {
  margin: 0 0 4px;
  font-size: 15px;
  color: var(--erp-text);
}

.mes-batch-result-header p,
.wms-batch-result-header p {
  margin: 0;
  color: var(--erp-text-secondary);
  font-size: 13px;
}

.mes-batch-result-actions,
.wms-batch-result-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

.mes-batch-result-stats,
.wms-batch-result-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: 8px;
  padding: 0 var(--erp-space-3) var(--erp-space-3);
}

.mes-batch-result-stat,
.wms-batch-result-stat {
  min-width: 0;
  padding: 10px 12px;
  border: 1px solid var(--erp-border-light);
  border-radius: var(--erp-radius-sm);
  background: var(--erp-bg-soft);
}

.mes-batch-result-stat span,
.wms-batch-result-stat span {
  display: block;
  color: var(--erp-text-secondary);
  font-size: var(--erp-font-size-sm);
}

.mes-batch-result-stat strong,
.wms-batch-result-stat strong {
  display: block;
  margin-top: 4px;
  color: var(--erp-text-primary);
  font-size: 18px;
  line-height: 1.2;
}

.mes-batch-result-details,
.wms-batch-result-details {
  display: grid;
  gap: 8px;
  max-width: 100%;
  padding: 0 var(--erp-space-3) var(--erp-space-3);
  overflow-x: auto;
}

.mes-batch-result-row,
.wms-batch-result-row {
  display: grid;
  grid-template-columns: minmax(88px, 0.8fr) minmax(180px, 1.5fr) minmax(140px, 1fr) auto minmax(220px, 2fr);
  gap: 8px;
  min-width: 760px;
  align-items: center;
  padding: 8px 10px;
  border: 1px solid var(--erp-border-light);
  border-radius: var(--erp-radius-sm);
  background: var(--erp-bg-soft);
  color: var(--erp-text-secondary);
  font-size: var(--erp-font-size-sm);
}

.result-row-id,
.result-row-code,
.result-row-message {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  word-break: break-word;
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
  .delivered-toolbar-main {
    flex-direction: column;
    align-items: stretch;
  }

  .delivered-primary-filters,
  .delivered-batch-actions {
    justify-content: flex-start;
  }

  .delivered-batch-actions {
    width: 100%;
    padding-left: 0;
    border-left: 0;
  }

  .mes-batch-result-header,
  .mes-batch-result-actions,
  .wms-batch-result-header,
  .wms-batch-result-actions {
    justify-content: flex-start;
  }
}

@media (max-width: 720px) {
  .delivered-primary-filters .customer-picker-trigger,
  .delivered-primary-filters .filter-input,
  .delivered-primary-filters .filter-select,
  .delivered-advanced-filters .filter-input,
  .delivered-advanced-filters .filter-select {
    width: auto;
    min-width: 120px;
    flex: 1 1 140px;
  }

  .filter-date-range {
    width: 100%;
    flex: 1 1 100%;
  }

  .mes-batch-result-row,
  .wms-batch-result-row {
    grid-template-columns: 1fr;
    min-width: 0;
    align-items: flex-start;
  }

  .result-row-id,
  .result-row-code,
  .result-row-message {
    white-space: normal;
  }
}
</style>
