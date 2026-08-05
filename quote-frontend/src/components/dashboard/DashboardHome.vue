<template>
  <section class="erp-page dashboard-home" v-loading="loading">
    <header class="erp-page-header dashboard-hero">
      <div class="dashboard-hero-copy">
        <p class="dashboard-eyebrow">QPO 工作台</p>
        <h1 class="erp-page-title">首页看板</h1>
        <p class="erp-page-description dashboard-muted">
          汇总询价订单、销售订单、待办和异常关注项，帮助用户登录后直接进入下一步处理。
        </p>
        <div class="dashboard-hero-tags">
          <el-tag size="small" effect="plain">订单状态</el-tag>
          <el-tag size="small" type="success" effect="plain">业务待办</el-tag>
          <el-tag size="small" type="warning" effect="plain">异常关注</el-tag>
        </div>
      </div>
      <div class="dashboard-refresh-panel dashboard-surface">
        <span class="dashboard-refresh-label">数据更新时间</span>
        <strong>{{ updatedAt }}</strong>
        <el-button type="primary" plain :icon="Refresh" :loading="loading" @click="refreshDashboard">刷新</el-button>
      </div>
    </header>

    <section v-if="errorMessage" class="erp-surface dashboard-error-state">
      <span>{{ errorMessage }}</span>
      <el-button type="primary" plain size="small" :loading="loading" @click="loadDashboardOverview">重试</el-button>
    </section>

    <div class="dashboard-summary-grid">
      <DashboardSummaryCard
        v-for="card in visibleSummaryCards"
        :key="card.key"
        :item="card"
        :auth-store="authStore"
        @select="goToItem"
      />
    </div>

    <div class="dashboard-placeholder-grid dashboard-chart-grid">
      <DashboardChartCard
        title="询价订单状态分布"
        description="只展示未核价、已核价、已报价三种主链路状态。"
        :data="rfqStatusDistribution"
        :palette="rfqPalette"
        empty-text="当前权限范围内暂无询价订单状态数据"
        @select="goToItem"
      />
      <DashboardChartCard
        title="销售订单已报价客户分布"
        description="销售订单饼图只统计已报价订单，分类维度为客户。"
        :data="salesQuotedCustomerDistribution"
        :palette="salesPalette"
        empty-text="当前权限范围内暂无已报价销售订单"
        @select="goToItem"
      />
    </div>

    <div class="dashboard-workbench-grid">
      <DashboardCustomerMatrix
        :rows="customerMatrix"
        :auth-store="authStore"
        @rfq-select="goToOrders"
        @sales-select="goToSalesOrders"
      />

      <aside class="dashboard-side-stack">
        <DashboardTodoCard
          :items="visibleTodoCards"
          :auth-store="authStore"
          @select="goToItem"
        />
        <DashboardAttentionList
          :items="visibleAttentionItems"
          :auth-store="authStore"
          @select="goToItem"
        />
      </aside>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import { getDashboardOverview } from '../../services/dashboardApi'
import { useAuthStore } from '../../stores/authStore'
import DashboardAttentionList from './DashboardAttentionList.vue'
import DashboardChartCard from './DashboardChartCard.vue'
import DashboardCustomerMatrix from './DashboardCustomerMatrix.vue'
import DashboardSummaryCard from './DashboardSummaryCard.vue'
import DashboardTodoCard from './DashboardTodoCard.vue'
import { goDashboardTarget } from './dashboardNavigation'

const router = useRouter()
const authStore = useAuthStore()
const emptyDashboardData = () => ({
  updatedAt: '',
  summaryCards: [],
  rfqStatusDistribution: [],
  salesQuotedCustomerDistribution: [],
  customerMatrix: [],
  todoCards: [],
  attentionItems: []
})

const dashboardData = ref(emptyDashboardData())
const loading = ref(false)
const errorMessage = ref('')

const rfqPalette = ['#526170', '#9a5b13', '#1769aa']
const salesPalette = ['#1769aa', '#20724f', '#9a5b13', '#526170']

const updatedAt = computed(() => dashboardData.value.updatedAt || (loading.value ? '加载中' : '--'))
const summaryCards = computed(() => dashboardData.value.summaryCards)
const rfqStatusDistribution = computed(() => dashboardData.value.rfqStatusDistribution)
const salesQuotedCustomerDistribution = computed(() => dashboardData.value.salesQuotedCustomerDistribution)
const customerMatrix = computed(() => dashboardData.value.customerMatrix)
const todoCards = computed(() => dashboardData.value.todoCards)
const attentionItems = computed(() => dashboardData.value.attentionItems)

const visibleSummaryCards = computed(() => summaryCards.value)
const visibleTodoCards = computed(() => todoCards.value)
const visibleAttentionItems = computed(() => attentionItems.value)

