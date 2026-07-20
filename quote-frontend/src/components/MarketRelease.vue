<template>
  <div class="erp-page release-page">
    <header class="erp-page-header release-page-header">
          <div>
            <h2>市场部：议价确认与提单生成</h2>
            <p>选择客户最终通过的零件，生成正式提单和对应工艺表。</p>
          </div>
          <div class="erp-page-actions release-page-actions"><el-button type="primary" :loading="loading" @click="openOrderPicker">选择订单</el-button></div>

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
              <el-button plain class="workflow-soft-button" @click="openOrderPicker" :loading="loading">选择订单</el-button>
            </div>

            <el-descriptions v-if="orderInfo.id" :column="1" border class="order-desc">
              <el-descriptions-item label="客户">{{ orderInfo.customerName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="RFQ号">{{ orderInfo.rfqNo || '-' }}</el-descriptions-item>
              <el-descriptions-item label="状态">{{ orderInfo.status || '-' }}</el-descriptions-item>
            </el-descriptions>

            <el-form label-width="80px" class="ticket-form">
              <el-form-item label="RFQ号">
                <el-input v-model="releaseForm.projectNo" placeholder="默认取 RFQ 号" disabled />
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
                :disabled="selectedRows.length === 0 || !releaseForm.internalProjectNo.trim() || !releaseForm.customerOrderNo.trim()"
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
                    :disabled="!selectedRfqId || itemList.length === 0"
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
            <div v-if="itemList.length" class="erp-pagination">
              <el-pagination
                v-model:current-page="releaseItemPagination.page"
                v-model:page-size="releaseItemPagination.pageSize"
                :page-sizes="CLIENT_PAGE_SIZES"
                :pager-count="7"
                :total="itemList.length"
                layout="total, sizes, prev, pager, next, jumper"
                background
                @size-change="releaseItemPagination.page = 1"
              />
            </div>
          </div>

          <div class="erp-table-surface release-table-panel ticket-panel">
            <div class="erp-table-header panel-title-row">
              <div class="panel-title">已生成提单行</div>
              <el-button size="small" plain class="workflow-soft-button" :disabled="ticketList.length === 0" @click="exportExcel('tickets')">
                导出提单 Excel
              </el-button>
            </div>
            <el-table :data="pagedTicketList" border stripe height="280" v-loading="loadingTickets || deleting">
              <el-table-column prop="internalProjectNo" label="内部项目号" width="130" show-overflow-tooltip />
              <el-table-column prop="itemType" label="类型" width="90" align="center" />
              <el-table-column prop="partNo" label="图号" width="130" show-overflow-tooltip />
              <el-table-column prop="version" label="版本" width="80" align="center" />
              <el-table-column prop="qty" label="数量" width="70" align="center" />
              <el-table-column prop="engineeringMaterialName" label="材料" width="110" show-overflow-tooltip />
              <el-table-column prop="surfaceTreatment" label="表面处理" width="110" show-overflow-tooltip />
              <el-table-column prop="startDate" label="开工日期" width="110" />
              <el-table-column prop="deliveryDate" label="交货日期" width="110" />
              <el-table-column label="安排类型" width="100">
                <template #default="{ row }">{{ displayTicketText(row.arrangeType) }}</template>
              </el-table-column>
              <el-table-column label="备注" min-width="180" show-overflow-tooltip>
                <template #default="{ row }">{{ displayTicketText(row.remark) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="90" align="center" fixed="right">
                <template #default="scope">
                  <div class="erp-table-actions"><el-button v-permission="'market:release:reset'" type="danger" size="small" plain class="erp-danger-action" @click="deleteTicket(scope.row)">删除</el-button></div>
                </template>
              </el-table-column>
            </el-table>
            <div v-if="ticketList.length" class="erp-pagination">
              <el-pagination
                v-model:current-page="ticketPagination.page"
                v-model:page-size="ticketPagination.pageSize"
                :page-sizes="CLIENT_PAGE_SIZES"
                :pager-count="7"
                :total="ticketList.length"
                layout="total, sizes, prev, pager, next, jumper"
                background
                @size-change="ticketPagination.page = 1"
              />
            </div>
          </div>

          <div class="erp-table-surface release-table-panel ticket-panel">
            <div class="erp-table-header panel-title-row">
              <div class="panel-title">已生成工艺表行</div>
              <el-button size="small" plain class="workflow-soft-button" :disabled="processSheetList.length === 0" @click="exportExcel('process')">
                导出工艺表 Excel
              </el-button>
            </div>
            <el-table :data="pagedProcessSheetList" border stripe height="260" v-loading="loadingProcessSheets || deleting">
              <el-table-column prop="internalProjectNo" label="内部项目号" width="130" show-overflow-tooltip />
              <el-table-column prop="itemType" label="类型" width="90" align="center" />
              <el-table-column prop="partNo" label="图号" width="130" show-overflow-tooltip />
              <el-table-column prop="version" label="版本" width="80" align="center" />
              <el-table-column prop="description" label="材料描述" min-width="160" show-overflow-tooltip />
              <el-table-column prop="qty" label="数量" width="70" align="center" />
              <el-table-column prop="engineeringMaterialName" label="材料" width="120" show-overflow-tooltip />
              <el-table-column label="毛重(kg)" width="95" align="right">
                <template #default="{ row }">{{ formatWeight(row.materialGrossWeight) }}</template>
              </el-table-column>
              <el-table-column label="净重(kg)" width="95" align="right">
                <template #default="{ row }">{{ formatWeight(row.partNetWeight) }}</template>
              </el-table-column>
              <el-table-column prop="surfaceTreatment" label="表面处理" width="130" show-overflow-tooltip />
              <el-table-column prop="deliveryDate" label="交期" width="110" />
              <el-table-column label="工艺路线" min-width="220" show-overflow-tooltip>
                <template #default="scope">
                  {{ formatRoute(scope.row.processRoute) }}
                </template>
              </el-table-column>
              <el-table-column label="操作" width="90" align="center" fixed="right">
                <template #default="scope">
                  <div class="erp-table-actions"><el-button v-permission="'market:release:reset'" type="danger" size="small" plain class="erp-danger-action" @click="deleteProcessSheet(scope.row)">删除</el-button></div>
                </template>
              </el-table-column>
            </el-table>
            <div v-if="processSheetList.length" class="erp-pagination">
              <el-pagination
                v-model:current-page="processSheetPagination.page"
                v-model:page-size="processSheetPagination.pageSize"
                :page-sizes="CLIENT_PAGE_SIZES"
                :pager-count="7"
                :total="processSheetList.length"
                layout="total, sizes, prev, pager, next, jumper"
                background
                @size-change="processSheetPagination.page = 1"
              />
            </div>
          </div>
        </el-col>
      </el-row>
    </section>

    <OrderPickerDialog
      v-model="orderPickerVisible"
      title="选择市场部议价订单"
      @select="handleOrderSelected"
    />
  </div>
</template>

<script setup>
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import OrderPickerDialog from './OrderPickerDialog.vue'
import {
  deleteProcessSheet as deleteProcessSheetRequest,
  deleteTicket as deleteTicketRequest,
  downloadMarketExport,
  generateTicket,
  getMarketSummary,
  listInternalTargetLeadTimeItems,
  listProcessSheets,
  listTickets,
  parsePartSelection,
  setInternalTargetLeadTime as setInternalTargetLeadTimeRequest,
  shiftInternalTargetLeadTime,
  updateInternalTargetLeadTime
} from '../services/marketApi'
import {
  CLIENT_PAGE_SIZES,
  DEFAULT_CLIENT_PAGE_SIZE,
  clampPage,
  paginateRows
} from '../utils/clientPagination'

const selectedRfqId = ref(null)
const orderPickerVisible = ref(false)
const orderInfo = ref({})
const itemList = ref([])
const ticketList = ref([])
const processSheetList = ref([])
const selectedRows = ref([])
const itemTableRef = ref(null)
const selectionUploadRef = ref(null)
const releaseItemPagination = reactive({ page: 1, pageSize: DEFAULT_CLIENT_PAGE_SIZE })
const ticketPagination = reactive({ page: 1, pageSize: DEFAULT_CLIENT_PAGE_SIZE })
const processSheetPagination = reactive({ page: 1, pageSize: DEFAULT_CLIENT_PAGE_SIZE })
const acceptedQtyMap = reactive({})
const internalShiftDays = ref(0)
const manualInternalTargetDate = ref('')
const savingInternalRowMap = reactive({})

const loading = ref(false)
const loadingTickets = ref(false)
const loadingProcessSheets = ref(false)
const submitting = ref(false)
const deleting = ref(false)
const adjustingInternalLeadTime = ref(false)
const settingInternalTargetLeadTime = ref(false)
const importSelecting = ref(false)
let syncingReleaseItemSelection = false

const selectedOrderLabel = computed(() => (
  orderInfo.value.id
    ? `${orderInfo.value.rfqNo || orderInfo.value.id} / ${orderInfo.value.customerName || '-'}`
    : '未选择订单'
))

const today = new Date().toISOString().slice(0, 10)
const releaseForm = reactive({
  projectNo: '',
  internalProjectNo: '',
  customerOrderNo: '',
  customerProjectNo: '',
  startDate: today,
  arrangeType: '计划'
})

const pagedReleaseItems = computed(() => paginateRows(itemList.value, releaseItemPagination))
const pagedTicketList = computed(() => paginateRows(ticketList.value, ticketPagination))
const pagedProcessSheetList = computed(() => paginateRows(processSheetList.value, processSheetPagination))
const selectedReleaseItemIds = computed(() => new Set(selectedRows.value.map(row => row.id)))

const resetReleasePagination = () => {
  releaseItemPagination.page = 1
  ticketPagination.page = 1
  processSheetPagination.page = 1
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
  () => [itemList.value.length, releaseItemPagination.page, releaseItemPagination.pageSize],
  () => {
    releaseItemPagination.page = clampPage(releaseItemPagination, itemList.value.length)
    syncReleaseItemTableSelection()
  }
)

watch(
  () => [ticketList.value.length, ticketPagination.page, ticketPagination.pageSize],
  () => {
    ticketPagination.page = clampPage(ticketPagination, ticketList.value.length)
  }
)

watch(
  () => [processSheetList.value.length, processSheetPagination.page, processSheetPagination.pageSize],
  () => {
    processSheetPagination.page = clampPage(processSheetPagination, processSheetList.value.length)
  }
)

const openOrderPicker = () => {
  orderPickerVisible.value = true
}

const handleOrderSelected = async (order) => {
  selectedRfqId.value = order.id
  await loadOrder(order.id)
}

const loadOrder = async (rfqId) => {
  if (!rfqId) return
  resetReleasePagination()
  loading.value = true
  try {
    const response = await getMarketSummary(rfqId)
    orderInfo.value = response.orderInfo || {}
    await loadInternalTargetItems(rfqId)
    releaseForm.projectNo = orderInfo.value.rfqNo || ''
    releaseForm.internalProjectNo = ''
    releaseForm.customerOrderNo = ''
    releaseForm.customerProjectNo = ''
    selectedRows.value = []
    await nextTick()
    itemTableRef.value?.clearSelection()
    await loadTickets(rfqId)
    await loadProcessSheets(rfqId)
  } catch (error) {
    console.error(error)
    ElMessage.error('询价单明细加载失败。')
  } finally {
    loading.value = false
  }
}

const loadInternalTargetItems = async (rfqId) => {
  const response = await listInternalTargetLeadTimeItems(rfqId)
  setItemList(response || [])
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
  })
}

