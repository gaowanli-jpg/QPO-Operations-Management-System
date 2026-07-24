<template>
  <div class="erp-page delivery-document-page">
    <header class="erp-page-header delivery-document-page-header">
      <div>
        <h1 class="erp-page-title">提单表据</h1>
        <p class="erp-page-description">选择已提单订单，查看并导出已生成提单行和工艺表行。</p>
      </div>
      <div class="erp-page-actions">
        <el-button
          v-permission="'market:delivery-document:order-select'"
          :icon="DocumentChecked"
          type="primary"
          :loading="loadingSummary"
          @click="openOrderPicker"
        >
          选择订单
        </el-button>
      </div>
    </header>

    <section v-if="!selectedRfqId" class="erp-table-surface delivery-document-empty">
      <el-empty description="请先选择部分已提单或已生成提单订单" />
    </section>

    <section v-else class="delivery-document-results">
      <section class="erp-table-surface delivery-document-summary" v-loading="loadingSummary">
        <div class="delivery-document-summary-title">订单摘要</div>
        <el-descriptions :column="3" border>
          <el-descriptions-item label="客户">{{ orderInfo.customerName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="RFQ号">{{ orderInfo.rfqNo || orderInfo.id || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ orderInfo.status || '-' }}</el-descriptions-item>
          <el-descriptions-item label="提单行数">{{ ticketTotal }}</el-descriptions-item>
          <el-descriptions-item label="提单数量">{{ documentSummary.ticketQty ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="工艺表行数">{{ processSheetTotal }}</el-descriptions-item>
        </el-descriptions>
      </section>

      <DeliveryTicketRowsTable
        :rows="ticketRows"
        :total="ticketTotal"
        :page="ticketPagination.page"
        :page-size="ticketPagination.pageSize"
        :loading="loadingTickets"
        :exportable="ticketExportable"
        @page-change="handleTicketPageChange"
        @page-size-change="handleTicketPageSizeChange"
        @export="exportDeliveryDocumentExcel('tickets')"
      />

      <ProcessSheetRowsTable
        :rows="processSheetRows"
        :total="processSheetTotal"
        :page="processSheetPagination.page"
        :page-size="processSheetPagination.pageSize"
        :loading="loadingProcessSheets"
        :exportable="processSheetExportable"
        @page-change="handleProcessSheetPageChange"
        @page-size-change="handleProcessSheetPageSizeChange"
        @export="exportDeliveryDocumentExcel('process')"
      />
    </section>

    <OrderPickerDialog
      v-model="orderPickerVisible"
      title="选择提单表据订单"
      customer-scope="delivery-document"
      order-scope="delivery-document"
      :allowed-statuses="DELIVERY_DOCUMENT_ALLOWED_STATUSES"
      @select="handleOrderSelected"
    />
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { DocumentChecked } from '@element-plus/icons-vue'
import DeliveryTicketRowsTable from './delivery-document/DeliveryTicketRowsTable.vue'
import OrderPickerDialog from './OrderPickerDialog.vue'
import ProcessSheetRowsTable from './delivery-document/ProcessSheetRowsTable.vue'
import {
  downloadDeliveryDocumentExport,
  getDeliveryDocumentSummary,
  listDeliveryDocumentProcessSheets,
  listDeliveryDocumentTickets
} from '../services/marketApi'
import { DEFAULT_CLIENT_PAGE_SIZE } from '../utils/clientPagination'

const DELIVERY_DOCUMENT_ALLOWED_STATUSES = ['部分已提单', '已生成提单']

const orderPickerVisible = ref(false)
const selectedRfqId = ref(null)
const selectedOrder = ref({})
const orderInfo = ref({})
const documentSummary = ref({})
const ticketRows = ref([])
const processSheetRows = ref([])
const ticketTotal = ref(0)
const processSheetTotal = ref(0)
const ticketPagination = reactive({ page: 1, pageSize: DEFAULT_CLIENT_PAGE_SIZE })
const processSheetPagination = reactive({ page: 1, pageSize: DEFAULT_CLIENT_PAGE_SIZE })
const loadingSummary = ref(false)
const loadingTickets = ref(false)
const loadingProcessSheets = ref(false)
const exportingTickets = ref(false)
const exportingProcessSheets = ref(false)

const ticketExportable = computed(() => Boolean(selectedRfqId.value && ticketTotal.value > 0 && !exportingTickets.value))
const processSheetExportable = computed(() => Boolean(selectedRfqId.value && processSheetTotal.value > 0 && !exportingProcessSheets.value))

const resetDocumentPagination = () => {
  ticketPagination.page = 1
  processSheetPagination.page = 1
}

const openOrderPicker = () => {
  orderPickerVisible.value = true
}

const handleOrderSelected = async (order) => {
  selectedOrder.value = order || {}
  selectedRfqId.value = order.id
  resetDocumentPagination()
  await loadDeliveryDocumentWorkspace(order.id)
}

const loadDeliveryDocumentWorkspace = async (rfqId) => {
  if (!rfqId) return
  await Promise.all([
    loadDocumentSummary(rfqId),
    loadTicketRows(rfqId),
    loadProcessSheetRows(rfqId)
  ])
}

const loadDocumentSummary = async (rfqId) => {
  loadingSummary.value = true
  try {
    const response = await getDeliveryDocumentSummary(rfqId)
    documentSummary.value = response || {}
    orderInfo.value = response.orderInfo || selectedOrder.value || {}
    ticketTotal.value = Number(response.ticketCount ?? ticketTotal.value ?? 0)
    processSheetTotal.value = Number(response.processSheetCount ?? processSheetTotal.value ?? 0)
  } catch (error) {
    console.error(error)
    ElMessage.error(error.response?.data?.message || '提单表据摘要加载失败。')
  } finally {
    loadingSummary.value = false
  }
}

const loadTicketRows = async (rfqId) => {
  loadingTickets.value = true
  try {
    const response = await listDeliveryDocumentTickets(rfqId, {
      page: ticketPagination.page,
      pageSize: ticketPagination.pageSize
    })
    ticketRows.value = response.records || []
    ticketTotal.value = Number(response.total ?? 0)
  } catch (error) {
    console.error(error)
    ElMessage.error(error.response?.data?.message || '提单行加载失败。')
  } finally {
    loadingTickets.value = false
  }
}

const loadProcessSheetRows = async (rfqId) => {
  loadingProcessSheets.value = true
  try {
    const response = await listDeliveryDocumentProcessSheets(rfqId, {
      page: processSheetPagination.page,
      pageSize: processSheetPagination.pageSize
    })
    processSheetRows.value = response.records || []
    processSheetTotal.value = Number(response.total ?? 0)
  } catch (error) {
    console.error(error)
    ElMessage.error(error.response?.data?.message || '工艺表行加载失败。')
  } finally {
    loadingProcessSheets.value = false
  }
}

const handleTicketPageChange = async (value) => {
  ticketPagination.page = value
  if (selectedRfqId.value) {
    await loadTicketRows(selectedRfqId.value)
  }
}

const handleTicketPageSizeChange = (value) => {
  ticketPagination.pageSize = value
}

const handleProcessSheetPageChange = async (value) => {
  processSheetPagination.page = value
  if (selectedRfqId.value) {
    await loadProcessSheetRows(selectedRfqId.value)
  }
}

const handleProcessSheetPageSizeChange = (value) => {
  processSheetPagination.pageSize = value
}

const exportDeliveryDocumentExcel = async (type) => {
  if (!selectedRfqId.value) return ElMessage.warning('请先选择提单表据订单。')
  const exportPathMap = {
    tickets: 'tickets',
    process: 'process-sheets'
  }
  const path = exportPathMap[type] || 'process-sheets'
  const exportingFlag = type === 'tickets' ? exportingTickets : exportingProcessSheets
  exportingFlag.value = true
  try {
    const result = await downloadDeliveryDocumentExport(
      path,
      selectedRfqId.value,
      `${path}-${selectedRfqId.value}.xlsx`
    )
    if (!result.success) {
      ElMessage.error(result.message || 'Excel 导出失败。')
    }
  } finally {
    exportingFlag.value = false
  }
}
</script>

<style scoped>
.delivery-document-page {
  gap: 16px;
}

.delivery-document-empty,
.delivery-document-summary {
  padding: 20px;
}

.delivery-document-results {
  display: grid;
  gap: 16px;
}

.delivery-document-summary-title {
  margin-bottom: 14px;
  font-size: 15px;
  font-weight: 600;
  color: var(--erp-text-primary);
}
</style>
