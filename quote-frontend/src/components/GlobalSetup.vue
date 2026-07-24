<template>
  <div class="erp-page engineering-setup-page">
    <header class="erp-page-header engineering-page-header">
      <div>
        <h1 class="erp-page-title">零件核价</h1>
        <p class="erp-page-description">
          先选择未核价订单，核对图纸和零件清单，再设置整单默认材料、形态和通用工艺路线。
        </p>
      </div>
      <div class="erp-page-actions engineering-header-actions">
        <el-button v-permission="'engineer:setup:order-select'" :icon="DocumentChecked" type="primary" @click="openOrderPicker" :loading="isLoading">
          选择订单
        </el-button>
        <el-button @click="fetchOrderData" :disabled="!testRfqId" :loading="isLoading">
          刷新当前订单
        </el-button>
      </div>
    </header>

    <section class="erp-surface engineering-context-card">
      <div class="engineering-context-main">
        <span class="selected-order-label">当前订单</span>
        <strong class="selected-order-value" :title="selectedOrderLabel">
          {{ selectedOrderLabel }}
        </strong>
      </div>
      <div class="engineering-metric-grid">
        <div class="engineering-metric">
          <span>总零件数</span>
          <strong>{{ itemCount || itemList.length || 0 }}</strong>
        </div>
        <div class="engineering-metric">
          <span>当前预览</span>
          <strong>{{ itemList.length || 0 }}</strong>
        </div>
        <div class="engineering-metric">
          <span>重复件</span>
          <strong>{{ repeatAnalysis?.repeatCount ?? '-' }}</strong>
        </div>
        <div class="engineering-metric">
          <span>新件</span>
          <strong>{{ repeatAnalysis?.newCount ?? '-' }}</strong>
        </div>
        <div class="engineering-metric">
          <span>通用工序</span>
          <strong>{{ config.processRoute.length }}</strong>
        </div>
      </div>
    </section>

    <section class="engineering-workspace">
      <section class="erp-table-surface engineering-overview-panel">
        <div class="erp-table-header engineering-panel-header">
          <div>
            <h2 class="erp-table-title">
              订单概览 <span v-if="orderInfo.rfqNo" class="order-no">({{ orderInfo.rfqNo }})</span>
            </h2>
            <p class="panel-desc">
              仅预览前 {{ previewLimit }} 个零件，用于快速判断全局材料与工艺设定；完整零件清单请进入逐件核价页处理。
            </p>
          </div>
        </div>
        <div class="erp-table-body engineering-table-body">
          <el-alert
            v-if="repeatAnalysis"
            :title="`重复订单识别：${repeatAnalysis.repeatCount || 0} 个重复零件，${repeatAnalysis.newCount || 0} 个新零件`"
            :type="repeatAnalysis.repeatCount > 0 ? 'success' : 'info'"
            :closable="false"
            show-icon
            class="repeat-alert"
          />

          <div v-if="!orderInfo.id" class="erp-empty-state engineering-order-empty">
            <strong>请先选择零件核价订单</strong>
            <span>选择订单后，这里会显示零件清单、重复件识别和图纸预览入口。</span>
          </div>

          <el-table v-else :data="itemList" border stripe height="520" v-loading="isLoading">
            <el-table-column prop="partNo" label="图号" width="140" show-overflow-tooltip />
            <el-table-column prop="version" label="版本" width="80" align="center" />
            <el-table-column prop="description" label="物料描述" min-width="120" show-overflow-tooltip />
            <el-table-column prop="qty" label="数量" width="70" align="center" />
            <el-table-column label="重复" width="90" align="center">
              <template #default="scope">
                <el-tag v-if="repeatItemMap.has(itemIdentityKey(scope.row))" type="success" size="small">重复</el-tag>
                <el-tag v-else type="info" size="small">新件</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="图纸" width="170" align="center">
              <template #default="scope">
                <div v-if="scope.row.drawingUrl" class="erp-table-actions drawing-actions">
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
      </section>

      <section class="erp-surface engineering-config-panel">
        <div class="erp-section-header engineering-panel-header">
          <div>
            <h2 class="erp-section-title">订单全局默认设定</h2>
            <p class="desc">设置适用于本批次大部分零件的基础材质、形态与工序路线，后续逐件核价仍可单独调整。</p>
          </div>
        </div>

        <div class="erp-section-body">
          <el-alert
            v-if="repeatAnalysis?.repeatCount > 0"
            title="已根据图号和版本匹配已提单历史重复件推荐默认材料与工艺路线，可继续手动调整后覆盖到本单其他零件。"
            type="success"
            :closable="false"
            show-icon
            class="repeat-alert"
          />

          <el-form label-width="100px" class="erp-dialog-form setup-form">
            <section class="erp-dialog-section engineering-config-section">
              <div class="section-title">默认材料与形态</div>
              <el-row :gutter="20">
                <el-col :span="12">
                  <el-form-item label="默认材质">
                    <el-button class="picker-trigger full-width" :title="selectedMaterialLabel" @click="materialPickerVisible = true">
                      <span class="picker-trigger-text" :title="selectedMaterialLabel">{{ selectedMaterialLabel }}</span>
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
            </section>

            <section class="erp-dialog-section engineering-config-section">
              <div class="section-title process-title">通用工艺路线排程</div>
              <div class="process-box">
                <el-row :gutter="10" v-for="(proc, index) in config.processRoute" :key="index" class="process-row">
                  <el-col :span="4"><el-tag type="info">第{{ index + 1 }}道</el-tag></el-col>
                  <el-col :span="14">
                    <el-button class="picker-trigger full-width" :title="formatProcessLabel(proc, '选择全局默认工序')" @click="openProcessPicker(index)">
                      <span :class="proc.processName ? 'picker-trigger-text' : 'picker-trigger-placeholder'" :title="formatProcessLabel(proc, '选择全局默认工序')">
                        {{ formatProcessLabel(proc, '选择全局默认工序') }}
                      </span>
                      <span class="picker-trigger-action">选择</span>
                    </el-button>
                  </el-col>
                  <el-col :span="6">
                    <el-button type="danger" plain size="small" class="erp-danger-action" @click="removeProcess(index)">删除</el-button>
                  </el-col>
                </el-row>
                <el-button type="primary" plain class="add-process-btn" @click="addProcess">
                  + 添加通用工序
                </el-button>
              </div>
            </section>

            <div class="engineering-save-panel">
              <div>
                <strong>保存整单默认设定</strong>
                <span>保存后进入逐件核价中心，逐个零件仍可覆盖材料、工艺和费用。</span>
              </div>
              <el-button v-permission="'engineer:setup:save-config'" type="primary" size="large" @click="handleNext" class="submit-btn">
                保存设定，进入逐件核价
              </el-button>
            </div>
          </el-form>
        </div>
      </section>
    </section>

    <el-dialog
      v-model="previewVisible"
      :title="`图纸预览：${previewTitle}`"
      width="75%"
      top="5vh"
      destroy-on-close
    >
      <div v-loading="!iframeLoaded" class="pdf-frame-shell">
        <iframe 
          :src="previewSrc" 
          width="100%" 
          height="100%" 
          frameborder="0"
          @load="iframeLoaded = true"
        ></iframe>
      </div>
      <template #footer>
        <div class="erp-dialog-footer preview-dialog-footer">
          <el-button type="primary" plain @click="openDrawingInNewWindow(currentPreviewUrl)">
            新窗口打开图纸
          </el-button>
        </div>
      </template>
    </el-dialog>

    <OrderPickerDialog
      v-model="orderPickerVisible"
      title="选择零件核价订单"
      customer-scope="engineer-setup"
      order-scope="engineer-setup"
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
import { ElMessage } from 'element-plus'
import { DocumentChecked } from '@element-plus/icons-vue'
import { useRouter, useRoute } from 'vue-router'
import EngineeringOptionPickerDialog from './EngineeringOptionPickerDialog.vue'
import OrderPickerDialog from './OrderPickerDialog.vue'
import { getRepeatAnalysis, getSetupSummary, saveOrderConfig } from '../services/engineerApi'
import { useDictionaryStore } from '../stores/dictionaryStore'
import { useGlobalConfigStore } from '../stores/globalConfigStore'
import {
  createAuthenticatedObjectUrl,
  openAuthenticatedFileInNewWindow,
  readFileErrorMessage,
  revokeObjectUrl
} from '../api'

