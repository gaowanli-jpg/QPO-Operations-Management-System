<script setup>
import { computed, onMounted, ref } from 'vue'
import { request } from '../api'
import { field, number } from '../helpers'
import TaskTable from '../components/TaskTable.vue'

const loading = ref(false)
const error = ref('')
const notice = ref('')
const syncResult = ref(null)
const tasks = ref([])
const selectedTaskIds = ref([])

const metric = computed(() => {
  const pendingTasks = tasks.value.filter((row) => field(row, 'assignment_status', 'assignmentStatus') !== 'ASSIGNED').length
  const assignedTasks = tasks.value.filter((row) => field(row, 'assignment_status', 'assignmentStatus') === 'ASSIGNED').length
  const partNeeds = tasks.value.filter((row) => field(row, 'part_stock_status', 'partStockStatus') === 'NEED_PRODUCTION').length
  const materialNeeds = tasks.value.filter((row) => field(row, 'material_stock_status', 'materialStockStatus') === 'SHORTAGE').length
  return { tasks: tasks.value.length, pendingTasks, assignedTasks, partNeeds, materialNeeds }
})

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

const loadTasks = async () => {
  tasks.value = await request('/api/aps/pmc/tasks?limit=100')
  const visibleIds = new Set(tasks.value.map((task) => field(task, 'id')))
  selectedTaskIds.value = selectedTaskIds.value.filter((id) => visibleIds.has(id))
}

const syncErp = async () => {
  await run(async () => {
    syncResult.value = await request('/api/aps/sync/erp-delivery-items', { method: 'POST' })
    await loadTasks()
  }, 'ERP 提单零件同步完成')
}

const toggleTaskSelect = (id, checked) => {
  if (!id) return
  if (checked) {
    if (!selectedTaskIds.value.includes(id)) {
      selectedTaskIds.value = [...selectedTaskIds.value, id]
    }
    return
  }
  selectedTaskIds.value = selectedTaskIds.value.filter((selectedId) => selectedId !== id)
}

const toggleAllTasks = (checked) => {
  selectedTaskIds.value = checked ? tasks.value.map((task) => field(task, 'id')).filter(Boolean) : []
}

const deleteSelectedTasks = async () => {
  if (selectedTaskIds.value.length === 0) return
  const ids = [...selectedTaskIds.value]
  if (!window.confirm(`确认删除 ${ids.length} 条 APS 任务？删除后同一 ERP 提单可重新同步。`)) return
  await run(async () => {
    await request('/api/aps/pmc/tasks', {
      method: 'DELETE',
      body: JSON.stringify({ ids })
    })
    selectedTaskIds.value = []
    await loadTasks()
  }, `已删除 ${ids.length} 条 APS 任务`)
}

onMounted(() => run(loadTasks))
</script>

<template>
  <section class="content-grid">
    <section class="metrics" aria-label="任务概览">
      <div class="metric"><span>APS 任务</span><strong>{{ number(metric.tasks) }}</strong></div>
      <div class="metric"><span>待 PMC 分配</span><strong>{{ number(metric.pendingTasks) }}</strong></div>
      <div class="metric"><span>已分配</span><strong>{{ number(metric.assignedTasks) }}</strong></div>
      <div class="metric"><span>需生产</span><strong>{{ number(metric.partNeeds) }}</strong></div>
      <div class="metric"><span>材料缺口</span><strong>{{ number(metric.materialNeeds) }}</strong></div>
    </section>

    <p v-if="error" class="message error">{{ error }}</p>
    <p v-else-if="notice" class="message notice">{{ notice }}</p>

    <section class="panel">
      <div class="section-head">
        <h2>同步结果</h2>
        <div class="section-actions">
          <span>{{ syncResult ? `批次 ${syncResult.syncRunId}` : '等待同步' }}</span>
          <button class="primary" type="button" :disabled="loading" @click="syncErp">
            {{ loading ? '同步中' : '同步 ERP 提单零件' }}
          </button>
        </div>
      </div>
      <div class="sync-summary">
        <div><span>接收</span><strong>{{ number(syncResult?.receivedCount) }}</strong></div>
        <div><span>导入</span><strong>{{ number(syncResult?.importedCount) }}</strong></div>
        <div><span>跳过</span><strong>{{ number(syncResult?.skippedCount) }}</strong></div>
        <div><span>生产需求</span><strong>{{ number(syncResult?.productionRequirementCount) }}</strong></div>
        <div><span>材料缺口</span><strong>{{ number(syncResult?.materialShortageCount) }}</strong></div>
      </div>
    </section>

    <TaskTable
      :tasks="tasks"
      :selected-ids="selectedTaskIds"
      @toggle-select="toggleTaskSelect"
      @toggle-select-all="toggleAllTasks"
      @delete-selected="deleteSelectedTasks"
    />
  </section>
</template>
