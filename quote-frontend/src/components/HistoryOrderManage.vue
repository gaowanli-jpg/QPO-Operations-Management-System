<template>
  <div class="history-container">
    <el-card shadow="never" class="history-card">
      <template #header>
        <div class="card-header">
          <div>
            <h2>历史订单管理</h2>
            <p>先选择客户，再查看该客户下的历史订单；彻底删除不会恢复库存。</p>
          </div>
          <div class="header-actions">
            <el-select
              v-model="selectedCustomer"
              filterable
              clearable
              placeholder="先选择客户"
              class="customer-select"
              @change="handleCustomerChange"
              @clear="handleCustomerClear"
            >
              <el-option
                v-for="customer in customers"
                :key="customer"
                :label="customer"
                :value="customer"
              />
            </el-select>
            <el-input
              v-model="keyword"
              clearable
              :disabled="!selectedCustomer"
              placeholder="搜索 RFQ 号 / 状态"
              class="search-input"
            />
            <el-button type="primary" :loading="loading" @click="refreshOrders">刷新</el-button>
          </div>
        </div>
      </template>

      <el-empty v-if="!selectedCustomer" description="请先选择客户查看历史订单" />

      <el-table v-else :data="filteredOrders" border stripe height="640" v-loading="loading">
        <el-table-column prop="rfqNo" label="订单号" min-width="170" fixed show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag type="info" size="small">{{ row.status || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="零件" width="90" align="center">
          <template #default="{ row }">{{ row.itemCount || 0 }}</template>
        </el-table-column>
        <el-table-column label="已报价" width="90" align="center">
          <template #default="{ row }">{{ row.quotedCount || 0 }}</template>
        </el-table-column>
        <el-table-column label="缺图" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.missingDrawingCount > 0 ? 'warning' : 'success'" size="small">
              {{ row.missingDrawingCount || 0 }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="提单行" width="90" align="center">
          <template #default="{ row }">{{ row.ticketCount || 0 }}</template>
        </el-table-column>
        <el-table-column label="工艺表行" width="100" align="center">
          <template #default="{ row }">{{ row.processSheetCount || 0 }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="210" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openDetail(row)">查看订单</el-button>
            <el-button v-if="canDeleteHistoryOrders" size="small" type="danger" plain @click="deleteHistoryOrder(row)">彻底删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-drawer v-model="detailVisible" title="历史订单详情" size="72%">
      <div v-if="orderDetail" v-loading="detailLoading" class="detail-wrap">
        <el-descriptions :column="2" border class="detail-descriptions">
          <el-descriptions-item label="订单号">{{ orderDetail.orderInfo?.rfqNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ orderDetail.orderInfo?.status || '-' }}</el-descriptions-item>
          <el-descriptions-item label="客户">{{ orderDetail.orderInfo?.customerName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="图纸目录" :span="2">{{ orderDetail.fileRoot || '-' }}</el-descriptions-item>
        </el-descriptions>

        <el-alert
          class="delete-alert"
          title="彻底删除不会恢复库存，只会删除订单、零件、提单、工艺表和图纸文件。适合订单完成很久后清理历史记录。"
          type="warning"
          show-icon
          :closable="false"
        />

        <el-tabs class="detail-tabs">
          <el-tab-pane label="零件明细">
            <el-table :data="orderDetail.itemList || []" border stripe height="360">
              <el-table-column prop="partNo" label="物品编号" min-width="140" fixed />
              <el-table-column prop="description" label="物料描述" min-width="180" show-overflow-tooltip />
              <el-table-column prop="qty" label="数量" width="70" align="center" />
              <el-table-column prop="status" label="状态" width="110" />
              <el-table-column label="含税单价" width="120" align="right">
                <template #default="{ row }">
                  {{ row.finalUnitPrice ? `¥ ${row.finalUnitPrice.toFixed(2)}` : '-' }}
                </template>
              </el-table-column>
              <el-table-column label="图纸" width="90" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.drawingUrl ? 'success' : 'warning'" size="small">
                    {{ row.drawingUrl ? '已绑定' : '缺图' }}
                  </el-tag>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="提单行">
            <el-table :data="orderDetail.ticketList || []" border stripe height="320">
              <el-table-column prop="partNo" label="物品编号" min-width="140" />
              <el-table-column prop="qty" label="数量" width="70" align="center" />
              <el-table-column prop="arrangeType" label="安排类型" width="110" />
              <el-table-column prop="startDate" label="开工日期" width="120" />
              <el-table-column prop="deliveryDate" label="交货日期" width="120" />
              <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="工艺表行">
            <el-table :data="orderDetail.processSheetList || []" border stripe height="320">
              <el-table-column prop="partNo" label="物品编号" min-width="140" />
              <el-table-column prop="description" label="物料描述" min-width="180" show-overflow-tooltip />
              <el-table-column prop="qty" label="数量" width="70" align="center" />
              <el-table-column prop="deliveryDate" label="交期" width="120" />
              <el-table-column label="工艺路线" min-width="260" show-overflow-tooltip>
                <template #default="{ row }">{{ formatRoute(row.processRoute) }}</template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { hasRole } from '../auth'
import { API_BASE_URL } from '../config'

const customers = ref([])
const orders = ref([])
const selectedCustomer = ref('')
const keyword = ref('')
const orderDetail = ref(null)
const loading = ref(false)
const detailLoading = ref(false)
const detailVisible = ref(false)
const canDeleteHistoryOrders = computed(() => hasRole(['MARKET', 'ADMIN']))

const filteredOrders = computed(() => {
  const text = keyword.value.trim().toLowerCase()
  if (!text) return orders.value
  return orders.value.filter(order => [
    order.rfqNo,
    order.status
  ].some(value => String(value || '').toLowerCase().includes(text)))
})

const loadCustomers = async () => {
  try {
    const response = await axios.get(`${API_BASE_URL}/api/order-management/customers`)
    customers.value = response.data || []
  } catch (error) {
    console.error(error)
    ElMessage.error('客户列表加载失败。')
  }
}

const loadOrders = async () => {
  if (!selectedCustomer.value) {
    orders.value = []
    return
  }
  loading.value = true
  try {
    const response = await axios.get(`${API_BASE_URL}/api/order-management/orders`, {
      params: { customerName: selectedCustomer.value }
    })
    orders.value = response.data || []
  } catch (error) {
    console.error(error)
    ElMessage.error('历史订单列表加载失败。')
  } finally {
    loading.value = false
  }
}

const refreshOrders = async () => {
  await loadCustomers()
  await loadOrders()
}

const handleCustomerChange = async () => {
  keyword.value = ''
  orderDetail.value = null
  detailVisible.value = false
  await loadOrders()
}

const handleCustomerClear = () => {
  keyword.value = ''
  orders.value = []
  orderDetail.value = null
  detailVisible.value = false
}

const openDetail = async (row) => {
  detailVisible.value = true
  detailLoading.value = true
  try {
    const response = await axios.get(`${API_BASE_URL}/api/order-management/orders/${row.id}`)
    orderDetail.value = response.data
  } catch (error) {
    console.error(error)
    ElMessage.error('历史订单详情加载失败。')
  } finally {
    detailLoading.value = false
  }
}

const deleteHistoryOrder = async (row) => {
  await ElMessageBox.confirm(
    `确认彻底删除历史订单「${row.rfqNo || row.id} / ${row.customerName || '-'}」？库存不会恢复，订单数据和图纸文件将被删除。`,
    '彻底删除历史订单',
    { type: 'error', confirmButtonText: '彻底删除', cancelButtonText: '取消' }
  )

  loading.value = true
  try {
    const response = await axios.delete(`${API_BASE_URL}/api/order-management/orders/${row.id}?deleteFiles=true`)
    ElMessage.success(response.data.message || '历史订单已删除')
    if (orderDetail.value?.orderInfo?.id === row.id) {
      orderDetail.value = null
      detailVisible.value = false
    }
    await loadOrders()
    await loadCustomers()
  } catch (error) {
    console.error(error)
    ElMessage.error(error.response?.data?.message || '历史订单删除失败。')
  } finally {
    loading.value = false
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

onMounted(loadCustomers)
</script>

<style scoped>
.history-container {
  padding: 0;
}

.history-card {
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

.card-header p {
  margin: 6px 0 0;
  color: var(--erp-text-secondary);
  font-size: 13px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.customer-select {
  width: 220px;
}

.search-input {
  width: 260px;
}

.detail-wrap {
  padding-right: 4px;
}

.detail-descriptions :deep(.el-descriptions__label) {
  color: var(--erp-text-secondary);
  background: #f5f7fb;
}

.detail-tabs {
  margin-top: 16px;
}

.delete-alert {
  margin: 14px 0;
}

@media (max-width: 1200px) {
  .card-header {
    flex-direction: column;
    align-items: stretch;
  }

  .header-actions {
    justify-content: flex-start;
  }
}

@media (max-width: 760px) {
  .customer-select,
  .search-input {
    width: 100%;
  }
}
</style>
