<template>
  <div class="page">
    <el-card shadow="never" class="template-card">
      <template #header>
        <div class="toolbar">
          <div>
            <h2>系统管理：报价单模板</h2>
            <p>维护市场部客户模板报价单，全系统共用一份 .xlsx 模板</p>
          </div>
          <el-button :icon="Refresh" plain :loading="loading" @click="loadStatus">刷新状态</el-button>
        </div>
      </template>

      <el-row :gutter="16">
        <el-col :span="10">
          <div class="panel">
            <div class="panel-title">当前模板</div>
            <el-descriptions :column="1" border>
              <el-descriptions-item label="状态">
                <el-tag :type="templateStatus.exists ? 'success' : 'warning'">
                  {{ templateStatus.exists ? '已上传' : '未上传' }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="文件名">
                {{ templateStatus.fileName || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="更新时间">
                {{ formatDateTime(templateStatus.updatedAt) }}
              </el-descriptions-item>
              <el-descriptions-item label="大小">
                {{ formatSize(templateStatus.size) }}
              </el-descriptions-item>
            </el-descriptions>
          </div>
        </el-col>

        <el-col :span="14">
          <div class="panel">
            <div class="panel-title">上传并替换模板</div>
            <el-alert
              title="模板第 8 行必须包含：序号、图号、物料描述、图纸状态、材料名称、表面处理、版本、数量、单位、含税单价、含税总价、交货周期、备注。"
              type="info"
              show-icon
              :closable="false"
              class="template-alert"
            />
            <el-upload
              ref="uploadRef"
              drag
              :auto-upload="false"
              :limit="1"
              accept=".xlsx"
              :on-change="handleFileChange"
              :on-remove="handleFileRemove"
              :on-exceed="handleExceed"
            >
              <el-icon class="upload-icon"><UploadFilled /></el-icon>
              <div class="el-upload__text">拖入 .xlsx 模板，或点击选择文件</div>
              <template #tip>
                <div class="el-upload__tip">上传成功后会立即替换当前全局报价单模板。</div>
              </template>
            </el-upload>
            <div class="actions">
              <el-button type="primary" :disabled="!selectedFile" :loading="uploading" @click="uploadTemplate">
                上传并替换模板
              </el-button>
            </div>
          </div>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, UploadFilled } from '@element-plus/icons-vue'
import { getQuoteTemplate, saveQuoteTemplate } from '../services/adminApi'

const loading = ref(false)
const uploading = ref(false)
const uploadRef = ref()
const selectedFile = ref(null)
const templateStatus = reactive({
  exists: false,
  fileName: '',
  updatedAt: '',
  size: 0
})

const loadStatus = async () => {
  loading.value = true
  try {
    const response = await getQuoteTemplate()
    Object.assign(templateStatus, {
      exists: !!response?.exists,
      fileName: response?.fileName || '',
      updatedAt: response?.updatedAt || '',
      size: response?.size || 0
    })
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '报价单模板状态加载失败。')
  } finally {
    loading.value = false
  }
}

const handleFileChange = (file) => {
  const name = file?.name || ''
  if (!name.toLowerCase().endsWith('.xlsx')) {
    ElMessage.warning('只能上传 .xlsx 模板文件。')
    uploadRef.value?.clearFiles()
    selectedFile.value = null
    return
  }
  selectedFile.value = file.raw
}

const handleFileRemove = () => {
  selectedFile.value = null
}

const handleExceed = (files) => {
  uploadRef.value?.clearFiles()
  const file = files?.[0]
  if (file) {
    uploadRef.value?.handleStart(file)
  }
}

const uploadTemplate = async () => {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择 .xlsx 模板。')
    return
  }
  const formData = new FormData()
  formData.append('templateFile', selectedFile.value)
  uploading.value = true
  try {
    const response = await saveQuoteTemplate(formData)
    ElMessage.success(response.message || '报价单模板已更新。')
    selectedFile.value = null
    uploadRef.value?.clearFiles()
    await loadStatus()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '报价单模板上传失败。')
  } finally {
    uploading.value = false
  }
}

const formatDateTime = (value) => {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString()
}

const formatSize = (value) => {
  const size = Number(value || 0)
  if (!size) return '-'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

onMounted(loadStatus)
</script>

<style scoped>
.page {
  padding: 0;
}

.template-card {
  border: 1px solid var(--erp-border-soft);
  border-radius: 8px;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.toolbar h2 {
  margin: 0;
  font-size: 20px;
  color: var(--erp-text);
}

.toolbar p {
  margin: 6px 0 0;
  color: var(--erp-text-secondary);
  font-size: 13px;
}

.panel {
  min-height: 280px;
  padding: 14px;
  border: 1px solid var(--erp-border-soft);
  border-radius: 8px;
  background: var(--erp-surface);
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

.template-alert {
  margin-bottom: 14px;
}

.upload-icon {
  font-size: 42px;
  color: var(--erp-primary);
}

.actions {
  margin-top: 14px;
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 980px) {
  .page :deep(.el-row) {
    display: block;
  }

  .page :deep(.el-col) {
    max-width: 100%;
    width: 100%;
    margin-bottom: 14px;
  }
}
</style>
