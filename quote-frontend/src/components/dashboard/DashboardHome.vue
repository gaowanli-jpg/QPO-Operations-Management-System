<template>
  <section class="erp-page dashboard-home">
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
        <el-button type="primary" plain :icon="Refresh" @click="refreshUpdatedAt">刷新</el-button>
      </div>
    </header>

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
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import { useAuthStore } from '../../stores/authStore'
import DashboardAttentionList from './DashboardAttentionList.vue'
import DashboardChartCard from './DashboardChartCard.vue'
import DashboardCustomerMatrix from './DashboardCustomerMatrix.vue'
import DashboardSummaryCard from './DashboardSummaryCard.vue'
import DashboardTodoCard from './DashboardTodoCard.vue'
import { dashboardMockData } from './dashboardMockData'
import { goDashboardTarget } from './dashboardNavigation'

const router = useRouter()
const authStore = useAuthStore()
const updatedAtValue = ref(new Date(dashboardMockData.updatedAt))

const rfqPalette = ['#526170', '#9a5b13', '#1769aa']
const salesPalette = ['#1769aa', '#20724f', '#9a5b13', '#526170']

const formatDateTime = (value) => value.toLocaleString('zh-CN', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hour12: false
}).replace(/\//g, '-')

const updatedAt = computed(() => formatDateTime(updatedAtValue.value))
const summaryCards = dashboardMockData.summaryCards
const rfqStatusDistribution = dashboardMockData.rfqStatusDistribution
const salesQuotedCustomerDistribution = dashboardMockData.salesQuotedCustomerDistribution
const customerMatrix = dashboardMockData.customerMatrix
const todoCards = dashboardMockData.todoCards
const attentionItems = dashboardMockData.attentionItems

const visibleSummaryCards = summaryCards
const visibleTodoCards = todoCards
const visibleAttentionItems = attentionItems

const refreshUpdatedAt = () => {
  updatedAtValue.value = new Date()
}

const goToItem = (item) => goDashboardTarget(router, item)

const goToOrders = (row, status) => goDashboardTarget(router, {
  path: '/orders',
  query: {
    status,
    customerId: row.customerId
  }
})

const goToSalesOrders = (row) => goDashboardTarget(router, {
  path: '/sales-orders',
  query: {
    status: '已报价',
    customerId: row.customerId
  }
})
</script>

<style scoped>
.dashboard-home {
  display: flex;
  flex-direction: column;
  gap: var(--erp-space-5);
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
  .dashboard-refresh-panel {
    flex-direction: column;
    align-items: flex-start;
  }

  .dashboard-refresh-panel {
    width: 100%;
    box-sizing: border-box;
    white-space: normal;
  }
}
</style>
