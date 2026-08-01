<template>
  <div class="erp-page sales-order-manage-page">
    <header class="erp-page-header">
      <div>
        <h1 class="erp-page-title">销售订单</h1>
        <p class="erp-page-description">按客户查看销售订单，后续接入独立销售订单列表、零件明细、提单行和工艺表行。</p>
      </div>
    </header>

    <section class="erp-toolbar">
      <div class="erp-toolbar-group sales-order-filter-group">
        <el-button v-permission="'market:sales-order:customer-select'" class="customer-picker-trigger" @click="customerPickerVisible = true">
          <span
            :class="selectedCustomer ? 'customer-picker-text' : 'customer-picker-placeholder'"
            :title="selectedCustomer || '先选择客户'"
          >
            {{ selectedCustomer || '先选择客户' }}
          </span>
          <span class="customer-picker-action">选择客户</span>
        </el-button>
        <el-button v-if="selectedCustomer" plain @click="handleCustomerClear">清除客户</el-button>
        <el-select v-model="statusFilter" :disabled="!selectedCustomer" placeholder="状态" class="status-filter">
          <el-option v-for="status in SALES_ORDER_STATUSES" :key="status.value" :label="status.label" :value="status.value" />
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
              <template #default="{ row }">
                <el-tag type="info" size="small">{{ row.status || '-' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="零件数" width="90" align="center">
              <template #default="{ row }">{{ row.itemCount || 0 }}</template>
            </el-table-column>
            <el-table-column label="已提 / 总数" width="110" align="center">
              <template #default="{ row }">{{ row.releasedQuantity || 0 }} / {{ row.totalQuantity || 0 }}</template>
            </el-table-column>
            <el-table-column label="缺图" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="row.drawingMissingCount > 0 ? 'warning' : 'success'" size="small">
                  {{ row.drawingMissingCount || 0 }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="u9SyncStatus" label="U9状态" width="120" />
            <el-table-column prop="updatedAt" label="更新时间" width="180" show-overflow-tooltip />
            <el-table-column label="操作" width="160" align="center" fixed="right">
              <template #default="{ row }">
                <div class="erp-table-actions sales-order-row-actions">
                  <el-button size="small" @click="openDetail(row)">查看销售订单</el-button>
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

    <el-drawer v-model="detailVisible" title="销售订单详情" size="72%">
      <div v-if="selectedOrder" class="detail-wrap">
        <el-descriptions :column="2" border class="detail-descriptions">
          <el-descriptions-item label="客户">{{ selectedCustomer || '-' }}</el-descriptions-item>
          <el-descriptions-item label="销售订单号">{{ selectedOrder.salesOrderNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="客户订单号">{{ selectedOrder.customerOrderNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="单据类型编码">{{ selectedOrder.documentTypeCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ selectedOrder.status || '-' }}</el-descriptions-item>
          <el-descriptions-item label="U9状态">{{ selectedOrder.u9SyncStatus || '-' }}</el-descriptions-item>
        </el-descriptions>

        <el-tabs v-model="activeDetailTab" class="detail-tabs">
          <el-tab-pane label="零件明细" name="items">
            <div class="erp-empty-state">
              <el-empty description="销售订单零件明细将在后续阶段接入独立接口" />
            </div>
          </el-tab-pane>
          <el-tab-pane label="提单行" name="tickets">
            <div class="erp-empty-state">
              <el-empty description="销售订单来源提单行将在后续阶段按来源字段分页展示" />
            </div>
          </el-tab-pane>
          <el-tab-pane label="工艺表行" name="processSheets">
            <div class="erp-empty-state">
              <el-empty description="销售订单来源工艺表行将在后续阶段接入" />
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-drawer>

    <CustomerPickerDialog
      v-model="customerPickerVisible"
      :current-customer="selectedCustomer"
      customer-scope="sales-order"
      @select="handleCustomerSelected"
    />
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import CustomerPickerDialog from './CustomerPickerDialog.vue'

const SALES_ORDER_STATUSES = [
  { label: '全部', value: '' },
  { label: '已报价', value: '已报价' },
  { label: '部分已提单', value: '部分已提单' },
  { label: '已生成提单', value: '已生成提单' },
  { label: '废弃', value: '废弃' }
]

const orders = ref([])
const selectedCustomer = ref('')
const selectedOrder = ref(null)
const keyword = ref('')
const statusFilter = ref('')
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const loading = ref(false)
const customerPickerVisible = ref(false)
const detailVisible = ref(false)
const activeDetailTab = ref('items')

watch([page, pageSize], () => {
  if (selectedCustomer.value) {
    refreshOrders()
  }
})

const refreshOrders = async () => {
  loading.value = true
  try {
    orders.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

const handleSearch = async () => {
  page.value = 1
  await refreshOrders()
}

const resolveCustomerName = (customer) => typeof customer === 'string' ? customer : (customer?.customerName || '')

const handleCustomerSelected = async (customer) => {
  selectedCustomer.value = resolveCustomerName(customer)
  keyword.value = ''
  statusFilter.value = ''
  page.value = 1
  selectedOrder.value = null
  detailVisible.value = false
  await refreshOrders()
}

const handleCustomerClear = () => {
  selectedCustomer.value = ''
  keyword.value = ''
  statusFilter.value = ''
  orders.value = []
  total.value = 0
  selectedOrder.value = null
  detailVisible.value = false
}

const openDetail = (row) => {
  selectedOrder.value = row
  activeDetailTab.value = 'items'
  detailVisible.value = true
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

.status-filter {
  width: 150px;
  flex: 0 0 150px;
}

.search-input {
  width: 320px;
  flex: 0 0 320px;
}

.sales-order-table-surface .erp-table-body {
  padding: var(--erp-space-3);
}

.sales-order-empty-state {
  margin: var(--erp-space-4);
}

.sales-order-row-actions {
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

@media (max-width: 1200px) {
  .sales-order-filter-group {
    justify-content: flex-start;
  }
}

@media (max-width: 760px) {
  .customer-picker-trigger,
  .search-input,
  .status-filter {
    width: 100%;
    flex-basis: auto;
  }
}
</style>
