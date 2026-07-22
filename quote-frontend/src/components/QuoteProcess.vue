<template>
  <div class="erp-page quote-process-page workflow-page">
    <header class="erp-page-header quote-process-page-header">
          <div>
            <h2>市场部：报价单处理生成</h2>
          </div>
          <div class="erp-page-actions header-actions">
            <el-button v-permission="'market:quote-process:order-select'" :icon="DocumentChecked" type="primary" @click="openOrderPicker">选择订单</el-button>
          </div>

    </header>

    <section class="erp-surface workflow-workspace quote-process-workspace">
      <el-row :gutter="16">
        <el-col :span="6">
          <div class="erp-surface workflow-side-panel quote-side-panel">
            <div class="panel-title">RFQ 订单</div>
            <div class="selected-order-text" :title="selectedOrderLabel">
              {{ selectedOrderLabel }}
            </div>
            <el-descriptions v-if="orderInfo.id" :column="1" border class="order-desc">
              <el-descriptions-item label="客户">{{ orderInfo.customerName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="RFQ号">{{ orderInfo.rfqNo || '-' }}</el-descriptions-item>
              <el-descriptions-item label="状态">
                <el-tag type="primary">{{ orderInfo.status || '-' }}</el-tag>
              </el-descriptions-item>
            </el-descriptions>

            <div class="workflow-batch-panel batch-box">
              <div class="panel-title compact">交期调整</div>
              <el-input-number
                v-model="shiftDays"
                :min="0"
                :max="365"
                :precision="0"
                controls-position="right"
                class="days-input"
              />
              <div class="erp-table-actions workflow-muted-actions batch-actions">
                <el-button v-permission="'market:quote-process:update-lead-time'"
                  :icon="Plus"
                  plain
                  class="workflow-soft-button"
                  :disabled="!canEditQuoteLeadTime || selectedQuoteProcessItemIds.size === 0"
                  :loading="adjusting"
                  @click="applyShift(1)"
                >
                  增加
                </el-button>
                <el-button v-permission="'market:quote-process:update-lead-time'"
                  :icon="Minus"
                  plain
                  class="workflow-soft-button"
                  :disabled="!canEditQuoteLeadTime || selectedQuoteProcessItemIds.size === 0"
                  :loading="adjusting"
                  @click="applyShift(-1)"
                >
                  减少
                </el-button>
              </div>
              <div class="quote-manual-date-row">
                <el-date-picker
                  v-model="manualQuoteDate"
                  type="date"
                  value-format="YYYY-MM-DD"
                  format="YYYY-MM-DD"
                  placeholder="手动指定报价交期"
                  clearable
                  class="date-input batch-date-input quote-manual-date-input"
                  :disabled="!canEditQuoteLeadTime"
                />
                <el-button v-permission="'market:quote-process:update-lead-time'"
                  plain
                  class="workflow-soft-button quote-manual-date-button"
                  :disabled="!canEditQuoteLeadTime || selectedQuoteProcessItemIds.size === 0 || !manualQuoteDate"
                  :loading="settingQuoteDate"
                  @click="setQuoteLeadTime"
                >
                  指定
                </el-button>
              </div>
              <div class="selection-count">已选 {{ selectedQuoteProcessItemIds.size }} 项</div>
            </div>

            <div class="workflow-action-stack quote-export-actions">
              <el-button
                v-permission="QUOTE_PROCESS_SAVE_QUOTE_PERMISSION"
                :icon="DocumentChecked"
                type="primary"
                size="large"
                class="workflow-primary-action download-btn"
                :disabled="!canSaveQuoteSheet"
                :loading="savingQuoteSheet"
                @click="saveMarketQuoteSheet"
              >
                保存报价单
              </el-button>
              <el-button v-permission="'market:quote-process:export'"
                :icon="Download"
                type="primary"
                size="large"
                class="workflow-primary-action download-btn"
                :disabled="!canExportQuoteSheet"
                @click="exportQuote"
              >
                生成详细报价单
              </el-button>
              <el-button v-permission="'market:quote-process:export'"
                :icon="Download"
                plain
                size="large"
                class="workflow-soft-button download-btn secondary"
                :disabled="!canExportQuoteSheet"
                @click="exportSimpleQuote"
              >
                生成简化报价单
              </el-button>
              <el-button v-permission="'market:quote-process:export'"
                :icon="Download"
                plain
                size="large"
                class="workflow-soft-button download-btn secondary"
                :disabled="!canExportQuoteSheet"
                @click="exportTemplateQuote"
              >
                生成客户模板详细报价单
              </el-button>
              <el-button v-permission="'market:quote-process:export'"
                :icon="Download"
                plain
                size="large"
                class="workflow-soft-button download-btn secondary"
                :disabled="!canExportQuoteSheet"
                @click="exportTemplateSimpleQuote"
              >
                生成客户模板简化报价单
              </el-button>
            </div>
          </div>
        </el-col>

        <el-col :span="18">
          <div class="erp-table-surface workflow-main-panel quote-process-table-panel">
            <div class="erp-table-header workflow-table-header panel-title-row">
              <div class="panel-title">零件交期</div>
              <el-tag v-if="quoteProcessItemTotal" type="info">{{ quoteProcessItemTotal }} 项</el-tag>
            </div>
            <el-empty v-if="!selectedRfqId" description="请选择已核价或已报价订单" />
            <el-table
              ref="quoteProcessTableRef"
              v-else
              :data="pagedQuoteProcessItems"
              row-key="id"
              border
              stripe
              height="640"
              v-loading="loadingItems"
              @selection-change="handleSelectionChange"
            >
              <el-table-column type="selection" width="46" fixed :reserve-selection="true" />
              <el-table-column prop="partNo" label="图号" width="140" fixed show-overflow-tooltip />
              <el-table-column prop="version" label="版本" width="72" align="center" />
              <el-table-column prop="description" label="物料描述" min-width="160" show-overflow-tooltip />
              <el-table-column prop="qty" label="数量" width="70" align="center" />
              <el-table-column label="类型" width="90" align="center">
                <template #default="{ row }">
                  <el-tag :type="isAssemblyItem(row) ? 'warning' : 'info'" size="small">
                    {{ isAssemblyItem(row) ? '组合件' : '普通件' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="含税单价" width="110" align="right">
                <template #default="{ row }">
                  <span v-if="row.finalUnitPrice != null">¥ {{ Number(row.finalUnitPrice).toFixed(2) }}</span>
                  <el-tag v-else type="warning" size="small">未报价</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="leadTime" label="客户询价交期" width="130" show-overflow-tooltip>
                <template #default="{ row }">{{ row.leadTime || '-' }}</template>
              </el-table-column>
              <el-table-column label="报价交期" width="180">
                <template #default="{ row }">
                  <el-date-picker
                    v-model="row.quoteLeadTimeDraft"
                    type="date"
                    value-format="YYYY-MM-DD"
                    format="YYYY-MM-DD"
                    placeholder="默认沿用客户交期"
                    class="date-input"
                    clearable
                    :disabled="!canEditQuoteLeadTime"
                  />
                </template>
              </el-table-column>
              <el-table-column prop="effectiveLeadTime" label="报价单交期" width="120" show-overflow-tooltip>
                <template #default="{ row }">{{ row.effectiveLeadTime || '-' }}</template>
              </el-table-column>
              <el-table-column label="解析日期" width="110" align="center">
                <template #default="{ row }">
                  <span>{{ row.parsedDate || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="自动调整" width="96" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.canAutoShift ? 'success' : 'info'" size="small">
                    {{ row.canAutoShift ? '支持' : '手动' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="96" align="center" fixed="right">
                <template #default="{ row }">
                  <el-button v-permission="'market:quote-process:update-lead-time'"
                    :icon="Check"
                    type="primary"
                    size="small"
                    plain
                    :disabled="!canEditQuoteLeadTime"
                    :loading="!!savingRowMap[row.id]"
                    @click="saveLeadTime(row)"
                  >
                    保存
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
            <div v-if="selectedRfqId && quoteProcessItemTotal" class="erp-pagination">
              <el-pagination
                v-model:current-page="quoteProcessPagination.page"
                v-model:page-size="quoteProcessPagination.pageSize"
                :page-sizes="CLIENT_PAGE_SIZES"
                :pager-count="7"
                :total="quoteProcessItemTotal"
                layout="total, sizes, prev, pager, next, jumper"
                background
                @size-change="quoteProcessPagination.page = 1"
              />
            </div>
          </div>
        </el-col>
      </el-row>
    </section>

    <OrderPickerDialog
      v-model="orderPickerVisible"
      title="选择已核价 / 已报价 RFQ 订单"
      :allowed-statuses="QUOTE_PROCESS_ALLOWED_STATUSES"
      customer-scope="quote-process"
      order-scope="quote-process"
      @select="handleOrderSelected"
    />
  </div>
</template>

<script setup>
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Check, DocumentChecked, Download, Minus, Plus } from '@element-plus/icons-vue'
import OrderPickerDialog from './OrderPickerDialog.vue'
import {
  downloadQuote,
  downloadQuoteTemplateDetail,
  downloadQuoteTemplateSimple,
  downloadSimpleQuote,
  getQuoteProcessSummary,
  listQuoteProcessItems,
  saveQuoteSheet as saveQuoteSheetApi,
  setQuoteProcessLeadTime,
  shiftQuoteProcessLeadTime,
  updateQuoteProcessLeadTime
} from '../services/marketApi'
import {
  CLIENT_PAGE_SIZES,
  DEFAULT_CLIENT_PAGE_SIZE
} from '../utils/clientPagination'

const QUOTE_PROCESS_ALLOWED_STATUSES = ['已核价', '已报价']
const QUOTE_PROCESS_SAVE_QUOTE_PERMISSION = 'market:quote-process:save-quote'

const selectedRfqId = ref(null)
const orderInfo = ref({})
const itemList = ref([])
const quoteProcessItemTotal = ref(0)
const selectedQuoteProcessItemIds = ref(new Set())
const quoteProcessTableRef = ref(null)
const quoteProcessPagination = reactive({ page: 1, pageSize: DEFAULT_CLIENT_PAGE_SIZE })
const orderPickerVisible = ref(false)
const shiftDays = ref(0)
const manualQuoteDate = ref('')
const savingRowMap = reactive({})

const loadingItems = ref(false)
const adjusting = ref(false)
const settingQuoteDate = ref(false)
const savingQuoteSheet = ref(false)
let syncingQuoteProcessSelection = false

const selectedOrderLabel = computed(() => (
  orderInfo.value.id
    ? `${orderInfo.value.rfqNo || orderInfo.value.id} / ${orderInfo.value.customerName || '-'}`
    : '未选择订单'
))

const pagedQuoteProcessItems = computed(() => itemList.value)

const selectedQuoteProcessIdsForSubmit = () => Array.from(selectedQuoteProcessItemIds.value)

const canEditQuoteLeadTime = computed(() => orderInfo.value.status === '已核价')

const canSaveQuoteSheet = computed(() => canEditQuoteLeadTime.value)

const canExportQuoteSheet = computed(() => orderInfo.value.status === '已报价')

const syncQuoteProcessPageSelection = async () => {
  syncingQuoteProcessSelection = true
  await nextTick()
  quoteProcessTableRef.value?.clearSelection()
  pagedQuoteProcessItems.value.forEach(row => {
    if (selectedQuoteProcessItemIds.value.has(row.id)) {
      quoteProcessTableRef.value?.toggleRowSelection(row, true)
    }
  })
  await nextTick()
  syncingQuoteProcessSelection = false
}

watch(
  () => [quoteProcessPagination.page, quoteProcessPagination.pageSize],
  async () => {
    if (selectedRfqId.value) {
      await fetchQuoteProcessItems()
    }
  }
)

const openOrderPicker = () => {
  orderPickerVisible.value = true
}

const handleOrderSelected = async (order) => {
  selectedRfqId.value = order.id
  await loadQuoteProcess(order.id)
}


const fetchQuoteProcessSummary = async (rfqId = selectedRfqId.value) => {
  const response = await getQuoteProcessSummary(rfqId)
  orderInfo.value = response.orderInfo || {}
  quoteProcessItemTotal.value = Number(response.itemCount ?? response.summary?.itemCount ?? quoteProcessItemTotal.value)
  return response
}

const fetchQuoteProcessItems = async () => {
  if (!selectedRfqId.value) return
  const response = await listQuoteProcessItems(selectedRfqId.value, {
    page: quoteProcessPagination.page,
    pageSize: quoteProcessPagination.pageSize
  })
  itemList.value = response.records || []
  itemList.value = normalizeRows(itemList.value)
  quoteProcessItemTotal.value = Number(response.total ?? 0)
  await syncQuoteProcessPageSelection()
}

const clearQuoteProcessSelection = async () => {
  selectedQuoteProcessItemIds.value = new Set()
  await nextTick()
  quoteProcessTableRef.value?.clearSelection()
}

const refreshQuoteProcessWorkspace = async ({ clearSelection = false } = {}) => {
  await fetchQuoteProcessSummary()
  await fetchQuoteProcessItems()
  if (clearSelection) {
    await clearQuoteProcessSelection()
  }
}

const loadQuoteProcess = async (rfqId) => {
  loadingItems.value = true
  try {
    selectedQuoteProcessItemIds.value = new Set()
    quoteProcessPagination.page = 1
    await fetchQuoteProcessSummary(rfqId)
    await fetchQuoteProcessItems()
    await clearQuoteProcessSelection()
  } catch (error) {
    console.error(error)
    ElMessage.error(error.response?.data?.message || '报价单处理数据加载失败。')
  } finally {
    loadingItems.value = false
  }
}

const normalizeRows = (rows) => rows.map(row => ({
  ...row,
  quoteLeadTimeDraft: row.quoteLeadTime || ''
}))

const isAssemblyItem = (row) => String(row?.quoteMode || '').toUpperCase() === 'ASSEMBLY'

const handleSelectionChange = (rows) => {
  if (syncingQuoteProcessSelection) return
  const currentPageIds = new Set(pagedQuoteProcessItems.value.map(row => row.id))
  currentPageIds.forEach(id => selectedQuoteProcessItemIds.value.delete(id))
  rows.forEach(row => selectedQuoteProcessItemIds.value.add(row.id))
  selectedQuoteProcessItemIds.value = new Set(selectedQuoteProcessItemIds.value)
}

const applyShift = async (direction) => {
  if (!selectedRfqId.value) return ElMessage.warning('请先选择 RFQ 订单。')
  if (!canEditQuoteLeadTime.value) return ElMessage.warning('已报价订单交期已锁定，不能再调整。')
  if (selectedQuoteProcessItemIds.value.size === 0) return ElMessage.warning('请至少选择一个零件。')
  const days = Math.abs(Number(shiftDays.value || 0)) * direction
  if (!days) return ElMessage.warning('调整天数为 0，不需要调整。')

  adjusting.value = true
  try {
    const response = await shiftQuoteProcessLeadTime({
      rfqId: selectedRfqId.value,
      itemIds: selectedQuoteProcessIdsForSubmit(),
      days
    })
    await refreshQuoteProcessWorkspace({ clearSelection: true })
    const adjusted = response.adjustedCount || 0
    const skipped = response.skippedCount || 0
    if (skipped > 0) {
      ElMessage.warning(`已调整 ${adjusted} 项，${skipped} 项无法自动调整。`)
    } else {
      ElMessage.success(`已调整 ${adjusted} 项。`)
    }
  } catch (error) {
    console.error(error)
    ElMessage.error(error.response?.data?.message || '交期调整失败。')
  } finally {
    adjusting.value = false
  }
}

const setQuoteLeadTime = async () => {
  if (!selectedRfqId.value) return ElMessage.warning('请先选择 RFQ 订单。')
  if (!canEditQuoteLeadTime.value) return ElMessage.warning('已报价订单交期已锁定，不能再调整。')
  if (selectedQuoteProcessItemIds.value.size === 0) return ElMessage.warning('请至少选择一个零件。')
  if (!manualQuoteDate.value) return ElMessage.warning('请先选择报价交期。')

  settingQuoteDate.value = true
  try {
    const response = await setQuoteProcessLeadTime({
      rfqId: selectedRfqId.value,
      itemIds: selectedQuoteProcessIdsForSubmit(),
      quoteLeadTime: manualQuoteDate.value
    })
    await refreshQuoteProcessWorkspace({ clearSelection: true })
    const adjusted = response.adjustedCount || 0
    const skipped = response.skippedCount || 0
    if (skipped > 0) {
      ElMessage.warning(`已指定 ${adjusted} 项，${skipped} 项未处理。`)
    } else {
      ElMessage.success(`已指定 ${adjusted} 项报价交期。`)
    }
  } catch (error) {
    console.error(error)
    ElMessage.error(error.response?.data?.message || '指定报价交期失败。')
  } finally {
    settingQuoteDate.value = false
  }
}

const saveLeadTime = async (row) => {
  if (!selectedRfqId.value || !row?.id) return
  if (!canEditQuoteLeadTime.value) return ElMessage.warning('已报价订单交期已锁定，不能再保存。')
  savingRowMap[row.id] = true
  try {
    const response = await updateQuoteProcessLeadTime({
      rfqId: selectedRfqId.value,
      itemId: row.id,
      quoteLeadTime: row.quoteLeadTimeDraft
    })
    const saved = normalizeRows([response.data || {}])[0]
    const index = itemList.value.findIndex(item => item.id === row.id)
    if (index >= 0) {
      itemList.value.splice(index, 1, saved)
    }
    await fetchQuoteProcessSummary()
    ElMessage.success('报价交期已保存。')
  } catch (error) {
    console.error(error)
    ElMessage.error(error.response?.data?.message || '报价交期保存失败。')
  } finally {
    savingRowMap[row.id] = false
  }
}

const saveMarketQuoteSheet = async () => {
  if (!selectedRfqId.value) return ElMessage.warning('请先选择 RFQ 订单。')
  if (!canSaveQuoteSheet.value) return ElMessage.warning('只有已核价订单可以保存报价单。')

  savingQuoteSheet.value = true
  try {
    const response = await saveQuoteSheetApi(selectedRfqId.value)
    orderInfo.value = response.orderInfo || orderInfo.value
    await refreshQuoteProcessWorkspace({ clearSelection: true })
    ElMessage.success('报价单已保存，可导出。')
  } catch (error) {
    console.error(error)
    ElMessage.error(error.response?.data?.message || '保存报价单失败。')
  } finally {
    savingQuoteSheet.value = false
  }
}

const exportQuote = async () => {
  if (!selectedRfqId.value) return ElMessage.warning('请先选择 RFQ 订单。')
  if (!canExportQuoteSheet.value) return ElMessage.warning('请先保存报价单后再导出。')
  const fallbackName = `${orderInfo.value.rfqNo || `quote-${selectedRfqId.value}`}-详细报价单.xlsx`
  const result = await downloadQuote(selectedRfqId.value, fallbackName)
  if (!result.success) {
    ElMessage.error(result.message || '报价单导出失败。')
  }
}

const exportSimpleQuote = async () => {
  if (!selectedRfqId.value) return ElMessage.warning('请先选择 RFQ 订单。')
  if (!canExportQuoteSheet.value) return ElMessage.warning('请先保存报价单后再导出。')
  const fallbackName = `${orderInfo.value.rfqNo || `quote-${selectedRfqId.value}`}-简化报价单.xlsx`
  const result = await downloadSimpleQuote(selectedRfqId.value, fallbackName)
  if (!result.success) {
    ElMessage.error(result.message || '简化报价单导出失败。')
  }
}

const exportTemplateQuote = async () => {
  if (!selectedRfqId.value) return ElMessage.warning('请先选择 RFQ 订单。')
  if (!canExportQuoteSheet.value) return ElMessage.warning('请先保存报价单后再导出。')
  const fallbackName = `${orderInfo.value.rfqNo || `quote-${selectedRfqId.value}`}-客户模板详细报价单.xlsx`
  const result = await downloadQuoteTemplateDetail(selectedRfqId.value, fallbackName)
  if (!result.success) {
    ElMessage.error(result.message || '客户模板详细报价单导出失败。')
  }
}

const exportTemplateSimpleQuote = async () => {
  if (!selectedRfqId.value) return ElMessage.warning('请先选择 RFQ 订单。')
  if (!canExportQuoteSheet.value) return ElMessage.warning('请先保存报价单后再导出。')
  const fallbackName = `${orderInfo.value.rfqNo || `quote-${selectedRfqId.value}`}-客户模板简化报价单.xlsx`
  const result = await downloadQuoteTemplateSimple(selectedRfqId.value, fallbackName)
  if (!result.success) {
    ElMessage.error(result.message || '客户模板简化报价单导出失败。')
  }
}

</script>

<style scoped>
.quote-process-page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.quote-process-page-header h2 {
  margin: 0;
  color: var(--erp-text);
  font-size: 20px;
  line-height: 1.3;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.quote-side-panel,
.quote-process-table-panel {
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
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.panel-title-row .panel-title {
  margin-bottom: 0;
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

.order-desc {
  margin-top: 14px;
}

.batch-box {
  margin-top: 0;
}

.days-input {
  width: 100%;
}

.date-input {
  width: 100%;
}

.batch-date-input {
  margin-top: 10px;
}

.quote-manual-date-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 76px;
  gap: 8px;
  align-items: center;
  margin-top: 10px;
}

.quote-manual-date-input {
  min-width: 0;
}

.quote-manual-date-row .batch-date-input {
  margin-top: 0;
}

.quote-manual-date-button {
  width: 100%;
  margin-left: 0;
  white-space: nowrap;
}

.batch-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin-top: 10px;
}

.batch-actions :deep(.el-button) {
  margin-left: 0;
}

.selection-count {
  margin-top: 10px;
  color: var(--erp-text-secondary);
  font-size: 13px;
}

.set-date-btn {
  width: 100%;
  margin-top: 10px;
  margin-left: 0;
}

.quote-export-actions {
  margin-top: 2px;
}

.download-btn {
  width: 100%;
  margin-left: 0;
}

.download-btn.secondary {
  margin-left: 0;
}

:deep(.el-descriptions__label) {
  width: 76px;
  color: var(--erp-text-secondary);
  background: #f5f7fb;
}

@media (max-width: 1180px) {
  .quote-process-page :deep(.el-row) {
    display: block;
  }

  .quote-process-page :deep(.el-col) {
    max-width: 100%;
    width: 100%;
    margin-bottom: 14px;
  }
}

@media (max-width: 760px) {
  .quote-process-page-header {
    flex-direction: column;
    align-items: stretch;
  }

  .header-actions {
    justify-content: flex-start;
  }
}
</style>