const router = useRouter()
const route = useRoute()
const dictionaryStore = useDictionaryStore()
const globalConfigStore = useGlobalConfigStore()

const testRfqId = ref(null)

const resolveRouteRfqId = () => {
  const value = Array.isArray(route.query.rfqId) ? route.query.rfqId[0] : route.query.rfqId
  const id = Number(value)
  return Number.isFinite(id) && id > 0 ? id : null
}

// 数据存放区
const isLoading = ref(false)
const orderPickerVisible = ref(false)
const materialPickerVisible = ref(false)
const processPickerVisible = ref(false)
const editingProcessIndex = ref(null)
const orderInfo = ref({})
const itemList = ref([])
const itemCount = ref(0)
const previewLimit = ref(10)
const materialOptions = computed(() => dictionaryStore.materials)
const processOptions = computed(() => dictionaryStore.processes)
const extraFeeOptions = computed(() => dictionaryStore.extraFeeItems)
const repeatAnalysis = ref(null)

const selectedOrderLabel = computed(() => (
  orderInfo.value.rfqNo
    ? `${orderInfo.value.rfqNo} / ${orderInfo.value.customerName || '-'}`
    : '未选择订单'
))

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

const combinedProcessOptions = computed(() => dictionaryStore.combinedProcessOptions)

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
    await dictionaryStore.loadBaseDicts()
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
    const nextConfig = JSON.parse(JSON.stringify(config))
    await saveOrderConfig(orderInfo.value.id, nextConfig)
    globalConfigStore.setConfig(orderInfo.value.id, nextConfig)
  } catch (error) {
    console.error(error)
    return ElMessage.error('订单全局默认设定保存失败，请检查后端服务。')
  }

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
    const response = await getSetupSummary(testRfqId.value)
    if (response && response.orderInfo) {
      orderInfo.value = response.orderInfo
      itemList.value = response.previewItemList || response.itemList || []
      itemCount.value = Number(response.itemCount ?? itemList.value.length)
      previewLimit.value = Number(response.previewLimit ?? 10)
      if (orderInfo.value.engineerConfig) {
        globalConfigStore.applyEngineerConfig(orderInfo.value.id, orderInfo.value.engineerConfig)
        assignConfig(orderInfo.value.engineerConfig)
      }
      await loadRepeatAnalysis(orderInfo.value.id, !!orderInfo.value.engineerConfig)
      ElMessage.success(`成功加载订单概览，总零件 ${itemCount.value} 个，当前预览 ${itemList.value.length} 个。`)
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
    const response = await getRepeatAnalysis(rfqId)
    repeatAnalysis.value = response
    if (!skipRecommendedConfig) {
      applyRecommendedConfig(response?.recommendedConfig)
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
  const initialRfqId = resolveRouteRfqId()
  if (initialRfqId) {
    testRfqId.value = initialRfqId
    await fetchOrderData()
  }
})
</script>