const loadTickets = async (rfqId) => {
  loadingTickets.value = true
  try {
    const response = await listTickets(rfqId)
    ticketList.value = response || []
  } catch (error) {
    console.error(error)
    ElMessage.error('提单列表加载失败。')
  } finally {
    loadingTickets.value = false
  }
}

const loadProcessSheets = async (rfqId) => {
  loadingProcessSheets.value = true
  try {
    const response = await listProcessSheets(rfqId)
    processSheetList.value = response || []
  } catch (error) {
    console.error(error)
    ElMessage.error('工艺表列表加载失败。')
  } finally {
    loadingProcessSheets.value = false
  }
}

const remainingQtyFor = (row) => {
  const qty = Number(row?.remainingQty ?? row?.qty ?? 0)
  return Number.isFinite(qty) ? Math.max(0, qty) : 0
}

const canSelectItem = (row) => {
  return row.status !== '已生成提单'
    && row.status !== '已提单'
    && row.status !== '已部分提单'
    && remainingQtyFor(row) > 0
}

const isAssemblyItem = (row) => String(row?.quoteMode || '').toUpperCase() === 'ASSEMBLY'

const handleSelectionChange = (rows) => {
  if (syncingReleaseItemSelection) return
  const currentPageIds = new Set(pagedReleaseItems.value.map(row => row.id))
  const preservedRows = selectedRows.value.filter(row => !currentPageIds.has(row.id))
  selectedRows.value = [...preservedRows, ...rows]
}

