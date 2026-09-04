<template>
  <div class="erp-page quote-process-page workflow-page">
    <header class="erp-page-header quote-process-page-header">
          <div>
            <h2>报价处理</h2>
            <p>维护已核价订单的报价交期，已报价/已提单订单只读导出客户报价单。</p>
          </div>
          <div class="erp-page-actions header-actions">
            <el-button v-permission="'market:quote-process:order-select'" :icon="DocumentChecked" type="primary" @click="openOrderPicker">选择订单</el-button>
            <el-button v-permission="'market:quote-process:order-select'" :icon="DocumentChecked" plain @click="openSupplementalQuoteOrderPicker">补充报价待确认</el-button>
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
            <el-alert
              v-if="isSupplementalQuoteProcessScope"
              class="supplemental-quote-alert"
              title="补充报价待确认只处理本次补图零件，不重置整单历史报价单或历史提单。"
              type="warning"
              :closable="false"
              show-icon
            />

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
                  :disabled="!canEditQuoteLeadTime || selectedQuoteProcessItemIds.size === 0 || quoteLeadTimeBatchPending || savingQuoteSheet || importSelecting || savingQuoteLeadTimeDrafts || leadTimeReplacementBusy"
                  :loading="adjusting"
                  @click="applyShift(1)"
                >
                  增加
                </el-button>
                <el-button v-permission="'market:quote-process:update-lead-time'"
                  :icon="Minus"
                  plain
                  class="workflow-soft-button"
                  :disabled="!canEditQuoteLeadTime || selectedQuoteProcessItemIds.size === 0 || quoteLeadTimeBatchPending || savingQuoteSheet || importSelecting || savingQuoteLeadTimeDrafts || leadTimeReplacementBusy"
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
                  :disabled="!canEditQuoteLeadTime || selectedQuoteProcessItemIds.size === 0 || !manualQuoteDate || quoteLeadTimeBatchPending || savingQuoteSheet || importSelecting || savingQuoteLeadTimeDrafts || leadTimeReplacementBusy"
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
                :disabled="!canSaveQuoteSheet || savingQuoteSheet || quoteLeadTimeBatchPending || importSelecting || savingQuoteLeadTimeDrafts || leadTimeReplacementBusy"
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
                :disabled="!canExportQuoteSheet || isQuoteExporting('exportDetail')"
                :loading="isQuoteExporting('exportDetail')"
                @click="exportQuote"
              >
                生成详细报价单
              </el-button>
              <el-button v-permission="'market:quote-process:export'"
                :icon="Download"
                plain
                size="large"
                class="workflow-soft-button download-btn secondary"
                :disabled="!canExportQuoteSheet || isQuoteExporting('exportOptimizedDetail')"
                :loading="isQuoteExporting('exportOptimizedDetail')"
                @click="exportOptimizedQuote"
              >
                优化详细报价单
              </el-button>
              <el-button v-permission="'market:quote-process:export'"
                :icon="Download"
                plain
                size="large"
                class="workflow-soft-button download-btn secondary"
                :disabled="!canExportQuoteSheet || isQuoteExporting('exportSimple')"
                :loading="isQuoteExporting('exportSimple')"
                @click="exportSimpleQuote"
              >
                生成简化报价单
              </el-button>
              <el-button v-permission="'market:quote-process:export'"
                :icon="Download"
                plain
                size="large"
                class="workflow-soft-button download-btn secondary"
                :disabled="!canExportQuoteSheet || isQuoteExporting('exportTemplateDetail')"
                :loading="isQuoteExporting('exportTemplateDetail')"
                @click="exportTemplateQuote"
              >
                生成客户模板详细报价单
              </el-button>
              <el-button v-permission="'market:quote-process:export'"
                :icon="Download"
                plain
                size="large"
                class="workflow-soft-button download-btn secondary"
                :disabled="!canExportQuoteSheet || isQuoteExporting('exportOptimizedTemplateDetail')"
                :loading="isQuoteExporting('exportOptimizedTemplateDetail')"
                @click="exportOptimizedTemplateQuote"
              >
                优化客户模板详细报价单
              </el-button>
              <el-button v-permission="'market:quote-process:export'"
                :icon="Download"
                plain
                size="large"
                class="workflow-soft-button download-btn secondary"
                :disabled="!canExportQuoteSheet || isQuoteExporting('exportTemplateSimple')"
                :loading="isQuoteExporting('exportTemplateSimple')"
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
              <div class="quote-process-title-group">
                <div class="panel-title">零件交期</div>
                <el-tag v-if="quoteProcessItemTotal" type="info">{{ quoteProcessItemTotal }} 项</el-tag>
              </div>
              <div class="quote-process-table-actions import-action-row">
                <el-button
                  v-permission="'market:quote-process:update-lead-time'"
                  size="small"
                  plain
                  class="workflow-soft-button"
                  :disabled="leadTimeReplacementDisabled"
                  :loading="leadTimeReplacementBusy"
                  @click="openLeadTimeReplacementDialog"
                >
                  表格数据替换
                </el-button>
                <el-button
                  v-permission="'market:quote-process:update-lead-time'"
                  size="small"
                  type="primary"
                  plain
                  class="workflow-soft-button"
                  :disabled="!hasQuoteLeadTimeDraftChanges || !canEditQuoteLeadTime || savingQuoteLeadTimeDrafts || quoteLeadTimeBatchPending || savingQuoteSheet || importSelecting || leadTimeReplacementBusy"
                  :loading="savingQuoteLeadTimeDrafts"
                  @click="saveAllQuoteLeadTimeDrafts"
                >
                  保存修改
                </el-button>
                <el-upload
                  ref="selectionUploadRef"
                  action="#"
                  :auto-upload="false"
                  :show-file-list="false"
                  accept=".xlsx, .xls"
                  :on-change="handleQuoteProcessSelectionExcelChange"
                >
                  <template #trigger>
                    <el-button
                      v-permission="'market:quote-process:update-lead-time'"
                      size="small"
                      plain
                      class="workflow-soft-button"
                      :disabled="!canEditQuoteLeadTime || quoteProcessItemTotal === 0 || importSelecting || quoteLeadTimeBatchPending || savingQuoteSheet || savingQuoteLeadTimeDrafts || leadTimeReplacementBusy"
                      :loading="importSelecting"
                    >
                      导入表格选定
                    </el-button>
                  </template>
                </el-upload>
              </div>
            </div>
            <el-empty v-if="!selectedRfqId" :description="emptyOrderDescription" />
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
                  <span v-if="row.taxIncludedUnitPrice != null">¥ {{ Number(row.taxIncludedUnitPrice).toFixed(2) }}</span>
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
                    @change="rememberQuoteLeadTimeDraft(row)"
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
                    :disabled="!canEditQuoteLeadTime || isLeadTimeRowSaving(row) || quoteLeadTimeBatchPending || savingQuoteSheet || savingQuoteLeadTimeDrafts || leadTimeReplacementBusy"
                    :loading="isLeadTimeRowSaving(row)"
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
      title="选择已核价 / 已报价 / 已提单 RFQ 订单"
      :allowed-statuses="QUOTE_PROCESS_ALLOWED_STATUSES"
      customer-scope="quote-process"
      order-scope="quote-process"
      @select="handleOrderSelected"
    />
    <OrderPickerDialog
      v-model="supplementalQuoteOrderPickerVisible"
      title="补充报价待确认"
      customer-scope="quote-process"
      order-scope="quote-process-supplemental"
      @select="handleSupplementalQuoteOrderSelected"
    />
    <QuoteLeadTimeReplacementDialog
      v-model="leadTimeReplacementVisible"
      :rfq-id="selectedRfqId"
      :disabled="leadTimeReplacementDisabled"
      :scope="currentQuoteProcessScope"
      @busy-change="leadTimeReplacementBusy = $event"
      @confirmed="handleLeadTimeReplacementConfirmed"
    />
  </div>
