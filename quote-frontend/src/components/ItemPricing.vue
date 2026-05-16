<template>
  <div class="pricing-container">
    <el-card shadow="never" class="main-card">
      <template #header>
        <div class="card-header">
          <h2>💰 工程部：逐件工艺路线与核价中心</h2>
          <div class="header-tags">
            <el-tag type="success" size="large" v-if="rfqId">正在处理询价单 ID: {{ rfqId }}</el-tag>
            <el-tag size="large" v-if="quoteData?.orderInfo?.status">订单状态：{{ quoteData.orderInfo.status }}</el-tag>
          </div>
        </div>
      </template>

      <!-- 头部操作区 -->
      <div style="display: flex; justify-content: space-between; margin-bottom: 20px;">
        <div style="display: flex; gap: 10px;">
          <el-button @click="$router.back()">⬅️ 返回修改全局设定</el-button>
          <el-button type="success" plain :disabled="!canExportQuote" @click="exportQuoteSheet">
            生成报价单
          </el-button>
          <el-button type="primary" :disabled="!canCompleteQuote" :loading="completeLoading" @click="completeOrderQuote">
            完成报价提交
          </el-button>
        </div>
        <el-button type="primary" @click="fetchQuoteData" :loading="loading">一键同步订单数据</el-button>
      </div>

      <div v-if="!quoteData" style="padding: 50px 0; text-align: center;" v-loading="loading">
        <el-empty description="正在拉取市场部数据..." />
      </div>

      <!-- 数据大表格 -->
      <div v-else>
        <el-table
          ref="pricingTableRef"
          :data="quoteData.itemList"
          row-key="id"
          border
          stripe
          style="width: 100%"
          height="450"
        >
          <el-table-column prop="partNo" label="图号/零件号" width="140" fixed />
          <el-table-column prop="description" label="名称/描述" min-width="150" show-overflow-tooltip />
          <el-table-column prop="qty" label="数量" width="70" align="center" />
          <el-table-column label="重复" width="90" align="center">
            <template #default="scope">
              <el-tag v-if="repeatItemMap.has(normalizePartNo(scope.row.partNo))" type="success" size="small">重复</el-tag>
              <el-tag v-else type="info" size="small">新件</el-tag>
            </template>
          </el-table-column>
          
          <!-- 🌟 新增：表格处的图纸查看按钮 🌟 -->
          <el-table-column label="图纸" width="90" align="center">
            <template #default="scope">
              <el-button 
                v-if="scope.row.drawingUrl" 
                type="success" size="small" plain 
                @click="openPdfPreview(scope.row.drawingUrl, scope.row.partNo)"
              >🔍 图纸</el-button>
              <el-tag v-else type="info" size="small">无图</el-tag>
            </template>
          </el-table-column>

          <el-table-column label="核算总成本" width="110" align="right">
            <template #default="scope">
              <span v-if="scope.row.baseCost">¥ {{ scope.row.baseCost.toFixed(2) }}</span><span v-else style="color:#909399">待核算</span>
            </template>
          </el-table-column>
          <el-table-column label="含税单价" width="110" align="right">
            <template #default="scope"><span v-if="scope.row.finalUnitPrice != null" style="color: #67C23A; font-weight: bold;">¥ {{ scope.row.finalUnitPrice.toFixed(2) }}</span></template>
          </el-table-column>
          <el-table-column label="单项合计" width="110" align="right">
            <template #default="scope"><span v-if="scope.row.finalTotalPrice != null" style="color: #F56C6C; font-weight: bold;">¥ {{ scope.row.finalTotalPrice.toFixed(2) }}</span></template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="100" align="center" />
          <el-table-column label="操作" width="120" align="center" fixed="right">
            <template #default="scope">
              <el-button
                v-if="scope.row.drawingUrl"
                type="primary"
                size="small"
                @click="openQuoteDialog(scope.row)"
              >去核价</el-button>
              <el-tag v-else type="warning" size="small">无图不核价</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>

    <!-- ==================== 🛠️ 工程师核价控制台 (弹窗) ==================== -->
    <el-dialog
      v-model="dialogVisible"
      title="⚙️ 工程师精准核价控制台"
      width="95vw"
      top="3vh"
      class="pricing-workbench-dialog"
      :close-on-click-modal="false"
    >
      <div class="pricing-workbench">
        <div class="pricing-form-pane">
          <!-- 弹窗顶部状态条：左边是信息，右边是图纸操作 -->
          <div class="dialog-header-bar">
            <el-alert
              :title="`当前核价：${currentRow?.partNo} - ${currentRow?.description} (需生产: ${currentRow?.qty} 件)`"
              type="success"
              show-icon
              :closable="false"
              class="current-item-alert"
            />
            <div v-if="currentRow?.drawingUrl" class="dialog-drawing-actions">
              <el-button type="warning" plain @click="openPdfPreview(currentRow.drawingUrl, currentRow.partNo)">
                弹窗预览
              </el-button>
              <el-button type="primary" plain @click="openDrawingInNewWindow(currentRow.drawingUrl)">
                新窗口打开图纸
              </el-button>
            </div>
          </div>

          <!-- 模块A：材料 -->
          <div style="font-weight: bold; color: #409EFF; margin-bottom: 10px;">📊 模块 A：智能材料成本核算 (已继承全局设定)</div>
          <el-form label-width="100px" style="background-color: #f5f7fa; padding: 15px; border-radius: 8px;">
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="材质选择">
                  <el-select v-model="matForm.materialName" style="width: 100%;">
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
                <el-form-item label="毛坯形态">
                  <el-radio-group v-model="matForm.shape">
                    <el-radio label="板材">板/块</el-radio><el-radio label="棒材">棒材</el-radio><el-radio label="管材">管材</el-radio>
                  </el-radio-group>
                </el-form-item>
              </el-col>
            </el-row>
            
            <!-- 尺寸填写 -->
            <el-row :gutter="20" style="background: #fff; padding: 10px; border-radius: 4px;">
              <el-col :span="8" v-if="matForm.shape === '板材'"><el-form-item label="长(mm)"><el-input-number v-model="matForm.L" :min="0" controls-position="right" style="width:100%" /></el-form-item></el-col>
              <el-col :span="8" v-if="matForm.shape === '板材'"><el-form-item label="宽(mm)"><el-input-number v-model="matForm.W" :min="0" controls-position="right" style="width:100%" /></el-form-item></el-col>
              <el-col :span="8" v-if="matForm.shape === '板材'"><el-form-item label="厚(mm)"><el-input-number v-model="matForm.T" :min="0" controls-position="right" style="width:100%" /></el-form-item></el-col>
              <el-col :span="8" v-if="matForm.shape !== '板材'"><el-form-item label="长度(mm)"><el-input-number v-model="matForm.L" :min="0" controls-position="right" style="width:100%" /></el-form-item></el-col>
              <el-col :span="8" v-if="matForm.shape !== '板材'"><el-form-item label="外径(mm)"><el-input-number v-model="matForm.outR" :min="0" controls-position="right" style="width:100%" /></el-form-item></el-col>
              <el-col :span="8" v-if="matForm.shape === '管材'"><el-form-item label="内径(mm)"><el-input-number v-model="matForm.inR" :min="0" controls-position="right" style="width:100%" /></el-form-item></el-col>
            </el-row>
            <p style="color:#F56C6C; margin-top:10px; font-weight: bold; text-align: right;">
              毛坯重量：{{ calculatedWeight.toFixed(3) }} KG，预测本件材料费：¥ {{ calculatedMaterialCost.toFixed(2) }}
            </p>
          </el-form>

          <!-- 模块B：工艺 -->
          <div style="font-weight: bold; color: #409EFF; margin-top: 20px; margin-bottom: 10px;">🛠️ 模块 B：工艺路线排程 (已继承全局设定)</div>
          <div style="background-color: #fafafa; padding: 15px; border-radius: 8px; border: 1px dashed #dcdfe6;">
            <el-row :gutter="10" v-for="(proc, index) in processRoute" :key="index" style="margin-bottom: 10px; align-items: center;">
              <el-col :span="8">
                <el-select v-model="proc.optionKey" style="width: 100%;" @change="syncProcessMeta(proc)">
                  <el-option
                    v-for="process in combinedProcessOptions"
                    :key="process.optionKey"
                    :label="process.label"
                    :value="process.optionKey"
                  />
                </el-select>
              </el-col>
              <el-col :span="4">
                <el-tag :type="proc.calcType === '非工时费用' ? 'warning' : 'info'">{{ proc.calcType || '工时' }}</el-tag>
              </el-col>
              <el-col :span="10">
                <el-input-number
                  v-model="proc.inputVal"
                  :min="0"
                  :step="proc.calcType === '非工时费用' ? 10 : 0.5"
                  :placeholder="proc.calcType === '非工时费用' ? '填入费用金额' : '填入工时'"
                  style="width: 100%;"
                />
              </el-col>
            </el-row>
          </div>
        </div>

        <aside class="drawing-pane">
          <div class="drawing-pane-header">
            <div>
              <div class="drawing-pane-title">当前零件图纸</div>
              <div class="drawing-pane-subtitle">{{ currentRow?.partNo || '-' }}</div>
            </div>
            <el-button
              v-if="currentRow?.drawingUrl"
              type="primary"
              plain
              size="small"
              @click="openDrawingInNewWindow(currentRow.drawingUrl)"
            >
              新窗口打开
            </el-button>
          </div>
          <div v-if="currentRow?.drawingUrl" class="drawing-frame">
            <iframe
              :key="currentRow?.id"
              :src="drawingUrl(currentRow.drawingUrl)"
              class="drawing-frame-iframe"
              frameborder="0"
            ></iframe>
          </div>
          <el-empty v-else description="当前零件没有绑定图纸" :image-size="90" class="drawing-empty" />
        </aside>
      </div>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="success" @click="submitCurrentQuote" :loading="submitLoading">确认提交</el-button>
      </template>
    </el-dialog>

    <!-- ==================== 🌟 终极图纸渲染器 (内嵌 iframe) ==================== -->
    <el-dialog
      v-model="pdfVisible"
      :title="`图纸预览：${pdfTitle}`"
      width="75%"
      top="5vh"
      destroy-on-close
    >
      <div v-loading="!pdfLoaded" style="height: 70vh; width: 100%;">
        <iframe 
          :src="currentPdfSrc" 
          width="100%" 
          height="100%" 
          frameborder="0"
          @load="pdfLoaded = true"
        ></iframe>
      </div>
    </el-dialog>

  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useRoute } from 'vue-router'
