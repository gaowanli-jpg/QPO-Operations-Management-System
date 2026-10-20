<template>
  <section class="erp-surface dashboard-surface dashboard-attention-card">
    <div class="dashboard-section-header">
      <div>
        <h2>最近异常 / 关注项</h2>
        <p class="dashboard-muted">只做入口提示，实际处理仍回到业务模块。</p>
      </div>
    </div>
    <div class="dashboard-action-list">
      <button
        v-for="item in visibleItems"
        :key="item.key"
        type="button"
        class="dashboard-attention-item"
        :class="severityClass(item)"
        @click="emit('select', item)"
      >
        <span>
          <strong>{{ item.title }}</strong>
          <small>{{ item.description }}</small>
        </span>
        <em>{{ formatDashboardNumber(item.value) }}{{ item.unit }}</em>
      </button>
      <p v-if="visibleItems.length === 0" class="dashboard-empty-text">
        当前权限范围内暂无异常或等待关注项
      </p>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import { canShowDashboardAction, formatDashboardNumber } from './dashboardNavigation'

const props = defineProps({
  items: {
    type: Array,
    default: () => []
  },
  authStore: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['select'])

const visibleItems = computed(() => props.items.filter((item) => canShowDashboardAction(item, props.authStore)))

const severityClass = (item = {}) => `dashboard-severity-${item.severity || 'info'}`
</script>

<style scoped>
.dashboard-surface {
  border-color: var(--erp-border);
  background: var(--erp-surface);
}

.dashboard-muted {
  color: var(--erp-text-secondary);
}

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

.dashboard-attention-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--erp-space-3);
  min-height: 48px;
  padding: var(--erp-space-3);
  color: var(--erp-text);
  border: 1px solid var(--erp-border-soft);
  border-radius: var(--erp-radius-lg);
  cursor: pointer;
  text-align: left;
  transition:
    border-color var(--erp-duration-fast) var(--erp-ease-standard),
    box-shadow var(--erp-duration-fast) var(--erp-ease-standard);
}

.dashboard-attention-item:hover,
.dashboard-attention-item:focus-visible {
  border-color: var(--erp-primary);
  box-shadow: var(--erp-shadow-sm);
}

.dashboard-attention-item span {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.dashboard-attention-item small {
  line-height: var(--erp-line-height-tight);
}

.dashboard-attention-item em {
  color: var(--erp-text);
  font-style: normal;
  font-variant-numeric: tabular-nums;
  font-weight: var(--erp-font-weight-bold);
}

.dashboard-severity-danger {
  background: var(--erp-danger-soft);
}

.dashboard-severity-warning {
  background: var(--erp-warning-soft);
}

.dashboard-severity-info {
  background: var(--erp-primary-soft);
}

.dashboard-empty-text {
  margin: 0;
  padding: var(--erp-space-3);
  color: var(--erp-text-secondary);
  background: var(--erp-surface-soft);
  border: 1px dashed var(--erp-border);
  border-radius: var(--erp-radius-lg);
  font-size: var(--erp-font-size-sm);
}
</style>
