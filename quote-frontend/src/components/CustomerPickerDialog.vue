<template>
  <el-dialog
    v-model="visible"
    :title="title"
    width="720px"
    class="customer-picker-dialog"
    @open="loadCustomers"
    @closed="resetSearch"
  >
    <div class="customer-picker-toolbar">
      <el-input
        v-model="keyword"
        clearable
        placeholder="搜索客户名称/代码"
        class="customer-picker-search"
      />
      <span class="customer-picker-count">共 {{ filteredCustomers.length }} 个客户</span>
    </div>

    <el-table
      :data="filteredCustomers"
      border
      stripe
      height="360"
      v-loading="loading"
      highlight-current-row
      class="customer-picker-table"
      :row-class-name="resolveRowClassName"
      @row-click="selectCustomer"
    >
      <el-table-column type="index" label="#" width="70" align="center" />
      <el-table-column prop="name" label="客户名称/代码" min-width="320" show-overflow-tooltip />
      <el-table-column label="操作" width="110" align="center" fixed="right">
        <template #default="{ row }">
          <el-button
            :type="row.name === currentCustomer ? 'success' : 'primary'"
            size="small"
            :plain="row.name !== currentCustomer"
            @click.stop="selectCustomer(row)"
          >
            {{ row.name === currentCustomer ? '已选择' : '选择' }}
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
import { computed, ref } from 'vue'
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
const keyword = ref('')
const loading = ref(false)

const customerRows = computed(() => customers.value.map(name => ({ name })))
const filteredCustomers = computed(() => {
  const text = keyword.value.trim().toLowerCase()
  if (!text) return customerRows.value
  return customerRows.value.filter(customer => customer.name.toLowerCase().includes(text))
})

const loadCustomers = async () => {
  loading.value = true
  try {
    const response = await listCustomers(props.customerScope)
    customers.value = response || []
  } catch (error) {
    console.error(error)
    ElMessage.error('客户列表加载失败。')
  } finally {
    loading.value = false
  }
}

const resetSearch = () => {
  keyword.value = ''
}

const selectCustomer = (row) => {
  const customer = typeof row === 'string' ? row : row?.name
  if (!customer) return
  emit('select', customer)
  visible.value = false
}

const resolveRowClassName = ({ row }) => {
  return row.name === props.currentCustomer ? 'selected-customer-row' : ''
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

  .customer-picker-search {
    width: 100%;
  }
}
</style>
