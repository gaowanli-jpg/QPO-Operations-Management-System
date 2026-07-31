<template>
  <div class="erp-page market-page">
    <header class="erp-page-header">
      <div>
        <h1 class="erp-page-title">市场部：新单导入中心</h1>
        <p class="erp-page-description">上传客户询价表和关联图纸，或导入销售订单到独立主表。</p>
      </div>
    </header>

    <section class="erp-surface import-mode-shell">
      <el-tabs v-model="activeImportMode" class="import-mode-tabs">
        <el-tab-pane label="询价单" name="rfq">
          <section class="erp-surface import-workflow">
            <div class="import-step import-step-customer">
              <div class="section-title">
                <span class="step-index">01</span>
                <span>步骤 1：客户基础信息</span>
              </div>
              <div class="form-box step-panel">
                <el-form :model="rfqForm" label-width="120px" class="customer-form">
                  <el-row :gutter="20" justify="center">
                    <el-col :xs="24" :sm="22" :md="14" :lg="10" :xl="9" class="customer-input-col">
                      <el-form-item label="客户名称/代码" required>
                        <el-input v-model="rfqForm.customerName" placeholder="例如：Tesla-01 或 华为">
                          <template #append>
                            <el-button v-permission="'market:rfq:customer-select'" class="existing-customer-button" @click="customerPickerVisible = true">
                              选择已有客户
                            </el-button>
                          </template>
                        </el-input>
                      </el-form-item>
                    </el-col>
                  </el-row>
                </el-form>
              </div>
            </div>

            <div class="import-step import-step-excel">
              <div class="section-title">
                <span class="step-index">02</span>
                <span>步骤 2：上传客户询价表（Excel）</span>
              </div>
              <div class="upload-box step-panel">
                <el-alert
                  title="客户询价表需包含表头：图号、物料描述、材料名称、表面处理、版本、数量、单位、交货周期、备注；交货周期支持具体日期（如 2026-03-08、2026-3-8、2026.03.08、2026.3.8、2026/3/8）、天数周期（如 7天）或 Excel 日期序列号（如 46089）。"
                  type="info"
                  show-icon
                  :closable="false"
                  class="upload-alert"
                />

                <div class="import-action-row template-download-row">
                  <el-button
                    v-permission="'market:rfq:import'"
                    plain
                    :icon="Download"
                    class="workflow-soft-button template-download-button import-axis-button"
                    :loading="isPending(DOWNLOAD_RFQ_TEMPLATE_ACTION_KEY)"
                    :disabled="isPending(DOWNLOAD_RFQ_TEMPLATE_ACTION_KEY)"
                    @click="downloadRfqTemplate"
                  >
                    下载询价单模板
                  </el-button>
                </div>

                <div class="import-action-row">
                  <el-upload v-permission="'market:rfq:import'"
                    ref="excelUploadRef"
                    action="#"
                    :auto-upload="false"
                    :limit="1"
                    accept=".xlsx, .xls"
                    :on-change="handleExcelChange"
                    :on-remove="handleExcelRemove"
                    class="centered-upload"
                  >
                    <template #trigger>
                      <el-button type="primary" plain class="workflow-primary-action import-axis-button">选取 客户询价表.xlsx</el-button>
                    </template>
                    <template #tip>
                      <div class="el-upload__tip">只能上传 1 份标准格式的 Excel 文件</div>
                    </template>
                  </el-upload>
                </div>
              </div>
            </div>

            <div class="import-step import-step-drawings">
              <div class="section-title">
                <span class="step-index">03</span>
                <span>步骤 3：上传关联图纸（PDF）</span>
              </div>
              <ImportDrawingUploadPanel
                v-model="drawingFiles"
                permission="market:rfq:upload-drawing"
              />
            </div>

            <div class="erp-toolbar submit-footer">
              <el-button
                v-permission="'market:rfq:import'"
                type="success"
                size="large"
                @click="submitRfqPackage"
                :loading="isPending(IMPORT_RFQ_ACTION_KEY)"
                :disabled="isPending(IMPORT_RFQ_ACTION_KEY)"
                class="submit-btn import-axis-button"
              >
                确认无误，提交工程部评估
              </el-button>
            </div>
          </section>
        </el-tab-pane>

        <el-tab-pane label="销售订单" name="sales-order">
          <section class="sales-order-workflow">
            <div class="import-step import-step-sales-order-customer">
              <div class="section-title">
                <span class="step-index">01</span>
                <span>步骤 1：客户基础信息</span>
              </div>
              <div class="form-box step-panel">
                <el-form :model="salesOrderForm" label-width="120px" class="customer-form sales-order-form">
                  <el-row :gutter="20" justify="center">
                    <el-col :xs="24" :md="14" :lg="10" class="customer-input-col">
                      <el-form-item label="客户" required>
                        <el-input
                          :model-value="salesOrderCustomerLabel"
                          readonly
                          placeholder="请选择客户主数据"
                        >
                          <template #append>
                            <el-button
                              v-permission="'market:rfq:customer-select'"
                              class="existing-customer-button"
                              @click="openSalesOrderCustomerPicker"
                            >
                              选择客户
                            </el-button>
                          </template>
                        </el-input>
                      </el-form-item>
                    </el-col>
                  </el-row>
                </el-form>
              </div>
            </div>

            <div class="import-step import-step-sales-order-document">
              <div class="section-title">
                <span class="step-index">02</span>
                <span>步骤 2：单据类型编码</span>
              </div>
              <div class="form-box step-panel">
                <el-form :model="salesOrderForm" label-width="120px" class="customer-form sales-order-form">
                  <el-row :gutter="20" justify="center">
                    <el-col :xs="24" :sm="18" :md="10" :lg="7" class="document-type-col">
                      <el-form-item label="单据类型编码" required>
                        <el-input
                          v-model.trim="salesOrderForm.documentTypeCode"
                          placeholder="SO01"
                          maxlength="50"
                          clearable
                        />
                      </el-form-item>
                    </el-col>
                  </el-row>
                </el-form>
              </div>
            </div>

            <div class="import-step import-step-sales-order">
              <div class="section-title">
                <span class="step-index">03</span>
                <span>步骤 3：上传销售订单（Excel）</span>
              </div>
              <div class="upload-box step-panel">
                <el-alert
                  title="销售订单 Excel 按市场实际模板导入，客户和单据类型在前两步选择；导入后生成已报价销售订单和零件，字段校验以后端返回为准。"
                  type="info"
                  show-icon
                  :closable="false"
                  class="upload-alert"
                />

                <div class="import-action-row template-download-row">
                  <el-button
                    v-permission="'market:rfq:import-sales-order'"
                    plain
                    :icon="Download"
                    class="workflow-soft-button template-download-button import-axis-button"
                    :loading="isPending(DOWNLOAD_SALES_ORDER_TEMPLATE_ACTION_KEY)"
                    :disabled="isPending(DOWNLOAD_SALES_ORDER_TEMPLATE_ACTION_KEY)"
                    @click="downloadSalesOrderTemplate"
                  >
                    下载销售订单模板
                  </el-button>
                </div>

                <div class="import-action-row">
                  <el-upload
                    v-permission="'market:rfq:import-sales-order'"
                    ref="salesOrderUploadRef"
                    action="#"
                    :auto-upload="false"
                    :limit="1"
                    accept=".xlsx, .xls"
                    :on-change="handleSalesOrderExcelChange"
                    :on-remove="handleSalesOrderExcelRemove"
                    :disabled="isPending(IMPORT_SALES_ORDER_ACTION_KEY)"
                    class="centered-upload"
                  >
                    <template #trigger>
                      <el-button
                        type="primary"
                        plain
                        class="workflow-primary-action import-axis-button"
                        :disabled="isPending(IMPORT_SALES_ORDER_ACTION_KEY)"
                      >
                        选取 销售订单.xlsx
                      </el-button>
                    </template>
                    <template #tip>
                      <div class="el-upload__tip">只能上传 1 份销售订单 Excel，导入后创建独立销售订单。</div>
                    </template>
                  </el-upload>
                </div>
              </div>
            </div>

            <div class="import-step import-step-sales-order-drawings">
              <div class="section-title">
                <span class="step-index">04</span>
                <span>步骤 4：上传关联图纸（PDF）</span>
              </div>
              <ImportDrawingUploadPanel
                v-model="salesOrderDrawingFiles"
                permission="market:rfq:import-sales-order"
                :disabled="isPending(IMPORT_SALES_ORDER_ACTION_KEY)"
                selected-title="已成功提取销售订单待绑定图纸"
              />
            </div>

            <div class="erp-toolbar submit-footer sales-order-submit-row">
              <el-button
                v-permission="'market:rfq:import-sales-order'"
                type="success"
                size="large"
                @click="submitSalesOrderImport"
                :loading="isPending(IMPORT_SALES_ORDER_ACTION_KEY)"
                :disabled="isPending(IMPORT_SALES_ORDER_ACTION_KEY)"
                class="submit-btn import-axis-button"
              >
                导入销售订单
              </el-button>
            </div>

            <section v-if="salesOrderImportResult" class="erp-table-surface sales-order-result-surface">
              <div class="erp-toolbar sales-order-result-header">
                <div>
                  <h3>销售订单导入结果</h3>
                  <p>批次号：{{ salesOrderImportResult.batchNo || '-' }}</p>
                </div>
                <div class="sales-order-result-summary">
                  <el-tag type="info">合计 {{ salesOrderImportResult.totalOrderCount || 0 }}</el-tag>
                  <el-tag type="success">成功 {{ salesOrderImportResult.successOrderCount || 0 }}</el-tag>
                  <el-tag type="success">图纸绑定 {{ salesOrderImportResult.drawingReport?.matchedItemCount || 0 }}</el-tag>
                  <el-tag :type="salesOrderImportResult.drawingReport?.missingDrawingCount > 0 ? 'warning' : 'info'">
                    缺图 {{ salesOrderImportResult.drawingReport?.missingDrawingCount || 0 }}
                  </el-tag>
                  <el-tag :type="salesOrderImportResult.failedOrderCount > 0 ? 'danger' : 'info'">
                    失败 {{ salesOrderImportResult.failedOrderCount || 0 }}
                  </el-tag>
                </div>
              </div>

              <el-alert
                :title="salesOrderResultMessage"
                :type="salesOrderResultType"
                show-icon
                :closable="false"
                class="sales-order-result-alert"
              />

              <el-table
                :data="salesOrderResultRows"
                border
                stripe
                height="360"
                empty-text="暂无导入明细"
              >
                <el-table-column prop="salesOrderNo" label="销售订单号" min-width="170" show-overflow-tooltip />
                <el-table-column prop="customerOrderNo" label="客户订单号" min-width="160" show-overflow-tooltip />
                <el-table-column prop="internalProjectNo" label="内部项目号" min-width="140" show-overflow-tooltip />
                <el-table-column prop="lineCount" label="零件数" width="90" align="right" />
                <el-table-column prop="matchedItemCount" label="已补全数" width="110" align="right" />
                <el-table-column prop="unmatchedItemCount" label="待销售匹配" width="130" align="right" />
                <el-table-column prop="matchedDrawingCount" label="图纸绑定数" width="120" align="right" />
                <el-table-column prop="missingDrawingCount" label="缺图数" width="100" align="right" />
                <el-table-column prop="status" label="状态" width="120" align="center">
                  <template #default="scope">
                    <el-tag :type="scope.row.success ? 'success' : 'danger'" effect="plain">
                      {{ scope.row.success ? '成功' : (scope.row.status || '失败') }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="warningMessage" label="提示信息" min-width="240" show-overflow-tooltip>
                  <template #default="scope">
                    <span>{{ scope.row.warningMessage || scope.row.errorMessage || '-' }}</span>
                  </template>
                </el-table-column>
              </el-table>
            </section>
          </section>
        </el-tab-pane>
      </el-tabs>
    </section>

    <CustomerPickerDialog
      v-model="customerPickerVisible"
      :current-customer="rfqForm.customerName"
      customer-scope="market-rfq"
      @select="selectExistingCustomer"
    />
    <CustomerPickerDialog
      v-model="salesOrderCustomerPickerVisible"
      :current-customer="salesOrderForm.customerName"
      customer-scope="market-rfq"
      @select="selectSalesOrderCustomer"
    />
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download } from '@element-plus/icons-vue'
import CustomerPickerDialog from './CustomerPickerDialog.vue'
import ImportDrawingUploadPanel from './import/ImportDrawingUploadPanel.vue'
import {
  downloadRfqImportTemplate,
  downloadSalesOrderImportTemplate,
  importRfq,
  importSalesOrderToQpo
} from '../services/marketApi'
import { usePendingAction } from '../composables/usePendingAction'
import { readActionError } from '../utils/actionError'

