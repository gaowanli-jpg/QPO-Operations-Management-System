<template>
  <section class="erp-page sales-order-u9-sync-page">
    <header class="erp-page-header sales-order-u9-sync-header">
      <div>
        <h1 class="erp-page-title">U9同步</h1>
        <p class="erp-page-description">选择已生成提单的销售订单，后续按整单同步已确认物料号到 U9。</p>
      </div>
      <div class="erp-page-actions">
        <el-button :icon="Refresh" disabled>刷新</el-button>
      </div>
    </header>

    <section class="erp-toolbar">
      <div class="sales-order-u9-sync-toolbar">
        <label class="sales-order-u9-sync-filter">
          <span>客户</span>
          <el-select placeholder="选择客户" disabled />
        </label>
        <label class="sales-order-u9-sync-filter sales-order-u9-sync-keyword-filter">
          <span>销售订单筛选</span>
          <el-input placeholder="销售订单号 / 客户订单号 / 内部项目号" disabled />
        </label>
        <label class="sales-order-u9-sync-filter">
          <span>U9 状态</span>
          <el-select placeholder="全部状态" disabled />
        </label>
      </div>
    </section>

    <section class="erp-surface sales-order-u9-sync-placeholder">
      <el-empty description="U9同步模块骨架已建立，列表、预览和提交能力将在后续阶段接入。" />
    </section>
  </section>
</template>

<script setup>
import { Refresh } from '@element-plus/icons-vue'
</script>

<style scoped>
.sales-order-u9-sync-page {
  gap: var(--erp-space-5);
}

.sales-order-u9-sync-header {
  align-items: flex-start;
}

.sales-order-u9-sync-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: var(--erp-space-4);
  align-items: flex-end;
}

.sales-order-u9-sync-filter {
  display: flex;
  min-width: 220px;
  flex-direction: column;
  gap: var(--erp-space-2);
  color: var(--erp-text-secondary);
  font-size: var(--erp-font-size-sm);
  font-weight: var(--erp-font-weight-medium);
}

.sales-order-u9-sync-keyword-filter {
  min-width: 320px;
  flex: 1 1 320px;
}

.sales-order-u9-sync-placeholder {
  min-height: 260px;
  display: grid;
  place-items: center;
}
</style>
