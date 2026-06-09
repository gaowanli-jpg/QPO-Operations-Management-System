<script setup>
import { computed, onMounted, ref } from 'vue'
import { request } from '../api'

const PROCESS_TYPES = [
  { value: 'WORK_HOUR', label: '工时工艺' },
  { value: 'NON_WORK_HOUR', label: '非工时工艺' }
]

const loading = ref(false)
const error = ref('')
const notice = ref('')
const processes = ref([])
const selectedType = ref('WORK_HOUR')

const visibleProcesses = computed(() => (
  processes.value.filter((process) => process.process_type === selectedType.value)
))

const typeLabel = (value) => (
  PROCESS_TYPES.find((type) => type.value === value)?.label || value || '-'
)

const countByType = (type) => (
  processes.value.filter((process) => process.process_type === type).length
)

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

const loadProcesses = async () => {
  processes.value = await request('/api/aps/processes')
}

const syncProcesses = async (processType = '') => {
  await run(async () => {
    const suffix = processType ? `?processType=${encodeURIComponent(processType)}` : ''
    const result = await request(`/api/aps/processes/sync-from-erp${suffix}`, { method: 'POST' })
    await loadProcesses()
    notice.value = `已同步 ${result.importedCount || 0} 项工艺`
  })
}

onMounted(() => run(loadProcesses))
</script>

<template>
  <section class="content-grid">
    <p v-if="error" class="message error">{{ error }}</p>
    <p v-else-if="notice" class="message notice">{{ notice }}</p>

    <section class="panel">
      <div class="section-head">
        <h2>工艺管理</h2>
        <div class="section-actions">
          <button class="secondary" type="button" :disabled="loading" @click="syncProcesses(selectedType)">
            同步当前类型
          </button>
          <button class="secondary" type="button" :disabled="loading" @click="syncProcesses()">
            同步全部
          </button>
          <button class="secondary" type="button" :disabled="loading" @click="run(loadProcesses)">刷新</button>
        </div>
      </div>

      <div class="process-toolbar">
        <div class="segmented" role="tablist" aria-label="工艺类型">
          <button
            v-for="type in PROCESS_TYPES"
            :key="type.value"
            type="button"
            :class="{ active: selectedType === type.value }"
            @click="selectedType = type.value"
          >
            {{ type.label }}
          </button>
        </div>
        <div class="process-counts">
          <span v-for="type in PROCESS_TYPES" :key="type.value">
            {{ type.label }}：{{ countByType(type.value) }}
          </span>
        </div>
      </div>

      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>工艺名称</th>
              <th>类型</th>
              <th>ERP 来源 ID</th>
              <th>排序</th>
              <th>同步时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="process in visibleProcesses" :key="process.id">
              <td><strong>{{ process.process_name }}</strong></td>
              <td><span class="pill muted">{{ typeLabel(process.process_type) }}</span></td>
              <td>{{ process.erp_source_id || '-' }}</td>
              <td>{{ process.sort_no ?? '-' }}</td>
              <td>{{ process.synced_at || '-' }}</td>
            </tr>
            <tr v-if="visibleProcesses.length === 0">
              <td class="empty" colspan="5">暂无工艺</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </section>
</template>
