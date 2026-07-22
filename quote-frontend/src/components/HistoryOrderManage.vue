<template>
  <div class="erp-page history-page">
    <header class="erp-page-header">
      <div>
        <h1 class="erp-page-title">历史订单管理</h1>
        <p class="erp-page-description">先选择客户，再查看该客户下的历史订单；彻底删除后不可恢复。</p>
      </div>
    </header>

    <section class="erp-toolbar">
      <div class="erp-toolbar-group history-filter-group">
        <el-button v-permission="'market:history-order:customer-select'" class="customer-picker-trigger" @click="customerPickerVisible = true">
          <span
            :class="selectedCustomer ? 'customer-picker-text' : 'customer-picker-placeholder'"
            :title="selectedCustomer || '先选择客户'"
          >
            {{ selectedCustomer || '先选择客户' }}
          </span>
          <span class="customer-picker-action">选择客户</span>
        </el-button>
        <el-button v-if="selectedCustomer" plain @click="handleCustomerClear">清除客户</el-button>
        <el-input
          v-model="keyword"
          clearable
          :disabled="!selectedCustomer"
          placeholder="搜索 RFQ 号 / 状态"
          class="search-input"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-button :disabled="!selectedCustomer" @click="handleSearch">搜索</el-button>
        <el-button type="primary" :loading="loading" :disabled="!selectedCustomer" @click="refreshOrders">刷新</el-button>
      </div>
    </section>

    <section class="erp-table-surface history-table-surface">
      <div v-if="!selectedCustomer" class="erp-empty-state history-empty-state">
        <el-empty description="请先选择客户查看历史订单" />
      </div>

      <template v-else>
        <div class="erp-table-body">
          <el-table :data="orders" border stripe height="600" v-loading="loading">
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
                <div class="erp-table-actions history-row-actions">
                  <el-button size="small" @click="openDetail(row)">查看订单</el-button>
                  <el-button v-permission="'market:order:delete'"
                    v-if="canDeleteHistoryOrders"
                    size="small"
                    type="danger"
                    plain
                    class="erp-danger-action"
                    @click="deleteHistoryOrder(row)"
                  >
                    彻底删除
                  </el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <div class="erp-pagination">
          <el-pagination
            v-model:current-page="page"
            v-model:page-size="pageSize"
            :page-sizes="[20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            :pager-count="7"
            :total="total"
            background
          />
        </div>
      </template>
    </section>

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
          title="彻底删除只会删除订单、零件、提单、工艺表和图纸文件。适合订单完成很久后清理历史记录。"
          type="warning"
          show-icon
          :closable="false"
        />

        <el-tabs v-model="activeDetailTab" class="detail-tabs" @tab-change="handleDetailTabChange">
          <el-tab-pane label="零件明细" name="items">
            <el-table :data="detailItemList" border stripe height="360" v-loading="detailItemsLoading">
              <el-table-column prop="partNo" label="图号" min-width="140" fixed show-overflow-tooltip />
              <el-table-column prop="version" label="版本" width="80" align="center" />
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
            <div class="erp-pagination detail-pagination">
              <el-pagination
                v-model:current-page="detailItemPagination.page"
                v-model:page-size="detailItemPagination.pageSize"
                :page-sizes="[20, 50, 100]"
                layout="total, sizes, prev, pager, next, jumper"
                :total="detailItemTotal"
                background
                :pager-count="7"
              />
            </div>
          </el-tab-pane>

          <el-tab-pane label="提单行" name="tickets">
            <el-table :data="detailTicketList" border stripe height="320" v-loading="detailTicketsLoading">
              <el-table-column prop="internalProjectNo" label="内部项目号" min-width="130" show-overflow-tooltip />
              <el-table-column prop="partNo" label="图号" min-width="140" show-overflow-tooltip />
              <el-table-column prop="version" label="版本" width="80" align="center" />
              <el-table-column prop="qty" label="数量" width="70" align="center" />
              <el-table-column prop="arrangeType" label="安排类型" width="110" />
              <el-table-column prop="startDate" label="开工日期" width="120" />
              <el-table-column prop="deliveryDate" label="交货日期" width="120" />
              <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
            </el-table>
            <div class="erp-pagination detail-pagination">
              <el-pagination
                v-model:current-page="detailTicketPagination.page"
                v-model:page-size="detailTicketPagination.pageSize"
                :page-sizes="[20, 50, 100]"
                layout="total, sizes, prev, pager, next, jumper"
                :total="detailTicketTotal"
                background
                :pager-count="7"
              />
            </div>
          </el-tab-pane>

          <el-tab-pane label="工艺表行" name="processSheets">
            <el-table :data="detailProcessSheetList" border stripe height="320" v-loading="detailProcessSheetsLoading">
              <el-table-column prop="internalProjectNo" label="内部项目号" min-width="130" show-overflow-tooltip />
              <el-table-column prop="partNo" label="图号" min-width="140" show-overflow-tooltip />
              <el-table-column prop="version" label="版本" width="80" align="center" />
              <el-table-column prop="description" label="物料描述" min-width="180" show-overflow-tooltip />
              <el-table-column prop="qty" label="数量" width="70" align="center" />
              <el-table-column prop="deliveryDate" label="交期" width="120" />
              <el-table-column label="工艺路线" min-width="260" show-overflow-tooltip>
                <template #default="{ row }">{{ formatRoute(row.processRoute) }}</template>
              </el-table-column>
            </el-table>
            <div class="erp-pagination detail-pagination">
              <el-pagination
                v-model:current-page="detailProcessSheetPagination.page"
                v-model:page-size="detailProcessSheetPagination.pageSize"
                :page-sizes="[20, 50, 100]"
                layout="total, sizes, prev, pager, next, jumper"
                :total="detailProcessSheetTotal"
                background
                :pager-count="7"
              />
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-drawer>

    <CustomerPickerDialog
      v-model="customerPickerVisible"
      :current-customer="selectedCustomer"
      customer-scope="history-order"
      @select="handleCustomerSelected"
    />
  </div>
