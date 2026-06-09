<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { request } from '../api'
import { label, statusClass } from '../helpers'

const PROCESS_TYPES = [
  { value: 'WORK_HOUR', label: '工时工艺' },
  { value: 'NON_WORK_HOUR', label: '非工时工艺' }
]

const MACHINE_STATUSES = [
  { value: 'IDLE', label: '空闲' },
  { value: 'RUNNING', label: '生产中' },
  { value: 'MAINTENANCE', label: '维修' },
  { value: 'STOPPED', label: '停机' }
]

const loading = ref(false)
const error = ref('')
const notice = ref('')
const departments = ref([])
const machines = ref([])
const capabilities = ref([])
const processes = ref([])
const selectedDepartmentId = ref('')
const machineDrafts = reactive({})
const capabilityDrafts = reactive({})

const machineForm = reactive({
  machineCode: '',
  machineName: '',
  machineStatus: 'IDLE',
  remark: ''
})

const selectedDepartment = computed(() => (
  departments.value.find((department) => String(department.id) === String(selectedDepartmentId.value))
))

const visibleMachines = computed(() => (
  machines.value.filter((machine) => String(machine.department_id) === String(selectedDepartmentId.value))
))

const capabilityMap = computed(() => {
  const groups = new Map()
  capabilities.value.forEach((capability) => {
    const key = String(capability.machine_id)
    if (!groups.has(key)) groups.set(key, [])
    groups.get(key).push(capability)
  })
  groups.forEach((rows) => {
    rows.sort((left, right) => {
      const leftType = processTypeForCapability(left)
      const rightType = processTypeForCapability(right)
      return leftType.localeCompare(rightType) || String(left.process_name).localeCompare(String(right.process_name), 'zh-CN')
    })
  })
  return groups
})

const selectedCapabilityCount = computed(() => (
  visibleMachines.value.reduce((sum, machine) => sum + capabilitiesForMachine(machine.id).length, 0)
))

const typeLabel = (value) => (
  PROCESS_TYPES.find((type) => type.value === value)?.label || value || '-'
)

const normalizeName = (value) => String(value || '').trim().toLowerCase()

const processTypeForCapability = (capability) => {
  if (capability?.process_type) return capability.process_type
  const calcType = String(capability?.calc_type || '')
  if (calcType === 'NON_WORK_HOUR' || calcType === '非工时费用') return 'NON_WORK_HOUR'
  return 'WORK_HOUR'
}

const capabilitiesForMachine = (machineId) => (
  capabilityMap.value.get(String(machineId)) || []
)

const availableProcessesForMachine = (machineId) => {
  const draft = capabilityDrafts[machineId]
  const processType = draft?.processType || 'WORK_HOUR'
  const existing = capabilitiesForMachine(machineId)
  const usedIds = new Set(existing.map((capability) => String(capability.process_id || '')).filter(Boolean))
  const usedNames = new Set(existing.map((capability) => (
    `${processTypeForCapability(capability)}:${normalizeName(capability.process_name)}`
  )))

  return processes.value.filter((process) => (
    process.process_type === processType
    && !usedIds.has(String(process.id))
    && !usedNames.has(`${processType}:${normalizeName(process.process_name)}`)
  ))
}

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

const syncDrafts = () => {
  machines.value.forEach((machine) => {
    if (!machineDrafts[machine.id]) {
      machineDrafts[machine.id] = {
        machineCode: machine.machine_code || '',
        machineName: machine.machine_name || '',
        machineStatus: machine.machine_status || 'IDLE',
        remark: machine.remark || ''
      }
    }
    if (!capabilityDrafts[machine.id]) {
      capabilityDrafts[machine.id] = { processType: 'WORK_HOUR', processId: '', remark: '' }
    }
  })
}

const syncSelectedDepartment = () => {
  const exists = departments.value.some((department) => String(department.id) === String(selectedDepartmentId.value))
  if (!exists) {
    selectedDepartmentId.value = departments.value[0] ? String(departments.value[0].id) : ''
  }
}

const loadData = async () => {
  const [departmentRows, machineRows, capabilityRows, processRows] = await Promise.all([
    request('/api/aps/pmc/departments'),
    request('/api/aps/pmc/machines'),
    request('/api/aps/pmc/machine-capabilities'),
    request('/api/aps/processes')
  ])
  departments.value = departmentRows
  machines.value = machineRows
  capabilities.value = capabilityRows
  processes.value = processRows
  syncSelectedDepartment()
  syncDrafts()
}

