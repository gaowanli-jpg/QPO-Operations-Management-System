<template>
  <el-dialog
    v-model="visible"
    :title="title"
    :width="dialogWidth"
    top="7vh"
    class="option-picker-dialog"
    destroy-on-close
    @opened="handleOpened"
    @closed="resetPicker"
  >
    <div class="picker-toolbar">
      <el-input
        v-model="keyword"
        clearable
        :placeholder="searchPlaceholder"
        class="keyword-input"
      />
      <el-tag type="info">共 {{ totalItemCount }} 项</el-tag>
    </div>

    <el-alert
      v-if="remoteError"
      :title="remoteError"
      type="error"
      show-icon
      :closable="false"
      class="picker-error"
    />

    <el-table
      :data="pagedItems"
      v-loading="remoteLoading"
      :loading="remoteLoading"
      border
      stripe
      height="460"
      highlight-current-row
      :empty-text="remoteEmptyText"
      :row-class-name="resolveRowClassName"
      @row-click="selectRow"
    >
      <el-table-column label="选择" width="78" align="center" fixed>
        <template #default="{ row }">
          <el-button
            :type="isActive(row) ? 'success' : 'primary'"
            :plain="!isActive(row)"
            size="small"
            @click.stop="selectRow(row)"
          >
            {{ isActive(row) ? '已选' : '选择' }}
          </el-button>
        </template>
      </el-table-column>

      <template v-if="type === 'material'">
        <el-table-column prop="materialCode" label="材料编码" width="150" fixed show-overflow-tooltip />
        <el-table-column prop="materialName" label="材料名称" min-width="150" fixed show-overflow-tooltip />
        <el-table-column prop="materialTypeName" label="材料类型" width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="material-type-cell">
              <span>{{ row.materialTypeName || '-' }}</span>
              <small v-if="row.materialTypeCode">{{ row.materialTypeCode }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="materialCategory" label="材质分类" width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.materialCategory || '-' }}</template>
        </el-table-column>
        <el-table-column label="密度" width="100" align="right">
          <template #default="{ row }">{{ formatNumber(row.plateDensity ?? row.density) }}</template>
        </el-table-column>
        <el-table-column label="板材价" width="100" align="right">
          <template #default="{ row }">{{ formatNumber(row.platePrice) }}</template>
        </el-table-column>
        <el-table-column label="棒材价" width="100" align="right">
          <template #default="{ row }">{{ formatNumber(row.barPrice) }}</template>
        </el-table-column>
        <el-table-column label="管材价" width="100" align="right">
          <template #default="{ row }">{{ formatNumber(row.tubePrice) }}</template>
        </el-table-column>
        <el-table-column prop="remarks" label="备注" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remarks || '-' }}</template>
        </el-table-column>
      </template>

      <template v-else>
        <el-table-column prop="detailName" label="具体工艺" min-width="150" fixed show-overflow-tooltip>
          <template #default="{ row }">{{ row.detailName || row.processName || row.feeName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="processCode" label="大类编码" width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.processCode || '-' }}</template>
        </el-table-column>
        <el-table-column prop="categoryName" label="大类名称" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.categoryName || '-' }}</template>
        </el-table-column>
        <el-table-column label="具体小时价格" width="180" align="right">
          <template #default="{ row }">
            <div v-if="canEditProcessRate(row)" class="inline-rate-editor" @click.stop>
              <el-input-number
                :model-value="processRateDraft(row)"
                :min="0"
                :precision="2"
                :value-on-clear="null"
                clearable
                controls-position="right"
                size="small"
                class="inline-rate-input"
                @update:model-value="value => setProcessRateDraft(row, value)"
              />
              <el-button
                v-permission="'engineer:dict:process-manage'"
                type="primary"
                plain
                size="small"
                :loading="isInlineRateSaving(row)"
                @click.stop="saveInlineProcessRate(row)"
              >
                保存
              </el-button>
            </div>
            <span v-else>{{ formatDetailHourlyRate(row.detailHourlyRate) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="大类小时价格" width="130" align="right">
          <template #default="{ row }">{{ formatHourlyRate(row.categoryHourlyRate) }}</template>
        </el-table-column>
        <el-table-column label="核价使用价格" width="130" align="right">
          <template #default="{ row }">{{ formatHourlyRate(row.effectiveHourlyRate ?? row.hourlyRate) }}</template>
        </el-table-column>
        <el-table-column label="类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="row.sourceType === 'extra' ? 'warning' : 'info'" size="small">
              {{ sourceTypeLabel(row) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="calcType" label="核算方式" width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.calcType || '-' }}</template>
        </el-table-column>
      </template>
    </el-table>

    <div v-if="showPagination" class="picker-pagination">
      <span>第 {{ currentPage }} / {{ totalPages }} 页</span>
      <div class="page-size-control">
        <span>每页</span>
        <el-select
          v-if="remote"
          v-model="remotePageSize"
          size="small"
          class="page-size-select"
          @change="handleRemotePageSizeChange"
        >
          <el-option
            v-for="size in pageSizeOptions"
            :key="size"
            :label="`${size} 条`"
            :value="size"
          />
        </el-select>
        <span v-else>{{ PROCESS_PICKER_PAGE_SIZE }} 条</span>
      </div>
      <div class="pagination-actions">
        <el-button size="small" :disabled="currentPage <= 1 || remoteLoading" @click="goPickerPage(currentPage - 1)">上一页</el-button>
        <el-button size="small" :disabled="currentPage >= totalPages || remoteLoading" @click="goPickerPage(currentPage + 1)">下一页</el-button>
      </div>
    </div>

    <template #footer>
      <div class="picker-footer">
        <span class="selected-hint" :title="selectedLabel">当前选择：{{ selectedLabel }}</span>
        <div class="footer-actions">
          <el-button @click="visible = false">取消</el-button>
          <el-button type="primary" :disabled="!selectedItem" @click="confirmSelection">
            确定选择
          </el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { saveProcessDetail } from '../services/dictApi'

const PROCESS_PICKER_PAGE_SIZE = 10

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  title: {
    type: String,
    default: '选择'
  },
  type: {
    type: String,
    default: 'material'
  },
  items: {
    type: Array,
    default: () => []
  },
  selectedKey: {
    type: [String, Number],
    default: ''
  },
  emptyText: {
    type: String,
    default: '暂无可选数据'
  },
  enableProcessRateEdit: {
    type: Boolean,
    default: false
  },
  remote: {
    type: Boolean,
    default: false
  },
  remoteLoader: {
    type: Function,
    default: null
  },
  pageSizeOptions: {
    type: Array,
    default: () => [20, 50, 100]
  },
  defaultPageSize: {
    type: Number,
    default: 20
  }
})

