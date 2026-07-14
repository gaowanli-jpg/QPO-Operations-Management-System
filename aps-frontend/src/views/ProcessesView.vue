<script setup>
import { computed, onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useReferenceDataStore } from '../stores/referenceDataStore'

const PROCESS_TYPES = [
  { value: 'WORK_HOUR', label: '工时工艺' },
  { value: 'NON_WORK_HOUR', label: '非工时工艺' }
]

const loading = ref(false)
const error = ref('')
const notice = ref('')
const referenceStore = useReferenceDataStore()
const { processes } = storeToRefs(referenceStore)
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
  await referenceStore.loadProcesses({ force: true })
}

const syncProcesses = async (processType = '') => {
  await run(async () => {
    const result = await referenceStore.syncProcessesFromErp(processType)
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
          <el-button :disabled="loading" @click="syncProcesses(selectedType)">
            同步当前类型
          </el-button>
          <el-button :disabled="loading" @click="syncProcesses()">
            同步全部
          </el-button>
          <el-button :disabled="loading" @click="run(loadProcesses)">刷新</el-button>
        </div>
      </div>

      <div class="process-toolbar">
        <el-radio-group v-model="selectedType" class="process-tabs">
          <el-radio-button v-for="type in PROCESS_TYPES" :key="type.value" :label="type.value">
            {{ type.label }}
          </el-radio-button>
        </el-radio-group>
        <div class="process-counts">
          <el-tag v-for="type in PROCESS_TYPES" :key="type.value" type="info" effect="plain">
            {{ type.label }}：{{ countByType(type.value) }}
          </el-tag>
        </div>
      </div>

      <el-table :data="visibleProcesses" border stripe class="aps-data-table" empty-text="暂无工艺" row-key="id">
        <el-table-column label="工艺名称" min-width="180">
          <template #default="{ row }">
            <strong>{{ row.process_name }}</strong>
          </template>
        </el-table-column>
        <el-table-column label="类型" min-width="120">
          <template #default="{ row }">
            <el-tag type="info" effect="plain">{{ typeLabel(row.process_type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="erp_source_id" label="ERP 来源 ID" min-width="140" />
        <el-table-column prop="sort_no" label="排序" width="90" />
        <el-table-column prop="synced_at" label="同步时间" min-width="180" />
      </el-table>
    </section>
  </section>
</template>
