<template>
  <button
    v-if="canShow"
    type="button"
    class="erp-surface dashboard-surface dashboard-summary-card"
    :class="`dashboard-tone-${item.tone || 'primary'}`"
    @click="emit('select', item)"
  >
    <span class="dashboard-card-label">{{ item.title }}</span>
    <strong class="dashboard-card-value">
      {{ formatDashboardNumber(item.value) }}<small>{{ item.unit }}</small>
    </strong>
    <span class="dashboard-card-helper dashboard-muted">{{ item.helper }}</span>
    <span class="dashboard-card-trend">{{ item.trendText }}</span>
  </button>
</template>

<script setup>
import { computed } from 'vue'
import { canShowDashboardAction, formatDashboardNumber } from './dashboardNavigation'

const props = defineProps({
  item: {
    type: Object,
    required: true
  },
  authStore: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['select'])

const canShow = computed(() => canShowDashboardAction(props.item, props.authStore))
</script>

<style scoped>
.dashboard-surface {
  border-color: var(--erp-border);
  background: var(--erp-surface);
}

.dashboard-muted {
  color: var(--erp-text-secondary);
}

.dashboard-summary-card {
  display: flex;
  min-height: 158px;
  padding: var(--erp-space-4);
  flex-direction: column;
  align-items: flex-start;
  gap: var(--erp-space-2);
  cursor: pointer;
  text-align: left;
  transition:
    border-color var(--erp-duration-fast) var(--erp-ease-standard),
    box-shadow var(--erp-duration-fast) var(--erp-ease-standard),
    background-color var(--erp-duration-fast) var(--erp-ease-standard);
}

.dashboard-summary-card:hover,
.dashboard-summary-card:focus-visible {
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
</style>