const emit = defineEmits(['update:modelValue', 'confirm', 'process-rate-updated'])

function normalizeRemotePageSize(value) {
  const numericValue = Number(value)
  return props.pageSizeOptions.includes(numericValue) ? numericValue : 20
}

const visible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value)
})

const keyword = ref('')
const activeKey = ref('')
const processPickerPage = ref(1)
const remotePage = ref(1)
const remotePageSize = ref(normalizeRemotePageSize(props.defaultPageSize))
const remoteTotal = ref(0)
const remoteRecords = ref([])
const remoteLoading = ref(false)
const remoteError = ref('')
const remoteSearchTimer = ref(null)
const remoteRequestSeq = ref(0)
const selectedItemSnapshot = ref(null)
const processRateDrafts = ref({})
const savingRateKeys = ref(new Set())

const isProcessPicker = computed(() => props.type === 'process')
const dialogWidth = computed(() => (isProcessPicker.value ? '1180px' : '920px'))

const searchPlaceholder = computed(() => (
  props.type === 'material'
    ? '搜索材料编码、名称、材料类型、材质分类、备注'
    : '搜索具体工艺、大类编码、大类名称、费用名称'
))

const keyOf = (item) => String(
  props.type === 'material'
    ? (item?.materialCode ?? item?.id ?? '')
    : (item?.optionKey ?? item?.id ?? item?.processName ?? item?.feeName ?? '')
)

const labelOf = (item) => {
  if (!item) return ''
  if (props.type === 'material') {
    const code = item.materialCode || '-'
    const name = item.materialName || '-'
    const typeName = item.materialTypeName || '-'
    const category = item.materialCategory || '-'
    return item.remarks
      ? `${code} / ${name} / ${typeName} / ${category} ${item.remarks}`
      : `${code} / ${name} / ${typeName} / ${category}`
  }
  return item.label || item.detailName || item.processName || item.feeName || ''
}

const searchTextOf = (item) => [
  item?.materialCode,
  item?.materialName,
  item?.materialTypeCode,
  item?.materialTypeName,
  item?.materialCategory,
  item?.remarks,
  item?.detailName,
  item?.processCode,
  item?.categoryName,
  item?.hourlyRate,
  item?.processName,
  item?.feeName,
  item?.label,
  item?.calcType,
  item?.sourceType
].filter(Boolean).join(' ').toLowerCase()

const displayItems = computed(() => (props.remote ? remoteRecords.value : props.items))

const filteredItems = computed(() => {
  if (props.remote) return remoteRecords.value
  const text = keyword.value.trim().toLowerCase()
  if (!text) return props.items
  return props.items.filter(item => searchTextOf(item).includes(text))
})

