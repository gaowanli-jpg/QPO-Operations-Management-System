<template>
  <div class="setup-container">
    <div class="test-header">
      <div class="selected-order">
        <span class="selected-order-label">当前订单</span>
        <span class="selected-order-value">{{ orderInfo.rfqNo ? `${orderInfo.rfqNo} / ${orderInfo.customerName || '-'}` : '未选择' }}</span>
      </div>
      <el-button type="primary" @click="openOrderPicker" :loading="isLoading">
        选择订单
      </el-button>
      <el-button @click="fetchOrderData" :disabled="!testRfqId" :loading="isLoading">
        刷新当前订单
      </el-button>
    </div>

    <el-row :gutter="16" class="setup-grid">
      <el-col :span="12">
        <div class="overview-section">
          <h3 class="panel-heading">
            订单概览 <span v-if="orderInfo.rfqNo" class="order-no">({{ orderInfo.rfqNo }})</span>
          </h3>
          <p class="panel-desc">
            系统已自动同步市场部传入的 Excel 明细与图纸，请工程师在此核对并预览。
          </p>

          <el-alert
            v-if="repeatAnalysis"
            :title="`重复订单识别：${repeatAnalysis.repeatCount || 0} 个重复零件，${repeatAnalysis.newCount || 0} 个新零件`"
            :type="repeatAnalysis.repeatCount > 0 ? 'success' : 'info'"
            :closable="false"
            show-icon
            class="repeat-alert"
          />

          <el-table :data="itemList" border stripe height="450" v-loading="isLoading">
            <el-table-column prop="partNo" label="图号" width="140" />
            <el-table-column prop="version" label="版本" width="80" align="center" />
            <el-table-column prop="description" label="物料描述" min-width="120" show-overflow-tooltip />
            <el-table-column prop="qty" label="数量" width="70" align="center" />
            <el-table-column label="重复" width="90" align="center">
              <template #default="scope">
                <el-tag v-if="repeatItemMap.has(itemIdentityKey(scope.row))" type="success" size="small">重复</el-tag>
                <el-tag v-else type="info" size="small">新件</el-tag>
              </template>
            </el-table-column>
            
            <el-table-column label="图纸" width="155" align="center">
              <template #default="scope">
                <div v-if="scope.row.drawingUrl" class="drawing-actions">
                  <el-button
                    type="success"
                    size="small"
                    plain
                    @click="openPreview(scope.row.drawingUrl, scope.row.partNo)"
                  >
                    预览
                  </el-button>
                  <el-button
                    type="primary"
                    size="small"
                    plain
                    @click="openDrawingInNewWindow(scope.row.drawingUrl)"
                  >
                    新窗口
                  </el-button>
                </div>
                <el-tag v-else type="danger" size="small">无图纸</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>

      <el-col :span="12">
        <div class="config-section">
          <h2 class="panel-heading">订单全局默认设定</h2>
          <p class="desc">工程师请先总览左侧图纸，设定适用于本批次大部分零件的【基础材质】与【工序路线】。</p>

          <el-alert
            v-if="repeatAnalysis?.repeatCount > 0"
            title="已根据同客户已提单历史重复件推荐默认材料与工艺路线，可继续手动调整后覆盖到本单其他零件。"
            type="success"
            :closable="false"
            show-icon
            class="repeat-alert"
          />

          <el-form label-width="100px" class="setup-form">
            <div class="section-title">默认材料设定</div>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="默认材质">
                  <el-button class="picker-trigger full-width" @click="materialPickerVisible = true">
                    <span class="picker-trigger-text">{{ selectedMaterialLabel }}</span>
                    <span class="picker-trigger-action">选择</span>
                  </el-button>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="默认形态">
                  <el-select v-model="config.shape" class="full-width">
                    <el-option label="板材/方块" value="板材" />
                    <el-option label="圆棒材" value="棒材" />
                    <el-option label="管材" value="管材" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>

            <div class="section-title process-title">通用工艺路线排程</div>
            <div class="process-box">
              <el-row :gutter="10" v-for="(proc, index) in config.processRoute" :key="index" class="process-row">
                <el-col :span="4"><el-tag type="info">第{{ index + 1 }}道</el-tag></el-col>
                <el-col :span="14">
                  <el-button class="picker-trigger full-width" @click="openProcessPicker(index)">
                    <span :class="proc.processName ? 'picker-trigger-text' : 'picker-trigger-placeholder'">
                      {{ formatProcessLabel(proc, '选择全局默认工序') }}
                    </span>
                    <span class="picker-trigger-action">选择</span>
                  </el-button>
                </el-col>
                <el-col :span="6">
                  <el-button type="danger" plain size="small" @click="removeProcess(index)">删除</el-button>
                </el-col>
              </el-row>
              <el-button type="primary" plain class="add-process-btn" @click="addProcess">
                + 添加通用工序
              </el-button>
            </div>

            <div class="form-actions">
              <el-button type="primary" size="large" @click="handleNext" class="submit-btn">
                保存设定，进入逐件核价
              </el-button>
            </div>
          </el-form>
        </div>
      </el-col>
    </el-row>

    <el-dialog
      v-model="previewVisible"
      :title="`图纸预览：${previewTitle}`"
      width="75%"
      top="5vh"
      destroy-on-close
    >
      <div class="preview-toolbar">
        <el-button type="primary" plain @click="openDrawingInNewWindow(currentPreviewUrl)">
          新窗口打开图纸
        </el-button>
      </div>
      <div v-loading="!iframeLoaded" class="pdf-frame-shell">
        <iframe 
          :src="previewSrc" 
          width="100%" 
          height="100%" 
          frameborder="0"
          @load="iframeLoaded = true"
        ></iframe>
      </div>
    </el-dialog>

    <OrderPickerDialog
      v-model="orderPickerVisible"
      title="选择工程核价订单"
      @select="handleOrderSelected"
    />

    <EngineeringOptionPickerDialog
      v-model="materialPickerVisible"
      title="选择默认材质"
      type="material"
      :items="materialOptions"
      :selected-key="config.materialName"
      empty-text="暂无材料字典数据"
      @confirm="selectMaterial"
    />

    <EngineeringOptionPickerDialog
      v-model="processPickerVisible"
      title="选择通用工序"
      type="process"
      :items="combinedProcessOptions"
      :selected-key="selectedProcessOptionKey"
      empty-text="暂无工艺字典数据"
      @confirm="selectProcess"
    />

  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import EngineeringOptionPickerDialog from './EngineeringOptionPickerDialog.vue'
