<template>
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
      <div v-if="rows.length === 0" class="dashboard-matrix-empty">当前权限范围内暂无客户状态统计</div>
      <div
        v-for="row in rows"
        :key="row.customerId"
        class="dashboard-matrix-row"
      >
        <span class="dashboard-customer-cell">
          <strong :title="row.customerName">{{ row.customerName }}</strong>
          <small class="dashboard-muted" :title="row.customerCode">{{ row.customerCode }}</small>
        </span>
        <button
          v-if="canOpenRfq"
          type="button"
          class="dashboard-matrix-button"
          :class="{ 'dashboard-number-muted': isZeroValue(row.rfqUnpriced) }"
          @click="emit('rfq-select', row, '未核价')"
        >
          {{ formatDashboardNumber(row.rfqUnpriced) }}
        </button>
        <span v-else class="dashboard-matrix-locked">--</span>
        <button
          v-if="canOpenRfq"
          type="button"
          class="dashboard-matrix-button"
          :class="{ 'dashboard-number-muted': isZeroValue(row.rfqPriced) }"
          @click="emit('rfq-select', row, '已核价')"
        >
          {{ formatDashboardNumber(row.rfqPriced) }}
        </button>
        <span v-else class="dashboard-matrix-locked">--</span>
        <button
          v-if="canOpenRfq"
          type="button"
          class="dashboard-matrix-button"
          :class="{ 'dashboard-number-muted': isZeroValue(row.rfqQuoted) }"
          @click="emit('rfq-select', row, '已报价')"
        >
          {{ formatDashboardNumber(row.rfqQuoted) }}
        </button>
        <span v-else class="dashboard-matrix-locked">--</span>
        <button
          v-if="canOpenSales"
          type="button"
          class="dashboard-matrix-button"
          :class="{ 'dashboard-number-muted': isZeroValue(row.salesQuoted) }"
          @click="emit('sales-select', row)"
        >
          {{ formatDashboardNumber(row.salesQuoted) }}
        </button>
        <span v-else class="dashboard-matrix-locked">--</span>
        <strong :class="{ 'dashboard-number-muted': isZeroValue(row.totalFocus) }">
          {{ formatDashboardNumber(row.totalFocus) }}
        </strong>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import { canShowDashboardAction, formatDashboardNumber } from './dashboardNavigation'

const props = defineProps({
  rows: {
    type: Array,
    default: () => []
  },
  authStore: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['rfq-select', 'sales-select'])

const canOpenRfq = computed(() => canShowDashboardAction({ permission: 'market:order:view' }, props.authStore))
const canOpenSales = computed(() => canShowDashboardAction({ permission: 'market:sales-order:view' }, props.authStore))

const isZeroValue = (value) => Number(value) === 0
</script>

<style scoped>
.dashboard-surface {
  border-color: var(--erp-border);
  background: var(--erp-surface);
}

.dashboard-muted {
  color: var(--erp-text-secondary);
}

.dashboard-matrix-card {
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

.dashboard-matrix-table {
  padding: var(--erp-space-4);
  overflow-x: auto;
}

.dashboard-matrix-row {
  display: grid;
  grid-template-columns: minmax(220px, 1.35fr) repeat(5, minmax(92px, 0.55fr));
  align-items: center;
  gap: var(--erp-space-2);
  min-width: 820px;
  padding: var(--erp-space-2) 0;
  border-bottom: 1px solid var(--erp-border-soft);
}

.dashboard-matrix-row:last-child {
  border-bottom: 0;
}

.dashboard-matrix-empty {
  min-width: 820px;
  margin-top: var(--erp-space-3);
  padding: var(--erp-space-5);
  color: var(--erp-text-secondary);
  background: var(--erp-surface-muted);
  border: 1px dashed var(--erp-border-strong);
  border-radius: var(--erp-radius-lg);
  text-align: center;
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

.dashboard-matrix-button {
  min-height: 34px;
  color: var(--erp-primary);
  background: var(--erp-primary-soft);
  border: 1px solid transparent;
  border-radius: var(--erp-radius-md);
  cursor: pointer;
  font: inherit;
  font-variant-numeric: tabular-nums;
  font-weight: var(--erp-font-weight-bold);
  transition:
    border-color var(--erp-duration-fast) var(--erp-ease-standard),
    box-shadow var(--erp-duration-fast) var(--erp-ease-standard);
}

.dashboard-matrix-button:hover,
.dashboard-matrix-button:focus-visible {
  border-color: var(--erp-primary);
  box-shadow: var(--erp-shadow-sm);
}

.dashboard-matrix-row > strong,
.dashboard-matrix-locked {
  font-variant-numeric: tabular-nums;
}

.dashboard-matrix-locked,
.dashboard-number-muted {
  color: var(--erp-text-muted);
  background: var(--erp-surface-soft);
}
</style>