const totalItemCount = computed(() => (props.remote ? remoteTotal.value : filteredItems.value.length))
const currentPage = computed(() => (props.remote ? remotePage.value : processPickerPage.value))
const remoteEmptyText = computed(() => (remoteLoading.value ? '正在加载可选数据...' : emptyTextForDisplay()))
const totalPages = computed(() => {
  const size = props.remote ? remotePageSize.value : PROCESS_PICKER_PAGE_SIZE
  const total = props.remote ? remoteTotal.value : filteredItems.value.length
  return Math.max(1, Math.ceil(total / size))
})
const showPagination = computed(() => (
  props.remote
    ? remoteTotal.value > remotePageSize.value || remotePage.value > 1
    : isProcessPicker.value && totalPages.value > 1
))

const pagedItems = computed(() => {
  if (props.remote) return remoteRecords.value
  if (!isProcessPicker.value) return filteredItems.value
  const start = (processPickerPage.value - 1) * PROCESS_PICKER_PAGE_SIZE
  return filteredItems.value.slice(start, start + PROCESS_PICKER_PAGE_SIZE)
})

const selectedItem = computed(() => {
  const currentKey = String(activeKey.value || '')
  if (!currentKey) return null
  return displayItems.value.find(item => keyOf(item) === currentKey)
    || (selectedItemSnapshot.value && keyOf(selectedItemSnapshot.value) === currentKey ? selectedItemSnapshot.value : null)
})

const selectedLabel = computed(() => labelOf(selectedItem.value) || '未选择')

function emptyTextForDisplay() {
  if (!props.remote) return props.emptyText
  return remoteError.value ? '加载失败，请调整关键词或重试' : props.emptyText
}

function syncSelectedSnapshotFromRows(rows) {
  const currentKey = String(activeKey.value || '')
  if (!currentKey || !Array.isArray(rows)) return
  const matched = rows.find(item => keyOf(item) === currentKey)
  if (matched) {
    selectedItemSnapshot.value = matched
  }
}

const syncSelectedKey = () => {
  activeKey.value = props.selectedKey == null ? '' : String(props.selectedKey)
  syncSelectedSnapshotFromRows(displayItems.value)
}

const resetPicker = () => {
  clearRemoteSearchTimer()
  keyword.value = ''
  processPickerPage.value = 1
  remotePage.value = 1
  remotePageSize.value = normalizeRemotePageSize(props.defaultPageSize)
  remoteTotal.value = 0
  remoteRecords.value = []
  remoteError.value = ''
  remoteLoading.value = false
  remoteRequestSeq.value += 1
  selectedItemSnapshot.value = null
  processRateDrafts.value = {}
  savingRateKeys.value = new Set()
}

const selectRow = (row) => {
  activeKey.value = keyOf(row)
  selectedItemSnapshot.value = row
}

const isActive = (row) => keyOf(row) === String(activeKey.value || '')

const resolveRowClassName = ({ row }) => (isActive(row) ? 'selected-picker-row' : '')

const confirmSelection = () => {
  if (!selectedItem.value) {
    ElMessage.warning('请先选择一条数据。')
    return
  }
  emit('confirm', selectedItem.value)
  visible.value = false
}

const sourceTypeLabel = (row) => (row.sourceType === 'extra' ? '非工时费用' : '工艺')

const formatNumber = (value) => {
  const numericValue = Number(value)
  return Number.isFinite(numericValue) ? numericValue.toFixed(2) : '-'
}

const formatHourlyRate = (value) => {
  const text = formatNumber(value)
  return text === '-' ? '-' : `${text} 元/小时`
}

const formatDetailHourlyRate = (value) => (value == null ? '继承大类' : formatHourlyRate(value))

const goProcessPage = (page) => {
  processPickerPage.value = Math.min(Math.max(1, page), totalPages.value)
}

const goPickerPage = (page) => {
  if (props.remote) {
    loadRemotePage(page)
    return
  }
  goProcessPage(page)
}

const handleOpened = () => {
  syncSelectedKey()
  if (props.remote) {
    remotePageSize.value = normalizeRemotePageSize(remotePageSize.value)
    loadRemotePage(1)
  }
}

const handleRemotePageSizeChange = () => loadRemotePage(1)

const clearRemoteSearchTimer = () => {
  if (remoteSearchTimer.value) {
    window.clearTimeout(remoteSearchTimer.value)
    remoteSearchTimer.value = null
  }
}

const scheduleRemoteSearch = () => {
  clearRemoteSearchTimer()
  remoteSearchTimer.value = window.setTimeout(() => loadRemotePage(1), 300)
}

