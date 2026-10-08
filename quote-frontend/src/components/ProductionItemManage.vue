<template>
  <div class="erp-page production-item-page" v-permission="PRODUCTION_ITEM_VIEW_PERMISSION">
    <header class="erp-page-header">
      <div>
        <h1 class="erp-page-title">待产零件</h1>
        <p class="erp-page-description">处理 WMS 拆分后仍需下发 MES 生产的待产行。</p>
      </div>
    </header>

    <section class="erp-toolbar production-filter-toolbar">
      <div class="production-toolbar-main">
        <div class="erp-toolbar-group production-primary-filters">
          <el-tag v-if="route.query.ticketId" type="info" effect="plain">提单ID：{{ route.query.ticketId }}</el-tag>
          <el-radio-group v-model="viewMode" size="small">
            <el-radio-button v-for="option in viewModeOptions" :key="option.value" :label="option.value">{{ option.label }}</el-radio-button>
          </el-radio-group>
          <CustomerAutocomplete
            v-model="filters.customerName"
            :customer-code="filters.customerCode"
            customer-scope="production-item"
            permission="market:production-item:customer-select"
            @select="handleCustomerSelected"
            @clear="handleCustomerClear"
            @open-picker="customerPickerVisible = true"
          />
          <el-input v-model="filters.partNo" clearable placeholder="图号" class="filter-input" @keyup.enter="handleSearch" @clear="handleSearch" />
          <el-input v-model="filters.version" clearable placeholder="版本" class="filter-input small" @keyup.enter="handleSearch" @clear="handleSearch" />
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

        <div v-if="viewMode === 'ACTIVE'" class="erp-toolbar-group production-batch-actions">
          <el-button
            v-permission="PRODUCTION_ITEM_MES_PRODUCTION_SUBMIT_PERMISSION"
            type="primary"
            plain
            :disabled="selectedAllocationIds.length === 0 || isMesProductionDispatchPending"
            :loading="isMesProductionDispatchPending"
            @click="handleMesProductionDispatch"
          >
            下发MES生产
          </el-button>
          <el-button
            v-permission="PRODUCTION_ITEM_DISCARD_PERMISSION"
            type="danger"
            plain
            :disabled="selectedAllocationIds.length === 0 || isBatchDiscardPending"
            :loading="isBatchDiscardPending"
            @click="handleBatchDiscard"
          >
            批量废弃
          </el-button>
        </div>
        <div v-if="viewMode === 'DISCARDED'" class="erp-toolbar-group production-batch-actions">
          <el-button
            v-permission="PRODUCTION_ITEM_RESTORE_PERMISSION"
            type="success"
            plain
            :disabled="selectedAllocationIds.length === 0 || isBatchRestorePending"
            :loading="isBatchRestorePending"
            @click="handleBatchRestore"
          >
            批量恢复
          </el-button>
        </div>
      </div>

      <div v-if="advancedFiltersVisible" class="erp-toolbar-group production-advanced-filters">
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
          <p>只下发待产数量行；RESULT_UNKNOWN 需要先核对结果，不能直接重复下发。</p>
        </div>
        <div class="mes-production-result-actions">
          <el-button text @click="closeMesProductionResult">关闭结果</el-button>
        </div>
      </div>
      <div class="mes-production-result-stats">
        <div v-for="stat in mesProductionStats" :key="stat.label" class="mes-production-result-stat">
          <span>{{ stat.label }}</span>
          <strong>{{ stat.value }}</strong>
        </div>
      </div>
      <div v-if="mesProductionRows.length" class="mes-production-result-details">
        <div v-for="row in mesProductionRows" :key="`mes-production-${row.allocationId || row.result?.requestId || row.message}`" class="mes-production-result-row">
          <span>分配ID：{{ row.allocationId || row.result?.allocationId || '-' }}</span>
          <span class="result-row-code">bizId：{{ mesProductionRowBizId(row) }}</span>
          <span class="result-row-code">requestId：{{ mesProductionRowRequestId(row) }}</span>
          <span class="result-row-code">planNo：{{ mesProductionRowPlanNo(row) }}</span>
          <el-tag :type="mesProductionRowTagType(row.status)" size="small">{{ mesProductionRowStatusText(row.status) }}</el-tag>
          <el-tooltip :content="row.message || row.result?.errorMessage || '-'" placement="top">
            <span class="mes-production-result-message">{{ row.message || row.result?.errorMessage || '-' }}</span>
          </el-tooltip>
          <el-button
            v-if="String(row.status || '').toUpperCase() === 'RESULT_UNKNOWN'"
            size="small"
            type="warning"
            plain
            :loading="isMesProductionReconcilePending(row)"
            :disabled="isMesProductionReconcilePending(row)"
            @click="handleMesProductionReconcile(row)"
          >
            核对结果
          </el-button>
        </div>
      </div>
    </section>

    <section class="erp-table-surface production-table-surface">
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
          <el-table-column label="材料编码" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ formatMaterialCode(row) }}</template>
          </el-table-column>
          <el-table-column label="材料类型" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ formatMaterialType(row) }}</template>
          </el-table-column>
          <el-table-column label="材料" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ formatMaterialName(row) }}</template>
          </el-table-column>
          <el-table-column label="材料形态" width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ formatMaterialShape(row) }}</template>
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
          <el-table-column v-if="viewMode === 'DISCARDED'" prop="discardReason" label="废弃原因" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ formatDash(row.discardReason) }}</template>
          </el-table-column>
          <el-table-column v-if="viewMode === 'DISCARDED'" prop="discardedAt" label="废弃时间" width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ formatDateTime(row.discardedAt) }}</template>
          </el-table-column>
          <el-table-column v-if="viewMode === 'DISCARDED'" prop="discardedBy" label="废弃人" width="110" align="center" show-overflow-tooltip>
            <template #default="{ row }">{{ formatDash(row.discardedBy) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="180" align="center" fixed="right">
            <template #default="{ row }">
              <div class="erp-table-actions production-row-actions">
                <el-button
                  v-permission="DELIVERED_ITEM_VIEW_PERMISSION"
                  size="small"
                  type="primary"
                  plain
                  @click="openTicketDetail(row)"
                >
                  查看提单行
                </el-button>
                <el-button
                  v-if="viewMode === 'ACTIVE'"
                  v-permission="PRODUCTION_ITEM_DISCARD_PERMISSION"
                  size="small"
                  type="danger"
                  plain
                  @click="handleDiscard(row)"
                >
                  废弃
                </el-button>
                <el-button
                  v-if="viewMode === 'DISCARDED'"
                  v-permission="PRODUCTION_ITEM_RESTORE_PERMISSION"
                  size="small"
                  type="success"
                  plain
                  @click="handleRestore(row)"
                >
                  恢复
                </el-button>
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
          <el-descriptions-item label="材料编码">{{ formatMaterialCode(selectedTicketDetail) }}</el-descriptions-item>
          <el-descriptions-item label="材料类型">{{ formatMaterialType(selectedTicketDetail) }}</el-descriptions-item>
          <el-descriptions-item label="材料">{{ formatMaterialName(selectedTicketDetail) }}</el-descriptions-item>
          <el-descriptions-item label="材料形态">{{ formatMaterialShape(selectedTicketDetail) }}</el-descriptions-item>
          <el-descriptions-item label="毛重">{{ formatWeight(selectedTicketDetail.materialGrossWeight) }}</el-descriptions-item>
          <el-descriptions-item label="净重">{{ formatWeight(selectedTicketDetail.partNetWeight) }}</el-descriptions-item>
          <el-descriptions-item label="备注">{{ formatDash(selectedTicketDetail.remark) }}</el-descriptions-item>
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
      :current-customer-code="filters.customerCode"
      customer-scope="production-item"
      @select="handleCustomerSelected"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import CustomerPickerDialog from './CustomerPickerDialog.vue'
