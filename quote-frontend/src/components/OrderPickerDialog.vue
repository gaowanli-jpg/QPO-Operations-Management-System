<template>
  <el-dialog v-model="visible" :title="title" width="900px" @closed="resetPicker">
    <div class="picker-step">
      <div class="step-title">1. 选择客户</div>
      <el-button
        v-permission="customerSelectPermission"
        class="customer-picker-trigger full-width"
        :disabled="Boolean(lockedCustomerName)"
        @click="customerPickerVisible = true"
      >
        <span class="customer-picker-content">
          <span
            :class="selectedCustomer ? 'customer-picker-text' : 'customer-picker-placeholder'"
            :title="selectedCustomer || '请选择客户名称'"
          >
            {{ selectedCustomer || '请选择客户名称' }}
          </span>
          <span class="customer-picker-action">{{ lockedCustomerName ? '已锁定' : '选择客户' }}</span>
        </span>
      </el-button>
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
                :type="isOrderSelected(row) ? 'success' : 'primary'"
                size="small"
                :plain="!isOrderSelected(row)"
                :disabled="isOrderAlreadySelected(row)"
                @click.stop="selectOrder(row)"
              >
                {{ isOrderSelected(row) ? '已选择' : '选择' }}
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
            @size-change="page = 1"
          />
        </div>
      </template>
    </div>

    <CustomerPickerDialog
      v-model="customerPickerVisible"
      :current-customer="selectedCustomer"
      :customer-scope="customerScope"
      @select="handleCustomerSelected"
    />

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :disabled="multiple ? pendingSelectedOrderCount === 0 : !selectedOrderId" @click="submitOrder">
        {{ multiple ? '添加订单' : '确定订单' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import CustomerPickerDialog from './CustomerPickerDialog.vue'
import { listMarketOrders } from '../services/marketApi'
import { listSetupOrders } from '../services/engineerApi'

const CUSTOMER_PERMISSION_BY_SCOPE = {
  'market-rfq': 'market:rfq:customer-select',
  'quote-process': 'market:quote-process:customer-select',
  release: 'market:release:customer-select',
  'delivery-document': 'market:delivery-document:customer-select',
  'engineer-setup': 'engineer:setup:customer-select',
  'market-order': 'market:order:customer-select',
  'history-order': 'market:history-order:customer-select'
}

const DEFAULT_STATUS_OPTIONS = ['未核价', '已核价', '已报价', '部分已提单', '已生成提单']

const STATUS_OPTIONS_BY_SCOPE = {
  'delivery-document': ['部分已提单', '已生成提单'],
  'engineer-setup': ['未核价']
}

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
  },
  customerScope: {
    type: String,
    default: 'quote-process'
  },
  orderScope: {
    type: String,
    default: 'quote-process'
  },
  multiple: {
    type: Boolean,
    default: false
  },
  lockedCustomerName: {
    type: String,
    default: ''
  },
  selectedOrderIds: {
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
const orders = ref([])
const selectedOrder = ref(null)
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const loadingOrders = ref(false)
const customerPickerVisible = ref(false)
const pendingSelectedOrders = reactive({})
let keywordTimer = null

const multiple = computed(() => props.multiple)
const lockedCustomerName = computed(() => props.lockedCustomerName || '')
const pendingSelectedOrderCount = computed(() => Object.keys(pendingSelectedOrders).length)
const orderSourceKey = (order) => `${order?.sourceType || 'RFQ'}:${order?.sourceOrderId ?? order?.id ?? ''}`

const statusOptions = computed(() => {
  const allowed = props.allowedStatuses.filter(Boolean)
  return allowed.length > 0 ? allowed : (STATUS_OPTIONS_BY_SCOPE[props.orderScope] || DEFAULT_STATUS_OPTIONS)
})

const customerSelectPermission = computed(() => (
  CUSTOMER_PERMISSION_BY_SCOPE[props.customerScope] || CUSTOMER_PERMISSION_BY_SCOPE['quote-process']
))

watch(selectedCustomer, () => {
  selectedStatus.value = ''
  selectedOrderId.value = null
  selectedOrder.value = null
  clearPendingSelectedOrders()
  resetPageAndRows()
})

watch(selectedStatus, () => {
  selectedOrderId.value = null
  selectedOrder.value = null
  clearPendingSelectedOrders()
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

watch(
  () => [props.modelValue, props.lockedCustomerName],
  ([isVisible, customerName]) => {
    if (isVisible && customerName) {
      selectedCustomer.value = customerName
    }
  },
  { immediate: true }
)

const submitOrder = () => {
  if (props.multiple) {
    const selected = Object.values(pendingSelectedOrders)
    if (selected.length === 0) return
    emit('select', Object.values(pendingSelectedOrders))
    visible.value = false
    return
  }
  const order = selectedOrder.value || orders.value.find(item => orderSourceKey(item) === selectedOrderId.value)
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
  clearPendingSelectedOrders()
  resetPageAndRows()
}

const selectOrder = (order) => {
  const key = orderSourceKey(order)
  if (props.multiple) {
    if (isOrderAlreadySelected(order)) return
    if (pendingSelectedOrders[key]) {
      delete pendingSelectedOrders[key]
    } else {
      pendingSelectedOrders[orderSourceKey(order)] = order
    }
    return
  }
  selectedOrderId.value = key
  selectedOrder.value = order
}

const clearPendingSelectedOrders = () => {
  Object.keys(pendingSelectedOrders).forEach(id => delete pendingSelectedOrders[id])
}

const resetPageAndRows = () => {
  page.value = 1
  total.value = 0
  orders.value = []
}

const resolveCustomerName = (customer) => typeof customer === 'string' ? customer : (customer?.customerName || '')

const handleCustomerSelected = (customer) => {
  selectedCustomer.value = resolveCustomerName(customer)
}

const loadOrders = async () => {
  if (!selectedCustomer.value || !selectedStatus.value) {
    orders.value = []
    total.value = 0
    return
  }
  loadingOrders.value = true
  try {
    const params = {
      customerName: selectedCustomer.value,
      status: selectedStatus.value,
      keyword: keyword.value.trim() || undefined,
      page: page.value,
      pageSize: pageSize.value
    }
    const response = props.orderScope === 'engineer-setup'
      ? await listSetupOrders(params)
      : await listMarketOrders(params, props.orderScope)
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
  return isOrderSelected(row) ? 'selected-order-row' : ''
}

const isOrderAlreadySelected = (row) => {
  return props.selectedOrderIds.includes(orderSourceKey(row))
}

const isOrderSelected = (row) => {
  const key = orderSourceKey(row)
  return props.multiple
    ? isOrderAlreadySelected(row) || Boolean(pendingSelectedOrders[key])
    : key === selectedOrderId.value
}

const statusTagType = (status) => {
  if (status === '已生成提单') return 'success'
  if (status === '已报价') return 'primary'
  if (status === '已核价') return 'warning'
  if (status === '部分已提单') return 'warning'
  if (status === '未核价') return 'info'
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
.customer-picker-trigger {
  min-height: 40px;
  padding: 0 12px;
  text-align: left;
}
.customer-picker-trigger :deep(> span) {
  display: block;
  width: 100%;
}
.customer-picker-content {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  min-width: 0;
}
.customer-picker-text,
.customer-picker-placeholder {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.customer-picker-placeholder {
  color: var(--erp-text-secondary);
}
.customer-picker-action {
  flex: none;
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding-left: 12px;
  border-left: 1px solid var(--erp-border-soft);
  color: var(--erp-primary);
  font-size: 12px;
  font-weight: 700;
}
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
