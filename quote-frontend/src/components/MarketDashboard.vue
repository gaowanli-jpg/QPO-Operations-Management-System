<template>
  <div class="market-container">
    <el-card class="main-card">
      <template #header>
        <div class="card-header">
          <h2>市场部：新询价单导入中心</h2>
        </div>
      </template>

      <!-- 第一步：客户与主单信息 -->
      <div class="section-title">步骤 1：客户基础信息</div>
      <el-form :model="rfqForm" label-width="120px" class="form-box">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="客户名称/代码" required>
              <el-input v-model="rfqForm.customerName" placeholder="例如：Tesla-01 或 华为" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <!-- 第二步：上传 Excel BOM 表 -->
      <div class="section-title">步骤 2：上传客户询价表（Excel）</div>
      <div class="upload-box">
        <el-upload
          ref="excelUploadRef"
          action="#"
          :auto-upload="false"
          :limit="1"
          accept=".xlsx, .xls"
          :on-change="handleExcelChange"
          :on-remove="handleExcelRemove"
        >
          <template #trigger>
            <el-button type="primary" plain>选取 客户询价表.xlsx</el-button>
          </template>
          <template #tip>
            <div class="el-upload__tip">只能上传 1 份标准格式的 Excel 文件</div>
          </template>
        </el-upload>
      </div>

      <!-- 第三步：文件夹图纸拖拽区 -->
      <div class="section-title">步骤 3：上传关联图纸（PDF）</div>
      <div class="upload-box">
        <el-alert 
          title="请将图纸文件夹或多个 PDF 图纸拖入下方区域，系统会自动提取图号，支持 图号.pdf、图号 空格说明.pdf、图号-说明.pdf。" 
          type="success" 
          show-icon 
          class="upload-alert"
        />
        
        <!-- 覆盖原生拖拽事件，接管控制权 -->
        <div 
          class="custom-dropzone" 
          @dragover.prevent 
          @drop.prevent="handleFolderDrop"
        >
          <el-icon class="el-icon--upload dropzone-icon"><upload-filled /></el-icon>
          <div class="el-upload__text dropzone-text">
            将 <em>图纸文件夹</em> 或多个图纸直接拖拽至此处
          </div>
          
          <!-- 备用的点击选文件夹按钮 -->
          <div class="folder-select-row">
             或点击 <el-button size="small" @click="triggerFolderSelect">选择文件夹</el-button>
             <input type="file" ref="folderInput" webkitdirectory directory multiple class="hidden-input" @change="handleFolderSelect" />
          </div>
        </div>

        <!-- 展示挖出来的 PDF 文件列表 -->
        <div v-if="drawingFiles.length > 0" class="file-stats">
          <h4>已成功提取待提交图纸（{{ drawingFiles.length }} 份）</h4>
          <ul class="pdf-list">
            <li v-for="(file, index) in drawingFiles" :key="index">
              <span>{{ file.name }}</span>
              <el-button link type="danger" size="small" @click="removeDrawing(index)">移除</el-button>
            </li>
          </ul>
        </div>
      </div>

      <!-- 底部提交按钮 -->
      <div class="submit-footer">
        <el-button type="success" size="large" @click="submitRfqPackage" :loading="isSubmitting" class="submit-btn">
          确认无误，提交工程部评估
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { API_BASE_URL } from '../config'

const rfqForm = reactive({ customerName: '' })
const excelFile = ref(null)
const drawingFiles = ref([])
const isSubmitting = ref(false)
const folderInput = ref(null)
const excelUploadRef = ref(null)

const handleExcelChange = (uploadFile) => { excelFile.value = uploadFile.raw }
const handleExcelRemove = () => { excelFile.value = null }

// 递归扫描文件夹中的 PDF 文件
const scanFiles = async (item) => {
  if (item.isFile) {
    return new Promise((resolve) => {
      item.file((file) => {
        // 只筛选出 PDF 文件
        if (file.name.toLowerCase().endsWith('.pdf')) {
          resolve(file)
        } else {
          resolve(null)
        }
      })
    })
  } else if (item.isDirectory) {
    const dirReader = item.createReader()
    let allFiles = []
    
    // 读取目录内的条目
    const readEntries = () => {
      return new Promise((resolve) => {
        dirReader.readEntries(async (entries) => {
          if (entries.length === 0) {
            resolve([])
          } else {
            const promises = entries.map(entry => scanFiles(entry))
            const files = await Promise.all(promises)
            // 递归读取以防一次读不完
            const moreFiles = await readEntries() 
            resolve([...files, ...moreFiles])
          }
        })
      })
    }
    return readEntries()
  }
}

// 处理拖拽文件夹事件
const handleFolderDrop = async (e) => {
  const items = e.dataTransfer.items
  let newFiles = []
  
  for (let i = 0; i < items.length; i++) {
    const item = items[i].webkitGetAsEntry()
    if (item) {
      const result = await scanFiles(item)
      // 抹平多维数组，过滤掉 null
      const flatFiles = Array.isArray(result) ? result.flat(Infinity).filter(f => f) : [result].filter(f => f)
      newFiles = [...newFiles, ...flatFiles]
    }
  }
  
  if (newFiles.length > 0) {
    drawingFiles.value = [...drawingFiles.value, ...newFiles]
    ElMessage.success(`成功从文件夹中提取 ${newFiles.length} 份 PDF！`)
  } else {
    ElMessage.warning('未在拖入的内容中找到 PDF 文件！')
  }
}

