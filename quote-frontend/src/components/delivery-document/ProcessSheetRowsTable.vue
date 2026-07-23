<template>
  <div class="erp-table-surface release-table-panel ticket-panel process-sheet-rows-table">
    <div class="erp-table-header panel-title-row">
      <div class="panel-title">已生成工艺表行</div>
      <el-button
        v-permission="'market:delivery-document:export'"
        size="small"
        plain
        class="workflow-soft-button"
        :disabled="!exportable"
        @click="$emit('export')"
      >
        导出工艺表 Excel
      </el-button>
    </div>
    <el-table
      :data="rows"
      border
      stripe
      height="260"
      v-loading="loading"
      :empty-text="emptyText"
    >
      <el-table-column prop="internalProjectNo" label="内部项目号" width="130" show-overflow-tooltip />
      <el-table-column prop="itemType" label="类型" width="90" align="center" />
      <el-table-column prop="partNo" label="图号" width="130" show-overflow-tooltip />
      <el-table-column prop="version" label="版本" width="80" align="center" />
      <el-table-column prop="description" label="材料描述" min-width="160" show-overflow-tooltip />
      <el-table-column prop="qty" label="数量" width="70" align="center" />
      <el-table-column prop="engineeringMaterialName" label="材料" width="120" show-overflow-tooltip />
      <el-table-column label="毛重(kg)" width="95" align="right">
        <template #default="{ row }">{{ formatWeight(row.materialGrossWeight) }}</template>
      </el-table-column>
      <el-table-column label="净重(kg)" width="95" align="right">
        <template #default="{ row }">{{ formatWeight(row.partNetWeight) }}</template>
      </el-table-column>
      <el-table-column prop="surfaceTreatment" label="表面处理" width="130" show-overflow-tooltip />
      <el-table-column prop="deliveryDate" label="交期" width="110" />
      <el-table-column label="工艺路线" min-width="220" show-overflow-tooltip>
        <template #default="scope">
          {{ formatRoute(scope.row.processRoute) }}
        </template>
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
    default: '暂无已生成工艺表行'
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

const formatRoute = (route) => {
  if (!route) return '-'
  let list = Array.isArray(route) ? route : []
  if (typeof route === 'string') {
    try {
      list = JSON.parse(route)
    } catch {
      list = []
    }
  }
  if (list.length === 0) return '-'
  return list
    .map(item => `${item.processName || ''}${item.inputVal ? `(${item.inputVal})` : ''}`)
    .filter(Boolean)
    .join(' / ')
}

const formatWeight = (value) => {
  const number = Number(value)
  if (!Number.isFinite(number)) return '-'
  return number.toFixed(3).replace(/\.?0+$/, '')
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
