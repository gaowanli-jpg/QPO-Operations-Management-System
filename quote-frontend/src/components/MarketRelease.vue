<template>
  <div class="release-container">
    <el-card shadow="never" class="release-card">
      <template #header>
        <div class="card-header">
          <div>
            <h2>市场部：议价确认与提单生成</h2>
            <p>选择客户最终通过的零件，系统会按待产库存、已入库库存、净生产需求自动拆分提单行。</p>
          </div>
          <el-button type="primary" :loading="loading" @click="loadOrders">刷新订单</el-button>
        </div>
      </template>

      <el-row :gutter="20">
        <el-col :span="7">
          <div class="panel">
            <div class="panel-title">询价单</div>
            <div class="order-picker-row">
              <div class="selected-order-text">
                {{ orderInfo.id ? `${orderInfo.rfqNo} / ${orderInfo.customerName || '-'} / ${orderInfo.projectName || '-'}` : '未选择订单' }}
              </div>
              <el-button type="primary" @click="openOrderPicker" :loading="loading">选择订单</el-button>
            </div>

            <el-descriptions v-if="orderInfo.id" :column="1" border class="order-desc">
              <el-descriptions-item label="客户">{{ orderInfo.customerName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="项目">{{ orderInfo.projectName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="状态">{{ orderInfo.status || '-' }}</el-descriptions-item>
            </el-descriptions>

            <el-form label-width="80px" class="ticket-form">
              <el-form-item label="项目号">
                <el-input v-model="releaseForm.projectNo" placeholder="默认取项目名称" />
              </el-form-item>
              <el-form-item label="开工日期">
                <el-date-picker
                  v-model="releaseForm.startDate"
                  type="date"
                  value-format="YYYY-MM-DD"
                  style="width: 100%;"
                />
              </el-form-item>
              <el-form-item label="安排类型">
                <el-select v-model="releaseForm.arrangeType" style="width: 100%;">
                  <el-option label="计划" value="计划" />
                  <el-option label="加急" value="加急" />
                  <el-option label="备货" value="备货" />
                </el-select>
              </el-form-item>
            </el-form>

            <div class="main-actions">
              <el-button
                type="success"
                size="large"
                :loading="submitting"
                :disabled="selectedRows.length === 0"
                @click="submitTicket"
              >
                生成正式提单
              </el-button>
              <el-button
                type="primary"
                plain
                size="large"
                :disabled="!selectedRfqId"
                @click="exportExcel('quote')"
              >
                导出报价单 Excel
              </el-button>
            </div>
          </div>
        </el-col>

        <el-col :span="17">
          <div class="panel">
            <div class="panel-title">通过零件选择</div>
            <el-table
              ref="itemTableRef"
              :data="itemList"
              border
              stripe
              height="360"
              v-loading="loading"
              @selection-change="handleSelectionChange"
            >
              <el-table-column type="selection" width="45" :selectable="canSelectItem" />
              <el-table-column prop="partNo" label="物品编号" width="130" fixed />
              <el-table-column prop="description" label="物料描述" min-width="160" show-overflow-tooltip />
              <el-table-column prop="qty" label="询价数量" width="90" align="center" />
              <el-table-column label="通过数量" width="130" align="center">
                <template #default="scope">
                  <el-input-number
                    v-model="acceptedQtyMap[scope.row.id]"
                    :min="1"
                    :max="scope.row.qty"
                    :disabled="!canSelectItem(scope.row)"
                    controls-position="right"
                    style="width: 105px;"
                  />
                </template>
              </el-table-column>
              <el-table-column prop="materialName" label="材料" width="110" show-overflow-tooltip />
              <el-table-column prop="surfaceTreatment" label="表面处理" width="110" show-overflow-tooltip />
              <el-table-column label="含税单价" width="110" align="right">
                <template #default="scope">
                  <span v-if="scope.row.finalUnitPrice != null">¥ {{ scope.row.finalUnitPrice.toFixed(2) }}</span>
                  <el-tag v-else type="warning" size="small">未报价</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="status" label="状态" width="110" align="center" />
            </el-table>
          </div>

          <div class="panel ticket-panel">
            <div class="panel-title-row">
              <div class="panel-title">已生成提单行</div>
              <el-button size="small" type="primary" plain :disabled="ticketList.length === 0" @click="exportExcel('tickets')">
                导出提单 Excel
              </el-button>
            </div>
            <el-table :data="ticketList" border stripe height="280" v-loading="loadingTickets || deleting">
              <el-table-column prop="partNo" label="物品编号" width="130" />
              <el-table-column prop="qty" label="数量" width="70" align="center" />
              <el-table-column prop="materialName" label="材料" width="110" show-overflow-tooltip />
              <el-table-column prop="surfaceTreatment" label="表面处理" width="110" show-overflow-tooltip />
              <el-table-column prop="startDate" label="开工日期" width="110" />
              <el-table-column prop="deliveryDate" label="交货日期" width="110" />
              <el-table-column prop="arrangeType" label="安排类型" width="100" />
              <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
              <el-table-column label="操作" width="90" align="center" fixed="right">
                <template #default="scope">
                  <el-button type="danger" size="small" plain @click="deleteTicket(scope.row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <div class="panel ticket-panel">
            <div class="panel-title-row">
              <div class="panel-title">已生成工艺表行</div>
              <el-button size="small" type="primary" plain :disabled="processSheetList.length === 0" @click="exportExcel('process')">
                导出工艺表 Excel
              </el-button>
            </div>
            <el-table :data="processSheetList" border stripe height="260" v-loading="loadingProcessSheets || deleting">
              <el-table-column prop="partNo" label="物品编号" width="130" />
              <el-table-column prop="description" label="材料描述" min-width="160" show-overflow-tooltip />
              <el-table-column prop="qty" label="数量" width="70" align="center" />
              <el-table-column prop="materialName" label="材料" width="120" show-overflow-tooltip />
              <el-table-column prop="surfaceTreatment" label="表面处理" width="130" show-overflow-tooltip />
              <el-table-column prop="deliveryDate" label="交期" width="110" />
              <el-table-column label="工艺路线" min-width="220" show-overflow-tooltip>
                <template #default="scope">
                  {{ formatRoute(scope.row.processRoute) }}
                </template>
              </el-table-column>
              <el-table-column label="操作" width="90" align="center" fixed="right">
                <template #default="scope">
                  <el-button type="danger" size="small" plain @click="deleteProcessSheet(scope.row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <OrderPickerDialog
      v-model="orderPickerVisible"
      title="选择市场部议价订单"
      :orders="orders"
      @select="handleOrderSelected"
    />
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import OrderPickerDialog from './OrderPickerDialog.vue'
import { API_BASE_URL } from '../config'

const orders = ref([])
const selectedRfqId = ref(null)
const orderPickerVisible = ref(false)
const orderInfo = ref({})
const itemList = ref([])
const ticketList = ref([])
const processSheetList = ref([])
const selectedRows = ref([])
const itemTableRef = ref(null)
const acceptedQtyMap = reactive({})

const loading = ref(false)
const loadingTickets = ref(false)
const loadingProcessSheets = ref(false)
const submitting = ref(false)
const deleting = ref(false)

const today = new Date().toISOString().slice(0, 10)
const releaseForm = reactive({
  projectNo: '',
  startDate: today,
  arrangeType: '计划'
})

const loadOrders = async () => {
  loading.value = true
  try {
    const response = await axios.get(`${API_BASE_URL}/api/market/orders`)
    orders.value = response.data || []
  } catch (error) {
    console.error(error)
    ElMessage.error('订单列表加载失败，请检查后端服务。')
  } finally {
    loading.value = false
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
  await loadOrder(order.id)
}

const loadOrder = async (rfqId) => {
  if (!rfqId) return
  loading.value = true
  try {
    const response = await axios.get(`${API_BASE_URL}/api/market/summary/${rfqId}`)
    orderInfo.value = response.data.orderInfo || {}
    itemList.value = response.data.itemList || []
    releaseForm.projectNo = orderInfo.value.projectName || ''
    selectedRows.value = []
    itemList.value.forEach(item => {
      acceptedQtyMap[item.id] = item.qty || 1
    })
    await loadTickets(rfqId)
    await loadProcessSheets(rfqId)
  } catch (error) {
    console.error(error)
    ElMessage.error('询价单明细加载失败。')
  } finally {
    loading.value = false
  }
}

const loadTickets = async (rfqId) => {
  loadingTickets.value = true
  try {
    const response = await axios.get(`${API_BASE_URL}/api/market/tickets/${rfqId}`)
    ticketList.value = response.data || []
  } catch (error) {
    console.error(error)
    ElMessage.error('提单列表加载失败。')
  } finally {
    loadingTickets.value = false
  }
}

const loadProcessSheets = async (rfqId) => {
  loadingProcessSheets.value = true
  try {
    const response = await axios.get(`${API_BASE_URL}/api/market/process-sheets/${rfqId}`)
    processSheetList.value = response.data || []
  } catch (error) {
    console.error(error)
    ElMessage.error('工艺表列表加载失败。')
  } finally {
    loadingProcessSheets.value = false
  }
}

const canSelectItem = (row) => {
  return row.status !== '已生成提单' && row.status !== '已部分提单'
}

const handleSelectionChange = (rows) => {
  selectedRows.value = rows
}

const submitTicket = async () => {
  if (!selectedRfqId.value) return ElMessage.warning('请先选择询价单。')
  if (selectedRows.value.length === 0) return ElMessage.warning('请至少选择一个通过零件。')

  const unquoted = selectedRows.value.filter(row => row.finalUnitPrice == null)
  if (unquoted.length > 0) {
    return ElMessage.warning(`仍有 ${unquoted.length} 个零件未报价，暂不生成提单。`)
  }

  await ElMessageBox.confirm(
    `确认生成 ${selectedRows.value.length} 个零件的正式提单？生成后会扣减库存，不能重复生成。`,
    '生成提单确认',
    { type: 'warning', confirmButtonText: '确认生成', cancelButtonText: '取消' }
  )

  submitting.value = true
  try {
    const payload = {
      rfqId: selectedRfqId.value,
      projectNo: releaseForm.projectNo,
      startDate: releaseForm.startDate,
      arrangeType: releaseForm.arrangeType,
      items: selectedRows.value.map(row => ({
        itemId: row.id,
        acceptedQty: acceptedQtyMap[row.id] || row.qty
      }))
    }
    const response = await axios.post(`${API_BASE_URL}/api/market/generate-ticket`, payload)
    if (response.data.success) {
      ElMessage.success(response.data.message)
      await loadOrder(selectedRfqId.value)
    } else {
      ElMessage.error(response.data.message || '提单生成失败。')
    }
  } catch (error) {
    console.error(error)
    ElMessage.error('提单生成请求失败。')
  } finally {
    submitting.value = false
  }
}

const deleteTicket = async (row) => {
  await ElMessageBox.confirm(
    `确认删除提单行「${row.partNo}」数量 ${row.qty}？如果该行曾冲销库存，系统会恢复对应库存数量。`,
    '删除提单行',
    { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
  )

  deleting.value = true
  try {
    const response = await axios.delete(`${API_BASE_URL}/api/market/tickets/${row.id}`)
    ElMessage.success(response.data.message || '提单行已删除')
    await loadOrder(selectedRfqId.value)
  } catch (error) {
    console.error(error)
    ElMessage.error('提单行删除失败。')
  } finally {
    deleting.value = false
  }
}

const deleteProcessSheet = async (row) => {
  await ElMessageBox.confirm(
    `确认删除工艺表行「${row.partNo}」？删除后可基于新的核价重新生成。`,
    '删除工艺表行',
    { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
  )

  deleting.value = true
  try {
    const response = await axios.delete(`${API_BASE_URL}/api/market/process-sheets/${row.id}`)
    ElMessage.success(response.data.message || '工艺表行已删除')
    await loadOrder(selectedRfqId.value)
  } catch (error) {
    console.error(error)
    ElMessage.error('工艺表行删除失败。')
  } finally {
    deleting.value = false
  }
}

const formatRoute = (route) => {
  if (!route) return '-'
  let list = Array.isArray(route) ? route : []
  if (typeof route === 'string') {
    try {
      list = JSON.parse(route)
    } catch {
      list = []
    }
  }
  if (list.length === 0) return '-'
  return list
    .map(item => `${item.processName || ''}${item.inputVal ? `(${item.inputVal})` : ''}`)
    .filter(Boolean)
    .join(' / ')
}

const exportExcel = (type) => {
  if (!selectedRfqId.value) return ElMessage.warning('请先选择询价单。')
  const pathMap = {
    tickets: 'tickets',
    process: 'process-sheets',
    quote: 'quote'
  }
  const path = pathMap[type] || 'process-sheets'
  window.open(`${API_BASE_URL}/api/market/export/${path}/${selectedRfqId.value}`, '_blank')
}

onMounted(loadOrders)
</script>

<style scoped>
.release-container { padding: 20px; }
.release-card { border-radius: 8px; }
.card-header { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; }
.card-header h2 { margin: 0; color: #303133; font-size: 22px; }
.card-header p { margin: 8px 0 0; color: #606266; font-size: 14px; }
.panel { background: #fff; border: 1px solid #ebeef5; border-radius: 8px; padding: 16px; }
.panel-title { font-weight: 700; color: #303133; margin-bottom: 14px; }
.order-picker-row { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 10px; align-items: center; }
.selected-order-text { min-height: 32px; display: flex; align-items: center; color: #303133; border: 1px solid #dcdfe6; border-radius: 4px; padding: 0 10px; background: #f8f9fa; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.panel-title-row { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.panel-title-row .panel-title { margin-bottom: 0; }
.order-desc { margin-top: 16px; }
.ticket-form { margin-top: 18px; }
.main-actions { display: grid; gap: 10px; }
.main-actions :deep(.el-button) { width: 100%; margin-left: 0; }
.ticket-panel { margin-top: 16px; }
</style>
