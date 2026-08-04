<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    width="720px"
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="supplement-dialog">
      <el-descriptions :column="1" border size="small" class="erp-dialog-section supplement-descriptions">
        <el-descriptions-item label="订单">{{ orderLabel || '-' }}</el-descriptions-item>
        <el-descriptions-item label="客户">{{ customerName || '-' }}</el-descriptions-item>
      </el-descriptions>

      <div class="erp-dialog-section missing-list">
        <div class="section-subtitle">待补图零件</div>
        <el-table :data="missingItems" border stripe height="180">
          <el-table-column prop="partNo" label="图号" min-width="140" show-overflow-tooltip />
          <el-table-column prop="version" label="版本" width="80" align="center" />
          <el-table-column prop="description" label="物料描述" min-width="180" show-overflow-tooltip />
          <el-table-column prop="qty" label="数量" width="70" align="center" />
        </el-table>
      </div>

      <div class="erp-upload-surface upload-area">
        <div class="supplement-upload-dual-grid">
          <section class="supplement-upload-entry">
            <div class="supplement-upload-title">按图号补传</div>
            <div
              v-permission="permission"
              class="custom-dropzone supplement-dropzone"
              @dragover.prevent
              @drop.prevent="handleDrop"
            >
              <el-icon class="el-icon--upload"><upload-filled /></el-icon>
              <div class="el-upload__text">拖拽 PDF 图纸或图纸文件夹到这里</div>
              <div class="el-upload__tip">支持 图号.pdf、图号 空格说明.pdf、图号-说明.pdf，例如 L20100000733.pdf 或 L20100000733-定位块.pdf。</div>
              <div class="folder-select-row">或点击下方按钮选择多个 PDF / 图纸文件夹</div>
              <div class="supplement-upload-actions import-upload-button-stack">
                <el-button v-permission="permission" plain class="workflow-soft-button folder-select-button" :disabled="pending" @click="triggerFileSelect">选择多个 PDF</el-button>
                <el-button v-permission="permission" plain class="workflow-soft-button folder-select-button" :disabled="pending" @click="triggerFolderSelect">选择文件夹</el-button>
              </div>
              <input ref="fileInput" type="file" accept=".pdf" multiple class="hidden-input" @change="handleFileSelect" />
              <input ref="folderInput" type="file" webkitdirectory directory multiple class="hidden-input" @change="handleFolderSelect" />
            </div>

            <div v-if="files.length > 0" class="supplement-file-stats">
              <h4>按图号待补传图纸（{{ files.length }} 份）</h4>
              <ul class="supplement-pdf-list">
                <li v-for="(file, index) in files" :key="`${file.name}-${file.size}-${index}`">
                  <span>{{ file.name }}</span>
                  <el-button v-permission="permission" link type="danger" size="small" :disabled="pending" @click="removeFile(index)">移除</el-button>
                </li>
              </ul>
            </div>
          </section>

          <section class="supplement-upload-entry">
            <div class="supplement-upload-title">按图号+版本补传</div>
            <div
              v-permission="permission"
              class="custom-dropzone supplement-dropzone"
              @dragover.prevent
              @drop.prevent="handleVersionDrop"
            >
              <el-icon class="el-icon--upload"><upload-filled /></el-icon>
              <div class="el-upload__text">拖拽 PDF 图纸或图纸文件夹到这里</div>
              <div class="el-upload__tip">支持 图号_版本.pdf、图号_版本 空格说明.pdf、图号_版本-说明.pdf，例如 L20100000733_A.pdf。</div>
              <div class="folder-select-row">或点击下方按钮选择多个 PDF / 图纸文件夹</div>
              <div class="supplement-upload-actions import-upload-button-stack">
                <el-button v-permission="permission" plain class="workflow-soft-button folder-select-button" :disabled="pending" @click="triggerVersionFileSelect">选择多个 PDF</el-button>
                <el-button v-permission="permission" plain class="workflow-soft-button folder-select-button" :disabled="pending" @click="triggerVersionFolderSelect">选择文件夹</el-button>
              </div>
              <input ref="versionFileInput" type="file" accept=".pdf" multiple class="hidden-input" @change="handleVersionFileSelect" />
              <input ref="versionFolderInput" type="file" webkitdirectory directory multiple class="hidden-input" @change="handleVersionFolderSelect" />
            </div>

            <div v-if="versionFiles.length > 0" class="supplement-file-stats">
              <h4>按图号+版本待补传图纸（{{ versionFiles.length }} 份）</h4>
              <ul class="supplement-pdf-list">
                <li v-for="(file, index) in versionFiles" :key="`${file.name}-${file.size}-${index}`">
                  <span>{{ file.name }}</span>
                  <el-button v-permission="permission" link type="danger" size="small" :disabled="pending" @click="removeVersionFile(index)">移除</el-button>
                </li>
              </ul>
            </div>
          </section>
        </div>
      </div>

      <div v-if="result" class="supplement-result">
        <el-alert :title="result.message" type="success" :closable="false" show-icon />
        <el-tabs>
          <el-tab-pane :label="`已绑定 ${result.boundCount || 0}`">
            <result-list :items="result.boundItems" />
          </el-tab-pane>
          <el-tab-pane :label="`已跳过 ${result.alreadyBoundCount || 0}`">
            <result-list :items="result.alreadyBoundItems" />
          </el-tab-pane>
          <el-tab-pane :label="`未匹配 ${result.unmatchedCount || 0}`">
            <result-list :items="result.unmatchedDrawings" />
          </el-tab-pane>
          <el-tab-pane :label="`非PDF ${result.skippedCount || 0}`">
            <result-list :items="result.skippedFiles" />
          </el-tab-pane>
        </el-tabs>
      </div>
    </div>

    <template #footer>
      <div class="erp-dialog-footer">
        <el-button @click="emit('update:modelValue', false)">关闭</el-button>
        <el-button
          v-permission="permission"
          type="primary"
          :loading="pending"
          :disabled="(files.length === 0 && versionFiles.length === 0) || pending"
          @click="emit('submit')"
        >
          {{ submitText }}
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { defineComponent, h, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { collectPdfFilesFromDataTransfer, mergePdfFiles } from '../../utils/drawingUpload'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: '补传图纸' },
  orderLabel: { type: String, default: '-' },
  customerName: { type: String, default: '-' },
  missingItems: { type: Array, default: () => [] },
  files: { type: Array, default: () => [] },
  versionFiles: { type: Array, default: () => [] },
  result: { type: Object, default: null },
  pending: { type: Boolean, default: false },
  permission: { type: String, required: true },
  submitText: { type: String, default: '上传并匹配' }
})

