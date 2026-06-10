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
const customerOptions = ref([])
const customerName = ref('')
const sortMode = ref('')
const selectedTaskIds = ref([])
const assignmentDetail = ref(null)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))

const assignmentModal = reactive({
  open: false,
  mode: 'single',
  ids: [],
  tasks: []
})

const assignmentForm = reactive({
  departmentId: '',
  assignedBy: 'PMC',
  remark: ''
})

const sortOptions = [
  { value: '', label: '默认排序' },
  { value: 'MATERIAL', label: '按材料排列' },
  { value: 'PROCESS', label: '按工艺路线排列' },
  { value: 'PROCESS_MATCH', label: '按相同工艺数量' }
]

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
  if (customerName.value) query.set('customerName', customerName.value)
  if (sortMode.value) query.set('sortMode', sortMode.value)
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

const loadCustomers = async () => {
  customerOptions.value = await request('/api/aps/pmc/tasks/customers?assignmentStatus=PENDING')
}

const openSingleAssignment = async (id) => {
  if (!id) return
  await run(async () => {
    const detail = await request(`/api/aps/pmc/tasks/${id}`)
    assignmentDetail.value = detail
    assignmentModal.open = true
    assignmentModal.mode = 'single'
    assignmentModal.ids = [id]
    assignmentModal.tasks = [detail.task]
    assignmentForm.departmentId = field(detail.task, 'assigned_department_id', 'assignedDepartmentId') || ''
  })
}

const openBulkAssignment = () => {
  if (selectedTaskIds.value.length === 0) return
  const selected = selectedTaskIds.value
    .map((id) => tasks.value.find((task) => field(task, 'id') === id))
    .filter(Boolean)
  assignmentDetail.value = null
  assignmentModal.open = true
  assignmentModal.mode = 'bulk'
  assignmentModal.ids = [...selectedTaskIds.value]
  assignmentModal.tasks = selected
  assignmentForm.departmentId = ''
}

const closeAssignmentModal = () => {
  assignmentModal.open = false
  assignmentModal.mode = 'single'
  assignmentModal.ids = []
  assignmentModal.tasks = []
  assignmentDetail.value = null
  assignmentForm.departmentId = ''
  assignmentForm.remark = ''
}

const submitAssignment = async () => {
  if (assignmentModal.ids.length === 0 || !assignmentForm.departmentId) return
  const ids = [...assignmentModal.ids]
  const assignedCount = ids.length
  await run(async () => {
    await request('/api/aps/pmc/tasks/assign-department', {
      method: 'POST',
      body: JSON.stringify({
        ids,
        departmentId: Number(assignmentForm.departmentId),
        assignedBy: assignmentForm.assignedBy,
        remark: assignmentForm.remark
      })
    })
    closeAssignmentModal()
    selectedTaskIds.value = selectedTaskIds.value.filter((id) => !ids.includes(id))
    await Promise.all([loadTasks(), loadDepartments(), loadCustomers()])
  }, assignedCount > 1 ? `已批量分配 ${assignedCount} 条任务` : '任务已分配到部门')
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
  closeAssignmentModal()
  await run(loadTasks)
}

const changePageSize = async () => {
  page.value = 1
  closeAssignmentModal()
  await run(loadTasks)
}

const applyFilters = async () => {
  page.value = 1
  closeAssignmentModal()
  selectedTaskIds.value = []
  await run(loadTasks)
}

const clearFilters = async () => {
  customerName.value = ''
  sortMode.value = ''
  await applyFilters()
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
    if (assignmentModal.ids.some((id) => ids.includes(id))) {
      closeAssignmentModal()
    }
    selectedTaskIds.value = []
    await Promise.all([loadTasks(), loadDepartments(), loadCustomers()])
  }, `已删除 ${ids.length} 条 APS 任务`)
}

onMounted(() => run(() => Promise.all([loadTasks(), loadDepartments(), loadCustomers()])))
</script>