const selectedRowsInItemOrder = () => {
  const selectedIds = new Set(selectedRows.value.map(row => row.id))
  return itemList.value.filter(row => selectedIds.has(row.id))
}

const normalizePartNo = (value) => String(value || '').trim().toUpperCase()
const normalizeVersion = (value) => {
  const normalized = String(value || '').trim().toUpperCase()
  return normalized
}
const itemIdentityKey = (item) => `${normalizePartNo(item?.partNo)}||${normalizeVersion(item?.version)}`

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
  importSelecting.value = true
  try {
    const response = await parsePartSelection(formData)
    const importedItems = response?.data || []
    const importedKeys = new Set(
      importedItems
        .map(item => `${normalizePartNo(item.normalizedPartNo || item.partNo)}||${normalizeVersion(item.normalizedVersion ?? item.version)}`)
        .filter(key => !key.startsWith('||'))
    )

    let matchedCount = 0
    let selectedCount = 0
    let skippedCount = 0
    const importedSelection = []
    itemList.value.forEach(row => {
      if (!importedKeys.has(itemIdentityKey(row))) {
        return
      }
      matchedCount++
      if (canSelectItem(row)) {
        importedSelection.push(row)
        selectedCount++
      } else {
        skippedCount++
      }
    })
    selectedRows.value = importedSelection
    await syncReleaseItemTableSelection()

    if (importedKeys.size === 0) {
      ElMessage.warning('表格中未读取到有效的图号+版本。')
    } else if (selectedCount === 0) {
      ElMessage.warning(`已读取 ${importedKeys.size} 个图号+版本，但没有匹配到当前订单中可选的零件。`)
    } else {
      const skippedText = skippedCount > 0 ? `，${skippedCount} 项因已提单未勾选` : ''
      ElMessage.success(`已读取 ${importedKeys.size} 个图号+版本，匹配 ${matchedCount} 项，已勾选 ${selectedCount} 项${skippedText}。`)
    }
  } catch (error) {
    console.error(error)
    ElMessage.error(error.response?.data?.message || '导入表格选定失败。')
  } finally {
    importSelecting.value = false
  }
}