import { API_BASE_URL, apiUrl } from '../config'

const route = useRoute()
const rfqId = ref('')
const globalConfig = ref({ materialName: '45#', shape: '板材', processRoute: [] }) // 默认保底配置

const quoteData = ref(null)
const loading = ref(false)
const completeLoading = ref(false)
const pricingTableRef = ref(null)
const materialOptions = ref([])
const processOptions = ref([])
const extraFeeOptions = ref([])
const repeatAnalysis = ref(null)

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

const defaultGlobalConfig = () => ({ materialName: '45#', shape: '板材', processRoute: [] })
const normalizePartNo = (value) => String(value || '').trim().toUpperCase()

const normalizeGlobalConfig = (config) => {
  if (!config || typeof config !== 'object') return defaultGlobalConfig()
  return {
    materialName: config.materialName || defaultGlobalConfig().materialName,
    shape: config.shape || defaultGlobalConfig().shape,
    processRoute: normalizeRouteList(config.processRoute)
  }
}

const repeatItemMap = computed(() => {
  const map = new Map()
  ;(repeatAnalysis.value?.repeatItems || []).forEach(item => {
    map.set(normalizePartNo(item.partNo), item)
  })
  return map
})

const canExportQuote = computed(() => {
  const items = quoteData.value?.itemList || []
  const drawableItems = items.filter(item => item.drawingUrl)
  return !!rfqId.value
    && items.length > 0
    && drawableItems.every(item => item.finalUnitPrice != null)
})

