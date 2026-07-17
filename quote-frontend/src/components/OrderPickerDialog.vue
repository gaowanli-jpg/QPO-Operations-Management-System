<template>
  <el-dialog v-model="visible" :title="title" width="900px" @closed="resetPicker">
    <div class="picker-step">
      <div class="step-title">1. 选择客户</div>
      <el-select
        v-model="selectedCustomer"
        filterable
        clearable
        placeholder="请选择客户名称"
        class="full-width"
        :loading="loadingCustomers"
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
          :label="status"
          :value="status"
        />
      </el-select>
    </div>

    <div class="picker-step">
      <div class="step-title">3. 选择 RFQ 订单</div>
      <el-empty v-if="!selectedStatus" description="请先选择订单状态" :image-size="72" />
      <template v-else>
        <el-input
          v-model="keyword"
          clearable
          placeholder="搜索 RFQ 号"
          class="keyword-input"
        />
        <el-table
          :data="orders"
          border
          stripe
          height="360"
          v-loading="loadingOrders"
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
        <div class="erp-pagination">
          <el-pagination
            v-model:current-page="page"
            v-model:page-size="pageSize"
            :page-sizes="[20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            :pager-count="7"
            :total="total"
            background
          />
        </div>
      </template>
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
import { ElMessage } from 'element-plus'
import { listMarketOrders } from '../services/marketApi'
import { listCustomers } from '../services/orderApi'

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
const keyword = ref('')
const customerOptions = ref([])
const orders = ref([])
const selectedOrder = ref(null)
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const loadingCustomers = ref(false)
const loadingOrders = ref(false)
let keywordTimer = null

const statusOptions = computed(() => {
  const allowed = props.allowedStatuses.filter(Boolean)
  return allowed.length > 0 ? allowed : DEFAULT_STATUS_OPTIONS
})

watch(selectedCustomer, () => {
  selectedStatus.value = ''
  selectedOrderId.value = null
  selectedOrder.value = null
  resetPageAndRows()
})

watch(selectedStatus, () => {
  selectedOrderId.value = null
  selectedOrder.value = null
  resetPageAndRows()
  if (selectedStatus.value) {
    loadOrders()
  }
})

watch(keyword, () => {
  clearTimeout(keywordTimer)
  keywordTimer = setTimeout(() => {
    page.value = 1
    loadOrders()
  }, 300)
})

watch([page, pageSize], () => {
  if (selectedCustomer.value && selectedStatus.value) {
    loadOrders()
  }
})

watch(visible, value => {
  if (value) {
    loadCustomers()
  }
})

const submitOrder = () => {
  const order = selectedOrder.value || orders.value.find(item => item.id === selectedOrderId.value)
  if (!order) return
  emit('select', order)
  visible.value = false
}

const resetPicker = () => {
  selectedCustomer.value = ''
  selectedStatus.value = ''
  selectedOrderId.value = null
  selectedOrder.value = null
  keyword.value = ''
  resetPageAndRows()
}

const selectOrder = (order) => {
  selectedOrderId.value = order.id
  selectedOrder.value = order
}

const resetPageAndRows = () => {
  page.value = 1
  total.value = 0
  orders.value = []
}

const loadCustomers = async () => {
  loadingCustomers.value = true
  try {
    const response = await listCustomers()
    customerOptions.value = response || []
  } catch (error) {
    console.error(error)
    ElMessage.error('客户列表加载失败。')
  } finally {
    loadingCustomers.value = false
  }
}

const loadOrders = async () => {
  if (!selectedCustomer.value || !selectedStatus.value) {
    orders.value = []
    total.value = 0
    return
  }
  loadingOrders.value = true
  try {
    const response = await listMarketOrders({
      customerName: selectedCustomer.value,
      status: selectedStatus.value,
      keyword: keyword.value.trim() || undefined,
      page: page.value,
      pageSize: pageSize.value
    })
    orders.value = response.records || []
    total.value = response.total || 0
  } catch (error) {
    console.error(error)
    ElMessage.error('订单列表加载失败。')
  } finally {
    loadingOrders.value = false
  }
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
.keyword-input { width: 260px; margin-bottom: 10px; }
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