const selectDepartment = () => {
  error.value = ''
  notice.value = ''
  resetMachine()
}

const saveMachine = async () => {
  if (!selectedDepartmentId.value) {
    error.value = '请先选择部门'
    return
  }
  await run(async () => {
    try {
      await request('/api/aps/pmc/machines', {
        method: 'POST',
        body: JSON.stringify({
          departmentId: Number(selectedDepartmentId.value),
          machineCode: machineForm.machineCode,
          machineName: machineForm.machineName,
          machineStatus: machineForm.machineStatus,
          remark: machineForm.remark
        })
      })
    } catch (err) {
      if (err?.status === 409) {
        throw new Error('该部门下的机台编号已存在，请使用其他编号。')
      }
      throw err
    }
    resetMachine()
    await loadData()
  }, '机台已保存')
}

const updateMachine = async (machine) => {
  const draft = machineDrafts[machine.id]
  if (!draft) return
  if (!String(draft.machineName || '').trim()) {
    error.value = '机台名称不能为空'
    return
  }
  await run(async () => {
    try {
      await request(`/api/aps/pmc/machines/${machine.id}`, {
        method: 'PUT',
        body: JSON.stringify({
          departmentId: Number(machine.department_id),
          machineCode: draft.machineCode,
          machineName: draft.machineName,
          machineStatus: draft.machineStatus,
          remark: draft.remark
        })
      })
    } catch (err) {
      if (err?.status === 409) {
        throw new Error('该部门下的机台编号已存在，请使用其他编号。')
      }
      throw err
    }
    await loadData()
  }, '机台已更新')
}

const removeMachine = async (machine) => {
  if (!window.confirm(`确认删除机台“${machine.machine_name}”？相关能力会一起删除。`)) return
  await run(async () => {
    await request(`/api/aps/pmc/machines/${machine.id}`, { method: 'DELETE' })
    await loadData()
  }, '机台已删除')
}

const saveCapability = async (machine) => {
  const draft = capabilityDrafts[machine.id]
  if (!draft?.processId) {
    error.value = '请选择工艺'
    return
  }
  await run(async () => {
    try {
      await request('/api/aps/pmc/machine-capabilities', {
        method: 'POST',
        body: JSON.stringify({
          machineId: machine.id,
          processId: Number(draft.processId),
          remark: draft.remark
        })
      })
    } catch (err) {
      if (err?.status === 409) {
        throw new Error('该机台已具备这道工艺能力，无需重复添加。')
      }
      throw err
    }
    capabilityDrafts[machine.id] = {
      processType: draft.processType || 'WORK_HOUR',
      processId: '',
      remark: ''
    }
    await loadData()
  }, '机台能力已保存')
}

const resetCapabilityProcess = (machineId) => {
  if (capabilityDrafts[machineId]) {
    capabilityDrafts[machineId].processId = ''
  }
}

const removeCapability = async (capability) => {
  if (!window.confirm(`确认删除“${capability.process_name}”能力？`)) return
  await run(async () => {
    await request(`/api/aps/pmc/machine-capabilities/${capability.id}`, { method: 'DELETE' })
    await loadData()
  }, '机台能力已删除')
}

const resetMachine = () => {
  Object.assign(machineForm, { machineCode: '', machineName: '', machineStatus: 'IDLE', remark: '' })
}

onMounted(() => run(loadData))
</script>

