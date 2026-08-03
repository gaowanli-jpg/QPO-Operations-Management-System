<template>
  <section class="erp-page sales-match-page">
    <header class="erp-page-header sales-match-header">
      <div>
        <h1 class="erp-page-title">销售匹配</h1>
        <p class="erp-page-description">选择已报价销售订单，预览历史生产快照命中情况，并按整单补全空缺生产信息。</p>
      </div>
      <div class="erp-page-actions sales-match-header-actions">
        <el-button
          v-permission="SALES_MATCH_ORDER_SELECT_PERMISSION"
          :icon="DocumentChecked"
          type="primary"
          @click="openOrderPicker"
        >
          选择销售订单
        </el-button>
        <el-button :loading="previewLoading" @click="refreshCurrentState">刷新</el-button>
        <el-tooltip :disabled="!applyDisabledReason" :content="applyDisabledReason" placement="top">
          <span class="sales-match-action-wrap">
            <el-button
              v-permission="SALES_MATCH_APPLY_PERMISSION"
              type="success"
              :loading="isPending(salesMatchActionKeys.apply)"
              :disabled="!canApply"
              @click="applyMatch"
            >
              补全生产快照
            </el-button>
          </span>
        </el-tooltip>
      </div>
    </header>

    <section class="erp-surface workflow-workspace sales-match-workspace">
      <aside class="erp-surface sales-match-side-panel">
        <div class="panel-title">已选销售订单</div>
        <el-table
          class="selected-order-mini-table"
          :data="selectedOrderRows"
          height="132"
          size="small"
          border
          stripe
          empty-text="请先选择销售订单"
        >
          <el-table-column prop="salesOrderNo" label="销售订单号" min-width="128" show-overflow-tooltip />
          <el-table-column prop="status" label="状态" width="96" align="center">
            <template #default="{ row }">
              <el-tag :type="orderStatusTagType(row.status)" size="small">{{ row.status || '-' }}</el-tag>
            </template>
          </el-table-column>
        </el-table>

        <div class="match-condition-panel">
          <div class="panel-title compact">匹配条件</div>
          <div class="condition-row">
            <span>可处理状态</span>
            <el-tag type="primary" size="small">已报价</el-tag>
          </div>
          <div class="condition-row">
            <span>当前订单状态</span>
            <el-tag :type="orderStatusTagType(currentOrderStatus)" size="small">{{ currentOrderStatus }}</el-tag>
          </div>
          <div class="condition-row">
            <span>预览零件</span>
            <strong>{{ previewSummary.totalItemCount }}</strong>
          </div>
          <div class="condition-row">
            <span>命中快照</span>
            <strong>{{ previewSummary.matchedItemCount }} / {{ previewSummary.totalItemCount }}</strong>
          </div>
          <div class="condition-row">
            <span>未匹配</span>
            <strong class="warning-text">{{ previewSummary.unmatchedItemCount }}</strong>
          </div>
          <div class="condition-row">
            <span>补全状态</span>
            <el-tag :type="previewData ? 'success' : 'info'" size="small">
              {{ previewData ? '已预览' : '待选择' }}
            </el-tag>
          </div>
          <el-alert
            v-if="previewSummary.warningMessage"
            class="sales-match-alert"
            :title="previewSummary.warningMessage"
            type="warning"
            :closable="false"
            show-icon
          />
        </div>

        <div class="selected-order-meta">
          <div class="panel-title compact">订单信息</div>
          <dl>
            <div>
              <dt>客户</dt>
              <dd>{{ customerDisplay }}</dd>
            </div>
            <div>
              <dt>单据类型编码</dt>
              <dd>{{ orderField('documentTypeCode') }}</dd>
            </div>
            <div>
              <dt>客户订单号</dt>
              <dd>{{ orderField('customerOrderNo') }}</dd>
            </div>
            <div>
              <dt>客户项目号</dt>
              <dd>{{ orderField('customerProjectNo') }}</dd>
            </div>
            <div>
              <dt>内部项目号</dt>
              <dd>{{ orderField('internalProjectNo') }}</dd>
            </div>
            <div>
              <dt>更新时间</dt>
              <dd>{{ formatDateTime(orderValue('updatedAt')) }}</dd>
            </div>
          </dl>
        </div>

        <div class="match-result-panel">
          <div class="panel-title compact">补全结果</div>
          <el-empty v-if="!applyResult" description="暂无补全结果" :image-size="64" />
          <template v-else>
            <div class="match-result-summary">
              <el-tag :type="applyResult.success === false ? 'warning' : 'success'">
                {{ applyResult.success === false ? '已返回失败' : '已返回结果' }}
              </el-tag>
              <span>更新 {{ numberOrZero(applyResult.updatedItemCount) }}</span>
              <span>未匹配 {{ numberOrZero(applyResult.unmatchedItemCount) }}</span>
              <span>跳过 {{ numberOrZero(applyResult.skippedItemCount) }}</span>
            </div>
            <el-alert
              v-if="applyResult.message || applyResult.warningMessage"
              class="sales-match-result-alert"
              :title="applyResult.warningMessage || applyResult.message"
              :type="applyResult.success === false ? 'warning' : 'success'"
              :closable="false"
              show-icon
            />
            <el-table :data="applyRows" border stripe max-height="190" empty-text="暂无补全明细">
              <el-table-column prop="partNo" label="图号" min-width="120" show-overflow-tooltip />
              <el-table-column prop="version" label="版本" width="82" align="center" show-overflow-tooltip />
              <el-table-column label="结果" width="96" align="center">
                <template #default="{ row }">
                  <el-tag :type="applyRowTagType(row)" size="small">{{ applyRowStatus(row) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="matchMessage" label="说明" min-width="150" show-overflow-tooltip />
            </el-table>
          </template>
        </div>
      </aside>

      <main class="erp-table-surface sales-match-main-panel">
        <div class="erp-table-header panel-title-row">
          <div>
            <div class="panel-title">整单零件预览</div>
            <p class="panel-subtitle">{{ itemPanelTitle }}</p>
          </div>
          <div class="sales-match-preview-tags">
            <el-tag :type="previewData ? 'success' : 'info'">{{ previewData ? '整单预览' : '待选择订单' }}</el-tag>
            <el-tag type="success" effect="plain">只补空缺生产快照</el-tag>
            <el-tag type="info" effect="plain">导入字段受保护</el-tag>
          </div>
        </div>
        <el-alert
          v-if="previewSummary.warningMessage"
          class="sales-match-alert inline"
          :title="previewSummary.warningMessage"
          type="warning"
          :closable="false"
          show-icon
        />
        <el-table
          :data="previewRows"
          border
          stripe
          height="640"
          v-loading="previewLoading"
          empty-text="请先选择销售订单"
        >
          <el-table-column prop="partNo" label="图号" min-width="140" fixed show-overflow-tooltip />
          <el-table-column prop="lineNo" label="行号" width="72" align="center" />
          <el-table-column prop="version" label="版本" width="88" align="center" show-overflow-tooltip />
          <el-table-column prop="qty" label="数量" width="82" align="right" />
          <el-table-column prop="currentMatchStatus" label="当前状态" width="112" align="center">
            <template #default="{ row }">
              <el-tag :type="matchTagType(row.currentMatchStatus)" size="small">
                {{ row.currentMatchStatus || '待匹配' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="命中" width="88" align="center">
            <template #default="{ row }">
              <el-tag :type="row.matched ? 'success' : 'warning'" size="small">
                {{ row.matched ? '已命中' : '未匹配' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="历史来源" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.matched">RFQ ID {{ row.sourceRfqId }} / 零件 {{ row.sourceItemId }}</span>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column prop="materialName" label="导入材料" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ row.materialName || '-' }}</template>
          </el-table-column>
          <el-table-column prop="engineeringMaterialName" label="真实材料" min-width="130" show-overflow-tooltip>
            <template #default="{ row }">{{ row.engineeringMaterialName || '-' }}</template>
          </el-table-column>
          <el-table-column label="材料形态" min-width="110" show-overflow-tooltip>
            <template #default="{ row }">{{ row.materialShape || formatSnapshotSource(row.changedFields?.materialShape) }}</template>
          </el-table-column>
          <el-table-column prop="surfaceTreatment" label="表面处理" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ row.surfaceTreatment || '-' }}</template>
          </el-table-column>
          <el-table-column label="空缺生产快照" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ formatFieldSummary(row.changedFields) }}</template>
          </el-table-column>
          <el-table-column label="保护字段摘要" min-width="170" show-overflow-tooltip>
            <template #default="{ row }">{{ formatFieldSummary(row.protectedFields) }}</template>
          </el-table-column>
          <el-table-column prop="assemblyRelationStatus" label="父子关系" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ row.assemblyRelationStatus || '-' }}</template>
          </el-table-column>
          <el-table-column prop="warningMessage" label="警告" min-width="190" show-overflow-tooltip>
            <template #default="{ row }">{{ row.warningMessage || '-' }}</template>
          </el-table-column>
        </el-table>
      </main>
    </section>

    <OrderPickerDialog
      v-model="orderPickerVisible"
      title="选择销售订单"
      customer-scope="sales-match"
      order-scope="sales-match"
      :allowed-statuses="SALES_MATCH_ALLOWED_STATUSES"
      @select="handleOrderSelected"
    />
  </section>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { DocumentChecked } from '@element-plus/icons-vue'
