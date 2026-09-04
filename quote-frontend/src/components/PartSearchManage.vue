<template>
  <div class="erp-page part-search-page">
    <header class="erp-page-header">
      <div>
        <h1 class="erp-page-title">零件查询</h1>
        <p class="erp-page-description">按图号精确查看询价零件和销售零件。</p>
      </div>
    </header>

    <section class="erp-toolbar part-search-toolbar">
      <div class="erp-toolbar-group part-search-filters">
        <el-input
          v-model="filters.partNo"
          clearable
          placeholder="图号（必填，精确查询）"
          class="part-search-input"
          @keyup.enter="handleSearch"
          @clear="clearResults"
        />
        <el-input
          v-model="filters.version"
          clearable
          placeholder="版本（可选，精确查询）"
          class="part-search-version"
          @keyup.enter="handleSearch"
          @clear="handleVersionClear"
        />
        <el-button type="primary" :loading="loading" @click="handleSearch">查询</el-button>
        <el-button plain @click="resetFilters">重置</el-button>
      </div>
    </section>

    <section class="erp-table-surface part-search-table-surface">
      <el-tabs v-model="activeSource" class="part-search-tabs" @tab-change="handleTabChange">
        <el-tab-pane label="询价零件" name="rfq">
          <el-table :data="rows" border stripe height="560" v-loading="loading" empty-text="请输入图号查询">
            <el-table-column prop="partNo" label="图号" min-width="150" fixed show-overflow-tooltip />
            <el-table-column prop="version" label="版本" width="90" align="center" show-overflow-tooltip />
            <el-table-column label="未税单价" min-width="120" align="right">
              <template #default="scope">{{ formatPrice(scope.row.untaxedUnitPrice) }}</template>
            </el-table-column>
            <el-table-column label="含税单价" min-width="120" align="right">
              <template #default="scope">{{ formatPrice(scope.row.taxIncludedUnitPrice) }}</template>
            </el-table-column>
            <el-table-column prop="orderNo" label="所属订单号" min-width="180" show-overflow-tooltip />
            <el-table-column prop="itemStatus" label="状态" width="120" align="center" show-overflow-tooltip>
              <template #default="scope">
                <el-tag size="small">{{ scope.row.itemStatus || '-' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="详情" width="96" align="center" fixed="right">
              <template #default="scope">
                <el-button size="small" @click="openDetail(scope.row)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="销售零件" name="sales">
          <el-table :data="rows" border stripe height="560" v-loading="loading" empty-text="请输入图号查询">
            <el-table-column prop="partNo" label="图号" min-width="150" fixed show-overflow-tooltip />
            <el-table-column prop="version" label="版本" width="90" align="center" show-overflow-tooltip />
            <el-table-column label="未税单价" min-width="120" align="right">
              <template #default="scope">{{ formatPrice(scope.row.untaxedUnitPrice) }}</template>
            </el-table-column>
            <el-table-column label="含税单价" min-width="120" align="right">
              <template #default="scope">{{ formatPrice(scope.row.taxIncludedUnitPrice) }}</template>
            </el-table-column>
            <el-table-column prop="orderNo" label="所属订单号" min-width="180" show-overflow-tooltip />
            <el-table-column prop="itemStatus" label="状态" width="120" align="center" show-overflow-tooltip>
              <template #default="scope">
                <el-tag size="small">{{ scope.row.itemStatus || '-' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="详情" width="96" align="center" fixed="right">
              <template #default="scope">
                <el-button size="small" @click="openDetail(scope.row)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>

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

    <el-drawer v-model="detailVisible" title="零件详情" size="68%">
      <div v-loading="detailLoading" class="part-search-detail">
        <template v-if="detail">
          <h3 class="detail-section-title">所属订单</h3>
          <el-descriptions :column="2" border class="detail-descriptions">
            <el-descriptions-item v-for="item in orderSummaryEntries" :key="item.key" :label="item.label">
              {{ item.value }}
            </el-descriptions-item>
          </el-descriptions>

          <h3 class="detail-section-title">零件字段</h3>
          <el-table :data="itemDetailEntries" border stripe max-height="520" class="detail-field-table">
            <el-table-column prop="label" label="字段" width="220" show-overflow-tooltip />
            <el-table-column prop="value" label="内容" show-overflow-tooltip />
          </el-table>
        </template>
        <el-empty v-else description="请选择零件查看详情" />
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getRfqPartSearchItem,
  getSalesPartSearchItem,
  listRfqPartSearchItems,
  listSalesPartSearchItems
} from '../services/partSearchApi'

const activeSource = ref('rfq')
const filters = reactive({ partNo: '', version: '' })
const rows = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const loading = ref(false)
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)

const ORDER_SUMMARY_LABELS = {
  id: '订单ID',
  orderNo: '所属订单号',
  status: '订单状态',
  documentNo: 'U9单据号',
  documentTypeCode: '单据类型',
  customerCode: '客户编码',
  customerName: '客户名称',
  customerShortName: '客户简称',
  customerOrderNo: '客户订单号',
  customerProjectNo: '客户项目号',
  internalProjectNo: '内部项目号',
  orderType: '订单类型',
  u9SyncStatus: 'U9状态',
  createdAt: '创建时间',
  updatedAt: '更新时间'
}

const ITEM_FIELD_LABELS = {
  id: '零件ID',
  rfqId: '询价订单ID',
  salesOrderId: '销售订单ID',
  lineNo: '行号',
  partNo: '图号',
  version: '版本',
  description: '物料描述',
  materialName: '材料名称',
  materialNo: '物料号',
  materialNoStatus: '物料号状态',
  qty: '数量',
  orderType: '订单类型',
  customerOrderNo: '客户订单号',
  customerProjectNo: '客户项目号',
  internalProjectNo: '内部项目号',
  engineeringMaterialName: '核价材料',
  materialShape: '材料形态',
  surfaceTreatment: '表面处理',
  leadTime: '交货周期',
  internalTargetLeadTime: '内部目标交期',
  untaxedUnitPrice: '未税单价',
  taxIncludedUnitPrice: '含税单价',
  untaxedTotalPrice: '未税总价',
  taxIncludedTotalPrice: '含税总价',
  status: '状态',
  drawingUrl: '图纸状态',
  remark: '备注'
}

const orderSummaryEntries = computed(() => toEntries(detail.value?.orderSummary || {}, ORDER_SUMMARY_LABELS))
const itemDetailEntries = computed(() => toEntries(detail.value?.itemDetail || {}, ITEM_FIELD_LABELS))

watch([page, pageSize], () => {
  if (filters.partNo.trim()) {
    loadRows()
  }
})

const loadRows = async () => {
  const partNo = filters.partNo.trim()
  if (!partNo) {
    ElMessage.warning('请输入图号')
    return
  }
  loading.value = true
  try {
    const params = {
      partNo,
      version: filters.version.trim() || undefined,
      page: page.value,
      pageSize: pageSize.value
    }
    const response = activeSource.value === 'sales'
      ? await listSalesPartSearchItems(params)
      : await listRfqPartSearchItems(params)
    rows.value = response.records || []
    total.value = Number(response.total || 0)
  } catch (error) {
    console.error(error)
    ElMessage.error(error.response?.data?.message || error.message || '零件查询失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = async () => {
  page.value = 1
  await loadRows()
}

const handleTabChange = async () => {
  rows.value = []
  total.value = 0
  detail.value = null
  detailVisible.value = false
  page.value = 1
  if (filters.partNo.trim()) {
    await loadRows()
  }
}

const handleVersionClear = async () => {
  if (filters.partNo.trim()) {
    await handleSearch()
  }
}

const clearResults = () => {
  rows.value = []
  total.value = 0
  detail.value = null
  detailVisible.value = false
}

const resetFilters = () => {
  filters.partNo = ''
  filters.version = ''
  page.value = 1
  clearResults()
}

const openDetail = async (row) => {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = activeSource.value === 'sales'
      ? await getSalesPartSearchItem(row.id)
      : await getRfqPartSearchItem(row.id)
  } catch (error) {
    console.error(error)
    ElMessage.error(error.response?.data?.message || error.message || '零件详情加载失败')
  } finally {
    detailLoading.value = false
  }
}

const formatPrice = (value) => {
  if (value === null || value === undefined || value === '') return '-'
  const number = Number(value)
  return Number.isFinite(number) ? number.toFixed(4) : String(value)
}

const toEntries = (source, labels) => Object.entries(source)
  .filter(([, value]) => value !== undefined)
  .map(([key, value]) => ({
    key,
    label: labels[key] || key,
    value: formatFieldValue(key, value)
  }))

const formatFieldValue = (key, value) => {
  if (value === null || value === undefined || value === '') return '-'
  if (key === 'drawingUrl') return value ? '有图' : '无图'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}
</script>

<style scoped>
.part-search-page {
  min-width: 0;
}

.part-search-toolbar {
  align-items: flex-start;
}

.part-search-filters {
  flex-wrap: wrap;
}

.part-search-input {
  width: 260px;
}

.part-search-version {
  width: 180px;
}

.part-search-table-surface {
  padding: var(--erp-space-4);
}

.part-search-tabs :deep(.el-tabs__content) {
  padding-top: var(--erp-space-2);
}

.part-search-detail {
  min-height: 360px;
}

.detail-section-title {
  margin: var(--erp-space-4) 0 var(--erp-space-3);
  color: var(--erp-text);
  font-size: var(--erp-font-size-base);
  font-weight: var(--erp-font-weight-bold);
}

.detail-section-title:first-child {
  margin-top: 0;
}

.detail-field-table {
  margin-top: var(--erp-space-2);
}

@media (max-width: 760px) {
  .part-search-input,
  .part-search-version {
    width: 100%;
  }
}
</style>
