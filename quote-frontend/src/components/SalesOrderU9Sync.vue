<template>
  <section class="erp-page sales-order-u9-sync-page">
    <header class="erp-page-header sales-order-u9-sync-header">
      <div>
        <h1 class="erp-page-title">U9同步</h1>
        <p class="erp-page-description">选择已提单销售订单，保存 Seller / SaleDepartment 后核对并同步到 U9。</p>
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
        <el-tooltip v-if="isPendingConfirmation" :disabled="canReconcileSelected" :content="reconcileDisabledReason" placement="top">
          <span class="sales-order-u9-sync-action-wrap">
            <el-button
              v-permission="U9_SYNC_SUBMIT_PERMISSION"
              type="warning"
              :loading="isPending(u9SyncActionKeys.reconcile)"
              :disabled="!canReconcileSelected"
              @click="handleReconcileOrder"
            >
              核对U9结果
            </el-button>
          </span>
        </el-tooltip>
        <el-tooltip v-else :disabled="!syncDisabledReason" :content="syncDisabledReason" placement="top">
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

        <div class="u9-assignment-panel">
          <div class="panel-title-row compact-row">
            <div>
              <div class="panel-title compact">U9选择</div>
              <p class="panel-subtitle">Seller 和 SaleDepartment 可为空，保存后用于本订单同步。</p>
            </div>
            <el-tag v-if="assignmentReadOnly" size="small" type="info">只读</el-tag>
          </div>
          <el-alert
            v-if="u9Options.stale || u9Options.warning"
            class="sales-order-u9-sync-alert"
            :title="u9Options.warning || 'U9主数据选项来自过期缓存。'"
            type="warning"
            :closable="false"
            show-icon
          />
          <div class="assignment-form-grid" v-loading="optionsLoading">
            <label class="assignment-field">
              <span>Seller</span>
              <el-select
                v-model="assignmentDraft.sellerCode"
                aria-label="Seller"
                filterable
                clearable
                :disabled="assignmentReadOnly || optionsLoading || !selectedOrderId"
                placeholder="选择U9销售员"
                @change="handleSellerChange"
              >
                <el-option
                  v-for="seller in u9SellerOptions"
                  :key="seller.code"
                  :label="sellerOptionLabel(seller)"
                  :value="seller.code"
                />
              </el-select>
            </label>
            <label class="assignment-field">
              <span>SaleDepartment</span>
              <el-select
                v-model="assignmentDraft.saleDepartmentCode"
                aria-label="SaleDepartment"
                filterable
                clearable
                :disabled="assignmentReadOnly || optionsLoading || !selectedOrderId"
                placeholder="选择U9销售部门"
                @change="handleDepartmentChange"
              >
                <el-option
                  v-for="department in u9DepartmentOptions"
                  :key="department.code"
                  :label="departmentOptionLabel(department)"
                  :value="department.code"
                />
              </el-select>
            </label>
            <div class="assignment-selected-line">
              <span>{{ assignmentDisplayText }}</span>
            </div>
            <el-button
              v-permission="U9_SYNC_SUBMIT_PERMISSION"
              type="primary"
              plain
              :loading="assignmentSaving"
              :disabled="assignmentSaveDisabled"
              @click="handleSaveAssignment"
            >
              保存U9选择
            </el-button>
          </div>
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
            <span>稳定DocNo</span>
            <strong>{{ orderField('documentNo') }}</strong>
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
            v-if="syncDisabledReason || reconcileDisabledReason"
            class="sales-order-u9-sync-alert"
            :title="isPendingConfirmation ? reconcileDisabledReason : syncDisabledReason"
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
              <dt>订单来源</dt>
              <dd>{{ orderSourceLabel }}</dd>
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
          <el-empty v-if="!syncResult && !reconcileResult" description="暂无同步结果" :image-size="64" />
          <template v-if="syncResult">
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
          <el-alert
            v-if="reconcileResult"
            class="sales-order-u9-sync-result-alert"
            :title="reconcileResult.message || '核对已完成'"
            :type="reconcileResult.u9SyncStatus === '已同步' ? 'success' : 'warning'"
            :closable="false"
            show-icon
          />
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
            <el-tag type="info">U9价格=未税单价</el-tag>
          </div>
        </div>
        <div v-if="previewMessages.length" class="message-stack">
          <el-alert
            v-for="message in previewMessages"
            :key="`${message.type}-${message.text}`"
            class="sales-order-u9-sync-alert inline"
            :title="message.text"
            :type="message.type"
            :closable="false"
            show-icon
          />
        </div>
        <el-table
          :data="previewLines"
          border
          stripe
          height="640"
          v-loading="previewLoading"
          empty-text="请先选择销售订单"
        >
          <el-table-column type="expand" width="52" label="U9私有字段">
            <template #default="{ row }">
              <div class="private-field-grid">
                <div v-for="field in privateFieldDefinitions" :key="field.key" class="private-field-card">
                  <span>{{ field.u9Field }}</span>
                  <strong>{{ u9PrivateFieldValue(row, field) }}</strong>
                </div>
              </div>
              <div v-if="lineMessages(row).length" class="line-message-stack">
                <el-tag
                  v-for="message in lineMessages(row)"
                  :key="message"
                  size="small"
                  :type="message.includes('警告') ? 'warning' : 'danger'"
                >
                  {{ message }}
                </el-tag>
              </div>
            </template>
          </el-table-column>
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
          <el-table-column prop="qty" label="数量(PCS)" width="96" align="right" />
          <el-table-column label="就绪" width="88" align="center">
            <template #default="{ row }">
              <el-tag :type="row.ready ? 'success' : 'danger'" size="small">
                {{ row.ready ? '已就绪' : '阻断' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="错误/警告" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">
              <span>{{ row.blockedReason || firstLineWarning(row) || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="untaxedUnitPrice" label="U9未税单价" width="122" align="right">
            <template #default="{ row }">
              <el-tag v-if="row.untaxedUnitPrice == null" type="danger" size="small">未税单价缺失</el-tag>
              <span v-else>{{ formatMoney(row.untaxedUnitPrice) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="untaxedTotalPrice" label="未税金额" width="112" align="right">
            <template #default="{ row }">{{ formatMoney(row.untaxedTotalPrice) }}</template>
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
          <el-table-column prop="engineeringMaterialCode" label="材料编码" min-width="120" show-overflow-tooltip />
          <el-table-column prop="engineeringMaterialTypeName" label="材料类型" min-width="120" show-overflow-tooltip />
          <el-table-column prop="surfaceTreatment" label="表面处理" min-width="110" show-overflow-tooltip />
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
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { DocumentChecked } from '@element-plus/icons-vue'
import { useRoute } from 'vue-router'
import { usePendingAction } from '../composables/usePendingAction'
import OrderPickerDialog from './OrderPickerDialog.vue'
import {
  getSalesOrderU9SyncOptions,
  previewSalesOrderU9Sync,
  reconcileSalesOrderU9,
  saveSalesOrderU9Assignment,
  syncSalesOrderU9
} from '../services/marketApi'

const U9_SYNC_SUBMIT_PERMISSION = 'market:u9-sync:submit'
const U9_SYNC_ORDER_SELECT_PERMISSION = 'market:u9-sync:order-select'
const U9_SYNC_ALLOWED_STATUSES = ['已生成提单']

const u9SyncActionKeys = {
  sync: 'sales-order-u9-sync',
  reconcile: 'sales-order-u9-reconcile'
}

const privateFieldDefinitions = [
  { key: 'customerProjectNo', u9Field: 'PrivateDescSeg1 客户项目号', fallback: 'customerProjectNo' },
  { key: 'customerOrderNo', u9Field: 'PrivateDescSeg2 客户采购订单号', fallback: 'customerOrderNo' },
  { key: 'internalProjectNo', u9Field: 'PrivateDescSeg3 内部项目号', fallback: 'internalProjectNo' },
  { key: 'customerDeliveryDate', u9Field: 'PrivateDescSeg4 客户交期', format: 'date' },
  { key: 'internalDeliveryDate', u9Field: 'PrivateDescSeg5 内部交期', format: 'date' },
  { key: 'untaxedUnitPrice', u9Field: 'PrivateDescSeg9 价格', format: 'money' },
  { key: 'remark', u9Field: 'PubDescSeg2 销售备注' }
]

const { isPending, runAction } = usePendingAction()
const route = useRoute()

const selectedOrder = ref(null)
const selectedOrderId = ref(null)
const previewData = ref(null)
const syncResult = ref(null)
const reconcileResult = ref(null)
const orderPickerVisible = ref(false)
const previewLoading = ref(false)
const optionsLoading = ref(false)
const assignmentSaving = ref(false)
const u9Options = reactive({ orgCode: '', stale: false, refreshedAt: '', warning: '', sellers: [], departments: [] })
const assignmentDraft = ref({ sellerCode: '', saleDepartmentCode: '' })
const savedAssignment = ref({ sellerCode: '', saleDepartmentCode: '' })

const selectedOrderLabel = computed(() => selectedOrder.value
  ? `${selectedOrder.value.salesOrderNo || selectedOrder.value.salesOrderId || '-'} / ${selectedOrder.value.customerName || '-'}`
  : '未选择订单')
const previewLines = computed(() => previewData.value?.lines || [])
const syncRows = computed(() => syncResult.value?.rows || [])
const u9SellerOptions = computed(() => u9Options.sellers || [])
const u9DepartmentOptions = computed(() => u9Options.departments || [])
const currentU9Status = computed(() => reconcileResult.value?.u9SyncStatus || previewData.value?.u9SyncStatus || selectedOrder.value?.u9SyncStatus || '未同步')
const isPendingConfirmation = computed(() => currentU9Status.value === '结果待确认')
const currentU9OrderCode = computed(() => {
  const returnedCode = syncRows.value.find(row => row?.u9OrderCode)?.u9OrderCode
  return returnedCode || reconcileResult.value?.u9OrderCode || previewData.value?.u9OrderCode || selectedOrder.value?.u9OrderCode || '-'
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

const orderSourceLabel = computed(() => {
  const source = previewData.value || selectedOrder.value || {}
  if (source.sourceLabel) return source.sourceLabel
  if (!source.sourceType && !source.sourceLabel) return '-'
  return source.sourceType === 'RFQ_RELEASE' ? 'RFQ 议价提单派生' : '销售订单导入'
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

const previewMessages = computed(() => {
  const result = []
  const source = previewData.value || {}
  ;(source.blockingErrors || []).forEach(text => result.push({ type: 'error', text }))
  if (source.blockedReason && !result.some(item => item.text === source.blockedReason)) {
    result.push({ type: 'warning', text: source.blockedReason })
  }
  ;(source.warnings || []).forEach(text => result.push({ type: 'warning', text }))
  return result
})

const assignmentReadOnly = computed(() => ['已同步', '同步中', '结果待确认'].includes(currentU9Status.value))
const assignmentDirty = computed(() => assignmentDraft.value.sellerCode !== savedAssignment.value.sellerCode ||
  assignmentDraft.value.saleDepartmentCode !== savedAssignment.value.saleDepartmentCode)
const assignmentSaveDisabled = computed(() => assignmentSaving.value || optionsLoading.value || !selectedOrderId.value || assignmentReadOnly.value || !assignmentDirty.value)
const assignmentDisplayText = computed(() => {
  const seller = sellerByCode(assignmentDraft.value.sellerCode)
  const department = departmentByCode(assignmentDraft.value.saleDepartmentCode)
  const sellerText = seller ? sellerOptionLabel(seller) : 'Seller 空'
  const departmentText = department ? departmentOptionLabel(department) : 'SaleDepartment 空'
  return `${sellerText} / ${departmentText}`
})

const syncDisabledReason = computed(() => {
  if (isPending(u9SyncActionKeys.sync)) return 'U9同步正在提交，请稍候'
  if (!selectedOrder.value) return '请先选择销售订单'
  if (isPendingConfirmation.value) return '结果待确认，请先核对 U9 结果'
  if (!previewData.value || previewData.value.salesOrderId !== selectedOrderId.value) {
    return '请先选择销售订单并加载零件预览'
  }
  if (currentU9Status.value === '已同步') return '该销售订单已同步 U9'
  if (assignmentDirty.value) return '请先保存 Seller / SaleDepartment 选择'
  if (previewData.value.syncReady !== true) {
    return previewData.value.blockedReason || selectedOrder.value.blockedReason || '未满足整单同步条件'
  }
  return ''
})

const reconcileDisabledReason = computed(() => {
  if (!isPendingConfirmation.value) return ''
  if (isPending(u9SyncActionKeys.reconcile)) return 'U9结果正在核对，请稍候'
  if (!selectedOrderId.value) return '请先选择销售订单'
  return ''
})

const canSyncSelected = computed(() => !syncDisabledReason.value)
const canReconcileSelected = computed(() => isPendingConfirmation.value && !reconcileDisabledReason.value)

const requestErrorMessage = (error, fallback) => error?.response?.data?.message || error?.payload?.message || error?.message || fallback

const openOrderPicker = () => {
  loadU9Options({ silent: true })
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
  reconcileResult.value = null
  hydrateAssignment(normalized)
  await previewSelectedOrder({ silent: true })
}

const refreshCurrentState = async () => {
  if (!selectedOrder.value) {
    ElMessage.warning('请先选择销售订单。')
    return
  }
  await Promise.all([
    loadU9Options({ silent: true }),
    previewSelectedOrder({ silent: true, preserveResult: true })
  ])
}

const loadU9Options = async ({ silent = false } = {}) => {
  if (optionsLoading.value) return
  optionsLoading.value = true
  try {
    const response = await getSalesOrderU9SyncOptions()
    Object.assign(u9Options, {
      orgCode: response?.orgCode || '',
      stale: response?.stale === true,
      refreshedAt: response?.refreshedAt || '',
      warning: response?.warning || '',
      sellers: response?.sellers || [],
      departments: response?.departments || []
    })
    if (!silent && u9Options.stale) {
      ElMessage.warning(u9Options.warning || 'U9主数据暂时不可用，已使用过期缓存。')
    }
  } catch (error) {
    if (!silent) ElMessage.error(requestErrorMessage(error, 'U9销售员和部门选项加载失败。'))
  } finally {
    optionsLoading.value = false
  }
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
    hydrateAssignment(response || {})
    if (!preserveResult) {
      syncResult.value = null
      reconcileResult.value = null
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
      reconcileResult.value = null
    }
    ElMessage.error(requestErrorMessage(error, 'U9同步预览失败。'))
  } finally {
    previewLoading.value = false
  }
}

const handleSellerChange = (sellerCode) => {
  const seller = sellerByCode(sellerCode)
  if (seller?.departmentCode) {
    assignmentDraft.value.saleDepartmentCode = seller.departmentCode
  }
}

const handleDepartmentChange = () => {
  assignmentDraft.value = { ...assignmentDraft.value }
}

const handleSaveAssignment = async () => {
  if (assignmentSaveDisabled.value) return
  assignmentSaving.value = true
  try {
    const payload = {
      sellerCode: assignmentDraft.value.sellerCode || null,
      saleDepartmentCode: assignmentDraft.value.saleDepartmentCode || null
    }
    const response = await saveSalesOrderU9Assignment(selectedOrderId.value, payload)
    applyAssignmentResponse(response || payload)
    ElMessage.success('U9销售员和部门已保存。')
    await previewSelectedOrder({ silent: true, preserveResult: true })
  } catch (error) {
    ElMessage.error(requestErrorMessage(error, 'U9销售员和部门保存失败。'))
  } finally {
    assignmentSaving.value = false
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
      reconcileResult.value = null
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

const handleReconcileOrder = async () => {
  if (!canReconcileSelected.value) {
    ElMessage.warning(reconcileDisabledReason.value || '当前销售订单不需要核对。')
    return
  }
  return runAction(u9SyncActionKeys.reconcile, async () => {
    try {
      const response = await reconcileSalesOrderU9(selectedOrderId.value)
      reconcileResult.value = response || {}
      syncResult.value = null
      if (response?.u9SyncStatus === '已同步') {
        ElMessage.success(response?.message || 'U9结果核对成功。')
      } else {
        ElMessage.warning(response?.message || 'U9结果仍需关注。')
      }
      await previewSelectedOrder({ silent: true, preserveResult: true })
    } catch (error) {
      ElMessage.error(requestErrorMessage(error, 'U9结果核对失败。'))
    }
  })
}

const hydrateAssignment = (source = {}) => {
  const next = {
    sellerCode: source.sellerCode || '',
    saleDepartmentCode: source.saleDepartmentCode || ''
  }
  assignmentDraft.value = { ...next }
  savedAssignment.value = { ...next }
}

const applyAssignmentResponse = (source = {}) => {
  const next = {
    sellerCode: source.sellerCode || '',
    saleDepartmentCode: source.saleDepartmentCode || ''
  }
  assignmentDraft.value = { ...next }
  savedAssignment.value = { ...next }
  selectedOrder.value = {
    ...selectedOrder.value,
    sellerCode: next.sellerCode,
    sellerName: source.sellerName || sellerByCode(next.sellerCode)?.name || '',
    saleDepartmentCode: next.saleDepartmentCode,
    saleDepartmentName: source.saleDepartmentName || departmentByCode(next.saleDepartmentCode)?.name || ''
  }
}

const sellerByCode = (code) => u9SellerOptions.value.find(seller => seller.code === code)
const departmentByCode = (code) => u9DepartmentOptions.value.find(department => department.code === code)
const sellerOptionLabel = (seller) => `${seller.code || '-'} / ${seller.name || '-'}${seller.departmentCode ? ` / ${seller.departmentCode}` : ''}`
const departmentOptionLabel = (department) => `${department.code || '-'} / ${department.treeDisplayName || department.name || '-'}`

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
  if (status === '同步中' || status === '结果待确认') return 'warning'
  return 'info'
}

const materialStatusTagType = (status) => {
  if (status === '已确认') return 'success'
  if (status === '获取失败') return 'danger'
  if (status === '未确认') return 'warning'
  return 'info'
}

const firstLineWarning = (row) => row?.warnings?.[0] || ''
const lineMessages = (row) => [
  ...(row?.blockingErrors || []).map(message => `错误：${message}`),
  ...(row?.warnings || []).map(message => `警告：${message}`)
]

const u9PrivateFieldValue = (row, field) => {
  const raw = row?.[field.key] ?? (field.fallback ? orderValue(field.fallback) : '')
  if (field.format === 'date') return formatDate(raw)
  if (field.format === 'money') return formatMoney(raw)
  return raw || '-'
}

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

const normalizeRouteSalesOrderId = (value) => {
  const raw = Array.isArray(value) ? value[0] : value
  const id = Number(raw)
  return Number.isInteger(id) && id > 0 ? id : null
}

watch(
  () => route.query.salesOrderId,
  async value => {
    const salesOrderId = normalizeRouteSalesOrderId(value)
    if (!salesOrderId || (salesOrderId === selectedOrderId.value && previewData.value)) return
    await handleOrderSelected({ salesOrderId })
  },
  { immediate: true }
)

onMounted(() => {
  loadU9Options({ silent: true })
})
</script>

<style scoped>
.sales-order-u9-sync-page {
  max-width: 100%;
  overflow-x: hidden;
  padding: 0;
}

.sales-order-u9-sync-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  min-width: 0;
}

.sales-order-u9-sync-header > div:first-child {
  min-width: 0;
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
  max-width: 100%;
  min-width: 0;
}

.sales-order-u9-sync-side-panel,
.sales-order-u9-sync-main-panel {
  min-width: 0;
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
  margin-bottom: 6px;
}

.panel-title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.compact-row {
  align-items: center;
  margin-bottom: 8px;
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

.u9-assignment-panel,
.sync-condition-panel,
.selected-order-meta,
.sync-result-panel {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--erp-border-soft);
}

.assignment-form-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 10px;
}

.assignment-field {
  display: grid;
  gap: 6px;
  min-width: 0;
  color: var(--erp-text-secondary);
  font-size: 12px;
}

.assignment-field :deep(.el-select) {
  width: 100%;
}

.assignment-selected-line {
  min-width: 0;
  color: var(--erp-text-secondary);
  font-size: 12px;
  word-break: break-all;
}

.condition-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-height: 32px;
  min-width: 0;
  color: var(--erp-text-secondary);
  font-size: 13px;
}

.condition-row strong {
  min-width: 0;
  color: var(--erp-text);
  font-weight: 700;
  text-align: right;
  word-break: break-all;
}

.warning-text {
  color: var(--erp-warning) !important;
}

.sales-order-u9-sync-alert {
  margin-top: 10px;
}

.sales-order-u9-sync-alert.inline {
  margin: 0 0 8px;
}

.message-stack {
  display: grid;
  gap: 8px;
  margin-bottom: 12px;
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
.sales-order-u9-sync-result-tags,
.line-message-stack {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
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

.private-field-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 10px;
  max-width: 100%;
  min-width: 0;
}

.private-field-card {
  display: grid;
  gap: 4px;
  min-width: 0;
  padding: 8px 10px;
  border: 1px solid var(--erp-border-soft);
  border-radius: 6px;
  background: var(--erp-surface-soft);
}

.private-field-card span {
  color: var(--erp-text-secondary);
  font-size: 12px;
}

.private-field-card strong {
  min-width: 0;
  color: var(--erp-text);
  font-size: 13px;
  font-weight: 600;
  word-break: break-all;
}

.line-message-stack {
  margin-top: 10px;
}

@media (max-width: 1180px) {
  .sales-order-u9-sync-workspace {
    grid-template-columns: minmax(0, 1fr);
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
    width: 100%;
  }

  .sales-order-u9-sync-header-actions :deep(.el-button),
  .sales-order-u9-sync-action-wrap,
  .assignment-form-grid :deep(.el-button) {
    width: 100%;
    margin-left: 0;
  }

  .selected-order-meta dl > div {
    grid-template-columns: minmax(0, 1fr);
    gap: 4px;
  }
}
</style>