import { usePendingAction } from '../composables/usePendingAction'
import OrderPickerDialog from './OrderPickerDialog.vue'
import {
  applySalesOrderMatchByOrder,
  previewSalesOrderMatchByOrder
} from '../services/marketApi'

const SALES_MATCH_APPLY_PERMISSION = 'market:sales-match:apply'
const SALES_MATCH_ORDER_SELECT_PERMISSION = 'market:sales-match:order-select'
const SALES_MATCH_ALLOWED_STATUSES = ['已报价']

const FIELD_LABELS = {
  materialName: '导入材料',
  surfaceTreatment: '表面处理',
  engineeringMaterialName: '真实材料',
  materialShape: '材料形态',
  materialGrossWeight: '毛重',
  partNetWeight: '净重',
  quoteMode: '核价模式',
  itemType: '零件类型',
  assemblyFee: '组装费',
  baseCost: '基础成本',
  drawingUrl: '图纸',
  materialSnapshotAvailable: '材料核价快照',
  processSnapshotAvailable: '工艺路线',
  feeSnapshotAvailable: '费用快照',
  qty: '数量',
  materialNo: '物料号',
  taxIncludedUnitPrice: '含税单价',
  finalUnitPrice: '最终单价',
  customerOrderNo: '客户订单号',
  internalProjectNo: '内部项目号'
}

