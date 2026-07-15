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
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-button :disabled="!selectedCustomer" @click="handleSearch">搜索</el-button>
        <el-button type="primary" :loading="loading" @click="refreshOrders">刷新</el-button>
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
          <el-table-column label="操作" width="280" align="center" fixed="right">
            <template #default="scope">
              <div class="erp-table-actions">
                <el-button size="small" @click="openDetail(scope.row)">详情</el-button>
                <el-button
                  size="small"
                  type="danger"
                  plain
                  class="erp-danger-action"
                  @click="resetProduction(scope.row)"
                >
                  重置提单/工艺
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
            layout="total, sizes, prev, pager, next"
            :total="total"
            background
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
            :title="`当前缺图零件：${missingDrawingItems.length} 个`"
            type="warning"
            :closable="false"
            show-icon
          />
          <el-button type="primary" plain :disabled="missingDrawingItems.length === 0" @click="openSupplementDialog">
            补传图纸
          </el-button>
        </div>

        <el-tabs class="detail-tabs">
          <el-tab-pane label="零件明细">
            <el-table :data="orderDetail.itemList || []" border stripe height="360">
              <el-table-column prop="partNo" label="图号" min-width="140" fixed />
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
          </el-tab-pane>

          <el-tab-pane label="提单行">
            <el-table :data="orderDetail.ticketList || []" border stripe height="320">
              <el-table-column prop="internalProjectNo" label="内部项目号" min-width="130" show-overflow-tooltip />
              <el-table-column prop="partNo" label="图号" min-width="140" />
              <el-table-column prop="version" label="版本" width="80" align="center" />
              <el-table-column prop="qty" label="数量" width="70" align="center" />
              <el-table-column prop="arrangeType" label="安排类型" width="110" />
              <el-table-column prop="startDate" label="开工日期" width="120" />
              <el-table-column prop="deliveryDate" label="交货日期" width="120" />
              <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="工艺表行">
            <el-table :data="orderDetail.processSheetList || []" border stripe height="320">
              <el-table-column prop="internalProjectNo" label="内部项目号" min-width="130" show-overflow-tooltip />
              <el-table-column prop="partNo" label="图号" min-width="140" />
              <el-table-column prop="version" label="版本" width="80" align="center" />
              <el-table-column prop="description" label="物料描述" min-width="180" show-overflow-tooltip />
              <el-table-column prop="qty" label="数量" width="70" align="center" />
              <el-table-column prop="deliveryDate" label="交期" width="120" />
              <el-table-column label="工艺路线" min-width="260" show-overflow-tooltip>
                <template #default="scope">{{ formatRoute(scope.row.processRoute) }}</template>
              </el-table-column>
            </el-table>
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
            <el-table-column prop="partNo" label="图号" min-width="140" />
            <el-table-column prop="version" label="版本" width="80" align="center" />
            <el-table-column prop="description" label="物料描述" min-width="180" show-overflow-tooltip />
            <el-table-column prop="qty" label="数量" width="70" align="center" />
          </el-table>
        </div>

        <div class="erp-upload-surface upload-area">
          <el-upload
            ref="supplementUploadRef"
            drag
            multiple
            action="#"
            accept=".pdf"
            :auto-upload="false"
            :on-change="handleSupplementFileChange"
            :on-remove="handleSupplementFileRemove"
          >
            <el-icon class="el-icon--upload"><upload-filled /></el-icon>
            <div class="el-upload__text">拖拽后补 PDF 图纸到这里，或点击选择</div>
            <template #tip>
              <div class="el-upload__tip">支持 图号.pdf、图号 空格说明.pdf、图号-说明.pdf，例如 L20100000733.pdf 或 L20100000733-定位块.pdf。</div>
            </template>
          </el-upload>
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
          <el-button type="primary" :loading="supplementUploading" :disabled="supplementFiles.length === 0" @click="submitSupplementDrawings">
            上传并匹配
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, defineComponent, h, ref, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import {
  getOrder,
  listCustomers,
  listOrders,
  resetProduction as resetProductionRequest,
  supplementDrawings
} from '../services/orderApi'