<style scoped>
.order-no {
  color: var(--erp-primary);
  font-size: 13px;
  font-weight: 600;
}

.panel-desc,
.desc {
  margin: 6px 0 0;
  color: var(--erp-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.engineering-page-header {
  align-items: center;
}

.engineering-header-actions {
  min-width: 260px;
}

.engineering-context-card {
  display: grid;
  grid-template-columns: minmax(260px, 1.4fr) minmax(360px, 2fr);
  gap: var(--erp-space-4);
  align-items: center;
  padding: var(--erp-space-4) var(--erp-space-5);
}

.engineering-context-main {
  display: flex;
  flex-direction: column;
  gap: var(--erp-space-2);
  min-width: 0;
}

.selected-order-label {
  color: var(--erp-text-secondary);
  font-size: var(--erp-font-size-xs);
  font-weight: var(--erp-font-weight-bold);
  letter-spacing: 0.02em;
}

.selected-order-value {
  min-width: 0;
  overflow: hidden;
  color: var(--erp-text);
  font-size: var(--erp-font-size-lg);
  line-height: var(--erp-line-height-tight);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.engineering-metric-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: var(--erp-space-2);
}

.engineering-metric {
  min-width: 0;
  padding: var(--erp-space-3);
  border: 1px solid var(--erp-border-soft);
  border-radius: var(--erp-radius-lg);
  background: var(--erp-surface-muted);
}

.engineering-metric span {
  display: block;
  color: var(--erp-text-secondary);
  font-size: var(--erp-font-size-xs);
}

.engineering-metric strong {
  display: block;
  margin-top: var(--erp-space-1);
  color: var(--erp-text);
  font-size: var(--erp-font-size-xl);
  line-height: var(--erp-line-height-tight);
}

.engineering-workspace {
  display: grid;
  grid-template-columns: minmax(0, 1.08fr) minmax(420px, 0.92fr);
  gap: var(--erp-space-4);
  align-items: start;
}

.engineering-overview-panel,
.engineering-config-panel {
  overflow: hidden;
}

.engineering-panel-header {
  align-items: flex-start;
}

.engineering-table-body {
  display: flex;
  flex-direction: column;
  gap: var(--erp-space-3);
}

.engineering-order-empty strong,
.engineering-order-empty span {
  display: block;
}

.engineering-order-empty strong {
  color: var(--erp-text);
}

.engineering-config-section + .engineering-config-section,
.engineering-save-panel {
  margin-top: var(--erp-space-3);
}

.repeat-alert {
  margin-bottom: 0;
}

.setup-form {
  padding-top: 0;
}

.section-title {
  margin: 0 0 12px;
  padding-left: 10px;
  border-left: 3px solid var(--erp-primary);
  color: var(--erp-text);
  font-size: 14px;
  font-weight: 700;
  line-height: 1.2;
}

.process-title {
  margin-top: 0;
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

.process-row:last-of-type {
  margin-bottom: 12px;
}

.add-process-btn,
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

.picker-trigger :deep(> span) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  min-width: 0;
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

.engineering-save-panel {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--erp-space-3);
  padding: var(--erp-space-4);
  border: 1px solid var(--erp-primary);
  border-radius: var(--erp-radius-lg);
  background: var(--erp-primary-soft);
}

.engineering-save-panel strong,
.engineering-save-panel span {
  display: block;
}

.engineering-save-panel strong {
  color: var(--erp-text);
  font-size: var(--erp-font-size-md);
}

.engineering-save-panel span {
  margin-top: var(--erp-space-1);
  color: var(--erp-text-secondary);
  font-size: var(--erp-font-size-xs);
}

.submit-btn {
  min-width: 210px;
}

.drawing-actions {
  justify-content: center;
}

.drawing-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.pdf-frame-shell {
  width: 100%;
  height: 70vh;
}

@media (max-width: 1180px) {
  .engineering-context-card,
  .engineering-workspace {
    grid-template-columns: 1fr;
  }

  .engineering-metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .engineering-context-card,
  .engineering-save-panel {
    align-items: stretch;
  }

  .engineering-header-actions,
  .engineering-save-panel {
    flex-direction: column;
  }

  .engineering-metric-grid {
    grid-template-columns: 1fr;
  }

  .submit-btn {
    width: 100%;
  }
}
</style>