import CustomerAutocomplete from './CustomerAutocomplete.vue'
import {
  batchDiscardProductionItems,
  batchRestoreProductionItems,
  discardProductionItem,
  getDeliveredItem,
  getDeliveryIntegrationTask,
  listProductionItems,
  listTicketAllocations,
  restoreProductionItem,
  submitMesProductionDispatchBatch
} from '../services/deliveredItemApi'
import { usePendingAction } from '../composables/usePendingAction'
import { formatProcessRouteSnapshot } from '../utils/processRouteFormatter'

const PRODUCTION_ITEM_VIEW_PERMISSION = 'market:production-item:view'
const PRODUCTION_ITEM_MES_PRODUCTION_SUBMIT_PERMISSION = 'market:production-item:mes-production-submit'
const PRODUCTION_ITEM_DISCARD_PERMISSION = 'market:production-item:discard'
const PRODUCTION_ITEM_RESTORE_PERMISSION = 'market:production-item:restore'
const DELIVERED_ITEM_VIEW_PERMISSION = 'market:delivered-item:view'

const route = useRoute()
const router = useRouter()
const { isPending, runAction } = usePendingAction()

const productionActionKeys = {
  batchDiscard: () => 'production-item:discard:batch',
  batchRestore: () => 'production-item:restore:batch',
  mesProductionDispatch: () => 'production-item:mes-production:batch',
  mesProductionReconcile: (row) => `production-item:mes-production:reconcile:${row?.allocationId || row?.result?.requestId || 'unknown'}`
}