import OrderPickerDialog from './OrderPickerDialog.vue'
import { API_BASE_URL } from '../config'
import {
  createAuthenticatedObjectUrl,
  openAuthenticatedFileInNewWindow,
  readFileErrorMessage,
  revokeObjectUrl
} from '../authenticatedFile'

const router = useRouter()

const testRfqId = ref(null)

// 数据存放区
const isLoading = ref(false)
const orderPickerVisible = ref(false)
const materialPickerVisible = ref(false)
const processPickerVisible = ref(false)
const editingProcessIndex = ref(null)
const orderInfo = ref({})
const itemList = ref([])
const materialOptions = ref([])
const processOptions = ref([])
const extraFeeOptions = ref([])
const repeatAnalysis = ref(null)

const normalizePartNo = (value) => String(value || '').trim().toUpperCase()
const normalizeVersion = (value) => {
  const text = String(value || '').trim().toUpperCase()
  return text
}
const itemIdentityKey = (item) => `${normalizePartNo(item?.partNo)}||${normalizeVersion(item?.version)}`
const PROCESS_SORT_STEP = 10
const numericSortNo = (value) => {
  const numericValue = Number(value)
  return Number.isFinite(numericValue) ? numericValue : null
}
const sortRouteList = (routes) => {
  const withIndex = (routes || []).map((route, index) => ({ route, index }))
  const hasSortNo = withIndex.some(item => numericSortNo(item.route?.sortNo) != null)
  if (!hasSortNo) return routes || []
  return withIndex
    .sort((a, b) => {
      const aSort = numericSortNo(a.route?.sortNo)
      const bSort = numericSortNo(b.route?.sortNo)
      if (aSort != null && bSort != null && aSort !== bSort) return aSort - bSort
      if (aSort != null && bSort == null) return -1
      if (aSort == null && bSort != null) return 1
      return a.index - b.index
    })
    .map(item => item.route)
}
const assignProcessSortNos = () => {
  config.processRoute.forEach((proc, index) => {
    proc.sortNo = (index + 1) * PROCESS_SORT_STEP
  })
}