<template>
  <section class="content-grid">
    <section class="panel task-filter-panel">
      <div class="task-filter">
        <label>
          客户
          <select v-model="customerName" :disabled="loading" @change="applyFilters">
            <option value="">全部客户</option>
            <option v-for="customer in customerOptions" :key="customer" :value="customer">{{ customer }}</option>
          </select>
        </label>
        <label>
          排列方式
          <select v-model="sortMode" :disabled="loading" @change="applyFilters">
            <option v-for="option in sortOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
          </select>
        </label>
        <div class="task-filter-actions">
          <button class="primary" type="button" :disabled="loading" @click="applyFilters">查询</button>
          <button class="secondary" type="button" :disabled="loading || (!customerName && !sortMode)" @click="clearFilters">清空</button>
          <button class="secondary" type="button" :disabled="loading" @click="run(() => Promise.all([loadTasks(), loadDepartments(), loadCustomers()]))">刷新待分配</button>
        </div>
      </div>
    </section>

    <p v-if="error" class="message error">{{ error }}</p>
    <p v-else-if="notice" class="message notice">{{ notice }}</p>

    <TaskTable
      title="待分配零件"
      :tasks="tasks"
      :selected-ids="selectedTaskIds"
      :show-process-group="true"
      :process-sort-mode="sortMode"
      action-label="分配"
      :bulk-assignable="true"
      @select="openSingleAssignment"
      @toggle-select="toggleTaskSelect"
      @toggle-select-all="toggleAllTasks"
      @delete-selected="deleteSelectedTasks"
      @bulk-assign="openBulkAssignment"
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

    <div v-if="assignmentModal.open" class="modal-overlay" @click.self="closeAssignmentModal">
      <section class="modal-panel assignment-modal" role="dialog" aria-modal="true">
        <div class="modal-head">
          <div>
            <h2>{{ assignmentModal.mode === 'bulk' ? '批量分配' : '分配零件' }}</h2>
            <span>{{ assignmentModal.mode === 'bulk' ? `已选择 ${assignmentModal.ids.length} 条任务` : `#${assignmentModal.ids[0]}` }}</span>
          </div>
          <button class="text-button" type="button" @click="closeAssignmentModal">关闭</button>
        </div>

        <div class="assignment-modal-body">
          <div class="assignment-info">
            <dl v-if="assignmentModal.mode === 'single' && assignmentDetail">
              <div><dt>内部项目号</dt><dd>{{ field(assignmentDetail.task, 'internal_project_no', 'internalProjectNo') || '-' }}</dd></div>
              <div><dt>客户订单号</dt><dd>{{ field(assignmentDetail.task, 'customer_order_no', 'customerOrderNo') || '-' }}</dd></div>
              <div><dt>客户项目号</dt><dd>{{ customerProjectText(assignmentDetail.task) }}</dd></div>
              <div><dt>客户</dt><dd>{{ field(assignmentDetail.task, 'customer_name', 'customerName') || '-' }}</dd></div>
              <div><dt>图号/版本</dt><dd>{{ field(assignmentDetail.task, 'part_no', 'partNo') }} / {{ field(assignmentDetail.task, 'version') || '-' }}</dd></div>
              <div><dt>交期</dt><dd>{{ field(assignmentDetail.task, 'delivery_date', 'deliveryDate') || '-' }}</dd></div>
              <div><dt>工时</dt><dd>{{ number(field(assignmentDetail.task, 'total_work_hours', 'totalWorkHours'), 2) }}</dd></div>
              <div class="detail-wide route-text">
                <dt>完整工艺路线</dt>
                <dd>{{ field(assignmentDetail.task, 'process_route_text', 'processRouteText') || '-' }}</dd>
              </div>
            </dl>

            <div v-if="assignmentModal.mode === 'single' && assignmentDetail" class="table-wrap compact">
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
                  <tr v-for="row in assignmentDetail.processRows" :key="row.id">
                    <td>{{ row.sort_no || '-' }}</td>
                    <td>{{ row.process_name }}</td>
                    <td>{{ row.calc_type || row.source_type || '-' }}</td>
                    <td>{{ number(row.work_hours, 2) }}</td>
                    <td>{{ number(row.line_cost, 2) }}</td>
                  </tr>
                  <tr v-if="!assignmentDetail.processRows?.length">
                    <td class="empty" colspan="5">暂无工艺</td>
                  </tr>
                </tbody>
              </table>
            </div>

            <div v-if="assignmentModal.mode === 'bulk'" class="table-wrap compact">
              <table>
                <thead>
                  <tr>
                    <th>任务</th>
                    <th>客户</th>
                    <th>图号 / 版本</th>
                    <th>需生产</th>
                    <th>工时</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="task in assignmentModal.tasks" :key="field(task, 'id')">
                    <td>#{{ field(task, 'id') }}</td>
                    <td>{{ field(task, 'customer_name', 'customerName') || '-' }}</td>
                    <td>{{ field(task, 'part_no', 'partNo') }} / {{ field(task, 'version') || '-' }}</td>
                    <td>{{ number(field(task, 'qty'), 0) }}</td>
                    <td>{{ number(field(task, 'total_work_hours', 'totalWorkHours'), 2) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          <form class="form-grid assignment-form" @submit.prevent="submitAssignment">
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
            <button class="primary" type="submit" :disabled="loading || !assignmentForm.departmentId">
              {{ assignmentModal.mode === 'bulk' ? '批量分配到部门' : '分配到部门' }}
            </button>
          </form>
        </div>
      </section>
    </div>
  </section>
</template>