const arrayOrEmpty = (value) => Array.isArray(value) ? value : []

const normalizeDashboardData = (payload = {}) => ({
  updatedAt: payload?.updatedAt || '',
  summaryCards: arrayOrEmpty(payload?.summaryCards),
  rfqStatusDistribution: arrayOrEmpty(payload?.rfqStatusDistribution),
  salesQuotedCustomerDistribution: arrayOrEmpty(payload?.salesQuotedCustomerDistribution),
  customerMatrix: arrayOrEmpty(payload?.customerMatrix),
  todoCards: arrayOrEmpty(payload?.todoCards),
  attentionItems: arrayOrEmpty(payload?.attentionItems)
})

const requestErrorMessage = (error) => error?.payload?.message || error?.response?.data?.message || error?.message || '首页看板数据加载失败，请稍后重试。'

const loadDashboardOverview = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const response = await getDashboardOverview()
    dashboardData.value = normalizeDashboardData(response)
  } catch (error) {
    errorMessage.value = requestErrorMessage(error)
  } finally {
    loading.value = false
  }
}

const refreshDashboard = () => loadDashboardOverview()

const compactQuery = (query) => Object.fromEntries(
  Object.entries(query).filter(([, value]) => value !== undefined && value !== null && value !== '')
)

const goToItem = (item) => goDashboardTarget(router, item)

const goToOrders = (row, status) => goDashboardTarget(router, {
  path: '/orders',
  query: compactQuery({
    status,
    customerId: row.customerId,
    customerName: row.customerName
  })
})

const goToSalesOrders = (row) => goDashboardTarget(router, {
  path: '/sales-orders',
  query: compactQuery({
    status: '已报价',
    customerId: row.customerId,
    customerName: row.customerName
  })
})

onMounted(loadDashboardOverview)
</script>

<style scoped>
.dashboard-home {
  display: flex;
  flex-direction: column;
  gap: var(--erp-space-5);
  min-height: 520px;
}

.dashboard-hero {
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--erp-space-4);
}

.dashboard-hero-copy {
  min-width: 0;
}

.dashboard-eyebrow {
  margin: 0 0 var(--erp-space-2);
  color: var(--erp-primary);
  font-size: var(--erp-font-size-xs);
  font-weight: var(--erp-font-weight-bold);
}

.dashboard-muted {
  color: var(--erp-text-secondary);
}

.dashboard-hero-tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--erp-space-2);
  margin-top: var(--erp-space-3);
}

.dashboard-surface {
  border-color: var(--erp-border);
  background: var(--erp-surface);
}

.dashboard-refresh-panel {
  display: flex;
  align-items: center;
  gap: var(--erp-space-3);
  min-height: 44px;
  padding: var(--erp-space-2) var(--erp-space-3);
  color: var(--erp-text);
  border: 1px solid var(--erp-border);
  border-radius: var(--erp-radius-lg);
  white-space: nowrap;
}

.dashboard-refresh-label {
  color: var(--erp-text-secondary);
  font-size: var(--erp-font-size-sm);
}

.dashboard-error-state {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--erp-space-3);
  padding: var(--erp-space-3) var(--erp-space-4);
  color: var(--erp-danger);
  border-color: var(--erp-danger-soft);
  background: var(--erp-danger-soft);
}

.dashboard-summary-grid,
.dashboard-placeholder-grid,
.dashboard-workbench-grid {
  display: grid;
  gap: var(--erp-space-4);
}

.dashboard-summary-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.dashboard-placeholder-grid,
.dashboard-chart-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.dashboard-workbench-grid {
  grid-template-columns: minmax(0, 1.45fr) minmax(340px, 0.55fr);
  align-items: start;
}

.dashboard-side-stack {
  display: flex;
  flex-direction: column;
  gap: var(--erp-space-4);
  min-width: 0;
}

:deep(.dashboard-summary-card:hover),
:deep(.dashboard-action-item:hover),
:deep(.dashboard-attention-item:hover),
:deep(.dashboard-matrix-button:hover) {
  border-color: var(--erp-primary);
  box-shadow: var(--erp-shadow-sm);
}

@media (max-width: 1280px) {
  .dashboard-summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .dashboard-workbench-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 960px) {
  .dashboard-placeholder-grid,
  .dashboard-chart-grid,
  .dashboard-summary-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .dashboard-hero,
  .dashboard-refresh-panel,
  .dashboard-error-state {
    flex-direction: column;
    align-items: flex-start;
  }

  .dashboard-refresh-panel,
  .dashboard-error-state {
    width: 100%;
    box-sizing: border-box;
    white-space: normal;
  }
}
</style>
