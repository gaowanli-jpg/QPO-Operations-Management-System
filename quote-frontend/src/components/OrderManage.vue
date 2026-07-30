<template>
  <div class="erp-page order-manage-page">
    <header class="erp-page-header">
      <div>
        <h1 class="erp-page-title">订单管理</h1>
        <p class="erp-page-description">先选择客户，再管理该客户下的订单、零件、图纸文件、提单和工艺表数据。</p>
      </div>
    </header>

    <section class="erp-toolbar">
      <div class="erp-toolbar-group order-filter-group">
        <el-button v-permission="'market:order:customer-select'" class="customer-picker-trigger" @click="customerPickerVisible = true">
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
          placeholder="搜索 RFQ 号"
          class="search-input"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-select
          v-model="statusFilter"
          clearable
          :disabled="!selectedCustomer"
          placeholder="全部状态"
          class="status-select"
          @change="handleSearch"
          @clear="handleSearch"
        >
          <el-option label="全部状态" value="" />
          <el-option
            v-for="status in ORDER_MANAGEMENT_STATUSES"
            :key="status"
            :label="status"
            :value="status"
          />
        </el-select>
        <el-button :disabled="!selectedCustomer" @click="handleSearch">搜索</el-button>
        <el-button type="primary" :loading="loading" :disabled="!selectedCustomer" @click="refreshOrders">刷新</el-button>
      </div>
    </section>

    <section class="erp-table-surface order-table-surface">
      <div v-if="!selectedCustomer" class="erp-empty-state">
        <el-empty description="请先选择客户查看订单" />
      </div>

      <template v-else>
        <div class="erp-table-body">
          <el-table :data="orders" border stripe height="600" v-loading="loading">
          <el-table-column prop="rfqNo" label="订单号" min-width="170" fixed show-overflow-tooltip />
          <el-table-column prop="status" label="状态" width="120" align="center">
            <template #default="scope">
              <el-tag type="success" size="small">
                {{ scope.row.status || '-' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="零件" width="90" align="center">
            <template #default="scope">{{ scope.row.itemCount || 0 }}</template>
          </el-table-column>
          <el-table-column label="已报价" width="90" align="center">
            <template #default="scope">{{ scope.row.quotedCount || 0 }}</template>
          </el-table-column>
          <el-table-column label="缺图" width="90" align="center">
            <template #default="scope">
              <el-tag :type="scope.row.missingDrawingCount > 0 ? 'warning' : 'success'" size="small">
                {{ scope.row.missingDrawingCount || 0 }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="提单行" width="90" align="center">
            <template #default="scope">{{ scope.row.ticketCount || 0 }}</template>
          </el-table-column>
          <el-table-column label="工艺表行" width="100" align="center">
            <template #default="scope">{{ scope.row.processSheetCount || 0 }}</template>
          </el-table-column>
          <el-table-column prop="createdAt" label="创建时间" width="180" show-overflow-tooltip />
          <el-table-column label="操作" width="270" align="center" fixed="right">
            <template #default="scope">
              <div class="erp-table-actions order-row-actions">
                <el-button size="small" @click="openDetail(scope.row)">详情</el-button>
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
                <el-tooltip
                  v-if="statusActionLocked(scope.row)"
                  :content="RELEASED_ORDER_STATUS_ACTION_DISABLED_REASON"
                  placement="top"
                >
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

    <el-drawer v-model="detailVisible" title="订单详情" size="72%">
      <div v-if="orderDetail" v-loading="detailLoading" class="detail-wrap">
        <el-descriptions :column="2" border class="detail-descriptions">
          <el-descriptions-item label="订单号">{{ orderDetail.orderInfo?.rfqNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ orderDetail.orderInfo?.status || '-' }}</el-descriptions-item>
          <el-descriptions-item label="客户">{{ orderDetail.orderInfo?.customerName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="图纸目录" :span="2">{{ orderDetail.fileRoot || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="detail-actions">
          <el-alert
            :title="`当前缺图零件：${drawingSummary?.missingDrawingCount || 0} 个`"
            type="warning"
            :closable="false"
            show-icon
          />
          <el-button v-permission="'market:order:supplement-drawing'" type="primary" plain :disabled="Number(drawingSummary?.missingDrawingCount || 0) === 0" @click="openSupplementDialog">
            补传图纸
          </el-button>
        </div>

        <el-tabs v-model="activeDetailTab" class="detail-tabs" @tab-change="handleDetailTabChange">
          <el-tab-pane label="零件明细" name="items">
            <el-table :data="detailItemList" border stripe height="360" v-loading="detailItemsLoading">
              <el-table-column prop="partNo" label="图号" min-width="140" fixed show-overflow-tooltip />
              <el-table-column prop="version" label="版本" width="80" align="center" />
              <el-table-column prop="description" label="物料描述" min-width="180" show-overflow-tooltip />
              <el-table-column prop="qty" label="数量" width="70" align="center" />
              <el-table-column prop="status" label="状态" width="110" align="center" />
              <el-table-column label="含税单价" width="110" align="right">
                <template #default="scope">
                  {{ scope.row.finalUnitPrice ? `¥ ${scope.row.finalUnitPrice.toFixed(2)}` : '-' }}
                </template>
              </el-table-column>
              <el-table-column label="图纸" width="90" align="center">
                <template #default="scope">
                  <el-tag :type="scope.row.drawingUrl ? 'success' : 'warning'" size="small">
                    {{ scope.row.drawingUrl ? '已绑定' : '缺图' }}
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
                @size-change="detailItemPagination.page = 1"
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
                @size-change="detailTicketPagination.page = 1"
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

    <el-dialog
      v-model="supplementVisible"
      title="补传图纸"
      width="720px"
      :close-on-click-modal="false"
    >
      <div v-if="orderDetail" class="supplement-dialog">
        <el-descriptions :column="1" border size="small" class="erp-dialog-section supplement-descriptions">
          <el-descriptions-item label="订单">{{ orderDetail.orderInfo?.rfqNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="客户">
            {{ orderDetail.orderInfo?.customerName || '-' }}
          </el-descriptions-item>
        </el-descriptions>

        <div class="erp-dialog-section missing-list">
          <div class="section-subtitle">待补图零件</div>
          <el-table :data="missingDrawingItems" border stripe height="180">
            <el-table-column prop="partNo" label="图号" min-width="140" show-overflow-tooltip />
            <el-table-column prop="version" label="版本" width="80" align="center" />
            <el-table-column prop="description" label="物料描述" min-width="180" show-overflow-tooltip />
            <el-table-column prop="qty" label="数量" width="70" align="center" />
          </el-table>
        </div>

        <div class="erp-upload-surface upload-area">
          <div v-permission="'market:order:supplement-drawing'"
            class="custom-dropzone supplement-dropzone"
            @dragover.prevent
            @drop.prevent="handleSupplementDrop"
          >
            <el-icon class="el-icon--upload"><upload-filled /></el-icon>
            <div class="el-upload__text">拖拽 PDF 图纸或图纸文件夹到这里</div>
            <div class="el-upload__tip">支持 图号.pdf、图号 空格说明.pdf、图号-说明.pdf，例如 L20100000733.pdf 或 L20100000733-定位块.pdf。</div>
            <div class="folder-select-row">或点击下方按钮选择多个 PDF / 图纸文件夹</div>
            <div class="supplement-upload-actions">
              <el-button v-permission="'market:order:supplement-drawing'" plain class="workflow-soft-button folder-select-button" @click="triggerSupplementFileSelect">选择多个 PDF</el-button>
              <el-button v-permission="'market:order:supplement-drawing'" plain class="workflow-soft-button folder-select-button" @click="triggerSupplementFolderSelect">选择文件夹</el-button>
            </div>
            <input type="file" ref="supplementFileInput" accept=".pdf" multiple class="hidden-input" @change="handleSupplementFileSelect" />
            <input type="file" ref="supplementFolderInput" webkitdirectory directory multiple class="hidden-input" @change="handleSupplementFolderSelect" />
          </div>
        </div>

        <div v-if="supplementFiles.length > 0" class="supplement-file-stats">
          <h4>已选择待补传图纸（{{ supplementFiles.length }} 份）</h4>
          <ul class="supplement-pdf-list">
            <li v-for="(file, index) in supplementFiles" :key="`${file.name}-${file.size}-${index}`">
              <span>{{ file.name }}</span>
              <el-button v-permission="'market:order:supplement-drawing'" link type="danger" size="small" @click="removeSupplementFile(index)">移除</el-button>
            </li>
          </ul>
        </div>

        <div v-if="supplementResult" class="supplement-result">
          <el-alert
            :title="supplementResult.message"
            type="success"
            :closable="false"
            show-icon
          />
          <el-tabs>
            <el-tab-pane :label="`已绑定 ${supplementResult.boundCount || 0}`">
              <result-list :items="supplementResult.boundItems" />
            </el-tab-pane>
            <el-tab-pane :label="`已跳过 ${supplementResult.alreadyBoundCount || 0}`">
              <result-list :items="supplementResult.alreadyBoundItems" />
            </el-tab-pane>
            <el-tab-pane :label="`未匹配 ${supplementResult.unmatchedCount || 0}`">
              <result-list :items="supplementResult.unmatchedDrawings" />
            </el-tab-pane>
            <el-tab-pane :label="`非PDF ${supplementResult.skippedCount || 0}`">
              <result-list :items="supplementResult.skippedFiles" />
            </el-tab-pane>
          </el-tabs>
        </div>
      </div>

      <template #footer>
        <div class="erp-dialog-footer">
          <el-button @click="supplementVisible = false">关闭</el-button>
          <el-button v-permission="'market:order:supplement-drawing'" type="primary" :loading="supplementUploadPending" :disabled="supplementFiles.length === 0 || supplementUploadPending" @click="submitSupplementDrawings">
            上传并匹配
          </el-button>
        </div>
      </template>
    </el-dialog>

    <CustomerPickerDialog
      v-model="customerPickerVisible"
      :current-customer="selectedCustomer"
      customer-scope="market-order"
      @select="handleCustomerSelected"
    />
  </div>
</template>

<script setup>
import { computed, defineComponent, h, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import CustomerPickerDialog from './CustomerPickerDialog.vue'
import { collectPdfFilesFromDataTransfer, mergePdfFiles } from '../utils/drawingUpload'
import { usePendingAction } from '../composables/usePendingAction'
import {
  discardOrder,
  getOrderDrawingSummary,
  getOrder,
  listOrderItems,
  listOrderProcessSheets,
  listOrderTickets,
  listOrders,
  restoreOrder,
  rollbackPricedOrder,
  rollbackQuotedOrder,
  supplementDrawings
} from '../services/orderApi'

const { isPending, runAction } = usePendingAction()

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
const supplementResult = ref(null)
const supplementFileInput = ref(null)
const supplementFolderInput = ref(null)
const customerPickerVisible = ref(false)

const ORDER_MANAGEMENT_STATUSES = ['未核价', '已核价', '已报价', '部分已提单', '已生成提单', '废弃']
const RELEASED_ORDER_STATUSES = ['部分已提单', '已生成提单']
const orderActionKeys = {
  statusChange: (orderId) => `order:status-change:${orderId || 'none'}`,
  supplementDrawings: (orderId) => `order:supplement-drawings:${orderId || 'none'}`
}
const ORDER_STATUS_ACTIONS = [
  {
    status: '已报价',
    label: '退回已核价',
    permission: 'market:order:rollback-quoted',
    handler: rollbackQuotedOrder,
    type: 'warning',
    reason: '订单管理：已报价退回已核价',
    successMessage: '订单已退回已核价。',
    errorMessage: '退回已核价失败。',
    confirmTitle: '退回已核价',
    confirmButtonText: '确认退回',
    confirmMessage: (row) => `确认将订单「${row.rfqNo || row.id}」从“已报价”退回“已核价”吗？系统只调整订单和零件状态，不会清空报价交期、核价结果或图纸。`
  },
  {
    status: '已核价',
    label: '退回未核价',
    permission: 'market:order:rollback-priced',
    handler: rollbackPricedOrder,
    type: 'warning',
    reason: '订单管理：已核价退回未核价',
    successMessage: '订单已退回未核价。',
    errorMessage: '退回未核价失败。',
    confirmTitle: '退回未核价',
    confirmButtonText: '确认退回',
    confirmMessage: (row) => `确认将订单「${row.rfqNo || row.id}」从“已核价”退回“未核价”吗？系统只调整状态，已有核价数据会保留为草稿。`
  },
  {
    status: '未核价',
    label: '废弃',
    permission: 'market:order:discard',
    handler: discardOrder,
    type: 'danger',
    reason: '订单管理：废弃未核价订单',
    successMessage: '订单已废弃。',
    errorMessage: '废弃订单失败。',
    confirmTitle: '废弃订单',
    confirmButtonText: '确认废弃',
    confirmMessage: (row) => `确认废弃订单「${row.rfqNo || row.id}」吗？废弃后订单不会进入核价、报价处理和议价提单候选，但数据仍保留。`
  },
  {
    status: '已核价',
    label: '废弃',
    permission: 'market:order:discard',
    handler: discardOrder,
    type: 'danger',
    reason: '订单管理：废弃已核价订单',
    successMessage: '订单已废弃。',
    errorMessage: '废弃订单失败。',
    confirmTitle: '废弃订单',
    confirmButtonText: '确认废弃',
    confirmMessage: (row) => `确认废弃订单「${row.rfqNo || row.id}」吗？系统只调整状态，不会清空核价结果、报价交期或图纸。`
  },
  {
    status: '已报价',
    label: '废弃',
    permission: 'market:order:discard',
    handler: discardOrder,
    type: 'danger',
    reason: '订单管理：废弃已报价订单',
    successMessage: '订单已废弃。',
    errorMessage: '废弃订单失败。',
    confirmTitle: '废弃订单',
    confirmButtonText: '确认废弃',
    confirmMessage: (row) => `确认废弃订单「${row.rfqNo || row.id}」吗？系统只调整状态，不会清空报价单数据。`
  },
  {
    status: '废弃',
    label: '恢复为已核价',
    permission: 'market:order:restore',
    handler: restoreOrder,
    type: 'success',
    reason: '订单管理：恢复废弃订单',
    successMessage: '订单已恢复为已核价。',
    errorMessage: '恢复订单失败。',
    confirmTitle: '恢复订单',
    confirmButtonText: '确认恢复',
    confirmMessage: (row) => `确认将废弃订单「${row.rfqNo || row.id}」恢复为“已核价”吗？恢复后仍需按后续业务校验保存报价单。`
  }
]
const RELEASED_ORDER_STATUS_ACTION_DISABLED_REASON = '订单已生成提单，不能废弃或回退'

const ResultList = defineComponent({
  props: {
    items: {
      type: Array,
      default: () => []
    }
  },
  setup(props) {
    return () => props.items.length
      ? h('ul', { class: 'result-list' }, props.items.map((item, index) => h('li', { key: index }, [
          h('span', { class: 'result-main' }, item.partNo ? `${item.partNo} / ${item.version || '-'}` : (item.fileName || '-')),
          item.description ? h('span', { class: 'result-sub' }, ` ｜ ${item.description}`) : null,
          item.fileName && item.partNo ? h('span', { class: 'result-sub' }, ` ｜ ${item.fileName}`) : null,
          item.reason ? h('span', { class: 'result-reason' }, ` ｜ ${item.reason}`) : null
        ])))
      : h('el-empty', { description: '无记录', imageSize: 60 })
  }
})

const missingDrawingItems = computed(() => detailItemList.value.filter(item => !item.drawingUrl))
const supplementUploadPending = computed(() => isPending(orderActionKeys.supplementDrawings(orderDetail.value?.orderInfo?.id)))

const isOrderStatusPending = (row) => isPending(orderActionKeys.statusChange(row?.id))

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
      status: statusFilter.value || undefined,
      page: page.value,
      pageSize: pageSize.value
    })
    orders.value = response.records || []
    total.value = response.total || 0
  } catch (error) {
    console.error(error)
    ElMessage.error('订单列表加载失败。')
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
    orderDetail.value = await getOrder(row.id)
    await fetchDrawingSummary(row.id)
    await fetchCurrentDetailTab()
  } catch (error) {
    console.error(error)
    ElMessage.error('订单详情加载失败。')
  } finally {
    detailLoading.value = false
  }
}

const fetchDrawingSummary = async (rfqId = orderDetail.value?.orderInfo?.id) => {
  if (!rfqId) return
  drawingSummary.value = await getOrderDrawingSummary(rfqId)
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
    ElMessage.error('订单零件明细加载失败。')
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
    ElMessage.error('订单提单行加载失败。')
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
    ElMessage.error('订单工艺表行加载失败。')
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
  supplementVisible.value = true
}

const addSupplementFiles = (files, sourceLabel) => {
  const beforeCount = supplementFiles.value.length
  supplementFiles.value = mergePdfFiles(supplementFiles.value, files)
  const addedCount = supplementFiles.value.length - beforeCount
  if (addedCount > 0) {
    ElMessage.success(`成功${sourceLabel} ${addedCount} 份 PDF。`)
  } else {
    ElMessage.warning('未找到新的 PDF 图纸。')
  }
}

const handleSupplementDrop = async (e) => {
  const pdfs = await collectPdfFilesFromDataTransfer(e.dataTransfer)
  addSupplementFiles(pdfs, '提取')
}

const triggerSupplementFileSelect = () => {
  supplementFileInput.value?.click()
}

const triggerSupplementFolderSelect = () => {
  supplementFolderInput.value?.click()
}

const handleSupplementFileSelect = (e) => {
  addSupplementFiles(e.target.files, '选择')
  e.target.value = ''
}

const handleSupplementFolderSelect = (e) => {
  addSupplementFiles(e.target.files, '从选中的文件夹中提取')
  e.target.value = ''
}

const removeSupplementFile = (index) => {
  supplementFiles.value.splice(index, 1)
}

const refreshCurrentDetail = async () => {
  if (!orderDetail.value?.orderInfo?.id) return
  const currentId = orderDetail.value.orderInfo.id
  const latestDetail = await getOrder(currentId)
  orderDetail.value = { ...orderDetail.value, ...latestDetail }
  await fetchDrawingSummary(currentId)
  await fetchCurrentDetailTab()
}

const statusActionLocked = (row) => {
  if (!row) return false
  return RELEASED_ORDER_STATUSES.includes(row.status) || Number(row.ticketCount || 0) > 0
}

const rowStatusActions = (row) => {
  if (!row || statusActionLocked(row)) {
    return []
  }
  return ORDER_STATUS_ACTIONS.filter(action => action.status === row.status)
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
  return runAction(orderActionKeys.statusChange(row.id), async () => {
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
  if (!orderDetail.value?.orderInfo?.id) return ElMessage.warning('请先打开订单详情。')
  if (supplementFiles.value.length === 0) return ElMessage.warning('请先选择需要补传的 PDF 图纸。')

  const rfqId = orderDetail.value.orderInfo.id
  return runAction(orderActionKeys.supplementDrawings(rfqId), async () => {
    const formData = new FormData()
    supplementFiles.value.forEach(file => formData.append('drawings', file))
    const response = await supplementDrawings(rfqId, formData)
    supplementResult.value = response
    ElMessage.success(response.message || '补传图纸处理完成。')
    supplementFiles.value = []
    await refreshCurrentDetail()
    await loadOrders()
  }).catch((error) => {
    if (error) {
      console.error(error)
      ElMessage.error(error.response?.data?.message || '补传图纸失败。')
    }
  })
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
.order-filter-group {
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

.status-select {
  width: 150px;
  flex: 0 0 150px;
}

.order-table-surface {
  overflow: hidden;
}

.order-table-surface .erp-table-body {
  padding: var(--erp-space-3);
}

.order-table-surface .erp-empty-state {
  margin: var(--erp-space-4);
}

.order-row-actions {
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

.section-subtitle {
  margin-bottom: 8px;
  padding-left: 10px;
  border-left: 3px solid var(--erp-primary);
  color: var(--erp-text);
  font-size: 14px;
  font-weight: 700;
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
  .order-filter-group {
    justify-content: flex-start;
  }
}

@media (max-width: 760px) {
  .customer-picker-trigger,
  .search-input,
  .status-select {
    width: 100%;
    flex-basis: 100%;
  }
}
</style>
