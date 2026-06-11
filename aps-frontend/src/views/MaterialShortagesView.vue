<script setup>
import { onMounted, ref } from 'vue'
import { request } from '../api'
import DataTable from '../components/DataTable.vue'

const loading = ref(false)
const error = ref('')
const notice = ref('')
const rows = ref([])

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
  rows.value = await request('/api/aps/inventory/materials/shortages?limit=200')
}

onMounted(() => run(loadData))
</script>

<template>
  <section class="content-grid">
    <div class="toolbar">
      <el-button :disabled="loading" @click="run(loadData)">刷新</el-button>
    </div>
    <p v-if="error" class="message error">{{ error }}</p>
    <p v-else-if="notice" class="message notice">{{ notice }}</p>
    <DataTable title="材料缺口" :rows="rows" :columns="[
      ['work_item_id', '任务'], ['internal_project_no', '内部项目号'],
      ['customer_order_no', '客户订单号'], ['customer_project_no', '客户项目号'],
      ['customer_name', '客户'], ['part_no', '图号'], ['material_name', '材料'],
      ['material_type', '类型'], ['shortage_weight_kg', '缺口 kg'], ['status', '状态']
    ]" />
  </section>
</template>