</template>

<script setup>
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Check, DocumentChecked, Download, Minus, Plus } from '@element-plus/icons-vue'
import OrderPickerDialog from './OrderPickerDialog.vue'
import QuoteLeadTimeReplacementDialog from './quote-process/QuoteLeadTimeReplacementDialog.vue'
import {
  batchUpdateQuoteProcessLeadTime,
  downloadOptimizedQuote,
  downloadOptimizedQuoteTemplateDetail,
  downloadQuote,
  downloadQuoteTemplateDetail,
  downloadQuoteTemplateSimple,
  downloadSimpleQuote,
  getQuoteProcessSummary,
  listQuoteProcessItems,
  parseQuoteProcessPartSelection,
  saveQuoteSheet as saveQuoteSheetApi,
  saveSupplementalQuoteSheet as saveSupplementalQuoteSheetApi,
  setQuoteProcessLeadTime,
  shiftQuoteProcessLeadTime,
  updateQuoteProcessLeadTime
} from '../services/marketApi'
import {
  CLIENT_PAGE_SIZES,
  DEFAULT_CLIENT_PAGE_SIZE
} from '../utils/clientPagination'
import { usePendingAction } from '../composables/usePendingAction'

const QUOTE_PROCESS_ALLOWED_STATUSES = ['已核价', '已报价', '部分已提单', '已生成提单']
const QUOTE_PROCESS_EXPORTABLE_STATUSES = ['已报价', '部分已提单', '已生成提单']
const QUOTE_PROCESS_RELEASED_STATUSES = ['部分已提单', '已生成提单']
const SUPPLEMENTAL_DRAWING_QUOTE_SCOPE = 'SUPPLEMENTAL_DRAWING_QUOTE'
const QUOTE_PROCESS_SAVE_QUOTE_PERMISSION = 'market:quote-process:save-quote'
const QUOTE_PROCESS_LOCKED_MESSAGE = '当前订单交期已锁定'