const salesMatchActionKeys = {
  apply: 'market:sales-match:apply'
}

const { isPending, runAction } = usePendingAction()

const selectedOrder = ref(null)
const selectedOrderId = ref(null)
const previewData = ref(null)
const applyResult = ref(null)
const orderPickerVisible = ref(false)
const previewLoading = ref(false)

const selectedOrderRows = computed(() => selectedOrder.value ? [selectedOrder.value] : [])
const previewRows = computed(() => previewData.value?.rows || [])
const applyRows = computed(() => applyResult.value?.rows || [])
const currentOrderStatus = computed(() => orderValue('status') || '未选择')

const previewSummary = computed(() => {
  const source = previewData.value || {}
  return {
    totalItemCount: numberOrZero(source.totalItemCount ?? source.itemCount ?? previewRows.value.length),
    matchedItemCount: numberOrZero(source.matchedItemCount),
    unmatchedItemCount: numberOrZero(source.unmatchedItemCount),
    warningMessage: source.warningMessage || ''
  }
})

const customerDisplay = computed(() => {
  const source = selectedOrder.value || {}
  const customerName = source.customerName || ''
  const extras = [source.customerShortName, source.customerCode]
    .filter(Boolean)
    .filter((value, index, array) => array.indexOf(value) === index && value !== customerName)
  if (customerName && extras.length > 0) return `${customerName}（${extras.join(' / ')}）`
  return customerName || extras.join(' / ') || '-'
})

const itemPanelTitle = computed(() => {
  if (!selectedOrder.value) return '请点击右上角选择已报价销售订单。'
  if (previewLoading.value) return '正在加载销售订单整单零件预览。'
  return `${selectedOrder.value.salesOrderNo || '未编号销售订单'} / ${selectedOrder.value.customerName || '未指定客户'}`
})

