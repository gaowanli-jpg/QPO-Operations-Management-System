<template>
  <section class="erp-page sales-order-change-page">
    <header class="erp-page-header sales-order-change-header">
      <div>
        <h1 class="erp-page-title">销售变更</h1>
        <p class="erp-page-description">校正已同步 U9 后的 QPO 本地销售订单零件数据。</p>
      </div>
      <div class="erp-page-actions sales-order-change-header-actions">
        <el-button
          v-permission="SALES_ORDER_CHANGE_ORDER_SELECT_PERMISSION"
          type="primary"
          :icon="DocumentChecked"
          @click="openOrderPicker"
        >
          选择销售订单
        </el-button>
        <el-button
          v-permission="SALES_ORDER_CHANGE_VIEW_PERMISSION"
          :loading="previewLoading"
          @click="refreshCurrentState"
        >
          刷新
        </el-button>
      </div>
    </header>

    <el-alert
      class="sales-order-change-warning"
      title="本功能仅修改 QPO 本地销售订单零件数据，不会同步到 U9。请确认 U9 已完成对应调整后再保存。"
      type="warning"
      show-icon
      :closable="false"
    />

    <section class="erp-surface workflow-workspace sales-order-change-workspace">
      <aside class="erp-surface sales-order-change-side-panel">
        <div class="panel-title">销售订单</div>
        <div class="selected-order-text" :title="selectedOrderLabel">
          {{ selectedOrderLabel }}
        </div>

        <div class="selected-order-meta">
          <div class="meta-row">
            <span>客户</span>
            <strong>{{ orderField('customerShortName') || orderField('customerName') }}</strong>
          </div>
          <div class="meta-row">
            <span>下单日期</span>
            <strong>{{ formatDate(orderField('salesOrderDate')) }}</strong>
          </div>
          <div class="meta-row">
            <span>订单类型</span>
            <strong>{{ orderField('orderType') }}</strong>
          </div>
          <div class="meta-row">
            <span>内部项目号</span>
            <strong>{{ orderField('internalProjectNo') }}</strong>
          </div>
          <div class="meta-row">
            <span>订单状态</span>
            <el-tag :type="orderStatusTagType(orderField('status'))" size="small">
              {{ orderField('status') }}
            </el-tag>
          </div>
          <div class="meta-row">
            <span>U9 状态</span>
            <el-tag :type="u9StatusTagType(orderField('u9SyncStatus'))" size="small">
              {{ orderField('u9SyncStatus') }}
            </el-tag>
          </div>
          <div class="meta-row">
            <span>U9 编码</span>
            <strong>{{ orderField('u9OrderCode') }}</strong>
          </div>
        </div>

        <el-alert
          class="sales-order-change-side-tip"
          title="本页面只做 QPO 本地校正，不会调用 U9。请确认 U9 已经完成对应人工调整。"
          type="info"
          :closable="false"
        />
      </aside>

      <main class="sales-order-change-main">
        <div class="erp-section-title-row">
          <div>
            <h2 class="erp-section-title">零件明细</h2>
            <p class="erp-section-description">第一阶段仅提供只读预览，保存和表格替换将在后续阶段开放。</p>
          </div>
          <el-tag type="info">{{ previewLines.length }} 行</el-tag>
        </div>

        <el-table
          :data="previewLines"
          border
          stripe
          height="560"
          v-loading="previewLoading"
          empty-text="请选择已同步 U9 的销售订单"
          class="sales-order-change-table"
        >
          <el-table-column prop="lineNo" label="行号" width="80" align="center" />
          <el-table-column prop="partNo" label="图号" min-width="150" show-overflow-tooltip />
          <el-table-column prop="version" label="版本" width="90" show-overflow-tooltip />
          <el-table-column prop="materialNo" label="物料号" min-width="150" show-overflow-tooltip />
          <el-table-column prop="materialNoStatus" label="物料号状态" width="120" align="center">
            <template #default="{ row }">
              <el-tag :type="row.materialNoStatus === '已确认' ? 'success' : 'info'" size="small">
                {{ row.materialNoStatus || '-' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="qty" label="数量" width="90" align="right" />
          <el-table-column prop="untaxedUnitPrice" label="未税单价" width="120" align="right">
            <template #default="{ row }">{{ formatMoney(row.untaxedUnitPrice) }}</template>
          </el-table-column>
          <el-table-column prop="untaxedTotalPrice" label="未税金额" width="120" align="right">
            <template #default="{ row }">{{ formatMoney(row.untaxedTotalPrice) }}</template>
          </el-table-column>
          <el-table-column prop="customerDeliveryDate" label="客户交期" width="120">
            <template #default="{ row }">{{ formatDate(row.customerDeliveryDate) }}</template>
          </el-table-column>
          <el-table-column prop="internalDeliveryDate" label="内部交期" width="120">
            <template #default="{ row }">{{ formatDate(row.internalDeliveryDate) }}</template>
          </el-table-column>
          <el-table-column prop="customerOrderNo" label="客户订单号" min-width="140" show-overflow-tooltip />
          <el-table-column prop="customerProjectNo" label="客户项目号" min-width="140" show-overflow-tooltip />
          <el-table-column prop="internalProjectNo" label="内部项目号" min-width="140" show-overflow-tooltip />
          <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
        </el-table>
      </main>
    </section>

    <OrderPickerDialog
      v-model="orderPickerVisible"
      title="选择销售变更订单"
      customer-scope="sales-order-change"
      order-scope="sales-order-change"
      @select="handleOrderSelected"
    />
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { DocumentChecked } from '@element-plus/icons-vue'
import OrderPickerDialog from './OrderPickerDialog.vue'
import { listSalesOrderChangeOrders, previewSalesOrderChange } from '../services/salesOrderChangeApi'

const SALES_ORDER_CHANGE_VIEW_PERMISSION = 'change:sales-order:view'
const SALES_ORDER_CHANGE_ORDER_SELECT_PERMISSION = 'change:sales-order:order-select'

const route = useRoute()
const orderPickerVisible = ref(false)
const previewLoading = ref(false)
const selectedOrderId = ref(null)
const selectedOrderSummary = ref(null)
const preview = ref(null)

const previewLines = computed(() => preview.value?.lines || [])
const selectedOrderLabel = computed(() => (
  orderField('salesOrderNo') || selectedOrderSummary.value?.salesOrderNo || '请选择销售订单'
))

const openOrderPicker = () => {
  orderPickerVisible.value = true
}

const handleOrderSelected = async (order) => {
  selectedOrderSummary.value = order
  selectedOrderId.value = resolveSalesOrderId(order)
  if (!selectedOrderId.value) {
    ElMessage.error('未识别销售订单 ID')
    return
  }
  await loadPreview()
}

const refreshCurrentState = async () => {
  if (!selectedOrderId.value) {
    ElMessage.warning('请先选择销售订单')
    return
  }
  await loadPreview()
}

const loadPreview = async () => {
  if (!selectedOrderId.value || previewLoading.value) return
  previewLoading.value = true
  try {
    preview.value = await previewSalesOrderChange(selectedOrderId.value)
  } catch (error) {
    ElMessage.error(error.message || '销售变更预览加载失败')
  } finally {
    previewLoading.value = false
  }
}

watch(
  () => route.query.salesOrderId,
  async (value) => {
    if (!value) return
    selectedOrderId.value = value
    selectedOrderSummary.value = null
    await loadPreview()
  },
  { immediate: true }
)

const resolveSalesOrderId = (order) => order?.salesOrderId ?? order?.sourceOrderId ?? order?.id ?? null

const orderField = (field) => {
  const value = preview.value?.[field] ?? selectedOrderSummary.value?.[field]
  return value ?? '-'
}

const formatDate = (value) => {
  if (!value || value === '-') return '-'
  return String(value).slice(0, 10)
}

const formatMoney = (value) => {
  if (value === null || value === undefined || value === '') return '-'
  const number = Number(value)
  return Number.isFinite(number) ? number.toFixed(2) : String(value)
}

const orderStatusTagType = (status) => {
  if (status === '已生成提单') return 'success'
  if (status === '部分已提单') return 'warning'
  if (status === '已报价') return 'primary'
  return 'info'
}

const u9StatusTagType = (status) => status === '已同步' ? 'success' : 'info'

void listSalesOrderChangeOrders
</script>

<style scoped>
.sales-order-change-page {
  gap: var(--erp-space-4);
}

.sales-order-change-header-actions {
  flex-wrap: wrap;
}

.sales-order-change-warning {
  margin-bottom: var(--erp-space-4);
}

.sales-order-change-workspace {
  display: grid;
  grid-template-columns: minmax(260px, 320px) minmax(0, 1fr);
  gap: var(--erp-space-4);
  padding: var(--erp-space-4);
}

.sales-order-change-side-panel {
  align-self: start;
  padding: var(--erp-space-4);
}

.panel-title {
  color: var(--erp-text);
  font-size: var(--erp-font-size-md);
  font-weight: var(--erp-font-weight-bold);
}

.selected-order-text {
  margin-top: var(--erp-space-2);
  overflow: hidden;
  color: var(--erp-primary);
  font-size: var(--erp-font-size-lg);
  font-weight: var(--erp-font-weight-bold);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.selected-order-meta {
  display: grid;
  gap: var(--erp-space-2);
  margin-top: var(--erp-space-4);
}

.meta-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--erp-space-3);
  min-height: 30px;
}

.meta-row span {
  color: var(--erp-text-secondary);
  font-size: var(--erp-font-size-sm);
}

.meta-row strong {
  min-width: 0;
  overflow: hidden;
  color: var(--erp-text);
  font-size: var(--erp-font-size-sm);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sales-order-change-side-tip {
  margin-top: var(--erp-space-4);
}

.sales-order-change-main {
  min-width: 0;
}

.erp-section-title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--erp-space-3);
  margin-bottom: var(--erp-space-3);
}

.erp-section-title {
  margin: 0;
  color: var(--erp-text);
  font-size: var(--erp-font-size-lg);
}

.erp-section-description {
  margin: var(--erp-space-1) 0 0;
  color: var(--erp-text-secondary);
  font-size: var(--erp-font-size-sm);
}

@media (max-width: 960px) {
  .sales-order-change-workspace {
    grid-template-columns: 1fr;
  }
}
</style>