const orders = ref([])
const customers = ref([])
const selectedCustomer = ref('')
const keyword = ref('')
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const loading = ref(false)
const detailLoading = ref(false)
const detailVisible = ref(false)
const orderDetail = ref(null)
const supplementVisible = ref(false)
const supplementUploading = ref(false)
const supplementFiles = ref([])
const supplementResult = ref(null)
const supplementUploadRef = ref(null)

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

const missingDrawingItems = computed(() => (orderDetail.value?.itemList || []).filter(item => !item.drawingUrl))

watch([page, pageSize], () => {
  if (selectedCustomer.value) {
    loadOrders()
  }
})

const loadCustomers = async () => {
  try {
    const response = await listCustomers()
    customers.value = response || []
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
    ElMessage.error('订单列表加载失败。')
  } finally {
    loading.value = false
  }
}

const refreshOrders = async () => {
  await loadCustomers()
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

const handleCustomerClear = () => {
  keyword.value = ''
  orders.value = []
  total.value = 0
  page.value = 1
  orderDetail.value = null
  detailVisible.value = false
}

const openDetail = async (row) => {
  detailVisible.value = true
  detailLoading.value = true
  try {
    orderDetail.value = await getOrder(row.id)
  } catch (error) {
    console.error(error)
    ElMessage.error('订单详情加载失败。')
  } finally {
    detailLoading.value = false
  }
}

const resetProduction = async (row) => {
  await ElMessageBox.confirm(
    `确认重置订单「${row.rfqNo}」的提单行和工艺表行？零件可重新生成提单。`,
    '重置提单和工艺表',
    { type: 'warning', confirmButtonText: '确认重置', cancelButtonText: '取消' }
  )
  await postAction(() => resetProductionRequest(row.id), '订单提单和工艺表已重置')
}

const openSupplementDialog = () => {
  supplementResult.value = null
  supplementFiles.value = []
  if (supplementUploadRef.value) {
    supplementUploadRef.value.clearFiles()
  }
  supplementVisible.value = true
}

const handleSupplementFileChange = (uploadFile, uploadFiles) => {
  supplementFiles.value = uploadFiles.map(file => file.raw).filter(Boolean)
}

const handleSupplementFileRemove = (uploadFile, uploadFiles) => {
  supplementFiles.value = uploadFiles.map(file => file.raw).filter(Boolean)
}

const refreshCurrentDetail = async () => {
  if (!orderDetail.value?.orderInfo?.id) return
  const currentId = orderDetail.value.orderInfo.id
  orderDetail.value = await getOrder(currentId)
}

const submitSupplementDrawings = async () => {
  if (!orderDetail.value?.orderInfo?.id) return ElMessage.warning('请先打开订单详情。')
  if (supplementFiles.value.length === 0) return ElMessage.warning('请先选择需要补传的 PDF 图纸。')

  supplementUploading.value = true
  const formData = new FormData()
  supplementFiles.value.forEach(file => formData.append('drawings', file))

  try {
    const rfqId = orderDetail.value.orderInfo.id
    const response = await supplementDrawings(rfqId, formData)
    supplementResult.value = response
    ElMessage.success(response.message || '补传图纸处理完成。')
    supplementFiles.value = []
    if (supplementUploadRef.value) {
      supplementUploadRef.value.clearFiles()
    }
    await refreshCurrentDetail()
    await loadOrders()
  } catch (error) {
    console.error(error)
    ElMessage.error(error.response?.data?.message || '补传图纸失败。')
  } finally {
    supplementUploading.value = false
  }
}

const postAction = async (action, fallbackMessage) => {
  loading.value = true
  try {
    const response = await action()
    ElMessage.success(response.message || fallbackMessage)
    await loadOrders()
    if (orderDetail.value?.orderInfo?.id) {
      await refreshCurrentDetail()
    }
  } catch (error) {
    console.error(error)
    ElMessage.error('操作失败。')
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
.order-filter-group {
  width: 100%;
  justify-content: flex-end;
}

.customer-select {
  width: 220px;
  flex: 0 0 220px;
}

.search-input {
  width: 260px;
  flex: 0 0 260px;
}

.order-table-surface {
  overflow: hidden;
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

.upload-area :deep(.el-upload) {
  width: 100%;
}

.upload-area :deep(.el-upload-dragger) {
  width: 100%;
  border-radius: 8px;
  background: var(--erp-surface-soft);
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
  .customer-select,
  .search-input {
    width: 100%;
  }
}
</style>
