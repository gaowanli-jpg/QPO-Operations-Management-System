<template>
  <div class="drawing-folder-file-browser" :class="{ 'is-compact': compact }">
    <el-alert
      v-if="showNotice"
      title="该零件绑定的是订单图纸文件夹，请在下方选择具体 PDF 预览或下载。"
      type="info"
      show-icon
      :closable="false"
    />

    <div class="drawing-folder-toolbar">
      <el-input
        v-model="searchKeyword"
        placeholder="按文件名搜索"
        clearable
        class="drawing-folder-search"
        @input="scheduleSearch"
        @clear="handleSearchClear"
      />
    </div>

    <el-table
      :data="files"
      border
      stripe
      :height="tableHeight"
      v-loading="loading"
      :empty-text="emptyText"
      class="drawing-folder-table"
    >
      <el-table-column prop="fileName" label="文件名" min-width="180" show-overflow-tooltip />
      <el-table-column label="大小" width="96" align="right">
        <template #default="{ row }">{{ formatSize(row.sizeBytes) }}</template>
      </el-table-column>
      <el-table-column v-if="!compact" prop="createdAt" label="上传时间" width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ row.createdAt || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" :width="compact ? 132 : 150" align="center" fixed="right">
        <template #default="{ row }">
          <div class="erp-table-actions folder-file-actions">
            <el-button type="primary" plain size="small" @click="previewFile(row)">预览</el-button>
            <el-button v-if="showDownload" plain size="small" @click="downloadDrawingFile(row)">下载</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <div v-if="total > 0" class="erp-pagination drawing-folder-pagination">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :page-sizes="[20, 50, 100]"
        :total="total"
        :pager-count="7"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @current-change="handlePageChange"
        @size-change="handlePageSizeChange"
      />
    </div>

    <el-alert
      v-if="error"
      :title="error"
      type="error"
      show-icon
      :closable="false"
    />
  </div>
</template>

<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { downloadFile, listDrawingFolderFiles, openAuthenticatedFileInNewWindow } from '../../api'
import { readActionError } from '../../utils/actionError'

const props = defineProps({
  folderUrl: { type: String, default: '' },
  active: { type: Boolean, default: true },
  compact: { type: Boolean, default: false },
  showNotice: { type: Boolean, default: false },
  showDownload: { type: Boolean, default: true },
  previewBehavior: { type: String, default: 'window' },
  tableHeight: { type: [Number, String], default: 360 },
  emptyText: { type: String, default: '暂无图纸文件' }
})

const emit = defineEmits(['preview'])

const files = ref([])
const loading = ref(false)
const error = ref('')
const searchKeyword = ref('')
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
let requestSeq = 0
let searchTimer = null

const loadFiles = async () => {
  const folderUrl = props.folderUrl
  if (!props.active || !folderUrl) {
    files.value = []
    total.value = 0
    error.value = ''
    return
  }
  const seq = ++requestSeq
  loading.value = true
  error.value = ''
  try {
    const response = await listDrawingFolderFiles(folderUrl, {
      keyword: searchKeyword.value.trim(),
      page: page.value,
      pageSize: pageSize.value
    })
    if (seq !== requestSeq) return
    const records = response?.records || response?.files || []
    files.value = records
    total.value = Number(response?.total ?? records.length) || 0
    page.value = Number(response?.page ?? page.value) || page.value
    pageSize.value = Number(response?.pageSize ?? pageSize.value) || pageSize.value
  } catch (loadError) {
    if (seq !== requestSeq) return
    error.value = readActionError(loadError, '图纸文件夹加载失败。')
  } finally {
    if (seq === requestSeq) {
      loading.value = false
    }
  }
}

const clearSearchTimer = () => {
  if (searchTimer) {
    clearTimeout(searchTimer)
    searchTimer = null
  }
}

const scheduleSearch = () => {
  clearSearchTimer()
  searchTimer = setTimeout(() => {
    page.value = 1
    loadFiles()
  }, 300)
}

const handleSearchClear = () => {
  page.value = 1
  clearSearchTimer()
  loadFiles()
}

const handlePageChange = (nextPage) => {
  page.value = nextPage
  loadFiles()
}

const handlePageSizeChange = (nextPageSize) => {
  pageSize.value = nextPageSize
  page.value = 1
  loadFiles()
}

const filePath = (row) => row?.publicPath || row?.url || ''

const previewFile = async (row) => {
  if (props.previewBehavior === 'emit') {
    emit('preview', row)
    return
  }
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
  () => [props.active, props.folderUrl],
  ([active]) => {
    clearSearchTimer()
    if (active) {
      searchKeyword.value = ''
      page.value = 1
      total.value = 0
      loadFiles()
      return
    }
    files.value = []
    total.value = 0
    error.value = ''
    loading.value = false
    searchKeyword.value = ''
  },
  { immediate: true }
)

onBeforeUnmount(clearSearchTimer)
</script>

<style scoped>
.drawing-folder-file-browser {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 0;
}

.drawing-folder-file-browser.is-compact {
  flex: 1;
  gap: 10px;
}

.drawing-folder-toolbar {
  display: flex;
  justify-content: flex-end;
}

.drawing-folder-search {
  width: 260px;
}

.is-compact .drawing-folder-search {
  width: 100%;
}

.drawing-folder-table {
  width: 100%;
}

.drawing-folder-pagination {
  margin-top: 0;
}

.is-compact .drawing-folder-pagination :deep(.el-pagination) {
  justify-content: flex-end;
}

.folder-file-actions {
  justify-content: center;
  gap: 8px;
}

.folder-file-actions :deep(.el-button) {
  margin-left: 0;
}
</style>
