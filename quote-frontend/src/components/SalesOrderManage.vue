<template>
  <div class="erp-page sales-order-manage-page">
    <header class="erp-page-header">
      <div>
        <h1 class="erp-page-title">销售订单</h1>
        <p class="erp-page-description">先选择客户，再查看该客户下的销售订单、零件、提单行、工艺表行和图纸绑定状态。</p>
      </div>
    </header>

    <section class="erp-toolbar">
      <div class="erp-toolbar-group sales-order-filter-group">
        <el-button v-permission="'market:sales-order:customer-select'" class="customer-picker-trigger" @click="customerPickerVisible = true">
          <span class="customer-picker-content">
            <span
              :class="selectedCustomer ? 'customer-picker-text' : 'customer-picker-placeholder'"
              :title="selectedCustomer || '先选择客户'"
            >
              {{ selectedCustomer || '先选择客户' }}
            </span>
            <span class="customer-picker-action">选择客户</span>
          </span>
        </el-button>
        <el-button v-if="selectedCustomer" plain @click="handleCustomerClear">清除客户</el-button>
        <el-select
          v-model="statusFilter"
          clearable
          :disabled="!selectedCustomer"
          placeholder="全部状态"
          class="status-filter"
          @change="handleSearch"
          @clear="handleSearch"
        >
          <el-option label="全部状态" value="" />
          <el-option v-for="status in SALES_ORDER_STATUSES" :key="status" :label="status" :value="status" />
        </el-select>
        <el-input
          v-model="keyword"
          clearable
          :disabled="!selectedCustomer"
          placeholder="搜索销售订单号 / 客户订单号 / 内部项目号"
          class="search-input"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-button :disabled="!selectedCustomer" @click="handleSearch">搜索</el-button>
        <el-button type="primary" :loading="loading" :disabled="!selectedCustomer" @click="refreshOrders">刷新</el-button>
      </div>
    </section>

    <section class="erp-table-surface sales-order-table-surface">
      <div v-if="!selectedCustomer" class="erp-empty-state sales-order-empty-state">
        <el-empty description="请先选择客户查看销售订单" />
      </div>

      <template v-else>
        <div class="erp-table-body">
          <el-table :data="orders" border stripe height="600" v-loading="loading">
            <el-table-column prop="salesOrderNo" label="销售订单号" min-width="170" fixed show-overflow-tooltip />
            <el-table-column prop="customerOrderNo" label="客户订单号" min-width="150" show-overflow-tooltip />
            <el-table-column prop="customerProjectNo" label="客户项目号" min-width="140" show-overflow-tooltip />
            <el-table-column prop="internalProjectNo" label="内部项目号" min-width="140" show-overflow-tooltip />
            <el-table-column prop="documentTypeCode" label="单据类型编码" width="130" />
            <el-table-column prop="status" label="状态" width="120" align="center">
              <template #default="scope">
                <el-tag :type="statusTagType(scope.row.status)" size="small">{{ scope.row.status || '-' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="零件" width="90" align="center">
              <template #default="scope">{{ scope.row.itemCount || 0 }}</template>
            </el-table-column>
            <el-table-column label="已提 / 总数" width="110" align="center">
              <template #default="scope">{{ scope.row.releasedQuantity || 0 }} / {{ scope.row.totalQuantity || 0 }}</template>
            </el-table-column>
            <el-table-column label="缺图" width="90" align="center">
              <template #default="scope">
                <el-tag :type="scope.row.drawingMissingCount > 0 ? 'warning' : 'success'" size="small">
                  {{ scope.row.drawingMissingCount || 0 }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="u9SyncStatus" label="U9状态" width="120" show-overflow-tooltip />
            <el-table-column prop="updatedAt" label="更新时间" width="180" show-overflow-tooltip />
            <el-table-column label="操作" width="250" align="center" fixed="right">
              <template #default="scope">
                <div class="erp-table-actions sales-order-row-actions">
                  <el-button size="small" @click="openDetail(scope.row)">查看销售订单</el-button>
                  <el-button
                    v-for="action in rowStatusActions(scope.row)"
                    :key="`${scope.row.id}-${action.label}`"
                    v-permission="action.permission"
                    size="small"
                    :type="action.type"
                    plain
                    class="status-action-button"
                    :loading="isOrderStatusPending(scope.row)"
                    :disabled="isOrderStatusPending(scope.row)"
                    @click="handleStatusAction(scope.row, action)"
                  >
                    {{ action.label }}
                  </el-button>
                  <el-tooltip v-if="statusActionLocked(scope.row)" :content="SALES_ORDER_STATUS_LOCKED_REASON" placement="top">
                    <span class="status-locked-label">状态锁定</span>
                  </el-tooltip>
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
            @size-change="page = 1"
          />
        </div>
      </template>
    </section>

    <el-drawer v-model="detailVisible" title="销售订单详情" size="76%">
      <div v-if="orderDetail" v-loading="detailLoading" class="detail-wrap">
        <el-descriptions :column="2" border class="detail-descriptions">
          <el-descriptions-item label="客户">{{ orderDetail.orderInfo?.customerName || selectedCustomer || '-' }}</el-descriptions-item>
          <el-descriptions-item label="客户编码">{{ orderDetail.orderInfo?.customerCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="客户简称">{{ orderDetail.orderInfo?.customerShortName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="单据类型编码">{{ orderDetail.orderInfo?.documentTypeCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="销售订单号">{{ orderDetail.orderInfo?.salesOrderNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="客户订单号">{{ orderDetail.orderInfo?.customerOrderNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="客户项目号">{{ orderDetail.orderInfo?.customerProjectNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="内部项目号">{{ orderDetail.orderInfo?.internalProjectNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ orderDetail.orderInfo?.status || '-' }}</el-descriptions-item>
          <el-descriptions-item label="U9状态">{{ orderDetail.orderInfo?.u9SyncStatus || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="auto-release-summary">
          <div class="auto-release-summary-card">
            <span>自动提单行</span>
            <strong>{{ autoReleaseSummary.ticketCount }}</strong>
          </div>
          <div class="auto-release-summary-card">
            <span>自动提单数量</span>
            <strong>{{ autoReleaseSummary.ticketQty }}</strong>
          </div>
          <div class="auto-release-summary-card">
            <span>已提 / 总数</span>
            <strong>{{ autoReleaseSummary.releasedQuantity }} / {{ autoReleaseSummary.totalQuantity }}</strong>
          </div>
          <div class="auto-release-summary-card">
            <span>工艺表行</span>
            <strong>{{ autoReleaseSummary.processSheetCount }}</strong>
          </div>
        </div>

        <div class="detail-actions">
          <el-alert
            :title="`当前缺图零件：${drawingSummary?.missingDrawingCount || 0} 个`"
            type="warning"
            :closable="false"
            show-icon
          />
          <el-button v-permission="'market:sales-order:supplement-drawing'" type="primary" plain :disabled="Number(drawingSummary?.missingDrawingCount || 0) === 0" @click="openSupplementDialog">
            补传图纸
          </el-button>
        </div>

        <el-tabs v-model="activeDetailTab" class="detail-tabs" @tab-change="handleDetailTabChange">
          <el-tab-pane label="零件明细" name="items">
            <el-table :data="detailItemList" border stripe height="380" v-loading="detailItemsLoading">
              <el-table-column prop="partNo" label="图号" min-width="150" fixed show-overflow-tooltip />
              <el-table-column prop="lineNo" label="行号" width="80" align="center" />
              <el-table-column prop="version" label="版本" width="80" align="center" />
              <el-table-column prop="materialNo" label="物料号" min-width="130" show-overflow-tooltip />
              <el-table-column prop="materialNoStatus" label="物料号状态" width="120" show-overflow-tooltip />
              <el-table-column prop="matchStatus" label="匹配状态" width="120" align="center" show-overflow-tooltip>
                <template #default="scope">
                  <el-tag :type="matchStatusTagType(scope.row.matchStatus)" size="small">{{ scope.row.matchStatus || '-' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="matchMessage" label="匹配说明" min-width="180" show-overflow-tooltip>
                <template #default="scope">{{ scope.row.matchMessage || '-' }}</template>
              </el-table-column>
              <el-table-column prop="qty" label="数量" width="80" align="center" />
              <el-table-column prop="status" label="状态" width="110" align="center" />
              <el-table-column label="图纸" width="90" align="center">
                <template #default="scope">
                  <el-tag :type="scope.row.drawingUrl ? 'success' : 'warning'" size="small">
                    {{ scope.row.drawingUrl ? '已绑定' : '缺图' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="description" label="物料描述" min-width="180" show-overflow-tooltip />
              <el-table-column prop="orderType" label="订单类型" width="120" show-overflow-tooltip />
              <el-table-column prop="customerOrderNo" label="客户订单号" min-width="140" show-overflow-tooltip />
              <el-table-column prop="customerProjectNo" label="客户项目号" min-width="140" show-overflow-tooltip />
              <el-table-column prop="internalProjectNo" label="内部项目号" min-width="140" show-overflow-tooltip />
              <el-table-column prop="customerDeliveryDate" label="客户交期" width="120" />
              <el-table-column prop="internalDeliveryDate" label="内部交期" width="120" />
              <el-table-column prop="materialName" label="材料" min-width="130" show-overflow-tooltip />
              <el-table-column prop="surfaceTreatment" label="表面处理" min-width="130" show-overflow-tooltip />
              <el-table-column label="未税单价" width="110" align="right">
                <template #default="scope">{{ formatMoney(scope.row.untaxedUnitPrice) }}</template>
              </el-table-column>
              <el-table-column label="含税单价" width="110" align="right">
                <template #default="scope">{{ formatMoney(scope.row.taxIncludedUnitPrice) }}</template>
              </el-table-column>
              <el-table-column label="未税总价" width="120" align="right">
                <template #default="scope">{{ formatMoney(scope.row.untaxedTotalPrice) }}</template>
              </el-table-column>
              <el-table-column label="含税总价" width="120" align="right">
                <template #default="scope">{{ formatMoney(scope.row.taxIncludedTotalPrice) }}</template>
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
                @size-change="detailItemPagination.page = 1"
              />
            </div>
          </el-tab-pane>

          <el-tab-pane label="提单行" name="tickets">
            <el-table :data="detailTicketList" border stripe height="330" v-loading="detailTicketsLoading">
              <el-table-column prop="internalProjectNo" label="内部项目号" min-width="130" show-overflow-tooltip />
              <el-table-column prop="partNo" label="图号" min-width="150" show-overflow-tooltip />
              <el-table-column prop="version" label="版本" width="80" align="center" />
              <el-table-column prop="materialNo" label="物料号" min-width="130" show-overflow-tooltip />
              <el-table-column prop="qty" label="数量" width="80" align="center" />
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
                @size-change="detailTicketPagination.page = 1"
              />
            </div>
          </el-tab-pane>

          <el-tab-pane label="工艺表行" name="processSheets">
            <el-table :data="detailProcessSheetList" border stripe height="330" v-loading="detailProcessSheetsLoading">
              <el-table-column prop="internalProjectNo" label="内部项目号" min-width="130" show-overflow-tooltip />
              <el-table-column prop="partNo" label="图号" min-width="150" show-overflow-tooltip />
              <el-table-column prop="version" label="版本" width="80" align="center" />
              <el-table-column prop="description" label="物料描述" min-width="180" show-overflow-tooltip />
              <el-table-column prop="qty" label="数量" width="80" align="center" />
              <el-table-column prop="materialName" label="材料" min-width="130" show-overflow-tooltip />
              <el-table-column prop="surfaceTreatment" label="表面处理" min-width="130" show-overflow-tooltip />
              <el-table-column prop="deliveryDate" label="交期" width="120" />
              <el-table-column label="工艺路线" min-width="260" show-overflow-tooltip>
                <template #default="scope">{{ formatRoute(scope.row.processRoute) }}</template>
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
                @size-change="detailProcessSheetPagination.page = 1"
              />
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-drawer>

    <SupplementDrawingDialog
      v-model="supplementVisible"
      v-model:files="supplementFiles"
      v-model:version-files="supplementVersionFiles"
      title="补传销售订单图纸"
      :order-label="orderDetail?.orderInfo?.salesOrderNo || '-'"
      :customer-name="orderDetail?.orderInfo?.customerName || selectedCustomer || '-'"
      :missing-items="missingDrawingItems"
      :result="supplementResult"
      :pending="supplementUploadPending"
      :permission="'market:sales-order:supplement-drawing'"
      @submit="submitSupplementDrawings"
    />
    <CustomerPickerDialog
      v-model="customerPickerVisible"
      :current-customer="selectedCustomer"
      customer-scope="sales-order"
      @select="handleCustomerSelected"
    />
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import CustomerPickerDialog from './CustomerPickerDialog.vue'
import SupplementDrawingDialog from './order/SupplementDrawingDialog.vue'
import { usePendingAction } from '../composables/usePendingAction'
import {
  discardSalesOrder,
  getSalesOrder,
  getSalesOrderDrawingSummary,
  listSalesOrderItems,
  listSalesOrders,
  listSalesOrderProcessSheets,
  listSalesOrderTickets,
  restoreSalesOrder,
  supplementSalesOrderDrawings
} from '../services/orderApi'

const { isPending, runAction } = usePendingAction()

const SALES_ORDER_STATUSES = ['已报价', '部分已提单', '已生成提单', '废弃']
const RELEASED_SALES_ORDER_STATUSES = ['部分已提单', '已生成提单']
const SALES_ORDER_STATUS_LOCKED_REASON = '销售订单已生成提单，不能废弃'
const salesOrderActionKeys = {
  statusChange: (orderId) => `sales-order:status-change:${orderId || 'none'}`,
  supplementDrawings: (orderId) => `sales-order:supplement-drawings:${orderId || 'none'}`
}
const SALES_ORDER_STATUS_ACTIONS = [
  {
    status: '已报价',
    label: '废弃',
    permission: 'market:sales-order:discard',
    handler: discardSalesOrder,
    type: 'danger',
    reason: '销售订单：废弃已报价订单',
    successMessage: '销售订单已废弃。',
    errorMessage: '废弃销售订单失败。',
    confirmTitle: '废弃销售订单',
    confirmButtonText: '确认废弃',
    confirmMessage: (row) => `确认废弃销售订单「${row.salesOrderNo || row.id}」吗？系统只调整销售订单头和零件状态，不清空价格、数量、交期、图纸或物料号。`
  },
  {
    status: '废弃',
    label: '恢复为已报价',
    permission: 'market:sales-order:restore',
    handler: restoreSalesOrder,
    type: 'success',
    reason: '销售订单：恢复废弃订单',
    successMessage: '销售订单已恢复为已报价。',
    errorMessage: '恢复销售订单失败。',
    confirmTitle: '恢复销售订单',
    confirmButtonText: '确认恢复',
    confirmMessage: (row) => `确认将废弃销售订单「${row.salesOrderNo || row.id}」恢复为“已报价”吗？恢复后用于异常治理或重新处理，不再进入旧的手工议价提单链路。`
  }
]


const orders = ref([])
const selectedCustomer = ref('')
const keyword = ref('')
const statusFilter = ref('')
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const loading = ref(false)
const detailLoading = ref(false)
const detailVisible = ref(false)
const orderDetail = ref(null)
const activeDetailTab = ref('items')
const detailItemList = ref([])
const detailTicketList = ref([])
const detailProcessSheetList = ref([])
const drawingSummary = ref(null)
const detailItemsLoading = ref(false)
const detailTicketsLoading = ref(false)
const detailProcessSheetsLoading = ref(false)
const detailItemPagination = reactive({ page: 1, pageSize: 20 })
const detailTicketPagination = reactive({ page: 1, pageSize: 20 })
const detailProcessSheetPagination = reactive({ page: 1, pageSize: 20 })
const detailItemTotal = ref(0)
const detailTicketTotal = ref(0)
const detailProcessSheetTotal = ref(0)
const supplementVisible = ref(false)
const supplementFiles = ref([])
const supplementVersionFiles = ref([])
const supplementResult = ref(null)
const customerPickerVisible = ref(false)

const missingDrawingItems = computed(() => drawingSummary.value?.missingItems || detailItemList.value.filter(item => !item.drawingUrl))
const supplementUploadPending = computed(() => isPending(salesOrderActionKeys.supplementDrawings(orderDetail.value?.orderInfo?.id)))
const autoReleaseSummary = computed(() => {
  const summary = orderDetail.value?.summary || {}
  return {
    ticketCount: numberOrZero(summary.ticketCount),
    ticketQty: numberOrZero(summary.ticketQty),
    releasedQuantity: numberOrZero(summary.releasedQuantity),
    totalQuantity: numberOrZero(summary.totalQuantity),
    processSheetCount: numberOrZero(summary.processSheetCount)
  }
})

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
    total.value = 0
    return
  }
  loading.value = true
  try {
    const response = await listSalesOrders({
      customerName: selectedCustomer.value,
      keyword: keyword.value.trim() || undefined,
      status: statusFilter.value || undefined,
      page: page.value,
      pageSize: pageSize.value
    })
    orders.value = response.records || []
    total.value = response.total || 0
  } catch (error) {
    console.error(error)
    ElMessage.error('销售订单列表加载失败。')
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
  statusFilter.value = ''
  page.value = 1
  orderDetail.value = null
  detailVisible.value = false
  await loadOrders()
}

const resolveCustomerName = (customer) => typeof customer === 'string' ? customer : (customer?.customerName || '')

const handleCustomerSelected = async (customer) => {
  selectedCustomer.value = resolveCustomerName(customer)
  await handleCustomerChange()
}

const handleCustomerClear = () => {
  selectedCustomer.value = ''
  keyword.value = ''
  statusFilter.value = ''
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
  drawingSummary.value = null
}

const openDetail = async (row) => {
  detailVisible.value = true
  detailLoading.value = true
  resetDetailPagination()
  try {
    orderDetail.value = await getSalesOrder(row.id)
    await fetchDrawingSummary(row.id)
    await fetchCurrentDetailTab()
  } catch (error) {
    console.error(error)
    ElMessage.error('销售订单详情加载失败。')
  } finally {
    detailLoading.value = false
  }
}

const fetchDrawingSummary = async (salesOrderId = orderDetail.value?.orderInfo?.id) => {
  if (!salesOrderId) return
  drawingSummary.value = await getSalesOrderDrawingSummary(salesOrderId)
}

const fetchDetailItems = async () => {
  const salesOrderId = orderDetail.value?.orderInfo?.id
  if (!salesOrderId) return
  detailItemsLoading.value = true
  try {
    const response = await listSalesOrderItems(salesOrderId, {
      page: detailItemPagination.page,
      pageSize: detailItemPagination.pageSize
    })
    detailItemList.value = response.records || []
    detailItemTotal.value = Number(response.total ?? 0)
  } catch (error) {
    console.error(error)
    ElMessage.error('销售订单零件明细加载失败。')
  } finally {
    detailItemsLoading.value = false
  }
}

const fetchDetailTickets = async () => {
  const salesOrderId = orderDetail.value?.orderInfo?.id
  if (!salesOrderId) return
  detailTicketsLoading.value = true
  try {
    const response = await listSalesOrderTickets(salesOrderId, {
      page: detailTicketPagination.page,
      pageSize: detailTicketPagination.pageSize
    })
    detailTicketList.value = response.records || []
    detailTicketTotal.value = Number(response.total ?? 0)
  } catch (error) {
    console.error(error)
    ElMessage.error('销售订单提单行加载失败。')
  } finally {
    detailTicketsLoading.value = false
  }
}

const fetchDetailProcessSheets = async () => {
  const salesOrderId = orderDetail.value?.orderInfo?.id
  if (!salesOrderId) return
  detailProcessSheetsLoading.value = true
  try {
    const response = await listSalesOrderProcessSheets(salesOrderId, {
      page: detailProcessSheetPagination.page,
      pageSize: detailProcessSheetPagination.pageSize
    })
    detailProcessSheetList.value = response.records || []
    detailProcessSheetTotal.value = Number(response.total ?? 0)
  } catch (error) {
    console.error(error)
    ElMessage.error('销售订单工艺表行加载失败。')
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

const openSupplementDialog = () => {
  supplementResult.value = null
  supplementFiles.value = []
  supplementVersionFiles.value = []
  supplementVisible.value = true
}

const refreshCurrentDetail = async () => {
  if (!orderDetail.value?.orderInfo?.id) return
  const currentId = orderDetail.value.orderInfo.id
  const latestDetail = await getSalesOrder(currentId)
  orderDetail.value = { ...orderDetail.value, ...latestDetail }
  await fetchDrawingSummary(currentId)
  await fetchCurrentDetailTab()
}

const isOrderStatusPending = (row) => isPending(salesOrderActionKeys.statusChange(row?.id))

const statusActionLocked = (row) => {
  if (!row) return false
  return RELEASED_SALES_ORDER_STATUSES.includes(row.status) || Number(row.ticketCount || 0) > 0
}

const rowStatusActions = (row) => {
  if (!row || statusActionLocked(row)) {
    return []
  }
  return SALES_ORDER_STATUS_ACTIONS.filter(action => action.status === row.status)
}

const handleStatusAction = async (row, action) => {
  if (!row?.id || !action?.handler) return
  try {
    await ElMessageBox.confirm(
      action.confirmMessage(row),
      action.confirmTitle,
      {
        type: action.type === 'danger' ? 'error' : 'warning',
        confirmButtonText: action.confirmButtonText,
        cancelButtonText: '取消'
      }
    )
  } catch {
    return
  }
  await performStatusAction(row, action)
}

const performStatusAction = async (row, action) => {
  return runAction(salesOrderActionKeys.statusChange(row.id), async () => {
    const response = await action.handler(row.id, { reason: action.reason })
    ElMessage.success(response.message || action.successMessage)
    await loadOrders()
    await refreshCurrentDetail()
  }).catch((error) => {
    if (error) {
      console.error(error)
      ElMessage.error(error.response?.data?.message || action.errorMessage)
    }
  })
}

const submitSupplementDrawings = async () => {
  if (!orderDetail.value?.orderInfo?.id) return ElMessage.warning('请先打开销售订单详情。')
  if (supplementFiles.value.length === 0 && supplementVersionFiles.value.length === 0) return ElMessage.warning('请先选择需要补传的 PDF 图纸。')

  const salesOrderId = orderDetail.value.orderInfo.id
  return runAction(salesOrderActionKeys.supplementDrawings(salesOrderId), async () => {
    const formData = new FormData()
    supplementFiles.value.forEach(file => formData.append('drawings', file))
    supplementVersionFiles.value.forEach(file => formData.append('versionDrawings', file))
    const response = await supplementSalesOrderDrawings(salesOrderId, formData)
    supplementResult.value = response
    ElMessage.success(response.message || '补传图纸处理完成。')
    supplementFiles.value = []
    supplementVersionFiles.value = []
    await fetchDetailItems()
    await fetchDrawingSummary()
    await loadOrders()
  }).catch((error) => {
    if (error) {
      console.error(error)
      ElMessage.error(error.response?.data?.message || '补传图纸失败。')
    }
  })
}

const statusTagType = (status) => {
  if (status === '已报价') return 'success'
  if (status === '部分已提单') return 'warning'
  if (status === '已生成提单') return 'info'
  if (status === '废弃') return 'danger'
  return 'info'
}

const matchStatusTagType = (status) => {
  if (status === '已匹配') return 'success'
  if (status === '未匹配') return 'warning'
  if (status === '匹配失败') return 'danger'
  return 'info'
}

function numberOrZero(value) {
  const number = Number(value)
  return Number.isFinite(number) ? number : 0
}

const formatMoney = (value) => {
  if (value === null || value === undefined || value === '') return '-'
  const number = Number(value)
  if (Number.isNaN(number)) return String(value)
  return `¥ ${number.toFixed(2)}`
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
.sales-order-filter-group {
  width: 100%;
  justify-content: flex-end;
}

.customer-picker-trigger {
  justify-content: space-between;
  width: 220px;
  flex: 0 0 220px;
  min-height: 32px;
  padding: 0 12px;
  text-align: left;
}

.customer-picker-trigger :deep(> span) {
  display: block;
  width: 100%;
}

.customer-picker-content {
  align-items: center;
  display: flex;
  gap: 12px;
  min-width: 0;
  width: 100%;
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
  align-items: center;
  border-left: 1px solid var(--erp-border-soft);
  display: inline-flex;
  flex: none;
  color: var(--erp-primary);
  font-size: 12px;
  font-weight: 700;
  min-height: 24px;
  padding-left: 12px;
}

.status-filter {
  width: 150px;
  flex: 0 0 150px;
}

.search-input {
  width: 320px;
  flex: 0 0 320px;
}

.sales-order-table-surface {
  overflow: hidden;
}

.sales-order-table-surface .erp-table-body {
  padding: var(--erp-space-3);
}

.sales-order-empty-state {
  margin: var(--erp-space-4);
}

.sales-order-row-actions {
  align-items: center;
  justify-content: flex-start;
  gap: 6px;
}

.status-action-button {
  margin-left: 0;
}

.status-locked-label {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 8px;
  border: 1px solid var(--erp-border-soft);
  border-radius: 6px;
  color: var(--erp-text-secondary);
  font-size: 12px;
  line-height: 1;
  background: var(--erp-surface-soft);
  cursor: default;
}

.detail-wrap {
  padding-right: 4px;
}

.detail-descriptions :deep(.el-descriptions__label),
.supplement-descriptions :deep(.el-descriptions__label) {
  color: var(--erp-text-secondary);
  background: #f5f7fb;
}

.auto-release-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(120px, 1fr));
  gap: 10px;
  margin-top: 14px;
}

.auto-release-summary-card {
  min-width: 0;
  padding: 12px;
  border: 1px solid var(--erp-border-soft);
  border-radius: 8px;
  background: var(--erp-surface-soft);
}

.auto-release-summary-card span {
  display: block;
  color: var(--erp-text-secondary);
  font-size: 12px;
}

.auto-release-summary-card strong {
  display: block;
  margin-top: 6px;
  color: var(--erp-text);
  font-size: 18px;
  line-height: 1.2;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.detail-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 14px;
  flex-wrap: wrap;
}

.detail-actions :deep(.el-alert) {
  flex: 1;
  min-width: 240px;
}

.detail-tabs {
  margin-top: 16px;
}

.supplement-dialog {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.custom-dropzone {
  width: 100%;
  padding: 28px 16px;
  border: 1px dashed #aab6c8;
  border-radius: 8px;
  background: var(--erp-surface-soft);
  text-align: center;
  transition: border-color 0.2s ease, background-color 0.2s ease;
}

.custom-dropzone:hover {
  border-color: var(--erp-primary);
  background: #f3f8fc;
}

.supplement-dropzone :deep(.el-icon),
.supplement-dropzone .el-icon {
  margin-bottom: 12px;
  color: #8c98aa;
  font-size: 42px;
}

.folder-select-row {
  margin-top: 12px;
  color: var(--erp-text-secondary);
  font-size: var(--erp-font-size-sm);
}

.supplement-upload-actions {
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 12px;
}

.folder-select-button {
  min-height: 32px;
  min-width: 150px;
  padding-inline: 14px;
}

.hidden-input {
  display: none;
}

.supplement-file-stats {
  padding: 12px 14px;
  border: 1px solid #cfe8d2;
  border-radius: 8px;
  background: #f4fbf5;
  color: var(--erp-success);
}

.supplement-file-stats h4 {
  margin: 0 0 8px;
  font-size: 14px;
}

.supplement-pdf-list {
  list-style: none;
  padding: 0;
  margin: 0;
  max-height: 150px;
  overflow-y: auto;
}

.supplement-pdf-list li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 7px 0;
  border-bottom: 1px solid #dcefe0;
  color: #256b2b;
}

.supplement-pdf-list li:last-child {
  border-bottom: none;
}

.supplement-result {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.result-list {
  list-style: none;
  padding: 0;
  margin: 0;
  max-height: 150px;
  overflow: auto;
  border: 1px solid var(--erp-border-soft);
  border-radius: 6px;
  background: var(--erp-surface-soft);
}

.result-list li {
  padding: 7px 10px;
  border-bottom: 1px solid var(--erp-border-soft);
  color: var(--erp-text);
}

.result-list li:last-child {
  border-bottom: none;
}

.result-main {
  font-weight: 700;
}

.result-sub {
  color: var(--erp-text-secondary);
}

.result-reason {
  color: var(--erp-warning);
}

@media (max-width: 1200px) {
  .sales-order-filter-group {
    justify-content: flex-start;
  }

  .auto-release-summary {
    grid-template-columns: repeat(2, minmax(120px, 1fr));
  }
}

@media (max-width: 760px) {
  .customer-picker-trigger,
  .search-input,
  .status-filter {
    width: 100%;
    flex-basis: auto;
  }

  .auto-release-summary {
    grid-template-columns: 1fr;
  }
}
</style>
