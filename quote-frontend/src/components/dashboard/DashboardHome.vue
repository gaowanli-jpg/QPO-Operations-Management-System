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
      <button
        v-for="card in visibleSummaryCards"
        :key="card.key"
        type="button"
        class="erp-surface dashboard-surface dashboard-summary-card"
        :class="`dashboard-tone-${card.tone}`"
        @click="goToItem(card)"
      >
        <span class="dashboard-card-label">{{ card.title }}</span>
        <strong class="dashboard-card-value">
          {{ formatDashboardNumber(card.value) }}<small>{{ card.unit }}</small>
        </strong>
        <span class="dashboard-card-helper dashboard-muted">{{ card.helper }}</span>
        <span class="dashboard-card-trend">{{ card.trendText }}</span>
      </button>
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
      <section class="erp-surface dashboard-surface dashboard-matrix-card">
        <div class="dashboard-section-header">
          <div>
            <h2>客户状态交叉表</h2>
            <p class="dashboard-muted">按客户合并展示询价三状态和销售已报价数量，不展示提单后状态。</p>
          </div>
          <el-tag size="small" type="info" effect="plain">客户视角</el-tag>
        </div>
        <div class="dashboard-matrix-table">
          <div class="dashboard-matrix-row dashboard-matrix-head">
            <span>客户</span>
            <span>未核价</span>
            <span>已核价</span>
            <span>已报价</span>
            <span>销售已报价</span>
            <span>关注合计</span>
          </div>
          <div
            v-for="row in customerMatrix"
            :key="row.customerId"
            class="dashboard-matrix-row"
          >
            <span class="dashboard-customer-cell">
              <strong>{{ row.customerName }}</strong>
              <small class="dashboard-muted">{{ row.customerCode }}</small>
            </span>
            <button type="button" @click="goToOrders(row, '未核价')">{{ formatDashboardNumber(row.rfqUnpriced) }}</button>
            <button type="button" @click="goToOrders(row, '已核价')">{{ formatDashboardNumber(row.rfqPriced) }}</button>
            <button type="button" @click="goToOrders(row, '已报价')">{{ formatDashboardNumber(row.rfqQuoted) }}</button>
            <button type="button" @click="goToSalesOrders(row)">{{ formatDashboardNumber(row.salesQuoted) }}</button>
            <strong>{{ formatDashboardNumber(row.totalFocus) }}</strong>
          </div>
        </div>
      </section>

      <aside class="dashboard-side-stack">
        <section class="erp-surface dashboard-surface dashboard-todo-card">
          <div class="dashboard-section-header">
            <div>
              <h2>待办快捷入口</h2>
              <p class="dashboard-muted">点击进入对应业务模块继续处理。</p>
            </div>
          </div>
          <div class="dashboard-action-list">
            <button
              v-for="todo in visibleTodoCards"
              :key="todo.key"
              type="button"
              class="dashboard-action-item"
              @click="goToItem(todo)"
            >
              <span>
                <strong>{{ todo.title }}</strong>
                <small class="dashboard-muted">{{ todo.description }}</small>
              </span>
              <em>{{ formatDashboardNumber(todo.value) }}{{ todo.unit }}</em>
            </button>
          </div>
        </section>

        <section class="erp-surface dashboard-surface dashboard-attention-card">
          <div class="dashboard-section-header">
            <div>
              <h2>最近异常 / 关注项</h2>
              <p class="dashboard-muted">只做入口提示，实际处理仍回到业务模块。</p>
            </div>
          </div>
          <div class="dashboard-action-list">
            <button
              v-for="item in visibleAttentionItems"
              :key="item.key"
              type="button"
              class="dashboard-attention-item"
              :class="`dashboard-severity-${item.severity}`"
              @click="goToItem(item)"
            >
              <span>
                <strong>{{ item.title }}</strong>
                <small>{{ item.description }}</small>
              </span>
              <em>{{ formatDashboardNumber(item.value) }}{{ item.unit }}</em>
            </button>
          </div>
        </section>
      </aside>
    </div>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import { useAuthStore } from '../../stores/authStore'
import DashboardChartCard from './DashboardChartCard.vue'
import { dashboardMockData } from './dashboardMockData'
import { formatDashboardNumber, canShowDashboardAction, goDashboardTarget } from './dashboardNavigation'

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

const visibleSummaryCards = computed(() => summaryCards.filter((item) => canShowDashboardAction(item, authStore)))
const visibleTodoCards = computed(() => todoCards.filter((item) => canShowDashboardAction(item, authStore)))
const visibleAttentionItems = computed(() => attentionItems.filter((item) => canShowDashboardAction(item, authStore)))

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

.dashboard-summary-card,
.dashboard-action-item,
.dashboard-attention-item,
.dashboard-matrix-row button {
  cursor: pointer;
}

