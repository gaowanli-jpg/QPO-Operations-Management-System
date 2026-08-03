<template>
  <div class="erp-page release-page workflow-page">
    <header class="erp-page-header release-page-header">
          <div>
            <h2>市场部：议价确认与提单生成</h2>
            <p>选择客户最终通过的零件，生成正式提单和对应工艺表。</p>
          </div>
          <div class="erp-page-actions release-page-actions"><el-button v-permission="'market:release:order-select'" :icon="DocumentChecked" type="primary" :loading="loading" @click="openOrderPicker">选择订单</el-button></div>

    </header>

    <section class="erp-surface workflow-workspace release-workspace">
      <el-row :gutter="16">
        <el-col :span="6">
          <div class="erp-surface workflow-side-panel release-panel">
            <el-table
              class="selected-orders-mini-table"
              :data="selectedOrders"
              height="132"
              size="small"
              border
              stripe
              empty-text="请在右上角选择订单"
            >
              <el-table-column prop="customerName" label="客户" min-width="86" show-overflow-tooltip />
              <el-table-column prop="sourceOrderNo" label="来源单号" min-width="122" show-overflow-tooltip />
              <el-table-column prop="status" label="订单状态" width="94" align="center">
                <template #default="{ row }">
                  <el-tag :type="statusTagType(row.status)" size="small">{{ row.status || '-' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="58" align="center" fixed="right">
                <template #default="{ row }">
                  <el-button link type="danger" size="small" :disabled="submitting" @click.stop="removeSelectedOrder(row)">移除</el-button>
                </template>
              </el-table-column>
            </el-table>

            <el-form label-width="80px" class="ticket-form">
              <el-form-item label="订单类型" required>
                <el-select v-model="releaseForm.orderType" class="full-width release-order-type-select" placeholder="请选择订单类型" clearable>
                  <el-option label="预测订单" value="预测订单" />
                  <el-option label="备货订单" value="备货订单" />
                  <el-option label="正式订单" value="正式订单" />
                </el-select>
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
                  :disabled="selectedRows.length === 0 || adjustingInternalLeadTime || submitting"
                  :loading="adjustingInternalLeadTime"
                  @click="adjustInternalLeadTime(1)"
                >
                  增加
                </el-button>
                <el-button
                  plain
                  class="workflow-soft-button"
                  :disabled="selectedRows.length === 0 || adjustingInternalLeadTime || submitting"
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
                  :disabled="selectedRows.length === 0 || !manualInternalTargetDate || settingInternalTargetLeadTime || submitting"
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
                :disabled="selectedRows.length === 0 || !releaseForm.orderType || !releaseForm.internalProjectNo.trim() || !releaseForm.customerOrderNo.trim() || submitting"
                @click="submitTicket"
              >
                生成正式提单
              </el-button>
            </div>
          </div>
        </el-col>

        <el-col :span="18">
          <div class="erp-table-surface workflow-main-panel release-table-panel">
            <div class="erp-table-header panel-title-row">
              <div class="panel-title">通过零件选择</div>
              <div class="release-table-actions import-action-row">
                <el-button
                  v-permission="'market:release:create'"
                  size="small"
                  plain
                  class="workflow-soft-button"
                  :disabled="selectedOrders.length === 0 || replacingDrawings || submitting"
                  :loading="replacingDrawings"
                  @click="openDrawingReplaceDialog"
                >
                  导入替换图纸
                </el-button>
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
                      :disabled="selectedOrders.length === 0 || releaseItemTotal === 0 || importSelecting || submitting"
                      :loading="importSelecting"
                    >
                      导入表格选定
                    </el-button>
                  </template>
                </el-upload>
              </div>
            </div>
            <el-table
              ref="itemTableRef"
              class="release-selection-table"
              :data="pagedReleaseItems"
              :row-key="rowKey"
              border
              stripe
              height="640"
              scrollbar-always-on
              v-loading="loading"
              @selection-change="handleSelectionChange"
            >
              <el-table-column type="selection" width="45" :selectable="canSelectItem" :reserve-selection="true" />
              <el-table-column prop="sourceType" label="来源" width="90" fixed show-overflow-tooltip />
              <el-table-column prop="sourceOrderNo" label="来源单号" width="150" fixed show-overflow-tooltip />
              <el-table-column prop="customerName" label="客户" width="130" show-overflow-tooltip />
              <el-table-column prop="orderStatus" label="订单状态" width="110" align="center">
                <template #default="{ row }">
                  <el-tag :type="statusTagType(row.orderStatus)" size="small">{{ row.orderStatus || '-' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="partNo" label="图号" width="130" fixed show-overflow-tooltip />
              <el-table-column prop="version" label="版本" width="150" align="center">
                <template #default="{ row }">
                  <div class="row-version-editor">
                    <el-input
                      v-model="row.versionDraft"
                      size="small"
                      maxlength="20"
                      clearable
                      :disabled="!canSelectItem(row)"
                      class="row-version-input"
                    />
                    <el-button
                      size="small"
                      type="primary"
                      plain
                      :disabled="!canSelectItem(row) || isVersionSaving(row) || submitting"
                      :loading="isVersionSaving(row)"
                      @click.stop="saveReleaseItemVersion(row)"
                    >
                      保存
                    </el-button>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="图纸" width="80" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.hasDrawing || row.drawingUrl ? 'success' : 'warning'" size="small">
                    {{ row.hasDrawing || row.drawingUrl ? '已绑定' : '缺图' }}
                  </el-tag>
                </template>
              </el-table-column>
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
                      :disabled="!canSelectItem(row) || isInternalLeadTimeSaving(row) || submitting"
                      :loading="isInternalLeadTimeSaving(row)"
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
                    v-model="acceptedQtyMap[rowKey(scope.row)]"
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

    <el-dialog
      v-model="drawingReplaceVisible"
      title="导入替换图纸"
      width="720px"
      :close-on-click-modal="false"
    >
      <div class="drawing-replace-dialog">
        <el-alert
          :title="`将按图号匹配当前已选 ${selectedOrders.length} 个订单，匹配成功会覆盖旧图纸。`"
          description="系统会在当前选中的订单范围内按图号匹配全部零件，命中后替换图纸；无需勾选零件。"
          type="warning"
          :closable="false"
          show-icon
        />

        <div class="erp-upload-surface drawing-replace-upload">
          <div
            v-permission="'market:release:create'"
            class="custom-dropzone drawing-replace-dropzone supplement-dropzone"
            @dragover.prevent
            @drop.prevent="handleReplacementDrop"
          >
            <el-icon class="el-icon--upload dropzone-icon"><upload-filled /></el-icon>
            <div class="el-upload__text dropzone-text">
              将 <em>图纸文件夹</em> 或多个 PDF 图纸直接拖拽至此处
            </div>
            <div class="el-upload__tip">支持 图号.pdf、图号 空格说明.pdf、图号-说明.pdf，例如 L20100000733.pdf 或 L20100000733-定位块.pdf。</div>
            <div class="folder-select-row">或点击下方按钮选择多个 PDF / 图纸文件夹</div>
            <div class="drawing-replace-upload-actions import-action-row">
              <el-button v-permission="'market:release:create'" plain class="workflow-soft-button folder-select-button" @click="triggerReplacementFileSelect">选择多个 PDF</el-button>
              <el-button v-permission="'market:release:create'" plain class="workflow-soft-button folder-select-button" @click="triggerReplacementFolderSelect">选择文件夹</el-button>
            </div>
            <input type="file" ref="replacementFileInput" accept=".pdf" multiple class="hidden-input" @change="handleReplacementFileSelect" />
            <input type="file" ref="replacementFolderInput" webkitdirectory directory multiple class="hidden-input" @change="handleReplacementFolderSelect" />
          </div>
        </div>

        <div v-if="replacementDrawingFiles.length > 0" class="replacement-file-stats">
          <h4>已选择待替换图纸（{{ replacementDrawingFiles.length }} 份）</h4>
          <ul class="replacement-pdf-list">
            <li v-for="(file, index) in replacementDrawingFiles" :key="`${file.name}-${file.size}-${index}`">
              <span>{{ file.name }}</span>
              <el-button v-permission="'market:release:create'" link type="danger" size="small" @click="removeReplacementDrawingFile(index)">移除</el-button>
            </li>
          </ul>
        </div>

        <div v-if="clientSkippedReplacementFiles.length > 0 && !replacementDrawingResult" class="replacement-result">
          <el-alert
            :title="`已跳过 ${clientSkippedReplacementFiles.length} 个非 PDF 文件。`"
            type="warning"
            :closable="false"
            show-icon
          />
        </div>

        <div v-if="replacementDrawingResult" class="replacement-result">
          <el-alert
            :title="replacementDrawingResult.message || '图纸替换处理完成。'"
            :type="replacementResultHasWarnings ? 'warning' : 'success'"
            :closable="false"
            show-icon
          />
          <div class="replacement-summary-grid">
            <div><strong>{{ replacementDrawingResult.uploadCount || 0 }}</strong><span>上传PDF</span></div>
            <div><strong>{{ replacementDrawingResult.matchedCount || 0 }}</strong><span>匹配零件</span></div>
            <div><strong>{{ replacementDrawingResult.replacedCount || 0 }}</strong><span>已替换</span></div>
            <div><strong>{{ replacementDrawingResult.unmatchedCount || 0 }}</strong><span>未匹配</span></div>
            <div><strong>{{ replacementSkippedFiles.length }}</strong><span>非PDF</span></div>
          </div>
          <el-tabs>
            <el-tab-pane :label="`已替换 ${replacementReplacedItems.length}`">
              <ul v-if="replacementReplacedItems.length" class="result-list replacement-result-list">
                <li v-for="(item, index) in replacementReplacedItems" :key="index">
                  <span class="result-main">{{ item.partNo || '-' }} / {{ item.version || '-' }}</span>
                  <span v-if="item.rfqNo" class="result-sub"> - {{ item.rfqNo }}</span>
                  <span v-if="item.fileName" class="result-sub"> - {{ item.fileName }}</span>
                </li>
              </ul>
              <el-empty v-else description="无记录" :image-size="60" />
            </el-tab-pane>
            <el-tab-pane :label="`未匹配 ${replacementUnmatchedDrawings.length}`">
              <ul v-if="replacementUnmatchedDrawings.length" class="result-list replacement-result-list">
                <li v-for="(item, index) in replacementUnmatchedDrawings" :key="index">
                  <span class="result-main">{{ item.fileName || '-' }}</span>
                  <span v-if="item.reason" class="result-reason"> - {{ item.reason }}</span>
                </li>
              </ul>
              <el-empty v-else description="无记录" :image-size="60" />
            </el-tab-pane>
            <el-tab-pane :label="`非PDF ${replacementSkippedFiles.length}`">
              <ul v-if="replacementSkippedFiles.length" class="result-list replacement-result-list">
                <li v-for="(item, index) in replacementSkippedFiles" :key="index">
                  <span class="result-main">{{ item.fileName || '-' }}</span>
                  <span v-if="item.reason" class="result-reason"> - {{ item.reason }}</span>
                </li>
              </ul>
              <el-empty v-else description="无记录" :image-size="60" />
            </el-tab-pane>
          </el-tabs>
        </div>
      </div>

      <template #footer>
        <div class="erp-dialog-footer">
          <el-button @click="drawingReplaceVisible = false">关闭</el-button>
          <el-button
            v-permission="'market:release:create'"
            type="primary"
            :loading="replacingDrawings"
            :disabled="replacementDrawingFiles.length === 0 || replacingDrawings || submitting"
            @click="submitReplacementDrawings"
          >
            上传并替换
          </el-button>
        </div>
      </template>
    </el-dialog>

    <OrderPickerDialog
      v-model="orderPickerVisible"
      title="选择市场部议价订单"
      customer-scope="release"
      order-scope="release"
      :allowed-statuses="RELEASE_ALLOWED_STATUSES"
      :multiple="true"
      :locked-customer-name="selectedCustomerName"
      :selected-order-ids="selectedOrderKeys"
      @select="handleOrderSelected"
    />
  </div>
</template>

<script setup>
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { DocumentChecked, UploadFilled } from '@element-plus/icons-vue'
import OrderPickerDialog from './OrderPickerDialog.vue'
import { collectPdfFilesFromDataTransfer, isPdfFile, mergePdfFiles } from '../utils/drawingUpload'
import {
  generateTicket,
  getReleaseSummary,
  listReleaseItemsBatch,
  parsePartSelectionBatch,
  replaceReleaseDrawings,
  setInternalTargetLeadTime as setInternalTargetLeadTimeRequest,
  shiftInternalTargetLeadTime,
  updateInternalTargetLeadTime,
  updateReleaseItemVersion
} from '../services/marketApi'
import {
  CLIENT_PAGE_SIZES,
  DEFAULT_CLIENT_PAGE_SIZE
} from '../utils/clientPagination'
import { usePendingAction } from '../composables/usePendingAction'

const selectedRfqId = ref(null)
const selectedOrders = ref([])
const orderPickerVisible = ref(false)
const itemList = ref([])
const releaseItemTotal = ref(0)
const selectedItemIdMap = reactive({})
const itemTableRef = ref(null)
const selectionUploadRef = ref(null)
const replacementFileInput = ref(null)
const replacementFolderInput = ref(null)
const releaseItemPagination = reactive({ page: 1, pageSize: DEFAULT_CLIENT_PAGE_SIZE })
const acceptedQtyMap = reactive({})
const internalShiftDays = ref(0)
const manualInternalTargetDate = ref('')
const drawingReplaceVisible = ref(false)
const replacementDrawingFiles = ref([])
const replacementDrawingResult = ref(null)
const clientSkippedReplacementFiles = ref([])

const loading = ref(false)
let syncingReleaseItemSelection = false

const RELEASE_ALLOWED_STATUSES = ['已报价', '部分已提单', '已生成提单']

const toReleaseOrderSource = (order) => ({
  sourceType: order?.sourceType || 'RFQ',
  sourceOrderId: Number(order?.sourceOrderId ?? order?.id)
})
const sourceKey = (source) => `${source?.sourceType || 'RFQ'}:${source?.sourceOrderId ?? source?.id ?? ''}`
const rowKey = (row) => row?.releaseItemKey || `${row?.sourceType || 'RFQ'}:${row?.sourceItemId ?? row?.id ?? ''}`
const isSalesOrderSource = (source) => source?.sourceType === 'SALES_ORDER'
const selectedOrderSources = computed(() => selectedOrders.value.map(toReleaseOrderSource))
const selectedOrderKeys = computed(() => selectedOrderSources.value.map(sourceKey))
const selectedRfqIds = computed(() => selectedOrderSources.value
  .filter(source => source.sourceType === 'RFQ')
  .map(source => source.sourceOrderId))
const selectedCustomerName = computed(() => selectedOrders.value[0]?.customerName || '')
const { isPending, runAction } = usePendingAction()
const releaseActionKeys = {
  importSelection: 'market:release:import-selection',
  replaceDrawings: 'market:release:replace-drawings',
  shiftInternalLeadTime: 'market:release:shift-internal-lead-time',
  setInternalTargetLeadTime: 'market:release:set-internal-target-lead-time',
  version: (itemId) => `market:release:version:${itemId}`,
  internalLeadTime: (itemId) => `market:release:internal-lead-time:${itemId}`,
  generateTicket: () => `market:release:generate-ticket:${selectedOrderKeys.value.join(',') || 'none'}`
}
const submitting = computed(() => isPending(releaseActionKeys.generateTicket()))
const adjustingInternalLeadTime = computed(() => isPending(releaseActionKeys.shiftInternalLeadTime))
const settingInternalTargetLeadTime = computed(() => isPending(releaseActionKeys.setInternalTargetLeadTime))
const importSelecting = computed(() => isPending(releaseActionKeys.importSelection))
const replacingDrawings = computed(() => isPending(releaseActionKeys.replaceDrawings))
const isVersionSaving = (row) => isPending(releaseActionKeys.version(row?.id))
const isInternalLeadTimeSaving = (row) => isPending(releaseActionKeys.internalLeadTime(row?.id))

const today = new Date().toISOString().slice(0, 10)
const releaseForm = reactive({
  orderType: '',
  internalProjectNo: '',
  customerOrderNo: '',
  customerProjectNo: '',
  startDate: today,
  arrangeType: '计划'
})

const pagedReleaseItems = computed(() => itemList.value)
const selectedRows = computed(() => Object.values(selectedItemIdMap))
const selectedReleaseItemIds = computed(() => new Set(selectedRows.value.map(row => rowKey(row))))
const replacementReplacedItems = computed(() => replacementDrawingResult.value?.replacedItems || [])
const replacementUnmatchedDrawings = computed(() => replacementDrawingResult.value?.unmatchedDrawings || [])
const replacementSkippedFiles = computed(() => replacementDrawingResult.value?.skippedFiles || [])
const replacementResultHasWarnings = computed(() => (
  Number(replacementDrawingResult.value?.unmatchedCount || 0) > 0
  || replacementSkippedFiles.value.length > 0
))

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
    if (selectedReleaseItemIds.value.has(rowKey(row))) {
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

const firstSelectedRfqId = () => selectedOrderSources.value
  .find(source => source.sourceType === 'RFQ')?.sourceOrderId || null

const applyReleaseDefaultsFromOrder = (order) => {
  const wasEmpty = selectedOrders.value.length === 0
  if (!wasEmpty || !isSalesOrderSource(order)) return
  releaseForm.orderType = order.orderType || releaseForm.orderType
  releaseForm.internalProjectNo = order.internalProjectNo || releaseForm.internalProjectNo
  releaseForm.customerOrderNo = order.customerOrderNo || releaseForm.customerOrderNo
  releaseForm.customerProjectNo = order.customerProjectNo || releaseForm.customerProjectNo
}

const handleOrderSelected = async (orderOrOrders) => {
  const incomingOrders = Array.isArray(orderOrOrders) ? orderOrOrders : [orderOrOrders]
  const existingKeys = new Set(selectedOrderKeys.value)
  const nextOrders = incomingOrders
    .filter(order => order?.id && !existingKeys.has(sourceKey(toReleaseOrderSource(order))))
  if (nextOrders.length === 0) return

  const wasEmpty = selectedOrders.value.length === 0
  if (wasEmpty) {
    resetReleaseFormForOrders()
    clearSelectedRows()
  }
  applyReleaseDefaultsFromOrder(nextOrders[0])
  selectedOrders.value = [...selectedOrders.value, ...nextOrders]
  selectedRfqId.value = firstSelectedRfqId()
  resetReleasePagination()
  loading.value = true
  try {
    await fetchReleaseItemsBatch()
  } finally {
    loading.value = false
  }
}

const removeSelectedOrder = async (order) => {
  const orderKey = sourceKey(toReleaseOrderSource(order))
  if (!orderKey) return

  const relatedRows = [
    ...itemList.value,
    ...Object.values(selectedItemIdMap)
  ].filter(row => sourceKey({
    sourceType: row?.sourceType || 'RFQ',
    sourceOrderId: row?.sourceOrderId ?? row?.rfqId ?? selectedRfqId.value
  }) === orderKey)

  relatedRows.forEach(row => {
    delete selectedItemIdMap[rowKey(row)]
    delete acceptedQtyMap[rowKey(row)]
  })

  selectedOrders.value = selectedOrders.value.filter(item => sourceKey(toReleaseOrderSource(item)) !== orderKey)
  selectedRfqId.value = firstSelectedRfqId()
  resetReleasePagination()

  if (selectedOrders.value.length === 0) {
    setItemList([])
    releaseItemTotal.value = 0
    await nextTick()
    itemTableRef.value?.clearSelection()
    return
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
    selectedRfqId.value = firstSelectedRfqId()
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
  if (selectedOrderSources.value.length === 0) {
    setItemList([])
    releaseItemTotal.value = 0
    return
  }
  const response = await listReleaseItemsBatch({
    sources: selectedOrderSources.value,
    rfqIds: selectedRfqIds.value,
    page: releaseItemPagination.page,
    pageSize: releaseItemPagination.pageSize
  })
  setItemList(response.records || [])
  releaseItemTotal.value = Number(response.total ?? 0)
  await syncReleaseItemTableSelection()
}

const resetReleaseFormForOrders = () => {
  releaseForm.orderType = ''
  releaseForm.internalProjectNo = ''
  releaseForm.customerOrderNo = ''
  releaseForm.customerProjectNo = ''
}

const setItemList = (rows) => {
  itemList.value = (rows || []).map(row => ({
    ...row,
    internalTargetLeadTimeDraft: row.internalTargetLeadTime || '',
    versionDraft: row.version || ''
  }))
  itemList.value.forEach(item => {
    const key = rowKey(item)
    const remainingQty = remainingQtyFor(item)
    if (!acceptedQtyMap[key] || acceptedQtyMap[key] > remainingQty) {
      acceptedQtyMap[key] = remainingQty > 0 ? remainingQty : 0
    }
    if (selectedItemIdMap[key]) {
      selectedItemIdMap[key] = { ...selectedItemIdMap[key], ...item }
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

const saveReleaseItemVersion = async (row) => {
  const rowRfqId = row?.rfqId || selectedRfqId.value
  if (!rowRfqId || !row?.id) return
  row.versionDraft = String(row.versionDraft ?? '').trim()
  return runAction(releaseActionKeys.version(row.id), async () => {
    try {
      const response = await updateReleaseItemVersion(row.id, {
        rfqId: rowRfqId,
        version: row.versionDraft
      })
      const savedData = response?.data || {}
      const saved = {
        ...row,
        ...savedData,
        versionDraft: savedData.version ?? row.versionDraft ?? ''
      }
      const index = itemList.value.findIndex(item => item.id === row.id)
      if (index >= 0) {
        itemList.value.splice(index, 1, saved)
      }
      if (selectedItemIdMap[rowKey(row)]) {
        selectedItemIdMap[rowKey(row)] = { ...selectedItemIdMap[rowKey(row)], ...saved }
      }
      ElMessage.success('版本已保存。')
    } catch (error) {
      console.error(error)
      ElMessage.error(error.response?.data?.message || '版本保存失败。')
    }
  })
}

const handleSelectionChange = (rows) => {
  if (syncingReleaseItemSelection) return
  const currentPageIds = new Set(itemList.value.map(row => rowKey(row)))
  currentPageIds.forEach(id => delete selectedItemIdMap[id])
  rows.forEach(row => {
    if (canSelectItem(row)) {
      selectedItemIdMap[rowKey(row)] = row
    }
  })
}

const selectedRowsInItemOrder = () => {
  return selectedRows.value.slice().sort((left, right) => {
    const leftSource = `${left?.sourceType || 'RFQ'}:${Number(left?.sourceOrderId ?? left?.rfqId ?? 0)}`
    const rightSource = `${right?.sourceType || 'RFQ'}:${Number(right?.sourceOrderId ?? right?.rfqId ?? 0)}`
    const sourceCompare = leftSource.localeCompare(rightSource)
    return sourceCompare !== 0 ? sourceCompare : Number(left.id || 0) - Number(right.id || 0)
  })
}

const selectedRowsRfqIds = () => {
  return [...new Set(selectedRows.value
    .filter(row => (row?.sourceType || 'RFQ') === 'RFQ')
    .map(row => row.rfqId || row.sourceOrderId || selectedRfqId.value)
    .filter(Boolean))]
}

const groupSelectedRowsBySource = (rows) => {
  const groups = new Map()
  ;(rows || []).forEach(row => {
    const sourceType = row?.sourceType || 'RFQ'
    const sourceOrderId = Number(row?.sourceOrderId ?? row?.rfqId ?? selectedRfqId.value)
    if (!sourceOrderId) return
    const source = { sourceType, sourceOrderId }
    const key = sourceKey(source)
    if (!groups.has(key)) {
      groups.set(key, { source, rows: [] })
    }
    groups.get(key).rows.push(row)
  })
  return groups
}

const orderSourceNo = (order, source) => {
  const sourceType = source?.sourceType || order?.sourceType || 'RFQ'
  if (sourceType === 'SALES_ORDER') {
    return order?.sourceOrderNo || order?.salesOrderNo || source?.sourceOrderId || sourceKey(source)
  }
  return order?.sourceOrderNo || order?.rfqNo || source?.sourceOrderId || sourceKey(source)
}

const orderLabelBySource = (source) => {
  const key = sourceKey(source)
  const order = selectedOrders.value.find(item => sourceKey(toReleaseOrderSource(item)) === key)
  return orderSourceNo(order, source)
}

const formatGenerationFailures = (failedResults) => {
  return failedResults
    .map(result => `${orderLabelBySource(result.source)}：${result.message}`)
    .join('；')
}

const refreshSelectedOrdersAfterGeneration = async (successItemIds = new Set()) => {
  successItemIds.forEach(id => delete selectedItemIdMap[id])
  const refreshedOrders = await Promise.all(selectedRfqIds.value.map(async (rfqId) => {
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
  selectedRfqId.value = firstSelectedRfqId()
  await fetchReleaseItemsBatch()
}

const requireSingleSelectedRowsRfqId = (actionText) => {
  if (!selectedOrders.value.length) {
    ElMessage.warning('请先选择订单。')
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
  if (selectedOrderSources.value.length === 0) return ElMessage.warning('请先选择订单。')
  if (!file) return

  const fileName = String(file.name || '').toLowerCase()
  if (!fileName.endsWith('.xlsx') && !fileName.endsWith('.xls')) {
    return ElMessage.warning('请上传 xlsx 或 xls 表格。')
  }

  const formData = new FormData()
  formData.append('excelFile', file)
  formData.append('rfqIds', JSON.stringify(selectedRfqIds.value))
  formData.append('sources', JSON.stringify(selectedOrderSources.value))
  return runAction(releaseActionKeys.importSelection, async () => {
    try {
      const response = await parsePartSelectionBatch(formData)
      clearSelectedRows()
      ;(response?.data || []).forEach(row => {
        selectedItemIdMap[rowKey(row)] = row
        const remainingQty = remainingQtyFor(row)
        acceptedQtyMap[rowKey(row)] = remainingQty > 0 ? remainingQty : 0
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
    }
  })
}

const openDrawingReplaceDialog = () => {
  if (selectedOrders.value.length === 0) {
    ElMessage.warning('请先选择订单。')
    return
  }
  replacementDrawingFiles.value = []
  replacementDrawingResult.value = null
  clientSkippedReplacementFiles.value = []
  drawingReplaceVisible.value = true
}

const addClientSkippedReplacementFiles = (files = []) => {
  const skipped = Array.from(files || [])
    .filter(file => !isPdfFile(file))
    .map(file => ({
      fileName: file.name || '-',
      reason: '仅支持 PDF 图纸'
    }))
  if (skipped.length === 0) return

  const seen = new Set(clientSkippedReplacementFiles.value.map(item => item.fileName))
  clientSkippedReplacementFiles.value = [
    ...clientSkippedReplacementFiles.value,
    ...skipped.filter(item => {
      if (seen.has(item.fileName)) return false
      seen.add(item.fileName)
      return true
    })
  ]
  ElMessage.warning(`已跳过 ${skipped.length} 个非 PDF 文件。`)
}

const addReplacementDrawingFiles = (files, sourceLabel) => {
  const incomingFiles = Array.from(files || [])
  addClientSkippedReplacementFiles(incomingFiles)
  const beforeCount = replacementDrawingFiles.value.length
  replacementDrawingFiles.value = mergePdfFiles(replacementDrawingFiles.value, incomingFiles)
  const addedCount = replacementDrawingFiles.value.length - beforeCount
  replacementDrawingResult.value = null
  if (addedCount > 0) {
    ElMessage.success(`成功${sourceLabel} ${addedCount} 份 PDF。`)
  } else if (incomingFiles.length > 0) {
    ElMessage.warning('未找到新的 PDF 图纸。')
  }
}

const handleReplacementDrop = async (event) => {
  addClientSkippedReplacementFiles(event.dataTransfer?.files)
  const pdfs = await collectPdfFilesFromDataTransfer(event.dataTransfer)
  addReplacementDrawingFiles(pdfs, '提取')
}

const triggerReplacementFileSelect = () => {
  replacementFileInput.value?.click()
}

const triggerReplacementFolderSelect = () => {
  replacementFolderInput.value?.click()
}

const handleReplacementFileSelect = (event) => {
  addReplacementDrawingFiles(event.target.files, '选择')
  event.target.value = ''
}

const handleReplacementFolderSelect = (event) => {
  addReplacementDrawingFiles(event.target.files, '从选中的文件夹中提取')
  event.target.value = ''
}

const removeReplacementDrawingFile = (index) => {
  replacementDrawingFiles.value.splice(index, 1)
}

const submitReplacementDrawings = async () => {
  if (selectedOrderSources.value.length === 0) return ElMessage.warning('请先选择订单。')
  if (replacementDrawingFiles.value.length === 0) return ElMessage.warning('请先选择需要替换的 PDF 图纸。')

  const formData = new FormData()
  formData.append('rfqIds', JSON.stringify(selectedRfqIds.value))
  formData.append('sources', JSON.stringify(selectedOrderSources.value))
  replacementDrawingFiles.value.forEach(file => formData.append('drawings', file))

  return runAction(releaseActionKeys.replaceDrawings, async () => {
    try {
      const response = await replaceReleaseDrawings(formData)
      const skippedFiles = [
        ...(response?.skippedFiles || []),
        ...clientSkippedReplacementFiles.value
      ]
      replacementDrawingResult.value = {
        ...(response || {}),
        skippedFiles,
        skippedCount: skippedFiles.length
      }
      replacementDrawingFiles.value = []
      clientSkippedReplacementFiles.value = []
      await fetchReleaseItemsBatch()

      if (Number(replacementDrawingResult.value.unmatchedCount || 0) > 0 || skippedFiles.length > 0) {
        ElMessage.warning(response?.message || '图纸替换处理完成，存在未匹配或跳过文件。')
      } else {
        ElMessage.success(response?.message || '图纸替换处理完成。')
      }
    } catch (error) {
      console.error(error)
      ElMessage.error(error.response?.data?.message || '图纸替换失败。')
    }
  })
}

const adjustInternalLeadTime = async (direction) => {
  if (selectedRows.value.length === 0) return ElMessage.warning('请至少选择一个零件。')
  const rfqId = requireSingleSelectedRowsRfqId('内部目标交期调整')
  if (!rfqId) return
  const days = Math.abs(Number(internalShiftDays.value || 0)) * direction
  if (!days) return ElMessage.warning('调整天数为 0，不需要调整。')

  return runAction(releaseActionKeys.shiftInternalLeadTime, async () => {
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
    }
  })
}

const setInternalTargetLeadTime = async () => {
  if (selectedRows.value.length === 0) return ElMessage.warning('请至少选择一个零件。')
  const rfqId = requireSingleSelectedRowsRfqId('指定内部目标交期')
  if (!rfqId) return
  if (!manualInternalTargetDate.value) return ElMessage.warning('请先选择内部目标交期。')

  return runAction(releaseActionKeys.setInternalTargetLeadTime, async () => {
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
    }
  })
}

const saveInternalTargetLeadTime = async (row) => {
  const rowRfqId = row?.rfqId || selectedRfqId.value
  if (!rowRfqId || !row?.id) return
  return runAction(releaseActionKeys.internalLeadTime(row.id), async () => {
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
      if (selectedItemIdMap[rowKey(row)]) {
        selectedItemIdMap[rowKey(row)] = { ...selectedItemIdMap[rowKey(row)], ...saved }
      }
      ElMessage.success('内部目标交期已保存。')
    } catch (error) {
      console.error(error)
      ElMessage.error(error.response?.data?.message || '内部目标交期保存失败。')
    }
  })
}

const submitTicket = async () => {
  if (selectedRows.value.length === 0) return ElMessage.warning('请至少选择一个通过零件。')
  if (!selectedOrders.value.length) return ElMessage.warning('请先选择订单。')
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

  const overRemaining = orderedSelectedRows.filter(row => Number(acceptedQtyMap[rowKey(row)] || 0) > remainingQtyFor(row))
  if (overRemaining.length > 0) {
    return ElMessage.warning(`有 ${overRemaining.length} 个零件的通过数量超过剩余可提数量。`)
  }

  const releaseGroups = groupSelectedRowsBySource(orderedSelectedRows)
  if (releaseGroups.size === 0) {
    return ElMessage.warning('已选零件缺少订单信息，无法生成正式提单。')
  }

  return runAction(releaseActionKeys.generateTicket(), async () => {
    await ElMessageBox.confirm(
      `确认按 ${releaseGroups.size} 个订单生成 ${orderedSelectedRows.length} 个零件的正式提单？通过数量不能超过剩余可提数量。`,
      '生成提单确认',
      { type: 'warning', confirmButtonText: '确认生成', cancelButtonText: '取消' }
    )

    try {
      const successfulResults = []
      const failedResults = []
      const successItemIds = new Set()
      for (const { source, rows } of releaseGroups.values()) {
        const payload = {
          ...(source.sourceType === 'RFQ' ? { rfqId: source.sourceOrderId } : {}),
          orderType: releaseForm.orderType,
          internalProjectNo,
          customerOrderNo,
          customerProjectNo,
          startDate: releaseForm.startDate,
          arrangeType: releaseForm.arrangeType,
          items: rows.map(row => ({
            sourceType: row?.sourceType || 'RFQ',
            sourceOrderId: Number(row?.sourceOrderId ?? row?.rfqId ?? selectedRfqId.value),
            sourceItemId: Number(row?.sourceItemId ?? row?.id),
            itemId: (row?.sourceType || 'RFQ') === 'RFQ' ? row.id : undefined,
            acceptedQty: acceptedQtyMap[rowKey(row)] || remainingQtyFor(row)
          }))
        }
        try {
          const response = await generateTicket(payload)
          if (response.success) {
            successfulResults.push({ source, rows, response })
            rows.forEach(row => successItemIds.add(rowKey(row)))
          } else {
            failedResults.push({ source, rows, message: response.message || '提单生成失败。' })
          }
        } catch (error) {
          console.error(error)
          failedResults.push({
            source,
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
    }
  })
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

.selected-orders-mini-table {
  width: 100%;
  border-radius: 6px;
}

.selected-orders-mini-table :deep(.el-table__cell) {
  padding: 5px 0;
}

.selected-orders-mini-table :deep(.el-table__empty-text) {
  color: var(--erp-text-secondary);
  font-size: 13px;
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

.ticket-form {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--erp-border-soft);
}

.import-action-row {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 10px;
  width: 100%;
}

.release-table-actions {
  justify-content: flex-end;
  width: auto;
  margin-top: 0;
}

.release-table-actions :deep(.el-upload) {
  display: flex;
}

.release-table-actions :deep(.el-button) {
  margin-left: 0;
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
  width: 100%;
  max-width: 100%;
  min-width: 0;
}

.market-manual-date-row :deep(.market-manual-date-input.el-date-editor.el-input) {
  width: 100%;
  max-width: 100%;
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

.row-version-editor {
  display: flex;
  align-items: center;
  gap: 6px;
}

.row-version-input {
  width: 72px;
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

.drawing-replace-dialog {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.drawing-replace-upload {
  padding: 0;
  border: none;
  background: transparent;
}

.custom-dropzone {
  width: 100%;
  padding: 28px 16px;
  border: 1px dashed #aab6c8;
  border-radius: 8px;
  background: var(--erp-surface-soft);
  text-align: center;
  transition: border-color 0.2s ease, background-color 0.2s ease;
}

.custom-dropzone:hover {
  border-color: var(--erp-primary);
  background: #f3f8fc;
}

.dropzone-icon {
  margin-bottom: 12px;
  color: #8c98aa;
  font-size: 42px;
}

.dropzone-text {
  color: var(--erp-text-secondary);
  font-size: 15px;
}

.drawing-replace-dropzone :deep(.el-upload__tip) {
  margin-top: 8px;
  color: var(--erp-text-secondary);
}

.folder-select-row {
  margin-top: 12px;
  color: var(--erp-text-secondary);
  font-size: var(--erp-font-size-sm);
}

.drawing-replace-upload-actions {
  margin-top: 12px;
}

.folder-select-button {
  min-height: 32px;
  min-width: 150px;
  padding-inline: 14px;
}

.hidden-input {
  display: none;
}

.replacement-file-stats {
  padding: 12px 14px;
  border: 1px solid #cfe8d2;
  border-radius: 8px;
  background: #f4fbf5;
  color: var(--erp-success);
}

.replacement-file-stats h4 {
  margin: 0 0 8px;
  font-size: 14px;
}

.replacement-pdf-list,
.result-list {
  list-style: none;
  padding: 0;
  margin: 0;
  max-height: 150px;
  overflow-y: auto;
}

.replacement-pdf-list li,
.result-list li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 7px 0;
  border-bottom: 1px solid #dcefe0;
  color: #256b2b;
}

.replacement-pdf-list li:last-child,
.result-list li:last-child {
  border-bottom: none;
}

.replacement-result {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.replacement-summary-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 8px;
}

.replacement-summary-grid > div {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 56px;
  border: 1px solid var(--erp-border-soft);
  border-radius: 6px;
  background: var(--erp-surface-soft);
}

.replacement-summary-grid strong {
  color: var(--erp-text);
  font-size: 18px;
}

.replacement-summary-grid span,
.result-sub {
  color: var(--erp-text-secondary);
  font-size: 12px;
}

.result-main {
  color: var(--erp-text);
  font-weight: 700;
}

.result-reason {
  color: var(--erp-warning);
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
  .panel-title-row {
    grid-template-columns: 1fr;
    flex-direction: column;
    align-items: stretch;
  }

  .release-table-actions {
    justify-content: stretch;
    width: 100%;
  }

  .release-table-actions :deep(.el-button),
  .release-table-actions :deep(.el-upload) {
    width: 100%;
  }

  .replacement-summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
