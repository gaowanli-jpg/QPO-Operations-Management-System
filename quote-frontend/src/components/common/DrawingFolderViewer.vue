<template>
  <el-dialog
    :model-value="modelValue"
    :title="viewerTitle"
    width="760px"
    top="6vh"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="drawing-folder-viewer">
      <el-alert
        title="该零件绑定的是订单图纸文件夹，请在下方选择具体 PDF 预览或下载。"
        type="info"
        show-icon
        :closable="false"
      />

      <el-table
        :data="files"
        border
        stripe
        height="360"
        v-loading="loading"
        empty-text="暂无图纸文件"
        class="drawing-folder-table"
      >
        <el-table-column prop="fileName" label="文件名" min-width="240" show-overflow-tooltip />
        <el-table-column label="大小" width="120" align="right">
          <template #default="{ row }">{{ formatSize(row.sizeBytes) }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="上传时间" width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.createdAt || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center" fixed="right">
          <template #default="{ row }">
            <div class="erp-table-actions folder-file-actions">
              <el-button type="primary" plain size="small" @click="previewFile(row)">预览</el-button>
              <el-button plain size="small" @click="downloadDrawingFile(row)">下载</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <el-alert
        v-if="error"
        :title="error"
        type="error"
        show-icon
        :closable="false"
      />
    </div>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { downloadFile, listDrawingFolderFiles, openAuthenticatedFileInNewWindow } from '../../api'
import { readActionError } from '../../utils/actionError'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  folderUrl: { type: String, default: '' },
  title: { type: String, default: '' }
})

const emit = defineEmits(['update:modelValue'])

const files = ref([])
const loading = ref(false)
const error = ref('')
let requestSeq = 0

const viewerTitle = computed(() => props.title || '图纸文件夹')

const loadFiles = async () => {
  const folderUrl = props.folderUrl
  if (!props.modelValue || !folderUrl) {
    files.value = []
    error.value = ''
    return
  }
  const seq = ++requestSeq
  loading.value = true
  error.value = ''
  try {
    const response = await listDrawingFolderFiles(folderUrl)
    if (seq !== requestSeq) return
    files.value = response?.files || []
  } catch (loadError) {
    if (seq !== requestSeq) return
    error.value = readActionError(loadError, '图纸文件夹加载失败。')
    files.value = []
  } finally {
    if (seq === requestSeq) {
      loading.value = false
    }
  }
}

const filePath = (row) => row?.publicPath || row?.url || ''

const previewFile = async (row) => {
  const path = filePath(row)
  if (!path) return ElMessage.warning('当前文件缺少访问地址。')
  const result = await openAuthenticatedFileInNewWindow(path)
  if (!result.success) {
    ElMessage.error(result.blocked ? '浏览器阻止了预览窗口，请允许弹窗后重试。' : (result.message || '图纸预览失败。'))
  }
}

const downloadDrawingFile = async (row) => {
  const path = filePath(row)
  if (!path) return ElMessage.warning('当前文件缺少下载地址。')
  const result = await downloadFile(path, row?.fileName || 'drawing.pdf')
  if (!result.success) {
    ElMessage.error(result.message || '图纸下载失败。')
  }
}

const formatSize = (value) => {
  const bytes = Number(value)
  if (!Number.isFinite(bytes) || bytes < 0) return '-'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

watch(
  () => [props.modelValue, props.folderUrl],
  loadFiles,
  { immediate: true }
)
</script>

<style scoped>
.drawing-folder-viewer {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.drawing-folder-table {
  width: 100%;
}

.folder-file-actions {
  justify-content: center;
  gap: 8px;
}

.folder-file-actions :deep(.el-button) {
  margin-left: 0;
}
</style>
