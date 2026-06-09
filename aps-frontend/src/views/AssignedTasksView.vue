<script setup>
import { onMounted, ref } from 'vue'
import { request } from '../api'
import { field, label, number } from '../helpers'
import TaskTable from '../components/TaskTable.vue'

const loading = ref(false)
const error = ref('')
const notice = ref('')
const tasks = ref([])
const selectedTaskId = ref(null)
const taskDetail = ref(null)

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
  tasks.value = await request('/api/aps/pmc/tasks?assignmentStatus=ASSIGNED&limit=100')
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

onMounted(() => run(loadTasks))
</script>

<template>
  <section class="content-grid">
    <div class="toolbar">
      <button class="secondary" type="button" :disabled="loading" @click="run(loadTasks)">刷新已分配</button>
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

    <section class="panel detail-panel">
      <div class="section-head">
        <h2>分配详情</h2>
        <span>{{ selectedTaskId ? `#${selectedTaskId}` : '未选择' }}</span>
      </div>
      <div v-if="taskDetail" class="detail-layout">
        <div class="detail-main">
          <dl>
            <div><dt>RFQ</dt><dd>{{ field(taskDetail.task, 'rfq_no', 'rfqNo') || '-' }}</dd></div>
            <div><dt>内部项目号</dt><dd>{{ field(taskDetail.task, 'internal_project_no', 'internalProjectNo') || '-' }}</dd></div>
            <div><dt>客户</dt><dd>{{ field(taskDetail.task, 'customer_name', 'customerName') || '-' }}</dd></div>
            <div><dt>图号/版本</dt><dd>{{ field(taskDetail.task, 'part_no', 'partNo') }} / {{ field(taskDetail.task, 'version') || '-' }}</dd></div>
            <div><dt>交期</dt><dd>{{ field(taskDetail.task, 'delivery_date', 'deliveryDate') || '-' }}</dd></div>
            <div><dt>工时</dt><dd>{{ number(field(taskDetail.task, 'total_work_hours', 'totalWorkHours'), 2) }}</dd></div>
            <div><dt>已分配部门</dt><dd>{{ field(taskDetail.task, 'assigned_department_name', 'assignedDepartmentName') || '-' }}</dd></div>
            <div><dt>分配时间</dt><dd>{{ field(taskDetail.task, 'assigned_at', 'assignedAt') || '-' }}</dd></div>
            <div><dt>PMC 状态</dt><dd>{{ label(field(taskDetail.task, 'assignment_status', 'assignmentStatus')) }}</dd></div>
          </dl>
          <div class="table-wrap compact">
            <table>
              <thead>
                <tr>
                  <th>顺序</th>
                  <th>工艺</th>
                  <th>类型</th>
                  <th>工时</th>
                  <th>费用</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in taskDetail.processRows" :key="row.id">
                  <td>{{ row.sort_no || '-' }}</td>
                  <td>{{ row.process_name }}</td>
                  <td>{{ row.calc_type || row.source_type || '-' }}</td>
                  <td>{{ number(row.work_hours, 2) }}</td>
                  <td>{{ number(row.line_cost, 2) }}</td>
                </tr>
                <tr v-if="!taskDetail.processRows?.length">
                  <td class="empty" colspan="5">暂无工艺</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <form class="form-grid side-form" @submit.prevent>
          <label>
            当前部门
            <input :value="field(taskDetail.task, 'assigned_department_name', 'assignedDepartmentName') || '-'" disabled />
          </label>
          <label>
            已分配工时
            <input :value="number(field(taskDetail.task, 'total_work_hours', 'totalWorkHours'), 2)" disabled />
          </label>
          <button class="secondary" type="button" :disabled="loading" @click="releaseAssignment">释放分配</button>
        </form>
      </div>
      <p v-else class="empty-state">从已分配列表选择一条任务查看分配信息。</p>
    </section>
  </section>
</template>
