<script setup>
import { computed } from 'vue'
import ApsStatusTag from './ApsStatusTag.vue'

const props = defineProps({
  title: {
    type: String,
    required: true
  },
  rows: {
    type: Array,
    default: () => []
  },
  columns: {
    type: Array,
    default: () => []
  },
  deletable: {
    type: Boolean,
    default: false
  },
  actionLabel: {
    type: String,
    default: '删除'
  },
  actions: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['deleteRow', 'rowAction'])

const normalizeColumn = (column) => {
  if (Array.isArray(column)) {
    const [prop, label, options = {}] = column
    return {
      prop,
      label,
      minWidth: options.minWidth || columnMinWidth(prop),
      width: options.width,
      align: options.align,
      status: options.status
    }
  }
  const prop = column.prop || column.key
  return {
    prop,
    label: column.label || column.title || prop,
    minWidth: column.minWidth || columnMinWidth(prop),
    width: column.width,
    align: column.align,
    status: column.status
  }
}

const columnMinWidth = (prop) => {
  if (['customer_order_no', 'customer_project_no', 'internal_project_no'].includes(prop)) return 150
  if (['customer_name', 'material_name'].includes(prop)) return 140
  if (['status', 'department_status', 'machine_status'].includes(prop) || prop?.endsWith('_status')) return 112
  if (['id', 'work_item_id'].includes(prop)) return 82
  return 130
}

const normalizedColumns = computed(() => props.columns.map(normalizeColumn))

const normalizeAction = (action) => {
  if (Array.isArray(action)) {
    return {
      name: action[0],
      label: action[1],
      tone: action[2] || 'primary'
    }
  }
  return {
    name: action.name || action.key,
    label: action.label,
    tone: action.type || action.tone || 'primary',
    disabled: action.disabled
  }
}

const normalizedActions = computed(() => props.actions.map(normalizeAction))

const operationWidth = computed(() => {
  const actionCount = normalizedActions.value.length + (props.deletable ? 1 : 0)
  return Math.max(96, actionCount * 58 + 40)
})

const showValue = (row, column) => {
  const value = row[column.prop]
  if (column.prop === 'customer_project_no' && (value === undefined || value === null || value === '')) return '空'
  if (value === undefined || value === null || value === '') return '-'
  return value
}

const isStatus = (key) => (
  key === 'status'
  || key.endsWith('_status')
  || key === 'department_type'
  || key === 'department_status'
  || key === 'machine_status'
)

const isStatusColumn = (column) => column.status === true || (column.status !== false && isStatus(column.prop))

const actionType = (action) => {
  if (action.tone === 'danger') return 'danger'
  if (action.tone === 'confirm' || action.tone === 'success') return 'success'
  return 'primary'
}

const actionDisabled = (action, row) => typeof action.disabled === 'function'
  ? action.disabled(row)
  : !!action.disabled

const rowKey = (row) => row.id
  ?? row.work_item_id
  ?? `${row.internal_project_no || ''}-${row.customer_order_no || ''}-${row.part_no || ''}-${row.material_name || ''}`
</script>

<template>
  <section class="panel">
    <div class="section-head">
      <h2>{{ title }}</h2>
      <span>{{ rows.length }} 条</span>
    </div>
    <el-table
      :data="rows"
      border
      class="aps-data-table"
      empty-text="暂无数据"
      :row-key="rowKey"
      stripe
    >
      <el-table-column
        v-for="column in normalizedColumns"
        :key="column.prop"
        :align="column.align"
        :label="column.label"
        :min-width="column.minWidth"
        :prop="column.prop"
        :width="column.width"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          <ApsStatusTag v-if="isStatusColumn(column)" :value="row[column.prop]" />
          <span v-else>{{ showValue(row, column) }}</span>
        </template>
      </el-table-column>

      <el-table-column
        v-if="deletable || normalizedActions.length"
        fixed="right"
        label="操作"
        :width="operationWidth"
      >
        <template #default="{ row }">
          <div class="row-actions">
            <el-button
              v-for="action in normalizedActions"
              :key="action.name"
              :disabled="actionDisabled(action, row)"
              :type="actionType(action)"
              link
              @click="emit('rowAction', action.name, row)"
            >
              {{ action.label }}
            </el-button>
            <el-button v-if="deletable" type="danger" link @click="emit('deleteRow', row.id)">
              {{ actionLabel }}
            </el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
  </section>
</template>
