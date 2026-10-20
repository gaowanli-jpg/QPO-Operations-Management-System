<template>
  <el-dialog
    v-model="visible"
    title="报价交期表格替换预览"
    width="min(980px, calc(100vw - 32px))"
    class="quote-lead-time-replacement-dialog"
    :close-on-click-modal="false"
    :before-close="handleBeforeClose"
    @closed="resetDialogState"
  >
    <div class="lead-time-replacement-dialog">
      <el-alert
        title="按图号和版本匹配当前 RFQ 零件，先预览报价交期差异，确认后才写入。"
        description="Excel 中报价交期为空的行不会清空原值；未匹配行不阻断其他可替换行。"
        type="info"
        :closable="false"
        show-icon
      />

      <section class="replacement-upload-section">
        <div class="replacement-upload-main">
          <el-upload
            ref="uploadRef"
            action="#"
            :auto-upload="false"
            :show-file-list="false"
            accept=".xlsx,.xls"
            :disabled="replacementBusy || disabled"
            :on-change="handleFileChange"
          >
            <template #trigger>
              <el-button
                plain
                class="workflow-soft-button"
                :loading="previewing"
                :disabled="replacementBusy || disabled"
              >
                {{ selectedFile ? '重新选择文件' : '选择替换表格' }}
              </el-button>
            </template>
          </el-upload>
          <div class="replacement-file-state">
            <strong>{{ selectedFile?.name || '尚未选择表格' }}</strong>
            <span v-if="selectedFile">{{ formatFileSize(selectedFile.size) }}</span>
            <span v-else>表头需包含：图号、版本、报价交期</span>
          </div>
        </div>
        <div class="replacement-fingerprint-state">
          <span>指纹状态</span>
          <strong>{{ fingerprintStatusText }}</strong>
        </div>
      </section>

      <el-alert
        v-if="previewError"
        :title="previewError"
        type="error"
        :closable="false"
        show-icon
      />

      <section v-if="previewResult" class="replacement-preview-section">
        <div class="replacement-section-title">识别字段</div>
        <div class="replacement-field-tags">
          <el-tag v-for="field in recognizedFields" :key="field" size="small" effect="plain">
            {{ field }}
          </el-tag>
        </div>

        <div class="replacement-summary-grid">
          <div v-for="item in summaryItems" :key="item.key" :class="item.className">
            <strong>{{ item.value }}</strong>
            <span>{{ item.label }}</span>
          </div>
        </div>

        <el-alert
          v-if="summary.errorCount > 0"
          :title="`存在 ${summary.errorCount} 行错误，请修正 Excel 后重新预览。`"
          type="error"
          :closable="false"
          show-icon
        />
        <el-alert
          v-else-if="summary.emptyValueCount > 0 || summary.unmatchedCount > 0"
          :title="`有 ${summary.emptyValueCount || 0} 行报价交期为空、${summary.unmatchedCount || 0} 行未匹配；不阻断其他可替换行。`"
          type="warning"
          :closable="false"
          show-icon
        />

        <div class="replacement-preview-table-wrap">
          <el-table
            :data="pagedPreviewRows"
            border
            stripe
            size="small"
            class="replacement-preview-table"
            empty-text="暂无预览行"
          >
            <el-table-column prop="excelRowNumber" label="Excel 行" width="82" align="center" />
            <el-table-column label="图号 / 版本" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">
                <div class="replacement-part-cell">
                  <strong>{{ row.partNo || '-' }}</strong>
                  <span>{{ row.version || '-' }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="110" align="center">
              <template #default="{ row }">
                <el-tag :type="rowStatusTagType(row.status)" size="small" effect="plain">
                  {{ statusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="当前报价交期" min-width="130">
              <template #default="{ row }">{{ formatValue(row.currentQuoteLeadTime) }}</template>
            </el-table-column>
            <el-table-column label="导入报价交期" min-width="130">
              <template #default="{ row }">{{ formatValue(row.importedQuoteLeadTime) }}</template>
            </el-table-column>
            <el-table-column prop="reason" label="说明" min-width="220" show-overflow-tooltip>
              <template #default="{ row }">
                <span :class="row.status === 'ERROR' ? 'replacement-error-text' : 'replacement-muted'">{{ row.reason || '-' }}</span>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <div v-if="previewRowTotal" class="erp-pagination replacement-preview-pagination">
          <el-pagination
            v-model:current-page="previewPagination.page"
            v-model:page-size="previewPagination.pageSize"
            :page-sizes="CLIENT_PAGE_SIZES"
            :pager-count="7"
            :total="previewRowTotal"
            background
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="handlePreviewPageSizeChange"
          />
        </div>
      </section>

      <section v-if="confirmResult" class="replacement-confirm-result">
        <el-alert
          :title="confirmResult.message || `确认完成，已更新 ${confirmResult.summary?.updatedItemCount || 0} 个零件报价交期。`"
          type="success"
          :closable="false"
          show-icon
        />
      </section>
    </div>

    <template #footer>
      <div class="erp-dialog-footer replacement-dialog-actions">
        <el-button :disabled="replacementBusy" @click="visible = false">关闭</el-button>
        <el-button plain :disabled="replacementBusy || !selectedFile" @click="resetSelectedFile">清空文件</el-button>
        <el-button
          plain
          class="workflow-soft-button"
          :loading="previewing"
          :disabled="!canPreview"
          @click="submitPreview"
        >
          生成预览
        </el-button>
        <el-button
          type="primary"
          :loading="confirming"
          :disabled="!canConfirmPreview"
          @click="submitConfirm"
        >
          确认替换
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { usePendingAction } from '../../composables/usePendingAction'
import { CLIENT_PAGE_SIZES, DEFAULT_CLIENT_PAGE_SIZE, clampPage, paginateRows } from '../../utils/clientPagination'
import {
  confirmQuoteProcessLeadTimeReplacement,
  previewQuoteProcessLeadTimeReplacement
} from '../../services/marketApi'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  rfqId: { type: [Number, String], default: null },
  disabled: { type: Boolean, default: false },
  scope: { type: String, default: '' }
})

const emit = defineEmits(['update:modelValue', 'busy-change', 'confirmed'])

const uploadRef = ref(null)
const selectedFile = ref(null)
const previewResult = ref(null)
const confirmResult = ref(null)
const previewError = ref('')
const confirmedPreviewFingerprint = ref('')
const replacementStateVersion = ref(0)
const previewPagination = reactive({ page: 1, pageSize: DEFAULT_CLIENT_PAGE_SIZE })

const { isPending, runAction } = usePendingAction()
const actionKeys = {
  preview: 'market:quote-process:lead-time-replacement-preview',
  confirm: 'market:quote-process:lead-time-replacement-confirm'
}

const visible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value)
})

