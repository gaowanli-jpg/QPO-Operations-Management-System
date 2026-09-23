<template>
  <section class="erp-page linked-change-page">
    <header class="erp-page-header">
      <div>
        <h1 class="erp-page-title">关联变更</h1>
        <p class="erp-page-description">跟踪关联废弃涉及的下游动作和人工通知状态。</p>
      </div>
      <div class="erp-page-actions">
        <el-tag type="warning" effect="plain">当前页未处理 {{ pendingTotal }}</el-tag>
        <el-button :icon="Refresh" :loading="loading" @click="loadRows">刷新</el-button>
      </div>
    </header>

    <section class="erp-toolbar linked-change-filter-toolbar">
      <div class="linked-change-toolbar-main">
        <div class="erp-toolbar-group linked-change-primary-filters">
          <el-button v-permission="'change:linked-change:customer-select'" class="customer-picker-trigger" @click="customerPickerVisible = true">
            <span class="customer-picker-content">
              <span
                :class="filters.customerName ? 'customer-picker-text' : 'customer-picker-placeholder'"
                :title="filters.customerName || '选择客户'"
              >
                {{ filters.customerName || '选择客户' }}
              </span>
              <span class="customer-picker-action">选择客户</span>
            </span>
          </el-button>
          <el-button v-if="filters.customerName" plain @click="clearCustomer">清除客户</el-button>
          <el-input v-model="filters.salesOrderNo" clearable placeholder="销售订单号" class="filter-input" @keyup.enter="search" @clear="search" />
          <el-input v-model="filters.partNo" clearable placeholder="图号" class="filter-input" @keyup.enter="search" @clear="search" />
          <el-input v-model="filters.version" clearable placeholder="版本" class="filter-input small" @keyup.enter="search" @clear="search" />
          <el-select v-model="filters.handlingStatus" class="filter-select" placeholder="通知状态" @change="search">
            <el-option label="未处理" value="UNHANDLED" />
            <el-option label="已处理" value="HANDLED" />
            <el-option label="全部" value="" />
          </el-select>
          <el-select v-model="impactFilter" class="filter-select" placeholder="下游影响" @change="search">
            <el-option label="有影响" value="true" />
            <el-option label="全部" value="" />
            <el-option label="无影响" value="false" />
          </el-select>
          <el-button type="primary" :icon="Search" @click="search">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
          <el-button plain @click="advancedFiltersVisible = !advancedFiltersVisible">
            {{ advancedFiltersVisible ? '收起筛选' : '高级筛选' }}
          </el-button>
        </div>
      </div>

      <div v-if="advancedFiltersVisible" class="erp-toolbar-group linked-change-advanced-filters">
        <el-input v-model="filters.customerCode" clearable placeholder="客户编码" class="filter-input" @keyup.enter="search" @clear="search" />
        <el-input v-model="filters.customerOrderNo" clearable placeholder="客户订单号" class="filter-input" @keyup.enter="search" @clear="search" />
        <el-input v-model="filters.internalProjectNo" clearable placeholder="内部项目号" class="filter-input" @keyup.enter="search" @clear="search" />
        <el-input v-model="filters.keyword" clearable placeholder="客户/订单/图号/版本" class="filter-input" @keyup.enter="search" @clear="search" />
        <el-select v-model="filters.triggerScope" class="filter-select" placeholder="触发来源" @change="search">
          <el-option label="全部" value="" />
          <el-option label="零件级" value="ITEM" />
          <el-option label="订单级" value="ORDER" />
        </el-select>
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          range-separator="至"
          clearable
          class="filter-date-range"
          @change="search"
          @clear="search"
        />
      </div>
    </section>

    <section class="erp-table-surface linked-change-table-wrap" v-loading="loading">
      <el-table :data="rows" border stripe height="min(68vh, 720px)" empty-text="暂无关联变更记录">
        <el-table-column prop="customerName" label="客户" min-width="150" show-overflow-tooltip />
        <el-table-column prop="salesOrderNo" label="销售订单号" min-width="170" show-overflow-tooltip />
        <el-table-column prop="lineNo" label="行号" width="76" align="center" />
        <el-table-column prop="partNo" label="图号" min-width="150" show-overflow-tooltip />
        <el-table-column prop="version" label="版本" width="88" />
        <el-table-column prop="triggerScope" label="来源" width="88" align="center">
          <template #default="{ row }">{{ row.triggerScope === 'ORDER' ? '订单级' : '零件级' }}</template>
        </el-table-column>
        <el-table-column label="WMS 锁库" min-width="132" align="center">
          <template #default="{ row }"><status-cell :submitted="row.wmsSubmitted" :status="row.wmsStatus" /></template>
        </el-table-column>
        <el-table-column label="MES 生产" min-width="132" align="center">
          <template #default="{ row }"><status-cell :submitted="row.mesProductionSubmitted" :status="row.mesProductionStatus" /></template>
        </el-table-column>
        <el-table-column label="锁库库存工单" min-width="150" align="center">
          <template #default="{ row }"><status-cell :submitted="row.stockPickingSubmitted" :status="row.stockPickingStatus" /></template>
        </el-table-column>
        <el-table-column prop="linkedDiscardedAt" label="关联废弃时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.linkedDiscardedAt) }}</template>
        </el-table-column>
        <el-table-column label="关联废弃人" width="110" align="center">
          <template #default="{ row }">{{ row.linkedDiscardedBy ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="通知状态" width="158" align="center">
          <template #default="{ row }">
            <el-tooltip v-if="row.handlingStatus === 'HANDLED'" :content="handledSummary(row)" placement="top">
              <el-tag type="success">已处理</el-tag>
            </el-tooltip>
            <el-tag v-else type="warning">未处理</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="showDetail(row)">详情</el-button>
            <el-button
              v-if="canHandle"
              link
              :type="row.handlingStatus === 'HANDLED' ? 'warning' : 'primary'"
              :loading="busyId === row.id"
              @click="toggleHandled(row)"
            >{{ row.handlingStatus === 'HANDLED' ? '恢复未处理' : '标记已处理' }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="erp-pagination">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :page-sizes="[20, 50, 100]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @change="loadRows"
        />
      </div>
    </section>

    <el-drawer v-model="detailVisible" title="关联变更详情" size="min(620px, 94vw)">
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="客户">{{ detail.customerName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="客户编码">{{ detail.customerCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="销售订单号">{{ detail.salesOrderNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="客户订单号">{{ detail.customerOrderNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="内部项目号">{{ detail.internalProjectNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="零件行号">{{ detail.lineNo ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="图号">{{ detail.partNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="版本">{{ detail.version || '-' }}</el-descriptions-item>
          <el-descriptions-item label="物料描述">{{ detail.description || '-' }}</el-descriptions-item>
          <el-descriptions-item label="材料名称">{{ detail.materialName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="触发来源">{{ detail.triggerScope === 'ORDER' ? '订单级' : '零件级' }}</el-descriptions-item>
          <el-descriptions-item label="关联废弃人 ID">{{ detail.linkedDiscardedBy ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="关联废弃时间">{{ formatDateTime(detail.linkedDiscardedAt) }}</el-descriptions-item>
          <el-descriptions-item label="关联废弃原因" :span="2">{{ detail.linkedDiscardReason || '-' }}</el-descriptions-item>
        </el-descriptions>

        <el-alert
          class="detail-boundary-alert"
          title="本页记录历史影响和人工通知状态，不会取消或回滚 WMS、MES、库存工单。"
          type="info"
          :closable="false"
        />

        <h2 class="detail-heading">下游动作快照</h2>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="WMS 锁库">
            {{ impactSummary(detail.wmsSubmitted, detail.wmsStatus) }}
            <span v-if="detail.wmsActionAt"> · {{ formatDateTime(detail.wmsActionAt) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="MES 生产">
            {{ impactSummary(detail.mesProductionSubmitted, detail.mesProductionStatus) }}
            <span v-if="detail.mesProductionRequestId"> · {{ detail.mesProductionRequestId }}</span>
            <span v-if="detail.mesProductionActionAt"> · {{ formatDateTime(detail.mesProductionActionAt) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="锁库库存工单">
            {{ impactSummary(detail.stockPickingSubmitted, detail.stockPickingStatus) }}
            <span v-if="detail.stockPickingBizId"> · {{ detail.stockPickingBizId }}</span>
            <span v-if="detail.stockPickingActionAt"> · {{ formatDateTime(detail.stockPickingActionAt) }}</span>
          </el-descriptions-item>
        </el-descriptions>

        <h2 class="detail-heading">人工通知</h2>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="状态">{{ detail.handlingStatus === 'HANDLED' ? '已处理' : '未处理' }}</el-descriptions-item>
          <el-descriptions-item label="处理时间">{{ formatDateTime(detail.handledAt) }}</el-descriptions-item>
          <el-descriptions-item label="处理人 ID">{{ detail.handledBy ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="处理备注" :span="2">{{ detail.handleRemark || '-' }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-drawer>

    <CustomerPickerDialog
      v-model="customerPickerVisible"
      :current-customer="filters.customerName"
      :current-customer-code="filters.customerCode"
      customer-scope="linked-change"
      @select="handleCustomerSelected"
    />

    <el-dialog v-model="handleDialogVisible" title="标记人工通知已处理" width="480px">
      <el-form label-position="top">
        <el-form-item label="通知备注">
          <el-input v-model="handleRemark" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="可填写通知对象、方式或外部处理单号" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="busyId !== null" @click="confirmHandle">确认已处理</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, defineComponent, h, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, ElTag } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/authStore'
import { handleLinkedChange, listLinkedChanges, reopenLinkedChange } from '../services/linkedChangeApi'
import CustomerPickerDialog from './CustomerPickerDialog.vue'

const VIEW_PERMISSION = 'change:linked-change:view'
const HANDLE_PERMISSION = 'change:linked-change:handle'
const StatusCell = defineComponent({
  props: { submitted: Boolean, status: String },
  setup(props) {
    return () => {
      const type = !props.submitted ? 'info' : props.status === 'SUCCESS' ? 'success' : props.status === 'FAILED' ? 'danger' : 'warning'
      const text = !props.submitted ? '未发生' : props.status === 'SUCCESS' ? '成功' : props.status === 'FAILED' ? '失败' : props.status === 'PENDING' ? '待处理' : props.status === 'NOT_OCCURRED' ? '未发生' : props.status || '未知'
      return h(ElTag, { type, size: 'small' }, () => text)
    }
  }
})

const authStore = useAuthStore()
const canHandle = computed(() => authStore.hasPermission(HANDLE_PERMISSION))
const loading = ref(false)
const busyId = ref(null)
const rows = ref([])
const dateRange = ref([])
const impactFilter = ref('true')
const advancedFiltersVisible = ref(false)
const customerPickerVisible = ref(false)
const filters = reactive({
  customerId: undefined,
  customerCode: '',
  customerName: '',
  customerOrderNo: '',
  internalProjectNo: '',
  salesOrderNo: '',
  keyword: '',
  partNo: '',
  version: '',
  triggerScope: '',
  handlingStatus: 'UNHANDLED'
})
const pagination = reactive({ page: 1, pageSize: 20, total: 0 })
const detailVisible = ref(false)
const detail = ref(null)
const handleDialogVisible = ref(false)
const handleRemark = ref('')
const pendingTotal = computed(() => rows.value.filter(row => row.handlingStatus === 'UNHANDLED').length)
const requestParams = () => {
  const { customerName, ...queryFilters } = filters
  return {
  ...queryFilters,
  hasDownstreamImpact: impactFilter.value === '' ? undefined : impactFilter.value,
  dateFrom: dateRange.value?.[0] || undefined,
  dateTo: dateRange.value?.[1] || undefined,
  page: pagination.page,
  pageSize: pagination.pageSize
  }
}

const loadRows = async () => {
  if (loading.value) return
  loading.value = true
  try {
    const result = await listLinkedChanges(requestParams())
    rows.value = result?.records || []
    pagination.total = Number(result?.total || 0)
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '关联变更加载失败')
  } finally {
    loading.value = false
  }
}

const search = () => {
  pagination.page = 1
  loadRows()
}

const resetFilters = () => {
  Object.assign(filters, {
    customerId: undefined,
    customerCode: '',
    customerName: '',
    customerOrderNo: '',
    internalProjectNo: '',
    salesOrderNo: '',
    keyword: '',
    partNo: '',
    version: '',
    triggerScope: '',
    handlingStatus: 'UNHANDLED'
  })
  dateRange.value = []
  impactFilter.value = 'true'
  advancedFiltersVisible.value = false
  search()
}

const handleCustomerSelected = (customer) => {
  filters.customerId = customer?.id || undefined
  filters.customerCode = customer?.customerCode || ''
  filters.customerName = customer?.customerName || customer?.customerShortName || filters.customerCode
  search()
}

const clearCustomer = () => {
  filters.customerId = undefined
  filters.customerCode = ''
  filters.customerName = ''
  search()
}

const showDetail = async (row) => {
  try {
    detail.value = await (await import('../services/linkedChangeApi')).getLinkedChange(row.id)
    detailVisible.value = true
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '关联变更详情加载失败')
  }
}

const toggleHandled = async (row) => {
  if (row.handlingStatus === 'HANDLED') {
    try {
      await ElMessageBox.confirm('将此记录恢复为未处理状态？下游动作不会回滚。', '恢复未处理', { type: 'warning' })
      busyId.value = row.id
      await reopenLinkedChange(row.id, {})
      ElMessage.success('已恢复为未处理')
      await loadRows()
      if (detail.value?.id === row.id) detail.value = await (await import('../services/linkedChangeApi')).getLinkedChange(row.id)
    } catch (error) {
      if (error !== 'cancel') ElMessage.error(error?.response?.data?.message || '操作失败')
    } finally {
      busyId.value = null
    }
    return
  }
  detail.value = row
  handleRemark.value = ''
  handleDialogVisible.value = true
}

const confirmHandle = async () => {
  const row = detail.value
  if (!row || busyId.value !== null) return
  busyId.value = row.id
  try {
    await handleLinkedChange(row.id, { remark: handleRemark.value.trim() || null })
    handleDialogVisible.value = false
    ElMessage.success('已标记人工通知完成')
    await loadRows()
    if (detailVisible.value) detail.value = await (await import('../services/linkedChangeApi')).getLinkedChange(row.id)
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '标记处理失败')
  } finally {
    busyId.value = null
  }
}

const impactSummary = (submitted, status) => submitted ? (status || '状态未知') : '未发生'
const handledSummary = (row) => `${row.handledBy ? `处理人 ID ${row.handledBy}` : '处理人未知'} · ${formatDateTime(row.handledAt)}`
const formatDateTime = (value) => value ? String(value).replace('T', ' ').slice(0, 19) : '-'

onMounted(() => {
  if (authStore.hasPermission(VIEW_PERMISSION)) loadRows()
})
</script>

<style scoped>
.linked-change-page { gap: var(--erp-space-4); }
.linked-change-filter-toolbar {
  align-items: stretch;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
  box-sizing: border-box;
}

.linked-change-toolbar-main {
  display: flex;
  width: 100%;
  gap: 12px;
  align-items: flex-start;
  justify-content: space-between;
  box-sizing: border-box;
}

.linked-change-primary-filters,
.linked-change-advanced-filters {
  width: 100%;
  justify-content: flex-start;
  row-gap: 8px;
  column-gap: 10px;
  box-sizing: border-box;
}

.linked-change-advanced-filters {
  padding: 12px;
  border: 1px solid var(--erp-border-soft);
  border-radius: 6px;
  background: var(--erp-surface-soft);
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

.filter-date-range {
  width: 250px;
  flex: 0 0 250px;
}

.linked-change-table-wrap { padding: var(--erp-space-4); }
.erp-pagination { display: flex; justify-content: flex-end; margin-top: var(--erp-space-3); }
.detail-boundary-alert { margin-top: var(--erp-space-4); }
.detail-heading { margin: var(--erp-space-5) 0 var(--erp-space-3); font-size: var(--erp-font-size-md); }

@media (max-width: 1200px) {
  .linked-change-toolbar-main {
    flex-direction: column;
    align-items: stretch;
  }
}

@media (max-width: 720px) {
  .linked-change-primary-filters .customer-picker-trigger,
  .linked-change-primary-filters .filter-input,
  .linked-change-primary-filters .filter-select,
  .linked-change-advanced-filters .filter-input,
  .linked-change-advanced-filters .filter-select {
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
