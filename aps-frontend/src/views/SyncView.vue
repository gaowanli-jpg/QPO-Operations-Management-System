<script setup>
import { computed, onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { field, number } from '../helpers'
import { useTaskStore } from '../stores/taskStore'
import ApsPager from '../components/ApsPager.vue'
import TaskTable from '../components/TaskTable.vue'
import { confirmAction } from '../utils/confirm'

const taskStore = useTaskStore()
const { tasks, selectedTaskIds, page, pageSize, total, syncResult } = storeToRefs(taskStore)
const loading = ref(false)
const error = ref('')
const notice = ref('')

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
  await taskStore.loadPmcTasks()
}

const syncErp = async () => {
  await run(async () => {
    await taskStore.syncErpDeliveryItems()
  }, 'ERP 提单零件同步完成')
}

const toggleTaskSelect = (id, checked) => {
  taskStore.toggleTaskSelect(id, checked)
}

const toggleAllTasks = (checked) => {
  taskStore.toggleAllTasks(checked)
}

const goPage = async (nextPage) => {
  page.value = nextPage
  await run(loadTasks)
}

const changePageSize = async (nextSize) => {
  pageSize.value = nextSize
  page.value = 1
  await run(loadTasks)
}

const deleteSelectedTasks = async () => {
  if (selectedTaskIds.value.length === 0) return
  const ids = [...selectedTaskIds.value]
  if (!await confirmAction(`\u786e\u8ba4\u5220\u9664 ${ids.length} \u6761 APS \u4efb\u52a1\uff1f\u5220\u9664\u540e\u540c\u4e00 ERP \u63d0\u5355\u53ef\u91cd\u65b0\u540c\u6b65\u3002`)) return
  await run(async () => {
    await taskStore.deleteSelectedTasks()
  }, `已删除 ${ids.length} 条 APS 任务`)
}

onMounted(() => run(loadTasks))
</script>

<template>
  <section class="content-grid">
    <section class="metrics" aria-label="任务概览">
      <div class="metric"><span>全部 APS 任务</span><strong>{{ number(total) }}</strong></div>
      <div class="metric"><span>本页待 PMC 分配</span><strong>{{ number(metric.pendingTasks) }}</strong></div>
      <div class="metric"><span>本页已分配</span><strong>{{ number(metric.assignedTasks) }}</strong></div>
      <div class="metric"><span>本页需生产</span><strong>{{ number(metric.partNeeds) }}</strong></div>
      <div class="metric"><span>本页材料缺口</span><strong>{{ number(metric.materialNeeds) }}</strong></div>
    </section>

    <p v-if="error" class="message error">{{ error }}</p>
    <p v-else-if="notice" class="message notice">{{ notice }}</p>

    <section class="panel">
      <div class="section-head">
        <h2>同步结果</h2>
        <div class="section-actions">
          <span>{{ syncResult ? `批次 ${syncResult.syncRunId}` : '等待同步' }}</span>
          <el-button type="primary" :disabled="loading" @click="syncErp">
            {{ loading ? '同步中' : '同步 ERP 提单零件' }}
          </el-button>
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

    <ApsPager
      :disabled="loading"
      :page="page"
      :page-size="pageSize"
      :total="total"
      @current-change="goPage"
      @size-change="changePageSize"
    />
  </section>
</template>
