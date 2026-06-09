<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { request } from '../api'
import { customerProjectText, field, label, number } from '../helpers'
import TaskTable from '../components/TaskTable.vue'

const loading = ref(false)
const error = ref('')
const notice = ref('')
const tasks = ref([])
const departments = ref([])
const selectedTaskId = ref(null)
const selectedTaskIds = ref([])
const taskDetail = ref(null)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))

const assignmentForm = reactive({
  departmentId: '',
  assignedBy: 'PMC',
  remark: ''
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
  const query = new URLSearchParams({
    assignmentStatus: 'PENDING',
    page: String(page.value),
    pageSize: String(pageSize.value)
  })
  const result = await request(`/api/aps/pmc/tasks/page?${query}`)
  tasks.value = result.records || []
  total.value = result.total || 0
  if (tasks.value.length === 0 && total.value > 0 && page.value > totalPages.value) {
    page.value = totalPages.value
    await loadTasks()
    return
  }
  const visibleIds = new Set(tasks.value.map((task) => field(task, 'id')))
  selectedTaskIds.value = selectedTaskIds.value.filter((id) => visibleIds.has(id))
}

const loadDepartments = async () => {
  departments.value = await request('/api/aps/pmc/departments')
}

const loadTaskDetail = async (id) => {
  selectedTaskId.value = id
  taskDetail.value = await request(`/api/aps/pmc/tasks/${id}`)
  assignmentForm.departmentId = field(taskDetail.value.task, 'assigned_department_id', 'assignedDepartmentId') || ''
}

const assignDepartment = async () => {
  if (!selectedTaskId.value || !assignmentForm.departmentId) return
  await run(async () => {
    await request(`/api/aps/pmc/tasks/${selectedTaskId.value}/assign-department`, {
      method: 'POST',
      body: JSON.stringify({
        departmentId: Number(assignmentForm.departmentId),
        assignedBy: assignmentForm.assignedBy,
        remark: assignmentForm.remark
      })
    })
    selectedTaskId.value = null
    taskDetail.value = null
    assignmentForm.departmentId = ''
    await Promise.all([loadTasks(), loadDepartments()])
  }, '任务已分配到部门')
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

const goPage = async (nextPage) => {
  page.value = nextPage
  selectedTaskId.value = null
  taskDetail.value = null
  await run(loadTasks)
}

const changePageSize = async () => {
  page.value = 1
  selectedTaskId.value = null
  taskDetail.value = null
  await run(loadTasks)
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
    if (selectedTaskId.value && ids.includes(selectedTaskId.value)) {
      selectedTaskId.value = null
      taskDetail.value = null
      assignmentForm.departmentId = ''
    }
    selectedTaskIds.value = []
    await Promise.all([loadTasks(), loadDepartments()])
  }, `已删除 ${ids.length} 条 APS 任务`)
}

onMounted(() => run(() => Promise.all([loadTasks(), loadDepartments()])))
</script>

<template>
  <section class="content-grid">
    <div class="toolbar">
      <button class="secondary" type="button" :disabled="loading" @click="run(() => Promise.all([loadTasks(), loadDepartments()]))">刷新待分配</button>
    </div>

    <p v-if="error" class="message error">{{ error }}</p>
    <p v-else-if="notice" class="message notice">{{ notice }}</p>

    <TaskTable
      title="待分配零件"
      :tasks="tasks"
      :selected-ids="selectedTaskIds"
      @select="loadTaskDetail"
      @toggle-select="toggleTaskSelect"
      @toggle-select-all="toggleAllTasks"
      @delete-selected="deleteSelectedTasks"
    />

    <div class="pager">
      <span>共 {{ total }} 条</span>
      <button class="secondary" type="button" :disabled="page <= 1 || loading" @click="goPage(page - 1)">上一页</button>
      <span>第 {{ page }} / {{ totalPages }} 页</span>
      <button class="secondary" type="button" :disabled="page >= totalPages || loading" @click="goPage(page + 1)">下一页</button>
      <select v-model.number="pageSize" class="page-size" :disabled="loading" @change="changePageSize">
        <option :value="20">20 条</option>
        <option :value="50">50 条</option>
        <option :value="100">100 条</option>
      </select>
    </div>

    <section class="panel detail-panel">
      <div class="section-head">
        <h2>任务详情</h2>
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

        <form class="form-grid side-form" @submit.prevent="assignDepartment">
          <label>
            部门
            <select v-model="assignmentForm.departmentId" required>
              <option value="">选择部门</option>
              <option v-for="department in departments" :key="department.id" :value="department.id">
                {{ department.department_name }} / {{ label(department.department_type) }} / 剩余 {{ number(department.remaining_hours, 2) }}h
              </option>
            </select>
          </label>
          <label>分配人<input v-model="assignmentForm.assignedBy" /></label>
          <label>备注<input v-model="assignmentForm.remark" /></label>
          <button class="primary" type="submit" :disabled="loading || !assignmentForm.departmentId">分配到部门</button>
        </form>
      </div>
      <p v-else class="empty-state">从待分配列表选择一条任务查看工艺、库存和分配信息。</p>
    </section>
  </section>
</template>
