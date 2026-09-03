<template>
  <el-dialog
    :model-value="modelValue"
    title="新增零件"
    width="900px"
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="append-dialog">
      <el-descriptions :column="1" border size="small" class="erp-dialog-section append-descriptions">
        <el-descriptions-item label="订单">{{ orderLabel || '-' }}</el-descriptions-item>
        <el-descriptions-item label="客户">{{ customerName || '-' }}</el-descriptions-item>
      </el-descriptions>

      <section class="erp-dialog-section append-current-items">
        <div class="section-subtitle">当前订单零件</div>
        <el-table :data="currentItems" border stripe height="180">
          <el-table-column prop="partNo" label="图号" min-width="140" show-overflow-tooltip />
          <el-table-column prop="version" label="版本" width="80" align="center" />
          <el-table-column prop="description" label="物料描述" min-width="180" show-overflow-tooltip />
          <el-table-column prop="qty" label="数量" width="70" align="center" />
        </el-table>
      </section>

      <section class="append-step erp-dialog-section">
        <div class="section-subtitle">1. 导入新增零件询价表</div>
        <div class="excel-picker">
          <el-button v-permission="permission" plain class="workflow-soft-button" :disabled="pending" @click="triggerExcelSelect">
            选择询价表
          </el-button>
          <span class="excel-file-name">{{ excelFile?.name || '支持 .xlsx / .xls' }}</span>
          <el-button v-if="excelFile" link type="danger" :disabled="pending" @click="emit('update:excelFile', null)">移除</el-button>
          <input ref="excelInput" type="file" accept=".xlsx,.xls" class="hidden-input" @change="handleExcelSelect" />
        </div>
      </section>

      <div class="erp-upload-surface append-upload-surface">
        <div class="append-upload-dual-grid">
          <section class="append-upload-entry">
            <div class="append-upload-title">按图号导入</div>
            <div
              v-permission="permission"
              class="custom-dropzone append-dropzone"
              @dragover.prevent
              @drop.prevent="handlePartDrop"
            >
              <el-icon class="el-icon--upload append-upload-icon"><upload-filled /></el-icon>
              <div class="el-upload__text">拖拽 PDF 图纸或图纸文件夹到这里</div>
              <div class="el-upload__tip">支持 图号.pdf、图号 空格说明.pdf。</div>
              <div class="folder-select-row">或点击下方按钮选择多个 PDF / 图纸文件夹</div>
              <div class="append-upload-actions import-upload-button-stack">
                <el-button v-permission="permission" plain class="workflow-soft-button folder-select-button" :disabled="pending" @click="triggerPartFileSelect">选择多个 PDF</el-button>
                <el-button v-permission="permission" plain class="workflow-soft-button folder-select-button" :disabled="pending" @click="triggerPartFolderSelect">选择文件夹</el-button>
              </div>
              <input ref="partFileInput" type="file" accept=".pdf" multiple class="hidden-input" style="display: none" @change="handlePartFileSelect" />
              <input ref="partFolderInput" type="file" webkitdirectory directory multiple class="hidden-input" style="display: none" @change="handlePartFolderSelect" />
            </div>

            <div v-if="files.length > 0" class="append-file-stats">
              <h4>按图号待导入图纸（{{ files.length }} 份）</h4>
              <ul class="append-pdf-list">
                <li v-for="(file, index) in files" :key="`${file.name}-${file.size}-${index}`">
                  <span>{{ file.name }}</span>
                  <el-button v-permission="permission" link type="danger" size="small" :disabled="pending" @click="removeFile(index)">移除</el-button>
                </li>
              </ul>
            </div>
          </section>

          <section class="append-upload-entry">
            <div class="append-upload-title">按图号+版本导入</div>
            <div
              v-permission="permission"
              class="custom-dropzone append-dropzone"
              @dragover.prevent
              @drop.prevent="handleVersionDrop"
            >
              <el-icon class="el-icon--upload append-upload-icon"><upload-filled /></el-icon>
              <div class="el-upload__text">拖拽 PDF 图纸或图纸文件夹到这里</div>
              <div class="el-upload__tip">支持 图号_版本.pdf、图号_版本 空格说明.pdf。</div>
              <div class="folder-select-row">或点击下方按钮选择多个 PDF / 图纸文件夹</div>
              <div class="append-upload-actions import-upload-button-stack">
                <el-button v-permission="permission" plain class="workflow-soft-button folder-select-button" :disabled="pending" @click="triggerVersionFileSelect">选择多个 PDF</el-button>
                <el-button v-permission="permission" plain class="workflow-soft-button folder-select-button" :disabled="pending" @click="triggerVersionFolderSelect">选择文件夹</el-button>
              </div>
              <input ref="versionFileInput" type="file" accept=".pdf" multiple class="hidden-input" style="display: none" @change="handleVersionFileSelect" />
              <input ref="versionFolderInput" type="file" webkitdirectory directory multiple class="hidden-input" style="display: none" @change="handleVersionFolderSelect" />
            </div>

            <div v-if="versionFiles.length > 0" class="append-file-stats">
              <h4>按图号+版本待导入图纸（{{ versionFiles.length }} 份）</h4>
              <ul class="append-pdf-list">
                <li v-for="(file, index) in versionFiles" :key="`${file.name}-${file.size}-${index}`">
                  <span>{{ file.name }}</span>
                  <el-button v-permission="permission" link type="danger" size="small" :disabled="pending" @click="removeVersionFile(index)">移除</el-button>
                </li>
              </ul>
            </div>
          </section>
        </div>
      </div>

      <section v-if="result" class="append-result erp-dialog-section">
        <el-alert :title="result.message" type="success" :closable="false" show-icon />
        <el-tabs>
          <el-tab-pane :label="`已绑定 ${result.boundCount || 0}`"><result-list :items="result.boundItems" /></el-tab-pane>
          <el-tab-pane :label="`缺图 ${result.missingDrawingCount || 0}`"><result-list :items="result.missingItems" /></el-tab-pane>
          <el-tab-pane :label="`未匹配 ${result.unmatchedCount || 0}`"><result-list :items="result.unmatchedDrawings" /></el-tab-pane>
          <el-tab-pane :label="`非PDF ${result.skippedCount || 0}`"><result-list :items="result.skippedFiles" /></el-tab-pane>
        </el-tabs>
      </section>
    </div>

    <template #footer>
      <div class="erp-dialog-footer">
        <el-button @click="emit('update:modelValue', false)">关闭</el-button>
        <el-button
          v-permission="permission"
          type="primary"
          :loading="pending"
          :disabled="!excelFile || pending"
          @click="emit('submit')"
        >
          导入新增零件
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
  excelFile: { type: Object, default: null },
  currentItems: { type: Array, default: () => [] },
  files: { type: Array, default: () => [] },
  versionFiles: { type: Array, default: () => [] },
  result: { type: Object, default: null },
  pending: { type: Boolean, default: false },
  permission: { type: String, required: true },
  orderLabel: { type: String, default: '-' },
  customerName: { type: String, default: '-' }
})