const filters = reactive({
  customerName: '',
  customerCode: '',
  rfqNo: '',
  partNo: '',
  version: '',
  materialNo: '',
  internalProjectNo: '',
  customerOrderNo: '',
  customerProjectNo: '',
  mesProductionStatus: '',
  dateRange: []
})

const allocationTypeOptions = [
  { value: 'PRODUCTION_REQUIRED', label: '待生产数量', type: 'danger' }
]
const mesProductionStatusOptions = [
  { value: 'PENDING', label: '待下发' },
  { value: 'PROCESSING', label: '下发中' },
  { value: 'RESULT_UNKNOWN', label: '结果待核对' },
  { value: 'SUCCESS', label: '已下发' },
  { value: 'FAILED', label: '下发失败' }
]
const viewModeOptions = [
  { label: '待处理', value: 'ACTIVE' },
  { label: '已废弃', value: 'DISCARDED' }
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
const viewMode = ref('ACTIVE')
const isDiscardedView = computed(() => viewMode.value === 'DISCARDED')

watch([page, pageSize], () => loadItems())
watch(() => route.query.ticketId, () => loadItems())
watch(viewMode, async () => {
  selectedRows.value = []
  page.value = 1
  await loadItems()
})
watch(() => route.query.mesProductionStatus, async () => {
  syncFiltersFromRouteQuery()
  page.value = 1
  await loadItems()
})

const normalizeRouteQueryValue = (value) => Array.isArray(value) ? (value[0] || '') : (value || '')

const syncFiltersFromRouteQuery = () => {
  filters.mesProductionStatus = normalizeRouteQueryValue(route.query.mesProductionStatus)
}

const buildParams = () => {
  const params = Object.fromEntries(Object.entries(filters)
    .filter(([key, value]) => key !== 'dateRange' && String(value || '').trim()))
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
    if (route.query.ticketId) {
      const records = await listTicketAllocations(route.query.ticketId)
      items.value = filterProductionRows(records)
      total.value = items.value.length
      return
    }
    const response = await listProductionItems(buildParams())
    items.value = response.records || []
    total.value = response.total || 0
  } catch (error) {
    console.error(error)
    ElMessage.error(error.response?.data?.message || error.message || '待产零件列表加载失败。')
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
    materialNo: '',
    internalProjectNo: '',
    customerOrderNo: '',
    customerProjectNo: '',
    mesProductionStatus: '',
    dateRange: []
  })
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

const filterProductionRows = (records) => (Array.isArray(records) ? records : [])
  .filter(row => row?.allocationType === 'PRODUCTION_REQUIRED')
  .filter(row => Boolean(row?.discarded) === (viewMode.value === 'DISCARDED'))
  .filter(row => String(row?.mesProductionStatus || 'PENDING').toUpperCase() !== 'SUCCESS')

const handleSelectionChange = (rows) => {
  selectedRows.value = rows || []
}

const handleDiscard = async (row) => {
  try {
    const { value } = await ElMessageBox.prompt('请输入废弃原因', '废弃待产零件', {
      confirmButtonText: '确认废弃',
      cancelButtonText: '取消',
      inputPlaceholder: '废弃原因',
      inputValidator: (value) => String(value || '').trim() ? true : '废弃原因不能为空'
    })
    await discardProductionItem(row.id, { reason: String(value || '').trim() })
    ElMessage.success('待产零件已废弃')
    selectedRows.value = []
    await loadItems()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    console.error(error)
    ElMessage.error(error.response?.data?.message || error.message || '待产零件废弃失败。')
  }
}