const previewing = computed(() => isPending(actionKeys.preview))
const confirming = computed(() => isPending(actionKeys.confirm))
const replacementBusy = computed(() => previewing.value || confirming.value)
const recognizedFields = computed(() => previewResult.value?.recognizedFields || [])
const previewRows = computed(() => previewResult.value?.rows || [])
const previewRowTotal = computed(() => previewRows.value.length)
const pagedPreviewRows = computed(() => paginateRows(previewRows.value, previewPagination))
const summary = computed(() => previewResult.value?.summary || {})
const previewFingerprint = computed(() => previewResult.value?.previewFingerprint || '')
const hasErroredRows = computed(() => previewRows.value.some(row => row.status === 'ERROR'))
const canPreview = computed(() => Boolean(selectedFile.value && props.rfqId && !props.disabled && !replacementBusy.value))
const canConfirmPreview = computed(() => Boolean(
  selectedFile.value
  && props.rfqId
  && previewResult.value?.canConfirm
  && previewFingerprint.value
  && !props.disabled
  && !replacementBusy.value
  && !confirmResult.value?.success
  && Number(summary.value.errorCount || 0) === 0
  && !hasErroredRows.value
))

const fingerprintStatusText = computed(() => {
  if (!previewFingerprint.value) return '未生成预览指纹'
  if (confirmedPreviewFingerprint.value === previewFingerprint.value) return '已按该指纹确认'
  return `已生成 ${previewFingerprint.value.slice(0, 12)}`
})

