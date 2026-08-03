<template>
  <div class="upload-box step-panel import-drawing-upload-panel">
    <el-alert
      :title="alertTitle"
      type="info"
      show-icon
      :closable="false"
      class="upload-alert"
    />

    <div
      v-permission="permission"
      class="custom-dropzone"
      @dragover.prevent
      @drop.prevent="handleDrop"
    >
      <el-icon class="el-icon--upload dropzone-icon"><UploadFilled /></el-icon>
      <div class="el-upload__text dropzone-text">
        将 <em>图纸文件夹</em> 或多个 PDF 图纸直接拖拽至此处
      </div>

      <div class="folder-select-row">或点击下方按钮选择多个 PDF / 图纸文件夹</div>
      <div class="import-upload-button-stack">
        <el-button
          v-permission="permission"
          plain
          class="workflow-soft-button folder-select-button import-axis-button"
          :disabled="disabled"
          @click="triggerFileSelect"
        >
          选择多个 PDF
        </el-button>
        <el-button
          v-permission="permission"
          plain
          class="workflow-soft-button folder-select-button import-axis-button"
          :disabled="disabled"
          @click="triggerFolderSelect"
        >
          选择文件夹
        </el-button>
      </div>
      <input type="file" ref="fileInput" accept=".pdf" multiple class="hidden-input" @change="handleFileSelect" />
      <input type="file" ref="folderInput" webkitdirectory directory multiple class="hidden-input" @change="handleFolderSelect" />
    </div>

    <div v-if="files.length > 0" class="file-stats">
      <h4>{{ selectedTitle }}（{{ files.length }} 份）</h4>
      <ul class="pdf-list">
        <li v-for="(file, index) in files" :key="fileKey(file, index)">
          <span>{{ file.name }}</span>
          <el-button
            v-permission="permission"
            link
            type="danger"
            size="small"
            :disabled="disabled"
            @click="removeDrawing(index)"
          >
            移除
          </el-button>
        </li>
      </ul>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { collectPdfFilesFromDataTransfer, mergePdfFiles } from '../../utils/drawingUpload'

const props = defineProps({
  modelValue: {
    type: Array,
    default: () => []
  },
  permission: {
    type: String,
    required: true
  },
  alertTitle: {
    type: String,
    default: '请将图纸文件夹或多个 PDF 图纸拖入下方区域，系统会自动提取图号，支持 图号.pdf、图号 空格说明.pdf、图号-说明.pdf。'
  },
  selectedTitle: {
    type: String,
    default: '已成功提取待提交图纸'
  },
  disabled: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue'])

const fileInput = ref(null)
const folderInput = ref(null)
const files = computed(() => props.modelValue || [])

const updateFiles = (nextFiles) => {
  emit('update:modelValue', nextFiles)
}

const addFiles = (incomingFiles, sourceLabel) => {
  const nextFiles = mergePdfFiles(files.value, incomingFiles)
  const addedCount = nextFiles.length - files.value.length
  updateFiles(nextFiles)
  if (addedCount > 0) {
    ElMessage.success(`成功${sourceLabel} ${addedCount} 份 PDF！`)
  } else {
    ElMessage.warning('未找到新的 PDF 文件！')
  }
}

const handleDrop = async (event) => {
  if (props.disabled) return
  const pdfs = await collectPdfFilesFromDataTransfer(event.dataTransfer)
  addFiles(pdfs, '提取')
}

const triggerFileSelect = () => {
  if (!props.disabled) fileInput.value?.click()
}

const triggerFolderSelect = () => {
  if (!props.disabled) folderInput.value?.click()
}

const handleFileSelect = (event) => {
  addFiles(event.target.files, '选择')
  event.target.value = ''
}

const handleFolderSelect = (event) => {
  addFiles(event.target.files, '从选中的文件夹中提取')
  event.target.value = ''
}

const removeDrawing = (index) => {
  const nextFiles = files.value.slice()
  nextFiles.splice(index, 1)
  updateFiles(nextFiles)
}

const fileKey = (file, index) => `${file?.name || 'pdf'}|${file?.size ?? ''}|${file?.lastModified ?? ''}|${index}`
</script>

<style scoped>
.upload-alert {
  margin-bottom: 16px;
}

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
  font-size: var(--erp-font-size-sm);
  text-align: center;
}

.import-upload-button-stack {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  width: 100%;
  margin-top: 12px;
}

.import-axis-button {
  justify-content: center;
  min-width: 240px;
}

.folder-select-button {
  min-height: 32px;
  padding-inline: 14px;
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

.file-stats h4 {
  margin: 0;
  font-size: 14px;
}

.pdf-list {
  max-height: 190px;
  margin: 8px 0 0;
  padding: 0;
  overflow-y: auto;
  list-style: none;
}

.pdf-list li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 7px 0;
  border-bottom: 1px solid #dcefe0;
  color: #256b2b;
}

.pdf-list li:last-child {
  border-bottom: none;
}
</style>