const repeatItemMap = computed(() => {
  const map = new Map()
  ;(repeatAnalysis.value?.repeatItems || []).forEach(item => {
    map.set(itemIdentityKey(item), item)
  })
  return map
})

// 全局配置的状态字典
const config = reactive({
  materialName: '45#',
  shape: '板材',
  processRoute: [
    { sortNo: 10, processName: '铣', calcType: '工时', sourceType: 'process' },
    { sortNo: 20, processName: 'CNC1', calcType: '工时', sourceType: 'process' }
  ]
})

const assignConfig = (source) => {
  if (!source) return
  config.materialName = source.materialName || config.materialName || '45#'
  config.shape = source.shape || config.shape || '板材'
  config.processRoute.splice(
    0,
    config.processRoute.length,
    ...sortRouteList(source.processRoute || []).map((item, index) => ({
      sortNo: numericSortNo(item.sortNo) ?? ((index + 1) * PROCESS_SORT_STEP),
      optionKey: item.optionKey || '',
      processName: item.processName || '',
      calcType: item.calcType || '工时',
      sourceType: item.sourceType || (item.calcType === '非工时费用' ? 'extra' : 'process')
    }))
  )
  assignProcessSortNos()
  hydrateProcessRoute()
}

const combinedProcessOptions = computed(() => [
  ...processOptions.value.map(item => ({
    optionKey: `process-${item.id}`,
    processName: item.processName,
    label: `${item.processName}（${item.calcType || '工时'}）`,
    calcType: item.calcType || '工时',
    sourceType: 'process'
  })),
  ...extraFeeOptions.value.map(item => ({
    optionKey: `extra-${item.id}`,
    processName: item.feeName,
    label: `${item.feeName}（非工时费用）`,
    calcType: '非工时费用',
    sourceType: 'extra'
  }))
])

const formatMaterialLabel = (material) => {
  if (!material) return ''
  return material.remarks ? `${material.materialName} ${material.remarks}` : material.materialName
}

const selectedMaterialLabel = computed(() => {
  const material = materialOptions.value.find(item => item.materialName === config.materialName)
  return formatMaterialLabel(material) || config.materialName || '请选择默认材质'
})

const findProcessOption = (proc) => {
  if (!proc) return null
  return combinedProcessOptions.value.find(item => item.optionKey === proc.optionKey)
    || combinedProcessOptions.value.find(item => item.processName === proc.processName && item.sourceType === (proc.sourceType || 'process'))
    || combinedProcessOptions.value.find(item => item.processName === proc.processName)
    || null
}

const selectedProcessOptionKey = computed(() => {
  const proc = config.processRoute[editingProcessIndex.value]
  return findProcessOption(proc)?.optionKey || proc?.optionKey || ''
})

const formatProcessLabel = (proc, fallback = '请选择工序') => {
  const option = findProcessOption(proc)
  if (option?.label) return option.label
  if (proc?.processName) return `${proc.processName}（${proc.calcType || '工时'}）`
  return fallback
}

const applyProcessSelection = (proc, selected) => {
  if (!proc || !selected) return
  proc.optionKey = selected.optionKey
  proc.processName = selected.processName
  proc.calcType = selected.calcType
  proc.sourceType = selected.sourceType
}