</template>
<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import CustomerPickerDialog from './CustomerPickerDialog.vue'
import { useAuthStore } from '../stores/authStore'
import { deleteOrder, getOrder, listOrderItems, listOrderProcessSheets, listOrderTickets, listOrders } from '../services/orderApi'

const authStore = useAuthStore()
const orders = ref([])
const selectedCustomer = ref('')
const keyword = ref('')
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const orderDetail = ref(null)
const loading = ref(false)
const detailLoading = ref(false)
const detailVisible = ref(false)
const activeDetailTab = ref('items')
const detailItemList = ref([])
const detailTicketList = ref([])
const detailProcessSheetList = ref([])
const detailItemsLoading = ref(false)
const detailTicketsLoading = ref(false)
const detailProcessSheetsLoading = ref(false)
const detailItemPagination = reactive({ page: 1, pageSize: 20 })
const detailTicketPagination = reactive({ page: 1, pageSize: 20 })
const detailProcessSheetPagination = reactive({ page: 1, pageSize: 20 })
const detailItemTotal = ref(0)
const detailTicketTotal = ref(0)
const detailProcessSheetTotal = ref(0)
const customerPickerVisible = ref(false)
const canDeleteHistoryOrders = computed(() => authStore.hasPermission('market:order:delete'))

watch([page, pageSize], () => {
  if (selectedCustomer.value) {
    loadOrders()
  }
})

watch(() => [detailItemPagination.page, detailItemPagination.pageSize], () => {
  if (detailVisible.value && activeDetailTab.value === 'items') {
    fetchDetailItems()
  }
})

watch(() => [detailTicketPagination.page, detailTicketPagination.pageSize], () => {
  if (detailVisible.value && activeDetailTab.value === 'tickets') {
    fetchDetailTickets()
  }
})

watch(() => [detailProcessSheetPagination.page, detailProcessSheetPagination.pageSize], () => {
  if (detailVisible.value && activeDetailTab.value === 'processSheets') {
    fetchDetailProcessSheets()
  }
})

const loadOrders = async () => {
  if (!selectedCustomer.value) {
    orders.value = []
    return
  }
  loading.value = true
  try {
    const response = await listOrders({
      customerName: selectedCustomer.value,
      keyword: keyword.value.trim() || undefined,
      page: page.value,
      pageSize: pageSize.value
    })
    orders.value = response.records || []
    total.value = response.total || 0
  } catch (error) {
    console.error(error)
    ElMessage.error('历史订单列表加载失败。')
  } finally {
    loading.value = false
  }
}

const refreshOrders = async () => {
  await loadOrders()
}

const handleSearch = async () => {
  page.value = 1
  await loadOrders()
}

const handleCustomerChange = async () => {
  keyword.value = ''
  page.value = 1
  orderDetail.value = null
  detailVisible.value = false
  await loadOrders()
}

const handleCustomerSelected = async (customer) => {
  selectedCustomer.value = customer
  await handleCustomerChange()
}

const handleCustomerClear = () => {
  keyword.value = ''
  orders.value = []
  total.value = 0
  page.value = 1
  orderDetail.value = null
  detailVisible.value = false
}

const resetDetailPagination = () => {
  activeDetailTab.value = 'items'
  detailItemPagination.page = 1
  detailTicketPagination.page = 1
  detailProcessSheetPagination.page = 1
  detailItemList.value = []
  detailTicketList.value = []
  detailProcessSheetList.value = []
  detailItemTotal.value = 0
  detailTicketTotal.value = 0
  detailProcessSheetTotal.value = 0
}