// 备用方案：点击按钮选择文件夹
const triggerFolderSelect = () => {
  folderInput.value.click()
}
const handleFolderSelect = (e) => {
  const files = Array.from(e.target.files)
  const pdfs = files.filter(f => f.name.toLowerCase().endsWith('.pdf'))
  drawingFiles.value = [...drawingFiles.value, ...pdfs]
  if (pdfs.length > 0) {
    ElMessage.success(`成功从选中的文件夹中提取 ${pdfs.length} 份 PDF！`)
  }
  // 清空 input，允许下次选同一个文件夹
  e.target.value = ''
}

// 移除不需要的图纸
const removeDrawing = (index) => {
  drawingFiles.value.splice(index, 1)
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

// 提交询价单和图纸
const submitRfqPackage = async () => {
  if (!rfqForm.customerName) return ElMessage.warning('请填写客户名称！')
  if (!excelFile.value) return ElMessage.warning('请上传客户询价表 Excel！')

  isSubmitting.value = true
  const formData = new FormData()
  formData.append('customerName', rfqForm.customerName)
  formData.append('excelFile', excelFile.value)

  drawingFiles.value.forEach(file => {
    formData.append('drawings', file)
  })

  try {
    const response = await axios.post(`${API_BASE_URL}/api/market/import-rfq`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    
    if (response.data.success) {
      const drawingReportHtml = buildDrawingReportHtml(response.data.drawingReport)
      ElMessageBox.alert(
        `<div style="font-size: 15px; line-height: 1.5;">${response.data.message}</div>
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
        message: response.data.message,
        duration: 5000, 
        showClose: true
      })
    }
  } catch (error) {
    console.error(error)
    ElMessage.error('网络请求失败，请确保后端服务正常。')
  } finally {
    isSubmitting.value = false
  }
}
</script>

<style scoped>
.market-container {
  padding: 0;
  min-height: auto;
}

.main-card {
  max-width: none;
  margin: 0;
  border: 1px solid var(--erp-border-soft);
  border-radius: 8px;
}

.card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.card-header h2 {
  margin: 0;
  color: var(--erp-text);
  font-size: 20px;
  line-height: 1.3;
}

.card-header p {
  margin: 6px 0 0;
  color: var(--erp-text-secondary);
  font-size: 13px;
}

.section-title {
  margin: 20px 0 10px;
  padding-left: 10px;
  border-left: 3px solid var(--erp-primary);
  color: var(--erp-text);
  font-size: 14px;
  font-weight: 700;
  line-height: 1.2;
}

.form-box,
.upload-box {
  border: 1px solid var(--erp-border-soft);
  border-radius: 8px;
  background: var(--erp-surface);
}

.form-box {
  padding: 16px 18px 2px;
}

.upload-box {
  padding: 16px 18px;
}

.upload-alert {
  margin-bottom: 14px;
}

.upload-box :deep(.el-upload) {
  width: 100%;
}

.upload-box :deep(.el-upload-dragger) {
  width: 100%;
  border-radius: 8px;
  background: var(--erp-surface-soft);
}

/* 自定义拖拽框样式 */
.custom-dropzone {
  border: 1px dashed #aab6c8;
  border-radius: 8px;
  background: var(--erp-surface-soft);
  text-align: center;
  padding: 34px 16px;
  cursor: pointer;
  transition: border-color 0.2s ease, background-color 0.2s ease;
}
.custom-dropzone:hover {
  border-color: var(--erp-primary);
  background: #f3f8fc;
}

.dropzone-icon {
  margin-bottom: 14px;
  color: #8c98aa;
  font-size: 44px;
}

.dropzone-text {
  color: var(--erp-text-secondary);
  font-size: 15px;
}

.folder-select-row {
  margin-top: 14px;
  color: var(--erp-text-secondary);
}

.hidden-input {
  display: none;
}

.file-stats {
  margin-top: 14px;
  padding: 12px 14px;
  border: 1px solid #cfe8d2;
  border-radius: 8px;
  background: #f4fbf5;
  color: var(--erp-success);
}
.pdf-list { list-style: none; padding: 0; margin: 8px 0 0; max-height: 190px; overflow-y: auto; }
.pdf-list li { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 7px 0; border-bottom: 1px solid #dcefe0; color: #256b2b; }
.pdf-list li:last-child { border-bottom: none; }

.submit-footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid var(--erp-border-soft);
}
.submit-btn { width: auto; min-width: 260px; font-size: 14px; font-weight: 700; }

@media (max-width: 900px) {
  .card-header,
  .submit-footer {
    flex-direction: column;
    align-items: stretch;
  }

  .submit-btn {
    width: 100%;
  }
}
</style>
