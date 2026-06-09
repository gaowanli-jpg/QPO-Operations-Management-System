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
  }
})

defineEmits(['deleteRow'])

const showValue = (row, key) => {
  const value = row[key]
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
            <th v-if="deletable">操作</th>
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
            <td v-if="deletable">
              <button class="text-button danger" type="button" @click="$emit('deleteRow', row.id)">删除</button>
            </td>
          </tr>
          <tr v-if="rows.length === 0">
            <td class="empty" :colspan="columns.length + (deletable ? 1 : 0)">暂无数据</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>
