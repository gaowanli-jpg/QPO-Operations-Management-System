<template>
  <div class="setup-container">
    <!-- 🌟 顶部的“拉取数据”小工具 -->
    <div class="test-header">
      <div class="selected-order">
        <span class="selected-order-label">当前订单</span>
        <span>{{ orderInfo.rfqNo ? `${orderInfo.rfqNo} / ${orderInfo.customerName || '-'} / ${orderInfo.projectName || '-'}` : '未选择' }}</span>
      </div>
      <el-button type="primary" @click="openOrderPicker" :loading="isLoading">
        选择订单
      </el-button>
      <el-button @click="fetchOrderData" :disabled="!testRfqId" :loading="isLoading">
        刷新当前订单
      </el-button>
    </div>

    <el-row :gutter="40">
      <!-- 🌟 左侧：订单图纸与零件总览区 🌟 -->
      <el-col :span="12">
        <div class="overview-section">
          <h3 style="margin-top: 0; color: #303133;">
            📋 订单概览 <span v-if="orderInfo.rfqNo" style="color:#409EFF; font-size: 14px;">({{ orderInfo.rfqNo }})</span>
          </h3>
          <p style="color: #909399; font-size: 13px; margin-bottom: 15px;">
            系统已自动同步市场部传入的 Excel 明细与图纸，请工程师在此核对并预览。
          </p>

          <el-alert
            v-if="repeatAnalysis"
            :title="`重复订单识别：${repeatAnalysis.repeatCount || 0} 个重复零件，${repeatAnalysis.newCount || 0} 个新零件`"
            :type="repeatAnalysis.repeatCount > 0 ? 'success' : 'info'"
            :closable="false"
            show-icon
            style="margin-bottom: 12px;"
          />

          <el-table :data="itemList" border stripe height="450" v-loading="isLoading">
            <el-table-column prop="partNo" label="物品编号(图号)" width="140" />
            <el-table-column prop="description" label="物料描述" min-width="120" show-overflow-tooltip />
            <el-table-column prop="qty" label="数量" width="70" align="center" />
            <el-table-column label="重复" width="90" align="center">
              <template #default="scope">
                <el-tag v-if="repeatItemMap.has(normalizePartNo(scope.row.partNo))" type="success" size="small">重复</el-tag>
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

      <!-- 🌟 右侧：全局配置表单 🌟 -->
      <el-col :span="12">
        <div class="config-section">
          <h2 style="margin-top: 0;">⚙️ 订单全局默认设定</h2>
          <p class="desc">工程师请先总览左侧图纸，设定适用于本批次大部分零件的【基础材质】与【工序路线】。</p>

          <el-alert
            v-if="repeatAnalysis?.repeatCount > 0"
            title="已根据同客户已提单历史重复件推荐默认材料与工艺路线，可继续手动调整后覆盖到本单其他零件。"
            type="success"
            :closable="false"
            show-icon
            style="margin-bottom: 16px;"
          />

          <el-form label-width="100px" class="setup-form">
            <div class="section-title">📦 默认材料设定</div>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="默认材质">
                  <el-select v-model="config.materialName" style="width: 100%;">
                    <el-option
                      v-for="material in materialOptions"
                      :key="material.id"
                      :label="material.remarks ? `${material.materialName} ${material.remarks}` : material.materialName"
                      :value="material.materialName"
                    />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="默认形态">
                  <el-select v-model="config.shape" style="width: 100%;">
                    <el-option label="板材/方块" value="板材" />
                    <el-option label="圆棒材" value="棒材" />
                    <el-option label="管材" value="管材" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>

            <div class="section-title" style="margin-top: 20px;">🛠️ 通用工艺路线排程</div>
            <div class="process-box">
              <el-row :gutter="10" v-for="(proc, index) in config.processRoute" :key="index" style="margin-bottom: 10px;">
                <el-col :span="4"><el-tag type="info">第{{ index + 1 }}道</el-tag></el-col>
                <el-col :span="14">
                  <el-select v-model="proc.optionKey" placeholder="选择全局默认工序" style="width: 100%;" @change="syncProcessMeta(proc)">
                    <el-option
                      v-for="process in combinedProcessOptions"
                      :key="process.optionKey"
                      :label="process.label"
                      :value="process.optionKey"
                    />
                  </el-select>
                </el-col>
                <el-col :span="6">
                  <el-button type="danger" plain size="small" @click="removeProcess(index)">删除</el-button>
                </el-col>
              </el-row>
              <el-button type="dashed" style="width: 100%; margin-top: 10px;" @click="addProcess">
                + 添加通用工序
              </el-button>
            </div>

            <div style="text-align: right; margin-top: 30px;">
              <!-- 核心按钮！ -->
              <el-button type="primary" size="large" @click="handleNext" style="width: 100%;">
                保存设定，进入逐件核价 ➡️
              </el-button>
            </div>
          </el-form>
        </div>
      </el-col>
    </el-row>

    <!-- 🌟 PDF 预览弹窗 🌟 -->
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
      <div v-loading="!iframeLoaded" style="height: 70vh; width: 100%;">
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
      :orders="orders"
      @select="handleOrderSelected"
    />

  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router' // 🌟 1. 引入路由工具！
import OrderPickerDialog from './OrderPickerDialog.vue'
import { API_BASE_URL, apiUrl } from '../config'

const router = useRouter() // 🌟 2. 实例化路由导航员

const testRfqId = ref(null)

