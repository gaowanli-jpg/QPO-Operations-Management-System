<script setup>
import { computed, onMounted, ref } from 'vue'
import { request } from '../api'
import { customerProjectText, field, label, number } from '../helpers'
import TaskTable from '../components/TaskTable.vue'

const loading = ref(false)
const error = ref('')
const notice = ref('')
const tasks = ref([])
const departments = ref([])
const departmentId = ref('')
const status = ref('ACTIVE')
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

const loadDepartments = async () => {
  departments.value = await request('/api/aps/pmc/departments')
}

const loadTasks = async () => {
  const query = new URLSearchParams({
    status: status.value,
    page: String(page.value),
    pageSize: String(pageSize.value)
  })
  if (departmentId.value) query.set('departmentId', departmentId.value)
  const result = await request(`/api/aps/department-center/tasks/page?${query}`)
  tasks.value = result.records || []
  total.value = result.total || 0
  if (tasks.value.length === 0 && total.value > 0 && page.value > totalPages.value) {
    page.value = totalPages.value
    await loadTasks()
  }
}

const loadTaskDetail = async (id) => {
  selectedTaskId.value = id
  taskDetail.value = await request(`/api/aps/department-center/tasks/${id}`)
}

const resetDetail = () => {
  selectedTaskId.value = null
  taskDetail.value = null
}

const applyFilters = async () => {
  page.value = 1
  resetDetail()
  await run(loadTasks)
}

const clearFilters = async () => {
  departmentId.value = ''
  status.value = 'ACTIVE'
  await applyFilters()
}

const refreshAll = async () => {
  await Promise.all([loadDepartments(), loadTasks()])
}

const goPage = async (nextPage) => {
  page.value = nextPage
  resetDetail()
  await run(loadTasks)
}

const changePageSize = async () => {
  page.value = 1
  resetDetail()
  await run(loadTasks)
}

onMounted(() => run(refreshAll))
</script>

<template>
  <section class="content-grid">
    <section class="panel task-filter-panel">
      <div class="task-filter">
        <label>
          部门
          <select v-model="departmentId" :disabled="loading" @change="applyFilters">
            <option value="">全部部门</option>
            <option v-for="department in departments" :key="department.id" :value="department.id">
              {{ department.department_name }} / {{ label(department.department_type) }}
            </option>
          </select>
        </label>
        <label>
          状态
          <select v-model="status" :disabled="loading" @change="applyFilters">
            <option value="ACTIVE">当前任务</option>
            <option value="RELEASED">已释放</option>
            <option value="ALL">全部</option>
          </select>
        </label>
        <div class="task-filter-actions">
          <button class="primary" type="button" :disabled="loading" @click="applyFilters">查询</button>
          <button class="secondary" type="button" :disabled="loading || (!departmentId && status === 'ACTIVE')" @click="clearFilters">清空</button>
          <button class="secondary" type="button" :disabled="loading" @click="run(refreshAll)">刷新</button>
        </div>
      </div>
    </section>

    <p v-if="error" class="message error">{{ error }}</p>
    <p v-else-if="notice" class="message notice">{{ notice }}</p>

    <TaskTable
      title="部门中心零件"
      :tasks="tasks"
      :selectable="false"
      :deletable="false"
      :show-department="true"
      action-label="详情"
      @select="loadTaskDetail"
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
        <h2>零件详情</h2>
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
            <div><dt>部门</dt><dd>{{ field(taskDetail.task, 'assigned_department_name', 'assignedDepartmentName') || '-' }}</dd></div>
            <div><dt>分配时间</dt><dd>{{ field(taskDetail.task, 'assigned_at', 'assignedAt') || '-' }}</dd></div>
            <div class="detail-wide route-text">
              <dt>完整工艺路线</dt>
              <dd>{{ field(taskDetail.task, 'process_route_text', 'processRouteText') || '-' }}</dd>
            </div>
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
            需生产数量
            <input :value="number(field(taskDetail.task, 'qty'), 0)" disabled />
          </label>
          <label>
            已分配工时
            <input :value="number(field(taskDetail.task, 'total_work_hours', 'totalWorkHours'), 2)" disabled />
          </label>
        </form>
      </div>
      <p v-else class="empty-state">从部门中心列表选择一条零件查看详情。</p>
    </section>
  </section>
</template>
