<script setup>
import { onMounted, reactive, ref } from 'vue'
import { createMaterial, deleteMaterial, listMaterials } from '../services/inventoryApi'
import DataTable from '../components/DataTable.vue'

const loading = ref(false)
const error = ref('')
const notice = ref('')
const materialInventory = ref([])

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
  materialInventory.value = await listMaterials({ limit: '200' })
}

const save = async () => {
  if (!form.materialName.trim()) {
    error.value = '请输入材料名称'
    return
  }
  await run(async () => {
    try {
      await createMaterial({
        ...form,
        stockWeightKg: Number(form.stockWeightKg || 0),
        safetyStockWeightKg: Number(form.safetyStockWeightKg || 0)
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
    await deleteMaterial(id)
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
          <el-button :disabled="loading" @click="run(loadData)">刷新</el-button>
        </div>
      </div>
      <form class="form-grid six" @submit.prevent="save">
        <label>材料名称<el-input v-model="form.materialName" placeholder="必填" /></label>
        <label>材料类型<el-input v-model="form.materialType" placeholder="板材/棒材/管材" /></label>
        <label>库存 kg<el-input-number v-model="form.stockWeightKg" :min="0" :precision="2" :step="0.01" controls-position="right" /></label>
        <label>安全库存 kg<el-input-number v-model="form.safetyStockWeightKg" :min="0" :precision="2" :step="0.01" controls-position="right" /></label>
        <label>单位<el-input v-model="form.unit" /></label>
        <label>备注<el-input v-model="form.remark" /></label>
        <el-button type="primary" native-type="submit" :disabled="loading">保存材料库存</el-button>
      </form>
    </section>

    <DataTable title="材料库存" :rows="materialInventory" deletable :columns="[
      ['material_name', '材料'], ['material_type', '类型'], ['stock_weight_kg', '库存 kg'],
      ['locked_weight_kg', '锁定 kg'], ['available_weight_kg', '可用 kg'],
      ['safety_stock_weight_kg', '安全库存'], ['unit', '单位']
    ]" @delete-row="remove" />
  </section>
</template>
