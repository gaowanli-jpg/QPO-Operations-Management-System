<template>
  <section class="erp-page">
    <header class="erp-page-header">
      <div>
        <h1>销售匹配</h1>
        <p>选择销售订单，预览历史已提单零件快照并确认补全生产信息。</p>
      </div>
    </header>

    <section class="erp-toolbar">
      <span class="toolbar-placeholder">阶段 1 已完成模块入口、路由和权限骨架。</span>
    </section>

    <section class="erp-surface">
      <h2>销售订单历史信息补全</h2>
      <p>后续阶段将在这里提供销售订单筛选、匹配预览和确认补全。</p>
    </section>
  </section>
</template>

<script setup>
</script>

<style scoped>
.erp-toolbar,
.erp-surface {
  color: var(--erp-text-secondary);
}

.erp-surface h2 {
  margin: 0 0 var(--erp-space-2);
  color: var(--erp-text);
  font-size: var(--erp-font-size-lg);
}

.erp-surface p {
  margin: 0;
}
</style>
