<script setup>
defineProps({
  page: {
    type: Number,
    required: true
  },
  pageSize: {
    type: Number,
    required: true
  },
  total: {
    type: Number,
    default: 0
  },
  disabled: {
    type: Boolean,
    default: false
  },
  pageSizes: {
    type: Array,
    default: () => [20, 50, 100]
  }
})

const emit = defineEmits(['currentChange', 'sizeChange'])
</script>

<template>
  <div class="aps-pager">
    <el-pagination
      background
      :current-page="page"
      :disabled="disabled"
      :page-size="pageSize"
      :page-sizes="pageSizes"
      :pager-count="5"
      :total="total"
      layout="total, prev, pager, next, sizes"
      @current-change="emit('currentChange', $event)"
      @size-change="emit('sizeChange', $event)"
    />
  </div>
</template>
