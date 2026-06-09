<script setup>
import { onMounted, reactive, ref } from 'vue'
import { request } from '../api'
import { label, number, statusClass } from '../helpers'

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
  departments.value = await request('/api/aps/pmc/departments')
  syncDrafts()
}

const save = async () => {
  await run(async () => {
    try {
      await request('/api/aps/pmc/departments', {
        method: 'POST',
        body: JSON.stringify({
          ...form,
          monthlyAvailableHours: form.monthlyAvailableHours === '' ? null : Number(form.monthlyAvailableHours)
        })
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
    await request(`/api/aps/pmc/departments/${department.id}/adjust-capacity`, {
      method: 'POST',
      body: JSON.stringify({ deltaHours: direction * hours })
    })
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
    await request(`/api/aps/pmc/departments/${department.id}/reset-capacity`, {
      method: 'POST',
      body: JSON.stringify({ monthlyAvailableHours })
    })
    await loadData()
  }, '部门月度容量已重置')
}

const remove = async (department) => {
  if (!window.confirm(`确认删除部门“${department.department_name}”？有正在分配任务时不能删除。`)) return
  await run(async () => {
    await request(`/api/aps/pmc/departments/${department.id}`, { method: 'DELETE' })
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
          <button class="secondary" type="button" :disabled="loading" @click="run(loadData)">刷新</button>
        </div>
      </div>
      <form class="form-grid six" @submit.prevent="save">
        <label>部门名称<input v-model="form.departmentName" required /></label>
        <label>
          类型
          <select v-model="form.departmentType">
            <option value="PRODUCTION">生产部门</option>
            <option value="OUTSOURCE">外协部门</option>
          </select>
        </label>
        <label>
          状态
          <select v-model="form.departmentStatus">
            <option value="ENABLED">启用</option>
            <option value="DISABLED">停用</option>
          </select>
        </label>
        <label>月可用工时<input v-model.number="form.monthlyAvailableHours" min="0" step="10" type="number" /></label>
        <label>备注<input v-model="form.remark" /></label>
        <button class="primary" type="submit" :disabled="loading">保存部门</button>
      </form>
    </section>

    <section class="department-grid">
      <article v-for="department in departments" :key="department.id" class="department-card">
        <div class="department-card-head">
          <div>
            <h2>{{ department.department_name }}</h2>
            <div class="mini-line">
              <span class="pill" :class="statusClass(department.department_type)">{{ label(department.department_type) }}</span>
              <span class="pill" :class="statusClass(department.department_status)">{{ label(department.department_status) }}</span>
            </div>
          </div>
          <button class="text-button danger" type="button" :disabled="loading" @click="remove(department)">删除</button>
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
            <input v-model.number="adjustForms[department.id]" min="10" step="10" type="number" />
          </label>
          <button class="secondary" type="button" :disabled="loading" @click="adjustCapacity(department, 1)">增加</button>
          <button class="secondary" type="button" :disabled="loading" @click="adjustCapacity(department, -1)">减少</button>
        </div>

        <div class="capacity-actions reset-row">
          <label>
            新月可用工时
            <input v-model.number="resetForms[department.id]" min="0" step="10" type="number" />
          </label>
          <button class="primary" type="button" :disabled="loading" @click="resetCapacity(department)">新月重置</button>
        </div>
      </article>
      <p v-if="departments.length === 0" class="empty-state">暂无部门</p>
    </section>
  </section>
</template>