const rfqForm = reactive({ customerName: '' })
const excelFile = ref(null)
const drawingFiles = ref([])
const excelUploadRef = ref(null)
const customerPickerVisible = ref(false)
const activeImportMode = ref('rfq')
const salesOrderForm = reactive({
  customerId: null,
  customerCode: '',
  customerShortName: '',
  customerName: '',
  documentTypeCode: 'SO01'
})
const salesOrderFile = ref(null)
const salesOrderDrawingFiles = ref([])
const salesOrderUploadRef = ref(null)
const salesOrderCustomerPickerVisible = ref(false)
const salesOrderImportResult = ref(null)
const IMPORT_RFQ_ACTION_KEY = 'market:rfq:import'
const IMPORT_SALES_ORDER_ACTION_KEY = 'market:rfq:import-sales-order'
const DOWNLOAD_RFQ_TEMPLATE_ACTION_KEY = 'market:rfq:download-template'
const DOWNLOAD_SALES_ORDER_TEMPLATE_ACTION_KEY = 'market:rfq:download-sales-order-template'
const { isPending, runAction } = usePendingAction()

const handleExcelChange = (uploadFile) => { excelFile.value = uploadFile.raw }
const handleExcelRemove = () => { excelFile.value = null }

const downloadRfqTemplate = () => runAction(DOWNLOAD_RFQ_TEMPLATE_ACTION_KEY, async () => {
  try {
    await downloadRfqImportTemplate()
  } catch (error) {
    console.error(error)
    ElMessage.error(readActionError(error, '询价单模板下载失败，请稍后重试。'))
  }
})

