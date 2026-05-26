<template>
  <div class="manage-container">
    <el-card shadow="never" class="manage-card">
      <template #header>
        <div class="card-header">
          <div>
            <h2>材料与工艺管理</h2>
            <p>维护材料价格字典和工艺阶梯费率，工程核价会直接读取这里的数据。</p>
          </div>
          <div class="header-actions">
            <el-button @click="loadAll" :loading="loading">刷新</el-button>
            <el-button v-if="activeTab === 'materials'" type="primary" @click="openMaterialDialog()">新增材料</el-button>
            <el-button v-else-if="activeTab === 'processes'" type="primary" @click="openProcessDialog()">新增工艺</el-button>
            <el-button v-else-if="activeTab === 'markupFees'" type="primary" @click="openMarkupFeeDialog()">新增费用类</el-button>
            <el-button v-else type="primary" @click="openExtraFeeDialog()">新增非工时费用工艺</el-button>
          </div>
        </div>
      </template>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="材料费用" name="materials">
          <el-table :data="materials" border stripe height="560" v-loading="loading">
            <el-table-column prop="materialName" label="材料" width="120" fixed />
            <el-table-column prop="materialType" label="材质" width="110" />
            <el-table-column prop="remarks" label="备注" min-width="150" show-overflow-tooltip />
            <el-table-column label="板材密度" width="150" align="right">
              <template #default="scope">{{ formatDensity(scope.row.plateDensity) }}</template>
            </el-table-column>
            <el-table-column label="板材单价" width="150" align="right">
              <template #default="scope">{{ formatPrice(scope.row.platePrice) }}</template>
            </el-table-column>
            <el-table-column label="棒材密度" width="150" align="right">
              <template #default="scope">{{ formatDensity(scope.row.barDensity) }}</template>
            </el-table-column>
            <el-table-column label="棒材单价" width="150" align="right">
              <template #default="scope">{{ formatPrice(scope.row.barPrice) }}</template>
            </el-table-column>
            <el-table-column label="管材密度" width="150" align="right">
              <template #default="scope">{{ formatDensity(scope.row.tubeDensity) }}</template>
            </el-table-column>
            <el-table-column label="管材单价" width="150" align="right">
              <template #default="scope">{{ formatPrice(scope.row.tubePrice) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="150" align="center" fixed="right">
              <template #default="scope">
                <el-button type="primary" size="small" @click="openMaterialDialog(scope.row)">编辑</el-button>
                <el-button type="danger" size="small" plain @click="deleteMaterial(scope.row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="工艺费用" name="processes">
          <el-table :data="processes" border stripe height="560" v-loading="loading">
            <el-table-column prop="sortNo" label="序号" width="70" align="center" fixed />
            <el-table-column prop="processName" label="工序" width="120" fixed />
            <el-table-column prop="calcType" label="类型" width="100" />
            <el-table-column prop="price1To5" label="1-5" width="90" align="right" />
            <el-table-column prop="price5To10" label="5-10" width="90" align="right" />
            <el-table-column prop="price10To50" label="10-50" width="90" align="right" />
            <el-table-column prop="price50To100" label="50-100" width="90" align="right" />
            <el-table-column prop="price100To500" label="100-500" width="100" align="right" />
            <el-table-column prop="price500To1000" label="500-1000" width="105" align="right" />
            <el-table-column prop="price1000To3000" label="1000-3000" width="115" align="right" />
            <el-table-column prop="price3000Plus" label=">3000" width="90" align="right" />
            <el-table-column label="操作" width="150" align="center" fixed="right">
              <template #default="scope">
                <el-button type="primary" size="small" @click="openProcessDialog(scope.row)">编辑</el-button>
                <el-button type="danger" size="small" plain @click="deleteProcess(scope.row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="百分比费用类" name="markupFees">
          <el-table :data="markupFees" border stripe height="560" v-loading="loading">
            <el-table-column prop="sortNo" label="序号" width="80" align="center" fixed />
            <el-table-column prop="feeName" label="费用名称" min-width="150" fixed />
            <el-table-column label="费率" width="120" align="right">
              <template #default="scope">{{ Number(scope.row.ratePercent || 0).toFixed(2) }}%</template>
            </el-table-column>
            <el-table-column label="启用" width="90" align="center">
              <template #default="scope">
                <el-tag :type="scope.row.enabled ? 'success' : 'info'" size="small">
                  {{ scope.row.enabled ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="160" align="center" fixed="right">
              <template #default="scope">
                <el-button type="primary" size="small" @click="openMarkupFeeDialog(scope.row)">编辑</el-button>
                <el-button type="danger" size="small" plain @click="deleteMarkupFee(scope.row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="非工时费用工艺" name="extraFees">
          <el-table :data="extraFeeItems" border stripe height="560" v-loading="loading">
            <el-table-column prop="sortNo" label="序号" width="80" align="center" fixed />
            <el-table-column prop="feeName" label="工艺名称" min-width="180" fixed />
            <el-table-column prop="remarks" label="备注" min-width="180" show-overflow-tooltip />
            <el-table-column label="启用" width="90" align="center">
              <template #default="scope">
                <el-tag :type="scope.row.enabled ? 'success' : 'info'" size="small">
                  {{ scope.row.enabled ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="160" align="center" fixed="right">
              <template #default="scope">
                <el-button type="primary" size="small" @click="openExtraFeeDialog(scope.row)">编辑</el-button>
                <el-button type="danger" size="small" plain @click="deleteExtraFee(scope.row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="materialDialogVisible" :title="materialForm.id ? '编辑材料' : '新增材料'" width="760px">
      <el-form :model="materialForm" label-width="140px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="材料" required>
              <el-input v-model="materialForm.materialName" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="材质">
              <el-input v-model="materialForm.materialType" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="materialForm.remarks" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="板材密度">
              <div class="unit-input">
                <el-input-number v-model="materialForm.plateDensity" :precision="4" :min="0" controls-position="right" class="unit-input-control" />
                <span class="unit-label">kg/m³</span>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="板材单价">
              <div class="unit-input">
                <el-input-number v-model="materialForm.platePrice" :precision="2" :min="0" controls-position="right" class="unit-input-control" />
                <span class="unit-label">元/kg</span>
              </div>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="棒材密度">
              <div class="unit-input">
                <el-input-number v-model="materialForm.barDensity" :precision="4" :min="0" controls-position="right" class="unit-input-control" />
                <span class="unit-label">kg/m³</span>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="棒材单价">
              <div class="unit-input">
                <el-input-number v-model="materialForm.barPrice" :precision="2" :min="0" controls-position="right" class="unit-input-control" />
                <span class="unit-label">元/kg</span>
              </div>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="管材密度">
              <div class="unit-input">
                <el-input-number v-model="materialForm.tubeDensity" :precision="4" :min="0" controls-position="right" class="unit-input-control" />
                <span class="unit-label">kg/m³</span>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="管材单价">
              <div class="unit-input">
                <el-input-number v-model="materialForm.tubePrice" :precision="2" :min="0" controls-position="right" class="unit-input-control" />
                <span class="unit-label">元/kg</span>
              </div>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="materialDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveMaterial">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="processDialogVisible" :title="processForm.id ? '编辑工艺' : '新增工艺'" width="860px">
      <el-form :model="processForm" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="序号">
              <el-input-number v-model="processForm.sortNo" :min="0" controls-position="right" style="width: 100%;" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="工序" required>
              <el-input v-model="processForm.processName" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="类型">
              <el-tag type="info" size="large">工时</el-tag>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col v-for="field in processPriceFields" :key="field.prop" :span="6">
            <el-form-item :label="field.label">
              <el-input-number v-model="processForm[field.prop]" :precision="2" :min="0" controls-position="right" style="width: 100%;" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="processDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveProcess">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="markupFeeDialogVisible" :title="markupFeeForm.id ? '编辑费用类' : '新增费用类'" width="520px">
      <el-form :model="markupFeeForm" label-width="100px">
        <el-form-item label="序号">
          <el-input-number v-model="markupFeeForm.sortNo" :min="0" controls-position="right" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="费用名称" required>
          <el-input v-model="markupFeeForm.feeName" placeholder="例如：运输费、管理费、利润、税费" />
        </el-form-item>
        <el-form-item label="费率(%)" required>
          <el-input-number v-model="markupFeeForm.ratePercent" :precision="2" :min="0" controls-position="right" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="markupFeeForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="markupFeeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveMarkupFee">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="extraFeeDialogVisible" :title="extraFeeForm.id ? '编辑非工时费用工艺' : '新增非工时费用工艺'" width="560px">
      <el-form :model="extraFeeForm" label-width="120px">
        <el-form-item label="序号">
          <el-input-number v-model="extraFeeForm.sortNo" :min="0" controls-position="right" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="工艺名称" required>
          <el-input v-model="extraFeeForm.feeName" placeholder="例如：EDM、热处理、表面处理、QA" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="extraFeeForm.remarks" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="extraFeeForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="extraFeeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveExtraFee">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { API_BASE_URL } from '../config'

const activeTab = ref('materials')
const materials = ref([])
const processes = ref([])
const markupFees = ref([])
const extraFeeItems = ref([])
const loading = ref(false)
const saving = ref(false)

const materialDialogVisible = ref(false)
const processDialogVisible = ref(false)
const markupFeeDialogVisible = ref(false)
const extraFeeDialogVisible = ref(false)

const processPriceFields = [
  { prop: 'price1To5', label: '1-5' },
  { prop: 'price5To10', label: '5-10' },
  { prop: 'price10To50', label: '10-50' },
  { prop: 'price50To100', label: '50-100' },
  { prop: 'price100To500', label: '100-500' },
  { prop: 'price500To1000', label: '500-1000' },
  { prop: 'price1000To3000', label: '1000-3000' },
  { prop: 'price3000Plus', label: '>3000' }
]

const materialForm = reactive(createEmptyMaterial())
const processForm = reactive(createEmptyProcess())
const markupFeeForm = reactive(createEmptyMarkupFee())
const extraFeeForm = reactive(createEmptyExtraFee())

const formatNumber = (value, precision) => {
  const numericValue = Number(value)
  return Number.isFinite(numericValue) ? numericValue.toFixed(precision) : '-'
}
const formatDensity = (value) => {
  const text = formatNumber(value, 4)
  return text === '-' ? '-' : `${text} kg/m³`
}
const formatPrice = (value) => {
  const text = formatNumber(value, 2)
  return text === '-' ? '-' : `${text} 元/kg`
}

function createEmptyMaterial() {
  return {
    id: null,
    materialName: '',
    materialType: '',
    remarks: '',
    plateDensity: 0,
    platePrice: 0,
    barDensity: 0,
    barPrice: 0,
    tubeDensity: 0,
    tubePrice: 0
  }
}

function createEmptyProcess() {
  return {
    id: null,
    sortNo: 0,
    processName: '',
    calcType: '工时',
    price1To5: 0,
    price5To10: 0,
    price10To50: 0,
    price50To100: 0,
    price100To500: 0,
    price500To1000: 0,
    price1000To3000: 0,
    price3000Plus: 0
  }
}

function createEmptyMarkupFee() {
  return {
    id: null,
    sortNo: 0,
    feeName: '',
    ratePercent: 0,
    enabled: true
  }
}

function createEmptyExtraFee() {
  return {
    id: null,
    sortNo: 0,
    feeName: '',
    enabled: true,
    remarks: ''
  }
}

const loadAll = async () => {
  loading.value = true
  try {
    const [materialResponse, processResponse, markupFeeResponse, extraFeeResponse] = await Promise.all([
      axios.get(`${API_BASE_URL}/api/dict/materials`),
      axios.get(`${API_BASE_URL}/api/dict/processes`),
      axios.get(`${API_BASE_URL}/api/dict/markup-fees`),
      axios.get(`${API_BASE_URL}/api/dict/extra-fee-items/all`)
    ])
    materials.value = materialResponse.data || []
    processes.value = processResponse.data || []
    markupFees.value = markupFeeResponse.data || []
    extraFeeItems.value = extraFeeResponse.data || []
  } catch (error) {
    console.error(error)
    ElMessage.error('材料或工艺数据加载失败，请检查后端服务。')
  } finally {
    loading.value = false
  }
}

const openMaterialDialog = (row) => {
  Object.assign(materialForm, createEmptyMaterial(), row || {})
  materialDialogVisible.value = true
}

const openProcessDialog = (row) => {
  Object.assign(processForm, createEmptyProcess(), row || {})
  processForm.calcType = '工时'
  processDialogVisible.value = true
}

const openMarkupFeeDialog = (row) => {
  Object.assign(markupFeeForm, createEmptyMarkupFee(), row || {})
  markupFeeDialogVisible.value = true
}

const openExtraFeeDialog = (row) => {
  Object.assign(extraFeeForm, createEmptyExtraFee(), row || {})
  extraFeeDialogVisible.value = true
}

const saveMaterial = async () => {
  if (!materialForm.materialName) return ElMessage.warning('请填写材料名称。')
  saving.value = true
  try {
    const payload = { ...materialForm, density: materialForm.plateDensity || materialForm.barDensity || materialForm.tubeDensity || 0 }
    const request = materialForm.id
      ? axios.put(`${API_BASE_URL}/api/dict/materials/${materialForm.id}`, payload)
      : axios.post(`${API_BASE_URL}/api/dict/materials`, payload)
    const response = await request
    ElMessage.success(response.data.message || '保存成功')
    materialDialogVisible.value = false
    await loadAll()
  } catch (error) {
    console.error(error)
    ElMessage.error('材料保存失败。')
  } finally {
    saving.value = false
  }
}

const saveProcess = async () => {
  if (!processForm.processName) return ElMessage.warning('请填写工序名称。')
  processForm.calcType = '工时'
  saving.value = true
  try {
    const request = processForm.id
      ? axios.put(`${API_BASE_URL}/api/dict/processes/${processForm.id}`, processForm)
      : axios.post(`${API_BASE_URL}/api/dict/processes`, processForm)
    const response = await request
    ElMessage.success(response.data.message || '保存成功')
    processDialogVisible.value = false
    await loadAll()
  } catch (error) {
    console.error(error)
    ElMessage.error('工艺保存失败。')
  } finally {
    saving.value = false
  }
}

const saveMarkupFee = async () => {
  if (!markupFeeForm.feeName) return ElMessage.warning('请填写费用名称。')
  saving.value = true
  try {
    const request = markupFeeForm.id
      ? axios.put(`${API_BASE_URL}/api/dict/markup-fees/${markupFeeForm.id}`, markupFeeForm)
      : axios.post(`${API_BASE_URL}/api/dict/markup-fees`, markupFeeForm)
    const response = await request
    ElMessage.success(response.data.message || '保存成功')
    markupFeeDialogVisible.value = false
    await loadAll()
  } catch (error) {
    console.error(error)
    ElMessage.error('费用类保存失败。')
  } finally {
    saving.value = false
  }
}

const saveExtraFee = async () => {
  if (!extraFeeForm.feeName) return ElMessage.warning('请填写工艺名称。')
  saving.value = true
  try {
    const request = extraFeeForm.id
      ? axios.put(`${API_BASE_URL}/api/dict/extra-fee-items/${extraFeeForm.id}`, extraFeeForm)
      : axios.post(`${API_BASE_URL}/api/dict/extra-fee-items`, extraFeeForm)
    const response = await request
    ElMessage.success(response.data.message || '保存成功')
    extraFeeDialogVisible.value = false
    await loadAll()
  } catch (error) {
    console.error(error)
    ElMessage.error('非工时费用工艺保存失败。')
  } finally {
    saving.value = false
  }
}

const deleteMaterial = async (row) => {
  await ElMessageBox.confirm(`确认删除材料「${row.materialName}」？`, '删除确认', { type: 'warning' })
  await axios.delete(`${API_BASE_URL}/api/dict/materials/${row.id}`)
  ElMessage.success('材料已删除')
  await loadAll()
}

const deleteProcess = async (row) => {
  await ElMessageBox.confirm(`确认删除工艺「${row.processName}」？`, '删除确认', { type: 'warning' })
  await axios.delete(`${API_BASE_URL}/api/dict/processes/${row.id}`)
  ElMessage.success('工艺已删除')
  await loadAll()
}

const deleteMarkupFee = async (row) => {
  await ElMessageBox.confirm(`确认删除费用类「${row.feeName}」？`, '删除确认', { type: 'warning' })
  await axios.delete(`${API_BASE_URL}/api/dict/markup-fees/${row.id}`)
  ElMessage.success('费用类已删除')
  await loadAll()
}

const deleteExtraFee = async (row) => {
  await ElMessageBox.confirm(`确认删除非工时费用工艺「${row.feeName}」？`, '删除确认', { type: 'warning' })
  await axios.delete(`${API_BASE_URL}/api/dict/extra-fee-items/${row.id}`)
  ElMessage.success('非工时费用工艺已删除')
  await loadAll()
}

onMounted(loadAll)
</script>

<style scoped>
.manage-container { padding: 20px; }
.manage-card { border-radius: 8px; }
.card-header { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; }
.card-header h2 { margin: 0; color: #303133; font-size: 22px; }
.card-header p { margin: 8px 0 0; color: #606266; font-size: 14px; }
.header-actions { display: flex; align-items: center; gap: 10px; }
.unit-input { display: flex; align-items: center; width: 100%; gap: 8px; }
.unit-input-control { flex: 1; min-width: 0; }
.unit-label {
  flex: 0 0 52px;
  color: #606266;
  font-size: 13px;
  text-align: left;
  white-space: nowrap;
}
</style>
