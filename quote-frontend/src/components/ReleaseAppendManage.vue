<template>
  <section class="erp-page release-append-page">
    <header class="erp-page-header">
      <div>
        <h1 class="erp-page-title">追加提单</h1>
        <p class="erp-page-description">选择同客户的 U9 未处理销售订单，将销售订单 Excel 中的新零件追加进去并生成提单行。</p>
      </div>
    </header>

    <section class="erp-surface release-append-workflow">
      <div class="append-step append-step-customer">
        <div class="section-title">
          <span class="step-index">01</span>
          <span>选择客户</span>
        </div>
        <div class="step-panel">
          <el-form label-width="120px" class="append-form">
            <el-form-item label="客户" required>
              <el-input :model-value="customerLabel" readonly placeholder="请选择客户主数据">
                <template #append>
                  <el-button v-permission="'market:release-append:customer-select'" @click="customerPickerVisible = true">
                    选择客户
                  </el-button>
                </template>
              </el-input>
            </el-form-item>
          </el-form>
        </div>
      </div>

      <div class="append-step append-step-target-order">
        <div class="section-title">
          <span class="step-index">02</span>
          <span>选择目标销售订单</span>
        </div>
        <div class="step-panel target-order-panel">
          <el-form label-width="120px" class="append-form">
            <el-form-item label="销售订单" required>
              <el-input :model-value="targetOrderLabel" readonly placeholder="请先选择客户，再选择可追加销售订单">
                <template #append>
                  <el-button
                    v-permission="'market:release-append:order-select'"
                    :disabled="!selectedCustomer.customerName"
                    @click="openOrderPicker"
                  >
                    选择订单
                  </el-button>
                </template>
              </el-input>
            </el-form-item>
          </el-form>
          <el-alert
            title="只允许选择同客户且 U9 未处理或失败的销售订单；本页面不改订单头，只追加新零件。"
            type="info"
            :closable="false"
            show-icon
          />
        </div>
      </div>

      <div class="append-step append-step-excel">
        <div class="section-title">
          <span class="step-index">03</span>
          <span>上传销售订单 Excel</span>
        </div>
        <div class="step-panel upload-panel">
          <el-alert
            title="Excel 校验、字段解析和空值兜底沿用销售订单导入规则；新零件行号会按目标销售订单现有最大行号顺延。"
            type="info"
            :closable="false"
            show-icon
          />
          <el-upload
            ref="excelUploadRef"
            v-permission="'market:release-append:create'"
            action="#"
            :auto-upload="false"
            :limit="1"
            accept=".xlsx, .xls"
            :on-change="handleExcelChange"
            :on-remove="handleExcelRemove"
            class="centered-upload"
          >
            <template #trigger>
              <el-button type="primary" plain>选取 销售订单.xlsx</el-button>
            </template>
            <template #tip>
              <div class="el-upload__tip">只能上传 1 份销售订单 Excel 文件</div>
            </template>
          </el-upload>
        </div>
      </div>

      <div class="append-step append-step-drawings">
        <div class="section-title">
          <span class="step-index">04</span>
          <span>上传关联图纸 / 上传订单图纸文件夹</span>
        </div>
        <div class="drawing-folder-upload-row">
          <ImportDrawingUploadPanel
            v-model="drawingFolderFiles"
            v-model:folder-name="drawingFolderName"
            upload-mode="folder"
            permission="market:release-append:create"
            alert-title="推荐：上传整单图纸文件夹，系统会把该文件夹绑定到目标销售订单和本次追加零件。"
            selected-title="追加提单整单图纸文件夹待提交文件"
            :disabled="isPending(APPEND_ACTION_KEY)"
          />
        </div>
        <div class="drawing-upload-dual-grid">
          <ImportDrawingUploadPanel
            v-model="drawingFiles"
            permission="market:release-append:create"
            alert-title="按图号绑定图纸：支持 图号.pdf、图号 空格说明.pdf；文件名中的 - 会按图号内容处理。"
            selected-title="按图号待绑定图纸"
            :disabled="isPending(APPEND_ACTION_KEY)"
          />
          <ImportDrawingUploadPanel
            v-model="versionDrawingFiles"
            permission="market:release-append:create"
            alert-title="按图号+版本绑定图纸：支持 图号_版本.pdf、图号_版本 空格说明.pdf；文件名中的 - 会按图号或版本内容处理。"
            selected-title="按图号+版本待绑定图纸"
            :disabled="isPending(APPEND_ACTION_KEY)"
          />
        </div>
      </div>

      <div class="erp-toolbar submit-footer">
        <el-button
          v-permission="'market:release-append:create'"
          type="success"
          size="large"
          :loading="isPending(APPEND_ACTION_KEY)"
          :disabled="isPending(APPEND_ACTION_KEY)"
          class="submit-btn"
          @click="submitAppend"
        >
          追加销售提单
        </el-button>
      </div>
    </section>

    <section v-if="appendResult" class="erp-table-surface append-result-panel">
      <div class="erp-section-title-row">
        <div>
          <h2 class="erp-section-title">追加结果</h2>
          <p class="erp-section-description">{{ appendResult.message || '追加处理完成。' }}</p>
        </div>
        <div class="result-stats">
          <el-tag type="success">新增 {{ appendResult.appendedItemCount || 0 }} 行</el-tag>
          <el-tag type="primary">提单 {{ appendResult.ticketCount || 0 }} 行</el-tag>
          <el-tag type="info">数量 {{ appendResult.ticketQty || 0 }}</el-tag>
        </div>
      </div>

      <el-table :data="appendRows" border stripe height="320" class="append-result-table">
        <el-table-column prop="lineNo" label="行号" width="80" align="center" />
        <el-table-column prop="partNo" label="图号" min-width="160" show-overflow-tooltip />
        <el-table-column prop="version" label="版本" width="100" show-overflow-tooltip />
        <el-table-column prop="qty" label="数量" width="100" align="right" />
        <el-table-column prop="status" label="状态" width="120" align="center" />
        <el-table-column prop="warningMessage" label="提示" min-width="220" show-overflow-tooltip />
      </el-table>
    </section>

    <CustomerPickerDialog
      v-model="customerPickerVisible"
      :current-customer="selectedCustomer.customerName"
      :current-customer-code="selectedCustomer.customerCode"
      customer-scope="release-append"
      @select="handleCustomerSelected"
    />

    <OrderPickerDialog
      v-model="orderPickerVisible"
      title="选择目标销售订单"
      customer-scope="release-append"
      order-scope="release-append"
      default-status-after-customer="可追加"
      :locked-customer-name="selectedCustomer.customerName"
      :locked-customer-code="selectedCustomer.customerCode"
      @select="handleOrderSelected"
    />
  </section>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import CustomerPickerDialog from './CustomerPickerDialog.vue'