const handleRestore = async (row) => {
  try {
    const { value } = await ElMessageBox.prompt('请输入恢复原因（可选）', '恢复待产零件', {
      confirmButtonText: '确认恢复',
      cancelButtonText: '取消',
      inputPlaceholder: '恢复原因（可选）'
    })
    await restoreProductionItem(row.id, { reason: String(value || '').trim() })
    ElMessage.success('待产零件已恢复')
    await loadItems()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    console.error(error)
    ElMessage.error(error.response?.data?.message || error.message || '待产零件恢复失败。')
  }
}

const isBatchDiscardPending = computed(() => isPending(productionActionKeys.batchDiscard()))
const isBatchRestorePending = computed(() => isPending(productionActionKeys.batchRestore()))

const handleBatchDiscard = async () => {
  if (selectedAllocationIds.value.length === 0) {
    return ElMessage.warning('请先选择需要废弃的待产零件。')
  }
  try {
    const { value } = await ElMessageBox.prompt(`请输入批量废弃原因，本次将废弃 ${selectedAllocationCountText()}。`, '批量废弃待产零件', {
      confirmButtonText: '确认废弃',
      cancelButtonText: '取消',
      inputPlaceholder: '批量废弃原因',
      inputValidator: (value) => String(value || '').trim() ? true : '废弃原因不能为空'
    })
    return runAction(productionActionKeys.batchDiscard(), async () => {
      await batchDiscardProductionItems(selectedAllocationIds.value, { reason: String(value || '').trim() })
      ElMessage.success('待产零件批量废弃完成')
      selectedRows.value = []
      await loadItems()
    })
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    console.error(error)
    ElMessage.error(error.response?.data?.message || error.message || '待产零件批量废弃失败。')
  }
}

const handleBatchRestore = async () => {
  if (selectedAllocationIds.value.length === 0) {
    return ElMessage.warning('请先选择需要恢复的待产零件。')
  }
  try {
    const { value } = await ElMessageBox.prompt(`请输入批量恢复原因（可选），本次将恢复 ${selectedAllocationCountText()}。`, '批量恢复待产零件', {
      confirmButtonText: '确认恢复',
      cancelButtonText: '取消',
      inputPlaceholder: '批量恢复原因（可选）'
    })
    return runAction(productionActionKeys.batchRestore(), async () => {
      await batchRestoreProductionItems(selectedAllocationIds.value, { reason: String(value || '').trim() })
      ElMessage.success('待产零件批量恢复完成')
      selectedRows.value = []
      await loadItems()
    })
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    console.error(error)
    ElMessage.error(error.response?.data?.message || error.message || '待产零件批量恢复失败。')
  }
}

const selectedAllocationCountText = () => `${selectedAllocationIds.value.length} 条待产零件`

const taskIdOf = (task) => task?.id || task?.taskId || task?.task?.id

const isMesProductionDispatchPending = computed(() => isPending(productionActionKeys.mesProductionDispatch()))

const productionDispatchPreview = computed(() => {
  const rows = selectedRows.value || []
  const blockedReasons = new Map()
  let sendableCount = 0
  let totalQty = 0
  const batchKeys = new Set()
  const addBlocked = (reason) => blockedReasons.set(reason, (blockedReasons.get(reason) || 0) + 1)

  rows.forEach(row => {
    const status = String(row?.mesProductionStatus || 'PENDING').toUpperCase()
    if (row?.allocationType !== 'PRODUCTION_REQUIRED') {
      addBlocked('非待生产行')
      return
    }
    if (row?.discarded) {
      addBlocked('已废弃')
      return
    }
    if (status === 'SUCCESS') {
      addBlocked('已下发成功')
      return
    }
    if (status === 'PROCESSING') {
      addBlocked('正在下发')
      return
    }
    if (status === 'RESULT_UNKNOWN') {
      addBlocked('结果待核对')
      return
    }
    if (!row?.materialNo) {
      addBlocked('缺少物料号')
      return
    }
    if (!row?.deliveryDate) {
      addBlocked('缺少交期')
      return
    }
    const qty = Number(row?.qty || 0)
    if (!Number.isFinite(qty) || qty <= 0) {
      addBlocked('数量无效')
      return
    }
    sendableCount += 1
    totalQty += qty
    batchKeys.add([
      row.customerCode || row.customerName || '',
      row.internalProjectNo || '',
      row.sourceOrderId || row.rfqNo || row.customerOrderNo || '',
      row.materialNo || ''
    ].join('|'))
  })

  return {
    selectedCount: rows.length,
    sendableCount,
    estimatedBatchCount: batchKeys.size,
    totalQty,
    blockedReasonText: Array.from(blockedReasons.entries())
      .map(([reason, count]) => `${reason} ${count} 行`)
      .join('；') || '无'
  }
})

