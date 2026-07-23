<template>
  <div class="erp-table-surface release-table-panel ticket-panel delivery-ticket-rows-table">
    <div class="erp-table-header panel-title-row">
      <div class="panel-title">已生成提单行</div>
      <el-button
        v-permission="'market:delivery-document:export'"
        size="small"
        plain
        class="workflow-soft-button"
        :disabled="!exportable"
        @click="$emit('export')"
      >
        导出提单 Excel
      </el-button>
    </div>
    <el-table
      :data="rows"
      border
      stripe
      height="280"
      v-loading="loading"
      :empty-text="emptyText"
    >
      <el-table-column prop="internalProjectNo" label="内部项目号" width="130" show-overflow-tooltip />
      <el-table-column prop="orderType" label="订单类型" width="100" align="center" show-overflow-tooltip />
      <el-table-column prop="itemType" label="类型" width="90" align="center" />
      <el-table-column prop="partNo" label="图号" width="130" show-overflow-tooltip />
      <el-table-column prop="version" label="版本" width="80" align="center" />
      <el-table-column prop="qty" label="数量" width="70" align="center" />
      <el-table-column prop="engineeringMaterialName" label="材料" width="110" show-overflow-tooltip />
      <el-table-column prop="surfaceTreatment" label="表面处理" width="110" show-overflow-tooltip />
      <el-table-column prop="startDate" label="开工日期" width="110" />
      <el-table-column prop="deliveryDate" label="交货日期" width="110" />
      <el-table-column label="安排类型" width="100">
        <template #default="{ row }">{{ displayTicketText(row.arrangeType) }}</template>
      </el-table-column>
      <el-table-column label="备注" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ displayTicketText(row.remark) }}</template>
      </el-table-column>
    </el-table>
    <div v-if="total" class="erp-pagination">
      <el-pagination
        :current-page="page"
        :page-size="pageSize"
        :page-sizes="CLIENT_PAGE_SIZES"
        :pager-count="7"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @current-change="handlePageChange"
        @size-change="handlePageSizeChange"
      />
    </div>
  </div>
</template>

<script setup>
import { CLIENT_PAGE_SIZES } from '../../utils/clientPagination'

defineProps({
  rows: {
    type: Array,
    default: () => []
  },
  total: {
    type: Number,
    default: 0
  },
  page: {
    type: Number,
    default: 1
  },
  pageSize: {
    type: Number,
    default: 20
  },
  loading: {
    type: Boolean,
    default: false
  },
  exportable: {
    type: Boolean,
    default: false
  },
  emptyText: {
    type: String,
    default: '暂无已生成提单行'
  }
})

const emit = defineEmits(['page-change', 'page-size-change', 'export'])

const handlePageChange = (value) => {
  emit('page-change', value)
}

const handlePageSizeChange = (value) => {
  emit('page-size-change', value)
  emit('page-change', 1)
}

const displayTicketText = (value) => {
  return String(value || '').replaceAll('待产', '在产')
}
</script>

<style scoped>
.release-table-panel {
  padding: 14px;
}

.panel-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.panel-title {
  margin-bottom: 0;
  padding-left: 10px;
  border-left: 3px solid var(--erp-primary);
  color: var(--erp-text);
  font-size: 14px;
  font-weight: 700;
  line-height: 1.2;
}

.ticket-panel {
  margin-top: 14px;
}
</style>
