<script setup>
import { onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useInventoryStore } from '../stores/inventoryStore'
import DataTable from '../components/DataTable.vue'

const loading = ref(false)
const error = ref('')
const notice = ref('')
const inventoryStore = useInventoryStore()
const { partProductionRequirements: rows } = storeToRefs(inventoryStore)
const status = ref('')

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

const loadData = async () => {
  await inventoryStore.loadPartProductionRequirements({ status: status.value })
}

onMounted(() => run(loadData))
</script>

<template>
  <section class="content-grid">
    <div class="toolbar">
      <el-select v-model="status" class="control-short" placeholder="全部状态" clearable @change="run(loadData)">
        <el-option label="待生产" value="PENDING_PRODUCTION" />
      </el-select>
      <el-button :disabled="loading" @click="run(loadData)">刷新</el-button>
    </div>
    <p v-if="error" class="message error">{{ error }}</p>
    <p v-else-if="notice" class="message notice">{{ notice }}</p>
    <DataTable title="生产需求" :rows="rows" :columns="[
      ['work_item_id', '任务'], ['internal_project_no', '内部项目号'],
      ['customer_order_no', '客户订单号'], ['customer_project_no', '客户项目号'],
      ['customer_name', '客户'], ['part_no', '图号'], ['version', '版本'],
      ['required_qty', '需生产'], ['status', '状态']
    ]" />
  </section>
</template>
