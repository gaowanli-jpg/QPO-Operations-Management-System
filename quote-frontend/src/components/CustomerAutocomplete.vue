<template>
  <div class="customer-autocomplete-shell" :class="{ 'is-disabled': disabled, 'full-width': fullWidth }">
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
      popper-class="customer-autocomplete-popper"
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
    <el-button
      class="customer-autocomplete-picker"
      v-permission="permission"
      :disabled="disabled"
      @click="$emit('open-picker')"
    >
      选择客户
    </el-button>
  </div>
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
  },
  permission: {
    type: String,
    default: ''
  },
  fullWidth: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue', 'select', 'clear', 'open-picker'])
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
      pageSize: 50
    })
    if (currentRequestId !== requestId) return
    const records = Array.isArray(response) ? response : (response?.records || [])
    callback(records.map(normalizeCustomer).filter(customer => customer.displayName))
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
  flex: 1;
  min-width: 0;
}

.customer-autocomplete-shell {
  display: flex;
  align-items: stretch;
  width: 220px;
  min-height: 32px;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  background: var(--el-fill-color-blank);
  overflow: visible;
}

.customer-autocomplete-shell.full-width {
  width: 100%;
}

.customer-autocomplete-shell:focus-within {
  border-color: var(--el-color-primary);
}

.customer-autocomplete-shell :deep(.el-input__wrapper) {
  min-height: 30px;
  padding: 0 8px;
  border: 0;
  box-shadow: none;
  background: transparent;
}

.customer-autocomplete-picker {
  flex: 0 0 auto;
  height: 30px;
  margin: 0;
  padding: 0 10px;
  border: 0;
  border-left: 1px solid var(--el-border-color);
  border-radius: 0;
  color: var(--el-color-primary);
  background: transparent;
  font-size: 12px;
}

.customer-autocomplete-picker:hover,
.customer-autocomplete-picker:focus {
  color: var(--el-color-primary-light-3);
  background: var(--el-fill-color-light);
}

.customer-autocomplete-shell.is-disabled {
  background: var(--el-disabled-bg-color);
  cursor: not-allowed;
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

:global(.customer-autocomplete-popper .el-autocomplete-suggestion__wrap) {
  max-height: 360px;
  overflow-y: auto;
}
</style>
