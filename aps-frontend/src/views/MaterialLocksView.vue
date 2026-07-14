<script setup>
import { onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useInventoryStore } from '../stores/inventoryStore'
import DataTable from '../components/DataTable.vue'

const loading = ref(false)
const error = ref('')
const notice = ref('')
const inventoryStore = useInventoryStore()
const { materialLocks: rows, materialLockCustomers: customerOptions } = storeToRefs(inventoryStore)
const customerName = ref('')

const run = async (fn, successMessage = '') => {
  loading.value = true
  error.value = ''
  notice.value = ''
  try {
    await fn()
    if (successMessage) notice.value = successMessage
  } catch (err) {
    error.value = err.message || '操作失败'
  } finally {
    loading.value = false
  }
}

const materialLockFilters = () => ({ customerName: customerName.value })

const loadData = async () => {
  await inventoryStore.loadMaterialLocks(materialLockFilters())
}

const loadCustomers = async () => {
  await inventoryStore.loadMaterialLockCustomers()
}

const confirmLock = async (id) => {
  if (!id) return
  await run(async () => {
    await inventoryStore.confirmMaterialLock(id)
  }, '锁定已确认')
}

const releaseLock = async (id) => {
  if (!id) return
  if (!window.confirm('确认释放这一条材料库存锁定？释放后库存会回滚，材料缺口会增加。')) return
  await run(async () => {
    await inventoryStore.releaseMaterialLock(id)
  }, '锁定已释放')
}

const handleLockAction = async (action, row) => {
  if (!row?.id) return
  if (action === 'confirm') {
    await confirmLock(row.id)
    return
  }
  if (action === 'release') {
    await releaseLock(row.id)
  }
}

const clearCustomer = async () => {
  customerName.value = ''
  await run(loadData)
}

onMounted(() => run(() => Promise.all([loadCustomers(), loadData()])))
</script>

<template>
  <section class="content-grid">
    <section class="panel lock-filter-panel">
      <div class="lock-filter">
        <label class="lock-filter-field">
          客户
          <el-select v-model="customerName" placeholder="全部客户" clearable filterable @change="run(loadData)">
            <el-option v-for="customer in customerOptions" :key="customer" :label="customer" :value="customer" />
          </el-select>
        </label>
        <div class="lock-filter-actions">
          <el-button type="primary" :disabled="loading" @click="run(loadData)">查询</el-button>
          <el-button :disabled="loading || !customerName" @click="clearCustomer">清空</el-button>
          <el-button :disabled="loading" @click="run(loadData)">刷新</el-button>
        </div>
      </div>
    </section>
    <p v-if="error" class="message error">{{ error }}</p>
    <p v-else-if="notice" class="message notice">{{ notice }}</p>
    <DataTable title="材料锁定记录" :rows="rows" :actions="[
      ['confirm', '已锁定', 'confirm'],
      ['release', '释放', 'danger']
    ]" :columns="[
      ['work_item_id', '任务'], ['internal_project_no', '内部项目号'],
      ['customer_order_no', '客户订单号'], ['customer_project_no', '客户项目号'],
      ['customer_name', '客户'], ['part_no', '图号'], ['material_name', '材料'],
      ['material_type', '类型'], ['demand_weight_kg', '需求 kg'],
      ['locked_weight_kg', '锁定 kg'], ['shortage_weight_kg', '缺口 kg'], ['status', '状态']
    ]" @row-action="handleLockAction" />
  </section>
</template>