import OrderPickerDialog from './OrderPickerDialog.vue'
import ImportDrawingUploadPanel from './import/ImportDrawingUploadPanel.vue'
import { appendSalesOrderRelease } from '../services/marketApi'
import { usePendingAction } from '../composables/usePendingAction'
import { readActionError } from '../utils/actionError'

const APPEND_ACTION_KEY = 'market:release-append:create'

const { isPending, runAction } = usePendingAction()

const selectedCustomer = reactive({
  customerCode: '',
  customerShortName: '',
  customerName: ''
})
const targetOrder = ref(null)
const excelFile = ref(null)
const drawingFiles = ref([])
const versionDrawingFiles = ref([])
const drawingFolderFiles = ref([])
const drawingFolderName = ref('')
const appendResult = ref(null)
const customerPickerVisible = ref(false)
const orderPickerVisible = ref(false)
const excelUploadRef = ref(null)

const customerLabel = computed(() => {
  if (!selectedCustomer.customerName && !selectedCustomer.customerCode) return ''
  return [selectedCustomer.customerCode, selectedCustomer.customerShortName || selectedCustomer.customerName]
    .filter(Boolean)
    .join('｜')
})

const targetOrderLabel = computed(() => {
  if (!targetOrder.value) return ''
  return [
    targetOrder.value.salesOrderNo || targetOrder.value.documentNo,
    targetOrder.value.customerOrderNo,
    targetOrder.value.internalProjectNo
  ].filter(Boolean).join('｜')
})

const targetSalesOrderId = computed(() => targetOrder.value?.salesOrderId || targetOrder.value?.sourceOrderId || targetOrder.value?.id || null)
const appendRows = computed(() => appendResult.value?.rows || [])

const handleCustomerSelected = (customer) => {
  selectedCustomer.customerCode = customer?.customerCode || ''
  selectedCustomer.customerShortName = customer?.customerShortName || ''
  selectedCustomer.customerName = customer?.customerName || ''
  targetOrder.value = null
  appendResult.value = null
}

const openOrderPicker = () => {
  if (!selectedCustomer.customerName) {
    ElMessage.warning('请先选择客户。')
    return
  }
  orderPickerVisible.value = true
}

const handleOrderSelected = (order) => {
  targetOrder.value = order
  appendResult.value = null
}

const handleExcelChange = (file) => {
  excelFile.value = file?.raw || null
}

const handleExcelRemove = () => {
  excelFile.value = null
}

const resetUploads = () => {
  excelFile.value = null
  drawingFiles.value = []
  versionDrawingFiles.value = []
  drawingFolderFiles.value = []
  drawingFolderName.value = ''
  excelUploadRef.value?.clearFiles?.()
}

const appendDrawingFolderFields = (formData, files, folderName) => {
  if (!files?.length) return
  files.forEach(file => {
    formData.append('drawingFolderFiles', file)
  })
  formData.append('drawingFolderName', folderName || '')
}