<template>
  <section class="content-grid">
    <p v-if="error" class="message error">{{ error }}</p>
    <p v-else-if="notice" class="message notice">{{ notice }}</p>

    <section class="panel">
      <div class="section-head">
        <h2>机台能力</h2>
        <div class="section-actions">
          <select v-model="selectedDepartmentId" class="control-short" @change="selectDepartment">
            <option value="">选择部门</option>
            <option v-for="department in departments" :key="department.id" :value="String(department.id)">
              {{ department.department_name }}
            </option>
          </select>
          <button class="secondary" type="button" :disabled="loading" @click="run(loadData)">刷新</button>
        </div>
      </div>

      <p v-if="!selectedDepartmentId" class="empty-state">请先选择部门，再维护该部门下的机台和可处理工艺。</p>

      <template v-else>
        <div class="sync-summary">
          <div>
            <span>当前部门</span>
            <strong>{{ selectedDepartment?.department_name || '-' }}</strong>
          </div>
          <div>
            <span>机台数量</span>
            <strong>{{ visibleMachines.length }}</strong>
          </div>
          <div>
            <span>能力项</span>
            <strong>{{ selectedCapabilityCount }}</strong>
          </div>
          <div>
            <span>可选工艺</span>
            <strong>{{ processes.length }}</strong>
          </div>
        </div>

        <form class="form-grid six" @submit.prevent="saveMachine">
          <label>机台编号<input v-model="machineForm.machineCode" required /></label>
          <label>机台名称<input v-model="machineForm.machineName" required /></label>
          <label>
            状态
            <select v-model="machineForm.machineStatus">
              <option v-for="status in MACHINE_STATUSES" :key="status.value" :value="status.value">
                {{ status.label }}
              </option>
            </select>
          </label>
          <label>备注<input v-model="machineForm.remark" /></label>
          <button class="primary" type="submit" :disabled="loading">新增机台</button>
        </form>
      </template>
    </section>

    <section v-if="selectedDepartmentId" class="machine-grid">
      <article v-for="machine in visibleMachines" :key="machine.id" class="machine-card">
        <div class="machine-card-head">
          <div>
            <h2>{{ machine.machine_name }}</h2>
            <div class="mini-line">
              <span>{{ machine.machine_code || '未编号' }}</span>
              <span class="pill" :class="statusClass(machine.machine_status)">{{ label(machine.machine_status) }}</span>
            </div>
          </div>
          <button class="text-button danger" type="button" :disabled="loading" @click="removeMachine(machine)">删除</button>
        </div>

        <div v-if="machineDrafts[machine.id]" class="machine-edit">
          <label>编号<input v-model="machineDrafts[machine.id].machineCode" /></label>
          <label>名称<input v-model="machineDrafts[machine.id].machineName" required /></label>
          <label>
            状态
            <select v-model="machineDrafts[machine.id].machineStatus">
              <option v-for="status in MACHINE_STATUSES" :key="status.value" :value="status.value">
                {{ status.label }}
              </option>
            </select>
          </label>
          <label>备注<input v-model="machineDrafts[machine.id].remark" /></label>
          <button class="secondary" type="button" :disabled="loading" @click="updateMachine(machine)">保存机台</button>
        </div>

        <div class="capability-list">
          <div class="capability-title">
            <strong>可处理工艺</strong>
            <span>{{ capabilitiesForMachine(machine.id).length }} 项</span>
          </div>
          <div v-if="capabilitiesForMachine(machine.id).length" class="capability-tags">
            <span v-for="capability in capabilitiesForMachine(machine.id)" :key="capability.id" class="capability-tag">
              <span>
                {{ capability.process_name }}
                <small>{{ typeLabel(processTypeForCapability(capability)) }}</small>
              </span>
              <button class="text-button danger" type="button" :disabled="loading" @click="removeCapability(capability)">删除</button>
            </span>
          </div>
          <p v-else class="muted">暂无工艺能力</p>
        </div>

        <form v-if="capabilityDrafts[machine.id]" class="capability-form" @submit.prevent="saveCapability(machine)">
          <label>
            类型
            <select v-model="capabilityDrafts[machine.id].processType" @change="resetCapabilityProcess(machine.id)">
              <option v-for="type in PROCESS_TYPES" :key="type.value" :value="type.value">
                {{ type.label }}
              </option>
            </select>
          </label>
          <label>
            工艺
            <select
              v-model="capabilityDrafts[machine.id].processId"
              :disabled="availableProcessesForMachine(machine.id).length === 0"
              required
            >
              <option value="">选择工艺</option>
              <option
                v-for="process in availableProcessesForMachine(machine.id)"
                :key="process.id"
                :value="process.id"
              >
                {{ process.process_name }}
              </option>
            </select>
          </label>
          <label>备注<input v-model="capabilityDrafts[machine.id].remark" /></label>
          <button class="primary" type="submit" :disabled="loading || availableProcessesForMachine(machine.id).length === 0">
            添加能力
          </button>
        </form>

        <p
          v-if="capabilityDrafts[machine.id] && availableProcessesForMachine(machine.id).length === 0"
          class="form-hint"
        >
          当前类型暂无可添加工艺，请先同步 ERP 工艺或检查是否已全部配置。
        </p>
      </article>

      <p v-if="visibleMachines.length === 0" class="empty-state">该部门暂无机台。</p>
    </section>
  </section>
</template>