const selectMaterial = (material) => {
  if (!material?.materialName) return
  config.materialName = material.materialName
}

const openProcessPicker = (index) => {
  editingProcessIndex.value = index
  processPickerVisible.value = true
}

const selectProcess = (selected) => {
  const proc = config.processRoute[editingProcessIndex.value]
  applyProcessSelection(proc, selected)
  editingProcessIndex.value = null
}

const addProcess = () => {
  config.processRoute.push({ processName: '', calcType: '工时', sourceType: 'process', optionKey: '' })
  assignProcessSortNos()
}
const removeProcess = (index) => {
  config.processRoute.splice(index, 1)
  assignProcessSortNos()
}

const syncProcessMeta = (proc) => {
  const selected = combinedProcessOptions.value.find(item => item.optionKey === proc.optionKey)
  if (!selected) return
  applyProcessSelection(proc, selected)
}

const hydrateProcessRoute = () => {
  config.processRoute.forEach(proc => {
    if (proc.optionKey) {
      syncProcessMeta(proc)
      return
    }
    const selected = combinedProcessOptions.value.find(item => item.processName === proc.processName && item.sourceType === (proc.sourceType || 'process'))
      || combinedProcessOptions.value.find(item => item.processName === proc.processName)
    if (selected) {
      proc.optionKey = selected.optionKey
      syncProcessMeta(proc)
    }
  })
}

const fetchDicts = async () => {
  try {
    const [materialResponse, processResponse, extraFeeResponse] = await Promise.all([
      axios.get(`${API_BASE_URL}/api/dict/materials`),
      axios.get(`${API_BASE_URL}/api/dict/processes`),
      axios.get(`${API_BASE_URL}/api/dict/extra-fee-items`)
    ])
    materialOptions.value = materialResponse.data || []
    processOptions.value = processResponse.data || []
    extraFeeOptions.value = extraFeeResponse.data || []
    if (!config.materialName && materialOptions.value.length > 0) {
      config.materialName = materialOptions.value[0].materialName
    }
    hydrateProcessRoute()
    const validOptionKeys = new Set(combinedProcessOptions.value.map(item => item.optionKey))
    if (config.processRoute.length === 0 || config.processRoute.some(item => !validOptionKeys.has(item.optionKey))) {
      config.processRoute.splice(
        0,
        config.processRoute.length,
        ...combinedProcessOptions.value.slice(0, 2).map((item, index) => ({
          sortNo: (index + 1) * PROCESS_SORT_STEP,
          optionKey: item.optionKey,
          processName: item.processName,
          calcType: item.calcType,
          sourceType: item.sourceType
        }))
      )
    }
  } catch (error) {
    console.error(error)
    ElMessage.warning('基础字典加载失败，工程部页面仍可手动填写。')
  }
}

const handleNext = async () => {
  if (!orderInfo.value.id) {
    return ElMessage.warning('请先选择订单并加载数据。')
  }

  try {
    assignProcessSortNos()
    await axios.post(`${API_BASE_URL}/api/engineer/order-config/${orderInfo.value.id}`, JSON.parse(JSON.stringify(config)))
  } catch (error) {
    console.error(error)
    return ElMessage.error('订单全局默认设定保存失败，请检查后端服务。')
  }

  if (repeatAnalysis.value?.repeatCount > 0) {
    try {
      const response = await axios.post(`${API_BASE_URL}/api/engineer/apply-repeat-quotes/${orderInfo.value.id}`)
      if (response.data.appliedCount > 0) {
        ElMessage.success(`已自动套用 ${response.data.appliedCount} 个重复零件的历史核价，请进入逐件核价页审核后手动完成报价提交。`)
      }
    } catch (error) {
      console.error(error)
      ElMessage.warning('重复件自动核价失败，可进入逐件核价页面手动处理。')
    }
  }

  localStorage.setItem(`erp_global_config_${orderInfo.value.id}`, JSON.stringify(config))
  localStorage.setItem('erp_global_config', JSON.stringify(config))

  ElMessage.success('基础设定已保存，正在为您跳转至逐件核价中心...')

  // 使用路由跳转到 /pricing 页面，并在网址栏里带上单号 ID，如：/pricing?rfqId=2
  router.push({
    path: '/pricing',
    query: { rfqId: orderInfo.value.id }
  })
}