const submitAppend = () => {
  if (!selectedCustomer.customerName) return ElMessage.warning('请选择客户。')
  if (!targetSalesOrderId.value) return ElMessage.warning('请选择目标销售订单。')
  if (!excelFile.value) return ElMessage.warning('请上传销售订单 Excel。')

  return runAction(APPEND_ACTION_KEY, async () => {
    const formData = new FormData()
    formData.append('excelFile', excelFile.value)
    drawingFiles.value.forEach(file => formData.append('drawings', file))
    versionDrawingFiles.value.forEach(file => formData.append('versionDrawings', file))
    appendDrawingFolderFields(formData, drawingFolderFiles.value, drawingFolderName.value)

    try {
      const response = await appendSalesOrderRelease(targetSalesOrderId.value, formData)
      appendResult.value = response
      if (response?.success) {
        const drawingReportHtml = buildDrawingReportHtml(response.drawingReport)
        await ElMessageBox.alert(
          `<div style="font-size: 15px; line-height: 1.5;">${escapeHtml(response.message || '追加销售提单成功。')}</div>${drawingReportHtml}`,
          '追加提单成功',
          {
            dangerouslyUseHTMLString: true,
            confirmButtonText: '知道了',
            type: 'success',
            center: true
          }
        )
        resetUploads()
      } else {
        ElMessage.warning(readActionError({ payload: response }, '追加提单存在失败明细，请查看结果。'))
      }
    } catch (error) {
      console.error(error)
      ElMessage.error(readActionError(error, '追加提单失败，请稍后重试。'))
    }
  })
}

const escapeHtml = (value) => String(value ?? '').replace(/[&<>"']/g, char => ({
  '&': '&amp;',
  '<': '&lt;',
  '>': '&gt;',
  '"': '&quot;',
  "'": '&#39;'
}[char]))

const buildDrawingReportHtml = (report) => {
  if (!report) return ''
  const missingItems = report.missingItems || []
  const unmatchedDrawings = report.unmatchedDrawings || []
  const hasWarning = missingItems.length > 0 || unmatchedDrawings.length > 0

  const missingHtml = missingItems.length > 0
    ? `<div style="margin-top: 10px;">
        <div style="font-weight: 600; color: #E6A23C;">缺失图纸零件（${missingItems.length} 个）</div>
        <div style="max-height: 120px; overflow: auto; margin-top: 6px; padding: 8px 10px; background: #fdf6ec; border: 1px solid #faecd8; border-radius: 4px;">
          ${missingItems.map(item => `<div style="line-height: 1.7;">${escapeHtml(item.partNo || '-')}${item.version ? `｜版本 ${escapeHtml(item.version)}` : ''}${item.description ? `｜${escapeHtml(item.description)}` : ''}${item.qty != null ? `｜数量 ${escapeHtml(item.qty)}` : ''}</div>`).join('')}
        </div>
      </div>`
    : ''

  const unmatchedHtml = unmatchedDrawings.length > 0
    ? `<div style="margin-top: 10px;">
        <div style="font-weight: 600; color: #909399;">未匹配到零件的图纸（${unmatchedDrawings.length} 份）</div>
        <div style="max-height: 100px; overflow: auto; margin-top: 6px; padding: 8px 10px; background: #f4f4f5; border: 1px solid #e9e9eb; border-radius: 4px;">
          ${unmatchedDrawings.map(file => `<div style="line-height: 1.7;">${escapeHtml(file.fileName || '-')}${file.partNo ? `｜识别图号 ${escapeHtml(file.partNo)}` : ''}</div>`).join('')}
        </div>
      </div>`
    : ''

  const okHtml = hasWarning
    ? ''
    : '<div style="margin-top: 10px; color: #67C23A;">图纸已全部匹配，没有发现缺图或多余图纸。</div>'

  return `<div style="margin-top: 12px; text-align: left;">
    <div style="font-weight: 600; color: ${hasWarning ? '#E6A23C' : '#67C23A'};">图纸匹配结果</div>
    <div style="margin-top: 6px; color: #606266;">Excel 零件 ${report.totalItemCount ?? 0} 个，上传 PDF ${report.uploadedDrawingCount ?? 0} 份，已绑定零件 ${report.matchedItemCount ?? 0} 个。</div>
    ${missingHtml}
    ${unmatchedHtml}
    ${okHtml}
  </div>`
}
</script>

<style scoped>
.release-append-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.release-append-workflow {
  display: grid;
  gap: 18px;
  padding: 20px;
}

.append-step {
  display: grid;
  gap: 10px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--erp-text-primary);
  font-weight: 700;
}

.step-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--erp-primary-soft);
  color: var(--erp-primary);
  font-size: 12px;
  font-weight: 800;
}

.step-panel {
  padding: 18px;
  border: 1px solid var(--erp-border-soft);
  border-radius: 8px;
  background: var(--erp-surface);
}

.append-form {
  max-width: 760px;
}

.target-order-panel,
.upload-panel {
  display: grid;
  gap: 14px;
}

.centered-upload {
  display: flex;
  justify-content: center;
}

.drawing-upload-dual-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.drawing-folder-upload-row {
  margin-bottom: 14px;
}

.submit-footer {
  justify-content: center;
  padding-top: 4px;
}

.submit-btn {
  min-width: 220px;
}

.append-result-panel {
  padding: 18px;
}

.result-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

@media (max-width: 960px) {
  .drawing-upload-dual-grid {
    grid-template-columns: 1fr;
  }
}
</style>
