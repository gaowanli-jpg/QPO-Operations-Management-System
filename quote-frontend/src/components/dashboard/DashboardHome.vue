<template>
  <section class="erp-page dashboard-home">
    <header class="erp-page-header dashboard-hero">
      <div>
        <p class="dashboard-eyebrow">QPO 工作台</p>
        <h1 class="erp-page-title">首页看板</h1>
        <p class="erp-page-description">
          先用占位数据搭建首页结构，后续接入真实聚合接口。
        </p>
      </div>
      <div class="dashboard-refresh-panel">
        <span class="dashboard-refresh-label">数据更新时间</span>
        <strong>{{ updatedAt }}</strong>
        <el-button type="primary" plain :icon="Refresh" @click="refreshUpdatedAt">刷新</el-button>
      </div>
    </header>

    <div class="dashboard-placeholder-grid">
      <section
        v-for="section in placeholderSections"
        :key="section.title"
        class="erp-surface dashboard-placeholder-card"
      >
        <div class="dashboard-placeholder-icon">
          <el-icon><component :is="section.icon" /></el-icon>
        </div>
        <div>
          <h2>{{ section.title }}</h2>
          <p>{{ section.description }}</p>
        </div>
      </section>
    </div>

    <section class="erp-surface dashboard-stage-note">
      <div>
        <h2>阶段 1 页面骨架</h2>
        <p>
          当前阶段只确认入口、菜单、页面标题和占位区域；图表、客户交叉表、待办和异常列表会在后续阶段接入 mock 数据。
        </p>
      </div>
    </section>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue'
import { DataAnalysis, Grid, Refresh, Tickets } from '@element-plus/icons-vue'

const updatedAtValue = ref(new Date())

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

const refreshUpdatedAt = () => {
  updatedAtValue.value = new Date()
}

const placeholderSections = [
  {
    title: '询价订单状态分布',
    description: '后续展示未核价、已核价、已报价三个状态的询价订单占比。',
    icon: DataAnalysis
  },
  {
    title: '销售订单已报价客户分布',
    description: '后续只统计已报价销售订单，并按客户展示分布。',
    icon: Tickets
  },
  {
    title: '客户状态交叉表',
    description: '后续按客户展示询价三状态和销售已报价数量。',
    icon: Grid
  }
]
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

.dashboard-eyebrow {
  margin: 0 0 var(--erp-space-2);
  color: var(--erp-primary);
  font-size: var(--erp-font-size-xs);
  font-weight: var(--erp-font-weight-bold);
}

.dashboard-refresh-panel {
  display: flex;
  align-items: center;
  gap: var(--erp-space-3);
  min-height: 44px;
  padding: var(--erp-space-2) var(--erp-space-3);
  color: var(--erp-text);
  background: var(--erp-surface);
  border: 1px solid var(--erp-border);
  border-radius: var(--erp-radius-lg);
  white-space: nowrap;
}

.dashboard-refresh-label {
  color: var(--erp-text-secondary);
  font-size: var(--erp-font-size-sm);
}

.dashboard-placeholder-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--erp-space-4);
}

.dashboard-placeholder-card {
  display: flex;
  min-height: 128px;
  gap: var(--erp-space-4);
  align-items: flex-start;
}

.dashboard-placeholder-card h2,
.dashboard-stage-note h2 {
  margin: 0 0 var(--erp-space-2);
  color: var(--erp-text);
  font-size: var(--erp-font-size-lg);
}

.dashboard-placeholder-card p,
.dashboard-stage-note p {
  margin: 0;
  color: var(--erp-text-secondary);
  line-height: var(--erp-line-height-relaxed);
}

.dashboard-placeholder-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  flex: 0 0 42px;
  color: var(--erp-primary);
  background: var(--erp-primary-soft);
  border-radius: var(--erp-radius-lg);
}

.dashboard-stage-note {
  min-height: 120px;
}

@media (max-width: 1180px) {
  .dashboard-placeholder-grid {
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