const loadRemotePage = async (page = remotePage.value) => {
  if (!props.remote) return
  if (typeof props.remoteLoader !== 'function') {
    remoteError.value = '远程选项加载器未配置。'
    return
  }
  const safePage = Math.max(1, Number(page) || 1)
  const safePageSize = normalizeRemotePageSize(remotePageSize.value)
  const requestSeq = remoteRequestSeq.value + 1
  remoteRequestSeq.value = requestSeq
  remoteLoading.value = true
  remoteError.value = ''
  try {
    const response = await props.remoteLoader({
      keyword: keyword.value.trim(),
      page: safePage,
      pageSize: safePageSize
    })
    if (requestSeq !== remoteRequestSeq.value) return
    const pageData = response?.data ?? response ?? {}
    const records = Array.isArray(pageData.records) ? pageData.records : []
    remoteRecords.value = records
    remoteTotal.value = Number(pageData.total ?? records.length) || 0
    remotePage.value = Number(pageData.page ?? safePage) || safePage
    remotePageSize.value = normalizeRemotePageSize(pageData.pageSize ?? safePageSize)
    syncSelectedSnapshotFromRows(records)
  } catch (error) {
    if (requestSeq !== remoteRequestSeq.value) return
    remoteError.value = error?.message || '选项加载失败，请稍后重试。'
  } finally {
    if (requestSeq === remoteRequestSeq.value) {
      remoteLoading.value = false
    }
  }
}

const canEditProcessRate = (row) => (
  props.enableProcessRateEdit
  && props.type === 'process'
  && row?.sourceType === 'process'
  && row?.processDetailId
)

const processRateDraft = (row) => {
  const key = keyOf(row)
  return Object.prototype.hasOwnProperty.call(processRateDrafts.value, key)
    ? processRateDrafts.value[key]
    : row?.detailHourlyRate ?? null
}

const setProcessRateDraft = (row, value) => {
  processRateDrafts.value = {
    ...processRateDrafts.value,
    [keyOf(row)]: value
  }
}

const isInlineRateSaving = (row) => savingRateKeys.value.has(keyOf(row))

const setInlineRateSaving = (row, saving) => {
  const next = new Set(savingRateKeys.value)
  if (saving) {
    next.add(keyOf(row))
  } else {
    next.delete(keyOf(row))
  }
  savingRateKeys.value = next
}

const saveInlineProcessRate = async (row) => {
  if (!canEditProcessRate(row)) return
  const draft = processRateDraft(row)
  const hourlyRate = draft === '' || draft == null ? null : Number(draft)
  if (hourlyRate != null && (!Number.isFinite(hourlyRate) || hourlyRate < 0)) {
    ElMessage.warning('具体小时价格不能小于 0。')
    return
  }
  const payload = {
    id: row.processDetailId,
    categoryId: row.categoryId ?? row.processCategoryId,
    detailName: row.detailName || row.processName,
    detailRemarks: row.detailRemarks || '',
    enabled: row.enabled !== false,
    sortNo: row.sortNo ?? 0,
    hourlyRate
  }
  setInlineRateSaving(row, true)
  try {
    const response = await saveProcessDetail(payload)
    const updatedDetail = response?.data || {
      ...row,
      id: row.processDetailId,
      categoryId: payload.categoryId,
      detailHourlyRate: hourlyRate,
      effectiveHourlyRate: hourlyRate ?? row.categoryHourlyRate,
      hourlyRate: hourlyRate ?? row.categoryHourlyRate
    }
    setProcessRateDraft(row, updatedDetail.detailHourlyRate ?? null)
    emit('process-rate-updated', updatedDetail)
    ElMessage.success(response?.message || '具体小时价格已保存')
  } catch (error) {
    console.error(error)
    ElMessage.error(error?.message || '具体小时价格保存失败。')
  } finally {
    setInlineRateSaving(row, false)
  }
}

watch(() => props.selectedKey, syncSelectedKey)
watch(keyword, () => {
  if (props.remote) {
    scheduleRemoteSearch()
    return
  }
  processPickerPage.value = 1
})
watch(filteredItems, () => {
  if (props.remote) return
  if (processPickerPage.value > totalPages.value) {
    processPickerPage.value = totalPages.value
  }
})
onBeforeUnmount(clearRemoteSearchTimer)
</script>

<style scoped>
.picker-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.keyword-input {
  flex: 1;
}

.material-type-cell {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.material-type-cell small {
  color: var(--erp-text-secondary);
  font-size: 12px;
  line-height: 1.2;
}

.inline-rate-editor {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
}

.inline-rate-input {
  width: 104px;
}

.picker-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-top: 12px;
  color: var(--erp-text-secondary);
  font-size: 13px;
}

.pagination-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.picker-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  width: 100%;
}

.selected-hint {
  min-width: 0;
  overflow: hidden;
  color: var(--erp-text-secondary);
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.footer-actions {
  flex: none;
  display: flex;
  align-items: center;
  gap: 10px;
}

.option-picker-dialog :deep(.selected-picker-row > td) {
  background: #eef5ff !important;
}
</style>
