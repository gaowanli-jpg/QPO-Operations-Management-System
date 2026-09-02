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

      <section class="append-step erp-dialog-section">
        <div class="section-subtitle">2. 导入图纸</div>
        <div class="append-upload-grid">
          <drawing-upload-column
            title="按图号导入"
            hint="支持 图号.pdf、图号 空格说明.pdf。"
            :files="files"
            :pending="pending"
            :permission="permission"
            @update:files="emit('update:files', $event)"
          />
          <drawing-upload-column
            title="按图号+版本导入"
            hint="支持 图号_版本.pdf、图号_版本 空格说明.pdf。"
            :files="versionFiles"
            :pending="pending"
            :permission="permission"
            @update:files="emit('update:versionFiles', $event)"
          />
        </div>
      </section>

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

const DrawingUploadColumn = defineComponent({
  components: { UploadFilled },
  props: {
    title: { type: String, required: true },
    hint: { type: String, required: true },
    files: { type: Array, default: () => [] },
    pending: { type: Boolean, default: false },
    permission: { type: String, required: true }
  },
  emits: ['update:files'],
  setup(columnProps, { emit: columnEmit }) {
    const fileInput = ref(null)
    const folderInput = ref(null)
    const addFiles = (incomingFiles, sourceLabel) => {
      const nextFiles = mergePdfFiles(columnProps.files, incomingFiles)
      const addedCount = nextFiles.length - columnProps.files.length
      columnEmit('update:files', nextFiles)
      if (addedCount > 0) {
        ElMessage.success(`成功${sourceLabel} ${addedCount} 份 PDF。`)
      } else {
        ElMessage.warning('未找到新的 PDF 图纸。')
      }
    }
    const handleDrop = async (event) => {
      if (columnProps.pending) return
      addFiles(await collectPdfFilesFromDataTransfer(event.dataTransfer), '提取')
    }
    const removeFile = (index) => {
      columnEmit('update:files', columnProps.files.filter((_, currentIndex) => currentIndex !== index))
    }
    return () => h('section', { class: 'append-upload-column' }, [
      h('div', { class: 'append-upload-title' }, columnProps.title),
      h('div', {
        class: 'custom-dropzone append-dropzone',
        onDragover: event => event.preventDefault(),
        onDrop: event => {
          event.preventDefault()
          handleDrop(event)
        }
      }, [
        h(UploadFilled, { class: 'append-upload-icon' }),
        h('div', { class: 'el-upload__text' }, '拖拽 PDF 图纸或图纸文件夹到这里'),
        h('div', { class: 'el-upload__tip' }, columnProps.hint),
        h('div', { class: 'append-upload-actions import-upload-button-stack' }, [
          h('button', { class: 'el-button workflow-soft-button folder-select-button', disabled: columnProps.pending, onClick: () => fileInput.value?.click() }, '选择多个 PDF'),
          h('button', { class: 'el-button workflow-soft-button folder-select-button', disabled: columnProps.pending, onClick: () => folderInput.value?.click() }, '选择文件夹')
        ]),
        h('input', { ref: fileInput, type: 'file', accept: '.pdf', multiple: true, class: 'hidden-input', onChange: event => { addFiles(event.target.files, '选择'); event.target.value = '' } }),
        h('input', { ref: folderInput, type: 'file', webkitdirectory: true, directory: true, multiple: true, class: 'hidden-input', onChange: event => { addFiles(event.target.files, '从选中的文件夹中提取'); event.target.value = '' } })
      ]),
      columnProps.files.length > 0
        ? h('div', { class: 'append-file-stats' }, [
            h('h4', `${columnProps.title}（${columnProps.files.length} 份）`),
            h('ul', { class: 'append-pdf-list' }, columnProps.files.map((file, index) => h('li', { key: `${file.name}-${file.size}-${index}` }, [
              h('span', file.name),
              h('button', { class: 'el-button el-button--danger is-link el-button--small', disabled: columnProps.pending, onClick: () => removeFile(index) }, '移除')
            ])))
          ])
        : null
    ])
  }
})
</script>

<style scoped>
.append-dialog,
.append-result {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.append-descriptions :deep(.el-descriptions__label) {
  color: var(--erp-text-secondary);
  background: #f5f7fb;
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

.append-upload-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.append-upload-column {
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
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  min-height: 250px;
  padding: 28px 16px;
  border: 1px dashed #aab6c8;
  border-radius: 8px;
  background: var(--erp-surface-soft);
  text-align: center;
  box-sizing: border-box;
}

.append-upload-icon {
  width: 42px;
  height: 42px;
  margin-bottom: 12px;
  color: #8c98aa;
}

.append-upload-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
  margin-top: auto;
  padding-top: 12px;
}

.append-upload-actions .el-button {
  margin-left: 0;
}

.folder-select-button {
  min-height: 32px;
}

.hidden-input {
  display: none;
}

.append-file-stats {
  margin-top: 12px;
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
  .append-upload-grid {
    grid-template-columns: 1fr;
  }
}
</style>
