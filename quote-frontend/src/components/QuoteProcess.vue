<template>
  <div class="quote-process-container">
    <el-card shadow="never" class="quote-card">
      <template #header>
        <div class="card-header">
          <div>
            <h2>市场部：报价单处理生成</h2>
          </div>
          <div class="header-actions">
            <el-button :icon="Refresh" type="primary" plain :loading="loadingOrders" @click="loadOrders">刷新订单</el-button>
            <el-button :icon="DocumentChecked" type="primary" @click="openOrderPicker" :loading="loadingOrders">选择订单</el-button>
          </div>
        </div>
      </template>

      <el-row :gutter="16">
        <el-col :span="6">
          <div class="panel">
            <div class="panel-title">RFQ 订单</div>
            <div class="selected-order-text">
              {{ orderInfo.id ? `${orderInfo.rfqNo || orderInfo.id} / ${orderInfo.customerName || '-'}` : '未选择订单' }}
            </div>
            <el-descriptions v-if="orderInfo.id" :column="1" border class="order-desc">
              <el-descriptions-item label="客户">{{ orderInfo.customerName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="RFQ号">{{ orderInfo.rfqNo || '-' }}</el-descriptions-item>
              <el-descriptions-item label="状态">
                <el-tag type="primary">{{ orderInfo.status || '-' }}</el-tag>
              </el-descriptions-item>
            </el-descriptions>

            <div class="batch-box">
              <div class="panel-title compact">交期调整</div>
              <el-input-number
                v-model="shiftDays"
                :min="0"
                :max="365"
                :precision="0"
                controls-position="right"
                class="days-input"
              />
              <div class="batch-actions">
                <el-button :icon="Plus" type="success" :disabled="selectedRows.length === 0" :loading="adjusting" @click="applyShift(1)">
                  增加
                </el-button>
                <el-button :icon="Minus" type="warning" plain :disabled="selectedRows.length === 0" :loading="adjusting" @click="applyShift(-1)">
                  减少
                </el-button>
              </div>
              <div class="selection-count">已选 {{ selectedRows.length }} 项</div>
            </div>

            <el-button
              :icon="Download"
              type="primary"
              size="large"
              class="download-btn"
              :disabled="!selectedRfqId"
              @click="exportQuote"
            >
              生成报价单
            </el-button>
          </div>
        </el-col>

        <el-col :span="18">
          <div class="panel">
            <div class="panel-title-row">
              <div class="panel-title">零件交期</div>
              <el-tag v-if="itemList.length" type="info">{{ itemList.length }} 项</el-tag>
            </div>
            <el-empty v-if="!selectedRfqId" description="请选择已报价订单" />
            <el-table
              v-else
              :data="itemList"
              border
              stripe
              height="640"
              v-loading="loadingItems"
              @selection-change="handleSelectionChange"
            >
              <el-table-column type="selection" width="46" fixed />
              <el-table-column prop="partNo" label="图号" width="140" fixed show-overflow-tooltip />
              <el-table-column prop="version" label="版本" width="72" align="center" />
              <el-table-column prop="description" label="物料描述" min-width="160" show-overflow-tooltip />
              <el-table-column prop="qty" label="数量" width="70" align="center" />
              <el-table-column label="含税单价" width="110" align="right">
                <template #default="{ row }">
                  <span v-if="row.finalUnitPrice != null">¥ {{ Number(row.finalUnitPrice).toFixed(2) }}</span>
                  <el-tag v-else type="warning" size="small">未报价</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="leadTime" label="客户询价交期" width="130" show-overflow-tooltip>
                <template #default="{ row }">{{ row.leadTime || '-' }}</template>
              </el-table-column>
              <el-table-column label="报价交期" width="180">
                <template #default="{ row }">
                  <el-input
                    v-model="row.quoteLeadTimeDraft"
                    placeholder="默认沿用客户交期"
                    clearable
                    @keyup.enter="saveLeadTime(row)"
                  />
                </template>
              </el-table-column>
              <el-table-column prop="effectiveLeadTime" label="报价单交期" width="120" show-overflow-tooltip>
                <template #default="{ row }">{{ row.effectiveLeadTime || '-' }}</template>
              </el-table-column>
              <el-table-column label="解析日期" width="110" align="center">
                <template #default="{ row }">
                  <span>{{ row.parsedDate || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="自动调整" width="96" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.canAutoShift ? 'success' : 'info'" size="small">
                    {{ row.canAutoShift ? '支持' : '手动' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="96" align="center" fixed="right">
                <template #default="{ row }">
                  <el-button
                    :icon="Check"
                    type="primary"
                    size="small"
                    plain
                    :loading="!!savingRowMap[row.id]"
                    @click="saveLeadTime(row)"
                  >
                    保存
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <OrderPickerDialog
      v-model="orderPickerVisible"
      title="选择已报价 RFQ 订单"
      :orders="orders"
      :allowed-statuses="['已报价']"
      @select="handleOrderSelected"
    />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { Check, DocumentChecked, Download, Minus, Plus, Refresh } from '@element-plus/icons-vue'
import OrderPickerDialog from './OrderPickerDialog.vue'
import { API_BASE_URL } from '../config'
import { downloadByAxios } from '../download'

const orders = ref([])
const selectedRfqId = ref(null)
const orderInfo = ref({})
const itemList = ref([])
const selectedRows = ref([])
const orderPickerVisible = ref(false)
const shiftDays = ref(0)
const savingRowMap = reactive({})

const loadingOrders = ref(false)
const loadingItems = ref(false)
const adjusting = ref(false)

const loadOrders = async () => {
  loadingOrders.value = true
  try {
    const response = await axios.get(`${API_BASE_URL}/api/market/orders`)
    orders.value = response.data || []
  } catch (error) {
    console.error(error)
    ElMessage.error('订单列表加载失败。')
  } finally {
    loadingOrders.value = false
  }
}

const openOrderPicker = async () => {
  if (orders.value.length === 0) {
    await loadOrders()
  }
  orderPickerVisible.value = true
}

const handleOrderSelected = async (order) => {
  selectedRfqId.value = order.id
  await loadQuoteProcess(order.id)
}

const loadQuoteProcess = async (rfqId) => {
  loadingItems.value = true
  try {
    const response = await axios.get(`${API_BASE_URL}/api/market/quote-process/${rfqId}`)
    orderInfo.value = response.data.orderInfo || {}
    itemList.value = normalizeRows(response.data.itemList || [])
    selectedRows.value = []
  } catch (error) {
    console.error(error)
    ElMessage.error(error.response?.data?.message || '报价单处理数据加载失败。')
  } finally {
    loadingItems.value = false
  }
}

const normalizeRows = (rows) => rows.map(row => ({
  ...row,
  quoteLeadTimeDraft: row.quoteLeadTime || ''
}))

const handleSelectionChange = (rows) => {
  selectedRows.value = rows
}

const applyShift = async (direction) => {
  if (!selectedRfqId.value) return ElMessage.warning('请先选择 RFQ 订单。')
  if (selectedRows.value.length === 0) return ElMessage.warning('请至少选择一个零件。')
  const days = Math.abs(Number(shiftDays.value || 0)) * direction
  if (!days) return ElMessage.warning('调整天数为 0，不需要调整。')

  adjusting.value = true
  try {
    const response = await axios.post(`${API_BASE_URL}/api/market/quote-process/lead-time/shift`, {
      rfqId: selectedRfqId.value,
      itemIds: selectedRows.value.map(row => row.id),
      days
    })
    itemList.value = normalizeRows(response.data.itemList || [])
    selectedRows.value = []
    const adjusted = response.data.adjustedCount || 0
    const skipped = response.data.skippedCount || 0
    if (skipped > 0) {
      ElMessage.warning(`已调整 ${adjusted} 项，${skipped} 项无法自动调整。`)
    } else {
      ElMessage.success(`已调整 ${adjusted} 项。`)
    }
  } catch (error) {
    console.error(error)
    ElMessage.error(error.response?.data?.message || '交期调整失败。')
  } finally {
    adjusting.value = false
  }
}

const saveLeadTime = async (row) => {
  if (!selectedRfqId.value || !row?.id) return
  savingRowMap[row.id] = true
  try {
    const response = await axios.post(`${API_BASE_URL}/api/market/quote-process/lead-time/update`, {
      rfqId: selectedRfqId.value,
      itemId: row.id,
      quoteLeadTime: row.quoteLeadTimeDraft
    })
    const saved = normalizeRows([response.data.data || {}])[0]
    const index = itemList.value.findIndex(item => item.id === row.id)
    if (index >= 0) {
      itemList.value.splice(index, 1, saved)
    }
    ElMessage.success('报价交期已保存。')
  } catch (error) {
    console.error(error)
    ElMessage.error(error.response?.data?.message || '报价交期保存失败。')
  } finally {
    savingRowMap[row.id] = false
  }
}

const exportQuote = async () => {
  if (!selectedRfqId.value) return ElMessage.warning('请先选择 RFQ 订单。')
  const fallbackName = `${orderInfo.value.rfqNo || `quote-${selectedRfqId.value}`}-报价单.xlsx`
  const result = await downloadByAxios(
    `${API_BASE_URL}/api/market/export/quote/${selectedRfqId.value}`,
    fallbackName
  )
  if (!result.success) {
    ElMessage.error(result.message || '报价单导出失败。')
  }
}

onMounted(loadOrders)
</script>

<style scoped>
.quote-process-container {
  padding: 0;
}

.quote-card {
  border: 1px solid var(--erp-border-soft);
  border-radius: 8px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.card-header h2 {
  margin: 0;
  color: var(--erp-text);
  font-size: 20px;
  line-height: 1.3;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.panel {
  background: var(--erp-surface);
  border: 1px solid var(--erp-border-soft);
  border-radius: 8px;
  padding: 14px;
}

.panel-title {
  margin-bottom: 12px;
  padding-left: 10px;
  border-left: 3px solid var(--erp-primary);
  color: var(--erp-text);
  font-size: 14px;
  font-weight: 700;
  line-height: 1.2;
}

.panel-title.compact {
  margin-bottom: 10px;
}

.panel-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.panel-title-row .panel-title {
  margin-bottom: 0;
}

.selected-order-text {
  min-height: 34px;
  display: flex;
  align-items: center;
  color: var(--erp-text);
  border: 1px solid var(--erp-border);
  border-radius: 6px;
  padding: 0 10px;
  background: var(--erp-surface-soft);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.order-desc {
  margin-top: 14px;
}

.batch-box {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--erp-border-soft);
}

.days-input {
  width: 100%;
}

.batch-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin-top: 10px;
}

.batch-actions :deep(.el-button) {
  margin-left: 0;
}

.selection-count {
  margin-top: 10px;
  color: var(--erp-text-secondary);
  font-size: 13px;
}

.download-btn {
  width: 100%;
  margin-top: 16px;
}

:deep(.el-descriptions__label) {
  width: 76px;
  color: var(--erp-text-secondary);
  background: #f5f7fb;
}

@media (max-width: 1180px) {
  .quote-process-container :deep(.el-row) {
    display: block;
  }

  .quote-process-container :deep(.el-col) {
    max-width: 100%;
    width: 100%;
    margin-bottom: 14px;
  }
}

@media (max-width: 760px) {
  .card-header {
    flex-direction: column;
    align-items: stretch;
  }

  .header-actions {
    justify-content: flex-start;
  }
}
</style>
