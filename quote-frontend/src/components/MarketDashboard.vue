<template>
  <div class="market-container">
    <el-card class="main-card">
      <template #header>
        <div class="card-header">
          <h2>💼 市场部：新询价单导入与智能分流中心</h2>
        </div>
      </template>

      <!-- 第一步：客户与主单信息 -->
      <div class="section-title">📌 Step 1: 客户基础信息</div>
      <el-form :model="rfqForm" label-width="120px" class="form-box">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="客户名称/代码" required>
              <el-input v-model="rfqForm.customerName" placeholder="例如：Tesla-01 或 华为" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="项目名称">
              <el-input v-model="rfqForm.projectName" placeholder="例如：2026年Q2底盘件批次" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <!-- 第二步：上传 Excel BOM 表 -->
      <div class="section-title">📊 Step 2: 上传客户询价表 (Excel)</div>
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
      <div class="section-title">📁 Step 3: 上传关联图纸 (PDF)</div>
      <div class="upload-box">
        <el-alert 
          title="请将图纸文件夹或多个 PDF 图纸拖入下方区域，系统会自动提取 PDF 文件。" 
          type="success" 
          show-icon 
          style="margin-bottom: 15px;" 
        />
        
        <!-- 覆盖原生拖拽事件，接管控制权 -->
        <div 
          class="custom-dropzone" 
          @dragover.prevent 
          @drop.prevent="handleFolderDrop"
        >
          <el-icon class="el-icon--upload" style="font-size: 48px; color: #8c939d; margin-bottom: 15px;"><upload-filled /></el-icon>
          <div class="el-upload__text" style="font-size: 16px; color: #606266;">
            将 <em>图纸文件夹</em> 或多个图纸直接拖拽至此处
          </div>
          
          <!-- 备用的点击选文件夹按钮 -->
          <div style="margin-top: 15px;">
             或点击 <el-button size="small" @click="triggerFolderSelect">选择文件夹</el-button>
             <input type="file" ref="folderInput" webkitdirectory directory multiple style="display: none;" @change="handleFolderSelect" />
          </div>
        </div>

        <!-- 展示挖出来的 PDF 文件列表 -->
        <div v-if="drawingFiles.length > 0" class="file-stats">
          <h4>✅ 已成功提取待提交图纸 ({{ drawingFiles.length }} 份)：</h4>
          <ul class="pdf-list">
            <li v-for="(file, index) in drawingFiles" :key="index">
              📄 {{ file.name }}
              <el-button link type="danger" size="small" @click="removeDrawing(index)" style="float: right;">移除</el-button>
            </li>
          </ul>
        </div>
      </div>

      <!-- 底部提交按钮 -->
      <div class="submit-footer">
        <el-button type="success" size="large" @click="submitRfqPackage" :loading="isSubmitting" class="submit-btn">
          🚀 确认无误，打包提交工程部评估
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

const rfqForm = reactive({ customerName: '', projectName: '' })
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
  formData.append('projectName', rfqForm.projectName)
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
        rfqForm.projectName = ''
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
.market-container { padding: 40px; background-color: #f0f2f5; min-height: 100vh; }
.main-card { max-width: 1000px; margin: 0 auto; border-radius: 12px; box-shadow: 0 8px 24px rgba(0,0,0,0.05); }
.section-title { font-size: 16px; font-weight: bold; color: #409EFF; border-bottom: 1px solid #ebeef5; padding-bottom: 8px; margin-bottom: 20px; margin-top: 20px; }
.form-box { background-color: #fafafa; padding: 20px 20px 0 20px; border-radius: 8px; border: 1px solid #ebeef5; }
.upload-box { background-color: #fff; padding: 20px; border-radius: 8px; border: 1px dashed #dcdfe6; }

/* 自定义拖拽框样式 */
.custom-dropzone {
  border: 2px dashed #dcdfe6;
  border-radius: 6px;
  background-color: #fafafa;
  text-align: center;
  padding: 40px 0;
  cursor: pointer;
  transition: border-color 0.3s;
}
.custom-dropzone:hover {
  border-color: #409eff;
}

.file-stats { margin-top: 20px; background: #f0f9eb; padding: 15px; border-radius: 6px; border: 1px solid #e1f3d8;}
.pdf-list { list-style: none; padding: 0; margin: 0; max-height: 200px; overflow-y: auto; }
.pdf-list li { padding: 8px; border-bottom: 1px dashed #e1f3d8; color: #67C23A; }
.pdf-list li:last-child { border-bottom: none; }

.submit-footer { text-align: center; margin-top: 40px; padding-top: 20px; border-top: 1px solid #ebeef5; }
.submit-btn { width: 300px; font-size: 16px; font-weight: bold; }
</style>
