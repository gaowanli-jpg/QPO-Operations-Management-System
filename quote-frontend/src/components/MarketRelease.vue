<template>
  <div class="erp-page release-page">
    <header class="erp-page-header release-page-header">
          <div>
            <h2>市场部：议价确认与提单生成</h2>
            <p>选择客户最终通过的零件，生成正式提单和对应工艺表。</p>
          </div>
          <div class="erp-page-actions release-page-actions"><el-button v-permission="'market:release:order-select'" type="primary" :loading="loading" @click="openOrderPicker">选择订单</el-button></div>

    </header>

    <section class="erp-surface release-workspace">
      <el-row :gutter="20">
        <el-col :span="7">
          <div class="erp-surface release-panel">
            <div class="panel-title">询价单</div>
            <div class="order-picker-row">
              <div class="selected-order-text" :title="selectedOrderLabel">
                {{ selectedOrderLabel }}
              </div>
              <el-button v-permission="'market:release:order-select'" plain class="workflow-soft-button" @click="openOrderPicker" :loading="loading">选择订单</el-button>
            </div>

            <div v-if="selectedOrders.length" class="selected-order-tags">
              <el-tag
                v-for="order in selectedOrders"
                :key="order.id"
                closable
                type="info"
                @close="removeSelectedOrder(order.id)"
              >
                {{ order.rfqNo || order.id }} / {{ order.status || '-' }}
              </el-tag>
            </div>

            <el-descriptions v-if="selectedOrders.length" :column="1" border class="order-desc">
              <el-descriptions-item label="客户">{{ selectedCustomerName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="订单数">{{ selectedOrders.length }}</el-descriptions-item>
              <el-descriptions-item label="状态">{{ selectedOrderStatusSummary }}</el-descriptions-item>
            </el-descriptions>

            <el-form label-width="80px" class="ticket-form">
              <el-form-item label="订单类型" required>
                <el-select v-model="releaseForm.orderType" class="full-width release-order-type-select" placeholder="请选择订单类型" clearable>
                  <el-option label="预测订单" value="预测订单" />
                  <el-option label="备货订单" value="备货订单" />
                  <el-option label="正式订单" value="正式订单" />
                </el-select>
              </el-form-item>
              <el-form-item label="RFQ号">
                <el-input v-model="releaseForm.projectNo" placeholder="多订单按各订单 RFQ 号生成" disabled />
              </el-form-item>
              <el-form-item label="开工日期">
                <el-date-picker
                  v-model="releaseForm.startDate"
                  type="date"
                  value-format="YYYY-MM-DD"
                  class="full-width"
                />
              </el-form-item>
              <el-form-item label="安排类型">
                <el-select v-model="releaseForm.arrangeType" class="full-width">
                  <el-option label="计划" value="计划" />
                  <el-option label="加急" value="加急" />
                  <el-option label="备货" value="备货" />
                </el-select>
              </el-form-item>
            </el-form>

            <div class="internal-lead-panel">
              <div class="panel-title compact">内部目标交期调整</div>
              <el-input-number
                v-model="internalShiftDays"
                :min="0"
                :max="365"
                :precision="0"
                controls-position="right"
                class="full-width"
              />
              <div class="erp-table-actions workflow-muted-actions lead-actions">
                <el-button
                  plain
                  class="workflow-soft-button"
                  :disabled="selectedRows.length === 0"
                  :loading="adjustingInternalLeadTime"
                  @click="adjustInternalLeadTime(1)"
                >
                  增加
                </el-button>
                <el-button
                  plain
                  class="workflow-soft-button"
                  :disabled="selectedRows.length === 0"
                  :loading="adjustingInternalLeadTime"
                  @click="adjustInternalLeadTime(-1)"
                >
                  减少
                </el-button>
              </div>
              <div class="market-manual-date-row">
                <el-date-picker
                  v-model="manualInternalTargetDate"
                  type="date"
                  value-format="YYYY-MM-DD"
                  format="YYYY-MM-DD"
                  placeholder="手动指定内部目标交期"
                  clearable
                  class="full-width manual-date-picker market-manual-date-input"
                />
                <el-button
                  plain
                  class="workflow-soft-button market-manual-date-button"
                  :disabled="selectedRows.length === 0 || !manualInternalTargetDate"
                  :loading="settingInternalTargetLeadTime"
                  @click="setInternalTargetLeadTime"
                >
                  指定
                </el-button>
              </div>
              <div class="selection-count">已选 {{ selectedRows.length }} 项</div>
            </div>

            <div class="internal-project-panel">
              <div class="panel-title compact">内部项目号</div>
              <el-input
                v-model="releaseForm.internalProjectNo"
                placeholder="请输入内部项目号"
                maxlength="100"
                clearable
              />
              <el-input
                v-model="releaseForm.customerOrderNo"
                placeholder="请输入客户订单号"
                maxlength="100"
                clearable
                class="release-meta-input"
              />
              <el-input
                v-model="releaseForm.customerProjectNo"
                placeholder="请输入客户项目号（可选）"
                maxlength="100"
                clearable
                class="release-meta-input"
              />
            </div>

            <div class="erp-dialog-footer main-actions">
              <el-button v-permission="'market:release:create'"
                type="primary"
                size="large"
                class="workflow-primary-action"
                :loading="submitting"
                :disabled="selectedRows.length === 0 || !releaseForm.orderType || !releaseForm.internalProjectNo.trim() || !releaseForm.customerOrderNo.trim()"
                @click="submitTicket"
              >
                生成正式提单
              </el-button>
            </div>
          </div>
        </el-col>

        <el-col :span="17">
          <div class="erp-table-surface release-table-panel">
            <div class="erp-table-header panel-title-row">
              <div class="panel-title">通过零件选择</div>
              <el-upload
                ref="selectionUploadRef"
                action="#"
                :auto-upload="false"
                :show-file-list="false"
                accept=".xlsx, .xls"
                :on-change="handleSelectionExcelChange"
              >
                <template #trigger>
                  <el-button
                    size="small"
                    plain
                    class="workflow-soft-button"
                    :disabled="selectedOrders.length === 0 || releaseItemTotal === 0"
                    :loading="importSelecting"
                  >
                    导入表格选定
                  </el-button>
                </template>
              </el-upload>
            </div>
            <el-table
              ref="itemTableRef"
              class="release-selection-table"
              :data="pagedReleaseItems"
              row-key="id"
              border
              stripe
              height="360"
              scrollbar-always-on
              v-loading="loading"
              @selection-change="handleSelectionChange"
            >
              <el-table-column type="selection" width="45" :selectable="canSelectItem" :reserve-selection="true" />
              <el-table-column prop="rfqNo" label="RFQ号" width="150" fixed show-overflow-tooltip />
              <el-table-column prop="customerName" label="客户" width="130" show-overflow-tooltip />
              <el-table-column prop="orderStatus" label="订单状态" width="110" align="center">
                <template #default="{ row }">
                  <el-tag :type="statusTagType(row.orderStatus)" size="small">{{ row.orderStatus || '-' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="partNo" label="图号" width="130" fixed show-overflow-tooltip />
              <el-table-column prop="version" label="版本" width="80" align="center" />
              <el-table-column prop="description" label="物料描述" min-width="160" show-overflow-tooltip />
              <el-table-column prop="qty" label="询价数量" width="90" align="center" />
              <el-table-column prop="ticketedQty" label="已提单数量" width="105" align="center" />
              <el-table-column prop="remainingQty" label="剩余可提数量" width="110" align="center" />
              <el-table-column label="类型" width="90" align="center">
                <template #default="{ row }">
                  <el-tag :type="isAssemblyItem(row) ? 'warning' : 'info'" size="small">
                    {{ isAssemblyItem(row) ? '组合件' : '普通件' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="leadTime" label="客户询价交期" width="120" show-overflow-tooltip>
                <template #default="{ row }">{{ row.leadTime || '-' }}</template>
              </el-table-column>
              <el-table-column prop="quoteLeadTime" label="报价承诺交期" width="120" show-overflow-tooltip>
                <template #default="{ row }">{{ row.quoteLeadTime || '-' }}</template>
              </el-table-column>
              <el-table-column label="内部目标交期" width="210">
                <template #default="{ row }">
                  <div class="row-date-editor">
                    <el-date-picker
                      v-model="row.internalTargetLeadTimeDraft"
                      type="date"
                      value-format="YYYY-MM-DD"
                      format="YYYY-MM-DD"
                      placeholder="默认沿用报价交期"
                      clearable
                      :disabled="!canSelectItem(row)"
                      class="row-date-picker"
                    />
                    <el-button
                      size="small"
                      type="primary"
                      plain
                      :disabled="!canSelectItem(row)"
                      :loading="!!savingInternalRowMap[row.id]"
                      @click.stop="saveInternalTargetLeadTime(row)"
                    >
                      保存
                    </el-button>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="提单交期" width="120" show-overflow-tooltip>
                <template #default="{ row }">{{ resolveDeliveryLeadTime(row) }}</template>
              </el-table-column>
              <el-table-column label="通过数量" width="130" align="center">
                <template #default="scope">
                  <el-input-number
                    v-model="acceptedQtyMap[scope.row.id]"
                    :min="remainingQtyFor(scope.row) > 0 ? 1 : 0"
                    :max="remainingQtyFor(scope.row)"
                    :disabled="!canSelectItem(scope.row)"
                    controls-position="right"
                    class="qty-input"
                  />
                </template>
              </el-table-column>
              <el-table-column prop="engineeringMaterialName" label="材料" width="110" show-overflow-tooltip />
              <el-table-column prop="surfaceTreatment" label="表面处理" width="110" show-overflow-tooltip />
              <el-table-column label="含税单价" width="110" align="right">
                <template #default="scope">
                  <span v-if="scope.row.finalUnitPrice != null">¥ {{ scope.row.finalUnitPrice.toFixed(2) }}</span>
                  <el-tag v-else type="warning" size="small">未报价</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="status" label="状态" width="110" align="center" />
            </el-table>
            <div v-if="releaseItemTotal" class="erp-pagination">
              <el-pagination
                v-model:current-page="releaseItemPagination.page"
                v-model:page-size="releaseItemPagination.pageSize"
                :page-sizes="CLIENT_PAGE_SIZES"
                :pager-count="7"
                :total="releaseItemTotal"
                layout="total, sizes, prev, pager, next, jumper"
                background
                @size-change="releaseItemPagination.page = 1"
              />
            </div>
          </div>

        </el-col>
      </el-row>
    </section>

    <OrderPickerDialog
      v-model="orderPickerVisible"
      title="选择市场部议价订单"
      customer-scope="release"
      order-scope="release"
      :allowed-statuses="RELEASE_ALLOWED_STATUSES"
      :multiple="true"
      :locked-customer-name="selectedCustomerName"
      :selected-order-ids="selectedOrderIds"
      @select="handleOrderSelected"
    />
  </div>
</template>

<script setup>
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import OrderPickerDialog from './OrderPickerDialog.vue'
import {
  generateTicket,
  getReleaseSummary,
  listReleaseItemsBatch,
  parsePartSelectionBatch,
  setInternalTargetLeadTime as setInternalTargetLeadTimeRequest,
  shiftInternalTargetLeadTime,
  updateInternalTargetLeadTime
} from '../services/marketApi'
import {
  CLIENT_PAGE_SIZES,
  DEFAULT_CLIENT_PAGE_SIZE
} from '../utils/clientPagination'

const selectedRfqId = ref(null)
const selectedOrders = ref([])
const orderPickerVisible = ref(false)
const itemList = ref([])
const releaseItemTotal = ref(0)
const selectedItemIdMap = reactive({})
const itemTableRef = ref(null)
const selectionUploadRef = ref(null)
const releaseItemPagination = reactive({ page: 1, pageSize: DEFAULT_CLIENT_PAGE_SIZE })
const acceptedQtyMap = reactive({})
const internalShiftDays = ref(0)
const manualInternalTargetDate = ref('')
const savingInternalRowMap = reactive({})

const loading = ref(false)
const submitting = ref(false)
const adjustingInternalLeadTime = ref(false)
const settingInternalTargetLeadTime = ref(false)
const importSelecting = ref(false)
let syncingReleaseItemSelection = false

const RELEASE_ALLOWED_STATUSES = ['已报价', '部分已提单', '已生成提单']

const selectedOrderIds = computed(() => selectedOrders.value.map(order => order.id))
const selectedCustomerName = computed(() => selectedOrders.value[0]?.customerName || '')
const selectedOrderStatusSummary = computed(() => {
  const statuses = [...new Set(selectedOrders.value.map(order => order.status).filter(Boolean))]
  return statuses.length > 0 ? statuses.join(' / ') : '-'
})

const selectedOrderLabel = computed(() => (
  selectedOrders.value.length === 1
    ? `${selectedOrders.value[0].rfqNo || selectedOrders.value[0].id} / ${selectedOrders.value[0].customerName || '-'}`
    : selectedOrders.value.length > 1
      ? `已选择 ${selectedOrders.value.length} 个订单 / ${selectedCustomerName.value || '-'}`
    : '未选择订单'
))

const today = new Date().toISOString().slice(0, 10)
const releaseForm = reactive({
  orderType: '',
  projectNo: '',
  internalProjectNo: '',
  customerOrderNo: '',
  customerProjectNo: '',
  startDate: today,
  arrangeType: '计划'
})

const pagedReleaseItems = computed(() => itemList.value)
const selectedRows = computed(() => Object.values(selectedItemIdMap))
const selectedReleaseItemIds = computed(() => new Set(selectedRows.value.map(row => row.id)))

const resetReleasePagination = () => {
  releaseItemPagination.page = 1
}

const clearSelectedRows = () => {
  Object.keys(selectedItemIdMap).forEach(id => delete selectedItemIdMap[id])
}

const syncReleaseItemTableSelection = async () => {
  syncingReleaseItemSelection = true
  await nextTick()
  itemTableRef.value?.clearSelection()
  pagedReleaseItems.value.forEach(row => {
    if (selectedReleaseItemIds.value.has(row.id)) {
      itemTableRef.value?.toggleRowSelection(row, true)
    }
  })
  await nextTick()
  syncingReleaseItemSelection = false
}

watch(
  () => [releaseItemPagination.page, releaseItemPagination.pageSize],
  () => {
    if (selectedOrders.value.length) {
      fetchReleaseItemsBatch()
    }
  }
)

const openOrderPicker = () => {
  orderPickerVisible.value = true
}

const handleOrderSelected = async (orderOrOrders) => {
  const incomingOrders = Array.isArray(orderOrOrders) ? orderOrOrders : [orderOrOrders]
  const existingIds = new Set(selectedOrderIds.value)
  const nextOrders = incomingOrders
    .filter(order => order?.id && !existingIds.has(order.id))
  if (nextOrders.length === 0) return

  const wasEmpty = selectedOrders.value.length === 0
  selectedOrders.value = [...selectedOrders.value, ...nextOrders]
  selectedRfqId.value = selectedOrders.value[0]?.id || null
  resetReleasePagination()
  if (wasEmpty) {
    resetReleaseFormForOrders()
    clearSelectedRows()
  }
  loading.value = true
  try {
    await fetchReleaseItemsBatch()
  } finally {
    loading.value = false
  }
}

const loadOrder = async (rfqId) => {
  if (!rfqId) return
  loading.value = true
  try {
    const response = await getReleaseSummary(rfqId)
    const refreshedOrder = response.orderInfo || {}
    selectedOrders.value = selectedOrders.value.map(order => order.id === rfqId ? { ...order, ...refreshedOrder } : order)
    selectedRfqId.value = selectedOrders.value[0]?.id || null
    await fetchReleaseItemsBatch()
    clearSelectedRows()
    await nextTick()
    itemTableRef.value?.clearSelection()
  } catch (error) {
    console.error(error)
    ElMessage.error('询价单明细加载失败。')
  } finally {
    loading.value = false
  }
}

const fetchReleaseItemsBatch = async () => {
  if (selectedOrderIds.value.length === 0) {
    setItemList([])
    releaseItemTotal.value = 0
    return
  }
  const response = await listReleaseItemsBatch({
    rfqIds: selectedOrderIds.value,
    page: releaseItemPagination.page,
    pageSize: releaseItemPagination.pageSize
  })
  setItemList(response.records || [])
  releaseItemTotal.value = Number(response.total ?? 0)
  await syncReleaseItemTableSelection()
}

const resetReleaseFormForOrders = () => {
  releaseForm.projectNo = ''
  releaseForm.orderType = ''
  releaseForm.internalProjectNo = ''
  releaseForm.customerOrderNo = ''
  releaseForm.customerProjectNo = ''
}

const removeSelectedOrder = async (orderId) => {
  selectedOrders.value = selectedOrders.value.filter(order => order.id !== orderId)
  selectedRfqId.value = selectedOrders.value[0]?.id || null
  const belongsToRemovedOrder = row => row.rfqId === orderId
  itemList.value = itemList.value.filter(row => !belongsToRemovedOrder(row))
  Object.keys(selectedItemIdMap).forEach(id => {
    if (belongsToRemovedOrder(selectedItemIdMap[id])) {
      delete selectedItemIdMap[id]
    }
  })
  if (selectedOrders.value.length === 0) {
    setItemList([])
    releaseItemTotal.value = 0
    clearSelectedRows()
    resetReleaseFormForOrders()
    await nextTick()
    itemTableRef.value?.clearSelection()
    return
  }
  resetReleasePagination()
  await fetchReleaseItemsBatch()
}

const setItemList = (rows) => {
  itemList.value = (rows || []).map(row => ({
    ...row,
    internalTargetLeadTimeDraft: row.internalTargetLeadTime || ''
  }))
  itemList.value.forEach(item => {
    const remainingQty = remainingQtyFor(item)
    if (!acceptedQtyMap[item.id] || acceptedQtyMap[item.id] > remainingQty) {
      acceptedQtyMap[item.id] = remainingQty > 0 ? remainingQty : 0
    }
    if (selectedItemIdMap[item.id]) {
      selectedItemIdMap[item.id] = { ...selectedItemIdMap[item.id], ...item }
    }
  })
}

const remainingQtyFor = (row) => {
  const qty = Number(row?.remainingQty ?? row?.qty ?? 0)
  return Number.isFinite(qty) ? Math.max(0, qty) : 0
}

const canSelectItem = (row) => {
  if (row?.canSelect === false) return false
  return row.status !== '已生成提单'
    && row.status !== '已提单'
    && row.status !== '已部分提单'
    && remainingQtyFor(row) > 0
}

const isAssemblyItem = (row) => String(row?.quoteMode || '').toUpperCase() === 'ASSEMBLY'

const handleSelectionChange = (rows) => {
  if (syncingReleaseItemSelection) return
  const currentPageIds = new Set(itemList.value.map(row => row.id))
  currentPageIds.forEach(id => delete selectedItemIdMap[id])
  rows.forEach(row => {
    if (canSelectItem(row)) {
      selectedItemIdMap[row.id] = row
    }
  })
}

const selectedRowsInItemOrder = () => {
  return selectedRows.value.slice().sort((left, right) => {
    const rfqCompare = Number(left.rfqId || 0) - Number(right.rfqId || 0)
    return rfqCompare !== 0 ? rfqCompare : Number(left.id || 0) - Number(right.id || 0)
  })
}

const selectedRowsRfqIds = () => {
  return [...new Set(selectedRows.value.map(row => row.rfqId || selectedRfqId.value).filter(Boolean))]
}

const groupSelectedRowsByRfqId = (rows) => {
  const groups = new Map()
  ;(rows || []).forEach(row => {
    const rfqId = row.rfqId || selectedRfqId.value
    if (!rfqId) return
    if (!groups.has(rfqId)) {
      groups.set(rfqId, [])
    }
    groups.get(rfqId).push(row)
  })
  return groups
}

const orderLabelById = (rfqId) => {
  const order = selectedOrders.value.find(item => item.id === rfqId)
  return order?.rfqNo || rfqId
}

const formatGenerationFailures = (failedResults) => {
  return failedResults
    .map(result => `${orderLabelById(result.rfqId)}：${result.message}`)
    .join('；')
}

const refreshSelectedOrdersAfterGeneration = async (successItemIds = new Set()) => {
  successItemIds.forEach(id => delete selectedItemIdMap[id])
  const refreshedOrders = await Promise.all(selectedOrderIds.value.map(async (rfqId) => {
    try {
      const response = await getReleaseSummary(rfqId)
      return { rfqId, orderInfo: response.orderInfo || {} }
    } catch (error) {
      console.error(error)
      return { rfqId, orderInfo: null }
    }
  }))
  selectedOrders.value = selectedOrders.value.map(order => {
    const refreshed = refreshedOrders.find(item => item.rfqId === order.id)
    return refreshed?.orderInfo ? { ...order, ...refreshed.orderInfo } : order
  })
  selectedRfqId.value = selectedOrders.value[0]?.id || null
  await fetchReleaseItemsBatch()
}

const requireSingleSelectedRowsRfqId = (actionText) => {
  if (!selectedOrders.value.length) {
    ElMessage.warning('请先选择询价单。')
    return null
  }
  const rfqIds = selectedRowsRfqIds()
  if (rfqIds.length !== 1) {
    ElMessage.warning(`${actionText}暂只支持同一订单下的已选零件，多订单统一处理将在后续阶段接入。`)
    return null
  }
  return rfqIds[0]
}

const handleSelectionExcelChange = async (uploadFile) => {
  const file = uploadFile?.raw
  selectionUploadRef.value?.clearFiles()
  if (!selectedRfqId.value) return ElMessage.warning('请先选择询价单。')
  if (!file) return

  const fileName = String(file.name || '').toLowerCase()
  if (!fileName.endsWith('.xlsx') && !fileName.endsWith('.xls')) {
    return ElMessage.warning('请上传 xlsx 或 xls 表格。')
  }

  const formData = new FormData()
  formData.append('excelFile', file)
  formData.append('rfqIds', JSON.stringify(selectedOrderIds.value))
  importSelecting.value = true
  try {
    const response = await parsePartSelectionBatch(formData)
    clearSelectedRows()
    ;(response?.data || []).forEach(row => {
      selectedItemIdMap[row.id] = row
      const remainingQty = remainingQtyFor(row)
      acceptedQtyMap[row.id] = remainingQty > 0 ? remainingQty : 0
    })
    await syncReleaseItemTableSelection()

    const importedCount = Number(response?.importedCount ?? 0)
    const matchedCount = Number(response?.matchedCount ?? 0)
    const selectedCount = Number(response?.selectedCount ?? 0)
    const skippedCount = Number(response?.skippedCount ?? 0)
    if (importedCount === 0) {
      ElMessage.warning('表格中未读取到有效的图号+版本。')
    } else if (selectedCount === 0) {
      ElMessage.warning(`已读取 ${importedCount} 个图号+版本，但没有匹配到当前订单中可选的零件。`)
    } else {
      const skippedText = skippedCount > 0 ? `，${skippedCount} 项因不可选未勾选` : ''
      ElMessage.success(`已读取 ${importedCount} 个图号+版本，匹配 ${matchedCount} 项，已勾选 ${selectedCount} 项${skippedText}。`)
    }
  } catch (error) {
    console.error(error)
    ElMessage.error(error.response?.data?.message || '导入表格选定失败。')
  } finally {
    importSelecting.value = false
  }
}

const adjustInternalLeadTime = async (direction) => {
  if (selectedRows.value.length === 0) return ElMessage.warning('请至少选择一个零件。')
  const rfqId = requireSingleSelectedRowsRfqId('内部目标交期调整')
  if (!rfqId) return
  const days = Math.abs(Number(internalShiftDays.value || 0)) * direction
  if (!days) return ElMessage.warning('调整天数为 0，不需要调整。')

  adjustingInternalLeadTime.value = true
  try {
    const response = await shiftInternalTargetLeadTime({
      rfqId,
      itemIds: selectedRows.value.map(row => row.id),
      days
    })
    clearSelectedRows()
    await fetchReleaseItemsBatch()
    const adjusted = response.adjustedCount || 0
    const skipped = response.skippedCount || 0
    if (skipped > 0) {
      ElMessage.warning(`已调整 ${adjusted} 项，${skipped} 项无法自动调整。`)
    } else {
      ElMessage.success(`已调整 ${adjusted} 项。`)
    }
  } catch (error) {
    console.error(error)
    ElMessage.error(error.response?.data?.message || '内部目标交期调整失败。')
  } finally {
    adjustingInternalLeadTime.value = false
  }
}

const setInternalTargetLeadTime = async () => {
  if (selectedRows.value.length === 0) return ElMessage.warning('请至少选择一个零件。')
  const rfqId = requireSingleSelectedRowsRfqId('指定内部目标交期')
  if (!rfqId) return
  if (!manualInternalTargetDate.value) return ElMessage.warning('请先选择内部目标交期。')

  settingInternalTargetLeadTime.value = true
  try {
    const response = await setInternalTargetLeadTimeRequest({
      rfqId,
      itemIds: selectedRows.value.map(row => row.id),
      internalTargetLeadTime: manualInternalTargetDate.value
    })
    clearSelectedRows()
    await fetchReleaseItemsBatch()
    const adjusted = response.adjustedCount || 0
    const skipped = response.skippedCount || 0
    if (skipped > 0) {
      ElMessage.warning(`已指定 ${adjusted} 项，${skipped} 项未处理。`)
    } else {
      ElMessage.success(`已指定 ${adjusted} 项内部目标交期。`)
    }
  } catch (error) {
    console.error(error)
    ElMessage.error(error.response?.data?.message || '指定内部目标交期失败。')
  } finally {
    settingInternalTargetLeadTime.value = false
  }
}

const saveInternalTargetLeadTime = async (row) => {
  const rowRfqId = row?.rfqId || selectedRfqId.value
  if (!rowRfqId || !row?.id) return
  savingInternalRowMap[row.id] = true
  try {
    const response = await updateInternalTargetLeadTime({
      rfqId: rowRfqId,
      itemId: row.id,
      internalTargetLeadTime: row.internalTargetLeadTimeDraft
    })
    const saved = {
      ...(response.data || {}),
      internalTargetLeadTimeDraft: response.data?.internalTargetLeadTime || ''
    }
    const index = itemList.value.findIndex(item => item.id === row.id)
    if (index >= 0) {
      itemList.value.splice(index, 1, saved)
    }
    if (selectedItemIdMap[row.id]) {
      selectedItemIdMap[row.id] = { ...selectedItemIdMap[row.id], ...saved }
    }
    ElMessage.success('内部目标交期已保存。')
  } catch (error) {
    console.error(error)
    ElMessage.error(error.response?.data?.message || '内部目标交期保存失败。')
  } finally {
    savingInternalRowMap[row.id] = false
  }
}

const submitTicket = async () => {
  if (selectedRows.value.length === 0) return ElMessage.warning('请至少选择一个通过零件。')
  if (!selectedOrders.value.length) return ElMessage.warning('请先选择询价单。')
  if (!releaseForm.orderType) return ElMessage.warning('请选择订单类型后再生成正式提单。')
  const internalProjectNo = releaseForm.internalProjectNo.trim()
  if (!internalProjectNo) return ElMessage.warning('请填写内部项目号后再生成正式提单。')
  const customerOrderNo = releaseForm.customerOrderNo.trim()
  if (!customerOrderNo) return ElMessage.warning('请填写客户订单号后再生成正式提单。')
  const customerProjectNo = releaseForm.customerProjectNo.trim()
  const orderedSelectedRows = selectedRowsInItemOrder()

  const unquoted = orderedSelectedRows.filter(row => row.finalUnitPrice == null)
  if (unquoted.length > 0) {
    return ElMessage.warning(`仍有 ${unquoted.length} 个零件未报价，暂不生成提单。`)
  }

  const overRemaining = orderedSelectedRows.filter(row => Number(acceptedQtyMap[row.id] || 0) > remainingQtyFor(row))
  if (overRemaining.length > 0) {
    return ElMessage.warning(`有 ${overRemaining.length} 个零件的通过数量超过剩余可提数量。`)
  }

  const releaseGroups = groupSelectedRowsByRfqId(orderedSelectedRows)
  if (releaseGroups.size === 0) {
    return ElMessage.warning('已选零件缺少订单信息，无法生成正式提单。')
  }

  await ElMessageBox.confirm(
    `确认按 ${releaseGroups.size} 个订单生成 ${orderedSelectedRows.length} 个零件的正式提单？通过数量不能超过剩余可提数量。`,
    '生成提单确认',
    { type: 'warning', confirmButtonText: '确认生成', cancelButtonText: '取消' }
  )

  submitting.value = true
  try {
    const successfulResults = []
    const failedResults = []
    const successItemIds = new Set()
    for (const [rfqId, rows] of releaseGroups) {
      const payload = {
        rfqId,
        orderType: releaseForm.orderType,
        projectNo: releaseForm.projectNo || '',
        internalProjectNo,
        customerOrderNo,
        customerProjectNo,
        startDate: releaseForm.startDate,
        arrangeType: releaseForm.arrangeType,
        items: rows.map(row => ({
          itemId: row.id,
          acceptedQty: acceptedQtyMap[row.id] || remainingQtyFor(row)
        }))
      }
      try {
        const response = await generateTicket(payload)
        if (response.success) {
          successfulResults.push({ rfqId, rows, response })
          rows.forEach(row => successItemIds.add(row.id))
        } else {
          failedResults.push({ rfqId, rows, message: response.message || '提单生成失败。' })
        }
      } catch (error) {
        console.error(error)
        failedResults.push({
          rfqId,
          rows,
          message: error.response?.data?.message || error.message || '提单生成请求失败。'
        })
      }
    }

    if (successfulResults.length > 0) {
      await refreshSelectedOrdersAfterGeneration(successItemIds)
    }

    if (failedResults.length === 0) {
      const successItemCount = successfulResults.reduce((total, result) => total + result.rows.length, 0)
      ElMessage.success(`已按 ${successfulResults.length} 个订单生成 ${successItemCount} 项提单，可到「提单表据」查看和导出。`)
    } else if (successfulResults.length > 0) {
      const failureText = formatGenerationFailures(failedResults)
      ElMessage.warning(`部分生成成功：成功 ${successfulResults.length} 个订单，失败 ${failedResults.length} 个订单。${failureText}`)
    } else {
      ElMessage.error(`提单生成失败：${formatGenerationFailures(failedResults)}`)
    }
  } catch (error) {
    console.error(error)
    ElMessage.error(error.response?.data?.message || '提单生成请求失败。')
  } finally {
    submitting.value = false
  }
}

const resolveDeliveryLeadTime = (row) => {
  return row.effectiveDeliveryLeadTime
    || row.internalTargetLeadTime
    || row.quoteLeadTime
    || row.leadTime
    || '-'
}

const statusTagType = (status) => {
  if (status === '已生成提单') return 'success'
  if (status === '已报价') return 'primary'
  if (status === '部分已提单') return 'warning'
  if (status === '已核价') return 'warning'
  if (status === '未核价') return 'info'
  return 'info'
}

</script>

<style scoped>
.release-page {
  padding: 0;
}

.release-page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

.release-page-header h2 {
  margin: 0;
  color: var(--erp-text);
  font-size: 20px;
  line-height: 1.3;
}

.release-page-header p {
  margin: 6px 0 0;
  color: var(--erp-text-secondary);
  font-size: 13px;
}

.release-panel,
.release-table-panel {
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

.order-picker-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
}

.selected-order-text {
  min-height: 32px;
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

.panel-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.panel-title-row .panel-title {
  margin-bottom: 0;
}

.order-desc {
  margin-top: 14px;
}

.ticket-form {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--erp-border-soft);
}

.full-width {
  width: 100%;
}

.qty-input {
  width: 105px;
}

.internal-lead-panel {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--erp-border-soft);
}

.internal-project-panel {
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid var(--erp-border-soft);
}

.release-meta-input {
  margin-top: 10px;
}

.lead-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin-top: 10px;
}

.lead-actions :deep(.el-button) {
  margin-left: 0;
}

.manual-date-picker,
.set-date-btn {
  margin-top: 10px;
}

.market-manual-date-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 76px;
  gap: 8px;
  align-items: center;
  margin-top: 10px;
}

.market-manual-date-input {
  min-width: 0;
}

.market-manual-date-row .manual-date-picker {
  margin-top: 0;
}

.market-manual-date-button {
  width: 100%;
  margin-left: 0;
  white-space: nowrap;
}

.set-date-btn {
  width: 100%;
  margin-left: 0;
}

.row-date-editor {
  display: flex;
  align-items: center;
  gap: 8px;
}

.row-date-picker {
  width: 128px;
}

.selection-count {
  margin-top: 10px;
  color: var(--erp-text-secondary);
  font-size: 13px;
}

.main-actions {
  display: grid;
  gap: 8px;
  padding-top: 14px;
  border-top: 1px solid var(--erp-border-soft);
}

.main-actions :deep(.el-button) {
  width: 100%;
  margin-left: 0;
}

.ticket-panel {
  margin-top: 14px;
}

:deep(.el-descriptions__label) {
  width: 78px;
  color: var(--erp-text-secondary);
  background: #f5f7fb;
}

@media (max-width: 1180px) {
  .release-page :deep(.el-row) {
    display: block;
  }

  .release-page :deep(.el-col) {
    max-width: 100%;
    width: 100%;
    margin-bottom: 14px;
  }
}

@media (max-width: 760px) {
  .release-page-header,
  .panel-title-row,
  .order-picker-row {
    grid-template-columns: 1fr;
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
