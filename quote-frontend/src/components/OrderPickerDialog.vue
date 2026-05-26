<template>
  <el-dialog v-model="visible" :title="title" width="620px" @closed="resetPicker">
    <div class="picker-step">
      <div class="step-title">1. 选择客户</div>
      <div class="step-row">
        <el-select
          v-model="selectedCustomer"
          filterable
          placeholder="请选择客户名称"
          style="width: 100%;"
          :disabled="customerConfirmed"
        >
          <el-option
            v-for="customer in customerOptions"
            :key="customer"
            :label="customer"
            :value="customer"
          />
        </el-select>
        <el-button type="primary" :disabled="!selectedCustomer" @click="confirmCustomer">
          确定客户
        </el-button>
      </div>
    </div>

    <div class="picker-step">
      <div class="step-title">2. 选择该客户订单</div>
      <el-select
        v-model="selectedOrderId"
        filterable
        placeholder="请先确定客户"
        style="width: 100%;"
        :disabled="!customerConfirmed"
      >
        <el-option
          v-for="order in filteredOrders"
          :key="order.id"
          :label="formatOrderLabel(order)"
          :value="order.id"
        />
      </el-select>
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button v-if="customerConfirmed" @click="reselectCustomer">重选客户</el-button>
      <el-button type="primary" :disabled="!selectedOrderId" @click="submitOrder">
        确定订单
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

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
  }
})

const emit = defineEmits(['update:modelValue', 'select'])

const visible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value)
})

const selectedCustomer = ref('')
const customerConfirmed = ref(false)
const selectedOrderId = ref(null)

const customerOptions = computed(() => {
  const names = props.orders
    .map(order => order.customerName || '未填写客户')
    .filter(Boolean)
  return [...new Set(names)].sort((a, b) => a.localeCompare(b, 'zh-Hans-CN'))
})

const filteredOrders = computed(() => {
  if (!customerConfirmed.value) return []
  return props.orders.filter(order => (order.customerName || '未填写客户') === selectedCustomer.value)
})

watch(filteredOrders, orders => {
  if (customerConfirmed.value && orders.length > 0 && !orders.some(order => order.id === selectedOrderId.value)) {
    selectedOrderId.value = orders[0].id
  }
})

const confirmCustomer = () => {
  if (!selectedCustomer.value) return
  customerConfirmed.value = true
  selectedOrderId.value = filteredOrders.value[0]?.id || null
}

const reselectCustomer = () => {
  customerConfirmed.value = false
  selectedOrderId.value = null
}

const submitOrder = () => {
  const order = props.orders.find(item => item.id === selectedOrderId.value)
  if (!order) return
  emit('select', order)
  visible.value = false
}

const resetPicker = () => {
  selectedCustomer.value = ''
  customerConfirmed.value = false
  selectedOrderId.value = null
}

const formatOrderLabel = (order) => {
  return `${order.rfqNo || order.id} / ${order.status || '-'}`
}
</script>

<style scoped>
.picker-step { margin-bottom: 18px; }
.step-title { font-weight: 700; color: #303133; margin-bottom: 10px; }
.step-row { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 10px; }
</style>
