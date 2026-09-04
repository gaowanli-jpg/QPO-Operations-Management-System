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
    <div :class="type === 'process' ? 'process-picker-layout' : 'picker-single-panel'">
      <aside v-if="type === 'process'" class="process-arrangement-picker-panel">
        <div class="process-arrangement-picker-header">
          <div>
            <strong>工艺层级</strong>
            <span :title="selectedProcessArrangementPathLabel">{{ selectedProcessArrangementPathLabel }}</span>
          </div>
          <el-button size="small" :type="selectedProcessArrangementKey ? 'default' : 'primary'" plain @click="clearProcessArrangementSelection">
            全部工艺
          </el-button>
        </div>
        <el-alert
          v-if="processArrangementError"
          :title="processArrangementError"
          type="warning"
          show-icon
          :closable="false"
          class="picker-error"
        />
        <div class="process-arrangement-tree-shell" v-loading="processArrangementLoading">
          <el-empty
            v-if="!processArrangementLoading && !processArrangementTree.length"
            description="暂无工艺编排，可直接搜索全部工艺"
            :image-size="72"
          />
          <el-tree
            v-else
            ref="processArrangementTreeRef"
            :data="processArrangementTree"
            node-key="treeKey"
            :props="processArrangementTreeProps"
            :default-expanded-keys="processArrangementExpandedKeys"
            highlight-current
            @node-click="selectProcessArrangementNode"
            @node-expand="handleProcessArrangementNodeExpand"
            @node-collapse="handleProcessArrangementNodeCollapse"
          >
            <template #default="{ data }">
              <div class="process-arrangement-picker-row" :class="{ 'is-selected': data.treeKey === selectedProcessArrangementKey }">
                <el-tag size="small" :type="data.nodeType === 'item' ? processArrangementNodeTagType(data.sourceType) : 'info'">
                  {{ data.nodeType === 'item' ? sourceTypeLabel(data) : '分组' }}
                </el-tag>
                <span class="process-arrangement-picker-label" :title="data.label">{{ data.label }}</span>
              </div>
            </template>
          </el-tree>
        </div>
      </aside>

      <section class="process-picker-table-panel">
        <div class="picker-toolbar">
          <el-input
            v-model="keyword"
            clearable
            :placeholder="searchPlaceholder"
            class="keyword-input"
          />
          <el-radio-group v-if="type === 'material'" v-model="materialTypeFilter" size="small" class="material-type-filter">
            <el-radio-button
              v-for="option in MATERIAL_TYPE_FILTER_OPTIONS"
              :key="option.label"
              :label="option.value"
            >
              {{ option.label }}
            </el-radio-button>
          </el-radio-group>
          <el-radio-group v-if="type === 'process'" v-model="processSourceTypeFilter" size="small" class="process-source-type-filter">
            <el-radio-button label="">全部</el-radio-button>
            <el-radio-button label="process">工时工艺</el-radio-button>
            <el-radio-button label="extra">非工时工艺</el-radio-button>
          </el-radio-group>
          <el-tag type="info">共 {{ totalItemCount }} 项</el-tag>
        </div>

        <div v-if="type === 'process'" class="process-picker-context" :title="selectedProcessArrangementPathLabel">
          当前层级：{{ selectedProcessArrangementPathLabel }}
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
      :height="type === 'process' ? 500 : 460"
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
        <el-table-column label="具体密度" width="144" align="right" class-name="material-edit-cell">
          <template #default="{ row }">
            <el-input-number
              v-if="canEditMaterialPrice(row)"
              :model-value="materialPriceDraft(row, 'density')"
              :min="0"
              :precision="4"
              :value-on-clear="null"
              clearable
              controls-position="right"
              size="small"
              class="inline-material-input density-input"
              @click.stop
              @update:model-value="value => setMaterialPriceDraft(row, 'density', value)"
            />
            <span v-else>{{ formatSpecificDensity(row.density) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="MES密度" width="110" align="right">
          <template #default="{ row }">{{ formatMesDensity(row.mesDensityRaw) }}</template>
        </el-table-column>
        <el-table-column label="实际使用密度" width="130" align="right">
          <template #default="{ row }">{{ formatEffectiveMaterialDensity(row) }}</template>
        </el-table-column>
        <el-table-column
          v-for="definition in MATERIAL_PRICE_SPEC_DEFINITIONS"
          :key="definition.specCode"
          :label="definition.specName"
          width="154"
          align="right"
          class-name="material-edit-cell"
        >
          <template #default="{ row }">
            <div class="material-spec-cell" @click.stop>
              <span :title="formatSpecUnifiedPrice(row, definition.specCode)">
                {{ formatSpecUnifiedPrice(row, definition.specCode) }}
              </span>
              <el-button size="small" link type="primary" @click.stop="openMaterialSpecDialog(row, definition.specCode)">
                详情
              </el-button>
            </div>
          </template>
        </el-table-column>
        <el-table-column v-if="enableMaterialPriceEdit" label="保存" width="92" align="center">
          <template #default="{ row }">
            <el-button
              v-permission="'engineer:dict:material-manage'"
              type="primary"
              plain
              size="small"
              :disabled="!canEditMaterialPrice(row)"
              :loading="isInlineMaterialSaving(row)"
              @click.stop="saveInlineMaterialPrice(row)"
            >
              保存
            </el-button>
          </template>
        </el-table-column>
        <el-table-column prop="materialTypeName" label="材料类型" width="120" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="material-type-cell">
              <span>{{ row.materialTypeName || '-' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="materialCategory" label="材质分类" width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.materialCategory || '-' }}</template>
        </el-table-column>
        <el-table-column prop="remarks" label="备注" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remarks || '-' }}</template>
        </el-table-column>
      </template>

      <template v-else>
        <el-table-column prop="processCode" label="工艺编码" width="140" fixed show-overflow-tooltip>
          <template #default="{ row }">{{ row.processCode || '-' }}</template>
        </el-table-column>
        <el-table-column prop="detailName" label="具体工艺" min-width="170" fixed show-overflow-tooltip>
          <template #default="{ row }">{{ row.detailName || row.processName || row.feeName || '-' }}</template>
        </el-table-column>
        <el-table-column label="所在层级" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ processArrangementPathLabel(row) }}</template>
        </el-table-column>
        <el-table-column label="小时价格" width="180" align="right">
          <template #default="{ row }">
            <div v-if="canEditProcessRate(row)" class="inline-rate-editor" @click.stop>
              <el-input-number
                :model-value="processRateDraft(row)"
                :min="0"
                :precision="2"
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
            <span v-else>{{ formatHourlyRate(row.hourlyRate ?? row.effectiveHourlyRate ?? row.detailHourlyRate) }}</span>
          </template>
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
      </div>
      <div class="pagination-actions">
        <el-button size="small" :disabled="currentPage <= 1 || remoteLoading" @click="goPickerPage(currentPage - 1)">上一页</el-button>
        <el-button size="small" :disabled="currentPage >= totalPages || remoteLoading" @click="goPickerPage(currentPage + 1)">下一页</el-button>
      </div>
    </div>
      </section>
    </div>

    <MaterialPriceSpecDetailDialog
      v-if="type === 'material'"
      v-model="materialSpecDialogVisible"
      :spec="activeMaterialSpecDraft"
      :readonly="!activeMaterialSpecEditable"
      @update:spec="updateActiveMaterialSpecDraft"
    />

    <template #footer>
      <div class="picker-footer">
        <span class="selected-hint" :title="selectedLabel">当前选择：{{ selectedLabel }}</span>
        <div class="footer-actions">
          <el-button @click="visible = false">取消</el-button>
          <template v-if="type === 'material'">
            <el-button
              v-for="definition in MATERIAL_PRICE_SPEC_DEFINITIONS"
              :key="definition.specCode"
              :type="definition.buttonType"
              :disabled="!selectedItem"
              @click="confirmMaterialSpecSelection(definition.specCode)"
            >
              选择{{ definition.specName }}
            </el-button>
          </template>
          <el-button v-else type="primary" :disabled="!selectedItem" @click="confirmSelection">
            确定选择
          </el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import MaterialPriceSpecDetailDialog from './material/MaterialPriceSpecDetailDialog.vue'
