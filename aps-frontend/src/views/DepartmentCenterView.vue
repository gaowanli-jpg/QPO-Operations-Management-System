<script setup>
import { computed, onMounted, ref } from 'vue'
import { listDepartments } from '../services/departmentApi'
import { getDepartmentCenterTask, listDepartmentCenterTasks } from '../services/taskApi'
import { customerProjectText, field, label, number } from '../helpers'
import ApsPager from '../components/ApsPager.vue'
import ApsProcessTable from '../components/ApsProcessTable.vue'
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
  departments.value = await listDepartments()
}

const loadTasks = async () => {
  const query = new URLSearchParams({
    status: status.value,
    page: String(page.value),
    pageSize: String(pageSize.value)
  })
  if (departmentId.value) query.set('departmentId', departmentId.value)
  const result = await listDepartmentCenterTasks(query)
  tasks.value = result.records || []
  total.value = result.total || 0
  if (tasks.value.length === 0 && total.value > 0 && page.value > totalPages.value) {
    page.value = totalPages.value
    await loadTasks()
  }
}

const loadTaskDetail = async (id) => {
  selectedTaskId.value = id
  taskDetail.value = await getDepartmentCenterTask(id)
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

const changePageSize = async (nextSize) => {
  pageSize.value = nextSize
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
          <el-select v-model="departmentId" :disabled="loading" placeholder="全部部门" clearable @change="applyFilters">
            <el-option label="全部部门" value="" />
            <el-option
              v-for="department in departments"
              :key="department.id"
              :label="`${department.department_name} / ${label(department.department_type)}`"
              :value="department.id"
            />
          </el-select>
        </label>
        <label>
          状态
          <el-select v-model="status" :disabled="loading" @change="applyFilters">
            <el-option label="当前任务" value="ACTIVE" />
            <el-option label="已释放" value="RELEASED" />
            <el-option label="全部" value="ALL" />
          </el-select>
        </label>
        <div class="task-filter-actions">
          <el-button type="primary" :disabled="loading" @click="applyFilters">查询</el-button>
          <el-button :disabled="loading || (!departmentId && status === 'ACTIVE')" @click="clearFilters">清空</el-button>
          <el-button :disabled="loading" @click="run(refreshAll)">刷新</el-button>
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
          <ApsProcessTable :rows="taskDetail.processRows || []" />
        </div>

        <form class="form-grid side-form" @submit.prevent>
          <label>
            当前部门
            <el-input :model-value="field(taskDetail.task, 'assigned_department_name', 'assignedDepartmentName') || '-'" disabled />
          </label>
          <label>
            需生产数量
            <el-input :model-value="number(field(taskDetail.task, 'qty'), 0)" disabled />
          </label>
          <label>
            已分配工时
            <el-input :model-value="number(field(taskDetail.task, 'total_work_hours', 'totalWorkHours'), 2)" disabled />
          </label>
        </form>
      </div>
      <p v-else class="empty-state">从部门中心列表选择一条零件查看详情。</p>
    </section>
  </section>
</template>
