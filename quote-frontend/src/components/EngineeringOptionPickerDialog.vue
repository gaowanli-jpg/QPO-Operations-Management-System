<template>
  <el-dialog
    v-model="visible"
    :title="title"
    width="920px"
    top="7vh"
    class="option-picker-dialog"
    destroy-on-close
    @opened="syncSelectedKey"
    @closed="resetPicker"
  >
    <div class="picker-toolbar">
      <el-input
        v-model="keyword"
        clearable
        :placeholder="searchPlaceholder"
        class="keyword-input"
      />
      <el-tag type="info">共 {{ filteredItems.length }} 项</el-tag>
    </div>

    <el-table
      :data="filteredItems"
      border
      stripe
      height="460"
      highlight-current-row
      :empty-text="emptyText"
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
        <el-table-column prop="materialName" label="材料名称" min-width="160" fixed show-overflow-tooltip />
        <el-table-column prop="materialType" label="材料类型" width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.materialType || '-' }}</template>
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
        <el-table-column prop="processName" label="工艺/费用名称" min-width="180" fixed show-overflow-tooltip />
        <el-table-column label="类型" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="row.sourceType === 'extra' ? 'warning' : 'info'" size="small">
              {{ sourceTypeLabel(row) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="calcType" label="核算方式" width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.calcType || '-' }}</template>
        </el-table-column>
        <el-table-column prop="label" label="显示名称" min-width="220" show-overflow-tooltip />
      </template>
    </el-table>

    <template #footer>
      <div class="picker-footer">
        <span class="selected-hint">当前选择：{{ selectedLabel }}</span>
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
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

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
  }
})

const emit = defineEmits(['update:modelValue', 'confirm'])

const visible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value)
})

const keyword = ref('')
const activeKey = ref('')

const searchPlaceholder = computed(() => (
  props.type === 'material'
    ? '搜索材料名称、类型、备注'
    : '搜索工艺名称、费用名称、核算方式'
))

const keyOf = (item) => String(
  props.type === 'material'
    ? (item?.materialName ?? item?.id ?? '')
    : (item?.optionKey ?? item?.id ?? item?.processName ?? item?.feeName ?? '')
)

const labelOf = (item) => {
  if (!item) return ''
  if (props.type === 'material') {
    return item.remarks ? `${item.materialName} ${item.remarks}` : item.materialName
  }
  return item.label || item.processName || item.feeName || ''
}

const searchTextOf = (item) => [
  item?.materialName,
  item?.materialType,
  item?.remarks,
  item?.processName,
  item?.feeName,
  item?.label,
  item?.calcType,
  item?.sourceType
].filter(Boolean).join(' ').toLowerCase()

const filteredItems = computed(() => {
  const text = keyword.value.trim().toLowerCase()
  if (!text) return props.items
  return props.items.filter(item => searchTextOf(item).includes(text))
})

const selectedItem = computed(() => {
  const currentKey = String(activeKey.value || '')
  if (!currentKey) return null
  return props.items.find(item => keyOf(item) === currentKey) || null
})

const selectedLabel = computed(() => labelOf(selectedItem.value) || '未选择')

const syncSelectedKey = () => {
  activeKey.value = props.selectedKey == null ? '' : String(props.selectedKey)
}

const resetPicker = () => {
  keyword.value = ''
}

const selectRow = (row) => {
  activeKey.value = keyOf(row)
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

watch(() => props.selectedKey, syncSelectedKey)
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