const emit = defineEmits([
  'update:modelValue',
  'update:excelFile',
  'update:files',
  'update:versionFiles',
  'submit'
])

const excelInput = ref(null)
const partFileInput = ref(null)
const partFolderInput = ref(null)
const versionFileInput = ref(null)
const versionFolderInput = ref(null)

const triggerExcelSelect = () => {
  if (!props.pending) excelInput.value?.click()
}

const handleExcelSelect = (event) => {
  const file = event.target.files?.[0] || null
  if (file && !/\.(xlsx|xls)$/i.test(file.name || '')) {
    ElMessage.warning('新增零件询价表仅支持 .xlsx 或 .xls 文件。')
    event.target.value = ''
    return
  }
  emit('update:excelFile', file)
  event.target.value = ''
}

const ResultList = defineComponent({
  props: { items: { type: Array, default: () => [] } },
  setup(listProps) {
    return () => listProps.items?.length
      ? h('ul', { class: 'append-result-list' }, listProps.items.map((item, index) => h('li', { key: index }, [
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
  const nextFiles = mergePdfFiles(currentFiles, incomingFiles)
  const addedCount = nextFiles.length - currentFiles.length
  emit(mode === 'version' ? 'update:versionFiles' : 'update:files', nextFiles)
  if (addedCount > 0) {
    ElMessage.success(`成功${sourceLabel} ${addedCount} 份 PDF。`)
  } else {
    ElMessage.warning('未找到新的 PDF 图纸。')
  }
}

const handlePartDrop = async (event) => {
  if (props.pending) return
  addFiles(await collectPdfFilesFromDataTransfer(event.dataTransfer), '提取')
}

const handleVersionDrop = async (event) => {
  if (props.pending) return
  addFiles(await collectPdfFilesFromDataTransfer(event.dataTransfer), '提取', 'version')
}

const triggerPartFileSelect = () => {
  if (!props.pending) partFileInput.value?.click()
}

const triggerPartFolderSelect = () => {
  if (!props.pending) partFolderInput.value?.click()
}

const triggerVersionFileSelect = () => {
  if (!props.pending) versionFileInput.value?.click()
}

const triggerVersionFolderSelect = () => {
  if (!props.pending) versionFolderInput.value?.click()
}

const handlePartFileSelect = (event) => {
  addFiles(event.target.files, '选择')
  event.target.value = ''
}

const handlePartFolderSelect = (event) => {
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
.append-dialog,
.append-result {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.append-dialog :deep(.hidden-input) {
  display: none;
}

.append-descriptions :deep(.el-descriptions__label) {
  color: var(--erp-text-secondary);
  background: #f5f7fb;
}

.append-current-items {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.append-step {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.excel-picker {
  display: flex;
  align-items: center;
  gap: 12px;
}

.excel-file-name {
  color: var(--erp-text-secondary);
}

.append-upload-dual-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  align-items: stretch;
  gap: 14px;
}

.append-upload-entry {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.append-upload-title {
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

.append-dropzone {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  min-height: 280px;
  box-sizing: border-box;
}

.append-upload-icon {
  margin-bottom: 12px;
  color: #8c98aa;
  font-size: 42px;
}

.append-dropzone :deep(.el-upload__tip) {
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
  text-align: center;
}

.append-upload-actions {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  justify-content: center;
  gap: 10px;
  width: 100%;
  margin-top: auto;
  padding-top: 12px;
}

.append-upload-actions :deep(.el-button) {
  margin-left: 0;
}

.folder-select-button {
  width: 100%;
  min-height: 32px;
  min-width: 150px;
  padding-inline: 14px;
}

.append-file-stats {
  padding: 12px 14px;
  border: 1px solid #cfe8d2;
  border-radius: 8px;
  background: #f4fbf5;
  color: var(--erp-success);
}

.append-file-stats h4 {
  margin: 0 0 8px;
  font-size: 14px;
}

.append-pdf-list,
.append-result-list {
  list-style: none;
  padding: 0;
  margin: 0;
  max-height: 150px;
  overflow-y: auto;
}

.append-pdf-list li,
.append-result-list li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 7px 0;
  border-bottom: 1px solid var(--erp-border-soft);
}

.append-pdf-list li:last-child,
.append-result-list li:last-child {
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
  .append-upload-dual-grid {
    grid-template-columns: 1fr;
  }
}
</style>