const canCompleteQuote = computed(() => {
  const items = quoteData.value?.itemList || []
  const drawableItems = items.filter(item => item.drawingUrl)
  return !!rfqId.value
    && quoteData.value?.orderInfo?.status !== '已报价'
    && items.length > 0
    && drawableItems.every(item => item.finalUnitPrice != null)
})

// 页面加载时自动拉取数据
onMounted(async () => {
  rfqId.value = route.query.rfqId
  await fetchDicts()
  
  // 1. 读取全局缓存
  const savedConfig = localStorage.getItem(`erp_global_config_${rfqId.value}`) || localStorage.getItem('erp_global_config')
  if (savedConfig) {
    globalConfig.value = JSON.parse(savedConfig)
  }

  // 2. 自动根据路由 ID 获取数据
  if (rfqId.value) {
    fetchQuoteData()
  } else {
    ElMessage.error('缺失订单 ID 参数，请返回上一页重新进入！')
  }
})

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
  } catch (error) {
    console.error(error)
    materialOptions.value = [
      { id: 'fallback-45', materialName: '45#', density: 7.85, plateDensity: 7.85, platePrice: 7.1, barDensity: 7.85, barPrice: 10, tubeDensity: 7.85, tubePrice: 10 }
    ]
    processOptions.value = [
      { id: 'fallback-mill', processName: '铣' },
      { id: 'fallback-cnc1', processName: 'CNC1' }
    ]
    extraFeeOptions.value = []
    ElMessage.warning('基础字典加载失败，已启用本地兜底数据。')
  }
}