const emit = defineEmits(['update:modelValue', 'update:files', 'update:versionFiles', 'submit'])
const fileInput = ref(null)
const folderInput = ref(null)
const versionFileInput = ref(null)
const versionFolderInput = ref(null)

const ResultList = defineComponent({
  props: {
    items: {
      type: Array,
      default: () => []
    }
  },
  setup(listProps) {
    return () => listProps.items.length
      ? h('ul', { class: 'result-list' }, listProps.items.map((item, index) => h('li', { key: index }, [
          h('span', { class: 'result-main' }, item.partNo ? `${item.partNo} / ${item.version || '-'}` : (item.fileName || '-')),
          item.description ? h('span', { class: 'result-sub' }, ` | ${item.description}`) : null,
          item.fileName && item.partNo ? h('span', { class: 'result-sub' }, ` | ${item.fileName}`) : null,
          item.reason ? h('span', { class: 'result-reason' }, ` | ${item.reason}`) : null
        ])))
      : h('el-empty', { description: '无记录', imageSize: 60 })
  }
})

const addFiles = (incomingFiles, sourceLabel, mode = 'part') => {
  const currentFiles = mode === 'version' ? props.versionFiles : props.files
  const mergedFiles = mergePdfFiles(currentFiles, incomingFiles)
  const addedCount = mergedFiles.length - currentFiles.length
  emit(mode === 'version' ? 'update:versionFiles' : 'update:files', mergedFiles)
  if (addedCount > 0) {
    ElMessage.success(`成功${sourceLabel} ${addedCount} 份 PDF。`)
  } else {
    ElMessage.warning('未找到新的 PDF 图纸。')
  }
}

const handleDrop = async (event) => {
  if (props.pending) return
  const pdfs = await collectPdfFilesFromDataTransfer(event.dataTransfer)
  addFiles(pdfs, '提取')
}

const handleVersionDrop = async (event) => {
  if (props.pending) return
  const pdfs = await collectPdfFilesFromDataTransfer(event.dataTransfer)
  addFiles(pdfs, '提取', 'version')
}