const applyDisabledReason = computed(() => {
  if (isPending(salesMatchActionKeys.apply)) return '销售匹配补全正在提交，请稍候。'
  if (!selectedOrder.value) return '请先选择已报价销售订单。'
  if (previewLoading.value) return '正在加载销售订单匹配预览。'
  if (currentOrderStatus.value !== '已报价') return '销售匹配只能处理已报价销售订单。'
  if (!previewData.value) return '请先选择销售订单并加载整单预览。'
  return ''
})

const canApply = computed(() => !applyDisabledReason.value)

const requestErrorMessage = (error, fallback) => error?.response?.data?.message || error?.payload?.message || error?.message || fallback

const openOrderPicker = () => {
  orderPickerVisible.value = true
}

const normalizeSalesOrder = (order = {}) => {
  const rawId = order.salesOrderId ?? order.sourceOrderId ?? order.id
  const salesOrderId = Number(rawId)
  return {
    ...order,
    salesOrderId: Number.isFinite(salesOrderId) ? salesOrderId : null,
    salesOrderNo: order.salesOrderNo || order.sourceOrderNo || order.orderNo || '-'
  }
}

const handleOrderSelected = async (order) => {
  const normalized = normalizeSalesOrder(order)
  if (!normalized.salesOrderId) {
    ElMessage.warning('请选择有效的销售订单。')
    return
  }
  selectedOrder.value = normalized
  selectedOrderId.value = normalized.salesOrderId
  previewData.value = null
  applyResult.value = null
  await previewSelectedOrder({ silent: true })
}

const refreshCurrentState = async () => {
  if (!selectedOrder.value) {
    ElMessage.warning('请先选择销售订单。')
    return
  }
  await previewSelectedOrder({ silent: false, preserveResult: true })
}

const previewSelectedOrder = async ({ silent = false, preserveResult = false } = {}) => {
  if (!selectedOrderId.value) {
    if (!silent) ElMessage.warning('请先选择销售订单。')
    return
  }
  previewLoading.value = true
  try {
    const response = await previewSalesOrderMatchByOrder(selectedOrderId.value)
    previewData.value = response || {}
    selectedOrder.value = {
      ...selectedOrder.value,
      salesOrderId: selectedOrderId.value
    }
    if (!preserveResult) {
      applyResult.value = null
    }
    if (!silent) {
      ElMessage.success(`预览完成：命中 ${previewSummary.value.matchedItemCount} 个，未匹配 ${previewSummary.value.unmatchedItemCount} 个。`)
    }
  } catch (error) {
    previewData.value = null
    if (!preserveResult) {
      applyResult.value = null
    }
    ElMessage.error(requestErrorMessage(error, '销售匹配预览失败。'))
  } finally {
    previewLoading.value = false
  }
}

const applyMatch = async () => {
  if (applyDisabledReason.value) {
    ElMessage.warning(applyDisabledReason.value)
    return
  }
  return runAction(salesMatchActionKeys.apply, async () => {
    try {
      await ElMessageBox.confirm(
        `确认对销售订单 ${selectedOrder.value.salesOrderNo || ''} 整单补全空缺生产快照？导入材料、表面处理、图纸、价格、数量、交期、客户字段和物料号不会被改写。`,
        '确认补全生产快照',
        {
          type: 'warning',
          confirmButtonText: '确认补全',
          cancelButtonText: '取消'
        }
      )
    } catch {
      return
    }
    try {
      const response = await applySalesOrderMatchByOrder(selectedOrderId.value)
      applyResult.value = response || {}
      if (response?.success === false) {
        ElMessage.warning(response?.message || '销售匹配补全返回失败，请查看补全结果。')
      } else {
        ElMessage.success(response?.message || '销售匹配补全完成。')
      }
      await previewSelectedOrder({ silent: true, preserveResult: true })
    } catch (error) {
      ElMessage.error(requestErrorMessage(error, '销售匹配补全失败。'))
    }
  })
}

const orderValue = (field) => selectedOrder.value?.[field] || ''
const orderField = (field) => orderValue(field) || '-'

function numberOrZero(value) {
  const number = Number(value)
  return Number.isFinite(number) ? number : 0
}

const orderStatusTagType = (status) => {
  if (status === '已报价') return 'primary'
  if (status === '部分已提单') return 'warning'
  if (status === '已生成提单') return 'success'
  if (status === '废弃') return 'info'
  return 'info'
}