// 拉取订单核心数据
const getPricingTableScrollTop = () => {
  const wrapper = pricingTableRef.value?.$el?.querySelector('.el-scrollbar__wrap')
  return wrapper?.scrollTop || 0
}

const restorePricingTableScrollTop = (scrollTop) => {
  requestAnimationFrame(() => {
    const wrapper = pricingTableRef.value?.$el?.querySelector('.el-scrollbar__wrap')
    if (wrapper) {
      wrapper.scrollTop = scrollTop
    }
  })
}

const fetchQuoteData = async (options = {}) => {
  const scrollTop = options.keepScroll ? getPricingTableScrollTop() : 0
  loading.value = true
  try {
    // 🌟 动态拼接真实的 rfqId
    const response = await axios.get(`${API_BASE_URL}/api/market/summary/${rfqId.value}`)
    quoteData.value = response.data
    if (quoteData.value?.orderInfo?.engineerConfig) {
      globalConfig.value = normalizeGlobalConfig(quoteData.value.orderInfo.engineerConfig)
      localStorage.setItem(`erp_global_config_${rfqId.value}`, JSON.stringify(globalConfig.value))
    }
    await loadRepeatAnalysis()
    if (options.keepScroll) {
      restorePricingTableScrollTop(scrollTop)
    }
  } catch (error) {
    ElMessage.error('获取失败，请检查后端状态！')
  } finally {
    loading.value = false
  }
}

const loadRepeatAnalysis = async () => {
  if (!rfqId.value) return
  try {
    const response = await axios.get(`${API_BASE_URL}/api/engineer/repeat-analysis/${rfqId.value}`)
    repeatAnalysis.value = response.data
  } catch (error) {
    console.error(error)
    repeatAnalysis.value = null
  }
}

const exportQuoteSheet = () => {
  if (!rfqId.value) return ElMessage.warning('缺失订单 ID。')
  window.open(`${API_BASE_URL}/api/market/export/quote/${rfqId.value}`, '_blank')
}

const completeOrderQuote = async () => {
  if (!canCompleteQuote.value) {
    return ElMessage.warning('所有有图零件完成核价后才能提交订单报价。')
  }
  completeLoading.value = true
  try {
    const response = await axios.post(`${API_BASE_URL}/api/engineer/complete-order/${rfqId.value}`)
    ElMessage.success(response.data.message || '订单报价已完成。')
    await fetchQuoteData()
  } catch (error) {
    console.error(error)
    ElMessage.error(error.response?.data?.message || '完成报价提交失败。')
  } finally {
    completeLoading.value = false
  }
}

// ==================== 🛠️ 图纸预览核心逻辑 ====================
const pdfVisible = ref(false)
const currentPdfUrl = ref('')
const pdfTitle = ref('')
const pdfLoaded = ref(false)
const currentPdfSrc = computed(() => apiUrl(currentPdfUrl.value))
const drawingUrl = (url) => apiUrl(url)