const openOrderPicker = () => {
  orderPickerVisible.value = true
}

const handleOrderSelected = async (order) => {
  testRfqId.value = order.id
  await fetchOrderData()
}

// 核心逻辑 1：从数据库拉取订单数据
const fetchOrderData = async () => {
  if (!testRfqId.value) return ElMessage.warning('请输入订单 ID')
  
  isLoading.value = true
  try {
    const response = await axios.get(`${API_BASE_URL}/api/market/summary/${testRfqId.value}`)
    if (response.data && response.data.orderInfo) {
      orderInfo.value = response.data.orderInfo
      itemList.value = response.data.itemList
      if (orderInfo.value.engineerConfig) {
        assignConfig(orderInfo.value.engineerConfig)
      }
      await loadRepeatAnalysis(orderInfo.value.id, !!orderInfo.value.engineerConfig)
      ElMessage.success(`成功加载 ${itemList.value.length} 条零件数据！`)
    } else {
      ElMessage.warning('未找到该订单，请检查 ID 是否正确。')
    }
  } catch (error) {
    console.error(error)
    ElMessage.error('网络请求失败，请确保后端服务正常。')
  } finally {
    isLoading.value = false
  }
}

// 核心逻辑 2：打开 PDF 预览弹窗
const previewVisible = ref(false)
const currentPreviewUrl = ref('')
const previewTitle = ref('')
const iframeLoaded = ref(false)
const previewSrc = ref('')

const cleanupPreviewSrc = () => {
  revokeObjectUrl(previewSrc.value)
  previewSrc.value = ''
}

const openPreview = async (url, partNo) => {
  if (!url) {
    return ElMessage.warning('当前零件没有绑定图纸。')
  }
  previewTitle.value = partNo
  currentPreviewUrl.value = url
  iframeLoaded.value = false
  cleanupPreviewSrc()
  previewVisible.value = true
  try {
    previewSrc.value = await createAuthenticatedObjectUrl(url)
  } catch (error) {
    previewVisible.value = false
    ElMessage.error(await readFileErrorMessage(error) || '图纸预览失败。')
  }
}

const openDrawingInNewWindow = async (url) => {
  if (!url) {
    return ElMessage.warning('当前零件没有绑定图纸。')
  }
  const result = await openAuthenticatedFileInNewWindow(url)
  if (!result.success) {
    ElMessage.warning(result.blocked ? '浏览器拦截了新窗口，请允许弹窗后重试。' : (result.message || '图纸打开失败。'))
  }
}

watch(previewVisible, (visible) => {
  if (!visible) {
    cleanupPreviewSrc()
    iframeLoaded.value = false
  }
})

watch(processPickerVisible, (visible) => {
  if (!visible) {
    editingProcessIndex.value = null
  }
})

onBeforeUnmount(cleanupPreviewSrc)

const loadRepeatAnalysis = async (rfqId, skipRecommendedConfig = false) => {
  repeatAnalysis.value = null
  if (!rfqId) return
  try {
    const response = await axios.get(`${API_BASE_URL}/api/engineer/repeat-analysis/${rfqId}`)
    repeatAnalysis.value = response.data
    if (!skipRecommendedConfig) {
      applyRecommendedConfig(response.data?.recommendedConfig)
    }
  } catch (error) {
    console.error(error)
    ElMessage.warning('重复订单分析失败，不影响手动设定。')
  }
}