const salesOrderResultRows = computed(() => salesOrderImportResult.value?.rows || [])
const salesOrderResultType = computed(() => salesOrderImportResult.value?.success ? 'success' : 'warning')
const salesOrderResultMessage = computed(() => {
  const result = salesOrderImportResult.value
  if (!result) return ''
  if (result.errorMessage) return result.errorMessage
  if (result.success) return '销售订单已成功导入独立销售订单主表。'
  return '销售订单导入存在失败明细，请查看下方结果表。'
})
const salesOrderCustomerLabel = computed(() => {
  const parts = [
    salesOrderForm.customerCode,
    salesOrderForm.customerShortName,
    salesOrderForm.customerName
  ].filter(Boolean)
  return parts.length > 0 ? parts.join(' / ') : ''
})

const handleSalesOrderExcelChange = (uploadFile) => {
  salesOrderFile.value = uploadFile.raw
  salesOrderImportResult.value = null
}

const handleSalesOrderExcelRemove = () => {
  salesOrderFile.value = null
  salesOrderImportResult.value = null
}

const downloadSalesOrderTemplate = () => runAction(DOWNLOAD_SALES_ORDER_TEMPLATE_ACTION_KEY, async () => {
  try {
    await downloadSalesOrderImportTemplate()
  } catch (error) {
    console.error(error)
    ElMessage.error(readActionError(error, '销售订单模板下载失败，请稍后重试。'))
  }
})