const productionDispatchConfirmMessage = () => {
  const preview = productionDispatchPreview.value
  return [
    `已选行数：${preview.selectedCount}`,
    `将实际发送：${preview.sendableCount}`,
    `预计 MES 批次：${preview.estimatedBatchCount}`,
    `待生产总数量：${preview.totalQty}`,
    `被阻断或跳过：${preview.blockedReasonText}`,
    '实际拆批和最终校验以后端为准；RESULT_UNKNOWN 行必须先核对结果，不能直接重复下发。'
  ].join('\n')
}

const handleMesProductionDispatch = async () => {
  if (selectedAllocationIds.value.length === 0) {
    return ElMessage.warning('请先选择需要下发 MES 生产的待产零件。')
  }
  mesProductionResult.value = null
  try {
    await ElMessageBox.confirm(productionDispatchConfirmMessage(), '下发MES生产', {
      confirmButtonText: '确认提交',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }

  return runAction(productionActionKeys.mesProductionDispatch(), async () => {
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
  router.push({ path: '/production-items' })
}

const openTicketDetail = async (row) => {
  if (!row?.ticketId) {
    ElMessage.warning('当前待产结果缺少提单ID，无法反查原始提单行。')
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

const formatDateTime = (value) => {
  if (!value) return '-'
  return String(value).replace('T', ' ').replace(/\.\d+$/, '').replace(/Z$/, '')
}

const statusText = (value) => ({
  PENDING: '未处理',
  PROCESSING: '处理中',
  CALLBACK_PENDING: '待MES回调',
  RESULT_UNKNOWN: '结果待核对',
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

const resultRowsOf = (result) => {
  const rows = result?.rows || result?.results || []
  return Array.isArray(rows) ? rows : []
}

const mesProductionRows = computed(() => resultRowsOf(mesProductionResult.value))

const numberCount = (value) => Number.isFinite(Number(value)) ? Number(value) : 0

const resultObjectOf = (row) => row?.result && typeof row.result === 'object' ? row.result : {}

const mesProductionRowBizId = (row) => resultObjectOf(row).bizId || row?.bizId || '-'

const mesProductionRowRequestId = (row) => resultObjectOf(row).requestId || row?.requestId || '-'

const mesProductionRowPlanNo = (row) => resultObjectOf(row).externalId || row?.externalId || '-'

const mesProductionStats = computed(() => {
  const result = mesProductionResult.value || {}
  return [
    { label: 'MES 批次数', value: numberCount(result.batchCount) },
    { label: '逐行结果', value: numberCount(result.totalCount) || mesProductionRows.value.length },
    { label: '成功', value: numberCount(result.successCount) },
    { label: '失败', value: numberCount(result.failedCount) },
    { label: '结果待核对', value: numberCount(result.unknownCount) || numberCount(result.resultUnknownCount) },
    { label: '跳过', value: numberCount(result.skippedCount) }
  ]
})

const closeMesProductionResult = () => {
  mesProductionResult.value = null
}

const isMesProductionReconcilePending = (row) => isPending(productionActionKeys.mesProductionReconcile(row))

const handleMesProductionReconcile = (row) => runAction(productionActionKeys.mesProductionReconcile(row), async () => {
  ElMessage.warning('当前数据中心未开放核对能力，请保留原 bizId/requestId 由运维核对 MES 结果。')
})

const waitFor = (milliseconds) => new Promise(resolve => globalThis.setTimeout(resolve, milliseconds))

const waitMesProductionTaskResult = async (taskId) => {
  if (!taskId) return null
  const delays = [300, 700, 1000, 1200, 1500]
  for (const delayMs of delays) {
    await waitFor(delayMs)
    try {
      const latestTask = await getDeliveryIntegrationTask(taskId)
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
  SKIPPED: '跳过',
  RESULT_UNKNOWN: '结果待核对'
}[value] || formatDash(value))

const mesProductionRowTagType = (value) => ({
  SUCCESS: 'success',
  FAILED: 'danger',
  SKIPPED: 'info',
  RESULT_UNKNOWN: 'warning'
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

const formatProcessRoute = (route) => formatProcessRouteSnapshot(route)

onMounted(() => {
  syncFiltersFromRouteQuery()
  loadItems()
})
</script>

<style scoped>
.production-filter-toolbar {
  align-items: stretch;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
  box-sizing: border-box;
}

.production-toolbar-main {
  display: flex;
  width: 100%;
  gap: 12px;
  row-gap: 8px;
  column-gap: 10px;
  align-items: flex-start;
  justify-content: space-between;
  box-sizing: border-box;
}

.production-primary-filters {
  flex: 1 1 680px;
  min-width: 0;
  justify-content: flex-start;
  row-gap: 8px;
  column-gap: 10px;
  box-sizing: border-box;
}

.production-batch-actions {
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

.production-advanced-filters {
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
  width: 140px;
  flex: 0 0 140px;
}


.production-status-select {
  width: 150px;
  flex-basis: 150px;
}

.filter-date-range {
  width: 250px;
  flex: 0 0 250px;
}

.production-table-surface .erp-table-body {
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

.mes-production-result-actions,
.mes-production-result-stats,
.mes-production-result-details {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.mes-production-result-actions {
  justify-content: flex-end;
}

.mes-production-result-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  padding: 0 var(--erp-space-3) var(--erp-space-3);
}

.mes-production-result-stat {
  min-width: 0;
  padding: 10px 12px;
  border: 1px solid var(--erp-border-light);
  border-radius: var(--erp-radius-sm);
  background: var(--erp-bg-soft);
}

.mes-production-result-stat span {
  display: block;
  color: var(--erp-text-secondary);
  font-size: var(--erp-font-size-sm);
}

.mes-production-result-stat strong {
  display: block;
  margin-top: 4px;
  color: var(--erp-text-primary);
  font-size: 18px;
  line-height: 1.2;
}

.mes-production-result-details {
  display: grid;
  gap: 8px;
  max-width: 100%;
  padding: 0 var(--erp-space-3) var(--erp-space-3);
  overflow-x: auto;
}

.mes-production-result-row {
  display: grid;
  grid-template-columns: minmax(88px, 0.8fr) minmax(170px, 1.4fr) minmax(150px, 1.2fr) minmax(130px, 1fr) auto minmax(220px, 2fr) auto;
  min-width: 900px;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border: 1px solid var(--erp-border-light);
  border-radius: var(--erp-radius-sm);
  background: var(--erp-bg-soft);
  color: var(--erp-text-secondary);
  font-size: var(--erp-font-size-sm);
}

.mes-production-result-message {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  word-break: break-word;
}

.result-row-code {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  word-break: break-word;
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
  .production-toolbar-main {
    flex-direction: column;
    align-items: stretch;
  }

  .production-primary-filters,
  .production-batch-actions {
    justify-content: flex-start;
  }

  .mes-production-result-header,
  .mes-production-result-actions {
    justify-content: flex-start;
  }

  .production-batch-actions {
    width: 100%;
    padding-left: 0;
    border-left: 0;
  }
}

@media (max-width: 720px) {
  .production-primary-filters .customer-picker-trigger,
  .production-primary-filters .filter-input,
  .production-primary-filters .filter-select,
  .production-advanced-filters .filter-input,
  .production-advanced-filters .filter-select {
    width: auto;
    min-width: 120px;
    flex: 1 1 140px;
  }

  .filter-date-range {
    width: 100%;
    flex: 1 1 100%;
  }

  .mes-production-result-row {
    grid-template-columns: 1fr;
    min-width: 0;
    align-items: flex-start;
  }

  .result-row-code,
  .mes-production-result-message {
    white-space: normal;
  }
}
</style>