const openDetail = async (row) => {
  detailVisible.value = true
  detailLoading.value = true
  resetDetailPagination()
  try {
    orderDetail.value = await getOrder(row.id)
    await fetchCurrentDetailTab()
  } catch (error) {
    console.error(error)
    ElMessage.error('历史订单详情加载失败。')
  } finally {
    detailLoading.value = false
  }
}

const fetchDetailItems = async () => {
  const rfqId = orderDetail.value?.orderInfo?.id
  if (!rfqId) return
  detailItemsLoading.value = true
  try {
    const response = await listOrderItems(rfqId, {
      page: detailItemPagination.page,
      pageSize: detailItemPagination.pageSize
    })
    detailItemList.value = response.records || []
    detailItemTotal.value = Number(response.total ?? 0)
  } catch (error) {
    console.error(error)
    ElMessage.error('历史订单零件明细加载失败。')
  } finally {
    detailItemsLoading.value = false
  }
}

const fetchDetailTickets = async () => {
  const rfqId = orderDetail.value?.orderInfo?.id
  if (!rfqId) return
  detailTicketsLoading.value = true
  try {
    const response = await listOrderTickets(rfqId, {
      page: detailTicketPagination.page,
      pageSize: detailTicketPagination.pageSize
    })
    detailTicketList.value = response.records || []
    detailTicketTotal.value = Number(response.total ?? 0)
  } catch (error) {
    console.error(error)
    ElMessage.error('历史订单提单行加载失败。')
  } finally {
    detailTicketsLoading.value = false
  }
}

const fetchDetailProcessSheets = async () => {
  const rfqId = orderDetail.value?.orderInfo?.id
  if (!rfqId) return
  detailProcessSheetsLoading.value = true
  try {
    const response = await listOrderProcessSheets(rfqId, {
      page: detailProcessSheetPagination.page,
      pageSize: detailProcessSheetPagination.pageSize
    })
    detailProcessSheetList.value = response.records || []
    detailProcessSheetTotal.value = Number(response.total ?? 0)
  } catch (error) {
    console.error(error)
    ElMessage.error('历史订单工艺表行加载失败。')
  } finally {
    detailProcessSheetsLoading.value = false
  }
}

const fetchCurrentDetailTab = async () => {
  if (activeDetailTab.value === 'tickets') {
    await fetchDetailTickets()
  } else if (activeDetailTab.value === 'processSheets') {
    await fetchDetailProcessSheets()
  } else {
    await fetchDetailItems()
  }
}

const handleDetailTabChange = async () => {
  await fetchCurrentDetailTab()
}

const deleteHistoryOrder = async (row) => {
  await ElMessageBox.confirm(
    `确认彻底删除历史订单「${row.rfqNo || row.id} / ${row.customerName || '-'}」？订单数据和图纸文件将被删除。`,
    '彻底删除历史订单',
    { type: 'error', confirmButtonText: '彻底删除', cancelButtonText: '取消' }
  )

  loading.value = true
  try {
    const response = await deleteOrder(row.id, { deleteFiles: true })
    ElMessage.success(response.message || '历史订单已删除')
    if (orderDetail.value?.orderInfo?.id === row.id) {
      orderDetail.value = null
      detailVisible.value = false
    }
    await loadOrders()
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
</script>

<style scoped>
.history-filter-group {
  width: 100%;
  justify-content: flex-end;
}

.customer-picker-trigger {
  justify-content: space-between;
  width: 220px;
  flex: 0 0 220px;
  min-height: 32px;
}

.customer-picker-text,
.customer-picker-placeholder {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.customer-picker-placeholder {
  color: var(--erp-text-secondary);
}

.customer-picker-action {
  flex: none;
  color: var(--erp-primary);
  font-size: 12px;
  font-weight: 700;
}

.search-input {
  width: 260px;
  flex: 0 0 260px;
}

.history-table-surface .erp-table-body {
  padding: var(--erp-space-3);
}

.history-empty-state {
  margin: var(--erp-space-4);
}

.history-row-actions {
  width: 100%;
}

.detail-wrap {
  padding-right: 4px;
}

.detail-descriptions :deep(.el-descriptions__label) {
  color: var(--erp-text-secondary);
  background: var(--erp-surface-soft);
}

.detail-tabs {
  margin-top: 16px;
}

.delete-alert {
  margin: 14px 0;
}

@media (max-width: 1200px) {
  .history-filter-group {
    justify-content: flex-start;
  }
}

@media (max-width: 760px) {
  .customer-picker-trigger,
  .search-input {
    width: 100%;
  }
}
</style>