import { saveMaterial, saveProcessDetail } from '../services/dictApi'
import {
  buildProcessArrangementPathIndex,
  collectProcessArrangementPickerFilter,
  findProcessArrangementPathLabels
} from '../utils/processArrangementPicker'
import { nextProcessArrangementExpandedKeys } from '../utils/processArrangementTreeExpansion'
import {
  MATERIAL_PRICE_SPEC_DEFINITIONS,
  buildMaterialSpecSelection,
  formatMaterialSpecPrice,
  normalizePriceSpecs,
  samePriceSpecs
} from '../utils/materialPriceSpecs'

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
  enableMaterialPriceEdit: {
    type: Boolean,
    default: false
  },
  remoteLoader: {
    type: Function,
    default: null
  },
  processArrangementTreeLoader: {
    type: Function,
    default: null
  },
  pageSizeOptions: {
    type: Array,
    default: () => [10, 20, 50, 100]
  },
  defaultPageSize: {
    type: Number,
    default: 10
  }
})

const emit = defineEmits(['update:modelValue', 'confirm', 'process-rate-updated', 'material-price-updated'])

const MATERIAL_TYPE_FILTER_OPTIONS = [
  { label: '全部', value: '' },
  { label: '原材料', value: 'MATERIAL,RAW_MATERIAL' },
  { label: '半成品', value: 'SEMI' },
  { label: '成品', value: 'PRODUCT' }
]

