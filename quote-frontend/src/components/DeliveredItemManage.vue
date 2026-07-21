<template>
  <div class="erp-page delivered-item-page" v-permission="DELIVERED_ITEM_VIEW_PERMISSION">
    <header class="erp-page-header">
      <div>
        <h1 class="erp-page-title">已提零件</h1>
        <p class="erp-page-description">查看已生成提单的零件需求池和 MES / WMS 基础状态。</p>
      </div>
    </header>

    <section class="erp-toolbar delivered-filter-toolbar">
      <div class="erp-toolbar-group delivered-filter-group">
        <el-input v-model="filters.customerName" clearable placeholder="客户" class="filter-input" @keyup.enter="handleSearch" @clear="handleSearch" />
        <el-input v-model="filters.rfqNo" clearable placeholder="RFQ号" class="filter-input" @keyup.enter="handleSearch" @clear="handleSearch" />
        <el-input v-model="filters.partNo" clearable placeholder="图号" class="filter-input" @keyup.enter="handleSearch" @clear="handleSearch" />
        <el-input v-model="filters.version" clearable placeholder="版本" class="filter-input small" @keyup.enter="handleSearch" @clear="handleSearch" />
        <el-input v-model="filters.materialNo" clearable placeholder="物料号" class="filter-input" @keyup.enter="handleSearch" @clear="handleSearch" />
        <el-input v-model="filters.internalProjectNo" clearable placeholder="内部项目号" class="filter-input" @keyup.enter="handleSearch" @clear="handleSearch" />
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
          start-placeholder="提单日期起"
          end-placeholder="提单日期止"
          class="filter-date-range"
          @change="handleSearch"
          @clear="handleSearch"
        />
        <el-button @click="handleSearch">搜索</el-button>
        <el-button type="primary" :loading="loading" @click="loadItems">刷新</el-button>
      </div>
    </section>

    <section class="erp-table-surface delivered-table-surface">
      <div class="erp-table-body">
        <el-table :data="items" border stripe height="600" v-loading="loading">
          <el-table-column prop="id" label="提单ID" width="96" fixed align="center" />
          <el-table-column prop="rfqNo" label="RFQ号" min-width="150" show-overflow-tooltip />
          <el-table-column prop="customerName" label="客户" min-width="150" show-overflow-tooltip />
          <el-table-column prop="partNo" label="图号" min-width="150" fixed show-overflow-tooltip />
          <el-table-column prop="version" label="版本" width="80" align="center">
            <template #default="{ row }">{{ row.version || '-' }}</template>
          </el-table-column>
          <el-table-column label="物料号" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ formatMaterialNo(row.materialNo) }}</template>
          </el-table-column>
          <el-table-column prop="qty" label="提单数量" width="100" align="right" />
          <el-table-column prop="internalProjectNo" label="内部项目号" min-width="140" show-overflow-tooltip />
          <el-table-column label="MES状态" width="110" align="center">
            <template #default="{ row }"><el-tag :type="statusTagType(row.mesSyncStatus)" size="small">{{ statusText(row.mesSyncStatus) }}</el-tag></template>
          </el-table-column>
          <el-table-column label="WMS状态" width="110" align="center">
            <template #default="{ row }"><el-tag :type="statusTagType(row.wmsAllocateStatus)" size="small">{{ statusText(row.wmsAllocateStatus) }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="deliveryDate" label="交货日期" width="120" />
          <el-table-column label="操作" width="170" align="center" fixed="right">
            <template #default="{ row }">
              <div class="erp-table-actions delivered-row-actions">
                <el-button size="small" @click="openDetail(row)">详情</el-button>
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
        />
      </div>
    </section>

    <el-drawer v-model="detailVisible" title="已提零件详情" size="62%">
      <div v-if="selectedItem" class="detail-wrap">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="提单ID">{{ selectedItem.id }}</el-descriptions-item>
          <el-descriptions-item label="RFQ号">{{ selectedItem.rfqNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="客户">{{ selectedItem.customerName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="图号">{{ selectedItem.partNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="版本">{{ selectedItem.version || '-' }}</el-descriptions-item>
          <el-descriptions-item label="物料号">{{ formatMaterialNo(selectedItem.materialNo) }}</el-descriptions-item>
          <el-descriptions-item label="MES状态">{{ statusText(selectedItem.mesSyncStatus) }}</el-descriptions-item>
          <el-descriptions-item label="WMS状态">{{ statusText(selectedItem.wmsAllocateStatus) }}</el-descriptions-item>
        </el-descriptions>

        <section class="detail-section">
          <h3>工艺快照</h3>
          <div class="route-preview">{{ formatProcessRoute(selectedItem.processRoute) }}</div>
        </section>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { openAuthenticatedFileInNewWindow } from '../api'
import { getDeliveredItem, listDeliveredItems } from '../services/deliveredItemApi'

const DELIVERED_ITEM_VIEW_PERMISSION = 'market:delivered-item:view'
const DELIVERED_ITEM_FILE_PREVIEW_PERMISSION = 'market:delivered-item:file-preview'

const filters = reactive({
  customerName: '',
  rfqNo: '',
  partNo: '',
  version: '',
  materialNo: '',
  internalProjectNo: '',
  mesSyncStatus: '',
  wmsAllocateStatus: '',
  dateRange: []
})
const syncStatusOptions = [
  { value: 'PENDING', label: '未处理' },
  { value: 'PROCESSING', label: '处理中' },
  { value: 'SUCCESS', label: '成功' },
  { value: 'FAILED', label: '失败' }
]
const items = ref([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const detailVisible = ref(false)
const selectedItem = ref(null)

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

const formatMaterialNo = (value) => value || '未获取'

const statusText = (value) => syncStatusOptions.find(option => option.value === value)?.label || '未处理'

const statusTagType = (value) => ({
  SUCCESS: 'success',
  FAILED: 'danger',
  PROCESSING: 'warning',
  PENDING: 'info'
}[value] || 'info')

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
}
</style>