const openPdfPreview = (url, partNo) => {
  pdfTitle.value = partNo
  currentPdfUrl.value = url
  pdfLoaded.value = false
  pdfVisible.value = true
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

// ==================== 🛠️ 核价弹窗核心逻辑 ====================
const dialogVisible = ref(false)
const currentRow = ref(null)
const submitLoading = ref(false)

const matForm = reactive({ materialName: '', shape: '', L: 0, W: 0, T: 0, outR: 0, inR: 0 })
const processRoute = ref([])

const syncProcessMeta = (proc) => {
  const selected = combinedProcessOptions.value.find(item => item.optionKey === proc.optionKey)
  if (!selected) return
  proc.processName = selected.processName
  proc.calcType = selected.calcType
  proc.sourceType = selected.sourceType
}

const hydrateRouteItem = (proc) => {
  if (proc.optionKey) {
    syncProcessMeta(proc)
    return proc
  }
  const selected = combinedProcessOptions.value.find(item => item.processName === proc.processName && item.sourceType === (proc.sourceType || 'process'))
    || combinedProcessOptions.value.find(item => item.processName === proc.processName)
  if (selected) {
    proc.optionKey = selected.optionKey
    proc.processName = selected.processName
    proc.calcType = selected.calcType
    proc.sourceType = selected.sourceType
  }
  return proc
}

const normalizeRouteList = (route) => {
  if (!route) return []
  if (Array.isArray(route)) return JSON.parse(JSON.stringify(route))
  if (typeof route === 'string') {
    try {
      return JSON.parse(route)
    } catch {
      return []
    }
  }
  return []
}

const mergeRoutesWithGlobalDefaults = (savedRoutes, defaultRoutes) => {
  const result = normalizeRouteList(savedRoutes).map(route => ({
    processName: route.processName,
    optionKey: route.optionKey || '',
    calcType: route.calcType || '工时',
    sourceType: route.sourceType || (route.calcType === '非工时费用' ? 'extra' : 'process'),
    inputVal: route.inputVal || 0
  })).map(hydrateRouteItem)

  normalizeRouteList(defaultRoutes).forEach(defaultRoute => {
    const exists = result.some(route => route.processName === defaultRoute.processName)
    if (!exists && defaultRoute.processName) {
      result.push(hydrateRouteItem({
        processName: defaultRoute.processName,
        optionKey: defaultRoute.optionKey || '',
        calcType: defaultRoute.calcType || '工时',
        sourceType: defaultRoute.sourceType || (defaultRoute.calcType === '非工时费用' ? 'extra' : 'process'),
        inputVal: 0
      }))
    }
  })

  return result
}

// 🌟 打开弹窗，继承全局配置
const openQuoteDialog = (row) => {
  currentRow.value = row
  dialogVisible.value = true
  
  // 继承阶段 1 的材料设置
  const savedMaterialData = row.materialData || {}
  const savedDimensions = savedMaterialData.dimensions || {}
  matForm.materialName = savedMaterialData.name || globalConfig.value.materialName || '45#'
  matForm.shape = savedMaterialData.shape || globalConfig.value.shape || '板材'
  matForm.L = savedDimensions.L || 0
  matForm.W = savedDimensions.W || 0
  matForm.T = savedDimensions.T || 0
  matForm.outR = savedDimensions.outR || 0
  matForm.inR = savedDimensions.inR || 0

  if (row.processRoute) {
    processRoute.value = mergeRoutesWithGlobalDefaults(row.processRoute, globalConfig.value.processRoute)
  } else if (globalConfig.value.processRoute && globalConfig.value.processRoute.length > 0) {
    processRoute.value = normalizeRouteList(globalConfig.value.processRoute).map(p => ({
      processName: p.processName,
      optionKey: p.optionKey || '',
      calcType: p.calcType || '工时',
      sourceType: p.sourceType || 'process',
      inputVal: 0 // 等待工程师输入工时/费用
    })).map(hydrateRouteItem)
  } else {
    processRoute.value = [] // 兜底空数组
  }
}

// 智能计算材料费
const currentMatInfo = computed(() => {
  return materialOptions.value.find(item => item.materialName === matForm.materialName) || {
    density: 0,
    platePrice: 0,
    barPrice: 0,
    tubePrice: 0
  }
})
const currentUnitPrice = computed(() => {
  if (matForm.shape === '棒材') return currentMatInfo.value.barPrice || 0
  if (matForm.shape === '管材') return currentMatInfo.value.tubePrice || 0
  return currentMatInfo.value.platePrice || 0
})
const currentDensity = computed(() => {
  if (matForm.shape === '棒材') return currentMatInfo.value.barDensity || currentMatInfo.value.density || 0
  if (matForm.shape === '管材') return currentMatInfo.value.tubeDensity || currentMatInfo.value.density || 0
  return currentMatInfo.value.plateDensity || currentMatInfo.value.density || 0
})
const calculatedWeight = computed(() => {
  let v = 0;
  if (matForm.shape === '板材') v = matForm.L * matForm.W * matForm.T;
  else if (matForm.shape === '棒材') v = Math.PI * Math.pow(matForm.outR / 2, 2) * matForm.L;
  else if (matForm.shape === '管材') v = Math.PI * (Math.pow(matForm.outR / 2, 2) - Math.pow(matForm.inR / 2, 2)) * matForm.L;
  return (v * currentDensity.value) / 1000000;
})
const calculatedMaterialCost = computed(() => calculatedWeight.value * currentUnitPrice.value)

// 提交逻辑
const submitCurrentQuote = async () => {
  submitLoading.value = true
  const validProcess = processRoute.value.filter(p => p.processName && p.inputVal > 0)
  
  const payload = {
    itemId: currentRow.value.id,
    materialData: {
      name: matForm.materialName,
      shape: matForm.shape,
      weight: calculatedWeight.value,
      density: currentDensity.value,
      unitPrice: currentUnitPrice.value,
      cost: calculatedMaterialCost.value,
      dimensions: {
        L: matForm.L,
        W: matForm.W,
        T: matForm.T,
        outR: matForm.outR,
        inR: matForm.inR
      }
    },
    processRoute: validProcess
  }
  
  try {
    await axios.post(`${API_BASE_URL}/api/engineer/submit-quote`, payload)
    ElMessage.success('核价成功！')
    dialogVisible.value = false
    await fetchQuoteData({ keepScroll: true }) // 刷新大表格，保留当前滚动位置
  } catch (error) {
    console.error(error)
    ElMessage.error(error.response?.data?.message || '核价提交失败，请检查后端服务。')
  } finally {
    submitLoading.value = false
  }
}
</script>

<style scoped>
.pricing-container { padding: 20px; }
.main-card { border-radius: 8px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.card-header h2 { margin: 0; color: #303133; }
.header-tags { display: flex; align-items: center; gap: 10px; }

:deep(.pricing-workbench-dialog .el-dialog__body) {
  max-height: 82vh;
  overflow: hidden;
  padding-top: 12px;
}

.pricing-workbench {
  display: grid;
  grid-template-columns: minmax(520px, 0.95fr) minmax(460px, 1.05fr);
  gap: 16px;
  height: 74vh;
  min-height: 560px;
}

.pricing-form-pane,
.drawing-pane {
  min-width: 0;
  min-height: 0;
}

.pricing-form-pane {
  overflow-y: auto;
  padding-right: 4px;
}

.drawing-pane {
  display: flex;
  flex-direction: column;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  background: #fff;
  overflow: hidden;
}

.drawing-pane-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border-bottom: 1px solid #ebeef5;
  background: #f5f7fa;
}

.drawing-pane-title {
  color: #303133;
  font-weight: 700;
  line-height: 1.3;
}

.drawing-pane-subtitle {
  color: #909399;
  font-size: 12px;
  line-height: 1.4;
}

.drawing-frame {
  flex: 1;
  min-height: 0;
  background: #f5f7fa;
}

.drawing-frame-iframe {
  display: block;
  width: 100%;
  height: 100%;
}

.drawing-empty {
  flex: 1;
  display: flex;
  justify-content: center;
}

/* 弹窗顶部栏样式 */
.dialog-header-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
  padding-bottom: 15px;
  border-bottom: 1px dashed #dcdfe6;
}

.current-item-alert {
  flex: 1;
}

.dialog-drawing-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

@media (max-width: 1180px) {
  :deep(.pricing-workbench-dialog .el-dialog__body) {
    max-height: none;
    overflow: visible;
  }

  .pricing-workbench {
    grid-template-columns: 1fr;
    height: auto;
    min-height: 0;
  }

  .pricing-form-pane {
    overflow: visible;
    padding-right: 0;
  }

  .drawing-pane {
    min-height: 520px;
  }
}
</style>
