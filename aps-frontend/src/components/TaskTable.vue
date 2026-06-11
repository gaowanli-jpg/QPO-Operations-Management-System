<script setup>
import { computed } from 'vue'
import { field, number, taskOrderText } from '../helpers'
import ApsStatusTag from './ApsStatusTag.vue'

const props = defineProps({
  title: {
    type: String,
    default: 'APS 任务'
  },
  tasks: {
    type: Array,
    default: () => []
  },
  selectedIds: {
    type: Array,
    default: () => []
  },
  selectable: {
    type: Boolean,
    default: true
  },
  deletable: {
    type: Boolean,
    default: true
  },
  showProcessGroup: {
    type: Boolean,
    default: false
  },
  showDepartment: {
    type: Boolean,
    default: false
  },
  processSortMode: {
    type: String,
    default: ''
  },
  actionLabel: {
    type: String,
    default: '详情'
  },
  bulkAssignable: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['select', 'toggleSelect', 'toggleSelectAll', 'deleteSelected', 'bulkAssign'])

const processGroupText = (task) => {
  if (!field(task, 'process_route_text', 'processRouteText')) return '-'
  if (props.processSortMode === 'PROCESS_MATCH') {
    return `最多相同 ${number(field(task, 'process_match_count', 'processMatchCount') || 0, 0)} 道`
  }
  return `同路线 ${number(field(task, 'process_group_count', 'processGroupCount') || 1, 0)} 件`
}

const taskId = (task) => field(task, 'id')
const visibleTaskIds = computed(() => props.tasks.map(taskId).filter(Boolean))
const selectedVisibleCount = computed(() => visibleTaskIds.value.filter((id) => props.selectedIds.includes(id)).length)
const allVisibleSelected = computed(() => visibleTaskIds.value.length > 0 && selectedVisibleCount.value === visibleTaskIds.value.length)
const selectionIndeterminate = computed(() => selectedVisibleCount.value > 0 && selectedVisibleCount.value < visibleTaskIds.value.length)

const assignmentStatus = (task) => props.showDepartment
  ? field(task, 'department_task_status', 'departmentTaskStatus')
  : field(task, 'assignment_status', 'assignmentStatus')
</script>

<template>
  <section class="panel">
    <div class="section-head">
      <h2>{{ title }}</h2>
      <span>{{ tasks.length }} 条</span>
      <div v-if="deletable || bulkAssignable" class="section-actions">
        <el-button
          v-if="bulkAssignable"
          type="primary"
          link
          :disabled="selectedIds.length === 0"
          @click="emit('bulkAssign')"
        >
          批量分配 {{ selectedIds.length || '' }}
        </el-button>
        <el-button
          v-if="deletable"
          type="danger"
          link
          :disabled="selectedIds.length === 0"
          @click="emit('deleteSelected')"
        >
          批量删除 {{ selectedIds.length || '' }}
        </el-button>
      </div>
    </div>
    <el-table
      :data="tasks"
      :row-key="taskId"
      border
      class="aps-task-table"
      empty-text="暂无 APS 任务"
      stripe
    >
      <el-table-column
        v-if="selectable"
        align="center"
        fixed="left"
        width="48"
      >
        <template #header>
          <el-checkbox
            :disabled="tasks.length === 0"
            :indeterminate="selectionIndeterminate"
            :model-value="allVisibleSelected"
            @change="emit('toggleSelectAll', $event)"
          />
        </template>
        <template #default="{ row: task }">
          <el-checkbox
            :model-value="selectedIds.includes(taskId(task))"
            @change="emit('toggleSelect', taskId(task), $event)"
          />
        </template>
      </el-table-column>

      <el-table-column fixed="left" label="任务" min-width="118">
        <template #default="{ row: task }">
          <div class="cell-stack">
            <strong>#{{ field(task, 'id') }}</strong>
            <small>{{ taskOrderText(task) }}</small>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="客户" min-width="130">
        <template #default="{ row: task }">
          {{ field(task, 'customer_name', 'customerName') || '-' }}
        </template>
      </el-table-column>
      <el-table-column v-if="showDepartment" label="部门" min-width="130">
        <template #default="{ row: task }">
          {{ field(task, 'assigned_department_name', 'assignedDepartmentName') || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="图号 / 版本" min-width="142">
        <template #default="{ row: task }">
          <div class="cell-stack">
            <strong>{{ field(task, 'part_no', 'partNo') }}</strong>
            <small>{{ field(task, 'version') || '-' }}</small>
          </div>
        </template>
      </el-table-column>
      <el-table-column align="right" label="需生产" width="88">
        <template #default="{ row: task }">
          {{ number(field(task, 'qty'), 0) }}
        </template>
      </el-table-column>
      <el-table-column label="交期" min-width="108">
        <template #default="{ row: task }">
          {{ field(task, 'delivery_date', 'deliveryDate') || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="材料" min-width="150">
        <template #default="{ row: task }">
          <div class="cell-stack">
            <strong>{{ field(task, 'material_name', 'materialName') || '-' }}</strong>
            <small>{{ field(task, 'material_type', 'materialType') || '-' }}</small>
          </div>
        </template>
      </el-table-column>
      <el-table-column v-if="showProcessGroup" label="工艺组合" min-width="118">
        <template #default="{ row: task }">
          <ApsStatusTag :value="processGroupText(task)" tone="muted" />
        </template>
      </el-table-column>
      <el-table-column align="right" label="工时" width="88">
        <template #default="{ row: task }">
          {{ number(field(task, 'total_work_hours', 'totalWorkHours'), 2) }}
        </template>
      </el-table-column>
      <el-table-column label="零件库存" min-width="110">
        <template #default="{ row: task }">
          <ApsStatusTag :value="field(task, 'part_stock_status', 'partStockStatus')" />
        </template>
      </el-table-column>
      <el-table-column label="材料库存" min-width="110">
        <template #default="{ row: task }">
          <ApsStatusTag :value="field(task, 'material_stock_status', 'materialStockStatus')" />
        </template>
      </el-table-column>
      <el-table-column :label="showDepartment ? '部门任务' : 'PMC'" min-width="110">
        <template #default="{ row: task }">
          <ApsStatusTag :value="assignmentStatus(task)" />
        </template>
      </el-table-column>
      <el-table-column fixed="right" label="操作" width="86">
        <template #default="{ row: task }">
          <el-button type="primary" link @click="emit('select', taskId(task))">{{ actionLabel }}</el-button>
        </template>
      </el-table-column>
    </el-table>
  </section>
</template>