.dashboard-summary-card {
  display: flex;
  min-height: 158px;
  padding: var(--erp-space-4);
  flex-direction: column;
  align-items: flex-start;
  gap: var(--erp-space-2);
  text-align: left;
  transition:
    border-color var(--erp-duration-fast) var(--erp-ease-standard),
    box-shadow var(--erp-duration-fast) var(--erp-ease-standard),
    background-color var(--erp-duration-fast) var(--erp-ease-standard);
}

.dashboard-summary-card:hover,
.dashboard-action-item:hover,
.dashboard-attention-item:hover,
.dashboard-matrix-row button:hover {
  border-color: var(--erp-primary);
  box-shadow: var(--erp-shadow-sm);
}

.dashboard-card-label,
.dashboard-card-helper,
.dashboard-card-trend {
  font-size: var(--erp-font-size-sm);
}

.dashboard-card-label {
  color: var(--erp-text-secondary);
}

.dashboard-card-value {
  color: var(--erp-text);
  font-size: 30px;
  font-variant-numeric: tabular-nums;
  line-height: 1;
}

.dashboard-card-value small {
  margin-left: var(--erp-space-1);
  color: var(--erp-text-muted);
  font-size: var(--erp-font-size-sm);
  font-weight: var(--erp-font-weight-medium);
}

.dashboard-card-trend {
  margin-top: auto;
  padding-top: var(--erp-space-2);
  color: var(--erp-text);
}

.dashboard-tone-primary {
  border-top: 3px solid var(--erp-primary);
}

.dashboard-tone-success {
  border-top: 3px solid var(--erp-success);
}

.dashboard-tone-warning {
  border-top: 3px solid var(--erp-warning);
}

.dashboard-tone-info {
  border-top: 3px solid var(--erp-info);
}

.dashboard-matrix-card,
.dashboard-todo-card,
.dashboard-attention-card {
  min-width: 0;
  overflow: hidden;
}

.dashboard-section-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--erp-space-3);
  padding: var(--erp-space-4);
  border-bottom: 1px solid var(--erp-border);
  background: var(--erp-surface-soft);
}

.dashboard-section-header h2 {
  margin: 0 0 var(--erp-space-1);
  color: var(--erp-text);
  font-size: var(--erp-font-size-lg);
}

.dashboard-section-header p {
  margin: 0;
  font-size: var(--erp-font-size-sm);
  line-height: var(--erp-line-height-base);
}

.dashboard-action-list {
  display: grid;
  gap: var(--erp-space-2);
  padding: var(--erp-space-4);
}

.dashboard-action-item,
.dashboard-attention-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--erp-space-3);
  min-height: 48px;
  padding: var(--erp-space-3);
  color: var(--erp-text);
  background: var(--erp-surface);
  border: 1px solid var(--erp-border-soft);
  border-radius: var(--erp-radius-lg);
  text-align: left;
}

.dashboard-action-item span,
.dashboard-attention-item span {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.dashboard-action-item small,
.dashboard-attention-item small {
  line-height: var(--erp-line-height-tight);
}

.dashboard-action-item em,
.dashboard-attention-item em {
  color: var(--erp-text);
  font-style: normal;
  font-variant-numeric: tabular-nums;
  font-weight: var(--erp-font-weight-bold);
}

.dashboard-severity-warning {
  background: var(--erp-warning-soft);
}

.dashboard-severity-danger {
  background: var(--erp-danger-soft);
}

.dashboard-matrix-table {
  padding: var(--erp-space-4);
  overflow-x: auto;
}

.dashboard-matrix-row {
  display: grid;
  grid-template-columns: minmax(180px, 1.35fr) repeat(5, minmax(92px, 0.55fr));
  align-items: center;
  gap: var(--erp-space-2);
  min-width: 780px;
  padding: var(--erp-space-2) 0;
  border-bottom: 1px solid var(--erp-border-soft);
}

.dashboard-matrix-row:last-child {
  border-bottom: 0;
}

.dashboard-matrix-head {
  color: var(--erp-text-secondary);
  font-size: var(--erp-font-size-sm);
  font-weight: var(--erp-font-weight-bold);
}

.dashboard-customer-cell {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.dashboard-customer-cell strong,
.dashboard-customer-cell small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dashboard-matrix-row button {
  min-height: 34px;
  color: var(--erp-primary);
  background: var(--erp-primary-soft);
  border: 1px solid transparent;
  border-radius: var(--erp-radius-md);
  font: inherit;
  font-variant-numeric: tabular-nums;
  font-weight: var(--erp-font-weight-bold);
}

.dashboard-matrix-row > strong {
  font-variant-numeric: tabular-nums;
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
