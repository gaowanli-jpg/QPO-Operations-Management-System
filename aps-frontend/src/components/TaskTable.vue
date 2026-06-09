<script setup>
import { field, label, number, statusClass, taskOrderText } from '../helpers'

defineProps({
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
  }
})

const emit = defineEmits(['select', 'toggleSelect', 'toggleSelectAll', 'deleteSelected'])
</script>

<template>
  <section class="panel">
    <div class="section-head">
      <h2>{{ title }}</h2>
      <span>{{ tasks.length }} 条</span>
      <div v-if="deletable" class="section-actions">
        <button
          class="text-button danger"
          type="button"
          :disabled="selectedIds.length === 0"
          @click="emit('deleteSelected')"
        >
          批量删除 {{ selectedIds.length || '' }}
        </button>
      </div>
    </div>
    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th v-if="selectable" class="check-col">
              <input
                type="checkbox"
                :checked="tasks.length > 0 && selectedIds.length === tasks.length"
                :disabled="tasks.length === 0"
                @change="emit('toggleSelectAll', $event.target.checked)"
              />
            </th>
            <th>任务</th>
            <th>客户</th>
            <th>图号 / 版本</th>
            <th>需生产</th>
            <th>交期</th>
            <th>材料</th>
            <th>工时</th>
            <th>零件库存</th>
            <th>材料库存</th>
            <th>PMC</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="task in tasks" :key="field(task, 'id')">
            <td v-if="selectable" class="check-col">
              <input
                type="checkbox"
                :checked="selectedIds.includes(field(task, 'id'))"
                @change="emit('toggleSelect', field(task, 'id'), $event.target.checked)"
              />
            </td>
            <td>
              <strong>#{{ field(task, 'id') }}</strong>
              <small>{{ taskOrderText(task) }}</small>
            </td>
            <td>{{ field(task, 'customer_name', 'customerName') || '-' }}</td>
            <td>
              <strong>{{ field(task, 'part_no', 'partNo') }}</strong>
              <small>{{ field(task, 'version') || '-' }}</small>
            </td>
            <td>{{ number(field(task, 'qty'), 0) }}</td>
            <td>{{ field(task, 'delivery_date', 'deliveryDate') || '-' }}</td>
            <td>
              <strong>{{ field(task, 'material_name', 'materialName') || '-' }}</strong>
              <small>{{ field(task, 'material_type', 'materialType') || '-' }}</small>
            </td>
            <td>{{ number(field(task, 'total_work_hours', 'totalWorkHours'), 2) }}</td>
            <td>
              <span class="pill" :class="statusClass(field(task, 'part_stock_status', 'partStockStatus'))">
                {{ label(field(task, 'part_stock_status', 'partStockStatus')) }}
              </span>
            </td>
            <td>
              <span class="pill" :class="statusClass(field(task, 'material_stock_status', 'materialStockStatus'))">
                {{ label(field(task, 'material_stock_status', 'materialStockStatus')) }}
              </span>
            </td>
            <td>
              <span class="pill" :class="statusClass(field(task, 'assignment_status', 'assignmentStatus'))">
                {{ label(field(task, 'assignment_status', 'assignmentStatus')) }}
              </span>
            </td>
            <td>
              <button class="text-button" type="button" @click="$emit('select', field(task, 'id'))">详情</button>
            </td>
          </tr>
          <tr v-if="tasks.length === 0">
            <td class="empty" :colspan="selectable ? 12 : 11">暂无 APS 任务</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>
