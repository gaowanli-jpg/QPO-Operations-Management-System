<template>
  <section class="erp-page sales-order-u9-sync-page">
    <header class="erp-page-header sales-order-u9-sync-header">
      <div>
        <h1 class="erp-page-title">U9同步</h1>
        <p class="erp-page-description">选择已提单销售订单，查看零件物料号状态后按整单同步到 U9。</p>
      </div>
      <div class="erp-page-actions sales-order-u9-sync-header-actions">
        <el-button
          v-permission="U9_SYNC_ORDER_SELECT_PERMISSION"
          :icon="DocumentChecked"
          type="primary"
          @click="openOrderPicker"
        >
          选择销售订单
        </el-button>
        <el-button :loading="previewLoading" @click="refreshCurrentState">刷新</el-button>
        <el-tooltip :disabled="!syncDisabledReason" :content="syncDisabledReason" placement="top">
          <span class="sales-order-u9-sync-action-wrap">
            <el-button
              v-permission="U9_SYNC_SUBMIT_PERMISSION"
              type="success"
              :loading="isPending(u9SyncActionKeys.sync)"
              :disabled="!canSyncSelected"
              @click="syncSelectedOrder"
            >
              同步U9
            </el-button>
          </span>
        </el-tooltip>
      </div>
    </header>

    <section class="erp-surface workflow-workspace sales-order-u9-sync-workspace">
      <aside class="erp-surface sales-order-u9-sync-side-panel">
        <div class="panel-title">销售订单</div>
        <div class="selected-order-text" :title="selectedOrderLabel">
          {{ selectedOrderLabel }}
        </div>

        <div class="sync-condition-panel">
          <div class="panel-title compact">同步条件</div>
          <div class="condition-row">
            <span>订单状态</span>
            <el-tag :type="orderStatusTagType(selectedOrder?.status)" size="small">
              {{ selectedOrder?.status || '未选择' }}
            </el-tag>
          </div>
          <div class="condition-row">
            <span>U9状态</span>
            <el-tag :type="u9StatusTagType(currentU9Status)" size="small">{{ currentU9Status }}</el-tag>
          </div>
          <div class="condition-row">
            <span>零件数</span>
            <strong>{{ readySummary.itemCount }}</strong>
          </div>
          <div class="condition-row">
            <span>已确认物料号</span>
            <strong>{{ readySummary.materialConfirmedItemCount }} / {{ readySummary.itemCount }}</strong>
          </div>
          <div class="condition-row">
            <span>缺失/未确认</span>
            <strong class="warning-text">{{ readySummary.missingMaterialItemCount }}</strong>
          </div>
          <div class="condition-row">
            <span>同步就绪</span>
            <el-tag :type="readySummary.syncReady ? 'success' : 'warning'" size="small">
              {{ readySummary.syncReady ? '可同步' : '不可同步' }}
            </el-tag>
          </div>
          <el-alert
            v-if="syncDisabledReason"
            class="sales-order-u9-sync-alert"
            :title="syncDisabledReason"
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
              <dt>订单类型</dt>
              <dd>{{ orderField('orderType') }}</dd>
            </div>
            <div>
              <dt>内部项目号</dt>
              <dd>{{ orderField('internalProjectNo') }}</dd>
            </div>
          </dl>
        </div>

        <div class="sync-result-panel">
          <div class="panel-title compact">同步结果</div>
          <div class="sync-result-code-row">
            <span>当前U9编码</span>
            <strong>{{ currentU9OrderCode }}</strong>
          </div>
          <el-empty v-if="!syncResult" description="暂无同步结果" :image-size="64" />
          <template v-else>
            <div class="sync-result-summary">
              <el-tag :type="syncResult.success ? 'success' : 'danger'">{{ syncResult.status || '-' }}</el-tag>
              <span>成功 {{ numberOrZero(syncResult.successOrderCount) }}</span>
              <span>失败 {{ numberOrZero(syncResult.failedOrderCount) }}</span>
            </div>
            <el-alert
              v-if="syncResult.message"
              class="sales-order-u9-sync-result-alert"
              :title="syncResult.message"
              :type="syncResult.success ? 'success' : 'warning'"
              :closable="false"
              show-icon
            />
            <el-table :data="syncRows" border stripe max-height="180" empty-text="暂无返回明细">
              <el-table-column prop="salesOrderNo" label="销售订单号" min-width="128" show-overflow-tooltip />
              <el-table-column prop="u9OrderCode" label="U9编码" min-width="110" show-overflow-tooltip>
                <template #default="{ row }">{{ row.u9OrderCode || '-' }}</template>
              </el-table-column>
              <el-table-column prop="errorMessage" label="失败明细" min-width="160" show-overflow-tooltip>
                <template #default="{ row }">{{ row.errorMessage || '-' }}</template>
              </el-table-column>
            </el-table>
          </template>
        </div>
      </aside>

      <main class="erp-table-surface sales-order-u9-sync-main-panel">
        <div class="erp-table-header panel-title-row">
          <div>
            <div class="panel-title">销售订单零件</div>
            <p class="panel-subtitle">{{ itemPanelTitle }}</p>
          </div>
          <div class="sales-order-u9-sync-result-tags">
            <el-tag :type="previewData?.syncReady ? 'success' : 'warning'">
              {{ previewData ? (previewData.syncReady ? '可同步' : '不可同步') : '待选择' }}
            </el-tag>
            <el-tag type="info">U9同步使用已确认物料号</el-tag>
          </div>
        </div>
        <el-alert
          v-if="previewData?.blockedReason"
          class="sales-order-u9-sync-alert inline"
          :title="previewData.blockedReason"
          type="warning"
          :closable="false"
          show-icon
        />
        <el-table
          :data="previewLines"
          border
          stripe
          height="640"
          v-loading="previewLoading"
          empty-text="请先选择销售订单"
        >
          <el-table-column prop="partNo" label="图号" min-width="130" fixed show-overflow-tooltip />
          <el-table-column prop="lineNo" label="行号" width="72" align="center" />
          <el-table-column prop="version" label="版本" width="88" align="center" show-overflow-tooltip />
          <el-table-column prop="materialNo" label="物料号" min-width="130" show-overflow-tooltip>
            <template #default="{ row }">{{ row.materialNo || '-' }}</template>
          </el-table-column>
          <el-table-column prop="materialNoStatus" label="物料号状态" width="116" align="center">
            <template #default="{ row }">
              <el-tag :type="materialStatusTagType(row.materialNoStatus)" size="small">
                {{ row.materialNoStatus || '未确认' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="qty" label="数量" width="82" align="right" />
          <el-table-column label="就绪" width="88" align="center">
            <template #default="{ row }">
              <el-tag :type="row.ready ? 'success' : 'danger'" size="small">
                {{ row.ready ? '已就绪' : '阻断' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="blockedReason" label="阻断原因" min-width="190" show-overflow-tooltip>
            <template #default="{ row }">{{ row.blockedReason || '-' }}</template>
          </el-table-column>
          <el-table-column prop="taxIncludedUnitPrice" label="U9含税单价" width="128" align="right">
            <template #default="{ row }">
              <el-tag v-if="row.taxIncludedUnitPrice == null" :type="priceMissingTagType(row)" size="small">含税单价缺失</el-tag>
              <span v-else>{{ formatMoney(row.taxIncludedUnitPrice) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="taxIncludedTotalPrice" label="含税金额" width="118" align="right">
            <template #default="{ row }">{{ formatMoney(row.taxIncludedTotalPrice) }}</template>
          </el-table-column>
          <el-table-column prop="salesOrderDate" label="下单日期" width="116">
            <template #default="{ row }">{{ formatDate(row.salesOrderDate) }}</template>
          </el-table-column>
          <el-table-column prop="customerOrderTitle" label="客户下单抬头" min-width="140" show-overflow-tooltip />
          <el-table-column prop="customerDeliveryDate" label="客户交期" width="116">
            <template #default="{ row }">{{ formatDate(row.customerDeliveryDate) }}</template>
          </el-table-column>
          <el-table-column prop="internalDeliveryDate" label="内部交期(给生产的交期)" width="170">
            <template #default="{ row }">{{ formatDate(row.internalDeliveryDate) }}</template>
          </el-table-column>
          <el-table-column prop="shippedQty" label="已出数量" width="90" align="right">
            <template #default="{ row }">{{ numberOrZero(row.shippedQty) }}</template>
          </el-table-column>
          <el-table-column prop="unshippedQty" label="未出数量" width="90" align="right">
            <template #default="{ row }">{{ numberOrZero(row.unshippedQty) }}</template>
          </el-table-column>
          <el-table-column prop="description" label="描述" min-width="160" show-overflow-tooltip />
          <el-table-column prop="materialName" label="材料" min-width="110" show-overflow-tooltip />
          <el-table-column prop="surfaceTreatment" label="表面处理" min-width="110" show-overflow-tooltip />
          <el-table-column prop="untaxedUnitPrice" label="未税单价(CNY)" width="122" align="right">
            <template #default="{ row }">{{ formatMoney(row.untaxedUnitPrice) }}</template>
          </el-table-column>
          <el-table-column prop="untaxedTotalPrice" label="（未税）金额" width="118" align="right">
            <template #default="{ row }">{{ formatMoney(row.untaxedTotalPrice) }}</template>
          </el-table-column>
          <el-table-column prop="actualShipDate" label="实际出货日期" width="120">
            <template #default="{ row }">{{ formatDate(row.actualShipDate) }}</template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip />
        </el-table>
      </main>
    </section>

    <OrderPickerDialog
      v-model="orderPickerVisible"
      title="选择销售订单"
      customer-scope="u9-sync"
      order-scope="u9-sync"
      :allowed-statuses="U9_SYNC_ALLOWED_STATUSES"
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
  previewSalesOrderU9Sync,
  syncSalesOrderU9
} from '../services/marketApi'

const U9_SYNC_SUBMIT_PERMISSION = 'market:u9-sync:submit'
const U9_SYNC_ORDER_SELECT_PERMISSION = 'market:u9-sync:order-select'
const U9_SYNC_ALLOWED_STATUSES = ['部分已提单', '已生成提单']

const u9SyncActionKeys = {
  sync: 'market:u9-sync:submit'
}

const { isPending, runAction } = usePendingAction()

const selectedOrder = ref(null)
const selectedOrderId = ref(null)
const previewData = ref(null)
const syncResult = ref(null)
const orderPickerVisible = ref(false)
const previewLoading = ref(false)

const selectedOrderLabel = computed(() => selectedOrder.value
  ? `${selectedOrder.value.salesOrderNo || selectedOrder.value.salesOrderId || '-'} / ${selectedOrder.value.customerName || '-'}`
  : '未选择订单')
const previewLines = computed(() => previewData.value?.lines || [])
const syncRows = computed(() => syncResult.value?.rows || [])
const currentU9Status = computed(() => previewData.value?.u9SyncStatus || selectedOrder.value?.u9SyncStatus || '未同步')
const currentU9OrderCode = computed(() => {
  const returnedCode = syncRows.value.find(row => row?.u9OrderCode)?.u9OrderCode
  return returnedCode || previewData.value?.u9OrderCode || selectedOrder.value?.u9OrderCode || '-'
})

const customerDisplay = computed(() => {
  const source = previewData.value || selectedOrder.value || {}
  const customerName = source.customerName || ''
  const extras = [source.customerShortName, source.customerCode]
    .filter(Boolean)
    .filter((value, index, array) => array.indexOf(value) === index && value !== customerName)
  if (customerName && extras.length > 0) return `${customerName}（${extras.join(' / ')}）`
  return customerName || extras.join(' / ') || '-'
})

const itemPanelTitle = computed(() => {
  if (!selectedOrder.value) return '请点击右上角选择销售订单。'
  if (previewLoading.value) return '正在加载销售订单零件。'
  return `${selectedOrder.value.salesOrderNo || '未编号销售订单'} / ${selectedOrder.value.customerName || '未指定客户'}`
})

const readySummary = computed(() => {
  const source = previewData.value || selectedOrder.value || {}
  return {
    itemCount: numberOrZero(source.itemCount),
    materialConfirmedItemCount: numberOrZero(source.materialConfirmedItemCount),
    missingMaterialItemCount: numberOrZero(source.missingMaterialItemCount),
    syncReady: source.syncReady === true
  }
})

const syncDisabledReason = computed(() => {
  if (isPending(u9SyncActionKeys.sync)) return 'U9同步正在提交，请稍候'
  if (!selectedOrder.value) return '请先选择销售订单'
  if (!previewData.value || previewData.value.salesOrderId !== selectedOrderId.value) {
    return '请先选择销售订单并加载零件预览'
  }
  if (previewData.value.u9SyncStatus === '已同步') return '该销售订单已同步 U9'
  if (previewData.value.syncReady !== true) {
    return previewData.value.blockedReason || selectedOrder.value.blockedReason || '未满足整单同步条件'
  }
  return ''
})

const canSyncSelected = computed(() => !syncDisabledReason.value)

const requestErrorMessage = (error, fallback) => error?.response?.data?.message || error?.payload?.message || error?.message || fallback

const openOrderPicker = () => {
  orderPickerVisible.value = true
}

const normalizeSalesOrder = (order = {}) => {
  const salesOrderId = Number(order.salesOrderId ?? order.sourceOrderId ?? order.id)
  return {
    ...order,
    salesOrderId,
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
  syncResult.value = null
  await previewSelectedOrder({ silent: true })
}

const refreshCurrentState = async () => {
  if (!selectedOrder.value) {
    ElMessage.warning('请先选择销售订单。')
    return
  }
  await previewSelectedOrder({ silent: true, preserveResult: true })
}

const previewSelectedOrder = async ({ silent = false, preserveResult = false } = {}) => {
  if (!selectedOrderId.value) {
    if (!silent) ElMessage.warning('请先选择销售订单。')
    return
  }
  previewLoading.value = true
  try {
    const response = await previewSalesOrderU9Sync(selectedOrderId.value)
    previewData.value = response || {}
    selectedOrder.value = {
      ...selectedOrder.value,
      ...(response || {}),
      salesOrderId: selectedOrderId.value
    }
    if (!preserveResult) {
      syncResult.value = null
    }
    if (!silent) {
      if (response?.syncReady) {
        ElMessage.success('U9同步预览已就绪，可以提交整单同步。')
      } else {
        ElMessage.warning(response?.blockedReason || '该销售订单暂不满足整单同步条件。')
      }
    }
  } catch (error) {
    previewData.value = null
    if (!preserveResult) {
      syncResult.value = null
    }
    ElMessage.error(requestErrorMessage(error, 'U9同步预览失败。'))
  } finally {
    previewLoading.value = false
  }
}

const syncSelectedOrder = async () => {
  if (!canSyncSelected.value) {
    ElMessage.warning(syncDisabledReason.value || '该销售订单暂不可同步。')
    return
  }
  return runAction(u9SyncActionKeys.sync, async () => {
    try {
      await ElMessageBox.confirm(
        `确认将销售订单 ${selectedOrder.value.salesOrderNo || ''} 整单同步到 U9？`,
        '确认同步U9',
        {
          type: 'warning',
          confirmButtonText: '同步U9',
          cancelButtonText: '取消'
        }
      )
    } catch {
      return
    }
    try {
      const response = await syncSalesOrderU9(selectedOrderId.value)
      syncResult.value = response || {}
      if (response?.success) {
        ElMessage.success(response?.message || 'U9同步成功。')
      } else {
        ElMessage.warning(response?.message || 'U9同步返回失败，请查看失败明细。')
      }
      await previewSelectedOrder({ silent: true, preserveResult: true })
    } catch (error) {
      ElMessage.error(requestErrorMessage(error, 'U9同步提交失败。'))
    }
  })
}

const orderValue = (field) => previewData.value?.[field] || selectedOrder.value?.[field] || ''
const orderField = (field) => orderValue(field) || '-'

function numberOrZero(value) {
  const number = Number(value)
  return Number.isFinite(number) ? number : 0
}

const orderStatusTagType = (status) => {
  if (status === '已生成提单') return 'success'
  if (status === '部分已提单') return 'warning'
  return 'info'
}

const u9StatusTagType = (status) => {
  if (status === '已同步') return 'success'
  if (status === '同步失败') return 'danger'
  if (status === '同步中') return 'warning'
  return 'info'
}

const materialStatusTagType = (status) => {
  if (status === '已确认') return 'success'
  if (status === '获取失败') return 'danger'
  if (status === '未确认') return 'warning'
  return 'info'
}

const priceMissingTagType = (row) => (row?.taxIncludedUnitPrice == null ? 'warning' : 'success')

const formatDate = (value) => {
  if (!value) return '-'
  return String(value).slice(0, 10)
}

const formatMoney = (value) => {
  if (value === null || value === undefined || value === '') return '-'
  const amount = Number(value)
  if (Number.isNaN(amount)) return String(value)
  return amount.toFixed(2)
}
</script>

<style scoped>
.sales-order-u9-sync-page {
  padding: 0;
}

.sales-order-u9-sync-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

.sales-order-u9-sync-header-actions {
  flex-wrap: wrap;
  justify-content: flex-end;
}

.sales-order-u9-sync-action-wrap {
  display: inline-flex;
}

.sales-order-u9-sync-workspace {
  display: grid;
  grid-template-columns: minmax(300px, 360px) minmax(0, 1fr);
  gap: var(--erp-space-4);
}

.sales-order-u9-sync-side-panel,
.sales-order-u9-sync-main-panel {
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

.selected-order-text {
  min-height: 34px;
  display: flex;
  align-items: center;
  color: var(--erp-text);
  border: 1px solid var(--erp-border);
  border-radius: 6px;
  padding: 0 10px;
  background: var(--erp-surface-soft);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.sync-condition-panel,
.selected-order-meta,
.sync-result-panel {
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

.sales-order-u9-sync-alert {
  margin-top: 10px;
}

.sales-order-u9-sync-alert.inline {
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

.sync-result-code-row,
.sync-result-summary,
.sales-order-u9-sync-result-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.sales-order-u9-sync-result-tags {
  justify-content: flex-end;
}

.sync-result-code-row {
  justify-content: space-between;
  margin-bottom: 10px;
  color: var(--erp-text-secondary);
  font-size: 13px;
}

.sync-result-code-row strong {
  min-width: 0;
  color: var(--erp-text);
  word-break: break-all;
}

.sync-result-summary {
  justify-content: flex-start;
  margin-bottom: 10px;
  color: var(--erp-text-secondary);
  font-size: 13px;
}

.sales-order-u9-sync-result-alert {
  margin-bottom: 10px;
}

@media (max-width: 1180px) {
  .sales-order-u9-sync-workspace {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .sales-order-u9-sync-header,
  .panel-title-row {
    flex-direction: column;
    align-items: stretch;
  }

  .sales-order-u9-sync-header-actions,
  .sales-order-u9-sync-result-tags {
    justify-content: stretch;
  }

  .sales-order-u9-sync-header-actions :deep(.el-button) {
    flex: 1;
    margin-left: 0;
  }
}
</style>
