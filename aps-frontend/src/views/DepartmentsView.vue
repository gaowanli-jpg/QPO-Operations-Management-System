<script setup>
import { onMounted, reactive, ref } from 'vue'
import {
  adjustDepartmentCapacity,
  createDepartment,
  deleteDepartment,
  listDepartments,
  resetDepartmentCapacity
} from '../services/departmentApi'
import { number } from '../helpers'
import ApsStatusTag from '../components/ApsStatusTag.vue'

const loading = ref(false)
const error = ref('')
const notice = ref('')
const departments = ref([])
const adjustForms = reactive({})
const resetForms = reactive({})

const form = reactive({
  departmentName: '',
  departmentType: 'PRODUCTION',
  departmentStatus: 'ENABLED',
  monthlyAvailableHours: 400,
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

const syncDrafts = () => {
  departments.value.forEach((department) => {
    if (adjustForms[department.id] === undefined) adjustForms[department.id] = 10
    if (resetForms[department.id] === undefined) {
      resetForms[department.id] = Number(department.monthly_available_hours || 0)
    }
  })
}

const loadData = async () => {
  departments.value = await listDepartments()
  syncDrafts()
}

const save = async () => {
  if (!form.departmentName.trim()) {
    error.value = '请输入部门名称'
    return
  }
  if (Number(form.monthlyAvailableHours || 0) < 0) {
    error.value = '月可用工时不能为负数'
    return
  }
  await run(async () => {
    try {
      await createDepartment({
        departmentName: form.departmentName.trim(),
        departmentType: form.departmentType,
        departmentStatus: form.departmentStatus,
        remark: form.remark.trim(),
        monthlyAvailableHours: form.monthlyAvailableHours === '' ? null : Number(form.monthlyAvailableHours)
      })
    } catch (err) {
      if (err?.status === 409) {
        throw new Error('部门名称已存在，请使用其他名称。')
      }
      throw err
    }
    reset()
    await loadData()
  }, '部门已保存')
}

const adjustCapacity = async (department, direction) => {
  const hours = Number(adjustForms[department.id] || 0)
  if (!hours || hours <= 0) {
    error.value = '请输入要调整的工时'
    return
  }
  await run(async () => {
    await adjustDepartmentCapacity(department.id, direction * hours)
    await loadData()
  }, direction > 0 ? '月可用工时已增加' : '月可用工时已减少')
}

const resetCapacity = async (department) => {
  const monthlyAvailableHours = Number(resetForms[department.id] || 0)
  if (monthlyAvailableHours < 0) {
    error.value = '月可用工时不能为负数'
    return
  }
  if (!window.confirm(`确认重置“${department.department_name}”的新月容量？已使用工时将清零。`)) return
  await run(async () => {
    await resetDepartmentCapacity(department.id, monthlyAvailableHours)
    await loadData()
  }, '部门月度容量已重置')
}

const remove = async (department) => {
  if (!window.confirm(`确认删除部门“${department.department_name}”？有正在分配任务时不能删除。`)) return
  await run(async () => {
    await deleteDepartment(department.id)
    await loadData()
  }, '部门已删除')
}

const reset = () => {
  Object.assign(form, {
    departmentName: '',
    departmentType: 'PRODUCTION',
    departmentStatus: 'ENABLED',
    monthlyAvailableHours: 400,
    remark: ''
  })
}

const usagePercent = (department) => {
  const monthly = Number(department.monthly_available_hours || 0)
  if (monthly <= 0) return 0
  return Math.min(100, Math.round((Number(department.occupied_hours || 0) / monthly) * 100))
}

const usageTone = (department) => {
  const percent = usagePercent(department)
  if (percent >= 90) return 'bad'
  if (percent >= 70) return 'warn'
  return 'ok'
}

onMounted(() => run(loadData))
</script>

<template>
  <section class="content-grid">
    <p v-if="error" class="message error">{{ error }}</p>
    <p v-else-if="notice" class="message notice">{{ notice }}</p>

    <section class="panel">
      <div class="section-head">
        <h2>部门维护</h2>
        <div class="section-actions">
          <span>月度工时容量池</span>
          <el-button :disabled="loading" @click="run(loadData)">刷新</el-button>
        </div>
      </div>
      <form class="form-grid six" @submit.prevent="save">
        <label>部门名称<el-input v-model="form.departmentName" placeholder="必填" /></label>
        <label>
          类型
          <el-select v-model="form.departmentType">
            <el-option label="生产部门" value="PRODUCTION" />
            <el-option label="外协部门" value="OUTSOURCE" />
          </el-select>
        </label>
        <label>
          状态
          <el-select v-model="form.departmentStatus">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </label>
        <label>月可用工时<el-input-number v-model="form.monthlyAvailableHours" :min="0" :step="10" controls-position="right" /></label>
        <label>备注<el-input v-model="form.remark" /></label>
        <el-button type="primary" native-type="submit" :disabled="loading">保存部门</el-button>
      </form>
    </section>

    <section class="department-grid">
      <article v-for="department in departments" :key="department.id" class="department-card">
        <div class="department-card-head">
          <div>
            <h2>{{ department.department_name }}</h2>
            <div class="mini-line">
              <ApsStatusTag :value="department.department_type" />
              <ApsStatusTag :value="department.department_status" />
            </div>
          </div>
          <el-button type="danger" link :disabled="loading" @click="remove(department)">删除</el-button>
        </div>

        <div class="capacity-metrics">
          <div>
            <span>月可用</span>
            <strong>{{ number(department.monthly_available_hours, 2) }}h</strong>
          </div>
          <div>
            <span>已使用</span>
            <strong>{{ number(department.occupied_hours, 2) }}h</strong>
          </div>
          <div>
            <span>剩余</span>
            <strong>{{ number(department.remaining_hours, 2) }}h</strong>
          </div>
        </div>

        <div class="capacity-bar">
          <div
            class="capacity-fill"
            :class="usageTone(department)"
            :style="{ width: `${usagePercent(department)}%` }"
          />
        </div>
        <div class="capacity-foot">
          <span>使用率 {{ usagePercent(department) }}%</span>
          <span>{{ department.remark || '-' }}</span>
        </div>

        <div class="capacity-actions">
          <label>
            调整工时
            <el-input-number v-model="adjustForms[department.id]" :min="10" :step="10" controls-position="right" />
          </label>
          <el-button :disabled="loading" @click="adjustCapacity(department, 1)">增加</el-button>
          <el-button :disabled="loading" @click="adjustCapacity(department, -1)">减少</el-button>
        </div>

        <div class="capacity-actions reset-row">
          <label>
            新月可用工时
            <el-input-number v-model="resetForms[department.id]" :min="0" :step="10" controls-position="right" />
          </label>
          <el-button type="primary" :disabled="loading" @click="resetCapacity(department)">新月重置</el-button>
        </div>
      </article>
      <p v-if="departments.length === 0" class="empty-state">暂无部门</p>
    </section>
  </section>
</template>