const matchTagType = (status) => {
  if (status === '已匹配') return 'success'
  if (status === '未匹配') return 'warning'
  if (status === '跳过') return 'info'
  return 'primary'
}

const applyRowStatus = (row) => {
  if (row?.skipped) return '跳过'
  if (row?.updated && row?.matched) return '已补全'
  if (row?.updated) return '已更新'
  return row?.matched ? '已匹配' : '未匹配'
}

const applyRowTagType = (row) => {
  if (row?.skipped) return 'info'
  if (row?.updated && row?.matched) return 'success'
  if (row?.matched) return 'primary'
  return 'warning'
}

const formatDateTime = (value) => {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

const formatSnapshotSource = (fieldValue) => {
  if (!fieldValue) return '-'
  if (typeof fieldValue === 'object' && !Array.isArray(fieldValue)) {
    return fieldValue.source ?? fieldValue.value ?? '-'
  }
  return fieldValue
}

const formatFieldSummary = (fields) => {
  if (!fields || Object.keys(fields).length === 0) return '-'
  const labels = Object.entries(fields)
    .filter(([, value]) => value !== false && value !== null && value !== undefined && value !== '')
    .map(([key]) => FIELD_LABELS[key] || key)
  if (labels.length === 0) return '-'
  return labels.length > 5 ? `${labels.slice(0, 5).join('、')}等 ${labels.length} 项` : labels.join('、')
}
</script>

<style scoped>
.sales-match-page {
  padding: 0;
}

.sales-match-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

.sales-match-header-actions {
  flex-wrap: wrap;
  justify-content: flex-end;
}

.sales-match-action-wrap {
  display: inline-flex;
}

.sales-match-workspace {
  display: grid;
  grid-template-columns: minmax(300px, 360px) minmax(0, 1fr);
  gap: var(--erp-space-4);
}

.sales-match-side-panel,
.sales-match-main-panel {
  padding: 14px;
}

.panel-title {
  margin-bottom: 12px;
  padding-left: 10px;
  border-left: 3px solid var(--erp-primary);
  color: var(--erp-text);
  font-size: 14px;
  font-weight: 700;
  line-height: 1.2;
}

.panel-title.compact {
  margin-bottom: 10px;
}

.panel-title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.panel-title-row .panel-title {
  margin-bottom: 4px;
}

.panel-subtitle {
  margin: 0;
  color: var(--erp-text-secondary);
  font-size: 12px;
}

.selected-order-mini-table {
  width: 100%;
  border-radius: 6px;
}

.selected-order-mini-table :deep(.el-table__cell) {
  padding: 5px 0;
}

.match-condition-panel,
.selected-order-meta,
.match-result-panel {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--erp-border-soft);
}

.condition-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-height: 32px;
  color: var(--erp-text-secondary);
  font-size: 13px;
}

.condition-row strong {
  color: var(--erp-text);
  font-weight: 700;
}

.warning-text {
  color: var(--erp-warning) !important;
}

.sales-match-alert {
  margin-top: 10px;
}

.sales-match-alert.inline {
  margin: 0 0 12px;
}

.selected-order-meta dl {
  display: grid;
  gap: 10px;
  margin: 0;
}

.selected-order-meta dl > div {
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr);
  gap: 10px;
  align-items: start;
}

.selected-order-meta dt {
  color: var(--erp-text-secondary);
  font-size: 12px;
}

.selected-order-meta dd {
  min-width: 0;
  margin: 0;
  color: var(--erp-text);
  font-size: 13px;
  word-break: break-all;
}

.match-result-summary,
.sales-match-preview-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.sales-match-preview-tags {
  justify-content: flex-end;
}

.match-result-summary {
  justify-content: flex-start;
  margin-bottom: 10px;
  color: var(--erp-text-secondary);
  font-size: 13px;
}

.sales-match-result-alert {
  margin-bottom: 10px;
}

@media (max-width: 1180px) {
  .sales-match-workspace {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .sales-match-header,
  .panel-title-row {
    flex-direction: column;
    align-items: stretch;
  }

  .sales-match-header-actions,
  .sales-match-preview-tags {
    justify-content: stretch;
  }

  .sales-match-header-actions :deep(.el-button) {
    flex: 1;
    margin-left: 0;
  }
}
</style>