const applyRecommendedConfig = (recommendedConfig) => {
  if (!recommendedConfig) return
  if (recommendedConfig.materialName) {
    config.materialName = recommendedConfig.materialName
  }
  if (recommendedConfig.shape) {
    config.shape = recommendedConfig.shape
  }
  if (recommendedConfig.processRoute?.length > 0) {
    config.processRoute.splice(
      0,
      config.processRoute.length,
      ...sortRouteList(recommendedConfig.processRoute).map((item, index) => ({
        sortNo: numericSortNo(item.sortNo) ?? ((index + 1) * PROCESS_SORT_STEP),
        optionKey: '',
        processName: item.processName || '',
        calcType: item.calcType || '工时',
        sourceType: item.sourceType || (item.calcType === '非工时费用' ? 'extra' : 'process')
      }))
    )
    assignProcessSortNos()
    hydrateProcessRoute()
  }
}

onMounted(async () => {
  await fetchDicts()
})
</script>

<style scoped>
.setup-container {
  padding: 0;
}

.test-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
  padding: 14px;
  border: 1px solid var(--erp-border-soft);
  border-radius: 8px;
  background: var(--erp-surface);
}

.selected-order {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  color: var(--erp-text);
}

.selected-order-label {
  flex: none;
  padding-left: 10px;
  border-left: 3px solid var(--erp-primary);
  color: var(--erp-text);
  font-weight: 700;
}

.selected-order-value {
  overflow: hidden;
  color: var(--erp-text-secondary);
  white-space: nowrap;
  text-overflow: ellipsis;
}

.overview-section,
.config-section {
  min-height: 560px;
  padding: 16px;
  border: 1px solid var(--erp-border-soft);
  border-radius: 8px;
  background: var(--erp-surface);
}

.panel-heading {
  margin: 0;
  color: var(--erp-text);
  font-size: 18px;
  line-height: 1.3;
}

.order-no {
  color: var(--erp-primary);
  font-size: 13px;
  font-weight: 600;
}

.panel-desc,
.desc {
  color: var(--erp-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.panel-desc {
  margin: 6px 0 12px;
}

.desc {
  margin: 6px 0 14px;
}

.repeat-alert {
  margin-bottom: 12px;
}

.setup-form {
  padding-top: 2px;
}

.section-title {
  margin: 18px 0 10px;
  padding-left: 10px;
  border-left: 3px solid var(--erp-primary);
  color: var(--erp-text);
  font-size: 14px;
  font-weight: 700;
  line-height: 1.2;
}

.process-title {
  margin-top: 16px;
}

.process-box {
  padding: 14px;
  border: 1px dashed var(--erp-border);
  border-radius: 8px;
  background: var(--erp-surface-soft);
}

.process-row {
  margin-bottom: 10px;
  align-items: center;
}

.add-process-btn,
.submit-btn,
.full-width {
  width: 100%;
}

.picker-trigger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 40px;
  padding: 0 12px;
  border-color: var(--erp-border);
  background: var(--erp-surface);
  color: var(--erp-text);
}

.picker-trigger:hover,
.picker-trigger:focus {
  border-color: var(--erp-primary);
  color: var(--erp-primary);
}

.picker-trigger-text,
.picker-trigger-placeholder {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.picker-trigger-placeholder {
  color: var(--erp-text-secondary);
}

.picker-trigger-action {
  flex: none;
  color: var(--erp-primary);
  font-size: 12px;
  font-weight: 700;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid var(--erp-border-soft);
}

.drawing-actions {
  display: flex;
  justify-content: center;
  gap: 6px;
}

.drawing-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.preview-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 10px;
}

.pdf-frame-shell {
  width: 100%;
  height: 70vh;
}

@media (max-width: 1180px) {
  .setup-grid :deep(.el-row) {
    display: block;
  }

  .setup-grid :deep(.el-col) {
    max-width: 100%;
    width: 100%;
    margin-bottom: 14px;
  }

  .overview-section,
  .config-section {
    min-height: auto;
  }
}

@media (max-width: 760px) {
  .test-header,
  .selected-order {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
