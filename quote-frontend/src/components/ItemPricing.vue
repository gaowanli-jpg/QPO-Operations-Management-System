<template>
  <div class="pricing-container">
    <el-card shadow="never" class="main-card">
      <template #header>
        <div class="card-header">
          <h2>工程部：逐件工艺路线与核价中心</h2>
          <div class="header-tags">
            <el-tag type="success" size="large" v-if="rfqId">正在处理询价单 ID: {{ rfqId }}</el-tag>
            <el-tag size="large" v-if="quoteData?.orderInfo?.status">订单状态：{{ quoteData.orderInfo.status }}</el-tag>
          </div>
        </div>
      </template>

      <!-- 头部操作区 -->
      <div class="toolbar-row">
        <div class="toolbar-actions">
          <el-button @click="$router.back()">返回修改全局设定</el-button>
          <el-button type="success" plain :disabled="!canExportQuote" @click="exportQuoteSheet">
            生成报价单
          </el-button>
          <el-button type="primary" :disabled="!canCompleteQuote" :loading="completeLoading" @click="completeOrderQuote">
            完成报价提交
          </el-button>
        </div>
      </div>

      <div v-if="!quoteData" class="empty-state" v-loading="loading">
        <el-empty description="正在拉取市场部数据..." />
      </div>

      <!-- 数据大表格 -->
      <div v-else class="table-shell">
        <el-table
          ref="pricingTableRef"
          :data="quoteData.itemList"
          row-key="id"
          border
          stripe
          class="pricing-table"
          height="450"
        >
          <el-table-column prop="partNo" label="图号" width="140" fixed />
          <el-table-column prop="version" label="版本" width="80" align="center" />
          <el-table-column prop="description" label="名称/描述" min-width="150" show-overflow-tooltip />
          <el-table-column prop="qty" label="数量" width="70" align="center" />
          <el-table-column label="类型" width="90" align="center">
            <template #default="scope">
              <el-tag :type="isAssemblyItem(scope.row) ? 'warning' : 'info'" size="small">
                {{ isAssemblyItem(scope.row) ? '组合件' : '普通件' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="材料毛重(kg)" width="120" align="right">
            <template #default="scope">{{ formatWeight(scope.row.materialGrossWeight) }}</template>
          </el-table-column>
          <el-table-column label="零件净重(kg)" width="120" align="right">
            <template #default="scope">{{ formatWeight(scope.row.partNetWeight) }}</template>
          </el-table-column>
          <el-table-column label="重复" width="90" align="center">
            <template #default="scope">
              <el-tag v-if="repeatItemMap.has(itemIdentityKey(scope.row))" type="success" size="small">重复</el-tag>
              <el-tag v-else type="info" size="small">新件</el-tag>
            </template>
          </el-table-column>
          
          <el-table-column label="图纸" width="90" align="center">
            <template #default="scope">
              <el-button 
                v-if="scope.row.drawingUrl" 
                type="success" size="small" plain 
                @click="openPdfPreview(scope.row.drawingUrl, scope.row.partNo)"
              >图纸</el-button>
              <el-tag v-else type="info" size="small">无图</el-tag>
            </template>
          </el-table-column>

          <el-table-column label="核算总成本" width="110" align="right">
            <template #default="scope">
              <span v-if="scope.row.baseCost">¥ {{ scope.row.baseCost.toFixed(2) }}</span><span v-else class="muted-price">待核算</span>
            </template>
          </el-table-column>
          <el-table-column label="含税单价" width="110" align="right">
            <template #default="scope"><span v-if="scope.row.finalUnitPrice != null" class="success-price">¥ {{ scope.row.finalUnitPrice.toFixed(2) }}</span></template>
          </el-table-column>
          <el-table-column label="单项合计" width="110" align="right">
            <template #default="scope"><span v-if="scope.row.finalTotalPrice != null" class="danger-price">¥ {{ scope.row.finalTotalPrice.toFixed(2) }}</span></template>
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

    <!-- 工程师核价控制台 -->
    <el-dialog
      v-model="dialogVisible"
      title="工程师精准核价控制台"
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
              :title="`当前核价：${currentRow?.partNo} / ${currentRow?.version || '-'} - ${currentRow?.description} (需生产: ${currentRow?.qty} 件)`"
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

          <el-form label-width="100px" class="pricing-section-form quote-mode-form">
            <el-form-item label="核价类型">
              <el-radio-group v-model="quoteMode">
                <el-radio-button label="NORMAL">普通零件</el-radio-button>
                <el-radio-button label="ASSEMBLY">组合件</el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-form>

          <template v-if="quoteMode === 'NORMAL'">
          <!-- 模块A：材料 -->
          <div class="form-section-title">模块 A：材料成本核算（已继承全局设定）</div>
          <el-form label-width="100px" class="pricing-section-form">
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="材质选择">
                  <el-select v-model="matForm.materialName" class="full-width">
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
            <el-row :gutter="20" class="dimension-row">
              <el-col :span="12" v-if="matForm.shape === '板材'"><el-form-item label="长(m)"><el-input-number v-model="matForm.L" :min="0" :step="0.01" :precision="4" placeholder="请输入" controls-position="right" class="full-width" /></el-form-item></el-col>
              <el-col :span="12" v-if="matForm.shape === '板材'"><el-form-item label="宽(m)"><el-input-number v-model="matForm.W" :min="0" :step="0.01" :precision="4" placeholder="请输入" controls-position="right" class="full-width" /></el-form-item></el-col>
              <el-col :span="12" v-if="matForm.shape === '板材'"><el-form-item label="厚(m)"><el-input-number v-model="matForm.T" :min="0" :step="0.01" :precision="4" placeholder="请输入" controls-position="right" class="full-width" /></el-form-item></el-col>
              <el-col :span="12" v-if="matForm.shape !== '板材'"><el-form-item label="长度(m)"><el-input-number v-model="matForm.L" :min="0" :step="0.01" :precision="4" placeholder="请输入" controls-position="right" class="full-width" /></el-form-item></el-col>
              <el-col :span="12" v-if="matForm.shape !== '板材'"><el-form-item label="外径(m)"><el-input-number v-model="matForm.outR" :min="0" :step="0.01" :precision="4" placeholder="请输入" controls-position="right" class="full-width" /></el-form-item></el-col>
              <el-col :span="12" v-if="matForm.shape === '管材'"><el-form-item label="内径(m)"><el-input-number v-model="matForm.inR" :min="0" :step="0.01" :precision="4" placeholder="请输入" controls-position="right" class="full-width" /></el-form-item></el-col>
            </el-row>
            <el-row :gutter="20" class="weight-row">
              <el-col :span="12">
                <el-form-item label="材料毛重(kg)">
                  <el-input :model-value="calculatedWeight.toFixed(3)" disabled class="full-width" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="零件净重(kg)">
                  <el-input-number
                    v-model="matForm.netWeight"
                    :min="0"
                    :step="0.01"
                    :precision="3"
                    placeholder="请输入"
                    controls-position="right"
                    class="full-width"
                  />
                </el-form-item>
              </el-col>
            </el-row>
            <p class="material-summary">
              毛坯体积：{{ calculatedVolume.toFixed(6) }} m³，毛坯重量：{{ calculatedWeight.toFixed(3) }} kg，材料单价：{{ currentUnitPrice.toFixed(2) }} 元/kg，预测本件材料费：¥ {{ calculatedMaterialCost.toFixed(2) }}
            </p>
          </el-form>

          <!-- 模块B：工艺 -->
          <div class="process-title-row">
            <div class="form-section-title process-title">模块 B：工艺路线排程（已继承全局设定）</div>
            <el-button type="primary" plain size="small" :icon="Plus" @click="addProcess">
              新增工序
            </el-button>
          </div>
          <div class="process-box">
            <el-empty v-if="processRoute.length === 0" description="暂无工艺路线" :image-size="60">
              <el-button type="primary" plain size="small" :icon="Plus" @click="addProcess">新增工序</el-button>
            </el-empty>
            <el-row :gutter="10" v-for="(proc, index) in processRoute" :key="proc.rowKey || index" class="process-row">
              <el-col :span="7">
                <el-select v-model="proc.optionKey" class="full-width" @change="syncProcessMeta(proc)">
                  <el-option
                    v-for="process in combinedProcessOptions"
                    :key="process.optionKey"
                    :label="process.label"
                    :value="process.optionKey"
                  />
                </el-select>
              </el-col>
              <el-col :span="3">
                <el-tag :type="proc.calcType === '非工时费用' ? 'warning' : 'info'">{{ proc.calcType || '工时' }}</el-tag>
              </el-col>
              <el-col :span="8">
                <el-input-number
                  v-model="proc.inputVal"
                  :min="0"
                  :step="proc.calcType === '非工时费用' ? 10 : 0.5"
                  placeholder="请输入"
                  class="full-width"
                />
              </el-col>
              <el-col :span="6">
                <div class="process-row-actions">
                  <el-tooltip content="在当前行前插入工序" placement="top">
                    <el-button size="small" plain :icon="Plus" @click="insertProcess(index)">插入</el-button>
                  </el-tooltip>
                  <el-tooltip content="删除当前工序" placement="top">
                    <el-button size="small" type="danger" plain :icon="Delete" @click="removeProcess(index)">删除</el-button>
                  </el-tooltip>
                </div>
              </el-col>
            </el-row>
          </div>
          </template>

          <div v-else class="assembly-box">
            <div class="form-section-title">模块 A：组合件子件汇总</div>
            <el-alert
              type="info"
              :closable="false"
              title="组合件父件价格 = 子件含税单价汇总 + 组装费；不会再次计算管理费、利润、税费。"
              class="assembly-tip"
            />
            <div class="assembly-actions">
              <el-button type="primary" plain size="small" :icon="Plus" @click="openAssemblyChildPicker">选择子件</el-button>
            </div>
            <el-empty v-if="assemblyChildren.length === 0" description="暂无子件关系" :image-size="60">
              <el-button type="primary" plain size="small" :icon="Plus" @click="openAssemblyChildPicker">选择子件</el-button>
            </el-empty>
            <el-row
              v-for="(child, index) in assemblyChildren"
              :key="child.rowKey || index"
              :gutter="10"
              class="assembly-row"
            >
              <el-col :span="10">
                <div class="assembly-child-info">
                  <div class="assembly-child-title">{{ child.partNo || '-' }} / {{ child.version || '-' }}</div>
                  <div class="assembly-child-desc">{{ child.description || '-' }}</div>
                </div>
              </el-col>
              <el-col :span="4">
                <el-input-number
                  v-model="child.qtyPerParent"
                  :min="0.0001"
                  :step="1"
                  :precision="4"
                  controls-position="right"
                  class="full-width"
                />
              </el-col>
              <el-col :span="6">
                <div class="assembly-line-price">
                  {{ child.finalUnitPrice != null ? `¥ ${(Number(child.finalUnitPrice) * Number(child.qtyPerParent || 0)).toFixed(2)}` : '子件未核价' }}
                </div>
              </el-col>
              <el-col :span="4">
                <el-button size="small" type="danger" plain :icon="Delete" @click="removeAssemblyChild(index)">删除</el-button>
              </el-col>
            </el-row>
            <el-form label-width="100px" class="pricing-section-form assembly-fee-form">
              <el-form-item label="组装费">
                <el-input-number
                  v-model="assemblyFee"
                  :min="0"
                  :step="10"
                  :precision="2"
                  placeholder="请输入"
                  controls-position="right"
                  class="full-width"
                />
              </el-form-item>
            </el-form>
            <div class="assembly-summary">
              子件含税汇总：¥ {{ assemblyChildrenTotal.toFixed(2) }}，
              组装费：¥ {{ Number(assemblyFee || 0).toFixed(2) }}，
              父件含税单价：¥ {{ assemblyFinalUnitPrice.toFixed(2) }}
            </div>
          </div>
        </div>

        <aside class="drawing-pane">
          <div class="drawing-pane-header">
            <div>
              <div class="drawing-pane-title">当前零件图纸</div>
              <div class="drawing-pane-subtitle">{{ currentRow ? `${currentRow.partNo || '-'} / ${currentRow.version || '-'}` : '-' }}</div>
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

    <el-dialog
      v-model="assemblyPickerVisible"
      title="选择组合件子件"
      width="86vw"
      top="6vh"
      class="assembly-picker-dialog"
      destroy-on-close
    >
      <div class="assembly-picker-toolbar">
        <div>
          <div class="assembly-picker-title">当前 RFQ 零件表</div>
          <div class="assembly-picker-subtitle">可一次选择多个子件，已加入的子件不可重复选择。</div>
        </div>
        <el-tag type="info">已选 {{ assemblyPickerSelection.length }} 项</el-tag>
      </div>
      <el-table
        ref="assemblyPickerTableRef"
        :data="assemblyChildOptions"
        row-key="id"
        border
        stripe
        height="560"
        @selection-change="handleAssemblyPickerSelection"
      >
        <el-table-column type="selection" width="48" :selectable="isAssemblyPickerRowSelectable" />
        <el-table-column prop="partNo" label="图号" width="150" fixed show-overflow-tooltip />
        <el-table-column prop="version" label="版本" width="80" align="center" />
        <el-table-column prop="description" label="名称/描述" min-width="180" show-overflow-tooltip />
        <el-table-column prop="qty" label="数量" width="80" align="center" />
        <el-table-column label="类型" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="isAssemblyItem(row) ? 'warning' : 'info'" size="small">
              {{ isAssemblyItem(row) ? '组合件' : '普通件' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">{{ row.status || '-' }}</template>
        </el-table-column>
        <el-table-column label="含税单价" width="110" align="right">
          <template #default="{ row }">
            <span v-if="row.finalUnitPrice != null">¥ {{ Number(row.finalUnitPrice).toFixed(2) }}</span>
            <el-tag v-else type="warning" size="small">未核价</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="图纸" width="190" align="center" fixed="right">
          <template #default="{ row }">
            <div v-if="row.drawingUrl" class="picker-drawing-actions">
              <el-button type="warning" plain size="small" @click.stop="openPdfPreview(row.drawingUrl, row.partNo)">
                弹窗预览
              </el-button>
              <el-button type="primary" plain size="small" @click.stop="openDrawingInNewWindow(row.drawingUrl)">
                新窗口
              </el-button>
            </div>
            <el-tag v-else type="info" size="small">无图</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="assemblyPickerVisible = false">取消</el-button>
        <el-button type="primary" :disabled="assemblyPickerSelection.length === 0" @click="confirmAssemblyChildPicker">
          加入子件
        </el-button>
      </template>
    </el-dialog>

    <!-- 图纸预览 -->
    <el-dialog
      v-model="pdfVisible"
      :title="`图纸预览：${pdfTitle}`"
      width="75%"
      top="5vh"
      destroy-on-close
    >
      <div v-loading="!pdfLoaded" class="pdf-frame-shell">
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
import { ref, reactive, computed, nextTick, onMounted } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { Delete, Plus } from '@element-plus/icons-vue'
import { useRoute } from 'vue-router'
import { API_BASE_URL, apiUrl } from '../config'
import { downloadByAxios } from '../download'

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
const normalizeVersion = (value) => {
  const text = String(value || '').trim().toUpperCase()
  return text
}
const itemIdentityKey = (item) => `${normalizePartNo(item?.partNo)}||${normalizeVersion(item?.version)}`

const normalizeGlobalConfig = (config) => {
  if (!config || typeof config !== 'object') return defaultGlobalConfig()
  return {
    materialName: config.materialName || defaultGlobalConfig().materialName,
    shape: config.shape || defaultGlobalConfig().shape,
    processRoute: normalizeRouteList(config.processRoute)
  }
}

const numberOrZero = (value) => {
  const numericValue = Number(value || 0)
  return Number.isFinite(numericValue) ? numericValue : 0
}
const numberOrNullForInput = (value) => {
  if (value === null || value === undefined || value === '') return null
  const numericValue = Number(value)
  if (!Number.isFinite(numericValue) || numericValue === 0) return null
  return numericValue
}
const formatWeight = (value) => {
  const numericValue = Number(value)
  return Number.isFinite(numericValue) ? numericValue.toFixed(3) : '-'
}
const toMetricDimension = (value, unit = 'm') => {
  if (value === null || value === undefined || value === '') return null
  const numericValue = numberOrZero(value)
  return unit === 'm' ? numericValue : numericValue / 1000
}

const repeatItemMap = computed(() => {
  const map = new Map()
  ;(repeatAnalysis.value?.repeatItems || []).forEach(item => {
    map.set(itemIdentityKey(item), item)
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
      { id: 'fallback-45', materialName: '45#', density: 7850, plateDensity: 7850, platePrice: 7.1, barDensity: 7850, barPrice: 10, tubeDensity: 7850, tubePrice: 10 }
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
    // 动态拼接真实的 rfqId
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

const exportQuoteSheet = async () => {
  if (!rfqId.value) return ElMessage.warning('缺失订单 ID。')
  const result = await downloadByAxios(
    `${API_BASE_URL}/api/market/export/quote/customer-lead-time/${rfqId.value}`,
    `quote-${rfqId.value}.xlsx`
  )
  if (!result.success) {
    ElMessage.error(result.message || '报价单导出失败。')
  }
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

// 图纸预览核心逻辑
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

// 核价弹窗核心逻辑
const dialogVisible = ref(false)
const currentRow = ref(null)
const submitLoading = ref(false)
const quoteMode = ref('NORMAL')
const assemblyFee = ref(null)
const assemblyChildren = ref([])
const assemblyPickerVisible = ref(false)
const assemblyPickerSelection = ref([])
const assemblyPickerTableRef = ref(null)
let assemblyChildSeq = 0

const matForm = reactive({ materialName: '', shape: '', L: null, W: null, T: null, outR: null, inR: null, netWeight: null })
const processRoute = ref([])
let processRouteSeq = 0
const PROCESS_SORT_STEP = 10

const isAssemblyItem = (item) => String(item?.quoteMode || '').toUpperCase() === 'ASSEMBLY'

const assemblyChildOptions = computed(() => {
  const currentId = currentRow.value?.id
  return (quoteData.value?.itemList || []).filter(item => item.id !== currentId)
})

const selectedAssemblyChildIds = computed(() => new Set(
  assemblyChildren.value
    .map(child => child.childItemId)
    .filter(Boolean)
))

const assemblyChildrenTotal = computed(() => assemblyChildren.value.reduce((sum, child) => {
  const unitPrice = Number(child.finalUnitPrice)
  const qty = Number(child.qtyPerParent || 0)
  return Number.isFinite(unitPrice) ? sum + unitPrice * qty : sum
}, 0))

const assemblyFinalUnitPrice = computed(() => assemblyChildrenTotal.value + numberOrZero(assemblyFee.value))

const numericSortNo = (value) => {
  const numericValue = Number(value)
  return Number.isFinite(numericValue) ? numericValue : null
}

const createProcessRow = (source = {}) => ({
  rowKey: `process-${Date.now()}-${processRouteSeq++}`,
  processName: source.processName || '',
  optionKey: source.optionKey || '',
  calcType: source.calcType || '工时',
  sourceType: source.sourceType || (source.calcType === '非工时费用' ? 'extra' : 'process'),
  inputVal: numberOrNullForInput(source.inputVal),
  sortNo: numericSortNo(source.sortNo)
})

const createAssemblyChildRow = (source = {}) => {
  const childId = source.childItemId ?? source.id ?? null
  const option = assemblyChildOptions.value.find(item => item.id === childId)
  return {
    rowKey: `assembly-child-${Date.now()}-${assemblyChildSeq++}`,
    childItemId: childId,
    qtyPerParent: Number(source.qtyPerParent ?? source.childQtyPerParent ?? 1),
    sortNo: numericSortNo(source.sortNo),
    partNo: source.partNo || option?.partNo || '',
    version: source.version || option?.version || '',
    description: source.description || option?.description || '',
    finalUnitPrice: source.finalUnitPrice ?? option?.finalUnitPrice ?? null,
    status: source.status || option?.status || ''
  }
}

const removeAssemblyChild = (index) => {
  assemblyChildren.value.splice(index, 1)
}

const openAssemblyChildPicker = async () => {
  assemblyPickerSelection.value = []
  assemblyPickerVisible.value = true
  await nextTick()
  assemblyPickerTableRef.value?.clearSelection()
}

const handleAssemblyPickerSelection = (rows) => {
  assemblyPickerSelection.value = rows
}

const isAssemblyPickerRowSelectable = (row) => !selectedAssemblyChildIds.value.has(row.id)

const confirmAssemblyChildPicker = () => {
  const existingIds = selectedAssemblyChildIds.value
  const rowsToAdd = assemblyPickerSelection.value.filter(row => !existingIds.has(row.id))
  rowsToAdd.forEach(row => {
    assemblyChildren.value.push(createAssemblyChildRow({
      childItemId: row.id,
      qtyPerParent: 1,
      partNo: row.partNo,
      version: row.version,
      description: row.description,
      finalUnitPrice: row.finalUnitPrice,
      status: row.status
    }))
  })
  assemblyPickerVisible.value = false
  assemblyPickerSelection.value = []
  if (rowsToAdd.length > 0) {
    ElMessage.success(`已加入 ${rowsToAdd.length} 个子件。`)
  }
}

const assignProcessSortNos = (rows = processRoute.value) => {
  rows.forEach((row, index) => {
    row.sortNo = (index + 1) * PROCESS_SORT_STEP
  })
  return rows
}

const addProcess = () => {
  processRoute.value.push(createProcessRow())
  assignProcessSortNos()
}

const insertProcess = (index) => {
  processRoute.value.splice(index, 0, createProcessRow())
  assignProcessSortNos()
}

const removeProcess = (index) => {
  processRoute.value.splice(index, 1)
  assignProcessSortNos()
}

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
  let list = []
  if (!route) return list
  if (Array.isArray(route)) {
    list = JSON.parse(JSON.stringify(route))
  } else if (typeof route === 'string') {
    try {
      list = JSON.parse(route)
    } catch {
      return list
    }
  } else {
    return list
  }
  return sortRouteList(Array.isArray(list) ? list : [])
}

const sortRouteList = (routes) => {
  const withIndex = routes.map((route, index) => ({ route, index }))
  const hasSortNo = withIndex.some(item => numericSortNo(item.route?.sortNo) != null)
  if (!hasSortNo) return routes
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

const processRouteKey = (route) => {
  const sourceType = route.sourceType || (route.calcType === '非工时费用' ? 'extra' : 'process')
  return `${sourceType}::${route.processName || ''}`
}

const mergeRoutesByGlobalOrder = (savedRoutes, defaultRoutes) => {
  const savedRows = normalizeRouteList(savedRoutes).map(route => hydrateRouteItem(createProcessRow(route)))
  const defaultRows = normalizeRouteList(defaultRoutes).map(route => hydrateRouteItem(createProcessRow(route)))
  const savedBuckets = new Map()
  savedRows.forEach(route => {
    const key = processRouteKey(route)
    if (!savedBuckets.has(key)) savedBuckets.set(key, [])
    savedBuckets.get(key).push(route)
  })

  return assignProcessSortNos(defaultRows
    .filter(defaultRoute => defaultRoute.processName)
    .map(defaultRoute => {
      const savedRoute = savedBuckets.get(processRouteKey(defaultRoute))?.shift()
      return {
        ...defaultRoute,
        inputVal: savedRoute ? savedRoute.inputVal : defaultRoute.inputVal
      }
    }))
}

const buildQuoteProcessRoute = () => processRoute.value
  .map((p, index) => ({ ...p, sortNo: (index + 1) * PROCESS_SORT_STEP }))
  .filter(p => p.processName && p.inputVal > 0)
  .map(p => ({
    sortNo: p.sortNo,
    processName: p.processName,
    inputVal: p.inputVal,
    calcType: p.calcType || '工时'
  }))

const buildGlobalProcessRoute = () => processRoute.value
  .map((p, index) => ({ ...p, sortNo: (index + 1) * PROCESS_SORT_STEP }))
  .filter(p => p.processName)
  .map(p => ({
    sortNo: p.sortNo,
    processName: p.processName,
    optionKey: p.optionKey || '',
    calcType: p.calcType || '工时',
    sourceType: p.sourceType || (p.calcType === '非工时费用' ? 'extra' : 'process')
  }))

const comparableProcessRoute = (route) => normalizeRouteList(route)
  .filter(p => p.processName)
  .map((p, index) => ({
    sortNo: (index + 1) * PROCESS_SORT_STEP,
    processName: p.processName || '',
    calcType: p.calcType || '工时',
    sourceType: p.sourceType || (p.calcType === '非工时费用' ? 'extra' : 'process')
  }))

const syncCurrentRouteToGlobalConfig = async () => {
  const nextProcessRoute = buildGlobalProcessRoute()
  if (JSON.stringify(comparableProcessRoute(globalConfig.value.processRoute)) === JSON.stringify(comparableProcessRoute(nextProcessRoute))) {
    return { updated: false }
  }

  const nextConfig = {
    ...normalizeGlobalConfig(globalConfig.value),
    processRoute: nextProcessRoute
  }
  globalConfig.value = nextConfig
  localStorage.setItem(`erp_global_config_${rfqId.value}`, JSON.stringify(nextConfig))
  localStorage.setItem('erp_global_config', JSON.stringify(nextConfig))
  await axios.post(`${API_BASE_URL}/api/engineer/order-config/${rfqId.value}`, JSON.parse(JSON.stringify(nextConfig)))
  return { updated: true }
}

const loadAssemblyRelations = async (row) => {
  assemblyChildren.value = []
  if (!row?.id) return
  try {
    const response = await axios.get(`${API_BASE_URL}/api/engineer/assembly-relations/${row.id}`)
    assemblyChildren.value = (response.data || []).map(item => createAssemblyChildRow(item))
  } catch (error) {
    console.error(error)
    ElMessage.error('组合件子件关系加载失败。')
  }
}

// 打开弹窗，继承全局配置
const openQuoteDialog = async (row) => {
  currentRow.value = row
  dialogVisible.value = true
  quoteMode.value = isAssemblyItem(row) ? 'ASSEMBLY' : 'NORMAL'
  assemblyFee.value = numberOrNullForInput(row.assemblyFee)
  assemblyChildren.value = []
  
  // 继承阶段 1 的材料设置
  const savedMaterialData = row.materialData || {}
  const savedDimensions = savedMaterialData.dimensions || {}
  matForm.materialName = savedMaterialData.name || globalConfig.value.materialName || '45#'
  matForm.shape = savedMaterialData.shape || globalConfig.value.shape || '板材'
  const savedDimensionUnit = savedMaterialData.dimensionUnit || 'mm'
  matForm.L = toMetricDimension(savedDimensions.L, savedDimensionUnit)
  matForm.W = toMetricDimension(savedDimensions.W, savedDimensionUnit)
  matForm.T = toMetricDimension(savedDimensions.T, savedDimensionUnit)
  matForm.outR = toMetricDimension(savedDimensions.outR, savedDimensionUnit)
  matForm.inR = toMetricDimension(savedDimensions.inR, savedDimensionUnit)
  matForm.netWeight = numberOrNullForInput(row.partNetWeight ?? savedMaterialData.netWeight)

  if (globalConfig.value.processRoute && globalConfig.value.processRoute.length > 0) {
    processRoute.value = mergeRoutesByGlobalOrder(row.processRoute, globalConfig.value.processRoute)
  } else if (row.processRoute) {
    processRoute.value = assignProcessSortNos(normalizeRouteList(row.processRoute).map(route => hydrateRouteItem(createProcessRow(route))))
  } else {
    processRoute.value = assignProcessSortNos(normalizeRouteList(globalConfig.value.processRoute)
      .map(p => hydrateRouteItem(createProcessRow(p))))
  }

  if (quoteMode.value === 'ASSEMBLY') {
    await loadAssemblyRelations(row)
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
const calculatedVolume = computed(() => {
  const length = numberOrZero(matForm.L)
  const width = numberOrZero(matForm.W)
  const thickness = numberOrZero(matForm.T)
  const outerDiameter = numberOrZero(matForm.outR)
  const innerDiameter = numberOrZero(matForm.inR)

  if (matForm.shape === '板材') {
    return length * width * thickness
  }
  if (matForm.shape === '棒材') {
    return Math.PI * Math.pow(outerDiameter / 2, 2) * length
  }
  if (matForm.shape === '管材') {
    const ringArea = Math.PI * (Math.pow(outerDiameter / 2, 2) - Math.pow(innerDiameter / 2, 2))
    return Math.max(ringArea, 0) * length
  }
  return 0
})
const calculatedWeight = computed(() => calculatedVolume.value * currentDensity.value)
const calculatedMaterialCost = computed(() => calculatedWeight.value * currentUnitPrice.value)

// 提交逻辑
const submitCurrentQuote = async () => {
  submitLoading.value = true
  let payload

  if (quoteMode.value === 'ASSEMBLY') {
    const validChildren = assemblyChildren.value
      .filter(child => child.childItemId && Number(child.qtyPerParent) > 0)
      .map((child, index) => ({
        childItemId: child.childItemId,
        qtyPerParent: Number(child.qtyPerParent),
        sortNo: (index + 1) * PROCESS_SORT_STEP
      }))
    if (validChildren.length === 0) {
      submitLoading.value = false
      return ElMessage.warning('组合件至少需要选择一个子件。')
    }
    const unquotedChildren = assemblyChildren.value.filter(child => child.childItemId && child.finalUnitPrice == null)
    if (unquotedChildren.length > 0) {
      submitLoading.value = false
      return ElMessage.warning('组合件存在未核价子件，请先完成子件核价。')
    }
    payload = {
      itemId: currentRow.value.id,
      quoteMode: 'ASSEMBLY',
      assemblyFee: numberOrZero(assemblyFee.value),
      assemblyChildren: validChildren,
      materialData: {},
      partNetWeight: 0,
      processRoute: []
    }
  } else {
    const validProcess = buildQuoteProcessRoute()
    payload = {
      itemId: currentRow.value.id,
      quoteMode: 'NORMAL',
      partNetWeight: numberOrZero(matForm.netWeight),
      materialData: {
        name: matForm.materialName,
        shape: matForm.shape,
        volume: calculatedVolume.value,
        weight: calculatedWeight.value,
        grossWeight: calculatedWeight.value,
        grossWeightUnit: 'kg',
        netWeight: numberOrZero(matForm.netWeight),
        netWeightUnit: 'kg',
        density: currentDensity.value,
        densityUnit: 'kg/m3',
        unitPrice: currentUnitPrice.value,
        unitPriceUnit: '元/kg',
        cost: calculatedMaterialCost.value,
        dimensionUnit: 'm',
        volumeUnit: 'm3',
        weightUnit: 'kg',
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
  }

  try {
    await axios.post(`${API_BASE_URL}/api/engineer/submit-quote`, payload)
    let globalSynced = true
    let globalUpdated = false
    if (quoteMode.value === 'NORMAL') {
      try {
        const syncResult = await syncCurrentRouteToGlobalConfig()
        globalUpdated = !!syncResult.updated
      } catch (syncError) {
        console.error(syncError)
        globalSynced = false
      }
    }
    ElMessage[globalSynced ? 'success' : 'warning'](
      quoteMode.value === 'ASSEMBLY'
        ? '组合件核价成功。'
        : globalSynced
        ? (globalUpdated ? '核价成功，当前工艺路线已同步为订单全局设定。' : '核价成功，工艺路线未变化。')
        : '核价成功，但订单全局工艺同步失败，请返回全局设定页检查。'
    )
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
.pricing-container {
  padding: 0;
}

.main-card {
  border: 1px solid var(--erp-border-soft);
  border-radius: 8px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

.card-header h2 {
  margin: 0;
  color: var(--erp-text);
  font-size: 20px;
  line-height: 1.3;
}

.header-tags {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  flex-wrap: wrap;
}

.toolbar-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  padding: 14px 0 0;
}

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.empty-state {
  padding: 48px 0;
  text-align: center;
}

.table-shell {
  border-top: 1px solid var(--erp-border-soft);
  padding-top: 14px;
}

.pricing-table,
.full-width {
  width: 100%;
}

.muted-price {
  color: var(--erp-text-secondary);
}

.success-price {
  color: var(--erp-success);
  font-weight: 700;
}

.danger-price {
  color: var(--erp-danger);
  font-weight: 700;
}

.form-section-title {
  margin: 18px 0 10px;
  padding-left: 10px;
  border-left: 3px solid var(--erp-primary);
  color: var(--erp-text);
  font-size: 14px;
  font-weight: 700;
  line-height: 1.2;
}

.pricing-section-form {
  padding: 14px 16px 12px;
  border: 1px solid var(--erp-border-soft);
  border-radius: 8px;
  background: var(--erp-surface-soft);
}

.dimension-row,
.weight-row {
  margin-top: 4px;
  padding: 10px;
  border-radius: 6px;
  background: var(--erp-surface);
}

.weight-row {
  margin-top: 10px;
}

.material-summary {
  margin-top: 10px;
  color: var(--erp-danger);
  font-weight: 700;
  text-align: right;
}

.process-title {
  margin-top: 18px;
}

.process-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 18px;
}

.process-title-row .process-title {
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

.process-row-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.quote-mode-form {
  margin-top: 12px;
}

.assembly-box {
  margin-top: 14px;
}

.assembly-tip {
  margin-bottom: 12px;
}

.assembly-actions {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 10px;
}

.assembly-row {
  margin-bottom: 10px;
  align-items: center;
  padding: 10px;
  border: 1px solid var(--erp-border-soft);
  border-radius: 8px;
  background: var(--erp-surface-soft);
}

.assembly-child-info {
  min-width: 0;
}

.assembly-child-title {
  color: var(--erp-text);
  font-weight: 700;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.assembly-child-desc {
  margin-top: 2px;
  color: var(--erp-text-secondary);
  font-size: 12px;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.assembly-line-price {
  min-height: 32px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  color: var(--erp-text);
  font-weight: 700;
}

.assembly-fee-form {
  margin-top: 12px;
}

.assembly-summary {
  margin-top: 12px;
  padding: 12px 14px;
  border-radius: 8px;
  background: #f5f7fb;
  color: var(--erp-danger);
  font-weight: 700;
  text-align: right;
}

.assembly-picker-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 12px;
}

.assembly-picker-title {
  color: var(--erp-text);
  font-size: 15px;
  font-weight: 700;
  line-height: 1.3;
}

.assembly-picker-subtitle {
  margin-top: 4px;
  color: var(--erp-text-secondary);
  font-size: 13px;
}

.picker-drawing-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.picker-drawing-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

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
  border: 1px solid var(--erp-border-soft);
  border-radius: 8px;
  background: var(--erp-surface);
  overflow: hidden;
}

.drawing-pane-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--erp-border-soft);
  background: var(--erp-surface-soft);
}

.drawing-pane-title {
  color: var(--erp-text);
  font-weight: 700;
  line-height: 1.3;
}

.drawing-pane-subtitle {
  color: var(--erp-text-secondary);
  font-size: 12px;
  line-height: 1.4;
}

.drawing-frame {
  flex: 1;
  min-height: 0;
  background: var(--erp-surface-soft);
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

.dialog-header-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--erp-border-soft);
}

.current-item-alert {
  flex: 1;
}

.dialog-drawing-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.pdf-frame-shell {
  width: 100%;
  height: 70vh;
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
