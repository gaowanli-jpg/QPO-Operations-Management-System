<template>
  <el-autocomplete
    v-model="inputValue"
    class="customer-autocomplete"
    :placeholder="placeholder"
    :disabled="disabled"
    :clearable="clearable"
    :fetch-suggestions="fetchSuggestions"
    :trigger-on-focus="triggerOnFocus"
    :debounce="0"
    value-key="displayName"
    @select="handleSelect"
    @clear="handleClear"
  >
    <template #default="{ item }">
      <div class="customer-autocomplete-option">
        <span class="customer-autocomplete-name">{{ item.displayName }}</span>
        <span class="customer-autocomplete-meta">
          {{ item.customerCode }}<template v-if="item.customerShortName && item.customerShortName !== item.displayName"> · {{ item.customerShortName }}</template>
        </span>
      </div>
    </template>
  </el-autocomplete>
</template>

<script setup>
import { ref, watch } from 'vue'
import { listCustomers } from '../services/orderApi'

const props = defineProps({
  modelValue: {
    type: String,
    default: ''
  },
  customerCode: {
    type: String,
    default: ''
  },
  customerScope: {
    type: String,
    default: 'market-order'
  },
  placeholder: {
    type: String,
    default: '输入客户编码、简称或名称'
  },
  disabled: {
    type: Boolean,
    default: false
  },
  clearable: {
    type: Boolean,
    default: true
  },
  triggerOnFocus: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue', 'select', 'clear'])
const inputValue = ref(props.modelValue || '')
const loading = ref(false)
let requestId = 0

watch(() => props.modelValue, value => {
  if (value !== inputValue.value) inputValue.value = value || ''
})

const normalizeCustomer = customer => ({
  id: customer?.id,
  customerCode: customer?.customerCode || customer?.code || '',
  customerShortName: customer?.customerShortName || customer?.shortName || '',
  customerName: customer?.customerName || customer?.name || '',
  displayName: customer?.customerName || customer?.name || customer?.customerShortName || customer?.shortName || customer?.customerCode || customer?.code || ''
})

const fetchSuggestions = async (queryString, callback) => {
  const query = queryString.trim()
  if (!query) {
    callback([])
    return
  }
  const currentRequestId = ++requestId
  loading.value = true
  try {
    const response = await listCustomers(props.customerScope, {
      field: 'all',
      keyword: query,
      page: 1,
      pageSize: 10
    })
    if (currentRequestId !== requestId) return
    const records = Array.isArray(response) ? response : (response?.records || [])
    callback(records.map(normalizeCustomer).filter(customer => customer.displayName).slice(0, 10))
  } catch (error) {
    if (currentRequestId === requestId) callback([])
    console.error(error)
  } finally {
    if (currentRequestId === requestId) loading.value = false
  }
}

const handleSelect = customer => {
  const normalized = normalizeCustomer(customer)
  inputValue.value = normalized.displayName
  emit('update:modelValue', normalized.displayName)
  emit('select', normalized)
}

const handleClear = () => {
  emit('update:modelValue', '')
  emit('clear')
}
</script>

<style scoped>
.customer-autocomplete {
  width: 230px;
}

.customer-autocomplete-option {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  min-width: 0;
}

.customer-autocomplete-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.customer-autocomplete-meta {
  flex: 0 0 auto;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