const resolveCustomerName = (customer) => typeof customer === 'string' ? customer : (customer?.customerName || '')

const selectExistingCustomer = (customer) => {
  rfqForm.customerName = resolveCustomerName(customer)
}

const openSalesOrderCustomerPicker = () => {
  salesOrderCustomerPickerVisible.value = true
}

const selectSalesOrderCustomer = (customer) => {
  salesOrderForm.customerId = customer?.id || null
  salesOrderForm.customerCode = customer?.customerCode || ''
  salesOrderForm.customerShortName = customer?.customerShortName || ''
  salesOrderForm.customerName = resolveCustomerName(customer)
  salesOrderImportResult.value = null
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
          ${missingItems.map(item => `
            <div style="line-height: 1.7;">
              ${escapeHtml(item.partNo || '-')}
              ${item.version ? `｜版本 ${escapeHtml(item.version)}` : ''}
              ${item.description ? `｜${escapeHtml(item.description)}` : ''}
              ${item.qty != null ? `｜数量 ${escapeHtml(item.qty)}` : ''}
            </div>
          `).join('')}
        </div>
      </div>`
    : ''

  const unmatchedHtml = unmatchedDrawings.length > 0
    ? `<div style="margin-top: 10px;">
        <div style="font-weight: 600; color: #909399;">未匹配到零件的图纸（${unmatchedDrawings.length} 份）</div>
        <div style="max-height: 100px; overflow: auto; margin-top: 6px; padding: 8px 10px; background: #f4f4f5; border: 1px solid #e9e9eb; border-radius: 4px;">
          ${unmatchedDrawings.map(file => `
            <div style="line-height: 1.7;">
              ${escapeHtml(file.fileName || '-')}
              ${file.partNo ? `｜识别图号 ${escapeHtml(file.partNo)}` : ''}
            </div>
          `).join('')}
        </div>
      </div>`
    : ''

  const okHtml = hasWarning
    ? ''
    : '<div style="margin-top: 10px; color: #67C23A;">图纸已全部匹配，没有发现缺图或多余图纸。</div>'

  return `<div style="margin-top: 12px; text-align: left;">
    <div style="font-weight: 600; color: ${hasWarning ? '#E6A23C' : '#67C23A'};">图纸匹配结果</div>
    <div style="margin-top: 6px; color: #606266;">
      Excel 零件 ${report.totalItemCount ?? 0} 个，上传 PDF ${report.uploadedDrawingCount ?? 0} 份，已绑定零件 ${report.matchedItemCount ?? 0} 个。
    </div>
    ${missingHtml}
    ${unmatchedHtml}
    ${okHtml}
  </div>`
}

const submitSalesOrderImport = async () => {
  if (!salesOrderForm.customerId) return ElMessage.warning('请选择销售订单客户！')
  if (!salesOrderFile.value) return ElMessage.warning('请上传销售订单 Excel！')

  return runAction(IMPORT_SALES_ORDER_ACTION_KEY, async () => {
    const formData = new FormData()
    const documentTypeCode = salesOrderForm.documentTypeCode.trim() || 'SO01'
    formData.append('customerId', String(salesOrderForm.customerId))
    formData.append('documentTypeCode', documentTypeCode)
    formData.append('excelFile', salesOrderFile.value)
    salesOrderDrawingFiles.value.forEach(file => {
      formData.append('drawings', file)
    })

    try {
      const response = await importSalesOrderToQpo(formData)
      salesOrderImportResult.value = response

      if (response?.success) {
        const drawingReportHtml = buildDrawingReportHtml(response.drawingReport)
        salesOrderForm.documentTypeCode = documentTypeCode
        ElMessageBox.alert(
          `<div style="font-size: 15px; line-height: 1.5;">销售订单已导入独立主表，成功 ${response.successOrderCount || 0} 单。</div>
           ${drawingReportHtml}`,
          '销售订单导入成功',
          {
            dangerouslyUseHTMLString: true,
            confirmButtonText: '开启下一单',
            type: 'success',
            center: true,
          }
        ).then(() => {
          salesOrderForm.customerId = null
          salesOrderForm.customerCode = ''
          salesOrderForm.customerShortName = ''
          salesOrderForm.customerName = ''
          salesOrderForm.documentTypeCode = 'SO01'
          salesOrderFile.value = null
          salesOrderDrawingFiles.value = []
          salesOrderUploadRef.value?.clearFiles?.()
        })
      } else {
        ElMessage.warning(readActionError({ response: { data: response } }, '销售订单导入存在失败明细，请查看下方结果表。'))
      }
    } catch (error) {
      console.error(error)
      ElMessage.error(readActionError(error, '销售订单导入失败，请稍后重试。'))
    }
  })
}

// 提交询价单和图纸
const submitRfqPackage = async () => {
  if (!rfqForm.customerName) return ElMessage.warning('请填写客户名称！')
  if (!excelFile.value) return ElMessage.warning('请上传客户询价表 Excel！')

  return runAction(IMPORT_RFQ_ACTION_KEY, async () => {
    const formData = new FormData()
    formData.append('customerName', rfqForm.customerName)
    formData.append('excelFile', excelFile.value)

    drawingFiles.value.forEach(file => {
      formData.append('drawings', file)
    })

    try {
      const response = await importRfq(formData)

      if (response.success) {
        const drawingReportHtml = buildDrawingReportHtml(response.drawingReport)
        ElMessageBox.alert(
          `<div style="font-size: 15px; line-height: 1.5;">${response.message}</div>
           <div style="color: #67C23A; margin-top: 10px;">工程部已收到系统推送任务。</div>
           ${drawingReportHtml}`,
          '订单导入成功',
          {
            dangerouslyUseHTMLString: true,
            confirmButtonText: '开启下一单',
            type: 'success',
            center: true,
          }
        ).then(() => {
          rfqForm.customerName = ''
          excelFile.value = null
          drawingFiles.value = [] // 清空拖拽出来的 PDF
          if (excelUploadRef.value) {
            excelUploadRef.value.clearFiles() // 清空原生的 Excel 选择框
          }
        })
      } else {
        ElMessage.error({
          message: response.message,
          duration: 5000,
          showClose: true
        })
      }
    } catch (error) {
      console.error(error)
      ElMessage.error(readActionError(error, '网络请求失败，请确保后端服务正常。'))
    }
  })
}
</script>

<style scoped>
.import-mode-shell {
  padding: 0 var(--erp-space-5) var(--erp-space-5);
}

.import-mode-tabs :deep(.el-tabs__header) {
  margin-bottom: var(--erp-space-4);
}

.import-mode-tabs :deep(.el-tabs__nav-wrap::after) {
  background: var(--erp-border-soft);
}

.import-mode-tabs :deep(.el-tabs__item) {
  min-width: 96px;
  font-weight: 700;
}

.import-workflow,
.sales-order-workflow {
  display: grid;
  gap: var(--erp-space-5);
  padding: var(--erp-space-5);
  background: linear-gradient(180deg, var(--erp-surface), var(--erp-bg-subtle));
}

.section-title {
  display: flex;
  align-items: center;
  gap: var(--erp-space-3);
  margin: 0;
  padding: 14px 18px;
  border-bottom: 1px solid var(--erp-border-soft);
  background: var(--erp-surface-soft);
  color: var(--erp-text);
  font-size: 14px;
  font-weight: 700;
  line-height: 1.2;
}

.step-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 34px;
  height: 24px;
  border-radius: var(--erp-radius-full);
  background: var(--erp-primary);
  color: var(--erp-text-inverse);
  font-size: var(--erp-font-size-xs);
  font-variant-numeric: tabular-nums;
  line-height: 1;
}

.import-step {
  overflow: hidden;
  border: 1px solid var(--erp-border-soft);
  border-radius: var(--erp-radius-xl);
  background: var(--erp-surface);
  box-shadow: var(--erp-shadow-sm);
}

.form-box,
.upload-box {
  border: 0;
  border-radius: 0;
  background: transparent;
}

.step-panel {
  padding: 18px 20px;
}

.customer-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.customer-input-col {
  max-width: 560px;
}

.existing-customer-button {
  min-width: 112px;
}

.upload-alert {
  margin-bottom: 16px;
}

.upload-box :deep(.el-upload) {
  width: 100%;
}

.centered-upload {
  width: 100%;
  text-align: center;
}

.centered-upload :deep(.el-upload) {
  display: flex;
  justify-content: center;
}

.centered-upload :deep(.el-upload__tip) {
  margin-top: 10px;
  color: var(--erp-text-muted);
  text-align: center;
}

.import-action-row {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 10px;
  width: 100%;
  margin-top: 12px;
}

.template-download-row {
  margin-top: 0;
}

.import-axis-button {
  justify-content: center;
  min-width: 240px;
}

.template-download-button {
  min-height: 34px;
}

.upload-box :deep(.el-upload-dragger) {
  width: 100%;
  border-radius: 8px;
  background: var(--erp-surface-soft);
}

.submit-footer {
  justify-content: center;
  margin-top: 0;
  border-style: dashed;
  background: var(--erp-surface);
}

.submit-btn {
  width: auto;
  min-width: 260px;
  font-size: 14px;
  font-weight: 700;
}

.sales-order-result-surface {
  padding: var(--erp-space-4);
}

.sales-order-result-header {
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--erp-space-3);
}

.sales-order-result-header h3 {
  margin: 0;
  color: var(--erp-text);
  font-size: 15px;
}

.sales-order-result-header p {
  margin: 4px 0 0;
  color: var(--erp-text-secondary);
  font-size: var(--erp-font-size-sm);
}

.sales-order-result-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

.sales-order-result-alert {
  margin: 12px 0;
}

@media (max-width: 900px) {
  .customer-input-col {
    max-width: 100%;
  }

  .submit-footer,
  .sales-order-result-header {
    flex-direction: column;
    align-items: stretch;
  }

  .sales-order-result-summary {
    justify-content: flex-start;
  }

  .submit-btn {
    width: 100%;
  }
}
</style>