function normalizeRemotePageSize(value) {
  const numericValue = Number(value)
  return props.pageSizeOptions.includes(numericValue) ? numericValue : props.defaultPageSize
}

const visible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value)
})

const keyword = ref('')
const activeKey = ref('')
const page = ref(1)
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
const materialPriceDrafts = ref({})
const savingMaterialKeys = ref(new Set())
const materialSpecDialogVisible = ref(false)
const materialSpecDialogRow = ref(null)
const materialSpecDialogCode = ref('SPEC_1')
const processArrangementTree = ref([])
const processArrangementTreeRef = ref(null)
const processArrangementLoading = ref(false)
const processArrangementError = ref('')
const selectedProcessArrangementKey = ref('')
const selectedProcessArrangementNode = ref(null)
const processArrangementExpandedKeys = ref([])
const materialTypeFilter = ref('')
const processSourceTypeFilter = ref('')

const processArrangementTreeProps = { children: 'children', label: 'label' }

const dialogWidth = computed(() => (
  props.type === 'process'
    ? '1320px'
    : props.type === 'material' && props.enableMaterialPriceEdit ? '1380px' : '1180px'
))

const searchPlaceholder = computed(() => (
  props.type === 'material'
    ? '搜索材料编码、名称、材料类型、材质分类、备注'
    : '搜索工艺编码、具体工艺、非工时费用编码/名称'
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
    const specName = item.priceSpecName ? ` / ${item.priceSpecName}` : ''
    return item.remarks
      ? `${code} / ${name} / ${typeName} / ${category}${specName} ${item.remarks}`
      : `${code} / ${name} / ${typeName} / ${category}${specName}`
  }
  return item.label || item.detailName || item.processName || item.feeName || ''
}

const totalItemCount = computed(() => remoteTotal.value)
const currentPage = computed(() => page.value)
const remoteEmptyText = computed(() => (remoteLoading.value ? '正在加载可选数据...' : emptyTextForDisplay()))
const totalPages = computed(() => {
  const size = remotePageSize.value
  const total = remoteTotal.value
  return Math.max(1, Math.ceil(total / size))
})
const showPagination = computed(() => remoteTotal.value > remotePageSize.value || page.value > 1)

const pagedItems = computed(() => remoteRecords.value)

const selectedItem = computed(() => {
  const currentKey = String(activeKey.value || '')
  if (!currentKey) return null
  return remoteRecords.value.find(item => keyOf(item) === currentKey)
    || (selectedItemSnapshot.value && keyOf(selectedItemSnapshot.value) === currentKey ? selectedItemSnapshot.value : null)
})

const selectedLabel = computed(() => labelOf(selectedItem.value) || '未选择')

const processArrangementPathIndex = computed(() => buildProcessArrangementPathIndex(processArrangementTree.value))

const processPickerFilter = computed(() => collectProcessArrangementPickerFilter({
  tree: processArrangementTree.value,
  selectedTreeKey: selectedProcessArrangementKey.value,
  sourceType: processSourceTypeFilter.value
}))

const selectedProcessArrangementPathLabel = computed(() => {
  if (!selectedProcessArrangementKey.value) return '全部工艺'
  const labels = findProcessArrangementPathLabels(processArrangementTree.value, selectedProcessArrangementKey.value)
  return labels.length ? labels.join(' / ') : '已选层级'
})