const summaryItems = computed(() => [
  { key: 'imported', label: '导入行', value: summary.value.importedCount || 0 },
  { key: 'matched', label: '已匹配', value: summary.value.matchedCount || 0 },
  { key: 'changed', label: '可替换', value: summary.value.changedCount || 0, className: 'summary-change' },
  { key: 'unchanged', label: '无变化', value: summary.value.unchangedCount || 0 },
  { key: 'empty', label: '空值跳过', value: summary.value.emptyValueCount || 0, className: 'summary-warning' },
  { key: 'unmatched', label: '未匹配', value: summary.value.unmatchedCount || 0, className: 'summary-warning' },
  { key: 'error', label: '错误', value: summary.value.errorCount || 0, className: 'summary-error' }
])

watch(replacementBusy, value => emit('busy-change', value), { immediate: true })

watch(visible, value => {
  if (!value) resetDialogState()
})

watch(
  () => [previewRowTotal.value, previewPagination.pageSize],
  () => {
    previewPagination.page = clampPage(previewPagination, previewRowTotal.value)
  }
)

const clearPreviewState = () => {
  previewResult.value = null
  confirmResult.value = null
  previewError.value = ''
  confirmedPreviewFingerprint.value = ''
  previewPagination.page = 1
}

const resetDialogState = () => {
  replacementStateVersion.value += 1
  selectedFile.value = null
  uploadRef.value?.clearFiles()
  clearPreviewState()
}

const handleBeforeClose = (done) => {
  if (replacementBusy.value) {
    ElMessage.warning('表格数据替换处理中，请稍后关闭。')
    return
  }
  done()
}

const handleFileChange = (uploadFile) => {
  const file = uploadFile?.raw
  uploadRef.value?.clearFiles()
  if (!file) return

  const fileName = String(file.name || '').toLowerCase()
  if (!fileName.endsWith('.xlsx') && !fileName.endsWith('.xls')) {
    ElMessage.warning('表格数据替换仅支持 .xlsx 或 .xls 文件。')
    return
  }

  selectedFile.value = file
  replacementStateVersion.value += 1
  clearPreviewState()
}

const resetSelectedFile = () => resetDialogState()

const buildReplacementFormData = () => {
  const formData = new FormData()
  formData.append('excelFile', selectedFile.value)
  return formData
}

const submitPreview = async () => {
  if (!selectedFile.value) return ElMessage.warning('请先选择替换表格。')
  if (!props.rfqId) return ElMessage.warning('请先选择 RFQ 订单。')

  const formData = buildReplacementFormData()
  const requestVersion = replacementStateVersion.value
  return runAction(actionKeys.preview, async () => {
    try {
      previewError.value = ''
      confirmResult.value = null
      const response = await previewQuoteProcessLeadTimeReplacement(props.rfqId, formData, props.scope || undefined)
      if (requestVersion !== replacementStateVersion.value || !visible.value) return
      previewResult.value = response || null
      previewPagination.page = 1
      confirmedPreviewFingerprint.value = ''
      if (response?.canConfirm) {
        ElMessage.success('预览已生成，请检查差异后确认。')
      } else if (Number(response?.summary?.errorCount || 0) > 0) {
        ElMessage.warning('预览已生成，但存在错误行，暂不能确认。')
      } else {
        ElMessage.warning('预览已生成，但没有可确认的替换内容。')
      }
    } catch (error) {
      console.error(error)
      previewError.value = requestErrorMessage(error, '报价交期表格替换预览失败。')
      ElMessage.error(previewError.value)
    }
  })
}

const submitConfirm = async () => {
  if (!canConfirmPreview.value) return

  const formData = buildReplacementFormData()
  formData.append('previewFingerprint', previewFingerprint.value)
  const requestVersion = replacementStateVersion.value
  const confirmedFingerprint = previewFingerprint.value
  return runAction(actionKeys.confirm, async () => {
    try {
      const response = await confirmQuoteProcessLeadTimeReplacement(props.rfqId, formData, props.scope || undefined)
      if (requestVersion !== replacementStateVersion.value || !visible.value) return
      confirmResult.value = response || null
      confirmedPreviewFingerprint.value = confirmedFingerprint
      emit('confirmed', response || {})
      ElMessage.success(response?.message || '报价交期表格替换已确认。')
    } catch (error) {
      console.error(error)
      previewError.value = requestErrorMessage(error, '报价交期表格替换确认失败。')
      ElMessage.error(previewError.value)
    }
  })
}

