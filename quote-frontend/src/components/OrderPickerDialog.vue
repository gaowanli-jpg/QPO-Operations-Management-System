<template>
  <el-dialog v-model="visible" :title="title" width="860px" @closed="resetPicker">
    <div class="picker-step">
      <div class="step-title">1. 选择客户</div>
      <el-select
        v-model="selectedCustomer"
        filterable
        clearable
        placeholder="请选择客户名称"
        class="full-width"
      >
        <el-option
          v-for="customer in customerOptions"
          :key="customer"
          :label="customer"
          :value="customer"
        />
      </el-select>
    </div>

    <div class="picker-step">
      <div class="step-title">2. 选择订单状态</div>
      <el-select
        v-model="selectedStatus"
        filterable
        placeholder="请先选择客户"
        class="full-width"
        :disabled="!selectedCustomer"
      >
        <el-option
          v-for="status in statusOptions"
          :key="status"
          :label="`${status}（${statusCounts[status] || 0}）`"
          :value="status"
        />
      </el-select>
    </div>

    <div class="picker-step">
      <div class="step-title">3. 选择 RFQ 订单</div>
      <el-empty v-if="!selectedStatus" description="请先选择订单状态" :image-size="72" />
      <el-table
        v-else
        :data="filteredOrders"
        border
        stripe
        height="360"
        highlight-current-row
        class="order-table"
        :row-class-name="resolveRowClassName"
        @row-click="selectOrder"
      >
        <el-table-column prop="rfqNo" label="RFQ号" min-width="170" fixed show-overflow-tooltip />
        <el-table-column prop="customerName" label="客户" min-width="150" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ row.status || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              :type="row.id === selectedOrderId ? 'success' : 'primary'"
              size="small"
              :plain="row.id !== selectedOrderId"
              @click.stop="selectOrder(row)"
            >
              {{ row.id === selectedOrderId ? '已选择' : '选择' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :disabled="!selectedOrderId" @click="submitOrder">
        确定订单
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

const DEFAULT_STATUS_OPTIONS = ['待工程部评估', '已报价', '部分已提单', '已生成提单']

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  title: {
    type: String,
    default: '选择订单'
  },
  orders: {
    type: Array,
    default: () => []
  },
  allowedStatuses: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['update:modelValue', 'select'])

const visible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value)
})

const selectedCustomer = ref('')
const selectedStatus = ref('')
const selectedOrderId = ref(null)

const statusOptions = computed(() => {
  const allowed = props.allowedStatuses.filter(Boolean)
  return allowed.length > 0 ? allowed : DEFAULT_STATUS_OPTIONS
})

const customerOptions = computed(() => {
  const names = props.orders
    .map(order => order.customerName || '未填写客户')
    .filter(Boolean)
  return [...new Set(names)].sort((a, b) => a.localeCompare(b, 'zh-Hans-CN'))
})

const customerOrders = computed(() => {
  if (!selectedCustomer.value) return []
  return props.orders.filter(order => (order.customerName || '未填写客户') === selectedCustomer.value)
})

const statusCounts = computed(() => {
  return customerOrders.value.reduce((result, order) => {
    const status = order.status || ''
    result[status] = (result[status] || 0) + 1
    return result
  }, {})
})

const filteredOrders = computed(() => {
  if (!selectedCustomer.value || !selectedStatus.value) return []
  return customerOrders.value.filter(order => order.status === selectedStatus.value)
})

watch(selectedCustomer, () => {
  selectedStatus.value = ''
  selectedOrderId.value = null
})

watch(selectedStatus, () => {
  selectedOrderId.value = null
})

const submitOrder = () => {
  const order = props.orders.find(item => item.id === selectedOrderId.value)
  if (!order) return
  emit('select', order)
  visible.value = false
}

const resetPicker = () => {
  selectedCustomer.value = ''
  selectedStatus.value = ''
  selectedOrderId.value = null
}

const selectOrder = (order) => {
  selectedOrderId.value = order.id
}

const resolveRowClassName = ({ row }) => {
  return row.id === selectedOrderId.value ? 'selected-order-row' : ''
}

const statusTagType = (status) => {
  if (status === '已生成提单') return 'success'
  if (status === '已报价') return 'primary'
  if (status === '部分已提单') return 'warning'
  return 'info'
}

const formatDateTime = (value) => {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}
</script>

<style scoped>
.picker-step { margin-bottom: 18px; }
.step-title { font-weight: 700; color: #303133; margin-bottom: 10px; }
.full-width { width: 100%; }
.order-table :deep(.selected-order-row > td.el-table__cell) {
  background: #d9ecff !important;
}
.order-table :deep(.selected-order-row:hover > td.el-table__cell) {
  background: #c6e2ff !important;
}
.order-table :deep(.selected-order-row .el-button) {
  font-weight: 700;
}
</style>
