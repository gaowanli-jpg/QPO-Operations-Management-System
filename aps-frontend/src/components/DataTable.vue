<script setup>
import { label, statusClass } from '../helpers'

defineProps({
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

defineEmits(['deleteRow', 'rowAction'])

const showValue = (row, key) => {
  const value = row[key]
  if (key === 'customer_project_no' && (value === undefined || value === null || value === '')) return '空'
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
</script>

<template>
  <section class="panel">
    <div class="section-head">
      <h2>{{ title }}</h2>
      <span>{{ rows.length }} 条</span>
    </div>
    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th v-for="column in columns" :key="column[0]">{{ column[1] }}</th>
            <th v-if="deletable || actions.length" class="action-col">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in rows" :key="row.id">
            <td v-for="column in columns" :key="column[0]">
              <span v-if="isStatus(column[0])" class="pill" :class="statusClass(row[column[0]])">
                {{ label(row[column[0]]) }}
              </span>
              <span v-else>{{ showValue(row, column[0]) }}</span>
            </td>
            <td v-if="deletable || actions.length" class="action-cell">
              <div class="row-actions">
                <button
                  v-for="action in actions"
                  :key="action[0]"
                  :class="['row-action', action[2] || '']"
                  type="button"
                  @click="$emit('rowAction', action[0], row)"
                >
                  {{ action[1] }}
                </button>
                <button v-if="deletable" class="row-action danger" type="button" @click="$emit('deleteRow', row.id)">{{ actionLabel }}</button>
              </div>
            </td>
          </tr>
          <tr v-if="rows.length === 0">
            <td class="empty" :colspan="columns.length + (deletable || actions.length ? 1 : 0)">暂无数据</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>
