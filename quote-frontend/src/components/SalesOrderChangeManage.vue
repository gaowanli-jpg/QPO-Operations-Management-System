<template>
  <section class="erp-page sales-order-change-page">
    <header class="erp-page-header sales-order-change-header">
      <div>
        <h1 class="erp-page-title">销售变更</h1>
        <p class="erp-page-description">选择已同步 U9 销售订单，不调用 U9，只做本地校正。</p>
      </div>
      <div class="erp-page-actions sales-order-change-header-actions">
        <el-tag v-if="!hasChangeUpdatePermission" type="info" effect="plain">只读查看</el-tag>
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

    <section class="erp-surface workflow-workspace sales-order-change-workspace">
      <aside class="erp-surface sales-order-change-side-panel">
        <div class="panel-title">销售订单</div>
        <div class="selected-order-text" :title="selectedOrderLabel">
          {{ selectedOrderLabel }}
        </div>

        <div class="selected-order-meta">
          <div class="meta-row">
            <span>客户</span>
            <strong>{{ customerDisplay }}</strong>
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
          title="不调用 U9，只做本地校正。本页面用于记录 U9 已人工调整后的 QPO 本地校正结果。"
          type="info"
          :closable="false"
        />
      </aside>

      <main class="erp-table-surface sales-order-change-main-panel">
        <div class="erp-section-title-row">
          <div>
            <h2 class="erp-section-title">零件明细</h2>
            <p class="erp-section-description">主表格展示已同步 U9 销售订单的本地零件行，当前用于本地校正前核对。</p>
          </div>
          <div class="sales-order-change-table-actions">
            <el-tag type="info">{{ previewLines.length }} 行</el-tag>
            <el-tag v-if="dirtyItemCount > 0" class="item-edit-dirty-tag" type="warning">
              已修改 {{ dirtyItemCount }} 行
            </el-tag>
            <el-tooltip :disabled="!itemEditSaveDisabledReason" :content="itemEditSaveDisabledReason" placement="top">
              <span class="sales-order-change-action-wrap">
                <el-button
                  v-permission="SALES_ORDER_CHANGE_UPDATE_PERMISSION"
                  type="primary"
                  plain
                  :loading="itemEditSaving"
                  :disabled="itemEditSaveDisabled"
                  @click="handleOpenSaveReasonDialog"
                >
                  保存修改
                </el-button>
              </span>
            </el-tooltip>
            <el-tooltip :disabled="!itemDataReplacementDisabledReason" :content="itemDataReplacementDisabledReason" placement="top">
              <span class="sales-order-change-action-wrap">
                <el-button
                  v-permission="SALES_ORDER_CHANGE_UPDATE_PERMISSION"
                  plain
                  :loading="itemDataReplacementBusy"
                  :disabled="itemDataReplacementDisabled"
                  @click="openItemDataReplacementDialog"
                >
                  表格数据替换
                </el-button>
              </span>
            </el-tooltip>
          </div>
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
          <el-table-column prop="qty" label="数量" width="118" align="right">
            <template #default="{ row }">
              <el-input-number
                v-if="itemDrafts[row.salesOrderItemId]"
                v-model="itemDrafts[row.salesOrderItemId].qty"
                class="item-edit-input item-edit-number"
                :controls="false"
                :min="1"
                :precision="0"
                :disabled="itemEditReadOnly"
                @change="handleItemDraftChange(row)"
              />
              <span v-else>{{ numberOrZero(row.qty) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="untaxedUnitPrice" label="未税单价" width="120" align="right">
            <template #default="{ row }">
              <el-input
                v-if="itemDrafts[row.salesOrderItemId]"
                v-model="itemDrafts[row.salesOrderItemId].untaxedUnitPrice"
                class="item-edit-input item-edit-money"
                inputmode="decimal"
                :disabled="itemEditReadOnly"
                placeholder="未税单价"
                @input="handleItemDraftChange(row)"
              />
              <span v-else>{{ formatMoney(row.untaxedUnitPrice) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="untaxedTotalPrice" label="未税金额" width="120" align="right">
            <template #default="{ row }">{{ formatMoney(row.untaxedTotalPrice) }}</template>
          </el-table-column>
          <el-table-column prop="customerDeliveryDate" label="客户交期" width="120">
            <template #default="{ row }">
              <el-date-picker
                v-if="itemDrafts[row.salesOrderItemId]"
                v-model="itemDrafts[row.salesOrderItemId].customerDeliveryDate"
                class="item-edit-input item-edit-date"
                type="date"
                format="YYYY-MM-DD"
                value-format="YYYY-MM-DD"
                clearable
                :disabled="itemEditReadOnly"
                placeholder="客户交期"
                @change="handleItemDraftChange(row)"
              />
              <span v-else>{{ formatDate(row.customerDeliveryDate) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="internalDeliveryDate" label="内部交期" width="120">
            <template #default="{ row }">
              <el-date-picker
                v-if="itemDrafts[row.salesOrderItemId]"
                v-model="itemDrafts[row.salesOrderItemId].internalDeliveryDate"
                class="item-edit-input item-edit-date"
                type="date"
                format="YYYY-MM-DD"
                value-format="YYYY-MM-DD"
                clearable
                :disabled="itemEditReadOnly"
                placeholder="内部交期"
                @change="handleItemDraftChange(row)"
              />
              <span v-else>{{ formatDate(row.internalDeliveryDate) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="customerOrderNo" label="客户订单号" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">
              <el-input
                v-if="itemDrafts[row.salesOrderItemId]"
                v-model="itemDrafts[row.salesOrderItemId].customerOrderNo"
                class="item-edit-input"
                clearable
                :disabled="itemEditReadOnly"
                placeholder="客户订单号"
                @input="handleItemDraftChange(row)"
              />
              <span v-else>{{ row.customerOrderNo || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="customerProjectNo" label="客户项目号" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">
              <el-input
                v-if="itemDrafts[row.salesOrderItemId]"
                v-model="itemDrafts[row.salesOrderItemId].customerProjectNo"
                class="item-edit-input"
                clearable
                :disabled="itemEditReadOnly"
                placeholder="客户项目号"
                @input="handleItemDraftChange(row)"
              />
              <span v-else>{{ row.customerProjectNo || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="internalProjectNo" label="内部项目号" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">
              <el-input
                v-if="itemDrafts[row.salesOrderItemId]"
                v-model="itemDrafts[row.salesOrderItemId].internalProjectNo"
                class="item-edit-input"
                clearable
                :disabled="itemEditReadOnly"
                placeholder="内部项目号"
                @input="handleItemDraftChange(row)"
              />
              <span v-else>{{ row.internalProjectNo || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">
              <el-input
                v-if="itemDrafts[row.salesOrderItemId]"
                v-model="itemDrafts[row.salesOrderItemId].remark"
                class="item-edit-input"
                clearable
                :disabled="itemEditReadOnly"
                placeholder="备注"
                @input="handleItemDraftChange(row)"
              />
              <span v-else>{{ row.remark || '-' }}</span>
            </template>
          </el-table-column>
        </el-table>
      </main>
    </section>

    <el-dialog
      v-model="reasonDialogVisible"
      title="填写变更原因"
      width="480px"
      :close-on-click-modal="!itemEditSaving"
      :close-on-press-escape="!itemEditSaving"
      :show-close="!itemEditSaving"
    >
      <el-alert
        title="保存后只校正 QPO 本地销售订单数据，不调用 U9。"
        type="warning"
        show-icon
        :closable="false"
        class="sales-order-change-reason-alert"
      />
      <el-form label-position="top">
        <el-form-item label="变更原因" required>
          <el-input
            v-model="changeReason"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            :disabled="itemEditSaving"
            placeholder="请填写本次本地校正原因，例如：U9 已人工调整，QPO 本地保持一致"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="itemEditSaving" @click="reasonDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="itemEditSaving" :disabled="itemEditSaving" @click="handleConfirmSaveItemEdits">
          确认保存
        </el-button>
      </template>
    </el-dialog>

    <SalesOrderChangeDataReplacementDialog
      v-model="itemDataReplacementVisible"
      :sales-order-id="selectedOrderId"
      :disabled="itemDataReplacementDisabled"
      @busy-change="itemDataReplacementBusy = $event"
      @confirmed="handleItemDataReplacementConfirmed"
    />

    <OrderPickerDialog
      v-model="orderPickerVisible"
      title="选择销售变更订单"
      customer-scope="sales-order-change"
      order-scope="sales-order-change"
      :allowed-statuses="SALES_ORDER_CHANGE_ALLOWED_STATUSES"
      @select="handleOrderSelected"
    />
  </section>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { DocumentChecked } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/authStore'
import OrderPickerDialog from './OrderPickerDialog.vue'
import SalesOrderChangeDataReplacementDialog from './sales-order-change/SalesOrderChangeDataReplacementDialog.vue'
import { previewSalesOrderChange, saveSalesOrderChangeItems } from '../services/salesOrderChangeApi'

const SALES_ORDER_CHANGE_VIEW_PERMISSION = 'change:sales-order:view'
const SALES_ORDER_CHANGE_ORDER_SELECT_PERMISSION = 'change:sales-order:order-select'
const SALES_ORDER_CHANGE_UPDATE_PERMISSION = 'change:sales-order:update'
const SALES_ORDER_CHANGE_ALLOWED_STATUSES = ['已生成提单']
const SALES_ORDER_CHANGE_ITEM_EDITABLE_FIELDS = [
  'qty',
  'customerDeliveryDate',
  'internalDeliveryDate',
  'untaxedUnitPrice',
  'customerOrderNo',
  'customerProjectNo',
  'internalProjectNo',
  'remark'
]
const TEXT_ITEM_EDITABLE_FIELDS = ['customerOrderNo', 'customerProjectNo', 'internalProjectNo', 'remark']
const NULLABLE_ITEM_EDITABLE_FIELDS = ['customerDeliveryDate', 'internalDeliveryDate', ...TEXT_ITEM_EDITABLE_FIELDS]

const route = useRoute()
const authStore = useAuthStore()
const orderPickerVisible = ref(false)
const previewLoading = ref(false)
const selectedOrderId = ref(null)
const selectedOrderSummary = ref(null)
const preview = ref(null)
const itemDrafts = reactive({})
const savedItemDrafts = reactive({})
const dirtyItemIds = ref(new Set())
const itemEditSaving = ref(false)
const reasonDialogVisible = ref(false)
const changeReason = ref('')
const itemDataReplacementVisible = ref(false)
const itemDataReplacementBusy = ref(false)

const previewLines = computed(() => preview.value?.lines || [])
const hasChangeUpdatePermission = computed(() => authStore.hasPermission(SALES_ORDER_CHANGE_UPDATE_PERMISSION))
const dirtyItemCount = computed(() => dirtyItemIds.value.size)
const changeEditable = computed(() => preview.value?.changeEditable !== false)
const itemEditReadOnly = computed(() => !hasChangeUpdatePermission.value || !selectedOrderId.value || !changeEditable.value)
const itemEditSaveDisabledReason = computed(() => {
  if (!selectedOrderId.value) return '请先选择销售订单'
  if (!hasChangeUpdatePermission.value) return '当前账号无销售变更保存权限'
  if (!changeEditable.value) return preview.value?.changeBlockedReason || '当前销售订单不可编辑'
  if (previewLoading.value) return '销售变更预览正在刷新，请稍后保存'
  if (dirtyItemCount.value === 0) return '暂无需要保存的零件行修改'
  return ''
})
const itemEditSaveDisabled = computed(() => itemEditSaving.value || previewLoading.value || itemEditReadOnly.value || dirtyItemCount.value === 0)
const itemDataReplacementDisabledReason = computed(() => {
  if (!selectedOrderId.value) return '请先选择销售订单'
  if (!hasChangeUpdatePermission.value) return '当前账号无销售变更保存权限'
  if (!changeEditable.value) return preview.value?.changeBlockedReason || '当前销售订单不可替换'
  if (itemEditSaving.value) return '零件行修改正在保存，请稍后替换'
  if (previewLoading.value) return '销售变更预览正在刷新，请稍后替换'
  if (dirtyItemCount.value > 0) return '请先保存或放弃当前零件行修改'
  return ''
})
const itemDataReplacementDisabled = computed(() => itemDataReplacementBusy.value || Boolean(itemDataReplacementDisabledReason.value))
const customerDisplay = computed(() => orderRawField('customerShortName') || orderRawField('customerName') || '-')
const selectedOrderLabel = computed(() => (
  orderRawField('salesOrderNo') || selectedOrderSummary.value?.salesOrderNo || '请选择销售订单'
))

const openOrderPicker = () => {
  orderPickerVisible.value = true
}

const handleOrderSelected = async (order) => {
  selectedOrderSummary.value = order
  selectedOrderId.value = resolveSalesOrderId(order)
  preview.value = null
  resetItemDrafts()
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
    applyPreviewData(await previewSalesOrderChange(selectedOrderId.value))
  } catch (error) {
    ElMessage.error(requestErrorMessage(error, '销售变更预览加载失败'))
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
    preview.value = null
    resetItemDrafts()
    await loadPreview()
  },
  { immediate: true }
)

const resolveSalesOrderId = (order) => order?.salesOrderId ?? order?.sourceOrderId ?? order?.id ?? null

const clearReactiveObject = (target) => {
  Object.keys(target).forEach(key => delete target[key])
}

const dateDraftValue = (value) => {
  if (!value) return null
  return String(value).slice(0, 10)
}

const textDraftValue = (line, field) => {
  const value = line?.[field]
  return value === undefined || value === null ? '' : String(value)
}

const normalizeItemDraft = (line = {}) => ({
  qty: line.qty ?? null,
  customerDeliveryDate: dateDraftValue(line.customerDeliveryDate),
  internalDeliveryDate: dateDraftValue(line.internalDeliveryDate),
  untaxedUnitPrice: line.untaxedUnitPrice === undefined || line.untaxedUnitPrice === null ? '' : String(line.untaxedUnitPrice),
  customerOrderNo: textDraftValue(line, 'customerOrderNo'),
  customerProjectNo: textDraftValue(line, 'customerProjectNo'),
  internalProjectNo: textDraftValue(line, 'internalProjectNo'),
  remark: textDraftValue(line, 'remark')
})

const resetItemDrafts = () => {
  clearReactiveObject(itemDrafts)
  clearReactiveObject(savedItemDrafts)
  dirtyItemIds.value = new Set()
}

const hydrateItemDrafts = (source = {}) => {
  clearReactiveObject(itemDrafts)
  clearReactiveObject(savedItemDrafts)
  ;(source.lines || []).forEach(line => {
    if (!line?.salesOrderItemId) return
    const key = String(line.salesOrderItemId)
    const draft = normalizeItemDraft(line)
    itemDrafts[key] = { ...draft }
    savedItemDrafts[key] = { ...draft }
  })
  dirtyItemIds.value = new Set()
}

const applyPreviewData = (data = {}) => {
  preview.value = data
  hydrateItemDrafts(data)
}

const comparableItemValue = (field, value) => {
  if (NULLABLE_ITEM_EDITABLE_FIELDS.includes(field)) {
    const text = value === undefined || value === null ? '' : String(value).trim()
    return text || null
  }
  if (field === 'qty') {
    if (value === undefined || value === null || value === '') return null
    const number = Number(value)
    return Number.isFinite(number) ? number : String(value)
  }
  if (field === 'untaxedUnitPrice') {
    if (value === undefined || value === null || value === '') return null
    return String(value).trim()
  }
  return value ?? null
}

const itemFieldChanged = (key, field) => comparableItemValue(field, itemDrafts[key]?.[field]) !== comparableItemValue(field, savedItemDrafts[key]?.[field])

const refreshItemDirtyState = (salesOrderItemId) => {
  const key = String(salesOrderItemId || '')
  if (!key || !itemDrafts[key] || !savedItemDrafts[key]) return
  const next = new Set(dirtyItemIds.value)
  if (SALES_ORDER_CHANGE_ITEM_EDITABLE_FIELDS.some(field => itemFieldChanged(key, field))) {
    next.add(key)
  } else {
    next.delete(key)
  }
  dirtyItemIds.value = next
}

const handleItemDraftChange = (row) => {
  refreshItemDirtyState(row?.salesOrderItemId)
}

const itemRequestValue = (field, value) => {
  if (TEXT_ITEM_EDITABLE_FIELDS.includes(field)) {
    const text = value === undefined || value === null ? '' : String(value).trim()
    return text || null
  }
  if (field === 'customerDeliveryDate' || field === 'internalDeliveryDate') return value || null
  if (field === 'qty') return value === undefined || value === null || value === '' ? null : Number(value)
  if (field === 'untaxedUnitPrice') return value === undefined || value === null || value === '' ? null : String(value).trim()
  return value
}

const buildDirtyItemPayload = () => Array.from(dirtyItemIds.value)
  .map(key => {
    const line = { salesOrderItemId: Number(key) }
    SALES_ORDER_CHANGE_ITEM_EDITABLE_FIELDS.forEach(field => {
      if (itemFieldChanged(key, field)) {
        line[field] = itemRequestValue(field, itemDrafts[key]?.[field])
      }
    })
    return line
  })
  .filter(line => Object.keys(line).length > 1)

const handleOpenSaveReasonDialog = () => {
  if (itemEditSaveDisabled.value) {
    ElMessage.warning(itemEditSaveDisabledReason.value || '当前暂不可保存销售变更')
    return
  }
  changeReason.value = ''
  reasonDialogVisible.value = true
}

const openItemDataReplacementDialog = () => {
  if (itemDataReplacementDisabled.value) {
    ElMessage.warning(itemDataReplacementDisabledReason.value || '当前暂不可进行表格数据替换')
    return
  }
  itemDataReplacementVisible.value = true
}

const handleConfirmSaveItemEdits = async () => {
  if (itemEditSaving.value) return
  const reason = changeReason.value.trim()
  if (!reason) {
    ElMessage.warning('变更原因不能为空')
    return
  }
  const items = buildDirtyItemPayload()
  if (!items.length) {
    dirtyItemIds.value = new Set()
    reasonDialogVisible.value = false
    changeReason.value = ''
    return
  }
  itemEditSaving.value = true
  try {
    const response = await saveSalesOrderChangeItems(selectedOrderId.value, {
      reason: changeReason.value.trim(),
      items
    })
    if (response?.preview) {
      applyPreviewData(response.preview)
    } else {
      applyPreviewData(await previewSalesOrderChange(selectedOrderId.value))
    }
    dirtyItemIds.value = new Set()
    reasonDialogVisible.value = false
    changeReason.value = ''
    ElMessage.success(response?.message || `已保存 ${items.length} 行销售变更零件数据。`)
  } catch (error) {
    ElMessage.error(requestErrorMessage(error, '销售变更零件行保存失败。'))
  } finally {
    itemEditSaving.value = false
  }
}

const handleItemDataReplacementConfirmed = async (response = {}) => {
  try {
    if (response?.preview) {
      applyPreviewData(response.preview)
    } else {
      applyPreviewData(await previewSalesOrderChange(selectedOrderId.value))
    }
    dirtyItemIds.value = new Set()
    itemDataReplacementVisible.value = false
  } catch (error) {
    ElMessage.error(requestErrorMessage(error, '表格数据替换后刷新预览失败。'))
  }
}

const orderRawField = (field) => preview.value?.[field] ?? selectedOrderSummary.value?.[field] ?? ''

const orderField = (field) => {
  const value = orderRawField(field)
  return value === null || value === undefined || value === '' ? '-' : value
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

const numberOrZero = (value) => {
  const number = Number(value)
  return Number.isFinite(number) ? number : 0
}

const requestErrorMessage = (error, fallback) => error?.response?.data?.message || error?.payload?.message || error?.message || fallback

const orderStatusTagType = (status) => {
  if (status === '已生成提单') return 'success'
  if (status === '部分已提单') return 'warning'
  if (status === '已报价') return 'primary'
  return 'info'
}

const u9StatusTagType = (status) => status === '已同步' ? 'success' : 'info'
</script>

<style scoped>
.sales-order-change-page {
  gap: var(--erp-space-4);
}

.sales-order-change-header-actions {
  flex-wrap: wrap;
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

.sales-order-change-main-panel {
  min-width: 0;
  padding: var(--erp-space-4);
}

.erp-section-title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--erp-space-3);
  margin-bottom: var(--erp-space-3);
}

.sales-order-change-table-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--erp-space-2);
  flex-wrap: wrap;
}

.sales-order-change-action-wrap {
  display: inline-flex;
}

.item-edit-dirty-tag {
  font-weight: var(--erp-font-weight-bold);
}

.item-edit-input {
  width: 100%;
}

.item-edit-number,
.item-edit-money {
  min-width: 86px;
}

.item-edit-date {
  min-width: 132px;
}

.sales-order-change-reason-alert {
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
