<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { request } from '../api'
import { customerProjectText, field, label, number } from '../helpers'
import ApsPager from '../components/ApsPager.vue'
import ApsProcessTable from '../components/ApsProcessTable.vue'
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

const changePageSize = async (nextSize) => {
  pageSize.value = nextSize
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
          <el-select v-model="customerName" :disabled="loading" placeholder="全部客户" clearable @change="applyFilters">
            <el-option label="全部客户" value="" />
            <el-option v-for="customer in customerOptions" :key="customer" :label="customer" :value="customer" />
          </el-select>
        </label>
        <label>
          排列方式
          <el-select v-model="sortMode" :disabled="loading" placeholder="默认排序" @change="applyFilters">
            <el-option v-for="option in sortOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </label>
        <div class="task-filter-actions">
          <el-button type="primary" :disabled="loading" @click="applyFilters">查询</el-button>
          <el-button :disabled="loading || (!customerName && !sortMode)" @click="clearFilters">清空</el-button>
          <el-button :disabled="loading" @click="run(() => Promise.all([loadTasks(), loadDepartments(), loadCustomers()]))">刷新待分配</el-button>
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

    <ApsPager
      :disabled="loading"
      :page="page"
      :page-size="pageSize"
      :total="total"
      @current-change="goPage"
      @size-change="changePageSize"
    />

    <el-dialog
      v-model="assignmentModal.open"
      class="assignment-dialog"
      width="1120px"
      @close="closeAssignmentModal"
    >
      <template #header>
        <div class="dialog-title">
          <h2>{{ assignmentModal.mode === 'bulk' ? '批量分配' : '分配零件' }}</h2>
          <span>{{ assignmentModal.mode === 'bulk' ? `已选择 ${assignmentModal.ids.length} 条任务` : `#${assignmentModal.ids[0]}` }}</span>
        </div>
      </template>

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

          <ApsProcessTable
            v-if="assignmentModal.mode === 'single' && assignmentDetail"
            :rows="assignmentDetail.processRows || []"
            empty-text="暂无工艺"
          />

          <el-table
            v-if="assignmentModal.mode === 'bulk'"
            :data="assignmentModal.tasks"
            border
            class="aps-data-table"
            empty-text="暂无任务"
            row-key="id"
            stripe
          >
            <el-table-column label="任务" width="86">
              <template #default="{ row: task }">#{{ field(task, 'id') }}</template>
            </el-table-column>
            <el-table-column label="客户" min-width="130">
              <template #default="{ row: task }">{{ field(task, 'customer_name', 'customerName') || '-' }}</template>
            </el-table-column>
            <el-table-column label="图号 / 版本" min-width="150">
              <template #default="{ row: task }">{{ field(task, 'part_no', 'partNo') }} / {{ field(task, 'version') || '-' }}</template>
            </el-table-column>
            <el-table-column align="right" label="需生产" width="92">
              <template #default="{ row: task }">{{ number(field(task, 'qty'), 0) }}</template>
            </el-table-column>
            <el-table-column align="right" label="工时" width="92">
              <template #default="{ row: task }">{{ number(field(task, 'total_work_hours', 'totalWorkHours'), 2) }}</template>
            </el-table-column>
          </el-table>
        </div>

        <form class="form-grid assignment-form" @submit.prevent="submitAssignment">
          <label>
            部门
            <el-select v-model="assignmentForm.departmentId" placeholder="选择部门">
              <el-option
                v-for="department in departments"
                :key="department.id"
                :label="`${department.department_name} / ${label(department.department_type)} / 剩余 ${number(department.remaining_hours, 2)}h`"
                :value="department.id"
              >
                {{ department.department_name }} / {{ label(department.department_type) }} / 剩余 {{ number(department.remaining_hours, 2) }}h
              </el-option>
            </el-select>
          </label>
          <label>分配人<el-input v-model="assignmentForm.assignedBy" /></label>
          <label>备注<el-input v-model="assignmentForm.remark" /></label>
          <el-button type="primary" native-type="submit" :disabled="loading || !assignmentForm.departmentId">
            {{ assignmentModal.mode === 'bulk' ? '批量分配到部门' : '分配到部门' }}
          </el-button>
        </form>
      </div>
    </el-dialog>
  </section>
</template>