const selectedRfqId = ref(null)
const orderInfo = ref({})
const itemList = ref([])
const quoteProcessItemTotal = ref(0)
const selectedQuoteProcessItemIds = ref(new Set())
const quoteProcessTableRef = ref(null)
const selectionUploadRef = ref(null)
const quoteProcessPagination = reactive({ page: 1, pageSize: DEFAULT_CLIENT_PAGE_SIZE })
const orderPickerVisible = ref(false)
const supplementalQuoteOrderPickerVisible = ref(false)
const leadTimeReplacementVisible = ref(false)
const leadTimeReplacementBusy = ref(false)
const quoteLeadTimeDraftCache = ref(new Map())
const currentQuoteProcessScope = ref('')
const shiftDays = ref(0)
const manualQuoteDate = ref('')

const loadingItems = ref(false)
let syncingQuoteProcessSelection = false
const { isPending, runAction } = usePendingAction()
const quoteProcessActionKeys = {
  saveQuote: () => `market:quote-process:save-quote:${selectedRfqId.value || 'none'}`,
  leadTimeBatch: () => `market:quote-process:lead-time-batch:${selectedRfqId.value || 'none'}`,
  leadTimeBulkSave: () => `market:quote-process:lead-time-bulk-save:${selectedRfqId.value || 'none'}`,
  importSelection: () => `market:quote-process:import-selection:${selectedRfqId.value || 'none'}`,
  exportDetail: () => `market:quote-process:export-detail:${selectedRfqId.value || 'none'}`,
  exportOptimizedDetail: () => `market:quote-process:export-optimized-detail:${selectedRfqId.value || 'none'}`,
  exportSimple: () => `market:quote-process:export-simple:${selectedRfqId.value || 'none'}`,
  exportTemplateDetail: () => `market:quote-process:export-template-detail:${selectedRfqId.value || 'none'}`,
  exportOptimizedTemplateDetail: () => `market:quote-process:export-optimized-template-detail:${selectedRfqId.value || 'none'}`,
  exportTemplateSimple: () => `market:quote-process:export-template-simple:${selectedRfqId.value || 'none'}`,
  leadTimeRow: (itemId) => `market:quote-process:lead-time:${itemId}`
}
const quoteLeadTimeBatchPending = computed(() => isPending(quoteProcessActionKeys.leadTimeBatch()))
const adjusting = quoteLeadTimeBatchPending
const settingQuoteDate = quoteLeadTimeBatchPending
const savingQuoteSheet = computed(() => isPending(quoteProcessActionKeys.saveQuote()))
const savingQuoteLeadTimeDrafts = computed(() => isPending(quoteProcessActionKeys.leadTimeBulkSave()))
const importSelecting = computed(() => isPending(quoteProcessActionKeys.importSelection()))
const isQuoteExporting = (action) => isPending(quoteProcessActionKeys[action]())
const isLeadTimeRowSaving = (row) => isPending(quoteProcessActionKeys.leadTimeRow(row?.id))