const triggerFileSelect = () => {
  if (!props.pending) fileInput.value?.click()
}

const triggerFolderSelect = () => {
  if (!props.pending) folderInput.value?.click()
}

const triggerVersionFileSelect = () => {
  if (!props.pending) versionFileInput.value?.click()
}

const triggerVersionFolderSelect = () => {
  if (!props.pending) versionFolderInput.value?.click()
}

const handleFileSelect = (event) => {
  addFiles(event.target.files, '选择')
  event.target.value = ''
}

const handleFolderSelect = (event) => {
  addFiles(event.target.files, '从选中的文件夹中提取')
  event.target.value = ''
}

const handleVersionFileSelect = (event) => {
  addFiles(event.target.files, '选择', 'version')
  event.target.value = ''
}

const handleVersionFolderSelect = (event) => {
  addFiles(event.target.files, '从选中的文件夹中提取', 'version')
  event.target.value = ''
}

const removeFile = (index) => {
  emit('update:files', props.files.filter((_, currentIndex) => currentIndex !== index))
}

const removeVersionFile = (index) => {
  emit('update:versionFiles', props.versionFiles.filter((_, currentIndex) => currentIndex !== index))
}
</script>

<style scoped>
.supplement-dialog {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.supplement-descriptions :deep(.el-descriptions__label) {
  color: var(--erp-text-secondary);
  background: #f5f7fb;
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

.supplement-dropzone :deep(.el-icon),
.supplement-dropzone .el-icon {
  margin-bottom: 12px;
  color: #8c98aa;
  font-size: 42px;
}

.supplement-dropzone {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  min-height: 280px;
  box-sizing: border-box;
}

.supplement-dropzone :deep(.el-upload__tip) {
  min-height: 48px;
  margin-top: 8px;
  color: var(--erp-text-secondary);
}

.folder-select-row {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 38px;
  margin-top: 12px;
  color: var(--erp-text-secondary);
  font-size: var(--erp-font-size-sm);
}

.supplement-upload-dual-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  align-items: stretch;
  gap: 14px;
}

.supplement-upload-entry {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.supplement-upload-title {
  min-height: 24px;
  display: flex;
  align-items: center;
  margin-bottom: 8px;
  padding-left: 10px;
  border-left: 3px solid var(--erp-primary);
  color: var(--erp-text);
  font-size: 14px;
  font-weight: 700;
}

.supplement-upload-actions {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  justify-content: center;
  gap: 10px;
  width: 100%;
  margin-top: auto;
  padding-top: 12px;
}

.supplement-upload-actions :deep(.el-button) {
  margin-left: 0;
}

.import-upload-button-stack {
  flex-direction: column;
}

.folder-select-button {
  width: 100%;
  min-height: 32px;
  min-width: 150px;
  padding-inline: 14px;
}

.hidden-input {
  display: none;
}

.supplement-file-stats {
  padding: 12px 14px;
  border: 1px solid #cfe8d2;
  border-radius: 8px;
  background: #f4fbf5;
  color: var(--erp-success);
}

.supplement-file-stats h4 {
  margin: 0 0 8px;
  font-size: 14px;
}

.supplement-pdf-list {
  list-style: none;
  padding: 0;
  margin: 0;
  max-height: 150px;
  overflow-y: auto;
}

.supplement-pdf-list li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 7px 0;
  border-bottom: 1px solid #dcefe0;
  color: #256b2b;
}

.supplement-pdf-list li:last-child {
  border-bottom: none;
}

.supplement-result {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.result-list {
  list-style: none;
  padding: 0;
  margin: 0;
  max-height: 150px;
  overflow: auto;
  border: 1px solid var(--erp-border-soft);
  border-radius: 6px;
  background: var(--erp-surface-soft);
}

.result-list li {
  padding: 7px 10px;
  border-bottom: 1px solid var(--erp-border-soft);
  color: var(--erp-text);
}

.result-list li:last-child {
  border-bottom: none;
}

.result-main {
  font-weight: 700;
}

.result-sub {
  color: var(--erp-text-secondary);
}

.result-reason {
  color: var(--erp-warning);
}

@media (max-width: 760px) {
  .supplement-upload-dual-grid {
    grid-template-columns: 1fr;
  }
}
</style>
