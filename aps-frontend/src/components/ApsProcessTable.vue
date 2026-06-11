<script setup>
import { number } from '../helpers'

defineProps({
  rows: {
    type: Array,
    default: () => []
  },
  emptyText: {
    type: String,
    default: '暂无工艺'
  }
})
</script>

<template>
  <el-table
    :data="rows"
    border
    class="aps-process-table"
    :empty-text="emptyText"
    row-key="id"
    stripe
  >
    <el-table-column label="顺序" width="72">
      <template #default="{ row }">{{ row.sort_no || '-' }}</template>
    </el-table-column>
    <el-table-column label="工艺" min-width="130">
      <template #default="{ row }">{{ row.process_name }}</template>
    </el-table-column>
    <el-table-column label="类型" min-width="110">
      <template #default="{ row }">{{ row.calc_type || row.source_type || '-' }}</template>
    </el-table-column>
    <el-table-column align="right" label="工时" width="92">
      <template #default="{ row }">{{ number(row.work_hours, 2) }}</template>
    </el-table-column>
    <el-table-column align="right" label="费用" width="92">
      <template #default="{ row }">{{ number(row.line_cost, 2) }}</template>
    </el-table-column>
  </el-table>
</template>
