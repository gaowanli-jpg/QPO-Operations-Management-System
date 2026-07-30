<template>
  <el-dialog
    v-model="visible"
    :title="title"
    width="820px"
    class="customer-picker-dialog"
    @open="loadCustomers"
    @closed="resetSearch"
  >
    <div class="customer-picker-toolbar">
      <el-select v-model="field" class="customer-picker-field" placeholder="筛选字段">
        <el-option label="全部字段" value="all" />
        <el-option label="客户编码" value="code" />
        <el-option label="客户简称" value="shortName" />
        <el-option label="客户名称" value="name" />
      </el-select>
      <el-input
        v-model="keyword"
        clearable
        placeholder="输入客户编码 / 简称 / 名称"
        class="customer-picker-search"
      />
      <span class="customer-picker-count">共 {{ customers.length }} 个客户</span>
    </div>

    <el-table
      :data="customers"
      border
      stripe
      height="360"
      v-loading="loading"
      highlight-current-row
      class="customer-picker-table"
      :row-class-name="resolveRowClassName"
      @row-click="selectCustomer"
    >
      <el-table-column type="index" label="#" width="64" align="center" />
      <el-table-column prop="customerCode" label="客户编码" min-width="150" show-overflow-tooltip />
      <el-table-column prop="customerShortName" label="客户简称" min-width="150" show-overflow-tooltip />
      <el-table-column prop="customerName" label="客户名称" min-width="220" show-overflow-tooltip />
      <el-table-column label="操作" width="110" align="center" fixed="right">
        <template #default="{ row }">
          <el-button
            :type="isCurrentCustomer(row) ? 'success' : 'primary'"
            size="small"
            :plain="!isCurrentCustomer(row)"
            @click.stop="selectCustomer(row)"
          >
            {{ isCurrentCustomer(row) ? '已选择' : '选择' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <template #footer>
      <div class="erp-dialog-footer">
        <el-button @click="visible = false">取消</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { listCustomers } from '../services/orderApi'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  title: {
    type: String,
    default: '选择客户'
  },
  currentCustomer: {
    type: String,
    default: ''
  },
  customerScope: {
    type: String,
    default: 'market-order'
  }
})

const emit = defineEmits(['update:modelValue', 'select'])

const visible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value)
})

const customers = ref([])
const field = ref('all')
const keyword = ref('')
const loading = ref(false)
let searchTimer = null

const customerQueryParams = () => ({
  field: field.value,
  keyword: keyword.value.trim() || undefined
})

const normalizeCustomer = (customer) => {
  if (typeof customer === 'string') {
    return {
      customerCode: customer,
      customerShortName: customer,
      customerName: customer
    }
  }
  return {
    id: customer?.id,
    customerCode: customer?.customerCode || customer?.code || customer?.customerName || '',
    customerShortName: customer?.customerShortName || customer?.shortName || customer?.customerName || '',
    customerName: customer?.customerName || customer?.name || customer?.customerShortName || customer?.customerCode || ''
  }
}

const loadCustomers = async () => {
  loading.value = true
  try {
    const response = await listCustomers(props.customerScope, customerQueryParams())
    customers.value = (response || []).map(normalizeCustomer).filter(customer => customer.customerName)
  } catch (error) {
    console.error(error)
    ElMessage.error('客户列表加载失败。')
  } finally {
    loading.value = false
  }
}

watch([field, keyword], () => {
  if (!visible.value) return
  clearTimeout(searchTimer)
  searchTimer = setTimeout(loadCustomers, 250)
})

const resetSearch = () => {
  field.value = 'all'
  keyword.value = ''
  customers.value = []
  clearTimeout(searchTimer)
}

const selectCustomer = (row) => {
  const customer = normalizeCustomer(row)
  if (!customer.customerName) return
  emit('select', customer)
  visible.value = false
}

const isCurrentCustomer = (row) => {
  const customer = normalizeCustomer(row)
  return Boolean(props.currentCustomer) && customer.customerName === props.currentCustomer
}

const resolveRowClassName = ({ row }) => {
  return isCurrentCustomer(row) ? 'selected-customer-row' : ''
}
</script>

<style scoped>
.customer-picker-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--erp-space-3);
  margin-bottom: var(--erp-space-3);
}

.customer-picker-field {
  width: 130px;
  flex: none;
}

.customer-picker-search {
  width: 320px;
  max-width: 100%;
}

.customer-picker-count {
  color: var(--erp-text-secondary);
  font-size: var(--erp-font-size-sm);
  white-space: nowrap;
}

.customer-picker-table :deep(.selected-customer-row > td.el-table__cell) {
  background: #e8f3ff !important;
}

.customer-picker-table :deep(.selected-customer-row:hover > td.el-table__cell) {
  background: #d9ecff !important;
}

.customer-picker-table :deep(.selected-customer-row .el-button) {
  font-weight: 700;
}

@media (max-width: 760px) {
  .customer-picker-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .customer-picker-field,
  .customer-picker-search {
    width: 100%;
  }
}
</style>