const selectedOrderLabel = computed(() => (
  orderInfo.value.id
    ? `${orderInfo.value.rfqNo || orderInfo.value.id} / ${orderInfo.value.customerName || '-'}`
    : '未选择订单'
))

const pagedQuoteProcessItems = computed(() => itemList.value)

const isSupplementalQuoteProcessScope = computed(() => currentQuoteProcessScope.value === SUPPLEMENTAL_DRAWING_QUOTE_SCOPE)

const emptyOrderDescription = computed(() => (
  isSupplementalQuoteProcessScope.value
    ? '请选择补充报价待确认订单'
    : '请选择已核价、已报价或已提单订单'
))

const selectedQuoteProcessIdsForSubmit = () => Array.from(selectedQuoteProcessItemIds.value)

const canEditQuoteLeadTime = computed(() => (
  isSupplementalQuoteProcessScope.value
    ? QUOTE_PROCESS_RELEASED_STATUSES.includes(orderInfo.value.status)
    : orderInfo.value.status === '已核价'
))

const canSaveQuoteSheet = computed(() => (
  isSupplementalQuoteProcessScope.value
    ? canEditQuoteLeadTime.value && quoteProcessItemTotal.value > 0
    : canEditQuoteLeadTime.value
))

const canExportQuoteSheet = computed(() => QUOTE_PROCESS_EXPORTABLE_STATUSES.includes(orderInfo.value.status))

const changedQuoteLeadTimeDrafts = computed(() => Array.from(quoteLeadTimeDraftCache.value.values()))

const hasQuoteLeadTimeDraftChanges = computed(() => changedQuoteLeadTimeDrafts.value.length > 0)

const leadTimeReplacementDisabled = computed(() => (
  !canEditQuoteLeadTime.value
  || !selectedRfqId.value
  || quoteProcessItemTotal.value === 0
  || importSelecting.value
  || quoteLeadTimeBatchPending.value
  || savingQuoteSheet.value
  || savingQuoteLeadTimeDrafts.value
  || leadTimeReplacementBusy.value
))

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

const openSupplementalQuoteOrderPicker = () => {
  supplementalQuoteOrderPickerVisible.value = true
}

const handleOrderSelected = async (order) => {
  currentQuoteProcessScope.value = ''
  selectedRfqId.value = order.id
  await loadQuoteProcess(order.id)
}

const handleSupplementalQuoteOrderSelected = async (order) => {
  currentQuoteProcessScope.value = SUPPLEMENTAL_DRAWING_QUOTE_SCOPE
  selectedRfqId.value = order.id
  await loadQuoteProcess(order.id)
}

const fetchQuoteProcessSummary = async (rfqId = selectedRfqId.value) => {
  const response = await getQuoteProcessSummary(rfqId, currentQuoteProcessScope.value)
  orderInfo.value = response.orderInfo || {}
  quoteProcessItemTotal.value = Number(response.itemCount ?? response.summary?.itemCount ?? quoteProcessItemTotal.value)
  return response
}