function emptyTextForDisplay() {
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
  syncSelectedSnapshotFromRows(remoteRecords.value)
}

const resetPicker = () => {
  clearRemoteSearchTimer()
  keyword.value = ''
  page.value = 1
  remotePageSize.value = normalizeRemotePageSize(props.defaultPageSize)
  remoteTotal.value = 0
  remoteRecords.value = []
  remoteError.value = ''
  remoteLoading.value = false
  remoteRequestSeq.value += 1
  selectedItemSnapshot.value = null
  processRateDrafts.value = {}
  savingRateKeys.value = new Set()
  materialPriceDrafts.value = {}
  savingMaterialKeys.value = new Set()
  materialSpecDialogVisible.value = false
  materialSpecDialogRow.value = null
  materialSpecDialogCode.value = 'SPEC_1'
  processArrangementTree.value = []
  processArrangementLoading.value = false
  processArrangementError.value = ''
  selectedProcessArrangementKey.value = ''
  selectedProcessArrangementNode.value = null
  processArrangementExpandedKeys.value = []
  materialTypeFilter.value = ''
  processSourceTypeFilter.value = ''
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

const confirmMaterialSpecSelection = (specCode) => {
  if (!selectedItem.value) {
    ElMessage.warning('请先选择一条材料。')
    return
  }
  if (hasInlineMaterialDraft(selectedItem.value)) {
    ElMessage.warning('当前材料规格价格或密度有未保存修改，请先保存后再选择规格。')
    return
  }
  emit('confirm', buildMaterialSpecSelection(selectedItem.value, specCode))
  visible.value = false
}

const sourceTypeLabel = (row) => (row.sourceType === 'extra' ? '非工时费用' : '工艺')

const processArrangementNodeTagType = (sourceType) => (sourceType === 'extra' ? 'warning' : 'success')

const processArrangementLookupKey = (row) => {
  if (row?.sourceType === 'process' && row.processDetailId != null) return `process:${row.processDetailId}`
  if (row?.sourceType === 'extra' && row.feeItemId != null) return `extra:${row.feeItemId}`
  return ''
}

const processArrangementPathLabel = (row) => {
  const key = processArrangementLookupKey(row)
  return key ? (processArrangementPathIndex.value.get(key) || '未编排') : '未编排'
}

const collectDescendantNodeKeys = (row, keys = new Set()) => {
  for (const child of row?.children || []) {
    if (child?.nodeType === 'node' && child.treeKey) keys.add(child.treeKey)
    collectDescendantNodeKeys(child, keys)
  }
  return keys
}

const handleProcessArrangementNodeExpand = (row) => {
  if (!row?.treeKey || row.nodeType !== 'node') return
  processArrangementExpandedKeys.value = Array.from(new Set([...processArrangementExpandedKeys.value, row.treeKey]))
}

const handleProcessArrangementNodeCollapse = (row) => {
  if (!row?.treeKey) return
  const removedKeys = collectDescendantNodeKeys(row)
  removedKeys.add(row.treeKey)
  processArrangementExpandedKeys.value = processArrangementExpandedKeys.value.filter(key => !removedKeys.has(key))
}

const clearProcessArrangementSelection = () => {
  selectedProcessArrangementKey.value = ''
  selectedProcessArrangementNode.value = null
  processArrangementTreeRef.value?.setCurrentKey?.(null, false)
  loadRemotePage(1)
}

const selectProcessArrangementNode = (row) => {
  selectedProcessArrangementKey.value = row?.treeKey || ''
  selectedProcessArrangementNode.value = row || null
  loadRemotePage(1)
}

const loadProcessArrangementTree = async ({ revealNodeId = null } = {}) => {
  if (props.type !== 'process' || typeof props.processArrangementTreeLoader !== 'function') {
    return
  }
  processArrangementLoading.value = true
  processArrangementError.value = ''
  try {
    const response = await props.processArrangementTreeLoader()
    const tree = response?.data ?? response ?? []
    processArrangementTree.value = Array.isArray(tree) ? tree : []
    processArrangementExpandedKeys.value = nextProcessArrangementExpandedKeys({
      tree: processArrangementTree.value,
      currentExpandedKeys: processArrangementExpandedKeys.value,
      revealNodeId
    })
    await nextTick()
    processArrangementTreeRef.value?.setCurrentKey?.(selectedProcessArrangementKey.value || null, false)
  } catch (error) {
    console.error(error)
    processArrangementError.value = error?.message || '工艺编排层级加载失败，可先使用搜索选择工艺。'
  } finally {
    processArrangementLoading.value = false
  }
}

const formatNumber = (value) => {
  const numericValue = Number(value)
  return Number.isFinite(numericValue) ? numericValue.toFixed(2) : '-'
}

const numberOrNull = (value) => {
  if (value === '' || value == null) return null
  const numericValue = Number(value)
  return Number.isFinite(numericValue) ? numericValue : null
}

const positiveNumberOrNull = (value) => {
  const numericValue = numberOrNull(value)
  return numericValue != null && numericValue > 0 ? numericValue : null
}

const resolveMaterialEffectiveDensity = (row = {}) => (
  positiveNumberOrNull(materialPriceDraft(row, 'density')) ?? positiveNumberOrNull(row.mesDensityRaw)
)

const formatDensityNumber = (value) => {
  const numericValue = Number(value)
  return Number.isFinite(numericValue) ? numericValue.toFixed(4) : '-'
}

const formatSpecificDensity = (value) => formatDensityNumber(value)

const formatMesDensity = (value) => formatDensityNumber(value)

const formatEffectiveMaterialDensity = (row) => formatDensityNumber(resolveMaterialEffectiveDensity(row))

const formatHourlyRate = (value) => {
  const text = formatNumber(value)
  return text === '-' ? '-' : `${text} 元/小时`
}

const goPickerPage = (page) => {
  loadRemotePage(page)
}

const handleOpened = () => {
  syncSelectedKey()
  remotePageSize.value = normalizeRemotePageSize(remotePageSize.value)
  if (props.type === 'process') {
    loadProcessArrangementTree()
  }
  loadRemotePage(1)
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

const loadRemotePage = async (nextPage = page.value) => {
  if (typeof props.remoteLoader !== 'function') {
    remoteError.value = '远程选项加载器未配置。'
    return
  }
  const safePage = Math.max(1, Number(nextPage) || 1)
  const safePageSize = normalizeRemotePageSize(remotePageSize.value)
  const requestSeq = remoteRequestSeq.value + 1
  remoteRequestSeq.value = requestSeq
  remoteError.value = ''
  const params = {
    keyword: keyword.value.trim(),
    page: safePage,
    pageSize: safePageSize
  }
  if (props.type === 'material' && materialTypeFilter.value) {
    params.materialTypeCodes = materialTypeFilter.value
  }
  if (props.type === 'process') {
    const pickerFilter = processPickerFilter.value
    params.sourceType = processSourceTypeFilter.value
    if (pickerFilter.hasSelection) {
      if (!pickerFilter.hasLeaves) {
        remoteRecords.value = []
        remoteTotal.value = 0
        page.value = safePage
        remotePageSize.value = safePageSize
        remoteLoading.value = false
        return
      }
      Object.assign(params, {
        processDetailIds: pickerFilter.processDetailIds.join(','),
        feeItemIds: pickerFilter.feeItemIds.join(',')
      })
    }
  }
  remoteLoading.value = true
  try {
    const response = await props.remoteLoader(params)
    if (requestSeq !== remoteRequestSeq.value) return
    const pageData = response?.data ?? response ?? {}
    const records = Array.isArray(pageData.records) ? pageData.records : []
    remoteRecords.value = records
    remoteTotal.value = Number(pageData.total ?? records.length) || 0
    page.value = Number(pageData.page ?? safePage) || safePage
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

const canEditMaterialPrice = (row) => (
  props.enableMaterialPriceEdit
  && props.type === 'material'
  && (row?.id || row?.materialId)
  && row?.materialCode
  && row?.materialName
  && row?.materialTypeId
)

const materialPriceDraft = (row, field) => {
  const key = keyOf(row)
  const rowDraft = materialPriceDrafts.value[key] || {}
  return Object.prototype.hasOwnProperty.call(rowDraft, field)
    ? rowDraft[field]
    : row?.[field] ?? null
}

const materialPriceSpecsDraft = (row) => {
  const key = keyOf(row)
  const rowDraft = materialPriceDrafts.value[key] || {}
  return normalizePriceSpecs(rowDraft.priceSpecs ?? row?.priceSpecs)
}

const setMaterialPriceSpecsDraft = (row, priceSpecs) => {
  const key = keyOf(row)
  materialPriceDrafts.value = {
    ...materialPriceDrafts.value,
    [key]: {
      ...(materialPriceDrafts.value[key] || {}),
      priceSpecs: normalizePriceSpecs(priceSpecs)
    }
  }
}

const materialSpecDraft = (row, specCode) => (
  materialPriceSpecsDraft(row).find(spec => spec.specCode === specCode) || normalizePriceSpecs([])[0]
)

const activeMaterialSpecDraft = computed(() => materialSpecDraft(materialSpecDialogRow.value, materialSpecDialogCode.value))

const activeMaterialSpecEditable = computed(() => canEditMaterialPrice(materialSpecDialogRow.value))

const formatSpecUnifiedPrice = (row, specCode) => formatMaterialSpecPrice(materialSpecDraft(row, specCode)?.unifiedPrice)

const openMaterialSpecDialog = (row, specCode) => {
  materialSpecDialogRow.value = row
  materialSpecDialogCode.value = specCode
  materialSpecDialogVisible.value = true
}

const updateActiveMaterialSpecDraft = (updatedSpec) => {
  const row = materialSpecDialogRow.value
  if (!row || !canEditMaterialPrice(row)) return
  const specs = materialPriceSpecsDraft(row).map(spec => (
    spec.specCode === updatedSpec.specCode ? updatedSpec : spec
  ))
  setMaterialPriceSpecsDraft(row, specs)
}

const setMaterialPriceDraft = (row, field, value) => {
  const key = keyOf(row)
  materialPriceDrafts.value = {
    ...materialPriceDrafts.value,
    [key]: {
      ...(materialPriceDrafts.value[key] || {}),
      [field]: value
    }
  }
}

const materialDraftNumber = (row, field) => {
  const draft = materialPriceDraft(row, field)
  return draft === '' || draft == null ? null : Number(draft)
}

const hasInlineMaterialDraft = (row) => {
  const key = keyOf(row)
  const rowDraft = materialPriceDrafts.value[key]
  if (!rowDraft) return false
  const draftDensity = materialPriceDraft(row, 'density')
  const sourceDensity = row?.density ?? null
  if ((draftDensity ?? null) !== (sourceDensity ?? null)) return true
  return !samePriceSpecs(rowDraft.priceSpecs ?? row?.priceSpecs, row?.priceSpecs)
}

const validateMaterialDraftNumber = (fieldLabel, value) => {
  if (value == null) return true
  if (!Number.isFinite(value) || value < 0) {
    ElMessage.warning(`${fieldLabel}不能小于 0。`)
    return false
  }
  return true
}

const isInlineMaterialSaving = (row) => savingMaterialKeys.value.has(keyOf(row))

const setInlineMaterialSaving = (row, saving) => {
  const next = new Set(savingMaterialKeys.value)
  if (saving) {
    next.add(keyOf(row))
  } else {
    next.delete(keyOf(row))
  }
  savingMaterialKeys.value = next
}

const replaceMaterialRecord = (row, updatedMaterial) => {
  const previousKey = keyOf(row)
  const merged = {
    ...row,
    ...updatedMaterial,
    id: updatedMaterial.id ?? updatedMaterial.materialId ?? row.id,
    materialId: updatedMaterial.materialId ?? updatedMaterial.id ?? row.materialId
  }
  remoteRecords.value = remoteRecords.value.map(record => (keyOf(record) === previousKey ? merged : record))
  if (selectedItemSnapshot.value && keyOf(selectedItemSnapshot.value) === previousKey) {
    selectedItemSnapshot.value = merged
  }
  return merged
}

const saveInlineMaterialPrice = async (row) => {
  if (!canEditMaterialPrice(row)) {
    ElMessage.warning('当前材料缺少编码、名称或材料类型，无法在核价弹窗保存。')
    return
  }
  const density = materialDraftNumber(row, 'density')
  const priceSpecs = materialPriceSpecsDraft(row)
  if (!validateMaterialDraftNumber('具体密度', density)) {
    return
  }
  const payload = {
    id: row.id ?? row.materialId,
    materialCode: row.materialCode,
    materialName: row.materialName,
    materialTypeId: row.materialTypeId,
    materialCategory: row.materialCategory || '',
    density,
    priceSpecs,
    remarks: row.remarks || '',
    enabled: row.enabled,
    sortNo: row.sortNo
  }
  setInlineMaterialSaving(row, true)
  try {
    const response = await saveMaterial(payload)
    const updatedMaterial = replaceMaterialRecord(row, response?.data || {
      ...row,
      ...payload,
      materialId: payload.id
    })
    materialPriceDrafts.value = {
      ...materialPriceDrafts.value,
      [keyOf(updatedMaterial)]: {
        density: updatedMaterial.density ?? null,
        priceSpecs: normalizePriceSpecs(updatedMaterial.priceSpecs)
      }
    }
    emit('material-price-updated', updatedMaterial)
    ElMessage.success(response?.message || '材料密度和规格价格已保存')
  } catch (error) {
    console.error(error)
    ElMessage.error(error?.response?.data?.message || error?.payload?.message || error?.message || '材料密度和规格价格保存失败。')
  } finally {
    setInlineMaterialSaving(row, false)
  }
}

const processRateDraft = (row) => {
  const key = keyOf(row)
  return Object.prototype.hasOwnProperty.call(processRateDrafts.value, key)
    ? processRateDrafts.value[key]
    : row?.hourlyRate ?? row?.effectiveHourlyRate ?? row?.detailHourlyRate ?? null
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
  if (draft === '' || draft == null) {
    ElMessage.warning('请填写小时价格。')
    return
  }
  const hourlyRate = Number(draft)
  if (!Number.isFinite(hourlyRate) || hourlyRate < 0) {
    ElMessage.warning('小时价格不能小于 0。')
    return
  }
  const payload = {
    id: row.processDetailId,
    categoryId: row.categoryId ?? row.processCategoryId ?? null,
    processCode: row.processCode,
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
      effectiveHourlyRate: hourlyRate,
      hourlyRate
    }
    setProcessRateDraft(row, updatedDetail.hourlyRate ?? updatedDetail.effectiveHourlyRate ?? updatedDetail.detailHourlyRate ?? null)
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
watch(keyword, scheduleRemoteSearch)
watch(materialTypeFilter, () => {
  if (visible.value && props.type === 'material') {
    loadRemotePage(1)
  }
})
watch(processSourceTypeFilter, () => {
  if (visible.value && props.type === 'process') {
    loadRemotePage(1)
  }
})
onBeforeUnmount(clearRemoteSearchTimer)
</script>

<style scoped>
.picker-single-panel,
.process-picker-table-panel {
  min-width: 0;
}

.process-picker-layout {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 14px;
  align-items: stretch;
}

.process-arrangement-picker-panel {
  display: flex;
  min-height: 584px;
  overflow: hidden;
  flex-direction: column;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-bg-color);
}

.process-arrangement-picker-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: var(--erp-surface-soft, #f5f7fa);
}

.process-arrangement-picker-header > div {
  min-width: 0;
}

.process-arrangement-picker-header strong,
.process-arrangement-picker-header span {
  display: block;
}

.process-arrangement-picker-header strong {
  color: var(--erp-text-primary, #1f2d3d);
  font-size: 14px;
}

.process-arrangement-picker-header span {
  overflow: hidden;
  margin-top: 4px;
  color: var(--erp-text-secondary, #606266);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.process-arrangement-tree-shell {
  min-height: 0;
  flex: 1;
  padding: 8px;
  overflow: auto;
}

.process-arrangement-picker-panel :deep(.el-tree-node__content) {
  height: 34px;
  border-radius: 6px;
}

.process-arrangement-picker-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  width: 100%;
  padding-right: 8px;
}

.process-arrangement-picker-row.is-selected {
  font-weight: 600;
}

.process-arrangement-picker-label {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.picker-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.keyword-input {
  flex: 1;
}

.material-type-filter,
.process-source-type-filter {
  flex: none;
}

.process-picker-context {
  overflow: hidden;
  margin: -4px 0 10px;
  color: var(--erp-text-secondary, #606266);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.material-type-cell {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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

.inline-material-input {
  width: 100%;
  max-width: 104px;
}

.inline-material-input.density-input {
  max-width: 116px;
}

.option-picker-dialog :deep(.material-edit-cell .cell) {
  overflow: visible;
  text-overflow: clip;
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

@media (max-width: 1180px) {
  .process-picker-layout {
    grid-template-columns: 1fr;
  }

  .process-arrangement-picker-panel {
    min-height: 240px;
  }

  .process-arrangement-tree-shell {
    max-height: 260px;
  }
}
</style>
