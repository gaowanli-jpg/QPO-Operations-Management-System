<script setup>
import { computed, onMounted, ref } from 'vue'
import { request } from '../api'
import { customerProjectText, field, number } from '../helpers'
import ApsPager from '../components/ApsPager.vue'
import ApsProcessTable from '../components/ApsProcessTable.vue'
import ApsStatusTag from '../components/ApsStatusTag.vue'
import TaskTable from '../components/TaskTable.vue'

const loading = ref(false)
const error = ref('')
const notice = ref('')
const tasks = ref([])
const selectedTaskId = ref(null)
const taskDetail = ref(null)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))

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
  const query = new URLSearchParams({
    assignmentStatus: 'ASSIGNED',
    page: String(page.value),
    pageSize: String(pageSize.value)
  })
  const result = await request(`/api/aps/pmc/tasks/page?${query}`)
  tasks.value = result.records || []
  total.value = result.total || 0
  if (tasks.value.length === 0 && total.value > 0 && page.value > totalPages.value) {
    page.value = totalPages.value
    await loadTasks()
  }
}

const loadTaskDetail = async (id) => {
  selectedTaskId.value = id
  taskDetail.value = await request(`/api/aps/pmc/tasks/${id}`)
}

const releaseAssignment = async () => {
  if (!selectedTaskId.value) return
  await run(async () => {
    await request(`/api/aps/pmc/tasks/${selectedTaskId.value}/release-assignment`, { method: 'POST' })
    selectedTaskId.value = null
    taskDetail.value = null
    await loadTasks()
  }, '任务分配已释放')
}

const goPage = async (nextPage) => {
  page.value = nextPage
  selectedTaskId.value = null
  taskDetail.value = null
  await run(loadTasks)
}

const changePageSize = async (nextSize) => {
  pageSize.value = nextSize
  page.value = 1
  selectedTaskId.value = null
  taskDetail.value = null
  await run(loadTasks)
}

onMounted(() => run(loadTasks))
</script>

<template>
  <section class="content-grid">
    <div class="toolbar">
      <el-button :disabled="loading" @click="run(loadTasks)">刷新已分配</el-button>
    </div>

    <p v-if="error" class="message error">{{ error }}</p>
    <p v-else-if="notice" class="message notice">{{ notice }}</p>

    <TaskTable
      title="已分配零件"
      :tasks="tasks"
      :selectable="false"
      :deletable="false"
      @select="loadTaskDetail"
    />

    <ApsPager
      :disabled="loading"
      :page="page"
      :page-size="pageSize"
      :total="total"
      @current-change="goPage"
      @size-change="changePageSize"
    />

    <section class="panel detail-panel">
      <div class="section-head">
        <h2>分配详情</h2>
        <span>{{ selectedTaskId ? `#${selectedTaskId}` : '未选择' }}</span>
      </div>
      <div v-if="taskDetail" class="detail-layout">
        <div class="detail-main">
          <dl>
            <div><dt>内部项目号</dt><dd>{{ field(taskDetail.task, 'internal_project_no', 'internalProjectNo') || '-' }}</dd></div>
            <div><dt>客户订单号</dt><dd>{{ field(taskDetail.task, 'customer_order_no', 'customerOrderNo') || '-' }}</dd></div>
            <div><dt>客户项目号</dt><dd>{{ customerProjectText(taskDetail.task) }}</dd></div>
            <div><dt>客户</dt><dd>{{ field(taskDetail.task, 'customer_name', 'customerName') || '-' }}</dd></div>
            <div><dt>图号/版本</dt><dd>{{ field(taskDetail.task, 'part_no', 'partNo') }} / {{ field(taskDetail.task, 'version') || '-' }}</dd></div>
            <div><dt>交期</dt><dd>{{ field(taskDetail.task, 'delivery_date', 'deliveryDate') || '-' }}</dd></div>
            <div><dt>工时</dt><dd>{{ number(field(taskDetail.task, 'total_work_hours', 'totalWorkHours'), 2) }}</dd></div>
            <div><dt>已分配部门</dt><dd>{{ field(taskDetail.task, 'assigned_department_name', 'assignedDepartmentName') || '-' }}</dd></div>
            <div><dt>分配时间</dt><dd>{{ field(taskDetail.task, 'assigned_at', 'assignedAt') || '-' }}</dd></div>
            <div><dt>PMC 状态</dt><dd><ApsStatusTag :value="field(taskDetail.task, 'assignment_status', 'assignmentStatus')" /></dd></div>
            <div class="detail-wide route-text">
              <dt>完整工艺路线</dt>
              <dd>{{ field(taskDetail.task, 'process_route_text', 'processRouteText') || '-' }}</dd>
            </div>
          </dl>
          <ApsProcessTable :rows="taskDetail.processRows || []" />
        </div>

        <form class="form-grid side-form" @submit.prevent>
          <label>
            当前部门
            <el-input :model-value="field(taskDetail.task, 'assigned_department_name', 'assignedDepartmentName') || '-'" disabled />
          </label>
          <label>
            已分配工时
            <el-input :model-value="number(field(taskDetail.task, 'total_work_hours', 'totalWorkHours'), 2)" disabled />
          </label>
          <el-button :disabled="loading" @click="releaseAssignment">释放分配</el-button>
        </form>
      </div>
      <p v-else class="empty-state">从已分配列表选择一条任务查看分配信息。</p>
    </section>
  </section>
</template>
