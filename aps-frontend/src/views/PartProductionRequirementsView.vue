<script setup>
import { onMounted, ref } from 'vue'
import { request } from '../api'
import DataTable from '../components/DataTable.vue'

const loading = ref(false)
const error = ref('')
const notice = ref('')
const rows = ref([])
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
  const query = status.value ? `?status=${encodeURIComponent(status.value)}&limit=200` : '?limit=200'
  rows.value = await request(`/api/aps/inventory/parts/production-requirements${query}`)
}

onMounted(() => run(loadData))
</script>

<template>
  <section class="content-grid">
    <div class="toolbar">
      <select v-model="status" class="control-short" @change="run(loadData)">
        <option value="">全部状态</option>
        <option value="PENDING_PRODUCTION">待生产</option>
      </select>
      <button class="secondary" type="button" :disabled="loading" @click="run(loadData)">刷新</button>
    </div>
    <p v-if="error" class="message error">{{ error }}</p>
    <p v-else-if="notice" class="message notice">{{ notice }}</p>
    <DataTable title="生产需求" :rows="rows" :columns="[
      ['work_item_id', '任务'], ['rfq_no', 'RFQ'], ['internal_project_no', '内部项目号'],
      ['customer_name', '客户'], ['part_no', '图号'], ['version', '版本'],
      ['required_qty', '需生产'], ['status', '状态']
    ]" />
  </section>
</template>