const fetchQuoteProcessItems = async () => {
  if (!selectedRfqId.value) return
  const response = await listQuoteProcessItems(selectedRfqId.value, {
    page: quoteProcessPagination.page,
    pageSize: quoteProcessPagination.pageSize,
    scope: currentQuoteProcessScope.value || undefined
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
    quoteLeadTimeDraftCache.value = new Map()
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

const normalizeQuoteLeadTime = (value) => (value === null || value === undefined ? '' : String(value))

const normalizeRows = (rows) => rows.map(row => ({
  ...row,
  quoteLeadTimeOriginal: normalizeQuoteLeadTime(row.quoteLeadTime),
  quoteLeadTimeDraft: quoteLeadTimeDraftCache.value.get(row.id)?.quoteLeadTime ?? normalizeQuoteLeadTime(row.quoteLeadTime)
}))

const rememberQuoteLeadTimeDraft = (row) => {
  if (!row?.id) return
  const quoteLeadTime = normalizeQuoteLeadTime(row.quoteLeadTimeDraft)
  const originalQuoteLeadTime = normalizeQuoteLeadTime(row.quoteLeadTimeOriginal)
  const nextCache = new Map(quoteLeadTimeDraftCache.value)
  if (quoteLeadTime === originalQuoteLeadTime) {
    nextCache.delete(row.id)
  } else {
    nextCache.set(row.id, {
      itemId: row.id,
      quoteLeadTime
    })
  }
  quoteLeadTimeDraftCache.value = nextCache
}

const isAssemblyItem = (row) => String(row?.quoteMode || '').toUpperCase() === 'ASSEMBLY'

const handleSelectionChange = (rows) => {
  if (syncingQuoteProcessSelection) return
  const currentPageIds = new Set(pagedQuoteProcessItems.value.map(row => row.id))
  currentPageIds.forEach(id => selectedQuoteProcessItemIds.value.delete(id))
  rows.forEach(row => selectedQuoteProcessItemIds.value.add(row.id))
  selectedQuoteProcessItemIds.value = new Set(selectedQuoteProcessItemIds.value)
}

const handleQuoteProcessSelectionExcelChange = async (uploadFile) => {
  const file = uploadFile?.raw
  selectionUploadRef.value?.clearFiles()
  if (!selectedRfqId.value) return ElMessage.warning('请先选择 RFQ 订单。')
  if (!canEditQuoteLeadTime.value) return ElMessage.warning(`${QUOTE_PROCESS_LOCKED_MESSAGE}，不能导入表格选定。`)
  if (!file) return

  const fileName = String(file.name || '').toLowerCase()
  if (!fileName.endsWith('.xlsx') && !fileName.endsWith('.xls')) {
    return ElMessage.warning('请上传 xlsx 或 xls 表格。')
  }

  const formData = new FormData()
  formData.append('excelFile', file)

  return runAction(quoteProcessActionKeys.importSelection(), async () => {
    try {
      const response = await parseQuoteProcessPartSelection(selectedRfqId.value, formData)
      const importedIds = (response?.data || [])
        .map(row => row.id)
        .filter(id => id !== null && id !== undefined)
      selectedQuoteProcessItemIds.value = new Set(importedIds)
      await syncQuoteProcessPageSelection()

      const importedCount = Number(response?.importedCount ?? 0)
      const matchedCount = Number(response?.matchedCount ?? 0)
      const selectedCount = Number(response?.selectedCount ?? importedIds.length)
      const skippedCount = Number(response?.skippedCount ?? 0)
      if (importedCount === 0) {
        ElMessage.warning('表格中未读取到有效的图号+版本。')
      } else if (selectedCount === 0) {
        ElMessage.warning(`已读取 ${importedCount} 个图号+版本，但没有匹配到当前订单的报价零件。`)
      } else {
        const skippedText = skippedCount > 0 ? `，${skippedCount} 项未选定` : ''
        ElMessage.success(`已读取 ${importedCount} 个图号+版本，匹配 ${matchedCount} 项，已选定 ${selectedCount} 项${skippedText}。`)
      }
    } catch (error) {
      console.error(error)
      ElMessage.error(error.response?.data?.message || '导入表格选定失败。')
    }
  })
}

const openLeadTimeReplacementDialog = () => {
  if (!selectedRfqId.value) return ElMessage.warning('请先选择 RFQ 订单。')
  if (!canEditQuoteLeadTime.value) return ElMessage.warning(`${QUOTE_PROCESS_LOCKED_MESSAGE}，不能进行表格数据替换。`)
  leadTimeReplacementVisible.value = true
}

const handleLeadTimeReplacementConfirmed = async () => {
  quoteLeadTimeDraftCache.value = new Map()
  await refreshQuoteProcessWorkspace({ clearSelection: false })
}

const applyShift = async (direction) => {
  if (!selectedRfqId.value) return ElMessage.warning('请先选择 RFQ 订单。')
  if (!canEditQuoteLeadTime.value) return ElMessage.warning(`${QUOTE_PROCESS_LOCKED_MESSAGE}，不能再调整。`)
  if (selectedQuoteProcessItemIds.value.size === 0) return ElMessage.warning('请至少选择一个零件。')
  const days = Math.abs(Number(shiftDays.value || 0)) * direction
  if (!days) return ElMessage.warning('调整天数为 0，不需要调整。')

  return runAction(quoteProcessActionKeys.leadTimeBatch(), async () => {
    try {
      const response = await shiftQuoteProcessLeadTime({
        rfqId: selectedRfqId.value,
        itemIds: selectedQuoteProcessIdsForSubmit(),
        days,
        scope: currentQuoteProcessScope.value || undefined
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
    }
  })
}

const setQuoteLeadTime = async () => {
  if (!selectedRfqId.value) return ElMessage.warning('请先选择 RFQ 订单。')
  if (!canEditQuoteLeadTime.value) return ElMessage.warning(`${QUOTE_PROCESS_LOCKED_MESSAGE}，不能再调整。`)
  if (selectedQuoteProcessItemIds.value.size === 0) return ElMessage.warning('请至少选择一个零件。')
  if (!manualQuoteDate.value) return ElMessage.warning('请先选择报价交期。')

  return runAction(quoteProcessActionKeys.leadTimeBatch(), async () => {
    try {
      const response = await setQuoteProcessLeadTime({
        rfqId: selectedRfqId.value,
        itemIds: selectedQuoteProcessIdsForSubmit(),
        quoteLeadTime: manualQuoteDate.value,
        scope: currentQuoteProcessScope.value || undefined
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
    }
  })
}

const saveLeadTime = async (row) => {
  if (!selectedRfqId.value || !row?.id) return
  if (!canEditQuoteLeadTime.value) return ElMessage.warning(`${QUOTE_PROCESS_LOCKED_MESSAGE}，不能再保存。`)
  return runAction(quoteProcessActionKeys.leadTimeRow(row.id), async () => {
    try {
      const response = await updateQuoteProcessLeadTime({
        rfqId: selectedRfqId.value,
        itemId: row.id,
        quoteLeadTime: row.quoteLeadTimeDraft,
        scope: currentQuoteProcessScope.value || undefined
      })
      const saved = normalizeRows([response.data || {}])[0]
      const index = itemList.value.findIndex(item => item.id === row.id)
      if (index >= 0) {
        itemList.value.splice(index, 1, saved)
      }
      const nextCache = new Map(quoteLeadTimeDraftCache.value)
      nextCache.delete(row.id)
      quoteLeadTimeDraftCache.value = nextCache
      await fetchQuoteProcessSummary()
      ElMessage.success('报价交期已保存。')
    } catch (error) {
      console.error(error)
      ElMessage.error(error.response?.data?.message || '报价交期保存失败。')
    }
  })
}

const saveAllQuoteLeadTimeDrafts = async () => {
  if (!selectedRfqId.value) return ElMessage.warning('请先选择 RFQ 订单。')
  if (!canEditQuoteLeadTime.value) return ElMessage.warning(`${QUOTE_PROCESS_LOCKED_MESSAGE}，不能再保存。`)
  if (!hasQuoteLeadTimeDraftChanges.value) return ElMessage.warning('没有需要保存的报价交期修改。')

  return runAction(quoteProcessActionKeys.leadTimeBulkSave(), async () => {
    try {
      const items = changedQuoteLeadTimeDrafts.value
      const response = await batchUpdateQuoteProcessLeadTime({
        rfqId: selectedRfqId.value,
        items: changedQuoteLeadTimeDrafts.value,
        scope: currentQuoteProcessScope.value || undefined
      })
      quoteLeadTimeDraftCache.value = new Map()
      await refreshQuoteProcessWorkspace({ clearSelection: false })
      const updated = Number(response?.updatedCount ?? response?.adjustedCount ?? items.length)
      ElMessage.success(`已保存 ${updated} 项报价交期修改。`)
    } catch (error) {
      console.error(error)
      ElMessage.error(error.response?.data?.message || '批量保存报价交期失败。')
    }
  })
}

const saveMarketQuoteSheet = async () => {
  if (!selectedRfqId.value) return ElMessage.warning('请先选择 RFQ 订单。')
  if (!canSaveQuoteSheet.value) {
    return ElMessage.warning(isSupplementalQuoteProcessScope.value
      ? '没有补充报价待确认零件，不能保存补充报价。'
      : '只有已核价订单可以保存报价单。')
  }

  return runAction(quoteProcessActionKeys.saveQuote(), async () => {
    try {
      let response
      if (isSupplementalQuoteProcessScope.value) {
        response = await saveSupplementalQuoteSheetApi(selectedRfqId.value)
      } else {
        response = await saveQuoteSheetApi(selectedRfqId.value)
      }
      orderInfo.value = response.orderInfo || orderInfo.value
      await refreshQuoteProcessWorkspace({ clearSelection: true })
      ElMessage.success(isSupplementalQuoteProcessScope.value
        ? '补充报价已确认，可进入议价提单追加提单。'
        : '报价单已保存，可导出。')
    } catch (error) {
      console.error(error)
      ElMessage.error(error.response?.data?.message || '保存报价单失败。')
    }
  })
}

const runQuoteExport = async (actionKey, fallbackName, downloadAction, errorMessage) => {
  if (!selectedRfqId.value) return ElMessage.warning('请先选择 RFQ 订单。')
  if (!canExportQuoteSheet.value) return ElMessage.warning('请先保存报价单后再导出。')

  return runAction(actionKey(), async () => {
    try {
      const result = await downloadAction(selectedRfqId.value, fallbackName)
      if (!result.success) {
        ElMessage.error(result.message || errorMessage)
      }
      return result
    } catch (error) {
      console.error(error)
      ElMessage.error(error.response?.data?.message || error.message || errorMessage)
      return { success: false, message: error.message }
    }
  })
}

const exportQuote = async () => {
  const fallbackName = `${orderInfo.value.rfqNo || `quote-${selectedRfqId.value}`}-详细报价单.xlsx`
  return runQuoteExport(quoteProcessActionKeys.exportDetail, fallbackName, downloadQuote, '报价单导出失败。')
}

const exportOptimizedQuote = async () => {
  const fallbackName = `${orderInfo.value.rfqNo || `quote-${selectedRfqId.value}`}-优化详细报价单.xlsx`
  return runQuoteExport(quoteProcessActionKeys.exportOptimizedDetail, fallbackName, downloadOptimizedQuote, '优化详细报价单导出失败。')
}

const exportSimpleQuote = async () => {
  const fallbackName = `${orderInfo.value.rfqNo || `quote-${selectedRfqId.value}`}-简化报价单.xlsx`
  return runQuoteExport(quoteProcessActionKeys.exportSimple, fallbackName, downloadSimpleQuote, '简化报价单导出失败。')
}

const exportTemplateQuote = async () => {
  const fallbackName = `${orderInfo.value.rfqNo || `quote-${selectedRfqId.value}`}-客户模板详细报价单.xlsx`
  return runQuoteExport(quoteProcessActionKeys.exportTemplateDetail, fallbackName, downloadQuoteTemplateDetail, '客户模板详细报价单导出失败。')
}

const exportOptimizedTemplateQuote = async () => {
  const fallbackName = `${orderInfo.value.rfqNo || `quote-${selectedRfqId.value}`}-优化客户模板详细报价单.xlsx`
  return runQuoteExport(quoteProcessActionKeys.exportOptimizedTemplateDetail, fallbackName, downloadOptimizedQuoteTemplateDetail, '优化客户模板详细报价单导出失败。')
}

const exportTemplateSimpleQuote = async () => {
  const fallbackName = `${orderInfo.value.rfqNo || `quote-${selectedRfqId.value}`}-客户模板简化报价单.xlsx`
  return runQuoteExport(quoteProcessActionKeys.exportTemplateSimple, fallbackName, downloadQuoteTemplateSimple, '客户模板简化报价单导出失败。')
}

</script>

<style scoped>
.quote-process-page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

.quote-process-page-header h2 {
  margin: 0;
  color: var(--erp-text);
  font-size: 20px;
  line-height: 1.3;
}

.quote-process-page-header p {
  margin: 6px 0 0;
  color: var(--erp-text-secondary);
  font-size: 13px;
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

.quote-process-title-group {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.import-action-row {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 10px;
  width: 100%;
}

.quote-process-table-actions {
  justify-content: flex-end;
  width: auto;
  margin-top: 0;
}

.quote-process-table-actions :deep(.el-upload) {
  display: flex;
}

.quote-process-table-actions :deep(.el-button) {
  margin-left: 0;
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
  width: 100%;
  max-width: 100%;
  min-width: 0;
}

.quote-manual-date-row :deep(.quote-manual-date-input.el-date-editor.el-input) {
  width: 100%;
  max-width: 100%;
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

  .quote-process-table-actions {
    justify-content: stretch;
    width: 100%;
  }

  .quote-process-table-actions :deep(.el-button),
  .quote-process-table-actions :deep(.el-upload) {
    width: 100%;
  }
}
</style>