const adjustInternalLeadTime = async (direction) => {
  if (!selectedRfqId.value) return ElMessage.warning('请先选择询价单。')
  if (selectedRows.value.length === 0) return ElMessage.warning('请至少选择一个零件。')
  const days = Math.abs(Number(internalShiftDays.value || 0)) * direction
  if (!days) return ElMessage.warning('调整天数为 0，不需要调整。')

  adjustingInternalLeadTime.value = true
  try {
    const response = await shiftInternalTargetLeadTime({
      rfqId: selectedRfqId.value,
      itemIds: selectedRows.value.map(row => row.id),
      days
    })
    setItemList(response.itemList || [])
    selectedRows.value = []
    await syncReleaseItemTableSelection()
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
  if (!selectedRfqId.value) return ElMessage.warning('请先选择询价单。')
  if (selectedRows.value.length === 0) return ElMessage.warning('请至少选择一个零件。')
  if (!manualInternalTargetDate.value) return ElMessage.warning('请先选择内部目标交期。')

  settingInternalTargetLeadTime.value = true
  try {
    const response = await setInternalTargetLeadTimeRequest({
      rfqId: selectedRfqId.value,
      itemIds: selectedRows.value.map(row => row.id),
      internalTargetLeadTime: manualInternalTargetDate.value
    })
    setItemList(response.itemList || [])
    selectedRows.value = []
    await syncReleaseItemTableSelection()
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
  if (!selectedRfqId.value || !row?.id) return
  savingInternalRowMap[row.id] = true
  try {
    const response = await updateInternalTargetLeadTime({
      rfqId: selectedRfqId.value,
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
    ElMessage.success('内部目标交期已保存。')
  } catch (error) {
    console.error(error)
    ElMessage.error(error.response?.data?.message || '内部目标交期保存失败。')
  } finally {
    savingInternalRowMap[row.id] = false
  }
}

const submitTicket = async () => {
  if (!selectedRfqId.value) return ElMessage.warning('请先选择询价单。')
  if (selectedRows.value.length === 0) return ElMessage.warning('请至少选择一个通过零件。')
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

  await ElMessageBox.confirm(
    `确认生成 ${orderedSelectedRows.length} 个零件的正式提单？通过数量不能超过剩余可提数量。`,
    '生成提单确认',
    { type: 'warning', confirmButtonText: '确认生成', cancelButtonText: '取消' }
  )

  submitting.value = true
  try {
    const payload = {
      rfqId: selectedRfqId.value,
      projectNo: releaseForm.projectNo,
      internalProjectNo,
      customerOrderNo,
      customerProjectNo,
      startDate: releaseForm.startDate,
      arrangeType: releaseForm.arrangeType,
      items: orderedSelectedRows.map(row => ({
        itemId: row.id,
        acceptedQty: acceptedQtyMap[row.id] || remainingQtyFor(row)
      }))
    }
    const response = await generateTicket(payload)
    if (response.success) {
      ElMessage.success(response.message)
      await loadOrder(selectedRfqId.value)
    } else {
      ElMessage.error(response.message || '提单生成失败。')
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

const deleteTicket = async (row) => {
  await ElMessageBox.confirm(
    `确认删除提单行「${row.partNo} / ${row.version || '-'}」数量 ${row.qty}？`,
    '删除提单行',
    { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
  )

  deleting.value = true
  try {
    const response = await deleteTicketRequest(row.id)
    ElMessage.success(response.message || '提单行已删除')
    await loadOrder(selectedRfqId.value)
  } catch (error) {
    console.error(error)
    ElMessage.error('提单行删除失败。')
  } finally {
    deleting.value = false
  }
}

const deleteProcessSheet = async (row) => {
  await ElMessageBox.confirm(
    `确认删除工艺表行「${row.partNo} / ${row.version || '-'}」？删除后可基于新的核价重新生成。`,
    '删除工艺表行',
    { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
  )

  deleting.value = true
  try {
    const response = await deleteProcessSheetRequest(row.id)
    ElMessage.success(response.message || '工艺表行已删除')
    await loadOrder(selectedRfqId.value)
  } catch (error) {
    console.error(error)
    ElMessage.error('工艺表行删除失败。')
  } finally {
    deleting.value = false
  }
}

const formatRoute = (route) => {
  if (!route) return '-'
  let list = Array.isArray(route) ? route : []
  if (typeof route === 'string') {
    try {
      list = JSON.parse(route)
    } catch {
      list = []
    }
  }
  if (list.length === 0) return '-'
  return list
    .map(item => `${item.processName || ''}${item.inputVal ? `(${item.inputVal})` : ''}`)
    .filter(Boolean)
    .join(' / ')
}

const formatWeight = (value) => {
  const number = Number(value)
  if (!Number.isFinite(number)) return '-'
  return number.toFixed(3).replace(/\.?0+$/, '')
}

const displayTicketText = (value) => {
  return String(value || '').replaceAll('待产', '在产')
}

const exportExcel = async (type) => {
  if (!selectedRfqId.value) return ElMessage.warning('请先选择询价单。')
  const pathMap = {
    tickets: 'tickets',
    process: 'process-sheets'
  }
  const path = pathMap[type] || 'process-sheets'
  const result = await downloadMarketExport(path, selectedRfqId.value, `${path}-${selectedRfqId.value}.xlsx`)
  if (!result.success) {
    ElMessage.error(result.message || 'Excel 导出失败。')
  }
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