// 数据存放区
const isLoading = ref(false)
const orders = ref([])
const orderPickerVisible = ref(false)
const orderInfo = ref({})
const itemList = ref([])
const materialOptions = ref([])
const processOptions = ref([])
const extraFeeOptions = ref([])
const repeatAnalysis = ref(null)

const normalizePartNo = (value) => String(value || '').trim().toUpperCase()

const repeatItemMap = computed(() => {
  const map = new Map()
  ;(repeatAnalysis.value?.repeatItems || []).forEach(item => {
    map.set(normalizePartNo(item.partNo), item)
  })
  return map
})

// 全局配置的状态字典
const config = reactive({
  materialName: '45#',
  shape: '板材',
  processRoute: [
    { processName: '铣', calcType: '工时', sourceType: 'process' },
    { processName: 'CNC1', calcType: '工时', sourceType: 'process' }
  ]
})

const assignConfig = (source) => {
  if (!source) return
  config.materialName = source.materialName || config.materialName || '45#'
  config.shape = source.shape || config.shape || '板材'
  config.processRoute.splice(
    0,
    config.processRoute.length,
    ...(source.processRoute || []).map(item => ({
      optionKey: item.optionKey || '',
      processName: item.processName || '',
      calcType: item.calcType || '工时',
      sourceType: item.sourceType || (item.calcType === '非工时费用' ? 'extra' : 'process')
    }))
  )
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

const addProcess = () => config.processRoute.push({ processName: '', calcType: '工时', sourceType: 'process', optionKey: '' })
const removeProcess = (index) => config.processRoute.splice(index, 1)

const syncProcessMeta = (proc) => {
  const selected = combinedProcessOptions.value.find(item => item.optionKey === proc.optionKey)
  if (!selected) return
  proc.processName = selected.processName
  proc.calcType = selected.calcType
  proc.sourceType = selected.sourceType
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
        ...combinedProcessOptions.value.slice(0, 2).map(item => ({
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

// 🌟 3. 核心大改造：从 emit 升级为 router.push
const handleNext = async () => {
  if (!orderInfo.value.id) {
    return ElMessage.warning('请先选择订单并加载数据。')
  }

  try {
    await axios.post(`${API_BASE_URL}/api/engineer/order-config/${orderInfo.value.id}`, JSON.parse(JSON.stringify(config)))
  } catch (error) {
    console.error(error)
    return ElMessage.error('订单全局默认设定保存失败，请检查后端服务。')
  }

  if (repeatAnalysis.value?.repeatCount > 0) {
    try {
      const response = await axios.post(`${API_BASE_URL}/api/engineer/apply-repeat-quotes/${orderInfo.value.id}`)
      if (response.data.appliedCount > 0) {
        ElMessage.success(`已自动套用 ${response.data.appliedCount} 个重复零件的历史核价。`)
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

const loadOrders = async () => {
  isLoading.value = true
  try {
    const response = await axios.get(`${API_BASE_URL}/api/market/orders`)
    orders.value = response.data || []
  } catch (error) {
    console.error(error)
    ElMessage.error('订单列表加载失败，请检查后端服务。')
  } finally {
    isLoading.value = false
  }
}

const openOrderPicker = async () => {
  if (orders.value.length === 0) {
    await loadOrders()
  }
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
const previewSrc = computed(() => apiUrl(currentPreviewUrl.value))

const openPreview = (url, partNo) => {
  previewTitle.value = partNo
  currentPreviewUrl.value = url
  iframeLoaded.value = false
  previewVisible.value = true
}

const openDrawingInNewWindow = (url) => {
  if (!url) {
    return ElMessage.warning('当前零件没有绑定图纸。')
  }
  const newWindow = window.open(apiUrl(url), '_blank')
  if (newWindow) {
    newWindow.opener = null
  } else {
    ElMessage.warning('浏览器拦截了新窗口，请允许弹窗后重试。')
  }
}

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
      ...recommendedConfig.processRoute.map(item => ({
        optionKey: '',
        processName: item.processName || '',
        calcType: item.calcType || '工时',
        sourceType: item.sourceType || (item.calcType === '非工时费用' ? 'extra' : 'process')
      }))
    )
    hydrateProcessRoute()
  }
}

onMounted(async () => {
  await fetchDicts()
  await loadOrders()
})
</script>

<style scoped>
.setup-container { background: #fff; padding: 20px; border-radius: 8px; }
.test-header { background: #f0f9eb; padding: 15px; border-radius: 8px; margin-bottom: 20px; border: 1px solid #e1f3d8; display: flex; align-items: center; gap: 10px; }
.selected-order { flex: 1; display: flex; align-items: center; gap: 10px; min-width: 0; color: #303133; }
.selected-order-label { flex: none; color: #67c23a; font-weight: 700; }
.overview-section { background: #f8f9fa; border: 1px solid #ebeef5; padding: 20px; border-radius: 8px; min-height: 550px; }
.config-section { background: #fff; padding: 10px 20px; border-left: 1px dashed #dcdfe6; min-height: 550px; }
.section-title { font-weight: bold; color: #409eff; margin-bottom: 15px; border-bottom: 1px solid #eee; padding-bottom: 8px; }
.desc { color: #606266; margin-bottom: 20px; font-size: 14px; line-height: 1.6; }
.process-box { background: #fafafa; padding: 20px; border-radius: 8px; border: 1px solid #ebeef5; }
.drawing-actions { display: flex; justify-content: center; gap: 6px; }
.drawing-actions :deep(.el-button + .el-button) { margin-left: 0; }
.preview-toolbar { display: flex; justify-content: flex-end; margin-bottom: 10px; }
</style>
