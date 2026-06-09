<script setup>
import { onMounted, reactive, ref } from 'vue'
import { request } from '../api'
import DataTable from '../components/DataTable.vue'

const loading = ref(false)
const error = ref('')
const notice = ref('')
const materialInventory = ref([])
const materialLocks = ref([])
const materialShortages = ref([])

const form = reactive({
  materialName: '',
  materialType: '',
  stockWeightKg: 0,
  safetyStockWeightKg: 0,
  unit: 'kg',
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

const loadData = async () => {
  const [inventory, locks, shortages] = await Promise.all([
    request('/api/aps/inventory/materials?limit=200'),
    request('/api/aps/inventory/materials/locks?limit=100'),
    request('/api/aps/inventory/materials/shortages?limit=100')
  ])
  materialInventory.value = inventory
  materialLocks.value = locks
  materialShortages.value = shortages
}

const save = async () => {
  await run(async () => {
    try {
      await request('/api/aps/inventory/materials', {
        method: 'POST',
        body: JSON.stringify({
          ...form,
          stockWeightKg: Number(form.stockWeightKg || 0),
          safetyStockWeightKg: Number(form.safetyStockWeightKg || 0)
        })
      })
    } catch (err) {
      if (err?.status === 409) {
        throw new Error('该材料名称和类型已存在，请直接修改现有库存。')
      }
      throw err
    }
    reset()
    await loadData()
  }, '材料库存已保存')
}

const remove = async (id) => {
  await run(async () => {
    await request(`/api/aps/inventory/materials/${id}`, { method: 'DELETE' })
    await loadData()
  }, '材料库存已删除')
}

const reset = () => {
  Object.assign(form, { materialName: '', materialType: '', stockWeightKg: 0, safetyStockWeightKg: 0, unit: 'kg', remark: '' })
}

onMounted(() => run(loadData))
</script>

<template>
  <section class="content-grid">
    <p v-if="error" class="message error">{{ error }}</p>
    <p v-else-if="notice" class="message notice">{{ notice }}</p>

    <section class="panel">
      <div class="section-head">
        <h2>材料库存维护</h2>
        <div class="section-actions">
          <span>材料名称 + 类型</span>
          <button class="secondary" type="button" :disabled="loading" @click="run(loadData)">刷新</button>
        </div>
      </div>
      <form class="form-grid six" @submit.prevent="save">
        <label>材料名称<input v-model="form.materialName" required /></label>
        <label>材料类型<input v-model="form.materialType" placeholder="板材/棒材/管材" /></label>
        <label>库存 kg<input v-model.number="form.stockWeightKg" min="0" step="0.01" type="number" /></label>
        <label>安全库存 kg<input v-model.number="form.safetyStockWeightKg" min="0" step="0.01" type="number" /></label>
        <label>单位<input v-model="form.unit" /></label>
        <label>备注<input v-model="form.remark" /></label>
        <button class="primary" type="submit" :disabled="loading">保存材料库存</button>
      </form>
    </section>

    <DataTable title="材料库存" :rows="materialInventory" deletable :columns="[
      ['material_name', '材料'], ['material_type', '类型'], ['stock_weight_kg', '库存 kg'],
      ['locked_weight_kg', '锁定 kg'], ['available_weight_kg', '可用 kg'],
      ['safety_stock_weight_kg', '安全库存'], ['unit', '单位']
    ]" @delete-row="remove" />

    <DataTable title="材料锁定记录" :rows="materialLocks" :columns="[
      ['work_item_id', '任务'], ['rfq_no', 'RFQ'], ['internal_project_no', '内部项目号'],
      ['customer_name', '客户'], ['part_no', '图号'], ['material_name', '材料'],
      ['material_type', '类型'], ['demand_weight_kg', '需求 kg'],
      ['locked_weight_kg', '锁定 kg'], ['shortage_weight_kg', '缺口 kg'], ['status', '状态']
    ]" />

    <DataTable title="材料缺口" :rows="materialShortages" :columns="[
      ['work_item_id', '任务'], ['rfq_no', 'RFQ'], ['internal_project_no', '内部项目号'],
      ['customer_name', '客户'], ['part_no', '图号'], ['material_name', '材料'],
      ['material_type', '类型'], ['shortage_weight_kg', '缺口 kg'], ['status', '状态']
    ]" />
  </section>
</template>