const handlePreviewPageSizeChange = () => {
  previewPagination.page = 1
}

const statusLabel = (status) => ({
  CHANGED: '可替换',
  UNCHANGED: '无变化',
  EMPTY_VALUE: '空值跳过',
  UNMATCHED: '未匹配',
  ERROR: '错误'
}[status] || status || '-')

const rowStatusTagType = (status) => ({
  CHANGED: 'success',
  UNCHANGED: 'info',
  EMPTY_VALUE: 'warning',
  UNMATCHED: 'warning',
  ERROR: 'danger'
}[status] || 'info')

const formatValue = (value) => (value === null || value === undefined || value === '' ? '空' : String(value))

const formatFileSize = (size) => {
  const number = Number(size || 0)
  if (number < 1024) return `${number} B`
  if (number < 1024 * 1024) return `${(number / 1024).toFixed(1)} KB`
  return `${(number / 1024 / 1024).toFixed(1)} MB`
}

const requestErrorMessage = (error, fallback) => error?.response?.data?.message || error?.payload?.message || error?.message || fallback
</script>

<style scoped>
.lead-time-replacement-dialog {
  display: flex;
  max-width: 100%;
  flex-direction: column;
  gap: 14px;
  overflow: hidden;
}

.replacement-upload-section {
  display: flex;
  align-items: stretch;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  padding: 12px;
  border: 1px dashed var(--erp-border-soft);
  border-radius: 8px;
  background: var(--erp-surface-soft);
}

.replacement-upload-main {
  display: flex;
  min-width: 0;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.replacement-file-state,
.replacement-fingerprint-state,
.replacement-preview-section {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 8px;
}

.replacement-file-state strong,
.replacement-fingerprint-state strong {
  max-width: 360px;
  overflow: hidden;
  color: var(--erp-text);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.replacement-file-state span,
.replacement-fingerprint-state span,
.replacement-muted {
  color: var(--erp-text-secondary);
  font-size: 12px;
}

.replacement-section-title {
  padding-left: 10px;
  border-left: 3px solid var(--erp-primary);
  color: var(--erp-text);
  font-size: 14px;
  font-weight: 700;
}

.replacement-field-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.replacement-summary-grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(78px, 1fr));
  gap: 8px;
}

.replacement-summary-grid > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  padding: 10px 8px;
  border: 1px solid var(--erp-border-soft);
  border-radius: 6px;
  background: #fff;
}

.replacement-summary-grid strong {
  color: var(--erp-text);
  font-size: 17px;
  line-height: 1.1;
}

.replacement-summary-grid span {
  color: var(--erp-text-secondary);
  font-size: 12px;
}

.replacement-summary-grid .summary-change strong {
  color: var(--erp-success);
}

.replacement-summary-grid .summary-warning strong {
  color: var(--erp-warning);
}

.replacement-summary-grid .summary-error strong {
  color: var(--erp-danger);
}

.replacement-preview-table-wrap {
  max-width: 100%;
  overflow-x: auto;
  border-radius: 8px;
}

.replacement-preview-table {
  min-width: 880px;
}

.replacement-part-cell {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.replacement-part-cell strong {
  color: var(--erp-text);
  font-weight: 700;
}

.replacement-part-cell span {
  color: var(--erp-text-secondary);
  font-size: 12px;
}

.replacement-error-text {
  color: var(--erp-danger);
  font-weight: 700;
}

.replacement-dialog-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.replacement-dialog-actions :deep(.el-button) {
  margin-left: 0;
}

@media (max-width: 640px) {
  .replacement-upload-section,
  .replacement-upload-main,
  .replacement-dialog-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .replacement-dialog-actions :deep(.el-button),
  .replacement-upload-main :deep(.el-button),
  .replacement-upload-main :deep(.el-upload) {
    width: 100%;
  }

  .replacement-summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
